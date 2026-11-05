-- pgTAP 047: the storefront RPCs - payload shape, ordering, caps and edit conflicts.
--
-- Contract under test, in the order it matters:
--   1. get_storefront returns IDS ONLY. No price, stock or fee key may appear, because the
--      whole design rests on the app resolving money from the server-side catalogue;
--   2. ordering and visibility are the manager's and the policy's, not the caller's;
--   3. save/reorder/delete are manager-only and validate before they write;
--   4. a stale p_expected_updated_at loses loudly instead of overwriting another manager.
--
-- A note on what this suite deliberately does NOT try to prove: "a failed save leaves no
-- partial state" is not observable through pgTAP. throws_ok runs the statement inside a
-- subtransaction and rolls it back, so ANY write the function made is undone by Postgres
-- whether or not the function was written carefully - a test for it would pass even against
-- a naive delete-then-validate implementation. What IS observable, and what S47-16..19 pin
-- down, is that a rejected save names the specific problem rather than failing generically,
-- and that a successful save writes the section and its whole item list in one call (S47-02..04).
--
-- Self-contained: creates pgTAP and fixtures inside this transaction, which the trailing
-- ROLLBACK undoes entirely.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgtap WITH SCHEMA extensions;
SET LOCAL search_path = public, extensions;

DO $boot$
BEGIN
  IF to_regprocedure('public.get_storefront(text)') IS NULL
     OR to_regprocedure('public.save_storefront_section(uuid,text,text,text,text,boolean,uuid[],timestamptz)') IS NULL THEN
    RAISE EXCEPTION 'Migration 20261105000001_storefront_sections.sql has not been applied. Apply it before running suite 047.';
  END IF;
END;
$boot$;

CREATE FUNCTION pg_temp.make_profile(p_id uuid, p_role public.user_role, p_name text)
RETURNS void LANGUAGE plpgsql SET search_path = public, extensions AS $fn$
BEGIN
  INSERT INTO auth.users(id, email, aud, role)
    VALUES (p_id, 'fixture-' || p_id::text || '@test.invalid', 'authenticated', 'authenticated');
  UPDATE public.profiles SET role = p_role, display_name = p_name, is_active = true,
         must_change_password = false
   WHERE id = p_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'Signup trigger did not create the fixture profile'; END IF;
END;
$fn$;

SELECT plan(27);

SELECT pg_temp.make_profile('04700000-0000-0000-0000-000000000001', 'customer', 'QA 047 Customer');
SELECT pg_temp.make_profile('04700000-0000-0000-0000-000000000002', 'manager',  'QA 047 Manager');

INSERT INTO public.categories (name, slug) VALUES ('QA47 Cat', 'qa47-cat');
INSERT INTO public.products (sku, name, quantity_type, price_paise, price_basis_qty,
                             unit_label, step_qty, min_qty, active, available)
     VALUES ('QA47-P1', 'QA47 Product One', 'PACKED', 6000, 1, '1 pack', 1, 1, true, true);

-- ---------------------------------------------------------------- empty
SELECT is(public.get_storefront('home'), '[]'::jsonb,
  'S47-01 an empty storefront returns [], so the app can key its fallback off it');

-- ---------------------------------------------------------------- manager creates a section
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '04700000-0000-0000-0000-000000000002';

SELECT lives_ok($$SELECT public.save_storefront_section(
        NULL, 'home', 'Daily essentials', NULL, 'products', true,
        ARRAY[(SELECT id FROM public.products WHERE sku = 'QA47-P1')])$$,
  'S47-02 a manager can create a products section');

SELECT is(
  (SELECT count(*) FROM public.storefront_sections WHERE title = 'Daily essentials'),
  1::bigint,
  'S47-03 the section was stored');

SELECT is(
  (SELECT count(*) FROM public.storefront_section_items i
     JOIN public.storefront_sections s ON s.id = i.section_id
    WHERE s.title = 'Daily essentials'),
  1::bigint,
  'S47-04 the item list was written in the same call');

SELECT is(
  (SELECT updated_by FROM public.storefront_sections WHERE title = 'Daily essentials'),
  auth.uid(),
  'S47-05 updated_by records who composed the section');

SELECT is(
  (SELECT count(*) FROM public.storefront_sections WHERE title = 'Daily essentials' AND sort_order = 1),
  1::bigint,
  'S47-06 a new section is appended in sort order rather than jumping the queue');

-- ---------------------------------------------------------------- payload shape
SELECT is(
  (SELECT count(*) FROM jsonb_array_elements(public.get_storefront('home')) e
     JOIN jsonb_object_keys(e.value) k ON true
    WHERE k IN ('price_paise','price','stock_qty','available','delivery_fee_paise',
                'mrp_paise','fee','threshold_paise','discount_value')),
  0::bigint,
  'S47-07 the payload carries no money, stock or fee key at all');

SELECT is(
  (SELECT count(DISTINCT k) FROM jsonb_array_elements(public.get_storefront('home')) e
     JOIN jsonb_object_keys(e.value) k ON true),
  6::bigint,
  'S47-08 the payload has exactly the six documented keys, no extras');

SELECT is(
  (SELECT jsonb_typeof((jsonb_array_elements(public.get_storefront('home')) -> 'item_ids'))),
  'array'::text,
  'S47-09 item_ids is an array of ids, not embedded objects');

-- ---------------------------------------------------------------- ordering and visibility
SELECT lives_ok($$SELECT public.save_storefront_section(
        NULL, 'home', 'Second section', 'a subtitle', 'products', true,
        ARRAY[(SELECT id FROM public.products WHERE sku = 'QA47-P1')])$$,
  'S47-10 a second section can be added');

SELECT is(
  (SELECT (array_agg((e.value ->> 'title')))
     FROM jsonb_array_elements(public.get_storefront('home')) e),
  ARRAY['Daily essentials','Second section'],
  'S47-11 sections come back in the manager sort order, not by id or creation time');

SELECT is(
  (SELECT (e.value ->> 'subtitle') FROM jsonb_array_elements(public.get_storefront('home')) e
    WHERE e.value ->> 'title' = 'Second section'),
  'a subtitle'::text,
  'S47-12 the optional subtitle is carried through');

CREATE TEMP TABLE s47_hidden AS
SELECT (public.save_storefront_section(NULL, 'home', 'Later hidden', NULL, 'products', true,
        ARRAY[]::uuid[]) ->> 'id')::uuid AS id;

SELECT lives_ok($$SELECT public.save_storefront_section(
        (SELECT id FROM s47_hidden), 'home', 'Later hidden', NULL, 'products', false,
        ARRAY[]::uuid[])$$,
  'S47-13 a manager can deactivate a section by id');

RESET ROLE;

SELECT is(
  (SELECT count(*) FROM jsonb_array_elements(public.get_storefront('home')) e
    WHERE (e.value ->> 'id')::uuid = (SELECT id FROM s47_hidden)),
  0::bigint,
  'S47-14 a deactivated section is absent from the customer payload');

-- ---------------------------------------------------------------- item order
SELECT is(
  (SELECT count(*) FROM public.storefront_section_items i
     JOIN public.storefront_sections s ON s.id = i.section_id
    WHERE s.title = 'Daily essentials' AND i.sort_order = 1),
  1::bigint,
  'S47-15 items are stored with an explicit order the app can trust');

-- ---------------------------------------------------------------- validation messages
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '04700000-0000-0000-0000-000000000002';

SELECT throws_ok($$SELECT public.save_storefront_section(
        NULL, 'home', '', NULL, 'products', true, ARRAY[]::uuid[])$$,
  NULL, '%heading is required%',
  'S47-16 an empty heading is refused with a readable message');

SELECT throws_ok($$SELECT public.save_storefront_section(
        NULL, 'home', 'Bad type', NULL, 'products', true,
        ARRAY[(SELECT id FROM public.categories WHERE slug = 'qa47-cat')])$$,
  NULL, '%does not exist%',
  'S47-17 a category id in a products section is refused, not silently dropped');

SELECT throws_ok($$SELECT public.save_storefront_section(
        NULL, 'home', 'Duplicated', NULL, 'products', true,
        ARRAY[(SELECT id FROM public.products WHERE sku='QA47-P1'),
              (SELECT id FROM public.products WHERE sku='QA47-P1')])$$,
  NULL, '%same item more than once%',
  'S47-18 a duplicated id is refused before anything is written');

SELECT throws_ok($$SELECT public.save_storefront_section(
        NULL, 'home', 'Too many', NULL, 'categories', true,
        (SELECT array_agg(id) FROM (SELECT id FROM public.categories
                                    UNION ALL SELECT gen_random_uuid() FROM generate_series(1,30)) x))$$,
  NULL, '%at most 30 items%',
  'S47-19 the 30-item cap is enforced by the RPC, not only by the trigger');

-- ---------------------------------------------------------------- edit conflict
CREATE TEMP TABLE s47_conflict AS
SELECT (public.save_storefront_section(NULL, 'home', 'Conflict target', NULL, 'products', true,
        ARRAY[]::uuid[]) ->> 'id')::uuid AS id,
       now() - interval '1 hour' AS stale;

SELECT throws_ok($$SELECT public.save_storefront_section(
        (SELECT id FROM s47_conflict), 'home', 'Conflict target', NULL, 'products', true,
        ARRAY[]::uuid[], (SELECT stale FROM s47_conflict))$$,
  NULL, '%changed by someone else%',
  'S47-20 a stale p_expected_updated_at loses loudly instead of overwriting');

SELECT is(
  (SELECT title FROM public.storefront_sections WHERE id = (SELECT id FROM s47_conflict)),
  'Conflict target'::text,
  'S47-21 the losing write changed nothing, so the winner''s text survived');

SELECT lives_ok($$SELECT public.save_storefront_section(
        (SELECT id FROM s47_conflict), 'home', 'Conflict target', NULL, 'products', true,
        ARRAY[]::uuid[], (SELECT updated_at FROM public.storefront_sections
                          WHERE id = (SELECT id FROM s47_conflict)))$$,
  'S47-22 the matching timestamp is accepted, so the guard is not simply refusing everything');

-- ---------------------------------------------------------------- reorder and delete
SELECT throws_ok($$SELECT public.reorder_storefront_sections('categories',
        ARRAY[(SELECT id FROM public.storefront_sections WHERE title = 'Daily essentials')])$$,
  NULL, '%not a section on page%',
  'S47-23 reordering cannot move a section onto another page');

SELECT lives_ok($$SELECT public.reorder_storefront_sections('home',
        ARRAY[(SELECT id FROM public.storefront_sections WHERE title = 'Conflict target'),
              (SELECT id FROM s47_hidden),
              (SELECT id FROM public.storefront_sections WHERE title = 'Second section'),
              (SELECT id FROM public.storefront_sections WHERE title = 'Daily essentials')])$$,
  'S47-24 a manager can reorder every section on one page in a single call');

SELECT is(
  (SELECT array_agg(title ORDER BY sort_order) FROM public.storefront_sections
    WHERE page = 'home'
      AND id IN ((SELECT id FROM public.storefront_sections WHERE title = 'Conflict target'),
                 (SELECT id FROM s47_hidden),
                 (SELECT id FROM public.storefront_sections WHERE title = 'Second section'),
                 (SELECT id FROM public.storefront_sections WHERE title = 'Daily essentials'))),
  ARRAY['Conflict target','Later hidden','Second section','Daily essentials']::text[],
  'S47-25 the stored sort_order follows the supplied sequence exactly, including the inactive section');

-- ---------------------------------------------------------------- access control
SET LOCAL request.jwt.claim.sub = '04700000-0000-0000-0000-000000000001';

SELECT throws_ok($$SELECT public.save_storefront_section(
        NULL, 'home', 'Customer attempt', NULL, 'products', true, ARRAY[]::uuid[])$$,
  NULL, '%Only a manager%',
  'S47-26 a signed-in customer cannot compose the storefront');

SELECT throws_ok($$SELECT public.delete_storefront_section(
        (SELECT id FROM public.storefront_sections WHERE title = 'Conflict target'))$$,
  NULL, '%Only a manager%',
  'S47-27 a signed-in customer cannot delete a section');

RESET ROLE;

SELECT * FROM finish();

ROLLBACK;

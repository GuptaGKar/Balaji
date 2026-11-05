-- pgTAP 050: list_storefront_sections - the editor read has to show hidden sections.
--
-- The defect this suite exists for: the Storefront page loaded through get_storefront, which
-- filters active = true, so pressing "Visible" once removed a section from the dashboard for
-- good. The editor read must therefore return inactive rows to a manager - and must not become a
-- way for anyone else to see rows they were never allowed to read.
--
-- It also pins the grant. Supabase creates functions with EXECUTE already granted to PUBLIC and
-- anon, so `GRANT ... TO authenticated` alone leaves anon able to call it; 20261105000004 revokes
-- that explicitly, and S50-11 is the assertion that would fail if the revoke were dropped.
--
-- Self-contained: creates pgTAP and fixtures inside this transaction, which the trailing
-- ROLLBACK undoes entirely.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgtap WITH SCHEMA extensions;
SET LOCAL search_path = public, extensions;

DO $boot$
BEGIN
  IF to_regprocedure('public.list_storefront_sections(text)') IS NULL THEN
    RAISE EXCEPTION 'Migration 20261105000004_storefront_editor_read.sql has not been applied. Apply it before running suite 050.';
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

SELECT plan(12);

-- One visible and one hidden section, each holding the same fixture product, so the editor read
-- can be compared against the customer read on identical data.
CREATE TEMP TABLE q50 AS
WITH inserted AS (
    INSERT INTO public.storefront_sections (page, title, section_type, sort_order, active)
        VALUES ('home', 'QA50 Visible', 'products', 1, true),
               ('home', 'QA50 Hidden',  'products', 2, false)
        RETURNING id, title
)
SELECT (SELECT id FROM inserted WHERE title = 'QA50 Visible')  AS visible_id,
       (SELECT id FROM inserted WHERE title = 'QA50 Hidden')   AS hidden_id,
       (SELECT id FROM public.products ORDER BY sku LIMIT 1)   AS product;

GRANT SELECT ON q50 TO PUBLIC;

INSERT INTO public.storefront_section_items (section_id, product_id, sort_order)
SELECT (SELECT visible_id FROM q50), (SELECT product FROM q50), 1
UNION ALL
SELECT (SELECT hidden_id FROM q50), (SELECT product FROM q50), 1;

SELECT pg_temp.make_profile('05000000-0000-0000-0000-000000000001', 'manager',  'QA50 Manager');
SELECT pg_temp.make_profile('05000000-0000-0000-0000-000000000002', 'customer', 'QA50 Customer');

-- ---------------------------------------------------------------- shape of the function
SELECT is(
  (SELECT p.prosecdef FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public' AND p.proname = 'list_storefront_sections'),
  false,
  'S50-01 the editor read is SECURITY INVOKER, so RLS and not the function is the authority');

SELECT is(
  (SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public' AND p.proname = 'list_storefront_sections'),
  1::bigint,
  'S50-02 exactly one signature, so PostgREST never has to guess');

-- ---------------------------------------------------------------- the manager sees everything
-- Every assertion is scoped to the two fixture ids rather than a global count: this suite is run
-- against CI's empty database AND against the live project, which already holds composed sections,
-- and a bare count would mean two different things in the two places.
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '05000000-0000-0000-0000-000000000001';

SELECT is(
  (SELECT count(*) FROM jsonb_array_elements(public.list_storefront_sections('home')) e
     WHERE e.value ->> 'id' IN ((SELECT visible_id FROM q50)::text, (SELECT hidden_id FROM q50)::text)),
  2::bigint,
  'S50-03 a manager sees the hidden section as well as the visible one');

SELECT is(
  (SELECT count(*) FROM jsonb_array_elements(public.list_storefront_sections('home')) e
     WHERE (e.value ->> 'title') = 'QA50 Hidden' AND (e.value ->> 'active') = 'false'),
  1::bigint,
  'S50-04 the hidden row is labelled inactive rather than dropped');

SELECT is(
  (SELECT (e.value ->> 'id') FROM jsonb_array_elements(public.list_storefront_sections('home')) e
     WHERE e.value ->> 'id' IN ((SELECT visible_id FROM q50)::text, (SELECT hidden_id FROM q50)::text)
     ORDER BY (e.value ->> 'sort_order')::int LIMIT 1),
  (SELECT visible_id::text FROM q50),
  'S50-05 rows come back in sort_order so the editor can show the real order');

SELECT is(
  (SELECT count(*) FROM jsonb_array_elements(public.list_storefront_sections('home')) e
     WHERE e.value ->> 'id' IN ((SELECT visible_id FROM q50)::text, (SELECT hidden_id FROM q50)::text)
       AND jsonb_array_length(e.value -> 'item_ids') = 1),
  2::bigint,
  'S50-06 a hidden section keeps its chosen items, so re-showing it restores the shelf');

SELECT is(
  (SELECT count(*) FROM jsonb_array_elements(public.list_storefront_sections('home')) e
     WHERE e.value ? ANY (ARRAY['price_paise','stock_qty','available','name'])),
  0::bigint,
  'S50-07 the editor read carries ids only, never money or stock');

RESET ROLE;

-- ---------------------------------------------------------------- nobody else learns anything
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '05000000-0000-0000-0000-000000000002';

SELECT is(
  (SELECT count(*) FROM jsonb_array_elements(public.list_storefront_sections('home')) e
     WHERE e.value ->> 'id' IN ((SELECT visible_id FROM q50)::text, (SELECT hidden_id FROM q50)::text)),
  (SELECT count(*) FROM jsonb_array_elements(public.get_storefront('home')) e
     WHERE e.value ->> 'id' IN ((SELECT visible_id FROM q50)::text, (SELECT hidden_id FROM q50)::text)),
  'S50-08 a signed-in customer sees no more through the editor read than through the customer read');

SELECT is(
  (SELECT count(*) FROM jsonb_array_elements(public.list_storefront_sections('home')) e
     WHERE (e.value ->> 'title') = 'QA50 Hidden'),
  0::bigint,
  'S50-09 and specifically not the hidden section');

RESET ROLE;

SET LOCAL ROLE anon;
SELECT throws_ok(
  $$SELECT public.list_storefront_sections('home')$$,
  '42501', NULL,
  'S50-10 anon cannot execute the editor read at all');
RESET ROLE;

SELECT is(
  has_function_privilege('anon', 'public.list_storefront_sections(text)', 'EXECUTE'),
  false,
  'S50-11 the Supabase default grant to anon was revoked, not merely left unmentioned');

SELECT is(
  has_function_privilege('authenticated', 'public.list_storefront_sections(text)', 'EXECUTE'),
  true,
  'S50-12 while the dashboard caller keeps it, so this is a narrowing and not an accident');

SELECT * FROM finish();

ROLLBACK;

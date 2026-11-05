-- pgTAP 049: only an authenticated principal may execute the storefront write RPCs.
--
-- 20261105000003 exists because Supabase creates functions with EXECUTE already granted to
-- PUBLIC and to anon, so the explicit "GRANT ... TO authenticated" in 20261105000001 narrowed
-- nobody. This suite is the proof that the surface is now what it was meant to be - and, just
-- as importantly, that closing it did not lock out the callers who are supposed to work:
-- managers saving a section, and anonymous shoppers reading one.
--
-- Two boundaries are pinned separately on purpose. A non-manager that is signed in reaches the
-- function body and gets the readable P0001 guard; an anonymous caller does not reach the body
-- at all and gets 42501. Those are different layers, and collapsing them would hide a revoke
-- that silently widened the surface again.
--
-- Self-contained: creates pgTAP and fixtures inside this transaction, which the trailing
-- ROLLBACK undoes entirely.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgtap WITH SCHEMA extensions;
SET LOCAL search_path = public, extensions;

DO $boot$
BEGIN
  IF to_regprocedure('public.save_storefront_section(uuid, text, text, text, text, boolean, uuid[], timestamp with time zone)') IS NULL
     OR to_regprocedure('public.set_product_categories(uuid, uuid, uuid[])') IS NULL THEN
    RAISE EXCEPTION 'Migrations 20261105000001 / 20261105000002 have not been applied. Apply them before running suite 049.';
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

SELECT plan(22);

SELECT pg_temp.make_profile('04900000-0000-0000-0000-000000000001', 'manager',  'QA49 Manager');
SELECT pg_temp.make_profile('04900000-0000-0000-0000-000000000002', 'customer', 'QA49 Customer');

-- ---------------------------------------------------------------- the granted surface
SELECT is(
  has_function_privilege('anon',
    'public.save_storefront_section(uuid, text, text, text, text, boolean, uuid[], timestamp with time zone)',
    'EXECUTE'),
  false,
  'S49-01 anon cannot execute a section save at all');
SELECT is(
  has_function_privilege('anon', 'public.reorder_storefront_sections(text, uuid[])', 'EXECUTE'),
  false,
  'S49-02 anon cannot execute a reorder');
SELECT is(
  has_function_privilege('anon', 'public.delete_storefront_section(uuid)', 'EXECUTE'),
  false,
  'S49-03 anon cannot execute a delete');
SELECT is(
  has_function_privilege('anon', 'public.set_product_categories(uuid, uuid, uuid[])', 'EXECUTE'),
  false,
  'S49-04 anon cannot execute a category reassignment');

-- The read path is deliberately still open to anon: a signed-out shopper renders Home.
SELECT is(
  has_function_privilege('anon', 'public.get_storefront(text)', 'EXECUTE'),
  true,
  'S49-05 the storefront read stays executable by anon, so the customer app is not broken');

SELECT is(
  has_function_privilege('authenticated',
    'public.save_storefront_section(uuid, text, text, text, text, boolean, uuid[], timestamp with time zone)',
    'EXECUTE'),
  true,
  'S49-06 a signed-in caller still reaches the section save - RLS and the role guard decide');
SELECT is(
  has_function_privilege('authenticated', 'public.reorder_storefront_sections(text, uuid[])', 'EXECUTE'),
  true,
  'S49-07 a signed-in caller still reaches the reorder');
SELECT is(
  has_function_privilege('authenticated', 'public.delete_storefront_section(uuid)', 'EXECUTE'),
  true,
  'S49-08 a signed-in caller still reaches the delete');
SELECT is(
  has_function_privilege('authenticated', 'public.set_product_categories(uuid, uuid, uuid[])', 'EXECUTE'),
  true,
  'S49-09 a signed-in caller still reaches the category reassignment');

-- service_role is the dashboard's server-side client. Revoking it would break manager saves
-- rather than protect anything, so it is asserted rather than assumed.
SELECT is(
  has_function_privilege('service_role',
    'public.save_storefront_section(uuid, text, text, text, text, boolean, uuid[], timestamp with time zone)',
    'EXECUTE'),
  true,
  'S49-10 service_role keeps EXECUTE on the section save for the dashboard server client');
SELECT is(
  has_function_privilege('service_role', 'public.reorder_storefront_sections(text, uuid[])', 'EXECUTE'),
  true,
  'S49-11 service_role keeps EXECUTE on the reorder');
SELECT is(
  has_function_privilege('service_role', 'public.delete_storefront_section(uuid)', 'EXECUTE'),
  true,
  'S49-12 service_role keeps EXECUTE on the delete');
SELECT is(
  has_function_privilege('service_role', 'public.set_product_categories(uuid, uuid, uuid[])', 'EXECUTE'),
  true,
  'S49-13 service_role keeps EXECUTE on the category reassignment');

-- ---------------------------------------------------------------- behaviour, from the outside
-- An anonymous caller must not reach the body, so the error is the privilege one (42501) and
-- not the guard message. That distinction is what these four assertions are for.
SET LOCAL ROLE anon;
SELECT throws_ok($$SELECT public.save_storefront_section(NULL, 'home', 'Anon section', NULL,
        'products', true, ARRAY[]::uuid[])$$,
  '42501', NULL,
  'S49-14 an anonymous section save is refused before the body runs');
SELECT throws_ok($$SELECT public.reorder_storefront_sections('home', ARRAY[]::uuid[])$$,
  '42501', NULL,
  'S49-15 an anonymous reorder is refused before the body runs');
SELECT throws_ok($$SELECT public.delete_storefront_section(gen_random_uuid())$$,
  '42501', NULL,
  'S49-16 an anonymous delete is refused before the body runs');
SELECT throws_ok($$SELECT public.set_product_categories(gen_random_uuid(), gen_random_uuid(),
        ARRAY[]::uuid[])$$,
  '42501', NULL,
  'S49-17 an anonymous category reassignment is refused before the body runs');

SELECT lives_ok($$SELECT public.get_storefront('home')$$,
  'S49-18 the anonymous read still works, refused writes have not narrowed it');
SELECT is(public.get_storefront('home'), '[]'::jsonb,
  'S49-19 and it still answers with the empty storefront, not an error');
RESET ROLE;

-- ---------------------------------------------------------------- legitimate callers still work
-- A manager must survive the revoke. This is the assertion that turns "the surface is
-- narrower" into "the surface is the intended one".
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '04900000-0000-0000-0000-000000000001';
SELECT lives_ok($$SELECT public.save_storefront_section(NULL, 'home', 'QA49 Manager section', NULL,
        'products', true, ARRAY[]::uuid[])$$,
  'S49-20 a manager can still compose a section after the anonymous revoke');
SELECT is(
  (SELECT count(*) FROM public.storefront_sections WHERE title = 'QA49 Manager section'),
  1::bigint,
  'S49-21 and that save really wrote the row, so the revoke did not quietly no-op it');

-- A signed-in non-manager reaches the body and gets the readable guard, not a bare permission
-- error. If this ever returns 42501 instead, the authenticated grant has gone and managers are
-- about to lose their dashboard.
SET LOCAL request.jwt.claim.sub = '04900000-0000-0000-0000-000000000002';
SELECT throws_ok($$SELECT public.save_storefront_section(NULL, 'home', 'QA49 Customer section', NULL,
        'products', true, ARRAY[]::uuid[])$$,
  'P0001', 'Only a manager can change the storefront layout.',
  'S49-22 a signed-in customer is stopped by the readable manager guard, not by a missing grant');

SELECT * FROM finish();

ROLLBACK;

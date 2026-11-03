-- pgTAP 042: manager zone authoring (save_delivery_zone) and the coverage overview.
-- Contract under test: only a manager may draw an area or read the amounts, a boundary is
-- only ever accepted as a real closed polygon, and none of it changes the PIN-code gate.

BEGIN;

-- pgTAP is not installed on this project, so `plan()` does not exist until it is created.
-- It is created inside this transaction and therefore disappears again with the trailing
-- ROLLBACK, leaving the database exactly as it was. The search_path is widened so the
-- assertions work whether pgTAP lands in "extensions" or in "public".
CREATE EXTENSION IF NOT EXISTS pgtap WITH SCHEMA extensions;
SET LOCAL search_path = public, extensions;

SELECT plan(22);

-- The fixture helper is inlined rather than pulled in with `\ir`, because `\ir` is a psql
-- meta-command: it works under `supabase test db` but fails with a syntax error in the
-- Supabase SQL editor and via `supabase db query`. Same body as
-- supabase/tests/helpers/role_fixtures.inc, created inside this rollback transaction.
CREATE FUNCTION pg_temp.make_profile(p_id uuid, p_role public.user_role, p_name text)
RETURNS void LANGUAGE plpgsql SET search_path = '' AS $fn$
BEGIN
  INSERT INTO auth.users (id, email, aud, role)
  VALUES (p_id, 'fixture-' || p_id::text || '@test.invalid', 'authenticated', 'authenticated');
  UPDATE public.profiles
     SET role = p_role,
         display_name = p_name,
         is_active = true,
         must_change_password = false,
         login_id = CASE WHEN p_role = 'delivery'
                         THEN 'QA-' || upper(replace(p_id::text, '-', ''))
                         ELSE NULL END
   WHERE id = p_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Signup trigger did not create the fixture profile';
  END IF;
END;
$fn$;

SELECT pg_temp.make_profile('04200000-0000-0000-0000-000000000001', 'manager', 'Zone Manager');
SELECT pg_temp.make_profile('04200000-0000-0000-0000-000000000002', 'customer', 'Zone Customer');

DELETE FROM public.service_areas WHERE value IN ('QA-ZONE-1', 'QA-ZONE-2', '247233');

-- Saved addresses are inserted while still the table owner: a manager may not write
-- another customer's address book, and these rows are fixtures, not a test of that rule.
-- One falls inside the square drawn below (12.9 N, 77.5 E), one does not (13.9 N, 78.5 E).
INSERT INTO public.addresses (customer_id, label, recipient_name, phone, address_lines,
                              city, state, postal_code, lat, lng)
VALUES ('04200000-0000-0000-0000-000000000002', 'Home', 'Zone Customer', '9876543210',
        '1 Inside Street', 'Test', 'Test', 'QA-ZONE-1', 12.9, 77.5),
       ('04200000-0000-0000-0000-000000000002', 'Work', 'Zone Customer', '9876543210',
        '1 Outside Street', 'Test', 'Test', '999999', 13.9, 78.5);

-- A square around 12.9 N, 77.5 E and a point inside it.
--   inside point = 12.9 N, 77.5 E
UPDATE public.store_settings
SET delivery_fee_paise = 3000,
    delivery_charge_threshold_paise = 25000,
    free_delivery_threshold_paise = NULL,
    mid_tier_fee_paise = NULL;

-- ─── Shape ───────────────────────────────────────────────────────────────────
SELECT has_function('public', 'save_delivery_zone',
  array['uuid','text','text','text','integer','text','bigint','bigint','bigint','boolean','boolean'],
  'Z42-1 save_delivery_zone exists with the advertised signature');
-- The empty argument list has to be typed or Postgres cannot infer it.
SELECT has_function('public', 'get_delivery_zone_coverage', array[]::text[],
  'Z42-2 coverage function exists');

-- ─── Manager-only, and anon cannot reach either ──────────────────────────────
SET LOCAL ROLE anon;
SELECT throws_ok(
  $q$SELECT public.save_delivery_zone(p_value => 'QA-ZONE-1')$q$, '42501', NULL,
  'Z42-3 anon cannot author a delivery area');
SELECT throws_ok(
  $q$SELECT public.get_delivery_zone_coverage()$q$, '42501', NULL,
  'Z42-4 anon cannot read the coverage amounts');
RESET ROLE;

SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claims = '{"sub":"04200000-0000-0000-0000-000000000002","role":"authenticated"}';
SET LOCAL request.jwt.claim.sub = '04200000-0000-0000-0000-000000000002';

SELECT is(
  (SELECT public.save_delivery_zone(p_value => 'QA-ZONE-1')->>'error'), 'FORBIDDEN',
  'Z42-5 a customer is refused, even though the function bypasses RLS');
SELECT is(
  (SELECT count(*) FROM public.service_areas WHERE value = 'QA-ZONE-1'), 0::bigint,
  'Z42-6 the refused write stored nothing');

-- ─── Manager path: validation first ─────────────────────────────────────────
SET LOCAL request.jwt.claims = '{"sub":"04200000-0000-0000-0000-000000000001","role":"authenticated"}';
SET LOCAL request.jwt.claim.sub = '04200000-0000-0000-0000-000000000001';

SELECT is((SELECT public.save_delivery_zone(p_value => '  ')->>'error'), 'VALIDATION_FAILED',
  'Z42-7 a blank PIN or area name is rejected');
SELECT is((SELECT public.save_delivery_zone(p_value => 'QA-ZONE-1', p_kind => 'district')->>'error'),
  'VALIDATION_FAILED', 'Z42-8 an unknown area type is rejected');
SELECT is((SELECT public.save_delivery_zone(p_value => 'QA-ZONE-1', p_priority => 0)->>'error'),
  'VALIDATION_FAILED', 'Z42-9 priority must be within range');
SELECT is((SELECT public.save_delivery_zone(p_value => 'QA-ZONE-1', p_delivery_fee_paise => -1)->>'error'),
  'VALIDATION_FAILED', 'Z42-10 a negative fee is rejected before it can hit a CHECK');
SELECT is((SELECT public.save_delivery_zone(p_value => 'QA-ZONE-1', p_geojson => 'not json')->>'error'),
  'INVALID_BOUNDARY', 'Z42-11 malformed GeoJSON is refused, not crashed on');
SELECT is((SELECT public.save_delivery_zone(
    p_value => 'QA-ZONE-1',
    p_geojson => '{"type":"Point","coordinates":[77.5,12.9]}'
  )->>'error'), 'INVALID_BOUNDARY', 'Z42-12 a point is not an area');

-- ─── A real polygon is accepted and reported ────────────────────────────────
SELECT (public.save_delivery_zone(
    p_kind => 'neighbourhood',
    p_value => 'QA-ZONE-1',
    p_name => 'QA inner belt',
    p_priority => 10,
    p_geojson => '{"type":"Polygon","coordinates":[[[77.0,12.5],[78.0,12.5],[78.0,13.5],[77.0,13.5],[77.0,12.5]]]}',
    p_delivery_fee_paise => 700
  ));

SELECT is((SELECT count(*) FROM public.service_areas WHERE value = 'QA-ZONE-1' AND boundary IS NOT NULL),
  1::bigint, 'Z42-13 the drawn area was stored with a boundary');
SELECT is((SELECT delivery_fee_paise FROM public.service_areas WHERE value = 'QA-ZONE-1'),
  700::bigint, 'Z42-14 the per-area fee was stored in paise');

-- Editing without redrawn geometry must keep the existing shape.
SELECT is(
  (SELECT (public.save_delivery_zone(
      p_id => (SELECT id FROM public.service_areas WHERE value = 'QA-ZONE-1'),
      p_kind => 'neighbourhood',
      p_value => 'QA-ZONE-1',
      p_name => 'QA inner belt renamed',
      p_priority => 20,
      p_delivery_fee_paise => 800
  ))->>'has_boundary'), 'true',
  'Z42-15 saving an area without new geometry keeps its boundary');
SELECT is((SELECT (boundary IS NOT NULL)::text FROM public.service_areas
            WHERE value = 'QA-ZONE-1'), 'true',
  'Z42-16 the stored boundary really survived the edit');

-- A duplicate (kind, value) must come back as a readable message, not a raw 23505.
SELECT is((SELECT public.save_delivery_zone(p_value => 'QA-ZONE-1', p_kind => 'neighbourhood')->>'error'),
  'VALIDATION_FAILED', 'Z42-17 a duplicate area name is reported kindly');

-- ─── Coverage overview ──────────────────────────────────────────────────────
-- jsonb_array_elements names its output column "value", which would collide with the
-- row key of the same name, so the element column is aliased explicitly everywhere.
SELECT is(
  (SELECT count(*)
     FROM jsonb_array_elements(public.get_delivery_zone_coverage()) AS item(elem)
    WHERE item.elem ->> 'value' = 'QA-ZONE-1'
      AND (item.elem ->> 'addresses_inside')::int = 1),
  1::bigint, 'Z42-18 coverage counts only the addresses actually inside the shape');
SELECT is(
  (SELECT (item.elem ->> 'delivery_fee_paise')
     FROM jsonb_array_elements(public.get_delivery_zone_coverage()) AS item(elem)
    WHERE item.elem ->> 'value' = 'QA-ZONE-1'
    LIMIT 1),
  '800', 'Z42-19 coverage exposes the current per-area fee to the manager');
SELECT is(
  (SELECT count(*)
     FROM jsonb_array_elements(public.get_delivery_zone_coverage()) AS item(elem)
    WHERE jsonb_typeof(item.elem -> 'geojson') <> 'object'),
  0::bigint, 'Z42-20 every coverage row carries a drawable geometry');
SELECT is(
  (SELECT count(*)
     FROM jsonb_array_elements(public.get_delivery_zone_coverage()) AS item(elem)
    WHERE item.elem ->> 'value' <> 'QA-ZONE-1'),
  0::bigint, 'Z42-21 areas without a boundary are left out of the map feed');

RESET ROLE;

-- ─── The gate is still the PIN, not the shape ────────────────────────────────
SELECT is(
  (SELECT fee_paise FROM public.compute_delivery_fee_for_pin(10000, 'QA-ZONE-1', 12.9, 77.5)),
  800::bigint,
  'Z42-22 the drawn area prices a spot inside it, exactly as the order path will');

SELECT * FROM finish();
ROLLBACK;

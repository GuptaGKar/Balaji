-- pgTAP 043: the coverage list must show EVERY area, drawn or not (20261103000002).
--
-- Reported by the owner: an area added in the admin panel did not appear under
-- "Drawn areas and what they cover". The old function filtered rows to `boundary IS NOT NULL`,
-- so a shapeless area vanished with no explanation. These assertions pin the new contract and,
-- just as importantly, pin the honest consequence: a row that has neither a shape nor PIN
-- semantics still cannot price anything, and listing it must not pretend otherwise.
--
-- Self-contained on purpose: it creates pgTAP inside its own transaction and inlines the
-- profile fixture, so it runs in the Supabase SQL editor and via `supabase db query` as well as
-- under `supabase test db`. Everything, including pgTAP, is undone by the trailing ROLLBACK.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgtap WITH SCHEMA extensions;
SET LOCAL search_path = public, extensions;

SELECT plan(16);

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

SELECT pg_temp.make_profile('04300000-0000-0000-0000-000000000001', 'manager',   'Coverage Manager');
SELECT pg_temp.make_profile('04300000-0000-0000-0000-000000000002', 'customer',  'Coverage Customer');

DELETE FROM public.service_areas
 WHERE value IN ('QA-COV-DRAWN', 'QA-COV-SHAPELESS', '247233');

UPDATE public.store_settings
   SET delivery_fee_paise = 3000,
       delivery_charge_threshold_paise = 25000,
       free_delivery_threshold_paise = NULL,
       mid_tier_fee_paise = NULL;

-- Three areas: one drawn square, one shapeless neighbourhood (the row the owner added), and one
-- shapeless PIN rule. The square wraps 12.9 N, 77.5 E.
INSERT INTO public.service_areas (kind, value, name, priority, active, delivery_fee_paise, boundary)
VALUES
  ('neighbourhood', 'QA-COV-DRAWN', 'Drawn square', 10, true, 5000,
   extensions.ST_SetSRID(extensions.ST_MakePolygon(
     extensions.ST_MakeLine(ARRAY[
       extensions.ST_MakePoint(77.4, 12.8), extensions.ST_MakePoint(77.6, 12.8),
       extensions.ST_MakePoint(77.6, 13.0), extensions.ST_MakePoint(77.4, 13.0),
       extensions.ST_MakePoint(77.4, 12.8)])), 4326)),
  ('neighbourhood', 'QA-COV-SHAPELESS', 'Shapeless area', 20, true, 4000, NULL),
  ('pincode',       '247233',         'Shapeless PIN',  30, true, NULL,  NULL);

-- Manager session, which is the only role allowed to read this list.
SET LOCAL request.jwt.claims = '{"sub":"04300000-0000-0000-0000-000000000001","role":"authenticated"}';
SET LOCAL request.jwt.claim.sub = '04300000-0000-0000-0000-000000000001';

-- ─── Nothing is hidden any more ──────────────────────────────────────────────
SELECT is(jsonb_array_length(public.get_delivery_zone_coverage())::int,
          (SELECT count(*)::int FROM public.service_areas),
  'Z43-01 the list is exactly as long as the table, so no row is filtered out');

SELECT is(public.get_delivery_zone_coverage()::text LIKE '%QA-COV-SHAPELESS%', true,
  'Z43-02 the shapeless neighbourhood that used to disappear is now in the list');

SELECT is(count(*)::int, 3::int,
  'Z43-03 all three fixture areas appear, whichever else the live table holds')
  FROM jsonb_array_elements(public.get_delivery_zone_coverage()) AS item(elem)
 WHERE item.elem ->> 'value' IN ('QA-COV-DRAWN', 'QA-COV-SHAPELESS', '247233');

-- ─── has_boundary says what the row actually is ─────────────────────────────
SELECT is(bool_or((item.elem ->> 'has_boundary')::text = 'true'), true,
  'Z43-04 every row carries a has_boundary flag')
  FROM jsonb_array_elements(public.get_delivery_zone_coverage()) AS item(elem);

SELECT is((item.elem ->> 'has_boundary')::text, 'true',
  'Z43-05 the drawn square reports has_boundary = true')
  FROM jsonb_array_elements(public.get_delivery_zone_coverage()) AS item(elem)
 WHERE item.elem ->> 'value' = 'QA-COV-DRAWN';

SELECT is((item.elem ->> 'has_boundary')::text, 'false',
  'Z43-06 the shapeless neighbourhood reports has_boundary = false')
  FROM jsonb_array_elements(public.get_delivery_zone_coverage()) AS item(elem)
 WHERE item.elem ->> 'value' = 'QA-COV-SHAPELESS';

SELECT is((item.elem -> 'geojson')::text, 'null',
  'Z43-07 a shapeless row still exposes a null geojson, so the map paints nothing for it')
  FROM jsonb_array_elements(public.get_delivery_zone_coverage()) AS item(elem)
 WHERE item.elem ->> 'value' = 'QA-COV-SHAPELESS';

SELECT is((item.elem ->> 'addresses_inside')::text, '0',
  'Z43-08 a shapeless row counts no addresses, rather than counting the whole table')
  FROM jsonb_array_elements(public.get_delivery_zone_coverage()) AS item(elem)
 WHERE item.elem ->> 'value' = 'QA-COV-SHAPELESS';

SELECT is((item.elem ->> 'overlapping_areas')::text, '0',
  'Z43-09 a shapeless row overlaps nothing, so the guard inside the subquery survived')
  FROM jsonb_array_elements(public.get_delivery_zone_coverage()) AS item(elem)
 WHERE item.elem ->> 'value' = 'QA-COV-SHAPELESS';

-- ─── Ordering is unchanged ──────────────────────────────────────────────────
SELECT is((SELECT (item.elem ->> 'priority')::int
             FROM jsonb_array_elements(public.get_delivery_zone_coverage())
                  WITH ORDINALITY AS item(elem, ord)
            ORDER BY item.ord LIMIT 1),
          (SELECT min(priority)::int FROM public.service_areas),
  'Z43-10 the first row is the lowest priority, so ordering is still by priority');

-- ─── The consequence stays honest: shapeless + not-a-PIN prices nothing ─────
SELECT is((SELECT value FROM public.resolve_delivery_zone(12.9, 77.5)), 'QA-COV-DRAWN',
  'Z43-11 a point inside the drawn square still resolves to that area');

SELECT is_empty($q$SELECT 1 FROM public.resolve_delivery_zone(13.9, 78.9)$q$,
  'Z43-12 a point outside every shape resolves to nothing');

SELECT is_empty($q$SELECT 1 FROM generate_series(-89, 89, 30) AS g(lat)
                  WHERE (SELECT value FROM public.resolve_delivery_zone(g.lat, 77.5)) = 'QA-COV-SHAPELESS'$q$,
  'Z43-13 the shapeless neighbourhood can never be the resolved zone, at any latitude');

-- A shapeless PIN row is still a working rule: it gates its own PIN.
SELECT is((public.check_delivery_availability('247233', NULL, NULL, 0) ->> 'pin_configured')::text, 'true',
  'Z43-14 a shapeless PIN rule still configures its PIN');

-- ─── Non-managers must still be refused, and refused in the same shape ─────
SET LOCAL request.jwt.claims = '{"sub":"04300000-0000-0000-0000-000000000002","role":"authenticated"}';
SET LOCAL request.jwt.claim.sub = '04300000-0000-0000-0000-000000000002';

SELECT is(jsonb_typeof(public.get_delivery_zone_coverage()), 'object',
  'Z43-15 a non-manager still gets the FORBIDDEN object, not the array');

SELECT has_function('public', 'get_delivery_zone_coverage', array[]::text[],
  'Z43-16 signature unchanged, so PostgREST still sees exactly one candidate');

SELECT * FROM finish();

ROLLBACK;

-- pgTAP 044: M4 autocomplete from our own data - search_area_suggestions(p_query, p_limit).
--
-- Contract under test, in the order it matters:
--   1. the private half is scoped to the caller and NOTHING leaks between customers;
--   2. a search is bounded (min characters, hard cap, no pattern wildcards from user input);
--   3. only active areas are offered, and no money is exposed through a public search;
--   4. ranking is deterministic: exact PIN first, then prefix, then substring.
--
-- Self-contained: creates pgTAP and the fixture helper inside this transaction, which the
-- trailing ROLLBACK undoes entirely, so it runs in the SQL editor, via `supabase db query`
-- and under `supabase test db` alike.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgtap WITH SCHEMA extensions;
SET LOCAL search_path = public, extensions;

-- Fail with instructions instead of a mid-file 42883 abort. This suite tests the function, it
-- does not create it: pasted on its own it used to die on a line deep inside, which reads like a
-- bug in the test rather than the ordering mistake it actually is.
DO $$
BEGIN
  IF to_regprocedure('public.search_area_suggestions(text, integer)') IS NULL THEN
    -- One literal: RAISE does not accept a concatenated message, and this used to fail with a
    -- 42601 that hid the very ordering problem it was meant to explain.
    RAISE EXCEPTION 'search_area_suggestions does not exist yet - apply supabase/migrations/20261103000003_area_suggestions.sql first, then run this suite.'
      USING ERRCODE = '42883', HINT = 'the migration defines the function; this file only tests it';
  END IF;
END;
$$;

SELECT plan(20);

CREATE FUNCTION pg_temp.make_profile(p_id uuid, p_role public.user_role, p_name text)
RETURNS void LANGUAGE plpgsql SET search_path = '' AS $fn$
BEGIN
  INSERT INTO auth.users (id, email, aud, role)
  VALUES (p_id, 'fixture-' || p_id::text || '@test.invalid', 'authenticated', 'authenticated');
  UPDATE public.profiles
     SET role = p_role, display_name = p_name, is_active = true, must_change_password = false,
         login_id = CASE WHEN p_role = 'delivery'
                         THEN 'QA-' || upper(replace(p_id::text, '-', '')) ELSE NULL END
   WHERE id = p_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Signup trigger did not create the fixture profile';
  END IF;
END;
$fn$;

SELECT pg_temp.make_profile('04400000-0000-0000-0000-000000000001', 'customer', 'Alpha Customer');
SELECT pg_temp.make_profile('04400000-0000-0000-0000-000000000002', 'customer', 'Beta Customer');

DELETE FROM public.addresses
 WHERE customer_id IN ('04400000-0000-0000-0000-000000000001'::uuid,
                       '04400000-0000-0000-0000-000000000002'::uuid);
DELETE FROM public.service_areas
 WHERE value IN ('569999', 'QA-SUG-PREFIX', 'QA-SUG-MIDDLE', 'QA-SUG-OFF');

-- Two areas that a shopper could type: one exact, one sharing a prefix, one matching only in
-- the middle, plus an INACTIVE area that must never be offered.
INSERT INTO public.service_areas (kind, value, name, priority, active, boundary)
VALUES
  ('pincode',       '569999',       'Suggestion PIN',   10, true,
   extensions.ST_SetSRID(extensions.ST_MakePolygon(extensions.ST_MakeLine(ARRAY[
     extensions.ST_MakePoint(77.40, 12.90), extensions.ST_MakePoint(77.60, 12.90),
     extensions.ST_MakePoint(77.60, 13.10), extensions.ST_MakePoint(77.40, 13.10),
     extensions.ST_MakePoint(77.40, 12.90)])), 4326)),
  ('neighbourhood', 'QA-SUG-PREFIX', 'Prefix Area',     20, true,  NULL),
  ('neighbourhood', 'QA-SUG-MIDDLE', 'Middle Area',     30, true,  NULL),
  ('neighbourhood', 'QA-SUG-OFF',    'Inactive Area',   40, false, NULL);

-- One saved address per customer, with a word only the owner may see.
INSERT INTO public.addresses (customer_id, label, recipient_name, phone, address_lines,
                             city, state, postal_code, is_default, lat, lng)
VALUES ('04400000-0000-0000-0000-000000000001', 'Alpha Home', 'Alpha Person', '9000000001',
        '1 Alpha Close', 'TestCity', 'TestState', '569999', true, 12.95, 77.45),
       ('04400000-0000-0000-0000-000000000002', 'Beta Secret', 'Beta Person', '9000000002',
        '2 Beta Close', 'TestCity', 'TestState', '569999', true, 12.96, 77.46);

-- Reads the first suggestion's field. jsonb has no [0] subscript in SQL, and spelling the
-- extraction out inline four times makes the assertions hard to read.
CREATE FUNCTION pg_temp.sugg_first(p_query text, p_key text, p_limit integer DEFAULT 8)
RETURNS text LANGUAGE sql STABLE AS $fn$
  SELECT item.elem ->> p_key
    FROM jsonb_array_elements(public.search_area_suggestions(p_query, p_limit))
         WITH ORDINALITY AS item(elem, ord)
   ORDER BY item.ord LIMIT 1
$fn$;

-- ─── Shape and guards ────────────────────────────────────────────────────────
SELECT has_function('public', 'search_area_suggestions', array['text', 'integer'],
  'S44-01 the suggestion function exists with the advertised signature');

SELECT is(jsonb_typeof(public.search_area_suggestions('qa')), 'array',
  'S44-02 it answers with a plain array');

SELECT is(jsonb_array_length(public.search_area_suggestions('')), 0::int,
  'S44-03 a blank query suggests nothing');

SELECT is(jsonb_array_length(public.search_area_suggestions('q')), 0::int,
  'S44-04 a single character is too short to search');

SELECT is(jsonb_array_length(public.search_area_suggestions(repeat('zz', 400))), 0::int,
  'S44-05 an absurdly long query is refused rather than passed to the planner');

-- ─── Ranking ─────────────────────────────────────────────────────────────────
SELECT is(pg_temp.sugg_first('569999', 'value'), '569999',
  'S44-06 an exact PIN typed in full comes back first');

SELECT is(pg_temp.sugg_first('QA-SUG-PRE', 'label'), 'Prefix Area',
  'S44-07 a prefix match outranks a match buried in the middle of the text');

SELECT is(jsonb_array_length(public.search_area_suggestions('SUG-MIDD')), 1::int,
  'S44-08 a middle-of-word query still finds the area');

-- ─── Bounded results ─────────────────────────────────────────────────────────
SELECT is(jsonb_array_length(public.search_area_suggestions('5699', 1)), 1::int,
  'S44-09 the caller limit is honoured');

SELECT is(jsonb_array_length(public.search_area_suggestions('5699', 999)) <= 10::bigint, true,
  'S44-10 a caller cannot ask for an unbounded page');

-- ─── A typed wildcard must not become a table-wide match ─────────────────────
-- Without escaping, 'SUG%' would match every QA-SUG-* area (three rows) and '%%' would match
-- anything at all. Both answers have to be empty: the text is a literal, not a pattern.
SELECT is(jsonb_array_length(public.search_area_suggestions('SUG%')), 0::int,
  'S44-19 a percent sign typed by hand matches no area');

SELECT is(jsonb_array_length(public.search_area_suggestions('%%')), 0::int,
  'S44-20 a query of pure wildcards matches no area');

-- ─── Only live areas, and never a price ──────────────────────────────────────
SELECT is(jsonb_array_length(public.search_area_suggestions('QA-SUG-OFF')), 0::int,
  'S44-11 an inactive area is never suggested');

SELECT is(bool_and((item.elem -> 'delivery_fee_paise') IS NULL), true,
  'S44-12 area suggestions carry no money fields; the price still comes from the PIN check')
  FROM jsonb_array_elements(public.search_area_suggestions('569999')) AS item(elem);

SELECT is(pg_temp.sugg_first('569999', 'has_boundary'), 'true',
  'S44-13 a bounded area reports it, so the picker can jump to its centre');

SELECT is(round(pg_temp.sugg_first('569999', 'lat')::numeric, 2), round(13.0::numeric, 2),
  'S44-14 the centroid of the drawn area is offered so the map can centre on it');

-- ─── The private half: scoping is the whole story ────────────────────────────
SET LOCAL request.jwt.claims = '{"sub":"04400000-0000-0000-0000-000000000001","role":"authenticated"}';
SET LOCAL request.jwt.claim.sub = '04400000-0000-0000-0000-000000000001';

SELECT is(public.search_area_suggestions('Alpha')::text LIKE '%Alpha Home%', true,
  'S44-15 a customer finds their own saved address');

SELECT is(public.search_area_suggestions('Beta')::text LIKE '%Beta Secret%', false,
  'S44-16 and cannot find another customer''s saved address');

RESET request.jwt.claims;
RESET request.jwt.claim.sub;

SELECT is(public.search_area_suggestions('Alpha')::text LIKE '%Alpha Home%', false,
  'S44-17 with no signed-in caller the private half is empty, while areas still work');

-- proconfig is a text[], so it has to be flattened before matching: `config ~ '...'` has no
-- operator for arrays and fails the whole batch.
SELECT is(
  (SELECT prosecdef
            AND coalesce(array_to_string(proconfig, ','), '') LIKE '%search_path=%'
     FROM pg_proc WHERE proname = 'search_area_suggestions' LIMIT 1),
  true,
  'S44-18 it is SECURITY DEFINER with a pinned search_path, as every definer function here must be');

SELECT * FROM finish();

ROLLBACK;

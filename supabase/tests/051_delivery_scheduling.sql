-- pgTAP 051: the delivery-scheduling foundation must compute real windows and stay inert.
--
-- Two jobs, and they are opposites.
--
-- 1. Prove the arithmetic. The mockups print "Tomorrow, 7:00 AM - 11:00 AM" as if it were a fact.
--    It is only a fact if the server derives it from a configured rule in the store's own
--    timezone, so the assertions here pin the local date AND the local hour and minute of the
--    returned window. A `now() + interval '24 hours'` implementation, or one that reads the
--    reader's clock instead of Asia/Kolkata, fails S51-15 through S51-17.
--
-- 2. Prove nothing changed. `accept_orders_when_closed` defaults to false and the guard in
--    place_order was deliberately left in place, so a closed store must still answer
--    STORE_CLOSED - with scheduling off AND with it on. S51-24 and S51-25 are the assertions
--    that make "this migration is inert" a fact rather than an intention: the moment someone
--    opens the gate, one of them has to be rewritten on purpose.
--
-- The grant assertions exist because this project has been caught by the Supabase default twice:
-- functions are created with EXECUTE already granted to PUBLIC and anon, so `GRANT ... TO
-- authenticated` narrows nobody. S51-07 and S51-08 fail if the revokes are dropped.
--
-- Self-contained: creates pgTAP and fixtures inside this transaction, which the trailing
-- ROLLBACK undoes entirely.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgtap WITH SCHEMA extensions;
SET LOCAL search_path = public, extensions;

DO $boot$
BEGIN
  IF to_regprocedure('public.get_next_delivery_window()') IS NULL THEN
    RAISE EXCEPTION 'Migration 20261106000001_delivery_scheduling_foundation.sql has not been applied. Apply it before running suite 051.';
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

-- The store's own zone, so "tomorrow" and "09:00" mean one thing on both sides of the assertion.
DO $tz$
BEGIN
  IF NOT public.is_valid_timezone('Asia/Kolkata') THEN
    RAISE EXCEPTION 'Asia/Kolkata is not a known timezone on this server; suite 051 cannot pin local time.';
  END IF;
END;
$tz$;

SELECT plan(27);

-- Created before anything else touches store_settings: that table has an audit trigger which
-- records auth.uid() as actor_id, so a claim left set to a non-existent profile would fail on the
-- foreign key rather than on the behaviour under test.
SELECT pg_temp.make_profile('05100000-0000-0000-0000-000000000001', 'customer', 'QA51 Customer');
SELECT pg_temp.make_profile('05100000-0000-0000-0000-000000000002', 'customer', 'QA51 Second');

-- ------------------------------------------------------------------ shape and authority
SELECT is(
  (SELECT p.prosecdef FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public' AND p.proname = 'get_next_delivery_window'),
  false,
  'S51-01 the window read is SECURITY INVOKER, so RLS decides what a caller may know');

SELECT is(
  (SELECT p.prosecdef FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public' AND p.proname = 'validate_delivery_slot'),
  false,
  'S51-02 the slot check is SECURITY INVOKER too');

SELECT is(
  (SELECT p.prosecdef FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public' AND p.proname = 'update_store_scheduling'),
  true,
  'S51-03 only the settings writer is SECURITY DEFINER, and it checks the role itself');

SELECT is(
  (SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public' AND p.proname = 'get_next_delivery_window'),
  1::bigint,
  'S51-04 exactly one signature, so PostgREST never has to guess');

SELECT is(
  (SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public' AND p.proname = 'validate_delivery_slot'),
  1::bigint,
  'S51-05 and one for the slot check');

-- ------------------------------------------------------------------ who may call what
SET LOCAL ROLE anon;
SELECT lives_ok(
  $$SELECT public.get_next_delivery_window()$$,
  'S51-06 anon may read the next window, because store hours are public information');

SELECT throws_like(
  $$SELECT public.validate_delivery_slot(now() + interval '3 days')$$,
  '%permission denied%',
  'S51-07 but anon may not validate a slot - it answers for a signed-in order');
RESET ROLE;

SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '05100000-0000-0000-0000-000000000002';
SELECT throws_like(
  $$SELECT public.update_store_scheduling(true)$$,
  '%permission denied%',
  'S51-08 a signed-in customer cannot change the hours the store commits to');
RESET ROLE;

-- ------------------------------------------------------------------ off means off
UPDATE public.store_settings
   SET accept_orders_when_closed = false,
       scheduling_min_lead_hours = 8,
       scheduling_max_days_ahead = 7,
       timezone = 'Asia/Kolkata';
DELETE FROM public.delivery_windows;

SELECT is(
  (SELECT (public.get_next_delivery_window() ->> 'enabled')::boolean),
  false,
  'S51-09 with the setting off, the answer is disabled whatever the windows table holds');

SELECT is(
  (SELECT public.get_next_delivery_window() -> 'window_start'),
  'null'::jsonb,
  'S51-10 and no window is offered, so no screen can show one by accident');

-- ------------------------------------------------------------------ empty windows are not "any time"
UPDATE public.store_settings SET accept_orders_when_closed = true;

SELECT is(
  (SELECT public.get_next_delivery_window() -> 'window_start'),
  'null'::jsonb,
  'S51-11 enabled but nothing configured: an empty table means unavailable, not unrestricted');

SELECT ok(
  (public.get_next_delivery_window() ->> 'reason') IS NOT NULL,
  'S51-12 and it says why, so the UI never has to guess at a cause');

-- ------------------------------------------------------------------ the arithmetic
-- One rule only, on the weekday seven store-local days out, at 09:00. With a 24-hour lead the
-- occurrence "today at 09:00" is always ineligible, so exactly one answer exists at any hour of
-- the day the suite runs at.
INSERT INTO public.delivery_windows (weekday, start_hour, start_minute, duration_minutes, active, note)
VALUES (
  EXTRACT(DOW FROM ((now() AT TIME ZONE 'Asia/Kolkata')::date + 7))::smallint,
  9, 0, 120, true, 'QA51 single rule'
);

UPDATE public.store_settings
   SET scheduling_min_lead_hours = 24,
       scheduling_max_days_ahead = 7;

SELECT is(
  (SELECT (public.get_next_delivery_window() ->> 'enabled')::boolean),
  true,
  'S51-13 enabled once the store both allows it and publishes a rule');

SELECT isnt(
  (SELECT public.get_next_delivery_window() -> 'window_start'),
  'null'::jsonb,
  'S51-14 and a window is returned');

SELECT is(
  (SELECT ((public.get_next_delivery_window() ->> 'window_start')::timestamptz
            AT TIME ZONE 'Asia/Kolkata')::date),
  (SELECT ((now() AT TIME ZONE 'Asia/Kolkata')::date + 7)),
  'S51-15 the date is the store-local day seven out, not now()+24h and not the phone''s day');

SELECT is(
  (SELECT EXTRACT(HOUR FROM (public.get_next_delivery_window() ->> 'window_start')::timestamptz
                    AT TIME ZONE 'Asia/Kolkata')::int),
  9,
  'S51-16 the hour is the configured 09:00 read in the store zone (a UTC read would say 3 or 4)');

SELECT is(
  (SELECT EXTRACT(MINUTE FROM (public.get_next_delivery_window() ->> 'window_start')::timestamptz
                    AT TIME ZONE 'Asia/Kolkata')::int),
  0,
  'S51-17 and the minute, because a half-hour zone like +05:30 turns a dropped minute into 30 past');

SELECT ok(
  (public.get_next_delivery_window() ->> 'window_start')::timestamptz
    >= now() + interval '24 hours',
  'S51-18 the window respects the store''s minimum lead time');

SELECT is(
  (SELECT (public.get_next_delivery_window() ->> 'is_same_day')::boolean),
  false,
  'S51-19 and it is not today, so no copy can claim same-day delivery');

SELECT is(
  (SELECT count(*) FROM public.delivery_windows
     WHERE weekday = EXTRACT(DOW FROM ((now() AT TIME ZONE 'Asia/Kolkata')::date + 7))::smallint),
  1::bigint,
  'S51-20 weekday follows PostgreSQL EXTRACT(DOW): 0 = Sunday, the convention the dashboard must match');

-- ------------------------------------------------------------------ the slot check
SELECT is(
  (SELECT (public.validate_delivery_slot(
     (public.get_next_delivery_window() ->> 'window_start')::timestamptz) ->> 'ok')::boolean),
  true,
  'S51-21 the window the store just published is accepted by its own validator');

SELECT is(
  (SELECT (public.validate_delivery_slot(
     (public.get_next_delivery_window() ->> 'window_start')::timestamptz - interval '1 day'))
     ->> 'reason'),
  'no delivery window on that date and time',
  'S51-22 a hand-made timestamp outside every rule is refused, not rounded into the nearest one');

SELECT is(
  (SELECT (public.validate_delivery_slot(
     (public.get_next_delivery_window() ->> 'window_start')::timestamptz + interval '7 days'))
     ->> 'reason'),
  'that slot is more than 7 days away',
  'S51-23 and one beyond the configured horizon is refused by the horizon, whichever rule it hits');

SELECT ok(
  NOT (SELECT (public.validate_delivery_slot(now() - interval '1 hour') ->> 'ok')::boolean),
  'S51-24 a slot in the past is never valid');

-- ------------------------------------------------------------------ the gate is still shut
-- Every one of these runs as a real signed-in customer, because the guard under test is reached
-- only after place_order's own authentication and profile checks.
UPDATE public.store_settings SET store_open = false, accept_orders_when_closed = true;

SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '05100000-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims =
  '{"sub":"05100000-0000-0000-0000-000000000001","role":"authenticated"}';

SELECT is(
  (SELECT (public.place_order(
     '{"items":[],"address":{"postal_code":"560001"}}'::jsonb) ->> 'error')),
  'STORE_CLOSED',
  'S51-25 with scheduling ON, a closed store still refuses the order: this migration is inert');

SELECT is(
  (SELECT public.place_order('{"items":[],"address":{"postal_code":"560001"}}'::jsonb)
     ? 'order_id'),
  false,
  'S51-26 a refused order carries no order id, so nothing half-exists behind the error');

RESET ROLE;

UPDATE public.store_settings SET accept_orders_when_closed = false;

SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '05100000-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims =
  '{"sub":"05100000-0000-0000-0000-000000000001","role":"authenticated"}';

SELECT is(
  (SELECT (public.place_order(
     '{"items":[],"address":{"postal_code":"560001"}}'::jsonb) ->> 'error')),
  'STORE_CLOSED',
  'S51-27 and with scheduling OFF, exactly as before - the guard at step 3 of place_order is intact');

RESET ROLE;

SELECT * FROM finish();

ROLLBACK;

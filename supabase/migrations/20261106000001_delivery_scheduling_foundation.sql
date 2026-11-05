-- ============================================================
-- 20261106000001  Delivery-window scheduling foundation (inert by default)
-- ============================================================
-- The redesign brief asks for a Store Closed mode in which the customer orders now and the
-- store delivers in the next real window. This migration is the smallest server-side foundation
-- that could support that without inventing store policy, and it changes no existing behaviour:
--
--   * `accept_orders_when_closed` defaults to FALSE, so `place_order` still refuses an order
--     while `store_open` is false. The guard at step 3 of 20261028000001_fix_place_order.sql is
--     NOT removed here. Making closed-store orders actually placeable is a separate,
--     owner-approved change, because it touches money, stock and the order state machine.
--   * `delivery_windows` starts EMPTY. An empty table means "no window exists", never "any time
--     is fine" - the safe reading of an unconfigured store.
--   * No timestamp is computed from phone-local time and nothing is ever `now() + 24 hours`.
--     Every window is derived from a configured rule in the store's own timezone.
--
-- What is genuinely missing, and is deliberately not faked:
--   * slot capacity (how many orders one window can take) - needs the owner's answer
--   * holiday / exceptional-closure dates - weekly rules alone cannot express a one-off shut day
--   * the `place_order` branch that would accept a scheduled order, and the staff queue labelling
--
-- The read functions are SECURITY INVOKER: they read store_settings and delivery_windows, which
-- RLS already exposes to anon and authenticated, so they widen nothing. `update_store_scheduling`
-- is SECURITY DEFINER and service-role only, because it writes configuration a customer must
-- never set about their own order.

-- ---------------------------------------------------------------------------
-- 1. Configuration
-- ---------------------------------------------------------------------------
ALTER TABLE public.store_settings
  ADD COLUMN IF NOT EXISTS accept_orders_when_closed boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS scheduling_min_lead_hours integer NOT NULL DEFAULT 8,
  ADD COLUMN IF NOT EXISTS scheduling_max_days_ahead smallint NOT NULL DEFAULT 7;

-- Bounds rather than guesses: a zero lead would allow an order for a window that has already
-- started, and a huge horizon would let a customer pick a date the store has not planned for.
ALTER TABLE public.store_settings DROP CONSTRAINT IF EXISTS store_settings_lead_hours_range;
ALTER TABLE public.store_settings
  ADD CONSTRAINT store_settings_lead_hours_range
  CHECK (scheduling_min_lead_hours BETWEEN 1 AND 72);

ALTER TABLE public.store_settings DROP CONSTRAINT IF EXISTS store_settings_max_days_range;
ALTER TABLE public.store_settings
  ADD CONSTRAINT store_settings_max_days_range
  CHECK (scheduling_max_days_ahead BETWEEN 1 AND 30);

COMMENT ON COLUMN public.store_settings.accept_orders_when_closed IS
  'Whether an order may be placed while store_open is false, for a future delivery window. '
  'False means closed-store ordering does not exist for this store, whatever the windows table '
  'contains.';
COMMENT ON COLUMN public.store_settings.scheduling_min_lead_hours IS
  'Minimum hours between placing an order and the start of the window it is placed into. A '
  'packing-and-dispatch policy the store owns, not something a client may assert.';

-- ---------------------------------------------------------------------------
-- 2. Recurring delivery windows
-- ---------------------------------------------------------------------------
-- One row is "on this weekday, from this local time, for this long". `weekday` follows
-- PostgreSQL's EXTRACT(DOW): 0 = Sunday .. 6 = Saturday, and the interpretation is pinned by a
-- test so a dashboard cannot quietly swap it.
CREATE TABLE IF NOT EXISTS public.delivery_windows (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  weekday          smallint NOT NULL CHECK (weekday BETWEEN 0 AND 6),
  start_hour       smallint NOT NULL CHECK (start_hour BETWEEN 0 AND 23),
  start_minute     smallint NOT NULL CHECK (start_minute BETWEEN 0 AND 59),
  duration_minutes smallint NOT NULL DEFAULT 120 CHECK (duration_minutes BETWEEN 30 AND 720),
  active           boolean NOT NULL DEFAULT true,
  note             text,
  created_at       timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT delivery_windows_slot_unique UNIQUE (weekday, start_hour, start_minute)
);

COMMENT ON TABLE public.delivery_windows IS
  'Recurring delivery windows the store commits to, in store-local time. Empty means no window '
  'is available, not that any time is available.';

CREATE INDEX IF NOT EXISTS delivery_windows_active_weekday_idx
  ON public.delivery_windows (weekday) WHERE active;

ALTER TABLE public.delivery_windows ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "delivery_windows_read" ON public.delivery_windows;
CREATE POLICY "delivery_windows_read" ON public.delivery_windows
  FOR SELECT TO anon, authenticated USING (true);

-- Writes go through update_store_scheduling (service role) or a migration. No client role gets
-- to edit the hours the store promises, and there is deliberately no manager policy here: the
-- dashboard toggle is not built yet, and a policy with no caller is a surface with no owner.
DROP POLICY IF EXISTS "delivery_windows_write_service" ON public.delivery_windows;
CREATE POLICY "delivery_windows_write_service" ON public.delivery_windows
  FOR ALL TO service_role USING (true) WITH CHECK (true);

GRANT SELECT ON public.delivery_windows TO anon, authenticated, service_role;

-- ---------------------------------------------------------------------------
-- 3. A timezone name is data, and a wrong one must be loud rather than wrong
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.is_valid_timezone(p_zone text)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public, pg_temp
AS $$
  SELECT EXISTS (SELECT 1 FROM pg_catalog.pg_timezone_names WHERE name = p_zone)
$$;

COMMENT ON FUNCTION public.is_valid_timezone(text) IS
  'Guard for store_settings.timezone. A typo in a zone name does not raise on read - it silently '
  'shifts every window by an hour or a day, which is how a "7 AM" slot ends up delivered at noon.';

REVOKE EXECUTE ON FUNCTION public.is_valid_timezone(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.is_valid_timezone(text) TO anon, authenticated, service_role;

-- ---------------------------------------------------------------------------
-- 4. The next window the store can actually honour
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.get_next_delivery_window()
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY INVOKER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_settings   public.store_settings%ROWTYPE;
  v_tz         text;
  v_window     timestamptz;
  v_duration   smallint;
BEGIN
  SELECT * INTO v_settings FROM public.store_settings LIMIT 1;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('enabled', false, 'window_start', null,
                              'reason', 'no store settings');
  END IF;

  v_tz := coalesce(nullif(btrim(v_settings.timezone), ''), 'Asia/Kolkata');

  IF NOT public.is_valid_timezone(v_tz) THEN
    RETURN jsonb_build_object('enabled', false, 'window_start', null,
                              'timezone', v_tz,
                              'reason', 'the configured timezone is not a real zone');
  END IF;

  IF NOT v_settings.accept_orders_when_closed THEN
    RETURN jsonb_build_object('enabled', false, 'window_start', null,
                              'timezone', v_tz,
                              'reason', 'closed-store ordering is off for this store');
  END IF;

  -- Today in the store's calendar, not the reader's. "Tomorrow" has to mean the store's
  -- tomorrow, or a customer messaging at 11 PM IST gets a date that already passed.
  WITH days AS (
    SELECT generate_series(0, v_settings.scheduling_max_days_ahead::int) AS n
  ),
  local_days AS (
    SELECT d.n,
           ((now() AT TIME ZONE v_tz)::date + d.n) AS local_date
      FROM days d
  ),
  candidates AS (
    SELECT ld.n,
           ((ld.local_date)::timestamp
             + make_interval(hours => w.start_hour, mins => w.start_minute))
             AT TIME ZONE v_tz AS starts_at,
           w.duration_minutes
      FROM local_days ld
      JOIN public.delivery_windows w
        ON w.active
       AND w.weekday = EXTRACT(DOW FROM ld.local_date)::smallint
  ),
  eligible AS (
    SELECT starts_at, duration_minutes
      FROM candidates
     WHERE starts_at >= now() + make_interval(hours => v_settings.scheduling_min_lead_hours)
  )
  SELECT starts_at, duration_minutes INTO v_window, v_duration
    FROM eligible
   ORDER BY starts_at
   LIMIT 1;

  IF v_window IS NULL THEN
    RETURN jsonb_build_object(
      'enabled', true,
      'window_start', null,
      'timezone', v_tz,
      'reason', 'no delivery window is available in the next '
                || v_settings.scheduling_max_days_ahead::text || ' days');
  END IF;

  RETURN jsonb_build_object(
    'enabled', true,
    'window_start', v_window,
    'window_end', v_window + make_interval(mins => v_duration::int),
    'timezone', v_tz,
    'lead_hours', v_settings.scheduling_min_lead_hours,
    'duration_minutes', v_duration,
    'is_same_day', ((v_window AT TIME ZONE v_tz)::date = (now() AT TIME ZONE v_tz)::date),
    'reason', null
  );
END;
$$;

COMMENT ON FUNCTION public.get_next_delivery_window() IS
  'The earliest delivery window the store can honour, or null with a reason. Answers about the '
  'store''s configuration only - it does not reserve capacity, and it is not a promise until '
  'validate_delivery_slot accepts the same instant at placement.';

-- ---------------------------------------------------------------------------
-- 5. Was the slot the customer chose still valid a moment ago?
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.validate_delivery_slot(
  p_slot timestamptz,
  p_pincode text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY INVOKER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_settings public.store_settings%ROWTYPE;
  v_tz       text;
  v_local    timestamp;
  v_rule     record;
BEGIN
  SELECT * INTO v_settings FROM public.store_settings LIMIT 1;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('ok', false, 'reason', 'no store settings');
  END IF;

  IF p_slot IS NULL THEN
    RETURN jsonb_build_object('ok', false, 'reason', 'no delivery slot chosen');
  END IF;

  v_tz := coalesce(nullif(btrim(v_settings.timezone), ''), 'Asia/Kolkata');

  IF NOT public.is_valid_timezone(v_tz) THEN
    RETURN jsonb_build_object('ok', false, 'reason', 'the configured timezone is not a real zone');
  END IF;

  IF NOT v_settings.accept_orders_when_closed THEN
    RETURN jsonb_build_object('ok', false, 'reason', 'closed-store ordering is off for this store');
  END IF;

  -- The store's lead time, not the phone's clock: a device with a wrong time must not be able to
  -- book a window that starts before the order was placed.
  IF p_slot < now() + make_interval(hours => v_settings.scheduling_min_lead_hours) THEN
    RETURN jsonb_build_object(
      'ok', false,
      'reason', 'that slot is less than ' || v_settings.scheduling_min_lead_hours::text
                || ' hours away');
  END IF;

  -- The horizon is counted in store-local DAYS, the same unit the enumeration in
  -- get_next_delivery_window uses. Comparing instants instead would reject the last permitted
  -- day whenever its window starts later in the day than this moment - the server would offer a
  -- window on Home and then refuse it at checkout, which is the worst kind of disagreement.
  IF (p_slot AT TIME ZONE v_tz)::date
       > ((now() AT TIME ZONE v_tz)::date + v_settings.scheduling_max_days_ahead) THEN
    RETURN jsonb_build_object(
      'ok', false,
      'reason', 'that slot is more than ' || v_settings.scheduling_max_days_ahead::text
                || ' days away');
  END IF;

  -- The slot must fall inside a rule the store published for that local weekday. Anything else
  -- is a hand-crafted timestamp from a client, and a client is not allowed to choose when the
  -- store works.
  v_local := p_slot AT TIME ZONE v_tz;
  SELECT w.* INTO v_rule
    FROM public.delivery_windows w
   WHERE w.active
     AND w.weekday = EXTRACT(DOW FROM v_local)::smallint
     AND (w.start_hour * 60 + w.start_minute)
         <= (EXTRACT(HOUR FROM v_local)::int * 60 + EXTRACT(MINUTE FROM v_local)::int)
     AND (w.start_hour * 60 + w.start_minute + w.duration_minutes)
         >  (EXTRACT(HOUR FROM v_local)::int * 60 + EXTRACT(MINUTE FROM v_local)::int)
   ORDER BY w.start_hour, w.start_minute
   LIMIT 1;

  IF NOT FOUND THEN
    RETURN jsonb_build_object('ok', false, 'reason', 'no delivery window on that date and time');
  END IF;

  -- Coverage is checked here too, so one call answers "can this be delivered" rather than
  -- "does the calendar allow it". An unconfigured service-area table stays permissive, matching
  -- place_order's own rule.
  IF p_pincode IS NOT NULL AND btrim(p_pincode) <> ''
     AND EXISTS (SELECT 1 FROM public.service_areas WHERE active AND kind = 'pincode')
     AND NOT EXISTS (
           SELECT 1 FROM public.service_areas
            WHERE active AND kind = 'pincode' AND lower(value) = lower(btrim(p_pincode))
         ) THEN
    RETURN jsonb_build_object('ok', false, 'reason', 'delivery is not available to this PIN code');
  END IF;

  RETURN jsonb_build_object(
    'ok', true,
    'slot', p_slot,
    'window_start', ((v_local::date)::timestamp
                      + make_interval(hours => v_rule.start_hour, mins => v_rule.start_minute))
                      AT TIME ZONE v_tz,
    'duration_minutes', v_rule.duration_minutes,
    'timezone', v_tz,
    'reason', null
  );
END;
$$;

COMMENT ON FUNCTION public.validate_delivery_slot(timestamptz, text) IS
  'Server-side revalidation of a chosen delivery slot: lead time, horizon, published window and '
  'PIN coverage. Called at placement, never trusted from the client.';

-- ---------------------------------------------------------------------------
-- 6. The only writer for this configuration
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.update_store_scheduling(
  p_accept_when_closed boolean,
  p_lead_hours integer DEFAULT NULL,
  p_max_days_ahead smallint DEFAULT NULL,
  p_timezone text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_zone text;
  v_lead integer;
  v_days smallint;
BEGIN
  -- The role PostgREST sets on the connection is the authority here, not a claim inside a token:
  -- any caller holding the service key is service_role, and nobody else can become it.
  IF current_setting('role', true) IS DISTINCT FROM 'service_role' THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN',
                              'message', 'Only the service role may change delivery scheduling');
  END IF;

  v_zone := coalesce(p_timezone, (SELECT s.timezone FROM public.store_settings s LIMIT 1));
  IF v_zone IS NOT NULL AND NOT public.is_valid_timezone(v_zone) THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED',
                              'message', 'Not a real timezone: ' || v_zone);
  END IF;

  v_lead := coalesce(p_lead_hours, (SELECT s.scheduling_min_lead_hours FROM public.store_settings s LIMIT 1));
  IF v_lead IS NULL OR v_lead < 1 OR v_lead > 72 THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED',
                              'message', 'Lead hours must be between 1 and 72');
  END IF;

  v_days := coalesce(p_max_days_ahead, (SELECT s.scheduling_max_days_ahead FROM public.store_settings s LIMIT 1));
  IF v_days IS NULL OR v_days < 1 OR v_days > 30 THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED',
                              'message', 'Days ahead must be between 1 and 30');
  END IF;

  UPDATE public.store_settings
     SET accept_orders_when_closed = p_accept_when_closed,
         scheduling_min_lead_hours = v_lead,
         scheduling_max_days_ahead = v_days,
         timezone = coalesce(v_zone, timezone)
   WHERE true;

  RETURN jsonb_build_object(
    'accept_orders_when_closed', p_accept_when_closed,
    'scheduling_min_lead_hours', v_lead,
    'scheduling_max_days_ahead', v_days,
    'timezone', v_zone,
    'next_window', public.get_next_delivery_window()
  );
END;
$$;

COMMENT ON FUNCTION public.update_store_scheduling(boolean, integer, smallint, text) IS
  'Validated writer for the scheduling settings. Service role only: no customer, staff member or '
  'anon key may change the hours the store commits to through this function.';

-- Supabase creates functions with EXECUTE already granted to PUBLIC and anon, so a GRANT to
-- authenticated narrows nobody. Each new function is revoked first and then granted to exactly
-- the roles that need it (the trap this project has fallen into twice).
REVOKE EXECUTE ON FUNCTION public.get_next_delivery_window() FROM PUBLIC, anon, authenticated;
GRANT  EXECUTE ON FUNCTION public.get_next_delivery_window() TO anon, authenticated, service_role;

REVOKE EXECUTE ON FUNCTION public.validate_delivery_slot(timestamptz, text) FROM PUBLIC, anon, authenticated;
GRANT  EXECUTE ON FUNCTION public.validate_delivery_slot(timestamptz, text) TO authenticated, service_role;

REVOKE EXECUTE ON FUNCTION public.update_store_scheduling(boolean, integer, smallint, text)
  FROM PUBLIC, anon, authenticated;
GRANT  EXECUTE ON FUNCTION public.update_store_scheduling(boolean, integer, smallint, text)
  TO service_role;

NOTIFY pgrst, 'reload schema';

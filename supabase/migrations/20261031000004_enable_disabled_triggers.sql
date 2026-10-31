-- Migration: 20261031000004_enable_disabled_triggers.sql
-- Every user trigger that ended up disabled is re-enabled, and each one is reported.
--
-- Why this exists: on 2026-10-31 all three triggers on public.orders were found with
-- tgenabled = '0' (disabled), so an order insert queued no push and status changes
-- queued nothing. No migration in this repository disables triggers, so they were
-- turned off outside the code (Table Editor "Disable triggers" or a manual session).
--
-- This is deliberately loud: the NOTICE lists every trigger it re-enables, so the next
-- time something switches them off it is visible in the SQL output instead of silently
-- breaking push, status history and inventory movements.

BEGIN;

DO $$
DECLARE
  r record;
  v_count integer := 0;
  v_names text := '';
BEGIN
  FOR r IN
    SELECT c.relname AS table_name, t.tgname AS trigger_name, t.tgenabled
    FROM pg_trigger t
    JOIN pg_class c ON c.oid = t.tgrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE NOT t.tgisinternal
      AND n.nspname = 'public'
      AND t.tgenabled <> 'O'
    ORDER BY c.relname, t.tgname
  LOOP
    EXECUTE format('ALTER TABLE public.%I ENABLE TRIGGER %I', r.table_name, r.trigger_name);
    v_count := v_count + 1;
    v_names := v_names || r.table_name || '.' || r.trigger_name || ' (was ' || r.tgenabled || '); ';
  END LOOP;

  IF v_count = 0 THEN
    RAISE NOTICE 'All public triggers were already enabled.';
  ELSE
    RAISE NOTICE 'Re-enabled % trigger(s): %', v_count, v_names;
  END IF;
END $$;

NOTIFY pgrst, 'reload schema';

COMMIT;

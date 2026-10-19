-- ===========================================================================
-- DEPRECATED / UNSAFE — DO NOT RUN
-- ===========================================================================
-- This script used to disable row-level security by creating
-- `FOR ALL TO public USING (true)` policies on identity, catalogue, orders and
-- settings. That exposed every customer's orders and addresses to anyone with
-- the public anon key.
--
-- Use the versioned migrations instead:
--   supabase/migrations/20261014000017_security_hardening.sql
-- (apply after 20261014000015 and 20261014000016 on an existing project).
-- ===========================================================================

DO $$
BEGIN
  RAISE EXCEPTION
    'fix_rls_catalogue.sql is deprecated and unsafe. Apply 20261014000017_security_hardening.sql instead.';
END $$;

-- Migration: 20261031000002_device_token_summary.sql
-- Read-only diagnostics for push registration. device_tokens is owner-only under RLS,
-- so neither a manager nor the support person can confirm that a phone registered.
-- This returns counts only - never the token itself - so no device can be impersonated.

BEGIN;

CREATE OR REPLACE FUNCTION public.device_token_summary()
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_result jsonb;
BEGIN
  IF public.current_role()::text <> 'manager' THEN
    RAISE EXCEPTION 'Only a manager can read push diagnostics' USING ERRCODE = '42501';
  END IF;

  SELECT jsonb_build_object(
    'customer_tokens', count(*) FILTER (WHERE app_flavour = 'customer'),
    'staff_tokens', count(*) FILTER (WHERE app_flavour = 'staff'),
    'total', count(*),
    'newest_at', max(updated_at)
  )
  INTO v_result
  FROM public.device_tokens;

  RETURN v_result;
END;
$$;

REVOKE ALL ON FUNCTION public.device_token_summary() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.device_token_summary() TO authenticated;

NOTIFY pgrst, 'reload schema';

COMMIT;

-- Migration: 20261030000003_staff_store_status.sql
-- The staff app needs the live store_open value to render the switch correctly.
-- store_settings is not readable by staff under RLS, so expose the single flag here.

BEGIN;

CREATE OR REPLACE FUNCTION public.staff_store_status()
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_open boolean;
BEGIN
  IF public.current_role()::text NOT IN ('staff', 'manager') THEN
    RAISE EXCEPTION 'Only store staff can read the store status' USING ERRCODE = '42501';
  END IF;

  SELECT store_open INTO v_open FROM public.store_settings LIMIT 1;

  RETURN jsonb_build_object('store_open', COALESCE(v_open, true));
END;
$$;

REVOKE ALL ON FUNCTION public.staff_store_status() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.staff_store_status() TO authenticated;

NOTIFY pgrst, 'reload schema';

COMMIT;

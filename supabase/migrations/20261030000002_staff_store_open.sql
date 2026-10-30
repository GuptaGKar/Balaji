-- Migration: 20261030000002_staff_store_open.sql
-- Staff app: open/close the store from the order desk.
-- store_settings is manager-writable under RLS today, so a staff member cannot flip
-- store_open directly. This RPC is the narrow, auditable path for staff and managers.

BEGIN;

CREATE OR REPLACE FUNCTION public.staff_set_store_open(p_open boolean)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_role text := public.current_role()::text;
  v_previous boolean;
BEGIN
  IF v_role NOT IN ('staff', 'manager') THEN
    RAISE EXCEPTION 'Only store staff can change the store status' USING ERRCODE = '42501';
  END IF;

  IF p_open IS NULL THEN
    RAISE EXCEPTION 'p_open is required' USING ERRCODE = '22023';
  END IF;

  SELECT store_open INTO v_previous FROM public.store_settings LIMIT 1;

  UPDATE public.store_settings SET store_open = p_open;

  RETURN jsonb_build_object(
    'store_open', p_open,
    'previous', v_previous,
    'changed_by', auth.uid(),
    'changed_at', now()
  );
END;
$$;

REVOKE ALL ON FUNCTION public.staff_set_store_open(boolean) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.staff_set_store_open(boolean) TO authenticated;

NOTIFY pgrst, 'reload schema';

COMMIT;

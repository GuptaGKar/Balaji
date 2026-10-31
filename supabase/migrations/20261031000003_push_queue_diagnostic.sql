-- Migration: 20261031000003_push_queue_diagnostic.sql
-- Read-only view of recent push attempts for a manager.
--
-- The shape of the rows tells the failure apart without any server shell access:
--   attempts = 0, sent_at IS NULL   -> send-push never ran (cron or secret problem)
--   attempts > 0, last_error set    -> send-push ran and FCM refused
--   sent_at set, last_error = null  -> FCM accepted the message
--   last_error = 'no device token'  -> the phone never registered
-- No token value is exposed.

BEGIN;

CREATE OR REPLACE FUNCTION public.push_queue_diagnostic(p_limit integer DEFAULT 10)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_rows jsonb;
BEGIN
  IF public.current_role()::text <> 'manager' THEN
    RAISE EXCEPTION 'Only a manager can read push diagnostics' USING ERRCODE = '42501';
  END IF;

  SELECT COALESCE(jsonb_agg(row_to_json(t) ORDER BY t.created_at DESC), '[]'::jsonb)
  INTO v_rows
  FROM (
    SELECT id,
           audience,
           left(title, 60) AS title,
           attempts,
           sent_at IS NOT NULL AS sent,
           last_error,
           created_at
    FROM public.push_queue
    ORDER BY created_at DESC
    LIMIT GREATEST(1, LEAST(COALESCE(p_limit, 10), 50))
  ) t;

  RETURN jsonb_build_object(
    'rows', v_rows,
    'pending', (SELECT count(*) FROM public.push_queue WHERE sent_at IS NULL),
    'total', (SELECT count(*) FROM public.push_queue)
  );
END;
$$;

REVOKE ALL ON FUNCTION public.push_queue_diagnostic(integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.push_queue_diagnostic(integer) TO authenticated;

NOTIFY pgrst, 'reload schema';

COMMIT;

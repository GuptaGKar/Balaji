-- Migration: 20261031000005_push_drain_warning.sql
-- The drain function used to return silently when the Vault secret was missing, so the
-- cron reported success while sending nothing (that cost hours on 2026-10-31).
-- It now raises a warning that appears in the cron/postgres log, and still no-ops.

BEGIN;

CREATE OR REPLACE FUNCTION public.trigger_push_drain()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_secret text;
BEGIN
  SELECT decrypted_secret INTO v_secret
  FROM vault.decrypted_secrets
  WHERE name = 'push_webhook_secret'
  LIMIT 1;

  IF v_secret IS NULL OR length(v_secret) = 0 THEN
    RAISE WARNING 'push drain skipped: vault secret push_webhook_secret is missing';
    RETURN;
  END IF;

  PERFORM net.http_post(
    url := 'https://czbwwnlnmhhzketyjnzt.supabase.co/functions/v1/send-push',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-push-secret', v_secret
    ),
    body := '{}'::jsonb,
    timeout_milliseconds := 15000
  );
END;
$$;

REVOKE ALL ON FUNCTION public.trigger_push_drain() FROM PUBLIC, anon, authenticated;

NOTIFY pgrst, 'reload schema';

COMMIT;

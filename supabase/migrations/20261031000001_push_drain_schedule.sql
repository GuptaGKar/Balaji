-- Migration: 20261031000001_push_drain_schedule.sql
-- Runs the send-push Edge Function once a minute.
--
-- Before applying, store the webhook secret once in Vault (SQL editor):
--   select vault.create_secret('<PUSH_WEBHOOK_SECRET>', 'push_webhook_secret');
--
-- Nothing else is hard-coded: the function reads the secret from Vault, so it is not
-- in this file, not in the repository, and can be rotated with vault.update_secret().
-- If the secret is missing the job is a no-op (the queue simply keeps waiting).

BEGIN;

CREATE EXTENSION IF NOT EXISTS pg_cron WITH SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS pg_net WITH SCHEMA extensions;

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
    RETURN; -- not configured yet; nothing to do
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

-- Re-scheduling with the same name replaces the previous entry.
SELECT cron.unschedule('send-push-every-minute')
WHERE EXISTS (SELECT 1 FROM cron.job WHERE jobname = 'send-push-every-minute');

SELECT cron.schedule(
  'send-push-every-minute',
  '* * * * *',
  'SELECT public.trigger_push_drain()'
);

COMMIT;

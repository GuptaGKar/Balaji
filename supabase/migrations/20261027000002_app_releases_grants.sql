-- Migration: 20261027000002_app_releases_grants.sql
-- Description:
-- 1. Replace the blanket `GRANT ALL` on public.app_releases with the exact
--    privileges the dashboard needs. GRANT ALL also hands out TRUNCATE,
--    REFERENCES and TRIGGER, which row level security does not police.
-- 2. Validate release payloads at the trusted boundary instead of relying on
--    the clients: the download link must be HTTPS and the minimum supported
--    version cannot be newer than the published version.

BEGIN;

REVOKE ALL ON public.app_releases FROM authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.app_releases TO authenticated;

-- NOT VALID keeps the migration safe if rows were already inserted by hand;
-- it is enforced for every new or updated row.
ALTER TABLE public.app_releases
  DROP CONSTRAINT IF EXISTS app_releases_apk_url_https;
ALTER TABLE public.app_releases
  ADD CONSTRAINT app_releases_apk_url_https
  CHECK (apk_url ~* '^https://') NOT VALID;

ALTER TABLE public.app_releases
  DROP CONSTRAINT IF EXISTS app_releases_min_version_not_after_version;
ALTER TABLE public.app_releases
  ADD CONSTRAINT app_releases_min_version_not_after_version
  CHECK (min_version_code <= version_code) NOT VALID;

COMMIT;

-- pgTAP test: app_releases privilege surface and payload validation
BEGIN;
SELECT plan(8);

-- Privileges: the dashboard needs CRUD, but TRUNCATE is never required and is
-- not policed by row level security, so it must not be granted.
SELECT ok(
  has_table_privilege('authenticated', 'public.app_releases', 'SELECT'),
  'authenticated can SELECT from app_releases');

SELECT ok(
  has_table_privilege('authenticated', 'public.app_releases', 'INSERT'),
  'authenticated can INSERT into app_releases');

SELECT ok(
  has_table_privilege('authenticated', 'public.app_releases', 'UPDATE'),
  'authenticated can UPDATE app_releases');

SELECT ok(
  has_table_privilege('authenticated', 'public.app_releases', 'DELETE'),
  'authenticated can DELETE from app_releases');

SELECT ok(
  NOT has_table_privilege('authenticated', 'public.app_releases', 'TRUNCATE'),
  'authenticated cannot TRUNCATE app_releases');

SELECT ok(
  NOT has_table_privilege('anon', 'public.app_releases', 'INSERT'),
  'anon cannot INSERT into app_releases');

-- Payload validation: the download link must be HTTPS or the app refuses it.
SELECT throws_ok(
  $sql$INSERT INTO public.app_releases (audience, version_code, version_name, apk_url)
       VALUES ('customer', 900, '9.0', 'http://example.test/app.apk')$sql$,
  null, null,
  'a non-HTTPS apk_url is rejected');

-- A minimum supported version newer than the release itself can never match.
SELECT throws_ok(
  $sql$INSERT INTO public.app_releases (audience, version_code, version_name, min_version_code, apk_url)
       VALUES ('customer', 901, '9.1', 950, 'https://example.test/app.apk')$sql$,
  null, null,
  'min_version_code newer than version_code is rejected');

SELECT * FROM finish();
ROLLBACK;

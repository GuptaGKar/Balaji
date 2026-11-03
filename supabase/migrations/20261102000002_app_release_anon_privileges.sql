-- Supabase's default table grants include writes; RLS is not a reason to retain them.
BEGIN;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER
  ON public.app_releases FROM PUBLIC, anon;
REVOKE TRUNCATE, REFERENCES, TRIGGER
  ON public.app_releases FROM authenticated;
NOTIFY pgrst, 'reload schema';
COMMIT;

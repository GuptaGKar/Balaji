-- M12: the current signup trigger already qualifies its application objects.
-- Harden its definer lookup path without changing signup policy or its body.
BEGIN;
ALTER FUNCTION public.handle_new_user() SET search_path = '';
REVOKE EXECUTE ON FUNCTION public.handle_new_user() FROM PUBLIC, anon, authenticated;
NOTIFY pgrst, 'reload schema';
COMMIT;

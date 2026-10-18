-- M2: auth wiring, current_role(), audit redaction helper, profiles trigger
-- IMPLEMENTATION_PLAN.md §5.2, §5.4, §4.9

-- 1) current_role() — stable, SECURITY DEFINER, used in RLS policies
CREATE OR REPLACE FUNCTION public.current_role()
RETURNS public.user_role
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $sql$
  SELECT COALESCE(
    (SELECT role FROM public.profiles WHERE id = auth.uid()),
    'customer'::public.user_role
  );
$sql$;

COMMENT ON FUNCTION public.current_role() IS
  'Returns the caller role from profiles. SECURITY DEFINER so RLS policies can call it without granting direct profiles access.';

-- 2) Redaction helper for audit_logs — strips sensitive keys from JSONB before insert
CREATE OR REPLACE FUNCTION public.redact_for_audit(val jsonb)
RETURNS jsonb
LANGUAGE sql
IMMUTABLE
SECURITY DEFINER
SET search_path = ''
AS $sql$
  SELECT val - 'password' - 'encrypted_password' - 'otp' - 'token' - 'secret' - 'api_key' - 'webhook_secret' - 'private_key' - 'card_number' - 'cvv' - 'expiry';
$sql$;

COMMENT ON FUNCTION public.redact_for_audit(jsonb) IS
  'Removes known sensitive keys from a JSONB value before writing to audit_logs. Add keys as needed; never log credentials.';

-- 3) profiles trigger on auth signup — inserts a customer profile row
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
  INSERT INTO public.profiles (id, role, display_name, phone, is_active, must_change_password)
  VALUES (
    NEW.id,
    'customer'::public.user_role,
    COALESCE(NEW.raw_user_meta_data->>'display_name', NEW.email),
    NEW.raw_user_meta_data->>'phone',
    true,
    false
  );
  RETURN NEW;
EXCEPTION WHEN unique_violation THEN
  -- Profile already exists (e.g., pre-created by admin); that's fine.
  RETURN NEW;
END;
$$;

-- Attach to auth.users (runs as the auth service role, which bypasses RLS)
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- 4) Rebuild updated_at trigger on profiles (idempotent)
DROP TRIGGER IF EXISTS set_updated_at_profiles ON profiles;
CREATE TRIGGER set_updated_at_profiles
  BEFORE UPDATE ON profiles
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Grant execute on helper functions to authenticated role (RLS policies run as the calling user)
GRANT EXECUTE ON FUNCTION public.current_role() TO authenticated;
GRANT EXECUTE ON FUNCTION public.redact_for_audit(jsonb) TO authenticated;

-- Note: handle_new_user runs as auth service role (not the new user), so no grant needed.
-- Migration: 20261031000006_unlink_staff_auth_phones.sql
-- Customers sign in by phone OTP; staff sign in with a username and password.
--
-- 20261030000005 cleared public.profiles.phone for non-customers, but Supabase Auth
-- keeps its own copy in auth.users.phone, and the OTP lookup runs against that table.
-- Result: a staff member's phone still authenticated into the staff profile, and the
-- customer app correctly refused it with "This account belongs to store staff".
--
-- This removes the phone and its phone identity from Auth for every non-customer
-- profile, so an OTP on that number can only ever create a customer account.

BEGIN;

UPDATE auth.users u
   SET phone = NULL,
       phone_confirmed_at = NULL,
       updated_at = now()
 WHERE u.phone IS NOT NULL
   AND EXISTS (
     SELECT 1
     FROM public.profiles p
     WHERE p.id = u.id
       AND p.role <> 'customer'::public.user_role
   );

DELETE FROM auth.identities i
 USING public.profiles p
 WHERE i.user_id = p.id
   AND i.provider = 'phone'
   AND p.role <> 'customer'::public.user_role;

-- Keep future writes honest: the profile trigger already nulls phone for staff, and
-- this trigger does the same in Auth so the two can never diverge again.
CREATE OR REPLACE FUNCTION public.clear_staff_auth_phone()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_role public.user_role;
BEGIN
  SELECT role INTO v_role FROM public.profiles WHERE id = NEW.id;
  IF v_role IS NOT NULL AND v_role <> 'customer'::public.user_role THEN
    NEW.phone := NULL;
    NEW.phone_confirmed_at := NULL;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_clear_staff_auth_phone ON auth.users;
CREATE TRIGGER trg_clear_staff_auth_phone
  BEFORE UPDATE OF phone ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.clear_staff_auth_phone();

-- Report what is left, so the result is visible instead of assumed.
DO $$
DECLARE
  v_left integer;
BEGIN
  SELECT count(*) INTO v_left
  FROM auth.users u
  JOIN public.profiles p ON p.id = u.id
  WHERE p.role <> 'customer'::public.user_role
    AND u.phone IS NOT NULL;

  RAISE NOTICE 'Non-customer Auth accounts still holding a phone: %', v_left;
END $$;

COMMIT;

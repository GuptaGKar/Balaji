-- Migration: 20261030000005_phone_is_customer_only.sql
-- Business rule: a phone-number OTP login always belongs to a customer. Staff and
-- managers sign in with a username and password created on the web and never carry a
-- phone number, so a phone can never resolve to a staff identity.
--
-- 1) Existing staff/manager/delivery profiles lose their phone number.
-- 2) A trigger keeps it that way for future writes from any client.

BEGIN;

CREATE OR REPLACE FUNCTION public.clear_staff_phone()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = ''
AS $$
BEGIN
  IF NEW.role IS DISTINCT FROM 'customer'::public.user_role THEN
    NEW.phone := NULL;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_clear_staff_phone ON public.profiles;
CREATE TRIGGER trg_clear_staff_phone
  BEFORE INSERT OR UPDATE OF role, phone ON public.profiles
  FOR EACH ROW EXECUTE FUNCTION public.clear_staff_phone();

UPDATE public.profiles
   SET phone = NULL
 WHERE role <> 'customer'::public.user_role
   AND phone IS NOT NULL;

NOTIFY pgrst, 'reload schema';

COMMIT;

-- Migration: 20261031000007_revoke_staff_sessions.sql
-- Deleting a phone identity stops FUTURE OTP logins from resolving to a staff account,
-- but it does not invalidate sessions that already exist. A customer app that had
-- authenticated with a staff member's number kept restoring that staff session on every
-- launch, so the "This account belongs to store staff" screen came back each time.
--
-- This revokes those sessions and refresh tokens. The affected apps must sign in again,
-- and the next OTP on that number can only create a customer account.

BEGIN;

-- Belt and braces: make sure no non-customer account still holds a phone identity.
DELETE FROM auth.identities i
 USING public.profiles p
 WHERE i.user_id = p.id
   AND i.provider = 'phone'
   AND p.role <> 'customer'::public.user_role;

UPDATE auth.users u
   SET phone = NULL,
       phone_confirmed_at = NULL,
       updated_at = now()
 WHERE u.phone IS NOT NULL
   AND EXISTS (
     SELECT 1 FROM public.profiles p
     WHERE p.id = u.id AND p.role <> 'customer'::public.user_role
   );

-- Invalidate live sessions for every non-customer account.
DELETE FROM auth.refresh_tokens rt
 USING public.profiles p
 WHERE rt.user_id::text = p.id::text
   AND p.role <> 'customer'::public.user_role;

DELETE FROM auth.sessions s
 USING public.profiles p
 WHERE s.user_id = p.id
   AND p.role <> 'customer'::public.user_role;

DO $$
DECLARE
  v_phones integer;
  v_sessions integer;
BEGIN
  SELECT count(*) INTO v_phones
  FROM auth.users u
  JOIN public.profiles p ON p.id = u.id
  WHERE p.role <> 'customer'::public.user_role AND u.phone IS NOT NULL;

  SELECT count(*) INTO v_sessions
  FROM auth.sessions s
  JOIN public.profiles p ON p.id = s.user_id
  WHERE p.role <> 'customer'::public.user_role;

  RAISE NOTICE 'Non-customer accounts with a phone: %, remaining sessions: %',
    v_phones, v_sessions;
END $$;

COMMIT;

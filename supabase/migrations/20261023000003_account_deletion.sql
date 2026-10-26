-- Play Store account-deletion requirement.
-- A signed-in customer can erase their account from the app. Financial records
-- are retained in anonymised form; all direct personal data is removed.

CREATE OR REPLACE FUNCTION public.delete_my_account(p_confirmation text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_user_id uuid := auth.uid();
  v_profile public.profiles%ROWTYPE;
  v_order_count integer := 0;
  v_address_count integer := 0;
BEGIN
  IF v_user_id IS NULL THEN
    RETURN jsonb_build_object('error', 'UNAUTHENTICATED', 'message', 'Login required');
  END IF;

  IF upper(btrim(COALESCE(p_confirmation, ''))) <> 'DELETE' THEN
    RETURN jsonb_build_object(
      'error', 'CONFIRMATION_REQUIRED',
      'message', 'Type DELETE to confirm account deletion'
    );
  END IF;

  SELECT * INTO v_profile FROM public.profiles WHERE id = v_user_id FOR UPDATE;
  IF NOT FOUND OR NOT v_profile.is_active THEN
    RETURN jsonb_build_object('error', 'NOT_ACTIVE', 'message', 'This account is not active');
  END IF;

  -- Only customers can self-delete. Staff/rider accounts are provisioned and
  -- removed by a manager through the admin tooling.
  IF v_profile.role <> 'customer' THEN
    RETURN jsonb_build_object(
      'error', 'FORBIDDEN',
      'message', 'Staff accounts must be removed by the store manager'
    );
  END IF;

  -- Keep an in-flight delivery operable; the customer can delete once every
  -- order reaches a terminal state.
  IF EXISTS (
    SELECT 1
    FROM public.orders
    WHERE customer_id = v_user_id
      AND status NOT IN ('delivered', 'cancelled', 'rejected', 'delivery_failed')
  ) THEN
    RETURN jsonb_build_object(
      'error', 'ACTIVE_ORDERS',
      'message', 'Finish or cancel your active orders before deleting your account'
    );
  END IF;

  SELECT count(*) INTO v_order_count FROM public.orders WHERE customer_id = v_user_id;
  SELECT count(*) INTO v_address_count FROM public.addresses WHERE customer_id = v_user_id;

  -- Remove direct personal data. Orders and bills keep only the amounts and
  -- item snapshots needed for accounting; names, phones, notes and addresses
  -- are erased.
  DELETE FROM public.addresses WHERE customer_id = v_user_id;

  UPDATE public.orders
  SET address_snapshot = jsonb_build_object('deleted', true),
      customer_note = NULL
  WHERE customer_id = v_user_id;

  UPDATE public.bills b
  SET customer_name = NULL,
      customer_phone = NULL
  WHERE b.order_id IN (SELECT id FROM public.orders WHERE customer_id = v_user_id);

  UPDATE public.profiles
  SET display_name = 'Deleted customer',
      phone = NULL,
      login_id = NULL,
      is_active = false,
      must_change_password = false
  WHERE id = v_user_id;

  -- Free-text operational reasons can contain customer details. Keep the
  -- status transition itself but remove the free text.
  UPDATE public.order_status_events
  SET reason = NULL
  WHERE order_id IN (SELECT id FROM public.orders WHERE customer_id = v_user_id);

  -- Remove queued messages for this customer; future notification workers must
  -- not send to a deleted account.
  DELETE FROM public.notification_outbox
  WHERE order_id IN (SELECT id FROM public.orders WHERE customer_id = v_user_id);

  -- Inventory notes may repeat a customer-supplied cancellation reason.
  UPDATE public.inventory_movements
  SET note = NULL
  WHERE reference_type = 'order'
    AND reference_id IN (SELECT id FROM public.orders WHERE customer_id = v_user_id);

  -- The profile update above fires the audit trigger. Scrub profile/order/bill
  -- JSON snapshots and free-text reasons without removing the audit rows.
  UPDATE public.audit_logs
  SET before = before
        - 'display_name' - 'phone' - 'login_id' - 'address_snapshot'
        - 'customer_name' - 'customer_phone' - 'customer_note' - 'reason'
        - 'recipient_name',
      after = after
        - 'display_name' - 'phone' - 'login_id' - 'address_snapshot'
        - 'customer_name' - 'customer_phone' - 'customer_note' - 'reason'
        - 'recipient_name'
  WHERE (entity_type = 'profiles' AND entity_id = v_user_id::text)
     OR (entity_type = 'orders' AND entity_id IN (
           SELECT id::text FROM public.orders WHERE customer_id = v_user_id
         ))
     OR (entity_type = 'bills' AND entity_id IN (
           SELECT b.id::text
           FROM public.bills b
           WHERE b.order_id IN (
             SELECT id FROM public.orders WHERE customer_id = v_user_id
           )
         ));

  -- Remove credentials and sign-in identifiers. The auth row is retained only
  -- because orders reference the profile; it is banned and contains no PII.
  DELETE FROM auth.identities WHERE user_id = v_user_id;
  DELETE FROM auth.sessions WHERE user_id = v_user_id;
  UPDATE auth.users
  SET phone = NULL,
      phone_confirmed_at = NULL,
      phone_change = NULL,
      phone_change_token = NULL,
      email = NULL,
      email_confirmed_at = NULL,
      email_change = NULL,
      email_change_token_new = NULL,
      email_change_token_current = NULL,
      encrypted_password = NULL,
      raw_user_meta_data = '{}'::jsonb,
      raw_app_meta_data = '{}'::jsonb,
      banned_until = 'infinity',
      updated_at = now()
  WHERE id = v_user_id;

  INSERT INTO public.audit_logs (
    actor_id, action, entity_type, entity_id, before, after
  ) VALUES (
    v_user_id,
    'account_deleted',
    'profile',
    v_user_id::text,
    jsonb_build_object('role', v_profile.role::text),
    jsonb_build_object(
      'deleted', true,
      'orders_retained', v_order_count,
      'addresses_deleted', v_address_count
    )
  );

  RETURN jsonb_build_object(
    'success', true,
    'orders_retained', v_order_count,
    'addresses_deleted', v_address_count,
    'message', 'Your account and personal data have been deleted'
  );
END;
$$;

COMMENT ON FUNCTION public.delete_my_account(text) IS
  'Customer self-service account deletion. Erases PII, disables auth credentials and retains anonymised order/bill records for accounting.';

REVOKE ALL ON FUNCTION public.delete_my_account(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.delete_my_account(text) TO authenticated;

-- New sign-ins must not reuse a disabled customer profile. Auth already creates
-- a fresh profile for a new auth user, so no additional trigger is needed.

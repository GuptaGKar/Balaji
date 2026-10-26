-- Customer profile details and saved delivery addresses
-- Adds customer-safe profile updates, address CRUD RPCs, and one default
-- address per customer. Phone remains the verified login number.

-- 1) Provision the verified auth phone on new profiles.
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
    COALESCE(NEW.phone, NEW.raw_user_meta_data->>'phone'),
    true,
    false
  );
  RETURN NEW;
EXCEPTION WHEN unique_violation THEN
  RETURN NEW;
END;
$$;

-- Backfill existing phone-OTP customers whose profile phone was missed.
UPDATE public.profiles p
SET phone = u.phone
FROM auth.users u
WHERE p.id = u.id
  AND p.role = 'customer'::public.user_role
  AND (p.phone IS NULL OR btrim(p.phone) = '')
  AND u.phone IS NOT NULL;

-- 2) Keep exactly one default address per customer where possible.
WITH ranked AS (
  SELECT id,
         row_number() OVER (
           PARTITION BY customer_id
           ORDER BY is_default DESC, id
         ) AS position
  FROM public.addresses
)
UPDATE public.addresses a
SET is_default = (r.position = 1)
FROM ranked r
WHERE a.id = r.id
  AND a.is_default IS DISTINCT FROM (r.position = 1);

CREATE UNIQUE INDEX IF NOT EXISTS uq_addresses_one_default_per_customer
  ON public.addresses(customer_id)
  WHERE is_default;

-- 3) Customer may edit their display name. Login phone is not editable here.
CREATE OR REPLACE FUNCTION public.update_my_profile(
  p_display_name text
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_user_id uuid := auth.uid();
  v_name text := NULLIF(btrim(COALESCE(p_display_name, '')), '');
  v_profile public.profiles%ROWTYPE;
BEGIN
  IF v_user_id IS NULL THEN
    RETURN jsonb_build_object('error', 'UNAUTHENTICATED', 'message', 'Login required');
  END IF;

  IF v_name IS NULL THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Enter your name.');
  END IF;

  IF length(v_name) > 80 THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Name is too long.');
  END IF;

  UPDATE public.profiles
  SET display_name = v_name
  WHERE id = v_user_id
  RETURNING * INTO v_profile;

  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Profile not found');
  END IF;

  RETURN jsonb_build_object(
    'display_name', v_profile.display_name,
    'phone', v_profile.phone,
    'role', v_profile.role
  );
END;
$$;

REVOKE ALL ON FUNCTION public.update_my_profile(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.update_my_profile(text) TO authenticated;

-- 4) Create or update an owned address. Returns the saved address.
CREATE OR REPLACE FUNCTION public.save_my_address(
  p_id uuid,
  p_label text,
  p_recipient_name text,
  p_phone text,
  p_address_lines text,
  p_landmark text,
  p_area text,
  p_city text,
  p_state text,
  p_postal_code text,
  p_is_default boolean
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_user_id uuid := auth.uid();
  v_label text := COALESCE(NULLIF(btrim(COALESCE(p_label, '')), ''), 'Home');
  v_name text := btrim(COALESCE(p_recipient_name, ''));
  v_phone text := regexp_replace(COALESCE(p_phone, ''), '\D', '', 'g');
  v_address_lines text := btrim(COALESCE(p_address_lines, ''));
  v_landmark text := NULLIF(btrim(COALESCE(p_landmark, '')), '');
  v_area text := NULLIF(btrim(COALESCE(p_area, '')), '');
  v_city text := btrim(COALESCE(p_city, ''));
  v_state text := btrim(COALESCE(p_state, ''));
  v_postal_code text := btrim(COALESCE(p_postal_code, ''));
  v_is_default boolean := COALESCE(p_is_default, false);
  v_address public.addresses%ROWTYPE;
  v_count bigint;
BEGIN
  IF v_user_id IS NULL THEN
    RETURN jsonb_build_object('error', 'UNAUTHENTICATED', 'message', 'Login required');
  END IF;

  IF v_name = '' OR v_address_lines = '' OR v_city = '' OR v_state = '' THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Complete delivery address required');
  END IF;

  IF length(v_phone) = 12 AND left(v_phone, 2) = '91' THEN
    v_phone := right(v_phone, 10);
  ELSIF length(v_phone) = 11 AND left(v_phone, 1) = '0' THEN
    v_phone := right(v_phone, 10);
  END IF;

  IF v_phone !~ '^[6-9][0-9]{9}$' THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Enter a valid 10-digit mobile number.');
  END IF;

  IF v_postal_code !~ '^[0-9]{6}$' THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Enter a valid 6-digit PIN code.');
  END IF;

  IF length(v_label) > 40 OR length(v_name) > 80 OR length(v_address_lines) > 300
     OR length(COALESCE(v_landmark, '')) > 120 OR length(COALESCE(v_area, '')) > 80
     OR length(v_city) > 80 OR length(v_state) > 80 THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Address details are too long.');
  END IF;

  IF p_id IS NOT NULL THEN
    SELECT * INTO v_address
    FROM public.addresses
    WHERE id = p_id AND customer_id = v_user_id
    FOR UPDATE;

    IF NOT FOUND THEN
      RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Address not found');
    END IF;

    -- Keep an existing default default even if the client did not re-check it.
    v_is_default := v_is_default OR v_address.is_default;

    IF v_is_default THEN
      UPDATE public.addresses
      SET is_default = false
      WHERE customer_id = v_user_id AND id <> p_id AND is_default;
    END IF;

    UPDATE public.addresses
    SET label = v_label,
        recipient_name = v_name,
        phone = v_phone,
        address_lines = v_address_lines,
        landmark = v_landmark,
        area = v_area,
        city = v_city,
        state = v_state,
        postal_code = v_postal_code,
        is_default = v_is_default
    WHERE id = p_id AND customer_id = v_user_id
    RETURNING * INTO v_address;
  ELSE
    SELECT count(*) INTO v_count
    FROM public.addresses
    WHERE customer_id = v_user_id;

    v_is_default := v_is_default OR v_count = 0;

    IF v_is_default THEN
      UPDATE public.addresses
      SET is_default = false
      WHERE customer_id = v_user_id AND is_default;
    END IF;

    INSERT INTO public.addresses (
      customer_id, label, recipient_name, phone, address_lines, landmark,
      area, city, state, postal_code, is_default
    ) VALUES (
      v_user_id, v_label, v_name, v_phone, v_address_lines, v_landmark,
      v_area, v_city, v_state, v_postal_code, v_is_default
    )
    RETURNING * INTO v_address;
  END IF;

  RETURN jsonb_build_object('address', to_jsonb(v_address));
END;
$$;

REVOKE ALL ON FUNCTION public.save_my_address(uuid, text, text, text, text, text, text, text, text, text, boolean) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.save_my_address(uuid, text, text, text, text, text, text, text, text, text, boolean) TO authenticated;

-- 5) Move the default marker without resending the whole address.
CREATE OR REPLACE FUNCTION public.set_default_my_address(
  p_id uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_user_id uuid := auth.uid();
  v_address public.addresses%ROWTYPE;
BEGIN
  IF v_user_id IS NULL THEN
    RETURN jsonb_build_object('error', 'UNAUTHENTICATED', 'message', 'Login required');
  END IF;

  SELECT * INTO v_address
  FROM public.addresses
  WHERE id = p_id AND customer_id = v_user_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Address not found');
  END IF;

  UPDATE public.addresses
  SET is_default = false
  WHERE customer_id = v_user_id AND id <> p_id AND is_default;

  UPDATE public.addresses
  SET is_default = true
  WHERE id = p_id AND customer_id = v_user_id
  RETURNING * INTO v_address;

  RETURN jsonb_build_object('success', true, 'address', to_jsonb(v_address));
END;
$$;

REVOKE ALL ON FUNCTION public.set_default_my_address(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.set_default_my_address(uuid) TO authenticated;

-- 6) Delete an owned address and promote another default when needed.
CREATE OR REPLACE FUNCTION public.delete_my_address(
  p_id uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_user_id uuid := auth.uid();
  v_was_default boolean;
  v_new_default uuid;
BEGIN
  IF v_user_id IS NULL THEN
    RETURN jsonb_build_object('error', 'UNAUTHENTICATED', 'message', 'Login required');
  END IF;

  SELECT is_default INTO v_was_default
  FROM public.addresses
  WHERE id = p_id AND customer_id = v_user_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Address not found');
  END IF;

  DELETE FROM public.addresses
  WHERE id = p_id AND customer_id = v_user_id;

  IF v_was_default THEN
    SELECT id INTO v_new_default
    FROM public.addresses
    WHERE customer_id = v_user_id
    ORDER BY is_default DESC, id
    LIMIT 1;

    IF v_new_default IS NOT NULL THEN
      UPDATE public.addresses
      SET is_default = true
      WHERE id = v_new_default AND customer_id = v_user_id;
    END IF;
  END IF;

  RETURN jsonb_build_object('success', true, 'default_address_id', v_new_default);
END;
$$;

REVOKE ALL ON FUNCTION public.delete_my_address(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.delete_my_address(uuid) TO authenticated;

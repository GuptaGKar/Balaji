-- Delivery coordinates for saved addresses and order snapshots.
-- The Android app captures a fix on demand and stores it with the address;
-- place_order copies lat/lng into address_snapshot for the rider view.

-- Replace the 11-argument RPC; defaults keep older clients working.
DROP FUNCTION IF EXISTS public.save_my_address(uuid, text, text, text, text, text, text, text, text, text, boolean);

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
  p_is_default boolean,
  p_lat double precision DEFAULT NULL,
  p_lng double precision DEFAULT NULL
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
  v_lat double precision := p_lat;
  v_lng double precision := p_lng;
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

  IF (v_lat IS NULL) <> (v_lng IS NULL) THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Location needs both latitude and longitude.');
  END IF;

  IF v_lat IS NOT NULL AND (v_lat < -90 OR v_lat > 90 OR v_lng < -180 OR v_lng > 180) THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Invalid location coordinates.');
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

    -- Keep the stored coordinates when the edit does not include a new fix.
    IF v_lat IS NULL OR v_lng IS NULL THEN
      v_lat := v_address.lat;
      v_lng := v_address.lng;
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
        lat = v_lat,
        lng = v_lng,
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
      area, city, state, postal_code, lat, lng, is_default
    ) VALUES (
      v_user_id, v_label, v_name, v_phone, v_address_lines, v_landmark,
      v_area, v_city, v_state, v_postal_code, v_lat, v_lng, v_is_default
    )
    RETURNING * INTO v_address;
  END IF;

  RETURN jsonb_build_object('address', to_jsonb(v_address));
END;
$$;

REVOKE ALL ON FUNCTION public.save_my_address(uuid, text, text, text, text, text, text, text, text, text, boolean, double precision, double precision) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.save_my_address(uuid, text, text, text, text, text, text, text, text, text, boolean, double precision, double precision) TO authenticated;

-- place_order keeps the same signature; CREATE OR REPLACE below only adds
-- lat/lng to the immutable address snapshot.
CREATE OR REPLACE FUNCTION public.place_order(
  p_payload jsonb,
  p_idempotency_key text
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_user_id uuid := auth.uid();
  v_profile public.profiles%ROWTYPE;
  v_items jsonb := p_payload->'items';
  v_address jsonb := p_payload->'address';
  v_customer_note text := p_payload->>'customer_note';
  v_payment_method public.payment_method := COALESCE(p_payload->>'payment_method', 'cod')::public.payment_method;
  v_quote jsonb;
  v_order_id uuid;
  v_public_order_number text;
  v_address_snapshot jsonb;
  v_fee_breakdown jsonb;
  v_item jsonb;
  v_item2 jsonb;
  v_product public.products%ROWTYPE;
  v_qty_base bigint;
  v_qty_text text;
  v_new_stock bigint;
  v_unit_price_paise bigint;
  v_line_total_paise bigint;
  v_display_qty text;
  v_order_status public.order_status := 'pending_review';
  v_payment_status public.payment_status := 'unpaid';
  v_idempotency_conflict boolean := false;
  v_settings public.store_settings%ROWTYPE;
BEGIN
  -- 1. Auth & role check
  IF v_user_id IS NULL THEN
    RETURN jsonb_build_object('error', 'UNAUTHENTICATED', 'message', 'Login required');
  END IF;

  SELECT * INTO v_profile FROM public.profiles WHERE id = v_user_id;
  IF NOT FOUND OR v_profile.role <> 'customer' OR NOT v_profile.is_active THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Customer access required');
  END IF;

  -- 2. Idempotency: insert or detect conflict
  BEGIN
    INSERT INTO public.idempotency_keys (user_id, scope, key, request_hash)
    VALUES (v_user_id, 'place_order', p_idempotency_key, md5(p_payload::text));
  EXCEPTION WHEN unique_violation THEN
    -- Check if same request (retry) or different (conflict)
    IF EXISTS (
      SELECT 1 FROM public.idempotency_keys
      WHERE user_id = v_user_id AND scope = 'place_order' AND key = p_idempotency_key
        AND request_hash = md5(p_payload::text)
    ) THEN
      -- Return cached response
      RETURN (SELECT response FROM public.idempotency_keys
              WHERE user_id = v_user_id AND scope = 'place_order' AND key = p_idempotency_key);
    ELSE
      RETURN jsonb_build_object('error', 'IDEMPOTENCY_MISMATCH',
        'message', 'Idempotency key reused with different payload');
    END IF;
  END;

  -- 3. Store check
  SELECT * INTO v_settings FROM public.store_settings;
  IF NOT v_settings.store_open THEN
    RETURN jsonb_build_object('error', 'STORE_CLOSED', 'message', 'Store is currently closed');
  END IF;
  -- TODO: check weekly_hours against now() in store timezone

  -- 4. Address validation
  IF v_address IS NULL OR v_address->>'postal_code' IS NULL THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Delivery address required');
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.service_areas
    WHERE active = true
      AND kind = 'pincode'
      AND lower(value) = lower(v_address->>'postal_code')
  ) THEN
    RETURN jsonb_build_object('error', 'OUT_OF_AREA',
      'message', 'Delivery not available to this PIN code');
  END IF;

  -- 5. Lock products (SELECT FOR UPDATE ordered by id for deadlock avoidance)
  FOR v_item IN SELECT value FROM jsonb_array_elements(v_items) ORDER BY (value->>'product_id')::uuid
  LOOP
    SELECT * INTO v_product
    FROM public.products
    WHERE id = (v_item->>'product_id')::uuid
    FOR UPDATE;

    IF NOT FOUND THEN
      RETURN jsonb_build_object('error', 'PRODUCT_UNAVAILABLE', 'message', 'Product not found');
    END IF;

    IF NOT v_product.active OR NOT v_product.available THEN
      RETURN jsonb_build_object('error', 'PRODUCT_UNAVAILABLE', 'message', v_product.name || ' is not available');
    END IF;

    v_qty_text := (v_item::jsonb)->>'qty_base';
    -- For PACKED, qty must be a valid integer string
    IF v_product.quantity_type = 'PACKED' THEN
      IF v_qty_text !~ '^\d+$' THEN
        RETURN jsonb_build_object('error', 'QTY_INVALID', 'message', 'Packed quantity must be a positive integer');
      END IF;
      v_qty_base := v_qty_text::bigint;
    ELSE
      -- For WEIGHTED, qty can be numeric but must be integer grams
      v_qty_base := v_qty_text::bigint;
    END IF;

    -- Validate qty
    IF v_product.quantity_type = 'PACKED' THEN
      IF v_qty_base < v_product.min_qty OR v_qty_base > COALESCE(v_product.max_qty, v_qty_base) THEN
        RETURN jsonb_build_object('error', 'QTY_INVALID', 'message', 'Invalid quantity for ' || v_product.name);
      END IF;
    ELSE
      IF v_qty_base % v_product.step_qty <> 0
         OR v_qty_base < v_product.min_qty
         OR v_qty_base > COALESCE(v_product.max_qty, v_qty_base) THEN
        RETURN jsonb_build_object('error', 'QTY_INVALID', 'message', 'Invalid weight for ' || v_product.name);
      END IF;
    END IF;

    IF v_product.stock_qty < v_qty_base THEN
      RETURN jsonb_build_object('error', 'INSUFFICIENT_STOCK', 'message', v_product.name || ' has only ' || v_product.stock_qty || ' available');
    END IF;
  END LOOP;

  -- 6. Compute pricing via quote_cart (same logic)
  v_quote := public.quote_cart(v_items);

  -- 7. Optional max_order_paise check
  IF v_settings.max_order_paise IS NOT NULL AND v_settings.max_order_paise > 0
     AND (v_quote->>'grand_total_paise')::bigint > v_settings.max_order_paise THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Order exceeds maximum allowed value');
  END IF;

  -- 8. Payment method check
  IF v_payment_method = 'online' AND NOT v_settings.online_payment_enabled THEN
    RETURN jsonb_build_object('error', 'ONLINE_PAYMENT_DISABLED', 'message', 'Online payments not enabled');
  END IF;
  IF v_payment_method = 'online' THEN
    v_payment_status := 'pending';
  END IF;

  -- 9. Create order + items + events + stock movements in single transaction
  v_public_order_number := 'BPS-' || to_char(now(), 'YYMMDD') || '-' || lpad(floor(random() * 10000)::text, 4, '0');
  -- Ensure uniqueness (extremely unlikely collision, but safe)
  WHILE EXISTS (SELECT 1 FROM public.orders WHERE public_order_number = v_public_order_number) LOOP
    v_public_order_number := 'BPS-' || to_char(now(), 'YYMMDD') || '-' || lpad(floor(random() * 10000)::text, 4, '0');
  END LOOP;

  -- Address snapshot
  v_address_snapshot := jsonb_build_object(
    'recipient_name', v_address->>'recipient_name',
    'phone', v_address->>'phone',
    'address_lines', v_address->>'address_lines',
    'landmark', v_address->>'landmark',
    'area', v_address->>'area',
    'city', v_address->>'city',
    'state', v_address->>'state',
    'postal_code', v_address->>'postal_code',
    'lat', CASE WHEN v_address->>'lat' IS NOT NULL THEN (v_address->>'lat')::double precision END,
    'lng', CASE WHEN v_address->>'lng' IS NOT NULL THEN (v_address->>'lng')::double precision END
  );

  -- Fee breakdown for historical explainability
  v_fee_breakdown := jsonb_build_object(
    'delivery_charge_threshold_paise', v_settings.delivery_charge_threshold_paise,
    'delivery_fee_paise', v_settings.delivery_fee_paise,
    'free_delivery_threshold_paise', v_settings.free_delivery_threshold_paise,
    'mid_tier_fee_paise', v_settings.mid_tier_fee_paise,
    'eligible_subtotal_paise', v_quote->>'item_subtotal_paise',
    'delivery_fee_applied_paise', v_quote->>'delivery_fee_paise',
    'rule_note', v_quote->>'fee_rule_note'
  );

  INSERT INTO public.orders (
    public_order_number, customer_id, address_snapshot, status, payment_method,
    payment_status, item_subtotal_paise, discount_paise, delivery_fee_paise,
    tax_paise, grand_total_paise, fee_breakdown, customer_note, idempotency_key
  ) VALUES (
    v_public_order_number, v_user_id, v_address_snapshot, v_order_status, v_payment_method,
    v_payment_status,
    (v_quote->>'item_subtotal_paise')::bigint,
    (v_quote->>'discount_paise')::bigint,
    (v_quote->>'delivery_fee_paise')::bigint,
    (v_quote->>'tax_paise')::bigint,
    (v_quote->>'grand_total_paise')::bigint,
    v_fee_breakdown, v_customer_note, p_idempotency_key
  ) RETURNING id INTO v_order_id;

  -- Order items + stock decrement + inventory movements
  FOR v_item2 IN SELECT value FROM jsonb_array_elements(v_items)
  LOOP
    SELECT * INTO v_product
    FROM public.products
    WHERE id = (v_item2->>'product_id')::uuid;

    v_qty_base := (v_item2->>'qty_base')::bigint;
    v_unit_price_paise := v_product.price_paise;
    v_line_total_paise := (2 * v_unit_price_paise * v_qty_base + v_product.price_basis_qty)
                            / (2 * v_product.price_basis_qty);

    -- Display qty
    IF v_product.quantity_type = 'WEIGHTED' THEN
      IF v_product.price_basis_qty = 1000 THEN
        v_display_qty := to_char(v_qty_base / 1000.0, 'FM999999990.000') || ' kg';
      ELSE
        v_display_qty := v_qty_base || ' g';
      END IF;
    ELSE
      v_display_qty := v_qty_base || ' ' || v_product.unit_label;
    END IF;

    INSERT INTO public.order_items (
      order_id, product_id, sku_snapshot, name_snapshot, quantity_type_snapshot,
      unit_label_snapshot, price_basis_qty_snapshot, qty_base, unit_price_paise, line_total_paise
    ) VALUES (
      v_order_id, v_product.id, v_product.sku, v_product.name, v_product.quantity_type,
      v_product.unit_label, v_product.price_basis_qty, v_qty_base, v_unit_price_paise, v_line_total_paise
    );

    -- Decrement stock + movement
    WITH updated AS (
      UPDATE public.products SET stock_qty = stock_qty - v_qty_base WHERE sku = v_product.sku
      RETURNING stock_qty
    )
    SELECT stock_qty FROM updated INTO v_new_stock;

    INSERT INTO public.inventory_movements (
      product_id, delta_qty, reason, reference_type, reference_id, actor_id
    ) VALUES (
      v_product.id, -v_qty_base, 'order_placed', 'order', v_order_id, v_user_id
    );
  END LOOP;

  -- Status event
  INSERT INTO public.order_status_events (order_id, from_status, to_status, actor_id, reason)
  VALUES (v_order_id, NULL, 'pending_review', v_user_id, 'Order placed by customer');

  -- Store idempotency response
  UPDATE public.idempotency_keys
  SET response = jsonb_build_object(
    'order_id', v_order_id,
    'public_order_number', v_public_order_number,
    'breakdown', v_quote
  )
  WHERE user_id = v_user_id AND scope = 'place_order' AND key = p_idempotency_key;

  -- Enqueue notification (outside money transaction)
  INSERT INTO public.notification_outbox (order_id, channel, template, status)
  VALUES (v_order_id, 'in_app', 'order_placed', 'pending');

  RETURN jsonb_build_object(
    'order_id', v_order_id,
    'public_order_number', v_public_order_number,
    'breakdown', v_quote
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.place_order(jsonb, text) TO authenticated;

REVOKE EXECUTE ON FUNCTION public.place_order(jsonb, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.place_order(jsonb, text) TO authenticated;

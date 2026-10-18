-- M4 (cont): place_order with idempotency, stock locking, movements, status events
-- IMPLEMENTATION_PLAN.md §4.7, §4.8, §4.9

-- 1) place_order — single transaction, server-authoritative pricing
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
    'postal_code', v_address->>'postal_code'
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
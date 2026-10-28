-- Migration: 20261028000001_fix_place_order.sql
-- Description:
--   20261026000006_coupons.sql replaced place_order with a body that could never
--   commit an order. Two of its statements target objects that do not exist in this
--   schema, so every checkout raised an exception at the last step and the whole RPC
--   (order row, items, stock changes) rolled back:
--     1. public.stock_movements does not exist; movements live in
--        public.inventory_movements (product_id, delta_qty, reason, reference_type,
--        reference_id, actor_id, note).
--     2. order_status_events has no note/created_by columns; it has actor_id and reason.
--
--   That rewrite also dropped checks the previous definition performed. This migration
--   restores them and keeps the coupon support:
--     * the caller must be an active customer profile;
--     * the delivery PIN code must be covered when service areas are configured;
--     * pack min/max and weight step-multiple validation;
--     * idempotency through public.idempotency_keys (per user, payload hash compared);
--     * notification_outbox row for the in-app "order placed" notification.
--
--   A retry that reuses a key whose previous attempt never produced a response is now
--   allowed to complete instead of being answered with NULL.

BEGIN;

CREATE OR REPLACE FUNCTION public.place_order(
  p_payload jsonb,
  p_idempotency_key text DEFAULT NULL
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
  v_product public.products%ROWTYPE;
  v_qty_base bigint;
  v_qty_text text;
  v_order_status public.order_status := 'pending_review';
  v_payment_status public.payment_status := 'unpaid';
  v_settings public.store_settings%ROWTYPE;
  v_request_hash text := md5(p_payload::text);
  v_existing_hash text;
  v_cached_response jsonb;
  v_coupon_code text;
  v_coupon_id uuid := NULL;
BEGIN
  -- 1. Authentication and role
  IF v_user_id IS NULL THEN
    RETURN jsonb_build_object('error', 'UNAUTHENTICATED', 'message', 'Login required');
  END IF;

  SELECT * INTO v_profile FROM public.profiles WHERE id = v_user_id;
  IF NOT FOUND OR v_profile.role <> 'customer' OR NOT v_profile.is_active THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Customer access required');
  END IF;

  IF p_payload IS NULL THEN
    RETURN jsonb_build_object('error', 'EMPTY_CART', 'message', 'Cart has no items');
  END IF;

  -- 2. Idempotency: one row per (user, scope, key) with the payload hash
  IF p_idempotency_key IS NOT NULL AND btrim(p_idempotency_key) <> '' THEN
    BEGIN
      INSERT INTO public.idempotency_keys (user_id, scope, key, request_hash)
      VALUES (v_user_id, 'place_order', p_idempotency_key, v_request_hash);
    EXCEPTION WHEN unique_violation THEN
      SELECT request_hash, response INTO v_existing_hash, v_cached_response
      FROM public.idempotency_keys
      WHERE user_id = v_user_id AND scope = 'place_order' AND key = p_idempotency_key;

      IF v_existing_hash IS DISTINCT FROM v_request_hash THEN
        RETURN jsonb_build_object('error', 'IDEMPOTENCY_MISMATCH',
          'message', 'Idempotency key reused with different payload');
      END IF;

      IF v_cached_response IS NOT NULL THEN
        RETURN v_cached_response;
      END IF;

      -- The earlier attempt with this key returned a validation error, so no response
      -- was stored. Clear the stale row instead of answering the retry with NULL.
      DELETE FROM public.idempotency_keys
      WHERE user_id = v_user_id AND scope = 'place_order' AND key = p_idempotency_key;
      INSERT INTO public.idempotency_keys (user_id, scope, key, request_hash)
      VALUES (v_user_id, 'place_order', p_idempotency_key, v_request_hash);
    END;
  END IF;

  -- 3. Store state
  SELECT * INTO v_settings FROM public.store_settings;
  IF NOT v_settings.store_open THEN
    RETURN jsonb_build_object('error', 'STORE_CLOSED', 'message', 'Store is currently closed');
  END IF;

  -- 4. Address and service area
  IF v_address IS NULL OR v_address->>'postal_code' IS NULL THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Delivery address required');
  END IF;

  -- Only restrict PIN codes when the store has actually configured service areas;
  -- an empty table must not reject every order.
  IF EXISTS (
    SELECT 1 FROM public.service_areas WHERE active = true AND kind = 'pincode'
  ) AND NOT EXISTS (
    SELECT 1 FROM public.service_areas
    WHERE active = true
      AND kind = 'pincode'
      AND lower(value) = lower(v_address->>'postal_code')
  ) THEN
    RETURN jsonb_build_object('error', 'OUT_OF_AREA',
      'message', 'Delivery not available to this PIN code');
  END IF;

  IF v_items IS NULL OR jsonb_array_length(v_items) = 0 THEN
    RETURN jsonb_build_object('error', 'EMPTY_CART', 'message', 'Cart has no items');
  END IF;

  -- 5. Lock products in a stable order, then validate availability, quantity and stock
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

    v_qty_text := v_item->>'qty_base';
    -- Quantities are integer base units (grams or packs); reject anything else before
    -- the cast so the caller gets QTY_INVALID instead of a cast error.
    IF v_qty_text IS NULL OR v_qty_text !~ '^\d+$' THEN
      RETURN jsonb_build_object('error', 'QTY_INVALID', 'message', 'Quantity must be a positive whole number');
    END IF;

    v_qty_base := v_qty_text::bigint;
    IF v_qty_base <= 0 THEN
      RETURN jsonb_build_object('error', 'QTY_INVALID', 'message', 'Quantity must be a positive whole number');
    END IF;

    IF v_product.quantity_type = 'PACKED' THEN
      IF v_qty_base < v_product.min_qty OR v_qty_base > COALESCE(v_product.max_qty, v_qty_base) THEN
        RETURN jsonb_build_object('error', 'QTY_INVALID', 'message', 'Invalid quantity for ' || v_product.name);
      END IF;
    ELSE
      IF (v_product.step_qty > 0 AND v_qty_base % v_product.step_qty <> 0)
         OR v_qty_base < v_product.min_qty
         OR v_qty_base > COALESCE(v_product.max_qty, v_qty_base) THEN
        RETURN jsonb_build_object('error', 'QTY_INVALID', 'message', 'Invalid weight for ' || v_product.name);
      END IF;
    END IF;

    IF v_product.stock_qty < v_qty_base THEN
      RETURN jsonb_build_object('error', 'INSUFFICIENT_STOCK', 'message', v_product.name || ' has only ' || v_product.stock_qty || ' available');
    END IF;
  END LOOP;

  -- 6. Server-side pricing with the optional coupon
  v_coupon_code := NULLIF(btrim(p_payload->>'coupon_code'), '');
  v_quote := public.quote_cart(v_items, v_coupon_code);

  IF v_coupon_code IS NOT NULL AND (v_quote->>'coupon_applied')::boolean IS NOT TRUE THEN
    RETURN jsonb_build_object(
      'error', 'INVALID_COUPON',
      'message', COALESCE(v_quote->>'coupon_error', 'Coupon could not be applied.')
    );
  END IF;

  IF (v_quote->>'coupon_applied')::boolean IS TRUE THEN
    SELECT id INTO v_coupon_id FROM public.coupons WHERE code = upper(btrim(v_coupon_code));
  END IF;

  -- 7. Maximum order value
  IF v_settings.max_order_paise IS NOT NULL AND v_settings.max_order_paise > 0
     AND (v_quote->>'grand_total_paise')::bigint > v_settings.max_order_paise THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Order exceeds maximum allowed value');
  END IF;

  -- 8. Payment method
  IF v_payment_method = 'online' AND NOT v_settings.online_payment_enabled THEN
    RETURN jsonb_build_object('error', 'ONLINE_PAYMENT_DISABLED', 'message', 'Online payments not enabled');
  END IF;
  IF v_payment_method = 'online' THEN
    v_payment_status := 'pending';
  END IF;

  -- 9. Order header
  v_public_order_number := 'BPS-' || to_char(now(), 'YYMMDD') || '-' || lpad(floor(random() * 10000)::text, 4, '0');
  WHILE EXISTS (SELECT 1 FROM public.orders WHERE public_order_number = v_public_order_number) LOOP
    v_public_order_number := 'BPS-' || to_char(now(), 'YYMMDD') || '-' || lpad(floor(random() * 10000)::text, 4, '0');
  END LOOP;

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

  v_fee_breakdown := jsonb_build_object(
    'delivery_charge_threshold_paise', v_settings.delivery_charge_threshold_paise,
    'delivery_fee_paise', v_settings.delivery_fee_paise,
    'free_delivery_threshold_paise', v_settings.free_delivery_threshold_paise,
    'mid_tier_fee_paise', v_settings.mid_tier_fee_paise,
    'eligible_subtotal_paise', v_quote->>'item_subtotal_paise',
    'delivery_fee_applied_paise', v_quote->>'delivery_fee_paise',
    'discount_applied_paise', v_quote->>'discount_paise',
    'coupon_code', v_quote->>'coupon_code',
    'rule_note', v_quote->>'fee_rule_note'
  );

  INSERT INTO public.orders (
    public_order_number, customer_id, address_snapshot, status, payment_method,
    payment_status, item_subtotal_paise, discount_paise, delivery_fee_paise,
    tax_paise, grand_total_paise, fee_breakdown, customer_note, idempotency_key,
    coupon_id, coupon_code
  ) VALUES (
    v_public_order_number, v_user_id, v_address_snapshot, v_order_status, v_payment_method,
    v_payment_status,
    (v_quote->>'item_subtotal_paise')::bigint,
    (v_quote->>'discount_paise')::bigint,
    (v_quote->>'delivery_fee_paise')::bigint,
    (v_quote->>'tax_paise')::bigint,
    (v_quote->>'grand_total_paise')::bigint,
    v_fee_breakdown, v_customer_note,
    NULLIF(btrim(p_idempotency_key), ''),
    v_coupon_id,
    CASE WHEN v_coupon_id IS NOT NULL THEN upper(btrim(v_coupon_code)) ELSE NULL END
  ) RETURNING id INTO v_order_id;

  -- 10. Lines, stock decrement and inventory movements
  FOR v_item IN SELECT value FROM jsonb_array_elements(v_items)
  LOOP
    SELECT * INTO v_product
    FROM public.products
    WHERE id = (v_item->>'product_id')::uuid;

    v_qty_base := (v_item->>'qty_base')::bigint;

    INSERT INTO public.order_items (
      order_id, product_id, sku_snapshot, name_snapshot, quantity_type_snapshot,
      unit_label_snapshot, price_basis_qty_snapshot, qty_base, unit_price_paise, line_total_paise
    ) VALUES (
      v_order_id, v_product.id, v_product.sku, v_product.name, v_product.quantity_type,
      v_product.unit_label, v_product.price_basis_qty, v_qty_base, v_product.price_paise,
      (2 * v_product.price_paise * v_qty_base + v_product.price_basis_qty)
        / (2 * v_product.price_basis_qty)
    );

    UPDATE public.products
    SET stock_qty = stock_qty - v_qty_base,
        updated_at = now()
    WHERE id = v_product.id;

    INSERT INTO public.inventory_movements (
      product_id, delta_qty, reason, reference_type, reference_id, actor_id, note
    ) VALUES (
      v_product.id, -v_qty_base, 'order_placed'::public.inventory_reason,
      'order', v_order_id, v_user_id, 'Order ' || v_public_order_number
    );
  END LOOP;

  -- 11. Status event, coupon usage, idempotency response and notification
  INSERT INTO public.order_status_events (order_id, from_status, to_status, actor_id, reason)
  VALUES (v_order_id, NULL, v_order_status, v_user_id, 'Order placed by customer');

  IF v_coupon_id IS NOT NULL THEN
    UPDATE public.coupons
    SET times_used = times_used + 1,
        updated_at = now()
    WHERE id = v_coupon_id;
  END IF;

  IF p_idempotency_key IS NOT NULL AND btrim(p_idempotency_key) <> '' THEN
    UPDATE public.idempotency_keys
    SET response = jsonb_build_object(
      'order_id', v_order_id,
      'public_order_number', v_public_order_number,
      'breakdown', v_quote
    )
    WHERE user_id = v_user_id AND scope = 'place_order' AND key = p_idempotency_key;
  END IF;

  INSERT INTO public.notification_outbox (order_id, channel, template, status)
  VALUES (v_order_id, 'in_app', 'order_placed', 'pending');

  RETURN jsonb_build_object(
    'order_id', v_order_id,
    'public_order_number', v_public_order_number,
    'breakdown', v_quote
  );
END;
$$;

REVOKE ALL ON FUNCTION public.place_order(jsonb, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.place_order(jsonb, text) TO authenticated;

-- PostgREST caches the RPC catalogue; reload so the replacement is used immediately.
NOTIFY pgrst, 'reload schema';

COMMIT;

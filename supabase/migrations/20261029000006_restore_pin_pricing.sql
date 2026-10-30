-- Migration: 20261029000006_restore_pin_pricing.sql
-- Reverts the delivery-zone pricing back to the PIN-based system, on request.
-- The zone migrations (003/004/005) were an abandoned experiment: the files are no
-- longer in the repository, but the live project applied them once, which is why this
-- migration also drops the zone functions.
--   * quote_cart(jsonb, text, text) with p_pin_code again (the 5-argument zone version
--     is dropped, so there is exactly one overload and no PostgREST ambiguity);
--   * compute_delivery_fee_for_pin is recreated and is the single fee authority;
--   * place_order quotes with the address postal code again;
--   * PIN-restricted coupons work again (coupons.pin_codes was never dropped).
-- Any zone objects the experiment left in the live database are unused from here on;
-- a fresh database never creates them. Coupons that the experiment deactivated are NOT
-- re-activated (their codes were printed in its NOTICE) - re-enable them in the
-- dashboard if they are still wanted.

BEGIN;

DROP FUNCTION IF EXISTS public.quote_cart(jsonb, text, uuid, double precision, double precision);
DROP FUNCTION IF EXISTS public.quote_delivery_pricing(uuid, double precision, double precision, bigint);
DROP FUNCTION IF EXISTS public.resolve_delivery_zone(double precision, double precision);

CREATE OR REPLACE FUNCTION public.compute_delivery_fee_for_pin(
  p_eligible_subtotal bigint,
  p_pin_code text
)
RETURNS TABLE (
  fee_paise bigint,
  rule_note text,
  below_charge_threshold boolean,
  amount_to_free_delivery bigint
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_settings public.store_settings%ROWTYPE;
  v_area public.service_areas%ROWTYPE;
  v_fee bigint := 0;
  v_note text := '';
  v_below boolean := false;
  v_to_free bigint := 0;
  v_charge_threshold bigint;
  v_free_threshold bigint;
BEGIN
  SELECT * INTO v_settings FROM public.store_settings;

  IF p_pin_code IS NOT NULL AND btrim(p_pin_code) <> '' THEN
    SELECT * INTO v_area
    FROM public.service_areas
    WHERE active = true
      AND kind = 'pincode'
      AND lower(value) = lower(btrim(p_pin_code))
    LIMIT 1;
  END IF;

  v_charge_threshold := COALESCE(v_area.delivery_charge_threshold_paise, v_settings.delivery_charge_threshold_paise);
  v_free_threshold := COALESCE(v_area.free_delivery_threshold_paise, v_settings.free_delivery_threshold_paise);

  IF v_charge_threshold IS NULL OR v_charge_threshold = 0 THEN
    RETURN QUERY SELECT 0::bigint, 'No delivery-charge threshold configured', false, 0::bigint;
    RETURN;
  END IF;

  IF p_eligible_subtotal < v_charge_threshold THEN
    v_fee := COALESCE(v_area.delivery_fee_paise, v_settings.delivery_fee_paise);
    v_below := true;
    v_note := CASE WHEN v_area.id IS NOT NULL AND v_area.delivery_fee_paise IS NOT NULL
                   THEN 'Delivery fee for PIN ' || v_area.value || ' applied (below threshold)'
                   ELSE 'Delivery fee applied (below delivery-charge threshold)' END;
    IF v_free_threshold IS NOT NULL AND v_free_threshold > 0 THEN
      v_to_free := GREATEST(v_free_threshold - p_eligible_subtotal, 0);
      v_note := v_note || '; add ' || public.paise_to_rupees(v_to_free) || ' for free delivery';
    END IF;
  ELSIF v_free_threshold IS NOT NULL
        AND v_free_threshold > 0
        AND p_eligible_subtotal < v_free_threshold THEN
    v_fee := COALESCE(v_settings.mid_tier_fee_paise, COALESCE(v_area.delivery_fee_paise, v_settings.delivery_fee_paise));
    v_below := true;
    v_note := 'Mid-tier delivery fee applied';
    v_to_free := GREATEST(v_free_threshold - p_eligible_subtotal, 0);
    v_note := v_note || '; add ' || public.paise_to_rupees(v_to_free) || ' for free delivery';
  ELSE
    v_fee := 0;
    v_below := false;
    v_note := 'Free delivery (at/above threshold)';
    v_to_free := 0;
  END IF;

  RETURN QUERY SELECT v_fee, v_note, v_below, v_to_free;
END;
$$;

CREATE OR REPLACE FUNCTION public.quote_cart(
  p_items jsonb,
  p_coupon_code text DEFAULT NULL,
  p_pin_code text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_item jsonb;
  v_product public.products%ROWTYPE;
  v_qty_base bigint;
  v_unit_price_paise bigint;
  v_line_total bigint;
  v_display_qty text;
  v_available boolean;
  v_error text;
  v_lines jsonb := '[]'::jsonb;
  v_item_subtotal bigint := 0;
  v_category_subtotal bigint := 0;
  v_discount_paise bigint := 0;
  v_delivery_fee_paise bigint := 0;
  v_original_delivery_fee_paise bigint := 0;
  v_tax_paise bigint := 0;
  v_grand_total bigint := 0;
  v_fee_info record;
  v_settings public.store_settings%ROWTYPE;

  -- Coupon fields
  v_clean_code text;
  v_coupon public.coupons%ROWTYPE;
  v_coupon_applied boolean := false;
  v_coupon_error text := NULL;
BEGIN
  SELECT * INTO v_settings FROM public.store_settings;

  -- Process line items
  FOR v_item IN SELECT * FROM jsonb_array_elements(p_items)
  LOOP
    v_available := true;
    v_error := NULL;

    SELECT * INTO v_product
    FROM public.products
    WHERE id = (v_item->>'product_id')::uuid;

    IF NOT FOUND THEN
      v_available := false;
      v_error := 'Product not found';
      v_qty_base := 0;
      v_unit_price_paise := 0;
      v_line_total := 0;
      v_display_qty := '0';
    ELSE
      v_qty_base := (v_item->>'qty_base')::bigint;

      -- Quantity rules stay server-side: pack limits and weight step multiples.
      IF v_qty_base <= 0 THEN
        v_available := false;
        v_error := 'Quantity must be positive';
      ELSIF v_product.quantity_type = 'PACKED' THEN
        IF v_qty_base < v_product.min_qty
           OR v_qty_base > COALESCE(v_product.max_qty, v_qty_base) THEN
          v_available := false;
          v_error := 'Invalid pack quantity';
        END IF;
      ELSE
        IF v_product.step_qty > 0 AND v_qty_base % v_product.step_qty <> 0 THEN
          v_available := false;
          v_error := 'Quantity must be multiple of step (' || v_product.step_qty || 'g)';
        ELSIF v_qty_base < v_product.min_qty
              OR v_qty_base > COALESCE(v_product.max_qty, v_qty_base) THEN
          v_available := false;
          v_error := 'Quantity out of range';
        END IF;
      END IF;

      IF v_product.stock_qty < v_qty_base THEN
        v_available := false;
        v_error := COALESCE(v_error, '') || 'Only ' || v_product.stock_qty || ' in stock';
      END IF;

      IF NOT v_product.active OR NOT v_product.available THEN
        v_available := false;
        v_error := COALESCE(v_error, '') || 'Product unavailable';
      END IF;

      v_unit_price_paise := v_product.price_paise;
      v_line_total := (2 * v_unit_price_paise * v_qty_base + v_product.price_basis_qty)
                        / (2 * v_product.price_basis_qty);

      -- Display quantity formatting
      IF v_product.quantity_type = 'WEIGHTED' THEN
        IF v_product.price_basis_qty = 1000 THEN
          v_display_qty := to_char(v_qty_base / 1000.0, 'FM999999990.000') || ' kg';
        ELSE
          v_display_qty := v_qty_base || ' g';
        END IF;
      ELSE
        v_display_qty := v_qty_base || ' ' || v_product.unit_label;
      END IF;

      IF v_available THEN
        v_item_subtotal := v_item_subtotal + v_line_total;
      END IF;

    END IF;

    v_lines := v_lines || jsonb_build_object(
      'product_id', v_item->>'product_id',
      'name', COALESCE(v_product.name, 'Unknown'),
      'category_id', v_product.category_id,
      'qty_base', v_qty_base,
      'display_qty', v_display_qty,
      'unit_price_paise', v_unit_price_paise,
      'line_total_paise', v_line_total,
      'available', v_available,
      'error', v_error
    );
  END LOOP;

  -- Compute standard delivery fee
  SELECT * INTO v_fee_info FROM public.compute_delivery_fee_for_pin(v_item_subtotal, p_pin_code);
  v_delivery_fee_paise := v_fee_info.fee_paise;
  v_original_delivery_fee_paise := v_delivery_fee_paise;

  -- Validate and calculate Coupon
  IF p_coupon_code IS NOT NULL AND btrim(p_coupon_code) <> '' THEN
    v_clean_code := upper(btrim(p_coupon_code));

    SELECT * INTO v_coupon
    FROM public.coupons
    WHERE code = v_clean_code;

    IF NOT FOUND THEN
      v_coupon_error := 'Coupon code not found.';
    ELSIF NOT v_coupon.active THEN
      v_coupon_error := 'This coupon is no longer active.';
    ELSIF v_coupon.starts_at > now() THEN
      v_coupon_error := 'This coupon is not valid yet.';
    ELSIF v_coupon.expires_at IS NOT NULL AND v_coupon.expires_at <= now() THEN
      v_coupon_error := 'This coupon has expired.';
    ELSIF v_coupon.usage_limit IS NOT NULL AND v_coupon.times_used >= v_coupon.usage_limit THEN
      v_coupon_error := 'This coupon has reached its usage limit.';
    ELSIF cardinality(v_coupon.pin_codes) > 0
          AND (p_pin_code IS NULL
               OR NOT (btrim(p_pin_code) = ANY (v_coupon.pin_codes))) THEN
      v_coupon_error := 'This coupon is not available for your PIN code.';
    ELSIF v_item_subtotal < v_coupon.min_order_paise THEN
      v_coupon_error := 'Minimum order of ₹' || to_char(v_coupon.min_order_paise / 100.0, 'FM999999990.00') || ' required for this coupon.';
    ELSE
      -- Calculate eligible subtotal for coupon scope
      IF (v_coupon.applies_to = 'categories' OR cardinality(v_coupon.category_ids) > 0 OR v_coupon.category_id IS NOT NULL)
         AND v_coupon.applies_to <> 'products' AND (v_coupon.product_ids IS NULL OR cardinality(v_coupon.product_ids) = 0) THEN
        
        SELECT COALESCE(sum((elem->>'line_total_paise')::bigint), 0) INTO v_category_subtotal
        FROM jsonb_array_elements(v_lines) elem
        WHERE (elem->>'available')::boolean = true
          AND (
            (v_coupon.category_ids IS NOT NULL AND cardinality(v_coupon.category_ids) > 0 AND (elem->>'category_id')::uuid = ANY(v_coupon.category_ids))
            OR (v_coupon.category_id IS NOT NULL AND (elem->>'category_id')::uuid = v_coupon.category_id)
          );

        IF v_category_subtotal <= 0 THEN
          v_coupon_error := 'No eligible items from the selected coupon categories found in cart.';
        END IF;

      ELSIF v_coupon.applies_to = 'products' OR (v_coupon.product_ids IS NOT NULL AND cardinality(v_coupon.product_ids) > 0) THEN
        
        SELECT COALESCE(sum((elem->>'line_total_paise')::bigint), 0) INTO v_category_subtotal
        FROM jsonb_array_elements(v_lines) elem
        WHERE (elem->>'available')::boolean = true
          AND (elem->>'product_id')::uuid = ANY(v_coupon.product_ids);

        IF v_category_subtotal <= 0 THEN
          v_coupon_error := 'No eligible items from the selected coupon products found in cart.';
        END IF;

      ELSE
        v_category_subtotal := v_item_subtotal;
      END IF;

      IF v_coupon_error IS NULL THEN
        IF v_coupon.discount_type = 'percent_off' THEN
          v_discount_paise := (v_category_subtotal * v_coupon.discount_value) / 100;
          IF v_coupon.max_discount_paise IS NOT NULL AND v_discount_paise > v_coupon.max_discount_paise THEN
            v_discount_paise := v_coupon.max_discount_paise;
          END IF;
          v_coupon_applied := true;

        ELSIF v_coupon.discount_type = 'rupees_off' THEN
          v_discount_paise := LEAST(v_coupon.discount_value, v_category_subtotal);
          v_coupon_applied := true;

        ELSIF v_coupon.discount_type = 'free_delivery' THEN
          IF v_original_delivery_fee_paise <= 0 THEN
            v_coupon_error := 'Free delivery already applies to this order.';
          ELSE
            -- Free delivery: Just make the delivery free (0 paise), no discount required
            v_discount_paise := 0;
            v_delivery_fee_paise := 0;
            v_coupon_applied := true;
          END IF;
        END IF;
      END IF;
    END IF;
  END IF;

  -- Ensure discount doesn't exceed subtotal
  IF v_discount_paise > v_item_subtotal THEN
    v_discount_paise := v_item_subtotal;
  END IF;

  -- Tax calculation
  IF v_settings.tax_mode = 'exclusive' AND v_settings.tax_rate_bps > 0 THEN
    v_tax_paise := ((v_item_subtotal - v_discount_paise + v_delivery_fee_paise) * v_settings.tax_rate_bps) / 10000;
  ELSIF v_settings.tax_mode = 'inclusive' AND v_settings.tax_rate_bps > 0 THEN
    v_tax_paise := 0;
  END IF;

  v_grand_total := GREATEST(0, v_item_subtotal - v_discount_paise + v_delivery_fee_paise + v_tax_paise);

  RETURN jsonb_build_object(
    'lines', v_lines,
    'item_subtotal_paise', v_item_subtotal,
    'discount_paise', v_discount_paise,
    'delivery_fee_paise', v_delivery_fee_paise,
    'fee_rule_note', v_fee_info.rule_note,
    'tax_paise', v_tax_paise,
    'grand_total_paise', v_grand_total,
    'below_charge_threshold', v_fee_info.below_charge_threshold,
    'amount_to_free_delivery', v_fee_info.amount_to_free_delivery,
    'coupon_code', CASE WHEN v_coupon_applied THEN v_clean_code ELSE NULL END,
    'coupon_applied', v_coupon_applied,
    'coupon_error', v_coupon_error
  );
END;
$$;

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
  v_quote := public.quote_cart(v_items, v_coupon_code, v_address->>'postal_code');

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

REVOKE ALL ON FUNCTION public.compute_delivery_fee_for_pin(bigint, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.compute_delivery_fee_for_pin(bigint, text) TO authenticated, anon;
REVOKE ALL ON FUNCTION public.quote_cart(jsonb, text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.quote_cart(jsonb, text, text) TO authenticated, anon;
REVOKE ALL ON FUNCTION public.place_order(jsonb, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.place_order(jsonb, text) TO authenticated;

NOTIFY pgrst, 'reload schema';

COMMIT;

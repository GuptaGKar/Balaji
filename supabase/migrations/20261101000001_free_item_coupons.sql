-- Migration: 20261101000001_free_item_coupons.sql
-- "Free item" coupons: when the cart reaches the coupon's milestone
-- (min_order_paise), the customer may claim ONE product from a pool the
-- manager configures on the web dashboard.
--   * coupons gains discount_type 'free_item', a product pool
--     (free_item_product_ids) and the free quantity (free_item_qty).
--   * order_items gains is_free_item so staff screens can explain a ₹0 line.
--   * quote_cart grows a p_free_item_id argument: it returns the pool for the
--     chooser and appends a ₹0 line for the selected gift (never touching the
--     subtotal, the delivery-fee milestone or the grand total).
--   * place_order re-quotes with the selection, locks the gift's stock and
--     writes the free line. Prices stay server-side; the client only ever
--     sends product ids and quantities.
-- Built on quote_cart/place_order as last defined by 20261029000006.

BEGIN;

-- ─── 1. Schema ───────────────────────────────────────────────────────────────

ALTER TABLE public.coupons DROP CONSTRAINT IF EXISTS coupons_discount_type_check;
ALTER TABLE public.coupons ADD CONSTRAINT coupons_discount_type_check
  CHECK (discount_type IN ('percent_off', 'rupees_off', 'free_delivery', 'free_item'));

ALTER TABLE public.coupons
  ADD COLUMN IF NOT EXISTS free_item_product_ids uuid[] NOT NULL DEFAULT '{}',
  ADD COLUMN IF NOT EXISTS free_item_qty bigint NOT NULL DEFAULT 1
    CHECK (free_item_qty > 0);

COMMENT ON COLUMN public.coupons.free_item_product_ids IS
  'Pool of gift candidates for a free_item coupon; the customer claims exactly one.';
COMMENT ON COLUMN public.coupons.free_item_qty IS
  'Base units of the chosen gift that are given free (grams for WEIGHTED, packs for PACKED).';

ALTER TABLE public.order_items
  ADD COLUMN IF NOT EXISTS is_free_item boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN public.order_items.is_free_item IS
  'True for a coupon gift line: qty is charged 0 paise and stock is still deducted.';

-- ─── 2. quote_cart ───────────────────────────────────────────────────────────
-- Exactly one overload after the drop, so PostgREST never sees ambiguity.
DROP FUNCTION IF EXISTS public.quote_cart(jsonb, text, text);

CREATE OR REPLACE FUNCTION public.quote_cart(
  p_items jsonb,
  p_coupon_code text DEFAULT NULL,
  p_pin_code text DEFAULT NULL,
  p_free_item_id uuid DEFAULT NULL
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

  -- Free-item gift fields
  v_free_options jsonb := '[]'::jsonb;
  v_free_selected uuid := NULL;
  v_free_selection_required boolean := false;
  v_free_error text := NULL;
  v_free_product public.products%ROWTYPE;
  v_free_qty bigint := 0;
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
      'error', v_error,
      'is_free_item', false
    );
  END LOOP;

  -- Compute standard delivery fee on the paid subtotal (a gift never shifts it).
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

        ELSIF v_coupon.discount_type = 'free_item' THEN
          -- The gift pool is the coupon's own product list.
          IF cardinality(v_coupon.free_item_product_ids) = 0 THEN
            v_coupon_error := 'This coupon is not configured with any free items yet.';
          ELSE
            -- Advertise the pool (with live price/stock) so the app can render a chooser.
            SELECT jsonb_agg(jsonb_build_object(
                     'product_id', gp.id,
                     'name', gp.name,
                     'image_path', gp.image_path,
                     'display_qty', CASE
                       WHEN gp.quantity_type = 'WEIGHTED' AND gp.price_basis_qty = 1000
                         THEN to_char(v_coupon.free_item_qty / 1000.0, 'FM999999990.000') || ' kg'
                       WHEN gp.quantity_type = 'WEIGHTED'
                         THEN v_coupon.free_item_qty || ' g'
                       ELSE v_coupon.free_item_qty || ' ' || gp.unit_label
                     END,
                     'regular_price_paise', CASE
                       WHEN gp.quantity_type = 'WEIGHTED'
                         THEN (2 * gp.price_paise * v_coupon.free_item_qty + gp.price_basis_qty)
                                / (2 * gp.price_basis_qty)
                       ELSE gp.price_paise * GREATEST(v_coupon.free_item_qty, 1)
                     END,
                     'claimable', gp.active AND gp.available AND gp.stock_qty >= v_coupon.free_item_qty
                   ))
              INTO v_free_options
              FROM public.products gp
              WHERE gp.id = ANY (v_coupon.free_item_product_ids);

            IF v_free_options IS NULL OR jsonb_array_length(v_free_options) = 0 THEN
              v_coupon_error := 'This coupon is not configured with any free items yet.';
            ELSIF p_free_item_id IS NULL THEN
              -- Milestone reached; the customer still has to pick a gift.
              v_coupon_applied := true;
              v_free_selection_required := true;
            ELSE
              v_free_selected := p_free_item_id;

              IF NOT (p_free_item_id = ANY (v_coupon.free_item_product_ids)) THEN
                v_free_error := 'That free item is not part of this coupon.';
              ELSIF EXISTS (
                    SELECT 1 FROM jsonb_array_elements(p_items) ci
                    WHERE (ci->>'product_id')::uuid = p_free_item_id
                  ) THEN
                -- Giving away a product the customer is already buying would stack the gift.
                v_free_error := 'This item is already in your cart, so it cannot also be claimed free.';
              ELSE
                SELECT * INTO v_free_product
                FROM public.products
                WHERE id = p_free_item_id;

                IF NOT FOUND THEN
                  v_free_error := 'This coupon''s free item is currently unavailable.';
                ELSIF NOT v_free_product.active OR NOT v_free_product.available THEN
                  v_free_error := 'This coupon''s free item is currently unavailable.';
                ELSIF v_free_product.stock_qty < v_coupon.free_item_qty THEN
                  v_free_error := 'This coupon''s free item is currently unavailable.';
                ELSE
                  v_free_qty := v_coupon.free_item_qty;

                  IF v_free_product.quantity_type = 'PACKED'
                     AND (v_free_qty < v_free_product.min_qty
                          OR v_free_qty > COALESCE(v_free_product.max_qty, v_free_qty)) THEN
                    v_free_error := 'The free quantity configured for this coupon is not a valid pack size.';
                  ELSIF v_free_product.quantity_type = 'WEIGHTED'
                        AND v_free_product.step_qty > 0
                        AND v_free_qty % v_free_product.step_qty <> 0 THEN
                    v_free_error := 'The free quantity configured for this coupon is not a valid weight.';
                  ELSE
                    -- ₹0 line. Deliberately NOT added to v_item_subtotal, so the
                    -- gift cannot change the discount base, delivery milestone or total.
                    v_lines := v_lines || jsonb_build_object(
                      'product_id', v_free_product.id,
                      'name', v_free_product.name,
                      'category_id', v_free_product.category_id,
                      'qty_base', v_free_qty,
                      'display_qty', CASE
                        WHEN v_free_product.quantity_type = 'WEIGHTED' AND v_free_product.price_basis_qty = 1000
                          THEN to_char(v_free_qty / 1000.0, 'FM999999990.000') || ' kg'
                        WHEN v_free_product.quantity_type = 'WEIGHTED'
                          THEN v_free_qty || ' g'
                        ELSE v_free_qty || ' ' || v_free_product.unit_label
                      END,
                      'unit_price_paise', 0,
                      'line_total_paise', 0,
                      'available', true,
                      'error', NULL,
                      'is_free_item', true
                    );
                    v_coupon_applied := true;
                  END IF;
                END IF;
              END IF;

              IF v_free_error IS NOT NULL THEN
                -- A bad selection voids the gift but keeps the coupon visible so the
                -- app can prompt for a different choice.
                v_coupon_applied := true;
                v_free_selection_required := true;
                v_free_selected := NULL;
              END IF;
            END IF;
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
    'coupon_error', v_coupon_error,
    'free_item_options', COALESCE(v_free_options, '[]'::jsonb),
    'free_item_selected_id', v_free_selected,
    'free_item_selection_required', v_free_selection_required,
    'free_item_error', v_free_error
  );
END;
$$;

-- ─── 3. place_order ──────────────────────────────────────────────────────────

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
  v_coupon public.coupons%ROWTYPE;

  -- Free-item gift
  v_free_item_id uuid := NULL;
  v_free_product public.products%ROWTYPE;
  v_free_qty bigint := 0;
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

  -- 6. Server-side pricing with the optional coupon and coupon gift selection
  v_coupon_code := NULLIF(btrim(p_payload->>'coupon_code'), '');
  v_free_item_id := NULLIF(btrim(COALESCE(p_payload->>'free_item_product_id', '')), '')::uuid;
  v_quote := public.quote_cart(v_items, v_coupon_code, v_address->>'postal_code', v_free_item_id);

  IF v_coupon_code IS NOT NULL AND (v_quote->>'coupon_applied')::boolean IS NOT TRUE THEN
    RETURN jsonb_build_object(
      'error', 'INVALID_COUPON',
      'message', COALESCE(v_quote->>'coupon_error', 'Coupon could not be applied.')
    );
  END IF;

  IF (v_quote->>'coupon_applied')::boolean IS TRUE THEN
    SELECT * INTO v_coupon FROM public.coupons WHERE code = upper(btrim(v_coupon_code));
    v_coupon_id := v_coupon.id;

    IF v_coupon.discount_type = 'free_item' THEN
      IF v_free_item_id IS NULL THEN
        RETURN jsonb_build_object('error', 'FREE_ITEM_REQUIRED',
          'message', 'Choose your free item before placing the order.');
      END IF;
      IF (v_quote->>'free_item_error') IS NOT NULL THEN
        RETURN jsonb_build_object('error', 'FREE_ITEM_UNAVAILABLE',
          'message', v_quote->>'free_item_error');
      END IF;

      -- Lock the gift's stock with the rest of the order and re-check it.
      SELECT * INTO v_free_product FROM public.products WHERE id = v_free_item_id FOR UPDATE;
      IF NOT FOUND OR NOT v_free_product.active OR NOT v_free_product.available
         OR v_free_product.stock_qty < v_coupon.free_item_qty THEN
        RETURN jsonb_build_object('error', 'FREE_ITEM_UNAVAILABLE',
          'message', 'This coupon''s free item is currently unavailable.');
      END IF;
      v_free_qty := v_coupon.free_item_qty;
    END IF;
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
    'free_item_product_id', v_quote->>'free_item_selected_id',
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

  -- 10b. The coupon gift: charged 0 paise, but it still leaves the shelf.
  IF v_coupon_id IS NOT NULL AND v_free_item_id IS NOT NULL AND v_free_product.id IS NOT NULL THEN
    INSERT INTO public.order_items (
      order_id, product_id, sku_snapshot, name_snapshot, quantity_type_snapshot,
      unit_label_snapshot, price_basis_qty_snapshot, qty_base, unit_price_paise,
      line_total_paise, is_free_item
    ) VALUES (
      v_order_id, v_free_product.id, v_free_product.sku, v_free_product.name,
      v_free_product.quantity_type, v_free_product.unit_label, v_free_product.price_basis_qty,
      v_free_qty, 0, 0, true
    );

    UPDATE public.products
    SET stock_qty = stock_qty - v_free_qty,
        updated_at = now()
    WHERE id = v_free_product.id;

    INSERT INTO public.inventory_movements (
      product_id, delta_qty, reason, reference_type, reference_id, actor_id, note
    ) VALUES (
      v_free_product.id, -v_free_qty, 'order_placed'::public.inventory_reason,
      'order', v_order_id, v_user_id,
      'Free item via coupon ' || upper(btrim(v_coupon_code)) || ' - Order ' || v_public_order_number
    );
  END IF;

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

-- ─── 4. Grants ───────────────────────────────────────────────────────────────

REVOKE ALL ON FUNCTION public.quote_cart(jsonb, text, text, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.quote_cart(jsonb, text, text, uuid) TO authenticated, anon;
REVOKE ALL ON FUNCTION public.place_order(jsonb, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.place_order(jsonb, text) TO authenticated;

NOTIFY pgrst, 'reload schema';

COMMIT;

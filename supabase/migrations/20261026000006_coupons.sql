-- Migration: 20261026000006_coupons.sql
-- Add coupons table, pricing engine coupon discount evaluation, and place_order coupon tracking.

-- 1. Create coupons table
CREATE TABLE IF NOT EXISTS public.coupons (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code text NOT NULL UNIQUE CHECK (code = upper(btrim(code)) AND length(code) >= 2),
  description text NOT NULL DEFAULT '',
  discount_type text NOT NULL CHECK (discount_type IN ('percent_off', 'rupees_off', 'free_delivery')),
  discount_value bigint NOT NULL DEFAULT 0 CHECK (discount_value >= 0),
  applies_to text NOT NULL DEFAULT 'all' CHECK (applies_to IN ('all', 'categories', 'products')),
  category_id uuid REFERENCES public.categories(id) ON DELETE SET NULL,
  category_ids uuid[] NOT NULL DEFAULT '{}',
  product_ids uuid[] NOT NULL DEFAULT '{}',
  min_order_paise bigint NOT NULL DEFAULT 0 CHECK (min_order_paise >= 0),
  max_discount_paise bigint CHECK (max_discount_paise IS NULL OR max_discount_paise >= 0),
  starts_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz,
  usage_limit integer CHECK (usage_limit IS NULL OR usage_limit > 0),
  times_used integer NOT NULL DEFAULT 0 CHECK (times_used >= 0),
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.coupons IS 'Discount coupons configurable by staff and managers.';
COMMENT ON COLUMN public.coupons.discount_type IS 'percent_off, rupees_off, or free_delivery.';
COMMENT ON COLUMN public.coupons.discount_value IS 'Percentage for percent_off (e.g. 10 for 10%), paise for rupees_off, 0 for free_delivery.';
COMMENT ON COLUMN public.coupons.applies_to IS 'Scope of coupon: all, categories, or products.';
COMMENT ON COLUMN public.coupons.category_ids IS 'Multiple category IDs discount applies to.';
COMMENT ON COLUMN public.coupons.product_ids IS 'Multiple product IDs discount applies to.';
COMMENT ON COLUMN public.coupons.category_id IS 'Specific category discount applies to (legacy / single), or NULL for all categories.';

-- 2. Add coupon columns to orders table
ALTER TABLE public.orders
  ADD COLUMN IF NOT EXISTS coupon_id uuid REFERENCES public.coupons(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS coupon_code text;

-- 3. Grants and Row-Level Security
ALTER TABLE public.coupons ENABLE ROW LEVEL SECURITY;

-- Staff and Managers have full CRUD
DROP POLICY IF EXISTS "coupons_staff_manager_all" ON public.coupons;
CREATE POLICY "coupons_staff_manager_all" ON public.coupons
  FOR ALL TO authenticated
  USING (public.current_role() IN ('manager'::public.user_role, 'staff'::public.user_role))
  WITH CHECK (public.current_role() IN ('manager'::public.user_role, 'staff'::public.user_role));

-- Public / Authenticated customers can view active, valid coupons
DROP POLICY IF EXISTS "coupons_customer_select" ON public.coupons;
CREATE POLICY "coupons_customer_select" ON public.coupons
  FOR SELECT TO authenticated, anon
  USING (
    active = true
    AND starts_at <= now()
    AND (expires_at IS NULL OR expires_at > now())
    AND (usage_limit IS NULL OR times_used < usage_limit)
  );

GRANT SELECT ON public.coupons TO anon, authenticated;
GRANT ALL ON public.coupons TO authenticated;

-- 4. quote_cart with coupon calculation
CREATE OR REPLACE FUNCTION public.quote_cart(
  p_items jsonb,
  p_coupon_code text DEFAULT NULL
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

      -- Validate quantity
      IF v_product.quantity_type = 'PACKED' THEN
        IF v_qty_base < v_product.min_qty OR v_qty_base > COALESCE(v_product.max_qty, v_qty_base) THEN
          v_available := false;
          v_error := 'Invalid pack quantity';
        END IF;
      ELSE
        IF v_qty_base % v_product.step_qty <> 0 THEN
          v_available := false;
          v_error := 'Quantity must be multiple of step (' || v_product.step_qty || 'g)';
        ELSIF v_qty_base < v_product.min_qty OR v_qty_base > COALESCE(v_product.max_qty, v_qty_base) THEN
          v_available := false;
          v_error := 'Quantity out of range';
        END IF;
      END IF;

      IF v_product.stock_qty < v_qty_base THEN
        v_available := false;
        v_error := COALESCE(v_error, '') || 'Insufficient stock';
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
  SELECT * INTO v_fee_info FROM public.compute_delivery_fee(v_item_subtotal);
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
          v_discount_paise := v_original_delivery_fee_paise;
          v_delivery_fee_paise := 0;
          v_coupon_applied := true;
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

-- Overload quote_cart(jsonb) for backward compatibility
CREATE OR REPLACE FUNCTION public.quote_cart(p_items jsonb)
RETURNS jsonb
LANGUAGE sql
SECURITY DEFINER
SET search_path = ''
AS $$
  SELECT public.quote_cart(p_items, NULL);
$$;

GRANT EXECUTE ON FUNCTION public.quote_cart(jsonb, text) TO authenticated, anon;
GRANT EXECUTE ON FUNCTION public.quote_cart(jsonb) TO authenticated, anon;

-- 5. Update place_order to record coupons and increment usage
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
  v_user_id uuid;
  v_order_id uuid;
  v_public_order_number text;
  v_quote jsonb;
  v_items jsonb;
  v_address jsonb;
  v_address_snapshot jsonb;
  v_payment_method public.payment_method;
  v_payment_status public.payment_status := 'unpaid';
  v_order_status public.order_status := 'pending_review';
  v_settings public.store_settings%ROWTYPE;
  v_item jsonb;
  v_product public.products%ROWTYPE;
  v_qty_base bigint;
  v_fee_breakdown jsonb;
  v_existing_id uuid;
  v_idempotency_key text;

  -- Coupon
  v_coupon_code text;
  v_coupon_id uuid := NULL;
BEGIN
  v_user_id := auth.uid();
  IF v_user_id IS NULL THEN
    RETURN jsonb_build_object('error', 'NOT_AUTHENTICATED', 'message', 'You must be signed in to place an order.');
  END IF;

  v_idempotency_key := NULLIF(btrim(p_idempotency_key), '');
  IF v_idempotency_key IS NOT NULL THEN
    SELECT id INTO v_existing_id FROM public.orders WHERE idempotency_key = v_idempotency_key;
    IF v_existing_id IS NOT NULL THEN
      SELECT public_order_number INTO v_public_order_number FROM public.orders WHERE id = v_existing_id;
      RETURN jsonb_build_object('order_id', v_existing_id, 'public_order_number', v_public_order_number, 'idempotent_replay', true);
    END IF;
  END IF;

  SELECT * INTO v_settings FROM public.store_settings;
  IF NOT v_settings.store_open THEN
    RETURN jsonb_build_object('error', 'STORE_CLOSED', 'message', 'The store is currently closed.');
  END IF;

  v_items := p_payload->'items';
  IF v_items IS NULL OR jsonb_array_length(v_items) = 0 THEN
    RETURN jsonb_build_object('error', 'EMPTY_CART', 'message', 'Cart has no items.');
  END IF;

  v_address := p_payload->'address';
  IF v_address IS NULL OR (v_address->>'recipient_name') IS NULL OR (v_address->>'phone') IS NULL OR (v_address->>'address_lines') IS NULL THEN
    RETURN jsonb_build_object('error', 'INVALID_ADDRESS', 'message', 'A valid delivery address is required.');
  END IF;

  v_payment_method := COALESCE((p_payload->>'payment_method')::public.payment_method, 'cod'::public.payment_method);

  FOR v_item IN SELECT * FROM jsonb_array_elements(v_items) LOOP
    v_qty_base := (v_item->>'qty_base')::bigint;
    SELECT * INTO v_product FROM public.products WHERE id = (v_item->>'product_id')::uuid FOR UPDATE;
    IF NOT FOUND OR NOT v_product.active OR NOT v_product.available THEN
      RETURN jsonb_build_object('error', 'ITEM_UNAVAILABLE', 'message', COALESCE(v_product.name, 'An item') || ' is no longer available.');
    END IF;
    IF v_product.stock_qty < v_qty_base THEN
      RETURN jsonb_build_object('error', 'INSUFFICIENT_STOCK', 'message', v_product.name || ' has only ' || v_product.stock_qty || ' available.');
    END IF;
  END LOOP;

  -- Pricing computation with Coupon
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

  IF v_settings.max_order_paise IS NOT NULL AND v_settings.max_order_paise > 0
     AND (v_quote->>'grand_total_paise')::bigint > v_settings.max_order_paise THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Order exceeds maximum allowed value.');
  END IF;

  IF v_payment_method = 'online' AND NOT v_settings.online_payment_enabled THEN
    RETURN jsonb_build_object('error', 'ONLINE_PAYMENT_DISABLED', 'message', 'Online payments are currently disabled.');
  END IF;
  IF v_payment_method = 'online' THEN
    v_payment_status := 'pending';
  END IF;

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
    v_fee_breakdown,
    p_payload->>'customer_note',
    v_idempotency_key,
    v_coupon_id,
    CASE WHEN v_coupon_id IS NOT NULL THEN upper(btrim(v_coupon_code)) ELSE NULL END
  )
  RETURNING id INTO v_order_id;

  -- Create order items and decrement stock
  FOR v_item IN SELECT * FROM jsonb_array_elements(v_items) LOOP
    v_qty_base := (v_item->>'qty_base')::bigint;
    SELECT * INTO v_product FROM public.products WHERE id = (v_item->>'product_id')::uuid;

    INSERT INTO public.order_items (
      order_id, product_id, sku_snapshot, name_snapshot,
      quantity_type_snapshot, unit_label_snapshot, price_basis_qty_snapshot,
      qty_base, unit_price_paise, line_total_paise
    ) VALUES (
      v_order_id, v_product.id, v_product.sku, v_product.name,
      v_product.quantity_type, v_product.unit_label, v_product.price_basis_qty,
      v_qty_base, v_product.price_paise,
      (2 * v_product.price_paise * v_qty_base + v_product.price_basis_qty) / (2 * v_product.price_basis_qty)
    );

    UPDATE public.products
    SET stock_qty = stock_qty - v_qty_base,
        updated_at = now()
    WHERE id = v_product.id;

    INSERT INTO public.stock_movements (
      product_id, movement_type, delta_qty, stock_after, reference_order_id, reason
    ) VALUES (
      v_product.id, 'order_placed', -v_qty_base, v_product.stock_qty - v_qty_base, v_order_id, 'Order ' || v_public_order_number
    );
  END LOOP;

  -- Increment coupon usage
  IF v_coupon_id IS NOT NULL THEN
    UPDATE public.coupons
    SET times_used = times_used + 1,
        updated_at = now()
    WHERE id = v_coupon_id;
  END IF;

  -- Record initial status event
  INSERT INTO public.order_status_events (order_id, from_status, to_status, note, created_by)
  VALUES (v_order_id, NULL, 'pending_review', 'Order placed by customer', v_user_id);

  RETURN jsonb_build_object(
    'order_id', v_order_id,
    'public_order_number', v_public_order_number,
    'breakdown', v_quote
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.place_order(jsonb, text) TO authenticated;

-- ===========================================================================
-- Migration 20261014000017: Security RLS Hardening, Customer Identity, Compound Indexes & Dashboard RPC
-- ===========================================================================

-- 1. Performance Compound Indexes
CREATE INDEX IF NOT EXISTS idx_orders_status_created_desc ON orders(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_products_category_active_avail ON products(category_id, active, available);

-- 2. Secure RLS Policies for Categories and Products
DROP POLICY IF EXISTS "categories_read_active" ON categories;
CREATE POLICY "categories_read_active" ON categories
  FOR SELECT TO public USING (active = true);

DROP POLICY IF EXISTS "categories_write" ON categories;
CREATE POLICY "categories_write" ON categories
  FOR ALL TO authenticated
  USING (public.current_role() = 'manager'::user_role)
  WITH CHECK (public.current_role() = 'manager'::user_role);

DROP POLICY IF EXISTS "products_read_published" ON products;
CREATE POLICY "products_read_published" ON products
  FOR SELECT TO public USING (active = true AND available = true);

DROP POLICY IF EXISTS "products_write_manager" ON products;
CREATE POLICY "products_write_manager" ON products
  FOR ALL TO authenticated
  USING (public.current_role() = 'manager'::user_role)
  WITH CHECK (public.current_role() = 'manager'::user_role);

-- 3. Secure RLS Policies for Store Settings
DROP POLICY IF EXISTS "store_settings_read" ON store_settings;
CREATE POLICY "store_settings_read" ON store_settings
  FOR SELECT TO public USING (true);

DROP POLICY IF EXISTS "store_settings_write" ON store_settings;
CREATE POLICY "store_settings_write" ON store_settings
  FOR ALL TO authenticated
  USING (public.current_role() = 'manager'::user_role)
  WITH CHECK (public.current_role() = 'manager'::user_role);

-- 4. Customer Isolation RLS Policies for Orders and Order Items
DROP POLICY IF EXISTS "orders_read_own" ON orders;
CREATE POLICY "orders_read_own" ON orders
  FOR SELECT TO authenticated
  USING (customer_id = auth.uid() OR public.current_role()::text IN ('staff', 'manager'));

DROP POLICY IF EXISTS "orders_update_status_staff" ON orders;
CREATE POLICY "orders_update_status_staff" ON orders
  FOR UPDATE TO authenticated
  USING (public.current_role()::text IN ('staff', 'manager'))
  WITH CHECK (public.current_role()::text IN ('staff', 'manager'));

DROP POLICY IF EXISTS "order_items_read_own" ON order_items;
CREATE POLICY "order_items_read_own" ON order_items
  FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM orders o
      WHERE o.id = order_items.order_id
        AND (o.customer_id = auth.uid() OR public.current_role()::text IN ('staff', 'manager'))
    )
  );

-- 5. Hardened place_order function enforcing server-side customer_id = auth.uid()
CREATE OR REPLACE FUNCTION public.place_order(
  p_payload jsonb,
  p_idempotency_key text
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
  v_user_id uuid;
  v_order_id uuid;
  v_public_order_number text;
  v_quote jsonb;
  v_item jsonb;
  v_product public.products%ROWTYPE;
  v_qty_base bigint;
  v_unit_price bigint;
  v_line_total bigint;
  v_cached_response jsonb;
  v_address jsonb;
BEGIN
  -- Authoritative customer_id comes from auth.uid()
  v_user_id := auth.uid();
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'Authentication required to place an order.'
      USING ERRCODE = '42501';
  END IF;

  -- Idempotency check
  SELECT response INTO v_cached_response
  FROM public.idempotency_keys
  WHERE user_id = v_user_id AND scope = 'place_order' AND key = p_idempotency_key;

  IF v_cached_response IS NOT NULL THEN
    RETURN v_cached_response;
  END IF;

  -- Record idempotency key
  INSERT INTO public.idempotency_keys (user_id, scope, key, request_hash)
  VALUES (v_user_id, 'place_order', p_idempotency_key, md5(p_payload::text))
  ON CONFLICT (user_id, scope, key) DO NOTHING;

  -- Calculate quote
  v_quote := public.quote_cart(p_payload->'items');

  IF (v_quote->>'grand_total_paise')::bigint <= 0 THEN
    RAISE EXCEPTION 'Cart is empty or invalid.';
  END IF;

  v_address := p_payload->'address';
  v_public_order_number := 'BPS/' || to_char(now(), 'YY') || '/' || LPAD(floor(random() * 1000000)::text, 6, '0');
  v_order_id := gen_random_uuid();

  -- Insert order
  INSERT INTO public.orders (
    id, public_order_number, customer_id, address_snapshot,
    status, payment_method, payment_status,
    item_subtotal_paise, discount_paise, delivery_fee_paise, tax_paise, grand_total_paise,
    fee_breakdown, customer_note, idempotency_key
  ) VALUES (
    v_order_id, v_public_order_number, v_user_id, v_address,
    'pending_review',
    COALESCE(p_payload->>'payment_method', 'cod')::payment_method,
    'unpaid',
    (v_quote->>'item_subtotal_paise')::bigint,
    (v_quote->>'discount_paise')::bigint,
    (v_quote->>'delivery_fee_paise')::bigint,
    (v_quote->>'tax_paise')::bigint,
    (v_quote->>'grand_total_paise')::bigint,
    v_quote,
    p_payload->>'customer_note',
    p_idempotency_key
  );

  -- Insert line items
  FOR v_item IN SELECT * FROM jsonb_array_elements(p_payload->'items')
  LOOP
    SELECT * INTO v_product FROM public.products WHERE id = (v_item->>'product_id')::uuid;
    IF v_product.id IS NULL OR NOT v_product.active OR NOT v_product.available THEN
      RAISE EXCEPTION 'Product % is unavailable.', COALESCE(v_item->>'product_id', 'unknown');
    END IF;

    v_qty_base := (v_item->>'qty_base')::bigint;
    v_unit_price := v_product.price_paise;
    v_line_total := (v_unit_price::numeric * (v_qty_base::numeric / v_product.price_basis_qty::numeric))::bigint;

    INSERT INTO public.order_items (
      order_id, product_id, sku_snapshot, name_snapshot, quantity_type_snapshot,
      unit_label_snapshot, price_basis_qty_snapshot, qty_base, unit_price_paise, line_total_paise
    ) VALUES (
      v_order_id, v_product.id, v_product.sku, v_product.name, v_product.quantity_type,
      v_product.unit_label, v_product.price_basis_qty, v_qty_base, v_unit_price, v_line_total
    );

    UPDATE public.products
    SET stock_qty = GREATEST(0, stock_qty - v_qty_base)
    WHERE id = v_product.id;
  END LOOP;

  INSERT INTO public.order_status_events (order_id, from_status, to_status, actor_id, reason)
  VALUES (v_order_id, NULL, 'pending_review', v_user_id, 'Order placed by customer');

  RETURN jsonb_build_object(
    'order_id', v_order_id,
    'public_order_number', v_public_order_number,
    'breakdown', v_quote
  );
END;
$$;

-- 6. SECURITY INVOKER Function for Web Dashboard Stats (Asia/Kolkata day boundary)
CREATE OR REPLACE FUNCTION public.get_dashboard_stats()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public, extensions
AS $$
DECLARE
  v_today_start timestamptz;
  v_total_products bigint;
  v_low_stock_count bigint;
  v_active_orders bigint;
  v_today_revenue bigint;
BEGIN
  v_today_start := (now() AT TIME ZONE 'Asia/Kolkata')::date::timestamptz AT TIME ZONE 'Asia/Kolkata';

  SELECT count(*) INTO v_total_products
  FROM public.products WHERE active = true;

  SELECT count(*) INTO v_low_stock_count
  FROM public.products
  WHERE active = true AND stock_qty > 0 AND stock_qty <= low_stock_threshold;

  SELECT count(*) INTO v_active_orders
  FROM public.orders
  WHERE status NOT IN ('delivered', 'cancelled', 'rejected', 'delivery_failed');

  SELECT COALESCE(sum(grand_total_paise), 0) INTO v_today_revenue
  FROM public.orders
  WHERE status NOT IN ('cancelled', 'rejected', 'delivery_failed')
    AND created_at >= v_today_start;

  RETURN jsonb_build_object(
    'total_products', v_total_products,
    'low_stock_count', v_low_stock_count,
    'active_orders', v_active_orders,
    'today_revenue_paise', v_today_revenue
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.quote_cart(jsonb) TO public;
GRANT EXECUTE ON FUNCTION public.place_order(jsonb, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_dashboard_stats() TO authenticated;

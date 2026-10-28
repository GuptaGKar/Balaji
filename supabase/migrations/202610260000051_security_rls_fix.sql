-- ===========================================================================
-- Migration 20261014000017: Security RLS Hardening, Customer Identity, Compound Indexes & Auto Profile Trigger
-- ===========================================================================

-- 1. Performance Compound Indexes
CREATE INDEX IF NOT EXISTS idx_orders_status_created_desc ON orders(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_products_category_active_avail ON products(category_id, active, available);

-- 2. Auto-Provision Customer Profile on Auth Signup Trigger
--
-- Every self-service signup (phone OTP from the Android app, or any direct call to
-- the auth API) becomes a customer. The role is NOT read from raw_user_meta_data:
-- that field is client-controlled, so trusting it would let anyone sign up as a
-- manager. Staff/rider accounts are created through /api/staff with the service
-- role, which sets the real role on the profile right after the auth user exists.
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
BEGIN
  INSERT INTO public.profiles (id, role, display_name, phone, is_active, must_change_password)
  VALUES (
    NEW.id,
    'customer'::public.user_role,
    COALESCE(NEW.raw_user_meta_data->>'display_name', NEW.email, NEW.phone, 'Customer'),
    NEW.phone,
    true,
    false
  )
  ON CONFLICT (id) DO NOTHING;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- 3. Secure RLS Policies for Categories and Products
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

-- 4. Secure RLS Policies for Store Settings
DROP POLICY IF EXISTS "store_settings_read" ON store_settings;
CREATE POLICY "store_settings_read" ON store_settings
  FOR SELECT TO public USING (true);

DROP POLICY IF EXISTS "store_settings_write" ON store_settings;
CREATE POLICY "store_settings_write" ON store_settings
  FOR ALL TO authenticated
  USING (public.current_role() = 'manager'::user_role)
  WITH CHECK (public.current_role() = 'manager'::user_role);

-- 5. Customer Isolation RLS Policies for Orders and Order Items
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

-- 6. place_order is deliberately NOT redefined in this migration.
--
-- Earlier revisions replaced public.place_order(jsonb, text) with a body whose second
-- parameter had no default. Applying that over the coupon-aware definition from
-- 20261026000006_coupons.sql fails with 42P13 ("cannot remove parameter defaults from
-- existing function"), and forcing it through with DROP FUNCTION first would silently
-- drop coupon validation, coupon usage counting, the empty-cart guard, the store-open
-- guard and the FOR UPDATE stock re-check.
--
-- The server-side identity rule this migration exists for is already enforced:
-- 20261022000021_address_coordinates.sql and 20261026000006_coupons.sql both derive
-- customer_id from auth.uid() and never from the client payload. This file therefore
-- stops at the privilege grants below.

-- 7. SECURITY INVOKER Function for Web Dashboard Stats
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

-- Grant whichever quote_cart signature exists at the time this migration runs.
-- 20261026000006_coupons.sql adds quote_cart(jsonb, text) while keeping the
-- quote_cart(jsonb) wrapper, and 20261027000001 drops the wrapper again; naming a
-- fixed signature here would fail depending on the order the files are applied.
DO $$
BEGIN
  IF to_regprocedure('public.quote_cart(jsonb, text)') IS NOT NULL THEN
    EXECUTE 'GRANT EXECUTE ON FUNCTION public.quote_cart(jsonb, text) TO public';
  END IF;
  IF to_regprocedure('public.quote_cart(jsonb)') IS NOT NULL THEN
    EXECUTE 'GRANT EXECUTE ON FUNCTION public.quote_cart(jsonb) TO public';
  END IF;
END $$;

GRANT EXECUTE ON FUNCTION public.place_order(jsonb, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_dashboard_stats() TO authenticated;

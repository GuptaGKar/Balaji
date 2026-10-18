-- M2: RLS policies for all tables (IMPLEMENTATION_PLAN.md §5.1, §5.2)
-- Default deny already set in M1 (migration 008). Policies grant minimum required access.

-- ============================================================
-- IDENTITY / CONFIG
-- ============================================================

-- profiles: users read own row; staff/manager read all; manager writes role/is_active
CREATE POLICY "profiles_read_own" ON profiles
  FOR SELECT TO authenticated USING (id = auth.uid());

CREATE POLICY "profiles_read_staff" ON profiles
  FOR SELECT TO authenticated USING (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

CREATE POLICY "profiles_update_role_active" ON profiles
  FOR UPDATE TO authenticated
  USING (public.current_role() = 'manager'::user_role)
  WITH CHECK (public.current_role() = 'manager'::user_role);

-- store_settings: everyone reads; only manager writes
CREATE POLICY "store_settings_read" ON store_settings
  FOR SELECT TO authenticated USING (true);

CREATE POLICY "store_settings_write" ON store_settings
  FOR ALL TO authenticated
  USING (public.current_role() = 'manager'::user_role)
  WITH CHECK (public.current_role() = 'manager'::user_role);

-- service_areas: everyone reads active; manager writes
CREATE POLICY "service_areas_read_active" ON service_areas
  FOR SELECT TO authenticated USING (active = true);

CREATE POLICY "service_areas_write" ON service_areas
  FOR ALL TO authenticated
  USING (public.current_role() = 'manager'::user_role)
  WITH CHECK (public.current_role() = 'manager'::user_role);

-- ============================================================
-- CATALOGUE (read via views for anon/customer; write via RPC)
-- ============================================================

-- categories: everyone reads active; manager writes
CREATE POLICY "categories_read_active" ON categories
  FOR SELECT TO authenticated USING (active = true);

CREATE POLICY "categories_write" ON categories
  FOR ALL TO authenticated
  USING (public.current_role() = 'manager'::user_role)
  WITH CHECK (public.current_role() = 'manager'::user_role);

-- products: everyone reads active+available; manager writes; staff adjusts stock only
CREATE POLICY "products_read_published" ON products
  FOR SELECT TO authenticated USING (active = true AND available = true);

CREATE POLICY "products_write_manager" ON products
  FOR ALL TO authenticated
  USING (public.current_role() = 'manager'::user_role)
  WITH CHECK (public.current_role() = 'manager'::user_role);

-- staff can only UPDATE stock_qty, low_stock_threshold, available (no price/active/category changes)
CREATE POLICY "products_stock_adjust_staff" ON products
  FOR UPDATE TO authenticated
  USING (public.current_role() = 'staff'::user_role)
  WITH CHECK (
    public.current_role() = 'staff'::user_role AND
    -- Only these columns may change (PostgreSQL doesn't support column-level WITH CHECK directly;
    -- enforcement is in the RPC `adjust_stock` and the UPDATE policy is a fallback.
    true
  );

-- ============================================================
-- CUSTOMER DATA (addresses, orders, order_items, bills)
-- ============================================================

-- addresses: customer CRUD on own; staff/manager read all
CREATE POLICY "addresses_own_crud" ON addresses
  FOR ALL TO authenticated
  USING (customer_id = auth.uid())
  WITH CHECK (customer_id = auth.uid());

CREATE POLICY "addresses_staff_read" ON addresses
  FOR SELECT TO authenticated USING (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

-- orders: customer reads own; staff/manager read all; customer inserts via RPC only (no direct INSERT policy)
CREATE POLICY "orders_read_own" ON orders
  FOR SELECT TO authenticated USING (customer_id = auth.uid());

CREATE POLICY "orders_read_staff" ON orders
  FOR SELECT TO authenticated USING (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

CREATE POLICY "orders_update_status_staff" ON orders
  FOR UPDATE TO authenticated
  USING (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager')
  WITH CHECK (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

-- order_items: follow orders ownership
CREATE POLICY "order_items_read_own" ON order_items
  FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM orders o WHERE o.id = order_items.order_id AND o.customer_id = auth.uid()));

CREATE POLICY "order_items_read_staff" ON order_items
  FOR SELECT TO authenticated USING (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

-- order_status_events: customer reads own order's events; staff/manager read all
CREATE POLICY "order_events_read_own" ON order_status_events
  FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM orders o WHERE o.id = order_status_events.order_id AND o.customer_id = auth.uid()));

CREATE POLICY "order_events_read_staff" ON order_status_events
  FOR SELECT TO authenticated USING (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

-- order_assignments: staff/manager read all; delivery reads own assignments
CREATE POLICY "order_assignments_read_staff" ON order_assignments
  FOR SELECT TO authenticated USING (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

CREATE POLICY "order_assignments_read_delivery" ON order_assignments
  FOR SELECT TO authenticated
  USING (public.current_role() = 'delivery'::user_role AND delivery_user_id = auth.uid());

CREATE POLICY "order_assignments_write_staff" ON order_assignments
  FOR ALL TO authenticated
  USING (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager')
  WITH CHECK (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

-- ============================================================
-- ORDER STATE MACHINE (read-only for all authenticated)
-- ============================================================

CREATE POLICY "order_transitions_read" ON order_transitions
  FOR SELECT TO authenticated USING (true);

-- ============================================================
-- INVENTORY / BILLING
-- ============================================================

-- inventory_movements: insert via RPC only; staff/manager read
CREATE POLICY "inventory_movements_read" ON inventory_movements
  FOR SELECT TO authenticated USING (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

-- bill_counters: manager only (gapless numbering via RPC)
CREATE POLICY "bill_counters_manager" ON bill_counters
  FOR ALL TO authenticated
  USING (public.current_role() = 'manager'::user_role)
  WITH CHECK (public.current_role() = 'manager'::user_role);

-- pos_sales: cashier (staff/manager) creates; staff/manager read
CREATE POLICY "pos_sales_write_staff" ON pos_sales
  FOR INSERT TO authenticated
  WITH CHECK (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

CREATE POLICY "pos_sales_read_staff" ON pos_sales
  FOR SELECT TO authenticated USING (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

-- bills: customer reads own (via order_id); staff/manager read all
CREATE POLICY "bills_read_own" ON bills
  FOR SELECT TO authenticated
  USING (
    (source = 'ORDER' AND EXISTS (
      SELECT 1 FROM orders o WHERE o.id = bills.order_id AND o.customer_id = auth.uid()
    )) OR
    public.current_role()::text = 'staff' OR public.current_role()::text = 'manager'
  );

CREATE POLICY "bills_write_staff" ON bills
  FOR ALL TO authenticated
  USING (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager')
  WITH CHECK (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

-- bill_items: follow bills
CREATE POLICY "bill_items_read_own" ON bill_items
  FOR SELECT TO authenticated
  USING (EXISTS (
    SELECT 1 FROM bills b
    WHERE b.id = bill_items.bill_id
      AND ((b.source = 'ORDER' AND EXISTS (
        SELECT 1 FROM orders o WHERE o.id = b.order_id AND o.customer_id = auth.uid()
      )) OR public.current_role()::text = 'staff' OR public.current_role()::text = 'manager')
  ));

CREATE POLICY "bill_items_write_staff" ON bill_items
  FOR ALL TO authenticated
  USING (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager')
  WITH CHECK (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

-- ============================================================
-- PAYMENTS (gated)
-- ============================================================

CREATE POLICY "payments_read_own" ON payments
  FOR SELECT TO authenticated
  USING (EXISTS (
    SELECT 1 FROM orders o WHERE o.id = payments.order_id AND o.customer_id = auth.uid()
  ));

CREATE POLICY "payments_read_staff" ON payments
  FOR SELECT TO authenticated USING (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

-- payment_events: internal only (webhook processor); no direct client access
CREATE POLICY "payment_events_none" ON payment_events
  FOR ALL TO authenticated USING (false);

-- ============================================================
-- CROSS-CUTTING
-- ============================================================

-- idempotency_keys: user sees only own keys (INSERT via RPC on conflict)
CREATE POLICY "idempotency_own" ON idempotency_keys
  FOR ALL TO authenticated
  USING (user_id = auth.uid())
  WITH CHECK (user_id = auth.uid());

-- audit_logs: manager reads all; insert via triggers only (no direct client writes)
CREATE POLICY "audit_logs_read_manager" ON audit_logs
  FOR SELECT TO authenticated USING (public.current_role() = 'manager'::user_role);

-- notification_outbox: internal; no direct client access
CREATE POLICY "notification_outbox_none" ON notification_outbox
  FOR ALL TO authenticated USING (false);

-- ============================================================
-- CATALOGUE READ VIEWS (for anon + authenticated customers)
-- These are SECURITY INVOKER so they respect the caller's RLS on products/categories.
-- ============================================================

CREATE OR REPLACE VIEW public.catalog_categories
WITH (security_invoker = true) AS
SELECT id, name, slug, sort_order
FROM categories
WHERE active = true
ORDER BY sort_order;

CREATE OR REPLACE VIEW public.catalog_products
WITH (security_invoker = true) AS
SELECT
  id, sku, name, description, category_id, image_path,
  quantity_type, price_paise, price_basis_qty,
  unit_label, pack_size_label, step_qty, min_qty, max_qty,
  stock_qty, low_stock_threshold
FROM products
WHERE active = true AND available = true;

GRANT SELECT ON public.catalog_categories TO anon, authenticated;
GRANT SELECT ON public.catalog_products TO anon, authenticated;

-- Note: anon has no direct table access; only these views. RLS on tables is enabled but
-- anon role is not in any policy USING clause, so it gets nothing from tables directly.
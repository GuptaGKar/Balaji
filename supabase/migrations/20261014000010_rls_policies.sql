-- M2: RLS policies for all tables (IMPLEMENTATION_PLAN.md §5.1, §5.2)

-- ============================================================
-- IDENTITY / CONFIG
-- ============================================================

DROP POLICY IF EXISTS "profiles_read_own" ON profiles;
CREATE POLICY "profiles_read_own" ON profiles
  FOR SELECT TO authenticated USING (id = auth.uid());

DROP POLICY IF EXISTS "profiles_read_staff" ON profiles;
CREATE POLICY "profiles_read_staff" ON profiles
  FOR SELECT TO authenticated USING (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

DROP POLICY IF EXISTS "profiles_update_role_active" ON profiles;
CREATE POLICY "profiles_update_role_active" ON profiles
  FOR UPDATE TO authenticated
  USING (public.current_role() = 'manager'::user_role)
  WITH CHECK (public.current_role() = 'manager'::user_role);

DROP POLICY IF EXISTS "store_settings_read" ON store_settings;
CREATE POLICY "store_settings_read" ON store_settings
  FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS "store_settings_write" ON store_settings;
CREATE POLICY "store_settings_write" ON store_settings
  FOR ALL TO authenticated
  USING (public.current_role() = 'manager'::user_role)
  WITH CHECK (public.current_role() = 'manager'::user_role);

DROP POLICY IF EXISTS "service_areas_read_active" ON service_areas;
CREATE POLICY "service_areas_read_active" ON service_areas
  FOR SELECT TO authenticated USING (active = true);

DROP POLICY IF EXISTS "service_areas_write" ON service_areas;
CREATE POLICY "service_areas_write" ON service_areas
  FOR ALL TO authenticated
  USING (public.current_role() = 'manager'::user_role)
  WITH CHECK (public.current_role() = 'manager'::user_role);

-- ============================================================
-- CATALOGUE
-- ============================================================

DROP POLICY IF EXISTS "categories_read_active" ON categories;
CREATE POLICY "categories_read_active" ON categories
  FOR SELECT TO authenticated USING (active = true);

DROP POLICY IF EXISTS "categories_write" ON categories;
CREATE POLICY "categories_write" ON categories
  FOR ALL TO authenticated
  USING (public.current_role() = 'manager'::user_role)
  WITH CHECK (public.current_role() = 'manager'::user_role);

DROP POLICY IF EXISTS "products_read_published" ON products;
CREATE POLICY "products_read_published" ON products
  FOR SELECT TO authenticated USING (active = true AND available = true);

DROP POLICY IF EXISTS "products_write_manager" ON products;
CREATE POLICY "products_write_manager" ON products
  FOR ALL TO authenticated
  USING (public.current_role() = 'manager'::user_role)
  WITH CHECK (public.current_role() = 'manager'::user_role);

DROP POLICY IF EXISTS "products_stock_adjust_staff" ON products;
CREATE POLICY "products_stock_adjust_staff" ON products
  FOR UPDATE TO authenticated
  USING (public.current_role() = 'staff'::user_role)
  WITH CHECK (public.current_role() = 'staff'::user_role);

-- ============================================================
-- CUSTOMER ORDERS & ADDRESSES
-- ============================================================

DROP POLICY IF EXISTS "addresses_own_crud" ON addresses;
CREATE POLICY "addresses_own_crud" ON addresses
  FOR ALL TO authenticated
  USING (customer_id = auth.uid())
  WITH CHECK (customer_id = auth.uid());

DROP POLICY IF EXISTS "addresses_staff_read" ON addresses;
CREATE POLICY "addresses_staff_read" ON addresses
  FOR SELECT TO authenticated
  USING (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

DROP POLICY IF EXISTS "orders_read_own" ON orders;
CREATE POLICY "orders_read_own" ON orders
  FOR SELECT TO authenticated USING (customer_id = auth.uid());

DROP POLICY IF EXISTS "orders_read_staff" ON orders;
CREATE POLICY "orders_read_staff" ON orders
  FOR SELECT TO authenticated
  USING (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

DROP POLICY IF EXISTS "orders_update_status_staff" ON orders;
CREATE POLICY "orders_update_status_staff" ON orders
  FOR UPDATE TO authenticated
  USING (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager')
  WITH CHECK (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

DROP POLICY IF EXISTS "order_items_read_own" ON order_items;
CREATE POLICY "order_items_read_own" ON order_items
  FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM orders o WHERE o.id = order_items.order_id AND o.customer_id = auth.uid()));

DROP POLICY IF EXISTS "order_items_read_staff" ON order_items;
CREATE POLICY "order_items_read_staff" ON order_items
  FOR SELECT TO authenticated
  USING (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

DROP POLICY IF EXISTS "order_events_read_own" ON order_status_events;
CREATE POLICY "order_events_read_own" ON order_status_events
  FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM orders o WHERE o.id = order_status_events.order_id AND o.customer_id = auth.uid()));

DROP POLICY IF EXISTS "order_events_read_staff" ON order_status_events;
CREATE POLICY "order_events_read_staff" ON order_status_events
  FOR SELECT TO authenticated
  USING (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

DROP POLICY IF EXISTS "order_assignments_read_staff" ON order_assignments;
CREATE POLICY "order_assignments_read_staff" ON order_assignments
  FOR SELECT TO authenticated
  USING (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

DROP POLICY IF EXISTS "order_assignments_read_delivery" ON order_assignments;
CREATE POLICY "order_assignments_read_delivery" ON order_assignments
  FOR SELECT TO authenticated
  USING (public.current_role() = 'delivery'::user_role AND delivery_user_id = auth.uid());

DROP POLICY IF EXISTS "order_assignments_write_staff" ON order_assignments;
CREATE POLICY "order_assignments_write_staff" ON order_assignments
  FOR ALL TO authenticated
  USING (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager')
  WITH CHECK (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

DROP POLICY IF EXISTS "order_transitions_read" ON order_transitions;
CREATE POLICY "order_transitions_read" ON order_transitions
  FOR SELECT TO authenticated USING (true);

-- ============================================================
-- INVENTORY & BILLING
-- ============================================================

DROP POLICY IF EXISTS "inventory_movements_read" ON inventory_movements;
CREATE POLICY "inventory_movements_read" ON inventory_movements
  FOR SELECT TO authenticated
  USING (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

DROP POLICY IF EXISTS "bill_counters_manager" ON bill_counters;
CREATE POLICY "bill_counters_manager" ON bill_counters
  FOR ALL TO authenticated
  USING (public.current_role() = 'manager'::user_role)
  WITH CHECK (public.current_role() = 'manager'::user_role);

DROP POLICY IF EXISTS "pos_sales_write_staff" ON pos_sales;
CREATE POLICY "pos_sales_write_staff" ON pos_sales
  FOR INSERT TO authenticated
  WITH CHECK (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

DROP POLICY IF EXISTS "pos_sales_read_staff" ON pos_sales;
CREATE POLICY "pos_sales_read_staff" ON pos_sales
  FOR SELECT TO authenticated
  USING (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

DROP POLICY IF EXISTS "bills_read_own" ON bills;
CREATE POLICY "bills_read_own" ON bills
  FOR SELECT TO authenticated
  USING (
    (source = 'ORDER' AND EXISTS (SELECT 1 FROM orders o WHERE o.id = bills.order_id AND o.customer_id = auth.uid()))
    OR public.current_role()::text = 'staff' OR public.current_role()::text = 'manager'
  );

DROP POLICY IF EXISTS "bills_write_staff" ON bills;
CREATE POLICY "bills_write_staff" ON bills
  FOR ALL TO authenticated
  USING (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager')
  WITH CHECK (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

DROP POLICY IF EXISTS "bill_items_read_own" ON bill_items;
CREATE POLICY "bill_items_read_own" ON bill_items
  FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM bills b
      WHERE b.id = bill_items.bill_id
        AND (
          (b.source = 'ORDER' AND EXISTS (SELECT 1 FROM orders o WHERE o.id = b.order_id AND o.customer_id = auth.uid()))
          OR public.current_role()::text = 'staff' OR public.current_role()::text = 'manager'
        )
    )
  );

DROP POLICY IF EXISTS "bill_items_write_staff" ON bill_items;
CREATE POLICY "bill_items_write_staff" ON bill_items
  FOR ALL TO authenticated
  USING (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager')
  WITH CHECK (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

-- ============================================================
-- PAYMENTS / IDEMPOTENCY / AUDIT / NOTIFICATION
-- ============================================================

DROP POLICY IF EXISTS "payments_read_own" ON payments;
CREATE POLICY "payments_read_own" ON payments
  FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM orders o WHERE o.id = payments.order_id AND o.customer_id = auth.uid()));

DROP POLICY IF EXISTS "payments_read_staff" ON payments;
CREATE POLICY "payments_read_staff" ON payments
  FOR SELECT TO authenticated
  USING (public.current_role()::text = 'staff' OR public.current_role()::text = 'manager');

DROP POLICY IF EXISTS "payment_events_none" ON payment_events;
CREATE POLICY "payment_events_none" ON payment_events
  FOR ALL TO authenticated USING (false);

DROP POLICY IF EXISTS "idempotency_own" ON idempotency_keys;
CREATE POLICY "idempotency_own" ON idempotency_keys
  FOR ALL TO authenticated
  USING (user_id = auth.uid())
  WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS "audit_logs_read_manager" ON audit_logs;
CREATE POLICY "audit_logs_read_manager" ON audit_logs
  FOR SELECT TO authenticated
  USING (public.current_role() = 'manager'::user_role);

DROP POLICY IF EXISTS "notification_outbox_none" ON notification_outbox;
CREATE POLICY "notification_outbox_none" ON notification_outbox
  FOR ALL TO authenticated USING (false);

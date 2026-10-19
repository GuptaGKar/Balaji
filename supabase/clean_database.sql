-- ===========================================================================
-- Balaji Provision Store — Clean Database & Reset Script
-- Run this in Supabase SQL Editor to wipe existing test items, fix RLS policies,
-- and allow adding categories and products cleanly from the Web Admin Portal.
-- ===========================================================================

-- 1. Truncate all tables safely with CASCADE
TRUNCATE TABLE
  bill_items,
  bills,
  pos_sales,
  inventory_movements,
  order_assignments,
  order_status_events,
  order_items,
  orders,
  addresses,
  products,
  categories,
  service_areas,
  audit_logs,
  notification_outbox,
  payment_events,
  payments,
  idempotency_keys
CASCADE;

-- 2. Create compatibility views so both "categories/products" and "catalog_categories/catalog_products" work
CREATE OR REPLACE VIEW catalog_categories AS SELECT * FROM categories;
CREATE OR REPLACE VIEW catalog_products AS SELECT * FROM products;

-- 3. Reset store settings singleton
INSERT INTO store_settings (
  id, store_open, timezone, weekly_hours, contact_phone,
  estimated_window_text, delivery_fee_paise, delivery_charge_threshold_paise,
  free_delivery_threshold_paise, cancellation_policy_text
) VALUES (
  true, true, 'Asia/Kolkata',
  '{"operating_hours":["08:00 AM","10:00 PM"]}'::jsonb,
  '9837803293',
  'Same-day delivery window confirmed upon order acceptance.',
  3000, 25000, 25000,
  'Cancellation allowed before order acceptance.'
) ON CONFLICT (id) DO UPDATE SET store_open = EXCLUDED.store_open;

-- 4. Fix RLS Policies for Categories, Products, Store Settings, Orders
ALTER TABLE categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE products ENABLE ROW LEVEL SECURITY;
ALTER TABLE store_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE addresses ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "categories_read_public" ON categories;
DROP POLICY IF EXISTS "categories_read_active" ON categories;
DROP POLICY IF EXISTS "categories_write" ON categories;
DROP POLICY IF EXISTS "categories_write_public" ON categories;
CREATE POLICY "categories_read_public" ON categories FOR SELECT TO public USING (true);
CREATE POLICY "categories_write_public" ON categories FOR ALL TO public USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "products_read_public" ON products;
DROP POLICY IF EXISTS "products_read_published" ON products;
DROP POLICY IF EXISTS "products_write_manager" ON products;
DROP POLICY IF EXISTS "products_write_public" ON products;
DROP POLICY IF EXISTS "products_stock_adjust_staff" ON products;
CREATE POLICY "products_read_public" ON products FOR SELECT TO public USING (true);
CREATE POLICY "products_write_public" ON products FOR ALL TO public USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "store_settings_read" ON store_settings;
DROP POLICY IF EXISTS "store_settings_write" ON store_settings;
DROP POLICY IF EXISTS "store_settings_read_public" ON store_settings;
DROP POLICY IF EXISTS "store_settings_write_public" ON store_settings;
CREATE POLICY "store_settings_read_public" ON store_settings FOR SELECT TO public USING (true);
CREATE POLICY "store_settings_write_public" ON store_settings FOR ALL TO public USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "addresses_all_public" ON addresses;
DROP POLICY IF EXISTS "addresses_own_crud" ON addresses;
DROP POLICY IF EXISTS "addresses_staff_read" ON addresses;
CREATE POLICY "addresses_all_public" ON addresses FOR ALL TO public USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "orders_all_public" ON orders;
DROP POLICY IF EXISTS "orders_read_own" ON orders;
DROP POLICY IF EXISTS "orders_read_staff" ON orders;
DROP POLICY IF EXISTS "orders_update_status_staff" ON orders;
CREATE POLICY "orders_all_public" ON orders FOR ALL TO public USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "order_items_all_public" ON order_items;
DROP POLICY IF EXISTS "order_items_read_own" ON order_items;
DROP POLICY IF EXISTS "order_items_read_staff" ON order_items;
CREATE POLICY "order_items_all_public" ON order_items FOR ALL TO public USING (true) WITH CHECK (true);

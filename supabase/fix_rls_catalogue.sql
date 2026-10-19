-- ===========================================================================
-- Balaji Provision Store — Fix Catalogue, Orders & Realtime Sync
-- Run this in Supabase SQL Editor to enable real-time synchronization between
-- Web Admin Portal and Android Mobile App.
-- ===========================================================================

-- 1. Enable RLS
ALTER TABLE categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE products ENABLE ROW LEVEL SECURITY;
ALTER TABLE store_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE addresses ENABLE ROW LEVEL SECURITY;

-- 2. Categories Policies (Allow read & write for web admin and mobile app)
DROP POLICY IF EXISTS "categories_read_public" ON categories;
DROP POLICY IF EXISTS "categories_read_active" ON categories;
DROP POLICY IF EXISTS "categories_write" ON categories;
DROP POLICY IF EXISTS "categories_write_public" ON categories;

CREATE POLICY "categories_read_public" ON categories
  FOR SELECT TO public USING (true);

CREATE POLICY "categories_write_public" ON categories
  FOR ALL TO public USING (true) WITH CHECK (true);

-- 3. Products Policies (Allow read & write for web admin and mobile app)
DROP POLICY IF EXISTS "products_read_public" ON products;
DROP POLICY IF EXISTS "products_read_published" ON products;
DROP POLICY IF EXISTS "products_write_manager" ON products;
DROP POLICY IF EXISTS "products_write_public" ON products;
DROP POLICY IF EXISTS "products_stock_adjust_staff" ON products;

CREATE POLICY "products_read_public" ON products
  FOR SELECT TO public USING (true);

CREATE POLICY "products_write_public" ON products
  FOR ALL TO public USING (true) WITH CHECK (true);

-- 4. Store Settings Policies
DROP POLICY IF EXISTS "store_settings_read" ON store_settings;
DROP POLICY IF EXISTS "store_settings_write" ON store_settings;
DROP POLICY IF EXISTS "store_settings_read_public" ON store_settings;
DROP POLICY IF EXISTS "store_settings_write_public" ON store_settings;

CREATE POLICY "store_settings_read_public" ON store_settings
  FOR SELECT TO public USING (true);

CREATE POLICY "store_settings_write_public" ON store_settings
  FOR ALL TO public USING (true) WITH CHECK (true);

-- 5. Orders & Addresses Policies
DROP POLICY IF EXISTS "addresses_all_public" ON addresses;
DROP POLICY IF EXISTS "addresses_own_crud" ON addresses;
DROP POLICY IF EXISTS "addresses_staff_read" ON addresses;

CREATE POLICY "addresses_all_public" ON addresses
  FOR ALL TO public USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "orders_all_public" ON orders;
DROP POLICY IF EXISTS "orders_read_own" ON orders;
DROP POLICY IF EXISTS "orders_read_staff" ON orders;
DROP POLICY IF EXISTS "orders_update_status_staff" ON orders;

CREATE POLICY "orders_all_public" ON orders
  FOR ALL TO public USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "order_items_all_public" ON order_items;
DROP POLICY IF EXISTS "order_items_read_own" ON order_items;
DROP POLICY IF EXISTS "order_items_read_staff" ON order_items;

CREATE POLICY "order_items_all_public" ON order_items
  FOR ALL TO public USING (true) WITH CHECK (true);

-- 6. Add Tables to Realtime Publication
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE categories, products, orders, store_settings;
    END IF;
EXCEPTION
    WHEN OTHERS THEN NULL;
END $$;

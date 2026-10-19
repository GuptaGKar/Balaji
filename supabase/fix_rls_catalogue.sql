-- ===========================================================================
-- Balaji Provision Store — Fix Catalogue, Orders, Profiles, RPC Permissions & Realtime Sync
-- Run this in Supabase SQL Editor to enable order placement and real-time sync.
-- ===========================================================================

-- 1. Make customer_id optional so app guest checkouts never fail on profiles foreign key constraint
ALTER TABLE orders ALTER COLUMN customer_id DROP NOT NULL;

-- 2. Enable RLS
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE products ENABLE ROW LEVEL SECURITY;
ALTER TABLE store_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE addresses ENABLE ROW LEVEL SECURITY;

-- 3. Profiles Policies
DROP POLICY IF EXISTS "profiles_read_own" ON profiles;
DROP POLICY IF EXISTS "profiles_read_staff" ON profiles;
DROP POLICY IF EXISTS "profiles_update_role_active" ON profiles;
DROP POLICY IF EXISTS "profiles_all_public" ON profiles;

CREATE POLICY "profiles_all_public" ON profiles
  FOR ALL TO public USING (true) WITH CHECK (true);

-- Ensure Default Guest Profile
INSERT INTO profiles (id, role, display_name, phone, is_active)
VALUES ('00000000-0000-0000-0000-000000000001', 'customer', 'App Customer', '9837803293', true)
ON CONFLICT (id) DO NOTHING;

-- 4. Categories Policies
DROP POLICY IF EXISTS "categories_read_public" ON categories;
DROP POLICY IF EXISTS "categories_read_active" ON categories;
DROP POLICY IF EXISTS "categories_write" ON categories;
DROP POLICY IF EXISTS "categories_write_public" ON categories;

CREATE POLICY "categories_read_public" ON categories
  FOR SELECT TO public USING (true);

CREATE POLICY "categories_write_public" ON categories
  FOR ALL TO public USING (true) WITH CHECK (true);

-- 5. Products Policies
DROP POLICY IF EXISTS "products_read_public" ON products;
DROP POLICY IF EXISTS "products_read_published" ON products;
DROP POLICY IF EXISTS "products_write_manager" ON products;
DROP POLICY IF EXISTS "products_write_public" ON products;
DROP POLICY IF EXISTS "products_stock_adjust_staff" ON products;

CREATE POLICY "products_read_public" ON products
  FOR SELECT TO public USING (true);

CREATE POLICY "products_write_public" ON products
  FOR ALL TO public USING (true) WITH CHECK (true);

-- 6. Store Settings Policies
DROP POLICY IF EXISTS "store_settings_read" ON store_settings;
DROP POLICY IF EXISTS "store_settings_write" ON store_settings;
DROP POLICY IF EXISTS "store_settings_read_public" ON store_settings;
DROP POLICY IF EXISTS "store_settings_write_public" ON store_settings;

CREATE POLICY "store_settings_read_public" ON store_settings
  FOR SELECT TO public USING (true);

CREATE POLICY "store_settings_write_public" ON store_settings
  FOR ALL TO public USING (true) WITH CHECK (true);

-- 7. Orders & Addresses Policies
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

-- 8. Grant RPC Executions to Public
GRANT EXECUTE ON FUNCTION public.quote_cart(jsonb) TO anon, authenticated, public;
GRANT EXECUTE ON FUNCTION public.place_order(jsonb, text) TO anon, authenticated, public;

-- 9. Add Tables to Realtime Publication
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE categories, products, orders, store_settings;
    END IF;
EXCEPTION
    WHEN OTHERS THEN NULL;
END $$;

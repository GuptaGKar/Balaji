-- M1: enum types (IMPLEMENTATION_PLAN.md §4.2)
-- Idempotent enum creation guards to prevent 42710 errors on existing databases.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'user_role') THEN
        CREATE TYPE user_role AS ENUM ('customer', 'staff', 'manager', 'delivery');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'order_status') THEN
        CREATE TYPE order_status AS ENUM (
          'pending_review',
          'accepted',
          'preparing',
          'ready_for_dispatch',
          'out_for_delivery',
          'delivered',
          'rejected',
          'cancelled',
          'delivery_failed'
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'payment_method') THEN
        CREATE TYPE payment_method AS ENUM ('cod', 'online');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'payment_status') THEN
        CREATE TYPE payment_status AS ENUM (
          'unpaid',
          'pending',
          'paid',
          'failed',
          'refund_pending',
          'partially_refunded',
          'refunded'
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'quantity_type') THEN
        CREATE TYPE quantity_type AS ENUM ('weighted', 'packed', 'WEIGHTED', 'PACKED');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'service_area_kind') THEN
        CREATE TYPE service_area_kind AS ENUM ('pincode', 'neighbourhood');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'inventory_reason') THEN
        CREATE TYPE inventory_reason AS ENUM (
          'order_placed',
          'order_cancelled',
          'order_rejected',
          'item_removed',
          'pos_sale',
          'pos_void',
          'manual_adjustment',
          'correction'
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'bill_source') THEN
        CREATE TYPE bill_source AS ENUM ('ORDER', 'POS');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'bill_status') THEN
        CREATE TYPE bill_status AS ENUM ('ISSUED', 'VOID');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'tax_mode') THEN
        CREATE TYPE tax_mode AS ENUM ('none', 'inclusive', 'exclusive');
    END IF;
END $$;




-- ===========================================================================
-- Balaji Provision Store — Synthetic Seed Data
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Store settings — default single row
-- ---------------------------------------------------------------------------
INSERT INTO store_settings (
  id, store_open, timezone, weekly_hours, contact_phone,
  estimated_window_text, delivery_fee_paise, delivery_charge_threshold_paise,
  free_delivery_threshold_paise, mid_tier_fee_paise, max_order_paise,
  cancellation_policy_text, tax_mode, tax_rate_bps,
  online_payment_enabled, pos_price_override_enabled
) VALUES (
  true,
  true,
  'Asia/Kolkata',
  '{"mon":["08:00","22:00"],"tue":["08:00","22:00"],"wed":["08:00","22:00"],"thu":["08:00","22:00"],"fri":["08:00","22:00"],"sat":["08:00","22:00"],"sun":["08:00","22:00"]}'::jsonb,
  '9837803293',
  'Same-day delivery; the exact window is confirmed when the store accepts the order.',
  3000,   -- ₹30 fee when order subtotal is below delivery-charge threshold
  25000,  -- ₹250 threshold
  25000,  -- ₹250 free delivery threshold
  NULL,
  NULL,
  'Cancellation is allowed until the store accepts the order.',
  'none', 0, false, false
)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Categories
-- ---------------------------------------------------------------------------
INSERT INTO catalog_categories (name, slug, sort_order) VALUES
  ('Grains & Staples',  'grains-staples',  1),
  ('Spices & Masalas',  'spices-masalas',  2),
  ('Oils & Ghee',       'oils-ghee',       3),
  ('Snacks & Namkeen',  'snacks-namkeen',  4),
  ('Beverages',         'beverages',       5),
  ('Personal Care',     'personal-care',   6),
  ('Household',         'household',       7),
  ('Dairy & Eggs',      'dairy-eggs',      8),
  ('Fresh Produce',     'fresh-produce',   9)
ON CONFLICT (slug) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Products — PACKED (price per pack; integer quantities only)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('BASMATI-RICE-1KG',      'Basmati Rice 1 kg',              'Premium aged basmati rice',        (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 12000, 1, 'pack', '1 kg',   1, 1, 50, 10, true, true),
('WHOLE-WHEAT-ATTA-5KG',  'Whole Wheat Atta 5 kg',        'Freshly ground whole wheat flour', (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 22000, 1, 'pack', '5 kg',   1, 1, 30,  5, true, true),
('TOOR-DAL-1KG',          'Toor Dal 1 kg',                'Split pigeon peas',                (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 13000, 1, 'pack', '1 kg',   1, 1, 40, 10, true, true),
('SUNFLOWER-OIL-1L',      'Sunflower Oil 1 L',            'Refined sunflower oil',            (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 14000, 1, 'pack', '1 L',    1, 1, 25,  5, true, true),
('MUSTARD-OIL-1L',        'Cold-Pressed Mustard Oil 1 L', 'Cold-pressed mustard oil',         (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 15000, 1, 'pack', '1 L',    1, 1, 20,  5, true, true),
('TURMERIC-POWDER-200G',  'Turmeric Powder 200 g',        'Pure turmeric powder',             (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  6000, 1, 'pack', '200 g',  1, 1, 50, 10, true, true),
('RED-CHILLI-POWDER-200G','Red Chilli Powder 200 g',      'Spicy red chilli powder',          (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  7000, 1, 'pack', '200 g',  1, 1, 45, 10, true, true),
('IODIZED-SALT-1KG',      'Iodized Salt 1 kg',            'Iodized table salt',               (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  2000, 1, 'pack', '1 kg',   1, 1, 60, 15, true, true),
('ASSAM-TEA-250G',        'Assam CTC Tea 250 g',          'Strong Assam CTC tea',             (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  8000, 1, 'pack', '250 g',  1, 1, 35, 10, true, true),
('INSTANT-COFFEE-50G',    'Instant Coffee 50 g',          'Classic instant coffee',           (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  9000, 1, 'pack', '50 g',   1, 1, 30,  5, true, true),
('BIKANERI-BHUJIA-200G',  'Bikaneri Bhujia 200 g',        'Crispy gram flour snack',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5000, 1, 'pack', '200 g',  1, 1, 40, 10, true, true),
('NAVRATAN-MIXTURE-200G', 'Navratan Mixture 200 g',       'Assorted savory mixture',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5500, 1, 'pack', '200 g',  1, 1, 35, 10, true, true),
('PREMIUM-CASHEWS-1KG',   'Premium Cashews 1 kg',         'Whole cashews, kiln roasted',      (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 25000, 1, 'pack', '1 kg',   1, 1, 15,  5, true, true),
('MIXED-DRY-FRUITS-250G', 'Mixed Dry Fruits 250 g',       'Assorted dry fruits',              (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 24999, 1, 'pack', '250 g',  1, 1, 20,  5, true, true);

-- ---------------------------------------------------------------------------
-- Products — WEIGHTED (price per kg unless noted; quantities in grams)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('ONIONS-FRESH',     'Onions',           'Fresh red onions',     (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 50000,  5000, true, true),
('POTATOES-FRESH',   'Potatoes',         'Fresh potatoes',       (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 3000, 1000, 'kg',   NULL, 10, 100, 80000, 10000, true, true),
('TOMATOES-FRESH',   'Tomatoes',         'Fresh red tomatoes',   (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 5000, 1000, 'kg',   NULL, 10, 100, 30000,  3000, true, true),
('BANANAS-ROBUSTA',  'Bananas (Robusta)','Fresh robusta bananas',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 40000,  5000, true, true),
('FRESH-CORIANDER',  'Fresh Coriander',  'Fresh coriander leaves',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 1500,  100, '100 g', NULL, 10, 100,  5000,   500, true, true);

-- ---------------------------------------------------------------------------
-- Service areas
-- ---------------------------------------------------------------------------
INSERT INTO service_areas (kind, value, active) VALUES
  ('pincode', '122001', true),
  ('pincode', '122002', true)
ON CONFLICT (kind, lower(value)) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Synthetic test identities (auth.users + profiles)
-- ---------------------------------------------------------------------------
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at) VALUES
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'customer-a@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'customer-b@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000003', 'authenticated', 'authenticated', 'rider-a@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000004', 'authenticated', 'authenticated', 'rider-b@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000005', 'authenticated', 'authenticated', 'staff-1@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000006', 'authenticated', 'authenticated', 'manager-1@balaji-test.invalid',  'synthetic-placeholder', now(), now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO profiles (id, role, display_name, phone, login_id, is_active, must_change_password) VALUES
('aaaaaaaa-0000-0000-0000-000000000001', 'customer', 'Customer A',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000002', 'customer', 'Customer B',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000003', 'delivery', 'Rider A',     '9837803293', 'RIDER-A-001', true, true),
('aaaaaaaa-0000-0000-0000-000000000004', 'delivery', 'Rider B',     '9837803293', 'RIDER-B-002', true, true),
('aaaaaaaa-0000-0000-0000-000000000005', 'staff',    'Staff One',   '9837803293', 'STAFF-001',   true, false),
('aaaaaaaa-0000-0000-0000-000000000006', 'manager',  'Manager One', '9837803293', 'MANAGER-001', true, false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO bill_counters (fy, last_no) VALUES ('2026-27', 0)
ON CONFLICT (fy) DO NOTHING;-- M1: shared helper functions (IMPLEMENTATION_PLAN.md §4.1 conventions)

-- Keeps updated_at current on every UPDATE for tables that carry the column.
-- Attached by trigger in the table migrations below.
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END;
$$;




-- ===========================================================================
-- Balaji Provision Store — Synthetic Seed Data
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Store settings — default single row
-- ---------------------------------------------------------------------------
INSERT INTO store_settings (
  id, store_open, timezone, weekly_hours, contact_phone,
  estimated_window_text, delivery_fee_paise, delivery_charge_threshold_paise,
  free_delivery_threshold_paise, mid_tier_fee_paise, max_order_paise,
  cancellation_policy_text, tax_mode, tax_rate_bps,
  online_payment_enabled, pos_price_override_enabled
) VALUES (
  true,
  true,
  'Asia/Kolkata',
  '{"mon":["08:00","22:00"],"tue":["08:00","22:00"],"wed":["08:00","22:00"],"thu":["08:00","22:00"],"fri":["08:00","22:00"],"sat":["08:00","22:00"],"sun":["08:00","22:00"]}'::jsonb,
  '9837803293',
  'Same-day delivery; the exact window is confirmed when the store accepts the order.',
  3000,   -- ₹30 fee when order subtotal is below delivery-charge threshold
  25000,  -- ₹250 threshold
  25000,  -- ₹250 free delivery threshold
  NULL,
  NULL,
  'Cancellation is allowed until the store accepts the order.',
  'none', 0, false, false
)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Categories
-- ---------------------------------------------------------------------------
INSERT INTO catalog_categories (name, slug, sort_order) VALUES
  ('Grains & Staples',  'grains-staples',  1),
  ('Spices & Masalas',  'spices-masalas',  2),
  ('Oils & Ghee',       'oils-ghee',       3),
  ('Snacks & Namkeen',  'snacks-namkeen',  4),
  ('Beverages',         'beverages',       5),
  ('Personal Care',     'personal-care',   6),
  ('Household',         'household',       7),
  ('Dairy & Eggs',      'dairy-eggs',      8),
  ('Fresh Produce',     'fresh-produce',   9)
ON CONFLICT (slug) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Products — PACKED (price per pack; integer quantities only)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('BASMATI-RICE-1KG',      'Basmati Rice 1 kg',              'Premium aged basmati rice',        (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 12000, 1, 'pack', '1 kg',   1, 1, 50, 10, true, true),
('WHOLE-WHEAT-ATTA-5KG',  'Whole Wheat Atta 5 kg',        'Freshly ground whole wheat flour', (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 22000, 1, 'pack', '5 kg',   1, 1, 30,  5, true, true),
('TOOR-DAL-1KG',          'Toor Dal 1 kg',                'Split pigeon peas',                (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 13000, 1, 'pack', '1 kg',   1, 1, 40, 10, true, true),
('SUNFLOWER-OIL-1L',      'Sunflower Oil 1 L',            'Refined sunflower oil',            (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 14000, 1, 'pack', '1 L',    1, 1, 25,  5, true, true),
('MUSTARD-OIL-1L',        'Cold-Pressed Mustard Oil 1 L', 'Cold-pressed mustard oil',         (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 15000, 1, 'pack', '1 L',    1, 1, 20,  5, true, true),
('TURMERIC-POWDER-200G',  'Turmeric Powder 200 g',        'Pure turmeric powder',             (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  6000, 1, 'pack', '200 g',  1, 1, 50, 10, true, true),
('RED-CHILLI-POWDER-200G','Red Chilli Powder 200 g',      'Spicy red chilli powder',          (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  7000, 1, 'pack', '200 g',  1, 1, 45, 10, true, true),
('IODIZED-SALT-1KG',      'Iodized Salt 1 kg',            'Iodized table salt',               (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  2000, 1, 'pack', '1 kg',   1, 1, 60, 15, true, true),
('ASSAM-TEA-250G',        'Assam CTC Tea 250 g',          'Strong Assam CTC tea',             (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  8000, 1, 'pack', '250 g',  1, 1, 35, 10, true, true),
('INSTANT-COFFEE-50G',    'Instant Coffee 50 g',          'Classic instant coffee',           (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  9000, 1, 'pack', '50 g',   1, 1, 30,  5, true, true),
('BIKANERI-BHUJIA-200G',  'Bikaneri Bhujia 200 g',        'Crispy gram flour snack',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5000, 1, 'pack', '200 g',  1, 1, 40, 10, true, true),
('NAVRATAN-MIXTURE-200G', 'Navratan Mixture 200 g',       'Assorted savory mixture',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5500, 1, 'pack', '200 g',  1, 1, 35, 10, true, true),
('PREMIUM-CASHEWS-1KG',   'Premium Cashews 1 kg',         'Whole cashews, kiln roasted',      (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 25000, 1, 'pack', '1 kg',   1, 1, 15,  5, true, true),
('MIXED-DRY-FRUITS-250G', 'Mixed Dry Fruits 250 g',       'Assorted dry fruits',              (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 24999, 1, 'pack', '250 g',  1, 1, 20,  5, true, true);

-- ---------------------------------------------------------------------------
-- Products — WEIGHTED (price per kg unless noted; quantities in grams)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('ONIONS-FRESH',     'Onions',           'Fresh red onions',     (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 50000,  5000, true, true),
('POTATOES-FRESH',   'Potatoes',         'Fresh potatoes',       (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 3000, 1000, 'kg',   NULL, 10, 100, 80000, 10000, true, true),
('TOMATOES-FRESH',   'Tomatoes',         'Fresh red tomatoes',   (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 5000, 1000, 'kg',   NULL, 10, 100, 30000,  3000, true, true),
('BANANAS-ROBUSTA',  'Bananas (Robusta)','Fresh robusta bananas',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 40000,  5000, true, true),
('FRESH-CORIANDER',  'Fresh Coriander',  'Fresh coriander leaves',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 1500,  100, '100 g', NULL, 10, 100,  5000,   500, true, true);

-- ---------------------------------------------------------------------------
-- Service areas
-- ---------------------------------------------------------------------------
INSERT INTO service_areas (kind, value, active) VALUES
  ('pincode', '122001', true),
  ('pincode', '122002', true)
ON CONFLICT (kind, lower(value)) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Synthetic test identities (auth.users + profiles)
-- ---------------------------------------------------------------------------
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at) VALUES
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'customer-a@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'customer-b@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000003', 'authenticated', 'authenticated', 'rider-a@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000004', 'authenticated', 'authenticated', 'rider-b@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000005', 'authenticated', 'authenticated', 'staff-1@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000006', 'authenticated', 'authenticated', 'manager-1@balaji-test.invalid',  'synthetic-placeholder', now(), now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO profiles (id, role, display_name, phone, login_id, is_active, must_change_password) VALUES
('aaaaaaaa-0000-0000-0000-000000000001', 'customer', 'Customer A',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000002', 'customer', 'Customer B',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000003', 'delivery', 'Rider A',     '9837803293', 'RIDER-A-001', true, true),
('aaaaaaaa-0000-0000-0000-000000000004', 'delivery', 'Rider B',     '9837803293', 'RIDER-B-002', true, true),
('aaaaaaaa-0000-0000-0000-000000000005', 'staff',    'Staff One',   '9837803293', 'STAFF-001',   true, false),
('aaaaaaaa-0000-0000-0000-000000000006', 'manager',  'Manager One', '9837803293', 'MANAGER-001', true, false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO bill_counters (fy, last_no) VALUES ('2026-27', 0)
ON CONFLICT (fy) DO NOTHING;-- M1: identity and configuration tables (IMPLEMENTATION_PLAN.md §4.2)

CREATE TABLE IF NOT EXISTS profiles (
  id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  role user_role NOT NULL DEFAULT 'customer',
  display_name text,
  phone text,
  login_id text UNIQUE,
  is_active boolean NOT NULL DEFAULT true,
  must_change_password boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_profiles_role ON profiles(role);
CREATE INDEX IF NOT EXISTS idx_profiles_login_id ON profiles(login_id);

DROP TRIGGER IF EXISTS trg_profiles_updated_at ON profiles;
CREATE TRIGGER trg_profiles_updated_at BEFORE UPDATE ON profiles
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE IF NOT EXISTS store_settings (
  id boolean PRIMARY KEY DEFAULT true CHECK (id),
  store_open boolean NOT NULL DEFAULT true,
  timezone text NOT NULL DEFAULT 'Asia/Kolkata',
  weekly_hours jsonb NOT NULL DEFAULT '{}'::jsonb,
  contact_phone text NOT NULL DEFAULT '',
  estimated_window_text text,
  delivery_fee_paise bigint NOT NULL DEFAULT 0 CHECK (delivery_fee_paise >= 0),
  delivery_charge_threshold_paise bigint NOT NULL DEFAULT 0 CHECK (delivery_charge_threshold_paise >= 0),
  free_delivery_threshold_paise bigint
    CHECK (free_delivery_threshold_paise IS NULL
           OR free_delivery_threshold_paise >= delivery_charge_threshold_paise),
  mid_tier_fee_paise bigint CHECK (mid_tier_fee_paise IS NULL OR mid_tier_fee_paise >= 0),
  max_order_paise bigint CHECK (max_order_paise IS NULL OR max_order_paise >= 0),
  cancellation_policy_text text,
  tax_mode tax_mode NOT NULL DEFAULT 'none',
  tax_rate_bps integer NOT NULL DEFAULT 0 CHECK (tax_rate_bps >= 0),
  online_payment_enabled boolean NOT NULL DEFAULT false,
  pos_price_override_enabled boolean NOT NULL DEFAULT false
);

CREATE TABLE IF NOT EXISTS service_areas (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  kind service_area_kind NOT NULL,
  value text NOT NULL,
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_service_areas_kind_value_lower
  ON service_areas (kind, lower(value));
CREATE INDEX IF NOT EXISTS idx_service_areas_active ON service_areas(active);




-- ===========================================================================
-- Balaji Provision Store — Synthetic Seed Data
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Store settings — default single row
-- ---------------------------------------------------------------------------
INSERT INTO store_settings (
  id, store_open, timezone, weekly_hours, contact_phone,
  estimated_window_text, delivery_fee_paise, delivery_charge_threshold_paise,
  free_delivery_threshold_paise, mid_tier_fee_paise, max_order_paise,
  cancellation_policy_text, tax_mode, tax_rate_bps,
  online_payment_enabled, pos_price_override_enabled
) VALUES (
  true,
  true,
  'Asia/Kolkata',
  '{"mon":["08:00","22:00"],"tue":["08:00","22:00"],"wed":["08:00","22:00"],"thu":["08:00","22:00"],"fri":["08:00","22:00"],"sat":["08:00","22:00"],"sun":["08:00","22:00"]}'::jsonb,
  '9837803293',
  'Same-day delivery; the exact window is confirmed when the store accepts the order.',
  3000,   -- ₹30 fee when order subtotal is below delivery-charge threshold
  25000,  -- ₹250 threshold
  25000,  -- ₹250 free delivery threshold
  NULL,
  NULL,
  'Cancellation is allowed until the store accepts the order.',
  'none', 0, false, false
)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Categories
-- ---------------------------------------------------------------------------
INSERT INTO catalog_categories (name, slug, sort_order) VALUES
  ('Grains & Staples',  'grains-staples',  1),
  ('Spices & Masalas',  'spices-masalas',  2),
  ('Oils & Ghee',       'oils-ghee',       3),
  ('Snacks & Namkeen',  'snacks-namkeen',  4),
  ('Beverages',         'beverages',       5),
  ('Personal Care',     'personal-care',   6),
  ('Household',         'household',       7),
  ('Dairy & Eggs',      'dairy-eggs',      8),
  ('Fresh Produce',     'fresh-produce',   9)
ON CONFLICT (slug) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Products — PACKED (price per pack; integer quantities only)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('BASMATI-RICE-1KG',      'Basmati Rice 1 kg',              'Premium aged basmati rice',        (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 12000, 1, 'pack', '1 kg',   1, 1, 50, 10, true, true),
('WHOLE-WHEAT-ATTA-5KG',  'Whole Wheat Atta 5 kg',        'Freshly ground whole wheat flour', (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 22000, 1, 'pack', '5 kg',   1, 1, 30,  5, true, true),
('TOOR-DAL-1KG',          'Toor Dal 1 kg',                'Split pigeon peas',                (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 13000, 1, 'pack', '1 kg',   1, 1, 40, 10, true, true),
('SUNFLOWER-OIL-1L',      'Sunflower Oil 1 L',            'Refined sunflower oil',            (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 14000, 1, 'pack', '1 L',    1, 1, 25,  5, true, true),
('MUSTARD-OIL-1L',        'Cold-Pressed Mustard Oil 1 L', 'Cold-pressed mustard oil',         (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 15000, 1, 'pack', '1 L',    1, 1, 20,  5, true, true),
('TURMERIC-POWDER-200G',  'Turmeric Powder 200 g',        'Pure turmeric powder',             (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  6000, 1, 'pack', '200 g',  1, 1, 50, 10, true, true),
('RED-CHILLI-POWDER-200G','Red Chilli Powder 200 g',      'Spicy red chilli powder',          (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  7000, 1, 'pack', '200 g',  1, 1, 45, 10, true, true),
('IODIZED-SALT-1KG',      'Iodized Salt 1 kg',            'Iodized table salt',               (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  2000, 1, 'pack', '1 kg',   1, 1, 60, 15, true, true),
('ASSAM-TEA-250G',        'Assam CTC Tea 250 g',          'Strong Assam CTC tea',             (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  8000, 1, 'pack', '250 g',  1, 1, 35, 10, true, true),
('INSTANT-COFFEE-50G',    'Instant Coffee 50 g',          'Classic instant coffee',           (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  9000, 1, 'pack', '50 g',   1, 1, 30,  5, true, true),
('BIKANERI-BHUJIA-200G',  'Bikaneri Bhujia 200 g',        'Crispy gram flour snack',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5000, 1, 'pack', '200 g',  1, 1, 40, 10, true, true),
('NAVRATAN-MIXTURE-200G', 'Navratan Mixture 200 g',       'Assorted savory mixture',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5500, 1, 'pack', '200 g',  1, 1, 35, 10, true, true),
('PREMIUM-CASHEWS-1KG',   'Premium Cashews 1 kg',         'Whole cashews, kiln roasted',      (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 25000, 1, 'pack', '1 kg',   1, 1, 15,  5, true, true),
('MIXED-DRY-FRUITS-250G', 'Mixed Dry Fruits 250 g',       'Assorted dry fruits',              (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 24999, 1, 'pack', '250 g',  1, 1, 20,  5, true, true);

-- ---------------------------------------------------------------------------
-- Products — WEIGHTED (price per kg unless noted; quantities in grams)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('ONIONS-FRESH',     'Onions',           'Fresh red onions',     (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 50000,  5000, true, true),
('POTATOES-FRESH',   'Potatoes',         'Fresh potatoes',       (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 3000, 1000, 'kg',   NULL, 10, 100, 80000, 10000, true, true),
('TOMATOES-FRESH',   'Tomatoes',         'Fresh red tomatoes',   (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 5000, 1000, 'kg',   NULL, 10, 100, 30000,  3000, true, true),
('BANANAS-ROBUSTA',  'Bananas (Robusta)','Fresh robusta bananas',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 40000,  5000, true, true),
('FRESH-CORIANDER',  'Fresh Coriander',  'Fresh coriander leaves',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 1500,  100, '100 g', NULL, 10, 100,  5000,   500, true, true);

-- ---------------------------------------------------------------------------
-- Service areas
-- ---------------------------------------------------------------------------
INSERT INTO service_areas (kind, value, active) VALUES
  ('pincode', '122001', true),
  ('pincode', '122002', true)
ON CONFLICT (kind, lower(value)) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Synthetic test identities (auth.users + profiles)
-- ---------------------------------------------------------------------------
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at) VALUES
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'customer-a@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'customer-b@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000003', 'authenticated', 'authenticated', 'rider-a@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000004', 'authenticated', 'authenticated', 'rider-b@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000005', 'authenticated', 'authenticated', 'staff-1@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000006', 'authenticated', 'authenticated', 'manager-1@balaji-test.invalid',  'synthetic-placeholder', now(), now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO profiles (id, role, display_name, phone, login_id, is_active, must_change_password) VALUES
('aaaaaaaa-0000-0000-0000-000000000001', 'customer', 'Customer A',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000002', 'customer', 'Customer B',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000003', 'delivery', 'Rider A',     '9837803293', 'RIDER-A-001', true, true),
('aaaaaaaa-0000-0000-0000-000000000004', 'delivery', 'Rider B',     '9837803293', 'RIDER-B-002', true, true),
('aaaaaaaa-0000-0000-0000-000000000005', 'staff',    'Staff One',   '9837803293', 'STAFF-001',   true, false),
('aaaaaaaa-0000-0000-0000-000000000006', 'manager',  'Manager One', '9837803293', 'MANAGER-001', true, false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO bill_counters (fy, last_no) VALUES ('2026-27', 0)
ON CONFLICT (fy) DO NOTHING;-- M1: catalogue tables (IMPLEMENTATION_PLAN.md §4.2)

CREATE TABLE IF NOT EXISTS categories (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  slug text NOT NULL UNIQUE,
  sort_order integer NOT NULL DEFAULT 0,
  active boolean NOT NULL DEFAULT true
);

CREATE INDEX IF NOT EXISTS idx_categories_active ON categories(active);

CREATE TABLE IF NOT EXISTS products (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  sku text NOT NULL UNIQUE,
  name text NOT NULL,
  description text,
  category_id uuid REFERENCES categories(id) ON DELETE SET NULL,
  image_path text,
  quantity_type quantity_type NOT NULL,
  price_paise bigint NOT NULL CHECK (price_paise >= 0),
  price_basis_qty bigint NOT NULL,
  unit_label text NOT NULL,
  pack_size_label text,
  step_qty bigint NOT NULL CHECK (step_qty > 0),
  min_qty bigint NOT NULL CHECK (min_qty > 0),
  max_qty bigint CHECK (max_qty IS NULL OR max_qty >= min_qty),
  stock_qty bigint NOT NULL DEFAULT 0 CHECK (stock_qty >= 0),
  low_stock_threshold bigint NOT NULL DEFAULT 0 CHECK (low_stock_threshold >= 0),
  active boolean NOT NULL DEFAULT true,
  available boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_products_packed') THEN
        ALTER TABLE products ADD CONSTRAINT chk_products_packed
          CHECK (quantity_type NOT IN ('PACKED', 'packed') OR (price_basis_qty = 1 AND step_qty = 1));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_products_weighted_basis') THEN
        ALTER TABLE products ADD CONSTRAINT chk_products_weighted_basis
          CHECK (quantity_type NOT IN ('WEIGHTED', 'weighted') OR price_basis_qty IN (100, 1000));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_products_category ON products(category_id);
CREATE INDEX IF NOT EXISTS idx_products_catalogue ON products(quantity_type, active, available);

DROP TRIGGER IF EXISTS trg_products_updated_at ON products;
CREATE TRIGGER trg_products_updated_at BEFORE UPDATE ON products
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE FUNCTION prevent_product_delete_when_referenced()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM order_items WHERE product_id = OLD.id)
      OR EXISTS (SELECT 1 FROM bill_items WHERE product_id = OLD.id) THEN
    RAISE EXCEPTION
      'Cannot delete product %: referenced by order or bill items. Archive it instead (set active=false).',
      OLD.sku;
  END IF;
  RETURN OLD;
END;
$$;

DROP TRIGGER IF EXISTS trg_products_prevent_delete ON products;
CREATE TRIGGER trg_products_prevent_delete BEFORE DELETE ON products
  FOR EACH ROW EXECUTE FUNCTION prevent_product_delete_when_referenced();

-- Views for backwards compatibility between categories / catalog_categories and products / catalog_products
CREATE OR REPLACE VIEW catalog_categories AS SELECT * FROM categories;
CREATE OR REPLACE VIEW catalog_products AS SELECT * FROM products;




-- ===========================================================================
-- Balaji Provision Store — Synthetic Seed Data
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Store settings — default single row
-- ---------------------------------------------------------------------------
INSERT INTO store_settings (
  id, store_open, timezone, weekly_hours, contact_phone,
  estimated_window_text, delivery_fee_paise, delivery_charge_threshold_paise,
  free_delivery_threshold_paise, mid_tier_fee_paise, max_order_paise,
  cancellation_policy_text, tax_mode, tax_rate_bps,
  online_payment_enabled, pos_price_override_enabled
) VALUES (
  true,
  true,
  'Asia/Kolkata',
  '{"mon":["08:00","22:00"],"tue":["08:00","22:00"],"wed":["08:00","22:00"],"thu":["08:00","22:00"],"fri":["08:00","22:00"],"sat":["08:00","22:00"],"sun":["08:00","22:00"]}'::jsonb,
  '9837803293',
  'Same-day delivery; the exact window is confirmed when the store accepts the order.',
  3000,   -- ₹30 fee when order subtotal is below delivery-charge threshold
  25000,  -- ₹250 threshold
  25000,  -- ₹250 free delivery threshold
  NULL,
  NULL,
  'Cancellation is allowed until the store accepts the order.',
  'none', 0, false, false
)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Categories
-- ---------------------------------------------------------------------------
INSERT INTO catalog_categories (name, slug, sort_order) VALUES
  ('Grains & Staples',  'grains-staples',  1),
  ('Spices & Masalas',  'spices-masalas',  2),
  ('Oils & Ghee',       'oils-ghee',       3),
  ('Snacks & Namkeen',  'snacks-namkeen',  4),
  ('Beverages',         'beverages',       5),
  ('Personal Care',     'personal-care',   6),
  ('Household',         'household',       7),
  ('Dairy & Eggs',      'dairy-eggs',      8),
  ('Fresh Produce',     'fresh-produce',   9)
ON CONFLICT (slug) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Products — PACKED (price per pack; integer quantities only)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('BASMATI-RICE-1KG',      'Basmati Rice 1 kg',              'Premium aged basmati rice',        (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 12000, 1, 'pack', '1 kg',   1, 1, 50, 10, true, true),
('WHOLE-WHEAT-ATTA-5KG',  'Whole Wheat Atta 5 kg',        'Freshly ground whole wheat flour', (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 22000, 1, 'pack', '5 kg',   1, 1, 30,  5, true, true),
('TOOR-DAL-1KG',          'Toor Dal 1 kg',                'Split pigeon peas',                (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 13000, 1, 'pack', '1 kg',   1, 1, 40, 10, true, true),
('SUNFLOWER-OIL-1L',      'Sunflower Oil 1 L',            'Refined sunflower oil',            (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 14000, 1, 'pack', '1 L',    1, 1, 25,  5, true, true),
('MUSTARD-OIL-1L',        'Cold-Pressed Mustard Oil 1 L', 'Cold-pressed mustard oil',         (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 15000, 1, 'pack', '1 L',    1, 1, 20,  5, true, true),
('TURMERIC-POWDER-200G',  'Turmeric Powder 200 g',        'Pure turmeric powder',             (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  6000, 1, 'pack', '200 g',  1, 1, 50, 10, true, true),
('RED-CHILLI-POWDER-200G','Red Chilli Powder 200 g',      'Spicy red chilli powder',          (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  7000, 1, 'pack', '200 g',  1, 1, 45, 10, true, true),
('IODIZED-SALT-1KG',      'Iodized Salt 1 kg',            'Iodized table salt',               (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  2000, 1, 'pack', '1 kg',   1, 1, 60, 15, true, true),
('ASSAM-TEA-250G',        'Assam CTC Tea 250 g',          'Strong Assam CTC tea',             (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  8000, 1, 'pack', '250 g',  1, 1, 35, 10, true, true),
('INSTANT-COFFEE-50G',    'Instant Coffee 50 g',          'Classic instant coffee',           (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  9000, 1, 'pack', '50 g',   1, 1, 30,  5, true, true),
('BIKANERI-BHUJIA-200G',  'Bikaneri Bhujia 200 g',        'Crispy gram flour snack',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5000, 1, 'pack', '200 g',  1, 1, 40, 10, true, true),
('NAVRATAN-MIXTURE-200G', 'Navratan Mixture 200 g',       'Assorted savory mixture',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5500, 1, 'pack', '200 g',  1, 1, 35, 10, true, true),
('PREMIUM-CASHEWS-1KG',   'Premium Cashews 1 kg',         'Whole cashews, kiln roasted',      (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 25000, 1, 'pack', '1 kg',   1, 1, 15,  5, true, true),
('MIXED-DRY-FRUITS-250G', 'Mixed Dry Fruits 250 g',       'Assorted dry fruits',              (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 24999, 1, 'pack', '250 g',  1, 1, 20,  5, true, true);

-- ---------------------------------------------------------------------------
-- Products — WEIGHTED (price per kg unless noted; quantities in grams)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('ONIONS-FRESH',     'Onions',           'Fresh red onions',     (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 50000,  5000, true, true),
('POTATOES-FRESH',   'Potatoes',         'Fresh potatoes',       (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 3000, 1000, 'kg',   NULL, 10, 100, 80000, 10000, true, true),
('TOMATOES-FRESH',   'Tomatoes',         'Fresh red tomatoes',   (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 5000, 1000, 'kg',   NULL, 10, 100, 30000,  3000, true, true),
('BANANAS-ROBUSTA',  'Bananas (Robusta)','Fresh robusta bananas',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 40000,  5000, true, true),
('FRESH-CORIANDER',  'Fresh Coriander',  'Fresh coriander leaves',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 1500,  100, '100 g', NULL, 10, 100,  5000,   500, true, true);

-- ---------------------------------------------------------------------------
-- Service areas
-- ---------------------------------------------------------------------------
INSERT INTO service_areas (kind, value, active) VALUES
  ('pincode', '122001', true),
  ('pincode', '122002', true)
ON CONFLICT (kind, lower(value)) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Synthetic test identities (auth.users + profiles)
-- ---------------------------------------------------------------------------
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at) VALUES
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'customer-a@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'customer-b@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000003', 'authenticated', 'authenticated', 'rider-a@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000004', 'authenticated', 'authenticated', 'rider-b@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000005', 'authenticated', 'authenticated', 'staff-1@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000006', 'authenticated', 'authenticated', 'manager-1@balaji-test.invalid',  'synthetic-placeholder', now(), now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO profiles (id, role, display_name, phone, login_id, is_active, must_change_password) VALUES
('aaaaaaaa-0000-0000-0000-000000000001', 'customer', 'Customer A',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000002', 'customer', 'Customer B',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000003', 'delivery', 'Rider A',     '9837803293', 'RIDER-A-001', true, true),
('aaaaaaaa-0000-0000-0000-000000000004', 'delivery', 'Rider B',     '9837803293', 'RIDER-B-002', true, true),
('aaaaaaaa-0000-0000-0000-000000000005', 'staff',    'Staff One',   '9837803293', 'STAFF-001',   true, false),
('aaaaaaaa-0000-0000-0000-000000000006', 'manager',  'Manager One', '9837803293', 'MANAGER-001', true, false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO bill_counters (fy, last_no) VALUES ('2026-27', 0)
ON CONFLICT (fy) DO NOTHING;-- M1: customer addresses, orders, order items, status events, assignments,
--     and the order state machine (IMPLEMENTATION_PLAN.md §4.2, §4.3)

CREATE TABLE IF NOT EXISTS addresses (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  label text NOT NULL DEFAULT '',
  recipient_name text NOT NULL,
  phone text NOT NULL,
  address_lines text NOT NULL,
  landmark text,
  area text,
  city text NOT NULL DEFAULT '',
  state text NOT NULL DEFAULT '',
  postal_code text NOT NULL,
  lat double precision,
  lng double precision,
  is_default boolean NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_addresses_customer ON addresses(customer_id);

CREATE TABLE IF NOT EXISTS orders (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  public_order_number text NOT NULL UNIQUE,
  customer_id uuid NOT NULL REFERENCES profiles(id),
  address_snapshot jsonb NOT NULL,
  status order_status NOT NULL DEFAULT 'pending_review',
  payment_method payment_method NOT NULL DEFAULT 'cod',
  payment_status payment_status NOT NULL DEFAULT 'unpaid',
  item_subtotal_paise bigint NOT NULL DEFAULT 0 CHECK (item_subtotal_paise >= 0),
  discount_paise bigint NOT NULL DEFAULT 0 CHECK (discount_paise >= 0),
  delivery_fee_paise bigint NOT NULL DEFAULT 0 CHECK (delivery_fee_paise >= 0),
  tax_paise bigint NOT NULL DEFAULT 0 CHECK (tax_paise >= 0),
  grand_total_paise bigint NOT NULL DEFAULT 0 CHECK (grand_total_paise >= 0),
  fee_breakdown jsonb,
  customer_note text,
  assigned_to uuid REFERENCES profiles(id),
  idempotency_key text,
  cod_collected_paise bigint CHECK (cod_collected_paise IS NULL OR cod_collected_paise >= 0),
  created_at timestamptz NOT NULL DEFAULT now(),
  accepted_at timestamptz,
  delivered_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_orders_customer_created ON orders(customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_idempotency ON orders(idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE TABLE IF NOT EXISTS order_items (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id uuid NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  product_id uuid REFERENCES products(id) ON DELETE SET NULL,
  sku_snapshot text NOT NULL,
  name_snapshot text NOT NULL,
  quantity_type_snapshot quantity_type NOT NULL,
  unit_label_snapshot text NOT NULL,
  price_basis_qty_snapshot bigint NOT NULL,
  qty_base bigint NOT NULL CHECK (qty_base > 0),
  unit_price_paise bigint NOT NULL CHECK (unit_price_paise >= 0),
  line_total_paise bigint NOT NULL CHECK (line_total_paise >= 0),
  removed_at timestamptz,
  removed_reason text
);

CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items(order_id);

CREATE TABLE IF NOT EXISTS order_status_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id uuid NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  from_status order_status,
  to_status order_status NOT NULL,
  actor_id uuid REFERENCES profiles(id),
  reason text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_order_status_events_order ON order_status_events(order_id, created_at DESC);

CREATE TABLE IF NOT EXISTS order_assignments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id uuid NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  delivery_user_id uuid NOT NULL REFERENCES profiles(id),
  assigned_by uuid REFERENCES profiles(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  unassigned_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_order_assignments_order ON order_assignments(order_id);
CREATE INDEX IF NOT EXISTS idx_order_assignments_active
  ON order_assignments(delivery_user_id) WHERE unassigned_at IS NULL;

CREATE TABLE IF NOT EXISTS order_transitions (
  from_status order_status NOT NULL,
  to_status order_status NOT NULL,
  allowed_roles user_role[] NOT NULL,
  PRIMARY KEY (from_status, to_status)
);

INSERT INTO order_transitions (from_status, to_status, allowed_roles) VALUES
  ('pending_review',    'accepted',           ARRAY['staff', 'manager']::user_role[]),
  ('pending_review',    'rejected',           ARRAY['staff', 'manager']::user_role[]),
  ('pending_review',    'cancelled',          ARRAY['customer', 'staff', 'manager']::user_role[]),
  ('accepted',          'preparing',          ARRAY['staff', 'manager']::user_role[]),
  ('accepted',          'cancelled',          ARRAY['staff', 'manager']::user_role[]),
  ('preparing',         'ready_for_dispatch', ARRAY['staff', 'manager']::user_role[]),
  ('preparing',         'cancelled',          ARRAY['staff', 'manager']::user_role[]),
  ('ready_for_dispatch','out_for_delivery',   ARRAY['staff', 'manager', 'delivery']::user_role[]),
  ('ready_for_dispatch','cancelled',          ARRAY['staff', 'manager']::user_role[]),
  ('out_for_delivery',  'delivered',          ARRAY['staff', 'manager', 'delivery']::user_role[]),
  ('out_for_delivery',  'delivery_failed',    ARRAY['staff', 'manager', 'delivery']::user_role[])
ON CONFLICT (from_status, to_status) DO NOTHING;




-- ===========================================================================
-- Balaji Provision Store — Synthetic Seed Data
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Store settings — default single row
-- ---------------------------------------------------------------------------
INSERT INTO store_settings (
  id, store_open, timezone, weekly_hours, contact_phone,
  estimated_window_text, delivery_fee_paise, delivery_charge_threshold_paise,
  free_delivery_threshold_paise, mid_tier_fee_paise, max_order_paise,
  cancellation_policy_text, tax_mode, tax_rate_bps,
  online_payment_enabled, pos_price_override_enabled
) VALUES (
  true,
  true,
  'Asia/Kolkata',
  '{"mon":["08:00","22:00"],"tue":["08:00","22:00"],"wed":["08:00","22:00"],"thu":["08:00","22:00"],"fri":["08:00","22:00"],"sat":["08:00","22:00"],"sun":["08:00","22:00"]}'::jsonb,
  '9837803293',
  'Same-day delivery; the exact window is confirmed when the store accepts the order.',
  3000,   -- ₹30 fee when order subtotal is below delivery-charge threshold
  25000,  -- ₹250 threshold
  25000,  -- ₹250 free delivery threshold
  NULL,
  NULL,
  'Cancellation is allowed until the store accepts the order.',
  'none', 0, false, false
)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Categories
-- ---------------------------------------------------------------------------
INSERT INTO catalog_categories (name, slug, sort_order) VALUES
  ('Grains & Staples',  'grains-staples',  1),
  ('Spices & Masalas',  'spices-masalas',  2),
  ('Oils & Ghee',       'oils-ghee',       3),
  ('Snacks & Namkeen',  'snacks-namkeen',  4),
  ('Beverages',         'beverages',       5),
  ('Personal Care',     'personal-care',   6),
  ('Household',         'household',       7),
  ('Dairy & Eggs',      'dairy-eggs',      8),
  ('Fresh Produce',     'fresh-produce',   9)
ON CONFLICT (slug) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Products — PACKED (price per pack; integer quantities only)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('BASMATI-RICE-1KG',      'Basmati Rice 1 kg',              'Premium aged basmati rice',        (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 12000, 1, 'pack', '1 kg',   1, 1, 50, 10, true, true),
('WHOLE-WHEAT-ATTA-5KG',  'Whole Wheat Atta 5 kg',        'Freshly ground whole wheat flour', (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 22000, 1, 'pack', '5 kg',   1, 1, 30,  5, true, true),
('TOOR-DAL-1KG',          'Toor Dal 1 kg',                'Split pigeon peas',                (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 13000, 1, 'pack', '1 kg',   1, 1, 40, 10, true, true),
('SUNFLOWER-OIL-1L',      'Sunflower Oil 1 L',            'Refined sunflower oil',            (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 14000, 1, 'pack', '1 L',    1, 1, 25,  5, true, true),
('MUSTARD-OIL-1L',        'Cold-Pressed Mustard Oil 1 L', 'Cold-pressed mustard oil',         (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 15000, 1, 'pack', '1 L',    1, 1, 20,  5, true, true),
('TURMERIC-POWDER-200G',  'Turmeric Powder 200 g',        'Pure turmeric powder',             (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  6000, 1, 'pack', '200 g',  1, 1, 50, 10, true, true),
('RED-CHILLI-POWDER-200G','Red Chilli Powder 200 g',      'Spicy red chilli powder',          (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  7000, 1, 'pack', '200 g',  1, 1, 45, 10, true, true),
('IODIZED-SALT-1KG',      'Iodized Salt 1 kg',            'Iodized table salt',               (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  2000, 1, 'pack', '1 kg',   1, 1, 60, 15, true, true),
('ASSAM-TEA-250G',        'Assam CTC Tea 250 g',          'Strong Assam CTC tea',             (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  8000, 1, 'pack', '250 g',  1, 1, 35, 10, true, true),
('INSTANT-COFFEE-50G',    'Instant Coffee 50 g',          'Classic instant coffee',           (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  9000, 1, 'pack', '50 g',   1, 1, 30,  5, true, true),
('BIKANERI-BHUJIA-200G',  'Bikaneri Bhujia 200 g',        'Crispy gram flour snack',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5000, 1, 'pack', '200 g',  1, 1, 40, 10, true, true),
('NAVRATAN-MIXTURE-200G', 'Navratan Mixture 200 g',       'Assorted savory mixture',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5500, 1, 'pack', '200 g',  1, 1, 35, 10, true, true),
('PREMIUM-CASHEWS-1KG',   'Premium Cashews 1 kg',         'Whole cashews, kiln roasted',      (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 25000, 1, 'pack', '1 kg',   1, 1, 15,  5, true, true),
('MIXED-DRY-FRUITS-250G', 'Mixed Dry Fruits 250 g',       'Assorted dry fruits',              (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 24999, 1, 'pack', '250 g',  1, 1, 20,  5, true, true);

-- ---------------------------------------------------------------------------
-- Products — WEIGHTED (price per kg unless noted; quantities in grams)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('ONIONS-FRESH',     'Onions',           'Fresh red onions',     (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 50000,  5000, true, true),
('POTATOES-FRESH',   'Potatoes',         'Fresh potatoes',       (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 3000, 1000, 'kg',   NULL, 10, 100, 80000, 10000, true, true),
('TOMATOES-FRESH',   'Tomatoes',         'Fresh red tomatoes',   (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 5000, 1000, 'kg',   NULL, 10, 100, 30000,  3000, true, true),
('BANANAS-ROBUSTA',  'Bananas (Robusta)','Fresh robusta bananas',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 40000,  5000, true, true),
('FRESH-CORIANDER',  'Fresh Coriander',  'Fresh coriander leaves',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 1500,  100, '100 g', NULL, 10, 100,  5000,   500, true, true);

-- ---------------------------------------------------------------------------
-- Service areas
-- ---------------------------------------------------------------------------
INSERT INTO service_areas (kind, value, active) VALUES
  ('pincode', '122001', true),
  ('pincode', '122002', true)
ON CONFLICT (kind, lower(value)) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Synthetic test identities (auth.users + profiles)
-- ---------------------------------------------------------------------------
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at) VALUES
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'customer-a@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'customer-b@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000003', 'authenticated', 'authenticated', 'rider-a@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000004', 'authenticated', 'authenticated', 'rider-b@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000005', 'authenticated', 'authenticated', 'staff-1@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000006', 'authenticated', 'authenticated', 'manager-1@balaji-test.invalid',  'synthetic-placeholder', now(), now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO profiles (id, role, display_name, phone, login_id, is_active, must_change_password) VALUES
('aaaaaaaa-0000-0000-0000-000000000001', 'customer', 'Customer A',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000002', 'customer', 'Customer B',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000003', 'delivery', 'Rider A',     '9837803293', 'RIDER-A-001', true, true),
('aaaaaaaa-0000-0000-0000-000000000004', 'delivery', 'Rider B',     '9837803293', 'RIDER-B-002', true, true),
('aaaaaaaa-0000-0000-0000-000000000005', 'staff',    'Staff One',   '9837803293', 'STAFF-001',   true, false),
('aaaaaaaa-0000-0000-0000-000000000006', 'manager',  'Manager One', '9837803293', 'MANAGER-001', true, false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO bill_counters (fy, last_no) VALUES ('2026-27', 0)
ON CONFLICT (fy) DO NOTHING;-- M1: inventory movements and billing/POS tables (IMPLEMENTATION_PLAN.md §4.2)

CREATE TABLE IF NOT EXISTS inventory_movements (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id uuid NOT NULL REFERENCES products(id),
  delta_qty bigint NOT NULL,
  reason inventory_reason NOT NULL,
  reference_type text,
  reference_id uuid,
  actor_id uuid REFERENCES profiles(id),
  note text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_inventory_movements_product
  ON inventory_movements(product_id, created_at DESC);

CREATE TABLE IF NOT EXISTS bill_counters (
  fy text PRIMARY KEY,
  last_no bigint NOT NULL DEFAULT 0 CHECK (last_no >= 0)
);

CREATE TABLE IF NOT EXISTS pos_sales (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  idempotency_key text NOT NULL UNIQUE,
  cashier_id uuid NOT NULL REFERENCES profiles(id),
  payment_method payment_method NOT NULL DEFAULT 'cod',
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS bills (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  bill_number text NOT NULL UNIQUE,
  source bill_source NOT NULL,
  order_id uuid REFERENCES orders(id) ON DELETE SET NULL,
  pos_sale_id uuid REFERENCES pos_sales(id) ON DELETE SET NULL,
  status bill_status NOT NULL DEFAULT 'ISSUED',
  issued_at timestamptz NOT NULL DEFAULT now(),
  issued_by uuid REFERENCES profiles(id),
  customer_name text,
  customer_phone text,
  subtotal_paise bigint NOT NULL DEFAULT 0 CHECK (subtotal_paise >= 0),
  discount_paise bigint NOT NULL DEFAULT 0 CHECK (discount_paise >= 0),
  delivery_fee_paise bigint NOT NULL DEFAULT 0 CHECK (delivery_fee_paise >= 0),
  tax_paise bigint NOT NULL DEFAULT 0 CHECK (tax_paise >= 0),
  grand_total_paise bigint NOT NULL DEFAULT 0 CHECK (grand_total_paise >= 0),
  payment_method payment_method NOT NULL DEFAULT 'cod',
  payment_status payment_status NOT NULL DEFAULT 'unpaid',
  void_reason text,
  voided_by uuid REFERENCES profiles(id),
  voided_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_bills_order ON bills(order_id);
CREATE INDEX IF NOT EXISTS idx_bills_pos_sale ON bills(pos_sale_id);
CREATE INDEX IF NOT EXISTS idx_bills_issued ON bills(issued_at DESC);

CREATE TABLE IF NOT EXISTS bill_items (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  bill_id uuid NOT NULL REFERENCES bills(id) ON DELETE CASCADE,
  product_id uuid REFERENCES products(id) ON DELETE SET NULL,
  sku text NOT NULL,
  name text NOT NULL,
  quantity_type quantity_type NOT NULL,
  qty_base bigint NOT NULL CHECK (qty_base > 0),
  unit_label text NOT NULL,
  price_basis_qty bigint NOT NULL,
  unit_price_paise bigint NOT NULL CHECK (unit_price_paise >= 0),
  line_total_paise bigint NOT NULL CHECK (line_total_paise >= 0)
);

CREATE INDEX IF NOT EXISTS idx_bill_items_bill ON bill_items(bill_id);




-- ===========================================================================
-- Balaji Provision Store — Synthetic Seed Data
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Store settings — default single row
-- ---------------------------------------------------------------------------
INSERT INTO store_settings (
  id, store_open, timezone, weekly_hours, contact_phone,
  estimated_window_text, delivery_fee_paise, delivery_charge_threshold_paise,
  free_delivery_threshold_paise, mid_tier_fee_paise, max_order_paise,
  cancellation_policy_text, tax_mode, tax_rate_bps,
  online_payment_enabled, pos_price_override_enabled
) VALUES (
  true,
  true,
  'Asia/Kolkata',
  '{"mon":["08:00","22:00"],"tue":["08:00","22:00"],"wed":["08:00","22:00"],"thu":["08:00","22:00"],"fri":["08:00","22:00"],"sat":["08:00","22:00"],"sun":["08:00","22:00"]}'::jsonb,
  '9837803293',
  'Same-day delivery; the exact window is confirmed when the store accepts the order.',
  3000,   -- ₹30 fee when order subtotal is below delivery-charge threshold
  25000,  -- ₹250 threshold
  25000,  -- ₹250 free delivery threshold
  NULL,
  NULL,
  'Cancellation is allowed until the store accepts the order.',
  'none', 0, false, false
)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Categories
-- ---------------------------------------------------------------------------
INSERT INTO catalog_categories (name, slug, sort_order) VALUES
  ('Grains & Staples',  'grains-staples',  1),
  ('Spices & Masalas',  'spices-masalas',  2),
  ('Oils & Ghee',       'oils-ghee',       3),
  ('Snacks & Namkeen',  'snacks-namkeen',  4),
  ('Beverages',         'beverages',       5),
  ('Personal Care',     'personal-care',   6),
  ('Household',         'household',       7),
  ('Dairy & Eggs',      'dairy-eggs',      8),
  ('Fresh Produce',     'fresh-produce',   9)
ON CONFLICT (slug) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Products — PACKED (price per pack; integer quantities only)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('BASMATI-RICE-1KG',      'Basmati Rice 1 kg',              'Premium aged basmati rice',        (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 12000, 1, 'pack', '1 kg',   1, 1, 50, 10, true, true),
('WHOLE-WHEAT-ATTA-5KG',  'Whole Wheat Atta 5 kg',        'Freshly ground whole wheat flour', (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 22000, 1, 'pack', '5 kg',   1, 1, 30,  5, true, true),
('TOOR-DAL-1KG',          'Toor Dal 1 kg',                'Split pigeon peas',                (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 13000, 1, 'pack', '1 kg',   1, 1, 40, 10, true, true),
('SUNFLOWER-OIL-1L',      'Sunflower Oil 1 L',            'Refined sunflower oil',            (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 14000, 1, 'pack', '1 L',    1, 1, 25,  5, true, true),
('MUSTARD-OIL-1L',        'Cold-Pressed Mustard Oil 1 L', 'Cold-pressed mustard oil',         (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 15000, 1, 'pack', '1 L',    1, 1, 20,  5, true, true),
('TURMERIC-POWDER-200G',  'Turmeric Powder 200 g',        'Pure turmeric powder',             (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  6000, 1, 'pack', '200 g',  1, 1, 50, 10, true, true),
('RED-CHILLI-POWDER-200G','Red Chilli Powder 200 g',      'Spicy red chilli powder',          (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  7000, 1, 'pack', '200 g',  1, 1, 45, 10, true, true),
('IODIZED-SALT-1KG',      'Iodized Salt 1 kg',            'Iodized table salt',               (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  2000, 1, 'pack', '1 kg',   1, 1, 60, 15, true, true),
('ASSAM-TEA-250G',        'Assam CTC Tea 250 g',          'Strong Assam CTC tea',             (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  8000, 1, 'pack', '250 g',  1, 1, 35, 10, true, true),
('INSTANT-COFFEE-50G',    'Instant Coffee 50 g',          'Classic instant coffee',           (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  9000, 1, 'pack', '50 g',   1, 1, 30,  5, true, true),
('BIKANERI-BHUJIA-200G',  'Bikaneri Bhujia 200 g',        'Crispy gram flour snack',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5000, 1, 'pack', '200 g',  1, 1, 40, 10, true, true),
('NAVRATAN-MIXTURE-200G', 'Navratan Mixture 200 g',       'Assorted savory mixture',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5500, 1, 'pack', '200 g',  1, 1, 35, 10, true, true),
('PREMIUM-CASHEWS-1KG',   'Premium Cashews 1 kg',         'Whole cashews, kiln roasted',      (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 25000, 1, 'pack', '1 kg',   1, 1, 15,  5, true, true),
('MIXED-DRY-FRUITS-250G', 'Mixed Dry Fruits 250 g',       'Assorted dry fruits',              (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 24999, 1, 'pack', '250 g',  1, 1, 20,  5, true, true);

-- ---------------------------------------------------------------------------
-- Products — WEIGHTED (price per kg unless noted; quantities in grams)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('ONIONS-FRESH',     'Onions',           'Fresh red onions',     (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 50000,  5000, true, true),
('POTATOES-FRESH',   'Potatoes',         'Fresh potatoes',       (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 3000, 1000, 'kg',   NULL, 10, 100, 80000, 10000, true, true),
('TOMATOES-FRESH',   'Tomatoes',         'Fresh red tomatoes',   (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 5000, 1000, 'kg',   NULL, 10, 100, 30000,  3000, true, true),
('BANANAS-ROBUSTA',  'Bananas (Robusta)','Fresh robusta bananas',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 40000,  5000, true, true),
('FRESH-CORIANDER',  'Fresh Coriander',  'Fresh coriander leaves',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 1500,  100, '100 g', NULL, 10, 100,  5000,   500, true, true);

-- ---------------------------------------------------------------------------
-- Service areas
-- ---------------------------------------------------------------------------
INSERT INTO service_areas (kind, value, active) VALUES
  ('pincode', '122001', true),
  ('pincode', '122002', true)
ON CONFLICT (kind, lower(value)) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Synthetic test identities (auth.users + profiles)
-- ---------------------------------------------------------------------------
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at) VALUES
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'customer-a@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'customer-b@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000003', 'authenticated', 'authenticated', 'rider-a@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000004', 'authenticated', 'authenticated', 'rider-b@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000005', 'authenticated', 'authenticated', 'staff-1@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000006', 'authenticated', 'authenticated', 'manager-1@balaji-test.invalid',  'synthetic-placeholder', now(), now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO profiles (id, role, display_name, phone, login_id, is_active, must_change_password) VALUES
('aaaaaaaa-0000-0000-0000-000000000001', 'customer', 'Customer A',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000002', 'customer', 'Customer B',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000003', 'delivery', 'Rider A',     '9837803293', 'RIDER-A-001', true, true),
('aaaaaaaa-0000-0000-0000-000000000004', 'delivery', 'Rider B',     '9837803293', 'RIDER-B-002', true, true),
('aaaaaaaa-0000-0000-0000-000000000005', 'staff',    'Staff One',   '9837803293', 'STAFF-001',   true, false),
('aaaaaaaa-0000-0000-0000-000000000006', 'manager',  'Manager One', '9837803293', 'MANAGER-001', true, false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO bill_counters (fy, last_no) VALUES ('2026-27', 0)
ON CONFLICT (fy) DO NOTHING;-- M1: gated payment tables and cross-cutting tables (IMPLEMENTATION_PLAN.md §4.2)

CREATE TABLE IF NOT EXISTS payments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id uuid NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  provider text NOT NULL,
  provider_order_id text,
  provider_payment_id text,
  amount_paise bigint NOT NULL CHECK (amount_paise >= 0),
  status text NOT NULL DEFAULT 'pending',
  verified_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_payments_order ON payments(order_id);

CREATE TABLE IF NOT EXISTS payment_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  provider text NOT NULL,
  provider_event_id text NOT NULL,
  payload_hash text NOT NULL,
  received_at timestamptz NOT NULL DEFAULT now(),
  processed_at timestamptz,
  UNIQUE (provider, provider_event_id)
);

CREATE TABLE IF NOT EXISTS idempotency_keys (
  user_id uuid NOT NULL,
  scope text NOT NULL,
  key text NOT NULL,
  request_hash text NOT NULL,
  response jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, scope, key)
);

CREATE TABLE IF NOT EXISTS audit_logs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_id uuid REFERENCES profiles(id),
  action text NOT NULL,
  entity_type text NOT NULL,
  entity_id text,
  before jsonb,
  after jsonb,
  request_id text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON audit_logs(created_at DESC);

REVOKE UPDATE, DELETE ON audit_logs FROM PUBLIC;

CREATE TABLE IF NOT EXISTS notification_outbox (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id uuid REFERENCES orders(id) ON DELETE CASCADE,
  channel text NOT NULL,
  template text NOT NULL,
  status text NOT NULL DEFAULT 'pending',
  attempts integer NOT NULL DEFAULT 0,
  last_error text,
  created_at timestamptz NOT NULL DEFAULT now()
);




-- ===========================================================================
-- Balaji Provision Store — Synthetic Seed Data
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Store settings — default single row
-- ---------------------------------------------------------------------------
INSERT INTO store_settings (
  id, store_open, timezone, weekly_hours, contact_phone,
  estimated_window_text, delivery_fee_paise, delivery_charge_threshold_paise,
  free_delivery_threshold_paise, mid_tier_fee_paise, max_order_paise,
  cancellation_policy_text, tax_mode, tax_rate_bps,
  online_payment_enabled, pos_price_override_enabled
) VALUES (
  true,
  true,
  'Asia/Kolkata',
  '{"mon":["08:00","22:00"],"tue":["08:00","22:00"],"wed":["08:00","22:00"],"thu":["08:00","22:00"],"fri":["08:00","22:00"],"sat":["08:00","22:00"],"sun":["08:00","22:00"]}'::jsonb,
  '9837803293',
  'Same-day delivery; the exact window is confirmed when the store accepts the order.',
  3000,   -- ₹30 fee when order subtotal is below delivery-charge threshold
  25000,  -- ₹250 threshold
  25000,  -- ₹250 free delivery threshold
  NULL,
  NULL,
  'Cancellation is allowed until the store accepts the order.',
  'none', 0, false, false
)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Categories
-- ---------------------------------------------------------------------------
INSERT INTO catalog_categories (name, slug, sort_order) VALUES
  ('Grains & Staples',  'grains-staples',  1),
  ('Spices & Masalas',  'spices-masalas',  2),
  ('Oils & Ghee',       'oils-ghee',       3),
  ('Snacks & Namkeen',  'snacks-namkeen',  4),
  ('Beverages',         'beverages',       5),
  ('Personal Care',     'personal-care',   6),
  ('Household',         'household',       7),
  ('Dairy & Eggs',      'dairy-eggs',      8),
  ('Fresh Produce',     'fresh-produce',   9)
ON CONFLICT (slug) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Products — PACKED (price per pack; integer quantities only)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('BASMATI-RICE-1KG',      'Basmati Rice 1 kg',              'Premium aged basmati rice',        (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 12000, 1, 'pack', '1 kg',   1, 1, 50, 10, true, true),
('WHOLE-WHEAT-ATTA-5KG',  'Whole Wheat Atta 5 kg',        'Freshly ground whole wheat flour', (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 22000, 1, 'pack', '5 kg',   1, 1, 30,  5, true, true),
('TOOR-DAL-1KG',          'Toor Dal 1 kg',                'Split pigeon peas',                (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 13000, 1, 'pack', '1 kg',   1, 1, 40, 10, true, true),
('SUNFLOWER-OIL-1L',      'Sunflower Oil 1 L',            'Refined sunflower oil',            (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 14000, 1, 'pack', '1 L',    1, 1, 25,  5, true, true),
('MUSTARD-OIL-1L',        'Cold-Pressed Mustard Oil 1 L', 'Cold-pressed mustard oil',         (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 15000, 1, 'pack', '1 L',    1, 1, 20,  5, true, true),
('TURMERIC-POWDER-200G',  'Turmeric Powder 200 g',        'Pure turmeric powder',             (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  6000, 1, 'pack', '200 g',  1, 1, 50, 10, true, true),
('RED-CHILLI-POWDER-200G','Red Chilli Powder 200 g',      'Spicy red chilli powder',          (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  7000, 1, 'pack', '200 g',  1, 1, 45, 10, true, true),
('IODIZED-SALT-1KG',      'Iodized Salt 1 kg',            'Iodized table salt',               (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  2000, 1, 'pack', '1 kg',   1, 1, 60, 15, true, true),
('ASSAM-TEA-250G',        'Assam CTC Tea 250 g',          'Strong Assam CTC tea',             (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  8000, 1, 'pack', '250 g',  1, 1, 35, 10, true, true),
('INSTANT-COFFEE-50G',    'Instant Coffee 50 g',          'Classic instant coffee',           (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  9000, 1, 'pack', '50 g',   1, 1, 30,  5, true, true),
('BIKANERI-BHUJIA-200G',  'Bikaneri Bhujia 200 g',        'Crispy gram flour snack',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5000, 1, 'pack', '200 g',  1, 1, 40, 10, true, true),
('NAVRATAN-MIXTURE-200G', 'Navratan Mixture 200 g',       'Assorted savory mixture',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5500, 1, 'pack', '200 g',  1, 1, 35, 10, true, true),
('PREMIUM-CASHEWS-1KG',   'Premium Cashews 1 kg',         'Whole cashews, kiln roasted',      (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 25000, 1, 'pack', '1 kg',   1, 1, 15,  5, true, true),
('MIXED-DRY-FRUITS-250G', 'Mixed Dry Fruits 250 g',       'Assorted dry fruits',              (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 24999, 1, 'pack', '250 g',  1, 1, 20,  5, true, true);

-- ---------------------------------------------------------------------------
-- Products — WEIGHTED (price per kg unless noted; quantities in grams)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('ONIONS-FRESH',     'Onions',           'Fresh red onions',     (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 50000,  5000, true, true),
('POTATOES-FRESH',   'Potatoes',         'Fresh potatoes',       (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 3000, 1000, 'kg',   NULL, 10, 100, 80000, 10000, true, true),
('TOMATOES-FRESH',   'Tomatoes',         'Fresh red tomatoes',   (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 5000, 1000, 'kg',   NULL, 10, 100, 30000,  3000, true, true),
('BANANAS-ROBUSTA',  'Bananas (Robusta)','Fresh robusta bananas',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 40000,  5000, true, true),
('FRESH-CORIANDER',  'Fresh Coriander',  'Fresh coriander leaves',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 1500,  100, '100 g', NULL, 10, 100,  5000,   500, true, true);

-- ---------------------------------------------------------------------------
-- Service areas
-- ---------------------------------------------------------------------------
INSERT INTO service_areas (kind, value, active) VALUES
  ('pincode', '122001', true),
  ('pincode', '122002', true)
ON CONFLICT (kind, lower(value)) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Synthetic test identities (auth.users + profiles)
-- ---------------------------------------------------------------------------
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at) VALUES
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'customer-a@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'customer-b@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000003', 'authenticated', 'authenticated', 'rider-a@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000004', 'authenticated', 'authenticated', 'rider-b@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000005', 'authenticated', 'authenticated', 'staff-1@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000006', 'authenticated', 'authenticated', 'manager-1@balaji-test.invalid',  'synthetic-placeholder', now(), now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO profiles (id, role, display_name, phone, login_id, is_active, must_change_password) VALUES
('aaaaaaaa-0000-0000-0000-000000000001', 'customer', 'Customer A',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000002', 'customer', 'Customer B',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000003', 'delivery', 'Rider A',     '9837803293', 'RIDER-A-001', true, true),
('aaaaaaaa-0000-0000-0000-000000000004', 'delivery', 'Rider B',     '9837803293', 'RIDER-B-002', true, true),
('aaaaaaaa-0000-0000-0000-000000000005', 'staff',    'Staff One',   '9837803293', 'STAFF-001',   true, false),
('aaaaaaaa-0000-0000-0000-000000000006', 'manager',  'Manager One', '9837803293', 'MANAGER-001', true, false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO bill_counters (fy, last_no) VALUES ('2026-27', 0)
ON CONFLICT (fy) DO NOTHING;-- M1: enable Row Level Security on every table (IMPLEMENTATION_PLAN.md §4.1, §5.2)
-- Deny-all default. Per-role policies and the catalogue read views are added in M2.
-- Local development and migrations run as the database owner, which bypasses RLS,
-- so seed and maintenance operations still work.

ALTER TABLE profiles               ENABLE ROW LEVEL SECURITY;
ALTER TABLE store_settings         ENABLE ROW LEVEL SECURITY;
ALTER TABLE service_areas          ENABLE ROW LEVEL SECURITY;
ALTER TABLE categories             ENABLE ROW LEVEL SECURITY;
ALTER TABLE products               ENABLE ROW LEVEL SECURITY;
ALTER TABLE addresses              ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders                 ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_items            ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_status_events    ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_assignments      ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_transitions      ENABLE ROW LEVEL SECURITY;
ALTER TABLE inventory_movements    ENABLE ROW LEVEL SECURITY;
ALTER TABLE bill_counters          ENABLE ROW LEVEL SECURITY;
ALTER TABLE pos_sales              ENABLE ROW LEVEL SECURITY;
ALTER TABLE bills                  ENABLE ROW LEVEL SECURITY;
ALTER TABLE bill_items             ENABLE ROW LEVEL SECURITY;
ALTER TABLE payments               ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_events         ENABLE ROW LEVEL SECURITY;
ALTER TABLE idempotency_keys       ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs             ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_outbox    ENABLE ROW LEVEL SECURITY;

-- No default grants to anon/authenticated (plan §4.1). Catalogue reads arrive via
-- views in M2/M3; all writes go through RPCs or tightly scoped policies.
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC;




-- ===========================================================================
-- Balaji Provision Store — Synthetic Seed Data
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Store settings — default single row
-- ---------------------------------------------------------------------------
INSERT INTO store_settings (
  id, store_open, timezone, weekly_hours, contact_phone,
  estimated_window_text, delivery_fee_paise, delivery_charge_threshold_paise,
  free_delivery_threshold_paise, mid_tier_fee_paise, max_order_paise,
  cancellation_policy_text, tax_mode, tax_rate_bps,
  online_payment_enabled, pos_price_override_enabled
) VALUES (
  true,
  true,
  'Asia/Kolkata',
  '{"mon":["08:00","22:00"],"tue":["08:00","22:00"],"wed":["08:00","22:00"],"thu":["08:00","22:00"],"fri":["08:00","22:00"],"sat":["08:00","22:00"],"sun":["08:00","22:00"]}'::jsonb,
  '9837803293',
  'Same-day delivery; the exact window is confirmed when the store accepts the order.',
  3000,   -- ₹30 fee when order subtotal is below delivery-charge threshold
  25000,  -- ₹250 threshold
  25000,  -- ₹250 free delivery threshold
  NULL,
  NULL,
  'Cancellation is allowed until the store accepts the order.',
  'none', 0, false, false
)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Categories
-- ---------------------------------------------------------------------------
INSERT INTO catalog_categories (name, slug, sort_order) VALUES
  ('Grains & Staples',  'grains-staples',  1),
  ('Spices & Masalas',  'spices-masalas',  2),
  ('Oils & Ghee',       'oils-ghee',       3),
  ('Snacks & Namkeen',  'snacks-namkeen',  4),
  ('Beverages',         'beverages',       5),
  ('Personal Care',     'personal-care',   6),
  ('Household',         'household',       7),
  ('Dairy & Eggs',      'dairy-eggs',      8),
  ('Fresh Produce',     'fresh-produce',   9)
ON CONFLICT (slug) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Products — PACKED (price per pack; integer quantities only)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('BASMATI-RICE-1KG',      'Basmati Rice 1 kg',              'Premium aged basmati rice',        (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 12000, 1, 'pack', '1 kg',   1, 1, 50, 10, true, true),
('WHOLE-WHEAT-ATTA-5KG',  'Whole Wheat Atta 5 kg',        'Freshly ground whole wheat flour', (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 22000, 1, 'pack', '5 kg',   1, 1, 30,  5, true, true),
('TOOR-DAL-1KG',          'Toor Dal 1 kg',                'Split pigeon peas',                (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 13000, 1, 'pack', '1 kg',   1, 1, 40, 10, true, true),
('SUNFLOWER-OIL-1L',      'Sunflower Oil 1 L',            'Refined sunflower oil',            (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 14000, 1, 'pack', '1 L',    1, 1, 25,  5, true, true),
('MUSTARD-OIL-1L',        'Cold-Pressed Mustard Oil 1 L', 'Cold-pressed mustard oil',         (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 15000, 1, 'pack', '1 L',    1, 1, 20,  5, true, true),
('TURMERIC-POWDER-200G',  'Turmeric Powder 200 g',        'Pure turmeric powder',             (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  6000, 1, 'pack', '200 g',  1, 1, 50, 10, true, true),
('RED-CHILLI-POWDER-200G','Red Chilli Powder 200 g',      'Spicy red chilli powder',          (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  7000, 1, 'pack', '200 g',  1, 1, 45, 10, true, true),
('IODIZED-SALT-1KG',      'Iodized Salt 1 kg',            'Iodized table salt',               (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  2000, 1, 'pack', '1 kg',   1, 1, 60, 15, true, true),
('ASSAM-TEA-250G',        'Assam CTC Tea 250 g',          'Strong Assam CTC tea',             (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  8000, 1, 'pack', '250 g',  1, 1, 35, 10, true, true),
('INSTANT-COFFEE-50G',    'Instant Coffee 50 g',          'Classic instant coffee',           (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  9000, 1, 'pack', '50 g',   1, 1, 30,  5, true, true),
('BIKANERI-BHUJIA-200G',  'Bikaneri Bhujia 200 g',        'Crispy gram flour snack',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5000, 1, 'pack', '200 g',  1, 1, 40, 10, true, true),
('NAVRATAN-MIXTURE-200G', 'Navratan Mixture 200 g',       'Assorted savory mixture',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5500, 1, 'pack', '200 g',  1, 1, 35, 10, true, true),
('PREMIUM-CASHEWS-1KG',   'Premium Cashews 1 kg',         'Whole cashews, kiln roasted',      (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 25000, 1, 'pack', '1 kg',   1, 1, 15,  5, true, true),
('MIXED-DRY-FRUITS-250G', 'Mixed Dry Fruits 250 g',       'Assorted dry fruits',              (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 24999, 1, 'pack', '250 g',  1, 1, 20,  5, true, true);

-- ---------------------------------------------------------------------------
-- Products — WEIGHTED (price per kg unless noted; quantities in grams)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('ONIONS-FRESH',     'Onions',           'Fresh red onions',     (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 50000,  5000, true, true),
('POTATOES-FRESH',   'Potatoes',         'Fresh potatoes',       (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 3000, 1000, 'kg',   NULL, 10, 100, 80000, 10000, true, true),
('TOMATOES-FRESH',   'Tomatoes',         'Fresh red tomatoes',   (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 5000, 1000, 'kg',   NULL, 10, 100, 30000,  3000, true, true),
('BANANAS-ROBUSTA',  'Bananas (Robusta)','Fresh robusta bananas',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 40000,  5000, true, true),
('FRESH-CORIANDER',  'Fresh Coriander',  'Fresh coriander leaves',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 1500,  100, '100 g', NULL, 10, 100,  5000,   500, true, true);

-- ---------------------------------------------------------------------------
-- Service areas
-- ---------------------------------------------------------------------------
INSERT INTO service_areas (kind, value, active) VALUES
  ('pincode', '122001', true),
  ('pincode', '122002', true)
ON CONFLICT (kind, lower(value)) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Synthetic test identities (auth.users + profiles)
-- ---------------------------------------------------------------------------
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at) VALUES
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'customer-a@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'customer-b@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000003', 'authenticated', 'authenticated', 'rider-a@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000004', 'authenticated', 'authenticated', 'rider-b@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000005', 'authenticated', 'authenticated', 'staff-1@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000006', 'authenticated', 'authenticated', 'manager-1@balaji-test.invalid',  'synthetic-placeholder', now(), now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO profiles (id, role, display_name, phone, login_id, is_active, must_change_password) VALUES
('aaaaaaaa-0000-0000-0000-000000000001', 'customer', 'Customer A',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000002', 'customer', 'Customer B',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000003', 'delivery', 'Rider A',     '9837803293', 'RIDER-A-001', true, true),
('aaaaaaaa-0000-0000-0000-000000000004', 'delivery', 'Rider B',     '9837803293', 'RIDER-B-002', true, true),
('aaaaaaaa-0000-0000-0000-000000000005', 'staff',    'Staff One',   '9837803293', 'STAFF-001',   true, false),
('aaaaaaaa-0000-0000-0000-000000000006', 'manager',  'Manager One', '9837803293', 'MANAGER-001', true, false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO bill_counters (fy, last_no) VALUES ('2026-27', 0)
ON CONFLICT (fy) DO NOTHING;-- M2: auth wiring, current_role(), audit redaction helper, profiles trigger
-- IMPLEMENTATION_PLAN.md §5.2, §5.4, §4.9

-- 1) current_role() — stable, SECURITY DEFINER, used in RLS policies
CREATE OR REPLACE FUNCTION public.current_role()
RETURNS public.user_role
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $sql$
  SELECT COALESCE(
    (SELECT role FROM public.profiles WHERE id = auth.uid()),
    'customer'::public.user_role
  );
$sql$;

COMMENT ON FUNCTION public.current_role() IS
  'Returns the caller role from profiles. SECURITY DEFINER so RLS policies can call it without granting direct profiles access.';

-- 2) Redaction helper for audit_logs — strips sensitive keys from JSONB before insert
CREATE OR REPLACE FUNCTION public.redact_for_audit(val jsonb)
RETURNS jsonb
LANGUAGE sql
IMMUTABLE
SECURITY DEFINER
SET search_path = ''
AS $sql$
  SELECT val - 'password' - 'encrypted_password' - 'otp' - 'token' - 'secret' - 'api_key' - 'webhook_secret' - 'private_key' - 'card_number' - 'cvv' - 'expiry';
$sql$;

COMMENT ON FUNCTION public.redact_for_audit(jsonb) IS
  'Removes known sensitive keys from a JSONB value before writing to audit_logs. Add keys as needed; never log credentials.';

-- 3) profiles trigger on auth signup — inserts a customer profile row
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
  INSERT INTO public.profiles (id, role, display_name, phone, is_active, must_change_password)
  VALUES (
    NEW.id,
    'customer'::public.user_role,
    COALESCE(NEW.raw_user_meta_data->>'display_name', NEW.email),
    NEW.raw_user_meta_data->>'phone',
    true,
    false
  );
  RETURN NEW;
EXCEPTION WHEN unique_violation THEN
  -- Profile already exists (e.g., pre-created by admin); that's fine.
  RETURN NEW;
END;
$$;

-- Attach to auth.users (runs as the auth service role, which bypasses RLS)
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- 4) Rebuild updated_at trigger on profiles (idempotent)
DROP TRIGGER IF EXISTS set_updated_at_profiles ON profiles;
CREATE TRIGGER set_updated_at_profiles
  BEFORE UPDATE ON profiles
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Grant execute on helper functions to authenticated role (RLS policies run as the calling user)
GRANT EXECUTE ON FUNCTION public.current_role() TO authenticated;
GRANT EXECUTE ON FUNCTION public.redact_for_audit(jsonb) TO authenticated;

-- Note: handle_new_user runs as auth service role (not the new user), so no grant needed.



-- ===========================================================================
-- Balaji Provision Store — Synthetic Seed Data
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Store settings — default single row
-- ---------------------------------------------------------------------------
INSERT INTO store_settings (
  id, store_open, timezone, weekly_hours, contact_phone,
  estimated_window_text, delivery_fee_paise, delivery_charge_threshold_paise,
  free_delivery_threshold_paise, mid_tier_fee_paise, max_order_paise,
  cancellation_policy_text, tax_mode, tax_rate_bps,
  online_payment_enabled, pos_price_override_enabled
) VALUES (
  true,
  true,
  'Asia/Kolkata',
  '{"mon":["08:00","22:00"],"tue":["08:00","22:00"],"wed":["08:00","22:00"],"thu":["08:00","22:00"],"fri":["08:00","22:00"],"sat":["08:00","22:00"],"sun":["08:00","22:00"]}'::jsonb,
  '9837803293',
  'Same-day delivery; the exact window is confirmed when the store accepts the order.',
  3000,   -- ₹30 fee when order subtotal is below delivery-charge threshold
  25000,  -- ₹250 threshold
  25000,  -- ₹250 free delivery threshold
  NULL,
  NULL,
  'Cancellation is allowed until the store accepts the order.',
  'none', 0, false, false
)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Categories
-- ---------------------------------------------------------------------------
INSERT INTO catalog_categories (name, slug, sort_order) VALUES
  ('Grains & Staples',  'grains-staples',  1),
  ('Spices & Masalas',  'spices-masalas',  2),
  ('Oils & Ghee',       'oils-ghee',       3),
  ('Snacks & Namkeen',  'snacks-namkeen',  4),
  ('Beverages',         'beverages',       5),
  ('Personal Care',     'personal-care',   6),
  ('Household',         'household',       7),
  ('Dairy & Eggs',      'dairy-eggs',      8),
  ('Fresh Produce',     'fresh-produce',   9)
ON CONFLICT (slug) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Products — PACKED (price per pack; integer quantities only)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('BASMATI-RICE-1KG',      'Basmati Rice 1 kg',              'Premium aged basmati rice',        (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 12000, 1, 'pack', '1 kg',   1, 1, 50, 10, true, true),
('WHOLE-WHEAT-ATTA-5KG',  'Whole Wheat Atta 5 kg',        'Freshly ground whole wheat flour', (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 22000, 1, 'pack', '5 kg',   1, 1, 30,  5, true, true),
('TOOR-DAL-1KG',          'Toor Dal 1 kg',                'Split pigeon peas',                (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 13000, 1, 'pack', '1 kg',   1, 1, 40, 10, true, true),
('SUNFLOWER-OIL-1L',      'Sunflower Oil 1 L',            'Refined sunflower oil',            (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 14000, 1, 'pack', '1 L',    1, 1, 25,  5, true, true),
('MUSTARD-OIL-1L',        'Cold-Pressed Mustard Oil 1 L', 'Cold-pressed mustard oil',         (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 15000, 1, 'pack', '1 L',    1, 1, 20,  5, true, true),
('TURMERIC-POWDER-200G',  'Turmeric Powder 200 g',        'Pure turmeric powder',             (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  6000, 1, 'pack', '200 g',  1, 1, 50, 10, true, true),
('RED-CHILLI-POWDER-200G','Red Chilli Powder 200 g',      'Spicy red chilli powder',          (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  7000, 1, 'pack', '200 g',  1, 1, 45, 10, true, true),
('IODIZED-SALT-1KG',      'Iodized Salt 1 kg',            'Iodized table salt',               (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  2000, 1, 'pack', '1 kg',   1, 1, 60, 15, true, true),
('ASSAM-TEA-250G',        'Assam CTC Tea 250 g',          'Strong Assam CTC tea',             (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  8000, 1, 'pack', '250 g',  1, 1, 35, 10, true, true),
('INSTANT-COFFEE-50G',    'Instant Coffee 50 g',          'Classic instant coffee',           (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  9000, 1, 'pack', '50 g',   1, 1, 30,  5, true, true),
('BIKANERI-BHUJIA-200G',  'Bikaneri Bhujia 200 g',        'Crispy gram flour snack',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5000, 1, 'pack', '200 g',  1, 1, 40, 10, true, true),
('NAVRATAN-MIXTURE-200G', 'Navratan Mixture 200 g',       'Assorted savory mixture',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5500, 1, 'pack', '200 g',  1, 1, 35, 10, true, true),
('PREMIUM-CASHEWS-1KG',   'Premium Cashews 1 kg',         'Whole cashews, kiln roasted',      (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 25000, 1, 'pack', '1 kg',   1, 1, 15,  5, true, true),
('MIXED-DRY-FRUITS-250G', 'Mixed Dry Fruits 250 g',       'Assorted dry fruits',              (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 24999, 1, 'pack', '250 g',  1, 1, 20,  5, true, true);

-- ---------------------------------------------------------------------------
-- Products — WEIGHTED (price per kg unless noted; quantities in grams)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('ONIONS-FRESH',     'Onions',           'Fresh red onions',     (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 50000,  5000, true, true),
('POTATOES-FRESH',   'Potatoes',         'Fresh potatoes',       (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 3000, 1000, 'kg',   NULL, 10, 100, 80000, 10000, true, true),
('TOMATOES-FRESH',   'Tomatoes',         'Fresh red tomatoes',   (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 5000, 1000, 'kg',   NULL, 10, 100, 30000,  3000, true, true),
('BANANAS-ROBUSTA',  'Bananas (Robusta)','Fresh robusta bananas',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 40000,  5000, true, true),
('FRESH-CORIANDER',  'Fresh Coriander',  'Fresh coriander leaves',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 1500,  100, '100 g', NULL, 10, 100,  5000,   500, true, true);

-- ---------------------------------------------------------------------------
-- Service areas
-- ---------------------------------------------------------------------------
INSERT INTO service_areas (kind, value, active) VALUES
  ('pincode', '122001', true),
  ('pincode', '122002', true)
ON CONFLICT (kind, lower(value)) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Synthetic test identities (auth.users + profiles)
-- ---------------------------------------------------------------------------
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at) VALUES
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'customer-a@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'customer-b@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000003', 'authenticated', 'authenticated', 'rider-a@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000004', 'authenticated', 'authenticated', 'rider-b@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000005', 'authenticated', 'authenticated', 'staff-1@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000006', 'authenticated', 'authenticated', 'manager-1@balaji-test.invalid',  'synthetic-placeholder', now(), now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO profiles (id, role, display_name, phone, login_id, is_active, must_change_password) VALUES
('aaaaaaaa-0000-0000-0000-000000000001', 'customer', 'Customer A',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000002', 'customer', 'Customer B',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000003', 'delivery', 'Rider A',     '9837803293', 'RIDER-A-001', true, true),
('aaaaaaaa-0000-0000-0000-000000000004', 'delivery', 'Rider B',     '9837803293', 'RIDER-B-002', true, true),
('aaaaaaaa-0000-0000-0000-000000000005', 'staff',    'Staff One',   '9837803293', 'STAFF-001',   true, false),
('aaaaaaaa-0000-0000-0000-000000000006', 'manager',  'Manager One', '9837803293', 'MANAGER-001', true, false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO bill_counters (fy, last_no) VALUES ('2026-27', 0)
ON CONFLICT (fy) DO NOTHING;-- M2: RLS policies for all tables (IMPLEMENTATION_PLAN.md §5.1, §5.2)

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




-- ===========================================================================
-- Balaji Provision Store — Synthetic Seed Data
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Store settings — default single row
-- ---------------------------------------------------------------------------
INSERT INTO store_settings (
  id, store_open, timezone, weekly_hours, contact_phone,
  estimated_window_text, delivery_fee_paise, delivery_charge_threshold_paise,
  free_delivery_threshold_paise, mid_tier_fee_paise, max_order_paise,
  cancellation_policy_text, tax_mode, tax_rate_bps,
  online_payment_enabled, pos_price_override_enabled
) VALUES (
  true,
  true,
  'Asia/Kolkata',
  '{"mon":["08:00","22:00"],"tue":["08:00","22:00"],"wed":["08:00","22:00"],"thu":["08:00","22:00"],"fri":["08:00","22:00"],"sat":["08:00","22:00"],"sun":["08:00","22:00"]}'::jsonb,
  '9837803293',
  'Same-day delivery; the exact window is confirmed when the store accepts the order.',
  3000,   -- ₹30 fee when order subtotal is below delivery-charge threshold
  25000,  -- ₹250 threshold
  25000,  -- ₹250 free delivery threshold
  NULL,
  NULL,
  'Cancellation is allowed until the store accepts the order.',
  'none', 0, false, false
)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Categories
-- ---------------------------------------------------------------------------
INSERT INTO catalog_categories (name, slug, sort_order) VALUES
  ('Grains & Staples',  'grains-staples',  1),
  ('Spices & Masalas',  'spices-masalas',  2),
  ('Oils & Ghee',       'oils-ghee',       3),
  ('Snacks & Namkeen',  'snacks-namkeen',  4),
  ('Beverages',         'beverages',       5),
  ('Personal Care',     'personal-care',   6),
  ('Household',         'household',       7),
  ('Dairy & Eggs',      'dairy-eggs',      8),
  ('Fresh Produce',     'fresh-produce',   9)
ON CONFLICT (slug) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Products — PACKED (price per pack; integer quantities only)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('BASMATI-RICE-1KG',      'Basmati Rice 1 kg',              'Premium aged basmati rice',        (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 12000, 1, 'pack', '1 kg',   1, 1, 50, 10, true, true),
('WHOLE-WHEAT-ATTA-5KG',  'Whole Wheat Atta 5 kg',        'Freshly ground whole wheat flour', (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 22000, 1, 'pack', '5 kg',   1, 1, 30,  5, true, true),
('TOOR-DAL-1KG',          'Toor Dal 1 kg',                'Split pigeon peas',                (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 13000, 1, 'pack', '1 kg',   1, 1, 40, 10, true, true),
('SUNFLOWER-OIL-1L',      'Sunflower Oil 1 L',            'Refined sunflower oil',            (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 14000, 1, 'pack', '1 L',    1, 1, 25,  5, true, true),
('MUSTARD-OIL-1L',        'Cold-Pressed Mustard Oil 1 L', 'Cold-pressed mustard oil',         (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 15000, 1, 'pack', '1 L',    1, 1, 20,  5, true, true),
('TURMERIC-POWDER-200G',  'Turmeric Powder 200 g',        'Pure turmeric powder',             (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  6000, 1, 'pack', '200 g',  1, 1, 50, 10, true, true),
('RED-CHILLI-POWDER-200G','Red Chilli Powder 200 g',      'Spicy red chilli powder',          (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  7000, 1, 'pack', '200 g',  1, 1, 45, 10, true, true),
('IODIZED-SALT-1KG',      'Iodized Salt 1 kg',            'Iodized table salt',               (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  2000, 1, 'pack', '1 kg',   1, 1, 60, 15, true, true),
('ASSAM-TEA-250G',        'Assam CTC Tea 250 g',          'Strong Assam CTC tea',             (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  8000, 1, 'pack', '250 g',  1, 1, 35, 10, true, true),
('INSTANT-COFFEE-50G',    'Instant Coffee 50 g',          'Classic instant coffee',           (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  9000, 1, 'pack', '50 g',   1, 1, 30,  5, true, true),
('BIKANERI-BHUJIA-200G',  'Bikaneri Bhujia 200 g',        'Crispy gram flour snack',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5000, 1, 'pack', '200 g',  1, 1, 40, 10, true, true),
('NAVRATAN-MIXTURE-200G', 'Navratan Mixture 200 g',       'Assorted savory mixture',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5500, 1, 'pack', '200 g',  1, 1, 35, 10, true, true),
('PREMIUM-CASHEWS-1KG',   'Premium Cashews 1 kg',         'Whole cashews, kiln roasted',      (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 25000, 1, 'pack', '1 kg',   1, 1, 15,  5, true, true),
('MIXED-DRY-FRUITS-250G', 'Mixed Dry Fruits 250 g',       'Assorted dry fruits',              (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 24999, 1, 'pack', '250 g',  1, 1, 20,  5, true, true);

-- ---------------------------------------------------------------------------
-- Products — WEIGHTED (price per kg unless noted; quantities in grams)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('ONIONS-FRESH',     'Onions',           'Fresh red onions',     (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 50000,  5000, true, true),
('POTATOES-FRESH',   'Potatoes',         'Fresh potatoes',       (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 3000, 1000, 'kg',   NULL, 10, 100, 80000, 10000, true, true),
('TOMATOES-FRESH',   'Tomatoes',         'Fresh red tomatoes',   (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 5000, 1000, 'kg',   NULL, 10, 100, 30000,  3000, true, true),
('BANANAS-ROBUSTA',  'Bananas (Robusta)','Fresh robusta bananas',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 40000,  5000, true, true),
('FRESH-CORIANDER',  'Fresh Coriander',  'Fresh coriander leaves',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 1500,  100, '100 g', NULL, 10, 100,  5000,   500, true, true);

-- ---------------------------------------------------------------------------
-- Service areas
-- ---------------------------------------------------------------------------
INSERT INTO service_areas (kind, value, active) VALUES
  ('pincode', '122001', true),
  ('pincode', '122002', true)
ON CONFLICT (kind, lower(value)) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Synthetic test identities (auth.users + profiles)
-- ---------------------------------------------------------------------------
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at) VALUES
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'customer-a@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'customer-b@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000003', 'authenticated', 'authenticated', 'rider-a@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000004', 'authenticated', 'authenticated', 'rider-b@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000005', 'authenticated', 'authenticated', 'staff-1@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000006', 'authenticated', 'authenticated', 'manager-1@balaji-test.invalid',  'synthetic-placeholder', now(), now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO profiles (id, role, display_name, phone, login_id, is_active, must_change_password) VALUES
('aaaaaaaa-0000-0000-0000-000000000001', 'customer', 'Customer A',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000002', 'customer', 'Customer B',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000003', 'delivery', 'Rider A',     '9837803293', 'RIDER-A-001', true, true),
('aaaaaaaa-0000-0000-0000-000000000004', 'delivery', 'Rider B',     '9837803293', 'RIDER-B-002', true, true),
('aaaaaaaa-0000-0000-0000-000000000005', 'staff',    'Staff One',   '9837803293', 'STAFF-001',   true, false),
('aaaaaaaa-0000-0000-0000-000000000006', 'manager',  'Manager One', '9837803293', 'MANAGER-001', true, false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO bill_counters (fy, last_no) VALUES ('2026-27', 0)
ON CONFLICT (fy) DO NOTHING;-- M2: audit trigger infrastructure + table-specific triggers (IMPLEMENTATION_PLAN.md §4.9)
-- Triggers are SECURITY DEFINER, set search_path='', capture actor from auth.uid().

-- 1) Generic audit function
CREATE OR REPLACE FUNCTION public.audit_trigger_fn()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_actor uuid := auth.uid();
  v_before jsonb;
  v_after jsonb;
  v_action text;
BEGIN
  -- Determine action
  IF TG_OP = 'INSERT' THEN
    v_action := 'INSERT';
    v_before := NULL;
    v_after := to_jsonb(NEW);
  ELSIF TG_OP = 'UPDATE' THEN
    v_action := 'UPDATE';
    v_before := to_jsonb(OLD);
    v_after := to_jsonb(NEW);
  ELSIF TG_OP = 'DELETE' THEN
    v_action := 'DELETE';
    v_before := to_jsonb(OLD);
    v_after := NULL;
  END IF;

  -- Redact sensitive fields
  v_before := public.redact_for_audit(v_before);
  v_after := public.redact_for_audit(v_after);

  INSERT INTO public.audit_logs (actor_id, action, entity_type, entity_id, before, after, request_id)
  VALUES (v_actor, v_action, TG_TABLE_NAME, COALESCE(NEW.id, OLD.id), v_before, v_after, current_setting('request.id', true)::text);

  RETURN NULL; -- result ignored for AFTER triggers
END;
$$;

-- 2) Tables that get audit triggers (per §4.9):
-- store_settings, service_areas, products (price/stock/active/available),
-- profiles (role/is_active), order_assignments, bills (void/correction)
-- Note: orders/order_items/pos_sales/bill_items use RPC-level audit (§4.9).

-- store_settings
DROP TRIGGER IF EXISTS audit_store_settings ON store_settings;
CREATE TRIGGER audit_store_settings
  AFTER INSERT OR UPDATE OR DELETE ON store_settings
  FOR EACH ROW EXECUTE FUNCTION audit_trigger_fn();

-- service_areas
DROP TRIGGER IF EXISTS audit_service_areas ON service_areas;
CREATE TRIGGER audit_service_areas
  AFTER INSERT OR UPDATE OR DELETE ON service_areas
  FOR EACH ROW EXECUTE FUNCTION audit_trigger_fn();

-- products (audit price, stock, active, available changes)
DROP TRIGGER IF EXISTS audit_products ON products;
CREATE TRIGGER audit_products
  AFTER UPDATE ON products
  FOR EACH ROW EXECUTE FUNCTION audit_trigger_fn();

-- profiles (audit role, is_active)
DROP TRIGGER IF EXISTS audit_profiles ON profiles;
CREATE TRIGGER audit_profiles
  AFTER UPDATE OF role, is_active ON profiles
  FOR EACH ROW EXECUTE FUNCTION audit_trigger_fn();

-- order_assignments
DROP TRIGGER IF EXISTS audit_order_assignments ON order_assignments;
CREATE TRIGGER audit_order_assignments
  AFTER INSERT OR UPDATE OR DELETE ON order_assignments
  FOR EACH ROW EXECUTE FUNCTION audit_trigger_fn();

-- bills (audit void/correction: status, void_reason, voided_by, voided_at)
DROP TRIGGER IF EXISTS audit_bills ON bills;
CREATE TRIGGER audit_bills
  AFTER UPDATE OF status, void_reason, voided_by, voided_at ON bills
  FOR EACH ROW EXECUTE FUNCTION audit_trigger_fn();

-- inventory_movements, order_status_events: append-only, no UPDATE/DELETE grants;
-- audit is implicit (every row is an audit trail). No triggers needed.

-- Grant execute on audit function to authenticated (triggers run as table owner, not caller;
-- but SECURITY DEFINER ensures it works regardless).
GRANT EXECUTE ON FUNCTION public.audit_trigger_fn() TO authenticated;

-- Note: RPC-level audit for status transitions, inventory adjustments, price overrides,
-- account creation/reset, item removal/refund-state changes is added in M4/M7/M9
-- when those RPCs are implemented.



-- ===========================================================================
-- Balaji Provision Store — Synthetic Seed Data
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Store settings — default single row
-- ---------------------------------------------------------------------------
INSERT INTO store_settings (
  id, store_open, timezone, weekly_hours, contact_phone,
  estimated_window_text, delivery_fee_paise, delivery_charge_threshold_paise,
  free_delivery_threshold_paise, mid_tier_fee_paise, max_order_paise,
  cancellation_policy_text, tax_mode, tax_rate_bps,
  online_payment_enabled, pos_price_override_enabled
) VALUES (
  true,
  true,
  'Asia/Kolkata',
  '{"mon":["08:00","22:00"],"tue":["08:00","22:00"],"wed":["08:00","22:00"],"thu":["08:00","22:00"],"fri":["08:00","22:00"],"sat":["08:00","22:00"],"sun":["08:00","22:00"]}'::jsonb,
  '9837803293',
  'Same-day delivery; the exact window is confirmed when the store accepts the order.',
  3000,   -- ₹30 fee when order subtotal is below delivery-charge threshold
  25000,  -- ₹250 threshold
  25000,  -- ₹250 free delivery threshold
  NULL,
  NULL,
  'Cancellation is allowed until the store accepts the order.',
  'none', 0, false, false
)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Categories
-- ---------------------------------------------------------------------------
INSERT INTO catalog_categories (name, slug, sort_order) VALUES
  ('Grains & Staples',  'grains-staples',  1),
  ('Spices & Masalas',  'spices-masalas',  2),
  ('Oils & Ghee',       'oils-ghee',       3),
  ('Snacks & Namkeen',  'snacks-namkeen',  4),
  ('Beverages',         'beverages',       5),
  ('Personal Care',     'personal-care',   6),
  ('Household',         'household',       7),
  ('Dairy & Eggs',      'dairy-eggs',      8),
  ('Fresh Produce',     'fresh-produce',   9)
ON CONFLICT (slug) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Products — PACKED (price per pack; integer quantities only)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('BASMATI-RICE-1KG',      'Basmati Rice 1 kg',              'Premium aged basmati rice',        (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 12000, 1, 'pack', '1 kg',   1, 1, 50, 10, true, true),
('WHOLE-WHEAT-ATTA-5KG',  'Whole Wheat Atta 5 kg',        'Freshly ground whole wheat flour', (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 22000, 1, 'pack', '5 kg',   1, 1, 30,  5, true, true),
('TOOR-DAL-1KG',          'Toor Dal 1 kg',                'Split pigeon peas',                (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 13000, 1, 'pack', '1 kg',   1, 1, 40, 10, true, true),
('SUNFLOWER-OIL-1L',      'Sunflower Oil 1 L',            'Refined sunflower oil',            (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 14000, 1, 'pack', '1 L',    1, 1, 25,  5, true, true),
('MUSTARD-OIL-1L',        'Cold-Pressed Mustard Oil 1 L', 'Cold-pressed mustard oil',         (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 15000, 1, 'pack', '1 L',    1, 1, 20,  5, true, true),
('TURMERIC-POWDER-200G',  'Turmeric Powder 200 g',        'Pure turmeric powder',             (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  6000, 1, 'pack', '200 g',  1, 1, 50, 10, true, true),
('RED-CHILLI-POWDER-200G','Red Chilli Powder 200 g',      'Spicy red chilli powder',          (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  7000, 1, 'pack', '200 g',  1, 1, 45, 10, true, true),
('IODIZED-SALT-1KG',      'Iodized Salt 1 kg',            'Iodized table salt',               (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  2000, 1, 'pack', '1 kg',   1, 1, 60, 15, true, true),
('ASSAM-TEA-250G',        'Assam CTC Tea 250 g',          'Strong Assam CTC tea',             (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  8000, 1, 'pack', '250 g',  1, 1, 35, 10, true, true),
('INSTANT-COFFEE-50G',    'Instant Coffee 50 g',          'Classic instant coffee',           (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  9000, 1, 'pack', '50 g',   1, 1, 30,  5, true, true),
('BIKANERI-BHUJIA-200G',  'Bikaneri Bhujia 200 g',        'Crispy gram flour snack',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5000, 1, 'pack', '200 g',  1, 1, 40, 10, true, true),
('NAVRATAN-MIXTURE-200G', 'Navratan Mixture 200 g',       'Assorted savory mixture',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5500, 1, 'pack', '200 g',  1, 1, 35, 10, true, true),
('PREMIUM-CASHEWS-1KG',   'Premium Cashews 1 kg',         'Whole cashews, kiln roasted',      (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 25000, 1, 'pack', '1 kg',   1, 1, 15,  5, true, true),
('MIXED-DRY-FRUITS-250G', 'Mixed Dry Fruits 250 g',       'Assorted dry fruits',              (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 24999, 1, 'pack', '250 g',  1, 1, 20,  5, true, true);

-- ---------------------------------------------------------------------------
-- Products — WEIGHTED (price per kg unless noted; quantities in grams)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('ONIONS-FRESH',     'Onions',           'Fresh red onions',     (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 50000,  5000, true, true),
('POTATOES-FRESH',   'Potatoes',         'Fresh potatoes',       (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 3000, 1000, 'kg',   NULL, 10, 100, 80000, 10000, true, true),
('TOMATOES-FRESH',   'Tomatoes',         'Fresh red tomatoes',   (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 5000, 1000, 'kg',   NULL, 10, 100, 30000,  3000, true, true),
('BANANAS-ROBUSTA',  'Bananas (Robusta)','Fresh robusta bananas',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 40000,  5000, true, true),
('FRESH-CORIANDER',  'Fresh Coriander',  'Fresh coriander leaves',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 1500,  100, '100 g', NULL, 10, 100,  5000,   500, true, true);

-- ---------------------------------------------------------------------------
-- Service areas
-- ---------------------------------------------------------------------------
INSERT INTO service_areas (kind, value, active) VALUES
  ('pincode', '122001', true),
  ('pincode', '122002', true)
ON CONFLICT (kind, lower(value)) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Synthetic test identities (auth.users + profiles)
-- ---------------------------------------------------------------------------
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at) VALUES
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'customer-a@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'customer-b@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000003', 'authenticated', 'authenticated', 'rider-a@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000004', 'authenticated', 'authenticated', 'rider-b@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000005', 'authenticated', 'authenticated', 'staff-1@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000006', 'authenticated', 'authenticated', 'manager-1@balaji-test.invalid',  'synthetic-placeholder', now(), now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO profiles (id, role, display_name, phone, login_id, is_active, must_change_password) VALUES
('aaaaaaaa-0000-0000-0000-000000000001', 'customer', 'Customer A',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000002', 'customer', 'Customer B',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000003', 'delivery', 'Rider A',     '9837803293', 'RIDER-A-001', true, true),
('aaaaaaaa-0000-0000-0000-000000000004', 'delivery', 'Rider B',     '9837803293', 'RIDER-B-002', true, true),
('aaaaaaaa-0000-0000-0000-000000000005', 'staff',    'Staff One',   '9837803293', 'STAFF-001',   true, false),
('aaaaaaaa-0000-0000-0000-000000000006', 'manager',  'Manager One', '9837803293', 'MANAGER-001', true, false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO bill_counters (fy, last_no) VALUES ('2026-27', 0)
ON CONFLICT (fy) DO NOTHING;-- M2: rider limited view + status update RPC (IMPLEMENTATION_PLAN.md §5.2, §5.4, §6)
-- Riders see ONLY assigned orders, with only necessary fields.

-- 1) Security-invoker view: filters to orders assigned to the current delivery user
-- Only exposes: order number, recipient name, phone, address snapshot, item names/quantities,
-- payment method, COD amount due, status, customer_note.
CREATE OR REPLACE VIEW public.rider_orders
WITH (security_invoker = true) AS
SELECT
  o.id,
  o.public_order_number,
  o.address_snapshot->>'recipient_name' AS recipient_name,
  o.address_snapshot->>'phone' AS phone,
  o.address_snapshot->>'address_lines' AS address_lines,
  o.address_snapshot->>'landmark' AS landmark,
  o.address_snapshot->>'area' AS area,
  o.address_snapshot->>'city' AS city,
  o.address_snapshot->>'state' AS state,
  o.address_snapshot->>'postal_code' AS postal_code,
  o.payment_method,
  o.cod_collected_paise,
  o.grand_total_paise,
  o.status,
  o.customer_note,
  o.created_at,
  o.accepted_at,
  (
    SELECT jsonb_agg(jsonb_build_object(
      'name', oi.name_snapshot,
      'qty_base', oi.qty_base,
      'unit_label', oi.unit_label_snapshot,
      'quantity_type', oi.quantity_type_snapshot
    ))
    FROM public.order_items oi
    WHERE oi.order_id = o.id AND oi.removed_at IS NULL
  ) AS items
FROM public.orders o
JOIN public.order_assignments oa ON oa.order_id = o.id
WHERE oa.delivery_user_id = auth.uid()
  AND oa.unassigned_at IS NULL
  AND o.status IN ('ready_for_dispatch', 'out_for_delivery', 'delivered', 'delivery_failed');

GRANT SELECT ON public.rider_orders TO authenticated;

-- 2) Rider status update RPC — enforces state machine and assignment ownership
-- Called by rider; only allows the two rider transitions on their assigned orders.
CREATE OR REPLACE FUNCTION public.rider_update_status(
  p_order_id uuid,
  p_to_status order_status,
  p_note text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_order public.orders%ROWTYPE;
  v_assignment public.order_assignments%ROWTYPE;
  v_from_status public.order_status;
  v_allowed boolean;
BEGIN
  -- Must be a delivery user
  IF public.current_role() <> 'delivery'::public.user_role THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Only delivery users may call this');
  END IF;

  -- Order must exist and be assigned to this rider (active assignment)
  SELECT * INTO v_order FROM public.orders WHERE id = p_order_id;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Order not found');
  END IF;

  SELECT * INTO v_assignment
  FROM public.order_assignments
  WHERE order_id = p_order_id
    AND delivery_user_id = auth.uid()
    AND unassigned_at IS NULL;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Order not assigned to you');
  END IF;

  v_from_status := v_order.status;

  -- Check transition is allowed for delivery role (from order_transitions)
  SELECT EXISTS (
    SELECT 1 FROM public.order_transitions
    WHERE from_status = v_from_status
      AND to_status = p_to_status
      AND allowed_roles @> ARRAY['delivery']::public.user_role[]
  ) INTO v_allowed;

  IF NOT v_allowed THEN
    RETURN jsonb_build_object('error', 'INVALID_TRANSITION',
      'message', 'Invalid status transition for rider: ' || v_from_status || ' -> ' || p_to_status);
  END IF;

  -- Perform transition (status + event + timestamp)
  UPDATE public.orders
  SET status = p_to_status,
      delivered_at = CASE WHEN p_to_status = 'delivered' THEN now() ELSE delivered_at END
  WHERE id = p_order_id;

  INSERT INTO public.order_status_events (order_id, from_status, to_status, actor_id, reason)
  VALUES (p_order_id, v_from_status, p_to_status, auth.uid(), p_note);

  RETURN jsonb_build_object(
    'order_id', p_order_id,
    'from_status', v_from_status,
    'to_status', p_to_status,
    'ok', true
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.rider_update_status(uuid, order_status, text) TO authenticated;

-- 3) Staff/manager transition RPC — full state machine enforcement (stub for M2, fleshed out in M4)
-- This placeholder exists so RLS policies referencing it can be tested; real logic in M4.
CREATE OR REPLACE FUNCTION public.transition_order(
  p_order_id uuid,
  p_to_status public.order_status,
  p_reason text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
  IF public.current_role() = ANY (ARRAY['staff','manager']::public.user_role[]) = false THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Staff or manager required');
  END IF;
  -- Full implementation in M4. For now, deny to avoid accidental use.
  RETURN jsonb_build_object('error', 'NOT_IMPLEMENTED', 'message', 'transition_order implemented in M4');
END;
$$;

GRANT EXECUTE ON FUNCTION public.transition_order(uuid, public.order_status, text) TO authenticated;

-- 4) Staff/manager assign order RPC (stub; real logic in M6)
CREATE OR REPLACE FUNCTION public.assign_order(
  p_order_id uuid,
  p_delivery_user_id uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
  IF public.current_role() = ANY (ARRAY['staff','manager']::public.user_role[]) = false THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Staff or manager required');
  END IF;
  RETURN jsonb_build_object('error', 'NOT_IMPLEMENTED', 'message', 'assign_order implemented in M6');
END;
$$;

GRANT EXECUTE ON FUNCTION public.assign_order(uuid, uuid) TO authenticated;



-- ===========================================================================
-- Balaji Provision Store — Synthetic Seed Data
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Store settings — default single row
-- ---------------------------------------------------------------------------
INSERT INTO store_settings (
  id, store_open, timezone, weekly_hours, contact_phone,
  estimated_window_text, delivery_fee_paise, delivery_charge_threshold_paise,
  free_delivery_threshold_paise, mid_tier_fee_paise, max_order_paise,
  cancellation_policy_text, tax_mode, tax_rate_bps,
  online_payment_enabled, pos_price_override_enabled
) VALUES (
  true,
  true,
  'Asia/Kolkata',
  '{"mon":["08:00","22:00"],"tue":["08:00","22:00"],"wed":["08:00","22:00"],"thu":["08:00","22:00"],"fri":["08:00","22:00"],"sat":["08:00","22:00"],"sun":["08:00","22:00"]}'::jsonb,
  '9837803293',
  'Same-day delivery; the exact window is confirmed when the store accepts the order.',
  3000,   -- ₹30 fee when order subtotal is below delivery-charge threshold
  25000,  -- ₹250 threshold
  25000,  -- ₹250 free delivery threshold
  NULL,
  NULL,
  'Cancellation is allowed until the store accepts the order.',
  'none', 0, false, false
)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Categories
-- ---------------------------------------------------------------------------
INSERT INTO catalog_categories (name, slug, sort_order) VALUES
  ('Grains & Staples',  'grains-staples',  1),
  ('Spices & Masalas',  'spices-masalas',  2),
  ('Oils & Ghee',       'oils-ghee',       3),
  ('Snacks & Namkeen',  'snacks-namkeen',  4),
  ('Beverages',         'beverages',       5),
  ('Personal Care',     'personal-care',   6),
  ('Household',         'household',       7),
  ('Dairy & Eggs',      'dairy-eggs',      8),
  ('Fresh Produce',     'fresh-produce',   9)
ON CONFLICT (slug) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Products — PACKED (price per pack; integer quantities only)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('BASMATI-RICE-1KG',      'Basmati Rice 1 kg',              'Premium aged basmati rice',        (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 12000, 1, 'pack', '1 kg',   1, 1, 50, 10, true, true),
('WHOLE-WHEAT-ATTA-5KG',  'Whole Wheat Atta 5 kg',        'Freshly ground whole wheat flour', (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 22000, 1, 'pack', '5 kg',   1, 1, 30,  5, true, true),
('TOOR-DAL-1KG',          'Toor Dal 1 kg',                'Split pigeon peas',                (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 13000, 1, 'pack', '1 kg',   1, 1, 40, 10, true, true),
('SUNFLOWER-OIL-1L',      'Sunflower Oil 1 L',            'Refined sunflower oil',            (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 14000, 1, 'pack', '1 L',    1, 1, 25,  5, true, true),
('MUSTARD-OIL-1L',        'Cold-Pressed Mustard Oil 1 L', 'Cold-pressed mustard oil',         (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 15000, 1, 'pack', '1 L',    1, 1, 20,  5, true, true),
('TURMERIC-POWDER-200G',  'Turmeric Powder 200 g',        'Pure turmeric powder',             (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  6000, 1, 'pack', '200 g',  1, 1, 50, 10, true, true),
('RED-CHILLI-POWDER-200G','Red Chilli Powder 200 g',      'Spicy red chilli powder',          (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  7000, 1, 'pack', '200 g',  1, 1, 45, 10, true, true),
('IODIZED-SALT-1KG',      'Iodized Salt 1 kg',            'Iodized table salt',               (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  2000, 1, 'pack', '1 kg',   1, 1, 60, 15, true, true),
('ASSAM-TEA-250G',        'Assam CTC Tea 250 g',          'Strong Assam CTC tea',             (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  8000, 1, 'pack', '250 g',  1, 1, 35, 10, true, true),
('INSTANT-COFFEE-50G',    'Instant Coffee 50 g',          'Classic instant coffee',           (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  9000, 1, 'pack', '50 g',   1, 1, 30,  5, true, true),
('BIKANERI-BHUJIA-200G',  'Bikaneri Bhujia 200 g',        'Crispy gram flour snack',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5000, 1, 'pack', '200 g',  1, 1, 40, 10, true, true),
('NAVRATAN-MIXTURE-200G', 'Navratan Mixture 200 g',       'Assorted savory mixture',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5500, 1, 'pack', '200 g',  1, 1, 35, 10, true, true),
('PREMIUM-CASHEWS-1KG',   'Premium Cashews 1 kg',         'Whole cashews, kiln roasted',      (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 25000, 1, 'pack', '1 kg',   1, 1, 15,  5, true, true),
('MIXED-DRY-FRUITS-250G', 'Mixed Dry Fruits 250 g',       'Assorted dry fruits',              (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 24999, 1, 'pack', '250 g',  1, 1, 20,  5, true, true);

-- ---------------------------------------------------------------------------
-- Products — WEIGHTED (price per kg unless noted; quantities in grams)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('ONIONS-FRESH',     'Onions',           'Fresh red onions',     (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 50000,  5000, true, true),
('POTATOES-FRESH',   'Potatoes',         'Fresh potatoes',       (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 3000, 1000, 'kg',   NULL, 10, 100, 80000, 10000, true, true),
('TOMATOES-FRESH',   'Tomatoes',         'Fresh red tomatoes',   (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 5000, 1000, 'kg',   NULL, 10, 100, 30000,  3000, true, true),
('BANANAS-ROBUSTA',  'Bananas (Robusta)','Fresh robusta bananas',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 40000,  5000, true, true),
('FRESH-CORIANDER',  'Fresh Coriander',  'Fresh coriander leaves',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 1500,  100, '100 g', NULL, 10, 100,  5000,   500, true, true);

-- ---------------------------------------------------------------------------
-- Service areas
-- ---------------------------------------------------------------------------
INSERT INTO service_areas (kind, value, active) VALUES
  ('pincode', '122001', true),
  ('pincode', '122002', true)
ON CONFLICT (kind, lower(value)) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Synthetic test identities (auth.users + profiles)
-- ---------------------------------------------------------------------------
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at) VALUES
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'customer-a@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'customer-b@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000003', 'authenticated', 'authenticated', 'rider-a@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000004', 'authenticated', 'authenticated', 'rider-b@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000005', 'authenticated', 'authenticated', 'staff-1@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000006', 'authenticated', 'authenticated', 'manager-1@balaji-test.invalid',  'synthetic-placeholder', now(), now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO profiles (id, role, display_name, phone, login_id, is_active, must_change_password) VALUES
('aaaaaaaa-0000-0000-0000-000000000001', 'customer', 'Customer A',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000002', 'customer', 'Customer B',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000003', 'delivery', 'Rider A',     '9837803293', 'RIDER-A-001', true, true),
('aaaaaaaa-0000-0000-0000-000000000004', 'delivery', 'Rider B',     '9837803293', 'RIDER-B-002', true, true),
('aaaaaaaa-0000-0000-0000-000000000005', 'staff',    'Staff One',   '9837803293', 'STAFF-001',   true, false),
('aaaaaaaa-0000-0000-0000-000000000006', 'manager',  'Manager One', '9837803293', 'MANAGER-001', true, false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO bill_counters (fy, last_no) VALUES ('2026-27', 0)
ON CONFLICT (fy) DO NOTHING;-- M4: Pricing engine, quote_cart, place_order, idempotency, stock locking, movements
-- IMPLEMENTATION_PLAN.md §4.4, §4.5, §4.6, §4.7, §4.9

-- 1) eligible_subtotal — merchandise subtotal before discount/tax, excl. delivery fee
CREATE OR REPLACE FUNCTION public.eligible_subtotal(p_items jsonb)
RETURNS bigint
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
  v_subtotal bigint := 0;
BEGIN
  FOR v_item IN SELECT * FROM jsonb_array_elements(p_items)
  LOOP
    -- Fetch product with FOR SHARE to allow concurrent reads but block updates
    SELECT * INTO v_product
    FROM public.products
    WHERE id = (v_item->>'product_id')::uuid
    FOR SHARE;

    IF NOT FOUND THEN
      CONTINUE; -- invalid product, will be caught in place_order
    END IF;

    v_qty_base := (v_item->>'qty_base')::bigint;
    v_unit_price_paise := v_product.price_paise;

    -- Line pricing: round half up per line (plan §4.5)
    -- line_total = (2 * price * qty + basis) / (2 * basis)
    v_line_total := (2 * v_unit_price_paise * v_qty_base + v_product.price_basis_qty)
                      / (2 * v_product.price_basis_qty);

    v_subtotal := v_subtotal + v_line_total;
  END LOOP;

  RETURN v_subtotal;
END;
$$;

-- 2) compute_delivery_fee — single source of truth for fee logic (plan §4.4)
CREATE OR REPLACE FUNCTION public.compute_delivery_fee(p_eligible_subtotal bigint)
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
  v_fee bigint := 0;
  v_note text := '';
  v_below boolean := false;
  v_to_free bigint := 0;
BEGIN
  SELECT * INTO v_settings FROM public.store_settings;

  IF v_settings.delivery_charge_threshold_paise IS NULL OR v_settings.delivery_charge_threshold_paise = 0 THEN
    RETURN QUERY SELECT 0, 'No delivery-charge threshold configured', false, 0;
    RETURN;
  END IF;

  IF p_eligible_subtotal < v_settings.delivery_charge_threshold_paise THEN
    v_fee := v_settings.delivery_fee_paise;
    v_below := true;
    v_note := 'Delivery fee applied (below delivery-charge threshold)';
    IF v_settings.free_delivery_threshold_paise IS NOT NULL AND v_settings.free_delivery_threshold_paise > 0 THEN
      v_to_free := GREATEST(v_settings.free_delivery_threshold_paise - p_eligible_subtotal, 0);
      v_note := v_note || '; add ' || public.paise_to_rupees(v_to_free) || ' for free delivery';
    END IF;
  ELSIF v_settings.free_delivery_threshold_paise IS NOT NULL
        AND v_settings.free_delivery_threshold_paise > 0
        AND p_eligible_subtotal < v_settings.free_delivery_threshold_paise THEN
    v_fee := COALESCE(v_settings.mid_tier_fee_paise, v_settings.delivery_fee_paise);
    v_below := true;
    v_note := 'Mid-tier delivery fee applied';
    v_to_free := GREATEST(v_settings.free_delivery_threshold_paise - p_eligible_subtotal, 0);
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

-- Helper: paise_to_rupees for note formatting
CREATE OR REPLACE FUNCTION public.paise_to_rupees(p_paise bigint)
RETURNS text
LANGUAGE sql
IMMUTABLE
AS $sql$
  SELECT to_char(p_paise / 100.0, 'FM999999990.00');
$sql$;

-- 3) quote_cart — read-only breakdown for cart/checkout display (plan §4.6)
CREATE OR REPLACE FUNCTION public.quote_cart(p_items jsonb)
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
  v_discount_paise bigint := 0;
  v_delivery_fee_paise bigint := 0;
  v_tax_paise bigint := 0;
  v_grand_total bigint := 0;
  v_fee_info record;
  v_settings public.store_settings%ROWTYPE;
BEGIN
  SELECT * INTO v_settings FROM public.store_settings;

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

      -- Validate qty per §4.5
      IF v_product.quantity_type = 'PACKED' THEN
        IF v_qty_base < v_product.min_qty OR v_qty_base > COALESCE(v_product.max_qty, v_qty_base) THEN
          v_available := false;
          v_error := 'Invalid pack quantity';
        END IF;
      ELSE
        -- WEIGHTED: check step_qty precision and bounds
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

      -- Display qty formatting
      IF v_product.quantity_type = 'WEIGHTED' THEN
        IF v_product.price_basis_qty = 1000 THEN
          v_display_qty := to_char(v_qty_base / 1000.0, 'FM999999990.000') || ' kg';
        ELSE
          v_display_qty := v_qty_base || ' g';
        END IF;
      ELSE
        v_display_qty := v_qty_base || ' ' || v_product.unit_label;
      END IF;

      v_item_subtotal := v_item_subtotal + v_line_total;

    END IF;

    v_lines := v_lines || jsonb_build_object(
      'product_id', v_item->>'product_id',
      'name', COALESCE(v_product.name, 'Unknown'),
      'qty_base', v_qty_base,
      'display_qty', v_display_qty,
      'unit_price_paise', v_unit_price_paise,
      'line_total_paise', v_line_total,
      'available', v_available,
      'error', v_error
    );
  END LOOP;

  -- Delivery fee
  SELECT * INTO v_fee_info FROM public.compute_delivery_fee(v_item_subtotal);
  v_delivery_fee_paise := v_fee_info.fee_paise;

  -- Tax (if enabled)
  IF v_settings.tax_mode = 'exclusive' AND v_settings.tax_rate_bps > 0 THEN
    v_tax_paise := ((v_item_subtotal - v_discount_paise + v_delivery_fee_paise) * v_settings.tax_rate_bps) / 10000;
  ELSIF v_settings.tax_mode = 'inclusive' AND v_settings.tax_rate_bps > 0 THEN
    -- Tax already included in prices; no additional tax line
    v_tax_paise := 0;
  END IF;

  v_grand_total := v_item_subtotal - v_discount_paise + v_delivery_fee_paise + v_tax_paise;

  RETURN jsonb_build_object(
    'lines', v_lines,
    'item_subtotal_paise', v_item_subtotal,
    'discount_paise', v_discount_paise,
    'delivery_fee_paise', v_delivery_fee_paise,
    'fee_rule_note', v_fee_info.rule_note,
    'tax_paise', v_tax_paise,
    'grand_total_paise', v_grand_total,
    'below_charge_threshold', v_fee_info.below_charge_threshold,
    'amount_to_free_delivery', v_fee_info.amount_to_free_delivery
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.quote_cart(jsonb) TO authenticated, anon;
GRANT EXECUTE ON FUNCTION public.compute_delivery_fee(bigint) TO authenticated, anon;
GRANT EXECUTE ON FUNCTION public.eligible_subtotal(jsonb) TO authenticated, anon;



-- ===========================================================================
-- Balaji Provision Store — Synthetic Seed Data
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Store settings — default single row
-- ---------------------------------------------------------------------------
INSERT INTO store_settings (
  id, store_open, timezone, weekly_hours, contact_phone,
  estimated_window_text, delivery_fee_paise, delivery_charge_threshold_paise,
  free_delivery_threshold_paise, mid_tier_fee_paise, max_order_paise,
  cancellation_policy_text, tax_mode, tax_rate_bps,
  online_payment_enabled, pos_price_override_enabled
) VALUES (
  true,
  true,
  'Asia/Kolkata',
  '{"mon":["08:00","22:00"],"tue":["08:00","22:00"],"wed":["08:00","22:00"],"thu":["08:00","22:00"],"fri":["08:00","22:00"],"sat":["08:00","22:00"],"sun":["08:00","22:00"]}'::jsonb,
  '9837803293',
  'Same-day delivery; the exact window is confirmed when the store accepts the order.',
  3000,   -- ₹30 fee when order subtotal is below delivery-charge threshold
  25000,  -- ₹250 threshold
  25000,  -- ₹250 free delivery threshold
  NULL,
  NULL,
  'Cancellation is allowed until the store accepts the order.',
  'none', 0, false, false
)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Categories
-- ---------------------------------------------------------------------------
INSERT INTO catalog_categories (name, slug, sort_order) VALUES
  ('Grains & Staples',  'grains-staples',  1),
  ('Spices & Masalas',  'spices-masalas',  2),
  ('Oils & Ghee',       'oils-ghee',       3),
  ('Snacks & Namkeen',  'snacks-namkeen',  4),
  ('Beverages',         'beverages',       5),
  ('Personal Care',     'personal-care',   6),
  ('Household',         'household',       7),
  ('Dairy & Eggs',      'dairy-eggs',      8),
  ('Fresh Produce',     'fresh-produce',   9)
ON CONFLICT (slug) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Products — PACKED (price per pack; integer quantities only)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('BASMATI-RICE-1KG',      'Basmati Rice 1 kg',              'Premium aged basmati rice',        (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 12000, 1, 'pack', '1 kg',   1, 1, 50, 10, true, true),
('WHOLE-WHEAT-ATTA-5KG',  'Whole Wheat Atta 5 kg',        'Freshly ground whole wheat flour', (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 22000, 1, 'pack', '5 kg',   1, 1, 30,  5, true, true),
('TOOR-DAL-1KG',          'Toor Dal 1 kg',                'Split pigeon peas',                (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 13000, 1, 'pack', '1 kg',   1, 1, 40, 10, true, true),
('SUNFLOWER-OIL-1L',      'Sunflower Oil 1 L',            'Refined sunflower oil',            (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 14000, 1, 'pack', '1 L',    1, 1, 25,  5, true, true),
('MUSTARD-OIL-1L',        'Cold-Pressed Mustard Oil 1 L', 'Cold-pressed mustard oil',         (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 15000, 1, 'pack', '1 L',    1, 1, 20,  5, true, true),
('TURMERIC-POWDER-200G',  'Turmeric Powder 200 g',        'Pure turmeric powder',             (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  6000, 1, 'pack', '200 g',  1, 1, 50, 10, true, true),
('RED-CHILLI-POWDER-200G','Red Chilli Powder 200 g',      'Spicy red chilli powder',          (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  7000, 1, 'pack', '200 g',  1, 1, 45, 10, true, true),
('IODIZED-SALT-1KG',      'Iodized Salt 1 kg',            'Iodized table salt',               (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  2000, 1, 'pack', '1 kg',   1, 1, 60, 15, true, true),
('ASSAM-TEA-250G',        'Assam CTC Tea 250 g',          'Strong Assam CTC tea',             (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  8000, 1, 'pack', '250 g',  1, 1, 35, 10, true, true),
('INSTANT-COFFEE-50G',    'Instant Coffee 50 g',          'Classic instant coffee',           (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  9000, 1, 'pack', '50 g',   1, 1, 30,  5, true, true),
('BIKANERI-BHUJIA-200G',  'Bikaneri Bhujia 200 g',        'Crispy gram flour snack',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5000, 1, 'pack', '200 g',  1, 1, 40, 10, true, true),
('NAVRATAN-MIXTURE-200G', 'Navratan Mixture 200 g',       'Assorted savory mixture',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5500, 1, 'pack', '200 g',  1, 1, 35, 10, true, true),
('PREMIUM-CASHEWS-1KG',   'Premium Cashews 1 kg',         'Whole cashews, kiln roasted',      (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 25000, 1, 'pack', '1 kg',   1, 1, 15,  5, true, true),
('MIXED-DRY-FRUITS-250G', 'Mixed Dry Fruits 250 g',       'Assorted dry fruits',              (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 24999, 1, 'pack', '250 g',  1, 1, 20,  5, true, true);

-- ---------------------------------------------------------------------------
-- Products — WEIGHTED (price per kg unless noted; quantities in grams)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('ONIONS-FRESH',     'Onions',           'Fresh red onions',     (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 50000,  5000, true, true),
('POTATOES-FRESH',   'Potatoes',         'Fresh potatoes',       (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 3000, 1000, 'kg',   NULL, 10, 100, 80000, 10000, true, true),
('TOMATOES-FRESH',   'Tomatoes',         'Fresh red tomatoes',   (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 5000, 1000, 'kg',   NULL, 10, 100, 30000,  3000, true, true),
('BANANAS-ROBUSTA',  'Bananas (Robusta)','Fresh robusta bananas',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 40000,  5000, true, true),
('FRESH-CORIANDER',  'Fresh Coriander',  'Fresh coriander leaves',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 1500,  100, '100 g', NULL, 10, 100,  5000,   500, true, true);

-- ---------------------------------------------------------------------------
-- Service areas
-- ---------------------------------------------------------------------------
INSERT INTO service_areas (kind, value, active) VALUES
  ('pincode', '122001', true),
  ('pincode', '122002', true)
ON CONFLICT (kind, lower(value)) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Synthetic test identities (auth.users + profiles)
-- ---------------------------------------------------------------------------
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at) VALUES
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'customer-a@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'customer-b@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000003', 'authenticated', 'authenticated', 'rider-a@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000004', 'authenticated', 'authenticated', 'rider-b@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000005', 'authenticated', 'authenticated', 'staff-1@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000006', 'authenticated', 'authenticated', 'manager-1@balaji-test.invalid',  'synthetic-placeholder', now(), now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO profiles (id, role, display_name, phone, login_id, is_active, must_change_password) VALUES
('aaaaaaaa-0000-0000-0000-000000000001', 'customer', 'Customer A',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000002', 'customer', 'Customer B',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000003', 'delivery', 'Rider A',     '9837803293', 'RIDER-A-001', true, true),
('aaaaaaaa-0000-0000-0000-000000000004', 'delivery', 'Rider B',     '9837803293', 'RIDER-B-002', true, true),
('aaaaaaaa-0000-0000-0000-000000000005', 'staff',    'Staff One',   '9837803293', 'STAFF-001',   true, false),
('aaaaaaaa-0000-0000-0000-000000000006', 'manager',  'Manager One', '9837803293', 'MANAGER-001', true, false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO bill_counters (fy, last_no) VALUES ('2026-27', 0)
ON CONFLICT (fy) DO NOTHING;-- M4 (cont): place_order with idempotency, stock locking, movements, status events
-- IMPLEMENTATION_PLAN.md §4.7, §4.8, §4.9

-- 1) place_order — single transaction, server-authoritative pricing
CREATE OR REPLACE FUNCTION public.place_order(
  p_payload jsonb,
  p_idempotency_key text
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
  v_item2 jsonb;
  v_product public.products%ROWTYPE;
  v_qty_base bigint;
  v_qty_text text;
  v_new_stock bigint;
  v_unit_price_paise bigint;
  v_line_total_paise bigint;
  v_display_qty text;
  v_order_status public.order_status := 'pending_review';
  v_payment_status public.payment_status := 'unpaid';
  v_idempotency_conflict boolean := false;
  v_settings public.store_settings%ROWTYPE;
BEGIN
  -- 1. Auth & role check
  IF v_user_id IS NULL THEN
    RETURN jsonb_build_object('error', 'UNAUTHENTICATED', 'message', 'Login required');
  END IF;

  SELECT * INTO v_profile FROM public.profiles WHERE id = v_user_id;
  IF NOT FOUND OR v_profile.role <> 'customer' OR NOT v_profile.is_active THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Customer access required');
  END IF;

  -- 2. Idempotency: insert or detect conflict
  BEGIN
    INSERT INTO public.idempotency_keys (user_id, scope, key, request_hash)
    VALUES (v_user_id, 'place_order', p_idempotency_key, md5(p_payload::text));
  EXCEPTION WHEN unique_violation THEN
    -- Check if same request (retry) or different (conflict)
    IF EXISTS (
      SELECT 1 FROM public.idempotency_keys
      WHERE user_id = v_user_id AND scope = 'place_order' AND key = p_idempotency_key
        AND request_hash = md5(p_payload::text)
    ) THEN
      -- Return cached response
      RETURN (SELECT response FROM public.idempotency_keys
              WHERE user_id = v_user_id AND scope = 'place_order' AND key = p_idempotency_key);
    ELSE
      RETURN jsonb_build_object('error', 'IDEMPOTENCY_MISMATCH',
        'message', 'Idempotency key reused with different payload');
    END IF;
  END;

  -- 3. Store check
  SELECT * INTO v_settings FROM public.store_settings;
  IF NOT v_settings.store_open THEN
    RETURN jsonb_build_object('error', 'STORE_CLOSED', 'message', 'Store is currently closed');
  END IF;
  -- TODO: check weekly_hours against now() in store timezone

  -- 4. Address validation
  IF v_address IS NULL OR v_address->>'postal_code' IS NULL THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Delivery address required');
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.service_areas
    WHERE active = true
      AND kind = 'pincode'
      AND lower(value) = lower(v_address->>'postal_code')
  ) THEN
    RETURN jsonb_build_object('error', 'OUT_OF_AREA',
      'message', 'Delivery not available to this PIN code');
  END IF;

  -- 5. Lock products (SELECT FOR UPDATE ordered by id for deadlock avoidance)
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

    v_qty_text := (v_item::jsonb)->>'qty_base';
    -- For PACKED, qty must be a valid integer string
    IF v_product.quantity_type = 'PACKED' THEN
      IF v_qty_text !~ '^\d+$' THEN
        RETURN jsonb_build_object('error', 'QTY_INVALID', 'message', 'Packed quantity must be a positive integer');
      END IF;
      v_qty_base := v_qty_text::bigint;
    ELSE
      -- For WEIGHTED, qty can be numeric but must be integer grams
      v_qty_base := v_qty_text::bigint;
    END IF;

    -- Validate qty
    IF v_product.quantity_type = 'PACKED' THEN
      IF v_qty_base < v_product.min_qty OR v_qty_base > COALESCE(v_product.max_qty, v_qty_base) THEN
        RETURN jsonb_build_object('error', 'QTY_INVALID', 'message', 'Invalid quantity for ' || v_product.name);
      END IF;
    ELSE
      IF v_qty_base % v_product.step_qty <> 0
         OR v_qty_base < v_product.min_qty
         OR v_qty_base > COALESCE(v_product.max_qty, v_qty_base) THEN
        RETURN jsonb_build_object('error', 'QTY_INVALID', 'message', 'Invalid weight for ' || v_product.name);
      END IF;
    END IF;

    IF v_product.stock_qty < v_qty_base THEN
      RETURN jsonb_build_object('error', 'INSUFFICIENT_STOCK', 'message', v_product.name || ' has only ' || v_product.stock_qty || ' available');
    END IF;
  END LOOP;

  -- 6. Compute pricing via quote_cart (same logic)
  v_quote := public.quote_cart(v_items);

  -- 7. Optional max_order_paise check
  IF v_settings.max_order_paise IS NOT NULL AND v_settings.max_order_paise > 0
     AND (v_quote->>'grand_total_paise')::bigint > v_settings.max_order_paise THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Order exceeds maximum allowed value');
  END IF;

  -- 8. Payment method check
  IF v_payment_method = 'online' AND NOT v_settings.online_payment_enabled THEN
    RETURN jsonb_build_object('error', 'ONLINE_PAYMENT_DISABLED', 'message', 'Online payments not enabled');
  END IF;
  IF v_payment_method = 'online' THEN
    v_payment_status := 'pending';
  END IF;

  -- 9. Create order + items + events + stock movements in single transaction
  v_public_order_number := 'BPS-' || to_char(now(), 'YYMMDD') || '-' || lpad(floor(random() * 10000)::text, 4, '0');
  -- Ensure uniqueness (extremely unlikely collision, but safe)
  WHILE EXISTS (SELECT 1 FROM public.orders WHERE public_order_number = v_public_order_number) LOOP
    v_public_order_number := 'BPS-' || to_char(now(), 'YYMMDD') || '-' || lpad(floor(random() * 10000)::text, 4, '0');
  END LOOP;

  -- Address snapshot
  v_address_snapshot := jsonb_build_object(
    'recipient_name', v_address->>'recipient_name',
    'phone', v_address->>'phone',
    'address_lines', v_address->>'address_lines',
    'landmark', v_address->>'landmark',
    'area', v_address->>'area',
    'city', v_address->>'city',
    'state', v_address->>'state',
    'postal_code', v_address->>'postal_code'
  );

  -- Fee breakdown for historical explainability
  v_fee_breakdown := jsonb_build_object(
    'delivery_charge_threshold_paise', v_settings.delivery_charge_threshold_paise,
    'delivery_fee_paise', v_settings.delivery_fee_paise,
    'free_delivery_threshold_paise', v_settings.free_delivery_threshold_paise,
    'mid_tier_fee_paise', v_settings.mid_tier_fee_paise,
    'eligible_subtotal_paise', v_quote->>'item_subtotal_paise',
    'delivery_fee_applied_paise', v_quote->>'delivery_fee_paise',
    'rule_note', v_quote->>'fee_rule_note'
  );

  INSERT INTO public.orders (
    public_order_number, customer_id, address_snapshot, status, payment_method,
    payment_status, item_subtotal_paise, discount_paise, delivery_fee_paise,
    tax_paise, grand_total_paise, fee_breakdown, customer_note, idempotency_key
  ) VALUES (
    v_public_order_number, v_user_id, v_address_snapshot, v_order_status, v_payment_method,
    v_payment_status,
    (v_quote->>'item_subtotal_paise')::bigint,
    (v_quote->>'discount_paise')::bigint,
    (v_quote->>'delivery_fee_paise')::bigint,
    (v_quote->>'tax_paise')::bigint,
    (v_quote->>'grand_total_paise')::bigint,
    v_fee_breakdown, v_customer_note, p_idempotency_key
  ) RETURNING id INTO v_order_id;

  -- Order items + stock decrement + inventory movements
  FOR v_item2 IN SELECT value FROM jsonb_array_elements(v_items)
  LOOP
    SELECT * INTO v_product
    FROM public.products
    WHERE id = (v_item2->>'product_id')::uuid;

    v_qty_base := (v_item2->>'qty_base')::bigint;
    v_unit_price_paise := v_product.price_paise;
    v_line_total_paise := (2 * v_unit_price_paise * v_qty_base + v_product.price_basis_qty)
                            / (2 * v_product.price_basis_qty);

    -- Display qty
    IF v_product.quantity_type = 'WEIGHTED' THEN
      IF v_product.price_basis_qty = 1000 THEN
        v_display_qty := to_char(v_qty_base / 1000.0, 'FM999999990.000') || ' kg';
      ELSE
        v_display_qty := v_qty_base || ' g';
      END IF;
    ELSE
      v_display_qty := v_qty_base || ' ' || v_product.unit_label;
    END IF;

    INSERT INTO public.order_items (
      order_id, product_id, sku_snapshot, name_snapshot, quantity_type_snapshot,
      unit_label_snapshot, price_basis_qty_snapshot, qty_base, unit_price_paise, line_total_paise
    ) VALUES (
      v_order_id, v_product.id, v_product.sku, v_product.name, v_product.quantity_type,
      v_product.unit_label, v_product.price_basis_qty, v_qty_base, v_unit_price_paise, v_line_total_paise
    );

    -- Decrement stock + movement
    WITH updated AS (
      UPDATE public.products SET stock_qty = stock_qty - v_qty_base WHERE sku = v_product.sku
      RETURNING stock_qty
    )
    SELECT stock_qty FROM updated INTO v_new_stock;

    INSERT INTO public.inventory_movements (
      product_id, delta_qty, reason, reference_type, reference_id, actor_id
    ) VALUES (
      v_product.id, -v_qty_base, 'order_placed', 'order', v_order_id, v_user_id
    );
  END LOOP;

  -- Status event
  INSERT INTO public.order_status_events (order_id, from_status, to_status, actor_id, reason)
  VALUES (v_order_id, NULL, 'pending_review', v_user_id, 'Order placed by customer');

  -- Store idempotency response
  UPDATE public.idempotency_keys
  SET response = jsonb_build_object(
    'order_id', v_order_id,
    'public_order_number', v_public_order_number,
    'breakdown', v_quote
  )
  WHERE user_id = v_user_id AND scope = 'place_order' AND key = p_idempotency_key;

  -- Enqueue notification (outside money transaction)
  INSERT INTO public.notification_outbox (order_id, channel, template, status)
  VALUES (v_order_id, 'in_app', 'order_placed', 'pending');

  RETURN jsonb_build_object(
    'order_id', v_order_id,
    'public_order_number', v_public_order_number,
    'breakdown', v_quote
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.place_order(jsonb, text) TO authenticated;



-- ===========================================================================
-- Balaji Provision Store — Synthetic Seed Data
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Store settings — default single row
-- ---------------------------------------------------------------------------
INSERT INTO store_settings (
  id, store_open, timezone, weekly_hours, contact_phone,
  estimated_window_text, delivery_fee_paise, delivery_charge_threshold_paise,
  free_delivery_threshold_paise, mid_tier_fee_paise, max_order_paise,
  cancellation_policy_text, tax_mode, tax_rate_bps,
  online_payment_enabled, pos_price_override_enabled
) VALUES (
  true,
  true,
  'Asia/Kolkata',
  '{"mon":["08:00","22:00"],"tue":["08:00","22:00"],"wed":["08:00","22:00"],"thu":["08:00","22:00"],"fri":["08:00","22:00"],"sat":["08:00","22:00"],"sun":["08:00","22:00"]}'::jsonb,
  '9837803293',
  'Same-day delivery; the exact window is confirmed when the store accepts the order.',
  3000,   -- ₹30 fee when order subtotal is below delivery-charge threshold
  25000,  -- ₹250 threshold
  25000,  -- ₹250 free delivery threshold
  NULL,
  NULL,
  'Cancellation is allowed until the store accepts the order.',
  'none', 0, false, false
)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Categories
-- ---------------------------------------------------------------------------
INSERT INTO catalog_categories (name, slug, sort_order) VALUES
  ('Grains & Staples',  'grains-staples',  1),
  ('Spices & Masalas',  'spices-masalas',  2),
  ('Oils & Ghee',       'oils-ghee',       3),
  ('Snacks & Namkeen',  'snacks-namkeen',  4),
  ('Beverages',         'beverages',       5),
  ('Personal Care',     'personal-care',   6),
  ('Household',         'household',       7),
  ('Dairy & Eggs',      'dairy-eggs',      8),
  ('Fresh Produce',     'fresh-produce',   9)
ON CONFLICT (slug) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Products — PACKED (price per pack; integer quantities only)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('BASMATI-RICE-1KG',      'Basmati Rice 1 kg',              'Premium aged basmati rice',        (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 12000, 1, 'pack', '1 kg',   1, 1, 50, 10, true, true),
('WHOLE-WHEAT-ATTA-5KG',  'Whole Wheat Atta 5 kg',        'Freshly ground whole wheat flour', (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 22000, 1, 'pack', '5 kg',   1, 1, 30,  5, true, true),
('TOOR-DAL-1KG',          'Toor Dal 1 kg',                'Split pigeon peas',                (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 13000, 1, 'pack', '1 kg',   1, 1, 40, 10, true, true),
('SUNFLOWER-OIL-1L',      'Sunflower Oil 1 L',            'Refined sunflower oil',            (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 14000, 1, 'pack', '1 L',    1, 1, 25,  5, true, true),
('MUSTARD-OIL-1L',        'Cold-Pressed Mustard Oil 1 L', 'Cold-pressed mustard oil',         (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 15000, 1, 'pack', '1 L',    1, 1, 20,  5, true, true),
('TURMERIC-POWDER-200G',  'Turmeric Powder 200 g',        'Pure turmeric powder',             (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  6000, 1, 'pack', '200 g',  1, 1, 50, 10, true, true),
('RED-CHILLI-POWDER-200G','Red Chilli Powder 200 g',      'Spicy red chilli powder',          (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  7000, 1, 'pack', '200 g',  1, 1, 45, 10, true, true),
('IODIZED-SALT-1KG',      'Iodized Salt 1 kg',            'Iodized table salt',               (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  2000, 1, 'pack', '1 kg',   1, 1, 60, 15, true, true),
('ASSAM-TEA-250G',        'Assam CTC Tea 250 g',          'Strong Assam CTC tea',             (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  8000, 1, 'pack', '250 g',  1, 1, 35, 10, true, true),
('INSTANT-COFFEE-50G',    'Instant Coffee 50 g',          'Classic instant coffee',           (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  9000, 1, 'pack', '50 g',   1, 1, 30,  5, true, true),
('BIKANERI-BHUJIA-200G',  'Bikaneri Bhujia 200 g',        'Crispy gram flour snack',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5000, 1, 'pack', '200 g',  1, 1, 40, 10, true, true),
('NAVRATAN-MIXTURE-200G', 'Navratan Mixture 200 g',       'Assorted savory mixture',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5500, 1, 'pack', '200 g',  1, 1, 35, 10, true, true),
('PREMIUM-CASHEWS-1KG',   'Premium Cashews 1 kg',         'Whole cashews, kiln roasted',      (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 25000, 1, 'pack', '1 kg',   1, 1, 15,  5, true, true),
('MIXED-DRY-FRUITS-250G', 'Mixed Dry Fruits 250 g',       'Assorted dry fruits',              (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 24999, 1, 'pack', '250 g',  1, 1, 20,  5, true, true);

-- ---------------------------------------------------------------------------
-- Products — WEIGHTED (price per kg unless noted; quantities in grams)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('ONIONS-FRESH',     'Onions',           'Fresh red onions',     (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 50000,  5000, true, true),
('POTATOES-FRESH',   'Potatoes',         'Fresh potatoes',       (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 3000, 1000, 'kg',   NULL, 10, 100, 80000, 10000, true, true),
('TOMATOES-FRESH',   'Tomatoes',         'Fresh red tomatoes',   (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 5000, 1000, 'kg',   NULL, 10, 100, 30000,  3000, true, true),
('BANANAS-ROBUSTA',  'Bananas (Robusta)','Fresh robusta bananas',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 40000,  5000, true, true),
('FRESH-CORIANDER',  'Fresh Coriander',  'Fresh coriander leaves',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 1500,  100, '100 g', NULL, 10, 100,  5000,   500, true, true);

-- ---------------------------------------------------------------------------
-- Service areas
-- ---------------------------------------------------------------------------
INSERT INTO service_areas (kind, value, active) VALUES
  ('pincode', '122001', true),
  ('pincode', '122002', true)
ON CONFLICT (kind, lower(value)) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Synthetic test identities (auth.users + profiles)
-- ---------------------------------------------------------------------------
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at) VALUES
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'customer-a@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'customer-b@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000003', 'authenticated', 'authenticated', 'rider-a@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000004', 'authenticated', 'authenticated', 'rider-b@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000005', 'authenticated', 'authenticated', 'staff-1@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000006', 'authenticated', 'authenticated', 'manager-1@balaji-test.invalid',  'synthetic-placeholder', now(), now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO profiles (id, role, display_name, phone, login_id, is_active, must_change_password) VALUES
('aaaaaaaa-0000-0000-0000-000000000001', 'customer', 'Customer A',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000002', 'customer', 'Customer B',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000003', 'delivery', 'Rider A',     '9837803293', 'RIDER-A-001', true, true),
('aaaaaaaa-0000-0000-0000-000000000004', 'delivery', 'Rider B',     '9837803293', 'RIDER-B-002', true, true),
('aaaaaaaa-0000-0000-0000-000000000005', 'staff',    'Staff One',   '9837803293', 'STAFF-001',   true, false),
('aaaaaaaa-0000-0000-0000-000000000006', 'manager',  'Manager One', '9837803293', 'MANAGER-001', true, false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO bill_counters (fy, last_no) VALUES ('2026-27', 0)
ON CONFLICT (fy) DO NOTHING;-- M7: rider provisioning, order corrections, delivery workflow
-- IMPLEMENTATION_PLAN.md §4.7, §4.8, §5.4, §5.7

-- 1) Rider provisioning RPC (manager only)
-- Creates auth user + profile with synthetic email, generates OTP
CREATE OR REPLACE FUNCTION public.create_rider(
  p_login_id text,
  p_display_name text,
  p_phone text
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_otp text;
  v_email text;
  v_user_id uuid;
  v_profile_id uuid;
BEGIN
  -- Authorization
  IF public.current_role() <> 'manager' THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Manager required');
  END IF;

  -- Validate login_id format
  IF p_login_id !~ '^[A-Z0-9-]+$' THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Login ID must be uppercase alphanumeric with hyphens');
  END IF;

  -- Check uniqueness
  IF EXISTS (SELECT 1 FROM public.profiles WHERE login_id = p_login_id) THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Login ID already exists');
  END IF;

  -- Generate synthetic email and OTP
  v_email := lower(p_login_id) || '@riders.invalid';
  v_otp := encode(gen_random_bytes(6), 'hex'); -- 12-char OTP

  -- Create auth user (using admin API via service role)
  -- This requires the function to be called with service role key
  -- For now, we create the profile directly and note the OTP
  -- In production, this would call auth.admin.create_user
  
  INSERT INTO public.profiles (id, role, display_name, phone, login_id, is_active, must_change_password)
  VALUES (gen_random_uuid(), 'delivery', p_display_name, p_phone, p_login_id, true, true)
  RETURNING id INTO v_profile_id;

  -- Audit log
  INSERT INTO public.audit_logs (actor_id, action, entity_type, entity_id, after, request_id)
  VALUES (auth.uid(), 'CREATE_RIDER', 'profiles', v_profile_id,
          jsonb_build_object('login_id', p_login_id, 'display_name', p_display_name, 'phone', p_phone),
          current_setting('request.id', true)::text);

  RETURN jsonb_build_object(
    'success', true,
    'rider_id', v_profile_id,
    'login_id', p_login_id,
    'otp', v_otp,  -- Shown ONCE to manager, never stored/logged
    'message', 'Rider created. Share OTP securely. Rider must change password on first login.'
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.create_rider(text, text, text) TO authenticated;

-- 2) Reset rider password (manager only)
CREATE OR REPLACE FUNCTION public.reset_rider_password(
  p_rider_id uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_otp text;
  v_login_id text;
BEGIN
  IF public.current_role() <> 'manager' THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Manager required');
  END IF;

  SELECT login_id INTO v_login_id FROM public.profiles WHERE id = p_rider_id AND role = 'delivery';
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Rider not found');
  END IF;

  v_otp := encode(gen_random_bytes(6), 'hex');

  -- In production: call auth.admin.update_user_by_id with new password
  -- For now, update profile flag
  UPDATE public.profiles SET must_change_password = true WHERE id = p_rider_id;

  INSERT INTO public.audit_logs (actor_id, action, entity_type, entity_id, after, request_id)
  VALUES (auth.uid(), 'RESET_RIDER_PASSWORD', 'profiles', p_rider_id,
          jsonb_build_object('login_id', v_login_id),
          current_setting('request.id', true)::text);

  RETURN jsonb_build_object(
    'success', true,
    'otp', v_otp,
    'message', 'Password reset. Share OTP securely.'
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.reset_rider_password(uuid) TO authenticated;

-- 3) Activate/deactivate rider (manager only)
CREATE OR REPLACE FUNCTION public.set_rider_active(
  p_rider_id uuid,
  p_active boolean
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
  IF public.current_role() <> 'manager' THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Manager required');
  END IF;

  UPDATE public.profiles SET is_active = p_active WHERE id = p_rider_id AND role = 'delivery';
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Rider not found');
  END IF;

  -- Revoke sessions if deactivating
  IF NOT p_active THEN
    -- In production: auth.admin.revoke_sessions(p_rider_id)
    NULL;
  END IF;

  INSERT INTO public.audit_logs (actor_id, action, entity_type, entity_id, after, request_id)
  VALUES (auth.uid(), 'SET_RIDER_ACTIVE', 'profiles', p_rider_id,
          jsonb_build_object('is_active', p_active),
          current_setting('request.id', true)::text);

  RETURN jsonb_build_object('success', true);
END;
$$;

GRANT EXECUTE ON FUNCTION public.set_rider_active(uuid, boolean) TO authenticated;

-- 4) Staff/manager assign_order (full implementation)
CREATE OR REPLACE FUNCTION public.assign_order(
  p_order_id uuid,
  p_delivery_user_id uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_order orders%ROWTYPE;
  v_rider profiles%ROWTYPE;
BEGIN
  IF public.current_role() NOT IN ('staff','manager') THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Staff or manager required');
  END IF;

  SELECT * INTO v_order FROM public.orders WHERE id = p_order_id;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Order not found');
  END IF;

  IF v_order.status NOT IN ('ready_for_dispatch', 'out_for_delivery') THEN
    RETURN jsonb_build_object('error', 'INVALID_STATUS', 'message', 'Order must be ready_for_dispatch or out_for_delivery');
  END IF;

  SELECT * INTO v_rider FROM public.profiles WHERE id = p_delivery_user_id AND role = 'delivery' AND is_active = true;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Active rider not found');
  END IF;

  -- Unassign any existing active assignment
  UPDATE public.order_assignments
  SET unassigned_at = now()
  WHERE order_id = p_order_id AND unassigned_at IS NULL;

  -- Create new assignment
  INSERT INTO public.order_assignments (order_id, delivery_user_id, assigned_by)
  VALUES (p_order_id, p_delivery_user_id, auth.uid());

  INSERT INTO public.audit_logs (actor_id, action, entity_type, entity_id, after, request_id)
  VALUES (auth.uid(), 'ASSIGN_ORDER', 'order_assignments', gen_random_uuid(),
          jsonb_build_object('order_id', p_order_id, 'rider_id', p_delivery_user_id),
          current_setting('request.id', true)::text);

  RETURN jsonb_build_object('success', true, 'order_id', p_order_id, 'rider_id', p_delivery_user_id);
END;
$$;

GRANT EXECUTE ON FUNCTION public.assign_order(uuid, uuid) TO authenticated;

-- 5) Rider update status (full implementation with state machine)
CREATE OR REPLACE FUNCTION public.rider_update_status(
  p_order_id uuid,
  p_to_status public.order_status,
  p_note text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_order orders%ROWTYPE;
  v_assignment order_assignments%ROWTYPE;
  v_from_status public.order_status;
  v_allowed boolean;
  v_cod_collected bigint;
BEGIN
  -- Must be delivery role
  IF public.current_role() <> 'delivery' THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Only delivery users may call this');
  END IF;

  -- Order must exist and be assigned to this rider (active assignment)
  SELECT * INTO v_order FROM public.orders WHERE id = p_order_id;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Order not found');
  END IF;

  SELECT * INTO v_assignment
  FROM public.order_assignments
  WHERE order_id = p_order_id
    AND delivery_user_id = auth.uid()
    AND unassigned_at IS NULL;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Order not assigned to you');
  END IF;

  v_from_status := v_order.status;

  -- Check transition is allowed for delivery role (from order_transitions)
  SELECT EXISTS (
    SELECT 1 FROM public.order_transitions
    WHERE from_status = v_from_status
      AND to_status = p_to_status
      AND allowed_roles @> ARRAY['delivery']::public.user_role[]
  ) INTO v_allowed;

  IF NOT v_allowed THEN
    RETURN jsonb_build_object('error', 'INVALID_TRANSITION',
      'message', 'Invalid status transition for rider: ' || v_from_status || ' -> ' || p_to_status);
  END IF;

  -- For delivered: collect COD amount if applicable
  v_cod_collected := NULL;
  IF p_to_status = 'delivered' AND v_order.payment_method = 'cod' THEN
    v_cod_collected := v_order.grand_total_paise;
  END IF;

  -- Perform transition
  UPDATE public.orders
  SET status = p_to_status,
      delivered_at = CASE WHEN p_to_status = 'delivered' THEN now() ELSE delivered_at END,
      cod_collected_paise = COALESCE(v_cod_collected, cod_collected_paise)
  WHERE id = p_order_id;

  INSERT INTO public.order_status_events (order_id, from_status, to_status, actor_id, reason)
  VALUES (p_order_id, v_from_status, p_to_status, auth.uid(), p_note);

  -- Update assignment unassigned_at for terminal states
  IF p_to_status IN ('delivered', 'delivery_failed') THEN
    UPDATE public.order_assignments
    SET unassigned_at = now()
    WHERE order_id = p_order_id AND delivery_user_id = auth.uid() AND unassigned_at IS NULL;
  END IF;

  RETURN jsonb_build_object(
    'order_id', p_order_id,
    'from_status', v_from_status,
    'to_status', p_to_status,
    'cod_collected_paise', v_cod_collected,
    'ok', true
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.rider_update_status(uuid, public.order_status, text) TO authenticated;

-- 6) Staff/manager transition_order (full state machine + stock restore)
CREATE OR REPLACE FUNCTION public.transition_order(
  p_order_id uuid,
  p_to_status public.order_status,
  p_reason text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_order orders%ROWTYPE;
  v_from_status public.order_status;
  v_allowed boolean;
  v_restore_stock boolean := false;
BEGIN
  IF public.current_role() NOT IN ('staff','manager') THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Staff or manager required');
  END IF;

  SELECT * INTO v_order FROM public.orders WHERE id = p_order_id;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Order not found');
  END IF;

  v_from_status := v_order.status;

  -- Check transition is allowed
  SELECT EXISTS (
    SELECT 1 FROM public.order_transitions
    WHERE from_status = v_from_status
      AND to_status = p_to_status
      AND allowed_roles @> ARRAY[public.current_role()]::public.user_role[]
  ) INTO v_allowed;

  IF NOT v_allowed THEN
    RETURN jsonb_build_object('error', 'INVALID_TRANSITION',
      'message', 'Invalid status transition: ' || v_from_status || ' -> ' || p_to_status);
  END IF;

  -- Determine if stock should be restored (rejection/cancellation before dispatch)
  v_restore_stock := p_to_status IN ('rejected', 'cancelled') 
                       AND v_from_status IN ('pending_review', 'accepted', 'preparing', 'ready_for_dispatch');

  -- Perform transition
  UPDATE public.orders
  SET status = p_to_status,
      delivered_at = CASE WHEN p_to_status = 'delivered' THEN now() ELSE delivered_at END
  WHERE id = p_order_id;

  INSERT INTO public.order_status_events (order_id, from_status, to_status, actor_id, reason)
  VALUES (p_order_id, v_from_status, p_to_status, auth.uid(), p_reason);

  -- Restore stock if needed (guarded by movement check to prevent double-restore)
  IF v_restore_stock THEN
    WITH movements AS (
      SELECT product_id, SUM(delta_qty) as total_delta
      FROM public.inventory_movements
      WHERE reference_type = 'order' AND reference_id = p_order_id AND reason = 'order_placed'
      GROUP BY product_id
    )
    UPDATE public.products p
    SET stock_qty = p.stock_qty + m.total_delta
    FROM movements m
    WHERE p.id = m.product_id
      AND NOT EXISTS (
        SELECT 1 FROM public.inventory_movements im
        WHERE im.reference_type = 'order' 
          AND im.reference_id = p_order_id
          AND im.reason IN ('order_cancelled', 'order_rejected', 'item_removed')
          AND im.product_id = m.product_id
      );

    INSERT INTO public.inventory_movements (product_id, delta_qty, reason, reference_type, reference_id, actor_id, note)
    SELECT m.product_id, m.total_delta,
           CASE WHEN p_to_status = 'rejected' THEN 'order_rejected' ELSE 'order_cancelled' END,
           'order', p_order_id, auth.uid(),
           'Auto-restore on ' || p_to_status || ': ' || COALESCE(p_reason, '')
    FROM movements m;
  END IF;

  RETURN jsonb_build_object(
    'order_id', p_order_id,
    'from_status', v_from_status,
    'to_status', p_to_status,
    'stock_restored', v_restore_stock,
    'ok', true
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.transition_order(uuid, public.order_status, text) TO authenticated;

-- 7) Remove order item (staff/manager) - PRD "uncheck unavailable item"
CREATE OR REPLACE FUNCTION public.remove_order_item(
  p_order_id uuid,
  p_item_id uuid,
  p_reason text
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_order orders%ROWTYPE;
  v_item order_items%ROWTYPE;
  v_item_total bigint;
BEGIN
  IF public.current_role() NOT IN ('staff','manager') THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Staff or manager required');
  END IF;

  SELECT * INTO v_order FROM public.orders WHERE id = p_order_id;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Order not found');
  END IF;

  IF v_order.status NOT IN ('pending_review', 'accepted', 'preparing') THEN
    RETURN jsonb_build_object('error', 'INVALID_STATUS', 'message', 'Item removal only allowed before dispatch');
  END IF;

  SELECT * INTO v_item FROM public.order_items WHERE id = p_item_id AND order_id = p_order_id;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Order item not found');
  END IF;

  IF v_item.removed_at IS NOT NULL THEN
    RETURN jsonb_build_object('error', 'ALREADY_REMOVED', 'message', 'Item already removed');
  END IF;

  v_item_total := v_item.line_total_paise;

  -- Mark item removed
  UPDATE public.order_items
  SET removed_at = now(), removed_reason = p_reason
  WHERE id = p_item_id;

  -- Restore stock
  IF v_item.product_id IS NOT NULL THEN
    UPDATE public.products
    SET stock_qty = stock_qty + v_item.qty_base
    WHERE id = v_item.product_id;

    INSERT INTO public.inventory_movements (product_id, delta_qty, reason, reference_type, reference_id, actor_id, note)
    VALUES (v_item.product_id, v_item.qty_base, 'item_removed', 'order', p_order_id, auth.uid(), p_reason);
  END IF;

  -- Recompute order totals
  UPDATE public.orders
  SET item_subtotal_paise = item_subtotal_paise - v_item_total,
      grand_total_paise = grand_total_paise - v_item_total
  WHERE id = p_order_id;

  -- Update payment status if needed (partial refund for COD)
  UPDATE public.orders
  SET payment_status = CASE 
    WHEN payment_method = 'cod' AND payment_status = 'unpaid' THEN 'unpaid'
    WHEN payment_method = 'online' THEN 'refund_pending'
    ELSE payment_status
  END
  WHERE id = p_order_id;

  INSERT INTO public.audit_logs (actor_id, action, entity_type, entity_id, before, after, request_id)
  VALUES (auth.uid(), 'REMOVE_ORDER_ITEM', 'order_items', p_item_id,
          to_jsonb(v_item), jsonb_build_object('removed_at', now(), 'removed_reason', p_reason),
          current_setting('request.id', true)::text);

  RETURN jsonb_build_object('success', true, 'restored_stock', v_item.product_id IS NOT NULL);
END;
$$;

GRANT EXECUTE ON FUNCTION public.remove_order_item(uuid, uuid, text) TO authenticated;

-- 8) Get rider orders RPC (for rider dashboard)
CREATE OR REPLACE FUNCTION public.get_rider_orders()
RETURNS SETOF jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_order RECORD;
BEGIN
  IF public.current_role() <> 'delivery' THEN
    RETURN;
  END IF;

  FOR v_order IN
    SELECT 
      o.id,
      o.public_order_number,
      o.address_snapshot,
      o.payment_method,
      o.cod_collected_paise,
      o.grand_total_paise,
      o.status,
      o.customer_note,
      o.created_at,
      (
        SELECT jsonb_agg(jsonb_build_object(
          'id', oi.id,
          'name', oi.name_snapshot,
          'qty', oi.qty_base,
          'unit', oi.unit_label_snapshot,
          'unitPrice', oi.unit_price_paise,
          'total', oi.line_total_paise
        ))
        FROM order_items oi
        WHERE oi.order_id = o.id AND oi.removed_at IS NULL
      ) AS items
    FROM orders o
    JOIN order_assignments oa ON oa.order_id = o.id
    WHERE oa.delivery_user_id = auth.uid()
      AND oa.unassigned_at IS NULL
      AND o.status IN ('ready_for_dispatch', 'out_for_delivery', 'delivered', 'delivery_failed')
    ORDER BY o.created_at DESC
  LOOP
    RETURN NEXT jsonb_build_object(
      'id', v_order.id,
      'public_order_number', v_order.public_order_number,
      'address_snapshot', v_order.address_snapshot,
      'payment_method', v_order.payment_method,
      'cod_collected_paise', v_order.cod_collected_paise,
      'grand_total_paise', v_order.grand_total_paise,
      'status', v_order.status,
      'customer_note', v_order.customer_note,
      'created_at', v_order.created_at,
      'items', v_order.items
    );
  END LOOP;
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_rider_orders() TO authenticated;



-- ===========================================================================
-- Balaji Provision Store — Synthetic Seed Data
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Store settings — default single row
-- ---------------------------------------------------------------------------
INSERT INTO store_settings (
  id, store_open, timezone, weekly_hours, contact_phone,
  estimated_window_text, delivery_fee_paise, delivery_charge_threshold_paise,
  free_delivery_threshold_paise, mid_tier_fee_paise, max_order_paise,
  cancellation_policy_text, tax_mode, tax_rate_bps,
  online_payment_enabled, pos_price_override_enabled
) VALUES (
  true,
  true,
  'Asia/Kolkata',
  '{"mon":["08:00","22:00"],"tue":["08:00","22:00"],"wed":["08:00","22:00"],"thu":["08:00","22:00"],"fri":["08:00","22:00"],"sat":["08:00","22:00"],"sun":["08:00","22:00"]}'::jsonb,
  '9837803293',
  'Same-day delivery; the exact window is confirmed when the store accepts the order.',
  3000,   -- ₹30 fee when order subtotal is below delivery-charge threshold
  25000,  -- ₹250 threshold
  25000,  -- ₹250 free delivery threshold
  NULL,
  NULL,
  'Cancellation is allowed until the store accepts the order.',
  'none', 0, false, false
)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Categories
-- ---------------------------------------------------------------------------
INSERT INTO catalog_categories (name, slug, sort_order) VALUES
  ('Grains & Staples',  'grains-staples',  1),
  ('Spices & Masalas',  'spices-masalas',  2),
  ('Oils & Ghee',       'oils-ghee',       3),
  ('Snacks & Namkeen',  'snacks-namkeen',  4),
  ('Beverages',         'beverages',       5),
  ('Personal Care',     'personal-care',   6),
  ('Household',         'household',       7),
  ('Dairy & Eggs',      'dairy-eggs',      8),
  ('Fresh Produce',     'fresh-produce',   9)
ON CONFLICT (slug) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Products — PACKED (price per pack; integer quantities only)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('BASMATI-RICE-1KG',      'Basmati Rice 1 kg',              'Premium aged basmati rice',        (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 12000, 1, 'pack', '1 kg',   1, 1, 50, 10, true, true),
('WHOLE-WHEAT-ATTA-5KG',  'Whole Wheat Atta 5 kg',        'Freshly ground whole wheat flour', (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 22000, 1, 'pack', '5 kg',   1, 1, 30,  5, true, true),
('TOOR-DAL-1KG',          'Toor Dal 1 kg',                'Split pigeon peas',                (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 13000, 1, 'pack', '1 kg',   1, 1, 40, 10, true, true),
('SUNFLOWER-OIL-1L',      'Sunflower Oil 1 L',            'Refined sunflower oil',            (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 14000, 1, 'pack', '1 L',    1, 1, 25,  5, true, true),
('MUSTARD-OIL-1L',        'Cold-Pressed Mustard Oil 1 L', 'Cold-pressed mustard oil',         (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 15000, 1, 'pack', '1 L',    1, 1, 20,  5, true, true),
('TURMERIC-POWDER-200G',  'Turmeric Powder 200 g',        'Pure turmeric powder',             (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  6000, 1, 'pack', '200 g',  1, 1, 50, 10, true, true),
('RED-CHILLI-POWDER-200G','Red Chilli Powder 200 g',      'Spicy red chilli powder',          (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  7000, 1, 'pack', '200 g',  1, 1, 45, 10, true, true),
('IODIZED-SALT-1KG',      'Iodized Salt 1 kg',            'Iodized table salt',               (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  2000, 1, 'pack', '1 kg',   1, 1, 60, 15, true, true),
('ASSAM-TEA-250G',        'Assam CTC Tea 250 g',          'Strong Assam CTC tea',             (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  8000, 1, 'pack', '250 g',  1, 1, 35, 10, true, true),
('INSTANT-COFFEE-50G',    'Instant Coffee 50 g',          'Classic instant coffee',           (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  9000, 1, 'pack', '50 g',   1, 1, 30,  5, true, true),
('BIKANERI-BHUJIA-200G',  'Bikaneri Bhujia 200 g',        'Crispy gram flour snack',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5000, 1, 'pack', '200 g',  1, 1, 40, 10, true, true),
('NAVRATAN-MIXTURE-200G', 'Navratan Mixture 200 g',       'Assorted savory mixture',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5500, 1, 'pack', '200 g',  1, 1, 35, 10, true, true),
('PREMIUM-CASHEWS-1KG',   'Premium Cashews 1 kg',         'Whole cashews, kiln roasted',      (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 25000, 1, 'pack', '1 kg',   1, 1, 15,  5, true, true),
('MIXED-DRY-FRUITS-250G', 'Mixed Dry Fruits 250 g',       'Assorted dry fruits',              (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 24999, 1, 'pack', '250 g',  1, 1, 20,  5, true, true);

-- ---------------------------------------------------------------------------
-- Products — WEIGHTED (price per kg unless noted; quantities in grams)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('ONIONS-FRESH',     'Onions',           'Fresh red onions',     (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 50000,  5000, true, true),
('POTATOES-FRESH',   'Potatoes',         'Fresh potatoes',       (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 3000, 1000, 'kg',   NULL, 10, 100, 80000, 10000, true, true),
('TOMATOES-FRESH',   'Tomatoes',         'Fresh red tomatoes',   (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 5000, 1000, 'kg',   NULL, 10, 100, 30000,  3000, true, true),
('BANANAS-ROBUSTA',  'Bananas (Robusta)','Fresh robusta bananas',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 40000,  5000, true, true),
('FRESH-CORIANDER',  'Fresh Coriander',  'Fresh coriander leaves',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 1500,  100, '100 g', NULL, 10, 100,  5000,   500, true, true);

-- ---------------------------------------------------------------------------
-- Service areas
-- ---------------------------------------------------------------------------
INSERT INTO service_areas (kind, value, active) VALUES
  ('pincode', '122001', true),
  ('pincode', '122002', true)
ON CONFLICT (kind, lower(value)) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Synthetic test identities (auth.users + profiles)
-- ---------------------------------------------------------------------------
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at) VALUES
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'customer-a@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'customer-b@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000003', 'authenticated', 'authenticated', 'rider-a@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000004', 'authenticated', 'authenticated', 'rider-b@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000005', 'authenticated', 'authenticated', 'staff-1@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000006', 'authenticated', 'authenticated', 'manager-1@balaji-test.invalid',  'synthetic-placeholder', now(), now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO profiles (id, role, display_name, phone, login_id, is_active, must_change_password) VALUES
('aaaaaaaa-0000-0000-0000-000000000001', 'customer', 'Customer A',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000002', 'customer', 'Customer B',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000003', 'delivery', 'Rider A',     '9837803293', 'RIDER-A-001', true, true),
('aaaaaaaa-0000-0000-0000-000000000004', 'delivery', 'Rider B',     '9837803293', 'RIDER-B-002', true, true),
('aaaaaaaa-0000-0000-0000-000000000005', 'staff',    'Staff One',   '9837803293', 'STAFF-001',   true, false),
('aaaaaaaa-0000-0000-0000-000000000006', 'manager',  'Manager One', '9837803293', 'MANAGER-001', true, false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO bill_counters (fy, last_no) VALUES ('2026-27', 0)
ON CONFLICT (fy) DO NOTHING;-- M8: Inventory adjustments, reports, audit UI (IMPLEMENTATION_PLAN.md §8)
-- adjust_stock, low-stock list, stock reconciliation, reports, audit viewer

-- 1) adjust_stock RPC - manual stock adjustment with reason (staff/manager)
CREATE OR REPLACE FUNCTION public.adjust_stock(
  p_product_id uuid,
  p_delta_qty bigint,
  p_reason public.inventory_reason,
  p_note text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_product products%ROWTYPE;
  v_new_stock bigint;
  v_actor_id uuid := auth.uid();
BEGIN
  -- Authorization
  IF public.current_role() NOT IN ('staff','manager') THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Staff or manager required');
  END IF;

  -- Product must exist
  SELECT * INTO v_product FROM public.products WHERE id = p_product_id;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Product not found');
  END IF;

  -- Validate delta
  IF p_delta_qty = 0 THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Delta quantity cannot be zero');
  END IF;

  -- Check resulting stock won't go negative
  v_new_stock := v_product.stock_qty + p_delta_qty;
  IF v_new_stock < 0 THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Insufficient stock for adjustment');
  END IF;

  -- Apply adjustment
  UPDATE public.products
  SET stock_qty = v_new_stock
  WHERE id = p_product_id;

  -- Record movement
  INSERT INTO public.inventory_movements (product_id, delta_qty, reason, reference_type, reference_id, actor_id, note)
  VALUES (p_product_id, p_delta_qty, p_reason, 'manual', gen_random_uuid(), v_actor_id, p_note);

  -- Audit log
  INSERT INTO public.audit_logs (actor_id, action, entity_type, entity_id, before, after, request_id)
  VALUES (v_actor_id, 'ADJUST_STOCK', 'products', p_product_id,
          jsonb_build_object('stock_qty', v_product.stock_qty),
          jsonb_build_object('stock_qty', v_new_stock, 'delta_qty', p_delta_qty, 'reason', p_reason),
          current_setting('request.id', true)::text);

  RETURN jsonb_build_object(
    'success', true,
    'product_id', p_product_id,
    'old_stock', v_product.stock_qty,
    'new_stock', v_new_stock,
    'delta_qty', p_delta_qty
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.adjust_stock(uuid, bigint, public.inventory_reason, text) TO authenticated;

-- 2) Low stock list RPC
CREATE OR REPLACE FUNCTION public.get_low_stock_products()
RETURNS TABLE (
  id uuid,
  sku text,
  name text,
  stock_qty bigint,
  low_stock_threshold bigint,
  quantity_type public.quantity_type,
  unit_label text,
  price_basis_qty bigint,
  price_paise bigint
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
  IF public.current_role() NOT IN ('staff','manager') THEN
    RETURN;
  END IF;

  RETURN QUERY
  SELECT p.id, p.sku, p.name, p.stock_qty, p.low_stock_threshold,
         p.quantity_type, p.unit_label, p.price_basis_qty, p.price_paise
  FROM public.products p
  WHERE p.active = true
    AND p.stock_qty > 0
    AND p.stock_qty <= p.low_stock_threshold
  ORDER BY p.low_stock_threshold - p.stock_qty DESC, p.name;
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_low_stock_products() TO authenticated;

-- 3) Stock reconciliation check RPC
CREATE OR REPLACE FUNCTION public.check_stock_reconciliation()
RETURNS TABLE (
  product_id uuid,
  sku text,
  name text,
  current_stock bigint,
  movement_sum bigint,
  discrepancy bigint
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
  IF public.current_role() NOT IN ('staff','manager') THEN
    RETURN;
  END IF;

  RETURN QUERY
  SELECT p.id as product_id, p.sku, p.name, p.stock_qty as current_stock,
         COALESCE(SUM(im.delta_qty), 0) as movement_sum,
         p.stock_qty - COALESCE(SUM(im.delta_qty), 0) as discrepancy
  FROM public.products p
  LEFT JOIN public.inventory_movements im ON im.product_id = p.id
  WHERE p.active = true
  GROUP BY p.id, p.sku, p.name, p.stock_qty
  HAVING p.stock_qty <> COALESCE(SUM(im.delta_qty), 0)
  ORDER BY ABS(p.stock_qty - COALESCE(SUM(im.delta_qty), 0)) DESC;
END;
$$;

GRANT EXECUTE ON FUNCTION public.check_stock_reconciliation() TO authenticated;

-- 4) Reports: Sales summary by date range and status
CREATE OR REPLACE FUNCTION public.get_sales_report(
  p_start_date timestamptz,
  p_end_date timestamptz,
  p_status public.order_status DEFAULT NULL
)
RETURNS TABLE (
  total_orders bigint,
  total_revenue_paise bigint,
  total_cod_paise bigint,
  total_online_paise bigint,
  avg_order_value_paise bigint,
  cod_collected_paise bigint,
  cod_pending_paise bigint,
  by_status jsonb,
  by_payment_method jsonb,
  daily_breakdown jsonb
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
  IF public.current_role() NOT IN ('staff','manager') THEN
    RETURN;
  END IF;

  -- Main aggregates
  RETURN QUERY
  WITH filtered_orders AS (
    SELECT o.*
    FROM public.orders o
    WHERE o.created_at >= p_start_date
      AND o.created_at <= p_end_date
      AND (p_status IS NULL OR o.status = p_status)
      AND o.status NOT IN ('cancelled', 'rejected')
  ),
  main_agg AS (
    SELECT 
      COUNT(*) as total_orders,
      COALESCE(SUM(grand_total_paise), 0) as total_revenue_paise,
      COALESCE(SUM(CASE WHEN payment_method = 'cod' THEN grand_total_paise ELSE 0 END), 0) as total_cod_paise,
      COALESCE(SUM(CASE WHEN payment_method = 'online' THEN grand_total_paise ELSE 0 END), 0) as total_online_paise,
      CASE WHEN COUNT(*) > 0 THEN COALESCE(SUM(grand_total_paise), 0) / COUNT(*) ELSE 0 END as avg_order_value_paise,
      COALESCE(SUM(cod_collected_paise), 0) as cod_collected_paise,
      COALESCE(SUM(CASE WHEN payment_method = 'cod' AND payment_status = 'unpaid' THEN grand_total_paise ELSE 0 END), 0) as cod_pending_paise
    FROM filtered_orders
  ),
  status_breakdown AS (
    SELECT jsonb_object_agg(status, cnt) as by_status
    FROM (
      SELECT status, COUNT(*) as cnt
      FROM filtered_orders
      GROUP BY status
    ) s
  ),
  payment_breakdown AS (
    SELECT jsonb_object_agg(payment_method, cnt) as by_payment_method
    FROM (
      SELECT payment_method, COUNT(*) as cnt
      FROM filtered_orders
      GROUP BY payment_method
    ) s
  ),
  daily AS (
    SELECT jsonb_agg(jsonb_build_object(
      'date', d,
      'orders', c,
      'revenue_paise', r
    ) ORDER BY d) as daily_breakdown
    FROM (
      SELECT DATE(created_at) as d,
             COUNT(*) as c,
             COALESCE(SUM(grand_total_paise), 0) as r
      FROM filtered_orders
      GROUP BY DATE(created_at)
    ) d
  )
  SELECT m.total_orders, m.total_revenue_paise, m.total_cod_paise, m.total_online_paise,
         m.avg_order_value_paise, m.cod_collected_paise, m.cod_pending_paise,
         s.by_status, p.by_payment_method, d.daily_breakdown
  FROM main_agg m
  CROSS JOIN status_breakdown s
  CROSS JOIN payment_breakdown p
  CROSS JOIN daily d;
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_sales_report(timestamptz, timestamptz, public.order_status) TO authenticated;

-- 5) Reports: COD reconciliation
CREATE OR REPLACE FUNCTION public.get_cod_reconciliation(
  p_start_date timestamptz,
  p_end_date timestamptz
)
RETURNS TABLE (
  total_cod_orders bigint,
  total_cod_paise bigint,
  collected_paise bigint,
  pending_paise bigint,
  by_rider jsonb,
  daily_breakdown jsonb
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
  IF public.current_role() NOT IN ('staff','manager') THEN
    RETURN;
  END IF;

  RETURN QUERY
  WITH cod_orders AS (
    SELECT o.*, oa.delivery_user_id
    FROM public.orders o
    LEFT JOIN public.order_assignments oa ON oa.order_id = o.id AND oa.unassigned_at IS NULL
    WHERE o.payment_method = 'cod'
      AND o.created_at >= p_start_date
      AND o.created_at <= p_end_date
      AND o.status NOT IN ('cancelled', 'rejected')
  ),
  main_agg AS (
    SELECT 
      COUNT(*) as total_cod_orders,
      COALESCE(SUM(grand_total_paise), 0) as total_cod_paise,
      COALESCE(SUM(cod_collected_paise), 0) as collected_paise,
      COALESCE(SUM(CASE WHEN cod_collected_paise IS NULL OR cod_collected_paise = 0 THEN grand_total_paise ELSE 0 END), 0) as pending_paise
    FROM cod_orders
  ),
  by_rider AS (
    SELECT jsonb_object_agg(rider, stats) as by_rider
    FROM (
      SELECT 
        COALESCE(p.display_name || ' (' || p.login_id || ')', 'Unassigned') as rider,
        jsonb_build_object(
          'orders', COUNT(*),
          'total_paise', COALESCE(SUM(o.grand_total_paise), 0),
          'collected_paise', COALESCE(SUM(o.cod_collected_paise), 0),
          'pending_paise', COALESCE(SUM(CASE WHEN o.cod_collected_paise IS NULL OR o.cod_collected_paise = 0 THEN o.grand_total_paise ELSE 0 END), 0)
        ) as stats
      FROM cod_orders o
      LEFT JOIN public.profiles p ON p.id = o.delivery_user_id
      GROUP BY p.id, p.display_name, p.login_id
    ) s
  ),
  daily AS (
    SELECT jsonb_agg(jsonb_build_object(
      'date', d,
      'orders', c,
      'total_paise', t,
      'collected_paise', col
    ) ORDER BY d) as daily_breakdown
    FROM (
      SELECT DATE(o.created_at) as d,
             COUNT(*) as c,
             COALESCE(SUM(o.grand_total_paise), 0) as t,
             COALESCE(SUM(o.cod_collected_paise), 0) as col
      FROM cod_orders o
      GROUP BY DATE(o.created_at)
    ) d
  )
  SELECT m.total_cod_orders, m.total_cod_paise, m.collected_paise, m.pending_paise,
         r.by_rider, d.daily_breakdown
  FROM main_agg m
  CROSS JOIN by_rider r
  CROSS JOIN daily d;
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_cod_reconciliation(timestamptz, timestamptz) TO authenticated;

-- 5) Audit log viewer RPC
CREATE OR REPLACE FUNCTION public.get_audit_logs(
  p_start_date timestamptz DEFAULT NULL,
  p_end_date timestamptz DEFAULT NULL,
  p_entity_type text DEFAULT NULL,
  p_actor_id uuid DEFAULT NULL,
  p_limit int DEFAULT 100,
  p_offset int DEFAULT 0
)
RETURNS TABLE (
  id uuid,
  actor_id uuid,
  action text,
  entity_type text,
  entity_id text,
  before jsonb,
  after jsonb,
  request_id text,
  created_at timestamptz,
  actor_name text,
  actor_role text
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
  IF public.current_role() <> 'manager' THEN
    RETURN;
  END IF;

  RETURN QUERY
  SELECT al.id, al.actor_id, al.action, al.entity_type, al.entity_id,
         al.before, al.after, al.request_id, al.created_at,
         p.display_name as actor_name, p.role as actor_role
  FROM public.audit_logs al
  LEFT JOIN public.profiles p ON p.id = al.actor_id
  WHERE (p_start_date IS NULL OR al.created_at >= p_start_date)
    AND (p_end_date IS NULL OR al.created_at <= p_end_date)
    AND (p_entity_type IS NULL OR al.entity_type = p_entity_type)
    AND (p_actor_id IS NULL OR al.actor_id = p_actor_id)
  ORDER BY al.created_at DESC
  LIMIT p_limit OFFSET p_offset;
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_audit_logs(timestamptz, timestamptz, text, uuid, int, int) TO authenticated;

-- 6) RLS policies for inventory_movements (already enabled in M1, add manager read)
-- Already covered in M2: inventory_movements read for staff/manager

-- 7) RLS policy for bill_counters (manager only)
CREATE POLICY "bill_counters_manager" ON bill_counters
  FOR ALL TO authenticated
  USING (public.current_role() = 'manager')
  WITH CHECK (public.current_role() = 'manager');

-- 8) RLS policy for pos_sales (staff/manager read, cashier insert)
CREATE POLICY "pos_sales_write_staff" ON pos_sales
  FOR INSERT TO authenticated
  WITH CHECK (public.current_role() IN ('staff','manager'));

CREATE POLICY "pos_sales_read_staff" ON pos_sales
  FOR SELECT TO authenticated
  USING (public.current_role() IN ('staff','manager'));

-- 9) RLS policy for bills (customer reads own, staff/manager read all)
CREATE POLICY "bills_read_own" ON bills
  FOR SELECT TO authenticated
  USING (
    (source = 'ORDER' AND EXISTS (
      SELECT 1 FROM orders o WHERE o.id = bills.order_id AND o.customer_id = auth.uid()
    )) OR
    public.current_role() IN ('staff','manager')
  );

CREATE POLICY "bills_write_staff" ON bills
  FOR ALL TO authenticated
  USING (public.current_role() IN ('staff','manager'))
  WITH CHECK (public.current_role() IN ('staff','manager'));

-- 10) RLS policy for bill_items (follow bills)
CREATE POLICY "bill_items_read_own" ON bill_items
  FOR SELECT TO authenticated
  USING (EXISTS (
    SELECT 1 FROM bills b
    WHERE b.id = bill_items.bill_id
      AND ((b.source = 'ORDER' AND EXISTS (
        SELECT 1 FROM orders o WHERE o.id = b.order_id AND o.customer_id = auth.uid()
      )) OR public.current_role() IN ('staff','manager'))
  ));

CREATE POLICY "bill_items_write_staff" ON bill_items
  FOR ALL TO authenticated
  USING (public.current_role() IN ('staff','manager'))
  WITH CHECK (public.current_role() IN ('staff','manager'));

-- 11) RLS policy for payment_events (internal only)
CREATE POLICY "payment_events_none" ON payment_events
  FOR ALL TO authenticated USING (false);

-- 12) RLS policy for idempotency_keys (user owns their keys)
CREATE POLICY "idempotency_own" ON idempotency_keys
  FOR ALL TO authenticated
  USING (user_id = auth.uid())
  WITH CHECK (user_id = auth.uid());

-- 13) RLS policy for audit_logs (manager read only)
CREATE POLICY "audit_logs_read_manager" ON audit_logs
  FOR SELECT TO authenticated
  USING (public.current_role() = 'manager');

-- 14) RLS policy for notification_outbox (internal only)
CREATE POLICY "notification_outbox_none" ON notification_outbox
  FOR ALL TO authenticated USING (false);



-- ===========================================================================
-- Balaji Provision Store — Synthetic Seed Data
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Store settings — default single row
-- ---------------------------------------------------------------------------
INSERT INTO store_settings (
  id, store_open, timezone, weekly_hours, contact_phone,
  estimated_window_text, delivery_fee_paise, delivery_charge_threshold_paise,
  free_delivery_threshold_paise, mid_tier_fee_paise, max_order_paise,
  cancellation_policy_text, tax_mode, tax_rate_bps,
  online_payment_enabled, pos_price_override_enabled
) VALUES (
  true,
  true,
  'Asia/Kolkata',
  '{"mon":["08:00","22:00"],"tue":["08:00","22:00"],"wed":["08:00","22:00"],"thu":["08:00","22:00"],"fri":["08:00","22:00"],"sat":["08:00","22:00"],"sun":["08:00","22:00"]}'::jsonb,
  '9837803293',
  'Same-day delivery; the exact window is confirmed when the store accepts the order.',
  3000,   -- ₹30 fee when order subtotal is below delivery-charge threshold
  25000,  -- ₹250 threshold
  25000,  -- ₹250 free delivery threshold
  NULL,
  NULL,
  'Cancellation is allowed until the store accepts the order.',
  'none', 0, false, false
)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Categories
-- ---------------------------------------------------------------------------
INSERT INTO catalog_categories (name, slug, sort_order) VALUES
  ('Grains & Staples',  'grains-staples',  1),
  ('Spices & Masalas',  'spices-masalas',  2),
  ('Oils & Ghee',       'oils-ghee',       3),
  ('Snacks & Namkeen',  'snacks-namkeen',  4),
  ('Beverages',         'beverages',       5),
  ('Personal Care',     'personal-care',   6),
  ('Household',         'household',       7),
  ('Dairy & Eggs',      'dairy-eggs',      8),
  ('Fresh Produce',     'fresh-produce',   9)
ON CONFLICT (slug) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Products — PACKED (price per pack; integer quantities only)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('BASMATI-RICE-1KG',      'Basmati Rice 1 kg',              'Premium aged basmati rice',        (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 12000, 1, 'pack', '1 kg',   1, 1, 50, 10, true, true),
('WHOLE-WHEAT-ATTA-5KG',  'Whole Wheat Atta 5 kg',        'Freshly ground whole wheat flour', (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 22000, 1, 'pack', '5 kg',   1, 1, 30,  5, true, true),
('TOOR-DAL-1KG',          'Toor Dal 1 kg',                'Split pigeon peas',                (SELECT id FROM catalog_categories WHERE slug='grains-staples'),  'packed', 13000, 1, 'pack', '1 kg',   1, 1, 40, 10, true, true),
('SUNFLOWER-OIL-1L',      'Sunflower Oil 1 L',            'Refined sunflower oil',            (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 14000, 1, 'pack', '1 L',    1, 1, 25,  5, true, true),
('MUSTARD-OIL-1L',        'Cold-Pressed Mustard Oil 1 L', 'Cold-pressed mustard oil',         (SELECT id FROM catalog_categories WHERE slug='oils-ghee'),       'packed', 15000, 1, 'pack', '1 L',    1, 1, 20,  5, true, true),
('TURMERIC-POWDER-200G',  'Turmeric Powder 200 g',        'Pure turmeric powder',             (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  6000, 1, 'pack', '200 g',  1, 1, 50, 10, true, true),
('RED-CHILLI-POWDER-200G','Red Chilli Powder 200 g',      'Spicy red chilli powder',          (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  7000, 1, 'pack', '200 g',  1, 1, 45, 10, true, true),
('IODIZED-SALT-1KG',      'Iodized Salt 1 kg',            'Iodized table salt',               (SELECT id FROM catalog_categories WHERE slug='spices-masalas'),  'packed',  2000, 1, 'pack', '1 kg',   1, 1, 60, 15, true, true),
('ASSAM-TEA-250G',        'Assam CTC Tea 250 g',          'Strong Assam CTC tea',             (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  8000, 1, 'pack', '250 g',  1, 1, 35, 10, true, true),
('INSTANT-COFFEE-50G',    'Instant Coffee 50 g',          'Classic instant coffee',           (SELECT id FROM catalog_categories WHERE slug='beverages'),       'packed',  9000, 1, 'pack', '50 g',   1, 1, 30,  5, true, true),
('BIKANERI-BHUJIA-200G',  'Bikaneri Bhujia 200 g',        'Crispy gram flour snack',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5000, 1, 'pack', '200 g',  1, 1, 40, 10, true, true),
('NAVRATAN-MIXTURE-200G', 'Navratan Mixture 200 g',       'Assorted savory mixture',          (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed',  5500, 1, 'pack', '200 g',  1, 1, 35, 10, true, true),
('PREMIUM-CASHEWS-1KG',   'Premium Cashews 1 kg',         'Whole cashews, kiln roasted',      (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 25000, 1, 'pack', '1 kg',   1, 1, 15,  5, true, true),
('MIXED-DRY-FRUITS-250G', 'Mixed Dry Fruits 250 g',       'Assorted dry fruits',              (SELECT id FROM catalog_categories WHERE slug='snacks-namkeen'),  'packed', 24999, 1, 'pack', '250 g',  1, 1, 20,  5, true, true);

-- ---------------------------------------------------------------------------
-- Products — WEIGHTED (price per kg unless noted; quantities in grams)
-- ---------------------------------------------------------------------------
INSERT INTO catalog_products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('ONIONS-FRESH',     'Onions',           'Fresh red onions',     (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 50000,  5000, true, true),
('POTATOES-FRESH',   'Potatoes',         'Fresh potatoes',       (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 3000, 1000, 'kg',   NULL, 10, 100, 80000, 10000, true, true),
('TOMATOES-FRESH',   'Tomatoes',         'Fresh red tomatoes',   (SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 5000, 1000, 'kg',   NULL, 10, 100, 30000,  3000, true, true),
('BANANAS-ROBUSTA',  'Bananas (Robusta)','Fresh robusta bananas',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 40000,  5000, true, true),
('FRESH-CORIANDER',  'Fresh Coriander',  'Fresh coriander leaves',(SELECT id FROM catalog_categories WHERE slug='fresh-produce'), 'weighted', 1500,  100, '100 g', NULL, 10, 100,  5000,   500, true, true);

-- ---------------------------------------------------------------------------
-- Service areas
-- ---------------------------------------------------------------------------
INSERT INTO service_areas (kind, value, active) VALUES
  ('pincode', '122001', true),
  ('pincode', '122002', true)
ON CONFLICT (kind, lower(value)) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Synthetic test identities (auth.users + profiles)
-- ---------------------------------------------------------------------------
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at) VALUES
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'customer-a@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'customer-b@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000003', 'authenticated', 'authenticated', 'rider-a@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000004', 'authenticated', 'authenticated', 'rider-b@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000005', 'authenticated', 'authenticated', 'staff-1@balaji-test.invalid',    'synthetic-placeholder', now(), now(), now()),
('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-0000-0000-000000000006', 'authenticated', 'authenticated', 'manager-1@balaji-test.invalid',  'synthetic-placeholder', now(), now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO profiles (id, role, display_name, phone, login_id, is_active, must_change_password) VALUES
('aaaaaaaa-0000-0000-0000-000000000001', 'customer', 'Customer A',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000002', 'customer', 'Customer B',  '9837803293', NULL,        true, false),
('aaaaaaaa-0000-0000-0000-000000000003', 'delivery', 'Rider A',     '9837803293', 'RIDER-A-001', true, true),
('aaaaaaaa-0000-0000-0000-000000000004', 'delivery', 'Rider B',     '9837803293', 'RIDER-B-002', true, true),
('aaaaaaaa-0000-0000-0000-000000000005', 'staff',    'Staff One',   '9837803293', 'STAFF-001',   true, false),
('aaaaaaaa-0000-0000-0000-000000000006', 'manager',  'Manager One', '9837803293', 'MANAGER-001', true, false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO bill_counters (fy, last_no) VALUES ('2026-27', 0)
ON CONFLICT (fy) DO NOTHING;-- ===========================================================================
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


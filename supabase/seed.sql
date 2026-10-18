-- Synthetic seed data for local development and tests (IMPLEMENTATION_PLAN.md §11, M1).
-- All values are PROVISIONAL placeholders for development — NOT owner policy
-- (see docs/DECISIONS.md). Thresholds/fees are manager-editable in the dashboard (M3).
-- Never use real customer data.

-- ---------------------------------------------------------------------------
-- store_settings singleton. Seed placeholders: delivery-charge threshold T1 and
-- delivery fee F1 are dev values; free-delivery threshold T2 is unset (NULL),
-- which means "fee F1 below T1, free at/above T1" (plan §4.4).
-- ---------------------------------------------------------------------------
INSERT INTO store_settings (
  id, store_open, timezone, weekly_hours, contact_phone, estimated_window_text,
  delivery_fee_paise, delivery_charge_threshold_paise, free_delivery_threshold_paise,
  mid_tier_fee_paise, max_order_paise, cancellation_policy_text,
  tax_mode, tax_rate_bps, online_payment_enabled, pos_price_override_enabled
) VALUES (
  true, true, 'Asia/Kolkata',
  '{"mon":["08:00","22:00"],"tue":["08:00","22:00"],"wed":["08:00","22:00"],"thu":["08:00","22:00"],"fri":["08:00","22:00"],"sat":["08:00","22:00"],"sun":["08:00","22:00"]}'::jsonb,
  '9837803293',
  'Same-day delivery; the exact window is confirmed when the store accepts the order.',
  3000,    -- F1: delivery fee below the delivery-charge threshold (placeholder)
  25000,   -- T1: delivery-charge threshold (placeholder seed value)
  NULL,    -- T2: free-delivery threshold (unset)
  NULL,    -- F2: mid-tier fee (unset)
  NULL,    -- max order value (unset)
  'Cancellation is allowed until the store accepts the order.',
  'none', 0, false, false
)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Categories
-- ---------------------------------------------------------------------------
INSERT INTO categories (name, slug, sort_order) VALUES
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
-- Includes items priced below / at / above the seeded delivery-charge
-- threshold so the threshold rules can be tested end-to-end (plan §12 TH-*).
-- ---------------------------------------------------------------------------
INSERT INTO products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('BASMATI-RICE-1KG',      'Basmati Rice 1 kg',              'Premium aged basmati rice',        (SELECT id FROM categories WHERE slug='grains-staples'),  'PACKED', 12000, 1, 'pack', '1 kg',   1, 1, 50, 10, true, true),
('WHOLE-WHEAT-ATTA-5KG',  'Whole Wheat Atta 5 kg',        'Freshly ground whole wheat flour', (SELECT id FROM categories WHERE slug='grains-staples'),  'PACKED', 22000, 1, 'pack', '5 kg',   1, 1, 30,  5, true, true),
('TOOR-DAL-1KG',          'Toor Dal 1 kg',                'Split pigeon peas',                (SELECT id FROM categories WHERE slug='grains-staples'),  'PACKED', 13000, 1, 'pack', '1 kg',   1, 1, 40, 10, true, true),
('SUNFLOWER-OIL-1L',      'Sunflower Oil 1 L',            'Refined sunflower oil',            (SELECT id FROM categories WHERE slug='oils-ghee'),       'PACKED', 14000, 1, 'pack', '1 L',    1, 1, 25,  5, true, true),
('MUSTARD-OIL-1L',        'Cold-Pressed Mustard Oil 1 L', 'Cold-pressed mustard oil',         (SELECT id FROM categories WHERE slug='oils-ghee'),       'PACKED', 15000, 1, 'pack', '1 L',    1, 1, 20,  5, true, true),
('TURMERIC-POWDER-200G',  'Turmeric Powder 200 g',        'Pure turmeric powder',             (SELECT id FROM categories WHERE slug='spices-masalas'),  'PACKED',  6000, 1, 'pack', '200 g',  1, 1, 50, 10, true, true),
('RED-CHILLI-POWDER-200G','Red Chilli Powder 200 g',      'Spicy red chilli powder',          (SELECT id FROM categories WHERE slug='spices-masalas'),  'PACKED',  7000, 1, 'pack', '200 g',  1, 1, 45, 10, true, true),
('IODIZED-SALT-1KG',      'Iodized Salt 1 kg',            'Iodized table salt',               (SELECT id FROM categories WHERE slug='spices-masalas'),  'PACKED',  2000, 1, 'pack', '1 kg',   1, 1, 60, 15, true, true),
('ASSAM-TEA-250G',        'Assam CTC Tea 250 g',          'Strong Assam CTC tea',             (SELECT id FROM categories WHERE slug='beverages'),       'PACKED',  8000, 1, 'pack', '250 g',  1, 1, 35, 10, true, true),
('INSTANT-COFFEE-50G',    'Instant Coffee 50 g',          'Classic instant coffee',           (SELECT id FROM categories WHERE slug='beverages'),       'PACKED',  9000, 1, 'pack', '50 g',   1, 1, 30,  5, true, true),
('BIKANERI-BHUJIA-200G',  'Bikaneri Bhujia 200 g',        'Crispy gram flour snack',          (SELECT id FROM categories WHERE slug='snacks-namkeen'),  'PACKED',  5000, 1, 'pack', '200 g',  1, 1, 40, 10, true, true),
('NAVRATAN-MIXTURE-200G', 'Navratan Mixture 200 g',       'Assorted savory mixture',          (SELECT id FROM categories WHERE slug='snacks-namkeen'),  'PACKED',  5500, 1, 'pack', '200 g',  1, 1, 35, 10, true, true),
-- Exactly at the seeded delivery-charge threshold (cart with just this item = at-threshold case).
('PREMIUM-CASHEWS-1KG',   'Premium Cashews 1 kg',         'Whole cashews, kiln roasted',      (SELECT id FROM categories WHERE slug='snacks-namkeen'),  'PACKED', 25000, 1, 'pack', '1 kg',   1, 1, 15,  5, true, true),
-- One paisa below the seeded delivery-charge threshold (below-threshold case).
('MIXED-DRY-FRUITS-250G', 'Mixed Dry Fruits 250 g',       'Assorted dry fruits',              (SELECT id FROM categories WHERE slug='snacks-namkeen'),  'PACKED', 24999, 1, 'pack', '250 g',  1, 1, 20,  5, true, true),
-- Unavailable item (active but not purchasable).
('INSTANT-NOODLES-4PK',   'Instant Noodles (pack of 4)',  'Masala noodles, pack of 4',        (SELECT id FROM categories WHERE slug='snacks-namkeen'),  'PACKED',  5600, 1, 'pack', '4 pack', 1, 1,  0, 10, true, false),
-- Low-stock item (stock below the low-stock threshold).
('HANDWASH-500ML',        'Hand Wash Refill 500 ml',      'Gentle hand wash refill',          (SELECT id FROM categories WHERE slug='household'),       'PACKED',  4500, 1, 'pack', '500 ml', 1, 1,  2, 10, true, true),
-- Archived item (not purchasable, not listed).
('LEGACY-DEMO-ITEM',      'Legacy Demo Item (archived)',  'Archived demo product',            (SELECT id FROM categories WHERE slug='household'),       'PACKED',  3000, 1, 'pack', '1 pack', 1, 1,  3,  0, false, false);

-- ---------------------------------------------------------------------------
-- Products — WEIGHTED (price per kg unless noted; quantities in grams)
-- ---------------------------------------------------------------------------
INSERT INTO products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('ONIONS-FRESH',     'Onions',           'Fresh red onions',     (SELECT id FROM categories WHERE slug='fresh-produce'), 'WEIGHTED', 6000, 1000, 'kg',   NULL, 10, 100, 50000,  5000, true, true),
('POTATOES-FRESH',   'Potatoes',         'Fresh potatoes',       (SELECT id FROM categories WHERE slug='fresh-produce'), 'WEIGHTED', 3000, 1000, 'kg',   NULL, 10, 100, 80000, 10000, true, true),
('TOMATOES-FRESH',   'Tomatoes',         'Fresh red tomatoes',   (SELECT id FROM categories WHERE slug='fresh-produce'), 'WEIGHTED', 5000, 1000, 'kg',   NULL, 10, 100, 30000,  3000, true, true),
('BANANAS-ROBUSTA',  'Bananas (Robusta)','Fresh robusta bananas',(SELECT id FROM categories WHERE slug='fresh-produce'), 'WEIGHTED', 6000, 1000, 'kg',   NULL, 10, 100, 40000,  5000, true, true),
-- Priced per 100 g to exercise the second allowed WEIGHTED price basis.
('FRESH-CORIANDER',  'Fresh Coriander',  'Fresh coriander leaves',(SELECT id FROM categories WHERE slug='fresh-produce'), 'WEIGHTED', 1500,  100, '100 g', NULL, 10, 100,  5000,   500, true, true);

-- ---------------------------------------------------------------------------
-- Service areas — two synthetic PIN codes (real list is a BLOCKED owner decision)
-- ---------------------------------------------------------------------------
INSERT INTO service_areas (kind, value, active) VALUES
  ('pincode', '122001', true),
  ('pincode', '122002', true)
ON CONFLICT (kind, lower(value)) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Synthetic test identities (auth.users + profiles)
-- Emails are .invalid by design; passwords are placeholders — these accounts
-- exist only for local RLS and integration tests (M2+).
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

-- ---------------------------------------------------------------------------
-- Bill counter for the current financial year (April–March). D-BILLNO:
-- gapless numbering BPS/<FY>/<6-digit>; the counter row is created here and
-- incremented row-locked inside each sale transaction (M9).
-- ---------------------------------------------------------------------------
INSERT INTO bill_counters (fy, last_no) VALUES ('2026-27', 0)
ON CONFLICT (fy) DO NOTHING;

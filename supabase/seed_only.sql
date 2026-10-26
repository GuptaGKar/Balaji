-- ===========================================================================
-- Balaji Provision Store — Synthetic Seed Data Only
-- Run this in Supabase SQL Editor if tables already exist.
-- ===========================================================================

-- Views for backwards compatibility between categories / catalog_categories and products / catalog_products
CREATE OR REPLACE VIEW catalog_categories AS SELECT * FROM categories;
CREATE OR REPLACE VIEW catalog_products AS SELECT * FROM products;

-- Store Settings
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
  3000, 25000, 25000, NULL, NULL,
  'Cancellation is allowed until the store accepts the order.',
  'none', 0, false, false
)
ON CONFLICT (id) DO NOTHING;

-- Categories
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

-- Products (PACKED)
INSERT INTO products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('BASMATI-RICE-1KG',      'Basmati Rice 1 kg',              'Premium aged basmati rice',        (SELECT id FROM categories WHERE slug='grains-staples'),  'packed', 12000, 1, 'pack', '1 kg',   1, 1, 50, 10, true, true),
('WHOLE-WHEAT-ATTA-5KG',  'Whole Wheat Atta 5 kg',        'Freshly ground whole wheat flour', (SELECT id FROM categories WHERE slug='grains-staples'),  'packed', 22000, 1, 'pack', '5 kg',   1, 1, 30,  5, true, true),
('TOOR-DAL-1KG',          'Toor Dal 1 kg',                'Split pigeon peas',                (SELECT id FROM categories WHERE slug='grains-staples'),  'packed', 13000, 1, 'pack', '1 kg',   1, 1, 40, 10, true, true),
('SUNFLOWER-OIL-1L',      'Sunflower Oil 1 L',            'Refined sunflower oil',            (SELECT id FROM categories WHERE slug='oils-ghee'),       'packed', 14000, 1, 'pack', '1 L',    1, 1, 25,  5, true, true),
('MUSTARD-OIL-1L',        'Cold-Pressed Mustard Oil 1 L', 'Cold-pressed mustard oil',         (SELECT id FROM categories WHERE slug='oils-ghee'),       'packed', 15000, 1, 'pack', '1 L',    1, 1, 20,  5, true, true),
('TURMERIC-POWDER-200G',  'Turmeric Powder 200 g',        'Pure turmeric powder',             (SELECT id FROM categories WHERE slug='spices-masalas'),  'packed',  6000, 1, 'pack', '200 g',  1, 1, 50, 10, true, true),
('RED-CHILLI-POWDER-200G','Red Chilli Powder 200 g',      'Spicy red chilli powder',          (SELECT id FROM categories WHERE slug='spices-masalas'),  'packed',  7000, 1, 'pack', '200 g',  1, 1, 45, 10, true, true),
('IODIZED-SALT-1KG',      'Iodized Salt 1 kg',            'Iodized table salt',               (SELECT id FROM categories WHERE slug='spices-masalas'),  'packed',  2000, 1, 'pack', '1 kg',   1, 1, 60, 15, true, true),
('ASSAM-TEA-250G',        'Assam CTC Tea 250 g',          'Strong Assam CTC tea',             (SELECT id FROM categories WHERE slug='beverages'),       'packed',  8000, 1, 'pack', '250 g',  1, 1, 35, 10, true, true),
('INSTANT-COFFEE-50G',    'Instant Coffee 50 g',          'Classic instant coffee',           (SELECT id FROM categories WHERE slug='beverages'),       'packed',  9000, 1, 'pack', '50 g',   1, 1, 30,  5, true, true),
('BIKANERI-BHUJIA-200G',  'Bikaneri Bhujia 200 g',        'Crispy gram flour snack',          (SELECT id FROM categories WHERE slug='snacks-namkeen'),  'packed',  5000, 1, 'pack', '200 g',  1, 1, 40, 10, true, true),
('NAVRATAN-MIXTURE-200G', 'Navratan Mixture 200 g',       'Assorted savory mixture',          (SELECT id FROM categories WHERE slug='snacks-namkeen'),  'packed',  5500, 1, 'pack', '200 g',  1, 1, 35, 10, true, true),
('PREMIUM-CASHEWS-1KG',   'Premium Cashews 1 kg',         'Whole cashews, kiln roasted',      (SELECT id FROM categories WHERE slug='snacks-namkeen'),  'packed', 25000, 1, 'pack', '1 kg',   1, 1, 15,  5, true, true),
('MIXED-DRY-FRUITS-250G', 'Mixed Dry Fruits 250 g',       'Assorted dry fruits',              (SELECT id FROM categories WHERE slug='snacks-namkeen'),  'packed', 24999, 1, 'pack', '250 g',  1, 1, 20,  5, true, true)
ON CONFLICT (sku) DO NOTHING;

-- Products (WEIGHTED)
INSERT INTO products (sku, name, description, category_id, quantity_type, price_paise, price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available) VALUES
('ONIONS-FRESH',     'Onions',           'Fresh red onions',     (SELECT id FROM categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 50000,  5000, true, true),
('POTATOES-FRESH',   'Potatoes',         'Fresh potatoes',       (SELECT id FROM categories WHERE slug='fresh-produce'), 'weighted', 3000, 1000, 'kg',   NULL, 10, 100, 80000, 10000, true, true),
('TOMATOES-FRESH',   'Tomatoes',         'Fresh red tomatoes',   (SELECT id FROM categories WHERE slug='fresh-produce'), 'weighted', 5000, 1000, 'kg',   NULL, 10, 100, 30000,  3000, true, true),
('BANANAS-ROBUSTA',  'Bananas (Robusta)','Fresh robusta bananas',(SELECT id FROM categories WHERE slug='fresh-produce'), 'weighted', 6000, 1000, 'kg',   NULL, 10, 100, 40000,  5000, true, true),
('FRESH-CORIANDER',  'Fresh Coriander',  'Fresh coriander leaves',(SELECT id FROM categories WHERE slug='fresh-produce'), 'weighted', 1500,  100, '100 g', NULL, 10, 100,  5000,   500, true, true)
ON CONFLICT (sku) DO NOTHING;

-- Service Areas
INSERT INTO service_areas (kind, value, active) VALUES
  ('pincode', '122001', true),
  ('pincode', '122002', true)
ON CONFLICT (kind, lower(value)) DO NOTHING;

-- Bill Counters
INSERT INTO bill_counters (fy, last_no) VALUES ('2026-27', 0)
ON CONFLICT (fy) DO NOTHING;

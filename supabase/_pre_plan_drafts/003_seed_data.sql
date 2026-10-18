-- Balaji Provision Store - Seed Data
-- Version 1.0 - Initial categories and sample products

-- Categories
INSERT INTO categories (name, description, display_order) VALUES
    ('Grains & Staples', 'Rice, wheat, flour, pulses', 1),
    ('Spices & Masalas', 'Whole and ground spices', 2),
    ('Oils & Ghee', 'Cooking oils, ghee, vanaspati', 3),
    ('Snacks & Namkeen', 'Bhujia, mixture, chips, papad', 4),
    ('Beverages', 'Tea, coffee, juices, soft drinks', 5),
    ('Personal Care', 'Soaps, shampoos, toothpaste', 6),
    ('Household', 'Detergents, cleaners, disposables', 7),
    ('Dairy & Eggs', 'Milk, curd, paneer, eggs', 8),
    ('Fresh Produce', 'Vegetables and fruits (weighted)', 9);

-- Sample Products (prices in paise)
-- Packed products
INSERT INTO products (sku, name, description, category_id, product_type, unit, price_paise, price_basis_unit, stock_quantity, min_stock_alert, is_active, is_available, display_order, created_by)
SELECT 'RICE-BAS-01', 'Basmati Rice 1kg', 'Premium aged basmati rice', c.id, 'packed', 'pack', 12000, 'pack', 50, 10, true, true, 1, p.id
FROM categories c, profiles p WHERE c.name = 'Grains & Staples' AND p.role = 'admin' LIMIT 1;

INSERT INTO products (sku, name, description, category_id, product_type, unit, price_paise, price_basis_unit, stock_quantity, min_stock_alert, is_active, is_available, display_order, created_by)
SELECT 'WHEAT-ATT-01', 'Whole Wheat Atta 5kg', 'Freshly ground whole wheat flour', c.id, 'packed', 'pack', 22000, 'pack', 30, 5, true, true, 2, p.id
FROM categories c, profiles p WHERE c.name = 'Grains & Staples' AND p.role = 'admin' LIMIT 1;

INSERT INTO products (sku, name, description, category_id, product_type, unit, price_paise, price_basis_unit, stock_quantity, min_stock_alert, is_active, is_available, display_order, created_by)
SELECT 'TOOR-DAL-01', 'Toor Dal 1kg', 'Split pigeon peas', c.id, 'packed', 'pack', 13000, 'pack', 40, 10, true, true, 3, p.id
FROM categories c, profiles p WHERE c.name = 'Grains & Staples' AND p.role = 'admin' LIMIT 1;

INSERT INTO products (sku, name, description, category_id, product_type, unit, price_paise, price_basis_unit, stock_quantity, min_stock_alert, is_active, is_available, display_order, created_by)
SELECT 'SUN-OIL-01', 'Sunflower Oil 1L', 'Refined sunflower oil', c.id, 'packed', 'pack', 14000, 'pack', 25, 5, true, true, 1, p.id
FROM categories c, profiles p WHERE c.name = 'Oils & Ghee' AND p.role = 'admin' LIMIT 1;

INSERT INTO products (sku, name, description, category_id, product_type, unit, price_paise, price_basis_unit, stock_quantity, min_stock_alert, is_active, is_available, display_order, created_by)
SELECT 'MUST-OIL-01', 'Mustard Oil 1L', 'Cold pressed mustard oil', c.id, 'packed', 'pack', 15000, 'pack', 20, 5, true, true, 2, p.id
FROM categories c, profiles p WHERE c.name = 'Oils & Ghee' AND p.role = 'admin' LIMIT 1;

INSERT INTO products (sku, name, description, category_id, product_type, unit, price_paise, price_basis_unit, stock_quantity, min_stock_alert, is_active, is_available, display_order, created_by)
SELECT 'TURMERIC-01', 'Turmeric Powder 200g', 'Pure turmeric powder', c.id, 'packed', 'pack', 6000, 'pack', 50, 10, true, true, 1, p.id
FROM categories c, profiles p WHERE c.name = 'Spices & Masalas' AND p.role = 'admin' LIMIT 1;

INSERT INTO products (sku, name, description, category_id, product_type, unit, price_paise, price_basis_unit, stock_quantity, min_stock_alert, is_active, is_available, display_order, created_by)
SELECT 'CHILLI-01', 'Red Chilli Powder 200g', 'Spicy red chilli powder', c.id, 'packed', 'pack', 7000, 'pack', 45, 10, true, true, 2, p.id
FROM categories c, profiles p WHERE c.name = 'Spices & Masalas' AND p.role = 'admin' LIMIT 1;

INSERT INTO products (sku, name, description, category_id, product_type, unit, price_paise, price_basis_unit, stock_quantity, min_stock_alert, is_active, is_available, display_order, created_by)
SELECT 'SALT-01', 'Iodized Salt 1kg', 'Tata salt / equivalent', c.id, 'packed', 'pack', 2000, 'pack', 60, 15, true, true, 3, p.id
FROM categories c, profiles p WHERE c.name = 'Spices & Masalas' AND p.role = 'admin' LIMIT 1;

INSERT INTO products (sku, name, description, category_id, product_type, unit, price_paise, price_basis_unit, stock_quantity, min_stock_alert, is_active, is_available, display_order, created_by)
SELECT 'TEA-01', 'Assam Tea 250g', 'Strong Assam CTC tea', c.id, 'packed', 'pack', 8000, 'pack', 35, 10, true, true, 1, p.id
FROM categories c, profiles p WHERE c.name = 'Beverages' AND p.role = 'admin' LIMIT 1;

INSERT INTO products (sku, name, description, category_id, product_type, unit, price_paise, price_basis_unit, stock_quantity, min_stock_alert, is_active, is_available, display_order, created_by)
SELECT 'COFFEE-01', 'Instant Coffee 50g', 'Classic instant coffee', c.id, 'packed', 'pack', 9000, 'pack', 30, 5, true, true, 2, p.id
FROM categories c, profiles p WHERE c.name = 'Beverages' AND p.role = 'admin' LIMIT 1;

INSERT INTO products (sku, name, description, category_id, product_type, unit, price_paise, price_basis_unit, stock_quantity, min_stock_alert, is_active, is_available, display_order, created_by)
SELECT 'BHUJIA-01', 'Bikaneri Bhujia 200g', 'Crispy gram flour snack', c.id, 'packed', 'pack', 5000, 'pack', 40, 10, true, true, 1, p.id
FROM categories c, profiles p WHERE c.name = 'Snacks & Namkeen' AND p.role = 'admin' LIMIT 1;

INSERT INTO products (sku, name, description, category_id, product_type, unit, price_paise, price_basis_unit, stock_quantity, min_stock_alert, is_active, is_available, display_order, created_by)
SELECT 'MIXTURE-01', 'Navratan Mixture 200g', 'Assorted savory mixture', c.id, 'packed', 'pack', 5500, 'pack', 35, 10, true, true, 2, p.id
FROM categories c, profiles p WHERE c.name = 'Snacks & Namkeen' AND p.role = 'admin' LIMIT 1;

-- Weighted products (stock in grams, price per kg)
INSERT INTO products (sku, name, description, category_id, product_type, unit, price_paise, price_basis_unit, stock_quantity, min_stock_alert, is_active, is_available, display_order, created_by)
SELECT 'ONION-01', 'Onions', 'Fresh red onions', c.id, 'weighted', 'kg', 4000, 'kg', 50000, 5000, true, true, 1, p.id
FROM categories c, profiles p WHERE c.name = 'Fresh Produce' AND p.role = 'admin' LIMIT 1;

INSERT INTO products (sku, name, description, category_id, product_type, unit, price_paise, price_basis_unit, stock_quantity, min_stock_alert, is_active, is_available, display_order, created_by)
SELECT 'POTATO-01', 'Potatoes', 'Fresh potatoes', c.id, 'weighted', 'kg', 3000, 'kg', 80000, 10000, true, true, 2, p.id
FROM categories c, profiles p WHERE c.name = 'Fresh Produce' AND p.role = 'admin' LIMIT 1;

INSERT INTO products (sku, name, description, category_id, product_type, unit, price_paise, price_basis_unit, stock_quantity, min_stock_alert, is_active, is_available, display_order, created_by)
SELECT 'TOMATO-01', 'Tomatoes', 'Fresh red tomatoes', c.id, 'weighted', 'kg', 5000, 'kg', 30000, 3000, true, true, 3, p.id
FROM categories c, profiles p WHERE c.name = 'Fresh Produce' AND p.role = 'admin' LIMIT 1;

INSERT INTO products (sku, name, description, category_id, product_type, unit, price_paise, price_basis_unit, stock_quantity, min_stock_alert, is_active, is_available, display_order, created_by)
SELECT 'BANANA-01', 'Bananas (Robusta)', 'Fresh robusta bananas', c.id, 'weighted', 'kg', 6000, 'kg', 40000, 5000, true, true, 4, p.id
FROM categories c, profiles p WHERE c.name = 'Fresh Produce' AND p.role = 'admin' LIMIT 1;

-- Service Areas (sample PIN codes)
INSERT INTO service_areas (name, pin_code, is_active, display_order, created_by)
SELECT 'Sector 15', '122001', true, 1, p.id FROM profiles p WHERE p.role = 'admin' LIMIT 1;

INSERT INTO service_areas (name, pin_code, is_active, display_order, created_by)
SELECT 'Sector 14', '122001', true, 2, p.id FROM profiles p WHERE p.role = 'admin' LIMIT 1;

INSERT INTO service_areas (name, pin_code, is_active, display_order, created_by)
SELECT 'Sector 17', '122002', true, 3, p.id FROM profiles p WHERE p.role = 'admin' LIMIT 1;

INSERT INTO service_areas (name, pin_code, is_active, display_order, created_by)
SELECT 'DLF Phase 1', '122002', true, 4, p.id FROM profiles p WHERE p.role = 'admin' LIMIT 1;

INSERT INTO service_areas (name, pin_code, is_active, display_order, created_by)
SELECT 'DLF Phase 2', '122002', true, 5, p.id FROM profiles p WHERE p.role = 'admin' LIMIT 1;

INSERT INTO service_areas (name, pin_code, is_active, display_order, created_by)
SELECT 'Sushant Lok', '122009', true, 6, p.id FROM profiles p WHERE p.role = 'admin' LIMIT 1;

INSERT INTO service_areas (name, pin_code, is_active, display_order, created_by)
SELECT 'Golf Course Road', '122009', true, 7, p.id FROM profiles p WHERE p.role = 'admin' LIMIT 1;
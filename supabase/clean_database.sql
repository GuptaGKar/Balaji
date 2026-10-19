-- ===========================================================================
-- Balaji Provision Store — Clean Database & Reset Script
-- Run this in Supabase SQL Editor to wipe existing test items and allow
-- adding categories and products cleanly from the Web Admin Portal.
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

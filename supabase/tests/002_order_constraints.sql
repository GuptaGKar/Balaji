-- pgTAP: order and order-item constraints (IMPLEMENTATION_PLAN.md §4.2)
-- Fixtures: a synthetic customer profile owns the test orders.

BEGIN;
SELECT plan(9);

INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        'bbbbbbbb-0000-0000-0000-000000000001',
        'authenticated', 'authenticated',
        'order-test@balaji-test.invalid', 'synthetic-placeholder', now(), now(), now());

INSERT INTO profiles (id, role, display_name)
VALUES ('bbbbbbbb-0000-0000-0000-000000000001', 'customer', 'Order Test');

-- 1. Order numbers are unique.
SELECT lives_ok(
  $sql$INSERT INTO orders (public_order_number, customer_id, address_snapshot)
       VALUES ('TST-ORDER-001', 'bbbbbbbb-0000-0000-0000-000000000001', '{}'::jsonb)$sql$,
  'first order number is accepted');
SELECT throws_ok(
  $sql$INSERT INTO orders (public_order_number, customer_id, address_snapshot)
       VALUES ('TST-ORDER-001', 'bbbbbbbb-0000-0000-0000-000000000001', '{}'::jsonb)$sql$,
  null, null,
  'duplicate public_order_number is rejected');

-- 2. Negative order money is rejected.
SELECT throws_ok(
  $sql$INSERT INTO orders (public_order_number, customer_id, address_snapshot, grand_total_paise)
       VALUES ('TST-ORDER-002', 'bbbbbbbb-0000-0000-0000-000000000001', '{}'::jsonb, -1)$sql$,
  null, null,
  'negative grand_total_paise is rejected');

-- 3. Order item quantity must be positive (integer base units).
INSERT INTO orders (public_order_number, customer_id, address_snapshot)
VALUES ('TST-ORDER-003', 'bbbbbbbb-0000-0000-0000-000000000001', '{}'::jsonb);
SELECT throws_ok(
  $sql$INSERT INTO order_items (order_id, sku_snapshot, name_snapshot, quantity_type_snapshot, unit_label_snapshot, price_basis_qty_snapshot, qty_base, unit_price_paise, line_total_paise)
       VALUES ((SELECT id FROM orders WHERE public_order_number = 'TST-ORDER-003'),
               'TST-SKU', 'Item', 'PACKED', 'pack', 1, 0, 100, 0)$sql$,
  null, null,
  'order item with qty_base <= 0 is rejected');

-- 4. Negative order-item money is rejected.
SELECT throws_ok(
  $sql$INSERT INTO order_items (order_id, sku_snapshot, name_snapshot, quantity_type_snapshot, unit_label_snapshot, price_basis_qty_snapshot, qty_base, unit_price_paise, line_total_paise)
       VALUES ((SELECT id FROM orders WHERE public_order_number = 'TST-ORDER-003'),
               'TST-SKU', 'Item', 'PACKED', 'pack', 1, 1, -5, -5)$sql$,
  null, null,
  'negative unit_price_paise / line_total_paise is rejected');

-- 5. Non-numeric quantity is rejected by the integer column type.
--    (Fractional strings would be rounded by PostgreSQL on cast; the
--     M4 RPC rejects fractional input before casting. See DECISIONS.md.)
SELECT throws_ok(
  $sql$INSERT INTO order_items (order_id, sku_snapshot, name_snapshot, quantity_type_snapshot, unit_label_snapshot, price_basis_qty_snapshot, qty_base, unit_price_paise, line_total_paise)
       VALUES ((SELECT id FROM orders WHERE public_order_number = 'TST-ORDER-003'),
               'TST-SKU', 'Item', 'PACKED', 'pack', 1, 'abc'::bigint, 100, 100)$sql$,
  null, null,
  'non-numeric qty_base is rejected');

-- 6. Deleting a product referenced by order_items is blocked;
--    archiving it is allowed.
INSERT INTO products (sku, name, quantity_type, price_paise, price_basis_qty, unit_label, step_qty, min_qty, stock_qty)
VALUES ('TST-REFPROD', 'Referenced product', 'PACKED', 100, 1, 'pack', 1, 1, 5);
INSERT INTO order_items (order_id, product_id, sku_snapshot, name_snapshot, quantity_type_snapshot, unit_label_snapshot, price_basis_qty_snapshot, qty_base, unit_price_paise, line_total_paise)
VALUES ((SELECT id FROM orders WHERE public_order_number = 'TST-ORDER-003'),
        (SELECT id FROM products WHERE sku = 'TST-REFPROD'),
        'TST-REFPROD', 'Referenced product', 'PACKED', 'pack', 1, 1, 100, 100);
SELECT throws_ok(
  $sql$DELETE FROM products WHERE sku = 'TST-REFPROD'$sql$,
  null, null,
  'deleting a product referenced by order_items is blocked');
SELECT lives_ok(
  $sql$UPDATE products SET active = false WHERE sku = 'TST-REFPROD'$sql$,
  'archiving a referenced product is allowed');

-- 7. A second store_settings row is rejected (singleton).
SELECT throws_ok(
  $sql$INSERT INTO store_settings (id) VALUES (true)$sql$,
  null, null,
  'second store_settings row is rejected');

-- 8. free_delivery_threshold_paise below delivery_charge_threshold_paise
--    is rejected (seeded T1 is raised here so the test is independent
--    of seed values).
SELECT throws_ok(
  $sql$UPDATE store_settings
       SET delivery_charge_threshold_paise = 1000, free_delivery_threshold_paise = 999$sql$,
  null, null,
  'free_delivery_threshold_paise < delivery_charge_threshold_paise is rejected');

-- 9. Negative bill counter is rejected.
SELECT throws_ok(
  $sql$INSERT INTO bill_counters (fy, last_no) VALUES ('0000-00', -1)$sql$,
  null, null,
  'negative bill counter last_no is rejected');

SELECT * FROM finish();
ROLLBACK;

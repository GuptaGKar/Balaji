-- pgTAP: RLS customer isolation tests
-- Verifies customer_a sees only own data, not customer_b's.

BEGIN;
SELECT plan(12);

-- Fixtures: two customers with orders
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        'cccccccc-0000-0000-0000-000000000001',
        'authenticated', 'authenticated',
        'cust-a@test.invalid', 'x', now(), now(), now()),
       ('00000000-0000-0000-0000-000000000000',
        'cccccccc-0000-0000-0000-000000000002',
        'authenticated', 'authenticated',
        'cust-b@test.invalid', 'x', now(), now(), now());

INSERT INTO profiles (id, role, display_name)
VALUES ('cccccccc-0000-0000-0000-000000000001', 'customer', 'Customer A'),
       ('cccccccc-0000-0000-0000-000000000002', 'customer', 'Customer B');

INSERT INTO orders (public_order_number, customer_id, address_snapshot)
VALUES ('CUST-A-ORDER-1', 'cccccccc-0000-0000-0000-000000000001', '{}'::jsonb),
       ('CUST-B-ORDER-1', 'cccccccc-0000-0000-0000-000000000002', '{}'::jsonb);

-- Test as customer_a
SET LOCAL request.jwt.claims.sub = 'cccccccc-0000-0000-0000-000000000001';

-- 1. customer_a reads own profile
SELECT lives_ok(
  $sql$SELECT 1 FROM profiles WHERE id = 'cccccccc-0000-0000-0000-000000000001'$sql$,
  'customer_a reads own profile'
);

-- 2. customer_a CANNOT read customer_b profile
SELECT throws_ok(
  $sql$SELECT 1 FROM profiles WHERE id = 'cccccccc-0000-0000-0000-000000000002'$sql$,
  null, null,
  'customer_a cannot read customer_b profile'
);

-- 3. customer_a reads own order
SELECT lives_ok(
  $sql$SELECT 1 FROM orders WHERE public_order_number = 'CUST-A-ORDER-1'$sql$,
  'customer_a reads own order'
);

-- 4. customer_a CANNOT read customer_b order
SELECT throws_ok(
  $sql$SELECT 1 FROM orders WHERE public_order_number = 'CUST-B-ORDER-1'$sql$,
  null, null,
  'customer_a cannot read customer_b order'
);

-- 5. customer_a reads own order_items
SELECT lives_ok(
  $sql$SELECT 1 FROM order_items WHERE order_id = (SELECT id FROM orders WHERE public_order_number = 'CUST-A-ORDER-1')$sql$,
  'customer_a reads own order_items'
);

-- 6. customer_a CANNOT read customer_b order_items
SELECT throws_ok(
  $sql$SELECT 1 FROM order_items WHERE order_id = (SELECT id FROM orders WHERE public_order_number = 'CUST-B-ORDER-1')$sql$,
  null, null,
  'customer_a cannot read customer_b order_items'
);

-- 7. customer_a reads own addresses
INSERT INTO addresses (customer_id, recipient_name, phone, address_lines, postal_code)
VALUES ('cccccccc-0000-0000-0000-000000000001', 'A', '1', 'a', '123');
SELECT lives_ok(
  $sql$SELECT 1 FROM addresses WHERE customer_id = 'cccccccc-0000-0000-0000-000000000001'$sql$,
  'customer_a reads own addresses'
);

-- 8. customer_a CANNOT read customer_b addresses
INSERT INTO addresses (customer_id, recipient_name, phone, address_lines, postal_code)
VALUES ('cccccccc-0000-0000-0000-000000000002', 'B', '2', 'b', '123');
SELECT throws_ok(
  $sql$SELECT 1 FROM addresses WHERE customer_id = 'cccccccc-0000-0000-0000-000000000002'$sql$,
  null, null,
  'customer_a cannot read customer_b addresses'
);

-- 9. customer_a CANNOT read audit_logs
SELECT throws_ok(
  $sql$SELECT 1 FROM audit_logs LIMIT 1$sql$,
  null, null,
  'customer_a cannot read audit_logs'
);

-- 10. customer_a CANNOT read store_settings (only manager)
SELECT throws_ok(
  $sql$SELECT 1 FROM store_settings LIMIT 1$sql$,
  null, null,
  'customer_a cannot read store_settings'
);

-- 11. customer_a CANNOT INSERT into orders directly (only via RPC)
SELECT throws_ok(
  $sql$INSERT INTO orders (public_order_number, customer_id, address_snapshot) VALUES ('TEST', 'cccccccc-0000-0000-0000-000000000001', '{}'::jsonb)$sql$,
  null, null,
  'customer_a cannot directly INSERT orders'
);

-- 12. customer_a CAN read catalog_products view
SELECT lives_ok(
  $sql$SELECT 1 FROM catalog_products LIMIT 1$sql$,
  'customer_a reads catalog_products view'
);

RESET request.jwt.claims.sub;
SELECT * FROM finish();
ROLLBACK;
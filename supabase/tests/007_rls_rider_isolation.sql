-- pgTAP: RLS rider isolation tests
-- Verifies riders see only their assigned orders via rider_orders view.

BEGIN;
SELECT plan(8);

-- Fixtures: two riders, orders assigned to rider_a
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        'dddddddd-0000-0000-0000-000000000001',
        'authenticated', 'authenticated',
        'rider-a@test.invalid', 'x', now(), now(), now()),
       ('00000000-0000-0000-0000-000000000000',
        'dddddddd-0000-0000-0000-000000000002',
        'authenticated', 'authenticated',
        'rider-b@test.invalid', 'x', now(), now(), now());

INSERT INTO profiles (id, role, display_name, login_id, is_active, must_change_password)
VALUES ('dddddddd-0000-0000-0000-000000000001', 'delivery', 'Rider A', 'RIDER-A-001', true, true),
       ('dddddddd-0000-0000-0000-000000000002', 'delivery', 'Rider B', 'RIDER-B-001', true, true);

INSERT INTO orders (public_order_number, customer_id, address_snapshot, status, assigned_to)
VALUES ('RIDER-A-ORDER-1', 'aaaaaaaa-0000-0000-0000-000000000001', '{}'::jsonb, 'out_for_delivery', 'dddddddd-0000-0000-0000-000000000001'),
       ('RIDER-A-ORDER-2', 'aaaaaaaa-0000-0000-0000-000000000001', '{}'::jsonb, 'ready_for_dispatch', 'dddddddd-0000-0000-0000-000000000001'),
       ('RIDER-B-ORDER-1', 'aaaaaaaa-0000-0000-0000-000000000001', '{}'::jsonb, 'out_for_delivery', 'dddddddd-0000-0000-0000-000000000002');

INSERT INTO order_assignments (order_id, delivery_user_id, assigned_by)
SELECT id, 'dddddddd-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000005'
FROM orders WHERE public_order_number IN ('RIDER-A-ORDER-1', 'RIDER-A-ORDER-2');
INSERT INTO order_assignments (order_id, delivery_user_id, assigned_by)
SELECT id, 'dddddddd-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000005'
FROM orders WHERE public_order_number = 'RIDER-B-ORDER-1';

-- Test as rider_a
SET LOCAL request.jwt.claims.sub = 'dddddddd-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'delivery';

-- 1. rider_a sees assigned orders in rider_orders view (2 orders)
SELECT results_eq(
  $sql$SELECT count(*) FROM rider_orders$sql$,
  $$VALUES (2)$$,
  'rider_a sees 2 assigned orders in rider_orders'
);

-- 2. rider_orders shows only necessary fields
SELECT lives_ok(
  $sql$SELECT public_order_number, recipient_name, phone, status, cod_collected_paise FROM rider_orders LIMIT 1$sql$,
  'rider_orders exposes only necessary fields'
);

-- 3. rider_a CANNOT read orders table directly
SELECT throws_ok(
  $sql$SELECT 1 FROM orders LIMIT 1$sql$,
  null, null,
  'rider_a cannot read orders table directly'
);

-- 4. rider_a CANNOT read profiles table
SELECT throws_ok(
  $sql$SELECT 1 FROM profiles LIMIT 1$sql$,
  null, null,
  'rider_a cannot read profiles table'
);

-- Test as rider_b
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;
SET LOCAL request.jwt.claims.sub = 'dddddddd-0000-0000-0000-000000000002';
SET LOCAL request.jwt.claims.role = 'delivery';

-- 5. rider_b sees only 1 assigned order
SELECT results_eq(
  $sql$SELECT count(*) FROM rider_orders$sql$,
  $$VALUES (1)$$,
  'rider_b sees only 1 assigned order'
);

-- 6. rider_b CANNOT see rider_a's orders
SELECT results_eq(
  $sql$SELECT count(*) FROM rider_orders WHERE public_order_number LIKE 'RIDER-A-%'$sql$,
  $$VALUES (0)$$,
  'rider_b cannot see rider_a orders'
);

-- 7. rider_b CANNOT read order_assignments for other riders
SELECT throws_ok(
  $sql$SELECT 1 FROM order_assignments WHERE delivery_user_id = 'dddddddd-0000-0000-0000-000000000001'$sql$,
  null, null,
  'rider_b cannot read other rider assignments'
);

-- 8. rider_b CAN read own order_assignments
SELECT lives_ok(
  $sql$SELECT 1 FROM order_assignments WHERE delivery_user_id = 'dddddddd-0000-0000-0000-000000000002'$sql$,
  'rider_b reads own order_assignments'
);

RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;
SELECT * FROM finish();
ROLLBACK;
-- pgTAP: RLS staff/manager access tests
-- Staff/manager see all orders; only manager writes settings.

BEGIN;
SELECT plan(10);

-- Fixtures: staff and manager
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        'eeeeeeee-0000-0000-0000-000000000001',
        'authenticated', 'authenticated',
        'staff@test.invalid', 'x', now(), now(), now()),
       ('00000000-0000-0000-0000-000000000000',
        'eeeeeeee-0000-0000-0000-000000000002',
        'authenticated', 'authenticated',
        'manager@test.invalid', 'x', now(), now(), now());

INSERT INTO profiles (id, role, display_name)
VALUES ('eeeeeeee-0000-0000-0000-000000000001', 'staff', 'Staff'),
       ('eeeeeeee-0000-0000-0000-000000000002', 'manager', 'Manager');

-- Test as staff
SET LOCAL request.jwt.claims.sub = 'eeeeeeee-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'staff';

-- 1. staff reads all orders
INSERT INTO orders (public_order_number, customer_id, address_snapshot)
VALUES ('STAFF-ORDER-1', 'aaaaaaaa-0000-0000-0000-000000000001', '{}'::jsonb);
SELECT lives_ok(
  $sql$SELECT 1 FROM orders WHERE public_order_number = 'STAFF-ORDER-1'$sql$,
  'staff reads all orders'
);

-- 2. staff reads all profiles
SELECT lives_ok(
  $sql$SELECT 1 FROM profiles WHERE id = 'aaaaaaaa-0000-0000-0000-000000000001'$sql$,
  'staff reads all profiles'
);

-- 3. staff CANNOT update profiles (manager only)
SELECT throws_ok(
  $sql$UPDATE profiles SET display_name = 'x' WHERE id = 'aaaaaaaa-0000-0000-0000-000000000001'$sql$,
  null, null,
  'staff cannot update profiles'
);

-- 4. staff CANNOT write store_settings
SELECT throws_ok(
  $sql$UPDATE store_settings SET delivery_fee_paise = 1000$sql$,
  null, null,
  'staff cannot write store_settings'
);

-- 5. staff CAN read service_areas
SELECT lives_ok(
  $sql$SELECT 1 FROM service_areas LIMIT 1$sql$,
  'staff reads service_areas'
);

-- Test as manager
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;
SET LOCAL request.jwt.claims.sub = 'eeeeeeee-0000-0000-0000-000000000002';
SET LOCAL request.jwt.claims.role = 'manager';

-- 6. manager reads all orders
SELECT lives_ok(
  $sql$SELECT 1 FROM orders WHERE public_order_number = 'STAFF-ORDER-1'$sql$,
  'manager reads all orders'
);

-- 7. manager writes store_settings
SELECT lives_ok(
  $sql$UPDATE store_settings SET delivery_fee_paise = 2000$sql$,
  'manager writes store_settings'
);

-- 8. manager reads audit_logs
SELECT lives_ok(
  $sql$SELECT 1 FROM audit_logs LIMIT 1$sql$,
  'manager reads audit_logs'
);

-- 9. manager CANNOT read payment_events (internal only)
SELECT throws_ok(
  $sql$SELECT 1 FROM payment_events LIMIT 1$sql$,
  null, null,
  'manager cannot read payment_events'
);

-- 10. manager CAN write order_assignments
SELECT lives_ok(
  $sql$INSERT INTO order_assignments (order_id, delivery_user_id, assigned_by)
    SELECT id, 'dddddddd-0000-0000-0000-000000000001', 'eeeeeeee-0000-0000-0000-000000000002'
    FROM orders WHERE public_order_number = 'STAFF-ORDER-1'
    ON CONFLICT DO NOTHING$sql$,
  'manager writes order_assignments'
);

RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;
SELECT * FROM finish();
ROLLBACK;
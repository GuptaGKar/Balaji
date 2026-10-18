-- pgTAP: audit trigger append-only tests
-- Verifies audit_logs cannot be updated/deleted, triggers fire on tracked tables.

BEGIN;
SELECT plan(7);

-- 1. audit_logs INSERT works (via trigger on store_settings)
UPDATE store_settings SET delivery_fee_paise = 5000;
SELECT lives_ok(
  $sql$SELECT 1 FROM audit_logs WHERE entity_type = 'store_settings' AND action = 'UPDATE'$sql$,
  'audit trigger fires on store_settings UPDATE'
);

-- 2. audit_logs UPDATE is rejected
SELECT throws_ok(
  $sql$UPDATE audit_logs SET action = 'TAMPERED' WHERE entity_type = 'store_settings'$sql$,
  null, null,
  'audit_logs UPDATE rejected'
);

-- 3. audit_logs DELETE is rejected
SELECT throws_ok(
  $sql$DELETE FROM audit_logs WHERE entity_type = 'store_settings'$sql$,
  null, null,
  'audit_logs DELETE rejected'
);

-- 4. products price_paise UPDATE triggers audit
UPDATE products SET price_paise = 9999 WHERE sku = 'TOOR-DAL-1KG';
SELECT lives_ok(
  $sql$SELECT 1 FROM audit_logs WHERE entity_type = 'products' AND action = 'UPDATE'$sql$,
  'audit trigger fires on products price_paise UPDATE'
);

-- 5. service_areas INSERT triggers audit
INSERT INTO service_areas (kind, value) VALUES ('pincode', '888888');
SELECT lives_ok(
  $sql$SELECT 1 FROM audit_logs WHERE entity_type = 'service_areas' AND action = 'INSERT'$sql$,
  'audit trigger fires on service_areas INSERT'
);

-- 6. profiles role UPDATE triggers audit
UPDATE profiles SET role = 'staff' WHERE id = 'eeeeeeee-0000-0000-0000-000000000001';
SELECT lives_ok(
  $sql$SELECT 1 FROM audit_logs WHERE entity_type = 'profiles' AND action = 'UPDATE'$sql$,
  'audit trigger fires on profiles role UPDATE'
);

-- 7. order_assignments INSERT triggers audit
INSERT INTO order_assignments (order_id, delivery_user_id, assigned_by)
SELECT id, 'dddddddd-0000-0000-0000-000000000001', 'eeeeeeee-0000-0000-0000-000000000002'
FROM orders WHERE public_order_number = 'STAFF-ORDER-1'
ON CONFLICT DO NOTHING;
SELECT lives_ok(
  $sql$SELECT 1 FROM audit_logs WHERE entity_type = 'order_assignments' AND action = 'INSERT'$sql$,
  'audit trigger fires on order_assignments INSERT'
);

SELECT * FROM finish();
ROLLBACK;
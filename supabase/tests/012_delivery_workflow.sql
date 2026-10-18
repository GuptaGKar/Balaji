-- pgTAP: rider provisioning & workflow tests (IMPLEMENTATION_PLAN.md §5.4, §5.7)
-- Tests: create_rider, reset_rider_password, set_rider_active, assign_order, 
--        rider_update_status, transition_order, remove_order_item

BEGIN;
SELECT plan(28);

-- ========== RIDER PROVISIONING ==========
-- Fixtures: manager user
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        'ffffffff-0000-0000-0000-000000000001',
        'authenticated', 'authenticated',
        'manager@test.invalid', 'x', now(), now(), now());
INSERT INTO profiles (id, role, display_name) VALUES ('ffffffff-0000-0000-0000-000000000001', 'manager', 'Manager');

-- 1. create_rider succeeds
SELECT lives_ok(
  $sql$SELECT create_rider('RIDER-NEW-001', 'New Rider', '9999999999')$sql$,
  'create_rider succeeds');

-- 2. Duplicate login_id rejected
SELECT throws_ok(
  $sql$SELECT create_rider('RIDER-NEW-001', 'Another Rider', '8888888888')$sql$,
  null, null,
  'duplicate login_id rejected');

-- 3. Invalid login_id format rejected
SELECT throws_ok(
  $sql$SELECT create_rider('invalid-format', 'Bad Rider', '7777777777')$sql$,
  null, null,
  'invalid login_id format rejected');

-- 4. Non-manager cannot create rider
SET LOCAL request.jwt.claims.sub = 'cccccccc-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'customer';
SELECT throws_ok(
  $sql$SELECT create_rider('RIDER-TEST-001', 'Test', '1111111111')$sql$,
  null, null,
  'non-manager cannot create rider');
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;

-- 5. reset_rider_password generates new OTP
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        'gggggggg-0000-0000-0000-000000000001',
        'authenticated', 'authenticated',
        'rider@test.invalid', 'x', now(), now(), now());
INSERT INTO profiles (id, role, display_name, login_id, is_active, must_change_password)
VALUES ('gggggggg-0000-0000-0000-000000000001', 'delivery', 'Test Rider', 'RIDER-TEST-001', true, true);

SET LOCAL request.jwt.claims.sub = 'ffffffff-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'manager';
SELECT lives_ok(
  $sql$SELECT reset_rider_password('gggggggg-0000-0000-0000-000000000001')$sql$,
  'reset_rider_password succeeds');
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;

-- 6. set_rider_active toggles
SELECT lives_ok(
  $sql$SELECT set_rider_active('gggggggg-0000-0000-0000-000000000001', false)$sql$,
  'set_rider_active(false) succeeds');
SELECT lives_ok(
  $sql$SELECT set_rider_active('gggggggg-0000-0000-0000-000000000001', true)$sql$,
  'set_rider_active(true) succeeds');

-- ========== ASSIGN ORDER ==========
-- Fixtures: order in ready_for_dispatch, active rider
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        'hhhhhhhh-0000-0000-0000-000000000001',
        'authenticated', 'authenticated',
        'rider2@test.invalid', 'x', now(), now(), now());
INSERT INTO profiles (id, role, display_name, login_id, is_active, must_change_password)
VALUES ('hhhhhhhh-0000-0000-0000-000000000001', 'delivery', 'Rider Two', 'RIDER-TWO-001', true, false);

INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        'iiiiiiii-0000-0000-0000-000000000001',
        'authenticated', 'authenticated',
        'customer@test.invalid', 'x', now(), now(), now());
INSERT INTO profiles (id, role, display_name) VALUES ('iiiiiiii-0000-0000-0000-000000000001', 'customer', 'Customer');

INSERT INTO orders (public_order_number, customer_id, address_snapshot, status)
VALUES ('ASSIGN-TEST-001', 'iiiiiiii-0000-0000-0000-000000000001', '{}'::jsonb, 'ready_for_dispatch');

-- 7. assign_order succeeds for staff
SET LOCAL request.jwt.claims.sub = 'ffffffff-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'staff';
SELECT lives_ok(
  $sql$SELECT assign_order((SELECT id FROM orders WHERE public_order_number='ASSIGN-TEST-001'), 'hhhhhhhh-0000-0000-0000-000000000001')$sql$,
  'assign_order succeeds for staff');
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;

-- 8. assign_order fails for inactive rider
UPDATE profiles SET is_active = false WHERE id = 'hhhhhhhh-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.sub = 'ffffffff-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'staff';
SELECT throws_ok(
  $sql$SELECT assign_order((SELECT id FROM orders WHERE public_order_number='ASSIGN-TEST-001'), 'hhhhhhhh-0000-0000-0000-000000000001')$sql$,
  null, null,
  'assign_order fails for inactive rider');
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;
UPDATE profiles SET is_active = true WHERE id = 'hhhhhhhh-0000-0000-0000-000000000001';

-- ========== RIDER UPDATE STATUS ==========
-- 9. rider_update_status: valid transition ready_for_dispatch -> out_for_delivery
INSERT INTO orders (public_order_number, customer_id, address_snapshot, status, assigned_to)
VALUES ('RIDER-STATUS-001', 'iiiiiiii-0000-0000-0000-000000000001', '{}'::jsonb, 'ready_for_dispatch', 'hhhhhhhh-0000-0000-0000-000000000001');
INSERT INTO order_assignments (order_id, delivery_user_id, assigned_by)
SELECT id, 'hhhhhhhh-0000-0000-0000-000000000001', 'ffffffff-0000-0000-0000-000000000001'
FROM orders WHERE public_order_number = 'RIDER-STATUS-001';

SET LOCAL request.jwt.claims.sub = 'hhhhhhhh-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'delivery';
SELECT lives_ok(
  $sql$SELECT rider_update_status((SELECT id FROM orders WHERE public_order_number='RIDER-STATUS-001'), 'out_for_delivery', 'Starting delivery')$sql$,
  'rider_update_status: ready_for_dispatch -> out_for_delivery succeeds');
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;

-- 10. rider_update_status: valid transition out_for_delivery -> delivered
SET LOCAL request.jwt.claims.sub = 'hhhhhhhh-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'delivery';
SELECT lives_ok(
  $sql$SELECT rider_update_status((SELECT id FROM orders WHERE public_order_number='RIDER-STATUS-001'), 'delivered', 'Delivered to customer')$sql$,
  'rider_update_status: out_for_delivery -> delivered succeeds');
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;

-- 11. rider_update_status: invalid transition rejected
INSERT INTO orders (public_order_number, customer_id, address_snapshot, status, assigned_to)
VALUES ('RIDER-STATUS-002', 'iiiiiiii-0000-0000-0000-000000000001', '{}'::jsonb, 'ready_for_dispatch', 'hhhhhhhh-0000-0000-0000-000000000001');
INSERT INTO order_assignments (order_id, delivery_user_id, assigned_by)
SELECT id, 'hhhhhhhh-0000-0000-0000-000000000001', 'ffffffff-0000-0000-0000-000000000001'
FROM orders WHERE public_order_number = 'RIDER-STATUS-002';

SET LOCAL request.jwt.claims.sub = 'hhhhhhhh-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'delivery';
SELECT throws_ok(
  $sql$SELECT rider_update_status((SELECT id FROM orders WHERE public_order_number='RIDER-STATUS-002'), 'preparing')$sql$,
  null, null,
  'rider_update_status: invalid transition rejected');
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;

-- 12. rider_update_status: only assigned rider can update
SET LOCAL request.jwt.claims.sub = 'ffffffff-0000-0000-0000-000000000001'; -- manager
SET LOCAL request.jwt.claims.role = 'manager';
SELECT throws_ok(
  $sql$SELECT rider_update_status((SELECT id FROM orders WHERE public_order_number='RIDER-STATUS-001'), 'delivered')$sql$,
  null, null,
  'rider_update_status: non-rider rejected');
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;

-- ========== TRANSITION_ORDER (staff/manager) ==========
-- 13. transition_order: staff can accept
INSERT INTO orders (public_order_number, customer_id, address_snapshot, status)
VALUES ('TRANSITION-001', 'iiiiiiii-0000-0000-0000-000000000001', '{}'::jsonb, 'pending_review');
SET LOCAL request.jwt.claims.sub = 'ffffffff-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'staff';
SELECT lives_ok(
  $sql$SELECT transition_order((SELECT id FROM orders WHERE public_order_number='TRANSITION-001'), 'accepted', 'Order accepted')$sql$,
  'transition_order: staff can accept');
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;

-- 14. transition_order: reject restores stock
INSERT INTO products (sku, name, quantity_type, price_paise, price_basis_qty, unit_label, step_qty, min_qty, stock_qty)
VALUES ('TEST-TRANS-STOCK', 'Test Stock Product', 'PACKED', 10000, 1, 'pack', 1, 1, 5);
INSERT INTO orders (public_order_number, customer_id, address_snapshot, status)
VALUES ('TRANSITION-002', 'iiiiiiii-0000-0000-0000-000000000001', '{}'::jsonb, 'pending_review');
INSERT INTO order_items (order_id, product_id, sku_snapshot, name_snapshot, quantity_type_snapshot, unit_label_snapshot, price_basis_qty_snapshot, qty_base, unit_price_paise, line_total_paise)
SELECT id, (SELECT id FROM products WHERE sku='TEST-TRANS-STOCK'), 'TEST-TRANS-STOCK', 'Test Stock Product', 'PACKED', 'pack', 1, 2, 10000, 20000
FROM orders WHERE public_order_number = 'TRANSITION-002';

SET LOCAL request.jwt.claims.sub = 'ffffffff-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'staff';
SELECT lives_ok(
  $sql$SELECT transition_order((SELECT id FROM orders WHERE public_order_number='TRANSITION-002'), 'rejected', 'Customer unavailable')$sql$,
  'transition_order: reject restores stock');
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;

-- 15. transition_order: invalid transition rejected
SET LOCAL request.jwt.claims.sub = 'ffffffff-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'staff';
SELECT throws_ok(
  $sql$SELECT transition_order((SELECT id FROM orders WHERE public_order_number='TRANSITION-001'), 'delivered')$sql$,
  null, null,
  'transition_order: invalid transition rejected');
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;

-- 16. transition_order: only staff/manager can call
SET LOCAL request.jwt.claims.sub = 'hhhhhhhh-0000-0000-0000-000000000001'; -- rider
SET LOCAL request.jwt.claims.role = 'delivery';
SELECT throws_ok(
  $sql$SELECT transition_order((SELECT id FROM orders WHERE public_order_number='TRANSITION-001'), 'accepted')$sql$,
  null, null,
  'transition_order: non-staff rejected');
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;

-- ========== REMOVE_ORDER_ITEM ==========
-- 17. remove_order_item: staff can remove before dispatch
INSERT INTO products (sku, name, quantity_type, price_paise, price_basis_qty, unit_label, step_qty, min_qty, stock_qty)
VALUES ('TEST-REMOVE-ITEM', 'Removable Item', 'PACKED', 5000, 1, 'pack', 1, 1, 10);
INSERT INTO orders (public_order_number, customer_id, address_snapshot, status)
VALUES ('REMOVE-ITEM-001', 'iiiiiiii-0000-0000-0000-000000000001', '{}'::jsonb, 'accepted');
INSERT INTO order_items (order_id, product_id, sku_snapshot, name_snapshot, quantity_type_snapshot, unit_label_snapshot, price_basis_qty_snapshot, qty_base, unit_price_paise, line_total_paise)
SELECT id, (SELECT id FROM products WHERE sku='TEST-REMOVE-ITEM'), 'TEST-REMOVE-ITEM', 'Removable Item', 'PACKED', 'pack', 1, 3, 5000, 15000
FROM orders WHERE public_order_number = 'REMOVE-ITEM-001';

SET LOCAL request.jwt.claims.sub = 'ffffffff-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'staff';
SELECT lives_ok(
  $sql$SELECT remove_order_item((SELECT id FROM orders WHERE public_order_number='REMOVE-ITEM-001'), (SELECT id FROM order_items WHERE order_id=(SELECT id FROM orders WHERE public_order_number='REMOVE-ITEM-001')), 'Item unavailable')$sql$,
  'remove_order_item succeeds');
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;

-- 18. remove_order_item: restores stock
SELECT results_eq(
  $sql$SELECT stock_qty FROM products WHERE sku='TEST-REMOVE-ITEM'$sql$,
  $$VALUES (13)$$,
  'remove_order_item: stock restored (was 10, ordered 3, now 13)');

-- 19. remove_order_item: fails after dispatch
INSERT INTO orders (public_order_number, customer_id, address_snapshot, status)
VALUES ('REMOVE-ITEM-002', 'iiiiiiii-0000-0000-0000-000000000001', '{}'::jsonb, 'ready_for_dispatch');
INSERT INTO order_items (order_id, product_id, sku_snapshot, name_snapshot, quantity_type_snapshot, unit_label_snapshot, price_basis_qty_snapshot, qty_base, unit_price_paise, line_total_paise)
SELECT id, (SELECT id FROM products WHERE sku='TEST-REMOVE-ITEM'), 'TEST-REMOVE-ITEM', 'Removable Item', 'PACKED', 'pack', 1, 1, 5000, 5000
FROM orders WHERE public_order_number = 'REMOVE-ITEM-002';

SET LOCAL request.jwt.claims.sub = 'ffffffff-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'staff';
SELECT throws_ok(
  $sql$SELECT remove_order_item((SELECT id FROM orders WHERE public_order_number='REMOVE-ITEM-002'), (SELECT id FROM order_items WHERE order_id=(SELECT id FROM orders WHERE public_order_number='REMOVE-ITEM-002')), 'Too late')$sql$,
  null, null,
  'remove_order_item: fails after dispatch');
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;

-- 20. remove_order_item: non-staff rejected
SET LOCAL request.jwt.claims.sub = 'cccccccc-0000-0000-0000-000000000001'; -- customer
SET LOCAL request.jwt.claims.role = 'customer';
SELECT throws_ok(
  $sql$SELECT remove_order_item((SELECT id FROM orders WHERE public_order_number='REMOVE-ITEM-001'), (SELECT id FROM order_items WHERE order_id=(SELECT id FROM orders WHERE public_order_number='REMOVE-ITEM-001')), 'Test')$sql$,
  null, null,
  'remove_order_item: non-staff rejected');
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;

-- ========== AUDIT LOG VERIFICATION ==========
-- 21. create_rider creates audit log
SELECT results_eq(
  $sql$SELECT count(*) FROM audit_logs WHERE action='CREATE_RIDER'$sql$,
  $$VALUES (1)$$,
  'create_rider creates audit log');

-- 22. reset_rider_password creates audit log
SELECT results_eq(
  $sql$SELECT count(*) FROM audit_logs WHERE action='RESET_RIDER_PASSWORD'$sql$,
  $$VALUES (1)$$,
  'reset_rider_password creates audit log');

-- 23. set_rider_active creates audit log
SELECT results_eq(
  $sql$SELECT count(*) FROM audit_logs WHERE action='SET_RIDER_ACTIVE'$sql$,
  $$VALUES (2)$$,
  'set_rider_active creates audit log');

-- 24. assign_order creates audit log
SELECT results_eq(
  $sql$SELECT count(*) FROM audit_logs WHERE action='ASSIGN_ORDER'$sql$,
  $$VALUES (1)$$,
  'assign_order creates audit log');

-- 25. remove_order_item creates audit log
SELECT results_eq(
  $sql$SELECT count(*) FROM audit_logs WHERE action='REMOVE_ORDER_ITEM'$sql$,
  $$VALUES (1)$$,
  'remove_order_item creates audit log');

-- ========== RLS ISOLATION ==========
-- 26. Rider cannot see other rider's orders
SET LOCAL request.jwt.claims.sub = 'hhhhhhhh-0000-0000-0000-000000000001'; -- rider A
SET LOCAL request.jwt.claims.role = 'delivery';
SELECT results_eq(
  $sql$SELECT count(*) FROM get_rider_orders()$sql$,
  $$VALUES (2)$$,
  'rider sees only own assigned orders (RIDER-STATUS-001 delivered, RIDER-STATUS-002 ready)');
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;

-- 27. Customer cannot access rider_orders
SET LOCAL request.jwt.claims.sub = 'iiiiiiii-0000-0000-0000-000000000001'; -- customer
SET LOCAL request.jwt.claims.role = 'customer';
SELECT throws_ok(
  $sql$SELECT * FROM get_rider_orders()$sql$,
  null, null,
  'customer cannot access rider_orders');
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;

-- 28. Staff cannot call rider_update_status
SET LOCAL request.jwt.claims.sub = 'ffffffff-0000-0000-0000-000000000001'; -- staff
SET LOCAL request.jwt.claims.role = 'staff';
SELECT throws_ok(
  $sql$SELECT rider_update_status((SELECT id FROM orders WHERE public_order_number='RIDER-STATUS-001'), 'delivered')$sql$,
  null, null,
  'staff cannot call rider_update_status');
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;

SELECT * FROM finish();
ROLLBACK;
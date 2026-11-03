-- pgTAP: rider provisioning & workflow tests (IMPLEMENTATION_PLAN.md §5.4, §5.7)
-- Tests: create_rider, reset_rider_password, set_rider_active, assign_order, 
--        rider_update_status, transition_order, remove_order_item

BEGIN;
SELECT plan(29);
\ir helpers/role_fixtures.inc
SELECT pg_temp.make_profile('012f0000-0000-0000-0000-000000000001','staff','Workflow Staff');

-- ========== RIDER PROVISIONING ==========
-- Fixtures: manager user
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        'ffffffff-0000-0000-0000-000000000001',
        'authenticated', 'authenticated',
        'manager@test.invalid', 'x', now(), now(), now());
INSERT INTO profiles (id, role, display_name) VALUES ('ffffffff-0000-0000-0000-000000000001', 'manager', 'Manager')
ON CONFLICT (id) DO UPDATE SET role = EXCLUDED.role, display_name = EXCLUDED.display_name;

SET LOCAL ROLE authenticated;
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = 'ffffffff-0000-0000-0000-000000000001';
-- SQL provisioning is explicitly unsupported; the web Admin API creates real accounts.
SELECT is(create_rider('RIDER-NEW-001', 'New Rider', '9999999999')->>'error', 'NOT_SUPPORTED', 'SQL provisioning never returns fake success');

-- 2. Duplicate login_id rejected
SELECT is((create_rider('RIDER-NEW-001', 'Another Rider', '8888888888'))->>'error', 'NOT_SUPPORTED', 'duplicate login_id rejected');

-- 3. Invalid login_id format rejected
SELECT is((create_rider('invalid-format', 'Bad Rider', '7777777777'))->>'error', 'NOT_SUPPORTED', 'invalid login_id format rejected');

-- 4. Non-manager cannot create rider
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = 'cccccccc-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'customer';
SELECT is((create_rider('RIDER-TEST-001', 'Test', '1111111111'))->>'error', 'FORBIDDEN', 'non-manager cannot create rider');
RESET request.jwt.claim.sub;
RESET request.jwt.claims.role;
RESET ROLE;

RESET ROLE;
-- 5. Reset is only supported through the Auth Admin API.
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        '77777777-0000-0000-0000-000000000001',
        'authenticated', 'authenticated',
        'rider@test.invalid', 'x', now(), now(), now());
INSERT INTO profiles (id, role, display_name, login_id, is_active, must_change_password)
VALUES ('77777777-0000-0000-0000-000000000001', 'delivery', 'Test Rider', 'RIDER-TEST-001', true, true)
ON CONFLICT (id) DO UPDATE SET role = EXCLUDED.role, display_name = EXCLUDED.display_name, login_id = EXCLUDED.login_id, is_active = EXCLUDED.is_active, must_change_password = EXCLUDED.must_change_password;

SET LOCAL ROLE authenticated;
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = 'ffffffff-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'manager';
SELECT is(reset_rider_password('77777777-0000-0000-0000-000000000001')->>'error', 'NOT_SUPPORTED', 'SQL reset never returns fake credentials');
RESET request.jwt.claim.sub;
RESET request.jwt.claims.role;
RESET ROLE;

-- 6. set_rider_active toggles
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = 'ffffffff-0000-0000-0000-000000000001';
SELECT is((set_rider_active('77777777-0000-0000-0000-000000000001', false))->>'success', 'true', 'set_rider_active(false) succeeds');
SELECT is((set_rider_active('77777777-0000-0000-0000-000000000001', true))->>'success', 'true', 'set_rider_active(true) succeeds');

RESET ROLE;
-- ========== ASSIGN ORDER ==========
-- Fixtures: order in ready_for_dispatch, active rider
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        '88888888-0000-0000-0000-000000000001',
        'authenticated', 'authenticated',
        'rider2@test.invalid', 'x', now(), now(), now());
INSERT INTO profiles (id, role, display_name, login_id, is_active, must_change_password)
VALUES ('88888888-0000-0000-0000-000000000001', 'delivery', 'Rider Two', 'RIDER-TWO-001', true, false)
ON CONFLICT (id) DO UPDATE SET role = EXCLUDED.role, display_name = EXCLUDED.display_name, login_id = EXCLUDED.login_id, is_active = EXCLUDED.is_active, must_change_password = EXCLUDED.must_change_password;

INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        '99999999-0000-0000-0000-000000000001',
        'authenticated', 'authenticated',
        'customer@test.invalid', 'x', now(), now(), now());
INSERT INTO profiles (id, role, display_name) VALUES ('99999999-0000-0000-0000-000000000001', 'customer', 'Customer')
ON CONFLICT (id) DO UPDATE SET role = EXCLUDED.role, display_name = EXCLUDED.display_name;

INSERT INTO orders (public_order_number, customer_id, address_snapshot, status)
VALUES ('ASSIGN-TEST-001', '99999999-0000-0000-0000-000000000001', '{}'::jsonb, 'ready_for_dispatch');

SET LOCAL ROLE authenticated;
-- 7. assign_order succeeds for manager
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '012f0000-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'staff';
SELECT is((assign_order((SELECT id FROM orders WHERE public_order_number='ASSIGN-TEST-001'), '88888888-0000-0000-0000-000000000001'))->>'success', 'true', 'assign_order succeeds for staff');
RESET request.jwt.claim.sub;
RESET request.jwt.claims.role;
RESET ROLE;

RESET ROLE;
-- 8. assign_order fails for inactive rider
UPDATE profiles SET is_active = false WHERE id = '88888888-0000-0000-0000-000000000001';
SET LOCAL ROLE authenticated;
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '012f0000-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'staff';
SELECT is((assign_order((SELECT id FROM orders WHERE public_order_number='ASSIGN-TEST-001'), '88888888-0000-0000-0000-000000000001'))->>'error', 'NOT_FOUND', 'assign_order fails for inactive rider');
RESET request.jwt.claim.sub;
RESET request.jwt.claims.role;
RESET ROLE;
RESET ROLE;
UPDATE profiles SET is_active = true WHERE id = '88888888-0000-0000-0000-000000000001';

-- ========== RIDER UPDATE STATUS ==========
-- 9. rider_update_status: valid transition ready_for_dispatch -> out_for_delivery
INSERT INTO orders (public_order_number, customer_id, address_snapshot, status, assigned_to)
VALUES ('RIDER-STATUS-001', '99999999-0000-0000-0000-000000000001', '{}'::jsonb, 'ready_for_dispatch', '88888888-0000-0000-0000-000000000001');
INSERT INTO order_assignments (order_id, delivery_user_id, assigned_by)
SELECT id, '88888888-0000-0000-0000-000000000001', 'ffffffff-0000-0000-0000-000000000001'
FROM orders WHERE public_order_number = 'RIDER-STATUS-001';

-- Dispatch proof is mandatory before the order can leave the store.
-- Stored-object fixtures for the real proof transition gate.
INSERT INTO storage.objects(bucket_id,name) VALUES
 ('order-proofs','cccccccc-0000-0000-0000-000000000001/proof/dispatch.jpg'),
 ('order-proofs','88888888-0000-0000-0000-000000000001/proof/delivery.mp4');

INSERT INTO order_proofs (order_id, stage, kind, path)
SELECT id, 'dispatch', 'image', 'cccccccc-0000-0000-0000-000000000001/proof/dispatch.jpg'
FROM orders WHERE public_order_number = 'RIDER-STATUS-001'
ON CONFLICT (order_id, stage) DO NOTHING;

SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '88888888-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'delivery';
SELECT is((rider_update_status((SELECT id FROM orders WHERE public_order_number='RIDER-STATUS-001'), 'out_for_delivery', 'Starting delivery'))->>'ok', 'true', 'rider_update_status: ready_for_dispatch -> out_for_delivery succeeds');
RESET request.jwt.claim.sub;
RESET request.jwt.claims.role;
RESET ROLE;

-- 10. rider_update_status: valid transition out_for_delivery -> delivered
INSERT INTO order_proofs (order_id, stage, kind, path)
SELECT id, 'delivery', 'video', '88888888-0000-0000-0000-000000000001/proof/delivery.mp4'
FROM orders WHERE public_order_number = 'RIDER-STATUS-001'
ON CONFLICT (order_id, stage) DO NOTHING;

SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '88888888-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'delivery';
SELECT is((rider_update_status((SELECT id FROM orders WHERE public_order_number='RIDER-STATUS-001'), 'delivered', 'Delivered to customer'))->>'ok', 'true', 'rider_update_status: out_for_delivery -> delivered succeeds');
RESET request.jwt.claim.sub;
RESET request.jwt.claims.role;
RESET ROLE;

-- 11. rider_update_status: invalid transition rejected
INSERT INTO orders (public_order_number, customer_id, address_snapshot, status, assigned_to)
VALUES ('RIDER-STATUS-002', '99999999-0000-0000-0000-000000000001', '{}'::jsonb, 'ready_for_dispatch', '88888888-0000-0000-0000-000000000001');
INSERT INTO order_assignments (order_id, delivery_user_id, assigned_by)
SELECT id, '88888888-0000-0000-0000-000000000001', 'ffffffff-0000-0000-0000-000000000001'
FROM orders WHERE public_order_number = 'RIDER-STATUS-002';

SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '88888888-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'delivery';
SELECT is((rider_update_status((SELECT id FROM orders WHERE public_order_number='RIDER-STATUS-002'), 'preparing'))->>'error', 'INVALID_TRANSITION', 'rider_update_status: invalid transition rejected');
RESET request.jwt.claim.sub;
RESET request.jwt.claims.role;
RESET ROLE;

-- 12. rider_update_status: only assigned rider can update
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = 'ffffffff-0000-0000-0000-000000000001'; -- manager
SET LOCAL request.jwt.claims.role = 'manager';
SELECT is((rider_update_status((SELECT id FROM orders WHERE public_order_number='RIDER-STATUS-001'), 'delivered'))->>'error', 'FORBIDDEN', 'rider_update_status: non-rider rejected');
RESET request.jwt.claim.sub;
RESET request.jwt.claims.role;
RESET ROLE;

-- ========== TRANSITION_ORDER (staff/manager) ==========
-- 13. transition_order: staff can accept
INSERT INTO orders (public_order_number, customer_id, address_snapshot, status)
VALUES ('TRANSITION-001', '99999999-0000-0000-0000-000000000001', '{}'::jsonb, 'pending_review');
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '012f0000-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'staff';
SELECT is((transition_order((SELECT id FROM orders WHERE public_order_number='TRANSITION-001'), 'accepted', 'Order accepted'))->>'ok', 'true', 'transition_order: staff can accept');
RESET request.jwt.claim.sub;
RESET request.jwt.claims.role;
RESET ROLE;

-- 14. transition_order: reject restores stock
INSERT INTO products (sku, name, quantity_type, price_paise, price_basis_qty, unit_label, step_qty, min_qty, stock_qty)
VALUES ('TEST-TRANS-STOCK', 'Test Stock Product', 'PACKED', 10000, 1, 'pack', 1, 1, 5);
INSERT INTO orders (public_order_number, customer_id, address_snapshot, status)
VALUES ('TRANSITION-002', '99999999-0000-0000-0000-000000000001', '{}'::jsonb, 'pending_review');
INSERT INTO order_items (order_id, product_id, sku_snapshot, name_snapshot, quantity_type_snapshot, unit_label_snapshot, price_basis_qty_snapshot, qty_base, unit_price_paise, line_total_paise)
SELECT id, (SELECT id FROM products WHERE sku='TEST-TRANS-STOCK'), 'TEST-TRANS-STOCK', 'Test Stock Product', 'PACKED', 'pack', 1, 2, 10000, 20000
FROM orders WHERE public_order_number = 'TRANSITION-002';

SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '012f0000-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'staff';
SELECT is((transition_order((SELECT id FROM orders WHERE public_order_number='TRANSITION-002'), 'rejected', 'Customer unavailable'))->>'ok', 'true', 'transition_order: reject restores stock');
RESET request.jwt.claim.sub;
RESET request.jwt.claims.role;
RESET ROLE;

-- 15. transition_order: invalid transition rejected
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '012f0000-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'staff';
SELECT is((transition_order((SELECT id FROM orders WHERE public_order_number='TRANSITION-001'), 'delivered'))->>'error', 'INVALID_TRANSITION', 'transition_order: invalid transition rejected');
RESET request.jwt.claim.sub;
RESET request.jwt.claims.role;
RESET ROLE;

-- 16. transition_order: only staff/manager can call
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '88888888-0000-0000-0000-000000000001'; -- rider
SET LOCAL request.jwt.claims.role = 'delivery';
SELECT is((transition_order((SELECT id FROM orders WHERE public_order_number='TRANSITION-001'), 'accepted'))->>'error', 'FORBIDDEN', 'transition_order: non-staff rejected');
RESET request.jwt.claim.sub;
RESET request.jwt.claims.role;
RESET ROLE;

-- ========== REMOVE_ORDER_ITEM ==========
-- 17. remove_order_item: staff can remove before dispatch
INSERT INTO products (sku, name, quantity_type, price_paise, price_basis_qty, unit_label, step_qty, min_qty, stock_qty)
VALUES ('TEST-REMOVE-ITEM', 'Removable Item', 'PACKED', 5000, 1, 'pack', 1, 1, 10);
INSERT INTO orders (public_order_number, customer_id, address_snapshot, status)
VALUES ('REMOVE-ITEM-001', '99999999-0000-0000-0000-000000000001', '{}'::jsonb, 'accepted');
INSERT INTO order_items (order_id, product_id, sku_snapshot, name_snapshot, quantity_type_snapshot, unit_label_snapshot, price_basis_qty_snapshot, qty_base, unit_price_paise, line_total_paise)
SELECT id, (SELECT id FROM products WHERE sku='TEST-REMOVE-ITEM'), 'TEST-REMOVE-ITEM', 'Removable Item', 'PACKED', 'pack', 1, 3, 5000, 15000
FROM orders WHERE public_order_number = 'REMOVE-ITEM-001';

SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '012f0000-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'staff';
SELECT is((remove_order_item((SELECT id FROM orders WHERE public_order_number='REMOVE-ITEM-001'), (SELECT id FROM order_items WHERE order_id=(SELECT id FROM orders WHERE public_order_number='REMOVE-ITEM-001')), 'Item unavailable'))->>'success', 'true', 'remove_order_item succeeds');
RESET request.jwt.claim.sub;
RESET request.jwt.claims.role;
RESET ROLE;

-- 18. remove_order_item: restores stock
SELECT results_eq(
  $sql$SELECT stock_qty FROM products WHERE sku='TEST-REMOVE-ITEM'$sql$,
  $$VALUES (13::bigint)$$,
  'remove_order_item: stock restored (was 10, ordered 3, now 13)');

-- 19. remove_order_item: fails after dispatch
INSERT INTO orders (public_order_number, customer_id, address_snapshot, status)
VALUES ('REMOVE-ITEM-002', '99999999-0000-0000-0000-000000000001', '{}'::jsonb, 'ready_for_dispatch');
INSERT INTO order_items (order_id, product_id, sku_snapshot, name_snapshot, quantity_type_snapshot, unit_label_snapshot, price_basis_qty_snapshot, qty_base, unit_price_paise, line_total_paise)
SELECT id, (SELECT id FROM products WHERE sku='TEST-REMOVE-ITEM'), 'TEST-REMOVE-ITEM', 'Removable Item', 'PACKED', 'pack', 1, 1, 5000, 5000
FROM orders WHERE public_order_number = 'REMOVE-ITEM-002';

SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '012f0000-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'staff';
SELECT is((remove_order_item((SELECT id FROM orders WHERE public_order_number='REMOVE-ITEM-002'), (SELECT id FROM order_items WHERE order_id=(SELECT id FROM orders WHERE public_order_number='REMOVE-ITEM-002')), 'Too late'))->>'error', 'INVALID_STATUS', 'remove_order_item: fails after dispatch');
RESET request.jwt.claim.sub;
RESET request.jwt.claims.role;
RESET ROLE;

-- 20. remove_order_item: non-staff rejected
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = 'cccccccc-0000-0000-0000-000000000001'; -- customer
SET LOCAL request.jwt.claims.role = 'customer';
SELECT is((remove_order_item((SELECT id FROM orders WHERE public_order_number='REMOVE-ITEM-001'), (SELECT id FROM order_items WHERE order_id=(SELECT id FROM orders WHERE public_order_number='REMOVE-ITEM-001')), 'Test'))->>'error', 'FORBIDDEN', 'remove_order_item: non-staff rejected');
RESET request.jwt.claim.sub;
RESET request.jwt.claims.role;
RESET ROLE;

-- ========== AUDIT LOG VERIFICATION ==========
-- 21. create_rider creates audit log
SELECT results_eq(
  $sql$SELECT count(*) FROM audit_logs WHERE action='CREATE_RIDER' AND actor_id='ffffffff-0000-0000-0000-000000000001'$sql$,
  $$VALUES (0::bigint)$$,
  'unsupported create_rider does not record a successful operation');

-- 22. reset_rider_password creates audit log
SELECT results_eq(
  $sql$SELECT count(*) FROM audit_logs WHERE action='RESET_RIDER_PASSWORD' AND actor_id='ffffffff-0000-0000-0000-000000000001'$sql$,
  $$VALUES (0::bigint)$$,
  'unsupported reset_rider_password does not record a successful operation');

-- 23. set_rider_active creates audit log
SELECT results_eq(
  $sql$SELECT count(*) FROM audit_logs WHERE action='SET_RIDER_ACTIVE' AND actor_id='ffffffff-0000-0000-0000-000000000001'$sql$,
  $$VALUES (2::bigint)$$,
  'set_rider_active creates audit log');

-- 24. assign_order creates audit log
SELECT results_eq(
  $sql$SELECT count(*) FROM audit_logs WHERE action='ASSIGN_ORDER' AND actor_id='012f0000-0000-0000-0000-000000000001'$sql$,
  $$VALUES (1::bigint)$$,
  'assign_order creates audit log');

-- 25. remove_order_item creates audit log
SELECT results_eq(
  $sql$SELECT count(*) FROM audit_logs WHERE action='REMOVE_ORDER_ITEM' AND actor_id='012f0000-0000-0000-0000-000000000001'$sql$,
  $$VALUES (1::bigint)$$,
  'remove_order_item creates audit log');

-- ========== RLS ISOLATION ==========
-- 26. Rider cannot see other rider's orders
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '88888888-0000-0000-0000-000000000001'; -- rider A
SET LOCAL request.jwt.claims.role = 'delivery';
SELECT results_eq(
  $sql$SELECT count(*) FROM get_rider_orders()$sql$,
  $$VALUES (2::bigint)$$,
  'rider sees two active assignments; completed delivery is excluded');
RESET request.jwt.claim.sub;
RESET request.jwt.claims.role;
RESET ROLE;

-- 27. Customer cannot access rider_orders
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '99999999-0000-0000-0000-000000000001'; -- customer
SET LOCAL request.jwt.claims.role = 'customer';
SELECT is((SELECT count(*) FROM get_rider_orders()), 0::bigint, 'customer cannot access rider_orders');
RESET request.jwt.claim.sub;
RESET request.jwt.claims.role;
RESET ROLE;

-- 28. Staff cannot call rider_update_status
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '012f0000-0000-0000-0000-000000000001'; -- staff
SET LOCAL request.jwt.claims.role = 'staff';
SELECT is((rider_update_status((SELECT id FROM orders WHERE public_order_number='RIDER-STATUS-001'), 'delivered'))->>'error', 'FORBIDDEN', 'staff cannot call rider_update_status');
RESET request.jwt.claim.sub;
RESET request.jwt.claims.role;
RESET ROLE;

SELECT * FROM finish();
ROLLBACK;
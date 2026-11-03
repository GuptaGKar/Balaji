-- Real database roles and separate customer/rider identities; all fixtures roll back.
BEGIN;
SELECT plan(9);

INSERT INTO auth.users (id, email)
SELECT id::uuid, email FROM (VALUES
 ('03300000-0000-0000-0000-000000000001', 'proof-customer-a@test.invalid'),
 ('03300000-0000-0000-0000-000000000002', 'proof-customer-b@test.invalid'),
 ('03300000-0000-0000-0000-000000000003', 'proof-rider-a@test.invalid'),
 ('03300000-0000-0000-0000-000000000004', 'proof-rider-b@test.invalid'),
 ('03300000-0000-0000-0000-000000000005', 'proof-staff@test.invalid'),
 ('03300000-0000-0000-0000-000000000006', 'proof-manager@test.invalid')
) AS fixture(id, email);

INSERT INTO public.profiles (id, role, display_name, is_active, must_change_password)
VALUES
 ('03300000-0000-0000-0000-000000000001', 'customer', 'Proof Customer A', true, false),
 ('03300000-0000-0000-0000-000000000002', 'customer', 'Proof Customer B', true, false),
 ('03300000-0000-0000-0000-000000000003', 'delivery', 'Proof Rider A', true, false),
 ('03300000-0000-0000-0000-000000000004', 'delivery', 'Proof Rider B', true, false),
 ('03300000-0000-0000-0000-000000000005', 'staff', 'Proof Staff', true, false),
 ('03300000-0000-0000-0000-000000000006', 'manager', 'Proof Manager', true, false)
ON CONFLICT (id) DO UPDATE SET role = EXCLUDED.role, is_active = true,
  display_name = EXCLUDED.display_name, must_change_password = false;

INSERT INTO public.orders (id, public_order_number, customer_id, assigned_to, address_snapshot)
VALUES
 ('03310000-0000-0000-0000-000000000001', 'PROOF-ISOLATION-A', '03300000-0000-0000-0000-000000000001', '03300000-0000-0000-0000-000000000003', '{}'),
 ('03310000-0000-0000-0000-000000000002', 'PROOF-ISOLATION-B', '03300000-0000-0000-0000-000000000002', '03300000-0000-0000-0000-000000000004', '{}');

INSERT INTO public.order_assignments (order_id, delivery_user_id, assigned_by)
VALUES
 ('03310000-0000-0000-0000-000000000001', '03300000-0000-0000-0000-000000000003', '03300000-0000-0000-0000-000000000006'),
 ('03310000-0000-0000-0000-000000000002', '03300000-0000-0000-0000-000000000004', '03300000-0000-0000-0000-000000000006');

INSERT INTO public.order_proofs (order_id, stage, kind, path, uploaded_by)
VALUES
 ('03310000-0000-0000-0000-000000000001', 'dispatch', 'image', '03300000-0000-0000-0000-000000000005/proof-a.jpg', '03300000-0000-0000-0000-000000000005'),
 ('03310000-0000-0000-0000-000000000002', 'dispatch', 'image', '03300000-0000-0000-0000-000000000005/proof-b.jpg', '03300000-0000-0000-0000-000000000005');

SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '03300000-0000-0000-0000-000000000001';
SELECT results_eq($$SELECT order_id FROM public.order_proofs$$,
 $$VALUES ('03310000-0000-0000-0000-000000000001'::uuid)$$, 'customer A sees only own proof');
SET LOCAL request.jwt.claim.sub = '03300000-0000-0000-0000-000000000002';
SELECT results_eq($$SELECT order_id FROM public.order_proofs$$,
 $$VALUES ('03310000-0000-0000-0000-000000000002'::uuid)$$, 'customer B sees only own proof');
SET LOCAL request.jwt.claim.sub = '03300000-0000-0000-0000-000000000003';
SELECT results_eq($$SELECT order_id FROM public.order_proofs$$,
 $$VALUES ('03310000-0000-0000-0000-000000000001'::uuid)$$, 'rider A sees only assigned proof');
SET LOCAL request.jwt.claim.sub = '03300000-0000-0000-0000-000000000004';
SELECT results_eq($$SELECT order_id FROM public.order_proofs$$,
 $$VALUES ('03310000-0000-0000-0000-000000000002'::uuid)$$, 'rider B sees only assigned proof');
SET LOCAL request.jwt.claim.sub = '03300000-0000-0000-0000-000000000005';
SELECT is((SELECT count(*) FROM public.order_proofs WHERE order_id IN ('03310000-0000-0000-0000-000000000001','03310000-0000-0000-0000-000000000002')), 2::bigint, 'staff sees operational proofs');
SET LOCAL request.jwt.claim.sub = '03300000-0000-0000-0000-000000000006';
SELECT is((SELECT count(*) FROM public.order_proofs WHERE order_id IN ('03310000-0000-0000-0000-000000000001','03310000-0000-0000-0000-000000000002')), 2::bigint, 'manager sees operational proofs');
SET LOCAL ROLE anon;
SELECT throws_ok($$SELECT * FROM public.order_proofs$$, '42501', NULL, 'anonymous role cannot read proofs');

RESET ROLE;
UPDATE public.order_assignments SET unassigned_at = now()
WHERE delivery_user_id = '03300000-0000-0000-0000-000000000003';
UPDATE public.profiles SET is_active = false WHERE id = '03300000-0000-0000-0000-000000000004';
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '03300000-0000-0000-0000-000000000003';
SELECT is((SELECT count(*) FROM public.order_proofs), 0::bigint, 'unassigned rider loses proof visibility');
SET LOCAL request.jwt.claim.sub = '03300000-0000-0000-0000-000000000004';
SELECT is((SELECT count(*) FROM public.order_proofs), 0::bigint, 'inactive rider sees no proofs');

SELECT * FROM finish();
ROLLBACK;

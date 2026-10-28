-- pgTAP test: dispatch and delivery proof are mandatory for the two transitions.
BEGIN;
SELECT plan(4);

INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        'bbbbbbbb-0000-0000-0000-000000000001',
        'authenticated', 'authenticated', 'proof-test@test.invalid', 'x', now(), now(), now());

INSERT INTO public.profiles (id, role, display_name)
VALUES ('bbbbbbbb-0000-0000-0000-000000000001', 'staff', 'Proof Staff')
ON CONFLICT (id) DO UPDATE SET role = EXCLUDED.role;

INSERT INTO public.orders (
  id, public_order_number, customer_id, address_snapshot, status, payment_method, payment_status
)
SELECT '99999999-9999-9999-9999-999999999901', 'BPS-TEST-PROOF-1', p.id,
       '{"recipient_name":"Proof Customer","phone":"9999999999","address_lines":"9 Test Road","postal_code":"122001"}'::jsonb,
       'ready_for_dispatch', 'cod', 'unpaid'
FROM public.profiles p
WHERE p.role = 'customer'
LIMIT 1
ON CONFLICT (id) DO UPDATE SET status = 'ready_for_dispatch';

-- Act as a signed-in staff user so the trigger is active (it skips service-role work).
SET LOCAL request.jwt.claim.sub = 'bbbbbbbb-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims = '{"sub":"bbbbbbbb-0000-0000-0000-000000000001","role":"authenticated"}';

-- 1. No dispatch proof: the transition is rejected.
SELECT throws_ok(
  $sql$UPDATE public.orders SET status = 'out_for_delivery'
       WHERE id = '99999999-9999-9999-9999-999999999901'$sql$,
  '23514',
  NULL,
  'out_for_delivery is blocked without a dispatch proof'
);

-- 2. With a dispatch proof the transition succeeds.
INSERT INTO public.order_proofs (order_id, stage, kind, path, uploaded_by)
VALUES ('99999999-9999-9999-9999-999999999901', 'dispatch', 'video',
        'bbbbbbbb-0000-0000-0000-000000000001/proof/dispatch.mp4',
        'bbbbbbbb-0000-0000-0000-000000000001')
ON CONFLICT (order_id, stage) DO UPDATE SET path = EXCLUDED.path;

SELECT lives_ok(
  $sql$UPDATE public.orders SET status = 'out_for_delivery'
       WHERE id = '99999999-9999-9999-9999-999999999901'$sql$,
  'out_for_delivery succeeds once the dispatch proof exists'
);

-- 3. Delivered needs its own proof.
SELECT throws_ok(
  $sql$UPDATE public.orders SET status = 'delivered'
       WHERE id = '99999999-9999-9999-9999-999999999901'$sql$,
  '23514',
  NULL,
  'delivered is blocked without a delivery proof'
);

-- 4. With a delivery proof it succeeds.
INSERT INTO public.order_proofs (order_id, stage, kind, path, uploaded_by)
VALUES ('99999999-9999-9999-9999-999999999901', 'delivery', 'image',
        'bbbbbbbb-0000-0000-0000-000000000001/proof/delivery.jpg',
        'bbbbbbbb-0000-0000-0000-000000000001')
ON CONFLICT (order_id, stage) DO UPDATE SET path = EXCLUDED.path;

SELECT lives_ok(
  $sql$UPDATE public.orders SET status = 'delivered'
       WHERE id = '99999999-9999-9999-9999-999999999901'$sql$,
  'delivered succeeds once the delivery proof exists'
);

SELECT * FROM finish();
ROLLBACK;

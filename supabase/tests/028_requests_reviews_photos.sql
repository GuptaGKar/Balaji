-- pgTAP test: product requests, order reviews and order-photo attachment.
BEGIN;
SELECT plan(11);

-- Fixtures: two customers, one staff member and one product.
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        'cccccccc-0000-0000-0000-000000000001',
        'authenticated', 'authenticated', 'request-customer@test.invalid', 'x', now(), now(), now()),
       ('00000000-0000-0000-0000-000000000000',
        'cccccccc-0000-0000-0000-000000000002',
        'authenticated', 'authenticated', 'request-staff@test.invalid', 'x', now(), now(), now());

INSERT INTO public.profiles (id, role, display_name)
VALUES ('cccccccc-0000-0000-0000-000000000001', 'customer', 'Request Customer'),
       ('cccccccc-0000-0000-0000-000000000002', 'staff', 'Request Staff')
ON CONFLICT (id) DO UPDATE SET role = EXCLUDED.role;

INSERT INTO public.products (
  id, sku, name, quantity_type, price_paise, price_basis_qty, unit_label, step_qty, min_qty, stock_qty, active, available
) VALUES (
  '77777777-7777-7777-7777-777777777777', 'REQ-TEST-1', 'Request Test Product',
  'PACKED', 5000, 1, 'pack', 1, 1, 20, true, true
)
ON CONFLICT (sku) DO UPDATE SET active = true, available = true, stock_qty = 20;

-- Two orders for the review checks: one still pending, one delivered.
INSERT INTO public.orders (id, public_order_number, customer_id, address_snapshot, status, payment_method, payment_status)
VALUES ('88888888-8888-8888-8888-888888888801', 'BPS-TEST-REV-1', 'cccccccc-0000-0000-0000-000000000001',
        '{"recipient_name":"Request Customer","phone":"9999999993","address_lines":"3 Test Road","postal_code":"122001"}'::jsonb,
        'pending_review', 'cod', 'unpaid'),
       ('88888888-8888-8888-8888-888888888802', 'BPS-TEST-REV-2', 'cccccccc-0000-0000-0000-000000000001',
        '{"recipient_name":"Request Customer","phone":"9999999993","address_lines":"3 Test Road","postal_code":"122001"}'::jsonb,
        'delivered', 'cod', 'paid')
ON CONFLICT (id) DO NOTHING;

SET LOCAL request.jwt.claim.sub = 'cccccccc-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims = '{"sub":"cccccccc-0000-0000-0000-000000000001","role":"authenticated"}';

-- 1. A customer can create a request.
SELECT is(
  (SELECT public.create_product_request('  Jaggery block  ', '2 kg', 'Not in the catalogue', NULL)->>'status'),
  'new',
  'customer can create a product request');

-- 2. The name is trimmed and stored.
SELECT is(
  (SELECT item_name FROM public.product_requests WHERE customer_id = 'cccccccc-0000-0000-0000-000000000001'),
  'Jaggery block',
  'request stores the trimmed item name');

-- 3. A non-customer cannot create one.
SET LOCAL request.jwt.claim.sub = 'cccccccc-0000-0000-0000-000000000002';
SET LOCAL request.jwt.claims = '{"sub":"cccccccc-0000-0000-0000-000000000002","role":"authenticated"}';
SELECT is(
  (SELECT public.create_product_request('Staff attempt', NULL, NULL, NULL)->>'error'),
  'FORBIDDEN',
  'staff cannot create a customer request');

-- 4. Staff can accept a request.
SELECT is(
  (SELECT public.staff_update_product_request(
     (SELECT id FROM public.product_requests WHERE customer_id = 'cccccccc-0000-0000-0000-000000000001'),
     'accepted', 'We will try to stock it', NULL
   )->>'status'),
  'accepted',
  'staff can accept a request');

-- 5. Closing as added requires the catalogue product.
SELECT is(
  (SELECT public.staff_update_product_request(
     (SELECT id FROM public.product_requests WHERE customer_id = 'cccccccc-0000-0000-0000-000000000001'),
     'added', NULL, NULL
   )->>'error'),
  'VALIDATION_FAILED',
  'added requires a linked product');

-- 6. Closing as added with the catalogue product succeeds.
SELECT is(
  (SELECT public.staff_update_product_request(
     (SELECT id FROM public.product_requests WHERE customer_id = 'cccccccc-0000-0000-0000-000000000001'),
     'added', 'Now stocked', '77777777-7777-7777-7777-777777777777'
   )->>'status'),
  'added',
  'staff can close a request as added');

-- 7. The closed request points at the product that was added.
SELECT is(
  (SELECT linked_product_id::text FROM public.product_requests
   WHERE customer_id = 'cccccccc-0000-0000-0000-000000000001'),
  '77777777-7777-7777-7777-777777777777',
  'added request is linked to the catalogue product');

-- 8. Reviews are rejected before delivery.
SET LOCAL request.jwt.claim.sub = 'cccccccc-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims = '{"sub":"cccccccc-0000-0000-0000-000000000001","role":"authenticated"}';
SELECT is(
  (SELECT public.submit_order_review('88888888-8888-8888-8888-888888888801', 5, 'Great')->>'error'),
  'NOT_DELIVERED',
  'only delivered orders can be reviewed');

-- 9. A delivered order accepts a review.
SELECT ok(
  (SELECT public.submit_order_review('88888888-8888-8888-8888-888888888802', 5, 'Very good')->>'review_id') IS NOT NULL,
  'delivered order accepts a review');

-- 10. The same order cannot be reviewed twice.
SELECT is(
  (SELECT public.submit_order_review('88888888-8888-8888-8888-888888888802', 4, 'Again')->>'error'),
  'ALREADY_REVIEWED',
  'one review per order');

-- 11. The photo path must live under the caller's uid folder.
SELECT is(
  (SELECT public.attach_order_image('88888888-8888-8888-8888-888888888801',
     'cccccccc-0000-0000-0000-000000000002/stolen.jpg')->>'error'),
  'VALIDATION_FAILED',
  'photo path is scoped to the customer folder');

SELECT * FROM finish();
ROLLBACK;

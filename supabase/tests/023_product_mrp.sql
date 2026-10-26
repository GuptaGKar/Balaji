-- pgTAP test: MRP column and price update by staff/manager
BEGIN;
SELECT plan(8);

INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        'a1a1a1a1-1111-4111-8111-111111111111',
        'authenticated', 'authenticated', 'mrp-staff@test.invalid', 'x', now(), now(), now()),
       ('00000000-0000-0000-0000-000000000000',
        'b2b2b2b2-2222-4222-8222-222222222222',
        'authenticated', 'authenticated', 'mrp-customer@test.invalid', 'x', now(), now(), now());

INSERT INTO public.profiles (id, role, display_name)
VALUES ('a1a1a1a1-1111-4111-8111-111111111111', 'staff', 'MRP Staff'),
       ('b2b2b2b2-2222-4222-8222-222222222222', 'customer', 'MRP Customer');

-- Privileges
SELECT ok(
  has_column_privilege('authenticated', 'public.products', 'mrp_paise', 'INSERT'),
  'authenticated can INSERT mrp_paise');
SELECT ok(
  has_column_privilege('authenticated', 'public.products', 'mrp_paise', 'UPDATE'),
  'authenticated can UPDATE mrp_paise');

-- Insert initial product as superuser
INSERT INTO public.products (
  id, sku, name, quantity_type, price_paise, mrp_paise, price_basis_qty, unit_label, step_qty, min_qty
) VALUES (
  '11111111-1111-1111-1111-111111111111', 'MRP-TEST-1', 'MRP Test Product',
  'PACKED', 9000, 10000, 1, 'pack', 1, 1
);

-- Customer is blocked from updating product price
SET LOCAL request.jwt.claims = '{"sub": "b2b2b2b2-2222-4222-8222-222222222222", "role": "authenticated"}';
SET LOCAL ROLE authenticated;
SELECT throws_ok(
  $sql$SELECT public.update_product_price('11111111-1111-1111-1111-111111111111', 8500, 10000)$sql$,
  'Only staff or managers can update product prices.',
  'customer cannot call update_product_price'
);

-- Staff can update product price and MRP
SET LOCAL request.jwt.claims = '{"sub": "a1a1a1a1-1111-4111-8111-111111111111", "role": "authenticated"}';
SELECT lives_ok(
  $sql$SELECT public.update_product_price('11111111-1111-1111-1111-111111111111', 8000, 10000)$sql$,
  'staff can call update_product_price'
);

-- Verify update
SELECT results_eq(
  $sql$SELECT price_paise, mrp_paise FROM public.products WHERE id = '11111111-1111-1111-1111-111111111111'$sql$,
  $sql$VALUES (8000::bigint, 10000::bigint)$sql$,
  'price_paise and mrp_paise updated correctly'
);

-- Direct update by staff
SELECT lives_ok(
  $sql$UPDATE public.products SET price_paise = 7500, mrp_paise = 9500 WHERE id = '11111111-1111-1111-1111-111111111111'$sql$,
  'staff can update products table directly via RLS policy'
);

-- Negative price validation
SELECT throws_ok(
  $sql$SELECT public.update_product_price('11111111-1111-1111-1111-111111111111', -100, 10000)$sql$,
  'Selling price cannot be negative.',
  'rejects negative selling price'
);

SELECT throws_ok(
  $sql$SELECT public.update_product_price('11111111-1111-1111-1111-111111111111', 5000, -500)$sql$,
  'MRP cannot be negative.',
  'rejects negative MRP'
);

SELECT * FROM finish();
ROLLBACK;

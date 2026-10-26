-- pgTAP test: Coupon creation, permissions, validation and quote_cart calculation
BEGIN;
SELECT plan(7);

INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        'c1c1c1c1-1111-4111-8111-111111111111',
        'authenticated', 'authenticated', 'coupon-staff@test.invalid', 'x', now(), now(), now()),
       ('00000000-0000-0000-0000-000000000000',
        'c2c2c2c2-2222-4222-8222-222222222222',
        'authenticated', 'authenticated', 'coupon-customer@test.invalid', 'x', now(), now(), now());

INSERT INTO public.profiles (id, role, display_name)
VALUES ('c1c1c1c1-1111-4111-8111-111111111111', 'staff', 'Coupon Staff'),
       ('c2c2c2c2-2222-4222-8222-222222222222', 'customer', 'Coupon Customer');

-- Privileges
SELECT ok(
  has_table_privilege('authenticated', 'public.coupons', 'SELECT'),
  'authenticated can SELECT from coupons');

-- Create test product
INSERT INTO public.products (
  id, sku, name, quantity_type, price_paise, price_basis_qty, unit_label, step_qty, min_qty, stock_qty, active, available
) VALUES (
  '22222222-2222-2222-2222-222222222222', 'COUPON-TEST-1', 'Coupon Test Product',
  'PACKED', 50000, 1, 'pack', 1, 1, 100, true, true
);

-- Staff can insert coupon
SET LOCAL request.jwt.claims = '{"sub": "c1c1c1c1-1111-4111-8111-111111111111", "role": "authenticated"}';
SET LOCAL ROLE authenticated;

SELECT lives_ok(
  $sql$
  INSERT INTO public.coupons (
    code, description, discount_type, discount_value, min_order_paise, max_discount_paise
  ) VALUES (
    'SAVE10', '10% off up to 100 Rs', 'percent_off', 10, 20000, 10000
  );
  $sql$,
  'staff can create coupons'
);

SELECT lives_ok(
  $sql$
  INSERT INTO public.coupons (
    code, description, discount_type, discount_value, min_order_paise
  ) VALUES (
    'FLAT50', 'Flat Rs 50 off', 'rupees_off', 5000, 25000
  );
  $sql$,
  'staff can create flat rupees off coupons'
);

-- Customer is blocked from creating coupons
SET LOCAL request.jwt.claims = '{"sub": "c2c2c2c2-2222-4222-8222-222222222222", "role": "authenticated"}';

SELECT throws_ok(
  $sql$
  INSERT INTO public.coupons (code, discount_type, discount_value)
  VALUES ('HACKER', 'percent_off', 99);
  $sql$,
  NULL,
  'customer cannot insert coupons'
);

-- Test quote_cart with percent_off coupon
SELECT ok(
  (
    SELECT (public.quote_cart(
      '[{"product_id": "22222222-2222-2222-2222-222222222222", "qty_base": 1}]'::jsonb,
      'SAVE10'
    )->>'coupon_applied')::boolean
  ),
  'quote_cart applies valid SAVE10 coupon'
);

-- Test quote_cart discount amount (10% of 50000 = 5000 paise)
SELECT is(
  (
    SELECT (public.quote_cart(
      '[{"product_id": "22222222-2222-2222-2222-222222222222", "qty_base": 1}]'::jsonb,
      'SAVE10'
    )->>'discount_paise')::bigint
  ),
  5000::bigint,
  'quote_cart computes 10 percent discount properly'
);

-- Test quote_cart invalid coupon returns coupon_error
SELECT ok(
  (
    SELECT public.quote_cart(
      '[{"product_id": "22222222-2222-2222-2222-222222222222", "qty_base": 1}]'::jsonb,
      'NONEXISTENT'
    )->>'coupon_error' IS NOT NULL
  ),
  'quote_cart flags invalid coupon with error message'
);

SELECT * FROM finish();
ROLLBACK;

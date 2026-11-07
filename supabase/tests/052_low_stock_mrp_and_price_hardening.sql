-- pgTAP test: staff low-stock rows carry MRP, and price RPC is not callable by anon.
BEGIN;
SELECT plan(8);

SELECT ok(
  has_function_privilege(
    'authenticated',
    'public.update_product_price(uuid,bigint,bigint)',
    'EXECUTE'
  ),
  'authenticated can execute update_product_price');

SELECT ok(
  NOT has_function_privilege(
    'anon',
    'public.update_product_price(uuid,bigint,bigint)',
    'EXECUTE'
  ),
  'anon cannot execute update_product_price');

SELECT ok(
  NOT has_function_privilege(
    'anon',
    'public.get_low_stock_products()',
    'EXECUTE'
  ),
  'anon cannot execute get_low_stock_products');

SELECT ok(
  pg_get_function_result('public.get_low_stock_products()'::regprocedure) LIKE '%mrp_paise%',
  'get_low_stock_products returns mrp_paise');

INSERT INTO auth.users (
  instance_id, id, aud, role, email, encrypted_password,
  email_confirmed_at, created_at, updated_at
)
VALUES (
  '00000000-0000-0000-0000-000000000000',
  'c3c3c3c3-3333-4333-8333-333333333333',
  'authenticated', 'authenticated', 'low-stock-mrp@test.invalid', 'x',
  now(), now(), now()
);

INSERT INTO public.profiles (id, role, display_name)
VALUES ('c3c3c3c3-3333-4333-8333-333333333333', 'staff', 'Low Stock MRP Staff')
ON CONFLICT (id) DO UPDATE
SET role = EXCLUDED.role, display_name = EXCLUDED.display_name;

INSERT INTO public.products (
  id, sku, name, quantity_type, price_paise, mrp_paise,
  price_basis_qty, unit_label, step_qty, min_qty,
  stock_qty, low_stock_threshold, active
) VALUES (
  '22222222-2222-2222-2222-222222222222', 'MRP-LOW-STOCK-1', 'Low Stock MRP Product',
  'PACKED', 4500, 5000, 1, 'pack', 1, 1, 2, 5, true
);

SET LOCAL request.jwt.claims =
  '{"sub": "c3c3c3c3-3333-4333-8333-333333333333", "role": "authenticated"}';
SET LOCAL ROLE authenticated;

SELECT results_eq(
  $sql$SELECT price_paise, mrp_paise
       FROM public.get_low_stock_products()
       WHERE id = '22222222-2222-2222-2222-222222222222'$sql$,
  $sql$VALUES (4500::bigint, 5000::bigint)$sql$,
  'low-stock row carries selling price and MRP');

SELECT is(
  (SELECT mrp_paise
   FROM public.get_low_stock_products()
   WHERE id = '22222222-2222-2222-2222-222222222222'),
  5000::bigint,
  'low-stock MRP survives the RPC boundary');

SELECT throws_ok(
  $sql$SELECT public.update_product_price(
         '22222222-2222-2222-2222-222222222222', 0, 5000)$sql$,
  'P0001',
  'Selling price must be greater than zero.',
  'staff cannot make a live product free');

SELECT throws_ok(
  $sql$SELECT public.update_product_price(
         '22222222-2222-2222-2222-222222222222', 4500, -1)$sql$,
  'P0001',
  'MRP cannot be negative.',
  'staff cannot set a negative MRP');

SELECT * FROM finish();
ROLLBACK;

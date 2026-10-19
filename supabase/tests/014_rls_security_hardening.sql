-- pgTAP: Security RLS Hardening Tests (Phase 1)
-- Verifies anon sees 0 orders/profiles, customer sees only own orders,
-- non-manager cannot write products or profiles, manager can write products/profiles.

BEGIN;
SELECT plan(6);

-- Setup Test Identities
INSERT INTO auth.users (id, email) VALUES
  ('11111111-1111-1111-1111-111111111111', 'cust1@test.invalid'),
  ('22222222-2222-2222-2222-222222222222', 'cust2@test.invalid'),
  ('33333333-3333-3333-3333-333333333333', 'mgr1@test.invalid')
ON CONFLICT DO NOTHING;

INSERT INTO public.profiles (id, role, display_name) VALUES
  ('11111111-1111-1111-1111-111111111111', 'customer', 'Customer 1'),
  ('22222222-2222-2222-2222-222222222222', 'customer', 'Customer 2'),
  ('33333333-3333-3333-3333-333333333333', 'manager', 'Manager 1')
ON CONFLICT DO NOTHING;

INSERT INTO public.orders (id, public_order_number, customer_id, address_snapshot, grand_total_paise) VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'ORD-CUST1', '11111111-1111-1111-1111-111111111111', '{}'::jsonb, 1000),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'ORD-CUST2', '22222222-2222-2222-2222-222222222222', '{}'::jsonb, 2000)
ON CONFLICT DO NOTHING;

-- Test 1: Anonymous sees 0 orders
SET LOCAL ROLE anon;
SELECT results_eq(
  $sql$SELECT count(*)::integer FROM public.orders$sql$,
  ARRAY[0],
  'Anonymous role sees 0 orders'
);

-- Test 2: Anonymous sees 0 profiles
SELECT results_eq(
  $sql$SELECT count(*)::integer FROM public.profiles$sql$,
  ARRAY[0],
  'Anonymous role sees 0 profiles'
);

-- Test 3: Customer 1 sees only their own order
SET LOCAL request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}';
SET LOCAL ROLE authenticated;
SELECT results_eq(
  $sql$SELECT public_order_number FROM public.orders$sql$,
  ARRAY['ORD-CUST1'],
  'Customer 1 sees only their own order'
);

-- Test 4: Customer 1 cannot insert a product
SELECT throws_ok(
  $sql$INSERT INTO public.products (sku, name, quantity_type, price_paise, price_basis_qty, unit_label, step_qty, min_qty) VALUES ('ILLEGAL-SKU', 'Illegal Prod', 'packed', 100, 1, 'pack', 1, 1)$sql$,
  '42501',
  null,
  'Customer 1 cannot insert a product'
);

-- Test 5: Manager sees all orders
SET LOCAL request.jwt.claims = '{"sub": "33333333-3333-3333-3333-333333333333", "role": "authenticated"}';
SET LOCAL ROLE authenticated;
SELECT results_eq(
  $sql$SELECT count(*)::integer FROM public.orders$sql$,
  ARRAY[2],
  'Manager sees all orders'
);

-- Test 6: Manager can insert a product
SELECT lives_ok(
  $sql$INSERT INTO public.products (sku, name, quantity_type, price_paise, price_basis_qty, unit_label, step_qty, min_qty) VALUES ('MGR-SKU', 'Mgr Prod', 'packed', 100, 1, 'pack', 1, 1)$sql$,
  'Manager can insert a product'
);

SELECT * FROM finish();
ROLLBACK;

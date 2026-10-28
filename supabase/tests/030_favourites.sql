-- pgTAP test: F3 favourites — owner-only rows, duplicate add is a no-op with the
-- upsert header the app sends, and another customer cannot read or forge a row.
BEGIN;
SELECT plan(5);

INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        'eeeeeeee-0000-0000-0000-000000000001',
        'authenticated', 'authenticated', 'fav-a@test.invalid', 'x', now(), now(), now()),
       ('00000000-0000-0000-0000-000000000000',
        'eeeeeeee-0000-0000-0000-000000000002',
        'authenticated', 'authenticated', 'fav-b@test.invalid', 'x', now(), now(), now());

INSERT INTO public.profiles (id, role, display_name)
VALUES ('eeeeeeee-0000-0000-0000-000000000001', 'customer', 'Fav A'),
       ('eeeeeeee-0000-0000-0000-000000000002', 'customer', 'Fav B')
ON CONFLICT (id) DO UPDATE SET role = EXCLUDED.role;

INSERT INTO public.products (
  id, sku, name, quantity_type, price_paise, price_basis_qty, unit_label, step_qty, min_qty, stock_qty, active, available
) VALUES (
  'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01', 'FAV-TEST-1', 'Favourite Test Product',
  'PACKED', 4000, 1, 'pack', 1, 1, 10, true, true
)
ON CONFLICT (sku) DO UPDATE SET active = true, available = true;

-- Act as customer A on the authenticated role so RLS applies.
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = 'eeeeeeee-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims = '{"sub":"eeeeeeee-0000-0000-0000-000000000001","role":"authenticated"}';

-- 1. A can favourite a product for themselves.
SELECT lives_ok(
  $sql$INSERT INTO public.favourites (customer_id, product_id)
       VALUES ('eeeeeeee-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01')
       ON CONFLICT DO NOTHING$sql$,
  'customer can favourite a product');

-- 2. Adding it again is a no-op (the app sends resolution=ignore-duplicates).
SELECT lives_ok(
  $sql$INSERT INTO public.favourites (customer_id, product_id)
       VALUES ('eeeeeeee-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01')
       ON CONFLICT DO NOTHING$sql$,
  'adding the same favourite twice changes nothing');

SELECT is(
  (SELECT count(*) FROM public.favourites
   WHERE customer_id = 'eeeeeeee-0000-0000-0000-000000000001'),
  1::bigint,
  'exactly one favourite row exists');

-- 3. Customer B cannot read A's favourites.
SET LOCAL request.jwt.claim.sub = 'eeeeeeee-0000-0000-0000-000000000002';
SET LOCAL request.jwt.claims = '{"sub":"eeeeeeee-0000-0000-0000-000000000002","role":"authenticated"}';
SELECT is(
  (SELECT count(*) FROM public.favourites),
  0::bigint,
  'another customer cannot see the row');

-- 4. Customer B cannot insert a row that belongs to A.
SELECT throws_ok(
  $sql$INSERT INTO public.favourites (customer_id, product_id)
       VALUES ('eeeeeeee-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01')
       ON CONFLICT DO NOTHING$sql$,
  '42501',
  NULL,
  'a customer cannot write a favourite for somebody else');

SELECT * FROM finish();
ROLLBACK;

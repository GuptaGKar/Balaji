-- pgTAP: product variant columns are writable by authenticated managers.
--
-- Regression for the "unable to save product" bug: migration
-- 20261014000017 revoked table-level INSERT/UPDATE and granted a column
-- allow-list, then 20261023000001 added variant_group_id/variant_label without
-- extending the allow-list. Verify the grants directly and exercise a manager
-- insert/update through RLS.

BEGIN;
SELECT plan(8);

INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        'e5e5e5e5-5555-4555-8555-555555555555',
        'authenticated', 'authenticated', 'variant-manager@test.invalid', 'x', now(), now(), now()),
       ('00000000-0000-0000-0000-000000000000',
        'f6f6f6f6-6666-4666-8666-666666666666',
        'authenticated', 'authenticated', 'variant-staff@test.invalid', 'x', now(), now(), now());

INSERT INTO public.profiles (id, role, display_name)
VALUES ('e5e5e5e5-5555-4555-8555-555555555555', 'manager', 'Variant Manager'),
       ('f6f6f6f6-6666-4666-8666-666666666666', 'staff', 'Variant Staff');

-- Column privileges exist for the role that the admin app uses.
SELECT ok(
  has_column_privilege('authenticated', 'public.products', 'variant_group_id', 'INSERT'),
  'authenticated can INSERT variant_group_id');
SELECT ok(
  has_column_privilege('authenticated', 'public.products', 'variant_group_id', 'UPDATE'),
  'authenticated can UPDATE variant_group_id');
SELECT ok(
  has_column_privilege('authenticated', 'public.products', 'variant_label', 'INSERT'),
  'authenticated can INSERT variant_label');
SELECT ok(
  has_column_privilege('authenticated', 'public.products', 'variant_label', 'UPDATE'),
  'authenticated can UPDATE variant_label');

-- Anonymous clients must not gain the new columns.
SELECT ok(
  NOT has_column_privilege('anon', 'public.products', 'variant_group_id', 'INSERT'),
  'anon cannot INSERT variant_group_id');

-- Manager row write still works through RLS.
SET LOCAL request.jwt.claims = '{"sub": "e5e5e5e5-5555-4555-8555-555555555555", "role": "authenticated"}';
SET LOCAL ROLE authenticated;
SELECT lives_ok(
  $sql$INSERT INTO public.products (
         sku, name, quantity_type, price_paise, price_basis_qty, unit_label,
         step_qty, min_qty, variant_label
       ) VALUES (
         'VARIANT-GRANT-TEST', 'Variant Grant Test',
         'PACKED', 10000, 1, 'pack', 1, 1, '1 kg'
       )$sql$,
  'manager can insert a product with variant_label');
SELECT lives_ok(
  $sql$UPDATE public.products
       SET variant_group_id = id
       WHERE sku = 'VARIANT-GRANT-TEST'$sql$,
  'manager can update variant_group_id');

-- Staff must remain blocked by the manager-only RLS policy.
SET LOCAL request.jwt.claims = '{"sub": "f6f6f6f6-6666-4666-8666-666666666666", "role": "authenticated"}';
SELECT throws_ok(
  $sql$INSERT INTO public.products (
         sku, name, quantity_type, price_paise, price_basis_qty, unit_label,
         step_qty, min_qty, variant_label
       ) VALUES ('VARIANT-STAFF-TEST', 'Staff Variant Test', 'PACKED', 100, 1, 'pack', 1, 1, '500 g')$sql$,
  '42501',
  null,
  'staff still cannot insert a product variant');

SELECT * FROM finish();
ROLLBACK;

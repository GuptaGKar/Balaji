-- pgTAP: RLS policy tests (IMPLEMENTATION_PLAN.md §5.2, §12)
-- Tests run as different roles: anon, customer_a, customer_b, rider_a, rider_b, staff, manager
-- Each test file sets up fixtures and verifies isolation.

BEGIN;
SELECT plan(4);

-- 1. anon can read catalog_products view
SELECT lives_ok(
  $sql$SELECT 1 FROM catalog_products LIMIT 1$sql$,
  'anon can read catalog_products view'
);

-- 2. anon CANNOT read products table directly
SELECT throws_ok(
  $sql$SELECT 1 FROM products LIMIT 1$sql$,
  null, null,
  'anon cannot read products table directly'
);

-- 3. anon CANNOT read orders table
SELECT throws_ok(
  $sql$SELECT 1 FROM orders LIMIT 1$sql$,
  null, null,
  'anon cannot read orders table'
);

-- 4. anon CANNOT read profiles table
SELECT throws_ok(
  $sql$SELECT 1 FROM profiles LIMIT 1$sql$,
  null, null,
  'anon cannot read profiles table'
);

SELECT * FROM finish();
ROLLBACK;
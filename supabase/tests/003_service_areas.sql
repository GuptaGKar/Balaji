-- pgTAP: service area constraints (IMPLEMENTATION_PLAN.md §4.2)
-- Uniqueness on (kind, lower(value)) makes service matching
-- case-insensitive, as the address-service matching does.

BEGIN;
SELECT plan(4);

-- 1. First service area is accepted.
SELECT lives_ok(
  $sql$INSERT INTO service_areas (kind, value) VALUES ('pincode', '999999')$sql$,
  'first service area is accepted');

-- 2. Exact duplicate is rejected.
SELECT throws_ok(
  $sql$INSERT INTO service_areas (kind, value) VALUES ('pincode', '999999')$sql$,
  null, null,
  'duplicate service area value is rejected');

-- 3. Second value with different case is accepted first.
SELECT lives_ok(
  $sql$INSERT INTO service_areas (kind, value) VALUES ('neighbourhood', 'Test Area')$sql$,
  'first neighbourhood value is accepted');

-- 4. Case-insensitive duplicate is rejected.
SELECT throws_ok(
  $sql$INSERT INTO service_areas (kind, value) VALUES ('neighbourhood', 'test area')$sql$,
  null, null,
  'case-insensitive duplicate service area is rejected');

SELECT * FROM finish();
ROLLBACK;

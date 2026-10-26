-- pgTAP: customer profile edits and saved address ownership/default rules
BEGIN;
SELECT plan(10);

INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        'dddddddd-0000-0000-0000-000000000001',
        'authenticated', 'authenticated',
        'profile-a@test.invalid', 'x', now(), now(), now()),
       ('00000000-0000-0000-0000-000000000000',
        'dddddddd-0000-0000-0000-000000000002',
        'authenticated', 'authenticated',
        'profile-b@test.invalid', 'x', now(), now(), now());

INSERT INTO profiles (id, role, display_name)
VALUES ('dddddddd-0000-0000-0000-000000000001', 'customer', 'Customer A'),
       ('dddddddd-0000-0000-0000-000000000002', 'customer', 'Customer B')
ON CONFLICT (id) DO UPDATE
SET role = EXCLUDED.role,
    display_name = EXCLUDED.display_name;

SELECT set_config('request.jwt.claims', json_build_object('sub', 'dddddddd-0000-0000-0000-000000000001', 'role', 'authenticated')::text, true);

SELECT is(
  public.update_my_profile('Asha A')->>'display_name',
  'Asha A',
  'customer can update own display name'
);

SELECT is(
  public.update_my_profile('   ')->>'error',
  'VALIDATION_FAILED',
  'blank display name is rejected'
);

SELECT is(
  public.save_my_address(
    NULL, 'Home', 'Asha A', '9876543210', '12, MG Road',
    'Near temple', 'Shivaji Nagar', 'Pune', 'Maharashtra', '411005', true
  )->'address'->>'is_default',
  'true',
  'first saved address becomes default'
);

SELECT is(
  public.save_my_address(
    NULL, 'Work', 'Asha A', '9876543210', 'Office 4', NULL,
    NULL, 'Pune', 'Maharashtra', '411001', false
  )->'address'->>'is_default',
  'false',
  'additional address can be non-default'
);

SELECT is(
  (SELECT count(*)::int FROM addresses WHERE customer_id = auth.uid() AND is_default),
  1,
  'customer has exactly one default address'
);

SELECT is(
  (public.save_my_address(
    (SELECT id FROM addresses WHERE customer_id = auth.uid() AND label = 'Work'),
    'Work', 'Asha A', '9876543210', 'Office 4', NULL,
    NULL, 'Pune', 'Maharashtra', '411001', true
  )->'address'->>'is_default'),
  'true',
  'customer can move the default marker'
);

SELECT is(
  (SELECT count(*)::int FROM addresses WHERE customer_id = auth.uid() AND is_default),
  1,
  'moving default still leaves exactly one'
);

SELECT is(
  (public.delete_my_address(
    (SELECT id FROM addresses WHERE customer_id = auth.uid() AND is_default)
  )->>'success'),
  'true',
  'customer can delete an owned address'
);

SELECT is(
  (SELECT count(*)::int FROM addresses WHERE customer_id = auth.uid() AND is_default),
  1,
  'deleting the default promotes another address'
);

SELECT set_config('request.jwt.claims', json_build_object('sub', 'dddddddd-0000-0000-0000-000000000002', 'role', 'authenticated')::text, true);

SELECT is(
  public.delete_my_address(
    (SELECT id FROM addresses WHERE customer_id = 'dddddddd-0000-0000-0000-000000000001' LIMIT 1)
  )->>'error',
  'NOT_FOUND',
  'customer cannot delete another customer address'
);

SELECT * FROM finish();
ROLLBACK;

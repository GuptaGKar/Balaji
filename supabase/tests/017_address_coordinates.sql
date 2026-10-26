-- pgTAP: saved address coordinates are validated and preserved on edit
BEGIN;
SELECT plan(7);

INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        'dddddddd-0000-0000-0000-000000000101',
        'authenticated', 'authenticated',
        'coords@test.invalid', 'x', now(), now(), now());

INSERT INTO profiles (id, role, display_name)
VALUES ('dddddddd-0000-0000-0000-000000000101', 'customer', 'Coordinate Tester')
ON CONFLICT (id) DO UPDATE
SET role = EXCLUDED.role,
    display_name = EXCLUDED.display_name;

SELECT set_config('request.jwt.claims', json_build_object('sub', 'dddddddd-0000-0000-0000-000000000101', 'role', 'authenticated')::text, true);

SELECT is(
  (public.save_my_address(
    NULL, 'Home', 'Asha', '9876543210', '12, MG Road',
    NULL, 'Kothrud', 'Pune', 'Maharashtra', '411038', false,
    18.5074, 73.8077
  )->'address'->>'lat')::double precision,
  18.5074::double precision,
  'latitude is stored with the address'
);

SELECT is(
  (SELECT lng FROM public.addresses WHERE customer_id = auth.uid() AND label = 'Home'),
  73.8077::double precision,
  'longitude is stored with the address'
);

SELECT is(
  public.save_my_address(
    NULL, 'No GPS', 'Asha', '9876543210', '5, Station Road',
    NULL, 'Kothrud', 'Pune', 'Maharashtra', '411038', false
  )->'address'->>'lat',
  NULL,
  'address can be saved without coordinates'
);

SELECT is(
  (public.save_my_address(
    (SELECT id FROM public.addresses WHERE customer_id = auth.uid() AND label = 'Home'),
    'Home', 'Asha', '9876543210', '12, MG Road',
    NULL, 'Kothrud', 'Pune', 'Maharashtra', '411038', false
  )->'address'->>'lat')::double precision,
  18.5074::double precision,
  'editing without a new fix preserves stored coordinates'
);

SELECT is(
  (public.save_my_address(
    (SELECT id FROM public.addresses WHERE customer_id = auth.uid() AND label = 'Home'),
    'Home', 'Asha', '9876543210', '12, MG Road',
    NULL, 'Kothrud', 'Pune', 'Maharashtra', '411038', false,
    18.5204, 73.8567
  )->'address'->>'lng')::double precision,
  73.8567::double precision,
  'editing with a new fix replaces coordinates'
);

SELECT is(
  public.save_my_address(
    NULL, 'Half', 'Asha', '9876543210', '1, Test Road',
    NULL, NULL, 'Pune', 'Maharashtra', '411001', false, 18.5, NULL
  )->>'error',
  'VALIDATION_FAILED',
  'a partial coordinate pair is rejected'
);

SELECT is(
  public.save_my_address(
    NULL, 'Bad', 'Asha', '9876543210', '2, Test Road',
    NULL, NULL, 'Pune', 'Maharashtra', '411001', false, 99.0, 73.8
  )->>'error',
  'VALIDATION_FAILED',
  'out-of-range coordinates are rejected'
);

SELECT * FROM finish();
ROLLBACK;

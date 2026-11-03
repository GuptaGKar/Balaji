-- Every public SECURITY DEFINER function must use an empty search_path.
BEGIN;
SELECT plan(3);
SELECT ok(COALESCE((SELECT proconfig @> ARRAY['search_path=""'] FROM pg_proc
 WHERE oid='public.handle_new_user()'::regprocedure),false),'signup definer uses an empty search_path');
INSERT INTO auth.users(id,email,aud,role,raw_user_meta_data)
VALUES('03800000-0000-0000-0000-000000000001','signup-path@test.invalid','authenticated','authenticated','{"role":"manager","display_name":"Synthetic Signup"}');
SELECT is((SELECT role::text FROM public.profiles WHERE id='03800000-0000-0000-0000-000000000001'),
 'customer','signup succeeds without trusting a client manager role');
SELECT is((SELECT display_name FROM public.profiles WHERE id='03800000-0000-0000-0000-000000000001'),
 'Synthetic Signup','qualified signup trigger still creates the profile');
SELECT * FROM finish();
ROLLBACK;

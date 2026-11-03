-- Real rider SQL roles and independent fixtures, including both sides of each boundary.
BEGIN;
SELECT plan(8);
\ir helpers/role_fixtures.inc
SELECT pg_temp.make_profile('00700000-0000-0000-0000-000000000001','delivery','Rider A');
SELECT pg_temp.make_profile('00700000-0000-0000-0000-000000000002','delivery','Rider B');
SELECT pg_temp.make_profile('00700000-0000-0000-0000-000000000003','customer','Customer');
SELECT pg_temp.make_profile('00700000-0000-0000-0000-000000000004','manager','Manager');
INSERT INTO public.orders(id,public_order_number,customer_id,address_snapshot,status,assigned_to) VALUES
 ('00710000-0000-0000-0000-000000000001','RIDER-A-ORDER','00700000-0000-0000-0000-000000000003','{}','ready_for_dispatch','00700000-0000-0000-0000-000000000001'),
 ('00710000-0000-0000-0000-000000000002','RIDER-B-ORDER','00700000-0000-0000-0000-000000000003','{}','ready_for_dispatch','00700000-0000-0000-0000-000000000002');
INSERT INTO public.order_assignments(order_id,delivery_user_id,assigned_by) VALUES
 ('00710000-0000-0000-0000-000000000001','00700000-0000-0000-0000-000000000001','00700000-0000-0000-0000-000000000004'),
 ('00710000-0000-0000-0000-000000000002','00700000-0000-0000-0000-000000000002','00700000-0000-0000-0000-000000000004');
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub='00700000-0000-0000-0000-000000000001';
SELECT results_eq($q$SELECT id FROM public.rider_orders$q$,$q$VALUES ('00710000-0000-0000-0000-000000000001'::uuid)$q$,'rider A view exposes only assigned order');
SELECT results_eq($q$SELECT id FROM public.orders$q$,$q$VALUES ('00710000-0000-0000-0000-000000000001'::uuid)$q$,'base orders table also isolates rider A');
SELECT is((SELECT count(*) FROM public.profiles WHERE id=auth.uid()),1::bigint,'rider can read own role profile');
SELECT is((SELECT count(*) FROM public.profiles WHERE id='00700000-0000-0000-0000-000000000003'),0::bigint,'rider cannot read customer profile directly');
SET LOCAL request.jwt.claim.sub='00700000-0000-0000-0000-000000000002';
SELECT results_eq($q$SELECT id FROM public.rider_orders$q$,$q$VALUES ('00710000-0000-0000-0000-000000000002'::uuid)$q$,'rider B view exposes only assigned order');
SELECT is((SELECT count(*) FROM public.orders WHERE id='00710000-0000-0000-0000-000000000001'),0::bigint,'rider B cannot read rider A order');
SELECT is((SELECT count(*) FROM public.order_assignments WHERE delivery_user_id=auth.uid()),1::bigint,'rider B sees own assignment');
SELECT is((SELECT count(*) FROM public.order_assignments WHERE delivery_user_id='00700000-0000-0000-0000-000000000001'),0::bigint,'rider B cannot read rider A assignment');
SELECT * FROM finish();
ROLLBACK;

-- A proof row without its stored object must not authorize a delivery transition.
BEGIN;
SELECT plan(4);
\ir helpers/role_fixtures.inc
SELECT pg_temp.make_profile('03600000-0000-0000-0000-000000000001','customer','Gate Customer');
SELECT pg_temp.make_profile('03600000-0000-0000-0000-000000000002','manager','Gate Manager');
INSERT INTO public.orders(id,public_order_number,customer_id,address_snapshot,status)
VALUES('03610000-0000-0000-0000-000000000001','OBJECT-GATE','03600000-0000-0000-0000-000000000001','{}','ready_for_dispatch');
INSERT INTO public.order_proofs(order_id,stage,kind,path,uploaded_by) VALUES
 ('03610000-0000-0000-0000-000000000001','dispatch','image','03600000-0000-0000-0000-000000000002/dispatch.png','03600000-0000-0000-0000-000000000002'),
 ('03610000-0000-0000-0000-000000000001','delivery','image','03600000-0000-0000-0000-000000000002/delivery.png','03600000-0000-0000-0000-000000000002');
-- Match the trigger's SECURITY DEFINER context: owner writes, authenticated caller claims.
SET LOCAL request.jwt.claim.sub='03600000-0000-0000-0000-000000000002';
SELECT throws_ok($q$UPDATE public.orders SET status='out_for_delivery' WHERE id='03610000-0000-0000-0000-000000000001'$q$,
 '23514',NULL,'dispatch requires the stored object, not just a path');
INSERT INTO storage.objects(bucket_id,name,owner_id)
VALUES('order-proofs','03600000-0000-0000-0000-000000000002/dispatch.png','03600000-0000-0000-0000-000000000002');
SELECT lives_ok($q$UPDATE public.orders SET status='out_for_delivery' WHERE id='03610000-0000-0000-0000-000000000001'$q$,
 'dispatch succeeds when its object exists');
SELECT throws_ok($q$UPDATE public.orders SET status='delivered' WHERE id='03610000-0000-0000-0000-000000000001'$q$,
 '23514',NULL,'delivery requires the stored object, not just a path');
INSERT INTO storage.objects(bucket_id,name,owner_id)
VALUES('order-proofs','03600000-0000-0000-0000-000000000002/delivery.png','03600000-0000-0000-0000-000000000002');
SELECT lives_ok($q$UPDATE public.orders SET status='delivered' WHERE id='03610000-0000-0000-0000-000000000001'$q$,
 'delivery succeeds when its object exists');
SELECT * FROM finish();
ROLLBACK;

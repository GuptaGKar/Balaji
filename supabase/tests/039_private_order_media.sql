-- Known paths must not disclose customer media to unrelated or anonymous users.
BEGIN;
SELECT plan(10);
\ir helpers/role_fixtures.inc
SELECT pg_temp.make_profile('03900000-0000-0000-0000-000000000001','customer','Media Customer');
SELECT pg_temp.make_profile('03900000-0000-0000-0000-000000000002','customer','Other Customer');
SELECT pg_temp.make_profile('03900000-0000-0000-0000-000000000003','staff','Media Staff');
SELECT pg_temp.make_profile('03900000-0000-0000-0000-000000000004','delivery','Assigned Rider');
SELECT pg_temp.make_profile('03900000-0000-0000-0000-000000000005','delivery','Other Rider');
INSERT INTO public.orders(id,public_order_number,customer_id,address_snapshot,status,image_path) VALUES
 ('03910000-0000-0000-0000-000000000001','PRIVATE-MEDIA','03900000-0000-0000-0000-000000000001','{}','out_for_delivery','03900000-0000-0000-0000-000000000001/order.png');
INSERT INTO public.order_assignments(order_id,delivery_user_id) VALUES('03910000-0000-0000-0000-000000000001','03900000-0000-0000-0000-000000000004');
INSERT INTO storage.objects(bucket_id,name,owner_id) VALUES
 ('order-images','03900000-0000-0000-0000-000000000001/order.png','03900000-0000-0000-0000-000000000001'),
 ('order-proofs','03900000-0000-0000-0000-000000000003/dispatch.png','03900000-0000-0000-0000-000000000003');
INSERT INTO public.order_proofs(order_id,stage,kind,path,uploaded_by) VALUES
 ('03910000-0000-0000-0000-000000000001','dispatch','image','03900000-0000-0000-0000-000000000003/dispatch.png','03900000-0000-0000-0000-000000000003');
SELECT is((SELECT count(*) FROM storage.buckets WHERE id IN ('order-images','order-proofs') AND public),0::bigint,'both order-media buckets are private');
SET LOCAL ROLE anon;
SELECT is((SELECT count(*) FROM storage.objects WHERE name IN ('03900000-0000-0000-0000-000000000001/order.png','03900000-0000-0000-0000-000000000003/dispatch.png')),0::bigint,'anonymous cannot read known order-media objects');
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub='03900000-0000-0000-0000-000000000001';
SELECT is((SELECT count(*) FROM storage.objects WHERE name IN ('03900000-0000-0000-0000-000000000001/order.png','03900000-0000-0000-0000-000000000003/dispatch.png')),2::bigint,'customer reads own image and order proof');
SET LOCAL request.jwt.claim.sub='03900000-0000-0000-0000-000000000002';
SELECT is((SELECT count(*) FROM storage.objects WHERE name IN ('03900000-0000-0000-0000-000000000001/order.png','03900000-0000-0000-0000-000000000003/dispatch.png')),0::bigint,'other customer cannot read either object');
SET LOCAL request.jwt.claim.sub='03900000-0000-0000-0000-000000000003';
SELECT is((SELECT count(*) FROM storage.objects WHERE name IN ('03900000-0000-0000-0000-000000000001/order.png','03900000-0000-0000-0000-000000000003/dispatch.png')),2::bigint,'staff retains operational media reads');
SET LOCAL request.jwt.claim.sub='03900000-0000-0000-0000-000000000004';
SELECT is((SELECT count(*) FROM storage.objects WHERE name IN ('03900000-0000-0000-0000-000000000001/order.png','03900000-0000-0000-0000-000000000003/dispatch.png')),2::bigint,'assigned rider reads assigned order media');
SET LOCAL request.jwt.claim.sub='03900000-0000-0000-0000-000000000005';
SELECT is((SELECT count(*) FROM storage.objects WHERE name IN ('03900000-0000-0000-0000-000000000001/order.png','03900000-0000-0000-0000-000000000003/dispatch.png')),0::bigint,'other rider cannot read known media paths');
RESET ROLE;
UPDATE public.order_assignments SET unassigned_at=now() WHERE order_id='03910000-0000-0000-0000-000000000001';
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub='03900000-0000-0000-0000-000000000004';
SELECT is((SELECT count(*) FROM storage.objects WHERE name IN ('03900000-0000-0000-0000-000000000001/order.png','03900000-0000-0000-0000-000000000003/dispatch.png')),0::bigint,'unassignment revokes media reads');
RESET ROLE;
UPDATE public.profiles SET is_active=false WHERE id='03900000-0000-0000-0000-000000000003';
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub='03900000-0000-0000-0000-000000000003';
SELECT is((SELECT count(*) FROM storage.objects WHERE name IN ('03900000-0000-0000-0000-000000000001/order.png','03900000-0000-0000-0000-000000000003/dispatch.png')),0::bigint,'inactive staff loses media reads');
RESET ROLE;
SELECT is((SELECT count(*) FROM storage.buckets WHERE id='product-images' AND public),1::bigint,'catalogue images remain public');
SELECT * FROM finish();
ROLLBACK;

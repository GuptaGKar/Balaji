-- Proof writes must be assigned, backed by a stored object, and customer-safe.
BEGIN;
SELECT plan(8);
\ir helpers/role_fixtures.inc
SELECT pg_temp.make_profile('03500000-0000-0000-0000-000000000001','customer','Proof Customer');
SELECT pg_temp.make_profile('03500000-0000-0000-0000-000000000002','delivery','Proof Rider A');
SELECT pg_temp.make_profile('03500000-0000-0000-0000-000000000003','delivery','Proof Rider B');
INSERT INTO public.orders(id,public_order_number,customer_id,address_snapshot,status,assigned_to) VALUES
 ('03510000-0000-0000-0000-000000000001','ATTACH-A','03500000-0000-0000-0000-000000000001','{}','out_for_delivery','03500000-0000-0000-0000-000000000002'),
 ('03510000-0000-0000-0000-000000000002','ATTACH-B','03500000-0000-0000-0000-000000000001','{}','out_for_delivery','03500000-0000-0000-0000-000000000003');
INSERT INTO public.order_assignments(order_id,delivery_user_id) VALUES
 ('03510000-0000-0000-0000-000000000001','03500000-0000-0000-0000-000000000002'),
 ('03510000-0000-0000-0000-000000000002','03500000-0000-0000-0000-000000000003');
-- Metadata fixtures model completed Storage uploads; real file bytes are tested by HTTP separately.
INSERT INTO storage.objects(bucket_id,name,owner_id) VALUES
 ('order-proofs','03500000-0000-0000-0000-000000000002/proof-a.png','03500000-0000-0000-0000-000000000002'),
 ('order-proofs','03500000-0000-0000-0000-000000000003/proof-b.png','03500000-0000-0000-0000-000000000003');
INSERT INTO public.order_proofs(order_id,stage,kind,path,uploaded_by) VALUES
 ('03510000-0000-0000-0000-000000000002','delivery','image','03500000-0000-0000-0000-000000000003/proof-b.png','03500000-0000-0000-0000-000000000003');
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub='03500000-0000-0000-0000-000000000002';
SELECT is(public.attach_order_proof('03510000-0000-0000-0000-000000000001','delivery','image','03500000-0000-0000-0000-000000000002/proof-a.png')->>'order_id',
 '03510000-0000-0000-0000-000000000001','assigned rider can attach an uploaded object');
SELECT is((SELECT uploaded_by FROM public.order_proofs WHERE order_id='03510000-0000-0000-0000-000000000001'),auth.uid(),'attached proof records the real caller');
SELECT is(public.attach_order_proof('03510000-0000-0000-0000-000000000002','delivery','image','03500000-0000-0000-0000-000000000002/proof-a.png')->>'error',
 'FORBIDDEN','rider cannot overwrite another assignment proof');
RESET ROLE;
SELECT is((SELECT uploaded_by FROM public.order_proofs WHERE order_id='03510000-0000-0000-0000-000000000002'),
 '03500000-0000-0000-0000-000000000003'::uuid,'denied write preserves the other rider proof');
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub='03500000-0000-0000-0000-000000000003';
SELECT is(public.attach_order_proof('03510000-0000-0000-0000-000000000002','delivery','image','03500000-0000-0000-0000-000000000003/missing.png')->>'error',
 'VALIDATION_FAILED','a nonexistent upload cannot satisfy delivery proof');
RESET ROLE;
SELECT is((SELECT path FROM public.order_proofs WHERE order_id='03510000-0000-0000-0000-000000000002'),
 '03500000-0000-0000-0000-000000000003/proof-b.png','missing file does not replace existing proof');
UPDATE public.order_assignments SET unassigned_at=now() WHERE delivery_user_id='03500000-0000-0000-0000-000000000002';
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub='03500000-0000-0000-0000-000000000002';
SELECT is(public.attach_order_proof('03510000-0000-0000-0000-000000000001','delivery','image','03500000-0000-0000-0000-000000000002/proof-a.png')->>'error',
 'FORBIDDEN','unassignment revokes proof attachment');
SET LOCAL request.jwt.claim.sub='03500000-0000-0000-0000-000000000001';
SELECT is(public.attach_order_proof('03510000-0000-0000-0000-000000000001','delivery','image','03500000-0000-0000-0000-000000000001/customer.png')->>'error',
 'FORBIDDEN','customer cannot attach delivery proof');
SELECT * FROM finish();
ROLLBACK;

-- Real authenticated deletion calls: newly introduced free text and media must not be lost or misreported.
BEGIN;
SELECT plan(15);
INSERT INTO auth.users(id,email,aud,role) VALUES
('e1000000-0000-4000-8000-000000000001','del-text@test.invalid','authenticated','authenticated'),
('e1000000-0000-4000-8000-000000000002','del-image@test.invalid','authenticated','authenticated'),
('e1000000-0000-4000-8000-000000000003','del-proof@test.invalid','authenticated','authenticated'),
('e1000000-0000-4000-8000-000000000004','del-orphan@test.invalid','authenticated','authenticated'),
('e1000000-0000-4000-8000-000000000005','del-request-photo@test.invalid','authenticated','authenticated'),
('e1000000-0000-4000-8000-000000000006','del-keep@test.invalid','authenticated','authenticated');
INSERT INTO public.orders(id,public_order_number,customer_id,address_snapshot,status,payment_method,payment_status,item_subtotal_paise,delivery_fee_paise,tax_paise,grand_total_paise,image_path) VALUES
('f1000000-0000-4000-8000-000000000001','QA-DEL-TEXT','e1000000-0000-4000-8000-000000000001','{}','delivered','cod','paid',100,0,0,100,NULL),
('f1000000-0000-4000-8000-000000000002','QA-DEL-IMAGE','e1000000-0000-4000-8000-000000000002','{}','delivered','cod','paid',100,0,0,100,'e1000000-0000-4000-8000-000000000002/photo.png'),
('f1000000-0000-4000-8000-000000000003','QA-DEL-PROOF','e1000000-0000-4000-8000-000000000003','{}','delivered','cod','paid',100,0,0,100,NULL),
('f1000000-0000-4000-8000-000000000006','QA-DEL-KEEP','e1000000-0000-4000-8000-000000000006','{}','delivered','cod','paid',100,0,0,100,NULL);
INSERT INTO public.product_requests(customer_id,item_name,note,photo_path) VALUES
('e1000000-0000-4000-8000-000000000001','Synthetic delete request','Synthetic personal note',NULL),
('e1000000-0000-4000-8000-000000000002','Synthetic image request','Keep while blocked',NULL),
('e1000000-0000-4000-8000-000000000005','Synthetic photo request','Keep while blocked','e1000000-0000-4000-8000-000000000005/photo.png'),
('e1000000-0000-4000-8000-000000000006','Synthetic keep request','Keep personal note',NULL);
INSERT INTO public.order_reviews(order_id,customer_id,rating,comment) VALUES
('f1000000-0000-4000-8000-000000000001','e1000000-0000-4000-8000-000000000001',4,'Synthetic personal review'),
('f1000000-0000-4000-8000-000000000006','e1000000-0000-4000-8000-000000000006',5,'Keep personal review');
INSERT INTO public.order_proofs(order_id,stage,kind,path) VALUES
('f1000000-0000-4000-8000-000000000003','delivery','image','staff-owned/synthetic.png');
-- These metadata fixtures are rolled back with the transaction; production object deletion uses Storage API only.
INSERT INTO storage.objects(bucket_id,name) VALUES
('order-images','e1000000-0000-4000-8000-000000000004/orphan.png'),
('order-proofs','e1000000-0000-4000-8000-000000000004/orphan.png');
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','e1000000-0000-4000-8000-000000000001',true);
SELECT is((public.delete_my_account('DELETE')->>'success')::boolean,true,'text-only customer deletion succeeds');
RESET ROLE;
SELECT is((SELECT count(*)::int FROM public.product_requests WHERE customer_id='e1000000-0000-4000-8000-000000000001'),0,'deletion removes customer requests containing arbitrary personal text');
SELECT is((SELECT count(*)::int FROM public.order_reviews WHERE customer_id='e1000000-0000-4000-8000-000000000001'),0,'deletion removes customer reviews containing arbitrary personal text');
SELECT is((SELECT grand_total_paise FROM public.orders WHERE id='f1000000-0000-4000-8000-000000000001'),100::bigint,'financial order amount remains');
SELECT is((SELECT count(*)::int FROM public.product_requests WHERE customer_id='e1000000-0000-4000-8000-000000000006'),1,'another customer request is preserved');
SELECT is((SELECT count(*)::int FROM public.order_reviews WHERE customer_id='e1000000-0000-4000-8000-000000000006'),1,'another customer review is preserved');
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','e1000000-0000-4000-8000-000000000002',true);
SELECT is(public.delete_my_account('DELETE')->>'error','MEDIA_CLEANUP_REQUIRED','order image blocks misleading deletion success');
SELECT like(public.delete_my_account('DELETE')->>'message','%contact%','blocked deletion explains how to request help');
RESET ROLE;
SELECT is((SELECT is_active FROM public.profiles WHERE id='e1000000-0000-4000-8000-000000000002'),true,'blocked customer remains active');
SELECT is((SELECT image_path FROM public.orders WHERE id='f1000000-0000-4000-8000-000000000002'),'e1000000-0000-4000-8000-000000000002/photo.png','blocked order media path is retained for cleanup');
SELECT is((SELECT count(*)::int FROM public.product_requests WHERE customer_id='e1000000-0000-4000-8000-000000000002'),1,'blocked request remains available');
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','e1000000-0000-4000-8000-000000000003',true);
SELECT is(public.delete_my_account('DELETE')->>'error','MEDIA_CLEANUP_REQUIRED','staff-owned proof linked to customer order blocks deletion');
SELECT set_config('request.jwt.claim.sub','e1000000-0000-4000-8000-000000000004',true);
SELECT is(public.delete_my_account('DELETE')->>'error','MEDIA_CLEANUP_REQUIRED','unlinked customer-owned uploads block deletion');
SELECT set_config('request.jwt.claim.sub','e1000000-0000-4000-8000-000000000005',true);
SELECT is(public.delete_my_account('DELETE')->>'error','MEDIA_CLEANUP_REQUIRED','request photo path blocks deletion');
RESET ROLE;
SELECT is((SELECT count(*)::int FROM storage.objects WHERE name LIKE 'e1000000-0000-4000-8000-000000000004/%'),2,'orphan object metadata is preserved for Storage API cleanup');
SELECT * FROM finish();
ROLLBACK;

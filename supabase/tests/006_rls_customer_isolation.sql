-- Customer fixtures exist on both sides; queries run as authenticated, never table owner.
BEGIN;
SELECT plan(12);
\ir helpers/role_fixtures.inc
SELECT pg_temp.make_profile('00600000-0000-0000-0000-000000000001','customer','Customer A');
SELECT pg_temp.make_profile('00600000-0000-0000-0000-000000000002','customer','Customer B');
INSERT INTO public.orders(id,public_order_number,customer_id,address_snapshot) VALUES
 ('00610000-0000-0000-0000-000000000001','CUST-A-ORDER','00600000-0000-0000-0000-000000000001','{}'),
 ('00610000-0000-0000-0000-000000000002','CUST-B-ORDER','00600000-0000-0000-0000-000000000002','{}');
INSERT INTO public.order_items(order_id,sku_snapshot,name_snapshot,quantity_type_snapshot,unit_label_snapshot,price_basis_qty_snapshot,qty_base,unit_price_paise,line_total_paise) VALUES
 ('00610000-0000-0000-0000-000000000001','A','Item A','PACKED','pack',1,1,100,100),
 ('00610000-0000-0000-0000-000000000002','B','Item B','PACKED','pack',1,1,100,100);
INSERT INTO public.addresses(customer_id,recipient_name,phone,address_lines,postal_code) VALUES
 ('00600000-0000-0000-0000-000000000001','A','9000000001','Synthetic A','122001'),
 ('00600000-0000-0000-0000-000000000002','B','9000000002','Synthetic B','122001');
INSERT INTO public.products(sku,name,quantity_type,price_paise,price_basis_qty,unit_label,step_qty,min_qty,stock_qty)
VALUES('RLS-CUSTOMER-PRODUCT','Public fixture','PACKED',100,1,'pack',1,1,1);
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub='00600000-0000-0000-0000-000000000001';
SELECT is((SELECT count(*) FROM public.profiles WHERE id=auth.uid()),1::bigint,'customer reads own existing profile');
SELECT is((SELECT count(*) FROM public.profiles WHERE id='00600000-0000-0000-0000-000000000002'),0::bigint,'other customer profile is filtered');
SELECT is((SELECT count(*) FROM public.orders WHERE id='00610000-0000-0000-0000-000000000001'),1::bigint,'customer reads own existing order');
SELECT is((SELECT count(*) FROM public.orders WHERE id='00610000-0000-0000-0000-000000000002'),0::bigint,'other customer order is filtered');
SELECT is((SELECT count(*) FROM public.order_items WHERE order_id='00610000-0000-0000-0000-000000000001'),1::bigint,'customer reads own existing item');
SELECT is((SELECT count(*) FROM public.order_items WHERE order_id='00610000-0000-0000-0000-000000000002'),0::bigint,'other customer item is filtered');
SELECT is((SELECT count(*) FROM public.addresses WHERE customer_id=auth.uid()),1::bigint,'customer reads own existing address');
SELECT is((SELECT count(*) FROM public.addresses WHERE customer_id='00600000-0000-0000-0000-000000000002'),0::bigint,'other customer address is filtered');
SELECT is((SELECT count(*) FROM public.audit_logs),0::bigint,'customer cannot read audit records');
SELECT is((SELECT count(*) FROM public.store_settings),1::bigint,'customer can read public store configuration');
SELECT throws_ok($q$INSERT INTO public.orders(public_order_number,customer_id,address_snapshot) VALUES('DIRECT-CUSTOMER',auth.uid(),'{}')$q$,'42501',NULL,'customer cannot bypass transactional checkout');
SELECT is((SELECT count(*) FROM public.products WHERE sku='RLS-CUSTOMER-PRODUCT'),1::bigint,'customer can browse a published product');
SELECT * FROM finish();
ROLLBACK;

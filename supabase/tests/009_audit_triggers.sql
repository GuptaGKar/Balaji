-- Append-only audit behavior under a real client manager, with populated targets.
BEGIN;
SELECT plan(7);
\ir helpers/role_fixtures.inc
SELECT pg_temp.make_profile('00900000-0000-0000-0000-000000000001','manager','Audit Manager');
SELECT pg_temp.make_profile('00900000-0000-0000-0000-000000000002','delivery','Audit Rider');
SELECT pg_temp.make_profile('00900000-0000-0000-0000-000000000003','customer','Audit Customer');
INSERT INTO public.products(sku,name,quantity_type,price_paise,price_basis_qty,unit_label,step_qty,min_qty,stock_qty)
VALUES('AUDIT-PRODUCT','Audit product','PACKED',100,1,'pack',1,1,10);
INSERT INTO public.orders(id,public_order_number,customer_id,address_snapshot,status)
VALUES('00910000-0000-0000-0000-000000000001','AUDIT-ASSIGN','00900000-0000-0000-0000-000000000003','{}','ready_for_dispatch');
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub='00900000-0000-0000-0000-000000000001';
UPDATE public.store_settings SET delivery_fee_paise=5100;
SELECT is((SELECT count(*) FROM public.audit_logs WHERE actor_id=auth.uid() AND action='UPDATE' AND entity_type='store_settings' AND after->>'delivery_fee_paise'='5100'),1::bigint,'manager setting edit writes an actor-attributed audit row');
UPDATE public.audit_logs SET action='TAMPERED' WHERE actor_id=auth.uid() AND entity_type='store_settings' AND after->>'delivery_fee_paise'='5100';
SELECT is((SELECT count(*) FROM public.audit_logs WHERE actor_id=auth.uid() AND action='UPDATE' AND entity_type='store_settings' AND after->>'delivery_fee_paise'='5100'),1::bigint,'client UPDATE cannot alter an existing audit row');
DELETE FROM public.audit_logs WHERE actor_id=auth.uid() AND entity_type='store_settings' AND after->>'delivery_fee_paise'='5100';
SELECT is((SELECT count(*) FROM public.audit_logs WHERE actor_id=auth.uid() AND action='UPDATE' AND entity_type='store_settings' AND after->>'delivery_fee_paise'='5100'),1::bigint,'client DELETE cannot remove an existing audit row');
UPDATE public.products SET price_paise=200 WHERE sku='AUDIT-PRODUCT';
SELECT ok(EXISTS(SELECT 1 FROM public.audit_logs WHERE actor_id=auth.uid() AND entity_type='products' AND after->>'sku'='AUDIT-PRODUCT' AND after->>'price_paise'='200'),'product price change is audited');
INSERT INTO public.service_areas(kind,value) VALUES('pincode','888889');
SELECT ok(EXISTS(SELECT 1 FROM public.audit_logs WHERE actor_id=auth.uid() AND entity_type='service_areas' AND after->>'value'='888889'),'service-area change is audited');
SELECT public.set_rider_active('00900000-0000-0000-0000-000000000002',false);
SELECT ok(EXISTS(SELECT 1 FROM public.audit_logs WHERE actor_id=auth.uid() AND entity_type='profiles' AND after->>'id'='00900000-0000-0000-0000-000000000002' AND after->>'is_active'='false'),'rider deactivation is audited');
SELECT public.set_rider_active('00900000-0000-0000-0000-000000000002',true);
SELECT public.assign_order('00910000-0000-0000-0000-000000000001','00900000-0000-0000-0000-000000000002');
SELECT ok(EXISTS(SELECT 1 FROM public.audit_logs WHERE actor_id=auth.uid() AND action='ASSIGN_ORDER' AND after->>'order_id'='00910000-0000-0000-0000-000000000001'),'server assignment is audited');
SELECT * FROM finish();
ROLLBACK;

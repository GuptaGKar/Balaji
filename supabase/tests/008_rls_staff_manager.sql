-- Staff operational reads and manager controls under actual client roles.
BEGIN;
SELECT plan(10);
\ir helpers/role_fixtures.inc
SELECT pg_temp.make_profile('00800000-0000-0000-0000-000000000001','staff','Staff');
SELECT pg_temp.make_profile('00800000-0000-0000-0000-000000000002','manager','Manager');
SELECT pg_temp.make_profile('00800000-0000-0000-0000-000000000003','customer','Unchanged Customer');
SELECT pg_temp.make_profile('00800000-0000-0000-0000-000000000004','delivery','Rider');
INSERT INTO public.orders(id,public_order_number,customer_id,address_snapshot,status) VALUES
 ('00810000-0000-0000-0000-000000000001','STAFF-ORDER','00800000-0000-0000-0000-000000000003','{}','ready_for_dispatch');
INSERT INTO public.service_areas(kind,value) VALUES('pincode','888887');
INSERT INTO public.payment_events(provider,provider_event_id,payload_hash) VALUES('local-test','RLS-PAYMENT-EVENT','synthetic');
UPDATE public.store_settings SET delivery_fee_paise=3000;
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub='00800000-0000-0000-0000-000000000001';
SELECT is((SELECT count(*) FROM public.orders WHERE id='00810000-0000-0000-0000-000000000001'),1::bigint,'staff sees operational order');
SELECT is((SELECT count(*) FROM public.profiles WHERE id='00800000-0000-0000-0000-000000000003'),1::bigint,'staff sees customer profile');
UPDATE public.profiles SET display_name='TAMPERED' WHERE id='00800000-0000-0000-0000-000000000003';
RESET ROLE;
SELECT is((SELECT display_name FROM public.profiles WHERE id='00800000-0000-0000-0000-000000000003'),'Unchanged Customer','staff profile edit affects no row');
SET LOCAL ROLE authenticated;
UPDATE public.store_settings SET delivery_fee_paise=1000;
RESET ROLE;
SELECT is((SELECT delivery_fee_paise FROM public.store_settings),3000::bigint,'staff configuration edit affects no row');
SET LOCAL ROLE authenticated;
SELECT is((SELECT count(*) FROM public.service_areas WHERE value='888887'),1::bigint,'staff sees active service area');
SET LOCAL request.jwt.claim.sub='00800000-0000-0000-0000-000000000002';
SELECT is((SELECT count(*) FROM public.orders WHERE id='00810000-0000-0000-0000-000000000001'),1::bigint,'manager sees operational order');
UPDATE public.store_settings SET delivery_fee_paise=2000;
SELECT is((SELECT delivery_fee_paise FROM public.store_settings),2000::bigint,'manager configuration edit succeeds');
SELECT ok(EXISTS(SELECT 1 FROM public.audit_logs WHERE actor_id=auth.uid() AND entity_type='store_settings' AND after->>'delivery_fee_paise'='2000'),'manager sees the audit record for their edit');
SELECT is((SELECT count(*) FROM public.payment_events WHERE provider_event_id='RLS-PAYMENT-EVENT'),0::bigint,'manager cannot read internal payment event');
SELECT public.assign_order('00810000-0000-0000-0000-000000000001','00800000-0000-0000-0000-000000000004');
SELECT is((SELECT count(*) FROM public.order_assignments WHERE order_id='00810000-0000-0000-0000-000000000001' AND delivery_user_id='00800000-0000-0000-0000-000000000004' AND unassigned_at IS NULL),1::bigint,'manager assigns an active rider through the server RPC');
SELECT * FROM finish();
ROLLBACK;

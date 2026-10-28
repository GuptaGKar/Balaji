-- pgTAP: customer self-service account deletion (Play Store requirement)
-- Verifies confirmation, active-order guard, PII erasure, anonymised financial
-- records, auth credential removal, and account isolation.

BEGIN;
SELECT plan(19);

INSERT INTO auth.users (instance_id, id, aud, role, email, phone, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES
  ('00000000-0000-0000-0000-000000000000', 'e1e1e1e1-1111-4111-8111-111111111111',
   'authenticated', 'authenticated', 'delete-me@test.invalid', '+919800000001', 'x', now(), now(), now()),
  ('00000000-0000-0000-0000-000000000000', 'e2e2e2e2-2222-4222-8222-222222222222',
   'authenticated', 'authenticated', 'keep-me@test.invalid', '+919800000002', 'x', now(), now(), now()),
  ('00000000-0000-0000-0000-000000000000', 'e3e3e3e3-3333-4333-8333-333333333333',
   'authenticated', 'authenticated', 'delete-manager@test.invalid', '+919800000003', 'x', now(), now(), now());

INSERT INTO public.profiles (id, role, display_name, phone)
VALUES
  ('e1e1e1e1-1111-4111-8111-111111111111', 'customer', 'Delete Me', '9800000001'),
  ('e2e2e2e2-2222-4222-8222-222222222222', 'customer', 'Keep Me', '9800000002'),
  ('e3e3e3e3-3333-4333-8333-333333333333', 'manager', 'Delete Manager', '9800000003');

INSERT INTO public.addresses (customer_id, label, recipient_name, phone, address_lines, city, state, postal_code, is_default)
VALUES
  ('e1e1e1e1-1111-4111-8111-111111111111', 'Home', 'Delete Me', '9800000001', '1 Test Lane', 'Kolkata', 'West Bengal', '700001', true),
  ('e2e2e2e2-2222-4222-8222-222222222222', 'Home', 'Keep Me', '9800000002', '2 Test Lane', 'Kolkata', 'West Bengal', '700002', true);

INSERT INTO public.orders (
  id, public_order_number, customer_id, address_snapshot, status,
  payment_method, payment_status, item_subtotal_paise, delivery_fee_paise,
  tax_paise, grand_total_paise, customer_note
) VALUES
  ('f1f1f1f1-1111-4111-8111-111111111111', 'DEL-ACTIVE-001',
   'e1e1e1e1-1111-4111-8111-111111111111',
   jsonb_build_object('recipient_name', 'Delete Me', 'phone', '9800000001', 'address_lines', '1 Test Lane'),
   'accepted', 'cod', 'unpaid', 10000, 3000, 0, 13000, 'Leave at door'),
  ('f2f2f2f2-2222-4222-8222-222222222222', 'DEL-DONE-001',
   'e1e1e1e1-1111-4111-8111-111111111111',
   jsonb_build_object('recipient_name', 'Delete Me', 'phone', '9800000001', 'address_lines', '1 Test Lane'),
   'delivered', 'cod', 'paid', 10000, 3000, 0, 13000, 'Leave at door'),
  ('f3f3f3f3-3333-4333-8333-333333333333', 'DEL-KEEP-001',
   'e2e2e2e2-2222-4222-8222-222222222222',
   jsonb_build_object('recipient_name', 'Keep Me', 'phone', '9800000002', 'address_lines', '2 Test Lane'),
   'delivered', 'cod', 'paid', 5000, 3000, 0, 8000, NULL);

INSERT INTO public.order_status_events (order_id, from_status, to_status, actor_id, reason)
VALUES
  ('f2f2f2f2-2222-4222-8222-222222222222', 'out_for_delivery', 'delivered',
   'e3e3e3e3-3333-4333-8333-333333333333', 'Call the customer on 9800000001');

INSERT INTO public.notification_outbox (order_id, channel, template)
VALUES ('f2f2f2f2-2222-4222-8222-222222222222', 'sms', 'order_delivered');

INSERT INTO public.bills (bill_number, source, order_id, customer_name, customer_phone, subtotal_paise, delivery_fee_paise, grand_total_paise, payment_method, payment_status)
VALUES
  ('DEL-BILL-001', 'ORDER', 'f2f2f2f2-2222-4222-8222-222222222222', 'Delete Me', '9800000001', 10000, 3000, 13000, 'cod', 'paid'),
  ('DEL-BILL-KEEP', 'ORDER', 'f3f3f3f3-3333-4333-8333-333333333333', 'Keep Me', '9800000002', 5000, 3000, 8000, 'cod', 'paid');

SET LOCAL request.jwt.claims.role = 'authenticated';
SET LOCAL request.jwt.claims.sub = 'e3e3e3e3-3333-4333-8333-333333333333';

SELECT results_eq(
  $$SELECT delete_my_account('DELETE')->>'error'$$,
  ARRAY['FORBIDDEN'],
  'staff accounts cannot self-delete'
);

SET LOCAL request.jwt.claims.sub = 'e1e1e1e1-1111-4111-8111-111111111111';

SELECT results_eq(
  $$SELECT delete_my_account('yes')->>'error'$$,
  ARRAY['CONFIRMATION_REQUIRED'],
  'deletion requires the DELETE confirmation'
);

SELECT results_eq(
  $$SELECT delete_my_account('DELETE')->>'error'$$,
  ARRAY['ACTIVE_ORDERS'],
  'active orders block account deletion'
);

-- Delivered now requires a delivery proof (order-proofs rule).
INSERT INTO public.order_proofs (order_id, stage, kind, path)
VALUES ('f1f1f1f1-1111-4111-8111-111111111111', 'delivery', 'image',
        'e3e3e3e3-3333-4333-8333-333333333333/proof/delivery.jpg')
ON CONFLICT (order_id, stage) DO NOTHING;

UPDATE public.orders SET status = 'delivered' WHERE id = 'f1f1f1f1-1111-4111-8111-111111111111';

SELECT results_eq(
  $$SELECT (delete_my_account('DELETE')->>'success')::boolean$$,
  ARRAY[true],
  'customer can delete after orders reach a terminal state'
);

SELECT is(
  (SELECT count(*)::int FROM public.addresses WHERE customer_id = 'e1e1e1e1-1111-4111-8111-111111111111'),
  0,
  'saved addresses are deleted'
);

SELECT is(
  (SELECT display_name FROM public.profiles WHERE id = 'e1e1e1e1-1111-4111-8111-111111111111'),
  'Deleted customer',
  'profile display name is anonymised'
);

SELECT is(
  (SELECT phone FROM public.profiles WHERE id = 'e1e1e1e1-1111-4111-8111-111111111111'),
  NULL::text,
  'profile phone is removed'
);

SELECT is(
  (SELECT is_active FROM public.profiles WHERE id = 'e1e1e1e1-1111-4111-8111-111111111111'),
  false,
  'profile is deactivated'
);

SELECT is(
  (SELECT phone FROM auth.users WHERE id = 'e1e1e1e1-1111-4111-8111-111111111111'),
  NULL::text,
  'auth phone is removed'
);

SELECT isnt(
  (SELECT banned_until FROM auth.users WHERE id = 'e1e1e1e1-1111-4111-8111-111111111111'),
  NULL::timestamptz,
  'auth account is banned'
);

SELECT is(
  (SELECT address_snapshot FROM public.orders WHERE id = 'f2f2f2f2-2222-4222-8222-222222222222'),
  jsonb_build_object('deleted', true),
  'order address snapshot is erased'
);

SELECT is(
  (SELECT customer_note FROM public.orders WHERE id = 'f2f2f2f2-2222-4222-8222-222222222222'),
  NULL::text,
  'customer note is erased'
);

SELECT is(
  (SELECT customer_name FROM public.bills WHERE bill_number = 'DEL-BILL-001'),
  NULL::text,
  'bill customer name is erased'
);

SELECT is(
  (SELECT customer_phone FROM public.bills WHERE bill_number = 'DEL-BILL-001'),
  NULL::text,
  'bill customer phone is erased'
);

SELECT is(
  (SELECT reason FROM public.order_status_events WHERE order_id = 'f2f2f2f2-2222-4222-8222-222222222222'),
  NULL::text,
  'free-text status reason is erased'
);

SELECT is(
  (SELECT count(*)::int FROM public.notification_outbox WHERE order_id = 'f2f2f2f2-2222-4222-8222-222222222222'),
  0,
  'queued notifications are removed'
);

SELECT is(
  (SELECT count(*)::int FROM public.audit_logs
   WHERE entity_type = 'profiles'
     AND entity_id = 'e1e1e1e1-1111-4111-8111-111111111111'
     AND (before->>'phone' IS NOT NULL OR after->>'phone' IS NOT NULL)),
  0,
  'audit snapshots no longer contain the profile phone'
);

SELECT is(
  (SELECT display_name FROM public.profiles WHERE id = 'e2e2e2e2-2222-4222-8222-222222222222'),
  'Keep Me',
  'another customer is unaffected'
);

SELECT results_eq(
  $$SELECT delete_my_account('DELETE')->>'error'$$,
  ARRAY['NOT_ACTIVE'],
  'a deleted account cannot be deleted twice'
);

SELECT * FROM finish();
ROLLBACK;

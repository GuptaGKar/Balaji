-- pgTAP: order state machine (IMPLEMENTATION_PLAN.md §4.3)
-- The order_transitions table is the single source of truth that the
-- M4 transition_order() RPC enforces.

BEGIN;
SELECT plan(5);

-- 1. pending_review -> accepted allows staff and manager.
SELECT results_eq(
  $sql$SELECT array_length(allowed_roles, 1)
       FROM order_transitions
       WHERE from_status = 'pending_review' AND to_status = 'accepted'$sql$,
  $$VALUES (2)$$,
  'pending_review -> accepted allows staff and manager');

-- 2. Customers may only cancel while the order is pending review
--    (default policy: cancel until acceptance; afterwards staff/manager).
SELECT results_eq(
  $sql$SELECT to_status
       FROM order_transitions
       WHERE from_status = 'pending_review'
         AND allowed_roles @> ARRAY['customer']::user_role[]$sql$,
  $$VALUES ('cancelled'::order_status)$$,
  'customer can only cancel from pending_review');

-- 3. The delivery role appears in exactly the two rider transitions
--    (ready_for_dispatch -> out_for_delivery, out_for_delivery -> delivered/failed
--     count together: dispatch, delivered, delivery_failed).
SELECT results_eq(
  $sql$SELECT count(*) FROM order_transitions
       WHERE allowed_roles @> ARRAY['delivery']::user_role[]$sql$,
  $$VALUES (3)$$,
  'delivery role appears in exactly the rider transitions');

-- 4. delivered, rejected and cancelled are terminal (no outgoing rows).
SELECT results_eq(
  $sql$SELECT count(*) FROM order_transitions
       WHERE from_status IN ('delivered', 'rejected', 'cancelled')$sql$,
  $$VALUES (0)$$,
  'delivered, rejected and cancelled are terminal');

-- 5. Every transition row lists at least one allowed role.
SELECT results_eq(
  $sql$SELECT count(*) FROM order_transitions
       WHERE coalesce(array_length(allowed_roles, 1), 0) < 1$sql$,
  $$VALUES (0)$$,
  'every transition allows at least one role');

SELECT * FROM finish();
ROLLBACK;

-- Client order writes must enter the authorized transactional RPCs.
BEGIN;
SELECT plan(6);
SELECT ok(NOT has_table_privilege('authenticated','public.orders','INSERT'),'client cannot insert an order directly');
SELECT ok(NOT has_table_privilege('authenticated','public.orders','UPDATE'),'client cannot update an order directly');
SELECT ok(NOT has_table_privilege('authenticated','public.orders','DELETE'),'client cannot delete an order directly');
SELECT ok(NOT has_table_privilege('anon','public.orders','UPDATE'),'anonymous role has no order update privilege');
SELECT ok(NOT has_table_privilege('authenticated','public.orders','TRUNCATE'),'client cannot truncate orders outside RLS');
SELECT ok(has_function_privilege('authenticated','public.transition_order(uuid,public.order_status,text)','EXECUTE'),'authenticated clients retain the transition RPC');
SELECT * FROM finish();
ROLLBACK;

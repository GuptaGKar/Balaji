-- Pricing and threshold regressions use current JSON RPC results and integer paise.
BEGIN;
SELECT plan(21);
UPDATE public.store_settings SET store_open=true, delivery_fee_paise=3000,
 delivery_charge_threshold_paise=25000, free_delivery_threshold_paise=NULL,
 mid_tier_fee_paise=NULL, online_payment_enabled=false, tax_mode='none', tax_rate_bps=0;
SELECT results_eq($q$SELECT fee_paise, below_charge_threshold FROM public.compute_delivery_fee(24999)$q$, $$VALUES (3000::bigint, true)$$, 'TH-1: below threshold is allowed with fee');
SELECT results_eq($q$SELECT fee_paise, below_charge_threshold FROM public.compute_delivery_fee(25000)$q$, $$VALUES (0::bigint, false)$$, 'TH-2: exact threshold has no fee');
SELECT results_eq($q$SELECT fee_paise, below_charge_threshold FROM public.compute_delivery_fee(25001)$q$, $$VALUES (0::bigint, false)$$, 'TH-3: above threshold has no fee');
UPDATE public.store_settings SET delivery_fee_paise=5000, delivery_charge_threshold_paise=30000;
SELECT results_eq($q$SELECT fee_paise FROM public.compute_delivery_fee(29999)$q$, $$VALUES (5000::bigint)$$, 'TH-4: manager configuration takes effect');
UPDATE public.store_settings SET delivery_fee_paise=3000, delivery_charge_threshold_paise=25000, free_delivery_threshold_paise=50000, mid_tier_fee_paise=NULL;
SELECT results_eq($q$SELECT fee_paise FROM public.compute_delivery_fee(24999)$q$, $$VALUES (3000::bigint)$$, 'TH-5a: configured free threshold without mid-tier fee');
SELECT results_eq($q$SELECT fee_paise FROM public.compute_delivery_fee(25000)$q$, $$VALUES (3000::bigint)$$, 'TH-5b: configured free threshold without mid-tier fee');
SELECT results_eq($q$SELECT fee_paise FROM public.compute_delivery_fee(49999)$q$, $$VALUES (3000::bigint)$$, 'TH-5c: configured free threshold without mid-tier fee');
SELECT results_eq($q$SELECT fee_paise FROM public.compute_delivery_fee(50000)$q$, $$VALUES (0::bigint)$$, 'TH-5d: configured free threshold without mid-tier fee');
UPDATE public.store_settings SET mid_tier_fee_paise=2000;
SELECT results_eq($q$SELECT fee_paise FROM public.compute_delivery_fee(24999)$q$, $$VALUES (3000::bigint)$$, 'TH-6a: configured mid-tier fee');
SELECT results_eq($q$SELECT fee_paise FROM public.compute_delivery_fee(25000)$q$, $$VALUES (2000::bigint)$$, 'TH-6b: configured mid-tier fee');
SELECT results_eq($q$SELECT fee_paise FROM public.compute_delivery_fee(49999)$q$, $$VALUES (2000::bigint)$$, 'TH-6c: configured mid-tier fee');
SELECT results_eq($q$SELECT fee_paise FROM public.compute_delivery_fee(50000)$q$, $$VALUES (0::bigint)$$, 'TH-6d: configured mid-tier fee');
SELECT throws_ok($q$UPDATE public.store_settings SET free_delivery_threshold_paise=20000$q$, NULL, NULL, 'TH-7: free threshold below charge threshold is rejected');
SELECT lives_ok($q$UPDATE public.store_settings SET delivery_fee_paise=4000$q$, 'TH-10: setting change succeeds');
SELECT is((SELECT count(*) FROM public.audit_logs WHERE entity_type='store_settings' AND action='UPDATE' AND after->>'delivery_fee_paise'='4000'), 1::bigint, 'TH-10: setting change has one audit record');
UPDATE public.store_settings SET delivery_fee_paise=3000, delivery_charge_threshold_paise=25000, free_delivery_threshold_paise=NULL, mid_tier_fee_paise=NULL;

INSERT INTO public.products (sku,name,quantity_type,price_paise,price_basis_qty,unit_label,step_qty,min_qty,stock_qty)
VALUES ('TEST-PACKED','Test packed','PACKED',4500,1,'pack',1,1,100),
 ('TEST-WEIGHTED-KG','Test weighted kg','WEIGHTED',6000,1000,'kg',10,100,10000),
 ('TEST-WEIGHTED-100G','Test weighted 100g','WEIGHTED',990,100,'100 g',1,1,10000);

-- Helper constructs real RPC inputs; expected amounts below are literal hand calculations.
CREATE FUNCTION pg_temp.quote_sku(p_sku text,p_qty bigint) RETURNS jsonb
LANGUAGE sql SET search_path='' AS $fn$
 SELECT public.quote_cart(jsonb_build_array(jsonb_build_object(
  'product_id',(SELECT id FROM public.products WHERE sku=p_sku),'qty_base',p_qty)))
$fn$;
SELECT results_eq($q$WITH quoted AS (SELECT pg_temp.quote_sku('TEST-PACKED',3) AS body)
 SELECT (body->>'item_subtotal_paise')::bigint,(body->>'grand_total_paise')::bigint FROM quoted$q$,
 $$VALUES (13500::bigint,16500::bigint)$$,'P-1: three packs at 4500 paise plus delivery');
SELECT is((pg_temp.quote_sku('TEST-WEIGHTED-KG',250)->>'item_subtotal_paise')::bigint,1500::bigint,'P-2: 250g at 6000 paise/kg');
SELECT is((pg_temp.quote_sku('TEST-WEIGHTED-100G',333)->>'item_subtotal_paise')::bigint,3297::bigint,'P-3: 333g at 990 paise/100g rounds half up');
SELECT is((pg_temp.quote_sku('TEST-WEIGHTED-KG',1500)->>'item_subtotal_paise')::bigint,9000::bigint,'P-4: 1500g at 6000 paise/kg');
SELECT is((pg_temp.quote_sku('TEST-PACKED',0)#>>'{lines,0,available}')::boolean,false,'P-5a: zero quantity line is unavailable');
SELECT is((pg_temp.quote_sku('TEST-PACKED',-1)#>>'{lines,0,available}')::boolean,false,'P-5b: negative quantity line is unavailable');
SELECT * FROM finish();
ROLLBACK;

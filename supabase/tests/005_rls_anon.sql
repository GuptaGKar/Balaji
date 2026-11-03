-- Anonymous catalogue visibility and private-data filtering under the actual SQL role.
BEGIN;
SELECT plan(4);
INSERT INTO public.products(sku,name,quantity_type,price_paise,price_basis_qty,unit_label,step_qty,min_qty,stock_qty,active)
VALUES('ANON-ACTIVE','Visible fixture','PACKED',100,1,'pack',1,1,1,true),('ANON-HIDDEN','Hidden fixture','PACKED',100,1,'pack',1,1,1,false);
SET LOCAL ROLE anon;
SELECT is((SELECT count(*) FROM public.products WHERE sku='ANON-ACTIVE'),1::bigint,'anon can browse a published product');
SELECT is((SELECT count(*) FROM public.products WHERE sku='ANON-HIDDEN'),0::bigint,'anon cannot browse an inactive product');
SELECT is((SELECT count(*) FROM public.orders),0::bigint,'anon sees no private orders');
SELECT is((SELECT count(*) FROM public.profiles),0::bigint,'anon sees no private profiles');
SELECT * FROM finish();
ROLLBACK;

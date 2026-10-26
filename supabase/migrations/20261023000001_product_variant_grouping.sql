-- Additive product-variant grouping (Option A).
--
-- The catalogue stays a flat `products` table: one row is still one sellable
-- SKU with its own price, stock and availability. This migration only adds the
-- ability to *group* rows that are pack sizes of the same base product, so the
-- client can render one card with a pack-size selector.
--
-- Why a grouping column and not a product_variants table: quote_cart,
-- place_order, inventory_movements, order_items and bill_items all already key
-- on products.id. Treating each pack as its own product row means the pricing
-- engine, stock, order snapshots and POS billing keep working with no change,
-- and historical rows are unaffected.
--
-- Backward compatible: existing rows get NULL and behave exactly as before.

ALTER TABLE public.products
  ADD COLUMN IF NOT EXISTS variant_group_id uuid
    REFERENCES public.products(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS variant_label text;

-- A group must contain at least two members before it is worth grouping; that
-- is a display concern, not an integrity concern, so no constraint is added.
-- variant_label is the short pack text ("500 ml", "1 kg", "Family pack"). When
-- it is NULL the client falls back to unit_label / pack_size_label.

CREATE INDEX IF NOT EXISTS idx_products_variant_group
  ON public.products(variant_group_id)
  WHERE variant_group_id IS NOT NULL;

-- catalog_products is SELECT * FROM products, so it picks the new columns up
-- automatically; re-create it explicitly to keep the definition obvious.
CREATE OR REPLACE VIEW public.catalog_products AS SELECT * FROM public.products;
ALTER VIEW public.catalog_products SET (security_invoker = true);

COMMENT ON COLUMN public.products.variant_group_id IS
  'Pack sizes of one base product share a group id. NULL = standalone product.';
COMMENT ON COLUMN public.products.variant_label IS
  'Short pack-size label for the variant selector, e.g. "500 ml", "1 kg".';

-- Rollback:
--   DROP INDEX IF EXISTS public.idx_products_variant_group;
--   DROP VIEW IF EXISTS public.catalog_products;
--   CREATE VIEW public.catalog_products AS SELECT * FROM public.products;
--   ALTER TABLE public.products
--     DROP COLUMN IF EXISTS variant_label,
--     DROP COLUMN IF EXISTS variant_group_id;

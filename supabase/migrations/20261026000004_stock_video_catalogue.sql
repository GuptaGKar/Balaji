-- One-time catalogue import from the three stock videos shot on 2026-10-26.
--
-- Scope and safety rules:
--   * Only details that are legible in the videos are recorded. Nothing is
--     inferred from a brand's typical range.
--   * A legible printed MRP is the only price written here. Handwritten numbers
--     (400, 180, 135) could be counts or shop codes; they are NOT treated as
--     selling prices. Those items are created as inactive drafts with price 0.
--   * No stock quantity is written. Every row starts at stock 0 and
--     available=false, so nothing can be ordered until a manager counts stock.
--   * Draft rows have active=false and price 0. They exist so the manager can
--     complete the missing price/pack size and then activate them. They are not
--     visible to customers and are rejected by the admin form until priced.
--   * Re-running is safe: rows are matched by SKU, exact name, or name without
--     a trailing pack size. Existing catalogue rows are never updated.
--
-- Sources: INV/VID-20261026-WA0003.mp4 (dry fruits),
--          INV/VID-20261026-WA0004.mp4 (rusks/toast),
--          INV/VID-20261026-WA0005.mp4 (spice mixes).

WITH incoming (
  sku,
  name,
  description,
  category_slug,
  pack_size_label,
  price_paise,
  active
) AS (
  VALUES
    -- Dry fruits (VID-20261026-WA0003)
    (
      'MUCH-MORE-WALNUT-KERNELS-250G',
      'Much More Walnut Kernels',
      'Draft from stock video VID-20261026-WA0003. Pack size 250 g is printed on the pack. No printed price was visible; the handwritten 400 is not confirmed as a price. Set the selling price and count stock before activating.',
      'snacks-namkeen',
      '250 g',
      0::bigint,
      false
    ),
    (
      'TEJAS-PREMIUM-RAISINS-250G',
      'Tejas Premium Raisins',
      'Draft from stock video VID-20261026-WA0003. Pack size 250 g is printed on the pack. No price was visible. Set the selling price and count stock before activating.',
      'snacks-namkeen',
      '250 g',
      0::bigint,
      false
    ),
    (
      'SWAD-DELICIOUS-CASHEW',
      'Swad Delicious Cashew',
      'Draft from stock video VID-20261026-WA0003. Net weight and price were not legible. Confirm the pack size, selling price and stock before activating.',
      'snacks-namkeen',
      NULL,
      0::bigint,
      false
    ),
    (
      'TULSI-CALIFORNIA-ALMONDS-250G',
      'Tulsi California Almonds',
      'Premium California almonds. Pack size 250 g and printed MRP ₹490.00 were read from the pack in stock video VID-20261026-WA0003. Stock has not been counted.',
      'snacks-namkeen',
      '250 g',
      49000::bigint,
      true
    ),
    (
      'ROYAL-DRY-FRUITS-PISTACHIO-250G',
      'Royal Dry Fruits Pistachio',
      'Roasted and salted pistachios (product of USA). Pack size 250 g and printed MRP ₹690.00 were read from the pack in stock video VID-20261026-WA0003. Stock has not been counted.',
      'snacks-namkeen',
      '250 g',
      69000::bigint,
      true
    ),
    (
      'REMOO-DRY-FIGS-AFGHANI',
      'Remoo Dry Figs Afghani',
      'Draft from stock video VID-20261026-WA0003. Pack size and printed price were not legible. Confirm the pack size, selling price and stock before activating.',
      'snacks-namkeen',
      NULL,
      0::bigint,
      false
    ),
    (
      'RAJU-MUNAKKA-AFSHAN-250G',
      'Raju Munakka Afshan',
      'Munakka packed by Hasan Brothers. Pack size 250 g and printed MRP ₹350.00 were read from the pack in stock video VID-20261026-WA0003. A handwritten 180 on the front is not confirmed as a price. Stock has not been counted.',
      'snacks-namkeen',
      '250 g',
      35000::bigint,
      true
    ),

    -- Rusks and toast (VID-20261026-WA0004)
    (
      'MUSKAN-CUSTOM-SUJI-TOAST-400G',
      'Muskan Custom Suji Toast',
      'Baked suji toast by Kharbanda Enterprises. Pack size 400 g and printed MRP ₹90.00 were read from the pack in stock video VID-20261026-WA0004. Stock has not been counted.',
      'snacks-namkeen',
      '400 g',
      9000::bigint,
      true
    ),
    (
      'BRITANNIA-TOASTEA-PREMIUM-BAKE-RUSK-200G',
      'Britannia Toastea Premium Bake Rusk',
      'Pack size 200 g and printed MRP ₹55.00 were read from the pack in stock video VID-20261026-WA0004. Stock has not been counted.',
      'snacks-namkeen',
      '200 g',
      5500::bigint,
      true
    ),
    (
      'MARIO-RUSK-90G',
      'Mario Rusk',
      'Pack size 90 g and printed MRP ₹10.00 were read from the pack in stock video VID-20261026-WA0004. Stock has not been counted.',
      'snacks-namkeen',
      '90 g',
      1000::bigint,
      true
    ),
    (
      'RUSKY-MILK-TOAST-250G',
      'Rusky Milk Toast',
      'Draft from stock video VID-20261026-WA0004. Pack size 250 g is printed on the pack. No price was visible. Set the selling price and count stock before activating.',
      'snacks-namkeen',
      '250 g',
      0::bigint,
      false
    ),
    (
      'CRISPY-LAND-SUJI-MILK-RUSK-400G',
      'Crispy Land Suji Milk Rusk',
      'Suji milk rusk by R.S. Food Products. Pack size 400 g and printed MRP ₹50.00 were read from the pack in stock video VID-20261026-WA0004. Stock has not been counted.',
      'snacks-namkeen',
      '400 g',
      5000::bigint,
      true
    ),
    (
      'BIKANERI-PREMIUM-MILK-RUSK-400G',
      'Bikaneri Premium Milk Rusk',
      'Pack size 400 g and printed MRP ₹65.00 were read from the pack in stock video VID-20261026-WA0004. Stock has not been counted.',
      'snacks-namkeen',
      '400 g',
      6500::bigint,
      true
    ),
    (
      'BREAKFAST-SPECIAL-BISCUITS',
      'Breakfast Special Cookies & Biscuits',
      'Draft from stock video VID-20261026-WA0004. Brand, net weight and price were not legible. Confirm all details before activating.',
      'snacks-namkeen',
      NULL,
      0::bigint,
      false
    ),

    -- Spice mixes (VID-20261026-WA0005)
    (
      'EVEREST-SHAHI-PANEER-MASALA',
      'Everest Shahi Paneer Masala',
      'Draft from stock video VID-20261026-WA0005. Net weight and printed MRP were not legible. Confirm the pack size, selling price and stock before activating.',
      'spices-masalas',
      NULL,
      0::bigint,
      false
    ),
    (
      'EVEREST-KASHMIRILAL-BRILLIANT-RED-CHILLI-POWDER',
      'Everest Kashmirilal Brilliant Red Chilli Powder',
      'Draft from stock video VID-20261026-WA0005. Net weight and printed MRP were not visible. Confirm the pack size, selling price and stock before activating.',
      'spices-masalas',
      NULL,
      0::bigint,
      false
    ),
    (
      'EVEREST-SHAHI-BIRYANI-MASALA',
      'Everest Shahi Biryani Masala',
      'Draft from stock video VID-20261026-WA0005. Net weight and printed MRP were not legible. Confirm the pack size, selling price and stock before activating.',
      'spices-masalas',
      NULL,
      0::bigint,
      false
    ),
    (
      'KEYA-MUTTER-PANEER-MIX-50G',
      'Keya Mutter Paneer Mix for Gravy',
      'Complete gravy mix. Pack size 50 g and printed MRP ₹52.00 were read from the pack in stock video VID-20261026-WA0005. Stock has not been counted.',
      'spices-masalas',
      '50 g',
      5200::bigint,
      true
    ),
    (
      'EVEREST-KITCHEN-KING-MASALA',
      'Everest Kitchen King Masala',
      'Draft from stock video VID-20261026-WA0005. Net weight and printed MRP were not legible. Confirm the pack size, selling price and stock before activating.',
      'spices-masalas',
      NULL,
      0::bigint,
      false
    ),
    (
      'EVEREST-CHAAT-MASALA-10G',
      'Everest Chaat Masala',
      'Pack size 10 g and printed MRP ₹10.00 were read from the pack in stock video VID-20261026-WA0005. Stock has not been counted.',
      'spices-masalas',
      '10 g',
      1000::bigint,
      true
    ),
    (
      'EVEREST-MEAT-MASALA-8G',
      'Everest Meat Masala',
      'Pack size 8 g and printed MRP ₹10.00 were read from the pack in stock video VID-20261026-WA0005. Stock has not been counted.',
      'spices-masalas',
      '8 g',
      1000::bigint,
      true
    ),
    (
      'EVEREST-CHHOLE-MASALA',
      'Everest Chhole Masala',
      'Draft from stock video VID-20261026-WA0005. Net weight and printed MRP were not legible. Confirm the pack size, selling price and stock before activating.',
      'spices-masalas',
      NULL,
      0::bigint,
      false
    ),
    (
      'EVEREST-SAMBHAR-MASALA-12G',
      'Everest Sambhar Masala',
      'Pack size 12 g and printed MRP ₹10.00 were read from the pack in stock video VID-20261026-WA0005. Stock has not been counted.',
      'spices-masalas',
      '12 g',
      1000::bigint,
      true
    )
)
INSERT INTO public.products (
  sku,
  name,
  description,
  category_id,
  quantity_type,
  price_paise,
  price_basis_qty,
  unit_label,
  pack_size_label,
  step_qty,
  min_qty,
  max_qty,
  stock_qty,
  low_stock_threshold,
  active,
  available
)
SELECT
  v.sku,
  v.name,
  v.description,
  c.id,
  'PACKED'::public.quantity_type,
  v.price_paise,
  1,
  'pack',
  v.pack_size_label,
  1,
  1,
  NULL,
  0,
  5,
  v.active,
  false
FROM incoming v
JOIN public.categories c ON c.slug = v.category_slug
WHERE NOT EXISTS (
  SELECT 1
  FROM public.products p
  WHERE p.sku = v.sku
     OR lower(p.name) = lower(v.name)
     OR lower(p.name) = lower(
          regexp_replace(v.name, '\s+[0-9]+(\.[0-9]+)?\s*(g|kg|ml|l|pc|pcs)$', '', 'i')
        )
)
ON CONFLICT (sku) DO NOTHING;

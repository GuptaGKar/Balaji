-- Backfill product images that already exist in the public product-images
-- bucket. The 73 objects below were verified with HTTP 200 on 2026-10-26.
-- Products without an object are deliberately left blank so the app shows its
-- package placeholder instead of a broken image.
--
-- Only blank image_path values are filled. A manager's custom URL or uploaded
-- image is never overwritten, so re-running this migration is safe.
--
-- This is a data backfill for this deployment's storage bucket. Objects follow
-- the standard Supabase public-storage layout
-- <project>.supabase.co/storage/v1/object/public/product-images/products/<SKU>.jpg.

update public.products p
set image_path =
  'https://czbwwnlnmhhzketyjnzt.supabase.co/storage/v1/object/public/product-images/products/'
  || p.sku || '.jpg'
where (p.image_path is null or btrim(p.image_path) = '')
  and p.sku in (
    'ALPENLIEBE-CANDY',
    'AMERICAN-CASHEW-COOKIES',
    'AMULYA-DAIRY-WHITENER-200G',
    'BALAJI-TRIYUG',
    'BLUE-SNACK-PACK',
    'CADBURY-5STAR-SOFTER-20',
    'CADBURY-DAIRY-MILK-FAMILY',
    'CADBURY-DAIRY-MILK-SILK-HAZELNUT',
    'CINTHOL-LIME-SOAP',
    'CRISPY-LAND-COOKIES',
    'CYCLE-PURE-THREE-IN-ONE',
    'DETTOL-ICY-COOL-SOAP',
    'DETTOL-ORIGINAL-SOAP',
    'DEVARSHAN-PETAL-ROSE',
    'DIVINE-GRACE-PETAL',
    'DOVE-SOAP',
    'EXO-DISHWASH-BAR',
    'EXO-DISHWASH-TUB',
    'FENA-MAHABAR-ADVANCED-180G-50G-FREE',
    'GODREJ-NO1-LIME-ALOE-4X150G',
    'GODREJ-NO1-SANDAL-TURMERIC-4X150G',
    'GOOD-KNIGHT-AGARBATTI',
    'HALDIRAMS-ALL-IN-ONE',
    'HALDIRAMS-ALOO-BHUJIA',
    'HALDIRAMS-BHAKARWADI',
    'HALDIRAMS-GUJARATI-MIXTURE',
    'HALDIRAMS-KAJU-MIXTURE',
    'HALDIRAMS-KASHMIRI-MIXTURE',
    'HALDIRAMS-LITE-CHIWDA',
    'HALDIRAMS-LITE-MIXTURE',
    'HALDIRAMS-LITE-MURMURA',
    'HALDIRAMS-MOONG-DAL',
    'HALDIRAMS-MURUKKU',
    'HALDIRAMS-NAVRATAN',
    'HALDIRAMS-PANCHRATTAN',
    'HALDIRAMS-PUNJABI-TADKA',
    'HALDIRAMS-ROYAL-ZAHIDI-DATES',
    'HALDIRAMS-SAMOSA',
    'HALDIRAMS-SHAHI-DAL-BHI',
    'HALDIRAMS-SHAHI-MIXTURE',
    'HALDIRAMS-SOAN-PAPDI',
    'HERITAGE-PRAYER-STICKS',
    'HERITAGE-RADANT-ROSE',
    'HIMALAYA-GENTLE-BABY-SOAP',
    'HONEY-ROSE',
    'JOY-SOAP',
    'KITKAT-FREE-20',
    'LIFEBUOY-SOAP',
    'LUX-SOAP',
    'MARGO-SOAP',
    'MEDIMIX-AYURVEDIC-SOAP',
    'MENTOS-ORANGE',
    'MENTOS-STRAWBERRY',
    'MYSORE-CARBOLIC-SOAP',
    'NAVGRAH-HAVAN-SAMIDHA',
    'PAMPERS-BABY-DRY-PANTS',
    'PARLE-MANGO-BITE',
    'PATANJALI-SUPER-DISHWASH-BAR',
    'PEARS-SOAP',
    'RIN-4-BAR-PACK',
    'SANTOOR-SANDAL-TURMERIC-4X55G',
    'SANTOOR-SANDAL-TURMERIC-SINGLE',
    'SHALIMAR-BELLA',
    'SHALIMAR-GUGAL',
    'SHALIMAR-MYSORE-CHANDAN',
    'SHALIMAR-ROSE',
    'SNICKERS',
    'SURF-EXCEL-BIG-BAR',
    'SWAD-CASHEW',
    'TEJAS-RAISINS',
    'TIDE-DETERGENT-POWDER-30G-FREE',
    'VIM-DISHWASH-BAR',
    'ZED-BLACK-3IN1'
  );

-- Rollback:
--   update public.products set image_path = null
--   where image_path like
--     'https://czbwwnlnmhhzketyjnzt.supabase.co/storage/v1/object/public/product-images/products/%';

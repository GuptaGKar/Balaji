TASK: Section-driven Home and Categories pages (managed from the web dashboard),
multi-category products, and a floating pill bottom navigation.

You are working in the repo GuptaGKar/Balaji (branch master): Android app (Kotlin + Jetpack
Compose, package com.balaji.provision, flavours customer and staff, under android/), web
dashboard (Next.js 14 + TypeScript + Tailwind, under web/) and Supabase (supabase/migrations,
supabase/tests with pgTAP). Work step by step, commit in the phases listed at the end, and
report honestly what you could and could not run.

STEP 0 - READ AND CONFIRM BEFORE CODING
- Read PROJECT_STATUS.md, docs/DECISIONS.md, QA_LOG.md. PROJECT_STATUS.md is out of date:
  push notifications, zone delivery pricing and private order media already exist in newer
  migrations. Trust the migrations and code over the doc.
- Grep how money is stored and shown. The owner says the business thinks in whole rupees.
  The repo currently stores integer paise (columns such as price_paise, formatPaise helpers).
  Do NOT migrate, rename, or convert any money column, RPC or helper, and do not touch
  pricing, quote_cart, coupons or fees. This feature only DISPLAYS prices in new places
  (web item picker and preview, Home/Categories rails). In those new places show whole
  rupees (for example "₹60", no ".00") by formatting at display time only, using one small
  shared helper per platform. If you find the repo has actually moved to rupees, follow the
  repo and note it in docs/DECISIONS.md.
- Check whether the UX patch from the previous round is present: ui/components/MiniCartBar.kt,
  ui/components/DeliveryCopy.kt, banner destination/overlay parsing in HomeViewModel, ranked
  search and category chips in ProductViewModel/CatalogScreen. If present, build on them and
  do not duplicate. If absent, do not add them here.

HARD RULES
- Prices, stock, fees, coupons and permissions stay decided in Postgres (RLS and functions);
  clients only display.
- The migration must NOT insert any sections or sample data. With zero sections, Home and
  Categories must look and behave exactly as they do today (fallback).
- Keep the legacy-select fallback pattern on HTTP 400 so an unmigrated database never blocks
  the customer catalogue.
- Do not bump versionCode or versionName and do not regenerate releases/. No new libraries
  unless unavoidable.
- Keep existing tests green and the existing banners editor untouched.

PART 1 - DATABASE (one new migration, timestamp after the newest existing file)
Tables
1. storefront_sections: id uuid pk default gen_random_uuid(); page text not null check in
   ('home','categories'); title text not null check (char_length between 1 and 40); subtitle
   text null check (char_length <= 80); section_type text not null check in
   ('products','categories'); sort_order int not null; active boolean not null default true;
   created_at, updated_at timestamptz; updated_by uuid. Index (page, sort_order). Max 20
   sections per page (trigger).
2. storefront_section_items: id uuid pk; section_id uuid not null references
   storefront_sections on delete cascade; product_id uuid null references products on delete
   cascade; category_id uuid null references categories on delete cascade; sort_order int not
   null. Constraint: exactly one of product_id / category_id is set. Trigger: the set column
   must match the parent section_type. Unique (section_id, product_id) and (section_id,
   category_id). Max 30 items per section (trigger).
3. product_categories: product_id, category_id, sort_order int default 0, primary key
   (product_id, category_id), both cascade. Backfill exactly one row per existing product
   from products.category_id. KEEP products.category_id as the product's PRIMARY category.
   Trigger: whenever products.category_id changes or a product is created, make sure that
   category exists in product_categories; deleting the primary pair is rejected unless the
   primary changes first.
Security
4. RLS on all three tables. anon and authenticated: SELECT only active sections and their
   items, and SELECT product_categories. Writes only for managers using the same
   manager-check helper other tables use. Explicit grants, never GRANT ALL.
Functions
5. get_storefront(p_page text) returns jsonb, security invoker, executable by anon and
   authenticated. Shape:
   [{"id":"...","title":"Daily essentials","subtitle":null,"section_type":"products",
     "updated_at":"...","item_ids":["uuid","uuid"]}, ...]
   Active sections only, ordered by sort_order, items ordered by sort_order. Returns [] when
   none. It returns IDS ONLY; the app resolves products and categories from the catalogue it
   already loads, so prices and stock are never duplicated here.
6. save_storefront_section(p_id uuid null, p_page text, p_title text, p_subtitle text,
   p_section_type text, p_active boolean, p_item_ids uuid[], p_expected_updated_at
   timestamptz null) for managers: creates or updates the section and REPLACES its items in
   one transaction. If p_expected_updated_at is given and does not match, raise a clear
   "changed by someone else" error so two managers cannot overwrite each other. Validate
   title length, type/item match, duplicates, and the 30-item cap with readable messages.
7. reorder_storefront_sections(p_page text, p_ordered_ids uuid[]) and
   delete_storefront_section(p_id uuid) for managers.
8. Grep every use of products.category_id (SQL functions, RLS, web, Android). For each one,
   decide: must match ANY of the product's categories (listing, filters, search, category
   counts) or stays primary-only (coupon scope, reports, variant grouping). Record each
   decision in docs/DECISIONS.md.
Tests (pgTAP, next free numbers in supabase/tests)
9. anon sees only active sections; non-manager cannot write; XOR and type-mismatch rejected;
   duplicate item rejected; 31st item rejected; 21st section rejected; backfill and
   primary-category trigger; save RPC rolls back fully when one item is invalid; stale
   p_expected_updated_at rejected; empty storefront returns []. Note in QA_LOG.md that pgTAP
   can only run in CI if there is no local Postgres.

PART 2 - WEB DASHBOARD (web/)
1. New manager page "Storefront" with tabs Home and Categories, matching existing styling.
2. Each tab lists its sections. Manager can: add a section (choosing type Products or
   Categories at creation; type is not editable afterwards), edit heading (required, 40
   chars) and optional subtitle (80), reorder (drag handles AND up/down buttons for
   keyboard), activate/deactivate, delete with confirmation.
3. Item picker per section: searchable multi-select in a drawer or modal.
   Products: image, name, pack label, whole-rupee price, stock state; search by name or SKU.
   Categories: image and name. Selected items can be reordered and removed. Warn (do not
   block) about inactive or out-of-stock products. Enforce 30 items, no duplicates.
4. Save through the RPCs only, passing the section's updated_at as p_expected_updated_at.
   Show saving, saved, error, and unsaved-changes-on-leave states, and a clear message on a
   stale-edit conflict with a Reload button.
5. A simple "Preview" panel for each tab: sections in order with item thumbnails and the
   section heading as it will appear, plus a note that products that are out of stock or
   inactive are hidden in the app.
6. Product form: replace the single category select with a multi-select of categories and a
   "Primary" radio on one of the selected categories (maps to products.category_id). Product
   list and any category filter match ANY of a product's categories; the product still
   appears once in the list. Saving writes product_categories and products.category_id in
   one request or RPC so they cannot disagree.
7. Loading, empty ("No sections yet, the app shows the default layout") and error states,
   keyboard access and labels. npm run lint, type-check and build must pass with no console
   errors.

PART 3 - ANDROID
A. Data layer
- Add the get_storefront call to SupabaseApi and a StorefrontRepository (Hilt singleton):
  in-memory cache; show the cached result immediately, refresh in the background, and
  refresh again when the page is re-entered after about 5 minutes. On any failure keep the
  cached result, otherwise use the fallback layout. Never show a blank page.
- Product domain model gets categoryIds: Set<String>, filled from an embedded
  product_categories select, falling back to setOf(categoryId) when the embed is missing
  (legacy-select fallback). Whole-rupee display helper for new UI only; existing screens
  keep their current formatter unless the owner asks.
- ProductViewModel: category filtering, category chips and category-name search match ANY of
  a product's categories. A product appears once per listing and once in All products.
  Variant grouping, Home round-robin fallback and coupon logic keep using the primary
  category.
B. Home page
- Keep header, address, search and banner carousel unchanged. When active sections exist
  they REPLACE the "Shop by category", "From every aisle / In stock now" and "More to
  explore" blocks, in sort_order. Keep the store info card at the bottom. With no sections,
  render today's layout.
- Products section: heading, optional subtitle, horizontal rail reusing
  HorizontalProductCard and the existing add / stepper / weighted-quantity logic.
- Categories section: heading plus a tile grid reusing CategoryShortcut; columns follow
  screen width (about 4 on phones, up to 6 on tablets).
- Resolve ids against the cached catalogue. Skip inactive, missing or out-of-stock
  products and unknown categories. Hide a section with no resolvable items. Keep the
  manager's order for the items that remain.
- Page length must be fully content-driven: one LazyColumn item per section, stable keys
  (section id), no fixed heights, so any number of sections just scrolls. Skeleton while
  loading, retry state on error, bottom padding so the last section clears the nav pill and
  the MiniCartBar.
C. Categories page
- Same model with page='categories'. Categories sections show tiles, Products sections show
  a rail. When sections exist, automatically append a final "More categories" section
  listing active categories that are not in any section, so no category becomes unreachable.
  With zero sections keep today's grid.
D. Bottom navigation (customer flavour)
- Replace the NavigationBar with a floating pill: fully rounded, about 16 dp horizontal
  inset, sitting above the system navigation bar, centred with max width about 480 dp on
  tablets. Background = surface colour at alpha NAV_PILL_ALPHA, a single named constant set
  to 0.70f. Owner wording was "70% transparency of pill background"; 0.70f means 70% opaque
  (30% see-through). If the owner meant 70% see-through, only this constant changes to
  0.30f. Add a 1 dp subtle outline and soft shadow so labels stay readable over busy images.
- Items Home, Categories, Orders, Basket (keep the existing count badge). Selected item gets
  a pill highlight. 48 dp minimum touch targets, keep contentDescriptions and existing
  visibility rules (hidden on product detail and checkout, and while the keyboard is open).
- Because the pill floats over content, every screen that sits inside the tab scaffold needs
  bottom content padding equal to pill height plus margins, and MiniCartBar must sit ABOVE
  the pill (never behind it). Check Home, Categories, Catalog, Orders, Basket and the empty
  states.

PART 4 - TESTS AND VERIFICATION
- Unit tests: section ordering, skipping unresolvable items, hiding empty sections, the
  appended "More categories" section, fallback with zero sections, multi-category filtering
  with no duplicate rows, whole-rupee formatting helper.
- Build customer and staff flavours, run unit tests and lint, run the web checks.
- Manual checks on the Galaxy A34 and on a tablet (both portrait): create two Home sections
  (one products, one categories) and one Categories section in the dashboard and see them in
  order; rename, reorder, deactivate and delete; put a product in two categories and see it
  under both but once in All products; add 30 items and confirm the 31st is refused; airplane
  mode shows the cached layout, then the fallback; the nav pill over a busy page together
  with the MiniCartBar.

PHASES (commit after each, keep the app buildable at every step)
1) Migration + pgTAP + DECISIONS entries. 2) Web: product multi-category, then Storefront
page. 3) Android data layer and multi-category filtering. 4) Android Home and Categories
sections. 5) Nav pill and padding. 6) Docs.

FINAL REPORT
List files changed per phase, what you ran and the results, what you could not run (for
example pgTAP without Postgres, device checks), every decision about products.category_id,
and any place the repo disagreed with this brief. Update PROJECT_STATUS.md, docs/DECISIONS.md
and QA_LOG.md.
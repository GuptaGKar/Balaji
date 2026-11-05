# Section-driven Home and Categories, Multi-category Products, Floating Nav Pill — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a manager compose the Home and Categories pages from ordered sections of products or categories held in Postgres, allow one product to belong to several categories, and replace the customer app's bottom bar with a floating pill.

**Architecture:** Three new tables plus four `SECURITY DEFINER`-free RPCs in one migration; the dashboard writes only through those RPCs and the apps read one ids-only payload, resolving every price, stock and availability fact from the catalogue they already fetch. Both Android pages keep today's layout as a byte-identical fallback whenever zero sections are active, so the feature is dark until a manager composes it.

**Tech Stack:** Kotlin + Jetpack Compose (flavours `customer`/`staff`), Next.js 14 + TypeScript + Tailwind, Supabase/Postgres + pgTAP, Gradle, ADB.

**Spec:** pasted brief `TASK: Section-driven Home and Categories pages…`, retained at `docs/superpowers/plans/2026-11-05-storefront-sections-brief.md`.

**Baseline evidence:** `docs/superpowers/plans/2026-11-02-device-release-audit.md` and the round 1–2 UX patch already in the tree (`ui/components/MiniCartBar.kt`, `ui/components/DeliveryCopy.kt`, banner destination/overlay parsing in `HomeViewModel`, ranked search + category chips in `ProductViewModel`/`CatalogScreen`). Build on those; do not re-add them.

## Global Constraints

- Money stays **integer paise**. Confirmed: 668 `_paise` references across `supabase/migrations`, `formatPaise` used 60×, `paise_to_rupees()` in SQL. **Do not** migrate, rename or convert any money column, RPC or helper. Do not touch `quote_cart`, coupons, fees or pricing.
- New display surfaces only (web item picker, web preview, Home/Categories rails) show **whole rupees without decimals** — `"₹60"`, never `"₹60.00"` — formatted at display time by exactly one shared helper per platform. Existing screens keep `formatPaise`.
- Prices, stock, fees, coupons and permissions are decided in Postgres (RLS + functions). Clients only display.
- The migration inserts **no sections and no sample data**. With zero active sections Home and Categories must look and behave exactly as today.
- Keep the legacy-select HTTP-400 fallback pattern so an unmigrated database never blocks the customer catalogue.
- Do not bump `versionCode`/`versionName` (currently 13 / 1.12). Do not regenerate `releases/`. No new libraries unless unavoidable.
- Existing tests stay green; the existing banners editor in `web/src/app/(admin)/settings/page.tsx` is untouched.
- New migration files use a strict 14-digit `YYYYMMDDNNNNNN` prefix sorting after `20261103000003_area_suggestions.sql`.
- Every migration touching functions ends with `NOTIFY pgrst, 'reload schema';`. `GRANT` names the real signature; **never `GRANT ALL`**. A changed function signature gives the new parameter a `DEFAULT` and drops the old signature in the same migration.
- RLS authorisation reuses the existing helper: `public.current_role() = 'manager'::public.user_role` (see `20261014000017_security_hardening.sql:48,57`).
- pgTAP can only be executed in CI here — there is no local Postgres and Docker is unavailable. Record that in `QA_LOG.md`.
- Limits, verbatim from the brief: title 1–40 chars, subtitle ≤ 80, ≤ 20 sections per page, ≤ 30 items per section, exactly one of `product_id`/`category_id`, item set must match `section_type`.

## Review Focus

Failure modes the brief implies but no single task's happy-path tests exercise. Each line is pinned by a test in the owning task.

1. **A section references a product that was deleted, deactivated or has gone to zero stock.** The app must skip it, keep the manager's order for what remains, and hide the section entirely if nothing resolves — never render a blank rail or a spinner that never ends. (Task 14)
2. **`products.category_id` and `product_categories` disagree** — e.g. the primary pair deleted, or a concurrent write leaves a product whose primary category is absent from the join table. The trigger must reject or repair, and every read must still place the product in its primary category exactly once. (Task 2)
3. **Two managers save the same section.** The stale `p_expected_updated_at` must be rejected with a message the dashboard can show, and the loser's work must not be silently overwritten. (Task 3, Task 9)
4. **The database is not yet migrated but the app is updated.** `get_storefront` returns 404/400 and both pages must fall back to today's layout rather than blank out. (Task 10)
5. **A floating pill over tall content hides the last row, or sits behind the `MiniCartBar`.** Content padding must be pill height + margin on every tab-scaffold screen, and the basket bar must render above the pill. (Task 17)

---

## Phase 1 — Database

One new migration, `supabase/migrations/20261105000001_storefront_sections.sql`, built up across Tasks 1–3 and applied once. It is unapplied during this phase, so appending to it is allowed; never edit a migration already pushed.

### Task 1: Section tables, constraints, limits, RLS

**Files:**
- Create: `supabase/migrations/20261105000001_storefront_sections.sql`
- Test: `supabase/tests/045_storefront_sections.sql`

**Interfaces:**
- Produces: table `storefront_sections(id uuid, page text, title text, subtitle text null, section_type text, sort_order int, active bool, created_at, updated_at, updated_by uuid null)`; table `storefront_section_items(id uuid, section_id uuid, product_id uuid null, category_id uuid null, sort_order int)`.

- [ ] **Step 1: Write the failing pgTAP test**

In `supabase/tests/045_storefront_sections.sql`, self-bootstrap pgTAP inside the transaction (project convention — no psql meta-commands, no `\i`), then assert: both tables exist; `page` rejects anything not in `('home','categories')`; `section_type` rejects anything not in `('products','categories')`; empty and 41-char titles rejected; 81-char subtitle rejected; the XOR constraint rejects an item with both ids and with neither; an item whose set column contradicts the parent `section_type` is rejected; a duplicate `(section_id, product_id)` is rejected; the 31st item is rejected; the 21st section on one page is rejected; `on delete cascade` removes items with the section.

- [ ] **Step 2: Run it and confirm it fails**

Run: `supabase test db --local` (or CI). Expected: FAIL — `relation "storefront_sections" does not exist`. If no local Postgres, record "not run locally; CI only" in `QA_LOG.md` and continue.

- [ ] **Step 3: Write the schema**

In the new migration: `gen_random_uuid()` defaults, `created_at`/`updated_at timestamptz not null default now()`, `updated_by uuid null references public.profiles(id)`. Add `create index storefront_sections_page_order_idx on public.storefront_sections (page, sort_order);`. Enforce the caps with three trigger functions — `enforce_storefront_section_limit()`, `enforce_storefront_section_item_limit()`, `enforce_storefront_item_type()` — each raising a readable `format()` message. Keep `products.category_id` untouched.

- [ ] **Step 4: Add RLS and grants**

`enable row level security` on both tables. Policies: `storefront_sections_read_active` and `storefront_section_items_read_active` — `for select to anon, authenticated using (active = true)` joined to the parent's `active` for items; `storefront_sections_manager_all` / `storefront_section_items_manager_all` — `for all to authenticated using (public.current_role() = 'manager'::public.user_role) with check (same)`. Grants: `GRANT SELECT ON public.storefront_sections, public.storefront_section_items TO anon, authenticated;` and `GRANT INSERT, UPDATE, DELETE ON … TO authenticated;` — explicit, never `GRANT ALL`.

- [ ] **Step 5: Re-run the test and confirm it passes**

Run: `supabase test db --local`. Expected: all assertions pass, 0 failures.

- [ ] **Step 6: Commit**

`git add supabase/migrations/20261105000001_storefront_sections.sql supabase/tests/045_storefront_sections.sql && git commit -m "feat(db): storefront section tables with caps, type match and RLS"`

### Task 2: `product_categories`, backfill and primary-category integrity

**Files:**
- Modify: `supabase/migrations/20261105000001_storefront_sections.sql` (append)
- Test: `supabase/tests/046_product_categories.sql`

**Interfaces:**
- Produces: table `product_categories(product_id uuid, category_id uuid, sort_order int default 0, primary key (product_id, category_id))`. `products.category_id` remains the PRIMARY category and is not dropped or repurposed.

- [ ] **Step 1: Write the failing test**

In `046_product_categories.sql`, inside one transaction that always rolls back: assert the backfill produced exactly one row per product that has a `category_id`, and that each such row equals that product's `category_id`; inserting a product with a `category_id` auto-creates the pair; `UPDATE products SET category_id = …` adds the new pair and keeps the old one; deleting the primary pair while it is still primary is rejected with a readable message; deleting it after `category_id` moved to another category succeeds; a product appears once per category and the join never contradicts the primary.

- [ ] **Step 2: Run it and confirm it fails**

Expected: FAIL — `relation "product_categories" does not exist`.

- [ ] **Step 3: Write the table, backfill and triggers**

`create table public.product_categories(...)` with both foreign keys `on delete cascade`. Backfill: `insert into public.product_categories (product_id, category_id) select id, category_id from public.products where category_id is not null on conflict do nothing;`. Then `sync_product_primary_category()` as an `after insert or update of category_id on public.products` trigger that guarantees the primary pair exists, and `protect_product_primary_category()` as a `before delete on public.product_categories` trigger raising when the removed pair still matches `products.category_id`.

- [ ] **Step 4: Add RLS and grants**

`anon, authenticated` may `SELECT` only; managers may write, using the same `current_role()` predicate. Note in the migration comment that the join is public-read because the app resolves membership client-side.

- [ ] **Step 5: Re-run and confirm it passes**

- [ ] **Step 6: Commit**

`git commit -m "feat(db): product_categories join with backfill and primary-category triggers"`

### Task 3: Storefront RPCs with optimistic concurrency

**Files:**
- Modify: `supabase/migrations/20261105000001_storefront_sections.sql` (append)
- Test: `supabase/tests/047_storefront_rpc.sql`

**Interfaces:**
- Consumes: tables from Tasks 1–2.
- Produces:
  - `public.get_storefront(p_page text) returns jsonb` — **security invoker**, executable by `anon, authenticated`. Returns `[{"id","title","subtitle","section_type","updated_at","item_ids":[…]}]`, active only, sections by `sort_order`, items by `sort_order`, `[]` when none. **Ids only** — never prices, stock or fees.
  - `public.save_storefront_section(p_id uuid default null, p_page text default 'home', p_title text default '', p_subtitle text default null, p_section_type text default 'products', p_active boolean default true, p_item_ids uuid[] default array[]::uuid[], p_expected_updated_at timestamptz default null) returns jsonb` — managers. Creates or updates and replaces items in one transaction.
  - `public.reorder_storefront_sections(p_page text, p_ordered_ids uuid[]) returns void`
  - `public.delete_storefront_section(p_id uuid) returns void`

- [ ] **Step 1: Write the failing test**

In `047_storefront_rpc.sql`: `get_storefront('home')` on an empty storefront returns `'[]'::jsonb`; it omits inactive sections and their items; ordering matches `sort_order`; the payload contains no key named `price`, `stock` or `fee` (assert with `jsonb_object_keys`); a non-manager executing `save_storefront_section` is refused; a stale `p_expected_updated_at` raises a message containing `changed by someone else`; an invalid title, a type/item mismatch, a duplicate id and a 31-item array each raise readable errors; a save that fails on its 20th item leaves **no** partial section and **no** partial items (count both before and after); `reorder_storefront_sections` with an id from another page is refused; `delete_storefront_section` cascades items.

- [ ] **Step 2: Run it and confirm it fails**

Expected: FAIL — `function public.get_storefront(text) does not exist`.

- [ ] **Step 3: Implement the four functions**

`get_storefront` builds jsonb with `jsonb_agg` over `jsonb_build_object`, filtering `active` and joining the item table ordered by `sort_order`, and is `security invoker` so RLS does the filtering. `save_storefront_section` validates first, then `insert … on conflict (id) do update`, then deletes and re-inserts items in the caller's transaction; when `p_expected_updated_at` is not null, `update … where updated_at = p_expected_updated_at` and raise if `found` is false. Set `search_path` explicitly on every function. End the migration with `NOTIFY pgrst, 'reload schema';`.

- [ ] **Step 4: Grant execute on the real signatures**

`GRANT EXECUTE ON FUNCTION public.get_storefront(text) TO anon, authenticated;` and on the other three `TO authenticated` only. Use `to_regprocedure` where a signature may vary. Never `GRANT ALL`.

- [ ] **Step 5: Re-run and confirm it passes**

- [ ] **Step 6: Commit**

`git commit -m "feat(db): get_storefront, save/reorder/delete RPCs with edit-conflict detection"`

### Task 4: `products.category_id` audit — ANY vs PRIMARY

**Files:**
- Modify: `docs/DECISIONS.md`

**Interfaces:**
- Produces: the decision table every later task cites. **ANY** = match if the product is in any of its categories. **PRIMARY** = keep using `products.category_id` alone.

- [ ] **Step 1: Enumerate every use**

Run: `grep -rn "category_id" supabase/migrations/*.sql web/src android/app/src/main/java --include=*.{ts,tsx,kt,sql} | grep -v node_modules`. Expect roughly 10 SQL files, 24 web hits, 28 Android hits. Record the count you actually get.

- [ ] **Step 2: Classify each one**

Default per the brief — **ANY** for catalogue listing, category filters, search, category counts, the Categories page, storefront category tiles; **PRIMARY** for coupon category scope, reports, variant grouping, the Home round-robin fallback. Deviate only where the code proves it, and say why.

- [ ] **Step 3: Write the dated DECISIONS entry**

Append `## Storefront sections and multi-category products (2026-11-05)` to `docs/DECISIONS.md`: the decision table (file, symbol, ANY/PRIMARY, reason), the money decision (paise retained; whole-rupee display only in new surfaces), the two brief disagreements (`docs/PROJECT_STATUS.md` path; `GRANT ALL` already present at `20261026000006_coupons.sql:61` — not copied into new tables, not repaired here), and that pgTAP runs in CI only.

- [ ] **Step 4: Commit**

`git commit -m "docs: category_id ANY/PRIMARY audit and storefront decisions"`

---

## Phase 2 — Web dashboard

### Task 5: Whole-rupee display helper

**Files:**
- Create: `web/src/lib/money-display.ts`
- Test: `web/src/lib/money-display.test.ts` (existing `npm test` runner)

**Interfaces:**
- Produces: `formatRupeesWhole(paise: number | null | undefined): string` → `"₹60"` for 6000, `"₹60"` for 6049, `"—"` for null. Rounds half up to the rupee. Display only; never used for a total sent to the server.

- [ ] **Step 1: Write the failing test**

Assert `formatRupeesWhole(6000) === '₹60'`, `formatRupeesWhole(6099) === '₹61'` (half-up at 50), `formatRupeesWhole(6050) === '₹61'`, `formatRupeesWhole(0) === '₹0'`, `formatRupeesWhole(null) === '—'`, and that no output ever contains `.00`.

- [ ] **Step 2: Run and confirm it fails** — Run: `npm --prefix web test`. Expected: module not found.

- [ ] **Step 3: Implement it** — integer arithmetic on paise; `Math.floor((paise + 50) / 100)`; no floating point.

- [ ] **Step 4: Run and confirm it passes**

- [ ] **Step 5: Commit** — `git commit -m "feat(web): whole-rupee display helper for new surfaces"`

### Task 6: Product multi-category editor

**Files:**
- Modify: `web/src/app/(admin)/catalogue/ProductsTab.tsx`
- Modify: the product save path it calls (RPC or direct table write — read it first)

**Interfaces:**
- Consumes: `product_categories`, `products.category_id` (PRIMARY).
- Produces: a multi-select of categories plus one **Primary** radio; saving writes both in a single request so they cannot disagree.

- [ ] **Step 1: Write the failing test**

Extend the existing form tests with: selecting two categories yields `category_ids` of length 2 and exactly one `primary_category_id`; choosing a primary not in the selection is rejected in-form; editing a product that already has rows keeps them.

- [ ] **Step 2: Run and confirm it fails**

- [ ] **Step 3: Replace the single select**

Swap the one `category_id` select for a searchable multi-select plus a radio naming the primary, labelled for screen readers. On submit, send `category_ids` and `primary_category_id` together; the server write must set `products.category_id` and replace `product_categories` rows in one transaction.

- [ ] **Step 4: Make list and category filter match ANY**

The product list must show a product once even when it is in several categories, and must match a category filter on ANY membership.

- [ ] **Step 5: Run checks** — `npm --prefix web run lint && npm --prefix web run type-check && npm --prefix web run build`. Expected: lint and build pass; type-check must not add errors beyond the 11 pre-existing react-hook-form resolver failures.

- [ ] **Step 6: Commit** — `git commit -m "feat(web): multi-category product editor with explicit primary"`

### Task 7: Storefront page — section list and CRUD

**Files:**
- Create: `web/src/app/(admin)/storefront/page.tsx`
- Create: `web/src/app/(admin)/storefront/StorefrontPanel.tsx`
- Modify: `web/src/app/(admin)/layout.tsx` (nav entry)

**Interfaces:**
- Consumes: `get_storefront`, `save_storefront_section`, `reorder_storefront_sections`, `delete_storefront_section`.
- Produces: tabs Home | Categories; per tab a list of sections with heading, subtitle, type badge, active toggle, up/down buttons and drag handles, add and delete.

- [ ] **Step 1: Write the failing test**

Cover the pure section-state reducer: adding, reordering by drag and by keyboard buttons, toggling active, and that a 21st add is refused with the server's message.

- [ ] **Step 2: Run and confirm it fails**

- [ ] **Step 3: Build the page**

Match existing admin styling. Type is chosen at creation and **not editable afterwards** — render it read-only after save. Route every write through the RPCs, passing the section's `updated_at` as `p_expected_updated_at`. States: loading, saved, error, unsaved-changes-on-leave, and a stale-edit conflict message with a **Reload** button. Empty state copy exactly: `"No sections yet, the app shows the default layout"`.

- [ ] **Step 4: Run lint, type-check and build** — `npm --prefix web run lint && npm --prefix web run type-check && npm --prefix web run build`. Expected: lint and build pass; type-check must not add errors beyond the 11 pre-existing react-hook-form resolver failures.

- [ ] **Step 5: Commit** — `git commit -m "feat(web): storefront section editor for Home and Categories"`

### Task 8: Item picker

**Files:**
- Create: `web/src/app/(admin)/storefront/ItemPicker.tsx`

**Interfaces:**
- Consumes: catalogue products (image, name, pack label, `formatRupeesWhole` price, stock state, searchable by name or SKU) and categories (image, name).

- [ ] **Step 1: Write the failing test**

Assert: no duplicates selectable; 31st selection refused; reorder of selected items is stable; an inactive or out-of-stock product raises a **warning but does not block**.

- [ ] **Step 2: Run and confirm it fails**

- [ ] **Step 3: Implement the drawer**

Searchable multi-select in a drawer/modal; selected list reorderable and removable; prices via `formatRupeesWhole` only.

- [ ] **Step 4: Run lint, type-check and build** — `npm --prefix web run lint && npm --prefix web run type-check && npm --prefix web run build`.

- [ ] **Step 5: Commit** — `git commit -m "feat(web): storefront item picker with caps and out-of-stock warning"`

### Task 9: Preview panel

**Files:**
- Modify: `web/src/app/(admin)/storefront/StorefrontPanel.tsx`

- [ ] **Step 1: Write the failing test**

Sections render in `sort_order`; heading shows as it will in the app; a note states inactive/out-of-stock products are hidden in the app.

- [ ] **Step 2: Run, confirm fail, implement, re-run**

- [ ] **Step 3: Commit** — `git commit -m "feat(web): storefront preview panel"`

---

## Phase 3 — Android data layer

### Task 10: `get_storefront` API and `StorefrontRepository`

**Files:**
- Modify: `android/app/src/main/java/com/balaji/provision/data/network/SupabaseApi.kt`
- Create: `android/app/src/main/java/com/balaji/provision/data/repository/StorefrontRepository.kt`
- Test: `android/app/src/test/java/com/balaji/provision/data/repository/StorefrontRepositoryTest.kt`

**Interfaces:**
- Produces:
  - `data class StorefrontSection(val id: String, val title: String, val subtitle: String?, val sectionType: String, val updatedAt: String, val itemIds: List<String>)`
  - `StorefrontRepository.sectionsFor(page: String): Flow<List<StorefrontSection>>` — Hilt singleton, in-memory cache, cached value emitted immediately, background refresh, re-refresh when re-entered after ~5 minutes.

- [ ] **Step 1: Write the failing test**

Using the project's existing fake-API style: cache hit emits before the network result; a network failure after a prior success keeps the cached list; a first-load failure yields an empty list (the fallback signal), never an exception; a re-entry older than 5 minutes refreshes, a younger one does not; malformed jsonb is skipped, not thrown.

- [ ] **Step 2: Run and confirm it fails** — `./gradlew :app:testCustomerDebugUnitTest --tests '*StorefrontRepositoryTest'`

- [ ] **Step 3: Implement**

Add the RPC call to `SupabaseApi`. Keep the **legacy-select fallback**: if the storefront call returns 400/404 because the migration is not applied, treat it as zero sections and let the UI fall back.

- [ ] **Step 4: Run and confirm it passes**

- [ ] **Step 5: Commit** — `git commit -m "feat(android): storefront repository with cache and TTL refresh"`

### Task 11: `Product.categoryIds` with legacy fallback

**Files:**
- Modify: `android/app/src/main/java/com/balaji/provision/domain/model/DomainModels.kt:84`
- Modify: the product select in `SupabaseApi.kt`
- Test: `android/app/src/test/java/com/balaji/provision/domain/model/ProductCategoryIdsTest.kt`

**Interfaces:**
- Produces: `val categoryIds: Set<String>` on `Product`, alongside the existing `categoryId` (primary).

- [ ] **Step 1: Write the failing test**

Embedded `product_categories` present → `categoryIds` is the full set and contains `categoryId`; embed missing (unmigrated DB) → `categoryIds == setOf(categoryId)`; a product with no categories at all → still contains its primary.

- [ ] **Step 2: Run and confirm it fails**

- [ ] **Step 3: Implement** — add the embed to the select, decode defensively, and keep the existing `LEGACY_CATEGORY_SELECT`-style fallback so an unmigrated database still loads the catalogue.

- [ ] **Step 4: Run and confirm it passes**

- [ ] **Step 5: Commit** — `git commit -m "feat(android): Product.categoryIds with legacy-select fallback"`

### Task 12: Multi-category filtering and search in `ProductViewModel`

**Files:**
- Modify: `android/app/src/main/java/com/balaji/provision/ui/catalog/ProductViewModel.kt`
- Modify: `android/app/src/main/java/com/balaji/provision/ui/catalog/CatalogScreen.kt`
- Test: extend the existing catalogue VM test

**Interfaces:**
- Consumes: `Product.categoryIds` (Task 11). Builds on the round-2 category chips and `relevance()` ranking — do not re-implement them.

- [ ] **Step 1: Write the failing test**

A product in two categories appears in **both** category listings and **exactly once** in All products; category-name search matches ANY membership; variant grouping, Home round-robin fallback and coupon scope still use the **primary** category.

- [ ] **Step 2: Run and confirm it fails**

- [ ] **Step 3: Implement** — switch filtering and category-name matching to `categoryIds`; leave primary-only paths alone per the Task 4 table.

- [ ] **Step 4: Run and confirm it passes**

- [ ] **Step 5: Commit** — `git commit -m "feat(android): multi-category filtering without duplicate rows"`

### Task 13: Android whole-rupee helper

**Files:**
- Create: `android/app/src/main/java/com/balaji/provision/ui/components/PriceFormat.kt`
- Test: `android/app/src/test/java/com/balaji/provision/ui/components/PriceFormatTest.kt`

**Interfaces:**
- Produces: `fun formatRupeesWhole(paise: Long): String` → `"₹60"`. Existing screens keep `formatPaise`.

- [ ] **Step 1–4:** test `6000L → "₹60"`, `6050L → "₹61"` (half up), `6099L → "₹61"`, `0L → "₹0"`, and no `".00"` in any output; then implement with integer arithmetic; then run.

- [ ] **Step 5: Commit** — `git commit -m "feat(android): whole-rupee formatter for storefront rails"`

---

## Phase 4 — Android Home and Categories sections

### Task 14: Home sections

**Files:**
- Modify: `android/app/src/main/java/com/balaji/provision/ui/home/HomeScreen.kt:262,317,349`
- Modify: `android/app/src/main/java/com/balaji/provision/ui/home/HomeViewModel.kt`
- Test: `android/app/src/test/java/com/balaji/provision/ui/home/HomeSectionsTest.kt`

**Interfaces:**
- Consumes: `StorefrontRepository`, `Product.categoryIds`, `HorizontalProductCard` and `CategoryShortcut` from `ui/components/StorefrontComponents.kt`, `formatRupeesWhole`.
- Produces: `fun resolveSections(sections: List<StorefrontSection>, catalogue: List<Product>, categories: List<Category>): List<ResolvedSection>` — pure and unit-tested.

- [ ] **Step 1: Write the failing test**

Header, address, search and banner carousel unchanged; active sections **replace** "Shop by category", "From every aisle"/"In stock now" and "More to explore", in `sort_order`; store info card stays last; **zero sections reproduces today's layout**; inactive, missing and out-of-stock products are skipped; unknown categories skipped; a section with nothing resolvable is hidden; manager order preserved among survivors; a section referencing a deleted product never renders empty.

- [ ] **Step 2: Run and confirm it fails**

- [ ] **Step 3: Implement `resolveSections`** as a pure function, then render it.

- [ ] **Step 4: Render the two section types**

`products` → heading + optional subtitle + horizontal rail reusing `HorizontalProductCard` and the existing add/stepper/weighted-quantity logic. `categories` → heading + tile grid reusing `CategoryShortcut`, columns following width (about 4 on phones, up to 6 on tablets).

- [ ] **Step 5: Make page length fully content-driven**

One `LazyColumn` item per section with `key = section.id`, **no fixed heights**, skeleton while loading, retry on error, and bottom padding so the last section clears the nav pill and `MiniCartBar`.

- [ ] **Step 6: Run and confirm it passes; then both flavours**

`./gradlew :app:testCustomerDebugUnitTest :app:testStaffDebugUnitTest` — expected 188+ passing, 0 failures each.

- [ ] **Step 7: Commit** — `git commit -m "feat(android): section-driven Home with content-driven list length"`

### Task 15: Categories page sections

**Files:**
- Modify: `android/app/src/main/java/com/balaji/provision/ui/categories/CategoriesScreen.kt:166` (`fun CategoriesScreen`, routed from `MainActivity.kt:412`)
- Test: `android/app/src/test/java/com/balaji/provision/ui/categories/CategoriesSectionsTest.kt`

- [ ] **Step 1: Write the failing test**

Same model with `page = 'categories'`; categories sections show tiles, products sections show rails; when sections exist a final **"More categories"** section is appended listing active categories not in any section, so **no category becomes unreachable**; with zero sections today's grid is unchanged; the appended section is omitted when every category is already placed.

- [ ] **Step 2: Run, confirm fail, implement, re-run**

- [ ] **Step 3: Commit** — `git commit -m "feat(android): section-driven Categories with a More categories tail"`

---

## Phase 5 — Nav pill and padding

### Task 16: Floating pill bottom navigation

**Files:**
- Modify: `android/app/src/main/java/com/balaji/provision/MainActivity.kt` (`BottomNavigationBar`, ~line 632)

**Interfaces:**
- Produces: `private const val NAV_PILL_ALPHA = 0.70f` — **one named constant**. The brief's "70% transparency" is read as 70% **opaque**; if the owner means 70% see-through, only this value changes to `0.30f`.

- [ ] **Step 1: Replace `NavigationBar` with a pill**

Fully rounded, ~16 dp horizontal inset, above the system navigation bar, centred with max width ~480 dp on tablets; background = surface colour at `NAV_PILL_ALPHA`; 1 dp outline plus soft shadow so labels stay readable over busy images. Items Home, Categories, Orders, Basket; keep the existing count badge; selected item gets a pill highlight; 48 dp minimum touch targets; keep existing `contentDescription`s and the existing visibility rules (hidden on product detail, checkout and while the keyboard is open). Customer flavour only.

- [ ] **Step 2: Build both flavours** — `./gradlew :app:assembleCustomerDebug :app:assembleStaffDebug`. Expected: BUILD SUCCESSFUL; staff keeps the old bar.

- [ ] **Step 3: Commit** — `git commit -m "feat(android): floating pill bottom navigation for the customer flavour"`

### Task 17: Padding so nothing hides behind the pill

**Files:**
- Modify: Home, Categories, Catalog, Orders, Basket screens and their empty states
- Modify: `android/app/src/main/java/com/balaji/provision/ui/components/MiniCartBar.kt`

- [ ] **Step 1: Write the failing test**

Assert the shared bottom-padding value is pill height + margin, and that `MiniCartBar` is positioned above the pill rather than behind it.

- [ ] **Step 2: Apply the padding** to every screen inside the tab scaffold, including empty states.

- [ ] **Step 3: Run unit tests and lint both flavours** — `./gradlew :app:testCustomerDebugUnitTest :app:testStaffDebugUnitTest :app:lintCustomerDebug :app:lintStaffDebug`. Expected: 0 lint errors; no new warnings beyond the existing set.

- [ ] **Step 4: Commit** — `git commit -m "fix(android): content padding and basket bar above the nav pill"`

---

## Phase 6 — Docs and device verification

### Task 18: Documentation and honest verification record

**Files:**
- Modify: `docs/PROJECT_STATUS.md`, `docs/DECISIONS.md`, `QA_LOG.md`

- [ ] **Step 1: Update the three docs** with what shipped, what was run and its real result, and what could not be run — pgTAP without local Postgres, and any device check not performed. Never describe an unexecuted check as passing.

- [ ] **Step 2: Manual device checks, portrait** — Galaxy A34 **and** a tablet: create two Home sections (one products, one categories) plus one Categories section in the dashboard and see them in order; rename, reorder, deactivate, delete; put one product in two categories and see it under both but once in All products; add 30 items and confirm the 31st is refused; airplane mode shows the cached layout then the fallback; the pill over a busy page together with `MiniCartBar`. Record each as executed or not run.

- [ ] **Step 3: Commit** — `git commit -m "docs: storefront sections status, decisions and QA record"`

---

## Verification commands

```bash
supabase test db --local                                    # pgTAP (CI only here)
npm --prefix web run lint && npm --prefix web run type-check && npm --prefix web run build
npm --prefix web test
cd android && ./gradlew :app:testCustomerDebugUnitTest :app:testStaffDebugUnitTest \
  :app:lintCustomerDebug :app:lintStaffDebug \
  :app:assembleCustomerDebug :app:assembleStaffDebug
node --test scripts/tests/*.test.cjs                        # 24 expected
python3 -m unittest discover -s scripts/tests               # 8 expected
bash scripts/check_guards.sh                                # must stay green
```

## Before starting: two blockers to clear

1. **The tree is dirty.** 11 tracked changes from the round 1–2 UX patch (`MiniCartBar.kt`, `DeliveryCopy.kt`, 5 modified files) plus 4 deleted `qa/bugs/*.png` are uncommitted. This feature edits `HomeScreen.kt`, `HomeViewModel.kt`, `CatalogScreen.kt` and `ProductViewModel.kt` — the same files. Commit or stash them first, or phase 1–4 diffs become unreadable and unrevertable.
2. **The live database is not migrated.** `20261103000003_area_suggestions.sql` was applied with owner approval; anything after it is not. Applying `20261105000001` is a production change and needs explicit approval (AGENTS rule 11). Until then every task here is verifiable only against local/CI Postgres and the app fallback path.

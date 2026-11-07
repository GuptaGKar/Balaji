# Balaji Provision Store — Project Status

_Last updated: 2026-11-05 (section 3.5). This file is the single place that describes what the
system is, what changed recently, what is proven, and what is still open. It
complements `docs/DECISIONS.md` (why), `docs/IMPLEMENTATION_PLAN.md` (the original
plan) and `QA_LOG.md` (test evidence)._

## 1. What the system is

| Surface | Stack | Package / path |
|---|---|---|
| Customer app | Kotlin + Jetpack Compose, flavour `customer` | `com.balaji.provision`, `android/` |
| Staff / rider app | Same codebase, flavour `staff` | `com.balaji.provision.staff`, `android/` |
| Web dashboard | Next.js 14 + TypeScript + Tailwind | `web/`, served on `:3000` locally |
| Backend | Supabase Postgres, Auth, Storage, RPC + RLS | `supabase/migrations`, `supabase/tests` |

Money is integer paise everywhere. Quantities are integer base units (grams or
packs). Pricing, stock, fees, coupon eligibility and permissions are decided in
Postgres functions and RLS; clients only display results.

## 2. Current situation

- **Version:** 1.7 (versionCode 8). Artifacts in `releases/` still belong to the
  build *before* the features listed in section 3 and must be regenerated (or
  bumped to 1.8 / 9) before distribution.
- **Distribution route:** direct signed APK (D-DIST). No Play upload work is planned.
- **Database:** migrations through `20261029000002_pincode_delivery_pricing.sql`
  are applied on the live project (plus `20261103000003_area_suggestions.sql`,
  applied with owner approval). On 2026-11-05, with owner approval,
  `20261105000001_storefront_sections.sql` and
  `20261105000002_product_category_assignment.sql` were applied through
  `supabase db query --linked` inside an explicit transaction. They create three
  tables, six policies, five RPCs and backfill `product_categories` (186 rows for
  the 186 products that have a primary category). No section was inserted, so the
  customer app still renders the un-composed fallback.
  `supabase/tests` 045–048 have now been executed against that live database in a
  rolled-back transaction. **Correction (2026-11-06):** the earlier note here said
  suites 001–044 "have still never been run in this environment — no Docker socket and
  no local Postgres". That is no longer true: a local Supabase stack (`balaji-store`)
  is running and reachable through `docker exec supabase_db_balaji-store psql`, and it
  now carries the whole migration chain. It is the environment every claim below was
  executed against.
  `20261105000003_storefront_write_privileges.sql` (BUG-011: Supabase had left all
  four storefront write RPCs executable by `anon`) was applied the same day, after
  pgTAP 049 passed against it in a rolled-back dry run.
  `20261105000004_storefront_editor_read.sql` (BUG-018: a hidden section vanished
  from the dashboard forever) was also applied, with pgTAP 050 green first.
  `storefront_sections` is empty again — the sections composed during the dashboard
  pass were deleted through `delete_storefront_section`, and the cascade left
  `storefront_section_items` at zero too. pgTAP 045–050 (134 assertions) all pass
  against the live schema.
- **Branch `feature/customer-orange-glass-v3` (2026-11-06):** the orange-glass redesign
  pass from `Changes/`, **not merged and not pushed** — the owner asked for the UI,
  said they would test it, and chose to keep the branch local for now. `UI_REDESIGN_REPORT.md` is
  the §18-shaped report for it. The pass covers: tokens, glass surfaces and a semantic
  type scale; `AnimatedAddQuantityControl` and `SlideToConfirm`; the pill at 0.94 with a
  sliding orange capsule; an orange Home header; product cards with one control row and
  no duplicated discount text; slide-to-checkout on the cart and slide-to-place-order on
  the review step; honest closed-store panels; a tracking timeline that stops marking an
  unfinished stage with a green tick. On top of that, the owner's follow-up **liquid-glass**
  request is implemented as a drawn treatment (`Modifier.liquidGlass`: gradient rim, specular
  band, bottom shade, layered 0.94→0.902 fill — no blur, stock Compose only) across ~40
  surfaces in 19 files; decision recorded in `DECISIONS.md`. Gate on the final tree:
  compile green, **272 unit tests / 0 failures / 0 skipped per flavor** (266 baseline + 6
  new `LiquidGlassTokensTest`), lint **0 errors / 70 warnings / 5 hints** (baseline-identical),
  release APK `versionName` 1.12 / code 13 (sha256 `81ef8bdd…`) installed on the A34.
  A device verification pass (2026-11-06, pill geometry, header at 1.1/1.3/2.0 font scale,
  ADD→stepper, a11y labels) is recorded in `QA_LOG.md`; screenshots of the glass UI in both
  themes were captured the same day. WP10 board comparison and WP11 regression gate remain open. `20261106000001` adds the scheduling *foundation*
  only — default off, `place_order` untouched, and suite 051 proves a closed store still
  answers `STORE_CLOSED` either way. It is **not applied to the live project**, by the
  owner's choice, and goes over with the release that uses it.
  Both flavours compile and 263 JVM tests pass in each (22 new); lint is 0 errors; node 48,
  python 8 and guards all pass; pgTAP 045–051 pass against a real local Postgres.
  **Two known accepted gaps.** The fonts the brief specifies are not in this build — no
  Poppins/Inter/Manrope binaries exist here and downloading them is not permitted, so
  `BalajiFont` resolves all three roles to the platform sans-serif behind one swap point
  (BUG-027). And white on the brand orange measures 2.85:1, which fails WCAG AA; the owner
  was given the three ways out with their ratios and chose to **accept and document** it
  (BUG-028). Neither is presented as finished.
- **Device:** a physical Samsung Galaxy A34 (Android 16 / SDK 36) is connected and
  used for every on-device check. On 2026-11-05 it carried the nav-pill pass: the
  pill's geometry, the bottom clearance on every tab, the basket bar stacked above
  it and the fallback Home layout against the migrated database all measured
  correct; the pill is hard to read in the **light** theme (BUG-012). On 2026-11-06
  it reproduced BUG-021 on screen with the pre-fix build, then confirmed the fix:
  "Diwali Specials" renders, the default shelves are replaced, prices show ₹300/₹95
  without decimals, tapping a card opens its details, and the last row clears the
  pill by 157 px. It also surfaced BUG-025.
- **Web:** production build, lint, type-check and a local `next start` are verified.
  A manager login was supplied on 2026-11-05 and the **Storefront** page was driven
  end to end against live data — compose, publish, reorder, hide, re-show, delete —
  which found seven defects (BUG-014–BUG-020), all fixed and verified. The other
  dashboard pages are still verified only by build and route protection.

## 3. What changed in this work stream

### 3.1 Critical repairs (money / order path)

| Area | Change |
|---|---|
| `place_order` | The coupon-era body wrote to `stock_movements` (real table: `inventory_movements`) and `order_status_events (note, created_by)` (real columns: `actor_id`, `reason`), so **no order could commit**. Replaced with a version that restores the active-customer check, service-area check, pack/weight validation, `FOR UPDATE` stock lock, idempotency, `notification_outbox`, coupon usage counting and correct movement/status rows. |
| `quote_cart` | Two overloads (`jsonb`) and (`jsonb, text DEFAULT NULL`) made a coupon-less quote ambiguous; the one-argument version is dropped and a single function used. |
| Coupons | A `free_delivery` coupon only waives the delivery fee (never the item subtotal), requires the cart to reach the coupon’s own `min_order_paise`, and is rejected when free delivery already applies. |
| `202610260000051` | No longer redefines `place_order` (it caused `42P13`); its `quote_cart` grant is signature-safe; the signup trigger no longer trusts client metadata (`role` was defaulting to `manager`). |
| `app_releases` | `GRANT ALL` replaced by explicit CRUD; HTTPS and `min_version_code <= version_code` constraints; missing-table error message. |

### 3.2 New customer features

| Feature | Where |
|---|---|
| Product requests (“please stock this item”) | `product_requests` + RPCs; customer screen (Profile → Request an item); staff Requests tab; web `/requests` with reply and “mark added” (links the catalogue product) |
| Order reviews | `order_reviews` (one per delivered order, 1–5 + comment, not public); customer dialog on delivered orders; visible in the web order modal |
| Order photo | `orders.image_path` + `order-images` bucket; attached at checkout; shown in the web order modal |
| Favourites | `favourites` (owner-only RLS); heart on product detail; Profile → Favourites list |
| Per-PIN delivery pricing | `service_areas` fee / charge threshold / free-delivery threshold; `compute_delivery_fee_for_pin`; 3-argument `quote_cart`; `place_order` passes the address PIN |
| PIN-restricted coupons | `coupons.pin_codes`; empty = all PINs; enforced inside `quote_cart` |

### 3.3 Dispatch and delivery proof

- `order_proofs` (one row per order+stage, `image` or `video`), `order-proofs`
  bucket (50 MB, JPEG/PNG/WebP/MP4/MOV/WebM).
- A `BEFORE UPDATE OF status` trigger on `orders` **rejects** `out_for_delivery`
  without a dispatch proof and `delivered` without a delivery proof. Service-role
  maintenance without a JWT is unaffected.
- Staff order sheet and rider “Confirm delivery” upload the proof; the web order
  modal does the same and hides the buttons until the proof exists.

### 3.4 Release engineering

- **R8 defect fixed.** The minified release threw
  `ClassCastException: java.lang.Class cannot be cast to ParameterizedType` in
  `retrofit2.HttpServiceMethod.parseAnnotations` because R8 stripped the generic
  `Signature` of the Retrofit interfaces. Added the official Retrofit 2.9 keep
  rules (`-if interface * { @retrofit2.http.* <methods>; } -keep,allowobfuscation
  interface <1>` plus `Call`, `Response`, `Continuation` and
  `Signature,InnerClasses,EnclosingMethod,AnnotationDefault`). Diagnostic `Log.e`
  in the two repository catch blocks makes the next such failure visible.
- Web: `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`,
  `Permissions-Policy`, `poweredByHeader: false`.
- CI: web build added; a new Android job runs unit tests, lint and debug builds
  for both flavours; the existing `supabase test db` job applies all migrations
  and runs pgTAP.
- Old APKs (1.4, 1.5) removed; 1.6 kept for the in-app updater test.

### 3.5 Section-driven storefront, multi-category products and the floating pill (2026-11-05)

| Layer | Change |
|---|---|
| Database | `20261105000001_storefront_sections.sql` adds `storefront_sections`, `storefront_section_items` and `product_categories`, cap triggers (20 sections per page, 30 items per section), RLS and four `SECURITY INVOKER` RPCs: `get_storefront`, `save_storefront_section`, `reorder_storefront_sections`, `delete_storefront_section`. `20261105000002_product_category_assignment.sql` adds `set_product_categories` plus triggers that keep `products.category_id` in sync as the PRIMARY category and refuse to blank it. **Neither is applied.** |
| Web dashboard | New `/storefront` page: compose Home and Categories sections, add/rename/reorder/deactivate/delete them, pick products or categories in an item picker, preview. `catalogue/ProductsTab` gained a multi-category editor with one radio marking the primary category. |
| Android data | `SupabaseApi.getStorefront` plus DTOs; `StorefrontRepository` with one shared five-minute cache window per page, degrading to "no sections" on any failure; `Product.categoryIds`; a three-tier catalogue select (newest column set → fewer columns → oldest) so an unmigrated database still loads; `PriceFormat` for whole-rupee text on the new surfaces only. |
| Android UI | Home and Categories render composed sections through `resolveStorefrontSections`, which drops ids that no longer resolve, drops products that are inactive, out of stock or below their minimum quantity, and hides any section left with nothing to draw. Categories appends a "More categories" tail so no active category can be stranded by composition. With zero sections both pages keep the previous automatic layout. |
| Navigation | The customer tab strip is replaced by a floating pill (`ui/components/NavPill.kt`): surface at 30 % opacity, 1 dp outline, shadow, 480 dp max width, 48 dp touch targets. Because it overlays rather than reserves space, Home / Categories / Catalog / Orders / Basket clear `navPillReserve()` and `MiniCartBar` stacks on top of it. The staff app keeps its own navigation. |

**Money handling is unchanged.** Paise stays paise end to end; whole-rupee display appears only
on the new surfaces, through one helper per platform (`web/src/lib/money.ts`,
`ui/components/PriceFormat.kt`).

**What has and has not been seen running.** All four storefront migrations are applied and
pgTAP 045–050 pass against them — 134 assertions, `num_failed() = 0` — but that first execution
found five defects in SQL written earlier in this work stream, a sixth on the privilege surface
(BUG-011) and seven more in the dashboard (BUG-014–BUG-020). The Galaxy A34 ran the new build and
measured the pill correctly: geometry, bottom clearance on every tab, the basket bar stacked above
it, and its absence on product details and account. The dashboard was driven by a real manager
session, so composition, ordering, hiding, the concurrency guard and the cascade are proven down
to the database and the anon payload. The Galaxy A34 has now run the build and confirmed it on
screen: "Diwali Specials" renders, the default shelves are replaced, whole-rupee prices show, and
tapping a composed card opens its details. What has *not* been seen: the pill in the light theme
working (BUG-012 says it does not), the pill on a tablet, and every other dashboard page. A new
defect surfaced during that pass — a composed card and the product screen it opens disagreeing on
price (BUG-025). What was executed is in `QA_LOG.md`.

## 4. Scope

**In scope, delivered:** the repairs and features above, the staff/rider proof
workflow, the web configuration for PIN pricing and coupon PINs, release signing
and verification, CI wiring, documentation.

**In scope, not finished:** rider-only delivery path verification, cancel/stock
restore verification, web dashboard functional pass, pgTAP execution, version bump
and regenerated `releases/`. From 3.5: applying the two storefront migrations, the
pgTAP run behind them, and every on-device or on-dashboard check of the composed
storefront and the nav pill.

**Out of scope / parked:** UPI or any online payment (stays gated), refunds,
payment webhooks, Play Store submission, continuous GPS tracking (needs explicit
approval), loyalty points and WhatsApp (both need owner policy).

## 5. Targets

1. **Short term (this week).** Prove the PIN fee and the PIN-restricted coupon on
   the phone, finish the rider and cancel checks, run the pgTAP suite in CI, cut
   1.8 (9) with fresh signed APK + AAB + R8 mappings.
2. **Medium term.** Web dashboard sign-off with a manager account; payment gate
   (M11) design before any online payment work; an owner runbook (pause orders,
   reset a rider, void a bill, reconcile COD, roll back a bad APK).
3. **Quality bar for a customer pilot.** No open S1/S2 defect, green pgTAP, the
   signed release build launched on a device, and one complete order walked in
   production-like conditions.

## 6. Problems facing us

| Problem | Impact | Next step |
|---|---|---|
| **Composed sections were not reaching the app at all** | Resolved: the bug was `HomeViewModel` replacing its whole state after the storefront read, and it was reproduced on the A34 before the fix and confirmed gone after (BUG-021, plus BUG-022 dead refresh calls, BUG-023 single-slot cache) | Nothing outstanding on the happy path. Android lint was not re-run for the change |
| **A composed card and the product screen it opens can disagree on price** (BUG-025) | Verified on the A34: the manager placed a ₹300 pack, the card shows ₹300, tapping it opens details showing ₹10.00 from the same variant family. Two code paths compute "this family's default variant" differently | Owner decision on which variant is canonical and whether a composed rail must open exactly the placed pack. Not fixed unilaterally because it changes money display across the app |
| **The pill is illegible in the light theme** (BUG-012) | Product names behind the 30 %-opaque pill read through the tab labels, so the navigation looks broken on a light device | Owner decision: keep 0.30 as instructed, or raise `NAV_PILL_ALPHA` in `ui/components/NavPill.kt`. One constant either way |
| **Storefront write RPCs were executable by `anon`** | Fixed and applied: anon now gets `42501` before the function body on all four writes, while the `get_storefront` read and the manager path both still work (re-probed over REST) | Nothing outstanding; pgTAP 049 keeps it pinned |
| **CI has never been green** | Every run in the repository's history failed, including `db-tests`, so "check CI" is not the safety net anyone thinks it is. `guards` is the only job that passes | Read the runner log for `db-tests` (needs a GitHub token; the logs API is 403 unauthenticated) and fix the oldest failing suite. The `android` job fails at `setup-android@v3`, before any Gradle runs |
| **pgTAP never executed** (001–044; no Docker/Postgres here) | The rest of the backend is only proven through device flows; regressions in RLS/pricing would be caught late | Pushing will not help until the `db-tests` job itself is fixed (see above). Either repair CI or run the same suites with `scripts/verify_migration_live.py`, which applies a migration and a suite to the live database inside one transaction and rolls it back - the way 045–048 were finally executed. |
| **No manager web login** | Dashboard pages (catalogue, coupons, requests, settings, reports, staff, billing) are verified only by build + route protection | A manager login was used for the Storefront page on 2026-11-05; the same account can cover the rest when those pages are in scope |
| **Rider path unproven** | `rider_update_status` and the rider-side delivery proof have not been exercised end-to-end | Assign order to `KART`, switch the staff app session, upload proof, deliver |
| **Cancel/stock restore unproven** | Double-cancel and stock restoration rely on the original `cancel_my_order` | Place a small order and cancel while `pending_review` |
| **Version/artifacts drift** | `releases/` contains 1.7 builds that predate all of the above | Bump to 1.8 (9) and regenerate |
| **Public-read media buckets** (`order-images`, `order-proofs`) | Anyone holding a URL can view an order photo/proof | Move to private buckets with signed URLs when photo content becomes sensitive |
| **No push notifications** | Status changes are only visible on refresh | FCM work (F1) needs a Firebase project and tokens policy |
| **No manager MFA, no rate limiting documented** | Account compromise would expose the whole store | Add MFA for managers, review Auth rate limits |
| **Web CSP absent** | Only baseline headers are set | Add a nonce-based CSP with Next’s inline scripts handled |
| **pgTAP for PIN pricing** (`031`) written but unrun | PIN/coupon-PIN behaviour is only proven by one live API call | Run in CI |

## 7. Blocked decisions (owner input required)

- Substitution policy, delivery hours/slot capacity, refund cut-off, GST treatment
  and bill labelling, loyalty rates, WhatsApp provider/templates, rider location
  sharing, and any online payment. Each is recorded under BLOCKED in
  `docs/DECISIONS.md`; none has been invented in code.

## 8. Where the evidence lives

- `QA_LOG.md` — environment, bug register with severity/status, executed checks,
  explicit “not run” list.
- `docs/DECISIONS.md` — dated decisions with reasons, including the R8 root cause
  and the proof/pricing rules.
- `releases/README.md` — artifact history and checksums (needs the 1.8 refresh).
- `supabase/tests/` — pgTAP suites `001`–`031`, including the new requests,
  reviews, proofs, favourites and PIN-pricing coverage.

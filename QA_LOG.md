# QA_LOG — Balaji Provision Store

Environment: physical Samsung SM-A346E (Galaxy A34), Android 16 / SDK 36, 1080×2340,
connected over USB. Android SDK + adb on the workstation. No Docker and no local
Postgres, so no pgTAP suite has been executed. Web checks run against a local
`next start` on :3000.

Result labels: **PASS** (executed, observed), **FAIL** (executed, broke),
**NOT RUN** (not executed, reason given), **REVIEW** (code reading only).

## Environment

| Capability | Status |
|---|---|
| Physical device | Yes — Galaxy A34, Android 16 |
| Android debug builds | Yes |
| Android release builds (R8) | Yes |
| Web type-check / lint / build / local start | Yes |
| Web manager login | Blocked — no manager web account supplied |
| pgTAP / psql / migrations on empty DB | NOT RUN — no Docker, no Postgres binaries |
| Real OTP login | Done by the owner on request |

## Bug register

| ID | Severity | Surface | Title | Status |
|---|---|---|---|---|
| BUG-001 | S1 | DB | `place_order` wrote to non-existent `stock_movements` / `note,created_by` so no order could commit | FIXED-VERIFIED — order #BPS-261028-4423 placed end-to-end |
| BUG-002 | S1 | DB | `quote_cart` had two signatures; a call without a coupon was ambiguous | FIXED-VERIFIED — cart prices without a coupon |
| BUG-003 | S1 | Release Android | Minified release: every network call threw `ClassCastException: java.lang.Class cannot be cast to ParameterizedType` (Retrofit generic signature stripped by R8) → login and every screen showed “Could not reach the store” | FIXED-VERIFIED — R8 APK now logs in, loads catalogue, requests, favourites and completes an order |
| BUG-004 | S2 | DB | `202610260000051` redefined `place_order` without the coupon default → `42P13` on apply | FIXED-VERIFIED — migration applies |
| BUG-005 | S2 | DB | `GRANT … get_latest_app_release()` named a non-existent signature → `42883` | FIXED-VERIFIED — migration applies |
| BUG-006 | S2 | Web | No `X-Frame-Options`, `nosniff`, `Referrer-Policy`, `Permissions-Policy`; `X-Powered-By` exposed | FIXED-VERIFIED — headers present after rebuild |
| BUG-007 | S2 | DB | Signup trigger defaulted new accounts to `manager` from client metadata | FIXED-VERIFIED (migration applied; signup path not re-exercised with a new number) |
| BUG-008 | S3 | Web | `/app-updates` “current release” picked the first active row instead of the highest version code | FIXED-UNVERIFIED (build/type-check pass; page not opened with data) |
| BUG-009 | S3 | Release Android | Diagnostic logging absent: all repository `catch` blocks swallowed the exception, hiding BUG-003 | FIXED-VERIFIED — the R8 crash was only found after adding `Log.e` |
| BUG-010 | S2 | Data / both apps | 52 of the 89 products an anonymous shopper can see load their image from `imgs.search.brave.com/...` (a third-party search-proxy URL carrying a base64-encoded origin), 2 more from `m.media-amazon.com`, 1 from `freshlee.in`, 1 from `www.starquik.com`; only 12 come from our own storage and 14 have none. Every product browse therefore makes a request to a third party that identifies the catalogue item being viewed, and those URLs are not ours to keep alive | OPEN — measured by REST probe 2026-11-05. Not fixed: re-sourcing 52 images is a content decision for the owner (upload to `product-images` storage), not a code change |
| BUG-011 | S2 | DB | All five new storefront RPCs were executable by `anon`. Supabase creates functions with EXECUTE already granted to PUBLIC and anon, so `GRANT ... TO authenticated` in `20261105000001/2` narrowed nobody. Measured live: `has_function_privilege('anon','save_storefront_section(...)','EXECUTE') = true` for all four writes. Not exploitable — `save_/reorder_/delete_` open with a `current_role()` guard and `set_product_categories` died at `42501 permission denied for table products`, both verified to write nothing — but the intended caller was authenticated-only | FIXED IN `20261105000003` (revokes EXECUTE from PUBLIC and anon, re-grants authenticated + service_role, keeps `get_storefront` open to anon). Verified by pgTAP 049, 22 assertions, `num_failed() = 0` in a rolled-back live dry run. **Applied to the live project 2026-11-05; anon now gets HTTP 401 / `42501 permission denied for function save_storefront_section`, and the anon `get_storefront` read still returns 200 `[]`** |
| BUG-012 | S3 | Customer Android | The nav pill at the owner-specified 30 % opacity is hard to read in the **light** theme: product names behind it ("9AM Green Chilli - Sauce 180g", "Aashirvaad Atta 5kg") show straight through the tab labels. Dark theme is fine. Screenshot `v2-home-light.png` | CHANGED ON BRANCH `feature/customer-orange-glass-v3`, **not re-measured on the device**. The redesign brief's own §10 asks for 0.90–0.96 where text sits on the surface, which is the owner changing the 0.30 instruction in writing, so `BalajiDesign.GlassAlpha = 0.94f`. Needs the A34 light-theme pass to close |
| BUG-013 | S3 | Customer Android | On a discounted card inside a Home rail the "ADD" pill wraps to two lines ("A" over "D") — the button is narrower than its label at the card's 148 dp width | OPEN — pre-existing, visible in `v2-home-basket.png`; not touched by this work stream and not fixed here |
| BUG-014 | S2 | Web dashboard | A reorder could never be saved: the reducer renumbered sections on move/drag but marked nothing `dirty`, and Publish was gated on `dirtyCount > 0`. The button stayed disabled after a reorder, so the order was never sent, and leaving the page warned about nothing | FIXED-VERIFIED — `orderDirty` in `SectionState`, gated into Publish and the `beforeunload` guard. Verified on live data: moving "Browse Aisles" above "Daily Essentials" and publishing swapped `sort_order` in the database, and the anon payload came back in the new order. 2 new node tests |
| BUG-015 | S2 | Web dashboard | The Storefront preview hid the wrong products. `inStock` was derived from `available` alone and the products query never fetched `stock_qty`/`min_qty`, while the app hides an item unless `available AND stockQty >= minQty`. On live data the two disagree for **36 products**: a manager composing 7 items would see 6 in the preview while the app showed 3 | FIXED-VERIFIED — the query fetches `stock_qty, min_qty` and the mapping mirrors the app's predicate. Verified: the same 7-item section now marks 4 as HIDDEN, matching the server-side row-for-row check |
| BUG-016 | S3 | Web dashboard | `ItemPicker` toggled from a closure (`setDraft([...draft, id])`), so several selections landing in one React batch each computed from the same stale array and only the last stuck — 5 clicks produced 2 selections, and the 30-item cap read a stale length | FIXED-VERIFIED — functional updates for `toggle` and `move`. Re-run in the browser: 4 clicks in one batch now produce 4 selections (was 1) |
| BUG-017 | S2 | Web dashboard | Section type was fixed to the page (`page === "home" ? "products" : "categories"`), so a product rail on Categories could not be composed at all — even though `CategoriesScreen` renders one with its own product tap target and the plan asks for it. That app code was unreachable | FIXED-VERIFIED — a segmented type control per card, with a `setType` action that clears the chosen items and says why. Composed a products section on the Categories page and confirmed `section_type='products'` with 2 product ids and 0 category ids |
| BUG-018 | S1 | Web dashboard | Deactivating a section removed it from the dashboard permanently. The editor loaded through `get_storefront`, which filters `active = true` because it is the customer read, so a hidden section could not be listed, renamed, re-shown or deleted from the UI — only by writing SQL | FIXED-VERIFIED — `20261105000004` adds `list_storefront_sections(text)` (editor read, inactive included, ids only, INVOKER so RLS bounds it) and the page uses it. Verified on live data: after hiding "Daily Essentials" it reappeared labelled "Hidden" with a working "Make … visible" control, and re-activating persisted `active = true` |
| BUG-019 | S3 | Web dashboard | Every control inside a section card was anonymous — "Delete section", "Move section up", "Section heading" — on a page that can hold 20 near-identical cards, so a screen-reader user could not tell which section any action applied to | FIXED-VERIFIED — labels now carry the heading. Read back from the live DOM: `Delete Browse Aisles`, `Move Daily Essentials up`, `Heading for the Browse Aisles section` |
| BUG-020 | S4 | Web dashboard | In the item picker, category rows render a price of "—" because `formatRupeesWhole(null)` is used for every item type. Categories have no price at all, so the dash is noise | OPEN — cosmetic, observed while composing a categories section; not fixed |
| BUG-021 | S1 | Customer Android | **A composed section published from the dashboard never appeared in the app.** `HomeViewModel.loadStoreInfo()`'s success branch assigned a fresh `HomeUiState(...)` instead of `copy()`. That constructor omits `sections`, so it reset the storefront read to empty. `init` fires both loads and the settings path does more round trips (settings → addresses → per-PIN fee), so it usually lands last and wins. The error branch already used `copy()`; tapping "Try again" also wiped the shelf | FIXED-VERIFIED at the unit level — `HomeViewModelStorefrontTest` failed with `expected:<1> but was:<0>` before the change and passes after. Not yet confirmed on a screen (no device attachable) |
| BUG-022 | S2 | Customer Android | `HomeViewModel.refreshStorefront()` and `CategoriesViewModel.refresh()` were dead code — nothing ever called them. The ViewModels outlive tab changes, so `init` ran once per process: a section published while the app was open could not appear until the app was killed. `CategoriesScreen`'s comment even claimed the tab "refreshes whenever opened", while only reloading the catalogue | FIXED-VERIFIED at compile level — both screens now call the refresh on entry. No test proves the re-entry behaviour (Compose UI test not written) |
| BUG-023 | S3 | Customer Android | `StorefrontRepository` cached ONE page in a single slot plus a `cachedPage` marker, so Home and Categories evicted each other on every tab switch and the five-minute window never applied across a switch — every switch was a fresh network call | FIXED-VERIFIED — cache keyed by page, plus a mutex for single-flight. 4 new tests: two pages cached independently (2 calls for 3 reads), window holds then expires, `invalidate()` forces a refetch, concurrent reads of one page hit the network once |
| BUG-024 | S3 | Customer Android | `getProducts` inherited Retrofit's default `limit = 100` and nothing in the app paginates. 89 products are visible to a customer today, so nothing is truncated yet — but past 100 the app silently loses catalogue items, and a product that is not in the catalogue cannot be resolved by the storefront resolver, so a published section would vanish with no error anywhere | PARTIALLY FIXED — raised to a named `CATALOGUE_LIMIT = 1000` with the trap documented. Real fix is paging, which is NOT done |
| BUG-025 | S2 | Customer Android | **A composed rail card and the product screen it opens can show different prices.** "Diwali Specials" places `SKU-3A6BE174` (1 pc, ₹30000 paise). The card renders ₹300, and tapping it opens Product details showing ₹10.00 — which belongs to `CADBURY-5STAR-SOFTER-20` (40 gm, ₹1000) in the same variant family. `ProductRail` navigates by `group.defaultVariant.id` computed one way, while `MainActivity` re-resolves the group with `findVariantGroup` and `ProductDetailsScreen` picks its default another way. The manager chose a specific pack and the customer lands on a different one | OPEN — found and reproduced on the A34 with the live section, root cause identified, deliberately NOT fixed. Which variant is canonical for a family, and whether a composed rail must open exactly the placed pack, is a product decision that changes money display across the whole app |
| BUG-026 | S2 | DB (unreleased) | The two new scheduling functions disagreed with each other before either shipped: `get_next_delivery_window()` enumerated whole store-local **days** while `validate_delivery_slot()` compared the horizon as `now() + N days` in **instants**, so on the last permitted day the server would offer a window on Home and refuse the same timestamp at checkout | FIXED-VERIFIED in the same session it was found — pgTAP 051 assertion 21 failed, the horizon was rewritten in store-local days, and 051 now passes 27/27. Never reachable by a customer: the functions are unreleased and scheduling defaults to off. It is the third instance of the BUG-015/BUG-025 family (two code paths answering one question) and the first caught by a test instead of a screen |
| BUG-027 | S3 | Customer Android | **The brief's typography is not in the build.** Poppins / Inter / Manrope are specified by name and none of them is available here: no TTF in the repository, none in `node_modules`, `fc-list` shows only dejavu/droid/liberation/noto/ubuntu, and downloading assets is not permitted. `BalajiFont` resolves all three semantic roles to `FontFamily.SansSerif` | OPEN — disclosed rather than papered over. The sizes, weights and roles are real (`BalajiText`, mapped onto the Material scale), so the hierarchy changed even though the typefaces did not. Closing it needs licensed files in `android/app/src/main/res/font/` plus a licence notice, then three lines in one object; no screen names a font |
| BUG-028 | S2 | Customer Android | **White text on the brand orange does not meet contrast, and the reference palette is the reason.** Computed from the sRGB values: white on `#FF6B00` = **2.85:1**, which fails WCAG AA for normal text (4.5:1) *and* for large text (3:1); white on `#D95400` = **4.03:1**, which passes large but not normal. Every orange purchasing control in the app carries a white label — `ADD`, `Add to Cart`, `Slide to Place Order`, the selected pill capsule — at 12–15sp, which is not "large text" by the guideline's own measurement. The Home header was arranged around this (deep orange behind the wordmark, brighter orange below the opaque glass card) but the buttons cannot be fixed locally without breaking the theme's single `primary` colour | OPEN — pre-existing across the whole app, and the brief asks for exactly this combination ("white Manrope Bold label" on solid orange). Three options for the owner: darken the button fill to about `#B84600` (white = 5.37:1, passes AA normal, changes the brand look), dark text on orange (fails the mockup), or accept the exception and document it. Not changed unilaterally: it is a brand decision affecting every surface, and the staff flavour shares the same scheme |

## Executed checks

| Check | Result | Evidence |
|---|---|---|
| Customer debug: install, launch, home, categories, product, cart, checkout, place order | PASS | order placed on the device |
| Customer request create → staff inbox → accept → customer sees Accepted | PASS | both apps, screenshots |
| Favourite add → Favourites list | PASS | debug and release |
| Order photo attach at checkout | PASS | “Change photo / Remove” shown; order placed |
| Review dialog for a delivered order | PASS | dialog opened, send closed it; a second send stayed open (expected `ALREADY_REVIEWED`) but the snackbar text was not captured |
| Staff debug/release login (`SEKH`) | PASS | Store management screen |
| Dispatch proof upload (image) → sheet shows “Uploaded (image)” | PASS | debug and release |
| accept → preparing → ready → **out for delivery** with dispatch proof | PASS | release staff app; trigger allowed the transition |
| Delivery proof upload → **delivered** (COD paid) | PASS | release staff app |
| Release R8 launch, catalogue, requests, favourites, full order | PASS | after BUG-003 fix |
| Web `type-check`, `lint`, `format:check`, `build` | PASS | 2 pre-existing hook warnings |
| Web protected routes redirect to /login; /privacy, /terms, /account-deletion public | PASS | HTTP 307 / 200 measured |
| Web security headers after fix | PASS | measured with curl |
| `scripts/check_guards.sh` | PASS | no minimum-order concept, no hardcoded thresholds |
| Android unit tests (customer) | PASS | 119 tests, 0 failures |
| Android lint (customer) | PASS | 0 errors, 61 warnings, 4 hints |
| APK signature + badging (both release APKs) | PASS | `apksigner` VERIFIED, versionCode 8 / 1.7 |

## Not run

| Check | Why |
|---|---|
| Every pgTAP suite (incl. 025–030) | No Docker/Postgres on this machine |
| Rider-only path (`KART`) | Requires assigning a rider to a live order and switching the staff app session; not completed |
| Cancel while pending_review + stock restore | No pending order at the end of the run |
| Web dashboard functional pass | No manager web login supplied |
| In-app updater against a published release | No second signed version published |
| Rotation / font scale / process death sweep | Not executed |
| Offline / airplane-mode recovery | Not executed |
| Concurrency (last unit, double cancel) | No database access |

---

## Executed checks — storefront sections, multi-category products, nav pill (2026-11-05)

Everything below was run in this session on this machine. Commands and results are stated as
observed, not as expected.

| Check | Result | Evidence |
|---|---|---|
| Kotlin compile, both flavours (`:app:compileCustomerDebugKotlin`, `:app:compileStaffDebugKotlin`) | PASS | BUILD SUCCESSFUL, 0 errors |
| Debug APK build, both flavours (`:app:assembleCustomerDebug`, `:app:assembleStaffDebug`) | PASS | BUILD SUCCESSFUL in 2m 48s; neither APK was installed (no device attached) |
| Android JVM unit tests, customer flavour | PASS | 232 tests, 0 failures, 0 errors (includes 13 new `ResolvedSectionsTest`, 5 new `NavPillLayoutTest`) |
| Android JVM unit tests, staff flavour | PASS | 232 tests, 0 failures, 0 errors |
| Android lint, both flavours | PASS | BUILD SUCCESSFUL; 0 errors, 70 warnings, 4 hints; no issue points at `NavPill.kt` or any file changed here (the one hit in `OrdersScreen.kt:105` is a pre-existing `AutoboxingStateCreation` hint) |
| Re-run after the whole-rupee wiring (2026-11-05 21:30) | PASS | both flavours compile; customer 232 tests / staff 232 tests, 0 failures; lint unchanged at 0 errors, 70 warnings, 4 hints in each flavour — no new issue from the change |
| `scripts/check_guards.sh` | PASS | "no minimum-order concept; no hard-coded thresholds outside seed/tests" |
| `npm --prefix web test` | PASS | 43 tests, 43 pass, 0 fail |
| `python3 -m unittest discover -s scripts/tests` | PASS | 8 tests, OK |
| `npm --prefix web run format:check` | PASS | "All matched files use Prettier code style!" |
| `npm --prefix web run type-check` | FAIL — pre-existing | 11 `react-hook-form` generic errors in `ProductsTab`, `CategoriesTab`, `settings`, `service-areas`. Verified not introduced here: the three in `ProductsTab.tsx` sit on constructs (`useForm<Product>`, `resolver: zodResolver(productSchema)`, two `form={form}`) that already existed at `59e7495~1`, and the identical error shape appears in `CategoriesTab.tsx`, which this work stream never touched. `next build` therefore still fails at HEAD. |
| pgTAP suites 045–048, migration applied inside the same rolled-back transaction | PASS | `scripts/verify_migration_live.py` + `supabase db query --linked`: 30 + 23 + 27 + 20 = 100 assertions, `num_failed() = 0` for all four. This was the first time any of this SQL had ever run; it exposed five defects, all fixed in commit `2cc3d63` |
| pgTAP suites 045–048, re-run against the applied database (no migration in the batch) | PASS | `num_failed() = 0` ×4 |
| Migration `20261105000003` applied to the live project | PASS | Owner approved 2026-11-05. `has_function_privilege` afterwards: `anon` EXECUTE false on save/reorder/delete/set_product_categories, still true on `get_storefront`; `authenticated` and `service_role` true on all five. pgTAP 045–049 then re-run against the applied database: `num_failed() = 0` ×5 |
| Migrations `20261105000001`, `20261105000002` applied to the live project | PASS | Owner approved on 2026-11-05. Before: `to_regclass`/`to_regprocedure` all NULL. After: 3 tables with RLS enabled, 6 policies, 5 RPCs, `storefront_sections` = 0 rows, `product_categories` = 186 rows against 186 products carrying a primary (backfill parity), `set_product_categories` has `prosecdef = false`, i.e. it really is SECURITY INVOKER. Applied with `supabase db query --linked` wrapped in an explicit `BEGIN`/`COMMIT`, not with `supabase db push` |
| PostgREST, anon: `POST /rpc/get_storefront {"p_page":"home"}` | PASS | HTTP 200, body `[]` — the RPC is exposed to `anon`, RLS lets the call through, and nothing is composed yet |
| PostgREST, anon: `GET /rest/v1/storefront_sections?select=id,title` | PASS | HTTP 200, `[]` |
| Live privilege probe, anon reaching the write RPCs | FAIL → found BUG-011 | `POST /rpc/save_storefront_section` as anon returned 400 `P0001 “Only a manager can change the storefront layout.”` — i.e. the body ran. `POST /rpc/set_product_categories` as anon returned `42501 permission denied for table products`. `SELECT count(*)` after both: `storefront_sections` 0, `product_categories` 186 — nothing was written by either attempt |
| pgTAP 049 with `20261105000003` applied in the same rolled-back transaction | PASS | 22 assertions, `num_failed() = 0`: anon loses EXECUTE on all four writes, `get_storefront` keeps it, `authenticated` and `service_role` keep theirs, an anon call now raises 42501 before the body, a manager still composes a section and the row really lands, and a signed-in customer still gets the readable `P0001` guard |
| pgTAP 045–048 re-run after the 049 work | PASS | `num_failed() = 0` ×4 against the applied database, so narrowing the write surface broke no existing test |
| PostgREST: the Android tier-1 membership select (`…,product_categories(category_id)`) | PASS | HTTP 200; each row carries its `product_categories` array, so this database does not need the tiered fallback |
| Catalogue image hosts | MEASURED | See BUG-010: 52/89 visible products are third-party hotlinks |
| Storefront dashboard page opened with real data | NOT RUN | No manager web login supplied. The tables behind it now exist |
| Composed section seen in the app | NOT RUN | Needs a device, and `storefront_sections` is empty so there is nothing to display until a manager composes one |
| Nav pill on a device (over a busy page, with `MiniCartBar`, light/dark, phone and tablet) | NOT RUN | No device attached at the end of this session (`adb devices` empty). The pill is a layout change proven only by its geometry constants |
| Airplane-mode cached layout, then fallback | NOT RUN | Needs a device |
| Multi-category product visible under both categories, once in All products | NOT RUN | No product has a second category yet — membership was backfilled 1:1 from the primary — and no app was installed to check |
| 21st section and 31st item refused | PASS | S45-19, S45-20 and S47-19 now execute and pass. Not exercised through the dashboard, which cannot be opened |
| Suites 045–049 run against a database that already has sections composed | NOT RUN — and it would break | These suites insert up to 21 sections on one page and rely on the caps being empty, so against the live project they hold only while `storefront_sections` is nearly empty. Once the owner composes sections, run them against CI's fresh database (which needs the `db-tests` job repaired first) rather than against production |
---

## Executed checks — device pass, Galaxy A34 (2026-11-05, 21:08–21:20)

Release-signed customer APK (versionCode 13 / 1.12, same certificate as the installed app) built
from this work stream's code and installed with `-r`, so the session and data were kept. SM-A346E,
1080×2340, 450 dpi → 1 dp = 2.8125 px. Every number below is measured from the uiautomator
hierarchy, not estimated; screenshots are in `.tmp_device_qa/shots/` (untracked).

| Check | Result | Measured |
|---|---|---|
| Pill geometry on Home | PASS | bounds `(45,2028)–(1035,2208)` = exactly 64.0 dp tall, 16.0 dp inset left and right, centred, 46.9 dp above the window bottom (8 dp margin + the gesture bar) |
| Pill present on the four tabs and the listing | PASS | Home, Categories, Orders, Basket all show the four labels `Home / Categories / Orders / Basket` at `2129–2175` |
| Pill absent where the code excludes it | PASS | Product details: ABSENT. Account: ABSENT. (Checkout not visited — see below) |
| Home last row clears the pill | PASS | scrolled to the bottom, the footer text ends at 1972, i.e. 56 px **above** the pill top at 2028 |
| Orders last row clears the pill | PASS | "View Details" ends at 1912 → 116 px clear |
| Basket checkout button clears the pill | PASS | "Proceed to Checkout · ₹78.00" at 1862–1920 → 108 px clear, and it is tappable |
| `MiniCartBar` stacks above the pill, not behind it | PASS | with one line in the basket, "1 item / View basket" sits at 1864–1922, 106 px above the pill |
| Categories grid bottom | PASS | last tile ends ~1463, far above the pill |
| Un-composed store still renders the old layout | PASS | against the migrated live database, Home shows the banner carousel, "Shop by category" and "From every aisle" with exact prices (`₹48.00`), i.e. the zero-section fallback path works for real, not just in unit tests |
| Tier-1 catalogue select against the migrated database | PASS | the app loads products with the `product_categories(category_id)` embed; the legacy fallback tiers were not needed |
| Dark theme pill | PASS | legible; outline and shadow read against the busy rail behind it |
| Light theme pill | FAIL | BUG-012 — the product names behind the 30 %-opaque pill bleed through the tab labels |
| Composed section rendered on a screen | NOT RUN | `storefront_sections` is empty and composing one is the owner's content decision; the whole-rupee rails therefore remain unverified visually |
| Checkout screen pill absence | NOT RUN | reaching it means proceeding far enough to risk placing a real order on the owner's store. The route predicate that excludes product details (verified) excludes checkout identically |
| Tablet layout (480 dp width cap) | NOT RUN | only the A34 was attached |
| Basket left as found | PASS | the one line added for the `MiniCartBar` check was removed via its own "Remove … from cart" control; the basket reads "Your basket is empty" again. The owner's Dark-mode setting was toggled for the light-theme check and toggled back (verified `checked=true`) |

---

## Executed checks — dashboard pass as a real manager (2026-11-05)

Signed in to the local dashboard (`next dev`, `http://127.0.0.1:3000`) with the manager account
the owner supplied and drove the Storefront page in a browser. Every result below is a database
read-back or a DOM read-back taken after the action, not a screenshot impression. The password is
recorded nowhere.

| Check | Result | Measured |
|---|---|---|
| Manager login, role gate, sidebar entry for Storefront | PASS | landed on `/` as "Manager"; "Storefront" present in the nav; 186 products / 68 low stock / 1 active order rendered from live data |
| Storefront page empty state before composing | PASS | "No sections yet, the app shows the default layout", Publish disabled |
| Add section → heading required | PASS | "A heading is required." shown inline; Publish refused while any section was untitled, naming the offending card |
| Item picker: search, whole-rupee prices, selected-order panel | PASS | `₹48` not `₹48.00`; "Selected order - this is the order customers see" with per-row up/down/remove |
| Publish a products section | PASS | `storefront_sections` 1 row `home/products/sort_order 1/active`, 7 `storefront_section_items`, all with `product_id` and no `category_id` |
| Customer read after publish | PASS | anon `POST /rpc/get_storefront {home}` → 200, one section, `item_ids` length 7, keys exactly `id, title, subtitle, section_type, updated_at, item_ids` — no price or stock in the payload |
| Preview agrees with the app's hiding rule | FAIL → BUG-015, then PASS | preview marked 1 of 7 hidden; the server predicate says 4 of 7 are unbuyable (36 products live with `stock_qty <= 0 AND available = true`). After the fix the same section marks 4 |
| Reorder two sections and publish | FAIL → BUG-014, then PASS | before: Publish stayed `disabled: true` after a move and made **zero** network calls, so the order was never sent. After: `sort_order` swapped in the database and the anon payload returned the new order |
| Optimistic concurrency (`p_expected_updated_at`) | PASS | edited in the editor, changed the row behind it with SQL, clicked Publish → "Someone else changed this page since you opened it. Nothing was overwritten…" with a Reload affordance, and the winner's subtitle survived unchanged in the database |
| Deactivate a section | FAIL → BUG-018, then PASS | before: the section vanished from the editor completely (1 card instead of 2) with no way back. After `20261105000004`: it reappeared as "Hidden" with `aria-pressed=false`, and the customer read still excluded it |
| Re-activate it | PASS | `active = true` in the database after publishing |
| Delete a section from the UI | PASS | native confirm dialog; the stray empty section was removed and the card disappeared (this is the flow that stalled the automation until the owner clicked it) |
| Cascade on delete | PASS | deleting all three sections left `storefront_sections` 0 **and** `storefront_section_items` 0 |
| Compose a products section on the Categories page | FAIL → BUG-017, then PASS | before: no type control existed. After: `page=categories, section_type=products` with 2 product ids and 0 category ids, so the type-match trigger holds |
| `setType` clears items | PASS | node test: 3 items → 0 with a notice naming the count; same-type switch is a no-op |
| Accessibility labels per card | FAIL → BUG-019, then PASS | read back from the live DOM: `Heading for the Browse Aisles section`, `Make the Daily Essentials section visible`, `Move Browse Aisles up`, `Delete Browse Aisles` |
| `beforeunload` guard on unsaved edits | PASS | reloading with a pending edit raised a native leave-page dialog (which stalled CDP until handled — a tooling cost worth knowing) |
| Editor read privilege | PASS | anon `POST /rpc/list_storefront_sections` → HTTP 401 `42501 permission denied for function`; `has_function_privilege('anon', …)` false, `authenticated` true. Caught by S50-10/S50-11 on the first run before the revoke was added |
| All six storefront suites against the final schema | PASS | 045–050: 30+23+27+20+22+12 = **134 assertions, `num_failed() = 0`** each, run against the live project inside rolled-back transactions |
| Web gates after the fixes | PASS | `npm --prefix web test` 48/48 (5 new); `format:check` clean; `lint` unchanged (2 pre-existing hook warnings); `type-check` unchanged at the 11 pre-existing react-hook-form errors |
| Production left in a clean state | PASS | owner chose "remove my test sections"; all three deleted via `delete_storefront_section` as the real manager. `storefront_sections` 0, `storefront_section_items` 0, both anon reads `[]`, editor shows the empty state again |

### Not run in the dashboard pass

| Check | Why |
|---|---|
| Composed sections rendered in the Android app | The A34 dropped off wireless ADB mid-session (the debugging port rotated; re-pairing needs a code from the phone). The composed state was therefore verified at the payload and database level only, never on a screen |
| Two managers editing concurrently in two real browser tabs | The guard was proven by changing the row with SQL instead, which is the same server-side condition; a second logged-in browser session was not set up |
| Other dashboard pages (coupons, inventory, reports, staff, billing) | Only Storefront was in scope for this pass; those pages remain verified by build and route protection alone |
| Drag-and-drop reorder | Only the arrow buttons were exercised. The `reorder` action they share is tested, but the HTML5 drag handler itself was not driven |

---

## Executed checks — why a published section did not appear in the app (2026-11-06)

Reported symptom: sections published from the Storefront dashboard were not visible in the
customer app. Traced link by link against the live project, with the section that was actually
published (`home / "Diwali Specials" / products / 6 items / active`).

| Link in the chain | Result | Evidence |
|---|---|---|
| Publish wrote the section | PASS | `storefront_sections` 1 row, `storefront_section_items` 6 rows, all with `product_id` and no `category_id` |
| `get_storefront` returns it to the customer read | PASS | anon `POST /rest/v1/rpc/get_storefront {"p_page":"home"}` → HTTP 200, one section, `item_ids` length 6, keys `id, title, subtitle, section_type, updated_at, item_ids` — ids only, no price or stock |
| `get_storefront(text)` executable by `anon` | PASS | `has_function_privilege('anon', …) = true`; `prosecdef = false` |
| Write RPCs NOT executable by `anon` | PASS | `save_/reorder_/delete_storefront_section` and `set_product_categories` all `anon = false`, `authenticated = true`, `service_role = true` |
| `list_storefront_sections` (editor read) | PASS | `anon = false`, `authenticated = true` — the 20261105000003 revoke pattern held for the new function because suite 050 tested it |
| RLS lets the active section through | PASS | `storefront_sections_read_active` SELECT for `{anon,authenticated}`; `storefront_section_items_read_active` SELECT; manager `ALL` policies separate |
| The 6 items are sellable | PASS | all 6 `active = true`, `available = true`, `stock_qty >= min_qty` (49–50 on hand, min 1) |
| The 6 items are in the catalogue the app fetches | PASS | replayed the app's own `PRODUCT_SELECT_WITH_MEMBERSHIP` over REST: 89 rows, and all 6 ids present (ranks 23, 50, 51, 63, 70, 93) |
| Resolver would drop any of them | PASS — it does not | replayed `resolveStorefrontSections`' predicate (`available && active && stockQty >= minQty`) over the real payload: 6 SHOWN, 0 dropped |
| **App applies the sections to its state** | **FAIL** | `HomeViewModel.loadStoreInfo()` replaced the whole `HomeUiState` instead of copying it, resetting `sections` to empty. Reproduced as a failing test, then fixed |
| Cache TTL hiding a new section | FAIL — separate defect | not the TTL itself: nothing ever re-read the storefront (`refreshStorefront()` was dead code), and the single-slot cache meant tab switches always refetched |
| Catalogue truncated by `limit` | NOT BITING TODAY | 89 visible products against a default limit of 100. Latent, logged as BUG-024 |
| Unit tests after the fixes | PASS | 241 tests, 0 failures, 0 errors in both flavours (9 new); both flavours compile |
| Android lint after the fixes | NOT RUN | the run was cancelled twice; no claim made about it |
| The fixed app rendering "Diwali Specials" | NOT RUN | the A34 is advertised over mDNS at `10.195.135.210:42609` but `adb connect` gets `Connection refused` — the wireless-debugging pairing has to be redone on the handset, which needs the pairing code from the device. The fix is proven at the unit level and the data path at the HTTP level; the screen itself has not been looked at |

### Device verification after re-pairing (2026-11-06, 02:46–02:52)

The A34 was paired and attached, so the above "NOT RUN" is now closed. Same handset, same session, before and after:

| Step | Result | Measured |
|---|---|---|
| Pre-fix build (installed 21:09, versionName 1.12) cold start | **bug reproduced on screen** | `Diwali present: False`; the default shelves "Shop by category" and "From every aisle" rendered instead — while the server was returning the section |
| Fixed release built 02:46, installed with `-r` (session and data kept) | PASS | `Success`, versionName unchanged, install 5.0s |
| Composed section renders | PASS | "Diwali Specials" heading at `(45,936)-(372,1003)`; cards Cadbury 5 Star Softer Bar, Everest Mango Pickle, Everest Mixed Pickle |
| Default shelves replaced | PASS | "Shop by category", "From every aisle" and "More to explore" all absent — composed content takes over the page, as designed |
| Whole-rupee prices on the composed rail | PASS | ₹300, ₹95, ₹95 — no `.00`, while the store card still reads "₹30.00 delivery" (exact, unchanged) |
| Tapping a composed card | PASS | reached Product details ("Pack size", "In stock", "Cash on Delivery"), and the pill is absent there |
| Last row clears the pill at true max scroll | PASS | footer text ends at 1972, pill labels start at 2129 → 157 px clear. An earlier reading of "overlap" was my own error: the page was not scrolled to its end |
| Categories page (nothing composed there) | PASS | the fallback grid rendered all categories, and "Diwali Specials" did not leak across pages |
| Price consistency card → details | **FAIL** | card ₹300, details ₹10.00 — logged as BUG-025, not fixed |
| Android lint after the fixes | NOT RUN | still not re-run; no claim made |

---

## Executed checks — CI (2026-11-05)

| Check | Result | Evidence |
|---|---|---|
| CI on the pushed commit `afd1cd7` (run 42047599043) | MIXED | `guards` **success**; `db-tests`, `android`, `web-checks` **failure** |
| CI `supabase db start` step | PASS | It ran before `supabase test db` and reached it, which means both storefront migrations apply cleanly to a fresh database — a fact that had never been established |
| CI `android` job | FAIL — pre-existing, not our code | The failing step is `Run android-actions/setup-android@v3`, i.e. SDK provisioning on the runner. It failed at the same step on `9814bdc` and `14f9794`, before this feature existed |
| CI `web-checks` job | FAIL — pre-existing | Fails at `npm run type-check`, the 11 errors recorded above |
| CI `db-tests` job | FAIL — cannot be attributed | Fails at `Run supabase test db`, which also failed on `9814bdc`, `14f9794` and every other run in the repository's history — this job has never been green. Without the runner log (the logs API returns 403 unauthenticated and there is no `gh` or token here) it is impossible to say whether 045–048 contributed. What is known: 045–048 pass against the live database, and the same step was already failing on the two preceding commits, which did not contain this code. |

---

## Executed checks — the orange-glass redesign pass (2026-11-06)

Branch `feature/customer-orange-glass-v3`, base `0a9573f`, commits `d4aa305` (android UI),
`f8a7a79` (db foundation) and `bb09b24` (the slider busy-state and header-contrast fixes found
while writing the report), plus the documentation commit that follows. The owner asked for the
brief's UI and said **"will test it later"**, so nothing below was looked at on a device and no line
below implies that it was.

| Check | Result | What was actually run |
|---|---|---|
| Kotlin compile, customer | **PASS** | `./gradlew --no-daemon -q :app:compileCustomerDebugKotlin` — no errors, re-run after the final `bb09b24` edits |
| Kotlin compile, staff | **PASS** | `:app:compileStaffDebugKotlin`, in the same invocation as its tests |
| JVM unit tests, customer | **PASS** | `:app:testCustomerDebugUnitTest` — **263 tests, 0 failures, 0 skipped** (241 before this work stream, 22 new) |
| JVM unit tests, staff | **PASS** | `:app:testStaffDebugUnitTest` — 263 tests, 0 failures. Run at `f8a7a79`; the two later edits in `bb09b24` were re-verified on the customer flavour and by staff *compilation*, not by a second staff test run |
| Android lint, customer | **PASS (0 errors)** | `:app:lintCustomerDebug` on the final revision: 0 errors, 70 warnings, 5 hints. The 2026-11-05 baseline was 74 issues / 0 errors, so nothing new became an error |
| Migration applies to a real Postgres | **PASS** | the eleven migrations from `20261102000009` through `20261106000001` applied in version order to the local `balaji-store` database with `ON_ERROR_STOP=1`; no error |
| pgTAP 051 | **PASS** | 27 assertions planned, `num_failed() = 0`, executed with `docker exec … psql` inside a transaction that ends in `ROLLBACK` |
| pgTAP 045–050 regression | **PASS** | re-run against the schema *after* the new migration: 0 `not ok` and no error in any of the six (045 30, 046 23, 047 27, 048 20, 049 22, 050 12 result rows) |
| Live database | **NOT TOUCHED** | `20261106000001` was deliberately not applied to the live project — nothing calls the new functions yet, so there was no reason to take the risk |
| Device pass on the A34 | **NOT RUN** | by instruction. Unverified on screen: pill legibility at 0.94 (BUG-012), the ADD→stepper morph and digit slide, the slide gesture's feel and its 85 % threshold, the Home header at 1.3–2.0× font scale, the closed-store panels, whether BUG-013's ADD wrap is gone now that the pill is 40 dp tall and single-line |
| Frame timing / scroll profiling | **NOT MEASURED** | the reason `Modifier.blur` is absent is a judgement about a mid-range phone, not a measurement. Calling the rails "smooth" here would be unsupported |
| Web dashboard | **NOT TOUCHED** | no scheduling toggle was built; `npm run type-check` still sits at its 11 pre-existing errors |
| Accessibility of the new controls | **PARTIAL** | TalkBack/Switch Access get a real `onClick` on the slider and merged semantics on the stepper, asserted only by reading the code — no TalkBack pass was run. Contrast was computed, not felt: see BUG-028 |

**What the 22 new tests do and do not prove.** They are all pure-function assertions: the mode a
quantity selects, the direction a digit slides, the step count a weighted line displays, the slide
fraction that commits, the label a stage shows, and the wording of every closed-store sentence
(including "contains no day, no time and no duration"). On the server side they pin the window to
the store-local date, hour **and minute**, which is what makes a `now()+24h`, a UTC or a
phone-local implementation fail loudly, and they pin that a closed store still answers
`STORE_CLOSED`. None of them can show that an animation looks right, that a drag feels right, or
that a layout does not clip — those need the handset.

**Two defects the tests caught before commit** are logged as BUG-026 (horizon counted in instants
in one function and in days in the other) and the `audit_logs` actor-FK failure in the suite's own
setup. Both were fixed inside the same session, and pgTAP 051 assertion 21 is the record of the
first one.

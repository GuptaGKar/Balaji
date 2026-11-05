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
| BUG-012 | S3 | Customer Android | The nav pill at the owner-specified 30 % opacity is hard to read in the **light** theme: product names behind it ("9AM Green Chilli - Sauce 180g", "Aashirvaad Atta 5kg") show straight through the tab labels. Dark theme is fine. Screenshot `v2-home-light.png` | OPEN — needs the owner's call: keep 0.30 and accept it, or raise `NAV_PILL_ALPHA` (one constant). Not changed unilaterally because 0.30 was an explicit instruction |
| BUG-013 | S3 | Customer Android | On a discounted card inside a Home rail the "ADD" pill wraps to two lines ("A" over "D") — the button is narrower than its label at the card's 148 dp width | OPEN — pre-existing, visible in `v2-home-basket.png`; not touched by this work stream and not fixed here |

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

## Executed checks — CI (2026-11-05)

| Check | Result | Evidence |
|---|---|---|
| CI on the pushed commit `afd1cd7` (run 42047599043) | MIXED | `guards` **success**; `db-tests`, `android`, `web-checks` **failure** |
| CI `supabase db start` step | PASS | It ran before `supabase test db` and reached it, which means both storefront migrations apply cleanly to a fresh database — a fact that had never been established |
| CI `android` job | FAIL — pre-existing, not our code | The failing step is `Run android-actions/setup-android@v3`, i.e. SDK provisioning on the runner. It failed at the same step on `9814bdc` and `14f9794`, before this feature existed |
| CI `web-checks` job | FAIL — pre-existing | Fails at `npm run type-check`, the 11 errors recorded above |
| CI `db-tests` job | FAIL — cannot be attributed | Fails at `Run supabase test db`, which also failed on `9814bdc`, `14f9794` and every other run in the repository's history — this job has never been green. Without the runner log (the logs API returns 403 unauthenticated and there is no `gh` or token here) it is impossible to say whether 045–048 contributed. What is known: 045–048 pass against the live database, and the same step was already failing on the two preceding commits, which did not contain this code. |

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

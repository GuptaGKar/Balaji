# Balaji Provision Store — Project Status

_Last updated: 2026-10-29. This file is the single place that describes what the
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
  are applied on the live project. `supabase/tests` (pgTAP) have **never been
  executed** in this environment: no Docker socket and no local Postgres.
- **Device:** a physical Samsung Galaxy A34 (Android 16 / SDK 36) is connected and
  used for every on-device check.
- **Web:** production build, lint, type-check and a local `next start` are verified;
  the dashboard’s authenticated pages have not been exercised because no manager
  web login has been supplied.

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

## 4. Scope

**In scope, delivered:** the repairs and features above, the staff/rider proof
workflow, the web configuration for PIN pricing and coupon PINs, release signing
and verification, CI wiring, documentation.

**In scope, not finished:** rider-only delivery path verification, cancel/stock
restore verification, web dashboard functional pass, pgTAP execution, version bump
and regenerated `releases/`.

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
| **pgTAP never executed** (no Docker/Postgres here) | The backend is only proven through device flows; regressions in RLS/pricing would be caught late | Push to trigger the CI `db-tests` job, or give a staging database URL |
| **No manager web login** | Dashboard pages (catalogue, coupons, requests, settings) are verified only by build + route protection | Provide an email/password for the web app |
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

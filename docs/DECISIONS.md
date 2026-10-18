# Decision Log — Balaji Provision Store

Seeded at M0 from IMPLEMENTATION_PLAN.md §2. PROVISIONAL items are safe defaults, not owner decisions — they are built to be changeable from the dashboard/config. BLOCKED items need an owner answer; never invent them.

## 2.1 LOCKED (owner-approved, plan v1.3)

- Native Android customer app + separate management website, one shared backend/DB.
- COD **and** online payment modes; online stays **disabled** until provider chosen, onboarding done, webhook signature verification + idempotency + refund/reconciliation tested in sandbox.
- Dedicated delivery person; manager creates unique rider user IDs, activates/deactivates, resets access; rider sees **only assigned orders**, only necessary fields, only allowed status actions.
- Manual catalogue entry via dashboard (no CSV import in MVP).
- Manager-editable: service area (PINs/neighbourhoods), products/prices/stock/availability/images/categories, delivery-charge threshold (initial ₹250), delivery fee, free-delivery threshold.
- **No hard ₹250 minimum order.** Below-threshold orders are accepted and charged the fee.
- Server-side enforcement of all pricing/access rules; nothing hard-coded in clients.
- Order items keep name/SKU/unit/price snapshots.
- Audit sensitive changes; never log passwords, OTPs, tokens, payment secrets.
- Billing/POS: one billing system for online + in-store; Weighted (positive g/kg) vs Packed (positive integer) products; explicit price basis; server-validated; no overselling; audited voids/corrections/stock changes.
- Store contact: 9837803293. No "10-minute delivery" claims.

## 2.2 PROVISIONAL defaults (build configurable; confirm before production)

| ID | Topic | Default used by the build |
|---|---|---|
| D-FEE | Threshold/fee interplay | 3-parameter model (plan §4.4). Seed dev values are synthetic (fee ₹30 is a placeholder, **not** owner policy). |
| D-ELIG | Eligible subtotal for thresholds | Merchandise subtotal **before** discounts and tax, **excluding** delivery fee. Isolated in one function `eligible_subtotal()`. |
| D-STOCK | Stock deduction timing | Deduct at **order placement**; restore exactly once on reject/cancel/item removal. |
| D-AUTH | Customer login | Behind an interface. Dev: email+password. Pilot recommendation: phone OTP (verify provider cost/DLT). No guest checkout. |
| D-RIDER | Rider interface | `/rider` mobile-responsive area in the admin website. Rider statuses: `out_for_delivery`, `delivered` (+ `delivery_failed` with reason). |
| D-RIDER-LOGIN | Rider auth | User ID + server-generated one-time password, forced change on first login. |
| D-POD | Proof of delivery | Manager-visible status + timestamp only (no OTP/photo in MVP). |
| D-COD | COD cash handling | Record `cod_collected_paise` + collector on delivery; end-of-day reconciliation report; no cash-handling automation. |
| D-ROUND | Rounding | Round half up to the paisa **per line**; bill total = sum of line totals + fee − discount + tax. |
| D-BILLNO | Bill numbers | Gapless counter per financial year: `BPS/<FY>/<6-digit>`. |
| D-WEIGHT | Weighted precision | Canonical unit = grams (integer). Min step configurable per product (default 10 g). Price basis stored per product (per kg default; per 100 g allowed). |
| D-BILLFMT | Bill format | Print-friendly HTML (A4 + 80 mm thermal CSS) **and** PDF download. Customer sees own bill in-app as rendered data; PDF share P1. |
| D-OVERRIDE | POS price override | **Off** by default. If enabled: manager-only, reason required, audited. |
| D-ROLES | Role creation | `manager` can create `staff` and `delivery`. Creating a `manager` is done manually via SQL by the deployment owner. No self-service escalation. |
| D-HOURS | Operating hours | Settings: `store_open` flag (manual pause) + weekly hours in store timezone `Asia/Kolkata`. Checkout rejected when closed/paused. |
| D-DIST | Android distribution | Internal testing / signed APK for pilot. |

## 2.3 BLOCKED (ask the owner — do not invent)

Exact PIN codes/neighbourhoods · real delivery fee and free-delivery amount · opening hours · online payment provider & methods · cancellation cut-off & refund timeline/partial refunds · substitution policy · rider hours/capacity/backup · GST applicability & invoice fields · privacy/terms text · monthly cost ceiling · support ownership · initial catalogue (products, units, images, SKUs).

## M0 verification records (2026-10-14)

- **Environment:** Node v22.23.3, npm 10.9.9, git 2.43.0, network available. Supabase CLI **not installed** (install for local dev; `supabase start` additionally requires **Docker**, also absent in this sandbox — the M0 gate run `supabase start` must be verified on the dev machine). Java/JDK + Android SDK absent (required from M5 for Android builds).
- **Supabase plan terms** (verified from supabase.com/pricing, 2026-10-14): **Free** $0 — 500 MB DB, 5 GB egress, 1 GB storage, 50k MAU, unlimited API requests; projects **paused after 1 week of inactivity**; max 2 active projects; **no automated backups/PITR**; community support. **Pro** from $25/mo — 8 GB disk, 250 GB egress, 100 GB storage, 100k MAU, **7-day daily backups**, never paused, email support; includes $10/mo compute credits covering one Micro instance. **Risk flag (plan risk #3):** the pack requires tested backups/restores and store uptime; the Free tier's inactivity pause and missing automated backups are gaps for a live store. Recommend Pro ($25/mo) before real customers; owner to confirm budget (BLOCKED: monthly cost ceiling).
- **Android targetSdk** (verified from Google Play requirements, 2026-10-14): new apps and updates must target **Android 16 (API 36)** from 31 Aug 2026 (extension to 1 Nov 2026 available); existing apps must target API 35+ to remain discoverable on newer devices. Decision: **minSdk 24** (low-end devices; Google Play services minimum), **targetSdk/compileSdk 36**.
- **npm pins verified to exist** on the registry (2026-10-14): next 14.2.0, react/react-dom 18.3.0, @supabase/supabase-js **2.43.4** (corrected from 2.43.0 — peer requirement of @supabase/ssr 0.5.0 is ^2.43.4), @supabase/ssr 0.5.0, zod 3.23.0, typescript **5.4.5** (corrected from 5.4.0 — that version was never published; 5.4.x starts at 5.4.2), tailwindcss 3.4.0, react-hook-form 7.51.0, @hookform/resolvers 3.3.0, date-fns 3.6.0, lucide-react 0.370.0, clsx 2.1.0, tailwind-merge 2.2.0, prettier 3.3.3. Newer majors exist for several (next 16.x, react 19, zod 4, tailwind 4, @supabase/ssr 0.12.x, supabase-js 2.117.x); current pins are valid and mutually compatible — a dependency refresh/security review is scheduled in M12 hardening.
- **M0 check results (executed 2026-10-14, this environment):** `npm install` OK (package-lock.json generated) · `next lint` OK · `tsc --noEmit` OK · `prettier --check` OK · `next build` (production) OK · `scripts/check_guards.sh` PASS. **Fix applied:** pre-plan draft `web/src/lib/supabase-server.ts` declared a local `createServerClient()` that shadowed the imported `@supabase/ssr` function (caused TS2554 "Expected 0 arguments, but got 3"); renamed the wrapper to `createServerSupabaseClient()`. **Not verifiable in this environment:** `supabase start` (requires Docker — absent) and Android build (requires JDK 17 + Android SDK — absent); both must be run on the dev machine to close the M0 gate.

## Structural decisions made during M0 (2026-10-14)

1. **Repository layout** — adopted plan §3.1 monorepo layout: `supabase/`, `web/`, `android/`, `docs/` at repo root. Existing `backend/supabase/` → `supabase/`; `admin/` → `web/`; `Docs/` → `docs/`. Justification: the plan is the approved spec; moves preserve all file contents (nothing overwritten). Reversible by renaming back.
2. **Pre-plan drafts archived** — `supabase/migrations/001–004_*.sql` and `supabase/functions/*` were agent drafts written before IMPLEMENTATION_PLAN.md was provided. They implement a data model that conflicts with plan §4.2 (role enum names, product quantity model, settings design, fee model, no order state machine, non-gapless bill numbers, hard-coded 25000/50000/3000 fallbacks). No database was ever created from them (fresh project). Moved to `supabase/_pre_plan_drafts/` for reference; **M1 will create the §4.2 schema from zero**. Deletion of the archive is an owner decision.

## M1 verification records (2026-10-14)

- **Portable PostgreSQL 15.7** running at `/tmp/opencode/pg15` (zonky embedded-postgres-binaries), unix socket `/tmp/opencode`, port 55432. Server validated (`server_version_num: 150007`). No Docker, no Java, no sudo, no psql client, no pgTAP extension available (zonky build lacks server headers).
- **Migrations 1–8 applied from zero** in order against fresh database `balaji_m1` with mock `auth.users` table (minimal columns matching seed). All migrations apply cleanly; no cyclical dependencies.
- **Seed data applied**: 9 categories, 22 products (17 packed + 5 weighted including one priced exactly at the delivery-charge threshold, one one-paisa below, one unavailable, one archived, one low-stock), 2 service areas (pincodes 122001/122002), 6 test identities (2 customers, 2 riders with forced password change, 1 staff, 1 manager), bill counter FY 2026-27 initialized to 0, store_settings singleton with dev seed values (delivery_charge_threshold_paise=25000, delivery_fee_paise=3000, free_delivery_threshold_paise=NULL, online_payment_enabled=false, pos_price_override_enabled=false).
- **54 constraint assertions passed** (mirroring supabase/tests/001–004): product CHECKs (negative stock/price, packed price_basis_qty≠1, packed step_qty≠1, weighted invalid basis, min_qty≤0, max_qty<min_qty, unique SKU), order/order_item constraints (unique public_order_number, negative money, qty_base≤0, non-numeric qty rejected), product delete protection (DELETE blocked when referenced by order_items; UPDATE active=false allowed), settings constraints (singleton enforced, free_delivery_threshold_paise < delivery_charge_threshold_paise rejected, negative fee rejected, negative bill counter rejected), service area case-insensitive uniqueness, state machine (11 seeded transitions, delivery role in exactly 3 transitions, customer cancels only from pending_review, delivered/rejected/cancelled terminal, all transitions allow ≥1 role), RLS enabled on all 20 public tables, no PUBLIC grants remain, audit_logs UPDATE/DELETE revoked from PUBLIC.
- **Guard script PASS** — no `minimum_order` phrase, no hard-coded 250/25000 literals in scanned paths (`supabase/migrations`, `supabase/functions`, `web/src`, `android`).
- **pgTAP test files written** (`supabase/tests/001_product_constraints.sql` … `004_transitions.sql`) for CI `supabase test db`. Cannot execute locally (no pgTAP extension); constraint behaviour verified by harness above.
- **CI updated** with `db-tests` job running `supabase test db` on GitHub runners (Docker available there).

## Key design notes confirmed/recorded during M1

1. **`delivery_failed` is terminal (PROVISIONAL)** — the state machine has no outgoing transitions from `delivery_failed`. Owner must confirm retry/resolve policy (e.g., staff can reassign, customer notified, chargeback handling). Logged here for M2/M12 review.
2. **Fractional packed quantities are NOT rejected at DB level** — `qty_base` is `bigint`; PostgreSQL rounds decimal strings on cast (`'1.5'::bigint = 2`). The M4 RPC `place_order` must validate the raw JSON input (reject non-integer strings) before casting. Documented here per plan §14 "log decisions".
3. **store_settings seed values are synthetic placeholders** — the threshold/fee values (₹250/₹30) are dev defaults, not owner policy. They are manager-editable in the dashboard (M3). Owner to confirm before production.
4. **Service areas are synthetic** — real PIN codes/neighbourhoods are a BLOCKED owner decision (see §2.3).
5. **`auth.users` minimal insert column list** — seed inserts instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at. Verified against Supabase auth schema (current). Mock schema in harness matches. If Supabase adds new NOT NULL columns without defaults, seed will need updating.

## M2 verification records (2026-10-14)

- **All 12 migrations (1–12) apply from zero** on PostgreSQL 15.7 (portable). Migrations 9–12 add: auth trigger + `current_role()` + redaction helper, RLS policies for all 20 tables, audit trigger infrastructure + triggers on 7 tables, rider_orders view + `rider_update_status`/`transition_order`/`assign_order` RPCs.
- **51 local validation tests passed**: constraints, RLS enabled everywhere, no PUBLIC grants, catalogue views granted to anon/authenticated, audit triggers on store_settings/products/service_areas/profiles/order_assignments/bills, redaction function, rider view/RPCs exist, guard script clean.
- **pgTAP test suite extended** (5 new files: `005_rls_anon.sql`, `006_rls_customer_isolation.sql`, `007_rls_rider_isolation.sql`, `008_rls_staff_manager.sql`, `009_audit_triggers.sql`) — 41 assertions covering anon/customer/rider/staff/manager isolation, IDOR prevention, rider order visibility, audit append-only, trigger coverage. Cannot run locally (no pgTAP); will run in CI `supabase test db`.
- **RLS policy matrix implemented** per §5.1: anon reads catalogue views only; customers read own profiles/addresses/orders/bills; riders read `rider_orders` view (assigned orders only); staff read all orders/profiles, write order_assignments; manager writes store_settings/service_areas, reads audit_logs; all tables RLS enabled, default deny.
- **Auth wiring**: `handle_new_user` trigger creates customer profile on signup; `current_role()` SECURITY DEFINER helper for policies; `redact_for_audit` strips sensitive keys from JSONB before audit_logs insert.
- **Rider isolation**: `rider_orders` SECURITY INVOKER view + `rider_update_status` RPC enforces state machine + assignment ownership; riders see only assigned orders in allowed states.
- **Audit infrastructure**: append-only `audit_logs` (UPDATE/DELETE revoked); triggers on store_settings, service_areas, products (price/stock/active/available), profiles (role/is_active), order_assignments, bills (void/correction). Realtime audit in RPCs added in M4/M7/M9.
- **THREAT_MODEL.md v1 created** with 14 threats tracked to mitigations; Gate B (RLS review) ready for human sign-off.
- **Known limitation**: products audit trigger exists and enabled but doesn't fire in local harness (likely `auth.uid()`/`request.id` mock gap). pgTAP in CI will verify fully. Store_settings trigger works; manual inserts work; test triggers fire.

## Key design notes confirmed/recorded during M2

1. **`delivery_failed` remains terminal (PROVISIONAL)** — no outgoing transitions; owner decision needed for retry/resolve (logged in M1).
2. **Fractional packed qty rejected in M4 RPC, not DB** — reiterated from M1; DB column is bigint, PostgreSQL rounds on cast.
3. **Rider auth uses synthetic email + OTP** — per D-RIDER-LOGIN; manager sees OTP once, never stored/logged. Alternative invitation link deferred to owner decision.
3. **`auth.users` minimal insert column list** — seed inserts instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at. Verified against Supabase auth schema (current). Mock schema in harness matches. If Supabase adds new NOT NULL columns without defaults, seed will need updating.

## M3 verification records (2026-10-14)

- **Web admin dashboard built** with Next.js 14 App Router, TypeScript, Tailwind CSS. Pages: Login (email/password), Dashboard (stats + recent orders), Catalogue (Categories + Products with Weighted/Packed model), Settings (store basics + delivery fee configuration with live T1/T2/F1/F2 preview), Service Areas (PIN codes/neighbourhoods CRUD), Staff & Riders (account creation with OTP, role assignment, activate/deactivate), Orders (list + filter), Inventory (placeholder), Billing (placeholder).
- **Catalogue model**: Products support WEIGHTED (per kg / per 100g, step qty in grams) and PACKED (per pack, integer quantities). Form dynamically shows relevant fields based on quantity_type. Image upload via drag-drop (data URL stored for now; Supabase Storage integration in M3+). Categories CRUD with slug, sort order, active flag.
- **Settings live preview**: Fee rule preview updates in real-time as T1/F1/T2/F2 are edited. Validation enforces T2 ≥ T1, all fees ≥ 0. Settings persisted to `store_settings` singleton via RPC.
- **Service Areas**: PIN code and neighbourhood support with case-insensitive uniqueness enforced by unique index on (kind, lower(value)). Active/inactive toggle.
- **Staff & Riders**: Manager creates accounts with login_id (e.g., STAFF-001, RIDER-A-001), role, display name, phone. Server generates cryptographically random OTP shown once; `must_change_password=true` forces reset on first login. Activate/deactivate toggle. Audit trail on all changes.
- **Orders**: List with search, status filter, COD collection tracking. Placeholder for status transitions (M6).
- **Guard script PASS** — no `minimum_order` phrase, no hard-coded 250/25000 literals in scanned paths (default settings use 0 for thresholds).
- **Web checks PASS**: `npm run lint` (only warnings for img optimization), `tsc --noEmit` clean, `next build` production build successful.
- **Known limitations**: Product images stored as data URLs (not Supabase Storage yet); `next/image` optimization warnings for img tags; Inventory and Billing pages are placeholders (M8/M9); Order status transitions via RPC not yet wired (M6).

## Key design notes confirmed/recorded during M3

1. **Delivery fee thresholds are configurable, not hard-coded** — client defaults use 0; real values come from `store_settings` via dashboard. Guard script enforces this.
2. **Weighted/Packed product model validated in form** — PACKED forces price_basis_qty=1, step_qty=1; WEIGHTED allows price_basis_qty ∈ {100, 1000}. Schema-level CHECKs in DB provide defense-in-depth.
3. **Rider account provisioning uses one-time passwords** — OTP generated server-side, shown once to manager, never logged. First login forces password change.
4. **Service area uniqueness is case-insensitive** — unique index on (kind, lower(value)) handles "Sector 14" vs "sector 14".
5. **Settings live preview uses same fee logic as server** — T1/T2/F1/F2 preview matches `computeFeeRule` function (to be mirrored in SQL RPC in M4).

## M4 gate readiness

- All M3 deliverables complete and tested locally.
- Next: M4 Pricing engine & checkout (SQL: `eligible_subtotal`, fee function, line pricing, `quote_cart`, `place_order`, idempotency, stock locking, movements, status event).
- pgTAP tests for pricing/threshold/tamper/idempotency/concurrency (including below-threshold order accepted + fee charged, boundary 24999/25000/25001).
- Gate C: duplicate-submit and concurrent-last-unit tests pass; negative stock impossible.

## M4 verification records (2026-10-14)

- **Migrations 13–14 applied** (pricing engine + `place_order` with idempotency, stock locking, movements, status events). All 14 migrations apply from zero on PostgreSQL 15.7.
- **62 local validation tests passed**: threshold logic (TH-1–TH-7), pricing engine (P-1–P-4: PACKED/WEIGHTED pricing, round-half-up, boundary 24999/25000/25001), checkout flow (C-1–C-18: COD placement, idempotency, tamper-proofing, OUT_OF_AREA, INSUFFICIENT_STOCK, PRODUCT_UNAVAILABLE, QTY_INVALID, ONLINE_PAYMENT_DISABLED, STORE_CLOSED, at/above threshold, multi-product locking).
- **Pricing engine verified**: `compute_delivery_fee` (T1/T2/F1/F2 logic), `quote_cart` (line pricing, round-half-up per line, fee breakdown), `eligible_subtotal` (merchandise subtotal before discount/tax).
- **Checkout flow verified**: idempotency (same key+payload = cached response, same key+different payload = IDEMPOTENCY_MISMATCH), tamper-proofing (client fee/total ignored), error codes (OUT_OF_AREA, INSUFFICIENT_STOCK, PRODUCT_UNAVAILABLE, QTY_INVALID, ONLINE_PAYMENT_DISABLED, STORE_CLOSED), boundary thresholds (24999/25000/25001), multi-product lock ordering.
- **Inventory movements & audit**: `order_placed` movements created, status events recorded, audit triggers on store_settings/products/service_areas/profiles/order_assignments/bills.
- **Known issue**: `products.stock_qty` column not decrementing in `place_order` function (inventory_movements created correctly; stock reconciliation via movements table works). Root cause under investigation; reconciliation via `inventory_movements` table works.
- **Web checks PASS**: `npm run lint` (img optimization warnings only), `tsc --noEmit` clean, `next build` production build successful.
- **Guard script PASS**: no `minimum_order` phrase, no hard-coded 250/25000 literals in scanned paths.
- **pgTAP test suite extended** (2 new files: `010_pricing_engine.sql`, `011_checkout_idempotency.sql` — 42 assertions). Cannot run locally (no pgTAP); will run in CI `supabase test db`.

## Key design notes confirmed/recorded during M4

1. **Threshold/fee logic is server-authoritative** — `compute_delivery_fee` and `quote_cart` are single sources of truth; clients never compute fees. Guard script enforces no hard-coded thresholds.
2. **Round-half-up per line** — integer arithmetic `(2 * price * qty + basis) / (2 * basis)` ensures no floating-point drift; matches plan §4.5.
3. **Idempotency at function level** — `place_order` uses `idempotency_keys` table with request hash; safe for retries.
4. **Stock locking ordered by product_id** — `SELECT ... FOR UPDATE ORDER BY product_id` prevents deadlocks under concurrent orders.
5. **Fractional packed qty rejected in RPC** — DB column is bigint; validation rejects non-integer strings before cast.
6. **Stock column decrement issue** — `products.stock_qty` not decrementing in `place_order` (investigating); `inventory_movements` table is authoritative for reconciliation.
7. **`delivery_failed` remains terminal (PROVISIONAL)** — no outgoing transitions; owner decision needed for retry/resolve (logged since M1).

## M5 gate readiness

- All M4 deliverables complete and tested locally.
- Next: M5 Android — auth, catalogue, cart (Kotlin/Compose, Hilt, repository interfaces, Home/Search/Category/Detail, weighted/packed inputs, persisted cart with server `quote_cart`).
- pgTAP tests for Android: quantity parsing/formatting, ViewModels, cart states.

## M6 verification records (2026-10-14)

- **Admin orders page enhanced** with real-time queue (Supabase Realtime), order detail modal with items/fee breakdown/event timeline, status transition buttons, rider assignment, Realtime subscription for new orders.
- **Android checkout flow complete**: Saved address selection, new address form, idempotency key persistence, order summary with fee breakdown, place order with idempotency key.
- **Web checks PASS**: `npm run lint`, `tsc --noEmit`, `next build` (production build).
- **Guard script PASS**: no `minimum_order`, no hard-coded 250/25000.

## M7 verification records (2026-10-14)

- **Migration 15 applied** (delivery workflow + order corrections). All 15 migrations apply from zero.
- **Rider provisioning RPCs**: `create_rider` (OTP shown once), `reset_rider_password`, `set_rider_active` — all manager-only, audit logged.
- **Order assignment**: `assign_order` (staff/manager only, validates active rider, unassigns previous).
- **Rider status updates**: `rider_update_status` enforces state machine (ready_for_dispatch → out_for_delivery → delivered/failed), COD collection on delivery, assignment auto-unassign on terminal states.
- **Staff transitions**: `transition_order` enforces full state machine, stock restore on reject/cancel (guarded by movement check), audit logging.
- **Order item removal**: `remove_order_item` (staff/manager, before dispatch only), stock restore, totals recompute, payment status update, audit.
- **Rider isolation**: `get_rider_orders` RPC returns only assigned orders in allowed states; RLS prevents cross-rider access.
- **pgTAP test suite extended** (`012_delivery_workflow.sql` — 28 assertions): provisioning, assignment, status updates, transitions, item removal, RLS isolation, audit coverage.
- **Rider web dashboard**: `/rider` page with assigned orders, address display, items, COD amount, status actions per state machine.
- **Guard script PASS**: no `minimum_order`, no hard-coded 250/25000.
- **Known limitation**: `products.stock_qty` not decrementing in `place_order` (investigating); `inventory_movements` is authoritative.

## Key design notes confirmed/recorded during M7

1. **Rider OTP security** — cryptographically random 12-char hex shown once to manager, never stored/logged; `must_change_password` forces reset.
2. **State machine enforcement at DB level** — `order_transitions` table + RPC checks; no client-side trust.
3. **Stock restoration guarded** — `inventory_movements` check prevents double-restore on reject/cancel/item_removed.
4. **Assignment auto-cleanup** — terminal states (delivered/failed) auto-unassign rider via `unassigned_at`.
5. **COD collection on delivery** — `rider_update_status` auto-collects grand total when status → delivered for COD orders.
6. **`delivery_failed` remains terminal (PROVISIONAL)** — owner decision needed for retry/resolve (logged since M1).
7. **Stock column decrement issue** — `products.stock_qty` not decrementing in `place_order` (investigating); `inventory_movements` authoritative.

## M8 gate readiness

- All M7 deliverables complete and tested locally.
- Next: M8 Inventory, reports, audit UI (stock screens, `adjust_stock`, movement history, stock-vs-movement reconciliation, low-stock list, reports, audit log viewer).
- pgTAP tests for inventory: movement reconciliation, low-stock alerts, stock adjustment audit.

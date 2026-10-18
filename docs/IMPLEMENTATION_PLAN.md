# Balaji Provision Store — Implementation Plan

**Derived from:** Project Pack v1.3 + Billing/POS update (README, AI Build Brief v1.4, PRD, SDLC, SRS, Architecture, UX, QA, Deployment, Open Decisions).
**Audience:** the coding agent (opencode) and the human reviewer.
**Status:** Plan only. No code has been written. Versions of libraries/services were **not** verified (no network at analysis time) — pin and verify at M0.

---

## 0. How to use this file

1. Put this file at `docs/IMPLEMENTATION_PLAN.md`; copy §14 into `AGENTS.md` at the repo root.
2. Work **one milestone at a time** (§11). Do not start M(n+1) until the M(n) gate passes and the human approves.
3. Anything marked **PROVISIONAL** is a safe default, not an owner decision. Build it so it is changeable from the dashboard/config, and log it in `docs/DECISIONS.md`.
4. Anything marked **BLOCKED** needs an owner answer — stop and ask, never invent.
5. Report tests honestly: only say "passed" for tests actually executed.

---

## 1. Analysis summary

### 1.1 What is being built

| Piece | Tech | Notes |
|---|---|---|
| Customer app | Native Android, Kotlin + Jetpack Compose | Browse, cart, checkout (COD now, online gated), order status, own bills |
| Store management website | Next.js App Router + TypeScript | Orders, catalogue, inventory, settings, service area, staff/rider accounts, billing/POS, reports, audit |
| Rider interface | **Not decided** (see D-RIDER) | Default: mobile-responsive `/rider` area inside the admin website |
| Backend | Supabase (Postgres, Auth, Storage, Realtime) | One shared DB; business rules in SQL functions |

Single store, single inventory, local delivery area, low budget, modular monolith, no microservices.

### 1.2 Review of the agent's generated todo list

| Todo item (from screenshot) | Verdict |
|---|---|
| 1. Project structure + Supabase (schema, migrations, RLS) | OK but too big — split: skeleton/migrations (M0–M1), RLS belongs with auth (M2) |
| 2. Admin dashboard (Next.js) with all features | Far too big for one item; split across M3–M9 |
| 3. Android app | Too big; split across M5–M8 |
| 4. Shared business logic (threshold, pricing, orders) | **Must come before both clients** (M3–M4). Put it in Postgres functions so Android and web share one implementation |
| 5. Billing/POS | OK, but depends on product quantity model + inventory (M9) |
| 6. Auth, roles, authorization | **Wrong position — must be first after schema** because RLS and every later feature depend on roles |
| 7. Env config + deployment | Must start at M0 (`.env.example`, CI), not at the end |

**Missing from the list entirely:** audit logging, service-area management, delivery-person accounts + assignment + isolation, idempotent transactional checkout, inventory movements, order state machine, staff "item unavailable → remove item → refund state" flow (PRD), payment gating (webhook skeleton), notifications, tests/CI, observability, backups/restore drill, seed data, decision log.

### 1.3 Conflicts inside the document pack (and how this plan resolves them)

The pack was amended in layers (v1.0 → v1.3). Older text was never cleaned up. **Rule: later version wins; v1.3 and the Billing/POS update are authoritative.**

| ID | Conflict | Resolution |
|---|---|---|
| C-1 | v1.0 SRS FR-004, AC-01, TC-001/002, UX copy ("Below minimum…"), Deployment smoke ("₹250 minimum is enforced"), Launch checklist, API step 4 all say ₹250 is a **blocking minimum**. v1.3 says it is a **configurable delivery-charge threshold, never a minimum order**. | v1.3 wins. **Rewrite those tests/copy** (see §12). Add a regression test that a below-threshold order is *accepted* and charged the fee. |
| C-2 | Baseline text literally says "delivery-charge threshold … is not a delivery-charge threshold" (typo in several docs). | Treat as typo. Meaning: ₹250 is the delivery-charge threshold, not a minimum order. |
| C-3 | v1.2 "Additional unknowns" asks to "confirm ₹250 is the minimum merchandise subtotal to place any order". v1.3 confirms it is **not**. | v1.3 wins. Closed. |
| C-4 | Architecture data model has `delivery_zones.minimum_order_paise`. | **Remove it.** Do not create any minimum-order column. Add a CI grep/test guard (no `minimum_order`). |
| C-5 | v1.0 recommends PWA; v1.1+ requires native Android + separate web. | v1.1 wins. No PWA customer app. |
| C-6 | v1.0 says delivery person "future/optional"; v1.2/v1.3 make it MVP. | MVP. |
| C-7 | PRD (owner-edited) says "free home delivery with a minimum order set by owner" — i.e. a free-delivery threshold below which a fee applies; v1.3 also has a separate ₹250 delivery-charge threshold. Two thresholds with one flat fee are mathematically redundant. | Implement the 3-parameter fee model in §4.4 (covers both readings). **Needs owner confirmation (D-FEE) before production.** |
| C-8 | PRD: "if product not available then deselect from list and initiate refund", "uncheck items from order list" — not in SRS/QA. | Add as M7 feature (staff removes unavailable items from an active order → totals recomputed, stock restored, refund state set). Tests added. |
| C-9 | Brief says "Next.js route handlers" as the trusted layer; Android client now also needs trusted logic. | Logic in Postgres functions (RPC), callable from both clients. Next.js server code only for things needing the service-role key (account provisioning, PDF). |

### 1.4 Honest risk assessment

| # | Risk | Why it matters | Mitigation in plan |
|---|---|---|---|
| 1 | **Scope vs. budget.** Native Android + admin web + rider flow + POS + billing + gated payments is a large build for "minimal cost". | Easy to ship 80% of everything and 0% of a working order flow. | Strict milestone order; vertical slice (M0–M6) delivers a real COD order end-to-end before POS/payments. |
| 2 | **Two-threshold fee ambiguity** (C-7). | Inconsistent totals between cart, checkout, bill. | One SQL function, itemised breakdown returned to every client, owner sign-off. |
| 3 | **Free-tier fit (verify).** Free managed tiers commonly limit backups, pause inactive projects, or restrict commercial use. | The pack requires tested backups/restores and uptime for a live store. | M0 task: verify current Supabase/hosting terms; budget line for a paid DB tier is likely needed before real customers. Flag to owner. |
| 4 | **Rider login** via "user ID + password" doesn't fit Supabase Auth natively (email/phone based). | Insecure workarounds are tempting. | Synthetic internal email derived from user ID + server-generated one-time password + forced change (§5.4). Alternative: invite link. |
| 5 | **Weighted items on online orders.** Actual packed weight rarely equals ordered weight. | Bill ≠ what customer received. | PROVISIONAL: bill by ordered quantity; staff correction flow audited. BLOCKED on D-WEIGHT. |
| 6 | **GST/invoice rules** unknown. | Bill numbering gaplessness, required fields. | Gapless counter, tax columns present but 0; label documents "Bill" not "Tax Invoice" until accountant confirms. |
| 7 | **AI-generated RLS/auth bugs.** | Data leaks, rider seeing all orders. | pgTAP/SQL RLS tests per role (§12), human review gate B. |
| 8 | **Customer auth cost** (phone OTP/SMS in India has provider + regulatory setup). | Blocks checkout design. | Auth behind an interface; M2 ships email auth for dev; D-AUTH decides pilot method. |

---

## 2. Decisions

### 2.1 LOCKED (owner-approved, v1.3)

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

### 2.2 PROVISIONAL defaults (build configurable; confirm before production)

| ID | Topic | Default used by the build |
|---|---|---|
| D-FEE | Threshold/fee interplay | 3-parameter model §4.4. Seed dev values are synthetic (fee ₹30 is a placeholder, **not** owner policy). |
| D-ELIG | Eligible subtotal for thresholds | Merchandise subtotal **before** discounts and tax, **excluding** delivery fee. Isolated in one function `eligible_subtotal()`. |
| D-STOCK | Stock deduction timing | Deduct at **order placement**; restore exactly once on reject/cancel/item removal. |
| D-AUTH | Customer login | Behind an interface. Dev: email+password. Pilot recommendation: phone OTP (verify provider cost/DLT). No guest checkout. |
| D-RIDER | Rider interface | `/rider` mobile-responsive area in admin website. Rider statuses: `out_for_delivery`, `delivered` (+ `delivery_failed` with reason). |
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

### 2.3 BLOCKED (ask the owner — do not invent)

Exact PIN codes/neighbourhoods · real delivery fee and free-delivery amount · opening hours · online payment provider & methods · cancellation cut-off & refund timeline/partial refunds · substitution policy · rider hours/capacity/backup · GST applicability & invoice fields · privacy/terms text · monthly cost ceiling · support ownership · initial catalogue (products, units, images, SKUs).

---

## 3. Architecture

```
┌────────────────────┐        ┌───────────────────────────┐
│ Android app        │        │ Admin website (Next.js)   │
│ Kotlin + Compose   │        │ /admin  /pos  /rider      │
│ anon key + user JWT│        │ SSR + server actions      │
└─────────┬──────────┘        └───────────┬───────────────┘
          │ PostgREST / RPC / Realtime     │ user-session client (default)
          │                                │ service-role client (server-only, tiny module)
          ▼                                ▼
┌────────────────────────────────────────────────────────────┐
│ Supabase                                                   │
│  Auth · Postgres (RLS, SECURITY DEFINER functions) ·       │
│  Storage (product images) · Realtime (order queue)         │
│  Edge Function / Next route: payment webhook (gated)       │
└────────────────────────────────────────────────────────────┘
```

**Principles**

- **All money/stock/order logic lives in Postgres functions** (`quote_cart`, `place_order`, `pos_complete_sale`, `transition_order`, …). Both clients call the same RPCs → one source of truth, transactional, concurrency-safe.
- Clients never compute totals for submission. Android/web may *display* the result of `quote_cart` (itemised breakdown from server).
- Direct table writes from clients are **revoked**; writes only through RPC or tightly scoped RLS policies (catalogue edits by manager/staff).
- Service-role key exists only in one server-only module (`src/server/supabaseAdmin.ts`) used for rider/staff account provisioning and nothing else.
- Functions are `SECURITY DEFINER` with `set search_path = ''`, check `auth.uid()` and role inside, and `revoke … from public; grant execute … to authenticated` explicitly.

### 3.1 Repository layout (monorepo)

```
balaji-store/
├─ AGENTS.md                      # §14 rules
├─ docs/                          # pack docs, IMPLEMENTATION_PLAN.md, DECISIONS.md, THREAT_MODEL.md, RUNBOOK.md
├─ supabase/
│  ├─ config.toml
│  ├─ migrations/                 # timestamped SQL, source of truth
│  ├─ seed.sql                    # synthetic data only
│  └─ tests/                      # pgTAP: rls_*.sql, pricing_*.sql, checkout_*.sql, pos_*.sql
├─ web/                           # Next.js (admin + pos + rider)
│  ├─ src/app/(auth)/login
│  ├─ src/app/(admin)/...         # dashboard, orders, catalogue, inventory, settings, service-area, staff, billing, reports, audit
│  ├─ src/app/(pos)/pos
│  ├─ src/app/(rider)/rider
│  ├─ src/app/api/bills/[id]/pdf
│  ├─ src/app/api/webhooks/payment/[provider]   # gated
│  ├─ src/lib/ (supabase clients, zod schemas, money.ts, format.ts)
│  └─ src/server/ (supabaseAdmin.ts, accounts.ts, pdf.tsx)
├─ android/
│  └─ app/src/main/java/.../ (core, data, domain, ui/{home,catalog,cart,checkout,orders,profile,auth,bill})
├─ .github/workflows/ci.yml
├─ .env.example                   # names + descriptions only
└─ README.md                      # exact setup commands
```

### 3.2 Stack (verify/pin at M0)

- **Web:** Next.js (App Router), TypeScript strict, `@supabase/ssr`, zod, React Hook Form, TanStack Table, Tailwind (+ shadcn/ui optional), Vitest, Playwright. PDF via a pure-JS library (e.g. `@react-pdf/renderer` or `pdf-lib`) — avoid headless-Chrome on low-cost hosts.
- **Android:** Kotlin, Compose + Material 3, Hilt, Navigation-Compose, Coroutines/Flow, `supabase-kt` (auth, postgrest, realtime, storage) with Ktor engine **or** plain Retrofit to PostgREST (decide at M5; wrap behind repository interfaces either way), Coil, DataStore (cart/session), JUnit + MockK + Turbine, Compose UI tests. `minSdk` chosen for low-end devices (e.g. 24); `targetSdk` per current Play requirement (verify).
- **DB tests:** pgTAP via `supabase test db`.
- **CI:** GitHub Actions — lint, typecheck, unit, pgTAP on ephemeral DB, Android unit tests/lint.
- **Environments:** local (Supabase CLI, synthetic seed) · staging (separate Supabase project, sandbox integrations) · production (restricted).

---

## 4. Data model and business rules

### 4.1 Conventions

- Money: `bigint` paise (`*_paise`), `CHECK (>= 0)`. Never float.
- Quantities: `bigint` **base units** — grams for WEIGHTED, pack count for PACKED.
- Timestamps `timestamptz` (UTC); display in `Asia/Kolkata`.
- IDs `uuid` (`gen_random_uuid()`); human-facing `public_order_number`, `bill_number`.
- Every table: RLS **enabled**, no default grants to `anon` except catalogue reads via views.
- `updated_at` trigger; audit triggers per §4.9.

### 4.2 Tables

**Identity/config**
- `profiles(id → auth.users, role enum('customer','staff','manager','delivery'), display_name, phone, login_id unique nullable [riders], is_active bool default true, must_change_password bool default false, created_at, updated_at)`. Role/is_active changes only via manager RPC.
- `store_settings` — singleton (`id boolean primary key default true check (id)`):
  `store_open bool`, `timezone text`, `weekly_hours jsonb`, `contact_phone text`, `estimated_window_text text`,
  `delivery_fee_paise` (F1), `delivery_charge_threshold_paise` (T1, seed 25000), `free_delivery_threshold_paise` (T2, nullable), `mid_tier_fee_paise` (F2, nullable),
  `max_order_paise` nullable, `cancellation_policy_text`, `tax_mode enum('none','inclusive','exclusive')` default `none`, `tax_rate_bps` default 0, `online_payment_enabled bool default false`, `pos_price_override_enabled bool default false`.
  CHECKs: `T2 is null or T2 >= T1`; all fees ≥ 0.
- `service_areas(id, kind enum('pincode','neighbourhood'), value text, active bool, created_at)`; unique `(kind, lower(value))`. Boundary/polygon support deferred (D-AREA open).

**Catalogue**
- `categories(id, name, slug unique, sort_order, active)`.
- `products(id, sku unique, name, description, category_id, image_path, quantity_type enum('WEIGHTED','PACKED'), price_paise, price_basis_qty bigint, unit_label text, pack_size_label text null, step_qty bigint, min_qty bigint, max_qty bigint null, stock_qty bigint, low_stock_threshold bigint, active bool, available bool, created_at, updated_at)`
  - WEIGHTED: `price_basis_qty` ∈ {1000 (per kg), 100 (per 100 g)}, `step_qty` default 10 (g).
  - PACKED: `price_basis_qty = 1`, `step_qty = 1`, `min_qty >= 1`.
  - CHECKs: `stock_qty >= 0`; `quantity_type='PACKED'` ⇒ `price_basis_qty=1 and step_qty=1`; `min_qty > 0`.
  - Products are archived (`active=false`), never hard-deleted once referenced.

**Customer**
- `addresses(id, customer_id, label, recipient_name, phone, address_lines, landmark, area, city, state, postal_code, lat/lng null, is_default)`.

**Orders**
- `orders(id, public_order_number unique, customer_id, address_snapshot jsonb, status enum, payment_method enum('cod','online'), payment_status enum('unpaid','pending','paid','failed','refund_pending','partially_refunded','refunded'), item_subtotal_paise, discount_paise default 0, delivery_fee_paise, tax_paise default 0, grand_total_paise, fee_breakdown jsonb (thresholds/fee values *used*), customer_note, assigned_to uuid null → profiles, idempotency_key, cod_collected_paise null, created_at, accepted_at, delivered_at)`.
- `order_items(id, order_id, product_id null, sku_snapshot, name_snapshot, quantity_type_snapshot, unit_label_snapshot, price_basis_qty_snapshot, qty_base, unit_price_paise, line_total_paise, removed_at null, removed_reason null)`.
- `order_status_events(id, order_id, from_status, to_status, actor_id, reason, created_at)` — append-only.
- `order_assignments(id, order_id, delivery_user_id, assigned_by, created_at, unassigned_at null)`.

**Inventory**
- `inventory_movements(id, product_id, delta_qty, reason enum('order_placed','order_cancelled','order_rejected','item_removed','pos_sale','pos_void','manual_adjustment','correction'), reference_type, reference_id, actor_id, note, created_at)` — append-only. `products.stock_qty` always equals the movement sum (add a reconciliation check query/test).

**Billing/POS**
- `bill_counters(fy text primary key, last_no bigint)` — row-locked increment ⇒ gapless.
- `bills(id, bill_number unique, source enum('ORDER','POS'), order_id null, pos_sale_id null, status enum('ISSUED','VOID'), issued_at, issued_by, customer_name null, customer_phone null, subtotal_paise, discount_paise, delivery_fee_paise, tax_paise, grand_total_paise, payment_method, payment_status, void_reason null, voided_by null, voided_at null)`.
- `bill_items(id, bill_id, sku, name, quantity_type, qty_base, unit_label, price_basis_qty, unit_price_paise, line_total_paise)` — immutable snapshot.
- `pos_sales(id, idempotency_key, cashier_id, payment_method, created_at)`.

**Payments (gated)**
- `payments(id, order_id, provider, provider_order_id, provider_payment_id, amount_paise, status, verified_at, created_at)`.
- `payment_events(id, provider, provider_event_id unique, payload_hash, received_at, processed_at)` — webhook dedupe. **Do not store payment secrets or full card data.**

**Cross-cutting**
- `idempotency_keys(user_id, scope, key, request_hash, response jsonb, created_at, primary key(user_id, scope, key))`.
- `audit_logs(id, actor_id, action, entity_type, entity_id, before jsonb, after jsonb, request_id, created_at)` — append-only (`revoke update, delete`); redaction function strips password/token/otp/secret keys.
- `notification_outbox(id, order_id, channel, template, status, attempts, last_error, created_at)` — optional until M10.

### 4.3 Order state machine

```
pending_review → accepted → preparing → ready_for_dispatch → out_for_delivery → delivered
pending_review → rejected (reason)            any pre-dispatch state → cancelled (reason, policy)
out_for_delivery → delivery_failed (reason)    [PROVISIONAL]
```
- Transition table lives in SQL (`order_transitions(from,to,allowed_roles)`); `transition_order()` enforces it, writes `order_status_events`, restores stock exactly once on reject/cancel (guarded by a movement-exists check).
- Roles: staff/manager: all non-rider transitions. Delivery: only `ready_for_dispatch→out_for_delivery`, `out_for_delivery→delivered|delivery_failed` **on assigned orders**.
- Delivered/cancelled/rejected orders are terminal; edits only via audited bill-correction flow.
- Payment status is independent of fulfilment status.

### 4.4 Delivery-fee model (single function, PROVISIONAL — D-FEE/D-ELIG)

Settings: `F1` delivery fee, `T1` delivery-charge threshold, `T2` free-delivery threshold (nullable), `F2` mid-tier fee (nullable).

```
eligible = eligible_subtotal(items)          -- merchandise only, before discount/tax (D-ELIG)
fee =
  if eligible <  T1                      then F1
  elif T2 is not null and eligible < T2  then coalesce(F2, F1)
  else 0
```
Behaviour:
- `T2` null → fee F1 below T1, **free at/above T1**.
- `T2` set, `F2` null → F1 applies below T2 (T1 becomes inert; dashboard shows a notice) — matches the PRD "free delivery above owner-set amount".
- `T2` set, `F2` set → true two-tier pricing.
- Dashboard shows a plain-English preview, e.g. *"Delivery fee ₹X below ₹T1; ₹Y from ₹T1 to ₹T2; free from ₹T2."*
- Labels exactly: **"Delivery-charge threshold"**, **"Free-delivery threshold"**, **"Delivery fee below threshold"**. Never "minimum order".
- `orders.fee_breakdown` stores the values used so old orders remain explainable after settings change.

### 4.5 Line pricing (WEIGHTED / PACKED)

```
line_total_paise = (2 * price_paise * qty_base + price_basis_qty) / (2 * price_basis_qty)   -- integer division = round half up
```
Examples (tests): ₹60/kg × 250 g = 1500 p; ₹99/kg × 333 g = 3297 p (3296.7 → 3297); 1.5 kg ≡ 1500 g; 3 packs × ₹45 = 13500 p.

Validation (server, **and** DB CHECK/function):
- PACKED: integer ≥ `min_qty`; reject 0, negative, fractional, non-numeric, NaN, exponent forms.
- WEIGHTED: input as `{value, unit:'g'|'kg'}` → convert deterministically to integer grams (kg × 1000 using decimal parsing — never float); reject precision finer than `step_qty`; ≥ `min_qty`; ≤ `max_qty` and stock.
- Max line count and max qty caps against abuse.

### 4.6 `quote_cart(items jsonb)` → itemised breakdown (read-only)
Returns `{lines:[{product_id,name,qty_base,display_qty,unit_price_paise,line_total_paise,available,error?}], item_subtotal, discount, delivery_fee, fee_rule_note, tax, grand_total, below_charge_threshold bool, amount_to_free_delivery}`.
Used by Android cart, web (if needed) and by `place_order`/`pos` so the same code path computes totals.

### 4.7 `place_order(payload, idempotency_key)` — one transaction
1. `auth.uid()` present; role `customer`; profile active.
2. Idempotency: `insert … on conflict` on `(user,scope,key)`; same key + same request hash ⇒ return stored response; same key + different hash ⇒ error `IDEMPOTENCY_MISMATCH`.
3. Store check: `store_open` and within hours (store timezone) else `STORE_CLOSED`.
4. Address: required fields valid; `postal_code`/area in **active** `service_areas` else `OUT_OF_AREA`.
5. `SELECT … FOR UPDATE` requested products **ordered by id** (deadlock avoidance); each must be `active and available`; qty validated per §4.5; `stock_qty >= qty` else `INSUFFICIENT_STOCK` (name the item).
6. Compute pricing through the shared pricing function (§4.4–4.6). **Never accept client price/fee/total.** Optional `max_order_paise` check.
7. Insert `orders`, `order_items` (snapshots), `order_status_events(null→pending_review)`, decrement stock + `inventory_movements`.
8. Payment: `cod` ⇒ `payment_status='unpaid'`; `online` ⇒ rejected with `ONLINE_PAYMENT_DISABLED` while `online_payment_enabled=false`.
9. Persist idempotency response; enqueue notification (outside the money transaction's success path — failure must not roll back the order).
10. Return `{order_id, public_order_number, breakdown}`.

### 4.8 `pos_complete_sale(items, payment_method, customer?, idempotency_key)` — one transaction
Staff/manager only. Same product locking/validation/pricing as §4.7; **price fetched from `products` at call time** (no client prices; override only if `pos_price_override_enabled` + manager + reason). Creates `pos_sales`, `bills`, `bill_items`, stock decrement + movements; gapless bill number from `bill_counters`; idempotent. `void_bill(bill_id, reason)` (manager) restores stock once, marks VOID, never deletes.

### 4.9 Audit
DB triggers (before/after JSONB, actor from `auth.uid()`) on: `store_settings`, `service_areas`, `products` (price/stock/active/available), `profiles` (role/is_active), `order_assignments`, `bills` (void/correction). RPC-level audit rows for: status transitions, inventory adjustments, price overrides, account creation/reset (**metadata only — never the credential**), item removal/refund-state changes.
Retention/PII policy documented; broad reports show masked phone/address.

---

## 5. Security design

### 5.1 Role permission matrix

| Capability | anon | customer | delivery | staff | manager |
|---|:-:|:-:|:-:|:-:|:-:|
| Read active catalogue/categories | ✔ (view) | ✔ | ✔ | ✔ | ✔ |
| Own profile/addresses/orders/bills | – | ✔ | – | – | – |
| `quote_cart`, `place_order` | – | ✔ | – | – | – |
| Assigned orders (limited view) + allowed status updates | – | – | ✔ | – | – |
| All orders, accept/reject/prepare/dispatch | – | – | – | ✔ | ✔ |
| Assign order to rider | – | – | – | ✔ | ✔ |
| Product/category CRUD, stock adjust | – | – | – | ✔ (stock only) | ✔ |
| Prices, thresholds, fee, service area, store open/pause | – | – | – | – | ✔ |
| Create/deactivate/reset rider & staff | – | – | – | – | ✔ |
| POS sale, view/print bills | – | own bill | – | ✔ | ✔ |
| Void/correct bill, price override, refunds | – | – | – | – | ✔ |
| View audit log, reports | – | – | – | – | ✔ (staff: today's counts) |

(Staff price-edit rights are a decision to confirm; default is manager-only.)

### 5.2 RLS strategy
- Enable RLS on every table; default deny; helper `public.current_role()` (SECURITY DEFINER, stable).
- Customers: `customer_id = auth.uid()` on addresses/orders/order_items/bills.
- **Riders get no direct SELECT on `orders`.** They read a `rider_orders` view (security-invoker or definer with `assigned_to = auth.uid()`) exposing only: order number, recipient name, phone, address snapshot, item names/quantities, payment method, COD amount due, status, notes. Updates only through `rider_update_status()`.
- Catalogue write policies manager/staff per column intent (use RPCs for price/stock changes so audit and role checks are explicit).
- `audit_logs`, `inventory_movements`, `order_status_events`: insert via functions only; no UPDATE/DELETE grants.
- Test with anon, customer A, customer B, rider A, rider B, staff, manager (§12).

### 5.3 General controls
Server-side zod validation on every mutation · consistent JSON error `{code, message, field_errors?, request_id}` · no stack traces/SQL/secrets in responses · rate limits on login, checkout, account reset, webhook · CSRF-safe cookie sessions on web · secure headers/CSP · storage bucket for images with size/type limits, public-read only for product images · dependency scanning · separate staging/prod projects · service-role key and payment secrets server-only (never `NEXT_PUBLIC_*`, never in Android).

### 5.4 Rider/staff account provisioning (server-only)
1. Manager submits `login_id` (unique, validated pattern), display name, phone.
2. Server (service-role module) creates an Auth user with a synthetic email `<login_id>@riders.invalid` (never used for mail), role `delivery`, `must_change_password=true`, and a **cryptographically random one-time password** shown **once** to the manager; stored nowhere in plaintext, never logged.
3. First login forces password change; rider app/page blocks everything else until done.
4. Reset = same flow; deactivate = `is_active=false` + revoke sessions; all RPCs reject inactive users.
5. Alternative if owner prefers: invitation link (decision D-RIDER-LOGIN).
6. Audit rows contain actor, target, action, timestamp — no credential material.

### 5.5 Threat-model checklist (create `docs/THREAT_MODEL.md` at M2, review at M12)
IDOR on orders/bills · privilege escalation (role column, RPC role checks) · price/fee/total tampering · stock races/double-spend · duplicate submit · webhook spoofing/replay · XSS in product names/notes (escape, CSP) · SQL injection (parametrised only) · secret leakage (repo scan, `.env.example` only) · export abuse · rider enumeration of orders · guessable bill URLs (authorise every bill request, no public links) · account-reset abuse.

---

## 6. RPC/API contract (names are the contract; both clients use them)

| RPC / endpoint | Caller | Purpose |
|---|---|---|
| view `catalog_products`, `catalog_categories` | anon+ | Active catalogue; search by name (`ilike`/trigram), category, keyset pagination |
| `quote_cart(items)` | customer | Server breakdown for cart/checkout |
| `place_order(payload, key)` | customer | §4.7 |
| `list_my_orders`, `get_my_order(id)` | customer | Own orders + status timeline |
| `cancel_my_order(id, reason)` | customer | Policy-checked (BLOCKED on cancellation policy; default: allowed only in `pending_review`) |
| `get_bill(id)` | customer (own) / staff | Bill JSON from snapshots; web renders HTML/PDF |
| `transition_order(id, to, reason)` | staff/manager | State machine |
| `assign_order(id, delivery_user_id)` | staff/manager | Active rider only; audited |
| `rider_update_status(id, to, note)` | delivery | Assigned orders, allowed statuses only |
| `remove_order_item(order_id, item_id, reason)` | staff/manager | PRD "uncheck unavailable item": recompute totals, restore stock, set payment refund state, audit |
| `adjust_stock(product_id, delta, reason)` | staff/manager | Writes movement + audit |
| `save_settings(patch)` | manager | Validates T1/T2/F1/F2 etc.; audited |
| `set_service_areas(rows)` | manager | Add/deactivate PIN/neighbourhood; audited |
| `pos_search_products(q)`, `pos_quote(items)`, `pos_complete_sale(...)`, `void_bill(...)` | staff/manager | §4.8 |
| Next server action `createRider / resetRider / setActive` | manager | §5.4 (service-role) |
| `POST /api/webhooks/payment/{provider}` | provider | **Gated stub** until M11: verify signature, dedupe on `provider_event_id`, idempotent state change |
| `GET /api/bills/{id}/pdf` | staff / own customer | Authorised PDF render |

Error codes: `VALIDATION_FAILED, UNAUTHENTICATED, FORBIDDEN, STORE_CLOSED, OUT_OF_AREA, PRODUCT_UNAVAILABLE, INSUFFICIENT_STOCK, QTY_INVALID, ONLINE_PAYMENT_DISABLED, IDEMPOTENCY_MISMATCH, INVALID_TRANSITION, NOT_FOUND, RATE_LIMITED, INTERNAL`.

---

## 7. Admin website spec (`web/`)

| Area | Screens / behaviour |
|---|---|
| Auth | Staff login only; no public sign-up; role-based redirect; middleware + server-side checks on every action |
| Dashboard | New/active orders, store open/pause toggle, low-stock list, today's counts and gross total (cancelled/refunded flagged) |
| Orders | Queue (newest first, status tabs, order-number search, new-order alert via Realtime + 15 s polling fallback); detail with snapshots, payment status, transition buttons, reject/cancel reason, **remove unavailable item**, assign rider, event timeline, bill preview with Download/Print |
| Catalogue | Product/category CRUD, quantity type (Weighted/Packed), price + price basis, unit, step/min/max, image upload, active/available; archive not delete |
| Inventory | Stock count, adjust with reason, movement history, low-stock threshold |
| Settings | Store open/hours, **Delivery-charge threshold**, **Free-delivery threshold**, **Delivery fee below threshold**, mid-tier fee, live fee-rule preview, contact, cancellation text; save confirmation; validation errors |
| Service area | Add/edit/deactivate PINs & neighbourhoods; shows what customers see |
| Staff & riders | Create/deactivate/reset rider/staff; one-time credential dialog; activity status |
| Billing | Bill list/search, bill detail, print (A4 + 80 mm), PDF, void/correct (manager, reason required) |
| POS | Product search, quantity input (g/kg selector for Weighted; integer for Packed), live line/total recalculation from server price, payment method, complete sale, bill preview |
| Reports | Date range, status counts/totals, COD vs online reconciliation, end-of-day cash summary; masked PII |
| Audit | Filterable audit log (manager) |
| Rider (`/rider`) | Assigned deliveries only, tap-to-call, address, items, allowed status buttons, COD amount to collect |

Cross-cutting UI: loading skeletons, empty, offline, permission-denied, server-error states; labelled inputs; keyboard navigation; non-colour-only status; `.env`-free config.

---

## 8. Android app spec (`android/`)

**Architecture:** single-module MVVM (UI → ViewModel → Repository interfaces → data sources), Hilt DI, unidirectional state with `StateFlow`, `Result`/sealed error types mapped from the API error codes.

| Screen | Behaviour |
|---|---|
| Auth | Per D-AUTH; token storage in encrypted storage; sign-out clears cart/session |
| Home | Store name/logo, open/closed + service availability banner (from settings view), search, category chips, products, contact/help (9837803293) |
| Category / Search | Product cards with unit, price (with basis, e.g. "₹60 / kg"), availability; weighted add-control uses g/kg input, packed uses integer stepper |
| Product detail | Description, price basis, quantity control, add to cart |
| Cart | Persisted locally (DataStore); **every change calls `quote_cart`** for the displayed breakdown; shows items, subtotal, **delivery fee as a separate line**, grand total; when below the delivery-charge threshold shows *"Delivery charges apply. Add ₹X more for free delivery"* (only if a free threshold applies) — **never a blocking "minimum order" message** |
| Checkout | Recipient/phone, address + landmark + PIN, payment (COD; online hidden/disabled until gated), note, itemised summary, single-submit with idempotency key generated per attempt-set and persisted so retries reuse it; "Checking your order… please don't submit again" state |
| Confirmation | Order number, status, breakdown |
| Orders | Active/past, status timeline (Realtime or polling), bill view for own order, "repeat order" only after re-quote |
| Profile | Name/phone, addresses, privacy/help links |
| States | Loading, empty, offline (cached catalogue read-only; checkout disabled), server error, out-of-area, store paused, stock error with item names |

No secrets in the APK beyond the Supabase URL + anon key. Release signing config documented; keystore never committed. ProGuard/R8 on release.

---

## 9. Billing and POS spec (summary of the owner-requested update)

- One `bills` system for `ORDER` and `POS` sources. Bill content: store identity, unique bill number, date/time, line items with quantity **and unit** (`250 g`, `1.5 kg`, `3 packs`), unit price **with basis**, line totals, subtotal, delivery fee (online), discount/tax if configured, grand total, payment method/status.
- Online bills render from order snapshots → unchanged by later catalogue edits. Customer can view only their own.
- Authorised access on every bill request; no guessable public URLs.
- POS never trusts client prices; totals recomputed server-side on every quantity change (`pos_quote`), debounced in UI.
- Stock deduction + sale + bill + movement in one transaction; concurrency-safe; insufficient stock aborts everything.
- Void/correction/override/stock adjustment: role-gated, reason required, audited, never destructive.
- Label documents "Bill" until accountant confirms GST/tax-invoice requirements (BLOCKED).

---

## 10. Payments and notifications (gated)

**Online payments — build only the safe skeleton until the gate opens (M11):**
- `online_payment_enabled=false` by default; `place_order` rejects `online` while off. No fake success paths anywhere.
- Gate to enable: provider chosen · merchant onboarding complete · webhook signature verification implemented · `provider_event_id` dedupe + idempotent processing · refund + reconciliation tested **in sandbox** · human review (Gate D).
- Never mark paid from a client redirect; forged webhook → rejected; duplicate → no-op.

**Notifications:** MVP = in-app status (Realtime/polling) + manual phone contact. FCM push, SMS, WhatsApp are P1 and need provider setup/consent. Failures logged, never roll back an order.

---

## 11. Milestones

Sizing: S ≈ small, M ≈ medium, L ≈ large. Each milestone ends with: lint/typecheck/tests run and reported, changed-files summary, risks, decision-log update, demo steps, **human approval**.

### Vertical slice (a real COD order end-to-end)

**M0 — Foundation & decisions (S)** · maps to todo 1 (partial), 7 (partial)
- Repo/monorepo scaffold, Supabase CLI init, `.env.example`, README with exact commands, CI skeleton, lint/format/typecheck configs, `docs/DECISIONS.md` seeded from §2, `docs/THREAT_MODEL.md` stub.
- Verify & record: current Supabase + hosting plan limits/terms (backups, pausing, commercial use), library versions pinned, Android `targetSdk`.
- Add CI guard: grep fails on `minimum_order`, hard-coded `250`/`25000` outside seed/tests.
- **Gate:** `supabase start` + web + Android build run from README on a clean machine.

**M1 — Schema & migrations (M)**
- Migrations for §4.2 (enums, constraints, indexes, triggers, `updated_at`), `order_transitions` table, `bill_counters`, helper functions, synthetic `seed.sql` (products below/at/above threshold, weighted & packed, low-stock, unavailable, two service areas, one rider, test customers).
- pgTAP: constraints (negative stock, packed fractional, T2<T1, unique SKU, unique order number).
- **Gate:** migrations apply from zero and are re-runnable in CI; no `minimum_order` anywhere.

**M2 — Auth, roles, RLS, audit core (M)** · maps to todo 6
- Auth wiring (web SSR + Android), `profiles` trigger on signup (always `customer`), `current_role()`, RLS policies for **all** tables, rider limited view, audit trigger infra + redaction, `docs/THREAT_MODEL.md` v1.
- pgTAP RLS suite across anon/customer A/B/rider A/B/staff/manager (IDOR, escalation, rider isolation, audit append-only).
- **Gate B (human review):** RLS + role-escalation tests all green; reviewer signs off.

**M3 — Catalogue & settings (M)** · maps to todo 2 (part)
- Admin: login, layout, category/product CRUD with Weighted/Packed model, image upload, availability, `save_settings` with T1/T2/F1/F2 validation + live preview, service-area editor, store open/pause. Audit visible in DB.
- **Gate:** manager can configure everything from §2.1 via UI; staff/customer/rider denied (tests).

**M4 — Pricing engine & checkout (L)** · maps to todo 4
- SQL: `eligible_subtotal`, fee function (§4.4), line pricing (§4.5), `quote_cart`, `place_order` (§4.7), idempotency, stock locking, movements, status event.
- pgTAP + integration: all §12 pricing/threshold/tamper/idempotency/concurrency tests (including **below-threshold order accepted + fee charged**, boundary 24999/25000/25001).
- **Gate C:** duplicate-submit and concurrent-last-unit tests pass; negative stock impossible.

**M5 — Android: auth, catalogue, cart (L)** · maps to todo 3 (part)
- Project scaffold, theme, DI, repositories, Home/Search/Category/Detail, weighted/packed quantity inputs, persisted cart with server `quote_cart` breakdown, store-paused/out-of-area/offline states.
- Unit tests for quantity parsing/formatting (g↔kg), ViewModels; UI tests for cart states.

**M6 — Android checkout + admin order queue (L)**
- Android: addresses, checkout with persisted idempotency key, confirmation, order history/status.
- Web: order queue with Realtime alert, detail, `transition_order` with reasons, event timeline, assign rider, reject/cancel with stock restore.
- E2E: customer places COD order → staff accepts → status visible in app.
- **Demo:** full COD order lifecycle without developer help (toward Gate F).

### Operations & completeness

**M7 — Delivery workflow & order corrections (M)**
- Rider provisioning (§5.4), `/rider` area, `assign_order`, `rider_update_status`, delivery_failed handling, COD collected recording.
- `remove_order_item` (unavailable item → recompute, restore stock, refund state, audit).
- Tests: rider isolation (RLS + RPC), inactive rider rejected, forced password change, no credential in logs/audit.

**M8 — Inventory, reports, audit UI (M)**
- Inventory screens, `adjust_stock`, movement history, stock-vs-movement reconciliation check, low-stock list, reports (counts/totals by date/status, COD reconciliation), audit log viewer.

**M9 — Billing & POS (L)** · maps to todo 5
- `issue_bill_for_order` (idempotent), gapless numbering, bill HTML (A4 + 80 mm) + PDF route, customer own-bill access, POS screen, `pos_quote`/`pos_complete_sale`/`void_bill`, override flag (off).
- Tests: §12 billing/POS list incl. historical bill stability, rounding, concurrent sales, insufficient stock atomicity.

**M10 — Notifications (S–M)**
- In-app status polish, outbox table + manual-contact fallback; push/SMS/WhatsApp only if provider configured (otherwise documented stubs behind an interface). Failure-doesn't-break-order test.

**M11 — Online payment gate (M, only when owner chooses a provider)**
- Provider adapter, create-payment flow, signed webhook endpoint, dedupe, refunds, reconciliation report; sandbox tests (forged/duplicate/delayed webhook). Flag stays **off** until Gate D.

**M12 — Hardening & launch readiness (M)**
- Rate limiting, headers/CSP, error-message audit (AC-12), threat-model review, dependency scan, performance checks on a low-end device/slow network, accessibility pass, observability (request IDs, error monitoring, checkout-failure alert), backup + **restore drill**, rollback procedure, runbook, manual fallback doc, production smoke checklist (corrected for C-1), privacy/terms placeholders for professional review.
- **Gates A–F** from the SDLC doc.

### Launch strategy (from the pack)
Private demo (synthetic) → staff-only pilot → limited customer pilot in a small zone with daily reconciliation → expand only after fulfilment/inventory/support are stable → consider extras.

---

## 12. Test plan and traceability

**Corrected threshold tests (replace TC-001/002/AC-01/FR-004 behaviour):**

| ID | Scenario (with T1 = ₹250, F1 = ₹X, T2 = null) | Expected |
|---|---|---|
| TH-1 | Eligible subtotal ₹249.99 | Order **accepted**; fee = F1 |
| TH-2 | Exactly ₹250.00 | Accepted; fee = 0 |
| TH-3 | ₹250.01 / above | Accepted; fee = 0 |
| TH-4 | Manager changes T1/F1; next `quote_cart`/`place_order` | Uses new values with no app update |
| TH-5 | T2 set, F2 null (T2 = ₹500): subtotals 249.99 / 250 / 499.99 / 500 | F1 / F1 / F1 / 0 |
| TH-6 | T2 set, F2 set: same subtotals | F1 / F2 / F2 / 0 |
| TH-7 | Save T2 < T1 | Rejected with field error |
| TH-8 | Client sends tampered fee/total | Ignored; server result used |
| TH-9 | Non-manager changes settings | Denied; audit shows nothing changed |
| TH-10 | Settings change | One audit row with actor, time, before/after |
| TH-11 | Old order after settings change | `fee_breakdown` and totals unchanged |
| TH-12 | Cart/checkout/bill | Fee itemised as its own line everywhere |

**Existing pack tests (kept):** TC-003…TC-020 and AC-02…AC-12 unchanged, mapped below. Add: AC for out-of-area, store paused, notification failure, forged/duplicate webhook (M11).

**Quantity & billing tests:** weighted g/kg equivalence · 1.5 kg = 1500 g totals · packed rejects 0/-1/1.5/"abc"/"1e2" · price-basis display · rounding at fractional paise (e.g. 333 g @ ₹99/kg → 3297 p) · online bill download/print uses snapshots · price change after order leaves bill unchanged · POS search + live recalculation · insufficient stock aborts atomically · concurrent POS + online sale of last unit → one wins · bill-number uniqueness/gaplessness · duplicate POS submit idempotent · void restores stock once · role restrictions · audit on voids/overrides/adjustments.

**Isolation tests:** customer A↔B (orders, addresses, bills), rider A↔B (orders), rider cannot read customer list/products write/settings/payments, inactive rider locked out, staff cannot change prices/settings (default), customer cannot self-promote.

**Traceability (abbrev.)**

| Requirement / test | Milestone |
|---|---|
| FR-001/002/003, TC-004/005 | M3–M5 |
| FR-005/006/007, AC-02/03/04/09/10, TC-003/006/007/008/015/016, TH-* | M4 |
| FR-008/009, AC-06/07, TC-010/011/012 | M2 |
| FR-010/011/013/014/016, AC-08, TC-013/014 | M6–M8 |
| FR-015, v1.3 settings/service-area/accounts | M3, M7 |
| FR-017/018, AC-11, TC-017/018/019 | M10–M11 |
| FR-019 audit | M2, M3, M7–M9 |
| FR-020 reports | M8 |
| Billing/POS FRs & tests | M9 |
| AC-12, TC-020, backups, smoke | M12 |

**Reconciliation checks (run in CI and nightly in staging):** `products.stock_qty = sum(inventory_movements)`; every order total = sum of active item lines + fee − discount + tax; no negative stock; no duplicate idempotency results.

---

## 13. DevOps, configuration, cost

**`.env.example` (names only):**
```
# Web (server-only unless prefixed NEXT_PUBLIC_)
NEXT_PUBLIC_SUPABASE_URL=
NEXT_PUBLIC_SUPABASE_ANON_KEY=
SUPABASE_SERVICE_ROLE_KEY=        # server-only, rider/staff provisioning only
APP_BASE_URL=
STORE_TIMEZONE=Asia/Kolkata
# Payments (leave empty until gate opens)
PAYMENT_PROVIDER=
PAYMENT_KEY_ID=
PAYMENT_KEY_SECRET=               # server-only
PAYMENT_WEBHOOK_SECRET=           # server-only
# Notifications (optional)
FCM_SERVER_CREDENTIALS_PATH=
SMS_PROVIDER_KEY=
```
Android: `local.properties`/CI secrets for `SUPABASE_URL`, `SUPABASE_ANON_KEY`, signing config; keystore outside repo.

**CI (every PR):** format, lint, typecheck, unit tests, pgTAP on fresh DB, Android lint + unit tests, secret scan, migration check, guard greps.
**Release:** reviewed migrations → staging → smoke → backup prod → migrate → deploy → smoke → monitor. Human approval for auth/RLS/payment/prod changes. Agent never uses production secrets or deploys.
**Backups/monitoring:** enable plan-appropriate backups, **restore drill before launch**, error monitoring, checkout failure/webhook failure alerts, request IDs in logs, daily reconciliation checklist, phone fallback (9837803293).
**Cost control:** modular monolith, no paid maps/real-time tracking, no SMS/WhatsApp until justified; monthly cost ceiling is BLOCKED on owner; record verified plan limits and costs in `docs/DECISIONS.md`.

---

## 14. Agent operating rules (copy into `AGENTS.md`)

```
# Balaji Provision Store — Agent Rules

1. Read docs/IMPLEMENTATION_PLAN.md and the pack docs before editing. The plan's later-version rules win over older pack text.
2. Work on ONE milestone at a time. Start each milestone with: current-state summary, assumptions, plan, files you will touch. Wait for approval on M0, M2, M4, M9, M11, M12.
3. NEVER enforce a minimum order. ₹250 is only the seed value of the configurable delivery-charge threshold. Do not hard-code 250/25000 outside seed/tests.
4. All pricing, fees, stock, order, billing and authorization logic is server-side (Postgres functions/RLS). Treat clients as untrusted. Never accept client prices, fees, or totals.
5. Money = integer paise; quantities = integer base units (grams / packs). No floating point for money or weight. Rounding = half up per line.
6. Do not invent business policy. Use the PROVISIONAL defaults, make them configurable, log them in docs/DECISIONS.md. For BLOCKED items, stop and ask.
7. Security: RLS on every table; SECURITY DEFINER functions set search_path=''; service-role key only in web/src/server/supabaseAdmin.ts; never log passwords, OTPs, tokens, payment secrets; never commit secrets; synthetic data only.
8. Online payments stay disabled (online_payment_enabled=false) and no fake success paths until the payment gate (M11) passes.
9. Every change: migrations (never edit applied ones), tests, loading/empty/error/offline states, accessible labels.
10. Run format, lint, typecheck and tests before reporting. Report real results; never claim a test passed unless executed. List remaining risks.
11. Do not deploy, use production credentials, delete production data, or run destructive migrations without explicit human approval.
12. Never leave critical flows as TODOs, stubs that look functional, or hard-coded success.
```

**Per-milestone prompt template**
```
Implement milestone M<n> from docs/IMPLEMENTATION_PLAN.md only.
First: summarise repo state, list assumptions and unresolved decisions that affect M<n>, propose a task list, and wait for my approval.
Then implement, run all checks, and report: files changed, tests executed and results, risks, demo steps, DECISIONS.md updates.
```

---

## 15. Owner decision log (what to ask the owner, in priority order)

| # | Question | Blocks | Default if unanswered |
|---|---|---|---|
| 1 | Real delivery fee; free-delivery amount; do you want one flat fee or two tiers? (D-FEE) | Production | Seed placeholders only |
| 2 | Do discounts/tax count toward the thresholds? (D-ELIG) | Production | Before discounts/tax |
| 3 | Delivery area: which PINs/neighbourhoods? | M3 data, pilot | Two synthetic areas |
| 4 | Opening hours; may orders be placed while closed? | Pilot | Manual pause + hours config |
| 5 | Customer login: phone OTP, email, or other? Budget for SMS? (D-AUTH) | M5/pilot | Email (dev only) |
| 6 | Rider login method; does the rider collect COD; end-of-day reconciliation? | M7 | §2.2 defaults |
| 7 | Cancellation cut-off, refunds, substitutions | M6/M7 | Cancel only in `pending_review`; no substitutions |
| 8 | Weighted products: per kg or per 100 g; smallest step; billed by ordered or measured weight? (D-WEIGHT) | M3/M9 | per kg, 10 g, ordered weight |
| 9 | Bill format; GST/tax invoice fields; business identifiers (accountant) | M9/production | "Bill" document, tax = 0 |
| 10 | Can staff change prices/stock? Who may void bills or override POS prices? | M3/M9 | Staff: stock only; manager: rest |
| 11 | Online payment provider, methods, refunds | M11 | Disabled |
| 12 | Monthly cost ceiling; domain; support owner | M12 | Verify free-tier limits, flag gaps |
| 13 | Android distribution (Play vs APK) | M12 | Internal APK |
| 14 | Privacy notice, terms, retention | Production | Placeholders for review |

---

## 16. Definition of done and release gates

**Per milestone DoD:** reviewed code · format/lint/typecheck green · tests executed and results reported · reversible migration or recovery note · authorization + validation server-side · loading/empty/error/offline states · docs, `.env.example`, DECISIONS.md updated · no secrets or personal data committed · acceptance criteria demonstrated.

**Release gates (all required before real customers):**
- **A** business rules and BLOCKED decisions approved
- **B** RLS/authorization and server-side checkout validation reviewed
- **C** checkout, inventory concurrency, duplicate-submit, status-transition tests pass
- **D** payment sandbox + webhook tests pass (only if online payment enabled)
- **E** backup restore and rollback demonstrated
- **F** owner can accept, fulfil, cancel/reconcile a test order without developer help
- Plus: no critical/high security defects · no duplicate-order/negative-stock defect · below-threshold-order-accepted test green on the **deployed** server · privacy/terms/refund/delivery copy professionally reviewed · phone fallback ready.

*This plan is a planning baseline — not legal, tax, payment-provider, or security certification.*

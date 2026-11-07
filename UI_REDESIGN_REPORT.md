# Orange-Glass Redesign Pass — Implementation Report

_Branch `feature/customer-orange-glass-v3`. Prepared 2026-11-06. Follows the report template in
`Changes/Balaji_Provision_Store_Master_UI_UX_Implementation_Prompt.md` §18._

**Read this first.** The instruction was *"apply ditto UI/UX, use recommended things, will test it
later"*. So this is an implementation report, not a verification report. Every claim below is
either a command that was run, with its output, or a thing that was **not** run and is labelled as
such. A device pass was run afterwards (2026-11-06, Galaxy A34, recorded in `QA_LOG.md`) and it
verified geometry and behaviour - pill, header, font scale, the ADD-to-stepper morph - but **no
screenshot was compared to the reference board**, which is WP10 and remains open. Where the
reference and correctness disagree, the disagreement is listed in §11 rather than hidden.

---

## 1. Branch, base and commits

| | |
|---|---|
| Branch | `feature/customer-orange-glass-v3` |
| Base | `0a9573f` (= `master`, = `origin/master` at the time of branching) |
| Commits | `d4aa305` android UI (16 files), `f8a7a79` db foundation (2 files), `bb09b24` two defects found while writing this report, `3523a9a`/`393a0ce` the report's own docs, `309b218` the device-pass fixes (header badge/copy, service tiles, peach page, web PGRST201), `3cad8c1` liquid glass + token tests, plus the docs commit that follows |
| Merged | **No.** Left as a reviewable branch. |
| Pushed | **No** — the owner chose to keep the branch local for now, so this work exists only on this machine until they say otherwise. |
| Live schema | **Untouched.** `20261106000001` is not applied to the live project, by the owner's choice; it goes over with the release that uses it. |

## 2. What was implemented

Nine work packages of the brief's eleven, at different depths.

- **WP1 tokens/surfaces/typography** — `BalajiDesign` (colour, radius, rhythm, motion timings, the
  85 % slide threshold, the 48 dp floor), `BalajiGlass` (glass surface, peach card, the one orange
  purchasing button), and a semantic type scale in `Type.kt` mapped onto the Material roles.
- **WP2 shared motion controls** — `AnimatedAddQuantityControl` and `SlideToConfirm`.
- **WP3 discovery** — Home's orange brand header with the delivery location and the store's real
  status on glass, plus a capability strip built from flags that exist.
- **WP4 browsing** — product cards: one control row each, no duplicated discount text, semantic
  type; Product Details: savings in money, sticky CTA carrying the line amount.
- **WP5 cart/checkout** — glass-consistent cart rows, "Slide to Checkout" where the gesture
  navigates, "Slide to Place Order" only where it calls `place_order`.
- **WP6 pill navigation** — alpha 0.30 → 0.94, solid orange capsule springing between four real
  destinations, nested-route selection, reduce-motion path.
- **WP7 orders** — tracking timeline recoloured to the truth (green only where the server has a
  timestamp), and the invented "shortly" line removed.
- **WP9 closed-mode surfaces** — Home, Product Details, Cart and Checkout panels that state what
  actually happens.
- **WP8 foundation only** — settings, `delivery_windows`, window computation and slot validation,
  all inert.

**Not attempted:** WP10 pixel-fidelity (captures exist in both themes from the 2026-11-06 device
pass, but nothing was compared to the reference board) and profiling (never run), WP11 final
regression gate.

## 3. Files

New: `ui/theme/BalajiDesign.kt`, `ui/components/BalajiGlass.kt`, `ui/components/AddQuantityControl.kt`,
`ui/components/SlideToConfirm.kt`, `ui/components/ClosedStorePanel.kt`,
`supabase/migrations/20261106000001_delivery_scheduling_foundation.sql`,
`supabase/tests/051_delivery_scheduling.sql`, three JVM test classes.

Modified: `ui/theme/Type.kt`, `ui/components/NavPill.kt`, `ui/components/StorefrontComponents.kt`,
`ui/home/HomeScreen.kt`, `ui/cart/CartScreen.kt`, `ui/checkout/CheckoutScreen.kt`,
`ui/catalog/ProductDetailsScreen.kt`, `ui/orders/OrderTrackingScreen.kt`.

**Font sources and licences: none.** See §11.

## 4. Screenshots

**None taken, none compared.** The brief's §2.1 comparison standard (before/after at identical size,
density, theme and font scale, cropped per screen) requires the handset, and the owner asked to test
later. A compiled APK is not visual validation, and this report does not pretend otherwise.

## 5. Motion clips

**None recorded.** The ADD→stepper morph, the digit slide and the capsule travel are implemented and
unit-tested at the level of the decisions they make (which mode, which direction, which offset,
which fraction commits). Their appearance is unverified.

## 6. Store Closed rules, defaults and compatibility

Audit first (§11.1 of the brief). What the code actually does today:

- `store_open` is one boolean on a singleton `store_settings` row; staff flip it via `set_store_open`.
- `place_order` refuses with `{"error":"STORE_CLOSED"}` at step 3 when it is false.
- `weekly_hours` exists and Home renders it, but **nothing computes a next-open instant**.
- Customer-facing delivery timing is free-text `estimated_window_text`, not a slot.
- No holiday table, no capacity, no scheduled column on `orders`.

Defaults shipped: `accept_orders_when_closed = false`, `scheduling_min_lead_hours = 8` (bounded
1–72), `scheduling_max_days_ahead = 7` (bounded 1–30), `delivery_windows` **empty**, which means
"no window exists", never "any time is fine".

Backward compatibility: the migration adds columns with defaults and new functions; it redefines
nothing and touches no existing signature, so every installed release behaves exactly as before.
Suite 051 asserts that directly — a closed store answers `STORE_CLOSED` with scheduling on *and*
off, called as a signed-in customer.

## 7. Orders and tracking

The event mapping is unchanged; only the honesty of the presentation moved:

| Node state | Before | Now |
|---|---|---|
| completed | green + check | green + check (unchanged) |
| **in progress** | **green + check** | **orange + dot** |
| future | grey | grey (unchanged) |
| `ready_for_dispatch` note | "A delivery partner will pick this up shortly." | "Waiting for a delivery partner to be assigned." |

A check on an unfinished stage reads as a completed one, and "shortly" is a timing claim nothing in
this build can answer. Ratings and Buy Again were not touched; Buy Again is not implemented and is
not claimed anywhere.

## 8. Testing evidence — all of it run

| Command | Result |
|---|---|
| `:app:compileCustomerDebugKotlin` | clean, re-run after the last edit |
| `:app:compileStaffDebugKotlin` | clean, in the same invocation |
| `:app:testCustomerDebugUnitTest` | **263 tests, 0 failures, 0 skipped** (241 before, 22 new), re-executed against `bb09b24` |
| `:app:testStaffDebugUnitTest` | **263 tests, 0 failures** |
| `:app:lintCustomerDebug` | **0 errors**, 70 warnings, 5 hints (2026-11-05 baseline: 74 issues, 0 errors) |
| eleven migrations `20261102000009`→`20261106000001` in order, `ON_ERROR_STOP=1` | applied, no error |
| pgTAP 051 | 27 assertions, `num_failed() = 0` |
| pgTAP 045–050 re-run after the new migration | 0 `not ok`, no error in any of the six |

Pre-existing failures that this work stream did not cause and did not fix: CI has never been green
(`db-tests`, `android` at SDK provisioning, `web-checks` at the 11 `type-check` errors); `npm run
type-check` still reports those 11; BUG-024's catalogue paging is still not implemented.

## 9. Performance evidence

**Not measured.** No frame timings, no scroll profiling, no memory observation, no `SimplePerf` or
`gfxinfo` capture. Two decisions were taken on the *expectation* of cost rather than a measurement:
`Modifier.blur` is absent from every surface, and card elevation stays at 0–3 dp inside lazy grids.
The brief's §14.2 goals (jank ratio, frame-time p95) are therefore unmet as evidence, not as design.

## 10. Security and integrity

- Pricing, stock, fees and order acceptance remain server-side. Nothing in this pass reads a client
  value as authority. The two numbers a card shows are display-only derivations of server prices,
  and the bill still comes from `quote_cart`.
- Money stayed integer paise. Whole-rupee formatting remains confined to the composed rails.
- The new functions follow the pattern this project has been caught by twice: `SECURITY INVOKER` for
  the reads, and an **explicit `REVOKE … FROM PUBLIC, anon`** before any grant, because Supabase
  creates functions executable by anon. `validate_delivery_slot` is not callable by anon;
  `update_store_scheduling` is service-role only and re-checks `current_setting('role')` itself.
- `SECURITY DEFINER` is used once, with `SET search_path = ''` and fully qualified names.
- No secret, token or credential is in any file here. No destructive migration. Nothing deployed.

## 11. Deliberate departures from the reference

Six places where the mockup and the software disagree, and the reason each way.

1. **Fonts: not shipped.** Poppins / Inter / Manrope are named by the brief; no TTF exists in this
   repository or on this machine, and downloading is not permitted here. `BalajiFont` resolves all
   three roles to the platform sans-serif behind one swap point, and `BalajiText` carries the brief's
   weights and sizes so the hierarchy is real while the typefaces are not. **The app does not read
   like the board's typography.** This is the largest single gap in the pass. (BUG-027)
2. **White on orange does not meet contrast — and the owner has now decided that.** Computed: white
   on `#FF6B00` = 2.85:1 (fails AA large *and* normal), white on `#D95400` = 4.03:1 (passes large,
   fails normal). Every orange purchasing control carries a white label at 12–15 sp, and the brief
   asks for exactly that in words. The Home header was arranged around it — deep orange behind the
   wordmark, brighter below the opaque glass card — but a button cannot be fixed locally without
   changing the theme's single `primary`, which the customer, staff and rider apps share. Asked with
   the three options and their ratios, the owner chose **accept and document**, so `primary` is
   unchanged and BUG-028 in `QA_LOG.md` is the record. An accepted exception is not a fixed one: the
   measurement stays true.
3. **Four pill tabs, not five.** The board shows Offers and Account; neither is a route. §10 of the
   brief itself says not to add destinations without real routes.
4. **The stepper got its own row.** A 48 dp stepper and a price do not share a 148 dp card. Shrinking
   the buttons would trade a touch target for a mockup's proportion; growing the card on add makes a
   list jump. So the control sits on a fixed-height row. The board's 32 dp pill and 30 dp buttons were
   measured and left behind on purpose.
5. **Closed means closed.** No panel says "tomorrow" or a time window, because `place_order` refuses
   while the store is shut and no scheduling exists. `ClosedStoreCopyTest` asserts that no
   closed-store sentence contains a day, a time or a duration.
6. **Categories kept its grid.** The rail is offered "if data density and actual screen width justify
   it"; at ~384 dp with roughly ten categories it does not, and rewriting a working 889-line screen
   with no device in hand is not a trade to make blind.

## 12. Outstanding items, in the order I would take them

1. **Device pass on the A34** — the whole of §4 and §5 is empty until this happens. It should cover:
   pill legibility in light theme at 0.94, the ADD morph and digit slide, the slide gesture's
   threshold and its TalkBack alternative, Home header and cards at 1.3× and 2.0× font scale, the
   closed-store panels with the store actually closed, and whether BUG-013's ADD wrap is gone.
2. **Fonts** — licensed TTFs into `res/font/` with a licence notice, then three lines in `BalajiFont`,
   then re-check ₹, Devanagari and long place names at 2.0×.
3. **Contrast (BUG-028)** — decided: accept and document. Revisit only as a single `primary` change
   across all three apps, together with a real accessibility pass.
4. **Scheduling policy** — the owner's answers on windows, capacity, holidays and cut-offs; then the
   dashboard toggle, then the `place_order` branch as its own reviewed change with its own pgTAP
   authorisation suite. Until then `ScheduledOrderingEnabled` stays false and the panels stay honest.
5. **BUG-025** (card price vs details price) and **BUG-024** (catalogue paging) remain open, both
   deliberately.

## 13. Visual fidelity scorecard

The brief forbids an unsupported blanket score, and there is no comparison to score against. So this
table records **what is known**, which is not the same thing.

| Screen | Score against the reference | Basis |
|---|---|---|
| Home | **captured, not compared** | light + dark captures on the A34 (`glass-{light,dark}-01-home-top`, `-02-home-cards`); structure and palette implemented; no board comparison |
| Categories | **captured, not compared** | `glass-{light,dark}-03`; otherwise unchanged by design (§11.6) |
| Product list | **not captured** | control row and discount dedup implemented; browse rails appear inside the Home captures only |
| Product details | **not captured** | savings chip, sticky CTA amount, closed panel implemented |
| Cart | **captured, not compared** | `glass-{light,dark}-04` with 2 lines; slider replaces the checkout button |
| Checkout | **captured, not compared** | `glass-{light,dark}-05`, review step only — the place-order slider was never swiped |
| My Orders | **captured, not compared** | `glass-{light,dark}-06` |
| Tracking | **not captured** | node colours and wording changed |
| Store Closed | **not run** | deliberately different copy (§11.5); needs a live `set_store_open` and the owner's approval |
| Typography | **KNOWN MISS** | correct scale and roles, wrong (default) typefaces |
| Pill navigation | **geometry verified, not compared** | capsule inside the pill on all four tabs, "Categories" whole (device pass, `QA_LOG.md`); 0.94 alpha, sliding capsule, four tabs; no board comparison |
| Liquid glass | **captured both themes, not compared** | ~40 surfaces on `Modifier.liquidGlass`; token behaviour pinned by 6 unit tests (272 total, 0 failures); rim/specular eyeballed on light and dark captures only — no board comparison, no profiling |
| Motion | **unverified** | implemented and unit-tested at the decision level only |
| Accessibility | **one accepted exception, one untested** | white-on-orange is 2.85:1 and the owner chose to accept and document it (BUG-028); the slider's TalkBack action and the stepper's merged semantics are coded but no TalkBack pass was run |
| Performance | **not measured** | §9 |

A blank here means "no evidence", which is the honest answer and not a smaller claim than a number.

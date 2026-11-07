# Balaji Provision Store — Master UI/UX & Scheduled Ordering Implementation Brief

**Artifact:** Implementation prompt for Claude Code, OpenAI Codex, or another autonomous coding agent  
**Target repository:** `https://github.com/GuptaGKar/Balaji`  
**Target platform:** Native Android customer app (Kotlin + Jetpack Compose), with narrowly scoped supporting changes to Supabase and operational staff/manager surfaces only where required  
**Reference:** Attach the **latest approved orange-glass UI mockup**, the **Store Closed Mode mockup**, and the **Orders UI mockup** to the agent's session  
**Quality objective:** Maximum demonstrable fidelity to supplied visual references, fluid interaction, production-grade correctness, and measurable performance. **Do not self-certify a “10/10” outcome without evidence.**

---

## 0. Executive mandate

You are a **principal Android engineer, Jetpack Compose specialist, mobile interaction/motion designer, design-systems architect, accessibility engineer, backend reliability engineer, and test automation lead**. You are responsible for implementing—not merely describing—an end-to-end redesign of Balaji Provision Store's **customer Android app** on a dedicated Git branch.

The approved visual direction is:

- **Orange-forward grocery-commerce UI** with brand orange `#FF6B00`, warm peach, near-white surfaces, and dark accessible text.
- **Restrained glassmorphism**: softly translucent, layered, rounded cards; crisp outlines; low-cost elevation; never sacrifice readability to transparency.
- **Three-family typography**: **Poppins** headings, **Inter** body/navigation, **Manrope** amounts, quantities, CTA text.
- **Floating, pill-shaped bottom navigation** with an animated orange selected indicator; retain real app destinations rather than blindly copying all mockup tabs.
- **Clean SOLID orange CTA buttons**—not metallic/glossy gradients—for ADD and Add to Cart.
- **Animated ADD → horizontal quantity stepper**, with directional sliding number changes when incrementing or decrementing, and graceful collapse to ADD at zero.
- **Slide-to-confirm** at checkout, with the actual order-submitting gesture only on the final validated confirmation step; preserve the existing multi-step flow if required.
- **Home, Categories, Product Listing, Product Details, Cart/Checkout, My Orders, Order Tracking, Order Details, Delivered state, Buy Again**, and accessible loading/empty/error states.
- **Store Closed Mode**: clearly communicate closed status; **optionally** accept scheduled orders for the next *actual available* service window; allow manager to configure availability; no fabricated timing promises.

### Definition of success

Produce native, interactive, testable, accessible screens with the closest achievable **visual and behavioral match** to the images. Keep the live business model intact: authenticated customers, Supabase data, accurate prices, PIN/zone service eligibility, offers/coupons, inventory, weighted and packed products, COD and any genuinely enabled payment options, order lifecycle, proof of delivery, staff/rider workflows and current security boundaries.

**Never replace working behavior with fake UI**, hardcoded product prices, fabricated order statuses, invented ETA, or static screenshot overlays.

**Important discrepancy to resolve explicitly:** The *presentation mockup* displays five tabs (including Offers/Account), but the source was previously observed to expose **Home, Categories, Orders, Basket**. Preserve the functioning four-tab routes by default and implement the pill style without adding fake routes or replacing Basket. Mockup product images, names, rupee prices and times are visual samples only.

---

## 1. Operating procedure and safeguards

### 1.1 First action: inspect the current repository

The repo may have changed since this brief was prepared. Inspect the current default branch and trace actual code references. Do not assume old file names or signatures remain valid.

Previous audit identified these likely paths:

```text
android/app/src/main/java/com/balaji/provision/
  ui/home/HomeScreen.kt
  ui/categories/CategoriesScreen.kt
  ui/catalog/CatalogScreen.kt
  ui/catalog/ProductDetailsScreen.kt
  ui/cart/CartScreen.kt
  ui/checkout/CheckoutScreen.kt
  ui/orders/OrdersScreen.kt
  ui/orders/OrderTrackingScreen.kt
  ui/components/StorefrontComponents.kt
  ui/components/NavPill.kt
  ui/components/MiniCartBar.kt
  ui/components/WeightedQuantityDialog.kt
  ui/theme/Color.kt
  ui/theme/Type.kt
  ui/theme/Theme.kt
  data/repository/CartRepository.kt
  data/repository/StorefrontRepository.kt
  MainActivity.kt

android/app/build.gradle.kts
web/src/components/Layout.tsx
web/src/app/(admin)/Dashboard.tsx
supabase/migrations/
supabase/tests/
.github/workflows/
```

Find actual ViewModels, shared UI, Supabase RPCs and tests by following imports and references. Review `docs/PROJECT_STATUS.md`, `QA_LOG.md`, `RELEASE_CHECKLIST.md`, `SECURITY_AUDIT_REPORT.md`, `SYNC_AUDIT_REPORT.md` and design decisions where present. Treat reports as **claims to cross-check**, not a substitute for test execution.

### 1.2 Branch and scope

- Create and work on `feature/customer-orange-glass-v3` (or a unique equivalent if already taken).
- Record base commit SHA; keep changes grouped in reviewable commits.
- First establish the existing test/build baseline and document known failures.
- UI implementation should primarily affect the **customer** flavor.
- Add backend migrations only for **new scheduled-order business behavior** after understanding current rules, and keep migrations forward-only.
- Make minimal compatible staff/manager changes necessary to configure, display and process scheduled orders.
- **Do not merge, deploy, publish releases, mutate live data, rotate signing keys, change production credentials, or perform real customer orders without explicit approval.**

### 1.3 Preserve all invariants

- Supabase/PostgreSQL remains the authority for money, inventory, discounts, delivery fees, order placement, permissions and scheduling.
- All monetary amounts remain **integer paise**; display rupees via current formatting helpers.
- Weighted quantities remain integer base units (e.g. grams); packed products follow existing step/min/max rules.
- Preserve RLS, role restrictions, storage authorization, idempotency and transactional stock changes.
- Preserve real customer sign-in, location service area selection, coupon rules, product variants, favourites, order reviews, order photos, and staff/rider permissions.
- Preserve current COD and only display online payment if actually enabled and supported.
- No fabricated same-day delivery claims or real-time rider location tracking without operational support.
- Leave the default staff-flavor appearance unchanged unless a shared component requires explicit adaptation.

### 1.4 Plan and execute, don't just propose

Produce a concise pre-change implementation map, then implement in vertical slices, checking each slice. If no physical device or screenshots are available, implement the best testable changes and **state what is unverified**; do not imply physical QA took place. No broad rewrites solely to simplify the redesign.

---

## 2. Reference-image interpretation and fidelity protocol

The supplied images are *visual targets*, not literal specifications of product data, routes or API responses. Extract a design spec from their visible geometry:

1. Top-level page proportions and safe area.
2. Header hierarchy and white-on-orange branding.
3. Cards and spacing rhythm.
4. Product photography framing and sizing.
5. Category tile arrangement and selected states.
6. Font styles and hierarchy.
7. Solid-orange CTA geometry.
8. Pill bottom nav, inset and selected capsule.
9. ADD-to-stepper animation states.
10. Swipe control geometry, thumb and text.
11. Closed-store alerts in Home, Product Details and Cart.
12. Orders cards, tracking timeline, delivery confirmation, ratings and Buy Again.

### 2.1 Comparison standard

Take **before** screenshots and **after** screenshots at identical device size, density, theme, font scale and mock data when possible. Crop and align the corresponding phone screen from the reference board (do not compare the full poster to a single phone capture). Score:

| Dimension | Weight |
|---|---:|
| Layout, spacing, proportion | 25% |
| Typography | 15% |
| Color, surfaces, shadows | 15% |
| Components, icons, images | 15% |
| Animations & interactions | 15% |
| Accessibility and responsive states | 10% |
| Functional visual accuracy (real states/data) | 5% |

Document mismatches and resolve the highest-impact ones. **A compiled APK is not visual validation.** A static screenshot is not animation validation. Do not invent quantitative similarity percentages.

### 2.2 Important reference compromises

- Prior mockups contain **sample 30–60 minute ETAs, overnight 7–11 AM windows, product prices and discounts**. Substitute actual configured values; omit unverifiable promises.
- The large Orders mockup shows one order ID in multiple sample states; use real unique order data and chronological statuses.
- In the reference's Cart screen, “Slide to Place Order” appears at the cart bottom. Keep this exact label only where the gesture **actually submits an order**. On the Cart screen, use “Slide to Checkout” if mandatory address/payment/review steps follow.
- Reproduce visual treatment, **not** copyrighted third-party logos/assets or proprietary brand illustrations as new default Balaji content. Show actual catalog assets, or licensed neutral placeholders.

---

## 3. Design system: colors, typography, geometry

### 3.1 Brand tokens

```text
brand.primary       #FF6B00
brand.primaryDeep   #D95400
brand.highlight     #FFB547
brand.softPeach     #FFF4EC
brand.orangeTint    #FFE9D9
surface.base        #FFFFFF
surface.secondary   #F7F7F5
text.primary        #252525
text.secondary      #757575
success             #218838
```

Keep existing semantic error/warning/info tokens and accessible dark-mode alternates. Use orange for branded controls and selection; green for confirmed positive status and *actual* savings; red for closed/error status as appropriate. Do not shift the whole UI to green/indigo from the web's older admin styling.

Create a tokenized `BalajiDesignSystem` rather than magic values repeated across screens.

### 3.2 Glass surfaces

Implement a reusable `BalajiGlassSurface` using transparent/near-opaque warm backgrounds, thin borders, soft shadow and natural shapes. Initial values to tune:

- Glass white alpha: **0.90–0.96** where text is overlaid.
- Border: 1 dp, subtle white/surface or theme outline.
- Corner radius: 14–18 dp cards, 18–20 dp hero blocks.
- Elevation: 2–6 dp (avoid tall blur/shadow costs for every product card).
- Standard horizontal page padding: 16 dp.
- Major vertical section rhythm: 20–24 dp; internal gaps: 6–12 dp.

Use *restrained* peach/orange backdrop gradients only in headers and hero areas. Avoid repeated blur modifiers/shaders in large lazy grids; prefer simple translucent fills with contrast-tested borders.

### 3.3 Fonts — required refresh

Font families:

- **Poppins Semibold/Bold**: app branding, screen titles, section headings, promotional headlines, optionally product titles.
- **Inter Regular/Medium/Semibold**: body, search, delivery addresses, navigation labels, item metadata and accessibility text.
- **Manrope Medium/Semibold/Bold/ExtraBold**: currency, prices, totals, discounts, quantity numerals and CTA labels.

Retrieve officially licensed resources and ship only allowed font weights/files. Do **not** include unlicensed fonts or expose user-private font files. Store app fonts in `android/app/src/main/res/font/`, with correct lowercase Android names and a license notice. Include fallback for Hindi, ₹ glyph, font scaling and absent weights.

Starting semantic scale (adjust against screenshots and accessible font size):

| Semantic role | Family/weight | Nominal size |
|---|---|---:|
| Brand | Poppins 700 | 23 sp |
| Page title | Poppins 700 | 22 sp |
| Section title | Poppins 600 | 17–18 sp |
| Product name | Poppins 600 | 13–15 sp |
| Main price | Manrope 700 | 16–18 sp |
| Total payable | Manrope 800 | 20–22 sp |
| Body/address | Inter 400/500 | 13–15 sp |
| Small metadata | Inter 400 | 12 sp |
| Navigation | Inter 500 | 11–12 sp |
| Orange CTA | Manrope 700 | 14–16 sp |

Use semantic Material3 typography overrides, not random direct `fontSize` in every component. Test longer local place names, multiline Hindi, 1.3–2.0x system font, accessibility scaling and ellipsis.

### 3.4 Interaction geometry

- Minimum **48 dp touch targets** where possible; never reduce hitboxes to preserve mockup appearance.
- ADD pill height approximately 36–44 dp, with expanded hitbox if visually smaller.
- Large primary CTA 52–56 dp.
- Glass bottom pill height ~64–72 dp plus system insets; active orange capsule is comfortably padded.
- Slide thumb ~48–52 dp; track ~56–60 dp; rounded end caps.
- Pack-size chips 44–52 dp high.
- Maintain stable layout during numeric changes.

---

## 4. Screen A: Home

**Likely:** `ui/home/HomeScreen.kt`

Implement the reference hierarchy:

1. Orange branded header, localized store identity, optional notification entry.
2. Delivery address selector with clear selected location and compact store status badge.
3. Large, visually accessible rounded search field, retaining the existing search destination.
4. Store Closed informational hero **only when actually closed**; otherwise configured promotional carousel.
5. Real promotional/benefit area without hardcoded speed or savings claims.
6. Shop by Category mini-grid (real categories with active products).
7. Buy Again, **only if the existing order-history/product APIs support it**; build read-only projection if feasible without weakening RLS.
8. Manager-composed sections from existing storefront RPCs; preserve fallback if none.
9. Mini cart overlay when items exist, stacked above bottom pill.
10. Floating nav capsule.

**Home visuals:** warm orange top area, clean hero, small spacing, soft glass service tiles, visually balanced product rails and good contrast. Do not overwhelm the first screen with redundant promotional ribbons.

**Interaction:** category taps, search, banner destinations, section read, cart increment/decrement, no janky recompositions or repeated server calls tied to animations. Preserve scroll position and dynamic refresh behavior.

---

## 5. Screen B: Categories

**Likely:** `ui/categories/CategoriesScreen.kt`

Adopt visual concept of a narrow left category rail plus a right image-rich grid **if data density and actual screen width justify it**. Keep responsive fallback to a normal grid on narrow displays and existing manager-composed section semantics.

- Poppins page heading, search entry and optional microphone only where truly supported.
- Visible active orange chip/rail selection.
- Neutral glass category tiles, consistent image frames and labels.
- Actual category icons/images via existing storage; fallback gracefully to accessible icons.
- Fast category switching, stable item keys and remembered scroll positions.
- Preserve all category navigation, sorting, custom grouping and last-resort “More categories” access.
- Don't strand categories when manager composes a partial home/categories layout.

---

## 6. Screen C: Product list and reusable product card

**Likely:** `ui/catalog/CatalogScreen.kt`, `ui/components/StorefrontComponents.kt`

### Product list

- Back, category title, search and basket access.
- Compact horizontal category chips.
- Clear Sort and Filter controls with selected state.
- Consistent product rows or grid according to existing surface; do not force a full rewrite that ruins performance.
- Keep product list loading, empty search, no connection and out-of-stock states.

### Product card required information

- Real item photo, name (2 lines max), pack label, price, MRP if applicable, **one** discount badge, favorite where supported.
- Small **solid orange** `ADD` button, or expanded quantity stepper.
- Weighted items display “Choose weight” (or concise localized equivalent) and launch weighted picker.
- Clear unavailable/low stock indicator that doesn't cause card height jumps.
- Align CTA consistently in lists and grids.

Do not duplicate discount text in both image badge and price area. `ContentScale.Fit` on packaged products. Provide fallback when product image doesn't load.

---

## 7. Signature motion: ADD → quantity control

**Build one reusable composable** and integrate in Home product rails, Product List, Product Details and Cart wherever semantically appropriate.

Suggested API (adapt for actual domain types):

```kotlin
@Composable
fun AnimatedAddQuantityControl(
    quantityBase: Long,
    stepQty: Long,
    canIncrease: Boolean,
    enabled: Boolean,
    onAdd: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier
)
```

### Defined states

1. `EMPTY`: `[ ADD ]`, orange fill, white Manrope Bold label.
2. `ADDING`: tactile press + brief waiting/optimistic state **only if existing repository supports safe reconciliation**.
3. `PRESENT`: `[ −   1   + ]` (quantity reflects real base units; for weighted products use supported formatter/picker).
4. `INCREMENT`: old number slides **left** and new number enters **from right**.
5. `DECREMENT`: old number slides **right** and new number enters **from left**.
6. `REMOVE_LAST`: control collapses back to ADD when final quantity is zero.
7. `UNAVAILABLE/UPDATING/ERROR`: accessible disabled, pending and recovery states; no false success.

### Motion tokens

- Press response 80–130 ms.
- ADD-to-stepper morph 220–280 ms.
- Quantity slide 140–200 ms.
- Stepper-to-ADD 200–260 ms.
- Material easing/spring to settle without exaggerated bounce.
- No animation restarts on unrelated lazy-list recomposition; stable key per product/variant.
- Avoid infinite shimmer/glow and `LaunchedEffect` loops; no animation should trigger network calls itself.

### Functional constraints

- Do not bypass `CartRepository`, quote refresh, quantity validations, stock availability or user session.
- Fast repeated plus/minus taps must produce the correct final quantity, not lost writes or duplicates.
- Coalesce/debounce only when that preserves all intended increments.
- If a cart action fails, revert or resolve to authoritative state and show a concise error.
- If stock falls below requested quantity, show availability guidance.
- Multiple variants must not share the wrong quantity animation state.
- Screen reader announces item name, current quantity and controls.
- Respect system animation-scale setting and offer semantic alternatives.

### Tests

ADD → 1; 1 → 2; 2 → 1; 1 → 0; rapid 5 taps; error rollback; maximum stock; weighted step; variant switch; screen recreation; scrolling off/on screen; TalkBack actions.

---

## 8. Screen D: Product Details

**Likely:** `ui/catalog/ProductDetailsScreen.kt`

- Clean top bar: Back, Favorite, Share, Cart.
- Big centered real product image on white/peach hero; small pager dots **only when multiple images exist**.
- Poppins product name and Inter pack metadata.
- Manrope bold current price, crossed MRP and one green/orange savings indicator based on actual math.
- Availability state, genuine COD/store delivery copy.
- Pack-size selector as rounded chips with selected light-orange fill/orange stroke and clear unavailable state.
- Quantity control that follows packed/weighted domain rules.
- Concise product description and attributes; prevent redundant copy blocks.
- Sticky simple-orange `Add to Cart · ₹[computed line amount]` button; full-width, 52–56 dp, white text, accessible loading and error states.
- When Store Closed & scheduled ordering enabled: insert a compact peach information panel stating the next *server-confirmed* delivery window; keep Add to Cart available for eligible items.
- When store ordering is paused: explain why checkout is unavailable, while preserving browsing.

**Never hardcode mockup products or amounts.** Existing variant selection, favourites and share actions must still work.

---

## 9. Screen E: Cart and checkout

**Likely:** `ui/cart/CartScreen.kt`, `ui/checkout/CheckoutScreen.kt`

### Cart layout

- “Your Cart” Poppins title and appropriate Clear action with confirmation.
- Compact glass item rows with photo, name, pack size, unit price and reusable quantity control.
- Coupon and free-gift interactions as existing backend supports.
- Server-derived free-delivery progress and fees; don't misrepresent threshold logic.
- Clearly separated subtotal, discounts, delivery charge, tax/other applicable fees and **total payable**.
- Distinguish **COD** and genuinely active alternate payment options.
- Closed-mode scheduled delivery card directly above final navigation/confirmation action.
- Preserve unusable item / stock changed errors and explicit retry.

### Slider: where it goes

- Cart → Checkout: label `Slide to Checkout` if address/payment/review still needs completion.
- Final Review → order RPC: label `Slide to Place Order`.
- If the current checkout can securely collapse into one page without breaking existing validation, document that as a separate measured UX change; don't silently skip mandatory steps.

### Slide control design and behavior

- Orange capsule track with white circular thumb and orange `»` icon.
- Track height 56–60 dp; thumb about 48–52 dp.
- Thumb tracks horizontal drag with bounded position; text fades subtly with drag progress.
- Completion threshold around 85% of available travel, **also require valid release/confirmation gesture**, not a tap.
- Early release springs back.
- Completion haptic once; progress locks while navigating/submitting.
- Disable when cart invalid, delivery not serviceable, quote unavailable, server disallows orders or processing underway.
- Prevent multiple order RPC calls; preserve existing idempotency token and recovery for uncertain timeout.
- TalkBack/Switch Access alternative: explicit semantic “Confirm order” action or equivalent accessible fallback.
- Visual success only after verified server response; no premature “Order Placed”.

---

## 10. Floating pill bottom navigation

**Likely:** `ui/components/NavPill.kt`

The previous code used a **0.30f surface opacity**, which was documented as hard to read in light mode. Fix contrast explicitly.

- Capsule with warm-white **0.90–0.96 alpha** where necessary; optional clipped backdrop blur only if fast and supported.
- Rounded max-radius shape; subtle 1 dp border; soft shadow.
- Centered, safe above Android gesture/navigation bar; width adapts to phones/tablets with sensible max width.
- Inactive tabs use neutral icons and readable Inter labels.
- Active item is **a solid orange rounded capsule** carrying the icon and label; animate this indicator smoothly between destination positions (roughly 220–300 ms).
- Retain **Home, Categories, Orders, Basket** as functional destinations by default; do not add Offers/Account from the mockup without actual routes and approval.
- Preserve nested-route active selection (e.g. category listing highlights Categories).
- Navigation should be hidden where existing app intentionally hides it (Product Detail, Checkout, keyboard-visible states as currently designed).
- Cart summary and last lazy-list row must not be obscured; calculate content insets from actual overlays.
- Touch area ≥48 dp, label supports accessibility font scaling, focus order and navigation semantics.

---

## 11. NEW: Store Closed Mode and optional next available delivery scheduling

**Reference:** Attached Store Closed Mode mockup for Home, Product Details and Cart.

### 11.1 Audit business behavior before coding

Inspect actual `store_open` setting, weekly operating hours, holiday/closure configuration, timezone, service-area constraints, delivery windows, quote/cart/place-order RPCs, staff processing rules and existing store-status guards. The previous code may reject orders when closed. **Do not simply remove this guard.** Write down how state currently flows through customer, database and staff.

### 11.2 Explicit authoritative states

Define a server-authoritative model distinguishing:

| State | Browsing | Add to cart | Submit order |
|---|---|---|---|
| Open & accepting | Yes | Yes | Yes, existing validation |
| Closed & future scheduling permitted | Yes | Yes | Yes, for an actual valid future delivery window |
| Closed & scheduling off | Yes | Prefer yes if useful | No |
| Temporarily paused / emergency | Yes | Optional | No |
| No available delivery slot | Yes | Yes | No until slot chosen/available |
| Address outside coverage | Yes | Yes | No for that address |

The manager must be able to toggle `accept_orders_when_closed` (exact field naming per existing conventions). Distinguish physical store openness from order-acceptance permission. Also permit exceptional closures and future delivery slots subject to configured capacity.

### 11.3 Delivery date/time truth

- Compute next valid window in the **store's configured timezone** (`Asia/Kolkata` if the store actually uses it), not blindly `now + 24h` and not phone-local time.
- Holiday/weekly closure/calendar and delivery area must be respected.
- “Tomorrow” only if the next truly eligible delivery date is tomorrow **in store-local time**.
- No fixed “7:00 AM – 11:00 AM” or “30–60 min” promise unless configured.
- Store the actual scheduled window with the order; distinguish `placed_at` from scheduled fulfillment date/time.
- If no supported delivery scheduling system exists, implement minimal secure scheduling deliberately; do not invent unvalidated time slots.

### 11.4 Customer messaging

**Home closed badge:** `Store Closed` in soft red/peach, with a clear icon. Open mode may show green `Store Open`.

**Home hero, only when scheduling is available:**

> Store is Closed  
> You can still place your order now.  
> Delivery: Tomorrow, [real configured window].

If tomorrow is not eligible, substitute the next actual date.

**Product Detail:** small contextual glass card explaining the next eligible delivery window, while allowing Add to Cart.

**Cart:** clear closed-store panel, delivery date/window, and slider label according to checkout stage. The cost quote remains accurate; the delivery window must be revalidated at submission.

**Order success:** “Order placed successfully” and “Scheduled for [real date/window]”; link to the created order; do not show dispatch or rider ETA before it exists.

### 11.5 Backend contract and migration safeguards

If new fields/RPCs are necessary, design a forward-only migration, pgTAP authorization tests and migration compatibility strategy. Prefer order-specific scheduling metadata separate from existing order status machine when possible.

At placement, verify transactionally:

- User active and permitted.
- Store is accepting immediate or scheduled orders.
- Requested/assigned slot is valid, still available and serviceable.
- Address and PIN/geo coverage.
- Product availability, min/max steps, stock row locks and idempotency.
- Server-calculated line prices, fees, taxes, discounts and coupon applicability.
- Capacity, if configured.
- Clear errors if store status or slot changes during checkout.

Avoid bypassing existing `place_order` safeguards. Audit other clients for backwards compatibility; version or extend RPCs without silently breaking existing Android releases. Default scheduling should remain disabled until owner-approved settings/config are established.

### 11.6 Staff and manager UX

- Manager toggle for scheduled orders while store closed.
- Manager-configured valid delivery windows, holidays and capacity.
- Orders Queue identifies `Scheduled for tomorrow` / explicit future date without treating them as immediate urgent dispatch.
- Staff/rider follow the existing permitted statuses, proof-of-dispatch/delivery requirements, COD accounting and cancellation/stock-restoration logic.
- Do not mark “preparing”, “on the way” or “delivered” until corresponding server event exists.

### 11.7 Edge-case matrix

Before midnight; after midnight; holiday; next-day full; area unsupported; store reopens; manager pauses during checkout; user resumes old cart; product out of stock; coupon expiry; network timeout with ambiguous order outcome; duplicate swipe; two customers contest last stock; cancellation before preparation; staff receives scheduled order; existing app version hits extended RPC; no configured slot.

---

## 12. NEW: Orders / tracking / delivered UI redesign

**Reference:** Attached three-screen Orders mockup (Orders list, live-status detail, Delivered order detail).

**Likely:** `ui/orders/OrdersScreen.kt`, `ui/orders/OrderTrackingScreen.kt`, existing order-repository/ViewModel classes. Inspect actual API and routes.

### 12.1 My Orders list

- Orange brand header or compatible existing app shell.
- Title `My Orders` in Poppins Bold.
- Rounded glass filter chips: `All`, `Ongoing`, `Upcoming`, `Completed` (and handling for canceled/failed). Map filters to **actual order statuses and scheduling metadata**; no fabricated classifications.
- Each order card displays actual unique order number, current status chip, placed date, item count, server total and contextual text.
- Upcoming scheduled order example: `Scheduled for tomorrow · [actual window]`.
- Ongoing example: `Out for delivery` with verified status; ETA **only when configured/available**, not invented.
- Completed: delivered timestamp; canceled: reason if available.
- Cards are fully tappable with correct accessible labels and logical chronological ordering.
- Maintain proper empty states for each filter, network error, pagination and refresh.
- Keep functional four-item floating pill and `Orders` selected capsule.

### 12.2 Order Details and tracking

- Poppins title, order number, placement time and status chip.
- Large but accurate headline panel: `Preparing Your Order`, `Scheduled for [date]`, `Out for Delivery`, etc.
- Vertical chronological timeline based on actual status events. Suggested milestones: `Order Placed`, `Accepted/Preparing`, `Ready for Dispatch`, `Out for Delivery`, `Delivered`, plus scheduled milestone where applicable.
- Completed: green check; current: orange highlight; future: neutral; canceled/rejected/failed use correct failure state.
- Never invent specific timestamps or a “25–35 min” ETA.
- Contact Store via actual configured phone; optional Chat button **only if real contact/chat destination exists** (otherwise omit).
- Real order item lines, quantity, totals, payment status, delivery address and associated instructions as authorized.
- Preserve realtime subscription/refresh logic; show last-updated state and manual retry when disconnected.
- Avoid claiming “live tracking” when no real-time rider location feed exists.

### 12.3 Delivered order

- Order Delivered confirmation card with actual delivered timestamp.
- Order rating using existing review API (`order_reviews` or current equivalent), with editability and one-review constraints per backend policy.
- Product thumbnails from real purchased items; total breakdown from saved historical order snapshot, **not current catalog prices**.
- `Buy Again` prominently displayed if supported: add **currently available** variants with their *current* prices and stock limits to a cart; show substitution/unavailable warnings instead of silently discarding items.
- Do not immediately place an order from Buy Again. Navigate to reviewable cart.
- Help/Contact Store call action with actual contact information; only show refund/return features where supported by store policy.

### 12.4 Scheduled order states

Upcoming future-delivery orders should not show active driver route or same-day ETA. The event timeline starts with `Placed` and `Scheduled`; future stages are pending until actual processing events.

### 12.5 Tests

Filter classification, pagination, realtime change, canceled and failed states, scheduled vs ongoing separation, historical prices, ratings permissions, Buy Again availability/missing variants, rapid filter switching, offline refresh and accessibility semantics.

---

## 13. Loading, empty, network and accessibility quality

For **every** redesigned screen, support:

- Initial loading skeleton with stable layout.
- Empty categories/no products/no orders/empty cart.
- Network timeout and retry.
- Offline with last-good cached data distinguished from current server state.
- Inactive/out-of-stock/low-stock products.
- Backend amount or stock changes during checkout.
- Session expiry and user reauthentication.
- Invalid service area and unavailable delivery schedule.
- Large font, TalkBack, switch/keyboard navigation and reduced-motion users.
- Light and dark mode, including translucent surface contrast.

Motion is decorative only; functionality and accessibility must work when animation is turned off.

---

## 14. Performance and fluency engineering

### 14.1 Compose requirements

- Stable lazy-list keys, content types where beneficial, and stable reusable components.
- Avoid expensive per-frame allocations and unnecessary StateFlow collectors.
- Use `remember`, `derivedStateOf` and immutable state judiciously; don't over-memoize blindly.
- Use efficient product thumbnail sizing and Coil caching; no huge images in 100dp cards.
- Avoid nested unbounded scrollables, heavy simultaneous animation in many visible cells, or blur shaders in grids.
- Don't attach long-running animations to values unaffected by user interaction.
- Ensure cart quote/network state changes do not recompose whole Home unnecessarily.

### 14.2 Instrumentation and goals

On a representative mid-range device (ideally Samsung Galaxy A34 if available):

- Record cold start, warm start, scroll frame timing/jank, memory/GC and interaction latency.
- Profile Home, Categories, product list and Cart before and after.
- During rapid ADD taps and navigation, verify no stuck button states, input lag, duplicate business events or flicker.
- Avoid unsupported absolute claims such as “120 FPS guaranteed”.
- Record measured results and their test conditions.

---

## 15. Detailed verification matrix

### 15.1 Visual captures required

At consistent screen dimensions/density/theme:

1. Home, store open.
2. Home, store closed and future ordering enabled.
3. Home, store paused/no delivery slot.
4. Categories default and selected category.
5. Product List default, scrolled, filter state.
6. Product card ADD, adding, quantity 1, quantity 2, removing to zero, out of stock, weighted.
7. Product Details default, different selected pack, quantity changed, closed state.
8. Cart normal, coupon applied, server quote unavailable, closed/scheduled state.
9. Checkout review slider: initial, dragging, below threshold, submitting, confirmed, error recovery.
10. My Orders: All, Ongoing, Upcoming, Completed, empty.
11. Order Tracking: scheduled, preparing, on the way, canceled/failed.
12. Delivered detail, rating and Buy Again.
13. Floating navigation each selected destination, overlay clearance.
14. Light, dark, large font, tablets and low network quality.

### 15.2 Video/frame-capture required

- ADD → stepper morph.
- Plus numeric slide and minus reverse slide.
- Stepper collapse to ADD.
- Floating active-pill interpolation between tabs.
- Slide-to-checkout and Slide-to-place-order early release and completed swipe.
- Error/retry and reduced-motion variation.

### 15.3 Unit/UI/backend tests

- Existing customer unit tests and lint/build.
- Staff flavor build/tests if shared code changed.
- Compose UI tests for tap targets and state semantics.
- Server tests for checkout idempotency, stock concurrency, pricing, coupons, delivery pricing and cancellations.
- pgTAP for scheduling authorization/availability and RLS.
- Day-boundary/holiday/timezone and manager-toggle tests.
- Backward compatibility for old clients.
- No production-data-mutating tests without explicit authorization.

### 15.4 Baseline-specific caution

A prior connected-repo review identified failing CI on a November 5, 2026 commit: web type-check issues; DB test suites `040`/`042`; Android SDK setup job failing before Gradle. **Recheck current CI** before work and differentiate pre-existing failures from regressions. Do not declare all tests pass when CI or test environments remain broken.

### 15.5 Example build commands (verify actual flavor/task names first)

```bash
cd android
./gradlew --no-daemon \
  :app:testCustomerDebugUnitTest \
  :app:lintCustomerDebug \
  :app:assembleCustomerDebug
```

Run staff checks if relevant; run web lint/type-check/build if manager settings or order queue changed; run isolated Supabase tests for migrations/RPC changes. Do not alter signing keys to get local builds working.

---

## 16. Work packages and commit strategy

Follow this order, providing a compact progress/verification update after each major slice:

**WP0 — Audit/baseline:** identify code, state flow, reference differences, test environment and pre-existing failures.

**WP1 — Design tokens/fonts:** Poppins/Inter/Manrope, semantically correct Material typography, orange/peach tokens, glass surfaces, icon and spacing consistency.

**WP2 — Shared motion controls:** `AnimatedAddQuantityControl`, outlined quantity stepper, simple solid-orange buttons and `SlideToConfirm` component + focused tests.

**WP3 — Customer discovery:** Home and Categories, preserving composed storefront and search.

**WP4 — Product browsing:** Product lists/cards, weighted items, packs and Product Details with sticky CTA.

**WP5 — Cart/checkout:** glass cart rows, bill hierarchy, coupon state and slider integration at the correct stage.

**WP6 — Pill navigation:** contrast, selected indicator animation, bottom clearance and nested-route state.

**WP7 — Orders:** My Orders list/filter, scheduled and ongoing, timeline, delivered summary, rating and Buy Again.

**WP8 — Closed mode foundation:** manager settings, database schedule model, availability RPC, forward migration/tests (if essential), safe compatibility and feature flag.

**WP9 — Closed-mode surfaces:** Home, Details, Cart, Orders, confirmation; staff/manager scheduling labels.

**WP10 — Pixel-fidelity/performance:** real screenshots, recordings, iterative alignment and profiling.

**WP11 — Final regression gate:** comprehensive tests, source review, code cleanup, documented unresolved limitations.

Do not combine UI-only and significant backend behavioral changes in one unreviewable commit. Flag any product/business decision that truly requires owner input (service hours, capacity, holiday policy, cancellation deadlines, etc.) and implement safe disabled defaults until configured rather than inventing rules.

---

## 17. Acceptance criteria — enforce as a gate

### Visual

- [ ] Reference-aligned Home, Categories, Product List, Product Details, Cart, Orders and tracking screens.
- [ ] Poppins headings, Inter body, Manrope prices consistently applied.
- [ ] Solid-orange buttons and restrained glass surfaces with readable text.
- [ ] Floating pill nav with smooth selected orange capsule and valid destinations.
- [ ] Category/product assets displayed with consistent framing.
- [ ] No clipped text, overlapping bars, inaccessible final rows, or broken large-font layouts.
- [ ] Actual device screenshots compared to corresponding reference screens and differences logged.

### Motion

- [ ] ADD→stepper and zero→ADD animate smoothly and correctly.
- [ ] Plus/minus numerals slide directionally without quantity desynchronization.
- [ ] Slider follows drag, resets on early release and completes at correct threshold.
- [ ] Confirmation cannot double-submit an order.
- [ ] Animation respects reduced-motion and is accessible without gesture.
- [ ] Navigation indicator animation is smooth, avoids jank and respects back stack.

### Functional

- [ ] Customer Auth, product data, search, variants, weighted/packed handling, favorites and coupons remain intact.
- [ ] Server-authoritative price, stock, fee, tax and checkout logic remain intact.
- [ ] Store open/closed/scheduled/paused state is server-derived and enforced.
- [ ] Closed-store orders are possible only if explicitly enabled and genuinely schedulable.
- [ ] Order tracking reflects actual server events and real timestamps.
- [ ] Delivered order reviews and Buy Again preserve permissions and safe current pricing.
- [ ] Staff/rider flows remain usable; scheduled work is labeled correctly.
- [ ] Existing Android client compatibility documented and preserved where required.

### Evidence

- [ ] Changed file list and concise rationale per work package.
- [ ] Unit/UI/SQL tests and actual command results.
- [ ] Build artifacts tested locally; no release or deployment without owner approval.
- [ ] Screenshot comparison and animation capture provided where available.
- [ ] Performance measurements or clear “not measured” disclosure.
- [ ] Explicit outstanding defects and severity.
- [ ] Code submitted as reviewable branch/PR, not silently merged.

---

## 18. Final report template

Deliver a final Markdown report with these sections:

1. **Branch, base SHA and commit IDs.**
2. **Summary of implemented screens and features.**
3. **Files changed, new components, font sources/licenses.**
4. **Screenshots** with reference-versus-actual differences.
5. **Motion clips/frame series** for ADD, quantity, navigation and checkout.
6. **Store Closed business rules**, configured defaults, migrations, backward compatibility.
7. **Orders/Tracking actual event mapping**, ratings, Buy Again behavior.
8. **Testing evidence**: commands, tests passed/failed, pre-existing issues.
9. **Performance evidence**: device, Android version, frame measurements, memory/scroll observations.
10. **Security/integrity review** and any approval gates.
11. **Unfinished items and proposed follow-up steps.**
12. **Visual fidelity scorecard** based on actual comparisons, never an unsupported blanket 10/10.

**Per-screen scorecard:** Home / Categories / Product List / Product Details / Cart / Checkout / My Orders / Tracking / Delivered / Store Closed / Typography / Pill Navigation / Motion / Accessibility / Performance.

---

## 19. Agent execution instructions

**Now implement.** Begin by inspecting the current repository and the three attached visual references. Produce a short action plan only to map files and risks; then do the work rather than returning a long conceptual answer. Keep commits incremental and verifiable. Use the existing native framework. If specific reference behavior conflicts with correctness, preserve correctness and document the deliberate design adaptation.

The finished result should feel like a **premium, trustworthy, locally operated grocery app** with **Poppins/Inter/Manrope typography**, **simple orange purchasing controls**, **fluid quantity animations**, **a readable floating pill navigation**, **a safe slide-to-order experience**, **accurate scheduled ordering while closed**, and **an excellent Orders/tracking experience**.

**Never claim a perfect visual match, safe backend deployment, passing tests, or verified smoothness without evidence. Never deploy, merge or publish without approval.**

# Product Podium Implementation Plan

**Goal:** Separate reusable theme-aware podiums from approved product-only image files.
**Architecture:** Compose Canvas on Android details; CSS podium in catalogue previews. Browser-local edge-connected white background removal and transparent square normalization, explicit per-product approval, existing Supabase storage/RLS and catalogue update.
**Spec:** User's Premium Orange Product Podium feature extension, 2026-11-13.

Scope is one catalogue presentation extension. Existing business rules and catalogue associations remain authoritative. No migration is needed: only the existing image_path is saved. Native execution is already requested by the user.

- [x] Test removal leaves enclosed white package pixels, handles transparent and empty images, and bounds products without clipping.
- [x] Implement pure image preparation in web/src/lib/product-cutout.ts; local file decoding and PNG encoding in the manager.
- [x] Add web/src/components/BulkProductImageManager.tsx with exact product selection, original/processed and both theme previews, sequential queue, rejection and explicit approve/upload/save.
- [x] Add Android ProductShowcasePodium and theme tokens; integrate only ProductDetailsScreen.
- [ ] Run web format/lint/typecheck/tests and Android compile/lint/unit tests. Compare running screenshots against supplied reference where a local environment and synthetic catalogue allow it. Record unavailable checks honestly.

Review focus: white packages connected to white backgrounds; transparent canvas margins; very wide/very tall shapes; stale product selection during asynchronous processing; storage upload succeeding while catalogue update fails. Background removal is conservative and optional; manager must inspect edges before approval. Images never receive podium pixels.

# Balaji Provision Store — Project Documentation Pack (v1.3)

## Approved baseline
- Customer platform: native Android app + separate store management website sharing one backend.
- Payments: Cash on Delivery (COD) + online payments; online gateway remains gated until onboarding and secure payment flows are tested.
- Delivery: dedicated delivery person with least-privilege access to assigned orders only.
- Catalogue: manual product entry and management through dashboard.
- Budget: keep initial costs minimal.
- Delivery-charge threshold: initial value ₹250, configurable by the manager from the dashboard. This is **not** a minimum order amount. Orders below the configured threshold are allowed and incur the configured delivery fee.
- Free-delivery threshold: separate manager-configurable setting. Final relationship between the two thresholds and eligibility treatment for discounts/taxes must be confirmed before production.
- Store contact: 9837803293.

## Dashboard requirements
- Manager can update serviceable neighbourhoods/PIN codes or the selected service-area definition; checkout validates against the active server-side configuration.
- Manager can add/edit/deactivate products and update prices, units/pack/weight, stock, categories, images, and availability.
- Manager can configure the delivery-charge threshold, delivery fee, and free-delivery threshold independently.
- Manager/admin can create unique delivery-person user IDs, securely provision/reset access, and activate/deactivate accounts.
- Staff can assign orders to delivery persons. Delivery persons see only assigned orders and can update only permitted delivery statuses.
- Changes to service area, product/price/stock, thresholds/fees, accounts, assignments, and delivery status must be validated and audited. Never log passwords, OTPs, tokens, or payment secrets.
- All pricing and access rules must be enforced server-side, not hard-coded in the app. Existing order items keep price/name/unit snapshots.

## Critical delivery-fee rule
Do not enforce a hard ₹250 minimum order. If the cart subtotal is below the currently configured delivery-charge threshold, allow checkout and add the configured delivery fee. Test below threshold, exact threshold, and above threshold. Clearly label the delivery-charge threshold separately from the free-delivery threshold in dashboard and checkout.

## Documents
- `00_README_and_AI_BUILD_BRIEF.docx` — AI build brief
- `01_PRD.docx` — updated product requirements
- `01_PRD_owner_uploaded_original.docx` — unmodified copy of the PRD uploaded by the owner
- `02_SDLC_and_Project_Plan.docx` — SDLC and milestones
- `03_SRS_and_Acceptance_Criteria.docx` — requirements and acceptance tests
- `04_Technical_Architecture_Data_and_API.docx` — architecture, data model and API
- `05_UX_Content_and_Screen_Spec.docx` — screen and UX requirements
- `06_QA_Test_Plan.docx` — testing and release criteria
- `07_Deployment_Security_and_Operations.docx` — deployment and security
- `08_Open_Decisions_and_Risks.docx` — open decisions and risks

## Still to decide
- Exact supported neighbourhoods/PIN codes and service-area editing method.
- Final delivery fee; whether the free-delivery threshold equals or differs from the delivery-charge threshold; whether eligibility subtotal is before/after discounts and tax.
- Delivery-person login method, rider interface, allowed statuses, proof of delivery, and COD cash reconciliation.
- Online payment provider, refunds/reconciliation, customer login/OTP approach, operating hours, cancellation/substitution rules, tax/invoice obligations, and monthly cost ceiling.

## Technical references
- Next.js: https://nextjs.org/docs
- Android: https://developer.android.com/
- Supabase: https://supabase.com/docs

This is a planning baseline, not legal, tax, payment-provider, or security certification. Confirm current obligations and production security before launch.


## Billing/POS and inventory quantity rules (owner-requested)
- Generate, download, and print bills for online orders and manual in-store sales.
- Online bills preserve order-time item/price snapshots.
- Manual POS: search inventory, enter quantity, fetch current inventory price, and recalculate totals automatically.
- Products are either **Weighted** (positive grams or kilograms) or **Packed** (positive integer quantity only).
- Show units and price basis; validate on the server; prevent overselling; audit bill voids/corrections and stock changes.

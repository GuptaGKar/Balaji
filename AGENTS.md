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

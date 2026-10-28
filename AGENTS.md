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

## Migration and verification rules (added 2026-10-28)

13. Read the latest definition of a function and the real table columns before editing it; never rewrite an RPC from memory. Past failures: `stock_movements` (real table: `inventory_movements`) and `order_status_events (note, created_by)` (real columns: `actor_id`, `reason`).
14. When a function signature changes, give the new parameter a `DEFAULT` and drop the old signature in the same migration so PostgREST never sees two candidates.
15. `GRANT ... ON FUNCTION` must name the real signature; use `to_regprocedure` where it may vary. Never `GRANT ALL`. End every migration that touches functions with `NOTIFY pgrst, 'reload schema';`.
16. New migration files use a strict 14-digit `YYYYMMDDNNNNNN` prefix that sorts after every applied file.
17. Honesty over progress: never write "verified", "passes" or "works" for anything not actually run. If a gate cannot run here (no Docker/Postgres, no device, no credentials), record it in `DECISIONS.md` and the report as "not run" with the reason.
18. BLOCKED policy decisions (substitution, hours/slots, GST, loyalty rates, WhatsApp provider, rider location, UPI) are not invented: build them as configurable settings with a safe default and OFF, and log the open question.

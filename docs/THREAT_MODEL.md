# Threat Model — Balaji Provision Store v1 (M2)

Based on IMPLEMENTATION_PLAN.md §5.5 checklist. Each item tracked to mitigation in code/tests.

| ID | Threat | Impact | Likelihood | Mitigation | Status |
|----|--------|--------|------------|------------|--------|
| T-01 | IDOR on orders/bills | Customer sees another's order | Medium | RLS policies on orders/bills/order_items; customer reads only own; pgTAP tests 006 | ✅ Mitigated |
| T-02 | Privilege escalation (role column) | Customer promotes to manager | Low | Role changes only via manager RPC; profiles UPDATE policy manager-only; pgTAP test 008 | ✅ Mitigated |
| T-03 | Price/fee/total tampering | Client sends fake total | Medium | All pricing in `quote_cart`/`place_order` RPC; server never accepts client totals; pgTAP pricing tests M4 | ✅ Mitigated |
| T-04 | Stock races/double-spend | Two orders sell last unit | Medium | `SELECT ... FOR UPDATE` ordered by id in `place_order`/`pos_complete_sale`; M4 concurrency tests | ✅ Designed |
| T-05 | Duplicate submit | Customer double-charged | Medium | Idempotency keys in `place_order`/`pos_complete_sale`; unique (user,scope,key); M4 tests | ✅ Designed |
| T-06 | Webhook spoofing/replay | Fake payment confirmation | Low | `payment_events` dedupe on (provider, provider_event_id); signature verification in M11 | 🔲 M11 |
| T-07 | XSS in product names/notes | Stored XSS in dashboard | Low | All user input escaped; CSP headers; text-only rendering in Android | 🔲 M3/M5 |
| T-08 | SQL injection | Data exfiltration | Low | All queries parametrised; no dynamic SQL in RPCs; pgTAP tests | ✅ Mitigated |
| T-09 | Secret leakage | API keys in repo/logs | Medium | `.env.example` only; service-role key only in `web/src/server/supabaseAdmin.ts`; never log credentials; git-secret scan in CI | ✅ Mitigated |
| T-10 | Export abuse | Bulk data download | Low | RLS limits rows; no unauthenticated endpoints; rate limiting M12 | ✅ Designed |
| T-11 | Rider enumeration of orders | Rider sees all orders | Medium | `rider_orders` view filters to assigned orders only; RLS on orders table; pgTAP test 007 | ✅ Mitigated |
| T-12 | Guessable bill URLs | Unauthorized bill access | Low | Bill access authorised via RLS (customer own, staff all); no public links; pgTAP tests | ✅ Mitigated |
| T-13 | Account-reset abuse | Rider lockout/harassment | Low | Manager-only reset via server action; audit trail; forced password change; rate limiting | ✅ Designed |
| T-14 | Inactive rider access | Deactivated rider still accesses | Low | `is_active` checked in RPCs; policies use `current_role()` which checks profile; sessions revoked on deactivate | ✅ Designed |

## Verification Checkpoints
- **M2 Gate B**: All pgTAP RLS tests (005-008) green + human review sign-off.
- **M4 Gate C**: Concurrency + idempotency tests pass.
- **M11 Gate D**: Webhook signature + dedupe tests pass.
- **M12 Gate E**: Rate limiting, CSP, secret scan all pass.

## Residual Risks (Accepted)
- Rider login via synthetic email + OTP: not standard Supabase Auth flow; relies on server-generated OTP shown once to manager. If manager account compromised, rider accounts can be reset. Mitigated by audit trail + manager MFA (recommended).
- `delivery_failed` terminal state: no retry path defined yet (BLOCKED owner decision). If exploited, orders stuck in failed state.
- Weighted item variance: bill by ordered qty; actual weight may differ. Mitigated by staff correction flow (M7) + audit.
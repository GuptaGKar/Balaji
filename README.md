# Balaji Provision Store

Hyperlocal grocery ordering platform: a native **Android customer app** and a separate **store-management website** (`web/`, Next.js) sharing one **Supabase** backend (Postgres, Auth, Storage, Realtime). Single store, single inventory, local delivery area, low budget, modular monolith.

> Planning baseline only — not legal, tax, payment-provider, or security certification.
> Store contact: **9837803293**. Online payments are **disabled** until milestone M11 passes.

## Repository layout

```
balaji-store/
├─ AGENTS.md                  # agent operating rules (from IMPLEMENTATION_PLAN §14)
├─ docs/                      # project pack + IMPLEMENTATION_PLAN.md + DECISIONS.md + THREAT_MODEL.md
├─ supabase/
│  ├─ config.toml             # Supabase CLI project config
│  ├─ migrations/             # timestamped SQL — source of truth (M1+)
│  ├─ seed.sql                # synthetic seed data (M1)
│  ├─ tests/                  # pgTAP tests (M1+)
│  ├─ functions/              # Edge Functions (gated webhook etc., M11)
│  └─ _pre_plan_drafts/       # archived pre-plan drafts — superseded, kept for reference only
├─ web/                       # Next.js App Router: /admin, /pos, /rider (M3–M9)
├─ android/                   # Kotlin + Jetpack Compose customer app (M5–M8)
├─ scripts/check_guards.sh    # CI guard: no minimum_order, no hard-coded 250/25000
└─ .github/workflows/ci.yml   # CI: lint, typecheck, format, guards, (pgTAP + Android from M1/M5)
```

## Prerequisites

- Node.js 20+ and npm 10+
- Supabase CLI (`npm install -g supabase` or per https://supabase.com/docs/guides/local-development) — **requires Docker** for `supabase start`
- Android Studio + JDK 17 (only for `android/`, from M5)

## Quickstart (local)

```bash
# 1. Start the local Supabase stack (Postgres, Auth, Studio) — needs Docker
cd supabase && supabase start && cd ..

# 2. Apply migrations and load synthetic seed (from M1)
cd supabase && supabase db reset && cd ..

# 3. Run the web app (admin + pos + rider)
cd web && npm install && npm run dev        # http://localhost:3000

# 4. Android app (from M5): open android/ in Android Studio
```

## Checks

```bash
npm --prefix web run lint
npm --prefix web run type-check
npm --prefix web run format:check
bash scripts/check_guards.sh
# pgTAP (from M1):  supabase test db
```

## Milestone status

| Milestone | Scope | Status |
|---|---|---|
| M0 | Foundation & decisions | done (this commit) |
| M1 | Schema & migrations | next — pending approval |
| M2 | Auth, roles, RLS, audit core | — |
| M3 | Catalogue & settings | — |
| M4 | Pricing engine & checkout | — |
| M5–M6 | Android app + admin order queue | — |
| M7–M8 | Delivery workflow, inventory, reports | — |
| M9 | Billing & POS | — |
| M10–M12 | Notifications, payment gate, hardening | — |

See `docs/IMPLEMENTATION_PLAN.md` for the full plan, gates, and test plan. See `docs/DECISIONS.md` for locked/provisional/blocked decisions.

#!/usr/bin/env python3
"""Assemble one rolled-back SQL batch that applies M1's two migrations and runs the
041 pgTAP suite against the LIVE database without persisting anything.

Nothing is committed: the batch opens with BEGIN and closes with ROLLBACK, and the
pgtap extension itself is created inside that transaction, so it disappears with it.
NOTIFY pgrst lines are stripped so no schema reload can be triggered.
"""
import re, sys

M = "supabase/migrations/20261102000009_delivery_zone_geometry.sql"
P = "supabase/migrations/20261102000010_zone_delivery_pricing.sql"
T = "supabase/tests/041_delivery_zones.sql"
OUT = "/tmp/verify_m1.sql"


def strip_txn_wrappers(src, include_rollback=False):
    pattern = r"(?m)^\s*(BEGIN|COMMIT)\s*;\s*$"
    if include_rollback:
        pattern = r"(?m)^\s*(BEGIN|COMMIT|ROLLBACK)\s*;\s*$"
    src = re.sub(pattern, "", src)
    return src.replace("NOTIFY pgrst, 'reload schema';", "-- (NOTIFY stripped for the dry run)")


def body(path, strip_txn, drop_finish=False):
    src = open(path, encoding="utf-8").read()
    if strip_txn:
        # The suite's own ROLLBACK must not fire in the middle of the batch, and its
        # finish() is replaced by the single finish() appended below.
        src = strip_txn_wrappers(src, include_rollback=drop_finish)
    if drop_finish:
        src = src.replace("SELECT * FROM finish();", "-- (finish() runs once at the end)")
    return src


parts = [
    "BEGIN;",
    "-- pgtap is created inside this transaction and vanishes with the rollback.",
    "CREATE EXTENSION IF NOT EXISTS pgtap;",
    "-- ===== migration 009 =====",
    body(M, True),
    "-- ===== migration 010 =====",
    body(P, True),
    "-- ===== suite 041 =====",
    body(T, True, drop_finish=True),
    "SELECT * FROM finish();",
    "-- The CLI only surfaces the final result set, so the verdict must be last: 0 = all pass.",
    "SELECT coalesce(num_failed(), -1) AS pgtap_failures;",
    "ROLLBACK;",
]

sql = "\n".join(parts)
open(OUT, "w", encoding="utf-8").write(sql)
print(f"wrote {OUT} ({len(sql.splitlines())} lines)")
for name, path in (("009", M), ("010", P), ("041", T)):
    if "BEGIN;" not in open(path, encoding="utf-8").read():
        print(f"note: {name} has no BEGIN wrapper", file=sys.stderr)

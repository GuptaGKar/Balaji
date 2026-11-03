#!/usr/bin/env python3
"""Build a single rolled-back SQL batch that applies a migration and runs a pgTAP suite
against the LIVE database, then reports failures. Nothing is committed: the batch opens
with BEGIN and ends with ROLLBACK. The suite is expected to create pgTAP itself (also
inside the transaction), which this runner deliberately does not do for it.

Usage: python3 scripts/verify_migration_live.py <migration.sql> <suite.sql>
"""
import re, sys

if len(sys.argv) != 3:
    sys.exit("usage: verify_migration_live.py <migration.sql> <suite.sql>")

MIGRATION, SUITE = sys.argv[1], sys.argv[2]
OUT = "/tmp/verify_live.sql"


def read(path):
    return open(path, encoding="utf-8").read()


def strip_txn(src):
    src = re.sub(r"(?m)^\s*(BEGIN|COMMIT|ROLLBACK)\s*;\s*$", "", src)
    src = src.replace("NOTIFY pgrst, 'reload schema';", "-- (NOTIFY stripped for the dry run)")
    return src


# Suites must be self-contained: `\ir` and other backslash commands are psql-only and
# fail with a syntax error in the SQL editor and under `supabase db query`.
suite = strip_txn(read(SUITE))
meta = re.findall(r"(?m)^\s*\\[a-z]+", suite)
if meta:
    sys.exit(f"{SUITE} contains psql meta-commands {meta}; inline the helper instead.")
suite = re.sub(r"(?m)^\s*SELECT \* FROM finish\(\);\s*$", "", suite)

parts = [
    "BEGIN;",
    # No pgtap bootstrap here on purpose: suites create it themselves, so this runner
    # proves that self-bootstrapping works instead of masking it.
    f"-- ===== {MIGRATION} =====",
    strip_txn(read(MIGRATION)),
    f"-- ===== {SUITE} =====",
    suite,
    "SELECT * FROM finish();",
    "SELECT coalesce(num_failed(), -1) AS pgtap_failures;",
    "ROLLBACK;",
]

sql = "\n".join(parts)
open(OUT, "w", encoding="utf-8").write(sql)
print(f"wrote {OUT} ({len(sql.splitlines())} lines)")

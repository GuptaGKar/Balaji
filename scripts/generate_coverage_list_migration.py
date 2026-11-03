#!/usr/bin/env python3
"""Generate the coverage-list migration by patching the REAL applied function body.

Never rewrite an RPC from memory (AGENTS rule 13): this slices the definition out of the
applied migration, applies each change with an exact single-match assertion, and emits a new
file. If the live definition ever differs from what is on disk, the assertions fail loudly
instead of silently producing a wrong function.

Why the change: get_delivery_zone_coverage ended with `WHERE sa.boundary IS NOT NULL`, so an
area saved without a drawn polygon never appeared in "Drawn areas and what they cover". The
manager's only feedback was that the area had vanished.
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APPLIED = ROOT / "supabase/migrations/20261103000001_delivery_zone_admin.sql"
OUT = ROOT / "supabase/migrations/20261103000002_delivery_zone_coverage_lists_every_area.sql"

source = APPLIED.read_text(encoding="utf-8")

start = source.index("CREATE OR REPLACE FUNCTION public.get_delivery_zone_coverage()")
end = source.index("$$;", start) + len("$$;")
body = source[start:end]

assert "WHERE sa.boundary IS NOT NULL" in body, "expected the boundary filter in the applied body"


def replace_once(text: str, old: str, new: str, why: str) -> str:
    count = text.count(old)
    assert count == 1, f"{why}: found {count} matches, expected exactly 1\n---\n{old}\n---"
    return text.replace(old, new)


# 1. List every area, not only the ones that already have a shape.
patched = replace_once(
    patched_source := body,
    "    FROM public.service_areas sa\n    WHERE sa.boundary IS NOT NULL\n  ) bounded;",
    "    FROM public.service_areas sa\n  ) all_areas;",
    "dropping the boundary-only filter",
)
del patched_source

# 2. Tell the caller whether the row has a shape, so the UI can say what a rule actually does.
patched = replace_once(
    patched,
    "      'geojson', CASE WHEN sa.boundary IS NULL THEN NULL",
    "      'has_boundary', sa.boundary IS NOT NULL,\n      'geojson', CASE WHEN sa.boundary IS NULL THEN NULL",
    "adding the has_boundary flag",
)

# 3. The heading promise is about coverage, so order the list by shape first is NOT wanted;
#    keep priority,label but make the stale comment honest.
patched = replace_once(
    patched,
    "RETURNS jsonb\nLANGUAGE plpgsql\nSTABLE",
    "-- Returns EVERY service area, drawn or not, with has_boundary saying whether it has a shape.\n"
    "-- A row without a boundary can still gate its own PIN (kind = 'pincode'); a row that is both\n"
    "-- shapeless and not a PIN rule prices nothing, and the admin table has to say so rather than\n"
    "-- hide it.\n"
    "RETURNS jsonb\nLANGUAGE plpgsql\nSTABLE",
    "documenting the new contract",
)

assert patched.count("'has_boundary', sa.boundary IS NOT NULL,") == 1, "the JSON key must appear once"
assert patched.count("has_boundary") == 2, "once in the new doc comment, once as the key"
assert "WHERE sa.boundary IS NOT NULL\n  )" not in patched, "the row filter must be gone"
# Two inner per-row guards (coverage count, overlap count) survive plus the new has_boundary flag;
# only the row-level filter was removed.
assert patched.count("sa.boundary IS NOT NULL") == 3, "expected 2 inner guards + the new flag"

migrated = f"""-- ============================================================
-- 20261103000002  Coverage list shows every area, drawn or not
-- ============================================================
-- Reported by the owner: an area added in the admin panel did not appear under
-- "Drawn areas and what they cover". Cause: get_delivery_zone_coverage filtered rows to
-- `boundary IS NOT NULL`, so a shape-less area was invisible with no explanation.
--
-- It also exposed a second, sharper fact that is preserved on purpose: an area with no
-- boundary AND a kind that is not a PIN rule cannot price anything. resolve_delivery_zone
-- requires `boundary IS NOT NULL`, and the PIN lookup in quote_cart/check_delivery_availability
-- only matches kind = 'pincode'. The list therefore has to show the row and say what it does,
-- rather than hide it.
--
-- Signature is unchanged, so no DROP is needed and PostgREST keeps one candidate.

{patched}

-- Grants are restated exactly as the applied migration set them (idempotent, no widening).
REVOKE ALL ON FUNCTION public.get_delivery_zone_coverage() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_delivery_zone_coverage() TO authenticated;

COMMENT ON FUNCTION public.get_delivery_zone_coverage IS
  'Manager-only list of EVERY delivery area with its coverage counts. has_boundary says whether '
  'the row carries a drawn polygon; shapeless rows that are not PIN rules price nothing.';

NOTIFY pgrst, 'reload schema';
"""

OUT.write_text(migrated, encoding="utf-8")
print(f"wrote {OUT.name}: {len(migrated.splitlines())} lines")
print("patched function body lines:", len(patched.splitlines()))

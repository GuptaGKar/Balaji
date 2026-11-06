#!/usr/bin/env python3
"""Build a rolled-back live batch that captures every pgTAP assertion's TAP line into a temp
table, so the failing ones can be printed even though `supabase db query` only shows the last
result set and this pgTAP build has no check_test_results().

Throwaway diagnostic; nothing here is committed and the batch always ends in ROLLBACK.
"""
import re
import sys

ASSERTIONS = (
    "is", "isnt", "ok", "nok", "eq", "not_eq", "eq_case", "is_sz", "matches", "doesnt_match",
    "results_eq", "results_ne", "bag_eq", "bag_ne", "set_eq", "set_ne", "arrays_same",
    "arrays_distinct", "lives_ok", "throws_ok", "throws_like", "throws_ilike", "throws_matching",
    "col_is_pk", "col_is_unique", "hasnt_index", "has_schema", "one_row", "todo_start", "todo_end",
    "col_type_is", "has_column", "hasnt_column", "has_table", "hasnt_table", "has_index",
    "has_trigger", "function_ok", "has_function", "schema_exists", "has_relation",
    "is_unique", "is_primary_key", "is_deferrable", "foreign_key_exists",
    "table_has_privs", "has_privs", "hasnt_privs", "role_exists", "has_sequence",
)


def statements(sql):
    """Split into top-level statements on ';' outside single quotes, dollar quotes and comments."""
    out, buf = [], ""
    i, n = 0, len(sql)
    while i < n:
        c = sql[i]
        if c == "'":
            j = i + 1
            while j < n:
                if sql[j] == "'":
                    if j + 1 < n and sql[j + 1] == "'":
                        j += 2
                        continue
                    break
                j += 1
            buf += sql[i:j + 1]
            i = j + 1
            continue
        if c == "$":
            m = re.match(r"\$([A-Za-z_0-9]*)\$", sql[i:])
            if m:
                tag = m.group(0)
                end = sql.find(tag, i + len(tag))
                if end == -1:
                    end = n - len(tag)
                buf += sql[i:end + len(tag)]
                i = end + len(tag)
                continue
        # Comments are dropped rather than emitted: a ';' inside a comment is not a separator,
        # and a leading '--' line would otherwise stop the statement matching an assertion.
        if sql[i:i + 2] == "--":
            j = sql.find("\n", i)
            i = n if j == -1 else j + 1
            buf += "\n"
            continue
        if sql[i:i + 2] == "/*":
            j = sql.find("*/", i + 2)
            i = n if j == -1 else j + 2
            buf += " "
            continue
        if c == ";":
            out.append(buf)
            buf = ""
            i += 1
            continue
        buf += c
        i += 1
    if buf.strip():
        out.append(buf)
    return out


def assertion_call(stmt):
    """True when the statement is a bare SELECT of a pgTAP assertion."""
    s = stmt.strip()
    m = re.match(r"(?is)^SELECT\s+(?:DISTINCT\s+)?([\w.]+)\s*\(", s)
    if not m:
        return False
    return m.group(1).lower().split(".")[-1] in ASSERTIONS


def main():
    migration, suite = sys.argv[1], sys.argv[2]

    def strip_txn(src):
        src = re.sub(r"(?m)^\s*(BEGIN|COMMIT|ROLLBACK)\s*;\s*$", "", src)
        src = src.replace("NOTIFY pgrst, 'reload schema';", "-- (NOTIFY stripped for the dry run)")
        return src

    parts = [
        "BEGIN;",
        "CREATE TEMP TABLE tapdiag (line text);",
        "GRANT SELECT, INSERT ON tapdiag TO PUBLIC;",
        "-- ===== migration =====",
        strip_txn(open(migration, encoding="utf-8").read()),
        "-- ===== suite =====",
    ]

    body = strip_txn(open(suite, encoding="utf-8").read())
    body = re.sub(r"(?m)^\s*SELECT \* FROM finish\(\);\s*$", "", body)
    captured = 0
    for stmt in statements(body):
        if not stmt.strip():
            continue
        if assertion_call(stmt):
            parts.append("INSERT INTO tapdiag(line) " + stmt.strip() + ";")
            captured += 1
        else:
            parts.append(stmt.strip() + ";")

    parts.append(
        "SELECT coalesce(string_agg('>> ' || line, E'\\n'), 'no failures captured') "
        "AS failures FROM tapdiag WHERE line LIKE 'not ok%';"
    )
    parts.append("ROLLBACK;")
    open("/tmp/verify_live.sql", "w", encoding="utf-8").write("\n".join(parts) + "\n")
    print(f"wrote /tmp/verify_live.sql ({captured} assertions captured)")


if __name__ == "__main__":
    sys.exit(main())

// M4 cross-client contract guard.
//
// The whole point of this feature is that a manager and a shopper are offered the same list from
// the same source, and that neither one searches on a single keystroke. Those properties live in
// three different files (SQL, Kotlin, TypeScript), so they are exactly the kind of thing a later
// edit drifts apart without anyone noticing.

const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const read = (rel) => fs.readFileSync(path.resolve(__dirname, rel), "utf8");

const migration = read("../../supabase/migrations/20261103000003_area_suggestions.sql");
const android = read(
  "../../android/app/src/main/java/com/balaji/provision/data/location/AreaSuggestion.kt",
);
const androidApi = read(
  "../../android/app/src/main/java/com/balaji/provision/data/network/SupabaseApi.kt",
);
const panel = read("../../web/src/app/(admin)/service-areas/DeliveryZonesPanel.tsx");

test("both clients call the same RPC the server defines", () => {
  assert.match(migration, /CREATE OR REPLACE FUNCTION public\.search_area_suggestions\(/);
  assert.match(androidApi, /@POST\("rest\/v1\/rpc\/search_area_suggestions"\)/);
  assert.match(panel, /rpc\("search_area_suggestions"/);
});

test("the minimum query length agrees across all three layers", () => {
  // The server is the authority; the clients only avoid a pointless round trip. If they diverge,
  // a client either fires requests the server rejects or withholds search the server would answer.
  assert.match(migration, /char_length\(v_raw\) < 2/);
  assert.match(android, /const val MIN_QUERY_LENGTH = 2/);
  assert.match(panel, /query\.length < 2/);
});

test("the server caps the page and refuses an over-long query", () => {
  assert.match(
    migration,
    /LEAST\(GREATEST\(coalesce\(p_limit, 8\), 1\), 10\)/,
    "page size is clamped",
  );
  assert.match(migration, /char_length\(v_raw\) > 64/, "a pasted wall of text is not a search");
  assert.match(android, /const val MAX_QUERY_LENGTH = 64/, "the client agrees on the ceiling");
});

test("user text is escaped before it becomes a LIKE pattern", () => {
  // Behaviour is proved in supabase/tests/044 (a typed % must not match every area); this only
  // pins that the escape chain is still in place, without hardcoding backslash counts.
  assert.match(
    migration,
    /replace\(replace\(replace\(v_raw,/,
    "wildcards must be escaped before use",
  );
  assert.match(migration, /, '%',/, "the percent wildcard is escaped");
  assert.match(migration, /, '_',/, "the underscore wildcard is escaped");
});

test("the private half is scoped to the caller and pinned as definer", () => {
  // SECURITY DEFINER bypasses RLS, so the predicate on auth.uid() is the isolation itself.
  assert.match(migration, /a\.customer_id = v_uid/);
  assert.match(migration, /v_uid := auth\.uid\(\)/);
  assert.match(migration, /WHERE v_uid IS NOT NULL/);
  assert.match(migration, /SET search_path = ''/);
});

test("no amount is exposed through a public search", () => {
  // The suggestion list is granted to anon; fees and thresholds must stay on the priced RPC.
  const areaBranch = migration.slice(
    migration.indexOf("-- ─── Areas the store serves"),
    migration.indexOf("-- ─── The caller"),
  );
  for (const column of [
    "delivery_fee_paise",
    "delivery_charge_threshold_paise",
    "free_delivery_threshold_paise",
  ]) {
    assert.ok(!areaBranch.includes(`'${column}'`), `area suggestions must not carry ${column}`);
  }
});

test("the admin list offers areas only, and debounces rather than searching per keystroke", () => {
  assert.match(
    panel,
    /row\.type === "area"/,
    "a manager is completing an area, not a private address",
  );
  assert.match(
    panel,
    /const timer = setTimeout\(async \(\) => \{[\s\S]*?\}, 300\);/,
    "300ms debounce",
  );
  assert.match(
    panel,
    /cancelled = true;/,
    "an in-flight answer must not land after the field moved on",
  );
});

test("an area chip only writes a PIN when the area is a PIN", () => {
  // Guessing a PIN for a neighbourhood would put a number in the field the server gates on.
  assert.match(
    android,
    /postalCode = if \(kind == PINCODE_KIND\) value\.orKeep\(draft\.postalCode\) else draft\.postalCode/,
  );
  assert.match(
    android,
    /city = city\.orKeep\(draft\.city\)/,
    "city comes from a saved address, never from an area row",
  );
});

// Regression guard for "added areas not showing in the coverage list" (2026-11-03).
//
// get_delivery_zone_coverage ended with `WHERE sa.boundary IS NOT NULL`, so an area saved
// without a drawn polygon was never returned - and the panel showed "No areas drawn yet", the
// same wording it uses when the database really is empty. Two more traps sat underneath: the
// function answers a non-manager with an OBJECT, which the client used to cast straight to an
// array (zones.map then throws and the whole route blanks), and a row that has neither a shape
// nor PIN semantics can never be selected by the server, so saving one is saving nothing.

const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const panel = fs.readFileSync(
  path.resolve(__dirname, "../../web/src/app/(admin)/service-areas/DeliveryZonesPanel.tsx"),
  "utf8",
);
const coverageMigration = fs.readFileSync(
  path.resolve(
    __dirname,
    "../../supabase/migrations/20261103000002_delivery_zone_coverage_lists_every_area.sql",
  ),
  "utf8",
);

test("the coverage function returns every area, not only the ones with a shape", () => {
  assert.doesNotMatch(
    coverageMigration,
    /FROM public\.service_areas sa\s+WHERE sa\.boundary IS NOT NULL\s+\)/,
    "a row-level boundary filter is what hid the area",
  );
  assert.match(
    coverageMigration,
    /'has_boundary', sa\.boundary IS NOT NULL,/,
    "the flag tells the UI what the row is",
  );
});

test("the inner per-row guards survive, so shapeless rows still count nothing", () => {
  // Removing the row filter must not accidentally remove the guards inside the coverage and
  // overlap subqueries, or a shapeless row would report it covers every address in the table.
  const guards = coverageMigration.match(/AND sa\.boundary IS NOT NULL/g) || [];
  assert.equal(
    guards.length,
    2,
    "expected the addresses_inside and overlapping_areas guards to remain",
  );
});

test("a failed load is not presented as an empty database", () => {
  assert.match(
    panel,
    /const \[zonesError, setZonesError\]/,
    "the panel needs a distinct error state",
  );
  assert.match(panel, /zonesError \?/, "the error must be rendered instead of the empty message");
  assert.match(
    panel,
    /Could not load delivery areas: \$\{error\.message\}/,
    "the server reason must reach the manager",
  );
});

test("the client refuses to treat the FORBIDDEN object as a list", () => {
  assert.match(
    panel,
    /if \(!Array\.isArray\(data\)\)/,
    "a non-manager gets an object, and zones.map would throw on it",
  );
});

test("a brand-new neighbourhood cannot be saved without a shape", () => {
  assert.match(
    panel,
    /!editingId && kind !== "pincode" && !geometry/,
    "a shapeless non-PIN row can never be resolved, so creating one must be refused",
  );
});

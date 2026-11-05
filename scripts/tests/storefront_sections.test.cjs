const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const Module = require("node:module");
const ts = require("../../web/node_modules/typescript");

// Same transpile-and-require approach as form_validation.test.cjs, so the reducer under test is
// the real web module and cannot drift from it.
const filename = path.resolve(__dirname, "../../web/src/lib/storefront.ts");
const compiled = ts.transpileModule(fs.readFileSync(filename, "utf8"), {
  compilerOptions: { module: ts.ModuleKind.CommonJS },
}).outputText;
const mod = new Module(filename, module);
mod.filename = filename;
mod.paths = Module._nodeModulePaths(path.dirname(filename));
mod._compile(compiled, filename);
const {
  sectionReducer,
  initialSectionState,
  validateSectionDraft,
  normaliseItemIds,
  MAX_SECTIONS_PER_PAGE,
  MAX_ITEMS_PER_SECTION,
} = mod.exports;

const section = (id, overrides = {}) => ({
  id,
  title: `Section ${id}`,
  subtitle: null,
  sectionType: "products",
  active: true,
  sortOrder: 0,
  updatedAt: null,
  itemIds: [],
  ...overrides,
});

const order = (state) => state.sections.map((s) => s.id).join(",");
const numbers = (state) => state.sections.map((s) => s.sortOrder).join(",");

test("adding appends and renumbers from one", () => {
  let state = initialSectionState([section("a"), section("b")]);
  assert.equal(order(state), "a,b");
  state = sectionReducer(state, { type: "add", section: section("c") });
  assert.equal(order(state), "a,b,c");
  assert.equal(numbers(state), "1,2,3");
});

test("the 21st add is refused in the UI and leaves the list untouched", () => {
  let state = initialSectionState(
    Array.from({ length: MAX_SECTIONS_PER_PAGE }, (_, i) => section(`s${i}`)),
  );
  assert.equal(state.sections.length, 20);
  const before = order(state);
  state = sectionReducer(state, { type: "add", section: section("overflow") });
  assert.equal(order(state), before, "no section was added");
  assert.match(state.notice, /at most 20 sections/, "the refusal is explained");
});

test("keyboard move swaps neighbours and keeps sort_order contiguous", () => {
  let state = initialSectionState([section("a"), section("b"), section("c")]);
  state = sectionReducer(state, { type: "move", id: "c", direction: -1 });
  assert.equal(order(state), "a,c,b");
  assert.equal(numbers(state), "1,2,3");
});

test("moving the first section up is a no-op, not a wraparound", () => {
  const state = initialSectionState([section("a"), section("b")]);
  const moved = sectionReducer(state, { type: "move", id: "a", direction: -1 });
  assert.equal(order(moved), "a,b");
});

test("moving the last section down is a no-op", () => {
  const state = initialSectionState([section("a"), section("b")]);
  const moved = sectionReducer(state, { type: "move", id: "b", direction: 1 });
  assert.equal(order(moved), "a,b");
});

test("drag reorder moves an item across the list rather than swapping", () => {
  let state = initialSectionState([section("a"), section("b"), section("c"), section("d")]);
  state = sectionReducer(state, { type: "reorder", fromId: "a", toId: "c" });
  assert.equal(order(state), "b,c,a,d", "splice semantics, not a swap");
  assert.equal(numbers(state), "1,2,3,4");
});

test("reorder onto itself or an unknown id changes nothing", () => {
  const state = initialSectionState([section("a"), section("b")]);
  assert.equal(order(sectionReducer(state, { type: "reorder", fromId: "a", toId: "a" })), "a,b");
  assert.equal(order(sectionReducer(state, { type: "reorder", fromId: "a", toId: "zz" })), "a,b");
});

test("deleting renumbers so there is never a gap in sort_order", () => {
  let state = initialSectionState([section("a"), section("b"), section("c")]);
  state = sectionReducer(state, { type: "remove", id: "a" });
  assert.equal(order(state), "b,c");
  assert.equal(numbers(state), "1,2", "no orphaned 2,3 left behind");
});

test("toggling active marks the section dirty so leaving is warned about", () => {
  let state = initialSectionState([section("a", { dirty: false })]);
  state = sectionReducer(state, { type: "toggleActive", id: "a" });
  assert.equal(state.sections[0].active, false);
  assert.equal(state.sections[0].dirty, true);
});

test("an update cannot change a saved section's type", () => {
  let state = initialSectionState([section("a", { sectionType: "products" })]);
  state = sectionReducer(state, {
    type: "update",
    id: "a",
    patch: { title: "Renamed", sectionType: "categories" },
  });
  assert.equal(state.sections[0].title, "Renamed", "the editable part applied");
  assert.equal(
    state.sections[0].sectionType,
    "products",
    "type is fixed at creation, so a patch cannot convert a rail into tiles",
  );
});

test("heading validation matches the database bounds", () => {
  assert.equal(validateSectionDraft({ title: "", itemIds: [] }), "A heading is required.");
  assert.equal(validateSectionDraft({ title: "   ", itemIds: [] }), "A heading is required.");
  assert.match(validateSectionDraft({ title: "x".repeat(41), itemIds: [] }), /40 characters/);
  assert.match(
    validateSectionDraft({ title: "ok", subtitle: "y".repeat(81), itemIds: [] }),
    /80 characters/,
  );
  assert.equal(validateSectionDraft({ title: "ok", itemIds: ["p1"] }), null);
});

test("the item cap is reported before the save, matching the trigger", () => {
  const tooMany = Array.from({ length: MAX_ITEMS_PER_SECTION + 1 }, (_, i) => `id-${i}`);
  assert.match(validateSectionDraft({ title: "ok", itemIds: tooMany }), /at most 30 items/);
});

test("normaliseItemIds always includes the primary and never duplicates", () => {
  assert.deepEqual(normaliseItemIds("a", ["b", "c"]), ["a", "b", "c"]);
  assert.deepEqual(normaliseItemIds("a", ["a", "b", "b"]), ["a", "b"]);
  assert.deepEqual(normaliseItemIds("a", []), ["a"]);
});

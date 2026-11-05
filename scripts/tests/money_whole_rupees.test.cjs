const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const Module = require("node:module");
const ts = require("../../web/node_modules/typescript");

// Same transpile-and-require approach form_validation.test.cjs uses, so the helper under test
// is the real web module rather than a copy that can drift away from it.
const filename = path.resolve(__dirname, "../../web/src/lib/money.ts");
const compiled = ts.transpileModule(fs.readFileSync(filename, "utf8"), {
  compilerOptions: { module: ts.ModuleKind.CommonJS },
}).outputText;
const moneyModule = new Module(filename, module);
moneyModule.filename = filename;
moneyModule.paths = Module._nodeModulePaths(path.dirname(filename));
moneyModule._compile(compiled, filename);
const { formatRupeesWhole, paiseToRupeesCompact, formatRupees } = moneyModule.exports;

// The storefront rails, the item picker and the dashboard preview show whole rupees only.
// Everything else keeps formatRupees / paiseToRupeesCompact with their decimals, so this
// suite pins BOTH halves: the new helper rounds, and the old helpers must not change.
test("whole-rupee formatting is exact at the boundary", () => {
  assert.equal(formatRupeesWhole(6000), "₹60");
  assert.equal(formatRupeesWhole(100), "₹1");
  assert.equal(formatRupeesWhole(0), "₹0");
});

test("whole-rupee formatting rounds half up, matching the per-line money rule", () => {
  assert.equal(formatRupeesWhole(6049), "₹60", "60.49 must not round up");
  assert.equal(formatRupeesWhole(6050), "₹61", "60.50 is the half-up boundary");
  assert.equal(formatRupeesWhole(6099), "₹61");
  assert.equal(formatRupeesWhole(49), "₹0", "under half a rupee rounds down");
  assert.equal(formatRupeesWhole(50), "₹1", "exactly half a rupee rounds up");
});

test("whole-rupee formatting uses Indian digit grouping and never prints decimals", () => {
  assert.equal(formatRupeesWhole(100000), "₹1,000");
  assert.equal(formatRupeesWhole(1500000), "₹15,000");
  assert.equal(formatRupeesWhole(123456700), "₹12,34,567");
  for (const paise of [0, 100, 6050, 100000, 123456700]) {
    assert.ok(!formatRupeesWhole(paise).includes("."), `no decimal point for ${paise}`);
    assert.ok(!formatRupeesWhole(paise).endsWith(".00"), `never .00 for ${paise}`);
  }
});

test("whole-rupee formatting has an explicit empty state instead of a fake price", () => {
  assert.equal(formatRupeesWhole(null), "—");
  assert.equal(formatRupeesWhole(undefined), "—");
  // A price of 0 paise is a real value and must not collapse into the empty state.
  assert.equal(formatRupeesWhole(0), "₹0");
});

test("the existing rupee helpers are untouched, so no current screen changes", () => {
  assert.equal(formatRupees(6050), "₹60.50");
  assert.equal(paiseToRupeesCompact(6050), "₹60.5");
  assert.equal(paiseToRupeesCompact(6000), "₹60");
});

test("whole-rupee math stays in integers, never floating-point division", () => {
  // 0.1 + 0.2 style drift is the reason money is paise; a large odd value catches it.
  assert.equal(formatRupeesWhole(999999999), "₹1,00,00,000");
  assert.equal(formatRupeesWhole(1234567891), "₹1,23,45,679");
});

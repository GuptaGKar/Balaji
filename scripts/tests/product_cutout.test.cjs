const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const ts = require("../../web/node_modules/typescript");
const code = ts.transpileModule(
  fs.readFileSync(
    require("node:path").join(__dirname, "../../web/src/lib/product-cutout.ts"),
    "utf8",
  ),
  { compilerOptions: { module: ts.ModuleKind.CommonJS } },
).outputText;
const m = { exports: {} };
new Function("exports", "module", code)(m.exports, m);
const { preparePixels } = m.exports;
test("only removes border-connected white; enclosed white branding survives", () => {
  const pixels = new Uint8ClampedArray(5 * 5 * 4).fill(255);
  for (let y = 1; y < 4; y++)
    for (let x = 1; x < 4; x++) {
      const i = (y * 5 + x) * 4;
      pixels[i] = 180;
      pixels[i + 1] = 40;
      pixels[i + 2] = 0;
    }
  pixels.set([255, 255, 255, 255], (2 * 5 + 2) * 4);
  const r = preparePixels(pixels, 5, 5, true);
  assert.equal(r.pixels[3], 0);
  assert.equal(r.pixels[(2 * 5 + 2) * 4 + 3], 255);
  assert.deepEqual(r.bounds, { x: 1, y: 1, width: 3, height: 3 });
  assert.equal(pixels[3], 255);
});
test("keep-background preserves white packaging; transparent input uses visible bounds", () => {
  const p = new Uint8ClampedArray(3 * 5 * 4);
  p.set([255, 255, 255, 255], (3 * 2 + 1) * 4);
  assert.deepEqual(preparePixels(p, 3, 5, false).bounds, { x: 1, y: 2, width: 1, height: 1 });
});
test("empty cutouts fail instead of silently uploading an invisible product", () => {
  assert.throws(
    () => preparePixels(new Uint8ClampedArray(16).fill(255), 2, 2, true),
    /No visible product/,
  );
});

const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const Module = require("node:module");
const ts = require("../../web/node_modules/typescript");
const React = require("../../web/node_modules/react");
const { renderToStaticMarkup } = require("../../web/node_modules/react-dom/server");
const filename = path.resolve(__dirname, "../../web/src/components/SafeImage.tsx");
const compiled = ts.transpileModule(fs.readFileSync(filename, "utf8"), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
}).outputText;
const component = new Module(filename, module);
component.filename = filename;
component.paths = Module._nodeModulePaths(path.dirname(filename));
component._compile(compiled, filename);
const { SafeImage } = component.exports;
function render(src) {
  return renderToStaticMarkup(
    React.createElement(SafeImage, {
      src,
      alt: "Synthetic photo",
      fallback: React.createElement("span", null, "No photo"),
    }),
  );
}
test("private order-media request stays on the authenticated same-origin API", () => {
  assert.match(
    render("/api/order-media?bucket=order-images&path=synthetic.png"),
    /src="\/api\/order-media\?/,
  );
});
test("public catalogue object still resolves to its public storage path", () => {
  assert.match(
    render("product-images/synthetic.png"),
    /storage\/v1\/object\/public\/product-images\/synthetic.png/,
  );
});
test("absolute image URL is preserved", () => {
  assert.match(
    render("https://example.invalid/synthetic.png"),
    /src="https:\/\/example.invalid\/synthetic.png"/,
  );
});

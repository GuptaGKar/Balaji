// Export the existing public pages without application services or credentials.
const fs = require("node:fs");
const path = require("node:path");
const Module = require("node:module");
const root = path.resolve(__dirname, "../..");
const fromWeb = Module.createRequire(path.join(root, "web/package.json"));
const ts = fromWeb("typescript");
const React = fromWeb("react");
const { renderToStaticMarkup } = fromWeb("react-dom/server");
const css = `*{box-sizing:border-box}body{margin:0;background:#fffaf5;color:#292524;font:17px/1.75 system-ui,sans-serif}main{max-width:820px;margin:auto;padding:40px 24px}h1{font-size:2.3rem;line-height:1.2}h2{font-size:1.3rem;margin-top:32px}a{color:#9a3412;text-underline-offset:3px}a:focus-visible{outline:3px solid #ea580c;outline-offset:4px}p,li{overflow-wrap:anywhere}section{margin:28px 0}main>div:last-child>a{margin-right:20px}main>p:first-child{color:#9a3412;font-weight:700} @media(max-width:480px){main{padding:24px 18px}h1{font-size:1.9rem}}`;
for (const slug of ["privacy", "account-deletion", "terms"]) {
  const file = path.join(root, "web/src/app", slug, "page.tsx");
  const js = ts.transpileModule(fs.readFileSync(file, "utf8"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText;
  const mod = { exports: {} };
  const localRequire = (name) =>
    name === "next/link"
      ? { default: ({ children, ...props }) => React.createElement("a", props, children) }
      : fromWeb(name);
  new Function("require", "module", "exports", js)(localRequire, mod, mod.exports);
  const content = renderToStaticMarkup(React.createElement(mod.exports.default));
  const html = `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${mod.exports.metadata.title}</title><style>${css}</style></head><body>${content}</body></html>`;
  const output = path.join(__dirname, "dist", slug);
  fs.mkdirSync(output, { recursive: true });
  fs.writeFileSync(path.join(output, "index.html"), html);
  if (slug === "privacy") fs.writeFileSync(path.join(__dirname, "dist/index.html"), html);
  console.log(`Exported /${slug}/`);
}

const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const Module = require("node:module");
const ts = require("../../web/node_modules/typescript");
const { zodResolver } = require("../../web/node_modules/@hookform/resolvers/zod");

const filename = path.resolve(__dirname, "../../web/src/lib/schemas.ts");
const compiled = ts.transpileModule(fs.readFileSync(filename, "utf8"), {
  compilerOptions: { module: ts.ModuleKind.CommonJS },
}).outputText;
const schemaModule = new Module(filename, module);
schemaModule.filename = filename;
schemaModule.paths = Module._nodeModulePaths(path.dirname(filename));
schemaModule._compile(compiled, filename);
const { loginSchema, createStaffSchema, categorySchema } = schemaModule.exports;
const options = { criteriaMode: "firstError", fields: {}, shouldUseNativeValidation: false };

test("login resolver accepts a valid ID and rejects invalid credentials with field errors", async () => {
  const validate = zodResolver(loginSchema);
  assert.deepEqual(
    (await validate({ email: "RIDER-123", password: "pass1234" }, {}, options)).errors,
    {},
  );
  const invalid = await validate({ email: "!", password: "x" }, {}, options);
  assert.ok(invalid.errors.email);
  assert.ok(invalid.errors.password);
});

test("staff form rejects mismatched passwords and normalizes the login ID", async () => {
  const validate = zodResolver(createStaffSchema);
  const values = {
    display_name: "Test",
    phone: "9000000000",
    role: "delivery",
    login_id: "rider-123",
    password: "pass1234",
    confirm_password: "pass1234",
  };
  const valid = await validate(values, {}, options);
  assert.equal(valid.values.login_id, "RIDER-123");
  const invalid = await validate({ ...values, confirm_password: "different" }, {}, options);
  assert.ok(invalid.errors.confirm_password);
});

test("category form normalizes valid slugs and rejects malformed slugs", async () => {
  const validate = zodResolver(categorySchema);
  const valid = await validate(
    { name: " Tea ", slug: " TEA-1 ", sort_order: 0, active: true },
    {},
    options,
  );
  assert.equal(valid.values.slug, "tea-1");
  assert.ok(
    (await validate({ name: "Tea", slug: "bad slug!", sort_order: 0, active: true }, {}, options))
      .errors.slug,
  );
});

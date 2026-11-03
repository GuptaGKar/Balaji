// Regression gate: anonymous public URLs must never return the uploaded private PNG.
const fs = require("node:fs");
const { randomUUID } = require("node:crypto");
const { createClient } = require("../web/node_modules/@supabase/supabase-js");
const cfg = JSON.parse(fs.readFileSync("/tmp/balaji-local-audit-connection.json", "utf8"));
const actors = JSON.parse(fs.readFileSync("/tmp/balaji-local-identities.json", "utf8"));
if (cfg.url.replace(/\/$/, "") !== "http://127.0.0.1:54321")
  throw new Error("Loopback QA stack required");
const c = createClient(cfg.url, cfg.anon_key, {
  auth: { persistSession: false, autoRefreshToken: false },
});
async function main() {
  const session = await c.auth.setSession({
    access_token: actors.customer.access_token,
    refresh_token: actors.customer.refresh_token,
  });
  if (session.error || !session.data.session) throw new Error("Local customer session unavailable");
  actors.customer.access_token = session.data.session.access_token;
  actors.customer.refresh_token = session.data.session.refresh_token;
  fs.writeFileSync("/tmp/balaji-local-identities.json", JSON.stringify(actors), { mode: 0o600 });
  const results = [];
  const png = Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNgaPj/HwAEggJ/59habAAAAABJRU5ErkJggg==",
    "base64",
  );
  for (const bucket of ["order-images", "order-proofs"]) {
    const path = `${actors.customer.id}/${randomUUID()}.png`;
    const up = await c.storage.from(bucket).upload(path, png, { contentType: "image/png" });
    if (up.error) throw new Error("Synthetic upload failed");
    // Deliberately supply no authorization or API key for this GET.
    const response = await fetch(`${cfg.url}/storage/v1/object/public/${bucket}/${path}`);
    const bytes = Buffer.from(await response.arrayBuffer());
    const leaked = !([400, 401, 403, 404].includes(response.status) && !bytes.equals(png));
    results.push({
      bucket,
      anonymous_http_status: response.status,
      synthetic_bytes_match: bytes.equals(png),
      status: leaked ? "FAIL" : "PASS",
    });
    console.log(
      `${leaked ? "FAIL" : "PASS"}: ${bucket} anonymous GET HTTP ${response.status}; synthetic bytes match=${bytes.equals(png)}`,
    );
  }
  fs.writeFileSync("/tmp/balaji-local-media-privacy.json", JSON.stringify(results, null, 2));
  if (results.some((r) => r.status === "FAIL")) process.exitCode = 1;
}
main()
  .catch(() => {
    console.error("FAIL: local media probe could not complete; credentials omitted");
    process.exitCode = 1;
  })
  .finally(async () => {
    c.auth.stopAutoRefresh();
    await c.realtime.disconnect();
  });

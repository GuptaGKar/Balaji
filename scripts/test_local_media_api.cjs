// Real cookie-authenticated Next route + Storage downloads; synthetic loopback media only.
const fs = require("node:fs");
const { randomUUID } = require("node:crypto");
const { createServerClient } = require("../web/node_modules/@supabase/ssr");
const cfg = JSON.parse(fs.readFileSync("/tmp/balaji-local-audit-connection.json", "utf8"));
const actors = JSON.parse(fs.readFileSync("/tmp/balaji-local-identities.json", "utf8"));
if (cfg.url.replace(/\/$/, "") !== "http://127.0.0.1:54321")
  throw new Error("Loopback QA required");
const clients = {};
const jars = {};
const checks = [];
function check(value, label) {
  if (!value) throw new Error(label);
  checks.push(label);
  console.log(`PASS: ${label}`);
}
async function request(role, bucket, path) {
  return fetch(`http://127.0.0.1:3011/api/order-media?${new URLSearchParams({ bucket, path })}`, {
    headers: { cookie: jars[role] || "" },
    redirect: "manual",
  });
}
async function main() {
  for (const role of ["customer", "manager", "staff", "delivery"]) {
    const jar = new Map();
    const c = (clients[role] = createServerClient(cfg.url, cfg.anon_key, {
      cookies: {
        getAll: () => [...jar].map(([name, value]) => ({ name, value })),
        setAll: (values) => values.forEach((v) => jar.set(v.name, v.value)),
      },
      auth: { autoRefreshToken: false },
    }));
    const login =
      role === "customer"
        ? await c.auth.setSession({
            access_token: actors[role].access_token,
            refresh_token: actors[role].refresh_token,
          })
        : await c.auth.signInWithPassword({
            email: actors[role].email,
            password: actors[role].password,
          });
    if (login.error || !login.data.session) throw new Error("Local SSR session unavailable");
    actors[role].access_token = login.data.session.access_token;
    actors[role].refresh_token = login.data.session.refresh_token;
    jars[role] = [...jar].map(([name, value]) => `${name}=${value}`).join("; ");
  }
  fs.writeFileSync("/tmp/balaji-local-identities.json", JSON.stringify(actors), { mode: 0o600 });
  const png = Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNgaPj/HwAEggJ/59habAAAAABJRU5ErkJggg==",
    "base64",
  );
  const staffPath = `${actors.staff.id}/${randomUUID()}.png`;
  const ownPath = `${actors.customer.id}/${randomUUID()}.png`;
  for (const [role, bucket, path] of [
    ["staff", "order-proofs", staffPath],
    ["customer", "order-images", ownPath],
  ]) {
    const uploaded = await clients[role].storage
      .from(bucket)
      .upload(path, png, { contentType: "image/png" });
    if (uploaded.error) throw new Error("Synthetic media upload failed");
  }
  const anonymous = await request("", "order-images", ownPath);
  check(
    anonymous.status === 307 && new URL(anonymous.headers.get("location")).pathname === "/login",
    "anonymous media route requires login",
  );
  check(
    (await request("staff", "product-images", ownPath)).status === 400,
    "media API rejects unrelated buckets",
  );
  check(
    (await request("staff", "order-images", `${actors.staff.id}/../escape.png`)).status === 400,
    "media API rejects path traversal",
  );
  for (const role of ["staff", "manager"]) {
    const r = await request(role, "order-proofs", staffPath);
    const location = r.headers.get("location");
    check(
      r.status === 307 &&
        location?.startsWith(`${cfg.url}/storage/v1/object/sign/order-proofs/`) &&
        r.headers.get("cache-control") === "private, no-store",
      `${role} receives authorized noncached short-lived media redirect`,
    );
    const download = await fetch(location);
    check(
      download.status === 200 && Buffer.from(await download.arrayBuffer()).equals(png),
      `${role} signed media download returns the uploaded PNG`,
    );
  }
  check(
    (await request("customer", "order-proofs", staffPath)).status === 404,
    "customer cannot sign unrelated staff proof",
  );
  check(
    (await request("delivery", "order-proofs", staffPath)).status === 404,
    "unassigned rider cannot sign known proof path",
  );
  const own = await request("customer", "order-images", ownPath);
  check(own.status === 307, "customer can sign own order image");
  const downloaded = await fetch(own.headers.get("location"));
  check(
    downloaded.status === 200 && Buffer.from(await downloaded.arrayBuffer()).equals(png),
    "customer signed download matches own PNG",
  );
  const life = JSON.parse(fs.readFileSync("/tmp/balaji-local-lifecycle-results.json", "utf8"));
  const proofs = await clients.customer
    .from("order_proofs")
    .select("path")
    .eq("order_id", life.order_id)
    .eq("stage", "dispatch")
    .single();
  if (proofs.error) throw new Error("Customer lifecycle proof lookup failed");
  check(
    (await request("customer", "order-proofs", proofs.data.path)).status === 307,
    "customer can sign proof of own delivered order",
  );
  fs.writeFileSync(
    "/tmp/balaji-local-media-api-results.json",
    JSON.stringify({ status: "PASS", checks }, null, 2),
  );
}
main()
  .catch((error) => {
    console.error(
      `FAIL: ${String(error.message).replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, "[JWT redacted]")}`,
    );
    process.exitCode = 1;
  })
  .finally(async () => {
    for (const c of Object.values(clients)) {
      c.auth.stopAutoRefresh();
      await c.realtime.disconnect();
    }
  });

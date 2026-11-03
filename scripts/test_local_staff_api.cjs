// Exercise the real Next.js route with SSR Auth cookies and local synthetic accounts.
const fs = require("node:fs");
const { randomUUID } = require("node:crypto");
const { spawnSync } = require("node:child_process");
const { createServerClient } = require("../web/node_modules/@supabase/ssr");
const { createClient } = require("../web/node_modules/@supabase/supabase-js");
const cfg = JSON.parse(fs.readFileSync("/tmp/balaji-local-audit-connection.json", "utf8"));
const actors = JSON.parse(fs.readFileSync("/tmp/balaji-local-identities.json", "utf8"));
const base = "http://127.0.0.1:3011";
if (cfg.url.replace(/\/$/, "") !== "http://127.0.0.1:54321")
  throw new Error("Loopback QA stack required");
const results = [];
function check(condition, label) {
  if (!condition) throw new Error(label);
  results.push(label);
  console.log(`PASS: ${label}`);
}
async function cookies(role) {
  const jar = new Map();
  const c = createServerClient(cfg.url, cfg.anon_key, {
    cookies: {
      getAll: () => [...jar].map(([name, value]) => ({ name, value })),
      setAll: (values) => values.forEach((v) => jar.set(v.name, v.value)),
    },
    auth: { autoRefreshToken: false },
  });
  const result = await c.auth.signInWithPassword({
    email: actors[role].email,
    password: actors[role].password,
  });
  if (result.error) throw new Error("Local SSR login failed");
  c.auth.stopAutoRefresh();
  return [...jar].map(([name, value]) => `${name}=${value}`).join("; ");
}
async function post(cookie, data) {
  const r = await fetch(`${base}/api/staff`, {
    method: "POST",
    redirect: "manual",
    headers: { "Content-Type": "application/json", cookie },
    body: JSON.stringify(data),
  });
  let body = {};
  if (r.headers.get("content-type")?.includes("application/json")) body = await r.json();
  return { status: r.status, body };
}
function fixture(sql) {
  const r = spawnSync(
    "docker",
    [
      "exec",
      "-i",
      "supabase_db_balaji-store",
      "psql",
      "-U",
      "postgres",
      "-d",
      "postgres",
      "-v",
      "ON_ERROR_STOP=1",
    ],
    { input: sql, encoding: "utf8" },
  );
  if (r.status !== 0) throw new Error("Local fixture update failed");
}
async function main() {
  const manager = await cookies("manager");
  const staff = await cookies("staff");
  const rider = await cookies("delivery");
  check(
    (await post("", { action: "unknown" })).status === 307,
    "anonymous API request redirects to login",
  );
  check(
    (await post(staff, { action: "unknown" })).status === 403,
    "staff denied manager provisioning API",
  );
  check(
    (await post(rider, { action: "unknown" })).status === 403,
    "rider denied manager provisioning API",
  );
  const elevated = await post(manager, {
    action: "create",
    login_id: `QA-ROLE-${randomUUID().slice(0, 8)}`,
    display_name: "Synthetic invalid role",
    phone: "9000000002",
    role: "manager",
    password: `Synthetic-${randomUUID()}`,
  });
  check(
    elevated.status === 400 && elevated.body.error === "Invalid role",
    "manager cannot create a manager via staff API",
  );
  const login = `QA-WEB-${randomUUID().slice(0, 8).toUpperCase()}`;
  const password = `Synthetic-${randomUUID()}`;
  const created = await post(manager, {
    action: "create",
    login_id: login,
    display_name: "Synthetic Web Rider",
    phone: "9000000002",
    role: "delivery",
    password,
  });
  check(
    created.status === 201 && created.body.success === true,
    "manager creates real local rider account",
  );
  const c = createClient(cfg.url, cfg.anon_key, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const signed = await c.auth.signInWithPassword({
    email: `${login.toLowerCase()}@riders.invalid`,
    password,
  });
  check(!signed.error && !!signed.data.user, "newly provisioned rider can authenticate");
  const id = signed.data.user.id;
  const profile = await c.from("profiles").select("role,login_id").eq("id", id).single();
  check(
    !profile.error && profile.data.role === "delivery" && profile.data.login_id === login,
    "provisioning creates correct rider profile",
  );
  const reset = await post(manager, { action: "reset_password", user_id: id });
  check(
    reset.status === 200 && typeof reset.body.tempPassword === "string",
    "manager reset returns a real temporary credential (omitted from logs)",
  );
  const reauth = await c.auth.signInWithPassword({
    email: `${login.toLowerCase()}@riders.invalid`,
    password: reset.body.tempPassword,
  });
  check(!reauth.error, "reset credential authenticates against local Auth");
  const flagged = await c.from("profiles").select("must_change_password").eq("id", id).single();
  check(
    !flagged.error && flagged.data.must_change_password === true,
    "password reset requires password change",
  );
  // Existing JWT remains usable after deactivation: the API itself must validate activity.
  fixture(`UPDATE public.profiles SET is_active=false WHERE id='${actors.manager.id}';`);
  try {
    check(
      (
        await post(manager, {
          action: "create",
          login_id: `QA-DENY-${randomUUID().slice(0, 8)}`,
          display_name: "Must not be created",
          phone: "9000000003",
          role: "delivery",
          password: `Synthetic-${randomUUID()}`,
        })
      ).status === 403,
      "inactive manager denied privileged API even with an existing JWT",
    );
  } finally {
    fixture(`UPDATE public.profiles SET is_active=true WHERE id='${actors.manager.id}';`);
  }
  fixture(`UPDATE public.profiles SET must_change_password=true WHERE id='${actors.manager.id}';`);
  try {
    check(
      (
        await post(manager, {
          action: "create",
          login_id: `QA-FORCE-${randomUUID().slice(0, 8)}`,
          display_name: "Must not be created",
          phone: "9000000003",
          role: "delivery",
          password: `Synthetic-${randomUUID()}`,
        })
      ).status === 403,
      "manager requiring password change denied privileged API",
    );
  } finally {
    fixture(
      `UPDATE public.profiles SET must_change_password=false WHERE id='${actors.manager.id}';`,
    );
  }
  c.auth.stopAutoRefresh();
  await c.realtime.disconnect();
  fs.writeFileSync(
    "/tmp/balaji-local-staff-api-results.json",
    JSON.stringify({ status: "PASS", checks: results }, null, 2),
  );
}
main().catch((error) => {
  console.error(
    `FAIL: ${String(error.message).replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, "[JWT redacted]")}`,
  );
  process.exitCode = 1;
});

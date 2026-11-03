// Exercise the real subscription used by the order queue, against local QA data only.
const fs = require("node:fs");
const { randomUUID } = require("node:crypto");
const { spawnSync } = require("node:child_process");
const { createClient } = require("../web/node_modules/@supabase/supabase-js");
const cfg = JSON.parse(fs.readFileSync("/tmp/balaji-local-audit-connection.json", "utf8"));
const actors = JSON.parse(fs.readFileSync("/tmp/balaji-local-identities.json", "utf8"));
if (cfg.url.replace(/\/$/, "") !== "http://127.0.0.1:54321") {
  throw new Error("This integration check requires the isolated loopback stack");
}
const client = createClient(cfg.url, cfg.anon_key, {
  auth: { persistSession: false, autoRefreshToken: false },
});
const owner = createClient(cfg.url, cfg.anon_key, {
  auth: { persistSession: false, autoRefreshToken: false },
});
let channel;
async function main() {
  const login = await client.auth.signInWithPassword({
    email: actors.manager.email,
    password: actors.manager.password,
  });
  if (login.error) throw new Error(`Local manager sign-in failed: ${login.error.code}`);
  const session = await owner.auth.setSession({
    access_token: actors.customer.access_token,
    refresh_token: actors.customer.refresh_token,
  });
  if (session.error || !session.data.session)
    throw new Error("Local customer session is unavailable");
  actors.customer.access_token = session.data.session.access_token;
  actors.customer.refresh_token = session.data.session.refresh_token;
  fs.writeFileSync("/tmp/balaji-local-identities.json", JSON.stringify(actors), { mode: 0o600 });
  const product = randomUUID();
  const setup = spawnSync(
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
    {
      input: `INSERT INTO public.products(id,sku,name,quantity_type,price_paise,price_basis_qty,unit_label,step_qty,min_qty,stock_qty) VALUES('${product}','QA-RT-${product}','Synthetic Realtime pack','PACKED',100,1,'pack',1,1,1);`,
      encoding: "utf8",
    },
  );
  if (setup.status !== 0) throw new Error("Local synthetic product setup failed");
  const placed = await owner.rpc("place_order", {
    p_payload: {
      items: [{ product_id: product, qty_base: 1 }],
      address: {
        recipient_name: "Synthetic Realtime customer",
        phone: "9000000001",
        address_lines: "1 Synthetic Test Road",
        postal_code: "122001",
      },
      payment_method: "cod",
    },
    p_idempotency_key: `QA-RT-${randomUUID()}`,
  });
  if (placed.error || !placed.data?.order_id) throw new Error("Local synthetic checkout failed");
  const order = { id: placed.data.order_id };
  const events = [];
  const systems = [];
  channel = client
    .channel(`audit-orders-${Date.now()}`)
    .on("system", {}, (payload) => {
      systems.push(payload);
      console.log(
        `Postgres subscription system: ${payload.status || "unknown"} ${payload.extension || "unknown"}`,
      );
    })
    .on(
      "postgres_changes",
      {
        event: "UPDATE",
        schema: "public",
        table: "orders",
        filter: `id=eq.${order.id}`,
      },
      (payload) => events.push({ event: payload.eventType, status: payload.new.status }),
    );
  await new Promise((resolve, reject) => {
    const timeout = setTimeout(
      () => reject(new Error("Subscription acknowledgement timeout")),
      12000,
    );
    channel.subscribe((status, error) => {
      console.log(`Subscription status: ${status}`);
      if (status === "SUBSCRIBED") {
        clearTimeout(timeout);
        resolve();
      }
      if (status === "CHANNEL_ERROR" || status === "TIMED_OUT") {
        clearTimeout(timeout);
        reject(new Error(`Subscription failed: ${error?.message || status}`));
      }
    });
  });
  const readyDeadline = Date.now() + 12000;
  while (
    !systems.some((event) => event.status === "ok" && event.extension === "postgres_changes") &&
    Date.now() < readyDeadline
  ) {
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  if (!systems.some((event) => event.status === "ok" && event.extension === "postgres_changes")) {
    throw new Error("Channel joined but PostgreSQL subscription did not become ready");
  }
  const transition = await client.rpc("transition_order", {
    p_order_id: order.id,
    p_to_status: "accepted",
    p_reason: "Synthetic Realtime audit",
  });
  if (transition.error || transition.data?.error) throw new Error("Synthetic transition failed");
  const deadline = Date.now() + 10000;
  while (!events.length && Date.now() < deadline)
    await new Promise((resolve) => setTimeout(resolve, 100));
  if (events.length !== 1 || events[0].status !== "accepted") {
    throw new Error(`Expected one accepted event; received ${events.length}`);
  }
  console.log("PASS: authenticated order subscription received the committed status change once");
}
main()
  .catch((error) => {
    const message = String(error.message).replace(
      /eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g,
      "[JWT redacted]",
    );
    console.error(`FAIL: ${message}`);
    process.exitCode = 1;
  })
  .finally(async () => {
    if (channel) await client.removeChannel(channel);
    await client.realtime.disconnect();
    await owner.realtime.disconnect();
    client.auth.stopAutoRefresh();
    owner.auth.stopAutoRefresh();
  });

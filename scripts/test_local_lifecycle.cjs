// Authenticated API integration against synthetic loopback data only. Never use cloud credentials.
const fs = require("node:fs");
const { randomUUID } = require("node:crypto");
const { spawnSync } = require("node:child_process");
const { createClient } = require("../web/node_modules/@supabase/supabase-js");
const cfg = JSON.parse(fs.readFileSync("/tmp/balaji-local-audit-connection.json", "utf8"));
const actors = JSON.parse(fs.readFileSync("/tmp/balaji-local-identities.json", "utf8"));
if (cfg.url.replace(/\/$/, "") !== "http://127.0.0.1:54321")
  throw new Error("Loopback QA stack required");
const clients = {};
const results = [];
function requireCheck(value, label) {
  if (!value) throw new Error(label);
  results.push(label);
  console.log(`PASS: ${label}`);
}
async function rpc(role, name, args) {
  const result = await clients[role].rpc(name, args);
  if (result.error || result.data?.error)
    throw new Error(`${name} failed (${result.error?.code || result.data.error})`);
  return result.data;
}
async function main() {
  for (const role of ["customer", "manager", "staff", "delivery"]) {
    const c = (clients[role] = createClient(cfg.url, cfg.anon_key, {
      auth: { persistSession: false, autoRefreshToken: false },
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
    if (login.error || !login.data.session) throw new Error(`Local ${role} authentication failed`);
    actors[role].access_token = login.data.session.access_token;
    actors[role].refresh_token = login.data.session.refresh_token;
  }
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
      input: `INSERT INTO public.products(id,sku,name,quantity_type,price_paise,price_basis_qty,unit_label,step_qty,min_qty,stock_qty) VALUES('${product}','QA-LIFE-${product}','Synthetic lifecycle pack','PACKED',1000,1,'pack',1,1,5);`,
      encoding: "utf8",
    },
  );
  if (setup.status !== 0) throw new Error("Synthetic fixture setup failed");
  const items = [{ product_id: product, qty_base: 2, unit_price_paise: 1 }];
  const quote = await rpc("customer", "quote_cart", { p_items: items, p_pin_code: "122001" });
  const key = `QA-LIFE-${randomUUID()}`;
  const args = {
    p_payload: {
      items,
      address: {
        recipient_name: "Synthetic Lifecycle Customer",
        phone: "9000000001",
        address_lines: "1 Synthetic Test Road",
        postal_code: "122001",
      },
      payment_method: "cod",
      grand_total_paise: 1,
      delivery_fee_paise: 0,
    },
    p_idempotency_key: key,
  };
  const placed = await rpc("customer", "place_order", args);
  const order = placed.order_id;
  requireCheck(
    !!order && placed.breakdown.item_subtotal_paise === 2000,
    "checkout ignores supplied client price and total",
  );
  const again = await rpc("customer", "place_order", args);
  requireCheck(again.order_id === order, "idempotent checkout returns the same order");
  const stock = await clients.manager
    .from("products")
    .select("stock_qty")
    .eq("id", product)
    .single();
  requireCheck(
    !stock.error && stock.data.stock_qty === 3,
    "duplicate checkout deducts stock only once",
  );
  const patch = await clients.staff.from("orders").update({ status: "cancelled" }).eq("id", order);
  requireCheck(
    patch.error?.code === "42501",
    "direct staff order UPDATE is denied by database privilege",
  );
  const changed = await clients.manager
    .from("products")
    .update({ price_paise: 1700 })
    .eq("id", product);
  requireCheck(!changed.error, "manager price edit accepted");
  const snapshot = await clients.customer
    .from("order_items")
    .select("unit_price_paise,line_total_paise")
    .eq("order_id", order);
  requireCheck(
    !snapshot.error &&
      snapshot.data.length === 1 &&
      snapshot.data[0].unit_price_paise === 1000 &&
      snapshot.data[0].line_total_paise === 2000,
    "existing order preserves its original price snapshot",
  );
  for (const status of ["accepted", "preparing", "ready_for_dispatch"]) {
    const t = await rpc("staff", "transition_order", {
      p_order_id: order,
      p_to_status: status,
      p_reason: "Synthetic integration",
    });
    requireCheck(t.to_status === status, `staff transition ${status} persisted`);
  }
  await rpc("manager", "assign_order", {
    p_order_id: order,
    p_delivery_user_id: actors.delivery.id,
  });
  const assigned = await rpc("delivery", "get_rider_orders", {});
  requireCheck(
    assigned.some((row) => row.id === order),
    "rider receives assigned order",
  );
  // Minimal synthetic PNG, no camera or customer photograph.
  const png = Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNgaPj/HwAEggJ/59habAAAAABJRU5ErkJggg==",
    "base64",
  );
  for (const [role, stage, status] of [
    ["staff", "dispatch", "out_for_delivery"],
    ["delivery", "delivery", "delivered"],
  ]) {
    const path = `${actors[role].id}/${randomUUID()}.png`;
    const upload = await clients[role].storage
      .from("order-proofs")
      .upload(path, png, { contentType: "image/png", upsert: false });
    requireCheck(!upload.error, `real Storage ${stage} PNG upload succeeds`);
    await rpc(role, "attach_order_proof", {
      p_order_id: order,
      p_stage: stage,
      p_kind: "image",
      p_path: path,
    });
    const t = await rpc("delivery", "rider_update_status", {
      p_order_id: order,
      p_to_status: status,
      p_note: "Synthetic integration",
    });
    requireCheck(t.to_status === status, `rider transition ${status} accepts uploaded proof`);
  }
  const final = await clients.customer
    .from("orders")
    .select("status,payment_status,cod_collected_paise,grand_total_paise")
    .eq("id", order)
    .single();
  requireCheck(
    !final.error &&
      final.data.status === "delivered" &&
      final.data.payment_status === "paid" &&
      final.data.cod_collected_paise === placed.breakdown.grand_total_paise,
    "customer reads delivered status and server-authoritative COD amount",
  );
  const bill = await rpc("staff", "issue_bill_for_order", { p_order_id: order });
  const secondBill = await rpc("manager", "issue_bill_for_order", { p_order_id: order });
  requireCheck(
    !!bill.bill?.id && secondBill.bill.id === bill.bill.id,
    "bill issuance is idempotent across staff and manager",
  );
  const ownBill = await rpc("customer", "get_bill", { p_bill_id: bill.bill.id });
  requireCheck(
    ownBill.bill.grand_total_paise === placed.breakdown.grand_total_paise &&
      ownBill.items[0].unit_price_paise === 1000,
    "customer bill agrees with original checkout and item snapshot",
  );
  const reconcile = await rpc("manager", "check_stock_reconciliation", {});
  requireCheck(
    !reconcile.some((row) => row.product_id === product),
    "delivered-order inventory reconciles",
  );
  fs.writeFileSync(
    "/tmp/balaji-local-lifecycle-results.json",
    JSON.stringify(
      {
        status: "PASS",
        checks: results,
        product_id: product,
        order_id: order,
        quote_fields: Object.keys(quote),
      },
      null,
      2,
    ),
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

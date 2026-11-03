// Observe the phone-created order in the actual management page. "after" never reloads.
const fs = require("node:fs");
const { connect } = require("./local_browser_qa.cjs");
(async () => {
  const context = JSON.parse(fs.readFileSync("/tmp/balaji-device-order-context.json", "utf8"));
  const after = process.argv[2] === "after";
  const expected = after ? "delivered" : "out for delivery";
  const b = await connect();
  try {
    if (!after) {
      await b.login();
      await b.navigate("/orders");
    }
    const rows = `[...document.querySelectorAll('tbody tr')].filter(r=>r.textContent.includes(${JSON.stringify(context.public_order_number)}))`;
    const status = `${rows}.length===1 && [...${rows}[0].querySelectorAll('*')].some(n=>n.textContent.trim().toLowerCase().replaceAll('_',' ').replace(/\\s+/g,' ')===${JSON.stringify(expected)})`;
    await b.wait(`location.pathname==='/orders' && (${status})`, 20000);
    const result = await b.evaluate(
      `(()=>{const r=${rows}[0];return {matching_rows:${rows}.length,expected_status_present:(${status}),total_190:/₹\\s*190(?:\\.00)?/.test(r.textContent)}})()`,
    );
    if (!after) {
      const deadline = Date.now() + 12000;
      while (!b.realtime.ready && Date.now() < deadline)
        await new Promise((r) => setTimeout(r, 100));
    }
    result.realtime_ready_observed = b.realtime.ready > 0;
    result.javascript_exceptions = b.exceptions.length;
    result.reload_performed = !after;
    if (!result.total_190 || result.javascript_exceptions)
      throw Error("Browser order consistency failed");
    fs.writeFileSync(
      `/tmp/balaji-browser-qa/order-${after ? "after" : "before"}.json`,
      JSON.stringify({ status: "PASS", result }, null, 2),
    );
    console.log(`PASS: management browser ${expected}; total 19000 paise`, JSON.stringify(result));
  } finally {
    await b.close();
  }
})().catch((error) => {
  console.error("FAIL: " + error.message);
  process.exitCode = 1;
});

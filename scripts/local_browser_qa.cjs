// CDP driver for the dedicated temporary QA browser. Reject all external requests.
const fs = require("node:fs");
const { createServerClient } = require("../web/node_modules/@supabase/ssr");
const cfg = JSON.parse(fs.readFileSync("/tmp/balaji-local-audit-connection.json", "utf8"));
if (cfg.url.replace(/\/$/, "") !== "http://127.0.0.1:54321")
  throw new Error("Loopback QA required");
async function connect() {
  const targets = await (await fetch("http://127.0.0.1:9229/json/list")).json();
  const target = targets.find(
    (t) =>
      t.type === "page" && (t.url === "about:blank" || t.url.startsWith("http://127.0.0.1:3011/")),
  );
  if (!target?.webSocketDebuggerUrl?.startsWith("ws://127.0.0.1:9229/"))
    throw new Error("Dedicated QA browser target unavailable");
  const ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => {
    ws.addEventListener("open", resolve, { once: true });
    ws.addEventListener("error", () => reject(new Error("QA browser connection failed")), {
      once: true,
    });
  });
  let id = 0;
  const pending = new Map();
  const exceptions = [];
  const blocked = [];
  const requests = [];
  const interceptionErrors = [];
  const realtime = { ready: 0, changes: 0, nonJson: 0 };
  const send = (method, params = {}) =>
    new Promise((resolve, reject) => {
      const next = ++id;
      const timer = setTimeout(() => {
        pending.delete(next);
        reject(new Error(`Browser operation timed out: ${method}`));
      }, 15000);
      pending.set(next, { resolve, reject, timer });
      ws.send(JSON.stringify({ id: next, method, params }));
    });
  ws.addEventListener("message", (event) => {
    const m = JSON.parse(String(event.data));
    if (m.id) {
      const p = pending.get(m.id);
      if (p) {
        clearTimeout(p.timer);
        pending.delete(m.id);
        m.error
          ? p.reject(new Error(`Browser operation failed: ${m.error.code}`))
          : p.resolve(m.result);
      }
      return;
    }
    if (m.method === "Runtime.exceptionThrown")
      exceptions.push(
        m.params.exceptionDetails.exception?.className || m.params.exceptionDetails.text,
      );
    if (m.method === "Network.webSocketFrameReceived" && m.params.response.opcode === 1) {
      try {
        const data = JSON.parse(m.params.response.payloadData);
        const event = Array.isArray(data) ? data[3] : data.event;
        const payload = Array.isArray(data) ? data[4] : data.payload;
        if (
          event === "system" &&
          payload?.extension === "postgres_changes" &&
          payload?.status === "ok"
        )
          realtime.ready++;
        if (event === "postgres_changes") realtime.changes++;
      } catch {
        realtime.nonJson++;
      }
    }
    if (m.method === "Fetch.requestPaused") {
      const p = m.params;
      let local = false;
      try {
        const u = new URL(p.request.url);
        local =
          ["127.0.0.1", "localhost"].includes(u.hostname) ||
          ["data:", "blob:", "about:"].includes(u.protocol);
        if (local) requests.push({ method: p.request.method, path: u.pathname });
      } catch {
        blocked.push("invalid request URL");
      }
      if (!local) blocked.push("external request blocked");
      send(
        local ? "Fetch.continueRequest" : "Fetch.failRequest",
        local
          ? { requestId: p.requestId }
          : { requestId: p.requestId, errorReason: "BlockedByClient" },
      ).catch(() => {
        interceptionErrors.push("interception request could not complete");
      });
    }
  });
  await send("Page.enable");
  await send("Runtime.enable");
  await send("Network.enable");
  await send("Fetch.enable", { patterns: [{ urlPattern: "*", requestStage: "Request" }] });
  const evaluate = async (expression) => {
    const result = await send("Runtime.evaluate", {
      expression,
      returnByValue: true,
      awaitPromise: true,
    });
    if (result.exceptionDetails)
      throw new Error("QA page evaluation failed; private values omitted");
    return result.result.value;
  };
  const wait = async (expression, timeout = 20000) => {
    const deadline = Date.now() + timeout;
    while (Date.now() < deadline) {
      if (await evaluate(expression)) return;
      await new Promise((r) => setTimeout(r, 150));
    }
    throw new Error("QA page did not reach the expected state");
  };
  const login = async (role = "manager") => {
    const actors = JSON.parse(fs.readFileSync("/tmp/balaji-local-identities.json", "utf8"));
    const jar = new Map();
    const auth = createServerClient(cfg.url, cfg.anon_key, {
      auth: { autoRefreshToken: false },
      cookies: {
        getAll: () => [...jar.values()].map((c) => ({ name: c.name, value: c.value })),
        setAll: (values) => values.forEach((c) => jar.set(c.name, c)),
      },
    });
    const signed = await auth.auth.signInWithPassword({
      email: actors[role].email,
      password: actors[role].password,
    });
    auth.auth.stopAutoRefresh();
    if (signed.error || !jar.size) throw new Error("Local browser Auth failed");
    await send("Network.clearBrowserCookies");
    await send("Network.setCookies", {
      cookies: [...jar.values()].map((c) => ({
        name: c.name,
        value: c.value,
        domain: "127.0.0.1",
        path: "/",
        secure: false,
        httpOnly: !!c.options?.httpOnly,
        sameSite: "Lax",
      })),
    });
  };
  const navigate = async (route) => {
    if (!route.startsWith("/") || route.startsWith("//")) throw new Error("Local route required");
    await send("Page.navigate", { url: "http://127.0.0.1:3011" + route });
    await wait('document.readyState === "complete"');
  };
  const screenshot = async (file) => {
    const r = await send("Page.captureScreenshot", { format: "png" });
    fs.writeFileSync(file, Buffer.from(r.data, "base64"), { mode: 0o600 });
  };
  const close = async () => {
    try {
      await send("Fetch.disable");
    } finally {
      ws.close();
    }
  };
  return {
    send,
    evaluate,
    wait,
    login,
    navigate,
    screenshot,
    close,
    exceptions,
    blocked,
    requests,
    interceptionErrors,
    realtime,
  };
}
module.exports = { connect };
if (require.main === module) {
  (async () => {
    const b = await connect();
    try {
      await b.send("Emulation.setDeviceMetricsOverride", {
        width: 1280,
        height: 900,
        deviceScaleFactor: 1,
        mobile: false,
      });
      await b.login();
      await b.navigate("/catalogue");
      await b.wait(
        'location.pathname === "/catalogue" && document.body.innerText.includes("Assam CTC Tea 250 g")',
      );
      const summary = await b.evaluate(
        '({path:location.pathname,title:document.title,headings:[...document.querySelectorAll("h1,h2")].map(n=>n.textContent.trim()),rows:document.querySelectorAll("tbody tr").length,buttons:[...document.querySelectorAll("button")].filter(n=>n.getBoundingClientRect().width).map(n=>n.textContent.trim()||n.getAttribute("aria-label")||"[unnamed]").slice(0,24)})',
      );
      if (!summary.rows || b.exceptions.length)
        throw new Error("Catalogue rendering or JavaScript check failed");
      await b.screenshot("/tmp/balaji-browser-qa/catalogue-desktop.png");
      console.log("Authenticated local catalogue:", JSON.stringify(summary));
      console.log(
        "Unhandled JS exceptions:",
        b.exceptions.length,
        "External requests blocked:",
        b.blocked.length,
      );
      fs.writeFileSync(
        "/tmp/balaji-browser-qa/catalogue-result.json",
        JSON.stringify(
          {
            status: "PASS",
            summary,
            exceptions: b.exceptions,
            blocked_count: b.blocked.length,
            requests: b.requests,
          },
          null,
          2,
        ),
      );
    } finally {
      await b.close();
    }
  })().catch((error) => {
    const message = String(error.message)
      .replace(/eyJ[\w-]+\.[\w-]+\.[\w-]+/g, "[JWT redacted]")
      .replace(/https?:\/\/\S+/g, "[URL omitted]");
    console.error("FAIL: isolated browser check: " + message);
    process.exitCode = 1;
  });
}

import assert from "node:assert/strict";
import test from "node:test";

import { createSmsHookHandler } from "./handler.ts";

const validEvent = {
  user: { phone: "+919876543210" },
  sms: { otp: "123456" },
};

const baseEnv = {
  SMS_PROVIDER: "gateway",
  SMS_GATEWAY_URL: "https://gateway.example/",
  SMS_GATEWAY_API_KEY: "gateway-key",
};

const twoFactorEnv = {
  SMS_PROVIDER: "2factor",
  TWOFACTOR_API_KEY: "tf-key",
  TWOFACTOR_TEMPLATE_NAME: "BalajiOTP",
};

function request(event: unknown = validEvent, method = "POST"): Request {
  return new Request("https://hook.example", {
    method,
    body: method === "POST" ? JSON.stringify(event) : undefined,
  });
}

function gatewayResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

test("forwards a signed Indian OTP request to the SIM gateway", async () => {
  let capturedUrl = "";
  let capturedInit: RequestInit | undefined;

  const handler = createSmsHookHandler({
    env: baseEnv,
    verify: (payload) => JSON.parse(payload),
    generateRequestId: () => "request-1",
    fetchImpl: async (url, init) => {
      capturedUrl = String(url);
      capturedInit = init;
      return gatewayResponse({ success: true });
    },
  });

  const response = await handler(request());

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {});
  assert.equal(capturedUrl, "https://gateway.example/api/send");
  assert.equal(capturedInit?.method, "POST");

  const headers = new Headers(capturedInit?.headers);
  assert.equal(headers.get("x-api-key"), "gateway-key");
  assert.equal(headers.get("x-request-id"), "request-1");

  assert.deepEqual(JSON.parse(String(capturedInit?.body)), {
    phone_number: "+919876543210",
    message: "Balaji Provision Store OTP: 123456. Do not share this code.",
    request_id: "request-1",
  });
});

test("adds the country code when Supabase omits it", async () => {
  let body: Record<string, unknown> = {};

  const handler = createSmsHookHandler({
    env: baseEnv,
    verify: (payload) => JSON.parse(payload),
    generateRequestId: () => "request-2",
    fetchImpl: async (_url, init) => {
      body = JSON.parse(String(init?.body));
      return gatewayResponse({ success: true });
    },
  });

  const response = await handler(
    request({
      user: { phone: "919876543210" },
      sms: { otp: "654321" },
    }),
  );

  assert.equal(response.status, 200);
  assert.equal(body.phone_number, "+919876543210");
});

test("uses sms.phone when user.phone is absent", async () => {
  let body: Record<string, unknown> = {};

  const handler = createSmsHookHandler({
    env: baseEnv,
    verify: (payload) => JSON.parse(payload),
    generateRequestId: () => "request-3",
    fetchImpl: async (_url, init) => {
      body = JSON.parse(String(init?.body));
      return gatewayResponse({ success: true });
    },
  });

  const response = await handler(
    request({
      user: {},
      sms: { phone: "+919876543210", otp: "111111" },
    }),
  );

  assert.equal(response.status, 200);
  assert.equal(body.phone_number, "+919876543210");
});

test("adds Cloudflare Access service-token headers when configured", async () => {
  let headers: Headers | undefined;

  const handler = createSmsHookHandler({
    env: {
      ...baseEnv,
      SMS_GATEWAY_CF_ACCESS_CLIENT_ID: "cf-id",
      SMS_GATEWAY_CF_ACCESS_CLIENT_SECRET: "cf-secret",
    },
    verify: (payload) => JSON.parse(payload),
    fetchImpl: async (_url, init) => {
      headers = new Headers(init?.headers);
      return gatewayResponse({ success: true });
    },
  });

  const response = await handler(request());

  assert.equal(response.status, 200);
  assert.equal(headers?.get("cf-access-client-id"), "cf-id");
  assert.equal(headers?.get("cf-access-client-secret"), "cf-secret");
});

test("sends the Supabase OTP through 2Factor", async () => {
  let capturedUrl = "";
  let capturedInit: RequestInit | undefined;

  const handler = createSmsHookHandler({
    env: twoFactorEnv,
    verify: (payload) => JSON.parse(payload),
    fetchImpl: async (url, init) => {
      capturedUrl = String(url);
      capturedInit = init;
      return gatewayResponse({ Status: "Success", Details: "session-1" });
    },
  });

  const response = await handler(request());

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {});
  assert.equal(capturedUrl, "https://2factor.in/API/V1/tf-key/SMS/9876543210/123456/BalajiOTP");
  assert.equal(capturedInit?.method, "POST");
  assert.equal(capturedInit?.body, undefined);
});

test("defaults to 2Factor when SMS_PROVIDER is omitted", async () => {
  let capturedUrl = "";

  const handler = createSmsHookHandler({
    env: { TWOFACTOR_API_KEY: "tf-key", TWOFACTOR_TEMPLATE_NAME: "BalajiOTP" },
    verify: (payload) => JSON.parse(payload),
    fetchImpl: async (url) => {
      capturedUrl = String(url);
      return gatewayResponse({ Status: "Success" });
    },
  });

  const response = await handler(request());

  assert.equal(response.status, 200);
  assert.equal(capturedUrl, "https://2factor.in/API/V1/tf-key/SMS/9876543210/123456/BalajiOTP");
});

test("omits the 2Factor template segment when no template is configured", async () => {
  let capturedUrl = "";

  const handler = createSmsHookHandler({
    env: { SMS_PROVIDER: "2factor", TWOFACTOR_API_KEY: "tf-key" },
    verify: (payload) => JSON.parse(payload),
    fetchImpl: async (url) => {
      capturedUrl = String(url);
      return gatewayResponse({ Status: "Success" });
    },
  });

  const response = await handler(request());

  assert.equal(response.status, 200);
  assert.equal(capturedUrl, "https://2factor.in/API/V1/tf-key/SMS/9876543210/123456");
});

test("rejects a 2Factor error response without leaking the OTP", async () => {
  const logs: unknown[][] = [];
  const originalError = console.error;
  console.error = (...args: unknown[]) => {
    logs.push(args);
  };

  try {
    const handler = createSmsHookHandler({
      env: twoFactorEnv,
      verify: (payload) => JSON.parse(payload),
      fetchImpl: async () => gatewayResponse({ Status: "Error", Details: "invalid template" }),
    });

    const response = await handler(request());
    const body = await response.json();

    assert.equal(response.status, 502);
    assert.deepEqual(body, {
      error: {
        http_code: 502,
        message: "SMS provider rejected the message",
      },
    });
    assert.equal(JSON.stringify(logs).includes("123456"), false);
    assert.equal(JSON.stringify(logs).includes("tf-key"), false);
    assert.equal(JSON.stringify(body).includes("123456"), false);
    assert.equal(JSON.stringify(body).includes("tf-key"), false);
  } finally {
    console.error = originalError;
  }
});

test("fails closed when the selected 2Factor provider has no API key", async () => {
  let fetched = false;

  const handler = createSmsHookHandler({
    env: { SMS_PROVIDER: "2factor" },
    verify: (payload) => JSON.parse(payload),
    fetchImpl: async () => {
      fetched = true;
      return gatewayResponse({ Status: "Success" });
    },
  });

  const response = await handler(request());

  assert.equal(response.status, 500);
  assert.equal(fetched, false);
});

test("rejects an unknown SMS provider", async () => {
  let fetched = false;

  const handler = createSmsHookHandler({
    env: { ...baseEnv, SMS_PROVIDER: "unknown" },
    verify: (payload) => JSON.parse(payload),
    fetchImpl: async () => {
      fetched = true;
      return gatewayResponse({ success: true });
    },
  });

  const response = await handler(request());

  assert.equal(response.status, 500);
  assert.equal(fetched, false);
});

test("rejects non-POST requests", async () => {
  const handler = createSmsHookHandler({
    env: baseEnv,
    verify: (payload) => JSON.parse(payload),
    fetchImpl: async () => gatewayResponse({ success: true }),
  });

  const response = await handler(request(validEvent, "GET"));

  assert.equal(response.status, 405);
});

test("rejects invalid webhook signatures", async () => {
  let fetched = false;

  const handler = createSmsHookHandler({
    env: baseEnv,
    verify: () => {
      throw new Error("bad signature");
    },
    fetchImpl: async () => {
      fetched = true;
      return gatewayResponse({ success: true });
    },
  });

  const response = await handler(request());

  assert.equal(response.status, 401);
  assert.equal(fetched, false);
});

test("rejects missing or invalid phone numbers and OTPs", async () => {
  const handler = createSmsHookHandler({
    env: baseEnv,
    verify: (payload) => JSON.parse(payload),
    fetchImpl: async () => gatewayResponse({ success: true }),
  });

  const invalidEvents = [
    { user: { phone: "+919876543210" }, sms: {} },
    { user: { phone: "+91987654321" }, sms: { otp: "123456" } },
    { user: { phone: "+15551234567" }, sms: { otp: "123456" } },
    { user: { phone: "+919876543210" }, sms: { otp: "12345" } },
    { user: { phone: "+919876543210" }, sms: { otp: "abcdef" } },
    { user: { phone: "" }, sms: { otp: "123456" } },
    {},
  ];

  for (const event of invalidEvents) {
    const response = await handler(request(event));
    assert.equal(response.status, 400);
  }
});

test("rejects insecure public gateway URLs", async () => {
  let fetched = false;

  const handler = createSmsHookHandler({
    env: { ...baseEnv, SMS_GATEWAY_URL: "http://gateway.example" },
    verify: (payload) => JSON.parse(payload),
    fetchImpl: async () => {
      fetched = true;
      return gatewayResponse({ success: true });
    },
  });

  const response = await handler(request());

  assert.equal(response.status, 500);
  assert.equal(fetched, false);
});

test("allows local HTTP gateways for development", async () => {
  const handler = createSmsHookHandler({
    env: { ...baseEnv, SMS_GATEWAY_URL: "http://host.docker.internal:8080" },
    verify: (payload) => JSON.parse(payload),
    fetchImpl: async () => gatewayResponse({ success: true }),
  });

  const response = await handler(request());

  assert.equal(response.status, 200);
});

test("fails closed when the gateway is not configured", async () => {
  let fetched = false;

  const handler = createSmsHookHandler({
    env: { SMS_PROVIDER: "gateway" },
    verify: (payload) => JSON.parse(payload),
    fetchImpl: async () => {
      fetched = true;
      return gatewayResponse({ success: true });
    },
  });

  const response = await handler(request());

  assert.equal(response.status, 500);
  assert.equal(fetched, false);
});

test("returns a generic error when the gateway rejects the request", async () => {
  const logs: unknown[][] = [];
  const originalError = console.error;
  console.error = (...args: unknown[]) => {
    logs.push(args);
  };

  try {
    const handler = createSmsHookHandler({
      env: baseEnv,
      verify: (payload) => JSON.parse(payload),
      generateRequestId: () => "request-4",
      fetchImpl: async () => gatewayResponse({ error: "slow down" }, 429),
    });

    const response = await handler(request());
    const body = await response.json();

    assert.equal(response.status, 502);
    assert.deepEqual(body, {
      error: {
        http_code: 502,
        message: "SMS gateway rejected the message",
      },
    });
    assert.equal(JSON.stringify(logs).includes("123456"), false);
  } finally {
    console.error = originalError;
  }
});

test("returns a generic error when the gateway reports success false", async () => {
  const handler = createSmsHookHandler({
    env: baseEnv,
    verify: (payload) => JSON.parse(payload),
    fetchImpl: async () => gatewayResponse({ success: false }),
  });

  const response = await handler(request());

  assert.equal(response.status, 502);
});

test("returns a generic error when the gateway returns invalid JSON", async () => {
  const handler = createSmsHookHandler({
    env: baseEnv,
    verify: (payload) => JSON.parse(payload),
    fetchImpl: async () => new Response("not-json", { status: 200 }),
  });

  const response = await handler(request());

  assert.equal(response.status, 502);
});

test("times out a slow gateway", async () => {
  const handler = createSmsHookHandler({
    env: baseEnv,
    verify: (payload) => JSON.parse(payload),
    timeoutMs: 5,
    fetchImpl: async (_url, init) =>
      new Promise((_resolve, reject) => {
        init?.signal?.addEventListener("abort", () => {
          reject(new DOMException("Aborted", "AbortError"));
        });
      }),
  });

  const response = await handler(request());

  assert.equal(response.status, 502);
});

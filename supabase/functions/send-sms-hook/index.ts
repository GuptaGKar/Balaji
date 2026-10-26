import { Webhook } from "https://esm.sh/standardwebhooks@1.0.0";

import { createSmsHookHandler } from "./handler.ts";

const secrets = (
  Deno.env.get("SEND_SMS_HOOK_SECRETS") ??
  Deno.env.get("SEND_SMS_HOOK_SECRET") ??
  ""
)
  .split("|")
  .map((secret) => secret.trim().replace(/^v1,whsec_/, ""))
  .filter(Boolean);

const verify = (payload: string, headers: Record<string, string>): unknown => {
  let lastError: unknown;

  for (const secret of secrets) {
    try {
      return new Webhook(secret).verify(payload, headers);
    } catch (error) {
      lastError = error;
    }
  }

  throw lastError ?? new Error("Send SMS Hook secret is not configured");
};

const smsHookHandler = createSmsHookHandler({
  env: Deno.env.toObject(),
  verify,
});

Deno.serve(async (request) => {
  if (secrets.length === 0) {
    console.error("SEND_SMS_HOOK_SECRET is not configured");
    return new Response(
      JSON.stringify({
        error: {
          http_code: 500,
          message: "SMS hook secret is not configured",
        },
      }),
      {
        status: 500,
        headers: { "Content-Type": "application/json" },
      },
    );
  }

  return smsHookHandler(request);
});

export interface SmsHookEnv {
  SMS_PROVIDER?: string;
  TWOFACTOR_API_KEY?: string;
  TWOFACTOR_TEMPLATE_NAME?: string;
  SMS_GATEWAY_URL?: string;
  SMS_GATEWAY_API_KEY?: string;
  SMS_GATEWAY_CF_ACCESS_CLIENT_ID?: string;
  SMS_GATEWAY_CF_ACCESS_CLIENT_SECRET?: string;
}

type SmsProvider = "2factor" | "gateway";

export type VerifySmsHook = (payload: string, headers: Record<string, string>) => unknown;

export interface SmsHookHandlerOptions {
  env: SmsHookEnv;
  verify: VerifySmsHook;
  fetchImpl?: typeof fetch;
  generateRequestId?: () => string;
  timeoutMs?: number;
}

interface ParsedSmsEvent {
  phone: string;
  otp: string;
}

const INDIA_MOBILE = /^\+91[6-9]\d{9}$/;
const SIX_DIGIT_OTP = /^\d{6}$/;
const DEFAULT_TIMEOUT_MS = 4000;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function hookError(httpCode: number, message: string): Response {
  return json({ error: { http_code: httpCode, message } }, httpCode);
}

function parseEvent(event: unknown): ParsedSmsEvent | null {
  if (!isRecord(event)) return null;

  const user = event.user;
  const sms = event.sms;
  if (!isRecord(user) || !isRecord(sms)) return null;

  const userPhone = typeof user.phone === "string" ? user.phone.trim() : "";
  const smsPhone = typeof sms.phone === "string" ? sms.phone.trim() : "";
  const rawPhone = userPhone || smsPhone;
  const rawOtp = typeof sms.otp === "string" ? sms.otp.trim() : "";
  const phone = rawPhone.startsWith("+") ? rawPhone : `+${rawPhone}`;

  if (!INDIA_MOBILE.test(phone) || !SIX_DIGIT_OTP.test(rawOtp)) return null;

  return { phone, otp: rawOtp };
}

function buildHeaders(apiKey: string, requestId: string, env: SmsHookEnv): Record<string, string> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    "X-API-Key": apiKey,
    "X-Request-Id": requestId,
  };

  const cfAccessClientId = env.SMS_GATEWAY_CF_ACCESS_CLIENT_ID?.trim();
  const cfAccessClientSecret = env.SMS_GATEWAY_CF_ACCESS_CLIENT_SECRET?.trim();
  if (cfAccessClientId && cfAccessClientSecret) {
    headers["CF-Access-Client-Id"] = cfAccessClientId;
    headers["CF-Access-Client-Secret"] = cfAccessClientSecret;
  }

  return headers;
}

function gatewaySucceeded(value: unknown): boolean {
  return isRecord(value) && value.success === true;
}

function twoFactorSucceeded(value: unknown): boolean {
  if (!isRecord(value)) return false;
  const status = typeof value.Status === "string" ? value.Status : value.status;
  return typeof status === "string" && status.trim().toLowerCase() === "success";
}

function normalizeProvider(raw: string | undefined): SmsProvider | null {
  const value = (raw ?? "2factor").trim().toLowerCase();
  if (value === "2factor" || value === "2-factor" || value === "twofactor") {
    return "2factor";
  }
  if (value === "gateway" || value === "sim") return "gateway";
  return null;
}

function twoFactorUrl(
  apiKey: string,
  phone: string,
  otp: string,
  templateName: string | undefined,
): string {
  const base = `https://2factor.in/API/V1/${encodeURIComponent(apiKey)}/SMS/${phone.replace(
    /^\+91/,
    "",
  )}/${otp}`;
  const template = templateName?.trim();
  return template ? `${base}/${encodeURIComponent(template)}` : base;
}

function normalizeGatewayUrl(raw: string): string {
  const value = raw.trim().replace(/\/+$/, "");
  if (!value) return "";

  try {
    const url = new URL(value);
    const localHost = ["localhost", "127.0.0.1", "host.docker.internal"].includes(url.hostname);
    const secure = url.protocol === "https:" || (url.protocol === "http:" && localHost);
    if (!secure || url.username || url.password || url.search || url.hash) {
      return "";
    }
    return value;
  } catch {
    return "";
  }
}

export function createSmsHookHandler(options: SmsHookHandlerOptions) {
  const fetchImpl = options.fetchImpl ?? fetch;
  const generateRequestId = options.generateRequestId ?? (() => crypto.randomUUID());
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
  const provider = normalizeProvider(options.env.SMS_PROVIDER);
  const gatewayUrl = normalizeGatewayUrl(options.env.SMS_GATEWAY_URL ?? "");
  const gatewayApiKey = (options.env.SMS_GATEWAY_API_KEY ?? "").trim();
  const twoFactorApiKey = (options.env.TWOFACTOR_API_KEY ?? "").trim();
  const twoFactorTemplateName = options.env.TWOFACTOR_TEMPLATE_NAME;

  return async function handleSmsHook(request: Request): Promise<Response> {
    if (request.method !== "POST") {
      return hookError(405, "Method not allowed");
    }

    if (!provider) {
      console.error("SMS provider configuration is invalid");
      return hookError(500, "SMS provider is not configured");
    }

    if (provider === "gateway" && (!gatewayUrl || !gatewayApiKey)) {
      console.error("SMS gateway URL or API key is not configured");
      return hookError(500, "SMS gateway is not configured");
    }

    if (provider === "2factor" && !twoFactorApiKey) {
      console.error("2Factor API key is not configured");
      return hookError(500, "SMS provider is not configured");
    }

    const payload = await request.text();
    let event: unknown;
    try {
      event = options.verify(payload, Object.fromEntries(request.headers));
    } catch {
      return hookError(401, "Invalid Send SMS Hook signature");
    }

    const parsed = parseEvent(event);
    if (!parsed) {
      return hookError(400, "Invalid SMS hook payload");
    }

    const requestId = generateRequestId();
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);

    try {
      const response =
        provider === "2factor"
          ? await fetchImpl(
              twoFactorUrl(twoFactorApiKey, parsed.phone, parsed.otp, twoFactorTemplateName),
              {
                method: "POST",
                signal: controller.signal,
              },
            )
          : await fetchImpl(`${gatewayUrl}/api/send`, {
              method: "POST",
              headers: buildHeaders(gatewayApiKey, requestId, options.env),
              body: JSON.stringify({
                phone_number: parsed.phone,
                message: `Balaji Provision Store OTP: ${parsed.otp}. Do not share this code.`,
                request_id: requestId,
              }),
              signal: controller.signal,
            });

      if (!response.ok) {
        console.error(
          provider === "2factor"
            ? "SMS provider rejected the request"
            : "SMS gateway rejected the request",
          {
            requestId,
            status: response.status,
          },
        );
        return hookError(
          502,
          provider === "2factor"
            ? "SMS provider rejected the message"
            : "SMS gateway rejected the message",
        );
      }

      let result: unknown;
      try {
        result = await response.json();
      } catch {
        console.error(
          provider === "2factor"
            ? "SMS provider returned invalid JSON"
            : "SMS gateway returned invalid JSON",
          {
            requestId,
            status: response.status,
          },
        );
        return hookError(
          502,
          provider === "2factor"
            ? "SMS provider returned an invalid response"
            : "SMS gateway returned an invalid response",
        );
      }

      if (provider === "2factor" && !twoFactorSucceeded(result)) {
        console.error("SMS provider did not accept the message", {
          requestId,
          status: response.status,
        });
        return hookError(502, "SMS provider rejected the message");
      }

      if (provider === "gateway" && !gatewaySucceeded(result)) {
        console.error("SMS gateway did not queue the message", {
          requestId,
          status: response.status,
        });
        return hookError(502, "SMS gateway did not queue the message");
      }

      return json({});
    } catch (error) {
      const reason = error instanceof Error ? error.name : "UnknownError";
      console.error(
        provider === "2factor" ? "SMS provider request failed" : "SMS gateway request failed",
        { requestId, reason },
      );
      return hookError(
        502,
        provider === "2factor" ? "SMS provider unavailable" : "SMS gateway unavailable",
      );
    } finally {
      clearTimeout(timer);
    }
  };
}

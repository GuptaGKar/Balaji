// Supabase Edge Function: send-push
// Drains public.push_queue and delivers through FCM HTTP v1.
//
// Secrets (Supabase dashboard -> Edge Functions -> Secrets):
//   FCM_SERVICE_ACCOUNT  the whole service-account JSON, one line
//   PUSH_WEBHOOK_SECRET  any long random string; callers must send it in x-push-secret
//
// Deploy:  supabase functions deploy send-push --no-verify-jwt
// Invoke:  curl -X POST https://<project>.supabase.co/functions/v1/send-push \
//            -H "x-push-secret: $PUSH_WEBHOOK_SECRET"
//
// The function is idempotent per queue row: a row is marked sent only after FCM
// accepts it, and tokens FCM reports as UNREGISTERED are deleted so they are not
// retried forever.

import { createClient } from "npm:@supabase/supabase-js@2";
import { JWT } from "npm:google-auth-library@9";

type QueueRow = {
  id: string;
  audience: "customer" | "staff";
  user_id: string | null;
  order_id: string | null;
  title: string;
  body: string;
  data: Record<string, unknown>;
  attempts: number;
};

const BATCH = 50;
const MAX_ATTEMPTS = 5;

function serviceAccount() {
  const raw = Deno.env.get("FCM_SERVICE_ACCOUNT");
  if (!raw) throw new Error("FCM_SERVICE_ACCOUNT is not set");
  const parsed = JSON.parse(raw);
  return {
    projectId: parsed.project_id as string,
    clientEmail: parsed.client_email as string,
    privateKey: (parsed.private_key as string).replace(/\\n/g, "\n"),
  };
}

async function accessToken(): Promise<{ token: string; projectId: string }> {
  const account = serviceAccount();
  const jwt = new JWT({
    email: account.clientEmail,
    key: account.privateKey,
    scopes: ["https://www.googleapis.com/auth/firebase.messaging"],
  });
  const { access_token } = await jwt.authorize();
  if (!access_token) throw new Error("Could not obtain a Google access token");
  return { token: access_token, projectId: account.projectId };
}

async function sendOne(
  projectId: string,
  bearer: string,
  deviceToken: string,
  row: QueueRow,
): Promise<{ ok: boolean; unregistered: boolean; error?: string }> {
  const response = await fetch(
    `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${bearer}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token: deviceToken,
          notification: { title: row.title, body: row.body },
          data: Object.fromEntries(
            Object.entries({ ...row.data, title: row.title, body: row.body }).map(
              ([k, v]) => [k, String(v)],
            ),
          ),
          android: {
            priority: "HIGH",
            notification: {
              channel_id: row.audience === "staff" ? "orders" : "order_status",
            },
          },
        },
      }),
    },
  );

  if (response.ok) return { ok: true, unregistered: false };

  const text = await response.text();
  const unregistered =
    response.status === 404 || /UNREGISTERED|INVALID_ARGUMENT/.test(text);
  return { ok: false, unregistered, error: `${response.status} ${text.slice(0, 300)}` };
}

Deno.serve(async (req) => {
  const secret = Deno.env.get("PUSH_WEBHOOK_SECRET");
  if (!secret || req.headers.get("x-push-secret") !== secret) {
    return new Response("Forbidden", { status: 403 });
  }

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );

  const { data: rows, error } = await supabase
    .from("push_queue")
    .select("*")
    .is("sent_at", null)
    .lt("attempts", MAX_ATTEMPTS)
    .order("created_at")
    .limit(BATCH);

  if (error) return Response.json({ error: error.message }, { status: 500 });
  if (!rows || rows.length === 0) return Response.json({ sent: 0, skipped: 0 });

  const { token: bearer, projectId } = await accessToken();

  let sent = 0;
  const failures: string[] = [];

  for (const row of rows as QueueRow[]) {
    let tokenQuery = supabase
      .from("device_tokens")
      .select("token")
      .eq("app_flavour", row.audience);

    if (row.user_id) tokenQuery = tokenQuery.eq("user_id", row.user_id);

    const { data: tokens } = await tokenQuery;
    if (!tokens || tokens.length === 0) {
      await supabase
        .from("push_queue")
        .update({ sent_at: new Date().toISOString(), last_error: "no device token" })
        .eq("id", row.id);
      continue;
    }

    let delivered = 0;
    let lastError: string | null = null;

    for (const { token } of tokens as { token: string }[]) {
      const result = await sendOne(projectId, bearer, token, row);
      if (result.ok) {
        delivered += 1;
        continue;
      }
      lastError = result.error ?? "unknown";
      if (result.unregistered) {
        await supabase.from("device_tokens").delete().eq("token", token);
      }
    }

    if (delivered > 0) {
      sent += 1;
      await supabase
        .from("push_queue")
        .update({ sent_at: new Date().toISOString(), last_error: null })
        .eq("id", row.id);
    } else {
      failures.push(row.id);
      await supabase
        .from("push_queue")
        .update({ attempts: row.attempts + 1, last_error: lastError })
        .eq("id", row.id);
    }
  }

  return Response.json({ sent, failed: failures.length, failures });
});

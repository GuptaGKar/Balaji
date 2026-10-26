import { NextRequest, NextResponse } from "next/server";
import { createServerClient } from "@supabase/ssr";
import { cookies } from "next/headers";
import { getAdminClient } from "@/server/supabaseAdmin";

async function getCallerRole(): Promise<string | null> {
  const cookieStore = await cookies();
  const supabase = createServerClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!,
    {
      cookies: {
        getAll() {
          return cookieStore.getAll();
        },
        setAll() {},
      },
    },
  );
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) return null;
  const { data } = await supabase.from("profiles").select("role").eq("id", user.id).single();
  return data?.role ?? null;
}

/**
 * POST /api/staff
 * Body: { action: "create", login_id, display_name, phone, role }
 *       { action: "reset_password", user_id }
 *
 * Only callable by authenticated managers (validated server-side).
 */
export async function POST(req: NextRequest) {
  try {
    const callerRole = await getCallerRole();
    if (callerRole !== "manager") {
      return NextResponse.json({ error: "Unauthorized" }, { status: 403 });
    }

    const body = await req.json();

    let admin: ReturnType<typeof getAdminClient>;
    try {
      admin = getAdminClient();
    } catch {
      return NextResponse.json(
        {
          error:
            "Staff provisioning is not configured on this server. Set SUPABASE_SERVICE_ROLE_KEY and restart the web app.",
        },
        { status: 503 },
      );
    }

    if (body.action === "create") {
      const { login_id, display_name, phone, role, password } = body;
      if (!login_id || !display_name || !phone || !role || !password) {
        return NextResponse.json({ error: "Missing required fields" }, { status: 400 });
      }
      if (!["staff", "delivery"].includes(role)) {
        return NextResponse.json({ error: "Invalid role" }, { status: 400 });
      }
      if (typeof login_id !== "string") {
        return NextResponse.json({ error: "Invalid login ID" }, { status: 400 });
      }
      const normalizedLoginId = login_id.trim().toUpperCase();
      if (!/^[A-Z0-9-]{3,40}$/.test(normalizedLoginId)) {
        return NextResponse.json(
          { error: "Login ID must be 3-40 letters, digits or hyphens" },
          { status: 400 },
        );
      }
      if (typeof display_name !== "string" || !display_name.trim() || display_name.length > 120) {
        return NextResponse.json({ error: "Invalid display name" }, { status: 400 });
      }
      if (typeof phone !== "string" || !/^[0-9+ -]{7,20}$/.test(phone)) {
        return NextResponse.json({ error: "Invalid phone number" }, { status: 400 });
      }
      if (typeof password !== "string" || password.length < 8 || password.length > 72) {
        return NextResponse.json(
          { error: "Password must be 8-72 characters" },
          { status: 400 },
        );
      }

      const email = `${normalizedLoginId.toLowerCase()}@riders.invalid`;

      const { data: authData, error: authError } = await admin.auth.admin.createUser({
        email,
        password,
        email_confirm: true,
        user_metadata: { display_name: display_name.trim(), phone: phone.trim() },
      });
      if (authError) {
        return NextResponse.json({ error: authError.message }, { status: 400 });
      }

      // Update profile with role info
      const { error: profileError } = await admin
        .from("profiles")
        .update({
          role,
          login_id: normalizedLoginId,
          display_name: display_name.trim(),
          phone: phone.trim(),
          is_active: true,
          must_change_password: false,
        })
        .eq("id", authData.user.id);

      if (profileError) {
        // Clean up: delete auth user if profile update failed
        await admin.auth.admin.deleteUser(authData.user.id);
        return NextResponse.json({ error: profileError.message }, { status: 500 });
      }

      return NextResponse.json({ success: true }, { status: 201 });
    }

    if (body.action === "reset_password") {
      const { user_id } = body;
      if (!user_id || typeof user_id !== "string") {
        return NextResponse.json({ error: "user_id required" }, { status: 400 });
      }

      const { data: target, error: targetError } = await admin
        .from("profiles")
        .select("id, role")
        .eq("id", user_id)
        .single();
      if (targetError || !target) {
        return NextResponse.json({ error: "User not found" }, { status: 404 });
      }
      if (!["staff", "delivery"].includes(target.role)) {
        return NextResponse.json(
          { error: "Only staff and rider accounts can be reset here" },
          { status: 400 },
        );
      }

      const tempPassword = crypto.randomUUID().slice(0, 12);
      const { error } = await admin.auth.admin.updateUserById(user_id, { password: tempPassword });
      if (error) {
        return NextResponse.json({ error: error.message }, { status: 400 });
      }

      // Mark must_change_password with the service-role client: the anon client
      // is subject to RLS and this update would silently affect zero rows.
      const { error: flagError } = await admin
        .from("profiles")
        .update({ must_change_password: true })
        .eq("id", user_id);
      if (flagError) {
        return NextResponse.json({ error: flagError.message }, { status: 500 });
      }

      return NextResponse.json({ tempPassword }, { status: 200 });
    }

    return NextResponse.json({ error: "Unknown action" }, { status: 400 });
  } catch (err: any) {
    return NextResponse.json({ error: err.message ?? "Internal server error" }, { status: 500 });
  }
}

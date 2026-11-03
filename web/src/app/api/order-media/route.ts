import { createServerClient } from "@supabase/ssr";
import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";

export const dynamic = "force-dynamic";

// Browser requests carry the current session; Storage RLS authorizes each path.
// No service-role client and no persisted signed URLs are used for private media.
export async function GET(request: NextRequest) {
  const bucket = request.nextUrl.searchParams.get("bucket");
  const path = request.nextUrl.searchParams.get("path");
  if (
    !bucket ||
    !["order-images", "order-proofs"].includes(bucket) ||
    !path ||
    path.length > 255 ||
    !/^[0-9a-f-]{36}\/[A-Za-z0-9/_ .-]+$/i.test(path) ||
    path.includes("..")
  ) {
    return NextResponse.json({ error: "Invalid media path" }, { status: 400 });
  }
  const cookieStore = await cookies();
  const supabase = createServerClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!,
    { cookies: { getAll: () => cookieStore.getAll(), setAll() {} } },
  );
  const { data: auth, error: authError } = await supabase.auth.getUser();
  if (authError || !auth.user) {
    return NextResponse.json({ error: "Login required" }, { status: 401 });
  }
  const { data, error } = await supabase.storage.from(bucket).createSignedUrl(path, 60);
  if (error || !data?.signedUrl) {
    return NextResponse.json({ error: "Media unavailable" }, { status: 404 });
  }
  const response = NextResponse.redirect(data.signedUrl, 307);
  response.headers.set("Cache-Control", "private, no-store");
  response.headers.set("Referrer-Policy", "no-referrer");
  return response;
}

import { createServerClient } from "@supabase/ssr";
import { NextResponse, type NextRequest } from "next/server";

export async function middleware(request: NextRequest) {
  const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const supabaseAnonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;

  if (!supabaseUrl || !supabaseAnonKey) {
    return new NextResponse("Supabase is not configured. Set NEXT_PUBLIC_SUPABASE_URL and NEXT_PUBLIC_SUPABASE_ANON_KEY.", {
      status: 500,
    });
  }

  let supabaseResponse = NextResponse.next({
    request,
  });
  let pendingCookies: Array<{ name: string; value: string; options?: any }> = [];

  const supabase = createServerClient(supabaseUrl, supabaseAnonKey, {
    cookies: {
      getAll() {
        return request.cookies.getAll();
      },
      setAll(cookiesToSet: Array<{ name: string; value: string; options?: any }>) {
        cookiesToSet.forEach(({ name, value }) => request.cookies.set(name, value));
        supabaseResponse = NextResponse.next({
          request,
        });
        cookiesToSet.forEach(({ name, value, options }) => {
          pendingCookies.push({ name, value, options });
          supabaseResponse.cookies.set(name, value, options);
        });
      },
    },
  });

  const {
    data: { user },
  } = await supabase.auth.getUser();

  const pathname = request.nextUrl.pathname;
  const isAuthenticated = user != null;
  const isAuthPage = pathname.startsWith("/login");
  const isChangePasswordPage = pathname === "/change-password";
  const isApi = pathname.startsWith("/api");
  const isPublicAsset = pathname.startsWith("/_next") || pathname === "/favicon.ico";

  const redirectWithCookies = (url: URL) => {
    const response = NextResponse.redirect(url);
    for (const { name, value, options } of pendingCookies) {
      response.cookies.set(name, value, options);
    }
    return response;
  };

  if (!isAuthenticated && !isAuthPage && !isPublicAsset) {
    const url = request.nextUrl.clone();
    url.pathname = "/login";
    url.searchParams.set("redirect", pathname);
    return NextResponse.redirect(url);
  }

  if (isAuthenticated && isAuthPage) {
    const url = request.nextUrl.clone();
    url.pathname = "/";
    const redirect = request.nextUrl.searchParams.get("redirect");
    if (redirect && redirect.startsWith("/") && !redirect.startsWith("//")) url.pathname = redirect;
    url.search = "";
    return NextResponse.redirect(url);
  }

  if (isAuthenticated && !isApi && !isPublicAsset) {
    const { data: profile, error: profileError } = await supabase
      .from("profiles")
      .select("role, is_active, must_change_password")
      .eq("id", user.id)
      .maybeSingle();

    if (profileError || !profile || !profile.is_active) {
      await supabase.auth.signOut();
      const url = request.nextUrl.clone();
      url.pathname = "/login";
      url.search = "";
      url.searchParams.set("error", profileError ? "profile" : "inactive");
      return redirectWithCookies(url);
    }

    if (profile.must_change_password && !isChangePasswordPage) {
      const url = request.nextUrl.clone();
      url.pathname = "/change-password";
      url.search = "";
      return NextResponse.redirect(url);
    }

    if (profile.role === "delivery" && !pathname.startsWith("/rider") && !isChangePasswordPage) {
      const url = request.nextUrl.clone();
      url.pathname = "/rider";
      url.search = "";
      return NextResponse.redirect(url);
    }

    const managerOnly = ["/settings", "/staff", "/audit", "/reports", "/service-areas"];
    const isManagerOnly = managerOnly.some(
      (path) => pathname === path || pathname.startsWith(`${path}/`),
    );
    if (isManagerOnly && profile.role !== "manager") {
      const url = request.nextUrl.clone();
      url.pathname = "/";
      url.search = "";
      return NextResponse.redirect(url);
    }
  }

  return supabaseResponse;
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)"],
};

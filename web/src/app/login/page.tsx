"use client";

import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { createClient } from "@/lib/supabase";
import { loginSchema, type LoginInput } from "@/lib/schemas";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { cn } from "@/lib/utils";
import { Loader2, AlertCircle, Eye, EyeOff, Mail, Lock, Store } from "lucide-react";
import { Suspense } from "react";

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const redirectParam = searchParams.get("redirect");
  const redirect =
    redirectParam && redirectParam.startsWith("/") && !redirectParam.startsWith("//")
      ? redirectParam
      : "/";
  const authError = searchParams.get("error");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState(
    authError === "inactive"
      ? "This account has been deactivated. Contact a manager."
      : authError === "profile"
        ? "Your profile could not be loaded. Contact a manager."
        : authError === "customer"
          ? "Customer accounts use the mobile app. Ask a manager for staff access."
          : "",
  );
  const [loading, setLoading] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginInput>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  const onSubmit = async (data: LoginInput) => {
    setLoading(true);
    setError("");

    try {
      const supabase = createClient();
      const { data: authData, error } = await supabase.auth.signInWithPassword({
        email: data.email.includes("@")
          ? data.email
          : `${data.email.toLowerCase()}@riders.invalid`,
        password: data.password,
      });

      if (error) {
        setError(error.message);
        setLoading(false);
        return;
      }

      const { data: profile } = await supabase
        .from("profiles")
        .select("role, is_active, must_change_password")
        .eq("id", authData.user.id)
        .maybeSingle();

      if (!profile) {
        await supabase.auth.signOut();
        setError("Your profile could not be loaded. Contact a manager.");
        setLoading(false);
        return;
      }

      if (profile.role === "customer") {
        await supabase.auth.signOut();
        setError("Customer accounts use the mobile app. Ask a manager for staff access.");
        setLoading(false);
        return;
      }

      if (!profile.is_active) {
        await supabase.auth.signOut();
        setError("This account has been deactivated. Contact a manager.");
        setLoading(false);
        return;
      }

      if (profile?.must_change_password) {
        router.replace("/change-password");
      } else if (profile?.role === "delivery") {
        router.replace("/rider");
      } else {
        router.replace(redirect);
      }
      router.refresh();
    } catch {
      setError("Unable to sign in. Check your connection and try again.");
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-100 px-4">
      <div className="max-w-md w-full bg-white rounded-3xl shadow-xl border border-slate-200 p-8 space-y-6">
        <div className="text-center space-y-2">
          <div className="inline-flex h-14 w-14 rounded-2xl bg-emerald-600 items-center justify-center text-white shadow-lg shadow-emerald-600/30 mb-2">
            <Store className="h-8 w-8" />
          </div>
          <h2 className="text-2xl font-extrabold text-slate-900 tracking-tight">Balaji Provision Store</h2>
          <p className="text-sm font-medium text-slate-500">Store Management & Staff Portal</p>
        </div>

        <form className="space-y-4" onSubmit={handleSubmit(onSubmit)}>
          {error && (
            <div
              className="flex items-center gap-2.5 p-3.5 text-sm font-medium text-red-700 bg-red-50 border border-red-200 rounded-xl"
              role="alert"
            >
              <AlertCircle className="h-5 w-5 flex-shrink-0 text-red-500" />
              <span>{error}</span>
            </div>
          )}

          <div className="space-y-3">
            <div>
              <label htmlFor="email" className="block text-xs font-semibold text-slate-700 mb-1">
                Email or Login ID
              </label>
              <div className="relative">
                <Mail className="absolute left-3.5 top-1/2 -translate-y-1/2 h-5 w-5 text-slate-400" />
                <input
                  {...register("email")}
                  id="email"
                  type="text"
                  autoComplete="username"
                  required
                  className={cn(
                    "w-full pl-11 pr-4 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 placeholder-slate-400 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:bg-white transition-all",
                    errors.email && "border-red-500 focus:ring-red-500",
                  )}
                  placeholder="STAFF-001 or admin@balajiprovision.com"
                  disabled={loading}
                />
              </div>
              {errors.email && <p className="mt-1 text-xs font-medium text-red-600">{errors.email.message}</p>}
            </div>

            <div>
              <label htmlFor="password" className="block text-xs font-semibold text-slate-700 mb-1">
                Password
              </label>
              <div className="relative">
                <Lock className="absolute left-3.5 top-1/2 -translate-y-1/2 h-5 w-5 text-slate-400" />
                <input
                  {...register("password")}
                  id="password"
                  type={showPassword ? "text" : "password"}
                  required
                  className={cn(
                    "w-full pl-11 pr-11 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 placeholder-slate-400 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:bg-white transition-all",
                    errors.password && "border-red-500 focus:ring-red-500",
                  )}
                  placeholder="••••••••"
                  disabled={loading}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 p-1"
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {errors.password && <p className="mt-1 text-xs font-medium text-red-600">{errors.password.message}</p>}
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full flex items-center justify-center py-3 px-4 rounded-xl text-sm font-bold text-white bg-emerald-600 hover:bg-emerald-700 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:ring-offset-2 disabled:opacity-50 transition-all shadow-md shadow-emerald-600/20"
          >
            {loading ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Signing in...
              </>
            ) : (
              "Sign In to Dashboard"
            )}
          </button>
        </form>

        <div className="pt-4 border-t border-slate-100 text-center">
          <p className="text-xs text-slate-400">
            Balaji Provision Store Management System • Secured Access
          </p>
        </div>
      </div>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen flex items-center justify-center">
          <Loader2 className="h-8 w-8 animate-spin text-emerald-600" />
        </div>
      }
    >
      <LoginForm />
    </Suspense>
  );
}
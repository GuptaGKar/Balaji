"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { createClient } from "@/lib/supabase";
import { AlertCircle, KeyRound, Loader2, Phone } from "lucide-react";

function normalizeIndianPhone(raw: string): string | null {
  const digits = raw.replace(/\D/g, "");
  let national = "";
  if (digits.length === 10) national = digits;
  else if (digits.length === 11 && digits.startsWith("0")) national = digits.slice(1);
  else if (digits.length === 12 && digits.startsWith("91")) national = digits.slice(2);
  else return null;
  if (!/^[6-9]\d{9}$/.test(national)) return null;
  return `+91${national}`;
}

export default function CustomerBillLogin({
  redirectTo,
  onStaffLogin,
}: {
  redirectTo: string;
  onStaffLogin: () => void;
}) {
  const router = useRouter();
  const [phone, setPhone] = useState("");
  const [otp, setOtp] = useState("");
  const [step, setStep] = useState<"phone" | "otp">("phone");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const sendOtp = async () => {
    const normalized = normalizeIndianPhone(phone);
    if (!normalized) {
      setError("Enter a valid 10-digit Indian mobile number.");
      return;
    }
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const supabase = createClient();
      const { error: otpError } = await supabase.auth.signInWithOtp({
        phone: normalized,
        options: { shouldCreateUser: false, channel: "sms" },
      });
      if (otpError) throw otpError;
      setPhone(normalized);
      setStep("otp");
      setNotice(`If ${normalized} is registered, an OTP has been sent.`);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Could not send the OTP. Check your connection and try again.",
      );
    } finally {
      setBusy(false);
    }
  };

  const verifyOtp = async () => {
    const token = otp.replace(/\D/g, "").slice(0, 6);
    if (token.length !== 6) {
      setError("Enter the 6-digit OTP sent to your phone.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const supabase = createClient();
      const { data, error: verifyError } = await supabase.auth.verifyOtp({
        phone,
        token,
        type: "sms",
      });
      if (verifyError) throw verifyError;
      if (!data.user) throw new Error("Sign in failed. Request a new OTP.");

      const { data: profile } = await supabase
        .from("profiles")
        .select("role, is_active")
        .eq("id", data.user.id)
        .maybeSingle();

      if (profile?.role !== "customer" || !profile.is_active) {
        await supabase.auth.signOut();
        throw new Error("This account cannot view customer bills.");
      }

      router.replace(redirectTo);
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not verify the OTP.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-lg font-extrabold text-slate-900">View your bills</h2>
        <p className="mt-1 text-sm text-slate-500">
          Sign in with the same mobile number you use for the store app.
        </p>
      </div>

      {error ? (
        <div
          className="flex items-center gap-2.5 rounded-xl border border-red-200 bg-red-50 p-3.5 text-sm font-medium text-red-700"
          role="alert"
        >
          <AlertCircle className="h-5 w-5 flex-shrink-0 text-red-500" />
          <span>{error}</span>
        </div>
      ) : null}
      {notice ? (
        <p className="rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-xs font-semibold text-emerald-800">
          {notice}
        </p>
      ) : null}

      {step === "phone" ? (
        <div className="space-y-3">
          <div>
            <label htmlFor="bill-phone" className="mb-1 block text-xs font-semibold text-slate-700">
              Mobile number
            </label>
            <div className="relative">
              <Phone className="absolute left-3.5 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-400" />
              <input
                id="bill-phone"
                type="tel"
                inputMode="tel"
                autoComplete="tel"
                value={phone}
                onChange={(event) => setPhone(event.target.value)}
                placeholder="98765 43210"
                disabled={busy}
                className="w-full rounded-xl border border-slate-300 bg-slate-50 py-2.5 pl-11 pr-4 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:bg-white"
              />
            </div>
          </div>
          <button
            type="button"
            onClick={sendOtp}
            disabled={busy}
            className="flex w-full items-center justify-center rounded-xl bg-emerald-600 px-4 py-3 text-sm font-bold text-white hover:bg-emerald-700 disabled:opacity-50"
          >
            {busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            {busy ? "Sending OTP…" : "Send OTP"}
          </button>
        </div>
      ) : (
        <div className="space-y-3">
          <div>
            <label htmlFor="bill-otp" className="mb-1 block text-xs font-semibold text-slate-700">
              6-digit OTP
            </label>
            <div className="relative">
              <KeyRound className="absolute left-3.5 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-400" />
              <input
                id="bill-otp"
                type="text"
                inputMode="numeric"
                autoComplete="one-time-code"
                value={otp}
                onChange={(event) => setOtp(event.target.value.replace(/\D/g, "").slice(0, 6))}
                placeholder="••••••"
                disabled={busy}
                className="w-full rounded-xl border border-slate-300 bg-slate-50 py-2.5 pl-11 pr-4 text-sm tracking-[0.3em] text-slate-900 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:bg-white"
              />
            </div>
          </div>
          <button
            type="button"
            onClick={verifyOtp}
            disabled={busy || otp.length !== 6}
            className="flex w-full items-center justify-center rounded-xl bg-emerald-600 px-4 py-3 text-sm font-bold text-white hover:bg-emerald-700 disabled:opacity-50"
          >
            {busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            {busy ? "Verifying…" : "Verify and view bills"}
          </button>
          <div className="flex items-center justify-between text-xs font-semibold">
            <button
              type="button"
              onClick={() => {
                setStep("phone");
                setOtp("");
                setNotice("");
                setError("");
              }}
              disabled={busy}
              className="text-slate-500 hover:text-slate-800"
            >
              Change number
            </button>
            <button
              type="button"
              onClick={sendOtp}
              disabled={busy}
              className="text-emerald-700 hover:text-emerald-800"
            >
              Resend OTP
            </button>
          </div>
        </div>
      )}

      <button
        type="button"
        onClick={onStaffLogin}
        className="w-full border-t border-slate-100 pt-3 text-center text-xs font-semibold text-slate-400 hover:text-slate-700"
      >
        Staff sign in instead
      </button>
    </div>
  );
}

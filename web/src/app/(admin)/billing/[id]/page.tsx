"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { createClient } from "@/lib/supabase";
import type { BillData } from "@/lib/billing";
import BillDocument from "@/components/BillDocument";
import BillPrintActions from "@/components/BillPrintActions";
import { AlertCircle, ArrowLeft, Loader2, ShieldAlert, XCircle } from "lucide-react";
import { toast } from "sonner";

export default function BillDetailPage() {
  const params = useParams<{ id: string }>();
  const [supabase] = useState(() => createClient());
  const [data, setData] = useState<BillData | null>(null);
  const [role, setRole] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [reason, setReason] = useState("");
  const [voiding, setVoiding] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    const {
      data: { user },
    } = await supabase.auth.getUser();
    if (user) {
      const { data: profile } = await supabase
        .from("profiles")
        .select("role")
        .eq("id", user.id)
        .maybeSingle();
      setRole(profile?.role ?? null);
    }

    const { data: result, error: rpcError } = await supabase.rpc("get_bill", {
      p_bill_id: params.id,
    });
    if (rpcError) {
      setError(rpcError.message);
    } else if (result?.error) {
      setError(result.message || result.error);
    } else {
      setData(result as BillData);
    }
    setLoading(false);
  }, [params.id, supabase]);

  useEffect(() => {
    load();
  }, [load]);

  const voidBill = async () => {
    if (!data || !reason.trim()) {
      toast.error("Enter a reason before voiding the bill");
      return;
    }
    if (!window.confirm("Void this bill? Stock for POS sales will be restored once.")) {
      return;
    }
    setVoiding(true);
    try {
      const { data: result, error: rpcError } = await supabase.rpc("void_bill", {
        p_bill_id: data.bill.id,
        p_reason: reason.trim(),
      });
      if (rpcError) throw rpcError;
      if (result?.error) throw new Error(result.message || result.error);
      toast.success(result?.already_void ? "Bill was already void" : "Bill voided");
      setReason("");
      await load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Could not void the bill");
    } finally {
      setVoiding(false);
    }
  };

  return (
    <div className="space-y-5 pb-12">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <Link
            href="/billing"
            className="inline-flex items-center gap-1.5 text-xs font-bold text-slate-500 hover:text-slate-900"
          >
            <ArrowLeft className="h-4 w-4" /> Back to bills
          </Link>
          <h1 className="mt-1 text-2xl font-extrabold tracking-tight text-slate-900">
            Bill details
          </h1>
        </div>
        {data ? (
          <BillPrintActions pdfHref={`/api/bills/${data.bill.id}/pdf`} />
        ) : null}
      </div>

      {loading ? (
        <div className="flex items-center justify-center gap-2 rounded-2xl border border-slate-200 bg-white p-16 text-sm font-semibold text-slate-500">
          <Loader2 className="h-5 w-5 animate-spin text-emerald-600" /> Loading bill…
        </div>
      ) : error ? (
        <div className="flex flex-col items-center gap-3 rounded-2xl border border-red-200 bg-red-50 p-12 text-center" role="alert">
          <AlertCircle className="h-8 w-8 text-red-500" />
          <p className="text-sm font-bold text-red-800">{error}</p>
          <Link
            href="/billing"
            className="rounded-xl border border-red-300 bg-white px-4 py-2 text-xs font-bold text-red-700"
          >
            Return to billing
          </Link>
        </div>
      ) : data ? (
        <>
          <BillDocument data={data} />

          {data.bill.status === "ISSUED" ? (
            role === "manager" ? (
              <section className="no-print mx-auto w-full max-w-3xl rounded-2xl border border-red-200 bg-red-50 p-5">
                <div className="flex items-start gap-2">
                  <ShieldAlert className="mt-0.5 h-5 w-5 text-red-600" />
                  <div className="flex-1">
                    <h2 className="text-sm font-bold text-red-800">Void this bill</h2>
                    <p className="mt-1 text-xs text-red-700">
                      Manager-only. The bill and its line items are never deleted; the
                      void reason and actor are audited.
                    </p>
                    <div className="mt-3 flex flex-col sm:flex-row gap-2">
                      <input
                        type="text"
                        value={reason}
                        onChange={(event) => setReason(event.target.value)}
                        placeholder="Reason (required)"
                        aria-label="Void reason"
                        className="flex-1 rounded-xl border border-red-300 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-red-400"
                      />
                      <button
                        type="button"
                        onClick={voidBill}
                        disabled={voiding || !reason.trim()}
                        className="inline-flex items-center justify-center gap-2 rounded-xl bg-red-600 px-4 py-2 text-xs font-bold text-white hover:bg-red-700 disabled:opacity-50"
                      >
                        {voiding ? (
                          <Loader2 className="h-4 w-4 animate-spin" />
                        ) : (
                          <XCircle className="h-4 w-4" />
                        )}
                        Void bill
                      </button>
                    </div>
                  </div>
                </div>
              </section>
            ) : (
              <p className="no-print mx-auto w-full max-w-3xl text-xs font-semibold text-slate-500">
                Only a manager can void this bill.
              </p>
            )
          ) : null}
        </>
      ) : null}
    </div>
  );
}

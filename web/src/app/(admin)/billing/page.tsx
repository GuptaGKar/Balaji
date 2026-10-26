"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { createClient } from "@/lib/supabase";
import { formatDateTime } from "@/lib/format";
import { formatRupees } from "@/lib/money";
import { billSourceLabel } from "@/lib/billing";
import { AlertCircle, Loader2, Plus, ReceiptText, RefreshCw, Search } from "lucide-react";
import { cn } from "@/lib/utils";

type BillRow = {
  id: string;
  bill_number: string;
  source: "ORDER" | "POS";
  status: "ISSUED" | "VOID";
  customer_name: string | null;
  customer_phone: string | null;
  grand_total_paise: number;
  issued_at: string;
  payment_status: string;
  order_id: string | null;
};

export default function BillingPage() {
  const [supabase] = useState(() => createClient());
  const [bills, setBills] = useState<BillRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [source, setSource] = useState<"all" | "ORDER" | "POS">("all");

  const fetchBills = useCallback(async () => {
    setLoading(true);
    setError("");
    let query = supabase
      .from("bills")
      .select(
        "id, bill_number, source, status, customer_name, customer_phone, grand_total_paise, issued_at, payment_status, order_id",
      )
      .order("issued_at", { ascending: false })
      .limit(300);
    if (source !== "all") query = query.eq("source", source);
    const { data, error: queryError } = await query;
    if (queryError) {
      setError(queryError.message);
    } else {
      setBills((data as BillRow[]) ?? []);
    }
    setLoading(false);
  }, [source, supabase]);

  useEffect(() => {
    fetchBills();
  }, [fetchBills]);

  const term = search.trim().toLowerCase();
  const filtered = bills.filter((bill) => {
    if (!term) return true;
    return (
      bill.bill_number.toLowerCase().includes(term) ||
      (bill.customer_name ?? "").toLowerCase().includes(term) ||
      (bill.customer_phone ?? "").includes(term)
    );
  });

  return (
    <div className="space-y-6 pb-12">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">
            Bills &amp; POS
          </h1>
          <p className="text-xs font-medium text-slate-500 mt-1">
            Bills are generated on the server from order snapshots or in-store sales.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={fetchBills}
            className="inline-flex items-center gap-2 rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-xs font-bold text-slate-700 hover:bg-slate-50"
          >
            <RefreshCw className="h-4 w-4" /> Refresh
          </button>
          <Link
            href="/billing/pos"
            className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 text-xs font-bold text-white hover:bg-emerald-700"
          >
            <Plus className="h-4 w-4" /> New POS sale
          </Link>
        </div>
      </div>

      <div className="flex flex-col sm:flex-row gap-3">
        <div className="relative flex-1">
          <Search className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            type="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search bill number, customer name or phone"
            aria-label="Search bills"
            className="w-full rounded-xl border border-slate-300 bg-white py-2.5 pl-10 pr-4 text-sm text-slate-900 focus:ring-2 focus:ring-emerald-500"
          />
        </div>
        <select
          value={source}
          onChange={(event) => setSource(event.target.value as "all" | "ORDER" | "POS")}
          aria-label="Filter by bill source"
          className="rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 focus:ring-2 focus:ring-emerald-500"
        >
          <option value="all">All bills</option>
          <option value="ORDER">Online orders</option>
          <option value="POS">In-store POS</option>
        </select>
      </div>

      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        {loading ? (
          <div className="flex items-center justify-center gap-2 p-12 text-sm font-semibold text-slate-500">
            <Loader2 className="h-5 w-5 animate-spin text-emerald-600" /> Loading bills…
          </div>
        ) : error ? (
          <div className="flex flex-col items-center gap-3 p-12 text-center" role="alert">
            <AlertCircle className="h-8 w-8 text-red-500" />
            <p className="text-sm font-semibold text-red-700">{error}</p>
            <button
              type="button"
              onClick={fetchBills}
              className="rounded-xl border border-slate-300 px-4 py-2 text-xs font-bold text-slate-700"
            >
              Try again
            </button>
          </div>
        ) : filtered.length === 0 ? (
          <div className="p-12 text-center">
            <ReceiptText className="mx-auto mb-3 h-10 w-10 text-slate-300" />
            <p className="text-sm font-bold text-slate-700">No bills found</p>
            <p className="mt-1 text-xs text-slate-500">
              {bills.length === 0
                ? "Generate a bill from an order or complete a POS sale."
                : "Try a different search or source filter."}
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-[11px] uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-3">Bill</th>
                  <th className="px-4 py-3">Source</th>
                  <th className="px-4 py-3">Customer</th>
                  <th className="px-4 py-3">Issued</th>
                  <th className="px-4 py-3">Payment</th>
                  <th className="px-4 py-3 text-right">Total</th>
                  <th className="px-4 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filtered.map((bill) => (
                  <tr key={bill.id} className="hover:bg-slate-50">
                    <td className="px-4 py-3">
                      <p className="font-mono text-xs font-bold text-slate-900">
                        {bill.bill_number}
                      </p>
                      {bill.status === "VOID" ? (
                        <span className="mt-1 inline-flex rounded-full bg-red-100 px-2 py-0.5 text-[10px] font-bold text-red-700">
                          VOID
                        </span>
                      ) : null}
                    </td>
                    <td className="px-4 py-3 text-xs text-slate-600">
                      {billSourceLabel(bill.source)}
                    </td>
                    <td className="px-4 py-3">
                      <p className="font-semibold text-slate-800">
                        {bill.customer_name || "Walk-in customer"}
                      </p>
                      {bill.customer_phone ? (
                        <p className="text-xs text-slate-500">{bill.customer_phone}</p>
                      ) : null}
                    </td>
                    <td className="px-4 py-3 text-xs text-slate-500">
                      {formatDateTime(bill.issued_at)}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={cn(
                          "inline-flex rounded-full px-2 py-0.5 text-[10px] font-bold capitalize",
                          bill.payment_status === "paid"
                            ? "bg-emerald-100 text-emerald-800"
                            : "bg-amber-100 text-amber-800",
                        )}
                      >
                        {bill.payment_status.replace(/_/g, " ")}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right font-bold text-slate-900">
                      {formatRupees(bill.grand_total_paise)}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <Link
                        href={`/billing/${bill.id}`}
                        className="inline-flex rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-bold text-slate-700 hover:bg-slate-100"
                      >
                        View
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

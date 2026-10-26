import Link from "next/link";
import { redirect } from "next/navigation";
import { createServerSupabaseClient } from "@/lib/supabase-server";
import { formatBillDateTime, billSourceLabel } from "@/lib/billing";
import { formatRupees } from "@/lib/money";
import CustomerSignOut from "@/components/CustomerSignOut";
import { ReceiptText } from "lucide-react";

export const dynamic = "force-dynamic";

type BillRow = {
  id: string;
  bill_number: string;
  source: string;
  status: string;
  issued_at: string;
  grand_total_paise: number;
  order_id: string | null;
};

export default async function CustomerBillsPage() {
  const supabase = await createServerSupabaseClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();

  if (!user) redirect("/login?redirect=/bills");

  const { data: profile } = await supabase
    .from("profiles")
    .select("role")
    .eq("id", user.id)
    .maybeSingle();

  if (profile?.role === "staff" || profile?.role === "manager") {
    redirect("/billing");
  }
  if (profile?.role !== "customer") {
    redirect("/login");
  }

  const { data, error } = await supabase
    .from("bills")
    .select("id, bill_number, source, status, issued_at, grand_total_paise, order_id")
    .eq("source", "ORDER")
    .order("issued_at", { ascending: false })
    .limit(100);

  const bills = (data as BillRow[] | null) ?? [];

  return (
    <main className="min-h-screen bg-slate-100 px-4 py-8 sm:py-12">
      <div className="mx-auto max-w-3xl space-y-6">
        <header className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.18em] text-emerald-700">
              Balaji Provision Store
            </p>
            <h1 className="mt-1 text-2xl font-extrabold tracking-tight text-slate-900">
              Your bills
            </h1>
            <p className="mt-1 text-sm text-slate-500">
              Bills for your online orders appear here once the store issues them.
            </p>
          </div>
          <CustomerSignOut />
        </header>

        {error ? (
          <div className="rounded-2xl border border-red-200 bg-red-50 p-6" role="alert">
            <p className="text-sm font-bold text-red-800">Could not load your bills.</p>
            <p className="mt-1 text-xs text-red-700">{error.message}</p>
          </div>
        ) : bills.length === 0 ? (
          <div className="rounded-2xl border border-slate-200 bg-white p-12 text-center">
            <ReceiptText className="mx-auto mb-3 h-10 w-10 text-slate-300" />
            <p className="text-sm font-bold text-slate-700">No bills yet</p>
            <p className="mx-auto mt-1 max-w-sm text-xs text-slate-500">
              Bills are issued by the store after an order. If you were given a bill link,
              open that link or ask the store to issue your bill.
            </p>
          </div>
        ) : (
          <ul className="space-y-3">
            {bills.map((bill) => (
              <li key={bill.id}>
                <Link
                  href={`/bills/${bill.id}`}
                  className="flex items-center justify-between gap-4 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm transition hover:border-emerald-300 hover:shadow"
                >
                  <div className="min-w-0">
                    <p className="font-mono text-sm font-bold text-slate-900">
                      {bill.bill_number}
                    </p>
                    <p className="mt-1 text-xs text-slate-500">
                      {billSourceLabel(bill.source)} ·{" "}
                      {formatBillDateTime(bill.issued_at, "Asia/Kolkata")}
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="text-sm font-extrabold text-slate-900">
                      {formatRupees(bill.grand_total_paise)}
                    </p>
                    <p
                      className={`mt-1 text-[11px] font-bold uppercase ${
                        bill.status === "VOID" ? "text-red-600" : "text-emerald-700"
                      }`}
                    >
                      {bill.status}
                    </p>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}

        <p className="text-center text-xs text-slate-400">
          Keep this page private. Bills are visible only to your signed-in account.
        </p>
      </div>
    </main>
  );
}

import Link from "next/link";
import { redirect } from "next/navigation";
import { createServerSupabaseClient } from "@/lib/supabase-server";
import type { BillData } from "@/lib/billing";
import BillDocument from "@/components/BillDocument";
import BillPrintActions from "@/components/BillPrintActions";
import CustomerSignOut from "@/components/CustomerSignOut";
import { ArrowLeft, ReceiptText } from "lucide-react";

export const dynamic = "force-dynamic";

export default async function CustomerBillPage({ params }: { params: { id: string } }) {
  const supabase = await createServerSupabaseClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();

  if (!user) redirect(`/login?redirect=${encodeURIComponent(`/bills/${params.id}`)}`);

  const { data: profile } = await supabase
    .from("profiles")
    .select("role")
    .eq("id", user.id)
    .maybeSingle();

  if (profile?.role === "staff" || profile?.role === "manager") {
    redirect(`/billing/${params.id}`);
  }
  if (profile?.role !== "customer") {
    redirect("/login");
  }

  const { data, error } = await supabase.rpc("get_bill", {
    p_bill_id: params.id,
  });
  const payload = data as (BillData & { error?: string; message?: string }) | null;
  const unavailable = error || !payload || payload.error;

  return (
    <main className="min-h-screen bg-slate-100 px-4 py-8 sm:py-12">
      <div className="mx-auto max-w-3xl space-y-5">
        <header className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <Link
              href="/bills"
              className="inline-flex items-center gap-1.5 text-xs font-bold text-slate-500 hover:text-slate-900"
            >
              <ArrowLeft className="h-4 w-4" /> Back to my bills
            </Link>
            <h1 className="mt-1 text-2xl font-extrabold tracking-tight text-slate-900">Bill</h1>
          </div>
          <div className="flex items-center gap-2">
            {!unavailable && payload ? (
              <BillPrintActions pdfHref={`/api/bills/${payload.bill.id}/pdf`} />
            ) : null}
            <CustomerSignOut />
          </div>
        </header>

        {unavailable ? (
          <div
            className="rounded-2xl border border-red-200 bg-red-50 p-10 text-center"
            role="alert"
          >
            <ReceiptText className="mx-auto mb-3 h-10 w-10 text-red-300" />
            <h2 className="text-sm font-bold text-red-800">Bill unavailable</h2>
            <p className="mx-auto mt-1 max-w-md text-xs text-red-700">
              {payload?.message || "This bill does not exist or does not belong to your account."}
            </p>
            <Link
              href="/bills"
              className="mt-4 inline-flex rounded-xl border border-red-300 bg-white px-4 py-2 text-xs font-bold text-red-700"
            >
              Return to my bills
            </Link>
          </div>
        ) : (
          <BillDocument data={payload as BillData} />
        )}
      </div>
    </main>
  );
}

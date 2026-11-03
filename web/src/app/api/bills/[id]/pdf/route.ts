import { NextResponse } from "next/server";
import { createServerSupabaseClient } from "@/lib/supabase-server";
import { buildBillPdf } from "@/lib/bill-pdf";
import type { BillData } from "@/lib/billing";

export const runtime = "nodejs";

// Next 15 passes dynamic route params as a Promise; the old synchronous shape fails
// the build's type check.
export async function GET(_request: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  const supabase = await createServerSupabaseClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();

  if (!user) {
    return NextResponse.json({ error: "Sign in required" }, { status: 401 });
  }

  const { data, error } = await supabase.rpc("get_bill", { p_bill_id: id });
  const payload = data as (BillData & { error?: string; message?: string }) | null;

  if (error || !payload || payload.error) {
    const code = payload?.error ?? error?.code ?? "NOT_FOUND";
    const status =
      code === "UNAUTHENTICATED"
        ? 401
        : code === "FORBIDDEN"
          ? 403
          : code === "VALIDATION_FAILED"
            ? 400
            : 404;
    return NextResponse.json({ error: payload?.message ?? "Bill not available" }, { status });
  }

  const bytes = await buildBillPdf(payload);
  const filename = `bill-${payload.bill.bill_number.replace(/[^A-Za-z0-9-]+/g, "-")}.pdf`;

  return new NextResponse(Buffer.from(bytes), {
    headers: {
      "Content-Type": "application/pdf",
      "Content-Disposition": `inline; filename="${filename}"`,
      "Cache-Control": "private, no-store",
    },
  });
}

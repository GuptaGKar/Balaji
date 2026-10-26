import type { BillData } from "@/lib/billing";
import {
  billSourceLabel,
  formatBillDateTime,
  paymentMethodLabel,
  paymentStatusLabel,
} from "@/lib/billing";
import { formatRupees } from "@/lib/money";

export default function BillDocument({ data }: { data: BillData }) {
  const { bill, items, store } = data;
  const isVoid = bill.status === "VOID";

  return (
    <article
      className="bill-doc mx-auto w-full max-w-3xl bg-white text-slate-900 border border-slate-200 rounded-2xl p-6 sm:p-10 shadow-sm"
      aria-label={`Bill ${bill.bill_number}`}
    >
      <header className="flex flex-col sm:flex-row sm:items-start justify-between gap-4 border-b border-slate-200 pb-5">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.18em] text-emerald-700">
            {store.name}
          </p>
          <h1 className="mt-1 text-2xl font-extrabold tracking-tight">BILL</h1>
          <p className="mt-1 text-xs text-slate-500">
            {billSourceLabel(bill.source)}
            {bill.order_public_number ? ` · Order #${bill.order_public_number}` : ""}
          </p>
          {store.phone ? (
            <p className="mt-1 text-xs text-slate-500">Store phone: {store.phone}</p>
          ) : null}
        </div>
        <div className="sm:text-right text-xs text-slate-600 space-y-1">
          <p className="font-mono font-bold text-sm text-slate-900">{bill.bill_number}</p>
          <p>Issued {formatBillDateTime(bill.issued_at, store.timezone)}</p>
          <p>
            <span
              className={`inline-flex rounded-full px-2.5 py-0.5 text-[11px] font-bold ${
                isVoid
                  ? "bg-red-100 text-red-700"
                  : "bg-emerald-100 text-emerald-800"
              }`}
            >
              {bill.status}
            </span>
          </p>
        </div>
      </header>

      {isVoid ? (
        <div className="mt-5 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
          <p className="font-bold">This bill was voided.</p>
          {bill.void_reason ? <p className="mt-1">Reason: {bill.void_reason}</p> : null}
        </div>
      ) : null}

      <section className="mt-5 grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
        <div>
          <p className="text-[11px] font-bold uppercase tracking-wide text-slate-400">
            Customer
          </p>
          <p className="mt-1 font-bold">{bill.customer_name || "Walk-in customer"}</p>
          {bill.customer_phone ? (
            <p className="text-xs text-slate-500">{bill.customer_phone}</p>
          ) : null}
        </div>
        <div className="sm:text-right">
          <p className="text-[11px] font-bold uppercase tracking-wide text-slate-400">
            Payment
          </p>
          <p className="mt-1 font-bold">{paymentMethodLabel(bill.payment_method)}</p>
          <p className="text-xs text-slate-500 capitalize">
            {paymentStatusLabel(bill.payment_status)}
          </p>
        </div>
      </section>

      <div className="mt-6 overflow-x-auto">
        <table className="w-full text-left text-xs border-collapse">
          <thead>
            <tr className="border-y border-slate-200 bg-slate-50 text-[11px] uppercase tracking-wide text-slate-500">
              <th className="px-2 py-2.5">#</th>
              <th className="px-2 py-2.5">Item</th>
              <th className="px-2 py-2.5">Qty</th>
              <th className="px-2 py-2.5 text-right">Rate</th>
              <th className="px-2 py-2.5 text-right">Amount</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {items.map((item, index) => (
              <tr key={item.id} className="align-top">
                <td className="px-2 py-2.5 text-slate-400">{index + 1}</td>
                <td className="px-2 py-2.5">
                  <p className="font-semibold text-slate-900">{item.name}</p>
                  <p className="text-[11px] text-slate-400">SKU {item.sku}</p>
                </td>
                <td className="px-2 py-2.5 whitespace-nowrap">{item.display_qty}</td>
                <td className="px-2 py-2.5 text-right whitespace-nowrap">
                  {formatRupees(item.unit_price_paise)}
                  <span className="block text-[10px] text-slate-400">
                    {item.price_basis_label}
                  </span>
                </td>
                <td className="px-2 py-2.5 text-right font-semibold whitespace-nowrap">
                  {formatRupees(item.line_total_paise)}
                </td>
              </tr>
            ))}
            {items.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-2 py-6 text-center text-slate-400">
                  No line items on this bill.
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>

      <section className="mt-5 ml-auto w-full sm:w-72 text-sm">
        <div className="flex items-center justify-between py-1">
          <span className="text-slate-500">Subtotal</span>
          <span className="font-semibold">{formatRupees(bill.subtotal_paise)}</span>
        </div>
        {bill.discount_paise > 0 ? (
          <div className="flex items-center justify-between py-1">
            <span className="text-slate-500">Discount</span>
            <span className="font-semibold">- {formatRupees(bill.discount_paise)}</span>
          </div>
        ) : null}
        {bill.source === "ORDER" ? (
          <div className="flex items-center justify-between py-1">
            <span className="text-slate-500">Delivery fee</span>
            <span className="font-semibold">{formatRupees(bill.delivery_fee_paise)}</span>
          </div>
        ) : null}
        {bill.tax_paise > 0 ? (
          <div className="flex items-center justify-between py-1">
            <span className="text-slate-500">Tax</span>
            <span className="font-semibold">{formatRupees(bill.tax_paise)}</span>
          </div>
        ) : null}
        <div className="mt-2 flex items-center justify-between border-t border-slate-200 pt-2 text-base">
          <span className="font-bold">Grand total</span>
          <span className="font-extrabold">{formatRupees(bill.grand_total_paise)}</span>
        </div>
      </section>

      <footer className="mt-8 border-t border-slate-200 pt-4 text-[11px] leading-5 text-slate-400">
        <p>
          This is a computer-generated bill. Prices and quantities are server-validated
          at the time of sale. {bill.tax_paise > 0 ? "Tax is computed by the server." : "Tax is not configured on this bill."}
        </p>
        <p className="mt-1">Thank you for shopping with {store.name}.</p>
      </footer>
    </article>
  );
}

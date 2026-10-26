"use client";

export default function BillPrintActions({
  pdfHref,
}: {
  pdfHref: string;
}) {
  const print = (size: "a4" | "thermal") => {
    document.getElementById("bill-print-page-size")?.remove();
    const style = document.createElement("style");
    style.id = "bill-print-page-size";
    style.textContent =
      size === "thermal"
        ? "@page { size: 80mm auto; margin: 3mm; } .bill-doc { width: 74mm !important; max-width: none !important; border: 0 !important; border-radius: 0 !important; box-shadow: none !important; padding: 0 !important; font-size: 10px; } .bill-doc table { font-size: 9px; } .bill-doc thead { display: table-header-group; }"
        : "@page { size: A4; margin: 12mm; } .bill-doc { width: 186mm !important; max-width: none !important; border: 0 !important; box-shadow: none !important; }";

    document.head.appendChild(style);
    const cleanup = () => {
      document.getElementById("bill-print-page-size")?.remove();
      window.removeEventListener("afterprint", cleanup);
    };
    window.addEventListener("afterprint", cleanup);
    window.print();
    window.setTimeout(cleanup, 60_000);
  };

  return (
    <div className="no-print flex flex-wrap items-center gap-2">
      <button
        type="button"
        onClick={() => print("a4")}
        className="inline-flex items-center rounded-xl border border-slate-300 bg-white px-4 py-2 text-xs font-bold text-slate-700 hover:bg-slate-50"
      >
        Print A4
      </button>
      <button
        type="button"
        onClick={() => print("thermal")}
        className="inline-flex items-center rounded-xl border border-slate-300 bg-white px-4 py-2 text-xs font-bold text-slate-700 hover:bg-slate-50"
      >
        Print 80 mm
      </button>
      <a
        href={pdfHref}
        className="inline-flex items-center rounded-xl bg-emerald-600 px-4 py-2 text-xs font-bold text-white hover:bg-emerald-700"
      >
        Download PDF
      </a>
    </div>
  );
}

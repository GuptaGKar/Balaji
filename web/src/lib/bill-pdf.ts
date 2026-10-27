import { PDFDocument, StandardFonts, rgb, type PDFFont } from "pdf-lib";
import type { BillData, BillItem } from "@/lib/billing";
import { billSourceLabel, formatBillDateTime, paymentMethodLabel } from "@/lib/billing";
import { paiseToRupees } from "@/lib/money";

const PAGE_WIDTH = 595.28;
const PAGE_HEIGHT = 841.89;
const MARGIN = 48;

function ascii(value: string): string {
  return value
    .replace(/₹/g, "Rs.")
    .replace(/[\u2013\u2014]/g, "-")
    .replace(/[^\x20-\x7E]/g, "?");
}

function money(paise: number): string {
  return `Rs.${paiseToRupees(paise)}`;
}

function wrap(text: string, font: PDFFont, size: number, maxWidth: number): string[] {
  const words = ascii(text).split(/\s+/).filter(Boolean);
  const lines: string[] = [];
  let current = "";
  for (const word of words) {
    const candidate = current ? `${current} ${word}` : word;
    if (font.widthOfTextAtSize(candidate, size) <= maxWidth) {
      current = candidate;
    } else {
      if (current) lines.push(current);
      current = word;
    }
  }
  if (current) lines.push(current);
  return lines.length > 0 ? lines : [""];
}

export async function buildBillPdf(data: BillData): Promise<Uint8Array> {
  const { bill, items, store } = data;
  const pdf = await PDFDocument.create();
  const font = await pdf.embedFont(StandardFonts.Helvetica);
  const bold = await pdf.embedFont(StandardFonts.HelveticaBold);
  const green = rgb(0.02, 0.47, 0.34);
  const slate = rgb(0.12, 0.16, 0.22);
  const muted = rgb(0.45, 0.5, 0.56);
  const line = rgb(0.88, 0.9, 0.92);

  let page = pdf.addPage([PAGE_WIDTH, PAGE_HEIGHT]);
  let y = PAGE_HEIGHT - MARGIN;

  page.drawText(ascii(store.name), { x: MARGIN, y, size: 16, font: bold, color: green });
  y -= 20;
  page.drawText("BILL", { x: MARGIN, y, size: 20, font: bold, color: slate });
  y -= 16;
  page.drawText(
    `${billSourceLabel(bill.source)}${bill.order_public_number ? ` | Order #${bill.order_public_number}` : ""}`,
    {
      x: MARGIN,
      y,
      size: 9,
      font,
      color: muted,
    },
  );
  if (store.phone) {
    y -= 12;
    page.drawText(ascii(`Store phone: ${store.phone}`), {
      x: MARGIN,
      y,
      size: 9,
      font,
      color: muted,
    });
  }

  const rightX = PAGE_WIDTH - MARGIN;
  const drawRight = (text: string, size: number, fontToUse: PDFFont, color = slate, offset = 0) => {
    const safe = ascii(text);
    page.drawText(safe, {
      x: rightX - fontToUse.widthOfTextAtSize(safe, size),
      y: PAGE_HEIGHT - MARGIN - offset,
      size,
      font: fontToUse,
      color,
    });
  };
  drawRight(bill.bill_number, 11, bold, slate, 0);
  drawRight(`Issued ${formatBillDateTime(bill.issued_at, store.timezone)}`, 9, font, muted, 16);
  drawRight(bill.status, 10, bold, bill.status === "VOID" ? rgb(0.75, 0.1, 0.1) : green, 31);

  y = Math.min(y, PAGE_HEIGHT - MARGIN - 48) - 16;
  page.drawLine({ start: { x: MARGIN, y }, end: { x: rightX, y }, thickness: 1, color: line });
  y -= 20;

  page.drawText("CUSTOMER", { x: MARGIN, y, size: 8, font: bold, color: muted });
  page.drawText("PAYMENT", { x: PAGE_WIDTH / 2, y, size: 8, font: bold, color: muted });
  y -= 13;
  page.drawText(ascii(bill.customer_name || "Walk-in customer"), {
    x: MARGIN,
    y,
    size: 10,
    font: bold,
    color: slate,
  });
  page.drawText(ascii(paymentMethodLabel(bill.payment_method)), {
    x: PAGE_WIDTH / 2,
    y,
    size: 10,
    font: bold,
    color: slate,
  });
  y -= 11;
  if (bill.customer_phone) {
    page.drawText(ascii(bill.customer_phone), { x: MARGIN, y, size: 9, font, color: muted });
  }
  page.drawText(ascii(bill.payment_status.replace(/_/g, " ")), {
    x: PAGE_WIDTH / 2,
    y,
    size: 9,
    font,
    color: muted,
  });

  if (bill.status === "VOID") {
    y -= 22;
    page.drawText(ascii(`VOIDED: ${bill.void_reason || "No reason recorded"}`), {
      x: MARGIN,
      y,
      size: 10,
      font: bold,
      color: rgb(0.75, 0.1, 0.1),
    });
  }

  y -= 26;
  const drawTableHeader = () => {
    page.drawRectangle({
      x: MARGIN,
      y: y - 4,
      width: rightX - MARGIN,
      height: 18,
      color: rgb(0.96, 0.97, 0.98),
    });
    page.drawText("#", { x: MARGIN + 4, y: y + 2, size: 8, font: bold, color: muted });
    page.drawText("ITEM", { x: MARGIN + 24, y: y + 2, size: 8, font: bold, color: muted });
    page.drawText("QTY", { x: 330, y: y + 2, size: 8, font: bold, color: muted });
    page.drawText("RATE", { x: 420, y: y + 2, size: 8, font: bold, color: muted });
    page.drawText("AMOUNT", { x: rightX - 56, y: y + 2, size: 8, font: bold, color: muted });
    y -= 22;
  };
  drawTableHeader();

  const drawItem = (item: BillItem, index: number) => {
    const nameLines = wrap(item.name, font, 9, 230);
    const rowHeight = Math.max(18, nameLines.length * 11 + 8);
    if (y - rowHeight < 120) {
      page = pdf.addPage([PAGE_WIDTH, PAGE_HEIGHT]);
      y = PAGE_HEIGHT - MARGIN;
      drawTableHeader();
    }
    page.drawText(String(index + 1), { x: MARGIN + 4, y, size: 9, font, color: muted });
    nameLines.forEach((text, i) => {
      page.drawText(text, {
        x: MARGIN + 24,
        y: y - i * 11,
        size: 9,
        font: i === 0 ? bold : font,
        color: slate,
      });
    });
    page.drawText(ascii(`SKU ${item.sku}`), {
      x: MARGIN + 24,
      y: y - nameLines.length * 11,
      size: 7,
      font,
      color: muted,
    });
    page.drawText(ascii(item.display_qty), { x: 330, y, size: 9, font, color: slate });
    page.drawText(money(item.unit_price_paise), { x: 420, y, size: 9, font, color: slate });
    const amount = money(item.line_total_paise);
    page.drawText(amount, {
      x: rightX - font.widthOfTextAtSize(amount, 9),
      y,
      size: 9,
      font: bold,
      color: slate,
    });
    y -= rowHeight;
    page.drawLine({
      start: { x: MARGIN, y: y + 8 },
      end: { x: rightX, y: y + 8 },
      thickness: 0.5,
      color: line,
    });
  };

  items.forEach(drawItem);

  const totalRow = (label: string, value: string, size = 10, boldFont = false) => {
    if (y < 100) {
      page = pdf.addPage([PAGE_WIDTH, PAGE_HEIGHT]);
      y = PAGE_HEIGHT - MARGIN;
    }
    page.drawText(label, {
      x: 360,
      y,
      size,
      font: boldFont ? bold : font,
      color: boldFont ? slate : muted,
    });
    page.drawText(value, {
      x: rightX - (boldFont ? bold : font).widthOfTextAtSize(value, size),
      y,
      size,
      font: boldFont ? bold : font,
      color: slate,
    });
    y -= 16;
  };

  y -= 4;
  totalRow("Subtotal", money(bill.subtotal_paise));
  if (bill.discount_paise > 0) totalRow("Discount", `- ${money(bill.discount_paise)}`);
  if (bill.source === "ORDER") totalRow("Delivery fee", money(bill.delivery_fee_paise));
  if (bill.tax_paise > 0) totalRow("Tax", money(bill.tax_paise));
  y -= 2;
  page.drawLine({
    start: { x: 360, y: y + 12 },
    end: { x: rightX, y: y + 12 },
    thickness: 1,
    color: line,
  });
  totalRow("Grand total", money(bill.grand_total_paise), 12, true);

  y = Math.min(y, 80);
  page.drawLine({ start: { x: MARGIN, y }, end: { x: rightX, y }, thickness: 0.5, color: line });
  y -= 14;
  page.drawText("Computer-generated bill. Prices and quantities validated by the store server.", {
    x: MARGIN,
    y,
    size: 8,
    font,
    color: muted,
  });
  y -= 11;
  page.drawText(
    bill.tax_paise > 0 ? "Tax is computed by the server." : "Tax is not configured on this bill.",
    {
      x: MARGIN,
      y,
      size: 8,
      font,
      color: muted,
    },
  );

  return pdf.save();
}

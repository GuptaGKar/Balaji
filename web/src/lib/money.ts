export function paiseToRupees(paise: number): string {
  const rupees = paise / 100;
  return rupees.toLocaleString("en-IN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

export function paiseToRupeesCompact(paise: number): string {
  const rupees = paise / 100;
  return `₹${rupees.toLocaleString("en-IN", {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  })}`;
}

export function formatRupees(paise: number): string {
  return `₹${paiseToRupees(paise)}`;
}

/**
 * Whole rupees, for the storefront surfaces only: the dashboard item picker, the section
 * preview, and the Home/Categories rails. The business thinks in rupees, so a composed rail
 * reading "₹60.00" is noise.
 *
 * Rounding is half up to match the per-line money rule, and the arithmetic stays in integers
 * so no floating-point drift can appear in a price. An absent price renders as an em dash
 * rather than inventing ₹0, because a missing price and a free product are different facts.
 *
 * Existing screens keep formatRupees / paiseToRupeesCompact with their decimals; nothing here
 * changes what a bill, quote or total shows.
 */
export function formatRupeesWhole(paise: number | null | undefined): string {
  if (paise === null || paise === undefined) return "—";
  const wholeRupees = Math.floor((paise + 50) / 100);
  return `₹${wholeRupees.toLocaleString("en-IN")}`;
}

export function parseRupees(input: string): number | null {
  const cleaned = input.replace(/[₹,\s]/g, "");
  const match = cleaned.match(/^(\d+)(\.\d{1,2})?$/);
  if (!match) return null;
  const rupees = parseFloat(cleaned);
  return Math.round(rupees * 100);
}

export function formatGrams(grams: number): string {
  if (grams >= 1000) {
    const kg = grams / 1000;
    return `${kg.toLocaleString("en-IN", { maximumFractionDigits: 3 })} kg`;
  }
  return `${grams.toLocaleString("en-IN")} g`;
}

export function parseWeightInput(input: string, unit: "g" | "kg" = "g"): number | null {
  const cleaned = input.trim().replace(/[,\s]/g, "");
  const match = cleaned.match(/^(\d+)(\.\d+)?$/);
  if (!match) return null;
  const value = parseFloat(cleaned);
  if (unit === "kg") {
    return Math.round(value * 1000);
  }
  return Math.round(value);
}

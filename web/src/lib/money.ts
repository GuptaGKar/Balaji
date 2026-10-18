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

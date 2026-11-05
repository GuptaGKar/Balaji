"use client";

import { useEffect, useMemo, useState } from "react";
import { cn } from "@/lib/utils";
import { formatRupeesWhole } from "@/lib/money";
import { MAX_ITEMS_PER_SECTION } from "@/lib/storefront";
import { Search, X, ArrowUp, ArrowDown, AlertTriangle, Check } from "lucide-react";

export interface PickableItem {
  id: string;
  name: string;
  /** Pack label for products; empty for categories. */
  packLabel?: string | null;
  /** Whole-rupee display happens at render time via formatRupeesWhole; null hides the price. */
  pricePaise?: number | null;
  sku?: string | null;
  imageUrl?: string | null;
  active?: boolean;
  inStock?: boolean;
}

interface ItemPickerProps {
  open: boolean;
  sectionType: "products" | "categories";
  items: PickableItem[];
  selectedIds: string[];
  onConfirm: (ids: string[]) => void;
  onClose: () => void;
}

/**
 * Searchable multi-select for one section's items.
 *
 * Two deliberate choices. Out-of-stock and inactive items are WARNED about, not blocked: a
 * manager may legitimately compose a section ahead of restocking, and refusing would make the
 * picker unusable for planning. And the cap is enforced here as well as in the database, so the
 * manager is stopped while composing rather than after pressing save.
 */
export default function ItemPicker({
  open,
  sectionType,
  items,
  selectedIds,
  onConfirm,
  onClose,
}: ItemPickerProps) {
  const [query, setQuery] = useState("");
  const [draft, setDraft] = useState<string[]>(selectedIds);

  useEffect(() => {
    if (open) {
      setDraft(selectedIds);
      setQuery("");
    }
  }, [open, selectedIds]);

  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  const byId = useMemo(() => new Map(items.map((item) => [item.id, item])), [items]);

  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return items;
    return items.filter(
      (item) =>
        item.name.toLowerCase().includes(needle) ||
        (item.sku ? item.sku.toLowerCase().includes(needle) : false),
    );
  }, [items, query]);

  const selected = useMemo(
    () => draft.map((id) => byId.get(id)).filter((item): item is PickableItem => Boolean(item)),
    [draft, byId],
  );

  const risky = selected.filter((item) => item.active === false || item.inStock === false);

  if (!open) return null;

  const toggle = (id: string) => {
    if (draft.includes(id)) {
      setDraft(draft.filter((x) => x !== id));
      return;
    }
    if (draft.length >= MAX_ITEMS_PER_SECTION) return;
    setDraft([...draft, id]);
  };

  const move = (index: number, direction: -1 | 1) => {
    const target = index + direction;
    if (target < 0 || target >= draft.length) return;
    const next = [...draft];
    [next[index], next[target]] = [next[target], next[index]];
    setDraft(next);
  };

  const atCap = draft.length >= MAX_ITEMS_PER_SECTION;

  return (
    <div
      className="fixed inset-0 z-50 flex justify-end bg-slate-900/40"
      role="dialog"
      aria-modal="true"
      aria-label={`Choose ${sectionType === "products" ? "products" : "categories"}`}
      onClick={onClose}
    >
      <div
        className="flex h-full w-full max-w-xl flex-col bg-white shadow-2xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-slate-200 px-5 py-4">
          <h2 className="text-base font-bold text-slate-900">
            {sectionType === "products" ? "Choose products" : "Choose categories"}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close item picker"
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="border-b border-slate-100 px-5 py-3">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <input
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder={sectionType === "products" ? "Search by name or SKU" : "Search by name"}
              aria-label="Search items"
              className="w-full rounded-xl border border-slate-300 bg-slate-50 py-2 pl-9 pr-3 text-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
          </div>
          <p className="mt-2 text-xs text-slate-500">
            {draft.length} of {MAX_ITEMS_PER_SECTION} selected
            {atCap ? " - this section is full" : ""}
          </p>
        </div>

        {risky.length > 0 && (
          <div className="mx-5 mt-3 flex gap-2 rounded-xl border border-amber-300 bg-amber-50 p-3 text-xs text-amber-800">
            <AlertTriangle className="h-4 w-4 shrink-0" />
            <span>
              {risky.length} selected {risky.length === 1 ? "item is" : "items are"} inactive or out
              of stock. They will be hidden in the app until they are available again. Saving is
              still allowed.
            </span>
          </div>
        )}

        <div className="min-h-0 flex-1 overflow-y-auto px-5 py-3">
          {filtered.length === 0 && (
            <p className="py-8 text-center text-sm text-slate-500">
              Nothing matches “{query}”. Try a shorter word.
            </p>
          )}
          <ul className="space-y-1.5">
            {filtered.map((item) => {
              const checked = draft.includes(item.id);
              const disabled = !checked && atCap;
              return (
                <li key={item.id}>
                  <button
                    type="button"
                    onClick={() => toggle(item.id)}
                    disabled={disabled}
                    aria-pressed={checked}
                    className={cn(
                      "flex w-full items-center gap-3 rounded-xl border p-2.5 text-left transition",
                      checked
                        ? "border-emerald-400 bg-emerald-50"
                        : "border-slate-200 hover:bg-slate-50",
                      disabled && "cursor-not-allowed opacity-50",
                    )}
                  >
                    {item.imageUrl ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img
                        src={item.imageUrl}
                        alt=""
                        className="h-10 w-10 shrink-0 rounded-lg bg-slate-100 object-cover"
                      />
                    ) : (
                      <span className="h-10 w-10 shrink-0 rounded-lg bg-slate-100" />
                    )}
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-semibold text-slate-800">
                        {item.name}
                      </span>
                      <span className="block text-xs text-slate-500">
                        {[item.packLabel, item.sku].filter(Boolean).join(" · ") || "—"}
                      </span>
                    </span>
                    {typeof item.pricePaise === "number" && (
                      <span className="shrink-0 text-sm font-bold text-slate-700">
                        {formatRupeesWhole(item.pricePaise)}
                      </span>
                    )}
                    {(item.active === false || item.inStock === false) && (
                      <span className="shrink-0 rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-bold text-amber-700">
                        {item.inStock === false ? "OUT OF STOCK" : "INACTIVE"}
                      </span>
                    )}
                    <span
                      aria-hidden
                      className={cn(
                        "flex h-5 w-5 shrink-0 items-center justify-center rounded-md border",
                        checked
                          ? "border-emerald-500 bg-emerald-500 text-white"
                          : "border-slate-300",
                      )}
                    >
                      {checked ? <Check className="h-3.5 w-3.5" /> : null}
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        </div>

        <div className="border-t border-slate-200 px-5 py-3">
          <h3 className="mb-2 text-xs font-semibold text-slate-700">
            Selected order - this is the order customers see
          </h3>
          {selected.length === 0 ? (
            <p className="text-xs text-slate-500">Nothing selected yet.</p>
          ) : (
            <ol className="max-h-40 space-y-1 overflow-y-auto">
              {selected.map((item, index) => (
                <li
                  key={item.id}
                  className="flex items-center gap-2 rounded-lg bg-slate-50 px-2 py-1 text-xs"
                >
                  <span className="w-5 shrink-0 text-slate-400">{index + 1}</span>
                  <span className="min-w-0 flex-1 truncate text-slate-700">{item.name}</span>
                  <button
                    type="button"
                    onClick={() => move(index, -1)}
                    disabled={index === 0}
                    aria-label={`Move ${item.name} up`}
                    className="rounded p-1 text-slate-500 hover:bg-slate-200 disabled:opacity-30"
                  >
                    <ArrowUp className="h-3.5 w-3.5" />
                  </button>
                  <button
                    type="button"
                    onClick={() => move(index, 1)}
                    disabled={index === selected.length - 1}
                    aria-label={`Move ${item.name} down`}
                    className="rounded p-1 text-slate-500 hover:bg-slate-200 disabled:opacity-30"
                  >
                    <ArrowDown className="h-3.5 w-3.5" />
                  </button>
                  <button
                    type="button"
                    onClick={() => toggle(item.id)}
                    aria-label={`Remove ${item.name}`}
                    className="rounded p-1 text-slate-500 hover:bg-slate-200"
                  >
                    <X className="h-3.5 w-3.5" />
                  </button>
                </li>
              ))}
            </ol>
          )}
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-slate-200 px-5 py-3">
          <button
            type="button"
            onClick={onClose}
            className="rounded-xl px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-100"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={() => onConfirm(draft)}
            className="rounded-xl bg-emerald-600 px-4 py-2 text-sm font-bold text-white hover:bg-emerald-700"
          >
            Save {draft.length} item{draft.length === 1 ? "" : "s"}
          </button>
        </div>
      </div>
    </div>
  );
}

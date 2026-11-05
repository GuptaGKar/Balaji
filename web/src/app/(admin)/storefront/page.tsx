"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { createClient } from "@/lib/supabase";
import { cn } from "@/lib/utils";
import { formatRupeesWhole } from "@/lib/money";
import {
  MAX_SECTIONS_PER_PAGE,
  initialSectionState,
  sectionReducer,
  validateSectionDraft,
  type SectionAction,
  type SectionState,
  type SectionType,
  type StorefrontPage,
  type StorefrontSection,
} from "@/lib/storefront";
import ItemPicker, { type PickableItem } from "./ItemPicker";
import { Toaster, toast } from "sonner";
import {
  Plus,
  Save,
  X,
  Trash2,
  GripVertical,
  ArrowUp,
  ArrowDown,
  Loader2,
  Eye,
  EyeOff,
  AlertTriangle,
  RefreshCw,
} from "lucide-react";

interface StorefrontRow {
  id: string;
  title: string;
  subtitle: string | null;
  section_type: SectionType;
  updated_at: string | null;
  item_ids: string[];
}

const toSection = (row: StorefrontRow, index: number): StorefrontSection => ({
  id: row.id,
  title: row.title,
  subtitle: row.subtitle,
  sectionType: row.section_type,
  active: true,
  sortOrder: index + 1,
  updatedAt: row.updated_at,
  itemIds: row.item_ids ?? [],
  dirty: false,
});

export default function StorefrontPageView() {
  const [page, setPage] = useState<StorefrontPage>("home");
  const [states, setStates] = useState<Record<StorefrontPage, SectionState>>({
    home: initialSectionState(),
    categories: initialSectionState(),
  });
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [conflict, setConflict] = useState<string | null>(null);
  const [pickerFor, setPickerFor] = useState<string | null>(null);
  const [products, setProducts] = useState<PickableItem[]>([]);
  const [categories, setCategories] = useState<PickableItem[]>([]);

  const state = states[page];

  const dispatch = (action: SectionAction) =>
    setStates((current) => ({ ...current, [page]: sectionReducer(current[page], action) }));

  const fetchAll = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    const supabase = createClient();

    // get_storefront is the only content read. Prices, stock and availability come from the
    // catalogue queries below, so the dashboard preview cannot accidentally become a second
    // source of truth for money.
    const [homeRes, catRes, productRes, categoryRes] = await Promise.all([
      supabase.rpc("get_storefront", { p_page: "home" }),
      supabase.rpc("get_storefront", { p_page: "categories" }),
      supabase
        .from("products")
        .select("id,name,sku,price_paise,pack_size_label,unit_label,image_path,active,available")
        .eq("active", true)
        .order("name"),
      supabase
        .from("categories")
        .select("id,name,image_path,active,sort_order")
        .eq("active", true)
        .order("sort_order"),
    ]);

    const rpcMissing =
      (homeRes.error && /Could not find|does not exist|PGRST202/i.test(homeRes.error.message)) ||
      false;

    if (rpcMissing) {
      setLoadError(
        "The storefront tables are not in this database yet. Apply migration 20261105000001_storefront_sections.sql, then reload. Until then the apps keep their built-in layout.",
      );
      setLoading(false);
      return;
    }

    if (homeRes.error || catRes.error) {
      setLoadError(homeRes.error?.message || catRes.error?.message || "Failed to load storefront");
      setLoading(false);
      return;
    }

    const parse = (value: unknown): StorefrontRow[] => {
      if (!value) return [];
      if (Array.isArray(value)) return value as StorefrontRow[];
      try {
        return JSON.parse(String(value)) as StorefrontRow[];
      } catch {
        return [];
      }
    };

    const homeRows = parse(homeRes.data);
    const catRows = parse(catRes.data);

    // `active` is not in the payload because the RPC only returns active rows under RLS, so a
    // saved section reads back as active. Deactivation is a manager write, not a read field.
    setStates({
      home: initialSectionState(homeRows.map(toSection)),
      categories: initialSectionState(catRows.map(toSection)),
    });

    setProducts(
      (productRes.data ?? []).map((row: Record<string, unknown>) => ({
        id: String(row.id),
        name: String(row.name ?? ""),
        sku: row.sku ? String(row.sku) : null,
        packLabel: (row.pack_size_label as string) || (row.unit_label as string) || null,
        pricePaise: typeof row.price_paise === "number" ? row.price_paise : null,
        imageUrl: row.image_path ? String(row.image_path) : null,
        active: row.active !== false,
        inStock: row.available !== false,
      })),
    );
    setCategories(
      (categoryRes.data ?? []).map((row: Record<string, unknown>) => ({
        id: String(row.id),
        name: String(row.name ?? ""),
        imageUrl: row.image_path ? String(row.image_path) : null,
        active: row.active !== false,
      })),
    );
    setLoading(false);
  }, []);

  useEffect(() => {
    void fetchAll();
  }, [fetchAll]);

  useEffect(() => {
    const anyDirty = Object.values(states).some((s) => s.sections.some((section) => section.dirty));
    if (!anyDirty) return;
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [states]);

  const pickerItems =
    page === "home" && state.sections.find((s) => s.id === pickerFor)?.sectionType === "products"
      ? products
      : page === "categories" &&
          state.sections.find((s) => s.id === pickerFor)?.sectionType === "categories"
        ? categories
        : state.sections.find((s) => s.id === pickerFor)?.sectionType === "products"
          ? products
          : categories;

  const dirtyCount = state.sections.filter((section) => section.dirty).length;

  const addSection = () => {
    dispatch({
      type: "add",
      section: {
        id: `new-${Date.now()}`,
        title: "",
        subtitle: null,
        sectionType: page === "home" ? "products" : "categories",
        active: true,
        sortOrder: 0,
        updatedAt: null,
        itemIds: [],
        dirty: true,
      },
    });
  };

  const save = async () => {
    const invalid = state.sections.find((section) =>
      validateSectionDraft({
        title: section.title,
        subtitle: section.subtitle,
        itemIds: section.itemIds,
      }),
    );
    if (invalid) {
      toast.error(
        validateSectionDraft({
          title: invalid.title,
          subtitle: invalid.subtitle,
          itemIds: invalid.itemIds,
        }) ?? "A section is not ready to save.",
      );
      return;
    }

    setSaving(true);
    setConflict(null);
    const supabase = createClient();

    // The server assigns ids to brand-new sections, so the reorder below has to use the ids it
    // returned rather than the local "new-..." placeholders - otherwise reorder would reject
    // every freshly added section as "not a section on this page".
    const savedIds: string[] = [];

    for (const section of state.sections) {
      const { data, error } = await supabase.rpc("save_storefront_section", {
        p_id: section.id.startsWith("new-") ? null : section.id,
        p_page: page,
        p_title: section.title.trim(),
        p_subtitle: section.subtitle?.trim() || null,
        p_section_type: section.sectionType,
        p_active: section.active,
        p_item_ids: section.itemIds,
        p_expected_updated_at: section.id.startsWith("new-") ? null : section.updatedAt,
      });

      if (error) {
        setSaving(false);
        if (/changed by someone else/i.test(error.message)) {
          setConflict(
            "Someone else changed this page since you opened it. Nothing was overwritten - reload to see the current version before saving again.",
          );
        } else {
          toast.error(`Could not save “${section.title || "untitled section"}”: ${error.message}`);
        }
        return;
      }

      const returned = (data as { id?: string } | null)?.id;
      savedIds.push(returned && !returned.startsWith("new-") ? returned : section.id);
    }

    if (savedIds.some((id) => id.startsWith("new-"))) {
      // The server did not tell us an id it created; reloading is safer than guessing.
      toast.error("Saved, but the new section could not be placed in order. Reloading…");
      setSaving(false);
      await fetchAll();
      return;
    }

    const { error: reorderError } = await supabase.rpc("reorder_storefront_sections", {
      p_page: page,
      p_ordered_ids: savedIds,
    });
    if (reorderError) {
      toast.error(`Sections saved, but the order could not be stored: ${reorderError.message}`);
    } else {
      toast.success("Storefront published.");
    }

    setSaving(false);
    await fetchAll();
  };

  const previewSections = useMemo(
    () => state.sections.filter((section) => section.active),
    [state.sections],
  );

  const nameFor = (id: string) =>
    [...products, ...categories].find((item) => item.id === id)?.name ?? "Unavailable item";

  return (
    <div className="w-full space-y-6">
      <div className="flex flex-col gap-4 rounded-2xl border border-slate-200 bg-white p-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-xl font-extrabold text-slate-900">Storefront</h1>
          <p className="text-sm text-slate-500">
            Compose what the app shows. Prices and stock always come from the server, never from
            here.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <div
            role="tablist"
            aria-label="Which page to edit"
            className="flex rounded-xl bg-slate-100 p-1"
          >
            {(["home", "categories"] as StorefrontPage[]).map((tab) => (
              <button
                key={tab}
                role="tab"
                type="button"
                aria-selected={page === tab}
                onClick={() => setPage(tab)}
                className={cn(
                  "rounded-lg px-4 py-1.5 text-sm font-semibold capitalize transition",
                  page === tab ? "bg-white text-emerald-700 shadow" : "text-slate-600",
                )}
              >
                {tab}
              </button>
            ))}
          </div>
          <button
            type="button"
            onClick={addSection}
            disabled={state.sections.length >= MAX_SECTIONS_PER_PAGE}
            className="flex items-center gap-1.5 rounded-xl bg-emerald-600 px-3 py-2 text-sm font-bold text-white hover:bg-emerald-700 disabled:opacity-50"
          >
            <Plus className="h-4 w-4" /> Section
          </button>
          <button
            type="button"
            onClick={() => void save()}
            disabled={saving || dirtyCount === 0}
            className="flex items-center gap-1.5 rounded-xl bg-slate-900 px-3 py-2 text-sm font-bold text-white hover:bg-slate-800 disabled:opacity-40"
          >
            {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
            Publish
          </button>
        </div>
      </div>

      {state.notice && (
        <p className="rounded-xl border border-amber-300 bg-amber-50 px-4 py-2 text-sm text-amber-800">
          {state.notice}
        </p>
      )}

      {conflict && (
        <div className="flex items-start gap-3 rounded-xl border border-red-300 bg-red-50 p-4">
          <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-red-600" />
          <div className="flex-1">
            <p className="text-sm font-semibold text-red-800">Edit conflict</p>
            <p className="text-sm text-red-700">{conflict}</p>
          </div>
          <button
            type="button"
            onClick={() => void fetchAll()}
            className="flex items-center gap-1.5 rounded-lg bg-red-600 px-3 py-1.5 text-sm font-bold text-white hover:bg-red-700"
          >
            <RefreshCw className="h-4 w-4" /> Reload
          </button>
        </div>
      )}

      {loading && (
        <div className="flex items-center gap-2 rounded-xl border border-slate-200 bg-white p-6 text-sm text-slate-500">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading the storefront…
        </div>
      )}

      {loadError && (
        <div className="rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900">
          {loadError}
        </div>
      )}

      {!loading && !loadError && state.sections.length === 0 && (
        <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center">
          <p className="text-sm font-semibold text-slate-700">
            No sections yet, the app shows the default layout
          </p>
          <p className="mt-1 text-sm text-slate-500">
            Adding a section here replaces the default shelves on the {page} page.
          </p>
        </div>
      )}

      {!loading && !loadError && state.sections.length > 0 && (
        <ul className="space-y-3">
          {state.sections.map((section, index) => {
            const problem = validateSectionDraft({
              title: section.title,
              subtitle: section.subtitle,
              itemIds: section.itemIds,
            });
            return (
              <li
                key={section.id}
                className={cn(
                  "rounded-2xl border bg-white p-4",
                  problem ? "border-red-300" : "border-slate-200",
                )}
              >
                <div className="flex items-start gap-3">
                  <span
                    className="mt-2 text-slate-300"
                    aria-hidden
                    title="Drag the section in the preview list, or use the arrows"
                  >
                    <GripVertical className="h-5 w-5" />
                  </span>
                  <div className="min-w-0 flex-1 space-y-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <input
                        value={section.title}
                        onChange={(event) =>
                          dispatch({
                            type: "update",
                            id: section.id,
                            patch: { title: event.target.value },
                          })
                        }
                        aria-label="Section heading"
                        placeholder="Heading"
                        maxLength={40}
                        className="min-w-0 flex-1 rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-bold focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                      />
                      <span className="rounded-lg bg-slate-100 px-2 py-1 text-xs font-semibold text-slate-600">
                        {section.sectionType}
                      </span>
                    </div>
                    <input
                      value={section.subtitle ?? ""}
                      onChange={(event) =>
                        dispatch({
                          type: "update",
                          id: section.id,
                          patch: { subtitle: event.target.value },
                        })
                      }
                      aria-label="Optional subtitle"
                      placeholder="Optional subtitle"
                      maxLength={80}
                      className="w-full rounded-lg border border-slate-200 px-3 py-1.5 text-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                    />
                    {problem && <p className="text-xs text-red-600">{problem}</p>}
                    <div className="flex flex-wrap items-center gap-2 text-sm">
                      <button
                        type="button"
                        onClick={() => setPickerFor(section.id)}
                        className="rounded-lg border border-slate-300 px-3 py-1 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                      >
                        {section.itemIds.length} item
                        {section.itemIds.length === 1 ? "" : "s"} - choose
                      </button>
                      <button
                        type="button"
                        onClick={() => dispatch({ type: "toggleActive", id: section.id })}
                        aria-pressed={section.active}
                        className="flex items-center gap-1.5 rounded-lg border border-slate-300 px-3 py-1 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                      >
                        {section.active ? (
                          <Eye className="h-3.5 w-3.5" />
                        ) : (
                          <EyeOff className="h-3.5 w-3.5" />
                        )}
                        {section.active ? "Visible" : "Hidden"}
                      </button>
                      <button
                        type="button"
                        onClick={() => dispatch({ type: "move", id: section.id, direction: -1 })}
                        disabled={index === 0}
                        aria-label="Move section up"
                        className="rounded-lg border border-slate-300 p-1.5 text-slate-600 hover:bg-slate-50 disabled:opacity-30"
                      >
                        <ArrowUp className="h-4 w-4" />
                      </button>
                      <button
                        type="button"
                        onClick={() => dispatch({ type: "move", id: section.id, direction: 1 })}
                        disabled={index === state.sections.length - 1}
                        aria-label="Move section down"
                        className="rounded-lg border border-slate-300 p-1.5 text-slate-600 hover:bg-slate-50 disabled:opacity-30"
                      >
                        <ArrowDown className="h-4 w-4" />
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          if (window.confirm(`Delete “${section.title || "this section"}”?`)) {
                            dispatch({ type: "remove", id: section.id });
                          }
                        }}
                        aria-label="Delete section"
                        className="ml-auto rounded-lg border border-slate-300 p-1.5 text-slate-500 hover:bg-red-50 hover:text-red-600"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </div>
                </div>
              </li>
            );
          })}
        </ul>
      )}

      {!loading && !loadError && state.sections.length > 0 && (
        <section className="rounded-2xl border border-slate-200 bg-white p-4">
          <h2 className="text-sm font-bold text-slate-900">Preview - {page}</h2>
          <p className="mt-1 text-xs text-slate-500">
            Products that are out of stock or inactive are hidden in the app, so a section can show
            fewer items here than it lists above.
          </p>
          <div className="mt-3 space-y-4">
            {previewSections.length === 0 && (
              <p className="text-sm text-slate-500">
                Every section is hidden, so the app keeps showing its default layout.
              </p>
            )}
            {previewSections.map((section) => (
              <div key={section.id}>
                <h3 className="text-sm font-extrabold text-slate-800">
                  {section.title || "Untitled section"}
                </h3>
                {section.subtitle && <p className="text-xs text-slate-500">{section.subtitle}</p>}
                <div
                  className={cn(
                    "mt-2 gap-2",
                    section.sectionType === "categories"
                      ? "grid grid-cols-4"
                      : "flex overflow-x-auto",
                  )}
                >
                  {section.itemIds.length === 0 && (
                    <p className="text-xs text-slate-400">No items chosen yet.</p>
                  )}
                  {section.itemIds.map((id) => {
                    const item = [...products, ...categories].find((x) => x.id === id);
                    const hidden = item && (item.active === false || item.inStock === false);
                    return (
                      <div
                        key={id}
                        className={cn(
                          "w-32 shrink-0 rounded-xl border border-slate-200 p-2 text-xs",
                          hidden && "opacity-50",
                        )}
                      >
                        {item?.imageUrl ? (
                          // eslint-disable-next-line @next/next/no-img-element
                          <img
                            src={item.imageUrl}
                            alt=""
                            className="mb-1 h-14 w-full rounded-lg bg-slate-100 object-cover"
                          />
                        ) : (
                          <span className="mb-1 block h-14 rounded-lg bg-slate-100" />
                        )}
                        <p className="truncate font-semibold text-slate-700">
                          {item?.name ?? nameFor(id)}
                        </p>
                        {typeof item?.pricePaise === "number" && (
                          <p className="text-slate-500">{formatRupeesWhole(item.pricePaise)}</p>
                        )}
                        {hidden && (
                          <p className="text-[10px] font-bold text-amber-700">HIDDEN IN APP</p>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      <ItemPicker
        open={Boolean(pickerFor)}
        sectionType={state.sections.find((s) => s.id === pickerFor)?.sectionType ?? "products"}
        items={pickerItems}
        selectedIds={state.sections.find((s) => s.id === pickerFor)?.itemIds ?? []}
        onClose={() => setPickerFor(null)}
        onConfirm={(ids) => {
          if (pickerFor) {
            dispatch({ type: "update", id: pickerFor, patch: { itemIds: ids } });
          }
          setPickerFor(null);
        }}
      />

      <Toaster position="top-right" />
    </div>
  );
}

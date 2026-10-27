"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { createClient } from "@/lib/supabase";
import { formatRupees } from "@/lib/money";
import { cn } from "@/lib/utils";
import {
  Tag,
  Plus,
  Trash2,
  CheckCircle,
  XCircle,
  AlertCircle,
  Loader2,
  Calendar,
  Layers,
  Sparkles,
  Search,
  Package,
  Globe,
  FolderTree,
  Check,
  X,
} from "lucide-react";
import { Toaster, toast } from "sonner";

type Category = {
  id: string;
  name: string;
  slug: string;
};

type Product = {
  id: string;
  name: string;
  sku: string;
  price_paise: number;
  category_id: string;
};

type Coupon = {
  id: string;
  code: string;
  description: string;
  discount_type: "percent_off" | "rupees_off" | "free_delivery";
  discount_value: number;
  applies_to: "all" | "categories" | "products";
  category_id: string | null;
  category_ids?: string[] | null;
  product_ids?: string[] | null;
  min_order_paise: number;
  max_discount_paise: number | null;
  starts_at: string;
  expires_at: string | null;
  usage_limit: number | null;
  times_used: number;
  active: boolean;
  created_at: string;
};

const supabase = createClient();

export default function CouponsPage() {
  const [coupons, setCoupons] = useState<Coupon[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // Form states
  const [code, setCode] = useState("");
  const [description, setDescription] = useState("");
  const [discountType, setDiscountType] = useState<"percent_off" | "rupees_off" | "free_delivery">(
    "percent_off",
  );
  const [discountValue, setDiscountValue] = useState("");
  const [appliesTo, setAppliesTo] = useState<"all" | "categories" | "products">("all");
  const [selectedCategoryIds, setSelectedCategoryIds] = useState<string[]>([]);
  const [selectedProductIds, setSelectedProductIds] = useState<string[]>([]);
  const [categorySearchQuery, setCategorySearchQuery] = useState("");
  const [productSearchQuery, setProductSearchQuery] = useState("");
  const [minOrderRupees, setMinOrderRupees] = useState("");
  const [maxDiscountRupees, setMaxDiscountRupees] = useState("");
  const [usageLimit, setUsageLimit] = useState("");
  const [expiresAt, setExpiresAt] = useState("");

  const categoryMap = useMemo(() => new Map(categories.map((c) => [c.id, c.name])), [categories]);

  const productMap = useMemo(() => new Map(products.map((p) => [p.id, p])), [products]);

  const fetchCouponsAndData = useCallback(async () => {
    try {
      setLoading(true);
      const [couponsRes, catsRes, prodsRes] = await Promise.all([
        supabase.from("coupons").select("*").order("created_at", { ascending: false }),
        supabase.from("categories").select("id, name, slug").eq("active", true).order("sort_order"),
        supabase
          .from("products")
          .select("id, name, sku, price_paise, category_id")
          .eq("active", true)
          .order("name"),
      ]);

      if (couponsRes.error) throw couponsRes.error;
      if (catsRes.error) throw catsRes.error;
      if (prodsRes.error) throw prodsRes.error;

      setCoupons(couponsRes.data || []);
      setCategories(catsRes.data || []);
      setProducts(prodsRes.data || []);
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : "Failed to load coupons");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchCouponsAndData();
  }, [fetchCouponsAndData]);

  const handleToggleActive = async (coupon: Coupon) => {
    try {
      const nextActive = !coupon.active;
      const { error } = await supabase
        .from("coupons")
        .update({ active: nextActive, updated_at: new Date().toISOString() })
        .eq("id", coupon.id);

      if (error) throw error;
      setCoupons((prev) =>
        prev.map((c) => (c.id === coupon.id ? { ...c, active: nextActive } : c)),
      );
      toast.success(`Coupon ${coupon.code} is now ${nextActive ? "Active" : "Disabled"}`);
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : "Could not toggle coupon status");
    }
  };

  const handleDeleteCoupon = async (coupon: Coupon) => {
    if (!window.confirm(`Are you sure you want to delete coupon ${coupon.code}?`)) return;

    try {
      const { error } = await supabase.from("coupons").delete().eq("id", coupon.id);
      if (error) throw error;
      setCoupons((prev) => prev.filter((c) => c.id !== coupon.id));
      toast.success(`Coupon ${coupon.code} deleted`);
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : "Could not delete coupon");
    }
  };

  const handleCreateCoupon = async (e: React.FormEvent) => {
    e.preventDefault();
    const cleanCode = code.trim().toUpperCase();
    if (!cleanCode || cleanCode.length < 2) {
      toast.error("Please enter a valid coupon code (min 2 characters)");
      return;
    }

    let val = 0;
    if (discountType === "percent_off") {
      const num = parseFloat(discountValue);
      if (isNaN(num) || num <= 0 || num > 100) {
        toast.error("Please enter a valid discount percentage (1-100%)");
        return;
      }
      val = Math.round(num);
    } else if (discountType === "rupees_off") {
      const num = parseFloat(discountValue);
      if (isNaN(num) || num <= 0) {
        toast.error("Please enter a valid rupee discount amount");
        return;
      }
      val = Math.round(num * 100);
    }

    if (appliesTo === "categories" && selectedCategoryIds.length === 0) {
      toast.error("Please select at least one category for this coupon.");
      return;
    }

    if (appliesTo === "products" && selectedProductIds.length === 0) {
      toast.error("Please select at least one product for this coupon.");
      return;
    }

    const minOrderPaise = minOrderRupees ? Math.round(parseFloat(minOrderRupees) * 100) : 0;
    const maxDiscountPaise = maxDiscountRupees
      ? Math.round(parseFloat(maxDiscountRupees) * 100)
      : null;
    const limit = usageLimit ? parseInt(usageLimit, 10) : null;

    try {
      setSubmitting(true);
      const { data, error } = await supabase
        .from("coupons")
        .insert({
          code: cleanCode,
          description: description.trim(),
          discount_type: discountType,
          discount_value: val,
          applies_to: appliesTo,
          category_id:
            appliesTo === "categories" && selectedCategoryIds.length > 0
              ? selectedCategoryIds[0]
              : null,
          category_ids: appliesTo === "categories" ? selectedCategoryIds : [],
          product_ids: appliesTo === "products" ? selectedProductIds : [],
          min_order_paise: minOrderPaise,
          max_discount_paise: maxDiscountPaise,
          usage_limit: limit,
          expires_at: expiresAt ? new Date(expiresAt).toISOString() : null,
          active: true,
        })
        .select()
        .single();

      if (error) throw error;

      toast.success(`Coupon ${cleanCode} created successfully!`);
      setCoupons((prev) => [data, ...prev]);
      setIsModalOpen(false);

      // Reset form
      setCode("");
      setDescription("");
      setDiscountType("percent_off");
      setDiscountValue("");
      setAppliesTo("all");
      setSelectedCategoryIds([]);
      setSelectedProductIds([]);
      setMinOrderRupees("");
      setMaxDiscountRupees("");
      setUsageLimit("");
      setExpiresAt("");
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : "Failed to create coupon");
    } finally {
      setSubmitting(false);
    }
  };

  const filteredCoupons = coupons.filter(
    (c) =>
      c.code.toLowerCase().includes(search.toLowerCase()) ||
      c.description.toLowerCase().includes(search.toLowerCase()),
  );

  const filteredCategoriesForModal = categories.filter((c) =>
    c.name.toLowerCase().includes(categorySearchQuery.toLowerCase()),
  );

  const filteredProductsForModal = products.filter(
    (p) =>
      p.name.toLowerCase().includes(productSearchQuery.toLowerCase()) ||
      p.sku.toLowerCase().includes(productSearchQuery.toLowerCase()),
  );

  const toggleCategorySelection = (id: string) => {
    setSelectedCategoryIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id],
    );
  };

  const toggleProductSelection = (id: string) => {
    setSelectedProductIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id],
    );
  };

  const renderCouponScope = (coupon: Coupon) => {
    const scope = coupon.applies_to || (coupon.category_id ? "categories" : "all");

    if (scope === "categories") {
      const ids =
        coupon.category_ids && coupon.category_ids.length > 0
          ? coupon.category_ids
          : coupon.category_id
            ? [coupon.category_id]
            : [];
      const names = ids.map((id) => categoryMap.get(id) || "Category");

      return (
        <div className="space-y-1">
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-purple-50 text-purple-700 border border-purple-200">
            <FolderTree className="h-3 w-3" />
            {names.length === 1 ? names[0] : `${names.length} Categories`}
          </span>
          {names.length > 1 && (
            <p
              className="text-[11px] text-slate-500 max-w-[200px] truncate"
              title={names.join(", ")}
            >
              {names.join(", ")}
            </p>
          )}
        </div>
      );
    }

    if (scope === "products") {
      const ids = coupon.product_ids || [];
      const names = ids.map((id) => productMap.get(id)?.name || "Product");

      return (
        <div className="space-y-1">
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-50 text-amber-700 border border-amber-200">
            <Package className="h-3 w-3" />
            {names.length === 1 ? names[0] : `${names.length} Products`}
          </span>
          {names.length > 1 && (
            <p
              className="text-[11px] text-slate-500 max-w-[200px] truncate"
              title={names.join(", ")}
            >
              {names.join(", ")}
            </p>
          )}
        </div>
      );
    }

    return (
      <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-slate-100 text-slate-700 border border-slate-200">
        <Globe className="h-3 w-3 text-slate-500" /> All Products
      </span>
    );
  };

  return (
    <div className="space-y-6">
      <Toaster position="top-right" richColors />

      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 flex items-center gap-2">
            <Tag className="h-6 w-6 text-emerald-600" /> Coupons & Offers
          </h1>
          <p className="text-sm text-slate-500 mt-1">
            Create promotional codes applied store-wide, on multiple categories, or specific
            products.
          </p>
        </div>
        <button
          onClick={() => setIsModalOpen(true)}
          className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-emerald-700 transition"
        >
          <Plus className="h-4 w-4" /> Create Coupon
        </button>
      </div>

      {/* Filters & Search */}
      <div className="flex items-center gap-3">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
          <input
            type="text"
            placeholder="Search coupon code or description..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full rounded-xl border border-slate-200 bg-white pl-10 pr-4 py-2 text-sm text-slate-800 placeholder-slate-400 focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500 shadow-xs"
          />
        </div>
        <span className="text-xs font-semibold text-slate-500">
          {filteredCoupons.length} coupon{filteredCoupons.length !== 1 ? "s" : ""}
        </span>
      </div>

      {/* Coupons Table */}
      {loading ? (
        <div className="flex flex-col items-center justify-center p-12 text-slate-400">
          <Loader2 className="h-8 w-8 animate-spin mb-3 text-emerald-600" />
          <p className="text-sm">Loading coupons...</p>
        </div>
      ) : filteredCoupons.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-12 text-center">
          <Tag className="mx-auto h-12 w-12 text-slate-300 mb-3" />
          <h3 className="text-base font-semibold text-slate-800">No coupons found</h3>
          <p className="text-sm text-slate-500 mt-1 max-w-md mx-auto">
            {search
              ? "No coupons match your search query."
              : "Get started by creating your first promotional coupon for your customers."}
          </p>
          {!search && (
            <button
              onClick={() => setIsModalOpen(true)}
              className="mt-4 inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-700 transition"
            >
              <Plus className="h-4 w-4" /> Create First Coupon
            </button>
          )}
        </div>
      ) : (
        <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xs">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-slate-600">
              <thead className="bg-slate-50 text-xs font-semibold text-slate-500 uppercase tracking-wider border-b border-slate-200">
                <tr>
                  <th className="px-5 py-3.5">Code & Description</th>
                  <th className="px-4 py-3.5">Type & Value</th>
                  <th className="px-4 py-3.5">Scope</th>
                  <th className="px-4 py-3.5">Rules</th>
                  <th className="px-4 py-3.5">Redemptions</th>
                  <th className="px-4 py-3.5">Status</th>
                  <th className="px-4 py-3.5 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filteredCoupons.map((coupon) => {
                  const isExpired = coupon.expires_at && new Date(coupon.expires_at) <= new Date();
                  const isMaxed = coupon.usage_limit && coupon.times_used >= coupon.usage_limit;

                  return (
                    <tr key={coupon.id} className="hover:bg-slate-50/60 transition">
                      <td className="px-5 py-4">
                        <div className="flex items-center gap-2.5">
                          <span className="font-mono font-bold text-slate-900 bg-slate-100 px-2.5 py-1 rounded-md text-xs border border-slate-200">
                            {coupon.code}
                          </span>
                        </div>
                        {coupon.description && (
                          <p className="text-xs text-slate-500 mt-1 max-w-xs truncate">
                            {coupon.description}
                          </p>
                        )}
                      </td>
                      <td className="px-4 py-4">
                        {coupon.discount_type === "percent_off" && (
                          <span className="inline-flex items-center gap-1 font-semibold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full text-xs border border-emerald-200">
                            <Sparkles className="h-3 w-3" /> {coupon.discount_value}% OFF
                          </span>
                        )}
                        {coupon.discount_type === "rupees_off" && (
                          <span className="inline-flex items-center gap-1 font-semibold text-blue-700 bg-blue-50 px-2 py-0.5 rounded-full text-xs border border-blue-200">
                            ₹{coupon.discount_value / 100} OFF
                          </span>
                        )}
                        {coupon.discount_type === "free_delivery" && (
                          <span className="inline-flex items-center gap-1 font-semibold text-purple-700 bg-purple-50 px-2 py-0.5 rounded-full text-xs border border-purple-200">
                            Free Delivery
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-4">{renderCouponScope(coupon)}</td>
                      <td className="px-4 py-4 text-xs space-y-1">
                        {coupon.min_order_paise > 0 && (
                          <div>
                            Min order:{" "}
                            <span className="font-semibold text-slate-800">
                              {formatRupees(coupon.min_order_paise)}
                            </span>
                          </div>
                        )}
                        {coupon.max_discount_paise && (
                          <div>
                            Max cap:{" "}
                            <span className="font-semibold text-slate-800">
                              {formatRupees(coupon.max_discount_paise)}
                            </span>
                          </div>
                        )}
                        {coupon.expires_at ? (
                          <div
                            className={cn(
                              "flex items-center gap-1",
                              isExpired ? "text-rose-600 font-semibold" : "text-slate-500",
                            )}
                          >
                            <Calendar className="h-3 w-3" />
                            {isExpired ? "Expired " : "Expires "}
                            {new Date(coupon.expires_at).toLocaleDateString()}
                          </div>
                        ) : (
                          <div className="text-slate-400">No expiry</div>
                        )}
                      </td>
                      <td className="px-4 py-4 text-xs">
                        <span className="font-semibold text-slate-800">{coupon.times_used}</span>
                        {coupon.usage_limit ? ` / ${coupon.usage_limit}` : " used"}
                        {isMaxed && (
                          <p className="text-[11px] text-amber-600 font-medium">Limit reached</p>
                        )}
                      </td>
                      <td className="px-4 py-4">
                        <button
                          onClick={() => handleToggleActive(coupon)}
                          className={cn(
                            "inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold transition cursor-pointer",
                            coupon.active && !isExpired && !isMaxed
                              ? "bg-emerald-100 text-emerald-800 hover:bg-emerald-200"
                              : "bg-slate-100 text-slate-600 hover:bg-slate-200",
                          )}
                        >
                          {coupon.active && !isExpired && !isMaxed ? (
                            <>
                              <CheckCircle className="h-3 w-3 text-emerald-600" /> Active
                            </>
                          ) : (
                            <>
                              <XCircle className="h-3 w-3 text-slate-400" /> Disabled
                            </>
                          )}
                        </button>
                      </td>
                      <td className="px-4 py-4 text-right">
                        <button
                          onClick={() => handleDeleteCoupon(coupon)}
                          className="p-1.5 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition"
                          title="Delete coupon"
                          aria-label="Delete coupon"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Create Coupon Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs overflow-y-auto">
          <div className="w-full max-w-xl my-8 rounded-2xl bg-white p-6 shadow-2xl border border-slate-100 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between pb-4 border-b border-slate-100 sticky top-0 bg-white z-10">
              <h2 className="text-lg font-bold text-slate-900 flex items-center gap-2">
                <Tag className="h-5 w-5 text-emerald-600" /> Create New Coupon
              </h2>
              <button
                onClick={() => setIsModalOpen(false)}
                className="text-slate-400 hover:text-slate-600 p-1"
                aria-label="Close modal"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleCreateCoupon} className="space-y-4 pt-4">
              <div className="grid grid-cols-2 gap-3">
                <div className="col-span-2 sm:col-span-1">
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Coupon Code *
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. FESTIVE20"
                    value={code}
                    onChange={(e) => setCode(e.target.value.toUpperCase())}
                    className="w-full rounded-xl border border-slate-300 px-3 py-2 text-sm font-mono uppercase focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
                  />
                </div>
                <div className="col-span-2 sm:col-span-1">
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Discount Type *
                  </label>
                  <select
                    value={discountType}
                    onChange={(e) =>
                      setDiscountType(
                        e.target.value as "percent_off" | "rupees_off" | "free_delivery",
                      )
                    }
                    className="w-full rounded-xl border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
                  >
                    <option value="percent_off">Percentage Off (%)</option>
                    <option value="rupees_off">Rupees Off (₹)</option>
                    <option value="free_delivery">Free Delivery</option>
                  </select>
                </div>
              </div>

              {discountType !== "free_delivery" && (
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    {discountType === "percent_off"
                      ? "Discount Percentage (%) *"
                      : "Discount Amount (₹) *"}
                  </label>
                  <input
                    type="number"
                    step="any"
                    required
                    min={discountType === "percent_off" ? "1" : "0.5"}
                    max={discountType === "percent_off" ? "100" : undefined}
                    placeholder={discountType === "percent_off" ? "e.g. 15 for 15%" : "e.g. 50"}
                    value={discountValue}
                    onChange={(e) => setDiscountValue(e.target.value)}
                    className="w-full rounded-xl border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
                  />
                </div>
              )}

              {/* Scope Selector: All / Categories / Products */}
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                  Applies To *
                </label>
                <div className="grid grid-cols-3 gap-2">
                  <button
                    type="button"
                    onClick={() => setAppliesTo("all")}
                    className={cn(
                      "flex flex-col items-center justify-center p-3 rounded-xl border text-xs font-semibold transition gap-1.5",
                      appliesTo === "all"
                        ? "border-emerald-600 bg-emerald-50 text-emerald-800 shadow-xs"
                        : "border-slate-200 bg-slate-50/70 text-slate-600 hover:bg-slate-100",
                    )}
                  >
                    <Globe className="h-4 w-4" />
                    Entire Store
                  </button>
                  <button
                    type="button"
                    onClick={() => setAppliesTo("categories")}
                    className={cn(
                      "flex flex-col items-center justify-center p-3 rounded-xl border text-xs font-semibold transition gap-1.5",
                      appliesTo === "categories"
                        ? "border-purple-600 bg-purple-50 text-purple-800 shadow-xs"
                        : "border-slate-200 bg-slate-50/70 text-slate-600 hover:bg-slate-100",
                    )}
                  >
                    <FolderTree className="h-4 w-4" />
                    Categories
                  </button>
                  <button
                    type="button"
                    onClick={() => setAppliesTo("products")}
                    className={cn(
                      "flex flex-col items-center justify-center p-3 rounded-xl border text-xs font-semibold transition gap-1.5",
                      appliesTo === "products"
                        ? "border-amber-600 bg-amber-50 text-amber-800 shadow-xs"
                        : "border-slate-200 bg-slate-50/70 text-slate-600 hover:bg-slate-100",
                    )}
                  >
                    <Package className="h-4 w-4" />
                    Products
                  </button>
                </div>
              </div>

              {/* Specific Categories Multi-Selection */}
              {appliesTo === "categories" && (
                <div className="space-y-2 rounded-xl border border-purple-200 bg-purple-50/30 p-3.5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-purple-900">
                      Select Categories ({selectedCategoryIds.length} selected)
                    </span>
                    <div className="flex items-center gap-2 text-xs">
                      <button
                        type="button"
                        onClick={() => setSelectedCategoryIds(categories.map((c) => c.id))}
                        className="text-purple-700 hover:underline font-medium"
                      >
                        Select All
                      </button>
                      <span>•</span>
                      <button
                        type="button"
                        onClick={() => setSelectedCategoryIds([])}
                        className="text-slate-500 hover:underline font-medium"
                      >
                        Clear
                      </button>
                    </div>
                  </div>

                  <div className="relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-slate-400" />
                    <input
                      type="text"
                      placeholder="Search categories..."
                      value={categorySearchQuery}
                      onChange={(e) => setCategorySearchQuery(e.target.value)}
                      className="w-full rounded-lg border border-purple-200 bg-white pl-8 pr-3 py-1.5 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-1 focus:ring-purple-500"
                    />
                  </div>

                  {/* Selected Category Tags */}
                  {selectedCategoryIds.length > 0 && (
                    <div className="flex flex-wrap gap-1.5 pt-1">
                      {selectedCategoryIds.map((id) => {
                        const name = categoryMap.get(id) || "Category";
                        return (
                          <span
                            key={id}
                            className="inline-flex items-center gap-1 rounded-md bg-purple-100 px-2 py-0.5 text-[11px] font-medium text-purple-800"
                          >
                            {name}
                            <button
                              type="button"
                              onClick={() => toggleCategorySelection(id)}
                              className="text-purple-600 hover:text-purple-900 ml-0.5"
                            >
                              <X className="h-3 w-3" />
                            </button>
                          </span>
                        );
                      })}
                    </div>
                  )}

                  {/* Category Checklist */}
                  <div className="max-h-36 overflow-y-auto divide-y divide-purple-100 rounded-lg border border-purple-200 bg-white">
                    {filteredCategoriesForModal.map((cat) => {
                      const isSelected = selectedCategoryIds.includes(cat.id);
                      return (
                        <label
                          key={cat.id}
                          className={cn(
                            "flex items-center gap-2.5 px-3 py-2 text-xs cursor-pointer transition",
                            isSelected
                              ? "bg-purple-50/70 font-semibold text-purple-900"
                              : "hover:bg-slate-50 text-slate-700",
                          )}
                        >
                          <input
                            type="checkbox"
                            checked={isSelected}
                            onChange={() => toggleCategorySelection(cat.id)}
                            className="rounded border-purple-300 text-purple-600 focus:ring-purple-500"
                          />
                          <span>{cat.name}</span>
                        </label>
                      );
                    })}
                  </div>
                </div>
              )}

              {/* Specific Products Multi-Selection */}
              {appliesTo === "products" && (
                <div className="space-y-2 rounded-xl border border-amber-200 bg-amber-50/30 p-3.5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-amber-900">
                      Select Products ({selectedProductIds.length} selected)
                    </span>
                    {selectedProductIds.length > 0 && (
                      <button
                        type="button"
                        onClick={() => setSelectedProductIds([])}
                        className="text-xs text-slate-500 hover:underline font-medium"
                      >
                        Clear All
                      </button>
                    )}
                  </div>

                  <div className="relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-slate-400" />
                    <input
                      type="text"
                      placeholder="Search products by name or SKU..."
                      value={productSearchQuery}
                      onChange={(e) => setProductSearchQuery(e.target.value)}
                      className="w-full rounded-lg border border-amber-200 bg-white pl-8 pr-3 py-1.5 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-1 focus:ring-amber-500"
                    />
                  </div>

                  {/* Selected Product Tags */}
                  {selectedProductIds.length > 0 && (
                    <div className="flex flex-wrap gap-1.5 pt-1 max-h-24 overflow-y-auto">
                      {selectedProductIds.map((id) => {
                        const product = productMap.get(id);
                        return (
                          <span
                            key={id}
                            className="inline-flex items-center gap-1 rounded-md bg-amber-100 px-2 py-0.5 text-[11px] font-medium text-amber-900"
                          >
                            <span className="max-w-[120px] truncate">
                              {product?.name || "Product"}
                            </span>
                            <button
                              type="button"
                              onClick={() => toggleProductSelection(id)}
                              className="text-amber-700 hover:text-amber-950 ml-0.5"
                            >
                              <X className="h-3 w-3" />
                            </button>
                          </span>
                        );
                      })}
                    </div>
                  )}

                  {/* Product Checklist */}
                  <div className="max-h-40 overflow-y-auto divide-y divide-amber-100 rounded-lg border border-amber-200 bg-white">
                    {filteredProductsForModal.length === 0 ? (
                      <p className="p-3 text-xs text-slate-400 text-center">No products found.</p>
                    ) : (
                      filteredProductsForModal.map((prod) => {
                        const isSelected = selectedProductIds.includes(prod.id);
                        const catName = categoryMap.get(prod.category_id);
                        return (
                          <label
                            key={prod.id}
                            className={cn(
                              "flex items-center justify-between px-3 py-2 text-xs cursor-pointer transition",
                              isSelected
                                ? "bg-amber-50/70 font-semibold text-amber-950"
                                : "hover:bg-slate-50 text-slate-700",
                            )}
                          >
                            <div className="flex items-center gap-2.5 min-w-0 pr-2">
                              <input
                                type="checkbox"
                                checked={isSelected}
                                onChange={() => toggleProductSelection(prod.id)}
                                className="rounded border-amber-300 text-amber-600 focus:ring-amber-500"
                              />
                              <div className="truncate">
                                <p className="truncate text-xs">{prod.name}</p>
                                {catName && (
                                  <p className="text-[10px] text-slate-400 font-normal">
                                    {catName}
                                  </p>
                                )}
                              </div>
                            </div>
                            <span className="text-[11px] font-mono text-slate-500 shrink-0">
                              {formatRupees(prod.price_paise)}
                            </span>
                          </label>
                        );
                      })
                    )}
                  </div>
                </div>
              )}

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Min Order Value (₹)
                  </label>
                  <input
                    type="number"
                    step="any"
                    min="0"
                    placeholder="e.g. 250"
                    value={minOrderRupees}
                    onChange={(e) => setMinOrderRupees(e.target.value)}
                    className="w-full rounded-xl border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Usage Limit (Optional)
                  </label>
                  <input
                    type="number"
                    min="1"
                    placeholder="e.g. 100"
                    value={usageLimit}
                    onChange={(e) => setUsageLimit(e.target.value)}
                    className="w-full rounded-xl border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                {discountType === "percent_off" && (
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                      Max Discount Cap (₹)
                    </label>
                    <input
                      type="number"
                      step="any"
                      min="0"
                      placeholder="e.g. 100"
                      value={maxDiscountRupees}
                      onChange={(e) => setMaxDiscountRupees(e.target.value)}
                      className="w-full rounded-xl border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
                    />
                  </div>
                )}
                <div className={discountType === "percent_off" ? "" : "col-span-2"}>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Expiry Date (Optional)
                  </label>
                  <input
                    type="date"
                    value={expiresAt}
                    onChange={(e) => setExpiresAt(e.target.value)}
                    className="w-full rounded-xl border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Description / Note
                </label>
                <input
                  type="text"
                  placeholder="e.g. Festive discount on Dal, Grains and Snacks"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  className="w-full rounded-xl border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
                />
              </div>

              <div className="flex items-center justify-end gap-2.5 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 transition"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-700 transition disabled:opacity-60"
                >
                  {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
                  Create Coupon
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

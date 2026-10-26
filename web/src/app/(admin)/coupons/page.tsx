"use client";

import { useCallback, useEffect, useState } from "react";
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
} from "lucide-react";
import { Toaster, toast } from "sonner";

type Category = {
  id: string;
  name: string;
  slug: string;
};

type Coupon = {
  id: string;
  code: string;
  description: string;
  discount_type: "percent_off" | "rupees_off" | "free_delivery";
  discount_value: number;
  category_id: string | null;
  min_order_paise: number;
  max_discount_paise: number | null;
  starts_at: string;
  expires_at: string | null;
  usage_limit: number | null;
  times_used: number;
  active: boolean;
  created_at: string;
  category?: { name: string } | null;
};

const supabase = createClient();

export default function CouponsPage() {
  const [coupons, setCoupons] = useState<Coupon[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
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
  const [categoryId, setCategoryId] = useState<string>("all");
  const [minOrderRupees, setMinOrderRupees] = useState("");
  const [maxDiscountRupees, setMaxDiscountRupees] = useState("");
  const [usageLimit, setUsageLimit] = useState("");
  const [expiresAt, setExpiresAt] = useState("");

  const fetchCouponsAndCategories = useCallback(async () => {
    try {
      setLoading(true);
      const [couponsRes, catsRes] = await Promise.all([
        supabase
          .from("coupons")
          .select("*, category:category_id (name)")
          .order("created_at", { ascending: false }),
        supabase.from("categories").select("id, name, slug").eq("active", true).order("sort_order"),
      ]);

      if (couponsRes.error) throw couponsRes.error;
      if (catsRes.error) throw catsRes.error;

      setCoupons(couponsRes.data || []);
      setCategories(catsRes.data || []);
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : "Failed to load coupons");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchCouponsAndCategories();
  }, [fetchCouponsAndCategories]);

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
          category_id: categoryId === "all" ? null : categoryId,
          min_order_paise: minOrderPaise,
          max_discount_paise: maxDiscountPaise,
          usage_limit: limit,
          expires_at: expiresAt ? new Date(expiresAt).toISOString() : null,
          active: true,
        })
        .select("*, category:category_id (name)")
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
      setCategoryId("all");
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
            Create and manage promo codes, category-specific discounts, and free delivery
            thresholds.
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
                      <td className="px-4 py-4">
                        <span className="inline-flex items-center gap-1 text-xs text-slate-700">
                          <Layers className="h-3.5 w-3.5 text-slate-400" />
                          {coupon.category ? coupon.category.name : "All Categories"}
                        </span>
                      </td>
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
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs">
          <div className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-2xl border border-slate-100">
            <div className="flex items-center justify-between pb-4 border-b border-slate-100">
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

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Category Scope
                </label>
                <select
                  value={categoryId}
                  onChange={(e) => setCategoryId(e.target.value)}
                  className="w-full rounded-xl border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
                >
                  <option value="all">All Categories (Whole Basket)</option>
                  {categories.map((cat) => (
                    <option key={cat.id} value={cat.id}>
                      Only in: {cat.name}
                    </option>
                  ))}
                </select>
              </div>

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
                  placeholder="e.g. Festive discount on Dal and Grains"
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

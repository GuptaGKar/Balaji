"use client";

import { useCallback, useEffect, useState } from "react";
import { createClient } from "@/lib/supabase";
import { storeSettingsSchema, type StoreSettings } from "@/lib/schemas";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Save, Loader2, Clock, Plus, Trash2, AlertTriangle } from "lucide-react";
import { Toaster, toast } from "sonner";

interface BannerItem {
  id: string;
  title: string;
  subtitle: string;
  image_url: string;
}

const emptySettings: StoreSettings = {
  store_open: true,
  timezone: "Asia/Kolkata",
  weekly_hours: { operating_hours: ["08:00 AM", "10:00 PM"] },
  contact_phone: "",
  estimated_window_text: "",
  delivery_fee_paise: 0,
  delivery_charge_threshold_paise: 0,
  free_delivery_threshold_paise: null,
  mid_tier_fee_paise: null,
  max_order_paise: null,
  cancellation_policy_text: "",
  tax_mode: "none",
  tax_rate_bps: 0,
  online_payment_enabled: false,
  pos_price_override_enabled: false,
};

const paiseToInput = (paise: number | null | undefined) =>
  paise === null || paise === undefined ? "" : (paise / 100).toString();

const parseRupees = (value: string): number | null => {
  if (value.trim() === "") return null;
  const rupees = Number(value);
  if (!Number.isFinite(rupees) || rupees < 0) return NaN;
  return Math.round(rupees * 100);
};

export default function SettingsPage() {
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState("");
  const [openTime, setOpenTime] = useState("08:00 AM");
  const [closeTime, setCloseTime] = useState("10:00 PM");
  const [deliveryFeeRupees, setDeliveryFeeRupees] = useState("");
  const [chargeThresholdRupees, setChargeThresholdRupees] = useState("");
  const [freeThresholdRupees, setFreeThresholdRupees] = useState("");
  const [midFeeRupees, setMidFeeRupees] = useState("");
  const [maxOrderRupees, setMaxOrderRupees] = useState("");
  const [banners, setBanners] = useState<BannerItem[]>([]);
  const [savedWeeklyHours, setSavedWeeklyHours] = useState<Record<string, unknown>>({});

  const form = useForm<StoreSettings>({
    resolver: zodResolver(storeSettingsSchema),
    defaultValues: emptySettings,
  });

  const loadSettings = useCallback(async () => {
    setLoading(true);
    setLoadError("");
    const supabase = createClient();
    const { data, error } = await supabase.from("store_settings").select("*").single();
    if (error || !data) {
      setLoadError(error?.message || "Store settings were not found");
      setLoading(false);
      return;
    }

    const settings = data as StoreSettings;
    form.reset({
      ...emptySettings,
      ...settings,
      free_delivery_threshold_paise: settings.free_delivery_threshold_paise ?? null,
      mid_tier_fee_paise: settings.mid_tier_fee_paise ?? null,
      max_order_paise: settings.max_order_paise ?? null,
    });
    setOpenTime(settings.weekly_hours?.operating_hours?.[0] || "08:00 AM");
    setCloseTime(settings.weekly_hours?.operating_hours?.[1] || "10:00 PM");
    setDeliveryFeeRupees(paiseToInput(settings.delivery_fee_paise));
    setChargeThresholdRupees(paiseToInput(settings.delivery_charge_threshold_paise));
    setFreeThresholdRupees(paiseToInput(settings.free_delivery_threshold_paise));
    setMidFeeRupees(paiseToInput(settings.mid_tier_fee_paise));
    setMaxOrderRupees(paiseToInput(settings.max_order_paise));
    setBanners(
      Array.isArray(settings.weekly_hours?.promotional_banners)
        ? settings.weekly_hours.promotional_banners
        : [],
    );
    setSavedWeeklyHours((settings.weekly_hours || {}) as Record<string, unknown>);
    setLoading(false);
  }, [form]);

  useEffect(() => {
    loadSettings();
  }, [loadSettings]);

  const addBanner = () => {
    const newBanner: BannerItem = {
      id: "b-" + Date.now(),
      title: "Special Offer Banner",
      subtitle: "Exclusive deals on groceries",
      image_url: "",
    };
    setBanners((current) => [...current, newBanner]);
  };

  const removeBanner = (id: string) => {
    setBanners((current) => current.filter((banner) => banner.id !== id));
  };

  const updateBanner = (id: string, field: keyof BannerItem, value: string) => {
    setBanners((current) =>
      current.map((banner) => (banner.id === id ? { ...banner, [field]: value } : banner)),
    );
  };

  const onSubmit = async (data: StoreSettings) => {
    setSaveError("");

    const deliveryFee = parseRupees(deliveryFeeRupees);
    const chargeThreshold = parseRupees(chargeThresholdRupees);
    const freeThreshold = parseRupees(freeThresholdRupees);
    const midFee = parseRupees(midFeeRupees);
    const maxOrder = parseRupees(maxOrderRupees);

    if (deliveryFee === null || Number.isNaN(deliveryFee)) {
      setSaveError("Enter a valid delivery fee");
      return;
    }
    if (chargeThreshold === null || Number.isNaN(chargeThreshold)) {
      setSaveError("Enter a valid delivery-charge threshold");
      return;
    }
    if (Number.isNaN(freeThreshold) || Number.isNaN(midFee) || Number.isNaN(maxOrder)) {
      setSaveError("Enter valid amounts for thresholds and fees");
      return;
    }
    if (freeThreshold !== null && freeThreshold < chargeThreshold) {
      setSaveError("Free-delivery threshold must be empty or at least the delivery-charge threshold");
      return;
    }
    if (!openTime.trim() || !closeTime.trim()) {
      setSaveError("Opening and closing times are required");
      return;
    }
    const invalidBanner = banners.find(
      (banner) =>
        !banner.title.trim() ||
        !/^https?:\/\//i.test(banner.image_url.trim()),
    );
    if (invalidBanner) {
      setSaveError("Every banner needs a title and an http(s) image URL");
      return;
    }

    setSaving(true);
    try {
      const supabase = createClient();
      const weeklyHours = {
        ...savedWeeklyHours,
        operating_hours: [openTime.trim(), closeTime.trim()],
        promotional_banners: banners,
      };

      const { data: result, error } = await supabase.rpc("save_settings", {
        p_patch: {
          store_open: data.store_open,
          timezone: data.timezone || "Asia/Kolkata",
          weekly_hours: weeklyHours,
          contact_phone: data.contact_phone || null,
          estimated_window_text: data.estimated_window_text || null,
          cancellation_policy_text: data.cancellation_policy_text || null,
          delivery_fee_paise: deliveryFee,
          delivery_charge_threshold_paise: chargeThreshold,
          free_delivery_threshold_paise: freeThreshold,
          mid_tier_fee_paise: midFee,
          max_order_paise: maxOrder,
          online_payment_enabled: false,
        },
      });
      if (error) throw error;
      if (result?.error) throw new Error(result.message || result.error);

      const saved = result?.settings;
      if (saved) {
        setSavedWeeklyHours((saved.weekly_hours || {}) as Record<string, unknown>);
      }
      toast.success("Store settings and banners saved");
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : "Failed to save store settings");
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin text-emerald-600" />
      </div>
    );
  }

  if (loadError) {
    return (
      <div className="max-w-4xl">
        <div
          role="alert"
          className="flex items-center justify-between rounded-2xl border border-red-200 bg-red-50 px-5 py-4 text-sm font-medium text-red-700"
        >
          <span className="flex items-center gap-2">
            <AlertTriangle className="h-5 w-5" /> {loadError}
          </span>
          <button onClick={loadSettings} className="font-bold underline">
            Retry
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-4xl space-y-6 pb-12">
      <div>
        <h1 className="text-2xl font-extrabold text-slate-900">Store Settings &amp; Operations</h1>
        <p className="text-sm font-medium text-slate-500">
          Configure live store status, operating hours, delivery thresholds in ₹, and mobile app
          banners
        </p>
      </div>

      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
        {saveError && (
          <div
            role="alert"
            className="flex items-center gap-2 rounded-2xl border border-red-200 bg-red-50 px-5 py-4 text-sm font-medium text-red-700"
          >
            <AlertTriangle className="h-5 w-5" /> {saveError}
          </div>
        )}

        <Section
          title="Store Status & Operating Hours"
          description="Control live store availability and customer timing display"
        >
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1">
                Live Store Availability
              </label>
              <select
                {...form.register("store_open", {
                  setValueAs: (value) => value === "true" || value === true,
                })}
                className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 font-semibold focus:ring-2 focus:ring-emerald-500"
              >
                <option value="true">🟢 Store Open (Accepting Orders)</option>
                <option value="false">🔴 Store Closed (Pause Deliveries)</option>
              </select>
            </div>

            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1">
                Contact Support Phone
              </label>
              <input
                {...form.register("contact_phone")}
                className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 font-medium focus:ring-2 focus:ring-emerald-500"
              />
            </div>

            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1">
                Store Opening Time
              </label>
              <div className="relative">
                <Clock className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
                <input
                  type="text"
                  value={openTime}
                  onChange={(e) => setOpenTime(e.target.value)}
                  placeholder="e.g. 08:00 AM"
                  className="w-full pl-10 pr-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 font-medium focus:ring-2 focus:ring-emerald-500"
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1">
                Store Closing Time
              </label>
              <div className="relative">
                <Clock className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
                <input
                  type="text"
                  value={closeTime}
                  onChange={(e) => setCloseTime(e.target.value)}
                  placeholder="e.g. 10:00 PM"
                  className="w-full pl-10 pr-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 font-medium focus:ring-2 focus:ring-emerald-500"
                />
              </div>
            </div>

            <div className="md:col-span-2">
              <label className="block text-sm font-semibold text-slate-700 mb-1">
                Estimated Delivery Window Notice
              </label>
              <textarea
                {...form.register("estimated_window_text")}
                rows={2}
                className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 text-sm focus:ring-2 focus:ring-emerald-500"
              />
            </div>

            <div className="md:col-span-2">
              <label className="block text-sm font-semibold text-slate-700 mb-1">
                Cancellation Policy
              </label>
              <textarea
                {...form.register("cancellation_policy_text")}
                rows={2}
                className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 text-sm focus:ring-2 focus:ring-emerald-500"
              />
            </div>
          </div>
        </Section>

        <Section
          title="Mobile App Promotional Banners"
          description="Manage rotating hero banners shown at the top of the Android app"
        >
          <div className="space-y-4">
            {banners.map((banner, index) => (
              <div
                key={banner.id}
                className="p-4 bg-slate-50 border border-slate-200 rounded-2xl space-y-3"
              >
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-emerald-800 bg-emerald-100 px-2.5 py-0.5 rounded-lg">
                    Banner #{index + 1}
                  </span>
                  <button
                    type="button"
                    onClick={() => removeBanner(banner.id)}
                    className="p-1 text-slate-400 hover:text-red-600 rounded-lg"
                    aria-label={`Remove banner ${index + 1}`}
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                      Banner Headline Title
                    </label>
                    <input
                      type="text"
                      value={banner.title}
                      onChange={(e) => updateBanner(banner.id, "title", e.target.value)}
                      className="w-full px-3 py-2 bg-white border border-slate-300 rounded-xl text-sm focus:ring-2 focus:ring-emerald-500"
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                      Subtitle / Deal Offer
                    </label>
                    <input
                      type="text"
                      value={banner.subtitle}
                      onChange={(e) => updateBanner(banner.id, "subtitle", e.target.value)}
                      className="w-full px-3 py-2 bg-white border border-slate-300 rounded-xl text-sm focus:ring-2 focus:ring-emerald-500"
                    />
                  </div>

                  <div className="md:col-span-2">
                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                      Banner Image URL
                    </label>
                    <input
                      type="url"
                      value={banner.image_url}
                      onChange={(e) => updateBanner(banner.id, "image_url", e.target.value)}
                      className="w-full px-3 py-2 bg-white border border-slate-300 rounded-xl text-sm font-mono focus:ring-2 focus:ring-emerald-500"
                      placeholder="https://..."
                    />
                  </div>
                </div>
              </div>
            ))}

            {banners.length === 0 && (
              <p className="text-sm text-slate-500">No promotional banners configured.</p>
            )}

            <button
              type="button"
              onClick={addBanner}
              className="flex items-center gap-2 px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-800 text-xs font-bold rounded-xl border border-slate-300 transition-all"
            >
              <Plus className="h-4 w-4" /> Add Another Banner
            </button>
          </div>
        </Section>

        <Section
          title="Delivery Fee Configuration"
          description="Amounts are stored as integer paise and applied by the server pricing engine"
        >
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1">
                Standard Delivery Fee (₹)
              </label>
              <input
                type="number"
                min="0"
                step="0.01"
                value={deliveryFeeRupees}
                onChange={(e) => setDeliveryFeeRupees(e.target.value)}
                className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 font-bold focus:ring-2 focus:ring-emerald-500"
                placeholder="30"
              />
              <span className="text-xs text-slate-500 mt-1 block">
                Charged when the eligible subtotal is below the delivery-charge threshold
              </span>
            </div>

            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1">
                Delivery-Charge Threshold (₹)
              </label>
              <input
                type="number"
                min="0"
                step="0.01"
                value={chargeThresholdRupees}
                onChange={(e) => setChargeThresholdRupees(e.target.value)}
                className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 font-bold focus:ring-2 focus:ring-emerald-500"
                placeholder="Enter amount"
              />
              <span className="text-xs text-slate-500 mt-1 block">
                Subtotal at which the standard fee stops applying (0 disables the fee)
              </span>
            </div>

            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1">
                Free-Delivery Threshold (₹)
              </label>
              <input
                type="number"
                min="0"
                step="0.01"
                value={freeThresholdRupees}
                onChange={(e) => setFreeThresholdRupees(e.target.value)}
                className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 font-bold focus:ring-2 focus:ring-emerald-500"
                placeholder="Leave empty for no free-delivery tier"
              />
              <span className="text-xs text-slate-500 mt-1 block">
                Optional; must be empty or at least the delivery-charge threshold
              </span>
            </div>

            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1">
                Mid-Tier Fee (₹)
              </label>
              <input
                type="number"
                min="0"
                step="0.01"
                value={midFeeRupees}
                onChange={(e) => setMidFeeRupees(e.target.value)}
                className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 font-bold focus:ring-2 focus:ring-emerald-500"
                placeholder="Leave empty to reuse the standard fee"
              />
              <span className="text-xs text-slate-500 mt-1 block">
                Optional fee between the charge threshold and free-delivery threshold
              </span>
            </div>

            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1">
                Maximum Order Amount (₹)
              </label>
              <input
                type="number"
                min="0"
                step="0.01"
                value={maxOrderRupees}
                onChange={(e) => setMaxOrderRupees(e.target.value)}
                className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 font-bold focus:ring-2 focus:ring-emerald-500"
                placeholder="Leave empty for no cap"
              />
            </div>

            <div className="md:col-span-2 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs font-medium text-amber-800">
              Online payments are disabled until the payment gate is approved. Saving settings
              cannot enable them.
            </div>
          </div>
        </Section>

        <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-200">
          <button
            type="submit"
            disabled={saving}
            className="flex items-center gap-2 px-5 py-2.5 rounded-xl font-bold text-white bg-emerald-600 hover:bg-emerald-700 focus:ring-2 focus:ring-emerald-500 shadow-md shadow-emerald-600/20 disabled:opacity-50"
          >
            {saving ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin" />
                Saving...
              </>
            ) : (
              <>
                <Save className="h-4 w-4" />
                Save Store Settings &amp; Banners
              </>
            )}
          </button>
        </div>
      </form>
      <Toaster position="top-right" />
    </div>
  );
}

function Section({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children: React.ReactNode;
}) {
  return (
    <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-4 shadow-sm">
      <div>
        <h2 className="text-lg font-bold text-slate-900">{title}</h2>
        <p className="text-xs text-slate-500">{description}</p>
      </div>
      {children}
    </div>
  );
}

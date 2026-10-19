"use client";

import { useEffect, useState } from "react";
import { createClient } from "@/lib/supabase";
import { storeSettingsSchema, type StoreSettings } from "@/lib/schemas";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { cn } from "@/lib/utils";
import { Save, Loader2, Clock, Image as ImageIcon, Plus, Trash2 } from "lucide-react";
import { Toaster, toast } from "sonner";

interface BannerItem {
  id: string;
  title: string;
  subtitle: string;
  image_url: string;
}

const defaultBanners: BannerItem[] = [
  {
    id: "b-1",
    title: "Fresh Atta & Groceries Delivered ⚡",
    subtitle: "Up to 30% OFF daily essentials",
    image_url: "https://images.unsplash.com/photo-1542838132-92c53300491e?w=800&q=80",
  },
  {
    id: "b-2",
    title: "Free Local Home Delivery 🎉",
    subtitle: "On all orders above ₹250",
    image_url: "https://images.unsplash.com/photo-1578916171728-46686eac8d58?w=800&q=80",
  },
];

const defaultSettings: StoreSettings = {
  store_open: true,
  timezone: "Asia/Kolkata",
  weekly_hours: {
    operating_hours: ["08:00 AM", "10:00 PM"],
    mon: ["08:00 AM", "10:00 PM"],
  },
  contact_phone: "9837803293",
  estimated_window_text:
    "Same-day delivery; the exact window is confirmed when the store accepts the order.",
  delivery_fee_paise: 3000,
  delivery_charge_threshold_paise: 25000,
  free_delivery_threshold_paise: 25000,
  mid_tier_fee_paise: null,
  max_order_paise: null,
  cancellation_policy_text: "Cancellation is allowed until the store accepts the order.",
  tax_mode: "none",
  tax_rate_bps: 0,
  online_payment_enabled: false,
  pos_price_override_enabled: false,
};

export default function SettingsPage() {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [openTime, setOpenTime] = useState("08:00 AM");
  const [closeTime, setCloseTime] = useState("10:00 PM");
  const [deliveryFeeRupees, setDeliveryFeeRupees] = useState<number>(30);
  const [freeDeliveryThresholdRupees, setFreeDeliveryThresholdRupees] = useState<number>(250);
  const [banners, setBanners] = useState<BannerItem[]>(defaultBanners);

  const form = useForm<StoreSettings>({
    resolver: zodResolver(storeSettingsSchema),
    defaultValues: defaultSettings,
  });

  useEffect(() => {
    const fetchSettings = async () => {
      const supabase = createClient();
      const { data, error } = await supabase.from("store_settings").select("*").single();
      if (!error && data) {
        form.reset({
          ...defaultSettings,
          ...data,
          free_delivery_threshold_paise: data.free_delivery_threshold_paise ?? null,
          mid_tier_fee_paise: data.mid_tier_fee_paise ?? null,
          max_order_paise: data.max_order_paise ?? null,
        });
        if (data.weekly_hours?.operating_hours?.[0]) setOpenTime(data.weekly_hours.operating_hours[0]);
        if (data.weekly_hours?.operating_hours?.[1]) setCloseTime(data.weekly_hours.operating_hours[1]);
        if (data.delivery_fee_paise != null) setDeliveryFeeRupees(data.delivery_fee_paise / 100);
        if (data.delivery_charge_threshold_paise != null) setFreeDeliveryThresholdRupees(data.delivery_charge_threshold_paise / 100);
        if (Array.isArray(data.weekly_hours?.promotional_banners)) {
          setBanners(data.weekly_hours.promotional_banners);
        }
      }
      setLoading(false);
    };
    fetchSettings();
  }, [form]);

  const addBanner = () => {
    const newB: BannerItem = {
      id: "b-" + Date.now(),
      title: "Special Offer Banner",
      subtitle: "Exclusive deals on groceries",
      image_url: "https://images.unsplash.com/photo-1542838132-92c53300491e?w=800&q=80",
    };
    setBanners([...banners, newB]);
  };

  const removeBanner = (id: string) => {
    setBanners(banners.filter((b) => b.id !== id));
  };

  const updateBanner = (id: string, field: keyof BannerItem, value: string) => {
    setBanners(banners.map((b) => (b.id === id ? { ...b, [field]: value } : b)));
  };

  const onSubmit = async (data: StoreSettings) => {
    setSaving(true);
    try {
      const supabase = createClient();
      const updatedWeeklyHours = {
        ...(typeof data.weekly_hours === "object" ? data.weekly_hours : defaultSettings.weekly_hours),
        operating_hours: [openTime, closeTime],
        promotional_banners: banners,
      };

      const calculatedDeliveryFeePaise = Math.round((deliveryFeeRupees || 0) * 100);
      const calculatedThresholdPaise = Math.round((freeDeliveryThresholdRupees || 0) * 100);

      const payload = {
        id: true,
        ...data,
        store_open: String(data.store_open) === "true" || data.store_open === true,
        weekly_hours: updatedWeeklyHours,
        delivery_fee_paise: calculatedDeliveryFeePaise,
        delivery_charge_threshold_paise: calculatedThresholdPaise,
        free_delivery_threshold_paise: calculatedThresholdPaise,
      };

      const { error } = await supabase.from("store_settings").upsert(payload);
      if (error) throw error;
      toast.success("Store settings & promotional banners updated! App & Web synchronized.");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to save settings");
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

  return (
    <div className="max-w-4xl space-y-6 pb-12">
      <div>
        <h1 className="text-2xl font-extrabold text-slate-900">Store Settings & Operations</h1>
        <p className="text-sm font-medium text-slate-500">Configure live store status, operating hours, delivery thresholds in ₹, and mobile app banners</p>
      </div>

      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
        {/* Store Basics */}
        <Section title="Store Status & Operating Hours" description="Control live store availability and customer timing display">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1">Live Store Availability</label>
              <select
                {...form.register("store_open")}
                className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 font-semibold focus:ring-2 focus:ring-emerald-500"
              >
                <option value="true">🟢 Store Open (Accepting Orders)</option>
                <option value="false">🔴 Store Closed (Pause Deliveries)</option>
              </select>
            </div>

            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1">Contact Support Phone</label>
              <input
                {...form.register("contact_phone")}
                className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 font-medium focus:ring-2 focus:ring-emerald-500"
              />
            </div>

            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1">Store Opening Time</label>
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
              <label className="block text-sm font-semibold text-slate-700 mb-1">Store Closing Time</label>
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
          </div>
        </Section>

        {/* Mobile App Promotional Banners */}
        <Section title="Mobile App Promotional Banners" description="Manage rotating hero banners shown at the top of the Android App">
          <div className="space-y-4">
            {banners.map((b, idx) => (
              <div key={b.id} className="p-4 bg-slate-50 border border-slate-200 rounded-2xl space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-emerald-800 bg-emerald-100 px-2.5 py-0.5 rounded-lg">
                    Banner #{idx + 1}
                  </span>
                  <button
                    type="button"
                    onClick={() => removeBanner(b.id)}
                    className="p-1 text-slate-400 hover:text-red-600 rounded-lg"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">Banner Headline Title</label>
                    <input
                      type="text"
                      value={b.title}
                      onChange={(e) => updateBanner(b.id, "title", e.target.value)}
                      className="w-full px-3 py-2 bg-white border border-slate-300 rounded-xl text-sm focus:ring-2 focus:ring-emerald-500"
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">Subtitle / Deal Offer</label>
                    <input
                      type="text"
                      value={b.subtitle}
                      onChange={(e) => updateBanner(b.id, "subtitle", e.target.value)}
                      className="w-full px-3 py-2 bg-white border border-slate-300 rounded-xl text-sm focus:ring-2 focus:ring-emerald-500"
                    />
                  </div>

                  <div className="md:col-span-2">
                    <label className="block text-xs font-semibold text-slate-700 mb-1">Banner Image URL</label>
                    <input
                      type="text"
                      value={b.image_url}
                      onChange={(e) => updateBanner(b.id, "image_url", e.target.value)}
                      className="w-full px-3 py-2 bg-white border border-slate-300 rounded-xl text-sm font-mono focus:ring-2 focus:ring-emerald-500"
                      placeholder="https://images.unsplash.com/..."
                    />
                  </div>
                </div>
              </div>
            ))}

            <button
              type="button"
              onClick={addBanner}
              className="flex items-center gap-2 px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-800 text-xs font-bold rounded-xl border border-slate-300 transition-all"
            >
              <Plus className="h-4 w-4" /> Add Another Banner
            </button>
          </div>
        </Section>

        {/* Delivery Fee Configuration (Rupees ₹) */}
        <Section
          title="Delivery Fee Configuration"
          description="Set thresholds and delivery charges in Rupees (₹)"
        >
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1">
                Delivery Charge Below Threshold (₹)
              </label>
              <input
                type="number"
                value={deliveryFeeRupees}
                onChange={(e) => setDeliveryFeeRupees(parseFloat(e.target.value) || 0)}
                className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 font-bold focus:ring-2 focus:ring-emerald-500"
                placeholder="30"
              />
              <span className="text-xs text-slate-500 mt-1 block">Flat delivery charge in Rupees (e.g. ₹30)</span>
            </div>

            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1">
                Free Delivery Threshold (₹)
              </label>
              <input
                type="number"
                value={freeDeliveryThresholdRupees}
                onChange={(e) => setFreeDeliveryThresholdRupees(parseFloat(e.target.value) || 0)}
                className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 font-bold focus:ring-2 focus:ring-emerald-500"
                placeholder="250"
              />
              <span className="text-xs text-slate-500 mt-1 block">Orders above this amount get FREE delivery (e.g. ₹250)</span>
            </div>
          </div>
        </Section>

        {/* Form Actions */}
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
                Save Store Settings & Banners
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
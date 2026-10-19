"use client";

import { useEffect, useState } from "react";
import { createClient } from "@/lib/supabase";
import { storeSettingsSchema, type StoreSettings } from "@/lib/schemas";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { cn } from "@/lib/utils";
import { formatRupees, parseRupees } from "@/lib/money";
import { Save, Loader2, RotateCcw, Clock, Store } from "lucide-react";
import { Toaster, toast } from "sonner";

const defaultSettings: StoreSettings = {
  store_open: true,
  timezone: "Asia/Kolkata",
  weekly_hours: {
    operating_hours: ["08:00 AM", "10:00 PM"],
    mon: ["08:00 AM", "10:00 PM"],
    tue: ["08:00 AM", "10:00 PM"],
    wed: ["08:00 AM", "10:00 PM"],
    thu: ["08:00 AM", "10:00 PM"],
    fri: ["08:00 AM", "10:00 PM"],
    sat: ["08:00 AM", "10:00 PM"],
    sun: ["08:00 AM", "10:00 PM"],
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

  const form = useForm<StoreSettings>({
    resolver: zodResolver(storeSettingsSchema),
    defaultValues: defaultSettings,
  });

  const watchedT1 = form.watch("delivery_charge_threshold_paise");
  const watchedF1 = form.watch("delivery_fee_paise");

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
      }
      setLoading(false);
    };
    fetchSettings();
  }, [form]);

  const onSubmit = async (data: StoreSettings) => {
    setSaving(true);
    try {
      const supabase = createClient();
      const updatedWeeklyHours = {
        ...(typeof data.weekly_hours === "object" ? data.weekly_hours : defaultSettings.weekly_hours),
        operating_hours: [openTime, closeTime],
      };

      const payload = {
        ...data,
        store_open: String(data.store_open) === "true" || data.store_open === true,
        weekly_hours: updatedWeeklyHours,
      };

      const { error } = await supabase.from("store_settings").upsert(payload);
      if (error) throw error;
      toast.success("Store settings updated! App & Web synchronized.");
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
    <div className="max-w-4xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Store Settings & Operations</h1>
        <p className="text-slate-500">Configure live store status, operating hours, and delivery fee thresholds</p>
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

        {/* Delivery Fee Configuration */}
        <Section
          title="Delivery Fee Configuration"
          description="Set thresholds and delivery charges for customer orders"
        >
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1">
                Delivery Charge Below Threshold (₹)
              </label>
              <input
                type="number"
                {...form.register("delivery_fee_paise", { valueAsNumber: true })}
                className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 font-medium focus:ring-2 focus:ring-emerald-500"
              />
              <span className="text-xs text-slate-500 mt-1 block">Value in paise (e.g. 3000 = ₹30)</span>
            </div>

            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1">
                Free Delivery Threshold (₹)
              </label>
              <input
                type="number"
                {...form.register("delivery_charge_threshold_paise", { valueAsNumber: true })}
                className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 font-medium focus:ring-2 focus:ring-emerald-500"
              />
              <span className="text-xs text-slate-500 mt-1 block">Value in paise (e.g. 25000 = ₹250)</span>
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
                Save Store Settings
              </>
            )}
          </button>
        </div>
      </form>
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
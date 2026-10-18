"use client";

import { useEffect, useState } from "react";
import { createClient } from "@/lib/supabase";
import { storeSettingsSchema, type StoreSettings } from "@/lib/schemas";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { cn } from "@/lib/utils";
import { formatRupees, parseRupees } from "@/lib/money";
import { Save, Loader2, RotateCcw } from "lucide-react";
import { Toaster, toast } from "sonner";

const defaultSettings: StoreSettings = {
  store_open: true,
  timezone: "Asia/Kolkata",
  weekly_hours: {
    mon: ["08:00", "22:00"],
    tue: ["08:00", "22:00"],
    wed: ["08:00", "22:00"],
    thu: ["08:00", "22:00"],
    fri: ["08:00", "22:00"],
    sat: ["08:00", "22:00"],
    sun: ["08:00", "22:00"],
  },
  contact_phone: "9837803293",
  estimated_window_text:
    "Same-day delivery; the exact window is confirmed when the store accepts the order.",
  delivery_fee_paise: 0,
  delivery_charge_threshold_paise: 0,
  free_delivery_threshold_paise: null,
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

  const form = useForm<StoreSettings>({
    resolver: zodResolver(storeSettingsSchema),
    defaultValues: defaultSettings,
  });

  const watchedT1 = form.watch("delivery_charge_threshold_paise");
  const watchedF1 = form.watch("delivery_fee_paise");
  const watchedT2 = form.watch("free_delivery_threshold_paise");
  const watchedF2 = form.watch("mid_tier_fee_paise");

  const feeRuleNote = computeFeeRule(watchedT1, watchedF1, watchedT2, watchedF2);

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
      }
      setLoading(false);
    };
    fetchSettings();
  }, [form]);

  const onSubmit = async (data: StoreSettings) => {
    setSaving(true);
    try {
      const supabase = createClient();
      const { error } = await supabase.from("store_settings").upsert(data);
      if (error) throw error;
      toast.success("Settings saved");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to save");
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin text-indigo-600" />
      </div>
    );
  }

  return (
    <div className="max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Store Settings</h1>
        <p className="text-gray-500">Configure store operations, delivery fees, and thresholds</p>
      </div>

      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
        {/* Store Basics */}
        <Section title="Store Basics" description="Basic store information and operating hours">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Store Open</label>
              <select
                {...form.register("store_open")}
                className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500"
              >
                <option value="true">Open</option>
                <option value="false">Closed (Pause)</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Timezone</label>
              <input
                {...form.register("timezone")}
                className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Contact Phone</label>
              <input
                {...form.register("contact_phone")}
                className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500"
              />
            </div>
            <div className="md:col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Estimated Delivery Window Text
              </label>
              <textarea
                {...form.register("estimated_window_text")}
                rows={2}
                className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500"
              />
            </div>
            <div className="md:col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Weekly Hours (JSON)
              </label>
              <textarea
                {...form.register("weekly_hours")}
                rows={4}
                className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500 font-mono text-sm"
              />
            </div>
          </div>
        </Section>

        {/* Delivery Fee Configuration */}
        <Section
          title="Delivery Fee Configuration"
          description="Set thresholds and fees. Changes take effect immediately."
        >
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Delivery Fee Below Threshold (F1) *
              </label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400">₹</span>
                <input
                  {...form.register("delivery_fee_paise", { valueAsNumber: true })}
                  type="number"
                  min="0"
                  step="1"
                  className="w-full pl-7 pr-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500"
                />
              </div>
              <p className="mt-1 text-xs text-gray-500">
                Fee charged when order subtotal is below delivery-charge threshold
              </p>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Delivery-Charge Threshold (T1) *
              </label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400">₹</span>
                <input
                  {...form.register("delivery_charge_threshold_paise", { valueAsNumber: true })}
                  type="number"
                  min="0"
                  step="1"
                  className="w-full pl-7 pr-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500"
                />
              </div>
              <p className="mt-1 text-xs text-gray-500">
                Orders below this subtotal pay F1; at/above this get free delivery (if T2 not set)
              </p>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Free-Delivery Threshold (T2) — Optional
              </label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400">₹</span>
                <input
                  {...form.register("free_delivery_threshold_paise", { valueAsNumber: true })}
                  type="number"
                  min="0"
                  step="1"
                  className="w-full pl-7 pr-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500"
                  placeholder="Optional"
                />
              </div>
              <p className="mt-1 text-xs text-gray-500">
                If set, F1 applies below T2; free at/above T2. T1 becomes inert.
              </p>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Mid-Tier Fee (F2) — Optional
              </label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400">₹</span>
                <input
                  {...form.register("mid_tier_fee_paise", { valueAsNumber: true })}
                  type="number"
                  min="0"
                  step="1"
                  className="w-full pl-7 pr-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500"
                  placeholder="Optional"
                />
              </div>
              <p className="mt-1 text-xs text-gray-500">
                Fee between T1 and T2 (requires T2). If T2 set but F2 empty, F1 applies.
              </p>
            </div>
          </div>

          {/* Live Preview */}
          <div className="mt-4 p-4 bg-gray-50 rounded-lg border border-gray-200">
            <h4 className="font-medium text-gray-900 mb-2">Live Fee Rule Preview</h4>
            <div className="text-sm text-gray-700 whitespace-pre-wrap font-mono">{feeRuleNote}</div>
          </div>
        </Section>

        {/* Advanced Settings */}
        <Section title="Advanced Settings" description="Additional store configuration">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Max Order Value (Optional)
              </label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400">₹</span>
                <input
                  {...form.register("max_order_paise", { valueAsNumber: true })}
                  type="number"
                  min="0"
                  step="1"
                  className="w-full pl-7 pr-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500"
                  placeholder="No limit"
                />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Cancellation Policy Text
              </label>
              <textarea
                {...form.register("cancellation_policy_text")}
                rows={2}
                className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Tax Mode</label>
              <select
                {...form.register("tax_mode")}
                className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500"
              >
                <option value="none">None</option>
                <option value="inclusive">Inclusive (GST included in prices)</option>
                <option value="exclusive">Exclusive (GST added on top)</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Tax Rate (basis points)
              </label>
              <input
                {...form.register("tax_rate_bps", { valueAsNumber: true })}
                type="number"
                min="0"
                max="10000"
                className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500"
                placeholder="e.g., 1800 for 18%"
              />
            </div>
            <div className="md:col-span-2 flex items-center gap-4">
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  {...form.register("online_payment_enabled")}
                  type="checkbox"
                  className="h-4 w-4 text-indigo-600 rounded border-gray-300"
                />
                <span className="text-sm text-gray-700">Enable Online Payments</span>
              </label>
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  {...form.register("pos_price_override_enabled")}
                  type="checkbox"
                  className="h-4 w-4 text-indigo-600 rounded border-gray-300"
                />
                <span className="text-sm text-gray-700">
                  Enable POS Price Override (Manager only)
                </span>
              </label>
            </div>
          </div>
        </Section>

        <div className="flex justify-end gap-3 pt-4 border-t border-gray-200">
          <button
            type="button"
            onClick={() => form.reset(defaultSettings)}
            className="px-4 py-2 text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50 flex items-center gap-2"
          >
            <RotateCcw className="h-4 w-4" /> Reset to Defaults
          </button>
          <button type="submit" disabled={saving} className="btn-primary flex items-center gap-2">
            {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
            {saving ? "Saving..." : "Save Settings"}
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
    <div className="bg-white rounded-lg border border-gray-200 p-6">
      <div className="mb-4">
        <h3 className="text-lg font-semibold text-gray-900">{title}</h3>
        <p className="text-sm text-gray-500">{description}</p>
      </div>
      {children}
    </div>
  );
}

function computeFeeRule(T1: number, F1: number, T2: number | null, F2: number | null): string {
  const fmt = (p: number | null) => (p ? formatRupees(p) : "—");
  const lines = [
    `F1 (Delivery fee) = ${fmt(F1)}`,
    `T1 (Delivery-charge threshold) = ${fmt(T1)}`,
    `T2 (Free-delivery threshold) = ${fmt(T2)}`,
    `F2 (Mid-tier fee) = ${fmt(F2)}`,
    "",
    "Fee calculation:",
  ];

  if (T2 === null) {
    lines.push(`  eligible < ${fmt(T1)}  →  ${fmt(F1)}`);
    lines.push(`  eligible ≥ ${fmt(T1)}  →  FREE`);
  } else {
    if (F2 === null) {
      lines.push(`  eligible < ${fmt(T2)}  →  ${fmt(F1)}`);
      lines.push(`  eligible ≥ ${fmt(T2)}  →  FREE`);
    } else {
      lines.push(`  eligible < ${fmt(T1)}  →  ${fmt(F1)}`);
      lines.push(`  ${fmt(T1)} ≤ eligible < ${fmt(T2)}  →  ${fmt(F2)}`);
      lines.push(`  eligible ≥ ${fmt(T2)}  →  FREE`);
    }
  }
  return lines.join("\n");
}

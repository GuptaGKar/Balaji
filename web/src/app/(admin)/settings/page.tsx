"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { createClient } from "@/lib/supabase";
import { storeSettingsSchema, type StoreSettings } from "@/lib/schemas";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { createUuid } from "@/lib/uuid";
import { SafeImage } from "@/components/SafeImage";
import {
  Save,
  Loader2,
  Clock,
  Plus,
  Trash2,
  AlertTriangle,
  RefreshCw,
  WifiOff,
  UploadCloud,
  X,
} from "lucide-react";
import { Toaster, toast } from "sonner";

interface BannerItem {
  id: string;
  title: string;
  subtitle: string;
  image_url: string;
}

const BANNER_IMAGE_BUCKET = "banner-images";
const BANNER_IMAGE_MAX_BYTES = 2 * 1024 * 1024;
const BANNER_IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"] as const;
const BANNER_IMAGE_URL_PREFIX = `${(process.env.NEXT_PUBLIC_SUPABASE_URL || "").replace(/\/$/, "")}/storage/v1/object/public/${BANNER_IMAGE_BUCKET}/`;

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

type BannerUploadState = {
  status: "idle" | "uploading" | "error";
  error: string | null;
  /** Local blob preview. Kept after a successful upload so the manager sees the exact file. */
  previewUrl: string | null;
};

const idleBannerUpload: BannerUploadState = {
  status: "idle",
  error: null,
  previewUrl: null,
};

const paiseToInput = (paise: number | null | undefined) =>
  paise === null || paise === undefined ? "" : (paise / 100).toString();

const parseRupees = (value: string): number | null => {
  if (value.trim() === "") return null;
  const rupees = Number(value);
  if (!Number.isFinite(rupees) || rupees < 0) return NaN;
  return Math.round(rupees * 100);
};

function managedBannerPath(url: string | null | undefined): string | null {
  if (!url || !url.startsWith(BANNER_IMAGE_URL_PREFIX)) return null;
  return decodeURIComponent(url.slice(BANNER_IMAGE_URL_PREFIX.length).split("?")[0]);
}

function validateBannerFile(file: File): string | null {
  if (!(BANNER_IMAGE_TYPES as readonly string[]).includes(file.type)) {
    return "Choose a JPEG, PNG or WebP image.";
  }
  if (file.size === 0) {
    return "That image file is empty. Choose another file.";
  }
  if (file.size > BANNER_IMAGE_MAX_BYTES) {
    const sizeMb = (file.size / 1024 / 1024).toFixed(1);
    return `That image is ${sizeMb} MB. The maximum size is 2 MB.`;
  }
  return null;
}

function toBannerItems(raw: unknown): BannerItem[] {
  if (!Array.isArray(raw)) return [];
  const seen = new Set<string>();
  const banners: BannerItem[] = [];
  raw.forEach((item, index) => {
    if (!item || typeof item !== "object") return;
    const record = item as Record<string, unknown>;
    const baseId =
      typeof record.id === "string" && record.id.trim() ? record.id.trim() : `b-${index}`;
    let id = baseId;
    while (seen.has(id)) id = `${baseId}-${index}`;
    seen.add(id);
    banners.push({
      id,
      title: typeof record.title === "string" ? record.title : "",
      subtitle: typeof record.subtitle === "string" ? record.subtitle : "",
      image_url:
        typeof record.image_url === "string"
          ? record.image_url
          : typeof record.imageUrl === "string"
            ? record.imageUrl
            : "",
    });
  });
  return banners;
}

function useOnlineStatus() {
  const [online, setOnline] = useState(true);

  useEffect(() => {
    if (typeof navigator === "undefined") return;
    const update = () => setOnline(navigator.onLine);
    update();
    window.addEventListener("online", update);
    window.addEventListener("offline", update);
    return () => {
      window.removeEventListener("online", update);
      window.removeEventListener("offline", update);
    };
  }, []);

  return online;
}

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
  const [bannerUploads, setBannerUploads] = useState<Record<string, BannerUploadState>>({});
  const [savedWeeklyHours, setSavedWeeklyHours] = useState<Record<string, unknown>>({});
  const savedBannersRef = useRef<BannerItem[]>([]);
  const pendingBannerUploadsRef = useRef<Set<string>>(new Set());
  const previewUrlsRef = useRef<Set<string>>(new Set());
  const online = useOnlineStatus();

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
    const loadedBanners = toBannerItems(settings.weekly_hours?.promotional_banners);
    setBanners(loadedBanners);
    savedBannersRef.current = loadedBanners;
    setBannerUploads({});
    setSavedWeeklyHours((settings.weekly_hours || {}) as Record<string, unknown>);
    setLoading(false);
  }, [form]);

  useEffect(() => {
    loadSettings();
  }, [loadSettings]);

  useEffect(() => {
    const previews = previewUrlsRef.current;
    return () => {
      previews.forEach((url) => URL.revokeObjectURL(url));
      previews.clear();
    };
  }, []);

  const addBanner = () => {
    const newBanner: BannerItem = {
      id: "b-" + Date.now(),
      title: "Special Offer Banner",
      subtitle: "Exclusive deals on groceries",
      image_url: "",
    };
    setBanners((current) => [...current, newBanner]);
  };

  const removePendingUpload = async (url: string | null | undefined) => {
    const path = managedBannerPath(url);
    if (!path || !pendingBannerUploadsRef.current.has(path)) return;
    pendingBannerUploadsRef.current.delete(path);
    try {
      const supabase = createClient();
      await supabase.storage.from(BANNER_IMAGE_BUCKET).remove([path]);
    } catch {
      // Best-effort cleanup: the banner is not referenced by store_settings yet.
    }
  };

  const removeBanner = (id: string) => {
    const banner = banners.find((item) => item.id === id);
    void removePendingUpload(banner?.image_url);
    const preview = bannerUploads[id]?.previewUrl;
    if (preview) {
      URL.revokeObjectURL(preview);
      previewUrlsRef.current.delete(preview);
    }
    setBanners((current) => current.filter((banner) => banner.id !== id));
    setBannerUploads((current) => {
      const next = { ...current };
      delete next[id];
      return next;
    });
  };

  const updateBanner = (id: string, field: keyof BannerItem, value: string) => {
    setBanners((current) =>
      current.map((banner) => (banner.id === id ? { ...banner, [field]: value } : banner)),
    );
  };

  const clearBannerImage = (id: string) => {
    const banner = banners.find((item) => item.id === id);
    void removePendingUpload(banner?.image_url);
    const preview = bannerUploads[id]?.previewUrl;
    if (preview) {
      URL.revokeObjectURL(preview);
      previewUrlsRef.current.delete(preview);
    }
    updateBanner(id, "image_url", "");
    setBannerUploads((current) => ({ ...current, [id]: idleBannerUpload }));
  };

  const handleBannerFile = async (bannerId: string, file: File) => {
    const current = bannerUploads[bannerId];
    const validationError = validateBannerFile(file);
    if (validationError) {
      setBannerUploads((previous) => ({
        ...previous,
        [bannerId]: {
          status: "error",
          error: validationError,
          previewUrl: previous[bannerId]?.previewUrl ?? null,
        },
      }));
      return;
    }
    if (!online) {
      setBannerUploads((previous) => ({
        ...previous,
        [bannerId]: {
          status: "error",
          error: "You're offline. Reconnect before uploading an image.",
          previewUrl: previous[bannerId]?.previewUrl ?? null,
        },
      }));
      return;
    }

    if (current?.previewUrl) {
      URL.revokeObjectURL(current.previewUrl);
      previewUrlsRef.current.delete(current.previewUrl);
    }
    const previewUrl = URL.createObjectURL(file);
    previewUrlsRef.current.add(previewUrl);
    setBannerUploads((previous) => ({
      ...previous,
      [bannerId]: { status: "uploading", error: null, previewUrl },
    }));

    const extension =
      file.type === "image/png" ? "png" : file.type === "image/webp" ? "webp" : "jpg";
    const objectPath = `banners/${createUuid()}.${extension}`;
    const supabase = createClient();

    try {
      const { error } = await supabase.storage.from(BANNER_IMAGE_BUCKET).upload(objectPath, file, {
        contentType: file.type,
        cacheControl: "31536000",
        upsert: false,
      });
      if (error) throw error;

      const { data } = supabase.storage.from(BANNER_IMAGE_BUCKET).getPublicUrl(objectPath);
      if (!data?.publicUrl) {
        throw new Error("The upload finished but no public image URL was returned.");
      }

      const banner = banners.find((item) => item.id === bannerId);
      const previousPath = managedBannerPath(banner?.image_url);
      if (
        previousPath &&
        previousPath !== objectPath &&
        pendingBannerUploadsRef.current.has(previousPath)
      ) {
        pendingBannerUploadsRef.current.delete(previousPath);
        void supabase.storage.from(BANNER_IMAGE_BUCKET).remove([previousPath]);
      }

      pendingBannerUploadsRef.current.add(objectPath);
      updateBanner(bannerId, "image_url", data.publicUrl);
      setBannerUploads((previous) => ({
        ...previous,
        [bannerId]: { status: "idle", error: null, previewUrl },
      }));
      toast.success("Banner image uploaded");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Image upload failed";
      URL.revokeObjectURL(previewUrl);
      previewUrlsRef.current.delete(previewUrl);
      setBannerUploads((previous) => ({
        ...previous,
        [bannerId]: {
          status: "error",
          error: message,
          previewUrl: null,
        },
      }));
      toast.error(message);
    }
  };

  const uploadingBannerCount = Object.values(bannerUploads).filter(
    (upload) => upload.status === "uploading",
  ).length;
  const isUploadingBanner = uploadingBannerCount > 0;

  const onSubmit = async (data: StoreSettings) => {
    setSaveError("");

    if (!online) {
      setSaveError("You're offline. Reconnect before saving store settings.");
      return;
    }
    if (isUploadingBanner) {
      setSaveError("Wait for the banner image uploads to finish before saving.");
      return;
    }

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
      setSaveError(
        "Free-delivery threshold must be empty or at least the delivery-charge threshold",
      );
      return;
    }
    if (!openTime.trim() || !closeTime.trim()) {
      setSaveError("Opening and closing times are required");
      return;
    }
    const invalidBanner = banners.find(
      (banner) => !banner.title.trim() || !/^https?:\/\//i.test(banner.image_url.trim()),
    );
    if (invalidBanner) {
      setSaveError("Every banner needs a title and an uploaded image.");
      return;
    }

    setSaving(true);
    try {
      const supabase = createClient();
      const previousBanners = savedBannersRef.current;
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
      savedBannersRef.current = banners;

      // Remove managed objects that are no longer referenced. External URLs and
      // objects still used by a banner are left untouched. Cleanup is
      // best-effort: the settings row has already been saved.
      const retainedPaths = new Set(
        banners
          .map((banner) => managedBannerPath(banner.image_url))
          .filter((path): path is string => Boolean(path)),
      );
      const removals = previousBanners
        .map((banner) => managedBannerPath(banner.image_url))
        .filter((path): path is string => Boolean(path) && !retainedPaths.has(path!))
        .map((path) => supabase.storage.from(BANNER_IMAGE_BUCKET).remove([path!]));
      if (removals.length > 0) {
        await Promise.allSettled(removals);
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

      {!online && (
        <div
          role="status"
          className="flex items-center gap-2 rounded-2xl border border-amber-200 bg-amber-50 px-5 py-4 text-sm font-medium text-amber-800"
        >
          <WifiOff className="h-5 w-5" /> You&apos;re offline. Existing settings are shown, but
          image uploads and saves will fail until you reconnect.
        </div>
      )}

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
          description="Upload the rotating hero images shown at the top of the Android app. JPEG, PNG or WebP up to 2 MB; a wide 2:1 image works best."
        >
          <div className="space-y-4">
            {banners.map((banner, index) => {
              const upload = bannerUploads[banner.id] ?? idleBannerUpload;
              return (
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
                      disabled={saving || upload.status === "uploading"}
                      className="p-1 text-slate-400 hover:text-red-600 rounded-lg disabled:cursor-not-allowed disabled:opacity-40"
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

                    <BannerImageField
                      banner={banner}
                      index={index}
                      upload={upload}
                      online={online}
                      disabled={saving}
                      onFile={(file) => handleBannerFile(banner.id, file)}
                      onRemove={() => clearBannerImage(banner.id)}
                    />
                  </div>
                </div>
              );
            })}

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
            disabled={saving || isUploadingBanner}
            className="flex items-center gap-2 px-5 py-2.5 rounded-xl font-bold text-white bg-emerald-600 hover:bg-emerald-700 focus:ring-2 focus:ring-emerald-500 shadow-md shadow-emerald-600/20 disabled:opacity-50"
          >
            {saving ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin" />
                Saving...
              </>
            ) : isUploadingBanner ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin" />
                Uploading image...
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

function BannerImageField({
  banner,
  index,
  upload,
  online,
  disabled,
  onFile,
  onRemove,
}: {
  banner: BannerItem;
  index: number;
  upload: BannerUploadState;
  online: boolean;
  disabled: boolean;
  onFile: (file: File) => void;
  onRemove: () => void;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragActive, setDragActive] = useState(false);
  const previewSrc = upload.previewUrl || banner.image_url;
  const isUploading = upload.status === "uploading";
  const isDisabled = disabled || isUploading;

  const openPicker = () => {
    if (isDisabled) return;
    inputRef.current?.click();
  };

  const takeFile = (file: File | undefined) => {
    if (!file || isDisabled) return;
    onFile(file);
  };

  return (
    <div className="md:col-span-2">
      <div className="mb-1 flex flex-wrap items-center justify-between gap-2">
        <label className="block text-xs font-semibold text-slate-700">Banner Image</label>
        <span className="text-[11px] font-medium text-slate-500">
          JPEG, PNG or WebP · max 2 MB · wide 2:1 image
        </span>
      </div>

      <input
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        className="sr-only"
        aria-label={`Choose image file for banner ${index + 1}`}
        onChange={(event) => {
          takeFile(event.target.files?.[0]);
          event.target.value = "";
        }}
        disabled={isDisabled}
      />

      {previewSrc ? (
        <div
          onDragOver={(event) => {
            event.preventDefault();
            if (!isDisabled) setDragActive(true);
          }}
          onDragLeave={() => setDragActive(false)}
          onDrop={(event) => {
            event.preventDefault();
            setDragActive(false);
            if (!isDisabled) takeFile(event.dataTransfer.files?.[0]);
          }}
          className={`overflow-hidden rounded-xl border bg-white transition-colors ${
            dragActive ? "border-emerald-500 ring-2 ring-emerald-200" : "border-slate-300"
          }`}
        >
          <div className="relative aspect-[2/1] bg-slate-100">
            <SafeImage
              src={previewSrc}
              alt={`Preview of banner ${index + 1}`}
              className="h-full w-full object-cover"
              fallback={
                <div className="flex h-full items-center justify-center text-xs font-medium text-slate-500">
                  Preview unavailable
                </div>
              }
            />
            {isUploading && (
              <div
                role="status"
                aria-live="polite"
                className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-slate-950/65 px-4 text-center text-white"
              >
                <Loader2 className="h-6 w-6 animate-spin" aria-hidden="true" />
                <span className="text-xs font-semibold">Uploading banner image…</span>
                <span className="h-1.5 w-32 overflow-hidden rounded-full bg-white/30">
                  <span className="block h-full w-1/2 animate-pulse rounded-full bg-white" />
                </span>
              </div>
            )}
          </div>
          <div className="flex flex-wrap items-center gap-2 border-t border-slate-200 p-2">
            <button
              type="button"
              onClick={openPicker}
              disabled={isDisabled || !online}
              className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
            >
              <RefreshCw className="h-3.5 w-3.5" /> Replace image
            </button>
            <button
              type="button"
              onClick={onRemove}
              disabled={isDisabled}
              className="inline-flex items-center gap-1.5 rounded-lg border border-red-200 bg-white px-2.5 py-1.5 text-xs font-semibold text-red-700 hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-50"
            >
              <X className="h-3.5 w-3.5" /> Remove image
            </button>
            <span
              className="min-w-0 flex-1 truncate text-[11px] text-slate-500"
              title={banner.image_url}
            >
              {banner.image_url.startsWith(BANNER_IMAGE_URL_PREFIX)
                ? "Stored in banner-images"
                : banner.image_url
                  ? "Existing external image"
                  : isUploading
                    ? "Uploading…"
                    : "Not saved yet"}
            </span>
          </div>
        </div>
      ) : !online ? (
        <div
          role="status"
          className="flex items-center gap-2 rounded-xl border border-dashed border-amber-300 bg-amber-50 px-4 py-6 text-sm font-medium text-amber-800"
        >
          <WifiOff className="h-5 w-5 shrink-0" /> You&apos;re offline. Reconnect to upload a banner
          image.
        </div>
      ) : (
        <div
          role="button"
          tabIndex={0}
          aria-label={`Upload image for banner ${index + 1}`}
          aria-disabled={isDisabled}
          onClick={openPicker}
          onKeyDown={(event) => {
            if (event.key === "Enter" || event.key === " ") {
              event.preventDefault();
              openPicker();
            }
          }}
          onDragOver={(event) => {
            event.preventDefault();
            if (!isDisabled) setDragActive(true);
          }}
          onDragLeave={() => setDragActive(false)}
          onDrop={(event) => {
            event.preventDefault();
            setDragActive(false);
            if (!isDisabled) takeFile(event.dataTransfer.files?.[0]);
          }}
          className={`flex cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed px-4 py-8 text-center transition-colors focus:outline-none focus:ring-2 focus:ring-emerald-500 ${
            dragActive
              ? "border-emerald-500 bg-emerald-50"
              : "border-slate-300 bg-white hover:border-emerald-400 hover:bg-emerald-50/40"
          } ${isDisabled ? "cursor-not-allowed opacity-60" : ""}`}
        >
          {isUploading ? (
            <>
              <Loader2 className="h-7 w-7 animate-spin text-emerald-600" aria-hidden="true" />
              <span className="text-sm font-semibold text-slate-700">Uploading banner image…</span>
            </>
          ) : (
            <>
              <span className="flex h-10 w-10 items-center justify-center rounded-full bg-emerald-100 text-emerald-700">
                <UploadCloud className="h-5 w-5" aria-hidden="true" />
              </span>
              <span className="text-sm font-semibold text-slate-700">
                Drop an image here or click to choose
              </span>
              <span className="text-xs text-slate-500">
                JPEG, PNG or WebP up to 2 MB. A wide 2:1 image works best.
              </span>
            </>
          )}
        </div>
      )}

      {upload.status === "error" && upload.error && (
        <p
          role="alert"
          className="mt-1.5 flex items-start gap-1.5 text-xs font-medium text-red-600"
        >
          <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" /> {upload.error}
        </p>
      )}
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

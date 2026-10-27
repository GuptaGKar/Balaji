"use client";

import { useState, useEffect, useRef } from "react";
import { createClient } from "@/lib/supabase";
import {
  Smartphone,
  Upload,
  Download,
  AlertCircle,
  CheckCircle2,
  Clock,
  ShieldAlert,
  Sparkles,
} from "lucide-react";
import { cn } from "@/lib/utils";

interface AppReleaseItem {
  id: string;
  audience: "customer" | "staff";
  version_code: number;
  version_name: string;
  min_version_code: number;
  apk_url: string;
  release_notes: string;
  force_update: boolean;
  is_active: boolean;
  created_at: string;
}

const STORAGE_BUCKET = "app-releases";
const MAX_APK_BYTES = 150 * 1024 * 1024; // 150MB

export default function AppUpdatesPage() {
  const [releases, setReleases] = useState<AppReleaseItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Form State
  const [audience, setAudience] = useState<"customer" | "staff">("customer");
  const [versionName, setVersionName] = useState("");
  const [versionCode, setVersionCode] = useState<number | "">("");
  const [minVersionCode, setMinVersionCode] = useState<number | "">(1);
  const [forceUpdate, setForceUpdate] = useState(false);
  const [releaseNotes, setReleaseNotes] = useState("");
  const [uploadMethod, setUploadMethod] = useState<"upload" | "url">("upload");
  const [directUrl, setDirectUrl] = useState("");

  // Upload Progress
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const fetchReleases = async () => {
    setLoading(true);
    setError(null);
    try {
      const supabase = createClient();
      const { data, error: fetchErr } = await supabase
        .from("app_releases")
        .select("*")
        .order("created_at", { ascending: false });

      if (fetchErr) throw fetchErr;
      setReleases(data || []);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to load app releases.";
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchReleases();
  }, []);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.name.toLowerCase().endsWith(".apk")) {
      setError("Please select a valid Android .apk file.");
      return;
    }

    if (file.size > MAX_APK_BYTES) {
      setError(
        `APK file is too large (${(file.size / (1024 * 1024)).toFixed(1)} MB). Limit is 150 MB.`,
      );
      return;
    }

    setSelectedFile(file);
    setError(null);
  };

  const handlePublish = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccessMsg(null);

    const vCode = Number(versionCode);
    const minCode = Number(minVersionCode) || 1;

    if (!versionName.trim()) {
      setError("Version name is required (e.g. 1.7).");
      return;
    }

    if (!vCode || vCode <= 0) {
      setError("Version code must be a positive integer (e.g. 8).");
      return;
    }

    let finalApkUrl = directUrl.trim();

    if (uploadMethod === "upload") {
      if (!selectedFile) {
        setError("Please choose an APK file to upload.");
        return;
      }

      setUploading(true);
      setUploadProgress(10);

      try {
        const supabase = createClient();
        const safeName = `balaji-${audience}-v${versionName.replace(/[^a-zA-Z0-9.-]/g, "_")}-${Date.now()}.apk`;
        const filePath = `${audience}/${safeName}`;

        setUploadProgress(40);
        const { error: uploadError } = await supabase.storage
          .from(STORAGE_BUCKET)
          .upload(filePath, selectedFile, {
            contentType: "application/vnd.android.package-archive",
            upsert: true,
          });

        if (uploadError) throw uploadError;

        setUploadProgress(85);
        const { data: urlData } = supabase.storage.from(STORAGE_BUCKET).getPublicUrl(filePath);
        finalApkUrl = urlData.publicUrl;
      } catch (err: unknown) {
        const message = err instanceof Error ? err.message : "APK upload failed.";
        setError(message);
        setUploading(false);
        return;
      }
    } else {
      if (!finalApkUrl.startsWith("http://") && !finalApkUrl.startsWith("https://")) {
        setError("Please provide a valid HTTP/HTTPS URL for the APK.");
        return;
      }
    }

    try {
      const supabase = createClient();
      const { error: insertError } = await supabase.from("app_releases").insert([
        {
          audience,
          version_name: versionName.trim(),
          version_code: vCode,
          min_version_code: minCode,
          apk_url: finalApkUrl,
          release_notes: releaseNotes.trim(),
          force_update: forceUpdate,
          is_active: true,
        },
      ]);

      if (insertError) throw insertError;

      setSuccessMsg(
        `Successfully published update v${versionName} (build ${vCode}) for ${audience} app!`,
      );
      // Reset form
      setVersionName("");
      setVersionCode("");
      setMinVersionCode(1);
      setReleaseNotes("");
      setDirectUrl("");
      setSelectedFile(null);
      setForceUpdate(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
      await fetchReleases();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to record release in database.";
      setError(message);
    } finally {
      setUploading(false);
      setUploadProgress(0);
    }
  };

  const toggleActiveStatus = async (release: AppReleaseItem) => {
    try {
      const supabase = createClient();
      const { error: updateErr } = await supabase
        .from("app_releases")
        .update({ is_active: !release.is_active })
        .eq("id", release.id);

      if (updateErr) throw updateErr;
      await fetchReleases();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to update release status.";
      setError(message);
    }
  };

  const customerReleases = releases.filter((r) => r.audience === "customer");
  const activeCustomer = customerReleases
    .filter((r) => r.is_active)
    .sort((a, b) => b.version_code - a.version_code)[0];

  return (
    <div className="max-w-6xl mx-auto space-y-8 p-4 sm:p-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-200 pb-5">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 flex items-center gap-2">
            <Smartphone className="h-7 w-7 text-emerald-600" />
            In-App Mobile Releases & Updates
          </h1>
          <p className="text-sm text-slate-500 mt-1">
            Publish Android APK updates directly to customer mobiles without waiting for app store
            reviews.
          </p>
        </div>

        {activeCustomer && (
          <div className="bg-emerald-50 border border-emerald-200 rounded-xl px-4 py-2.5 flex items-center gap-3">
            <div className="h-3 w-3 rounded-full bg-emerald-500 animate-pulse" />
            <div>
              <div className="text-xs text-emerald-800 font-medium">Active Customer Version</div>
              <div className="text-sm font-bold text-emerald-950">
                v{activeCustomer.version_name} (Build {activeCustomer.version_code})
              </div>
            </div>
          </div>
        )}
      </div>

      {error && (
        <div className="bg-rose-50 border border-rose-200 text-rose-800 rounded-xl p-4 flex items-start gap-3">
          <AlertCircle className="h-5 w-5 text-rose-600 flex-shrink-0 mt-0.5" />
          <div className="text-sm font-medium">{error}</div>
        </div>
      )}

      {successMsg && (
        <div className="bg-emerald-50 border border-emerald-200 text-emerald-800 rounded-xl p-4 flex items-start gap-3">
          <CheckCircle2 className="h-5 w-5 text-emerald-600 flex-shrink-0 mt-0.5" />
          <div className="text-sm font-medium">{successMsg}</div>
        </div>
      )}

      {/* Main Grid: Form on Left, Release History on Right */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        {/* Release Form */}
        <div className="lg:col-span-5 bg-white border border-slate-200 rounded-2xl p-6 shadow-sm">
          <div className="flex items-center gap-2 mb-4 pb-3 border-b border-slate-100">
            <Sparkles className="h-5 w-5 text-emerald-600" />
            <h2 className="text-lg font-bold text-slate-900">Publish New Update</h2>
          </div>

          <form onSubmit={handlePublish} className="space-y-4">
            {/* Target Audience */}
            <div>
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                Target App Audience
              </label>
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  onClick={() => setAudience("customer")}
                  className={cn(
                    "px-3 py-2 text-xs font-medium rounded-lg border transition text-center",
                    audience === "customer"
                      ? "border-emerald-600 bg-emerald-50 text-emerald-900 font-bold"
                      : "border-slate-200 text-slate-600 hover:bg-slate-50",
                  )}
                >
                  Customer App
                </button>
                <button
                  type="button"
                  onClick={() => setAudience("staff")}
                  className={cn(
                    "px-3 py-2 text-xs font-medium rounded-lg border transition text-center",
                    audience === "staff"
                      ? "border-emerald-600 bg-emerald-50 text-emerald-900 font-bold"
                      : "border-slate-200 text-slate-600 hover:bg-slate-50",
                  )}
                >
                  Staff / Rider App
                </button>
              </div>
            </div>

            {/* Version Name & Code */}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-slate-700 mb-1">
                  Version Name <span className="text-rose-500">*</span>
                </label>
                <input
                  type="text"
                  placeholder="e.g. 1.7"
                  value={versionName}
                  onChange={(e) => setVersionName(e.target.value)}
                  className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
                  required
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-700 mb-1">
                  Version Code (Int) <span className="text-rose-500">*</span>
                </label>
                <input
                  type="number"
                  placeholder="e.g. 8"
                  value={versionCode}
                  onChange={(e) => setVersionCode(e.target.value ? Number(e.target.value) : "")}
                  className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
                  required
                />
              </div>
            </div>

            {/* Minimum Supported Version Code */}
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">
                Minimum Supported Version Code
              </label>
              <input
                type="number"
                placeholder="1"
                value={minVersionCode}
                onChange={(e) => setMinVersionCode(e.target.value ? Number(e.target.value) : "")}
                className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
              <p className="text-[11px] text-slate-400 mt-1">
                Apps older than this version code will be forced to update immediately.
              </p>
            </div>

            {/* APK Delivery Method */}
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">
                APK Delivery Method
              </label>
              <div className="flex gap-4 mb-2">
                <label className="flex items-center gap-1.5 text-xs text-slate-700 cursor-pointer">
                  <input
                    type="radio"
                    name="method"
                    checked={uploadMethod === "upload"}
                    onChange={() => setUploadMethod("upload")}
                    className="text-emerald-600 focus:ring-emerald-500"
                  />
                  Upload APK File
                </label>
                <label className="flex items-center gap-1.5 text-xs text-slate-700 cursor-pointer">
                  <input
                    type="radio"
                    name="method"
                    checked={uploadMethod === "url"}
                    onChange={() => setUploadMethod("url")}
                    className="text-emerald-600 focus:ring-emerald-500"
                  />
                  Direct APK Link
                </label>
              </div>

              {uploadMethod === "upload" ? (
                <div className="border-2 border-dashed border-slate-200 hover:border-emerald-400 rounded-xl p-4 text-center transition bg-slate-50/50">
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept=".apk"
                    onChange={handleFileChange}
                    className="hidden"
                    id="apk-upload"
                  />
                  <label htmlFor="apk-upload" className="cursor-pointer flex flex-col items-center">
                    <Upload className="h-8 w-8 text-slate-400 mb-1" />
                    <span className="text-xs font-semibold text-emerald-600 hover:underline">
                      {selectedFile ? selectedFile.name : "Select .apk file"}
                    </span>
                    <span className="text-[10px] text-slate-400 mt-0.5">
                      {selectedFile
                        ? `${(selectedFile.size / (1024 * 1024)).toFixed(1)} MB`
                        : "Direct upload up to 150 MB"}
                    </span>
                  </label>
                </div>
              ) : (
                <input
                  type="url"
                  placeholder="https://example.com/balaji-v1.7.apk"
                  value={directUrl}
                  onChange={(e) => setDirectUrl(e.target.value)}
                  className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
              )}
            </div>

            {/* Release Notes */}
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">
                Release Notes / What&apos;s New
              </label>
              <textarea
                rows={3}
                placeholder="e.g. Added instant order tracking, improved speed, fixed coupon calculation..."
                value={releaseNotes}
                onChange={(e) => setReleaseNotes(e.target.value)}
                className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>

            {/* Force Update Toggle */}
            <div className="flex items-center gap-2 p-3 bg-amber-50/80 border border-amber-200/60 rounded-xl">
              <input
                type="checkbox"
                id="force-update"
                checked={forceUpdate}
                onChange={(e) => setForceUpdate(e.target.checked)}
                className="h-4 w-4 rounded border-slate-300 text-amber-600 focus:ring-amber-500"
              />
              <label
                htmlFor="force-update"
                className="text-xs text-amber-900 font-medium cursor-pointer"
              >
                Mandatory Update (users cannot skip or dismiss the prompt)
              </label>
            </div>

            {/* Submit Button */}
            <button
              type="submit"
              disabled={uploading}
              className="w-full bg-emerald-600 hover:bg-emerald-700 disabled:bg-slate-300 text-white font-semibold py-2.5 px-4 rounded-xl shadow transition text-sm flex items-center justify-center gap-2"
            >
              {uploading ? (
                <>
                  <div className="h-4 w-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                  Uploading APK ({uploadProgress}%)...
                </>
              ) : (
                <>
                  <Upload className="h-4 w-4" />
                  Publish In-App Update
                </>
              )}
            </button>
          </form>
        </div>

        {/* Release History */}
        <div className="lg:col-span-7 space-y-4">
          <div className="flex items-center justify-between pb-2 border-b border-slate-200">
            <h2 className="text-lg font-bold text-slate-900 flex items-center gap-2">
              <Clock className="h-5 w-5 text-slate-500" />
              Published App Releases ({releases.length})
            </h2>
          </div>

          {loading ? (
            <div className="bg-white border border-slate-200 rounded-2xl p-8 text-center text-slate-400 text-sm">
              Loading release history...
            </div>
          ) : releases.length === 0 ? (
            <div className="bg-white border border-slate-200 rounded-2xl p-8 text-center text-slate-500 text-sm">
              No releases published yet. Use the form on the left to publish your first in-app
              update.
            </div>
          ) : (
            <div className="space-y-3">
              {releases.map((rel) => (
                <div
                  key={rel.id}
                  className={cn(
                    "bg-white border rounded-2xl p-5 shadow-sm transition space-y-3",
                    rel.is_active
                      ? "border-emerald-200 bg-emerald-50/20"
                      : "border-slate-200 opacity-75",
                  )}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-center gap-2.5">
                      <div
                        className={cn(
                          "px-2.5 py-1 rounded-md text-xs font-bold uppercase",
                          rel.audience === "customer"
                            ? "bg-blue-100 text-blue-800"
                            : "bg-purple-100 text-purple-800",
                        )}
                      >
                        {rel.audience}
                      </div>
                      <div className="text-base font-bold text-slate-900">
                        v{rel.version_name}
                        <span className="text-xs font-normal text-slate-500 ml-1.5">
                          (Code {rel.version_code})
                        </span>
                      </div>
                      {rel.force_update && (
                        <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-amber-700 bg-amber-100/70 px-2 py-0.5 rounded-full">
                          <ShieldAlert className="h-3 w-3" /> Mandatory
                        </span>
                      )}
                    </div>

                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => toggleActiveStatus(rel)}
                        className={cn(
                          "text-xs px-2.5 py-1 rounded-lg font-medium border transition",
                          rel.is_active
                            ? "bg-emerald-100 text-emerald-800 border-emerald-300 hover:bg-emerald-200"
                            : "bg-slate-100 text-slate-600 border-slate-200 hover:bg-slate-200",
                        )}
                      >
                        {rel.is_active ? "Active" : "Archived"}
                      </button>
                    </div>
                  </div>

                  {rel.release_notes && (
                    <p className="text-xs text-slate-600 bg-slate-50 p-2.5 rounded-lg border border-slate-100 whitespace-pre-line">
                      {rel.release_notes}
                    </p>
                  )}

                  <div className="flex flex-wrap items-center justify-between text-xs text-slate-400 pt-2 border-t border-slate-100 gap-2">
                    <div>
                      Published{" "}
                      {new Date(rel.created_at).toLocaleDateString("en-IN", {
                        day: "numeric",
                        month: "short",
                        year: "numeric",
                        hour: "2-digit",
                        minute: "2-digit",
                      })}
                    </div>

                    <div className="flex items-center gap-3">
                      <a
                        href={rel.apk_url}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-1 text-emerald-600 hover:text-emerald-700 font-medium"
                      >
                        <Download className="h-3.5 w-3.5" /> Download APK
                      </a>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

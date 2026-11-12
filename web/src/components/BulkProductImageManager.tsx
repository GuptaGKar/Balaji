"use client";

import { useEffect, useState } from "react";
import { createClient } from "@/lib/supabase";
import { createUuid } from "@/lib/uuid";
import { prepareProductFile } from "@/lib/product-cutout";
import styles from "./ProductShowcase.module.css";

type CatalogueProduct = {
  id: string;
  name: string;
  sku?: string | null;
  pack_size_label?: string | null;
};

function Showcase({ src, dark }: { src: string; dark: boolean }) {
  return (
    <div className={`${styles.showcase} ${dark ? styles.dark : ""}`}>
      <div className={styles.shadow} />
      <div className={styles.edge} />
      <div className={styles.top} />
      {/* eslint-disable-next-line @next/next/no-img-element -- local product-only preview blob */}
      <img src={src} alt={`${dark ? "Dark" : "Light"} podium alignment preview`} />
    </div>
  );
}

/** Queue is local; catalogue/storage are touched only by explicit approval. */
export function BulkProductImageManager({
  products,
  onSaved,
}: {
  products: CatalogueProduct[];
  onSaved: () => Promise<void>;
}) {
  const [files, setFiles] = useState<File[]>([]);
  const [index, setIndex] = useState(0);
  const [productId, setProductId] = useState("");
  const [removeWhite, setRemoveWhite] = useState(true);
  const [original, setOriginal] = useState("");
  const [processed, setProcessed] = useState<{
    blob: Blob;
    url: string;
    previewUrl: string;
    source: File;
    removeWhite: boolean;
  } | null>(null);
  const [preparing, setPreparing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const file = files[index];
  useEffect(() => {
    setProcessed(null);
    setError("");
    setProductId("");
    if (!file) {
      setOriginal("");
      return;
    }
    const url = URL.createObjectURL(file);
    setOriginal(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);
  useEffect(() => {
    setProcessed(null);
    setError("");
    if (!file) return;
    let cancelled = false,
      outputUrl = "",
      previewUrl = "";
    setPreparing(true);
    // Yield before decoding so the loading state is painted.
    const timer = setTimeout(() => {
      void prepareProductFile(file, removeWhite)
        .then(({ blob, previewBlob }) => {
          if (cancelled) return;
          if (blob.size > 2 * 1024 * 1024)
            throw new Error("Prepared PNG exceeds 2 MB. Choose a smaller source.");
          outputUrl = URL.createObjectURL(blob);
          previewUrl = URL.createObjectURL(previewBlob);
          setProcessed({ blob, url: outputUrl, previewUrl, source: file, removeWhite });
        })
        .catch((e) => {
          if (!cancelled) setError(e instanceof Error ? e.message : "Image processing failed");
        })
        .finally(() => {
          if (!cancelled) setPreparing(false);
        });
    }, 0);
    return () => {
      cancelled = true;
      clearTimeout(timer);
      if (outputUrl) URL.revokeObjectURL(outputUrl);
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    };
  }, [file, removeWhite]);
  const advance = () => {
    setProductId("");
    setIndex((i) => i + 1);
  };
  async function approve() {
    if (
      !processed ||
      processed.source !== file ||
      processed.removeWhite !== removeWhite ||
      !productId ||
      saving ||
      preparing
    )
      return;
    if (!navigator.onLine) {
      setError("You are offline. Reconnect before approving an upload.");
      return;
    }
    const product = products.find((p) => p.id === productId);
    if (!product) return;
    setSaving(true);
    setError("");
    const supabase = createClient(),
      path = `products/${productId}/${createUuid()}.png`;
    let uploaded = false,
      saved = false;
    try {
      const upload = await supabase.storage
        .from("product-images")
        .upload(path, processed.blob, { contentType: "image/png", upsert: false });
      if (upload.error) throw upload.error;
      uploaded = true;
      const url = supabase.storage.from("product-images").getPublicUrl(path).data.publicUrl;
      const result = await supabase
        .from("products")
        .update({ image_path: url })
        .eq("id", productId)
        .select("id")
        .single();
      if (result.error) throw result.error;
      saved = true;
      setNotice(`Approved image for ${product.name} (${product.sku}).`);
      advance();
      await onSaved();
    } catch (e) {
      // A lost response may mean the catalogue update committed. Keep the new object
      // rather than delete a potentially referenced image; a retry uses a fresh path.
      if (uploaded && !saved)
        setNotice(
          "Upload completed but catalogue save was not confirmed. Check the catalogue before retrying.",
        );
      setError(
        e instanceof Error
          ? e.message
          : typeof e === "object" && e && "message" in e
            ? String(e.message)
            : "Could not save image. Please retry.",
      );
    } finally {
      setSaving(false);
    }
  }
  return (
    <details className="rounded-2xl border border-orange-200 bg-white p-4">
      <summary className="cursor-pointer font-bold">Bulk Product Image Manager</summary>
      <p className="my-3 text-sm text-slate-600">
        Choose up to 30 product photos. Review each cutout and choose its exact catalogue pack
        before approving. Only the product PNG is saved; podiums are preview layers.
      </p>
      <label className="block text-sm font-semibold">
        Product photo queue
        <input
          className="my-2 block"
          type="file"
          multiple
          accept="image/png,image/webp,image/jpeg"
          disabled={saving || preparing}
          onChange={(e) => {
            const selected = Array.from(e.target.files ?? []);
            if (selected.length > 30) {
              setError("Choose at most 30 images per queue");
              return;
            }
            setFiles(selected);
            setIndex(0);
            setNotice("");
          }}
        />
      </label>
      {notice && (
        <p role="status" className="my-2 text-green-800">
          {notice}
        </p>
      )}
      {error && (
        <p role="alert" className="my-2 text-red-700">
          {error}
        </p>
      )}
      {!file ? (
        <p className="text-sm">
          {files.length ? "Queue finished. Choose more images to continue." : "No images selected."}
        </p>
      ) : (
        <>
          <p className="my-2 font-semibold">
            {index + 1} / {files.length}: {file.name}
          </p>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={removeWhite}
              disabled={saving || preparing}
              onChange={(e) => setRemoveWhite(e.target.checked)}
            />
            Remove connected near-white background
          </label>
          <p className="my-2 text-sm text-amber-800">
            Inspect white packaging edges and labels carefully. If edges disappear, uncheck
            background removal. Complex backgrounds need a transparent cutout prepared externally;
            this tool only removes connected near-white backgrounds.
          </p>
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <figure>
              <figcaption>Original</figcaption>
              {original && (
                <>
                  {/* eslint-disable-next-line @next/next/no-img-element -- local source blob */}
                  <img className={styles.plain} src={original} alt="Original product photo" />
                </>
              )}
            </figure>
            <figure>
              <figcaption>Product-only PNG</figcaption>
              {processed && (
                <>
                  {/* eslint-disable-next-line @next/next/no-img-element -- local processed blob */}
                  <img
                    className={`${styles.plain} ${styles.checker}`}
                    src={processed.url}
                    alt="Processed product; inspect package edges"
                  />
                </>
              )}
            </figure>
            <figure>
              <figcaption>Light theme</figcaption>
              {processed && <Showcase src={processed.previewUrl} dark={false} />}
            </figure>
            <figure>
              <figcaption>Dark theme</figcaption>
              {processed && <Showcase src={processed.previewUrl} dark />}
            </figure>
          </div>
          {preparing && <p role="status">Preparing product image…</p>}
          <label className="my-3 block text-sm font-semibold">
            Catalogue product and pack
            <select
              className="mt-1 block w-full rounded border p-2"
              value={productId}
              disabled={saving}
              onChange={(e) => setProductId(e.target.value)}
            >
              <option value="">Select the exact product / SKU</option>
              {products.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} · {p.pack_size_label} · {p.sku || p.id}
                </option>
              ))}
            </select>
          </label>
          <div className="flex gap-3">
            <button
              type="button"
              className="rounded bg-orange-600 px-4 py-2 text-white disabled:opacity-50"
              disabled={!processed || !productId || preparing || saving}
              onClick={() => void approve()}
            >
              {saving ? "Saving…" : "Approve, upload and save"}
            </button>
            <button
              type="button"
              className="rounded border px-4 py-2 disabled:opacity-50"
              disabled={saving || preparing}
              onClick={() => {
                advance();
                setNotice("Image rejected. No upload was made.");
              }}
            >
              Reject and next
            </button>
          </div>
        </>
      )}
    </details>
  );
}

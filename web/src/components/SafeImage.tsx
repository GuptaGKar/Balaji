"use client";

import { useEffect, useState, type ImgHTMLAttributes, type ReactNode } from "react";

type SafeImageProps = Omit<ImgHTMLAttributes<HTMLImageElement>, "src" | "alt"> & {
  src?: string | null;
  alt: string;
  /** Rendered when the URL is blank or the browser reports a load error. */
  fallback: ReactNode;
};

/**
 * Catalogue images can point at Supabase Storage or an external URL. A broken
 * URL must not leave a broken-image icon in the table or preview, so swap to
 * the provided fallback instead.
 */
export function SafeImage({ src, alt, fallback, ...imageProps }: SafeImageProps) {
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setFailed(false);
  }, [src]);

  if (!src || failed) return <>{fallback}</>;

  return (
    // eslint-disable-next-line @next/next/no-img-element -- external catalogue URLs are not configured in next.config images.
    <img
      {...imageProps}
      src={src}
      alt={alt}
      onError={() => setFailed(true)}
      loading={imageProps.loading ?? "lazy"}
      decoding={imageProps.decoding ?? "async"}
    />
  );
}

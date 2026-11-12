/** Product pixels only. No podium, lighting or shadows enter this pipeline. */
export function preparePixels(
  input: Uint8ClampedArray,
  width: number,
  height: number,
  removeWhite: boolean,
) {
  if (width < 1 || height < 1 || input.length !== width * height * 4)
    throw new Error("Invalid image dimensions");
  const pixels = new Uint8ClampedArray(input);
  if (removeWhite) {
    const visited = new Uint8Array(width * height);
    const queue = new Int32Array(width * height);
    let start = 0,
      end = 0;
    const enqueue = (x: number, y: number) => {
      if (x < 0 || y < 0 || x >= width || y >= height) return;
      const p = y * width + x;
      if (visited[p]) return;
      visited[p] = 1;
      const i = p * 4;
      // Restrict removal to near-white, neutral pixels connected to the outer edge.
      if (
        pixels[i + 3] < 8 ||
        (Math.min(pixels[i], pixels[i + 1], pixels[i + 2]) >= 245 &&
          Math.max(pixels[i], pixels[i + 1], pixels[i + 2]) -
            Math.min(pixels[i], pixels[i + 1], pixels[i + 2]) <=
            8)
      )
        queue[end++] = p;
    };
    for (let x = 0; x < width; x++) {
      enqueue(x, 0);
      enqueue(x, height - 1);
    }
    for (let y = 0; y < height; y++) {
      enqueue(0, y);
      enqueue(width - 1, y);
    }
    while (start < end) {
      const p = queue[start++],
        x = p % width,
        y = Math.floor(p / width);
      pixels[p * 4 + 3] = 0;
      enqueue(x - 1, y);
      enqueue(x + 1, y);
      enqueue(x, y - 1);
      enqueue(x, y + 1);
    }
  }
  let left = width,
    top = height,
    right = -1,
    bottom = -1;
  for (let y = 0; y < height; y++)
    for (let x = 0; x < width; x++)
      if (pixels[(y * width + x) * 4 + 3] > 8) {
        left = Math.min(left, x);
        top = Math.min(top, y);
        right = Math.max(right, x);
        bottom = Math.max(bottom, y);
      }
  if (right < left)
    throw new Error("No visible product remains. Keep the original background and try again.");
  return { pixels, bounds: { x: left, y: top, width: right - left + 1, height: bottom - top + 1 } };
}

export async function prepareProductFile(
  file: File,
  removeWhite: boolean,
): Promise<{ blob: Blob; previewBlob: Blob }> {
  if (!["image/png", "image/webp", "image/jpeg"].includes(file.type))
    throw new Error("Choose PNG, WebP or JPEG");
  if (file.size > 12 * 1024 * 1024) throw new Error("Source image must be under 12 MB");
  const bitmap = await createImageBitmap(file);
  try {
    if (bitmap.width * bitmap.height > 24000000)
      throw new Error("Source image exceeds 24 megapixels");
    const scale = Math.min(1, 1024 / Math.max(bitmap.width, bitmap.height));
    const source = document.createElement("canvas");
    source.width = Math.max(1, Math.round(bitmap.width * scale));
    source.height = Math.max(1, Math.round(bitmap.height * scale));
    const ctx = source.getContext("2d");
    if (!ctx) throw new Error("Image processing is unavailable in this browser");
    ctx.drawImage(bitmap, 0, 0, source.width, source.height);
    const data = ctx.getImageData(0, 0, source.width, source.height);
    const result = preparePixels(data.data, source.width, source.height, removeWhite);
    data.data.set(result.pixels);
    ctx.putImageData(data, 0, 0);
    const output = document.createElement("canvas");
    output.width = output.height = 1024;
    const target = output.getContext("2d");
    if (!target) throw new Error("Image processing unavailable");
    const b = result.bounds;
    const fit = 960 / Math.max(b.width, b.height),
      w = b.width * fit,
      h = b.height * fit;
    target.drawImage(source, b.x, b.y, b.width, b.height, (1024 - w) / 2, (1024 - h) / 2, w, h);
    const encode = (canvas: HTMLCanvasElement) =>
      new Promise<Blob>((resolve, reject) =>
        canvas.toBlob(
          (blob) => (blob ? resolve(blob) : reject(new Error("Could not encode image"))),
          "image/png",
        ),
      );
    const preview = document.createElement("canvas");
    preview.width = b.width;
    preview.height = b.height;
    const previewContext = preview.getContext("2d");
    if (!previewContext) throw new Error("Preview unavailable");
    previewContext.drawImage(source, b.x, b.y, b.width, b.height, 0, 0, b.width, b.height);
    return { blob: await encode(output), previewBlob: await encode(preview) };
  } finally {
    bitmap.close();
  }
}

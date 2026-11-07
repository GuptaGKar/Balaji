#!/usr/bin/env python3
"""BUG-012, measured rather than judged.

The claim was: at 0.30 alpha, product names behind the pill read through the tab labels. The fix is
one constant (BalajiDesign.GlassAlpha 0.30 -> 0.94). Whether that fixed it is a question about
pixels, so this answers it with pixels, and runs the identical measurement on the archived 0.30
screenshot from 2026-11-05 so the result is a delta and not an opinion.

Two numbers per screenshot:

  bleed   - the luminance stddev *inside* the pill surface, away from every label and icon rect.
            A transparent pill lets the page's own detail through, so this variance is high; an
            opaque one flattens it. This is the direct measure of "text showing through".
  contrast- WCAG contrast between each tab label's darkest text pixel and the median surface it
            sits on. The guideline floor for this label size is 4.5:1.

The pill is located by scanning rows, not by trusting a hardcoded inset: the gesture navigation bar
height is not in the layout and differs between a shot taken over the rail and one taken over the
page end.

Untracked scratch tool.
"""
import statistics
import sys

from PIL import Image

import qa_lib as Q

BRAND = (255, 107, 0)
TABS = ["Home", "Categories", "Orders", "Basket"]


def rel_lum(rgb):
    out = []
    for c in rgb[:3]:
        s = c / 255.0
        out.append(s / 12.92 if s <= 0.03928 else ((s + 0.055) / 1.055) ** 2.4)
    return 0.2126 * out[0] + 0.7152 * out[1] + 0.0722 * out[2]


def contrast(a, b):
    la, lb = rel_lum(a), rel_lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def is_brand(rgb, tol=52):
    return all(abs(rgb[i] - BRAND[i]) <= tol for i in range(3))


def find_band(img):
    """Rows in the lower half whose middle columns are almost entirely light: the pill surface."""
    w, h = img.size
    px = img.load()
    x_lo, x_hi = round(24 * Q.DP), w - round(24 * Q.DP)
    rows = []
    for y in range(int(h * 0.55), h):
        light = 0
        for x in range(x_lo, x_hi, 6):
            if rel_lum(px[x, y][:3]) > 0.75:
                light += 1
        rows.append((y, light / len(range(x_lo, x_hi, 6))))
    # The band is the longest run of rows that are >= 80 % light.
    best, cur, start, best_span = 0, 0, None, None
    for y, frac in rows:
        if frac >= 0.80:
            if cur == 0:
                start = y
            cur += 1
            if cur > best:
                best, best_span = cur, (start, y)
        else:
            cur = 0
    if not best_span:
        return None
    y0, y1 = best_span
    if y1 - y0 < round(40 * Q.DP):      # too thin to be the 64 dp pill
        return None
    return y0, y1, x_lo, x_hi


def rects_for(img_path, xml):
    """Label and icon rects to exclude from the bleed sample, when the hierarchy is available."""
    rects = []
    for n in Q.nodes(xml):
        t = Q.attr(n, "text")
        b = Q.bounds(n)
        if b and (t in TABS or t.isdigit()):
            rects.append(b)
    if not rects:
        # Archived shot: no sidecar xml for the exact frame, so exclude nothing and let the
        # measurement be conservative - text pixels only inflate the variance it reports.
        pass
    return rects


def measure(img, band, rects, label):
    y0, y1, x_lo, x_hi = band
    px = img.load()
    excluded = set()
    for rx1, ry1, rx2, ry2 in rects:
        if ry2 < y0 or ry1 > y1:
            continue
        for x in range(max(x_lo, rx1 - 4), min(x_hi, rx2 + 4)):
            for y in range(max(y0, ry1 - 4), min(y1, ry2 + 4)):
                excluded.add((x, y))

    lum, brand_px = [], []
    for y in range(y0, y1 + 1):
        for x in range(x_lo, x_hi + 1):
            rgb = px[x, y][:3]
            if is_brand(rgb):
                brand_px.append((x, y))
                continue
            if (x, y) in excluded:
                continue
            lum.append(rel_lum(rgb) * 255)
    if not lum:
        print(f"{label}: no surface samples")
        return

    median = statistics.median(lum)
    p02, p98 = sorted(lum)[int(len(lum) * 0.02)], sorted(lum)[int(len(lum) * 0.98)]
    print(f"--- {label} ---")
    print(f"  band rows        {y0}-{y1}  ({round((y1 - y0) / Q.DP, 1)} dp tall)")
    print(f"  surface median   {round(median, 1)} / 255")
    print(f"  BLEED stddev     {round(statistics.pstdev(lum), 2)}   (lower = less page showing through)")
    print(f"  BLEED p98-p02    {round(p98 - p02, 1)}")
    print(f"  samples          {len(lum)}")

    if brand_px:
        xs = [p[0] for p in brand_px]
        ys = [p[1] for p in brand_px]
        print(f"  orange capsule   x{min(xs)}-{max(xs)} y{min(ys)}-{max(ys)} "
              f"= {round((max(xs) - min(xs)) / Q.DP, 1)}dp x {round((max(ys) - min(ys)) / Q.DP, 1)}dp, "
              f"centre x={(min(xs) + max(xs)) // 2}")

    # Per-label contrast against the surface it sits on.
    worst = None
    for rx1, ry1, rx2, ry2 in rects:
        if ry2 < y0 or ry1 > y1:
            continue
        darkest = None
        for y in range(ry1, ry2 + 1):
            for x in range(rx1, rx2 + 1):
                rgb = px[x, y][:3]
                if is_brand(rgb):
                    continue
                if darkest is None or sum(rgb) < sum(darkest):
                    darkest = rgb
        if darkest:
            c = contrast(darkest, (median,) * 3)
            worst = c if worst is None else min(worst, c)
    if worst is not None:
        print(f"  label contrast   {round(worst, 2)}:1 worst-case (floor 4.5:1 for this size)")


def main():
    which = sys.argv[1] if len(sys.argv) > 1 else "both"

    if which in ("both", "old"):
        old = Image.open(f"{Q.SHOTS}/v2-home-light.png").convert("RGB")
        band = find_band(old)
        print("archived 0.30 screenshot:", old.size, "band:", band)
        if band:
            measure(old, band, [], "2026-11-05 build, GlassAlpha 0.30")

    if which in ("both", "new"):
        xml = Q.dump_xml()
        img = Image.open(Q.shot("v4-pill-band.png")).convert("RGB")
        band = find_band(img)
        print("current build screenshot:", img.size, "band:", band)
        if band:
            measure(img, band, rects_for(img, xml), "redesign build, GlassAlpha 0.94")
            labels = [(Q.attr(n, "text"), Q.bounds(n)) for n in Q.nodes(xml)
                      if Q.attr(n, "text") in TABS]
            for t, b in labels:
                print(f"    label {t:11s} x{b[0]}-{b[2]} centre x={(b[0]+b[2])//2}")


if __name__ == "__main__":
    main()

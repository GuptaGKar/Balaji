#!/usr/bin/env python3
"""Controlled A/B of the pill surface: the archived pre-redesign build against the redesign build,
on the same scene, measured the same way.

BUG-012 said the 0.30-alpha pill let product names read through the tab labels. The fix raised
GlassAlpha to 0.94. A before/after between two different screenshots would not prove anything, so
this installs `releases/balaji-provision-1.12-release.apk` (the exact build the bug was reported
against), scrolls to a fixed state, measures, then installs the redesign build and repeats the
identical sequence.

Per build it reports:

  surface median      - how light the pill interior is
  bleed stddev / spread - luminance variance inside the pill away from every label rect. This is
                        "page detail showing through", expressed as a number.
  per-label contrast  - each tab label's most extreme pixel against the surface immediately around
                        it, which is the WCAG pair a reader actually sees.

Untracked scratch tool. Leaves the redesign build installed and restores device settings.
"""
import os
import statistics
import subprocess
import time

from PIL import Image

import qa_lib as Q
from pass_pill import contrast, is_brand, rel_lum

TABS = ["Home", "Categories", "Orders", "Basket"]
OLD_APK = "/home/linuai/Balaji/releases/balaji-provision-1.12-release.apk"
NEW_APK = "/home/linuai/Balaji/android/app/build/outputs/apk/customer/release/app-customer-release.apk"


def install(apk):
    Q.sh("install", "-r", apk)


def settle():
    """Cold start, then scroll Home by a fixed amount so a rail sits behind the pill."""
    Q.sh("shell", "am", "force-stop", Q.PKG, check=False)
    time.sleep(1)
    Q.sh("shell", "monkey", "-p", Q.PKG, "-c", "android.intent.category.LAUNCHER", "1", check=False)
    time.sleep(11)
    for _ in range(2):
        Q.swipe(540, 1600, 540, 950, 500)
        time.sleep(1.7)
    time.sleep(1.2)


def measure(tag, png, xml):
    labels = {}
    for n in Q.nodes(xml):
        t = Q.attr(n, "text")
        if t in TABS:
            labels[t] = Q.bounds(n)
    if len(labels) < 4:
        print(f"{tag}: only found {list(labels)} - not on the customer shell, skipping")
        return None
    im = Image.open(png).convert("RGB")
    px = im.load()
    pad = round(14 * Q.DP)
    y0 = min(b[1] for b in labels.values()) - pad
    y1 = max(b[3] for b in labels.values()) + pad
    x_lo, x_hi = round(16 * Q.DP), im.size[0] - round(16 * Q.DP)

    excluded = set()
    for b in labels.values():
        for x in range(max(x_lo, b[0] - 6), min(x_hi, b[2] + 6)):
            for y in range(max(y0, b[1] - 6), min(y1, b[3] + 6)):
                excluded.add((x, y))

    lum, brand = [], []
    for y in range(y0, y1 + 1):
        for x in range(x_lo, x_hi + 1):
            rgb = px[x, y][:3]
            if is_brand(rgb):
                brand.append((x, y))
                continue
            if (x, y) in excluded:
                continue
            lum.append(rel_lum(rgb) * 255)

    med = statistics.median(lum)
    spread = sorted(lum)[int(len(lum) * 0.98)] - sorted(lum)[int(len(lum) * 0.02)]
    res = {
        "band": (y0, y1, round((y1 - y0) / Q.DP, 1)),
        "median": round(med, 1),
        "stddev": round(statistics.pstdev(lum), 2),
        "spread_p98_p02": round(spread, 1),
    }
    if brand:
        xs = [p[0] for p in brand]
        res["capsule"] = {"x": (min(xs), max(xs)), "cx": (min(xs) + max(xs)) // 2,
                          "width_dp": round((max(xs) - min(xs)) / Q.DP, 1)}

    rows = []
    for t, b in labels.items():
        # Local background: the ring just outside the label, so the pair is what a reader sees.
        ring = []
        for x in range(max(x_lo, b[0] - 14), min(x_hi, b[2] + 14), 3):
            for y in (max(y0, b[1] - 12), min(y1, b[3] + 12)):
                rgb = px[x, y][:3]
                if not is_brand(rgb):
                    ring.append(rel_lum(rgb) * 255)
        bg = statistics.median(ring) if ring else med
        # Text pixel = the one furthest from that background.
        best, best_d = None, -1
        for y in range(b[1], b[3] + 1):
            for x in range(b[0], b[2] + 1):
                rgb = px[x, y][:3]
                d = abs(rel_lum(rgb) * 255 - bg)
                if d > best_d:
                    best_d, best = d, rgb
        rows.append((t, round(bg, 1), best, round(contrast(best, (bg,) * 3), 2)))
    res["labels"] = rows

    print(f"=== {tag} ===")
    print(f"  pill band      rows {res['band'][0]}-{res['band'][1]} ({res['band'][2]} dp)")
    print(f"  surface median {res['median']}")
    print(f"  BLEED stddev   {res['stddev']}")
    print(f"  BLEED spread   {res['spread_p98_p02']}   <- page detail leaking through")
    if "capsule" in res:
        home_cx = (labels["Home"][0] + labels["Home"][2]) // 2
        print(f"  capsule        {res['capsule']['width_dp']} dp wide, centre x={res['capsule']['cx']}, "
              f"Home label centre x={home_cx}, delta={abs(res['capsule']['cx'] - home_cx)} px")
    for t, bg, rgb, c in rows:
        print(f"  label {t:11s} bg={bg:<6} text={rgb} contrast={c}:1")
    return res


def main():
    results = {}
    for tag, apk in (("OLD 0.30 (archived 1.12)", OLD_APK), ("NEW 0.94 (redesign)", NEW_APK)):
        if not os.path.exists(apk):
            print(f"{tag}: missing {apk}")
            continue
        install(apk)
        settle()
        xml = Q.dump_xml()
        png = Q.shot(f"ab-{tag.split()[0].strip('()')}.png")
        results[tag] = measure(tag, png, xml)

    a, b = results.get("OLD 0.30 (archived 1.12)"), results.get("NEW 0.94 (redesign)")
    if a and b:
        print("=== DELTA ===")
        print(f"  bleed stddev {a['stddev']} -> {b['stddev']}  ({round(a['stddev'] / max(b['stddev'], 0.01), 2)}x flatter)")
        print(f"  bleed spread {a['spread_p98_p02']} -> {b['spread_p98_p02']}  ({round(a['spread_p98_p02'] / max(b['spread_p98_p02'], 0.01), 2)}x flatter)")
        worst_a = min(r[3] for r in a["labels"])
        worst_b = min(r[3] for r in b["labels"])
        print(f"  worst label contrast {worst_a}:1 -> {worst_b}:1   (WCAG AA floor for 11-12sp is 4.5:1)")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""BUG-012 re-measured on the glass build: light theme, same frame, both builds' numbers.

The bug was: at GlassAlpha 0.30, product names behind the nav pill showed through the tab
labels in the LIGHT theme. The redesign set 0.94; the liquid-glass pass then painted that
same fill through Modifier.liquidGlass. This takes one screenshot and one uiautomator
dump of the same frame with busy product content behind the pill, runs pass_pill's
measurement (BLEED stddev + worst-case label contrast), and runs the identical measurement
on the archived 0.30 screenshot so the result is a delta.

Restores: nothing - theme and settings are not touched; only the scroll position moves.
Untracked scratch, screenshots contain customer data: never commit.
"""
import sys
import time

sys.path.insert(0, "/home/linuai/Balaji/.tmp_device_qa")
from PIL import Image

import pass_pill as P
import pass_v8 as V8
import qa_lib as Q
from pass_glass import tab, wake


def main():
    wake()
    Q.launch()
    time.sleep(1.5)

    # Home, then scroll so product names sit behind the pill - the bug's own condition.
    tab(Q.dump_xml(), "Home")
    time.sleep(1.5)
    Q.swipe(540, 1750, 540, 1150, 350)
    time.sleep(1.8)

    xml = Q.dump_xml()                    # dump and shot in the same frame...
    png = Q.shot("bug012-light-pill.png")  # ...so rects and pixels describe one moment
    img = Image.open(png).convert("RGB")

    labels = V8.tab_labels(xml)
    print("pill labels in frame:", sorted(labels))
    if len(labels) < 4:
        print("ABORT: not all four tab labels are on screen")
        return

    pill = V8.pill_bounds_from_xml(xml, labels)
    if not pill:
        print("ABORT: pill not found in the hierarchy")
        return
    x0, y0, x1, y1 = pill
    print(f"pill bounds from hierarchy: [{x0},{y0}][{x1},{y1}] "
          f"= {round((x1 - x0) / Q.DP)}dp x {round((y1 - y0) / Q.DP)}dp")

    # Every leaf inside the pill - labels and tab icons - excluded from the surface sample.
    # pass_pill's pixel scan was tried first and found the white *cards* above the pill: at
    # 0.94 the pill and the page are both light, so a "mostly light rows" scan cannot tell
    # them apart. The hierarchy can.
    rects = []
    for n in Q.nodes(xml):
        b = Q.bounds(n)
        if not b or b == pill:
            continue
        cls = Q.attr(n, "class")
        if b[0] >= x0 and b[1] >= y0 and b[2] <= x1 and b[3] <= y1:
            if "Text" in cls or "Image" in cls or "Button" in cls:
                rects.append(b)
    print(f"excluded child rects: {len(rects)}")

    # Inset by 1 px so the rim/gradient edge is not sampled as if it were fill.
    band = (y0 + 3, y1 - 3, x0 + 3, x1 - 3)
    P.measure(img, band, rects, "glass build, GlassAlpha 0.94, app-light, pill interior (XML bounds)")

    # The archived 0.30 screenshot, same measurement, for the delta.
    try:
        old = Image.open(f"{Q.SHOTS}/v2-home-light.png").convert("RGB")
    except FileNotFoundError:
        print("(archived v2-home-light.png not found; delta skipped)")
        return
    old_band = P.find_band(old)
    if old_band:
        P.measure(old, old_band, [], "2026-11-05 build, GlassAlpha 0.30 (archived)")


if __name__ == "__main__":
    main()

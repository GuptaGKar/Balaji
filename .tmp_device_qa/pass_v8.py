#!/usr/bin/env python3
"""v8: the four things the working tree changed at 11:41-12:35 and nobody has looked at yet.

The device pass ended at 12:29 with `v7-pill-crop.png` showing the capsule truncating "Categories"
to "Categ...". `NavPill.kt` was edited three minutes later (icon over label, capsule sized to the
slot, `matchParentSize` so the capsule cannot escape the pill) and has not been on a screen since.
This pass exists to answer, with pixels rather than with the diff:

  1. pill geometry  - is the capsule inside the pill on all four tabs, vertically as well as
                      horizontally? The pre-fix build pushed it past the pill's bottom edge, which
                      no unit test could see.
  2. label fit      - does "Categories" render whole? Measured two ways: the text node's width
                      against the two six-character tabs it has to differ from, and the crop
                      itself, looked at.
  3. ADD -> stepper - does the morph still happen after the card moved the control onto the price
                      row, and is BUG-013's "A"/"D" wrap gone?
  4. font scale     - the Home header at 1.3x and 2.0x, which is where a compact badge broke last
                      time.

Not in this pass: the closed-store panels. Closing the store is a live `set_store_open` call
against the production database and the owner has not been asked, so that item stays NOT RUN
rather than being simulated.

Untracked scratch tool. Restores every device setting it touches.
"""
import os
import statistics
import time

from PIL import Image

import qa_lib as Q
from pass_pill import contrast, find_band, is_brand, rel_lum

TABS = ["Home", "Categories", "Orders", "Basket"]
HERE = os.path.dirname(os.path.abspath(__file__))


def install(apk):
    print(f"installing {apk}")
    print(Q.sh("install", "-r", apk))


def wake():
    """The handset drops to dozing between steps, and a screencap of a sleeping panel is black."""
    Q.sh("shell", "input", "keyevent", "KEYCODE_WAKEUP", check=False)
    time.sleep(0.8)
    Q.sh("shell", "input", "keyevent", "82", check=False)   # MENU: dismisses a swipe lockscreen
    time.sleep(0.8)
    Q.sh("shell", "wm", "dismiss-keyguard", check=False)
    time.sleep(1.0)


def cold_start():
    wake()
    Q.sh("shell", "am", "force-stop", Q.PKG, check=False)
    time.sleep(1)
    Q.launch()
    time.sleep(7)
    wake()


def tab(xml, name):
    """Tap a bottom-pill destination by its visible label.

    The page can carry a heading with the same word ("Categories" is both a tab and a section
    title), so only nodes in the bottom fifth of the screen are candidates.
    """
    hits = [n for n in Q.find(xml, text=name)
            if (Q.bounds(n) or (0, 0, 0, 0))[1] > Q.SCREEN_H * 0.80]
    if not hits:
        print(f"  ! no pill node labelled {name}")
        return False
    Q.tap(hits[0])
    time.sleep(2.4)
    return True


def find_desc(xml, contains):
    """Nodes whose TalkBack label contains the string. `Q.find` only reads `text`."""
    return [n for n in Q.nodes(xml)
            if contains.lower() in Q.attr(n, "content-desc").lower()]


def capture(tag, xml):
    """One screenshot used for both reports, so geometry and labels are the same frame."""
    png = Q.shot(f"v8-{tag}.png")
    res = pill_report(tag, png, xml)
    label_report(tag, xml, png)
    if res:
        im = Image.open(png)
        top = max(0, res["pill"][0] - int(30 * Q.DP))
        bot = min(im.size[1], res["pill"][1] + int(30 * Q.DP))
        im.crop((0, top, im.size[0], bot)).save(f"{Q.SHOTS}/v8-crop-{tag}.png")
    return png


def tab_labels(xml):
    """The four pill labels - and only those: a section heading can carry the same word."""
    out = {}
    for name in TABS:
        hits = [n for n in Q.find(xml, text=name)
                if (Q.bounds(n) or (0, 0, 0, 0))[1] > Q.SCREEN_H * 0.75]
        if hits:
            out[name] = Q.bounds(hits[-1])
    return out


def pill_bounds_from_xml(xml, labels):
    """The smallest node in the hierarchy that contains all four labels: the pill itself.

    Reading it from the hierarchy rather than from pixels is what makes the containment claim
    safe - a white card behind the pill is white, the pill surface is white, and a column probe
    cannot tell them apart.
    """
    if len(labels) < 4:
        return None
    best = None
    for n in Q.nodes(xml):
        b = Q.bounds(n)
        if not b:
            continue
        if all(b[0] <= r[0] and b[1] <= r[1] and b[2] >= r[2] and b[3] >= r[3]
               for r in labels.values()):
            area = (b[2] - b[0]) * (b[3] - b[1])
            if best is None or area < best[0]:
                best = (area, b)
    return best[1] if best else None


def pill_report(tag, png, xml):
    """Geometry of one screenshot: pill bounds, capsule extent, whether the capsule is inside it."""
    im = Image.open(png).convert("RGB")
    px = im.load()
    w, h = im.size

    labels = tab_labels(xml)
    pill = pill_bounds_from_xml(xml, labels)
    if not pill:
        print(f"  {tag}: pill node not found in the hierarchy (labels {list(labels)})")
        return None
    pill_top, pill_bot = pill[1], pill[3]
    pill_left, pill_right = pill[0], pill[2]

    # Band rows around the pill, for the capsule scan.
    band = find_band(im)
    if band:
        y0, y1, x_lo, x_hi = band
        y0, y1 = min(y0, pill_top), max(y1, pill_bot)
    else:
        y0, y1 = pill_top - 20, pill_bot + 20
        x_lo, x_hi = round(16 * Q.DP), w - round(16 * Q.DP)

    # Capsule: the longest run of brand-orange per row. A card's ADD pill can sit behind the
    # pill and would otherwise be mistaken for the capsule, and it never spans a whole row.
    runs = {}
    for y in range(max(0, y0 - 40), min(h, y1 + 40)):
        best = (0, None, None)      # length, x0, x1
        cur = 0
        start = None
        for x in range(x_lo, x_hi):
            if is_brand(px[x, y][:3]):
                if cur == 0:
                    start = x
                cur += 1
                if cur > best[0]:
                    best = (cur, start, x)
            else:
                cur = 0
        if best[1] is not None:
            runs[y] = best
    wide = {y: r for y, r in runs.items() if r[0] >= round(40 * Q.DP)}
    if not wide:
        print(f"  {tag}: no capsule pixels")
        return None
    cy0, cy1 = min(wide), max(wide)
    cx0 = min(r[1] for r in wide.values())
    cx1 = max(r[2] for r in wide.values())

    inset_top, inset_bot = cy0 - pill_top, pill_bot - cy1
    inset_left, inset_right = cx0 - pill_left, pill_right - cx1
    inside = inset_top > 4 and inset_bot > 4 and inset_left > 0 and inset_right > 0
    print(f"  {tag}")
    print(f"    pill    y {pill_top}-{pill_bot} ({round((pill_bot - pill_top) / Q.DP, 1)} dp), "
          f"x {pill_left}-{pill_right}")
    print(f"    capsule y {cy0}-{cy1} ({round((cy1 - cy0) / Q.DP, 1)} dp), "
          f"x {cx0}-{cx1} ({round((cx1 - cx0) / Q.DP, 1)} dp)")
    print(f"    inset above {round(inset_top / Q.DP, 1)} dp, below {round(inset_bot / Q.DP, 1)} dp, "
          f"left {round(inset_left / Q.DP, 1)} dp, right {round(inset_right / Q.DP, 1)} dp"
          f"\n    -> {'INSIDE the pill' if inside else 'OVERFLOWS the pill'}")
    return {"pill": (pill_top, pill_bot), "capsule": (cy0, cy1), "inside": inside}


def label_report(tag, xml, png):
    """Label rects, and the width a reader would compare them by."""
    im = Image.open(png).convert("RGB")
    px = im.load()
    rows = tab_labels(xml)
    if len(rows) < 4:
        print(f"  {tag}: labels found {list(rows)}")
        return None
    print(f"  {tag} label widths (dp):")
    for name, b in rows.items():
        width_dp = round((b[2] - b[0]) / Q.DP, 1)
        # Local background: the ring just outside the label, *including* the capsule for the
        # selected tab - the pair a reader sees there is white on orange, not white on white.
        ring = []
        for x in range(max(0, b[0] - 14), min(im.size[0], b[2] + 14), 3):
            for y in (max(0, b[1] - 12), min(im.size[1], b[3] + 12)):
                ring.append(rel_lum(px[x, y][:3]) * 255)
        bg = statistics.median(ring) if ring else 128
        best, best_d = None, -1
        for y in range(b[1], b[3]):
            for x in range(b[0], b[2]):
                d = abs(rel_lum(px[x, y][:3]) * 255 - bg)
                if d > best_d:
                    best_d, best = d, px[x, y][:3]
        c = round(contrast(best, (bg,) * 3), 2) if best else 0
        print(f"    {name:11s} {width_dp:>6} dp   contrast {c}:1")
    cat, orders = rows["Categories"][2] - rows["Categories"][0], rows["Orders"][2] - rows["Orders"][0]
    verdict = "WIDER than the 6-character tabs -> not truncated" if cat > orders * 1.25 \
        else "SAME WIDTH as the 6-character tabs -> suspicious"
    print(f"    Categories/Orders width ratio {round(cat / max(orders, 1), 2)}: {verdict}")
    return rows


def main():
    args = os.sys.argv[1:]
    apk = args[0] if args else "/home/linuai/Balaji/android/app/build/outputs/apk/customer/debug/app-customer-debug.apk"
    before = Q.state()
    print("device state before:", before)
    if "--skip-install" not in args:
        install(apk)

    # ---- 1. pill geometry and label fit on all four tabs -------------------
    cold_start()
    capture("home", Q.dump_xml())

    for name in ("Categories", "Orders", "Basket", "Home"):
        xml = Q.dump_xml()
        if not tab(xml, name):
            continue
        capture(name.lower(), Q.dump_xml())

    # ---- 2. ADD -> stepper on a Home rail ---------------------------------
    xml = Q.dump_xml()
    wake()
    Q.tap((540, 1500))
    time.sleep(1.2)
    Q.swipe(540, 1700, 540, 900, 500)
    time.sleep(1.7)
    Q.swipe(540, 1700, 540, 1000, 500)
    time.sleep(1.8)
    xml = Q.dump_xml()
    Q.shot("v8-before-add.png")
    adds = Q.find(xml, text="ADD")
    print(f"  ADD pills on screen: {len(adds)}")
    if adds:
        Q.tap(adds[0])
        time.sleep(1.6)
        xml = Q.dump_xml()
        Q.shot("v8-after-add.png")
        minus = Q.find(xml, contains="Remove one from")
        plus = Q.find(xml, contains="Add one more to")
        print(f"  stepper nodes after tap: minus={len(minus)} plus={len(plus)}")
        for n in minus + plus:
            b = Q.bounds(n)
            print(f"    {Q.attr(n, 'content-desc')[:44]:46s} {b} "
                  f"{round((b[3] - b[1]) / Q.DP)}dp tall")
        # BUG-013: the ADD pill wrapping to two lines. Its height is the tell.
        for n in Q.find(xml, text="ADD"):
            b = Q.bounds(n)
            print(f"    remaining ADD pill {b} -> {round((b[3] - b[1]) / Q.DP)} dp tall")

    # ---- 3. font scale: the header that broke at 43 characters ------------
    original_scale = before.get("font_scale", "1.0")
    for scale in ("1.3", "2.0"):
        Q.sh("shell", "settings", "put", "system", "font_scale", scale)
        cold_start()
        Q.shot(f"v8-header-font{scale.replace('.', '')}.png")
        xml = Q.dump_xml()
        badge = Q.find(xml, text="Store Open") + Q.find(xml, text="Store Closed")
        addr = Q.find(xml, contains="Delivering to")
        print(f"  font {scale}: badge={bool(badge)} addressNode={bool(addr)} "
              f"badgeBounds={[Q.bounds(b) for b in badge]}")
        Q.shot(f"v8-home-font{scale.replace('.', '')}.png")
    Q.sh("shell", "settings", "put", "system", "font_scale", original_scale)
    print("restored font_scale", original_scale)
    cold_start()
    Q.shot("v8-restored.png")
    print("device state after:", Q.state())


if __name__ == "__main__":
    main()
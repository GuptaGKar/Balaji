#!/usr/bin/env python3
"""Second half of the device pass: the screens the pill sits over, with a real basket.

Every step is measured from the uiautomator hierarchy rather than eyeballed: the pill band, the
basket bar band, the sticky checkout band, and the lowest content node, so "clears the pill" is a
number and not an opinion. The basket is left with the same number of lines it was found with.

Untracked scratch tool.
"""
import json
import os
import re
import subprocess
import sys
import time

ADB = "/home/linuai/gate_prep/Android/Sdk/platform-tools/adb"
PKG = "com.balaji.provision"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "shots")
DP = 450.0 / 160.0
SCREEN_H = 2340
PILL_H_PX = round(64 * DP)
os.makedirs(OUT, exist_ok=True)


def sh(*args, binary=False):
    r = subprocess.run([ADB, *args], capture_output=True)
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")


def dump():
    for attempt in range(3):
        sh("shell", "uiautomator", "dump", "/sdcard/p.xml")
        xml = sh("exec-out", "cat", "/sdcard/p.xml", binary=True).decode("utf-8", "replace")
        if "<hierarchy" in xml:
            return xml
        time.sleep(1.5)
    return xml


def nodes(xml):
    return re.findall(r"<node[^>]*?/?>", xml)


def attr(n, key):
    m = re.search(r'(?:^|\s)' + key + r'="([^"]*)"', n)
    return m.group(1) if m else None


def box(n):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", attr(n, "bounds") or "")
    return tuple(int(v) for v in m.groups()) if m else None


def shot(name, xml):
    open(os.path.join(OUT, name + ".png"), "wb").write(sh("exec-out", "screencap", "-p", binary=True))
    open(os.path.join(OUT, name + ".xml"), "w").write(xml)


def find(xml, wanted, exact=True):
    hits = []
    for n in nodes(xml):
        t = attr(n, "text") or ""
        if (t == wanted) if exact else (wanted.lower() in t.lower()):
            b = box(n)
            if b:
                hits.append((t, b))
    return hits


def tap(x, y, wait=2.0):
    sh("shell", "input", "tap", str(x), str(y))
    time.sleep(wait)


def tap_text(wanted, wait=2.0, exact=True):
    hits = find(dump(), wanted, exact)
    if not hits:
        print(f"    !! no tappable node {wanted!r}")
        return False
    b = hits[0][1]
    tap((b[0] + b[2]) // 2, (b[1] + b[3]) // 2, wait)
    return True


def band(xml, low_y=1500):
    """Everything drawn in the bottom third, which is where the pill competes for room."""
    out = []
    for n in nodes(xml):
        b = box(n)
        t = attr(n, "text") or attr(n, "content-desc") or ""
        if b and b[1] >= low_y and t.strip():
            out.append((t.strip()[:40], b))
    out.sort(key=lambda x: x[1][1])
    return out


def pill_bounds(xml):
    for n in nodes(xml):
        b = box(n)
        if not b:
            continue
        h = (b[3] - b[1]) / DP
        if 58 <= h <= 70 and (b[2] - b[0]) > 800 and b[3] > 1900:
            return b
    return None


def analyse(name, xml, expect_pill=True):
    p = pill_bounds(xml)
    lines = [f"== {name}"]
    lines.append("   bottom band (top->bottom): " + ", ".join(f"{t}@{b[1]}-{b[3]}" for t, b in band(xml)[:6]))
    if p:
        lines.append(f"   pill: {p} h={(p[3]-p[1])/DP:.1f}dp gap_to_bottom={(SCREEN_H-p[3])/DP:.1f}dp")
    else:
        lines.append("   pill: ABSENT")
    ok = (p is not None) == expect_pill
    lines.append(f"   expected pill present={expect_pill} -> {'MATCH' if ok else 'MISMATCH'}")
    # lowest content that is not part of the pill or the basket bar
    lowest = None
    for t, b in band(xml):
        if p and b[1] >= p[1] - 2:
            continue
        if lowest is None or b[3] > lowest[1][3]:
            lowest = (t, b)
    if p:
        if lowest:
            gap = p[1] - lowest[1][3]
            lines.append(f"   lowest non-pill node {lowest[0]!r} bottom={lowest[1][3]} -> "
                         f"{'clear by %dpx' % gap if gap >= 0 else 'COVERED by %dpx' % -gap}")
        else:
            lines.append("   no content node above the pill in this view")
    print("\n".join(lines))
    return p


def scroll_bottom(repeat=10):
    for _ in range(repeat):
        sh("shell", "input", "swipe", "540", "1700", "540", "450", "250")
        time.sleep(0.3)


def main():
    sh("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    time.sleep(1)
    sh("shell", "am", "start", "-n", PKG + "/.MainActivity")
    time.sleep(5)

    xml = dump()
    analyse("01-home-top", xml)
    shot("v2-home-top", xml)
    print("   basket state before touching anything:",
          [t for t, b in find(xml, "Basket", False)][:1],
          [t for t, bb in band(xml) if "item" in t or "item" in t.lower()][:3])

    scroll_bottom()
    xml = dump()
    analyse("02-home-bottom", xml)
    shot("v2-home-bottom", xml)

    # put exactly one line in the basket so MiniCartBar is drawn over Home. The product is added
    # and removed from the Home rail stepper, never from the Basket header, which holds a
    # destructive "Clear cart" icon.
    xml = dump()
    add = find(xml, "ADD")
    added = None
    if add:
        b = add[0][1]
        tap(b[0] + (b[2] - b[0]) // 2, (b[1] + b[3]) // 2, 2.0)
        xml = dump()
        shot("v2-after-add", xml)
        print("== 03-after-add: ADD tapped; stepper now present:",
              [t for t, bb in band(xml) if t in ("−", "+", "-", "1")][:4])
    else:
        print("== 03-after-add: no ADD node found on this screen, basket left untouched")

    xml = dump()
    analyse("05-home-with-basket", xml)
    shot("v2-home-basket", xml)

    tap_text("Categories", wait=2.5)
    xml = dump()
    analyse("06-categories", xml)
    shot("v2-categories", xml)

    tap_text("Orders", wait=2.5)
    scroll_bottom()
    xml = dump()
    analyse("07-orders-bottom", xml)
    shot("v2-orders-bottom", xml)

    tap_text("Basket", wait=2.5)
    xml = dump()
    analyse("08-basket", xml)
    shot("v2-basket", xml)
    sticky = [t for t, b in band(xml) if "Checkout" in t]
    print("   sticky checkout button visible:", sticky[:2])

    # leave the basket as it was found: go back to the Home rail and take that stepper down
    tap_text("Home", wait=2.5)
    xml = dump()
    minus = find(xml, "−") or find(xml, "-")
    if minus:
        b = minus[0][1]
        tap(b[0] + (b[2] - b[0]) // 2, (b[1] + b[3]) // 2, 2.0)
        xml = dump()
        print("   undo tapped on the Home rail; ADD visible again:", bool(find(xml, "ADD")))
        shot("v2-after-undo", xml)
    else:
        print("   !! no stepper found on the Home rail - the added line may still be in the basket")
        shot("v2-undo-failed", xml)
    print("\nDone.")


if __name__ == "__main__":
    sys.exit(main())

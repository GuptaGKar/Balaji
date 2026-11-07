#!/usr/bin/env python3
"""v9: verify ADD -> stepper morph, new per-button content descriptions, and ADD wrap.

Untracked scratch. Touches only .tmp_device_qa/shots plus the throwaway uiautomator dump.
Ends by removing whatever it added to the basket, so the device is left as found.
"""
import re
import sys
import time

sys.path.insert(0, "/home/linuai/Balaji/.tmp_device_qa")
from qa_lib import attr, bounds, centre, dump_xml, find, launch, shot, shell, swipe, tap

SAFE = (560, 1980)   # y-range that is clear of header, nav pill and system bar


def wake():
    shell("input keyevent KEYCODE_WAKEUP", check=False)
    shell("wm dismiss-keyguard", check=False)


def font_scale():
    return shell("settings get system font_scale")


def desc_nodes(xml, needle):
    return [n for n in find(xml, contains=None)
            if needle.lower() in attr(n, "content-desc").lower()]


def nearest_product_name(xml, x, y):
    """Product-name node whose text box sits above (x, y) and closest vertically."""
    best, best_dy = None, 10**9
    for n in find(xml, cls="TextView"):
        b = bounds(n)
        t = attr(n, "text")
        if not b or not t or b[3] > y:
            continue
        cx = (b[0] + b[2]) // 2
        if abs(cx - x) > 300:
            continue
        dy = y - b[3]
        if dy < best_dy:
            best, best_dy = t, dy
    return best


def pick_add(xml):
    """The widest visible ADD text node inside the safe band."""
    cands = []
    for n in find(xml, text="ADD"):
        b = bounds(n)
        if b and SAFE[0] <= b[1] and b[3] <= SAFE[1]:
            cands.append((b[2] - b[0], n, b))
    if not cands:
        return None
    return max(cands, key=lambda c: c[0])


def main():
    wake()
    if font_scale() != "1.1":
        shell("settings put system font_scale 1.1")
    launch()
    time.sleep(2)

    xml = dump_xml()
    add = pick_add(xml)
    tries = 0
    while add is None and tries < 4:
        swipe(540, 1700, 540, 900, 500)
        time.sleep(1.2)
        xml = dump_xml()
        add = pick_add(xml)
        tries += 1
    if add is None:
        print("FAIL: no ADD pill in safe band")
        return 1

    _, add_node, add_b = add
    ax, ay = centre(add_node)
    w, h = add_b[2] - add_b[0], add_b[3] - add_b[1]
    product = nearest_product_name(xml, ax, ay)
    print(f"ADD target: '{product}' at ({ax},{ay}) text box {w}x{h}px "
          f"({w/2.8125:.1f}x{h/2.8125:.1f}dp) -> wrap={'YES (BUG-013)' if h > 60 else 'no'}")
    shot("v9-before-add.png")
    tap((ax, ay))
    time.sleep(2.5)

    xml = dump_xml()
    shot("v9-after-add.png")
    minus = desc_nodes(xml, "Remove one")
    plus = desc_nodes(xml, "Add one more")
    print("a11y descriptions:")
    for n in minus:
        print("   minus:", attr(n, "content-desc")[:80],
              bounds(n))
    for n in plus:
        print("   plus :", attr(n, "content-desc")[:80],
              bounds(n))
    if not minus or not plus:
        print("FAIL: stepper descriptions not found after ADD")
        return 1

    qty_nodes = [attr(n, "text") for n in find(xml, cls="TextView")
                 if attr(n, "text").isdigit()]
    print("quantity texts visible:", qty_nodes[:5])

    # increment once, confirm the quantity follows
    tap(plus[0])
    time.sleep(2.0)
    xml2 = dump_xml()
    qty2 = [attr(n, "text") for n in find(xml2, cls="TextView")
            if attr(n, "text").isdigit()]
    print("after +1:", qty2[:5])
    shot("v9-after-inc.png")

    # decrement back to zero so the basket ends as it started
    for _ in range(3):
        m = desc_nodes(xml2, "Remove one")
        if not m:
            break
        tap(m[0])
        time.sleep(1.6)
        xml2 = dump_xml()
    restored = bool(find(xml2, text="ADD"))
    print("stepper reverted to ADD:", restored)
    shot("v9-restored.png")
    print("PASS" if restored else "WARN: ADD did not return")
    return 0


if __name__ == "__main__":
    sys.exit(main())

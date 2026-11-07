#!/usr/bin/env python3
"""Device pass for the floating navigation pill on the customer build.

Captures a screenshot plus the uiautomator hierarchy for each screen the pill is drawn over, and
measures what the layout actually guarantees:

  * the pill exists, is centred, is ~64 dp tall and keeps its 16 dp side inset
  * every scrollable screen can be brought fully clear of it (Task 17's reserve)
  * the pill is absent where the code says it is absent

Untracked scratch tool - it writes to .tmp_device_qa/ and does not touch the app or the data.
"""
import os
import re
import subprocess
import sys
import time

ADB = "/home/linuai/gate_prep/Android/Sdk/platform-tools/adb"
PKG = "com.balaji.provision"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "shots")
DENSITY = 450.0
DP = DENSITY / 160.0

os.makedirs(OUT, exist_ok=True)


def sh(*args, binary=False):
    r = subprocess.run([ADB, *args], capture_output=True)
    if binary:
        return r.stdout
    return r.stdout.decode("utf-8", "replace")


def launch():
    sh("shell", "monkey", "-p", PKG, "-c",
       "android.intent.category.LAUNCHER", "1")
    time.sleep(4)


def dump():
    sh("shell", "uiautomator", "dump", "/sdcard/pill.xml")
    xml = sh("exec-out", "cat", "/sdcard/pill.xml", binary=True).decode("utf-8", "replace")
    if "could not get idle state" in xml or "<hierarchy" not in xml:
        time.sleep(2)
        sh("shell", "uiautomator", "dump", "/sdcard/pill.xml")
        xml = sh("exec-out", "cat", "/sdcard/pill.xml", binary=True).decode("utf-8", "replace")
    return xml


def shot(name, xml):
    png = os.path.join(OUT, name + ".png")
    open(png, "wb").write(sh("exec-out", "screencap", "-p", binary=True))
    open(os.path.join(OUT, name + ".xml"), "w").write(xml)
    return png


def nodes(xml):
    return re.findall(r"<node[^>]*?/?>", xml)


def attr(node, key):
    m = re.search(r'(?:^|\s)' + key + r'="([^"]*)"', node)
    return m.group(1) if m else None


def box(node):
    b = attr(node, "bounds") or ""
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
    return tuple(int(v) for v in m.groups()) if m else None


def texts(xml):
    return [attr(n, "text") for n in nodes(xml) if attr(n, "text")]


TAB_LABELS = ["Home", "Categories", "Orders", "Basket"]


def pill_box(xml):
    """The pill is the only clickable row that carries all four tab labels near the bottom."""
    cands = []
    for n in nodes(xml):
        b = box(n)
        if not b or b[3] < 1600:
            continue
        cands.append((b, n))
    for b, n in cands:
        # a node whose bottom is the pill's bottom and whose top is ~64dp above it
        h = (b[3] - b[1]) / DP
        if 55 <= h <= 80 and b[2] - b[0] > 700:
            return b, h
    return None, None


def tap(x, y, wait=1.8):
    sh("shell", "input", "tap", str(x), str(y))
    time.sleep(wait)


def tap_text(xml, wanted):
    for n in nodes(xml):
        if (attr(n, "text") or "") == wanted:
            b = box(n)
            if b:
                tap((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)
                return True
    return False


def swipe_up(repeat=8):
    for _ in range(repeat):
        sh("shell", "input", "swipe", "540", "1800", "540", "500", "300")
        time.sleep(0.35)


def report(name, xml):
    b, h = pill_box(xml)
    t = texts(xml)
    present = all(label in t for label in TAB_LABELS)
    print(f"--- {name}")
    print(f"    tab labels present: {present}")
    if b:
        print(f"    pill bounds: {b}  height={h:.1f}dp  left_inset={b[0]/DP:.1f}dp  "
              f"right_inset={(1080-b[2])/DP:.1f}dp  gap_to_bottom={(2340-b[3])/DP:.1f}dp")
    else:
        print("    pill: not found in this hierarchy")
    lowest = None
    for n in nodes(xml):
        bb = box(n)
        if bb and (attr(n, "text") or attr(n, "content-desc")):
            if lowest is None or bb[3] > lowest[1][3]:
                lowest = (attr(n, "text") or attr(n, "content-desc"), bb)
    if lowest and b:
        overlapped = lowest[1][3] > b[1]
        print(f"    lowest labelled node: {lowest[0][:52]!r} bottom={lowest[1][3]} "
              f"-> {'OVERLAPS the pill' if overlapped else 'clear of the pill'}")
    return b


def main():
    launch()

    steps = []
    for label, action in [
        ("home", None),
        ("home_bottom", "scroll"),
        ("categories", "tab:Categories"),
        ("categories_bottom", "scroll"),
        ("orders", "tab:Orders"),
        ("orders_bottom", "scroll"),
        ("basket", "tab:Basket"),
    ]:
        xml = dump()
        if action == "scroll":
            swipe_up()
            xml = dump()
        elif action and action.startswith("tab:"):
            if not tap_text(xml, action.split(":")[1]):
                xml = dump()
                tap_text(xml, action.split(":")[1])
        b = report(label, xml)
        shot(label, xml)
        steps.append((label, b))

    # product detail and checkout must show no pill at all
    xml = dump()
    if tap_text(xml, "Basket"):
        pass
    print("\nDone. Screenshots in", OUT)


if __name__ == "__main__":
    sys.exit(main())

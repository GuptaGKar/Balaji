import re
import subprocess
import sys
import time

ADB = "/home/linuai/gate_prep/Android/Sdk/platform-tools/adb"
PKG = "com.balaji.provision"


def sh(*args):
    return subprocess.run([ADB, *args], capture_output=True, text=True).stdout


def dump():
    sh("shell", "uiautomator", "dump", "/sdcard/step.xml")
    return sh("exec-out", "cat", "/sdcard/step.xml")


def nodes(xml):
    return re.findall(r"<node[^>]*>", xml)


def attr(node, key):
    m = re.search(key + r'="([^"]*)"', node)
    return m.group(1) if m else None


def box(node):
    b = attr(node, "bounds") or ""
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
    if not m:
        return None
    return tuple(int(v) for v in m.groups())


def center(b):
    x1, y1, x2, y2 = b
    return (x1 + x2) // 2, (y1 + y2) // 2


def tap_node_for_text(xml, text, require_clickable_parent=True):
    """Find a node with the given text and tap its clickable ancestor (or itself)."""
    target = None
    for n in nodes(xml):
        if attr(n, "text") == text:
            target = n
            break
    if target is None:
        return None
    tb = box(target)
    if not tb:
        return None
    tx, ty = center(tb)

    if require_clickable_parent:
        best = None
        for n in nodes(xml):
            if attr(n, "clickable") != "true":
                continue
            b = box(n)
            if not b:
                continue
            if b[0] <= tx <= b[2] and b[1] <= ty <= b[3]:
                area = (b[2] - b[0]) * (b[3] - b[1])
                if best is None or area < best[0]:
                    best = (area, b)
        if best:
            x, y = center(best[1])
            sh("shell", "input", "tap", str(x), str(y))
            return x, y

    x, y = tx, ty
    sh("shell", "input", "tap", str(x), str(y))
    return x, y


def texts(xml, limit=14):
    out = []
    for n in nodes(xml):
        t = attr(n, "text") or ""
        if 2 <= len(t) <= 48:
            out.append(t)
        if len(out) >= limit:
            break
    return out


if __name__ == "__main__":
    action = sys.argv[1] if len(sys.argv) > 1 else "status"
    xml = dump()
    print("before:", texts(xml, 8))

    if action == "add":
        hit = tap_node_for_text(xml, "ADD")
        print("tapped ADD at", hit)
        time.sleep(3)
        after = dump()
        print("after :", texts(after, 8))
        print("cart_hint:", "item" in " ".join(texts(after, 40)).lower())
    elif action == "basket":
        hit = tap_node_for_text(xml, "Basket")
        print("tapped Basket at", hit)
        time.sleep(3)
        after = dump()
        print("after :", texts(after, 12))
    elif action == "text":
        want = sys.argv[2]
        hit = tap_node_for_text(xml, want)
        print(f"tapped {want!r} at", hit)
        time.sleep(3)
        print("after :", texts(dump(), 12))

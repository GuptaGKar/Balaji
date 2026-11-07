#!/usr/bin/env python3
"""Shared device helpers for the redesign pass.

Untracked scratch tool. Nothing here writes outside .tmp_device_qa except the throwaway
uiautomator dump on the handset, which is deleted by dump_xml() on every call. The dumps and
screenshots can contain the signed-in customer's area name, so this directory must stay
untracked and must never be committed.
"""
import os
import re
import subprocess
import time

ADB = "/home/linuai/gate_prep/Android/Sdk/platform-tools/adb"
PKG = "com.balaji.provision"
ACTIVITY = f"{PKG}/.MainActivity"
HERE = os.path.dirname(os.path.abspath(__file__))
SHOTS = os.path.join(HERE, "shots")
DP = 450.0 / 160.0          # 1 dp = 2.8125 px on the A34 at its physical density
SCREEN_W, SCREEN_H = 1080, 2340
os.makedirs(SHOTS, exist_ok=True)


def sh(*args, binary=False, check=True):
    r = subprocess.run([ADB, *args], capture_output=True)
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} -> {r.stderr.decode('utf-8','replace').strip()}")
    return r.stdout if binary else r.stdout.decode("utf-8", "replace").strip()


def shell(cmd, check=True):
    return sh("shell", cmd, check=check)


def shot(name):
    """PNG into shots/, returned as a path. `exec-out` avoids CRLF corruption."""
    path = os.path.join(SHOTS, name)
    with open(path, "wb") as fh:
        fh.write(sh("exec-out", "screencap", "-p", binary=True))
    return path


def dump_xml():
    """Hierarchy text, with the on-device temp file removed again."""
    xml = ""
    for _ in range(4):
        sh("shell", "uiautomator", "dump", "/sdcard/qa_pass.xml", check=False)
        xml = sh("exec-out", "cat", "/sdcard/qa_pass.xml", binary=True).decode("utf-8", "replace")
        sh("shell", "rm", "-f", "/sdcard/qa_pass.xml", check=False)
        if "<hierarchy" in xml:
            return xml
        time.sleep(1.2)
    return xml


NODE = re.compile(r"<node[^>]*?/?>")


def nodes(xml):
    return NODE.findall(xml)


def attr(node, key, default=""):
    m = re.search(rf'{key}="([^"]*)"', node)
    return m.group(1) if m else default


def bounds(node):
    m = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
    if not m:
        return None
    x1, y1, x2, y2 = (int(v) for v in m.groups())
    return x1, y1, x2, y2


def find(xml, text=None, contains=None, cls=None, rid=None):
    """Nodes matching the given criteria, in document order."""
    out = []
    for n in nodes(xml):
        t = attr(n, "text")
        if text is not None and t != text:
            continue
        if contains is not None and contains.lower() not in t.lower():
            continue
        if cls is not None and cls not in attr(n, "class"):
            continue
        if rid is not None and rid not in attr(n, "resource-id"):
            continue
        out.append(n)
    return out


def centre(node):
    x1, y1, x2, y2 = bounds(node)
    return (x1 + x2) // 2, (y1 + y2) // 2


def tap(node_or_point):
    x, y = centre(node_or_point) if isinstance(node_or_point, str) else node_or_point
    sh("shell", "input", "tap", str(x), str(y))
    return x, y


def swipe(x1, y1, x2, y2, ms=400):
    sh("shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), str(ms))


def launch():
    sh("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1", check=False)
    time.sleep(3.5)


def state():
    """Device settings the pass depends on, all of them restorable."""
    return {
        # `cmd uimode night query` is not supported on this Samsung build; the stored setting and
        # the resolved night mode are read separately so a failure to change one is visible.
        "ui_night_mode_setting": shell("settings get secure ui_night_mode"),
        "mNightMode": shell("dumpsys uimode | grep -m1 -o 'mNightMode=.*'", check=False),
        "ui_mode_resolved": shell("dumpsys uimode | grep -m1 -o 'mCurUiMode=.*'", check=False),
        "font_scale": shell("settings get system font_scale"),
        "anim_window": shell("settings get global window_animation_scale"),
        "anim_transition": shell("settings get global transition_animation_scale"),
        "anim_duration": shell("settings get global animator_duration_scale"),
        "wakefulness": shell("dumpsys power | grep -m1 -o 'mWakefulness=.*'", check=False),
    }

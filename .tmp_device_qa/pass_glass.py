#!/usr/bin/env python3
"""glass: screenshot the liquid-glass UI on the A34, light and dark.

Untracked scratch. Captures Home / Categories / Cart / Checkout / Orders (plus product
details when it is easy to reach) in both themes, then restores everything it touched:
night mode back to the value it found, basket emptied again. Screenshots land in shots/
and contain customer data - this directory must never be committed.

Checkout is screenshotted on the review step only. "Slide to Place Order" is never
swiped: placing an order on the owner's live store is not a screenshot.
"""
import os
import sys
import time

sys.path.insert(0, "/home/linuai/Balaji/.tmp_device_qa")
from qa_lib import attr, bounds, centre, dump_xml, find, launch, shot, shell, swipe, tap, sh

SAFE = (560, 1980)
EMPTY_BASKET = "Browse the store and add the groceries"


def wake():
    shell("input keyevent KEYCODE_WAKEUP", check=False)
    shell("wm dismiss-keyguard", check=False)


def night_mode():
    """`mNightMode=N (yes|no)` is the readable line on this Samsung build; the resolved
    `mCurUiMode` is hex and contains no 'night' string to grep for (the first version of
    this pass got that wrong and left the device in the wrong theme)."""
    return shell("dumpsys uimode | grep -m1 -oE 'mNightMode=[0-9]+ \\((yes|no)\\)'", check=False)


def is_night() -> bool:
    return "(yes)" in night_mode()


def set_night(yes: bool) -> bool:
    """Set dark/light and verify from the resolved mode. Returns True only if the device
    actually reports the requested mode afterwards."""
    want = "(yes)" if yes else "(no)"
    shell(f"cmd uimode night {'yes' if yes else 'no'}", check=False)
    for _ in range(10):
        time.sleep(0.75)
        if want in night_mode():
            return True
    return want in night_mode()


def tab(xml, name):
    """Tap a bottom-pill destination by its visible label."""
    for n in find(xml, text=name):
        b = bounds(n)
        if b and (b[3] - b[1]) < 200:
            tap(((b[0] + b[2]) // 2, (b[1] + b[3]) // 2))
            time.sleep(2.0)
            return True
    print(f"  ! no pill node labelled {name}")
    return False


def scroll(dy=900):
    swipe(540, 1750, 540, 1750 - dy, 350)
    time.sleep(1.2)


def cap(tag):
    p = shot(f"glass-{tag}.png")
    sz = os.path.getsize(p)
    if sz < 1000:
        print(f"  !! cap {tag}: {sz} B (channel suspect)")
    else:
        print(f"  cap {tag} {sz // 1024} KB")


def ensure_basket():
    """The pass screenshots Cart and Checkout, so the basket needs something in it.
    Checked *on the Basket screen* - the first version of this pass dumped whatever
    screen the app happened to open on and concluded 'has lines' from the Home copy."""
    tab(dump_xml(), "Basket")
    time.sleep(2.0)
    xml = dump_xml()
    if EMPTY_BASKET in xml:
        print("  basket empty -> adding two lines from Home")
        tab(dump_xml(), "Home")
        time.sleep(1.5)
        for _ in range(2):
            x = dump_xml()
            adds = [n for n in find(x, text="ADD")
                    if (b := bounds(n)) and SAFE[0] <= b[1] and b[3] <= SAFE[1]]
            if not adds:
                scroll()
                x = dump_xml()
                adds = [n for n in find(x, text="ADD")
                        if (b := bounds(n)) and SAFE[0] <= b[1] and b[3] <= SAFE[1]]
            if not adds:
                print("  ! no ADD pill in safe band; cart screenshots may be empty-state")
                return
            tap(adds[0])
            time.sleep(1.5)
        tab(dump_xml(), "Basket")
        time.sleep(2.0)
        print("  basket after adding:", "empty" if EMPTY_BASKET in dump_xml() else "has lines")
    else:
        print("  basket already has lines")


def empty_basket():
    """Remove every line through the stepper, then confirm the empty state."""
    for i in range(16):
        xml = dump_xml()
        if EMPTY_BASKET in xml:
            print(f"  basket empty after {i} removals")
            return True
        minus = [n for n in find(xml, contains=None)
                 if "Remove one" in attr(n, "content-desc")]
        if not minus:
            # a row may be below the fold
            scroll()
            xml = dump_xml()
            minus = [n for n in find(xml, contains=None)
                     if "Remove one" in attr(n, "content-desc")]
        if not minus:
            print(f"  ! no stepper left to tap (iteration {i}); dump has no 'Remove one'")
            return False
        b = bounds(minus[0])
        if b and not (SAFE[0] <= b[1] and b[3] <= SAFE[1]):
            # steppers sit above the pill; still tappable if clear of the system bar
            if b[3] > 2260:
                scroll(-400)
                continue
        tap(minus[0])
        time.sleep(1.4)
    print("  ! removal loop hit its cap")
    return False


def go_checkout():
    """Cart -> checkout by swiping the 'Slide to Checkout' control. Never touches the
    review step's 'Slide to Place Order' slider."""
    xml = dump_xml()
    thumbs = [n for n in find(xml, contains=None)
              if attr(n, "content-desc") == "slideThumb"
              or "Slide to Checkout" in attr(n, "text")
              or "Slide to Checkout" in attr(n, "content-desc")]
    if not thumbs:
        # the slider may be below the fold
        scroll(-600)
        xml = dump_xml()
        thumbs = [n for n in find(xml, contains=None)
                  if attr(n, "content-desc") == "slideThumb"
                  or "Slide to Checkout" in attr(n, "text")
                  or "Slide to Checkout" in attr(n, "content-desc")]
    if not thumbs:
        print("  ! Slide to Checkout not found; checkout screenshot skipped")
        return False
    x1, y1, x2, y2 = bounds(thumbs[0])
    cy = (y1 + y2) // 2
    swipe(x1 + 40, cy, x2 - 40, cy, 600)
    time.sleep(2.5)
    return True


def capture_theme(theme, tag):
    print(f" [{theme}]")
    # 01 Home top (header + glass search + banner)
    tab(dump_xml(), "Home")
    time.sleep(1.5)
    cap(f"{tag}-01-home-top")
    # 02 Home cards (rails + pill over a busy page)
    scroll(1500)
    cap(f"{tag}-02-home-cards")
    # 03 Categories
    tab(dump_xml(), "Categories")
    time.sleep(1.5)
    cap(f"{tag}-03-categories")
    # 04 Cart
    tab(dump_xml(), "Basket")
    time.sleep(2.0)
    cap(f"{tag}-04-cart")
    # 05 Checkout review step (screenshot only - no slide)
    if go_checkout():
        cap(f"{tag}-05-checkout")
        sh("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(2.0)
    # 06 Orders
    tab(dump_xml(), "Orders")
    time.sleep(2.0)
    cap(f"{tag}-06-orders")


def main():
    before_state = None
    try:
        from qa_lib import state
        before_state = state()
        print("device state before:", before_state)
    except Exception as e:  # noqa: BLE001 - state() is a nicety, not a gate
        print("  state() failed:", e)

    wake()
    launch()

    ensure_basket()

    # The device was found in DARK mode (ui_night_mode=2, mNightMode=2 (yes), recorded in
    # the first run's 'device state before'). The first run's broken night check left it in
    # light, so 'dark' is both the first theme to shoot and the restore target, every run.
    print("night mode at start:", night_mode())

    print("== dark ==")
    if set_night(True):
        time.sleep(2.0)
        capture_theme("dark", "dark")
    else:
        print("  !! could not switch to dark; dark screenshots NOT TAKEN")

    print("== light ==")
    if set_night(False):
        time.sleep(2.5)
        capture_theme("light", "light")
    else:
        print("  !! could not switch to light; light screenshots NOT TAKEN")

    print("== restore ==")
    restored = set_night(True)
    time.sleep(1.5)
    print("night mode after:", night_mode(), "restored:", restored)

    # Back to the basket and empty it, so the device is left as found.
    tab(dump_xml(), "Basket")
    time.sleep(2.0)
    ok = empty_basket()
    print("basket emptied:", ok)

    try:
        from qa_lib import state
        print("device state after:", state())
    except Exception as e:  # noqa: BLE001
        print("  state() failed:", e)


if __name__ == "__main__":
    main()

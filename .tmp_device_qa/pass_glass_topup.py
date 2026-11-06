#!/usr/bin/env python3
"""glass top-up: the cart/checkout shots pass_glass.py could not take.

The first two runs found Home still loading its sections when the ADD probe ran, so the
basket stayed empty and the empty cart has no 'Slide to Checkout' slider. This script
retries the add until the basket verifiably has lines, then shoots cart + checkout in
both themes and empties the basket again. Same restore rules: ends dark, basket empty.
"""
import os
import sys
import time

sys.path.insert(0, "/home/linuai/Balaji/.tmp_device_qa")
import qa_lib as Q
from pass_glass import (EMPTY_BASKET, cap, go_checkout, is_night, night_mode,
                        scroll, set_night, tab, wake)


def basket_has_lines() -> bool:
    tab(Q.dump_xml(), "Basket")
    time.sleep(2.5)
    return EMPTY_BASKET not in Q.dump_xml()


def add_two_lines() -> bool:
    """Two ADD taps from Home, retrying until the basket verifiably has lines."""
    for round_no in range(4):
        tab(Q.dump_xml(), "Home")
        time.sleep(2.5)
        adds = []
        for attempt in range(8):        # Home may still be fetching sections
            x = Q.dump_xml()
            adds = [n for n in Q.find(x, text="ADD")
                    if (b := Q.bounds(n)) and 380 <= b[1] and b[3] <= 2150]
            if adds:
                break
            time.sleep(1.5)
        if not adds:
            scroll(700)
            continue
        for n in adds[:2]:
            Q.tap(n)
            time.sleep(1.8)
        if basket_has_lines():
            print(f"  basket has lines after round {round_no + 1}")
            return True
        print(f"  round {round_no + 1}: ADD tapped but basket still empty; retrying")
    return False


def capture_cart_checkout(tag):
    """Cart and one theme's pair of shots; checkout is a screenshot only."""
    tab(Q.dump_xml(), "Basket")
    time.sleep(2.5)
    cap(f"{tag}-04-cart")
    if go_checkout():
        cap(f"{tag}-05-checkout")
        Q.sh("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(2.0)
    else:
        print(f"  !! {tag}: Slide to Checkout still not found")


def main():
    print("state before:", Q.state())
    wake()
    Q.launch()

    if not add_two_lines():
        print("ABORT: could not get lines into the basket; no shots taken")
        return

    # Device is found dark (restored by pass_glass run 2). Dark pair first.
    print("night at start:", night_mode())
    if not is_night():
        set_night(True)
        time.sleep(2.0)
    capture_cart_checkout("dark")

    if set_night(False):
        time.sleep(2.5)
        capture_cart_checkout("light")
    else:
        print("  !! could not switch to light; light cart/checkout NOT TAKEN")

    print("restore:", set_night(True), night_mode())

    # Empty the basket again so the device is left as found.
    from pass_glass import empty_basket
    tab(Q.dump_xml(), "Basket")
    time.sleep(2.0)
    print("basket emptied:", empty_basket())
    print("state after:", Q.state())


if __name__ == "__main__":
    main()

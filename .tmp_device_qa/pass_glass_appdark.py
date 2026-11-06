#!/usr/bin/env python3
"""glass dark: the app has its own theme switch, so system night mode is not enough.

Theme.kt defaults to isSystemInDarkTheme() but ThemeViewModel overrides it from DataStore
(default false = light), and the stored value wins - `cmd uimode` changes the status bar
and nothing else in this app. The real dark render is reached through Profile -> Dark mode.

This pass: with the basket already holding lines (left by pass_glass_topup), toggle the
in-app theme to dark, shoot all six screens, toggle back to light (the state the device
was found in), then empty the basket through the cart stepper - whose description is
"Decrease quantity of <name>", not the browse stepper's "Remove one from <name>".
Ends: system night as found (dark), app theme light, basket empty, font 1.1.
"""
import sys
import time

sys.path.insert(0, "/home/linuai/Balaji/.tmp_device_qa")
import qa_lib as Q
from pass_glass import (EMPTY_BASKET, capture_theme,
                        night_mode, set_night, tab, wake)
from pass_glass_topup import add_two_lines

AVATAR = "Your account"


def go_profile():
    tab(Q.dump_xml(), "Home")
    time.sleep(1.5)
    # The avatar lives in the header; a previous capture leaves Home scrolled, and a
    # uiautomator dump only contains what is on screen. Scroll back to the top first.
    Q.swipe(540, 700, 540, 1900, 400)
    time.sleep(1.2)
    Q.swipe(540, 700, 540, 1900, 400)
    time.sleep(1.2)
    xml = Q.dump_xml()
    av = [n for n in Q.nodes(xml) if AVATAR in Q.attr(n, "content-desc")]
    if not av:
        print("  ! avatar not found on Home (even at the top)")
        return False
    Q.tap(av[0])
    time.sleep(2.2)
    return True


def app_dark() -> bool:
    """Read the theme from the Profile subtitle the switch carries."""
    xml = Q.dump_xml()
    if "On · dark colours" in xml:
        return True
    if "Off · light colours" in xml:
        return False
    # subtitle may be below the fold
    Q.swipe(540, 1700, 540, 900, 350)
    time.sleep(1.2)
    xml = Q.dump_xml()
    if "On · dark colours" in xml:
        return True
    if "Off · light colours" in xml:
        return False
    raise RuntimeError("cannot read the Dark mode state on Profile")


def set_app_dark(want: bool) -> bool:
    if not go_profile():
        return False
    now = app_dark()
    if now == want:
        Q.sh("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(1.5)
        print(f"  app theme already {'dark' if want else 'light'}")
        return True
    rows = [n for n in Q.nodes(Q.dump_xml()) if Q.attr(n, "text") == "Dark mode"]
    if not rows:
        print("  ! 'Dark mode' row not found")
        Q.sh("shell", "input", "keyevent", "KEYCODE_BACK")
        return False
    Q.tap(rows[0])
    time.sleep(2.2)
    ok = app_dark() == want
    Q.sh("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(1.8)
    print(f"  app theme set to {'dark' if want else 'light'}: {ok}")
    return ok


def empty_cart() -> bool:
    """Remove every line via the cart stepper ('Decrease quantity of <name>')."""
    tab(Q.dump_xml(), "Basket")
    time.sleep(2.5)
    for i in range(16):
        xml = Q.dump_xml()
        if EMPTY_BASKET in xml:
            print(f"  basket empty after {i} removals")
            return True
        minus = [n for n in Q.nodes(xml)
                 if Q.attr(n, "content-desc").startswith("Decrease quantity")]
        if not minus:
            Q.swipe(540, 1700, 540, 1100, 350)
            time.sleep(1.0)
            xml = Q.dump_xml()
            minus = [n for n in Q.nodes(xml)
                     if Q.attr(n, "content-desc").startswith("Decrease quantity")]
        if not minus:
            print(f"  ! no cart stepper found at iteration {i}")
            return False
        Q.tap(minus[0])
        time.sleep(1.4)
    print("  ! removal loop hit its cap")
    return False


def main():
    print("state before:", Q.state())
    wake()
    Q.launch()

    # The basket should still hold the two lines pass_glass_topup added.
    tab(Q.dump_xml(), "Basket")
    time.sleep(2.0)
    if EMPTY_BASKET in Q.dump_xml():
        print("  basket empty again -> re-adding")
        if not add_two_lines():
            print("ABORT: no lines to photograph")
            return

    # System night mode stays as found the whole time; only the app theme moves.
    print("system night:", night_mode())

    print("== app dark ==")
    if set_app_dark(True):
        capture_theme("dark", "dark")     # overwrites the light-rendering 'dark' shots
    else:
        print("  !! could not switch the app to dark; dark shots NOT TAKEN")

    print("== app light (restore) ==")
    restored = set_app_dark(False)

    print("== empty basket ==")
    emptied = empty_cart()

    # Final read-back of everything this pass touched.
    print("app light restored:", restored, "| basket emptied:", emptied)
    print("system night after:", night_mode())
    print("state after:", Q.state())


if __name__ == "__main__":
    main()

#!/usr/bin/env bash
# Device QA harness for the Balaji customer app.
# Rules: bounds come from a live uiautomator dump only; focus must belong to the
# target package immediately before every injection; abort if a human takes over.
export PATH="$PATH:/home/linuai/gate_prep/Android/Sdk/platform-tools"
# Target device is overridable: the A34 phone is the reference hardware for this
# change set; the SM-X510 tablet was used for round 1.
D="${QA_DEVICE:-adb-RZCW9087GAJ-Ra6D0G._adb-tls-connect._tcp}"
PKG="com.balaji.provision"
ROOT="/home/linuai/Balaji/.tmp_device_qa"
A="adb -s $D"
mkdir -p "$ROOT/shots" "$ROOT/xml"

focus() { $A shell dumpsys window 2>/dev/null | grep -oE "mCurrentFocus=Window\{[^ ]+ u0 [^/]+" | sed 's|.*u0 ||' | head -1; }

assert_focus() {
  local f; f="$(focus)"
  case "$f" in
    *"$PKG"*) return 0 ;;
    *) echo "  !! FOCUS-LOST -> $f  (aborting injection; a human may have the device)" >&2; return 1 ;;
  esac
}

cap() {
  local n="$1"
  $A exec-out screencap -p > "$ROOT/shots/$n.png" 2>/dev/null
  $A shell uiautomator dump /sdcard/q.xml >/dev/null 2>&1
  $A exec-out cat /sdcard/q.xml > "$ROOT/xml/$n.xml" 2>/dev/null
  local sz=$(stat -c%s "$ROOT/shots/$n.png" 2>/dev/null)
  # a 0-byte capture means the channel died, not that the screen is blank
  if [ "${sz:-0}" -lt 1000 ]; then echo "  !! cap $n: shot=$sz B (CHANNEL SUSPECT, re-check)"; fi
  echo "  cap $n: ${sz}B, focus=$(focus)"
}

labels() {
  python3 -c "
import re,sys
x=open('$ROOT/xml/$1.xml').read()
seen=[]
for s in re.findall(r'<node[^>]*>',x):
    t=re.search(r'text=\"([^\"]*)\"',s); d=re.search(r'content-desc=\"([^\"]*)\"',s)
    v=(t.group(1) if t and t.group(1) else (d.group(1) if d else '')).strip()
    if v and v not in seen: seen.append(v)
print('  nodes=%d' % x.count('<node'))
for v in seen[:45]: print('   ', repr(v[:70]))
"
}

tap() {
  local n="$1" re="$2"
  assert_focus || return 1
  $A shell uiautomator dump /sdcard/q.xml >/dev/null 2>&1
  local out
  out=$($A exec-out cat /sdcard/q.xml 2>/dev/null | python3 -c "
import sys,re
x=sys.stdin.read()
pat=re.compile(r'''$re''', re.I)
for m in re.finditer(r'<node[^>]*>', x):
    s=m.group(0)
    for a in ('text','content-desc'):
        mm=re.search(a+r'=\"([^\"]*)\"', s)
        if mm and pat.search(mm.group(1)):
            bb=re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', s)
            if bb:
                x1,y1,x2,y2=map(int,bb.groups())
                if x2-x1>4 and y2-y1>4:
                    print((x1+x2)//2); print((y1+y2)//2); print(a+'='+mm.group(1)[:50]); sys.exit(0)
print('NOMATCH'); sys.exit(1)")
  if [ "$out" = "NOMATCH" ] || [ -z "$out" ]; then
    echo "  TAP $n: no live node matched /$re/ — NOT proof of absence, re-dump to confirm"
    return 2
  fi
  local X Y L
  X=$(printf '%s\n' "$out" | sed -n 1p); Y=$(printf '%s\n' "$out" | sed -n 2p); L=$(printf '%s\n' "$out" | sed -n 3p)
  echo "  TAP $n -> ($X,$Y) $L"
  $A shell input tap "$X" "$Y"
}

type_() { assert_focus || return 1; $A shell input text "$1"; }
key()   { assert_focus || return 1; $A shell input keyevent "$1"; }
swipe() { assert_focus || return 1; $A shell input swipe "$1" "$2" "$3" "$4" "${5:-500}"; }
launch(){ $A shell monkey -p $PKG 1 >/dev/null 2>&1; sleep 6; echo "  launched, focus=$(focus)"; }

"$@"

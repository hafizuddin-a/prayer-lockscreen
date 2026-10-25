#!/usr/bin/env bash
# Dev tool: tap the on-screen element whose text or content description contains the given string.
#   tools/uitap.sh "Choose a photo"          tap it (first match)
#   tools/uitap.sh --list                    print every labelled element with its bounds
#   tools/uitap.sh --scroll "Background"     swipe up until the text is on screen (up to 8 times), then tap it
set -u
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
SERIAL="${SERIAL:-emulator-5554}"
adb() { "$ADB" -s "$SERIAL" "$@"; }

dump() { adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml; }

find_center() { # $1 = needle ; reads the UI dump on stdin ; prints "x y" of the first match, or nothing
  python3 -c '
import re, sys
needle = sys.argv[1]
xml = sys.stdin.read()
nodes = []
for m in re.finditer(r"<node ([^>]*)>", xml):
    a = dict(re.findall(r"(\S+?)=\"([^\"]*)\"", m.group(1)))
    nodes.append((a.get("text", "").replace("&amp;", "&"), a.get("content-desc", "").replace("&amp;", "&"), a.get("bounds", "")))
# An exact match beats a node that merely contains the words (a heading versus its button).
for exact in (True, False):
    for text, desc, bounds in nodes:
        hit = needle in (text, desc) if exact else (needle in text or needle in desc)
        if hit and bounds:
            x0, y0, x1, y1 = map(int, re.findall(r"\d+", bounds))
            print((x0 + x1) // 2, (y0 + y1) // 2)
            sys.exit(0)
' "$1"
}

if [ "${1:-}" = "--list" ]; then
  dump | python3 -c '
import re, sys
for m in re.finditer(r"<node[^>]*?text=\"([^\"]*)\"[^>]*?content-desc=\"([^\"]*)\"[^>]*?bounds=\"(\[[^\"]*)\"", sys.stdin.read()):
    t, d, b = m.groups()
    if t or d: print(repr(t.replace("&amp;", "&")), repr(d), b)
'
  exit 0
fi

scroll=0
if [ "${1:-}" = "--scroll" ]; then scroll=1; shift; fi
needle="$1"
for attempt in $(seq 12); do
  xy=$(dump | find_center "$needle")
  if [ -n "$xy" ]; then adb shell input tap $xy; echo "tapped '$needle' at $xy"; exit 0; fi
  [ "$scroll" = 1 ] || break
  # Sweep down the page for the first four tries, then back up, so the order of calls does not matter.
  if [ "$attempt" -le 4 ]; then adb shell input swipe 30 1900 30 900 300; else adb shell input swipe 30 900 30 1900 300; fi
  sleep 0.6
done
echo "not found: $needle" >&2
exit 1

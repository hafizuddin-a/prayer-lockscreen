#!/usr/bin/env bash
# Dev tool (debug build): draws a widget's real RemoteViews at a given size into a PNG.
#   tools/widgetshot.sh out.png next 146 72                       2x1 at font scale 1.0
#   tools/widgetshot.sh out.png next 146 72 --ef fontscale 1.3 --es lang ar --ez h24 true --es bgcolor 0f2a53
# Extra arguments go to DebugClockActivity (for example --es at 2026-10-05T04:00:00 to set the clock first).
set -eu
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
SERIAL="${SERIAL:-emulator-5554}"
out="$1"; kind="$2"; w="$3"; h="$4"; shift 4
name="shot_$$"
"$ADB" -s "$SERIAL" shell am start --activity-clear-task -n com.prayerwallpaper.app/.DebugClockActivity \
  --es widgetshot "$kind" --ei w "$w" --ei h "$h" --es out "$name" --ez finish true "$@" > /dev/null
for _ in $(seq 1 40); do
  "$ADB" -s "$SERIAL" shell run-as com.prayerwallpaper.app ls "files/widgetshots/$name.png" > /dev/null 2>&1 && break
  sleep 0.25
done
"$ADB" -s "$SERIAL" exec-out run-as com.prayerwallpaper.app cat "files/widgetshots/$name.png" > "$out"
"$ADB" -s "$SERIAL" shell run-as com.prayerwallpaper.app rm "files/widgetshots/$name.png"
echo "$out"

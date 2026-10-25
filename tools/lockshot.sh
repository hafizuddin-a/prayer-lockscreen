#!/usr/bin/env bash
# Dev tool, debug builds only. Optionally moves the fake clock while the screen is off, wakes the
# device, waits for the wallpaper's real "visible" redraw, then saves a lock screen screenshot.
#
#   tools/lockshot.sh out.png                                   just wake and shoot
#   tools/lockshot.sh out.png --es at 2026-10-05T04:00:00       set the fake time first
#   tools/lockshot.sh out.png --ez next true                    step to the next change first
#
# Extra arguments go to DebugClockActivity (see its header). ADB and SERIAL can be overridden.
set -u
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
SERIAL="${SERIAL:-emulator-5554}"
adb() { "$ADB" -s "$SERIAL" "$@"; }
screen() { adb shell dumpsys window | grep -o 'screenState=[A-Z_]*' | head -1; }

out="$1"; shift

if [ "$(screen)" = "screenState=SCREEN_STATE_ON" ]; then adb shell input keyevent 26; fi
for _ in $(seq 20); do [ "$(screen)" = "screenState=SCREEN_STATE_OFF" ] && break; sleep 0.5; done

if [ $# -gt 0 ]; then
  # A fresh task each time: otherwise the intent is handed to whatever activity is on top and never runs.
  adb shell am start --activity-clear-task -n com.prayerwallpaper.app/.DebugClockActivity "$@" --ez finish true > /dev/null
  sleep 1
fi

adb logcat -c
adb shell input keyevent 224
for _ in $(seq 30); do adb logcat -d -s PrayerWallpaper:D | grep -q "redraw(visible)" && break; sleep 0.5; done
sleep 1.5
adb exec-out screencap -p > "$out"
adb logcat -d -s PrayerWallpaper:D | grep "redraw(visible)" | tail -1 | sed 's/.*redraw/redraw/'

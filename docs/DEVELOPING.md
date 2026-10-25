# Developing

Building, debugging and yearly upkeep. How the code is organised is in [HOW_IT_WORKS.md](HOW_IT_WORKS.md), and publishing is in
[play/RELEASE_STEPS.md](play/RELEASE_STEPS.md).

## Build

You need Android Studio (its bundled JDK is used) and the Android SDK. There is no system Java on this Mac.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:testDebugUnitTest                    # the unit tests
./gradlew :app:assembleDebug                        # debug APK, includes the fake clock screen
./gradlew :app:lintRelease                          # 0 errors; two version notes (target SDK, Gradle) are expected
./gradlew :app:assembleRelease :app:bundleRelease   # minified APK and AAB
```

`minSdk` is 34 and `targetSdk` is 36, which meets Google Play's rule for new apps and updates from 31 August 2026. `compileSdk` is 37 only
because that is the platform installed on this Mac; it does not change how the app runs. Re-check the target in mid 2027, when the next
level is expected to become the requirement (https://support.google.com/googleplay/android-developer/answer/11926878).

Bump `versionCode` and `versionName` in `app/build.gradle.kts` for every release.

**Without the exact alarm permission.** `-PexactAlarms=false` builds without `SCHEDULE_EXACT_ALARM`: a build step removes it from the merged
manifest (for the APK and the bundle), the setup's Exact alarms page is left out, and everything else is the same, with reminders using
approximate alarms. It exists in case Google Play does not accept the declaration for that permission:
`./gradlew :app:bundleRelease -PexactAlarms=false`.

### A sideload APK

```bash
tools/build-sideload.sh      # runs the tests and lint, builds the release APK, signs it with the Android debug key
~/Library/Android/sdk/platform-tools/adb install -r build-out/prayer-wallpaper-1.8.0-sideload.apk
```

The APK is signed with the debug key, so a later install from Google Play needs it removed first (the signing keys differ). `install -r` keeps the
settings and the wallpaper. Use `adb -s <serial>` when an emulator and a phone are both connected.

For Google Play, sign with your own upload key instead: `tools/build-play-bundle.sh` (see `play/RELEASE_STEPS.md`).

## Debug tools

A debug build adds a **Fake clock** app icon. It sets a time that keeps running (read in the selected place's zone), so you can park a prayer
boundary a minute away. None of it exists in the release build (checked in the built APK). Extras that change settings are saved, so put them
back afterwards.

```bash
tools/lockshot.sh out.png --es at 2026-10-05T04:00:00      # set the fake time, wake the phone, screenshot
tools/lockshot.sh out.png --ez next true                   # step to the next prayer boundary
tools/lockshot.sh out.png --es place London,GB,51.5085,-0.1257,Europe/London --es at 2026-10-05T13:00:00
                                                           # pick a place first (commas, adb eats pipes), then the time
tools/lockshot.sh out.png --ez showhijri false             # hide the Hijri line for this shot
tools/lockshot.sh out.png --ez countdown false             # hide the countdown line for this shot
tools/lockshot.sh out.png --es hijrialign LEFT --ez hijrievents false                 # left aligned, no event name
tools/lockshot.sh out.png --es hijrievent "'a very long event name'"   # force an event name; note the inner quotes (the device shell splits on spaces); "none" clears it
tools/lockshot.sh out.png --es method UMM_AL_QURA --es asr HANAFI --es names ENGLISH --es adjust 0,0,0,0,5,0
tools/lockshot.sh out.png --es mode PHOTO --ef dim 0.3 --ef listTop 0.5
tools/lockshot.sh out.png --es mode SOLID --ei solid -16316665      # colours are signed ARGB ints
tools/lockshot.sh out.png --es mode PHOTO --ef zoom 2 --ef panX 0 --ef panY 0.3   # photo framing
tools/lockshot.sh out.png --es photo /data/data/com.prayerwallpaper.app/cache/x.jpg   # photo background from a file (adb push, then run-as cp)
tools/lockshot.sh out.png --es textsize LARGE --ez highcontrast true --ez bold true --ez followsystem false

# Widgets: draw a widget's real RemoteViews at any size, font scale, language and clock style into a PNG
tools/widgetshot.sh out.png next 146 72 --ef fontscale 1.3 --es lang ar --ez h24 true --es bgcolor 22304d      # kinds: next, today, strip
#   settings: --es widgetstyle GLASS|CARD --es widgettint LIGHT|DARK --ef widgetdarkopacity 0.55 --es widgetcard AUTO|DARK|LIGHT --ef widgetopacity 0.12 --ez widgetfollow true
#   --ez widgetupdate true (what a boundary alarm does, now); --ez widgetfast true [--ei widgetstep 2] (today's six times two minutes apart; false puts the real ones back)
#   --es at 2026-10-08T09:00:00 sets the fake time first (to see the Syuruk mark: 06:50:20 shows it, 06:51:10 does not)
adb logcat -d -s WidgetUpdater WidgetReceiver        # every redraw, with its reason, and every alarm when it arrived
adb exec-out run-as com.prayerwallpaper.app cat files/widget_history.json      # the lateness record (debug builds)

# Reminders (the alarms use the real clock). Read the result with: adb logcat -d -s Reminders
adb shell am start --activity-clear-task -n com.prayerwallpaper.app/.DebugClockActivity --ez reminders true --ez reminderfast true --ez finish true
#   --ez reminderfast true: today's six prayer times replaced by ones two minutes apart, the first in about three minutes (false: back to real)
#   --es remindermethod EXACT|INEXACT|ALARM_CLOCK|none, --ez remindersilent true, --es reminderprayers SUBUH,ZOHOR, --ei reminderpre 10
#   --ez remindertest true (the health screen's test), --ez reminderreset true, --ez reminderensure true, --ez reminderlog true (print the history)
adb shell cmd appops set --uid com.prayerwallpaper.app SCHEDULE_EXACT_ALARM deny|allow     # --uid once it has been given in Settings
adb shell pm revoke com.prayerwallpaper.app android.permission.POST_NOTIFICATIONS            # to see the notification prompt again
adb shell dumpsys alarm | grep -A3 "Alarm{.*com.prayerwallpaper.app}"                       # what Android holds
adb shell dumpsys deviceidle force-idle                                                        # deep Doze; unforce to end it

# Other
adb shell am broadcast -a com.prayerwallpaper.app.DUMP_A11Y -p com.prayerwallpaper.app ; adb logcat -d -s A11yDump
                                                           # a model of what a screen reader reads (uiautomator cannot dump while a countdown ticks)
tools/uitap.sh "Choose a photo"                            # tap a UI element by its text (also --list, --scroll); hangs on screens that keep changing: tap by coordinates there
swift tools/pixeldiff.swift a.png b.png 0 1600 1080 400    # count differing pixels in a region (refactor checks)
swift tools/compose.swift out.png 560 a.png 0 0 1080 2424 "label" b.png ...   # labelled comparison grid
swift tools/make-test-photos.swift /tmp                    # a dark and a bright test photo
```

**Location on the emulator.** `adb emu geo fix` only feeds GPS, and the app has the coarse permission, which uses the network or fused
provider. To give it a position, allow mock locations for the shell and override the fused provider:
`adb shell appops set com.android.shell android:mock_location allow`, then `adb shell cmd location providers add-test-provider fused`,
`... set-test-provider-enabled fused true`, `... set-test-provider-location fused --location 41.0082,28.9784`.

**Emulator notes.**
- Start it with `-dns-server 8.8.8.8,1.1.1.1`, or its DNS stops working.
- The `Pixel_9` AVD only keeps the screen on when it counts as plugged in: `adb shell dumpsys battery set ac 1` and
  `adb shell svc power stayon true` (undo with `dumpsys battery reset`).
- `cmd alarm set-time <ms>` and `cmd alarm set-timezone Asia/Singapore` set the system clock (the lock screen's own clock follows it).
- After a cold boot run `adb shell wm dismiss-keyguard` once, or the lock screen says "Unlock for all features and data".
- `adb shell am force-stop` on this app resets the live wallpaper, as Android does for any live wallpaper; `install -r` does not.
- To scroll the app's screens by swipe, swipe from the right margin (x about 1066 of 1080). A swipe over the position preview drags the list.

### The Play posters

`swift tools/make-posters.swift docs/play/screenshots-raw docs/play/graphics` rebuilds the six posters, the feature graphic and the icon from the
raw emulator screenshots in `docs/play/screenshots-raw/`. To retake the lock screen shots: set the emulator clock to 16:21 on 8 October 2026
Singapore time, then
`tools/lockshot.sh out.png --es mode GRADIENT --ei gradientTop -12961222 --ei gradientBottom -16777216 --ez followsystem false --es textsize STANDARD --ez highcontrast false --ez bold false --ef listTop 0.25`
(`listTop` 0.25 and 0.62 for `lock_high` and `lock_low`; below about 0.22 the Hijri line meets the clock). The app screens are ordinary
emulator screenshots with the status and navigation bars cropped by the script.

## Yearly upkeep

### The Hijri month table (before 27 January 2028)

The Hijri date line comes from `data/hijri_months.csv`, the month start dates of Singapore's own MUIS calendar. MUIS decides each month's
start by imkan ar-rukyah, so the app never calculates or estimates a Hijri date: where the table does not give a certain answer it shows no
line. MUIS has said the dates may be used in the app (confirmed by the developer on 13 October 2026); the About screen credits the Muis
Calendar and keeps the "not affiliated with or endorsed by MUIS" line.

The table runs from 1 Syaaban 1447 (20 Jan 2026) to 1 Syaaban 1449 (30 Dec 2027) and gives certain dates through **27 January 2028** (day 29
of the last month). The settings screen shows "Hijri dates available until ..." and warns when fewer than 60 days remain (from 29 November 2027).
After the last certain date the line is hidden, with no placeholder. In Maghrib mode the last certain day ends at its Maghrib, when the date
would be day 30 of the last month and so is unknown; the settings screen still says "available until 27 Jan 2028". The app fetches nothing for
this, so nothing else can tell a user the table is running low: ship an update before then.

1. **Get the PDF.** Download "Key Islamic dates <year>" from https://www.muis.gov.sg/resources/islamic-calendar/. Do not commit it: it is
   MUIS's document, not part of this project.
2. **Read it as text.** This Mac has no poppler, but macOS has PDFKit:
   ```bash
   swift tools/pdftext.swift "Key Islamic dates 2028.pdf" > /tmp/2028.txt
   swift tools/pdftext.swift "Key Islamic dates 2028.pdf" /tmp/2028-pages   # also renders the pages to PNG, to look at
   ```
   The PDF has two tables: month starts ("1 Syaaban 1447H ... 20 January 2026 (Tuesday)") and Islamic events.
3. **Add rows to the end of `data/hijri_months.csv`.** One row per Hijri month start that is not already there, in date order, columns
   `hijri_year,month_number,month_name,gregorian_start,weekday`. The weekday is the one the PDF prints. Month names are the spellings in
   `tools/make-hijri.py` (`Ramadhan`, with an h). Type what the PDF says; never work a date out from an event or from the previous month, and
   if something is unclear, ask.
4. **Check against the PDF mechanically**, using every PDF text file you have so far:
   ```bash
   python3 tools/check-hijri-pdf.py /tmp/2026.txt /tmp/2027.txt /tmp/2028.txt
   ```
   Every month start must be in the CSV with the same date and weekday, and every event must land where the CSV's month start puts it. Read
   each warning: MUIS's PDFs have typos, and a Hijri year or a spelling that differs from the CSV is a warning, not an error. Known ones: the
   2026 PDF prints `1 Muharram 1447H` on 17 June 2026 but means 1448H (its own events table says 1448H, and the next row is `1 Safar 1448H`),
   it spells `Ramadhan` where the 2027 PDF says `Ramadan`, and the 2027 PDF has `31October` with no space.
5. **Generate the asset:** `python3 tools/make-hijri.py`. It refuses to write if the table is out of order, has a gap that is not 29 or 30
   days, a weekday that does not match its date, a month that does not follow the previous one, or a name that does not match its month
   number. It writes `app/src/main/assets/hijri_months.json`.
6. **Check the events too.** The same checker compares every event's name and Hijri day and month with `data/islamic_events.csv` (nine rows:
   month, day, name, in month and day order). If MUIS adds or renames an event it reports an error: edit the CSV to match the PDF (names as
   MUIS prints them, without a year tag such as "(1448H)") and run `python3 tools/make-hijri.py` again.
7. **Update the tests that name the data**, in `app/src/test/java/com/prayerwallpaper/app/data/HijriDataTest.kt`: add the new year's events
   to the events list, add any events newly inside the table's range to the expected list in the whole-table test, and change the coverage
   test (the last month start and the last certain date). The structural data tests need no change.
8. **Run the tests:** `./gradlew :app:testDebugUnitTest`. A table that is wrong in a way the checks can see fails the build.
9. **Ship it:** bump the version, build the release (see above), and look at the settings screen: it should say "Hijri dates available until"
   the new date.

### The Singapore timetable (late November)

MUIS publishes the next year's timetable in late November. The app looks for it from 1 December and falls back to daily retries through
January. On 1 December check that the new year's dataset was found: the **Prayer times data** card under *Location and names*. If
data.gov.sg changes its API or rate limits, the app shows the cached times and keeps working until the cache runs out at the end of the year.

### The city list

The bundled list is GeoNames `cities15000` as of 4 October 2026. To refresh it, download
https://download.geonames.org/export/dump/cities15000.zip and run `python3 tools/make-cities.py cities15000.txt`, then run the tests. Not
urgent: cities rarely change. It is plain text on purpose: the Android build decompresses and renames `.gz` assets.

### Dependencies

Adhan 1.2.1 (March 2021) is the latest release of the calculation library. The astronomy does not go out of date, but watch
https://github.com/batoulapps/adhan-java for fixes. After every dependency or SDK bump run the tests and `lintRelease`.

# How it works

Developer notes for Prayer Live Wallpaper: where each part of the code lives and why some things are done the way they are.

| Part | Where |
|---|---|
| Fetch, parse, cache, refresh policy | `data/` (`DatasetParser`, `PrayerApi`, `PrayerStore`, `PrayerRepository`) |
| Which prayer is current, when it next changes | `logic/PrayerSchedule.kt`, with an injected `Clock` |
| Where the list's times come from (MUIS or calculated), and in which zone | `data/Timetable.kt` (`MuisTimetable`, `CalculatedTimetable`) |
| Calculating times, method defaults by country | `logic/PrayerCalculator.kt`, `logic/CalcSettings.kt`, `logic/MethodDefaults.kt` |
| The city list, search, nearest city, one-shot location | `data/CityIndex.kt`, `data/Place.kt`, `data/DeviceLocation.kt`, asset `cities.tsv` |
| The Hijri date line: month table, logic, expiry message | `data/hijri_months.csv`, `tools/make-hijri.py`, `logic/HijriCalendar.kt`, `logic/HijriStatus.kt`, `data/HijriAsset.kt`; yearly update in `docs/DEVELOPING.md` |
| The countdown line: wording, rounding, the once-a-minute tick | `logic/Countdown.kt`, `logic/PrayerSchedule.kt` (`nextMinuteStart`), `wallpaper/WallpaperRenderer.kt` (`drawCountdown`), `wallpaper/PrayerWallpaperService.kt` |
| Widgets: what they show now, the boundaries, the countdown base | `logic/WidgetState.kt` (pure, tested with an injected clock) |
| Widgets: time text, Hijri form, screen reader words, windows of entries | `logic/WidgetText.kt` |
| Widgets: what fits (sizes, what is dropped), glass and card colours, history and its share text | `logic/WidgetLayout.kt`, `logic/WidgetTheme.kt`, `logic/WidgetHistory.kt` |
| Widgets: the three RemoteViews, the refresh engine, the alarms, the receivers | `widgets/` (`NextPrayerView`, `TodayTimesView`, `PrayerStripView`, `WidgetUpdater`, `WidgetScheduler`, `WidgetReceiver`, `WidgetProviders`), `res/layout/widget_*.xml`, `res/xml/widget_*_info.xml` |
| Widgets: settings, live preview, health screen | `ui/WidgetsUi.kt`, `ui/WidgetsScreen.kt`, `ui/WidgetHealthScreen.kt` |
| The settings app's look and shared parts: the black and white palette (contrast tested), type scale, line icons, screen frame, rows, status banner | `ui/theme/Brand.kt`, `ui/components/Components.kt`, `res/drawable/ic_*.xml`, `ui/Common.kt` |
| Where each screen is, Back, the first-run setup's pages and who sees it, what each home row says | `logic/Navigation.kt`, `logic/Setup.kt`, `logic/HomeSummary.kt` (all pure and tested), `ui/AppRoot.kt` |
| The screens: home, Look, Location and names, Easier reading, Reminders, Widgets, the first-run setup | `ui/HomeScreen.kt`, `ui/LookScreen.kt`, `ui/LocationScreen.kt`, `ui/AccessibilityScreen.kt`, `ui/RemindersScreen.kt`, `ui/WidgetsScreen.kt`, `ui/setup/` (`SetupFlow.kt`, `SetupSteps.kt`), `ui/SettingsState.kt` |
| Reminders: which alarm is next, the history log, the stale rule | `logic/ReminderPlan.kt`, `logic/ReminderHistory.kt`, `logic/ReminderContent.kt` (pure, tested with an injected clock) |
| Reminders: the scheduler, the alarm and notification services, the receivers | `reminders/` (`Reminders`, `AlarmGateway`, `ReminderNotifier`, `ReminderReceiver`, `ReminderTriggerReceiver`), `work/ReminderCheckWorker.kt` |
| Reminder health: the rules, the screen, the share text | `logic/ReminderHealth.kt`, `logic/ReminderShare.kt`, `reminders/AndroidHealth.kt`, `ui/ReminderHealthScreen.kt`, `ui/RemindersUi.kt` |
| Daily refresh job (Singapore only) | `work/RefreshWorker.kt` |
| Updating itself from GitHub: which release is newer, which file, the checks, the install (not in a Play build) | `logic/AppUpdate.kt` (pure, tested), `update/` (`UpdateManager`, `UpdateStore`, `UpdateState`, `UpdateNotifier`, `UpdateInstallReceiver`), `work/UpdateWorker.kt`, `ui/UpdateUi.kt` (the home banner and the About section) |
| The wallpaper: drawing and redraw triggers | `wallpaper/PrayerWallpaperService.kt`, `wallpaper/WallpaperRenderer.kt` |
| Settings, photo copy, colour capture | `settings/` |
| The app UI (Compose) | `ui/` |
| The hosted pages (GitHub Pages): the privacy policy and the download page | `site/` (`index.html`, `download/index.html`, `assets/`), published by `.github/workflows/pages.yml`; the policy's URL is `Legal.PRIVACY_POLICY_URL` |

Things worth knowing:

- **The first-run setup** (`ui/setup/`) is six numbered pages and a closing one: Welcome, put it on the lock screen, keep the list visible,
  reminders, exact alarms, widgets, done. `SetupStep` (`logic/Setup.kt`) knows the order, and every question about it takes `exactAlarms`
  (`BuildConfig.EXACT_ALARMS`), because a build without that permission has no Exact alarms page and counts out of five. It opens only on a
  fresh install (`shouldShowSetup`, decided once at start, because answering page 1 changes what it reads); **Run setup again** is on the home
  screen. The apply page judges the result only once the user is back from Android's screen. The Reminders page runs the same permission flow
  as the Reminders screen (`ReminderFlow`) but leaves the exact alarm pop-up to the next page (`offerExact = false`).
- **The updater** (only in the build downloaded from GitHub) is `update/UpdateManager`, with its decisions in `logic/AppUpdate.kt`. It asks
  `releases/latest` of this repository (GitHub leaves out drafts and pre-releases, and the parser skips them too), reads the tag as the version and
  takes the asset named `prayer-wallpaper.apk` (or the only `.apk`). A download URL is trusted only if it starts with this repository's
  `releases/download/` (a debug build also accepts `http://10.0.2.2:`, so an update can be tried against a local file). The file goes to
  `cacheDir/updates` (emptied at the next start), where `problemWith` checks the size, the package name, a higher `versionCode` and that its signer
  SHA-256 is the running copy's. Android enforces all of that itself; the checks only let the app say what is wrong in plain words. The install is a
  `PackageInstaller` session with `USER_ACTION_NOT_REQUIRED`: Android skips its own prompt only for the app that installed the running copy, so an
  update of a copy installed from a downloaded file asks once, and later ones can go in quietly. It needs `REQUEST_INSTALL_PACKAGES` and the
  person's own **Allow from this source** (`SystemScreens.unknownSources`). When Android wants a tap it hands back an intent: the app starts it if
  its screen is in front, otherwise posts a notification (only if notifications are already allowed), and the home banner covers the rest. The
  app asks when it opens (`onAppResumed`, at most once a month) and through `UpdateWorker` once a month, both only while **Keep the app up to date**
  is on; **Check for updates** always asks. `-PselfUpdate=false` removes all of it, and the permission, which Google Play needs. Play Protect blocked
  a debug build as "Harmful app" but showed only its usual "App scan recommended", then "This app looks safe", for the signed release build (Play
  emulator, 11 November 2026), so test the real APK, not a debug one.
- **Friday.** `PrayerDay.label(prayer, names)` gives the Friday prayer's name on a Friday (Jumaat or Jumuah in place of
  Zohor or Dhuhr), so it follows the day whose times are on the list, which is the calendar day's: from midnight on Friday it says Jumaat. It
  applies to every place. Only the name changes.
- **The countdown** (`logic/Countdown.kt`) is `PrayerSnapshot.countdown(now, names)`: the next prayer's name as its own day's
  list writes it, and the time left rounded up to whole minutes, so the last minute reads `1m` and never `0m`. Hours and
  minutes (`2h 14m`), whole hours (`2h`), or minutes alone (`14m`). After Isyak it counts to tomorrow's Subuh (`PrayerSnapshot.next`); with no
  timetable for today, or at the very end of the cached data, there is no line. The words "in", "h" and "m" are English
  whichever prayer names you chose, as the Islamic event names are mixed too. **It costs one redraw a minute, and only
  while it is on the screen.** Prayer times fall on whole minutes, so the service arms its one existing timer for the
  earliest of the next prayer, midnight (Hijri date, midnight mode) and the next whole minute (`nextMinuteStart`), and
  only when a countdown was actually drawn: not when it is switched off, not when the shared home screen is hiding the
  list, and nothing at all when the screen is off, because `onVisibilityChanged(false)` cancels the timer. No alarm, no
  wake lock, no loop. It is drawn as a row of its own half a row under the list, at 85% of the list's text size
  (`ListLayout.COUNTDOWN_*`), so the list and the Hijri line do not move; the preview's drag outline takes it in. It
  shrinks, then squeezes, on a wide font, and a list parked so low that the line would run off a nearly square screen is
  lifted just enough to keep it on screen. The Centred or Left choice is the Hijri date's setting (`hijriAlign`, now
  under "Alignment") and applies to both lines.
- **Widgets** (`widgets/WidgetUpdater.kt`) are RemoteViews built from the cached timetable, the settings and the colours Android reports for the
  wallpaper, and nothing else: no network, no permission, no foreground service. `WidgetState` (pure) works out the current entry (before Subuh it
  is Isyak of last night), the next one (after Isyak, tomorrow's Subuh) and the countdown target. The countdown is a `Chronometer` in the launcher
  (counting down to `elapsedRealtime() + remaining`), so it ticks with the app killed; past zero it reads `in -00:11`, which is why the picture is
  redrawn at every boundary. Layouts are worked out for the size the launcher reports (one per size it may use, with `RemoteViews(Map<SizeF, ...>)`)
  and by what fits: nothing is below 11 sp, an element that does not fit goes (the Hijri line first, then the countdown; the footer, then the Hijri
  line; the icon before a row), and six columns that do not fit become three. A name too wide ends in an ellipsis.
- **Refreshing the widgets.** `updateAll` redraws them, records how late that was for the latest boundary (a prayer time or midnight in the place's
  zone), and sets two alarms for the next one: a window alarm, `setWindow(boundary, 10 minutes)` (Android may deliver it from the boundary to ten
  minutes later, never before), and a safety alarm two minutes after, `setAndAllowWhileIdle`. The spec names `setAndAllowWhileIdle` for the first
  as well; on the emulator that call was given a window of 75% of the time left (45 minutes at one hour ahead), so `setWindow` is used, whose 10
  minutes is the shortest Android grants an app without the exact alarm permission. `updateAll` is also called by: the widget callbacks (bursts are
  merged), a reboot, an app update, a clock, time zone or language change, a settings change (not the lock screen's sliders), the settings app
  opening, the daily data job, a reminder alarm, the lock screen wallpaper becoming visible (`refreshIfStale`: one comparison in memory, a redraw
  only if a boundary has passed since the last one or the font scale, language, clock style or density changed), and `updatePeriodMillis` (30
  minutes). Bigger font scale: the widgets are laid out for the scale they were drawn at, so a change is noticed by that check and by the app's own
  configuration callback.
- **The widget history** (`files/widget_history.json`, 14 days) records the first redraw after each boundary whatever caused it. Updates after a reboot,
  an app update or a clock change are listed but not counted in the median and worst. Share as text works as for reminders.
- **Reminders** (`reminders/Reminders.kt`) set only the next alarm, as a chain: each alarm that arrives records its lateness, shows the
  notification, sets the one after it, and asks the wallpaper to redraw. Anything that can break the chain calls `ensureScheduled`: a
  reboot, an app update, a clock or time zone change, the exact alarm permission changing (all by manifest-declared broadcast), the
  settings changing, the app opening, the wallpaper becoming visible (at most every 30 minutes), a daily check job (`ReminderCheckWorker`,
  no network needed) and the daily data refresh. Times are the chosen place's, in its own zone. `FLAG_NO_CREATE` only proves a pending
  intent exists, not that Android still holds the alarm (taking the exact permission away clears alarms and leaves the intents), so these
  triggers set the alarm again rather than trust it. Each alarm has a pending intent of its own, so setting the next never cancels one that is
  overdue. Alarms use the real clock, never the debug fake one.
- **Exact or approximate.** Android does not give a new app the exact alarm permission, so until the person allows it (the turn-on flow
  takes them to the screen) reminders use `setAndAllowWhileIdle`, which Android may deliver late: measured, a window of 75% of the time to
  go capped at **one hour**. So the stale limit is 30 minutes for an exact alarm and 90 for an approximate one, and the app says "up to an
  hour late" wherever it asks. A notification that would arrive after its limit is dropped and logged as "too late"; one that never arrives is
  logged as "missed" once its limit has passed; one that arrives with notifications switched off is "blocked".
- **Two channels**, because a channel's sound cannot be changed in code: `prayer_reminders` (importance high: a banner and the person's own
  notification sound) and `prayer_reminders_silent` (default importance, no sound, vibration on). "Notification sound settings" opens the
  channel's own system screen.
- **`-PexactAlarms=false`** builds without the exact alarm permission (it is removed from the merged manifest by a build step, for the APK and
  the bundle), and everything else is the same, with approximate alarms only. It exists because how Google Play reviews a prayer reminder app
  declaring that permission is unknown: `docs/play/RELEASE_STEPS.md` has the declaration text and this fallback. The setup's Exact alarms page
  is left out of such a build (`SetupStep.pages(exactAlarms)`). `./gradlew :app:bundleRelease -PexactAlarms=false`.
- **Islamic events** are `data/islamic_events.csv`: nine rows of (Hijri month, Hijri day, name) from the Islamic Events tables
  in the two MUIS PDFs. An event falls on the same Hijri day every year, so the app looks the name up by the Hijri date it is
  already showing; the Gregorian date then comes from the month start table, and `tools/check-hijri-pdf.py` checks every
  event's name and date against the PDFs. Names are as MUIS prints them, a mix of Malay and English, whichever prayer names
  you chose. The event follows the Hijri date, so with the Maghrib setting on it appears from Maghrib on the eve.
- **A long event name** sits on the same line as the date, which is why `fitHijriLine` (`logic/HijriLineFit.kt`) makes it fit,
  in order: as it is if it fits; else the whole line shrinks, down to 70% of the list text size; else the event name is
  shortened with an ellipsis (the date is never cut); in the last resort the date alone. The longest real name, Prophet
  Muhammad's Birthday, needs about 90% of the size on an ordinary font and still shows in full on a font 20% wider. The line
  may be 92% of the screen width, a little wider than the list.
- **The Hijri line is data, not calculation.** Singapore decides each Hijri month by imkan ar-rukyah, so a calculated
  calendar can be a day out. `data/hijri_months.csv` holds the first day of each month, copied from MUIS's "Key Islamic
  dates" PDFs; `tools/make-hijri.py` validates it (ascending, consecutive months, gaps of 29 or 30 days, weekdays match)
  and writes the asset the app reads. A month is 29 or 30 days, found from the next month's start, so the last month in
  the table is certain for its first 29 days only (through 27 Jan 2028 today). The date changes at Maghrib by default,
  because an Islamic day starts at sunset: from Maghrib the line shows the next civil day's date, so the evening before
  1 Jamadilawal already reads 1 Jamadilawal, and midnight changes nothing. Maghrib is today's from the MUIS timetable; if
  that is not known (no prayer data for the day) the line is hidden rather than guessed. With the "Change the date at
  Maghrib" setting off, the date changes at 00:00 Singapore time instead. Either way the wallpaper's own timer fires at
  the change while the screen is on (the highlight timer at Maghrib, an extra one at midnight), with no alarms. It is drawn
  for the Singapore place only, as a row of its own half a row above the list, in the list's text size, font and colour, with
  a moon icon in the prayer icons' style (an outline circle with the lit part filled; waxing phases lit on the right, waning
  on the left) chosen by the day of the Hijri month: 1 to 5 waxing crescent, 6 to 9 first quarter, 10 to 12 waxing gibbous,
  13 to 16 full, 17 to 20 waning gibbous, 21 to 23 last quarter, 24 to 27 waning crescent, 28 to 30 dark. It is a picture,
  not astronomy (`logic/MoonPhase.kt`). The list itself does not
  move. The settings screen shows how far the table reaches and warns from 60 days before it runs out.

- **Other places are calculated, not looked up.** `AdhanCalculator` wraps Adhan 1.2.1 (plain Java, no other
  dependencies). Adhan reads its date as a UTC day, so for places far from UTC (Hawaii, New Zealand) an event
  can land on the neighbouring date; the wrapper tries the neighbouring dates and takes each prayer from the one
  that lands on the local date. A day with no sunrise or sunset (Tromsø at midsummer or midwinter), or with a
  prayer after midnight, is treated as having no times, and the wallpaper says so. Fajr and Isha use the
  angle-based fallback where the sun never gets low enough. Tests compare against the sun's real altitude
  (within 2 minutes) and against an independent implementation (within 3), and calculated Singapore lands within
  2 minutes of twelve real MUIS days. The "usual" method per country is in `MethodDefaults`.
- **Times are in the place's zone, not the phone's.** Every reader goes through `Timetable.snapshot(clock)`,
  which reads the clock in the place's zone, so a phone in the wrong zone still shows the right list.
- **Choosing a place** resets the method and minute offsets (they belong to that place) but keeps the Asr rule
  and the prayer-name choice, which are personal. Unset choices follow the country.
- **The city list** is `app/src/main/assets/cities.tsv`, built by `tools/make-cities.py` from GeoNames
  `cities15000` (34,088 cities; Singapore's neighbourhoods are folded into one entry; a few dozen aliases such
  as Mecca for Makkah). It is plain text on purpose: the Android build decompresses and renames `.gz` assets.

- **The engine never touches the network.** It reads the cached JSON and redraws only when the surface
  changes, the screen turns on, the next prayer boundary passes while visible, or the system clock or
  zone changes. Nothing is scheduled while the screen is off.
- **Data quirks.** The 2024 and 2025 datasets and the consolidated one use 12-hour times with no am/pm;
  only 2026 is 24-hour. The parser reads from Zohor on as pm and rejects any day whose six times are not
  strictly increasing. The API reports a `next` page link even on the last page, and answers HTTP 429
  after a handful of requests in a few seconds, so a normal refresh is a single request.
- **Refresh policy.** A missing year is fetched on every run (the job runs daily); a cached year is
  refreshed weekly. In December the next year is wanted too, which is how the 2027 timetable is found
  once MUIS publishes it (late November).
- **Android darkens the lock screen wallpaper.** SystemUI puts a black scrim at 20% over it, so colours
  look about 0.8 times as bright as authored. Compare against the real lock screen, not the preview.
- **Text colour** switches between light and dark from the measured brightness behind the list, after
  the dim scrim is applied, and takes its tint from the background: blue-white on the navy default, plain
  white on black. The system's own clock colour is Android's decision.
- **Text colour options** (Background, Text colour). *From the background* is the default. *Pick a colour* uses one
  colour you choose, whatever the wallpaper, with White and Black buttons for the common cases (white matches the
  system clock on phones that draw it white, measured at exactly #FFFFFF in a screenshot; black suits a bright
  wallpaper). A first version tried to follow the system automatically and did not match such a phone's clock, which
  is why the choice is the user's. *Android's clock colours* reads the Material You
  palette instead: on the Android 16 emulator the clock is `system_accent1_100` on dark wallpapers and
  `system_accent2_600` on bright ones (measured under navy, black, dark red and white wallpapers), which is how a
  Pixel draws it. An app cannot read the colour SystemUI draws, only the `android.R.color.system_*` palette, which it
  reads on every draw because it follows the wallpaper; it chooses light or dark the way the system does, from
  `WallpaperColors.fromBitmap(...).colorHints` and `HINT_SUPPORTS_DARK_TEXT`, the hint our `onComputeColors` supplies.
  The lock screen's black scrim (20% on the emulator, a little less on some phones) sits over our list but under
  the clock, so a picked or system colour is brightened by 1/0.8 as far as its brightest channel allows (`scrimBoost`):
  dark colours then show as picked, while white and near-white are already at the limit and show slightly dimmer than
  the clock.
- **Times follow the phone's 12/24-hour setting.** 12-hour shows `6:16 am`; 24-hour shows `06:16` with no suffix.
- **Applied to both screens?** Android then runs a single engine for the home and lock screens. The list is
  drawn only while the phone is locked, so it never sits behind your home screen icons. The app also tells
  you when the home screen is using the wallpaper, because that replaced your own home wallpaper.
- **The highlight is the prayer that is on now**, on the lock screen and on the widgets alike. It moves the moment a prayer starts. The list is the calendar day's: after Isyak it
  stays today's with Isyak marked, and from midnight it is the new day's, with Isyak still marked (last night's) until Subuh. The
  countdown line under it still counts to the next prayer. The current row is not bold (`ListLayout.HIGHLIGHT_ROW_WEIGHT` in
  `WallpaperRenderer.kt` changes that).
- **Syuruk is sunrise, not a prayer, so it is marked for one minute only**, on the lock screen and on every widget. From Syuruk until Zohor the list has no marked row (the countdown still says "Zohor in ..."), and Zohor is marked the
  moment it starts. The minute is `SYURUK_PILL_DURATION` in `PrayerSchedule.kt`; the lock screen redraws exactly at the end of it, the
  widgets add it as a boundary (`WidgetState.kt`, `changeTimes`), but a widget redraw is ALWAYS an inexact window alarm
  (`WidgetScheduler`: `setWindow` 10 minutes; the widgets never use exact alarms, whatever is granted), so a widget can show or drop the
  Syuruk mark up to about ten minutes late, or never show it. The exact-alarm permission only affects reminders.
  With "Show Syuruk" off nothing changes: Subuh stays marked until Zohor.
- **Notifications over the list.** On stock Android 16 same-app notifications are bundled into one card, so 3 and 6 notifications look the same.
  At 30% from the top the list's first row slides under the card; at 40% and below it is clear for every count, which is why 40% is the default
  list position. With no notifications the big two-line clock reaches about 62% of the height; the *Dynamic clock* switch under Display, Lock screen
  turns that off.
- **The "keep the list visible" page opens a settings screen, but there is no public intent for "notifications on lock screen".** All five documented
  candidates resolve on the emulator: `DISPLAY_SETTINGS` lands on Display, which has a Lock screen entry holding *Notifications on lock screen*
  (used first); `SECURITY_SETTINGS` and `PRIVACY_SETTINGS` land on unlock setup and the Safety Center; `ALL_APPS_NOTIFICATION_SETTINGS` lands on
  the per-app list; `SETTINGS` opens the home page (used as the fallback). To see which one a phone resolves:
  `adb shell cmd package resolve-activity --brief -a android.settings.DISPLAY_SETTINGS`.
- **Android resets a live wallpaper, and deletes an app's alarms, when the app is force-stopped.** A crash loop resets the wallpaper too. Never
  press Force stop while testing; set the wallpaper again from the app if it happens. `install -r` keeps the wallpaper.

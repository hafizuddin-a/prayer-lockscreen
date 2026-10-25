# Prayer Live Wallpaper

An Android live wallpaper that draws today's prayer times on the lock screen: two columns, six rows, the prayer that is on now marked, and a
countdown to the next one. Singapore uses the official MUIS timetable and Hijri calendar; any other place is calculated on the phone.
Version 1.8.0, for Android 14 and later. No account, no ads, no analytics.

## What it does

- **Lock screen.** The six times (`6:16 am`, or 24-hour), the prayer that is on now marked, and an optional line such as `Asar in 2h 14m`.
  Syuruk is sunrise, not a prayer, so it is marked for one minute only. The list updates itself and is drawn only while the screen is on.
  Friday shows Jumaat (Malay) or Jumuah (English) in place of Zohor or Dhuhr.
- **Places.** Singapore uses the official timetable (downloaded daily while it is selected). Any of about 34,000 other cities, found by search or by
  "Use my location" (approximate, once), is calculated on the phone with Adhan, using the usual method for the country, which you can change.
  Prayer names can be Malay or English and Arabic.
- **Hijri date** (Singapore only). A line above the list such as `23 Rabiulakhir 1448H`, with a moon icon, from a bundled table of MUIS month
  starts: it is never calculated, and where the table does not give a certain answer no line is drawn. It changes at Maghrib (or at midnight, by a
  setting) and names Islamic days such as Hari Raya.
- **Reminders** (optional, off until you turn them on). A notification at each prayer you choose, with an optional heads-up, a vibrate-only mode,
  and a **Reminder health** screen that says what could stop them and how to fix it. Exact timing needs your permission in Android settings.
- **Home screen widgets.** Next prayer, today's times, or all six in a row, in light or dark glass or a solid card.
- **Easier reading.** Large and Extra large text, a high contrast card, bold text, or follow the phone's own settings. The screen also lists
  today's times as plain text for a screen reader.
- **Look.** A gradient, a solid colour, a photo you can position and zoom, or your wallpaper's colours as the background; text colour; dim;
  the list position, which you move in the app with a phone-shaped preview (the lock screen itself cannot be dragged).
- **Guided setup.** A fresh install walks you through putting the wallpaper on the lock screen (and checks that it worked), keeping
  notifications from covering the list, reminders, exact alarms and widgets. **Run setup again** is at the bottom of the home screen.

Everything works offline once the Singapore timetable has been downloaded; other places never use the network.

## Install on your phone

A sideload APK is built at `build-out/prayer-wallpaper-1.8.0-sideload.apk` (how to rebuild it: [docs/DEVELOPING.md](docs/DEVELOPING.md)). It is
signed with the Android debug key, so a later install from Google Play needs it removed first.

1. On the phone: Settings, About phone, tap *Build number* seven times; then Settings, System, Developer options, turn on *USB debugging*.
2. Connect it, then `~/Library/Android/sdk/platform-tools/adb install -r build-out/prayer-wallpaper-1.8.0-sideload.apk`
   (or copy the APK to the phone and open it).
3. Open **Prayer Live Wallpaper** and follow the setup. On Android's wallpaper screen choose **Lock screen**, so your home screen keeps its own
   wallpaper.

**Keeping it working.** The wallpaper does nothing in the background; only the daily timetable refresh and reminders can be delayed by a phone
maker's aggressive battery rules. Set Settings, Apps, Prayer Live Wallpaper, Battery to **Unrestricted** (the wording varies), turn on an *Autostart* or
*Background activity* switch if your phone has one, and **never press Force stop**: Android resets a live wallpaper when its app is force-stopped.
The **Battery** row of Reminder health reads these rules.

**Using your existing wallpaper.** Android does not let an ordinary app read the current wallpaper image, and applying a live wallpaper replaces the
old one. Choose the same photo in **Photo** mode and line it up with **Adjust position and zoom**, or use *Match my wallpaper* (three colours, read
before this app is on the lock screen).

## Documentation

| | |
|---|---|
| [docs/HOW_IT_WORKS.md](docs/HOW_IT_WORKS.md) | Where each part of the code lives, and the reasons behind the less obvious choices |
| [docs/DEVELOPING.md](docs/DEVELOPING.md) | Build, sideload, debug tools, and the yearly upkeep (Hijri table, timetable, city list) |
| [docs/play/RELEASE_STEPS.md](docs/play/RELEASE_STEPS.md) | Publishing on Google Play: steps, answers for the forms, the exact alarm declaration |
| [docs/play/listing.md](docs/play/listing.md) | The store listing text; the graphics are in `docs/play/graphics/` |
| `site/` | The privacy policy page, published at https://hafizuddin-a.github.io/prayer-lockscreen/ (its own git repository) |

## Known limits

- So far everything was checked on an Android 16 emulator and by unit tests. Not yet tested end to end on a real phone: a phone maker's launcher,
  battery rules, lock screen layout and fonts, and real TalkBack.
- The widgets' alarms are inexact, so a widget can redraw up to about ten minutes after a prayer time (the countdown itself never lags), and the
  one-minute Syuruk mark may show late or not at all on a widget. The lock screen redraws on the minute.
- Singapore data stops at the end of the cached year until the next timetable is published and fetched (late November). The Hijri table runs out
  after 27 January 2028; the line is then hidden until an update adds MUIS's next calendar.
- Calculated times can differ from a local mosque by a few minutes. Cities under about 15,000 people are not in the list: pick the nearest
  bigger one. Adhan 1.2.1 has no preset for Tehran's method, so Iran starts on the Muslim World League.
- The countdown's "in", "h" and "m" are English even with Malay prayer names.
- The list does not follow the phone as it travels: change the place yourself. Only portrait was checked.

## Credits

Not affiliated with or endorsed by MUIS. Prayer times data for Singapore: Majlis Ugama Islam Singapura (MUIS), via data.gov.sg, under the Singapore
Open Data Licence; Hijri month starts and Islamic events follow the Muis Calendar published by MUIS. Calculated times use Adhan (MIT); cities come
from GeoNames (CC BY 4.0). Calculated times are not an official timetable. The About screen carries all the credits and licence texts.

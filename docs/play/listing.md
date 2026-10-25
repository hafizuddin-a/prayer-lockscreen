# Store listing text

Copy each block into the Play Console (Grow, Store presence, Main store listing). Everything marked **[...]** needs a value from you. The steps
are in [RELEASE_STEPS.md](RELEASE_STEPS.md).
Character limits are Google's: title 30, short description 80, full description 4,000.

## App name (21 of 30)

```
Prayer Live Wallpaper
```

## Short description (79 of 80)

```
Prayer times on your lock screen. Reminders, widgets, MUIS times for Singapore.
```

## Full description (3224 of 4,000)

```
Prayer times, right on your lock screen.

Prayer Live Wallpaper turns your lock screen into your prayer timetable. Today's six times sit under your clock, the prayer that is on now is marked, and a line below counts down to the next one. It updates itself: nothing to open, nothing to refresh.

FEATURES
• Lock screen wallpaper that updates itself. The six prayer times, the current prayer marked, and a countdown to the next.
• Accurate to MUIS for Singapore. Singapore times come from the official MUIS timetable, and the Hijri date follows the MUIS calendar, with days such as Hari Raya and Nuzul Quran named. (Singapore only.)
• Any other city. Search about 34,000 cities, or use your approximate location once. Times are calculated on your phone with a method chosen for your country, which you can change.
• Reliable, on-time reminders (optional). A notification at each prayer time you choose, with an optional heads-up before. A health screen shows what could stop reminders on your phone and how to fix it.
• Easier to read. Large and extra large text, a high contrast card and bold letters, or follow your phone's own settings.
• Home screen widgets. Next prayer, today's times, or all six in a row. Light or dark glass, or a solid card.
• Light on battery. No foreground service and no wake locks. The wallpaper redraws only while your screen is on.
• Private. No account, no ads, no analytics. Your settings stay on your phone.
• Make it yours. A gradient, a solid colour or your own photo as the background; move the list; Malay or English prayer names.

SET UP IN A MINUTE
A guided setup walks you through putting the wallpaper on your lock screen. Choose Lock screen when Android asks, so your home screen keeps its own wallpaper.

GOOD TO KNOW
• This is a live wallpaper, so it replaces your lock screen background. Android draws the clock and notifications on top of it. If notifications cover the list, move the list lower or hide notification text in your phone's settings.
• Times for places outside Singapore are calculated on your phone and can differ from your local mosque or authority by a few minutes. Check with them if you need the exact time.
• For reminders at the exact minute, Android asks your permission to schedule exact alarms. Without it, reminders still work but can arrive late. Some phone makers' battery settings can also delay alarms.
• Needs Android 14 or later.

DATA AND CREDITS
Prayer times data: Majlis Ugama Islam Singapura (MUIS), via data.gov.sg, under the Open Data Licence. This app is not affiliated with or endorsed by MUIS. Times shown here may differ from official announcements. Please check MUIS for the official timetable.

Contains information from Muslim Prayer Timetables accessed on [DATE OF YOUR LAST DATA CHECK] from data.gov.sg which is made available under the terms of the Singapore Open Data Licence version 1.0 https://data.gov.sg/open-data-licence

Hijri month start dates and Islamic events follow the Muis Calendar published by MUIS.

Times for other places are calculated with Adhan (MIT licence). City data from GeoNames (geonames.org), CC BY 4.0.

FEEDBACK
Found a bug or want a feature? Message @madebyhafiz on Instagram: instagram.com/madebyhafiz
```

Before you paste it: replace **[DATE OF YOUR LAST DATA CHECK]** with the date you last checked the data (the About screen shows the real
download date, and the Singapore Open Data Licence asks for this notice in this form). Update it with each release.

## What's new (first release, limit 500)

```
First release. Prayer times on your lock screen that update themselves. Singapore times and the Hijri date follow the official MUIS timetable; other cities are calculated on your phone. Optional reminders, home screen widgets, accessibility options and a guided setup.
```

## Other fields

| Field | Value |
|---|---|
| Category | **Lifestyle**. (Personalization is the other fair choice, since it is a live wallpaper. Lifestyle matches what people search for.) |
| Tags | Prayer times, Live wallpaper, Lock screen, Widgets (pick the closest from Google's list) |
| Contact email | **[your public developer email: required by Play, shown on the listing]** |
| Website | https://www.instagram.com/madebyhafiz/ (optional) |
| Privacy policy | https://hafizuddin-a.github.io/prayer-lockscreen/ (hosted from `site/`, see [RELEASE_STEPS.md](RELEASE_STEPS.md), step 4) |
| Default language | English (United Kingdom or Singapore, as you prefer) |

## Graphics (already made, in `docs/play/graphics/`)

| Slot | File | Size |
|---|---|---|
| App icon | `icon-512.png` | 512 x 512 |
| Feature graphic | `feature-graphic-1024x500.png` | 1024 x 500 |
| Phone screenshots (2 to 8) | `poster-1-lock-screen.png`, `poster-6-movable-list.png`, `poster-2-accurate-to-muis.png`, `poster-3-reminders.png`, `poster-4-accessibility.png`, `poster-5-battery.png`, in this order | 1080 x 1920 |

The six posters are one per feature: the lock screen wallpaper, the movable list, accurate to MUIS, on-time reminders, accessibility, and battery. Poster 6 says the list is moved in the app (not on the lock screen itself). They are
made from real screenshots by `tools/make-posters.swift` (see "The Play posters" in [../DEVELOPING.md](../DEVELOPING.md) to rebuild them after the app's look changes).
They show the emulator, not your phone: Play accepts that, but you may prefer to re-shoot the screens on your phone and rebuild.

## Claims to keep honest (Google's metadata policy)

- No "official", no MUIS logo, and the "not affiliated with or endorsed by MUIS" line stays in the description and on poster 2.
- Say "accurate to MUIS" only for **Singapore** (the poster and description do). Other cities are calculated and can differ.
- Do not promise that widgets or reminders arrive to the second on every phone; the description already says what can delay them.
- No ratings, rankings, "free" or price claims in the title or the icon.

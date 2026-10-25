# Publishing on Google Play

The plan, in order, with the answers Google asks for. The listing text is in [listing.md](listing.md) and the graphics are in `graphics/`.
Package: `com.prayerwallpaper.app` (permanent once the first bundle is uploaded). Name: **Prayer Live Wallpaper**.

## Where things stand (13 October 2026)

| Item | State |
|---|---|
| Developer account | **Waiting for Google's identity verification** |
| Upload key | **Not created yet** (step 2). The bundle script refuses to build without it |
| Privacy policy | **Hosted**: https://hafizuddin-a.github.io/prayer-lockscreen/ (repository https://github.com/hafizuddin-a/prayer-lockscreen, source in `site/`). The app's About screen links to it |
| MUIS and the Hijri dates | **Confirmed**: the developer has MUIS's go-ahead to use the Hijri dates. The About screen keeps the credit |
| Exact alarm declaration | **Open**: text below, and a fallback build if Google says no |
| Developer contact email | **Open**: yours to give; the policy and listing currently point to Instagram (@madebyhafiz) |
| Date in the data licence notice | **Open**: fill in when you paste the listing |
| Listing text, graphics, data safety answers | Ready |
| Compliance checks | Passed (table below) |
| Tried from a Play install on a real phone | Not yet (step 8) |

## 1. Account

Create the Google Play developer account (one-off fee, identity verification). Check in the Console whether a **closed test with a minimum number
of testers for 14 days** is required before you can apply for production access: it applies to personal accounts created after November 2023. If
it does, plan for it now, because it is the slowest step. Add testers by email in the Closed testing track.

## 2. Create the upload key and sign (once)

Play App Signing is the default: you sign the bundle with an **upload key** only you hold, and Google re-signs it with the app signing key. Keep the
key file and its passwords somewhere safe and backed up; losing the upload key means asking Google to reset it.

```bash
keytool -genkeypair -v -keystore ~/prayer-wallpaper-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
cp keystore.properties.example keystore.properties     # then fill in the path and both passwords (the file is git-ignored)
tools/build-play-bundle.sh
```

The script runs the tests and lint, builds the bundle, and refuses to finish unless the bundle is really signed. The result is
`build-out/prayer-wallpaper-<version>-play.aab`. The R8 mapping file is embedded, so crash reports are readable.

## 3. Version

For each upload `versionCode` in `app/build.gradle.kts` must be higher than the last one uploaded (it is 9 now). `versionName` is what people see.

## 4. Privacy policy

Already hosted (see the table). Put the same address in the Console's privacy policy field. To change the policy: edit `site/index.html`, then
commit and push from inside `site/` (it is its own repository):

```bash
cd site && git add -A && git commit -m "Update the privacy policy" && git push
```

GitHub Pages republishes in about a minute. Change the "Last updated" date in the page when the text changes. The app's in-app privacy summary is
`Legal.PRIVACY_SUMMARY`; keep it in step with the page.

## 5. Create the app in the Console

Create app, name `Prayer Live Wallpaper`, English, **App**, **Free**, accept the declarations.

## 6. Store listing

Paste the text from [listing.md](listing.md) and upload the files from `graphics/`: the icon (512), the feature graphic (1024x500) and the six
posters as phone screenshots in the order the listing gives. Fill the **[DATE]** in the full description and your contact email. Widget
screenshots are a good addition: take them on your phone (the three widgets on a home screen, glass and card styles).

## 7. App content and declarations

### Data safety

You are the one attesting, so read each answer against the form's current wording. Play Console, Policy and programs, App content, Data safety.

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not applicable (nothing is collected). The one network request uses HTTPS (`usesCleartextTraffic="false"`) |
| Do you provide a way for users to request that their data is deleted? | Not applicable (no accounts, no data collected) |

Why "no data collected" is accurate:

| Data | What the app does | Leaves the phone? |
|---|---|---|
| Approximate location | Read **once**, only when the user taps "Use my location" (permission asked then, with a plain explanation), to pick the nearest city from a list inside the app. Not saved; only the chosen city is | No |
| Notifications | Optional reminders. The permission is asked only when reminders are turned on and used for nothing else. Alarms and notifications are set and shown on the phone | No |
| Reminder and widget history | A small 14-day log of when each alarm was planned and arrived, in private storage. Leaves only if the user taps "Share ... as text" | Only by the user's own action |
| Photo | The system photo picker. The app sees only the one photo chosen and keeps a smaller private copy | No |
| Wallpaper colours | Three colour numbers if the user asks to match their wallpaper, never the image | No |
| Identifiers, contacts, files, messages, purchases, health | Not accessed | No |

Google's form treats data that is only processed on the device and never transmitted as "not collected". Check this reading against the current form
wording for **Approximate location**.

**The one judgement call.** For Singapore the app downloads the public timetable from data.gov.sg over HTTPS. Like any web request, that server sees
the phone's IP address. Nothing about the user is put in the request, so the reading here is that the app is not "collecting" user data. If you
would rather be conservative, the policy already mentions the IP address, and if the form asks you can declare that no identifiers are sent by the
app itself.

### Other App content forms

| Section | Answer |
|---|---|
| Privacy policy | https://hafizuddin-a.github.io/prayer-lockscreen/ |
| Ads | No ads. The app does not use the advertising ID (answer No) |
| App access | All functionality is available without sign-in |
| Content rating (IARC) | Category "Utility, Productivity, Communication or Other". No violence, sexual content, strong language, controlled substances, gambling, user-generated content, location sharing or purchases. Expect "Everyone" |
| Target audience | 13 and over (not designed for children, so the Families policy does not apply) |
| News app, Government app | No, no. The data is government open data but the app is independent and says so; never describe it as official |
| Health, financial, COVID-19 | No |
| Photo and video permissions | Does not apply: no media permission is requested (the system photo picker is used) |
| Exact alarms | A declaration is needed because the manifest has `SCHEDULE_EXACT_ALARM`: see below |

### Exact alarms

The manifest declares `SCHEDULE_EXACT_ALARM` for reminders at the exact minute. It is **not** `USE_EXACT_ALARM`, which is granted automatically and
which Play keeps for alarm clock and calendar apps: this one the user grants in Android settings. Play restricts it and asks for a declaration. I
did not re-check the wording of Play's current form, so answer the questions it actually shows and use this as the content, after checking every
sentence is still true:

> Prayer Live Wallpaper has an optional prayer reminders feature. It sends a notification at each prayer time and, if chosen, a heads-up a set number of
> minutes before. Prayer times are fixed times set by religious practice, so a reminder that arrives late defeats its purpose. Exact timing is therefore
> necessary for this user-facing feature. Reminders are off by default. The user turns them on and then grants "Alarms & reminders" in Android settings.
> The app uses at most six alarms a day, plus the optional heads-up. It uses them only to show the reminder notification. It does not use exact alarms for
> ads, syncing, analytics, the wallpaper or the widgets. Without the permission the app still works and reminders fall back to approximate timing.

What backs each claim in the app:

| Claim | Where it is true |
|---|---|
| Optional, off by default | The Reminders switch; the setup's Reminders page has a "Not now" |
| The user grants it in settings | The setup's Exact alarms page and the Reminders screen open `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` (`SystemScreens.exactAlarms`) |
| Only for the reminder notification | `AndroidAlarmGateway` (`reminders/AlarmGateway.kt`) is the only caller of `setExactAndAllowWhileIdle`; the receiver shows the notification |
| Not used by widgets or the wallpaper | `WidgetScheduler` uses `setWindow` and `setAndAllowWhileIdle`; the wallpaper redraws from its own timer while visible |
| Works without it | The `AlarmMethod.INEXACT` fallback; the Reminders screen and Reminder health say reminders can be late |

Points that help: opt-in, user-granted, comparable to a calendar event reminder, narrow use, and a working fallback. Say plainly that reminders are one
feature and the wallpaper is the app's main purpose: a reviewer may notice, and the fallback is there for that case.

**If Play rejects the declaration**, publish with the fallback:

```bash
tools/build-play-bundle.sh --no-exact
```

That leaves the permission out of the manifest and the setup's Exact alarms page out of the app. Everything else is the same, and reminders arrive
approximately (Android may deliver them up to an hour late). The Reminders screen already says so.

## 8. Test, then release

1. Upload the signed bundle to **Internal testing**, install it from Play on your phone (remove the sideloaded copy first: it is signed with the debug
   key), and try every screen, the setup from a fresh install, a reminder (**Reminder health**, send a test reminder), and the widgets. Everything so
   far was checked on an emulator only.
2. Read the Pre-launch report in the Console for crashes on other devices, and any "Notification permission" warning.
3. Closed testing (if required, step 1), then Production, with a staged rollout if you like.

## 9. Before you press "send for review"

- [ ] The date in the Open Data Licence notice in the full description.
- [ ] A public developer email in the Console.
- [ ] The privacy policy URL works and is set in the Console (and in the app: it is).
- [ ] The exact alarm declaration is answered (or you chose the fallback build).
- [ ] You tried the app from the Play-installed build, not only the sideloaded one.

## Compliance review (8 October 2026, version 1.8.0, code 9)

Checked in the signed release APK and the source:

| Check | Result |
|---|---|
| Target API | **Pass.** `targetSdk 36` meets the rule for new apps and updates from 31 August 2026 |
| 16 KB page size | **Pass.** The only native code is androidx's `libandroidx.graphics.path.so` (4 ABIs): zip-aligned to 16 KB (`zipalign -c -P 16`) and every LOAD segment has `p_align 0x4000` |
| Permissions | **Pass.** `INTERNET`, `ACCESS_COARSE_LOCATION`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, plus androidx's own signature permission. No storage, media, fine or background location, overlay, accessibility, `USE_EXACT_ALARM`, battery-optimisation exemption, full-screen intent, foreground service or wake lock |
| Cleartext, backup, debuggable | **Pass.** `usesCleartextTraffic=false`, `allowBackup=false`, not debuggable, no debug classes in the dex, no API key in release |
| Live wallpaper feature | The manifest declares `android.software.live_wallpaper` as required, so Play hides the app from phones that cannot show one. `android.hardware.location` and `...location.network` are `required="false"` |
| Lint and tests | **Pass.** Release lint 0 errors |

How the permissions are used: `ACCESS_COARSE_LOCATION` is a runtime permission asked only when "Use my location" is tapped, after a plain explanation
(approximate, foreground only; no special declaration form, but it is in the Data safety section above and the policy describes it). `ACCESS_NETWORK_STATE`
comes from WorkManager. `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM` and `RECEIVE_BOOT_COMPLETED` belong to the optional reminders (and the boot
receiver also sets the widgets' alarms again). A `-PexactAlarms=false` build leaves `SCHEDULE_EXACT_ALARM` out of the merged manifest entirely.

After launch, the yearly jobs (the Hijri table, the new timetable, the city list) are in [../DEVELOPING.md](../DEVELOPING.md).

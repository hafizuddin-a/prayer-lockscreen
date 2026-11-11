# Releasing: GitHub, Google's developer verification, and Google Play

How the app reaches people and what has to be done for each release. **A** and **B** are how it is distributed now (a download page and the GitHub
release). From **Google Play: where things stand** down is the Play plan, with the answers Google asks for, kept for if Play is ever used: it needs a
closed test with enough testers, which is why it is parked. The listing text is in [listing.md](listing.md) and the graphics are in `graphics/`.
Package: `com.prayerwallpaper.app`. Name: **Prayer Live Wallpaper**.

## A. Releasing on GitHub (how people get the app)

People get the app from the download page (`site/download/`, at https://hafizuddin-a.github.io/prayer-lockscreen/download/), whose button is
`https://github.com/hafizuddin-a/prayer-lockscreen/releases/latest/download/prayer-wallpaper.apk`. The installed app asks the same release feed
(`releases/latest`) about updates. Both rely on two rules, so keep to them:

- every release has **one APK asset named exactly `prayer-wallpaper.apk`** (the file `tools/build-release-apk.sh` writes), and
- every release is **signed with the same key** (`~/prayer-wallpaper-upload.jks`). Android only installs an update signed like the installed app.

For each release:

1. Raise `versionCode` and `versionName` in `app/build.gradle.kts`. The app offers a release when its tag (`v1.8.2`) is a later version than its own
   `versionName`, and Android needs the new `versionCode` to be higher.
2. `tools/build-release-apk.sh`: tests, lint, build, sign, and the checksums.
3. Worth doing: install that file on your own phone by opening it (not with `adb`) and, if Google Play Protect says **App scan recommended**, tap
   **Scan app**. Google then has seen the file, which may spare other people that step. (Not something Google documents, so treat it as a courtesy.)
4. `gh release create v1.8.2 build-out/prayer-wallpaper.apk --title "Prayer Live Wallpaper 1.8.2" --notes-file notes.md`. Notes: what changed in
   plain words, the APK's SHA-256 and the certificate's SHA-256 (both printed by the script).
5. Check the fixed link: `curl -sIL https://github.com/hafizuddin-a/prayer-lockscreen/releases/latest/download/prayer-wallpaper.apk | grep -iE '^(HTTP|content-length)'`
   should end in a 200 and the APK's size. Phones with the app see the release within a day, or at once with **Check for updates** under About.

Do not replace a published release's APK with a different file under the same version (phones that have that version would never be offered the
fix), and do not publish a pre-release or draft expecting phones to get it: the app ignores both.

What a person sees, checked on the emulator on 11 November 2026 (1.8.0, installed from a file, updating to the published 1.8.1): the home screen
shows **Update available**, **Update** leads to **Allow updates** (Android's **Install unknown apps** page, switched on once), the app downloads
from GitHub, checks the file, and Android asks **Update this app?**. A file Google has not seen yet can also bring up Play Protect's **App scan
recommended** (it did not for 1.8.1, which had been scanned once already: see step 3). Afterwards `dumpsys package` showed the app itself as the
installer (`installerPackageName=com.prayerwallpaper.app`), which is what lets Android skip its prompt for later updates. That quiet case was then seen on 1.8.2 to 1.8.3
(12 November 2026): no Android prompt, though Play Protect's **App scan recommended** still appeared for the new file.

The page and the privacy policy are in `site/`: edit, push to `main`, and `.github/workflows/pages.yml` publishes them in about a minute.
`site/assets/download-qr.svg` is a QR code for the download page's address (made with `swift tools/make-qr.swift`; scan the result with a phone
before pushing if the address ever changes).

## B. Registering the app with Google (Android developer verification)

**Why.** From 2027 every app on a certified Android phone has to be registered to a verified developer, or the person installing it must go through
Android's "advanced flow" (turn on developer mode, restart, wait a day, then tap "Install anyway"). Since 30 September 2026 this is enforced in
Singapore, Brazil, Indonesia and Thailand, but only for installs from Google Play, Galaxy Store, GetApps and the other stores Google lists.
Installing an APK downloaded from the page is not checked yet, and `adb` installs never are. Registering takes a few steps and keeps the install
experience as it is now. (Google's overview: https://developer.android.com/developer-verification/guides.)

**What gets registered:** the package `com.prayerwallpaper.app` and the SHA-256 of the key that signs the GitHub APK:
`7C:0F:AF:E6:5F:40:F8:2F:D0:E3:39:53:81:67:2B:84:3C:81:B2:D4:2F:87:26:8B:4C:26:3B:B7:71:AC:7D:A7`
(the same in plain hex: `7c0fafe65f40f82fd0e3395381672b843c81b2d42f87268b4c263bb771ac7da7`; `tools/build-release-apk.sh` prints it).

**Where.** With a Play Console account (identity verified), use Play Console; it needs no store listing and no testers for an app that is only
distributed outside Play. Without one, the Android Developer Console (https://android.google.com/developerconsole) does the same: identity check,
a one-off US$25 fee for full distribution. Its free "limited distribution" account is for up to 20 devices, so it does not suit a public download.

**Steps** (Play Console Help, "Registering Android package names"):

1. Play Console, **Android developer verification**, **Package names** tab, **Register package name**. Enter `com.prayerwallpaper.app` and a name for
   yourself (Prayer Live Wallpaper), then **Next**.
2. **Key.** If Google has never seen the package on Android, choose **Add key** and paste the SHA-256 above. If it has installs, choose **Select
   key** and pick the fingerprint from the list of eligible ones (or search for it), then **Add key**. A key that is not listed can still be
   requested: expand the list of other keys and give a short reason (the key signs the app people already have installed).
3. **Prove you own the key.** Choose **Upload APK**, copy the **snippet** the console shows, create `app/src/main/assets/adi-registration.properties`
   with the snippet in it, run `tools/build-release-apk.sh`, and upload `build-out/prayer-wallpaper.apk`. Before uploading, check it:
   `unzip -p build-out/prayer-wallpaper.apk assets/adi-registration.properties` shows the snippet and `apksigner verify --print-certs` shows the
   key above. The file name must be exactly `adi-registration.properties`, and the APK must be a release build signed with that key.
4. Wait for Google's email. The package's status on the **Package names** tab goes from In review to **Registered** (a key takes a few minutes to
   reach Android's registry after it is added).
5. Keep `adi-registration.properties` in the app: it is an identifier for the developer account, not a secret, and later releases can carry it.

Notes. A package can have more than one key (Add key on its page), which is how a second signing key, such as Google's if the app ever goes on Play,
is registered alongside this one. **If the key is lost the app cannot be registered or updated**: keep `~/prayer-wallpaper-upload.jks` and its
password in two places. Check the status in the Console, or with Android Studio Panda 4 or later when it builds a signed APK.

## C. Keeping the signing key safe

Three things matter: the key file `~/prayer-wallpaper-upload.jks` (alias `upload`), its store password and its key password (both in
`keystore.properties`, which is git-ignored). **Lose them** and no release can ever update the phones that already have the app (people would have
to uninstall, losing their settings), and the app cannot be registered or re-registered with Google. **Leak them** and someone could sign an update
Android accepts as yours. So the key needs a copy that survives your Mac and a copy that stays secret.

1. **Password manager.** One secure note, "Prayer Live Wallpaper signing key": the alias, both passwords, the certificate SHA-256
   (`7C:0F:AF:E6:...:7D:A7`) and where the file lives. If the manager can attach files (1Password, Bitwarden Premium), attach the `.jks` too.
2. **An encrypted copy away from the Mac.** Put the `.jks` in an encrypted disk image (the passphrase is a new one, kept in the password manager),
   then copy the image to a USB stick kept elsewhere, and to one more place (an encrypted image is safe in a cloud drive too):
   `hdiutil create -size 5m -fs APFS -encryption AES-256 -volname signing-key ~/signing-key.dmg` (it asks for the passphrase), open the image, copy the
   file in, eject.
3. **Test a copy now, and once a year.** Copy the `.jks` out of the backup to a temporary folder and run
   `keytool -list -v -keystore <that copy> -alias upload`. It must ask for the password and show SHA-256 `7C:0F:AF:E6:5F:40:F8:2F:D0:E3:39:53:81:67:2B:84:3C:81:B2:D4:2F:87:26:8B:4C:26:3B:B7:71:AC:7D:A7`.
4. **Never** email the key, commit it, put it unencrypted in iCloud, Dropbox or Drive, or keep the password only in the folder beside it.
5. **The Mac itself.** FileVault on, a strong login password, and Time Machine backups encrypted. `git clean -fdx` deletes ignored files, including
   `keystore.properties` (the passwords, not the key file): keep the passwords in the password manager, not only there.

If the key ever leaks, Android's key rotation (`apksigner rotate`, which needs the old key to prove the link) lets phones follow to a new key, and the
Console lets a package have more than one key: so keep the old key even after a rotation, and register the new one.

## Google Play: where things stand (25 October 2026)

The Play bundle is built with `tools/build-play-bundle.sh`, which always passes `-PselfUpdate=false`: Google Play does not allow an app to update
itself, so that build has no `REQUEST_INSTALL_PACKAGES`, no update check and no update screens, and everything in this part is unchanged by the
updater.

| Item | State |
|---|---|
| Developer account | **Waiting for Google's identity verification** (as of 25 October: check Settings, Developer account in the Console) |
| Upload key | **Created** (25 October 2026): `~/prayer-wallpaper-upload.jks`, with its password in `keystore.properties` (git-ignored). **Back both up.** The same key signs the GitHub release APK |
| Privacy policy | **Hosted**: https://hafizuddin-a.github.io/prayer-lockscreen/ (source in `site/`, published by a GitHub Actions workflow). The app's About screen links to it |
| Source code | **Public and open source (MIT)**: https://github.com/hafizuddin-a/prayer-lockscreen, with the APK in the GitHub release |
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
key file and its passwords somewhere safe and backed up; losing the upload key means asking Google to reset it. The key already exists (see the
table above); these are the commands that made it, for the record or to make another:

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

Already hosted (see the table). Put the same address in the Console's privacy policy field. To change the policy: edit `site/index.html`, commit and
push to `main`; the workflow in `.github/workflows/pages.yml` republishes it in about a minute. Change the "Last updated" date in the page when the text changes. The app's in-app privacy summary is
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
| Wallpaper colours | Only from older versions that had "Match my wallpaper" (removed): three colour numbers, never the image | No |
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
which Play keeps for alarm clock and calendar apps: this one the user grants in Android settings. Play restricts it and asks for a declaration. The
wording of Play's current form has not been re-checked, so answer the questions it actually shows and use this as the content, after checking every
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

1. Upload the signed bundle to **Internal testing**, install it from Play on your phone (remove any sideloaded copy first: Google re-signs the app with its own
   key, which differs from the one on the GitHub APK and on a debug build), and try every screen, the setup from a fresh install, a reminder (**Reminder health**, send a test reminder), and the widgets. Everything so
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
The build downloaded from GitHub also declares `REQUEST_INSTALL_PACKAGES`, for its updater; a `-PselfUpdate=false` build (the Play bundle, made by
`tools/build-play-bundle.sh`) leaves it out the same way (checked in a built APK with `aapt2 dump permissions`, 11 November 2026).

After launch, the yearly jobs (the Hijri table, the new timetable, the city list) are in [../DEVELOPING.md](../DEVELOPING.md).

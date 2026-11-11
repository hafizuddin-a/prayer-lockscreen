# Security

## Reporting a problem

Please report a security problem privately, not in a public issue.

- On GitHub: the **Security** tab, then **Report a vulnerability**.
- Or message the developer on Instagram, [@madebyhafiz](https://www.instagram.com/madebyhafiz/).

Say what you found and how to see it. You will get an answer, and a fix in the next release.

## Which version

Only the newest release gets fixes: https://github.com/hafizuddin-a/prayer-lockscreen/releases/latest. The app looks for it once a month (switch that off under About, or tap **Check for updates**).

## Checking a download

Every release is signed with the same key, and Android only installs an update signed like the app already on the phone. The signing certificate's SHA-256 is:

```
7C:0F:AF:E6:5F:40:F8:2F:D0:E3:39:53:81:67:2B:84:3C:81:B2:D4:2F:87:26:8B:4C:26:3B:B7:71:AC:7D:A7
```

Check a file with `apksigner verify --print-certs prayer-wallpaper.apk`. Each release also lists the APK's own SHA-256. Only download from the [download page](https://hafizuddin-a.github.io/prayer-lockscreen/download/) or this repository's releases.

## What the app does

It has no account and collects nothing. It asks the network for the Singapore timetable (data.gov.sg) and, in the build from GitHub, for new versions (api.github.com). The [privacy policy](https://hafizuddin-a.github.io/prayer-lockscreen/) has the details, and [docs/HOW_IT_WORKS.md](docs/HOW_IT_WORKS.md) explains how updates are checked before they are installed.

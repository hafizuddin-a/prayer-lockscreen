#!/usr/bin/env bash
# Builds an APK you can install on your own phone: runs the tests and lint, builds the minified release APK, and signs it with the Android
# debug key. It needs no key of yours, so anyone who clones the repository can run it. Output: build-out/prayer-wallpaper-<version>-sideload.apk
#   tools/build-sideload.sh
# A phone with this copy cannot update to the release-key APK (tools/build-release-apk.sh) or to the Google Play version without being
# uninstalled first, because the signing keys differ. For the same reason this build is made without the self-updater
# (-PselfUpdate=false): it could never install the releases on GitHub, so it would only show an "Update available" that fails.
set -eu
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
BT="${BUILD_TOOLS:-$HOME/Library/Android/sdk/build-tools/36.0.0}"
out=app/build/outputs/apk/release
# Gradle names the file by whether it signed it (it does when keystore.properties exists), so clear both before building.
rm -f "$out/app-release-unsigned.apk" "$out/app-release.apk"
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease -PselfUpdate=false --console=plain
src="$out/app-release-unsigned.apk"
[ -f "$src" ] || src="$out/app-release.apk"
version=$(grep -E 'versionName' app/build.gradle.kts | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
mkdir -p build-out
dest="build-out/prayer-wallpaper-$version-sideload.apk"
"$BT/apksigner" sign --ks ~/.android/debug.keystore --ks-pass pass:android --key-pass pass:android --out "$dest" "$src"
"$BT/apksigner" verify "$dest"
echo "Sideload APK ready: $dest"

#!/usr/bin/env bash
# Builds the APK to install on your own phone: runs the tests and lint, builds the minified release APK, and signs it with the Android
# debug key. Output: build-out/prayer-wallpaper-<version>-sideload.apk
#   tools/build-sideload.sh
# A later install from Google Play needs this copy removed first, because the signing keys differ. For Play use tools/build-play-bundle.sh.
set -eu
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
BT="${BUILD_TOOLS:-$HOME/Library/Android/sdk/build-tools/36.0.0}"
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease --console=plain
version=$(grep -E 'versionName' app/build.gradle.kts | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
mkdir -p build-out
dest="build-out/prayer-wallpaper-$version-sideload.apk"
"$BT/apksigner" sign --ks ~/.android/debug.keystore --ks-pass pass:android --key-pass pass:android \
  --out "$dest" app/build/outputs/apk/release/app-release-unsigned.apk
"$BT/apksigner" verify "$dest"
echo "Sideload APK ready: $dest"

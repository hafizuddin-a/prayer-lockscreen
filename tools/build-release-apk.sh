#!/usr/bin/env bash
# Builds the APK for a GitHub release: runs the tests and lint, builds the minified release APK signed with the key in keystore.properties,
# checks the signature and prints the checksums to put in the release notes. Output: build-out/prayer-wallpaper-<version>.apk
#   tools/build-release-apk.sh
# Needs keystore.properties in the project root (see keystore.properties.example and docs/play/RELEASE_STEPS.md, step 2). Keep the key file and
# its password backed up: phones that install this APK can only be updated by an APK signed with the same key.
set -eu
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
BT="${BUILD_TOOLS:-$HOME/Library/Android/sdk/build-tools/36.0.0}"
if [ ! -f keystore.properties ]; then
  echo "keystore.properties is missing: create the key and the file first (docs/play/RELEASE_STEPS.md, step 2)."
  exit 1
fi
out=app/build/outputs/apk/release
rm -f "$out/app-release-unsigned.apk" "$out/app-release.apk"
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease --console=plain
if [ ! -f "$out/app-release.apk" ]; then
  echo "Gradle did not sign the APK. Check storeFile in keystore.properties."
  exit 1
fi
version=$(grep -E 'versionName' app/build.gradle.kts | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
mkdir -p build-out
dest="build-out/prayer-wallpaper-$version.apk"
cp "$out/app-release.apk" "$dest"
"$BT/apksigner" verify --print-certs "$dest" | grep -E "Signer #1 certificate (DN|SHA-256)"
echo "APK SHA-256: $(shasum -a 256 "$dest" | cut -d' ' -f1)"
echo "Release APK ready: $dest"

#!/usr/bin/env bash
# Builds the signed Android App Bundle for Google Play and checks it is really signed.
#   tools/build-play-bundle.sh                 normal build (exact alarm permission declared)
#   tools/build-play-bundle.sh --no-exact      fallback: leaves SCHEDULE_EXACT_ALARM out (reminders then arrive approximately)
# Needs keystore.properties in the project root (see keystore.properties.example and docs/play/RELEASE_STEPS.md).
set -eu
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
extra=""
[ "${1:-}" = "--no-exact" ] && extra="-PexactAlarms=false"
if [ ! -f keystore.properties ]; then
  echo "keystore.properties is missing: Play needs a bundle signed with your upload key."
  echo "Create the key and the file first: docs/play/RELEASE_STEPS.md, step 2."
  exit 1
fi
./gradlew :app:testDebugUnitTest :app:lintRelease :app:bundleRelease $extra --console=plain
aab=app/build/outputs/bundle/release/app-release.aab
version=$(grep -E 'versionName' app/build.gradle.kts | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
if "$JAVA_HOME/bin/jarsigner" -verify "$aab" 2>&1 | grep -q "jar verified"; then
  mkdir -p build-out
  dest="build-out/prayer-wallpaper-$version-play.aab"
  cp "$aab" "$dest"
  echo "Signed bundle ready: $dest"
else
  echo "The bundle is NOT signed. Check keystore.properties."
  exit 1
fi

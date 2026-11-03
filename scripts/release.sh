#!/usr/bin/env bash
# Builds, verifies and publishes a release from this machine.
#
#   scripts/release.sh            build and verify only (APK + .sha256 in dist/)
#   scripts/release.sh --publish  also tag the current commit and create the GitHub release
#
# Signing: uses keystore.properties or DISCIPLINE_* env vars if present, otherwise the debug
# key in ~/.android/debug.keystore (what every release so far is signed with).
set -euo pipefail
cd "$(dirname "$0")/.."

EXPECTED_CERT_SHA256=654590b31f96b13e4f2f0af08b1c46c27b3b6219fbe5d63ac9c3e18de56a0b3f
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

version=$(grep -oE 'versionName = "[^"]+"' app/build.gradle.kts | cut -d'"' -f2)
apk="dist/Discipline-$version.apk"
echo "==> Discipline $version"

./gradlew -q :app:testDebugUnitTest :app:assembleRelease
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk "$apk"

apksigner=$(ls "$SDK"/build-tools/*/apksigner | sort -V | tail -1)
cert=$("$apksigner" verify --print-certs "$apk" | grep -m1 'SHA-256 digest' | awk '{print $NF}')
if [ "$cert" != "$EXPECTED_CERT_SHA256" ]; then
  echo "APK is signed with $cert, expected $EXPECTED_CERT_SHA256" >&2
  exit 1
fi
shasum -a 256 "$apk" | awk '{print $1}' > "$apk.sha256"
echo "==> $apk ($(du -h "$apk" | cut -f1)), sha256 $(cat "$apk.sha256")"

if [ "${1:-}" = "--publish" ]; then
  shift  # anything else is passed to gh as extra assets
  awk -v v="$version" '
    $0 ~ "^## \\[?v?"v"\\]?" { on = 1; next }
    on && /^## / { exit }
    on { print }
  ' CHANGELOG.md > dist/notes.md
  # Creating the release also creates the tag on GitHub, so the Release workflow sees an
  # existing release and skips instead of building a second copy.
  git push origin HEAD
  gh release create "v$version" "$apk" "$apk.sha256" "$@" --target "$(git rev-parse HEAD)" \
    --title "Discipline $version" --notes-file dist/notes.md
  git fetch --tags -q
fi

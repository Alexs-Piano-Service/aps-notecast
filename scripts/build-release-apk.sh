#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

SIGNING_ENV="${APS_NOTECAST_SIGNING_ENV:-$HOME/.aps-notecast-signing.env}"
if [[ ! -f "$SIGNING_ENV" ]]; then
  echo "Missing signing environment file: $SIGNING_ENV" >&2
  echo "Expected APS_NOTECAST_KEYSTORE, APS_NOTECAST_KEY_ALIAS, and APS_NOTECAST_KEYSTORE_PASSWORD." >&2
  exit 1
fi

# shellcheck disable=SC1090
source "$SIGNING_ENV"

: "${APS_NOTECAST_KEYSTORE:?Missing APS_NOTECAST_KEYSTORE}"
: "${APS_NOTECAST_KEY_ALIAS:?Missing APS_NOTECAST_KEY_ALIAS}"
: "${APS_NOTECAST_KEYSTORE_PASSWORD:?Missing APS_NOTECAST_KEYSTORE_PASSWORD}"

if [[ ! -f "$APS_NOTECAST_KEYSTORE" ]]; then
  echo "Missing keystore: $APS_NOTECAST_KEYSTORE" >&2
  exit 1
fi

if [[ -n "${ANDROID_HOME:-}" ]]; then
  SDK_DIR="$ANDROID_HOME"
elif [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
  SDK_DIR="$ANDROID_SDK_ROOT"
elif [[ -f local.properties ]]; then
  SDK_DIR="$(sed -n 's/^sdk\.dir=//p' local.properties | head -n 1)"
else
  SDK_DIR=""
fi

if [[ -z "$SDK_DIR" || ! -d "$SDK_DIR/build-tools" ]]; then
  echo "Could not find Android SDK build-tools. Set ANDROID_HOME or sdk.dir in local.properties." >&2
  exit 1
fi

BUILD_TOOLS_DIR="$(find "$SDK_DIR/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
APKSIGNER="$BUILD_TOOLS_DIR/apksigner"
ZIPALIGN="$BUILD_TOOLS_DIR/zipalign"

if [[ ! -x "$APKSIGNER" || ! -x "$ZIPALIGN" ]]; then
  echo "Could not find apksigner and zipalign in $BUILD_TOOLS_DIR." >&2
  exit 1
fi

if [[ -x ./gradlew ]]; then
  GRADLE_CMD=(./gradlew)
elif command -v gradle >/dev/null 2>&1; then
  GRADLE_CMD=(gradle)
else
  GRADLE_CMD=(/home/peppe/.gradle/wrapper/dists/gradle-8.14.3-bin/cv11ve7ro1n3o1j4so8xd9n66/gradle-8.14.3/bin/gradle)
fi

"${GRADLE_CMD[@]}" :app:assembleRelease

RELEASE_DIR="$ROOT_DIR/app/build/outputs/apk/release"
UNSIGNED_APK="$RELEASE_DIR/app-release-unsigned.apk"
ALIGNED_APK="$RELEASE_DIR/aps-notecast-release-aligned.apk"
SIGNED_APK="$RELEASE_DIR/aps-notecast-release-signed.apk"

if [[ ! -f "$UNSIGNED_APK" ]]; then
  echo "Release build did not produce $UNSIGNED_APK." >&2
  exit 1
fi

"$ZIPALIGN" -p -f 4 "$UNSIGNED_APK" "$ALIGNED_APK"
"$APKSIGNER" sign \
  --ks "$APS_NOTECAST_KEYSTORE" \
  --ks-key-alias "$APS_NOTECAST_KEY_ALIAS" \
  --ks-pass env:APS_NOTECAST_KEYSTORE_PASSWORD \
  --out "$SIGNED_APK" \
  "$ALIGNED_APK"

"$APKSIGNER" verify --verbose "$SIGNED_APK"

echo "Signed APK: $SIGNED_APK"

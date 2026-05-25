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

if [[ -x ./gradlew ]]; then
  GRADLE_CMD=(./gradlew)
elif command -v gradle >/dev/null 2>&1; then
  GRADLE_CMD=(gradle)
else
  GRADLE_CMD=(/home/peppe/.gradle/wrapper/dists/gradle-8.14.3-bin/cv11ve7ro1n3o1j4so8xd9n66/gradle-8.14.3/bin/gradle)
fi

if ! command -v jarsigner >/dev/null 2>&1; then
  echo "Could not find jarsigner. Install/use a JDK, not just a JRE." >&2
  exit 1
fi

"${GRADLE_CMD[@]}" :app:bundleRelease

RELEASE_DIR="$ROOT_DIR/app/build/outputs/bundle/release"
UNSIGNED_AAB="$RELEASE_DIR/app-release.aab"
SIGNED_AAB="$RELEASE_DIR/aps-notecast-release-signed.aab"

if [[ ! -f "$UNSIGNED_AAB" ]]; then
  echo "Release build did not produce $UNSIGNED_AAB." >&2
  exit 1
fi

rm -f "$SIGNED_AAB"
jarsigner \
  -keystore "$APS_NOTECAST_KEYSTORE" \
  -storepass "$APS_NOTECAST_KEYSTORE_PASSWORD" \
  -keypass "${APS_NOTECAST_KEY_PASSWORD:-$APS_NOTECAST_KEYSTORE_PASSWORD}" \
  -signedjar "$SIGNED_AAB" \
  "$UNSIGNED_AAB" \
  "$APS_NOTECAST_KEY_ALIAS"

jarsigner -verify -verbose -certs "$SIGNED_AAB" >/dev/null

echo "Signed app bundle: $SIGNED_AAB"

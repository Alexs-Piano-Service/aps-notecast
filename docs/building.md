# Building APS NoteCast

## Requirements

- Android Studio or Android SDK 36
- JDK 17
- An Android device or emulator running Android 8.0 or newer

Build, test, and lint from the repository root:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Install and start the debug build:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.alexanderpeppe.notecast/com.alexanderpeppe.pianobeam.MainActivity
```

Use a physical Android device for Bluetooth LE MIDI, USB MIDI, foreground media controls, and real disconnect testing.

## Signed release bundle

Run:

```bash
scripts/build-release-bundle.sh
```

The release script reads these values from `~/.aps-notecast-signing.env` by default:

```text
APS_NOTECAST_KEYSTORE
APS_NOTECAST_KEY_ALIAS
APS_NOTECAST_KEYSTORE_PASSWORD
# Optional when the key password differs from the keystore password:
APS_NOTECAST_KEY_PASSWORD
```

`APS_NOTECAST_KEY_PASSWORD` defaults to `APS_NOTECAST_KEYSTORE_PASSWORD`. Set `APS_NOTECAST_SIGNING_ENV=/path/to/signing.env` to use another file. The signed Android App Bundle is written to:

```text
app/build/outputs/bundle/release/aps-notecast-release-signed.aab
```

In VS Code, the same workflow is available from **Terminal → Run Build Task → Build signed release App Bundle**.

## Project structure

- `app/src/main/java/com/alexanderpeppe/pianobeam/MainActivity.kt` — Jetpack Compose library, connection, playback, recording, and app-info UI.
- `app/src/main/java/com/alexanderpeppe/pianobeam/service/NoteCastService.kt` — MIDI connections, playback, media session, notification, recording, reconnect, and diagnostics.
- `app/src/main/java/com/alexanderpeppe/pianobeam/data/` — library metadata, settings models, and persistence.
- `app/src/main/java/com/alexanderpeppe/pianobeam/midi/` — Standard MIDI File parser, writer, routing, merge, and note-state logic.
- `app/src/main/java/com/alexanderpeppe/pianobeam/ui/` — theme and settings UI.
- `app/src/main/res/raw/` — bundled public-domain demo MIDI files.
- `app/src/main/res/drawable-nodpi/` — app and brand artwork.
- `fastlane/metadata/android/` — Android store metadata and changelogs.

## Languages

Android locale resources are present for English, Spanish, French, German, Italian, Portuguese, Portuguese (Brazil), Dutch, Polish, Japanese, Korean, Simplified Chinese, and Bulgarian. Keep stable user-facing Compose strings in Android resources so they can be localized consistently.

See [CONTRIBUTING.md](../CONTRIBUTING.md) for code style and pull-request expectations, and [TESTING.md](../TESTING.md) for device and player-piano test procedures.

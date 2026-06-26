# Contributing

Thanks for helping improve APS NoteCast.

## Development Setup

1. Install Android Studio, Android SDK 36, and JDK 17.
2. Open the repository in Android Studio or build from the command line.
3. Run:

```bash
./gradlew :app:assembleDebug :app:lintDebug
```

Use a real Android device for BLE MIDI work. Emulators generally cannot validate WIDI pairing, Android MIDI device exposure, foreground media controls, or real disconnect behavior.

## Test Copies And Instruments

Use test copies of MIDI files whenever possible. When testing with a physical player piano:

- Start at low volume.
- Test Stop and second-tap Stop cleanup before long playback.
- Keep the phone close to the BLE MIDI adapter during timing tests.
- Confirm sustain and all-notes-off behavior on the receiving instrument.
- Do not test unattended playlists until disconnect, stop, and second-tap Stop cleanup behavior are known-good.

## Code Style

- Prefer existing Kotlin and Compose patterns in the app.
- Keep the main library UI compact.
- Put advanced controls in settings or dialogs unless they are needed every session.
- Keep Bluetooth and MIDI error messages direct and actionable.
- Use Android framework MIDI APIs for device I/O.
- Keep MIDI parser/writer changes covered by careful manual testing with known-good files.
- Move stable user-facing text into Android string resources as UI labels settle.

## Pull Request Checklist

- Describe the user-facing change.
- Mention tested Android version/device when Bluetooth, media controls, or storage flows are involved.
- Run `:app:assembleDebug`.
- Run `:app:lintDebug`.
- Update `CHANGELOG.md` for notable behavior, settings, safety, or compatibility changes.
- Update `README.md` if workflows, permissions, or hardware expectations change.

## Contribution License

By contributing to APS NoteCast, you agree that your contribution is provided under the Apache License, Version 2.0.

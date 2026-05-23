# APS NoteCast

APS NoteCast is an Android app for playing Standard MIDI Files over Bluetooth LE MIDI to WIDI and compatible MIDI adapters connected to player-piano systems.

The primary path is:

`Android phone -> Bluetooth LE MIDI -> WIDI / compatible adapter -> DIN MIDI -> player piano`

APS NoteCast is developed by Alex's Piano Service LLC for practical player-piano service workflows: importing MIDI files, organizing playlists, connecting to BLE MIDI hardware, recording incoming MIDI, and keeping playback controls accessible from both the app and Android media controls.

## Highlights

- First-run connection wizard for BLE MIDI.
- Pair-first guidance for WIDI devices that must be paired in Android Bluetooth settings.
- Bluetooth-off notices with a direct request to turn Bluetooth on.
- BLE and Android MIDI device scan results in one connection flow.
- Preferred-device memory and auto-reconnect while the app is open.
- Real-time connection monitoring that marks the device disconnected when Android MIDI removal, Bluetooth state, or MIDI heartbeat checks indicate the adapter is gone.
- Timed reconnect attempts after unexpected connection loss.
- Local MIDI library with bundled Chopin demo files.
- Sample playlist containing the bundled Chopin pieces.
- Collapsible playlists with add-file multi-select sheets, drag/drop support, playlist cloning, rename, delete, and reordering.
- Single-file and playlist playback, including sequential, shuffle, repeat-one, repeat-playlist, stop-after-current, and stop-after-playlist behaviors.
- Playback transport with play/pause, stop, previous, next, visible progress, seek, volume, and panic.
- Android foreground media playback service with lock-screen/media controls and seek support.
- MIDI panic that sends sustain off, sostenuto off, soft pedal off, all sound off, reset controllers, and all notes off on all 16 MIDI channels.
- Tempo and transpose controls, including an option to leave channel 10 drums untransposed.
- Advanced per-channel mute, solo, and volume controls.
- BLE MIDI recording to Standard MIDI File with count-in, discard confirmation, silence trimming, and optional save-to-playlist.
- MIDI file export/share and library backup/restore.
- Light, dark, and system-default appearance modes.

## Hardware Notes

APS NoteCast is designed around Android's MIDI API and the standard BLE MIDI service UUID:

`03B80E5A-EDE8-4B33-A751-6CE34EC4C700`

Many WIDI devices need to be paired in Android Bluetooth settings before Android exposes them as MIDI devices. APS NoteCast's connection flow intentionally tells users to pair first, then scan and connect inside the app.

For reliable testing:

1. Power the WIDI or compatible BLE MIDI adapter.
2. Pair it in Android Bluetooth settings if Android requires pairing.
3. Open APS NoteCast.
4. Grant Bluetooth/Nearby Devices permissions.
5. Scan from APS NoteCast and connect to the paired MIDI device.
6. Import or select a MIDI file.
7. Test playback at low volume first.
8. Test Stop and Panic before leaving a playlist unattended.

## Safety

Player pianos are physical instruments. Before using long playlists or unattended playback, confirm that the receiving instrument responds correctly to stop, panic, pedal-off, and all-notes-off messages.

Use copies of MIDI files whenever possible and keep backups of anything important. APS NoteCast can export individual MIDI files and a JSON library backup, but device storage and Android document permissions can still fail or be revoked.

## Build

Requirements:

- Android Studio with Android SDK 36.
- JDK 17.
- A device or emulator running Android 8.0 or later.

Build from the repo root:

```bash
./gradlew :app:assembleDebug
```

Lint:

```bash
./gradlew :app:lintDebug
```

Install a debug build:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.alexanderpeppe.pianobeam/.MainActivity
```

## Project Structure

- `app/src/main/java/com/alexanderpeppe/pianobeam/MainActivity.kt`: Jetpack Compose UI, library, connection dialogs, transport, recording, and app info.
- `app/src/main/java/com/alexanderpeppe/pianobeam/service/NoteCastService.kt`: BLE MIDI connection, MIDI playback, media session, notification, recording, reconnect, and diagnostics.
- `app/src/main/java/com/alexanderpeppe/pianobeam/data/`: Library metadata, settings models, and repository.
- `app/src/main/java/com/alexanderpeppe/pianobeam/midi/`: Standard MIDI File parser and writer.
- `app/src/main/java/com/alexanderpeppe/pianobeam/ui/`: Theme and settings UI.
- `app/src/main/res/raw/`: Bundled demo MIDI files.
- `app/src/main/res/drawable-nodpi/`: App and brand image assets.

## Languages

APS NoteCast declares Android locale support for the APS MIDI Prep Tool language set requested for this app, including Bulgarian:

- English
- Spanish
- French
- German
- Italian
- Portuguese (Brazil)
- Dutch
- Polish
- Japanese
- Korean
- Chinese (Simplified)
- Bulgarian

The Android resource scaffolding is present so app metadata and resource-backed strings can localize by system language. User-facing Compose strings should continue moving into string resources as the UI stabilizes.

## Legal And Policies

APS NoteCast is licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

Alex's Piano Service LLC website policies:

- [Disclaimer](https://www.alexanderpeppe.com/disclaimer/)
- [Privacy Policy](https://www.alexanderpeppe.com/privacy-policy/)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development workflow, test-copy guidance, code style, and contribution licensing.

## Security

See [SECURITY.md](SECURITY.md) for reporting recommendations and security-minded usage notes.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for release notes.

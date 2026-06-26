# APS NoteCast

APS NoteCast is an Android app for playing Standard MIDI Files over Bluetooth LE MIDI or Android USB MIDI to compatible MIDI adapters connected to player-piano systems.

The primary wireless path is:

`Android phone -> Bluetooth LE MIDI -> WIDI / compatible adapter -> DIN MIDI -> player piano`

USB MIDI adapters exposed through Android's MIDI API can also appear in the device list and be connected directly.

APS NoteCast is developed by Alex's Piano Service LLC for practical player-piano service workflows: importing MIDI files, organizing playlists, connecting to MIDI hardware, recording incoming MIDI, and keeping playback controls accessible from both the app and Android media controls.

APS NoteCast is free and open-source. It has no subscriptions, in-app purchases,
advertising, or paid music catalog. It is independent and is not affiliated with
Yamaha, PianoStream, PianoDisc, QRS, Steinway, or Spirio.

## Highlights

- First-run connection wizard for MIDI devices.
- BLE MIDI connection guidance that supports both direct app scanning and Android Bluetooth pairing when Android requests it.
- Bluetooth-off notices with a direct request to turn Bluetooth on.
- BLE and Android MIDI device scan results in one connection flow.
- USB MIDI adapters exposed by Android appear in the same device list.
- Preferred-device memory and auto-reconnect while the app is open.
- Real-time connection monitoring that marks the device disconnected when Android MIDI removal, Bluetooth state, or MIDI heartbeat checks indicate the adapter is gone.
- Timed reconnect attempts after unexpected connection loss.
- Local MIDI library with bundled Mutopia Project public-domain demo files.
- Sample playlist containing the bundled Mutopia demo pieces.
- Collapsible playlists with add-file multi-select sheets, drag/drop support, playlist cloning, rename, delete, and reordering.
- Single-file and playlist playback, including sequential, shuffle, repeat-one, repeat-playlist, stop-after-current, and stop-after-playlist behaviors.
- Playback transport with play/pause, stop, previous, next, visible progress, seek, volume, and second-tap Stop cleanup.
- Android foreground media playback service with lock-screen/media controls and seek support.
- Second-tap Stop cleanup that sends sustain off, sostenuto off, soft pedal off, all sound off, reset controllers, and all notes off on all 16 MIDI channels.
- Tempo and transpose controls, including an option to leave channel 10 drums untransposed.
- Advanced per-channel mute, solo, and volume controls.
- Piano-channel routing options for two-channel piano files and pedal-controller channels.
- BLE MIDI recording to Standard MIDI File with count-in, discard confirmation, silence trimming, and optional save-to-playlist.
- MIDI file export/share and library backup/restore.
- User-initiated import from local files, recordings, and clearly labeled external MIDI sources.
- Light, dark, and system-default appearance modes.

## Hardware Notes

APS NoteCast is designed around Android's MIDI API. For Bluetooth devices, it uses the standard BLE MIDI service UUID:

`03B80E5A-EDE8-4B33-A751-6CE34EC4C700`

Most WIDI and compatible BLE MIDI adapters can be connected from APS NoteCast: scan, connect, then play. If Android asks to pair, pairing is okay; paired adapters remain visible and connectable in APS NoteCast. USB MIDI adapters that Android exposes through the MIDI API can appear in the same connection list.

For reliable testing:

1. Power the WIDI or compatible BLE MIDI adapter.
2. Open APS NoteCast.
3. Grant Bluetooth/Nearby Devices permissions.
4. Scan from APS NoteCast.
5. Connect to the MIDI device if it is available.
6. If Android displays a Bluetooth pairing request, pair the adapter; APS NoteCast will keep using the BLE MIDI connection.
7. Import or select a MIDI file.
8. Test playback at low volume first.
9. Test Stop during playback, then tap Stop again while stopped before leaving a playlist unattended.

## Connection Flow

### First-time connection to a device

1. Power on the WIDI or compatible BLE MIDI adapter.
2. Turn on Android Bluetooth.
3. Open APS NoteCast.
4. Grant Bluetooth/Nearby Devices permissions if prompted.
5. Open MIDI connection.
6. Tap Scan.
7. Tap Connect on the target adapter.
8. If Android displays a Bluetooth pairing request, pair the adapter.
9. If the adapter is not shown, open Android Bluetooth settings and connect or pair it there.
10. Return to APS NoteCast.
11. Tap Scan again.
12. Tap Connect.
13. Confirm APS NoteCast shows the adapter as Connected.
14. Play a short MIDI file at low volume.
15. Tap Stop and confirm all notes and pedals stop.

### Reconnecting later

1. Power on the same adapter.
2. Open APS NoteCast.
3. Wait for auto-reconnect if it is enabled.
4. If it does not reconnect automatically, open MIDI connection.
5. Tap Connect on the saved adapter.
6. If APS NoteCast says the saved adapter is not currently available, tap Scan.
7. Tap Connect when the adapter appears.
8. If it was paired previously, it may appear as Paired Bluetooth and can be connected directly.
9. Play a short MIDI file.
10. Tap Stop and confirm all notes and pedals stop.

### Switching to another saved device

1. Stop playback before changing adapters.
2. Power on the adapter you want to use.
3. Open MIDI connection.
4. Tap Scan if the target adapter is not listed as ready.
5. Tap Connect on the target adapter.
6. Confirm APS NoteCast shows the new adapter as Connected.
7. Play a short MIDI file.
8. Confirm only the intended instrument responds.
9. Tap Stop and confirm all notes and pedals stop.

## Safety

Player pianos are physical instruments. Before using long playlists or unattended playback, confirm that the receiving instrument responds correctly to stop, second-tap Stop cleanup, pedal-off, and all-notes-off messages.

Use copies of MIDI files whenever possible and keep backups of anything important. APS NoteCast can export individual MIDI files and a JSON library backup, but device storage and Android document permissions can still fail or be revoked.

## External MIDI Sources

APS NoteCast is a MIDI player, recorder, and library tool. It can help users
find, preview, and import MIDI files from external sources such as the Kuhmann /
Disklavier World source and the Mutopia Project, but those files are not
presented as an APS NoteCast catalog.

Kuhmann and similar community sources may contain public-domain, open,
community, or mixed-rights material. Mutopia Project files shown by APS NoteCast
are labeled Public Domain / no rights reserved on their source pages. APS
NoteCast does not own those files. Users should treat external-source access as
personal/noncommercial unless the source clearly grants broader rights, and
should download, import, and use only files they have the right to use.

Do not sell, redistribute, remaster, or use external-source files for
paid/commercial playback unless allowed by the rights holder. A public-domain
composition does not necessarily mean a specific MIDI sequence, arrangement, or
performance file is unrestricted.

External-source imports are user initiated. APS NoteCast should not be described
in store listings, screenshots, or release text as providing a built-in catalog
of free songs.

External MIDI sources are modular. The built-in source list is defined in
`app/src/main/assets/external_midi_sources.json`, and custom builds or
installations can add more JSON source definitions without changing the search
UI. See [External MIDI Sources](docs/external-midi-sources.md) for the source
schema, search endpoint contract, and instructions for linking APS NoteCast to
your own hosted MIDI directory files.

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
adb shell am start -n com.alexanderpeppe.notecast/com.alexanderpeppe.pianobeam.MainActivity
```

Build a signed release Android App Bundle for Google Play:

```bash
scripts/build-release-bundle.sh
```

The release signing script reads `APS_NOTECAST_KEYSTORE`, `APS_NOTECAST_KEY_ALIAS`,
and `APS_NOTECAST_KEYSTORE_PASSWORD` from `~/.aps-notecast-signing.env` by default.
Set `APS_NOTECAST_SIGNING_ENV=/path/to/signing.env` to use another local signing
file. The signed bundle is written to:

```text
app/build/outputs/bundle/release/aps-notecast-release-signed.aab
```

In VS Code, run **Build signed release App Bundle** from **Terminal > Run Build
Task**.

## Project Structure

- `app/src/main/java/com/alexanderpeppe/pianobeam/MainActivity.kt`: Jetpack Compose UI, library, connection dialogs, transport, recording, and app info.
- `app/src/main/java/com/alexanderpeppe/pianobeam/service/NoteCastService.kt`: MIDI connection, playback, media session, notification, recording, reconnect, and diagnostics.
- `app/src/main/java/com/alexanderpeppe/pianobeam/data/`: Library metadata, settings models, and repository.
- `app/src/main/java/com/alexanderpeppe/pianobeam/midi/`: Standard MIDI File parser and writer.
- `app/src/main/java/com/alexanderpeppe/pianobeam/ui/`: Theme and settings UI.
- `app/src/main/res/raw/`: Bundled demo MIDI files.
- `app/src/main/res/drawable-nodpi/`: App and brand image assets.

## Languages

APS NoteCast declares Android locale support for the language set requested for this app, including Bulgarian:

- English
- Spanish
- French
- German
- Italian
- Portuguese
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

Third-party dependency and bundled asset provenance is documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

In the app, open About for the current website, disclaimer, DMCA / removal, and privacy policy links.

Alex's Piano Service LLC website policies:

- [Disclaimer](https://www.alexanderpeppe.com/disclaimer/)
- [DMCA / Removal Policy](https://www.alexanderpeppe.com/dmca-policy/)
- [Privacy Policy](https://www.alexanderpeppe.com/privacy-policy/)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development workflow, test-copy guidance, code style, and contribution licensing.

## Security

See [SECURITY.md](SECURITY.md) for reporting recommendations and security-minded usage notes.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for release notes.

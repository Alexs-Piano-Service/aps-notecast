# Changelog

All notable changes to APS NoteCast are documented here.

## Unreleased

## 0.1.5

### Added

- ZIP imports that create playlists from bundled MIDI files.
- Cached Kuhmann searches with automatic Piano Only/Ensemble re-filtering.

### Changed

- Ensemble Kuhmann search results now only show multi-channel ensemble candidates.
- Disklavier playback now routes source channels 0/1 and Acoustic Grand Piano parts to MIDI channel 0.

## 0.1.4

### Added

- BLE MIDI connection monitoring that updates the UI when a device is lost.
- Timed reconnect attempts after unexpected disconnects.
- Reconnect timeout setting.
- Library playlist add-file sheet with multi-select.
- File and playlist rename actions.
- Playlist clone action.
- MIDI file export/share.
- Library backup/restore.
- Playback behavior settings for repeat, stop-after-current, stop-after-playlist, auto-play next, and shuffle default.
- Tempo and transpose controls.
- Optional channel 10 transpose exclusion.
- Advanced per-channel mute, solo, and volume controls.
- Recording settings for silence trim, count-in, metronome count-in, target playlist, and discard confirmation.
- App info branding for Alex's Piano Service LLC, address, and website.
- Apache 2.0 license, notice, contributing guide, security policy, and expanded README.
- Android locale declarations for APS MIDI Prep Tool languages plus Bulgarian.
- App-wide font size setting.

### Changed

- Buttons now use rectangular Material shapes.
- The record action uses a redder visual treatment.
- The connection pill has more balanced visual padding.
- The main screen keeps advanced MIDI controls in settings to stay compact.
- Startup splash styling now uses the NoteCast loading mark instead of the launcher preview.

### Fixed

- Manual disconnect suppresses auto-reconnect, while unexpected disconnects can reconnect.
- Recording count-in cancellation is handled safely.

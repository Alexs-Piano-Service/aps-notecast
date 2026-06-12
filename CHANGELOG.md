# Changelog

All notable changes to APS NoteCast are documented here.

## Unreleased

### Added

- USB MIDI adapters exposed by Android now appear in the adapter list and can be connected like Bluetooth MIDI devices.
- Mutopia Project is now available as a selectable External MIDI source alongside Kuhmann.

### Changed

- Pedal controllers now fold into the detected piano output channel by default, with a Pedal Options switch to preserve source channels.
- Pedal curves now default to stable 0/127 output using the standard MIDI pedal threshold, with continuous pedal output still available in settings.
- Sustain pedal state now restores on resume when pausing in the middle of a sustained passage.
- Kuhmann is now presented as an external noncommercial MIDI source with copyright and DMCA/removal guidance.
- Bundled demo MIDI files now come from Mutopia Project public-domain sources.
- Recommended battery settings now appear as a one-time startup dialog instead of inside the connection wizard.
- Channel 2 to channel 1 piano routing is now a default-on playback setting.
- General MIDI files can now switch between Alphabetical sections and the All Songs list.
- Alphabetical MIDI sections now include an independent expand/collapse-all button beside the Alphabetical view selector.
- External MIDI Piano/Ensemble filtering now treats channel 3 as piano-only only when it is pedal/controller-only, not when it carries instrument notes.

## 0.1.7

### Added

- Pedal output mode for systems that also expect sustain as piano roll note 18.
- Sustain pedal test chord to verify that Pedal Test On holds notes until Pedal Test Off.
- Localized battery/background playback guidance, including Xiaomi/POCO/HyperOS wording.

### Changed

- Pedal-only MIDI channels now route pedal controllers to likely piano output channels.
- Imported MIDI and ZIP names are cleaned up more consistently, including smart quotes and separator text.

### Fixed

- Song completion sends full note, pedal, all-sound-off, reset-controller, and all-notes-off cleanup.

## 0.1.6

### Changed

- Improved playback scheduling to reduce UI-triggered MIDI jitter.
- Mixer instrument changes now apply immediately during playback.

### Fixed

- Pause, stop, and panic stop now send note-off cleanup for active notes.

## 0.1.5

### Added

- ZIP imports that create playlists from bundled MIDI files.
- Cached Kuhmann searches with automatic Piano Only/Ensemble re-filtering.

### Changed

- Ensemble Kuhmann search results now only show multi-channel ensemble candidates.
- Disklavier playback now routes MIDI channels 1/2 and Acoustic Grand Piano parts to MIDI channel 1.

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
- Android locale declarations for the requested app languages plus Bulgarian.
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

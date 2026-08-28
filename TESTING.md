# APS NoteCast field test notes

## Large-library performance regression

Run this matrix when changing library loading, search, sorting, paging, playlists, or repository persistence. Use the same physical device, Android version, build type, display mode, and page size for before/after comparisons. A release or profileable build gives meaningful timing data; a debug build is suitable only for correctness checks.

Use a disposable app installation. Create deterministic `library.json` fixtures containing 1,000, 10,000, and 50,000 files. Spread titles across A-Z and `#`, give 10 percent of the files a shared search term, give one file a unique term, and include both a large playlist and several small playlists. Set the fixture's name-normalization version to the current repository version so a migration does not distort normal startup measurements. Metadata-only entries are sufficient for list tests, but do not play or export them because their MIDI files do not exist.

For a debug build, a fixture can be installed without exercising the importer:

```bash
adb shell am force-stop com.alexanderpeppe.notecast
adb push /path/to/library.json /data/local/tmp/notecast-library.json
adb shell run-as com.alexanderpeppe.notecast cp /data/local/tmp/notecast-library.json files/library.json
```

For each fixture, run every scenario at least five times and report the median. Record `TotalTime` for a process-cold launch:

```bash
adb shell am force-stop com.alexanderpeppe.notecast
adb shell am start -W -n com.alexanderpeppe.notecast/com.alexanderpeppe.pianobeam.MainActivity
```

Reset frame statistics immediately before the scrolling and paging scenarios, then inspect them afterward. Capture an Android Studio or Perfetto system trace for any run with a long or frozen frame.

```bash
adb shell dumpsys gfxinfo com.alexanderpeppe.notecast reset
# Perform the scenario on the device.
adb shell dumpsys gfxinfo com.alexanderpeppe.notecast
adb shell dumpsys meminfo com.alexanderpeppe.notecast
```

Exercise the following scenarios at every fixture size:

1. Launch after a force-stop, then launch twice more with unchanged metadata to cover both first-read and cached-read behavior.
2. Scroll continuously from the first file through at least ten screens in list and alphabetical modes while playback progress is updating.
3. With page sizes 50 and 500, visit the first, middle, and final pages. Verify that boundaries contain no duplicates or omissions and that the final partial page is correct.
4. Search for the common term, the unique term, and a term with no matches, then clear the query quickly. Verify that older background results never replace the newest query.
5. Expand the large playlist, scroll through its tracks, collapse it, and expand it again. Also search playlists by playlist name and track title.
6. Open Add files to playlist, search, select and deselect files, and confirm the selection. Check that scrolling remains responsive as the selected set grows.
7. Rename and delete a file, add and remove playlist tracks, and batch-import several files. Relaunch after each operation and verify that the persisted catalog matches the UI.

A run passes when catalog order and page boundaries are correct, search never presents stale results, actions survive relaunch, and no operation produces an ANR or multi-second UI freeze. For performance comparisons, treat a greater than 10 percent regression in median launch time, peak memory, or reported janky-frame rate on the 1,000-file fixture as a failure. The 10,000- and 50,000-file fixtures should show bounded page-composition cost, no main-thread metadata reads or writes in the trace, and a clear improvement over the pre-change baseline. Record device and build details with the measurements rather than comparing numbers from different devices.

## Recommended first test file

Use a short, simple piano MIDI file before testing dense or expressive files. A C-major scale MIDI file is ideal because it makes stuck notes, missing note-off events, and timing problems obvious.

## MIDI connection integrity test

Use this sequence when validating a new build against one or more real WIDI, compatible BLE MIDI, or Android USB MIDI adapters. Normal users do not need to forget devices before every use; the forget/remove steps are only for clean-slate testing.

### Setup

1. Install the latest available APS NoteCast build on the Android device.
2. Put the phone near the adapters and the receiving instrument.
3. Close other MIDI or Bluetooth MIDI apps that might hold the adapter connection.
4. Turn Bluetooth on.
5. Open APS NoteCast and grant Bluetooth/Nearby Devices permissions.
6. Select a short, simple MIDI file and keep the receiving instrument at a safe volume.

### Clean-slate device state

1. Stop any active playback in APS NoteCast.
2. In Android Bluetooth settings, open each test adapter and choose Forget or Unpair.
3. In APS NoteCast, open the MIDI connection panel.
4. Remove each saved MIDI adapter from APS NoteCast.
5. Power-cycle each adapter.

### First adapter

1. Open the MIDI connection panel.
2. Tap Scan.
3. If the target adapter appears with Connect enabled, tap Connect.
4. If the target adapter does not appear, or appears but cannot connect, open Android Bluetooth settings.
5. **Pair the adapter there only if Android requires it, return to APS NoteCast, then tap Scan again.**
6. Tap Connect on the target adapter.
7. Confirm APS NoteCast shows the adapter as Connected.
8. Play the short test MIDI file.
9. Confirm music reaches the receiving instrument.
10. Tap Stop before the file ends.
11. Confirm all notes and pedals stop.
12. Tap Stop again if any note or pedal remains active.

### Second adapter and switching

1. Power on the second adapter and keep the first adapter powered on.
2. Open the MIDI connection panel.
3. Tap Scan.
4. Connect to the second adapter, using Android Bluetooth settings only if Scan cannot produce a connectable device.
5. Confirm APS NoteCast shows the second adapter as Connected.
6. Play the short test MIDI file.
7. Confirm only the intended receiving instrument responds.
8. Tap Stop and confirm all notes and pedals stop.
9. Reconnect to the first adapter from the MIDI connection panel.
10. Keep both adapters powered on and confirm the quick reconnect action connects to the adapter named in its panel, not the other saved adapter.
11. Play the short test MIDI file again.
12. Tap Stop and confirm all notes and pedals stop.

### Reconnect integrity

1. With one adapter connected, tap Disconnect.
2. Confirm playback controls no longer send music until a device is connected.
3. Tap Connect on the saved adapter, or tap Scan if APS NoteCast says the saved adapter is not currently available.
4. Confirm the adapter reconnects without opening Android Bluetooth settings.
5. Play the short test MIDI file.
6. Tap Stop and confirm all notes and pedals stop.
7. Force a lost connection by powering off the connected adapter.
8. Confirm APS NoteCast marks the device disconnected or reports connection loss.
9. Power the adapter back on.
10. Confirm APS NoteCast reconnects automatically if auto-reconnect is enabled, or reconnects after Scan/Connect if manual reconnect is required.
11. Play and stop the short test MIDI file one final time.

### Pass criteria

1. Scan finds powered, nearby adapters after permissions are granted.
2. **Android pairing is only required when Android will not expose or open the adapter without pairing.**
3. Connect, disconnect, reconnect, and device switching leave APS NoteCast showing the same device that actually receives MIDI.
4. Music never continues after Stop.
5. Tapping Stop again clears any stuck notes or pedals.
6. Switching adapters does not leave the previous adapter receiving new playback.
7. Unexpected adapter power loss is reflected in the app instead of leaving a stale Connected state.

## Pedal regression test

Use at least one expressive player-piano roll and one dense classical MIDI file with pedal data. Good regression candidates are the Piano Man track that previously showed inconsistent pedal engagement and a Rachmaninoff etude with pedal events before the first note.

1. In Settings, leave Stable pedal values enabled.
2. Leave Fold pedals into piano channel enabled.
3. Select the acoustic piano's actual MIDI input channel, then leave Fold channel 2 into the piano input enabled unless testing an unusual receiver.
4. Connect the WIDI, BLE MIDI adapter, or USB MIDI adapter that feeds the receiving instrument.
5. Play the first test file and watch the sustain pedal throughout the first minute.
6. Confirm pedal motion changes at musical points instead of rapidly fluttering or staying down through unrelated passages.
7. Pause during a sustained section and confirm sustain releases while paused.
8. Resume and confirm sustain returns if the playback position is still inside that sustained section.
9. Pause when sustain is not active and confirm it stays released.
10. Stop during a sustained section and confirm sustain releases and does not return.
11. Tap Stop again and confirm sustain, sostenuto, and soft pedal are released.
12. Repeat with the second test file.
13. Disable Fold pedals into piano channel and repeat if validating source-channel preservation.
14. Disable Stable pedal values only when testing a receiver known to handle continuous pedal curves correctly.

### Pedal pass criteria

1. Sustain output is binary 0/127 by default.
2. Pause temporarily releases sustain and resume restores it only when the current playback position calls for sustain.
3. Stop, second-tap Stop cleanup, seek, skip, and song completion send pedal-off cleanup.
4. Pedal-only channels can drive the selected acoustic piano input channel.
5. Channel 3 is treated as piano-only only when it carries pedal/controller data without instrument note events.

## MIDI channel routing regression test

Use a MIDI monitor or multitimbral receiver so source and output channels, program changes, CC7 volume, and pedal messages can be observed independently. Use one short file with notes on at least channels 1, 2, 3, and 10; include distinct programs, CC7 values, and sustain changes. Also keep a file with a sustained passage for the paused-routing checks.

1. Set Acoustic piano input channel to a value other than 1 and verify the setting commits once when an option is selected, not while browsing the list.
2. Start playback and open the mixer. Confirm every row shows `Source Ch X → Output Ch Y` and that Automatic reports the output it would use after clearing an override.
3. Assign source channel 1 to an unused output channel. Confirm subsequent notes, note-offs, program changes, CC7, and pedals use the selected output while mute, solo, and volume remain associated with source channel 1.
4. Open the output picker and choose an output marked as used by another source. Confirm the help and option label warn that both sources now share program, controller, pedal, pitch-bend, and Standard MIDI volume state; route back to an unused output before continuing independent-state checks.
5. Change that assignment to a second output during active notes. Confirm the old output receives cleanup, the new output receives setup, and neither output has a stuck note or pedal.
6. Select Automatic and confirm the source returns to the configured piano-routing behavior. Set an explicit identity assignment and confirm it can override automatic piano routing.
7. Assign another source to a different output, close and reopen the mixer, restart the song, and relaunch the app. Confirm both per-song assignments persist and do not affect a different song.
8. Use Clear song routing and confirm every row returns to Automatic without clearing instrument overrides. Separately clear song instruments and confirm channel assignments remain.
9. Change an instrument override on a reassigned source during playback. Confirm cleanup/setup occurs once and the selected program is sent on the effective output channel.
10. Select each acoustic piano input channel needed by the test receiver. Run the chromatic-scale and pedal diagnostics and confirm notes and pedal messages use that selected channel rather than channel 1.
11. Select Standard MIDI volume, then enable Merge all instruments to the piano input. Confirm all 16 possible source channels, including channel 10, route to the selected piano input and the receiver remains on Acoustic Grand Piano. Confirm instrument/output pickers and per-row volume sliders are disabled, per-source mute remains enabled, and Main Volume changes the shared level.
12. While merge-all playback is active, exercise source program changes, bank selects, CC7, sustain, pitch bend, and all-notes-off events. Confirm no source program replaces Acoustic Grand Piano, cleanup does not strand notes or sustain, and the UI accurately treats channel-wide controller state as shared rather than independently adjustable per source.
13. Disable merge-all during playback and confirm source routing/program behavior is restored without restarting the song and without stuck notes.
14. Pause inside a sustained passage, change the acoustic piano input channel or a source assignment, and wait briefly. Confirm sustain remains released while paused.
15. Resume and confirm sustain is restored only on the new effective output when the playhead is inside a sustained section. Repeat while not inside a sustained section and confirm no sustain-on is synthesized.
16. Seek backward and forward after a routing change, then skip to another song. Confirm program, CC7, and pedal state are correct at the destination and cleanup reaches both old and new outputs.

### Routing pass criteria

1. The canonical route shown in the mixer matches every emitted channel message class.
2. A live settings change produces one cleanup/setup transition, with note-off and pedal-off on the old destination before setup on the new destination.
3. Per-song assignments persist independently and Automatic/identity assignments have distinct behavior.
4. Merge-all keeps one Acoustic Grand Piano destination, clearly identifies channel-wide controller and Standard MIDI volume state as shared, and uses Main Volume for the shared level.
5. Pause, resume, seek, skip, stop, and second-tap Stop never leave notes or pedals active on an old or new destination.

## Test sequence

1. Confirm WIDI appears in scan results.
2. Connect to WIDI.
3. Play a simple file.
4. Stop mid-file and confirm all notes stop.
5. Tap Stop again and confirm no notes or pedals remain active.
6. Pause and resume mid-file, then confirm no notes hang.
7. Play the same file three times in a row.
8. Create a three-song playlist.
9. Drag a MIDI file onto the playlist folder and confirm it appears in the playlist.
10. Play the playlist sequentially, then with Shuffle.
11. Use Skip Next and Skip Previous during playlist playback.
12. Record a short phrase from BLE MIDI input and confirm the saved file appears in the library.
13. Let the playlist finish without touching the phone.
14. Turn the phone screen off during playback and confirm foreground playback continues.
15. Move the phone farther away and determine a practical distance limit.

## Signs of trouble

- Notes continue after Stop: inspect whether MIDI input is actually routed to the piano and whether second-tap Stop cleanup messages are being received.
- Device does not appear: power-cycle WIDI, verify the app has Nearby Devices permission, and make sure another app is not already holding the WIDI connection.
- Timing feels uneven: test with the phone near the adapter, disable battery restrictions, and try a less dense MIDI file.
- Playback stops when screen turns off: verify the foreground notification appears and Android battery optimization is disabled for the app.

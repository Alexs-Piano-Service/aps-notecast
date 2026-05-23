# APS NoteCast field test notes

## Recommended first test file

Use a short, simple piano MIDI file before testing dense or expressive files. A C-major scale MIDI file is ideal because it makes stuck notes, missing note-off events, and timing problems obvious.

## BLE MIDI connection integrity test

Use this sequence when validating a new build against one or more real WIDI or compatible BLE MIDI adapters. Normal users do not need to forget devices before every use; the forget/remove steps are only for clean-slate testing.

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
3. In APS NoteCast, open the BLE MIDI connection panel.
4. Remove each saved MIDI adapter from APS NoteCast.
5. Power-cycle each adapter.

### First adapter

1. Open the BLE MIDI connection panel.
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
12. Tap Panic if any note or pedal remains active.

### Second adapter and switching

1. Power on the second adapter and keep the first adapter powered on.
2. Open the BLE MIDI connection panel.
3. Tap Scan.
4. Connect to the second adapter, using Android Bluetooth settings only if Scan cannot produce a connectable device.
5. Confirm APS NoteCast shows the second adapter as Connected.
6. Play the short test MIDI file.
7. Confirm only the intended receiving instrument responds.
8. Tap Stop and confirm all notes and pedals stop.
9. Reconnect to the first adapter from the BLE MIDI connection panel.
10. Play the short test MIDI file again.
11. Tap Stop and confirm all notes and pedals stop.

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
5. Panic clears any stuck notes or pedals.
6. Switching adapters does not leave the previous adapter receiving new playback.
7. Unexpected adapter power loss is reflected in the app instead of leaving a stale Connected state.

## Test sequence

1. Confirm WIDI appears in scan results.
2. Connect to WIDI.
3. Play a simple file.
4. Stop mid-file and confirm all notes stop.
5. Use Panic and confirm no notes or pedals remain active.
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

- Notes continue after Stop: inspect whether MIDI input is actually routed to the piano and whether panic messages are being received.
- Device does not appear: power-cycle WIDI, verify the app has Nearby Devices permission, and make sure another app is not already holding the WIDI connection.
- Timing feels uneven: test with the phone near the adapter, disable battery restrictions, and try a less dense MIDI file.
- Playback stops when screen turns off: verify the foreground notification appears and Android battery optimization is disabled for the app.

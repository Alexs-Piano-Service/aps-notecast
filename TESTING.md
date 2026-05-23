# PianoBeam field test notes

## Recommended first test file

Use a short, simple piano MIDI file before testing dense or expressive files. A C-major scale MIDI file is ideal because it makes stuck notes, missing note-off events, and timing problems obvious.

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

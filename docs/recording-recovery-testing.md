# Recording recovery and MIDI parser regression checks

Run `./gradlew testDebugUnitTest assembleDebug lintDebug` for the automated checks.
`NoteCastRecordingTest` injects an `IOException` at the repository write boundary by
replacing its isolated test MIDI directory with a file. Repairing that directory and
retrying must save the original MIDI bytes without recapturing any notes.

Recordings now have active, saving, and pending states. A pending recording blocks a
new take until it is saved or explicitly discarded. Export Copy writes an independent
MIDI file and keeps the original pending. Recovery runs independently of the library,
so unavailable library metadata does not prevent recovering or exporting a take.

The service writes an atomic checkpoint to its private, non-backed-up storage once
per second while capturing, and checkpoints again on finish or disconnect/destruction.
An abrupt process kill can lose events since the last completed checkpoint. A failed
checkpoint preserves the previous one and reports the failure while capture continues
in memory. A persistent recording ID prevents a retry from duplicating a song if the
library commit completed before recovery cleanup was interrupted.

## On-device checks

1. Connect a MIDI device, start recording with playback stopped, and play a few notes.
   Swipe NoteCast from Recents, continue playing, then reopen it. Recording should
   remain active with a foreground notification; save and verify both sets of notes.
2. Repeat during count-in and with playback running alongside recording. Stopping
   playback must not remove the recording notification or stop capture.
3. Disconnect the MIDI device during recording. Reconnect or stay disconnected:
   the captured take must offer Retry Save, Export Copy, and Discard. Export must
   work without reconnecting the device. Canceling the destination picker must leave
   the take intact.
4. Record for several seconds, then terminate the process in a debug environment
   without relying on `onDestroy`. Reopen the app. The latest checkpoint must appear
   as a pending take with its title and event count. Save it, reopen again, and verify
   that it does not appear a second time.
5. Discard a pending take, reopen the app, and confirm that it is gone and a new
   recording can start. Do not fill device storage to simulate a save failure; use
   the injected unit-test failure above.

`MidiFileParserTest` builds equivalent MIDI fixtures in memory. Extension chunks
before and between music tracks must preserve event bytes, timestamps, and duration.
Missing declared tracks, truncated chunks, overflowing lengths, and track events
that cross chunk boundaries must fail. Type 2 files must explain that independent
patterns are unsupported and suggest exporting Type 0 or Type 1.

# Connecting MIDI hardware

APS NoteCast can connect to compatible Bluetooth LE MIDI adapters and USB MIDI devices exposed through Android's MIDI API. Both appear in the same in-app device picker.

## Before you start

- Use an Android device running Android 8.0 or newer.
- Power the MIDI adapter and connect it to the intended receiver or player piano.
- Close other MIDI apps that may already hold the adapter connection.
- Start with the receiving instrument at a low volume.

For Bluetooth devices, APS NoteCast looks for the standard BLE MIDI service UUID:

```text
03B80E5A-EDE8-4B33-A751-6CE34EC4C700
```

## Connect for the first time

1. Turn on Android Bluetooth.
2. Open APS NoteCast and grant all requested permissions. Depending on the Android version, these include Bluetooth or Nearby Devices, Location, and notifications.
3. Open **MIDI adapter**.
4. Tap **Find MIDI adapters**. Use **Scan again** inside the device list if needed.
5. Choose the target adapter and tap **Connect**.
6. If Android requests pairing, accept it. If the adapter is not available in NoteCast, pair it in Android Bluetooth settings, return to NoteCast, and scan again.
7. Confirm the connection pill shows the expected device.
8. Play a short MIDI file at low volume.
9. Tap **Stop**, then tap it a second time to confirm every note and pedal releases.

An attached USB MIDI device can appear without a Bluetooth scan. Choose it from the **Attached USB MIDI** section and connect directly.

## Reconnect later

APS NoteCast remembers saved or preferred devices and can attempt to reconnect while the app is open.

1. Power on the previously used adapter.
2. Open APS NoteCast and wait briefly for auto-reconnect if it is enabled.
3. If needed, open **MIDI adapter** and choose **Connect** on the saved device.
4. Scan again if Android does not currently report the adapter as available.

Unexpected connection loss is reflected in the UI. Timed reconnect attempts can resume when the adapter becomes available again.

## Switch adapters

1. Stop playback before changing hardware.
2. Power on the new adapter.
3. Open **MIDI adapter** and scan if the device is not already listed.
4. Connect to the intended adapter and confirm its name in the connection pill.
5. Play a short test file and verify that only the intended instrument responds.
6. Stop playback and confirm notes and pedals release.

## Background playback

APS NoteCast uses an Android foreground media service so playback controls can remain available from the notification and lock screen. For reliable background Bluetooth MIDI playback, allow background activity and set the app's battery usage to **Unrestricted**. Manufacturer-specific battery savers may need to be disabled separately.

## Troubleshooting

**The adapter does not appear**

- Confirm it is powered, nearby, and not connected to another app.
- Verify Bluetooth or Nearby Devices and Location permissions are enabled for APS NoteCast. On Android 8–11, also confirm that system Location is turned on for BLE discovery.
- Power-cycle the adapter and scan again.
- If Android requires pairing, pair in system Bluetooth settings and return to NoteCast.

**The UI shows a stale connection**

- Open the device picker and disconnect, then reconnect.
- Power-cycle the adapter.
- Confirm that the device named in the connection pill is the receiver you are testing.

**Notes or pedals remain active**

- Tap **Stop** a second time to send full cleanup across all 16 MIDI channels.
- Confirm the receiving instrument uses the expected piano and pedal channels.
- Test with a short, simple MIDI file before trying dense or expressive material.

For the complete hardware regression checklist, see [TESTING.md](../TESTING.md).

# Security Policy

## Reporting

For security-sensitive issues, suspicious behavior, privacy concerns, or false-positive antivirus reports, contact Alex's Piano Service LLC through:

- Website: https://www.alexanderpeppe.com
- Privacy Policy: https://www.alexanderpeppe.com/privacy-policy/

Avoid posting private device identifiers, customer data, or exploit details in public issue threads.

## Scope

Security-relevant areas include:

- Android Bluetooth and Nearby Devices permissions.
- Android document picker import/export flows.
- Library backup/restore JSON handling.
- Shared MIDI file URIs.
- Foreground media playback service behavior.
- BLE MIDI reconnect and disconnect handling.

## Recommendations For Users

- Install builds only from trusted sources.
- Keep Android security updates current.
- Pair only with MIDI devices you recognize.
- Use test copies of MIDI files and keep backups.
- Review exported library backups before sharing; they include embedded MIDI file data.
- Do not share bug reports containing private customer files, proprietary MIDI libraries, or device addresses unless necessary and intentionally redacted.

## Project Practices

- APS NoteCast uses Android system pickers for import/export instead of broad file-system access.
- Shared MIDI files are exposed through Android `FileProvider` read grants.
- The app does not require account login.
- The app should not collect analytics or transmit library contents.
- Disconnect and reconnect behavior should favor clear user notice over silent failure.

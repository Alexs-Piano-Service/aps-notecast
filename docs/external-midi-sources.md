# External MIDI Sources

APS NoteCast external MIDI search is source-driven. The app is not locked to
Kuhmann, Mutopia, or any single archive. A source definition tells the app which
search endpoint to call, how to label the source, and which download hosts are
allowed for MIDI files returned by that endpoint.

## Where Source Files Load From

Sources are loaded at service startup in this order:

1. `app/src/main/assets/external_midi_sources.json`
2. Any bundled JSON files in `app/src/main/assets/external_midi_sources/*.json`
3. Any installed app-local JSON files in `external_midi_sources/*.json`

Later files with the same `key` replace earlier definitions. Use this to
override a bundled source without changing UI code. New keys are appended to the
source picker.

The app-local directory is created under both the internal app files directory
and the Android app-specific external files directory when the service starts.
For the published application ID, the external app-specific path is:

```text
/sdcard/Android/data/com.alexanderpeppe.notecast/files/external_midi_sources/
```

Restart APS NoteCast after adding or changing local source JSON files.

## Add A Source To A Build

For a custom build, either edit `app/src/main/assets/external_midi_sources.json`
or add a separate file:

```bash
mkdir -p app/src/main/assets/external_midi_sources
cp my-source.json app/src/main/assets/external_midi_sources/my-source.json
./gradlew :app:assembleDebug
```

Separate files are usually easier to review and keep the default sources
unchanged.

## Add A Source To An Installed App

After opening APS NoteCast once, push a source file into the app-specific
external directory and restart the app:

```bash
adb shell mkdir -p /sdcard/Android/data/com.alexanderpeppe.notecast/files/external_midi_sources
adb push my-source.json /sdcard/Android/data/com.alexanderpeppe.notecast/files/external_midi_sources/my-source.json
```

This is intended for testing, custom deployments, and source maintainers. A
normal end-user flow should still import MIDI files through Android's document
picker unless the app later adds an in-app source editor.

## Source Definition Schema

A file can contain a single source object, an array of source objects, or an
object with a `sources` array.

```json
{
  "key": "my-archive",
  "displayName": "My Archive",
  "dialogSubtitle": "My Archive - check source rights",
  "rightsSummary": "Use only files you have rights to use.",
  "initialMessage": "Search My Archive MIDI files.",
  "searchMessage": "Searching My Archive...",
  "resultLabel": "My Archive MIDI file",
  "searchUrl": "https://example.com/notecast/search",
  "foundMessageSuffix": "Check source rights before importing.",
  "allowedDownloadHosts": [
    "example.com",
    "cdn.example.com"
  ],
  "supportsFormatFilter": false,
  "supportsChannelFilter": false,
  "useInstrumentPianoFilter": false
}
```

Required fields:

- `key`: Stable lowercase identifier. Non-alphanumeric characters are normalized
  to dashes.
- `searchUrl`: HTTPS JSON search endpoint.

Recommended fields:

- `displayName`, `dialogSubtitle`, `rightsSummary`, `initialMessage`,
  `searchMessage`, `resultLabel`, and `foundMessageSuffix` control source-picker
  and status text.
- `allowedDownloadHosts` limits which hosts the app may download MIDI files
  from. If omitted, the host from `searchUrl` is used.

Optional behavior flags:

- `supportsFormatFilter`: Send `format=<midi format>` when the UI provides one.
- `supportsChannelFilter`: Send `channel=<1-16>` when the UI provides one.
- `useInstrumentPianoFilter`: Treat non-piano `instrument` labels from search
  results as ensemble files.

## Search Endpoint Contract

APS NoteCast calls `searchUrl` with query parameters:

- `q`: Search text.
- `limit`: Maximum result count requested by the app.
- `format`: Only sent when `supportsFormatFilter` is true.
- `channel`: Only sent when `supportsChannelFilter` is true.

The endpoint should return JSON:

```json
{
  "ok": true,
  "count": 1,
  "results": [
    {
      "id": 1001,
      "title": "Example Piece",
      "filename": "example-piece.mid",
      "url": "https://example.com/midi/example-piece.mid",
      "folder": "Classical/Example",
      "midi_type": "midi",
      "midi_format": 1,
      "channel_count": 2,
      "channels": [1, 2],
      "note_channels": [1, 2],
      "pedal_only_channels": [],
      "file_size": 24576,
      "sha256": "optional-lowercase-sha256",
      "source": "My Archive",
      "composer": "Example Composer",
      "instrument": "piano",
      "style": "Classical",
      "opus": "",
      "license": "Check source page",
      "license_url": "https://example.com/rights",
      "source_url": "https://example.com/pieces/example-piece"
    }
  ]
}
```

Each result must include:

- `id`: Stable integer within that source.
- `url`: HTTPS URL to a MIDI file on an allowed host.
- `title` or `filename`.

Useful optional fields include `channels`, `note_channels`,
`pedal_only_channels`, `midi_format`, `file_size`, `sha256`, `composer`,
`instrument`, `license`, `license_url`, and `source_url`. If `sha256` is
provided, APS NoteCast verifies the downloaded file before import or preview.

## Generate A Directory JSON File

Use `scripts/build-external-midi-directory.py` to scan a hosted MIDI directory
and create a JSON file with one `results` entry per MIDI file. The script is
standalone and only uses Python's standard library.

```bash
scripts/build-external-midi-directory.py /path/to/hosted-midi \
  --base-url https://example.com/midi/ \
  --source-name "My Archive" \
  --license "Check source page" \
  --output my-archive-directory.json
```

The script recursively indexes `.mid`, `.midi`, and `.kar` files. For each file
it emits a stable integer `id`, title, relative path, HTTPS download URL, parent
folder, MIDI format, channel lists, note channels, pedal-only channels, file
size, SHA-256 checksum, track names, duration when possible, and optional source
rights metadata.

The generated file is shaped like the search endpoint response, so an external
database/search tool can ingest it directly and serve filtered `results` for
APS NoteCast. If the file is served directly without a filtering layer, APS
NoteCast will receive the full directory for every search request.

## Linking To Your Own Directory Files

To expose files from your own MIDI directory, put the files behind HTTPS and
make your search endpoint return direct file URLs in `results[].url`. Then set
`allowedDownloadHosts` to the host or CDN serving those files.

The app deliberately does not crawl arbitrary directory listings. Keeping a
small search endpoint between APS NoteCast and the file directory gives source
owners control over metadata, rights labels, removal requests, and which files
are shown.

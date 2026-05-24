# Third-Party Notices

This file records third-party code and bundled content used by APS NoteCast.
It is intended to make release and F-Droid review easier by keeping source,
license, and provenance notes in one place.

## Runtime Libraries

The Android release runtime classpath is composed of AndroidX, Jetpack Compose,
Kotlin, KotlinX, JSpecify, and Guava ListenableFuture artifacts. Gradle POM
metadata for the resolved release runtime classpath reports Apache-2.0-family
licenses for these libraries.

| Component | Purpose | License |
| --- | --- | --- |
| AndroidX and Jetpack Compose | Android app framework, lifecycle, UI, saved state, activity integration, and related support libraries | Apache-2.0 |
| Kotlin standard library and Kotlin Gradle plugins | Kotlin language runtime and build tooling | Apache-2.0 |
| kotlinx.coroutines and transitive kotlinx serialization artifacts | Coroutine runtime and Android coroutine integration | Apache-2.0 |
| JSpecify annotations | Nullness/type-use annotations pulled transitively by AndroidX | Apache-2.0 |
| Guava ListenableFuture | Transitive AndroidX async interface dependency | Apache-2.0 |

## Bundled Image Assets

The APS NoteCast app icon and Alex's Piano Service logo assets are project brand
assets supplied for APS NoteCast.

| Files | Source | License/permission |
| --- | --- | --- |
| `app/src/main/res/drawable-nodpi/logo_*.png` | Alex's Piano Service LLC brand assets | Included with APS NoteCast by Alex's Piano Service LLC |
| `app/src/main/res/mipmap-*/ic_launcher*.png` | APS NoteCast launcher icons derived from project brand assets | Included with APS NoteCast by Alex's Piano Service LLC |

## Bundled Demo MIDI Files

APS NoteCast includes two demo MIDI files so a new installation has immediate
sample content. The underlying Chopin compositions are public-domain works, but
the MIDI files are specific captured or prepared files and their separate reuse
license should be kept explicit for public app-store distribution.

| App resource | Original filename | Embedded/source credit | SHA-256 |
| --- | --- | --- | --- |
| `app/src/main/res/raw/demo_chopin_andante_polonaise.mid` | `Frederic Chopin, Andante Spianato and Grande Polonaise Brillante, Op. 22 (Zuber-06).mid` | Embedded MIDI text: "Andante Spianato, Chopin"; "Yamaha Disklavier Pro Mark IV concert grand piano, model DCFIIISM4PRO"; "AndantePolonaiseEric Zuber" | `afa68c906b1ed2fb34277f1257e9168876fdcd5e324509af82032fdc3842ca79` |
| `app/src/main/res/raw/demo_chopin_etude_op10_no5.mid` | `Frederic Chopin - Etude Op. 10 No. 5 (KimG-04).mid` | Embedded MIDI text/source listing: "2009 Piano-e-Competition audition round"; "captured January 2009"; "http://www.piano-e-competition.com/"; "File processed for distribution by software from Zenph Studios, Inc., http://www.zenph.com"; "Etude Op. 10/5  Grace EunHae Kim" | `d35b134d42584f141c97efed7999a62f1714c6e481499be592d8cb4c9c5c8c42` |

### F-Droid Packaging Note

The software dependencies are free/open-source, but the bundled demo MIDI files
should be reviewed as content assets. Before submitting to the main F-Droid
repository, either confirm and document a libre redistribution license for these
MIDI files, or remove/replace them with demo MIDI files whose source and license
are unambiguous.

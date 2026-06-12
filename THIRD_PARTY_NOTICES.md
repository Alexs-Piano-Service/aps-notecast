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

Demo MIDI files are from the Mutopia Project and are marked Public Domain / no
rights reserved on their source pages. They are included so users can test APS
NoteCast immediately.

| App resource | Work | Source page | Mutopia ID / updated | Source marking | SHA-256 |
| --- | --- | --- | --- | --- | --- |
| `app/src/main/res/raw/demo_beethoven_fur_elise.mid` | `Fur Elise, WoO 59` by L. V. Beethoven | https://www.mutopiaproject.org/cgibin/piece-info.cgi?id=931 | `Mutopia-2015/08/18-931`, updated 2015/Aug/18 | Public Domain / no rights reserved | `1c12c21c7bbf4cf163896732672648a69d497636059837abd153c71abe50215a` |
| `app/src/main/res/raw/demo_bach_wtc1_prelude1.mid` | `Das Wohltemperierte Clavier I, Praeludium I, BWV 846` by J. S. Bach | https://www.mutopiaproject.org/cgibin/piece-info.cgi?id=5 | `Mutopia-2011/09/12-5`, updated 2011/Sep/12 | Public Domain / no rights reserved | `874e07d0479542971bfceaf420d6117da8d602d89d26eea4610e7dd1ef58bf26` |

## External MIDI Sources

APS NoteCast can search, preview, and import files from external MIDI sources
such as the Kuhmann MIDI directory and the Mutopia Project. Those files are not
bundled in the APK/AAB unless they are explicitly listed above as bundled demo
files, and should not be treated as an APS NoteCast-owned or APS NoteCast-licensed
catalog.

Rights and permissions may vary by file and source. External-source access
should be treated as personal/noncommercial unless the source clearly grants
broader rights. Users should download, import, and use only files they have the
right to use. Do not sell, redistribute, remaster, or use external-source files
for paid/commercial playback unless allowed by the rights holder.

Mutopia Project files shown by APS NoteCast are labeled Public Domain / no
rights reserved on their source pages. Kuhmann and similar community archives may
contain public-domain, open, community, or mixed-rights material.

Copyright concerns or removal requests can be sent through the published DMCA /
removal policy:

https://www.alexanderpeppe.com/dmca-policy/

### F-Droid Packaging Note

The software dependencies are free/open-source. The bundled demo MIDI files are
content assets from the Mutopia Project and are marked Public Domain / no rights
reserved on their source pages. Keep the source URLs and SHA-256 values above
current for review.

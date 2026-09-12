# Screenshot showcase source

The images in `docs/screenshots/` are deterministic product-showcase renders based on APS NoteCast's real Compose layouts, labels, demo titles, colors, and supported states. They are designed for documentation and are not presented as raw device captures.

## Render the set

From the repository root, run:

```bash
docs/screenshots/source/render-screenshots.sh
```

The renderer requires headless Chromium and ImageMagick. It produces five `720 × 1560` feature images and one `1600 × 900` README hero in WebP format.

The source deliberately uses only bundled Mutopia demo titles and non-identifying hardware labels such as WIDI Core, USB MIDI Interface, and BLE MIDI Controller. Keep personal libraries, device addresses, notifications, and third-party catalog claims out of future renders.

## Backdrop

`gallery-backdrop.png` was created with OpenAI's built-in image-generation tool from this production prompt:

```text
Use case: ads-marketing
Asset type: subtle reusable background for an open-source Android app screenshot gallery
Primary request: create a sophisticated, understated abstract backdrop inspired by the geometry of piano keys and the flow of MIDI signals; it must support crisp app screenshots placed over it later
Scene/backdrop: deep midnight navy field with softly lit piano-key geometry fading into an atmospheric blue-to-teal gradient and a very faint flowing signal ribbon
Style/medium: premium editorial product backdrop, photoreal materials with restrained abstract treatment, subtle fine grain
Composition/framing: wide landscape composition, calm center and generous negative space, no focal object, usable at multiple crops
Lighting/mood: soft studio edge light, confident, calm, technical, musical
Color palette: midnight navy, APS NoteCast blue, restrained teal, tiny hints of pale cyan
Constraints: no app UI, no phones, no logos, no people, no legible notation; background only
Avoid: text, letters, numbers, watermark, neon cyberpunk, busy patterns, high contrast hotspots, stock-photo look
```

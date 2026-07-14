# Generating sprite art with an image model

OpenGGF's art converters are exact-match, not perceptual: every pixel in a source
PNG must land on one of a sheet's declared 16 colors, and every one of those 16
colors must itself be exactly representable on real Genesis hardware (3 bits per
channel — 8 discrete levels). No general-purpose image model reliably produces that
on the first try. This guide covers the practical loop — prompt, quantize, lay out,
convert, read the warning, iterate — for generating original character/badnik/tile
art with an image-generation model and getting it through `ggfmod convert art`
cleanly. It assumes you've already read
[Standalone platformer build-along](standalone-platformer.md) or
[Flappy remix](flappy-remix.md) for the surrounding project structure; this guide is
scoped to the art step alone.

## 1. Prompting for a converter-ready sprite

Ask for a sheet, not a single image, with the constraints the converter actually
enforces baked into the prompt:

- **Fixed frame size, 8-pixel-aligned.** Decide your piece dimensions up front (e.g.
  Bolt's 24×32 frames in `sample-platformer`) and ask the model to keep every frame
  identically sized and tile-aligned. `ArtConverter` requires every piece's
  `sourceX`/`sourceY` to be a multiple of 8 and its `widthPixels`/`heightPixels` to
  be a positive multiple of 8 — request frames on an 8px grid from the start rather
  than cropping irregular output afterward.
- **Flat colors, no gradients, no anti-aliasing.** Explicitly ask for "flat colors,
  hard pixel edges, no anti-aliasing, no gradients, no dithering, indexed/paletted
  16-bit-console pixel art." Models default to soft shading and anti-aliased edges
  that scatter hundreds of near-duplicate colors across an image — even a good
  prompt won't reliably produce exactly 16 colors, so budget for the quantization
  pass in Chapter 2 regardless of how the prompt is worded.
- **Transparent background.** Request a transparent (alpha) background. It matters
  differently depending on which converter you're feeding: `TmxLevelImporter` (Tiled
  tileset PNGs, `--from-tmx`) requires every pixel's alpha to be *exactly* 0 or 255
  — no partial transparency — and treats alpha-0 pixels as palette index 0.
  `ArtConverter`/`PlayableArtConverter` (object and playable sheets, ordinary
  `convert art [--playable]`) ignore alpha entirely and match RGB only, so for those
  a transparent background is a convenience for your own editing, not something the
  converter checks — reserve index 0 of your declared 16-color palette as the
  background color by hardware convention instead (Genesis CRAM index 0 is always
  the transparent/backdrop slot for a given line).
- **Readable at native resolution.** This is a 16-bit-console engine; sprites render
  1:1, not scaled up. Ask for silhouettes and details that stay legible at the
  actual pixel dimensions you'll ship, not a large hi-res illustration you plan to
  downscale.

## 2. Quantizing to a 16-color Genesis-representable palette

The exact rule (from `ArtConverter.exactChannel` and `TmxLevelImporter.readPalette`,
the same formula both converters use): each channel of every declared `#RRGGBB`
color must round-trip through the Genesis 3-bit-per-channel encoding —
`channel == ((round(channel * 7 / 255) * 255 + 3) / 7)`. That admits **exactly eight
values per channel**: `0, 36, 73, 109, 146, 182, 219, 255`. Any other value —
including almost anything an image model actually painted — is rejected outright.
`ArtConverter` reports it as `"palette color is not exactly Genesis-representable"`;
if a pixel's color simply isn't in your declared 16, you get
`"PNG contains colors outside the declared 16-color line: (12,4)=#3A7F12, ..."`
listing every offending pixel/color up to a sample cap.

**Python (Pillow) recipe** — quantize to 15 colors (reserving index 0 for the
background), then snap every retained color onto the legal 8-level grid:

```python
from PIL import Image

LEVELS = [0, 36, 73, 109, 146, 182, 219, 255]

def snap(value):
    return min(LEVELS, key=lambda level: abs(level - value))

img = Image.open("bolt-source.png").convert("RGBA")
alpha = img.split()[3]

rgb = img.convert("RGB").quantize(colors=15, method=Image.MEDIANCUT)
palette = rgb.getpalette()[:15 * 3]
snapped = [snap(c) for c in palette]
rgb.putpalette(snapped + [0] * (768 - len(snapped)))

out = rgb.convert("RGB")
out.putalpha(alpha)  # keep for TMX tilesets (alpha-exact importer);
                      # ArtConverter/PlayableArtConverter ignore alpha and match RGB only.
out.save("bolt-quantized.png")
print(["#%02X%02X%02X" % tuple(snapped[i:i + 3]) for i in range(0, len(snapped), 3)])
```

**ImageMagick recipe** — do the color-count reduction with ImageMagick, then snap
the resulting (still arbitrary) palette onto the legal 8-level grid:

```sh
magick source.png -alpha off +dither -colors 15 PNG8:reduced.png
magick reduced.png -unique-colors txt:reduced-palette.txt
```

`reduced-palette.txt` lists the up-to-15 retained colors as plain `#RRGGBB` text.
ImageMagick's own color reduction does not guarantee a Genesis-legal result, so run
each of those hex values through the same `snap()` function from the Python recipe
above (or paste them into a one-line Python snippet that imports `LEVELS`/`snap`
and prints the snapped hex) before using them in a sheet YAML — ImageMagick handles
the "reduce to few colors" half well; Python's five-line `snap()` is still the
simplest way to get the *exact* Genesis round-trip right.

In practice the Python recipe above, used end to end, is the more reliable of the
two for getting an exact Genesis-legal palette in one pass. Either way, verify the
final 16 hex values against the `{0,36,73,109,146,182,219,255}`-per-channel set
before pasting them into a sheet YAML.

Paste the printed hex strings into the sheet YAML's `palette:` list, in the same
order the converter expects (index 0 is your background/transparent slot), then
re-run `ggfmod convert art`. If any pixel still fails — usually a stray
anti-aliased edge pixel the quantize pass missed — the converter's error message
gives you the exact coordinates to go fix by hand.

## 3. 8-aligned sheet layout + YAML descriptor

Lay converted frames out on a simple strip at 8px-aligned offsets and describe them
in the sheet YAML — `bolt-sheet.yaml`'s two 24×32 frames stacked vertically at
`sourceY: 0` and `sourceY: 32` is the minimal pattern to copy for a two-frame
character or object:

```yaml
formatVersion: 1
paletteLine: 0
palette: ["#000000", "#242424", "#494949", ... 16 entries total]
frames:
  - delay: 30
    pieces:
      - { sourceX: 0, sourceY: 0, widthPixels: 24, heightPixels: 32, xOffset: -12, yOffset: -16, hFlip: false, vFlip: false, paletteIndex: 0, priority: false }
  - delay: 30
    pieces:
      - { sourceX: 0, sourceY: 32, widthPixels: 24, heightPixels: 32, xOffset: -12, yOffset: -16, hFlip: false, vFlip: false, paletteIndex: 0, priority: false }
```

Each piece's `xOffset`/`yOffset` is its negative half-extent — pieces are
centre-anchored, the same convention `sample-flappy`'s `pipe-sheet.yaml` documents
(see [`flappy-remix.md` Chapter 6](flappy-remix.md#6-pipes-score-death)): a 24×32
piece drawn at `(x, y)` covers `[x-12, x+12) × [y-16, y+16)`, not `(x, y)` as a
top-left corner. `sourceX`/`sourceY` must be multiples of 8, `widthPixels`/
`heightPixels` must be positive multiples of 8, and the whole box must lie inside
the source image — the same 8-alignment rule Chapter 1's prompt already builds
toward, now expressed as the YAML the converter actually reads.

## 4. Reading the converter's VRAM/bank cost warning

`ggfmod convert art --playable ...` always prints:

```text
WARNING generated trivial full-frame DPLC runs; bank cost=<N> patterns
```

Treat it as a VRAM budget, not noise. `PlayableArtConverter` generates one trivial
full-frame DPLC run per frame — it does not currently deduplicate or share 8×8
tiles across frames the way a hand-tuned DPLC layout would — so `bankSize` is
roughly the total distinct 8×8 tile count spanned across every frame in the sheet.
More frames and larger per-frame pixel dimensions cost more VRAM linearly; a
generated sheet with many painted frames can quickly cost far more bank space than
a compact hand-tuned equivalent covering the same visual ground. The engine reserves
separate fixed-size virtual pattern banks for mains, sidekicks, and duplicate
character kinds (see `CLAUDE.md`'s "Virtual Pattern ID System"), and rejects a
capacity overflow before installing any art — so a sheet that's too generous with
frame count/size fails loudly at launch, not silently at render time. Keep AI-generated
sheets to the frame counts you actually need.

## 5. Swapping your art into either sample

Replace the PNG in place — same file name, same frame geometry already declared in
the matching `*-sheet.yaml`, same 16-slot palette order — and rerun `mvn package`.
Both maintained samples wire their `convert art`/`convert art --playable` calls into
Maven's `generate-resources` phase, so a plain rebuild reconverts the swapped PNG
automatically:

- `sample-platformer`: `bolt.png` (playable, via `bolt-sheet.yaml`), or
  `zapbug.png`/`springpad.png`/`ring.png` (object sheets, via their matching
  `*-sheet.yaml`). All three object sheets deliberately share palette line 2 with
  the level's own GPAL (see
  [`standalone-platformer.md` Chapter 3](standalone-platformer.md#3-authoring-the-level-in-tiled))
  — a replacement badnik/gimmick sprite should keep using line 2 unless you also
  regenerate `palette.gpal` to match (it's a small generated binary, not something
  you hand-edit; see that same chapter).
- `sample-flappy`: `pipe.png`, via `pipe-sheet.yaml`.

If your new art needs a different frame count, box size, or palette than the
original, edit the matching `*-sheet.yaml` to match first — the converter never
infers geometry from the image; the PNG and the YAML must agree exactly or
conversion fails with a clear geometry error.

## 6. What stays hand-authored

None of the above replaces these — an image model has no way to decide them, and no
tool infers them automatically:

- **Sheet YAML** (`*-sheet.yaml`): frame/piece geometry, per-frame delay, and
  per-piece `paletteIndex`/offsets. This file is the contract between your art and
  the converter; nothing generates it from a PNG.
- **Palette line assignment**: which of a level's four CRAM lines a given sheet
  targets, and the `(paletteLine + piece.paletteIndex) & 3` arithmetic that follows
  from it. This is a project-wide decision made once per level/GPAL — which objects
  share which line — not a per-sprite-generation choice.
- **TMX collision layers** (`COLLISION`/`COLLISION_ALT` GIDs): terrain solidity is
  gameplay design, not visual content. No image model can decide where the ground is
  actually solid.
- **The GPAL palette file** for a TMX level: a small binary container generated once
  alongside the level and tileset by your own offline authoring process (see
  [`standalone-platformer.md` Chapter 3](standalone-platformer.md#3-authoring-the-level-in-tiled)),
  not something you regenerate per sprite.

See [Standalone platformer build-along](standalone-platformer.md) for the full
project this guide's examples are drawn from, and
[Flappy remix](flappy-remix.md) for the sibling sample's `pipe.png` swap target.

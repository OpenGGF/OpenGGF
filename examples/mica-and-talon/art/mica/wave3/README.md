# MICA wave 3 — partial art handoff

Run from the repository root, with Python 3, Pillow and the pinned disassemblies:

```bash
python3 -I examples/mica-and-talon/art/mica/wave3/build.py --review /tmp/mica-art4-review-20261108
python3 -I examples/mica-and-talon/art/mica/wave3/verify.py
```

The native-coordinate polygon source is original pixel art. It never downsamples
or rotates a generated bitmap. Native art is decoded only into disposable review
files, never into the character outputs. Rebuilding requires the pinned mapping,
DPLC and palette references, even without `--review`; no Java build is needed.

`wave3.yaml` uses JSON-subset YAML. Piece arrays are native x/y offsets in pixels,
tile width/height, tile index, x/y flip, relative palette and priority flags.
Hotspots locate the native object origin inside each mapping's bounding canvas.
The transparent Blue Sphere entry has a 1×1 placeholder, zero pieces/tiles.

The normal PNGs retain the shared MICA palette byte for byte. `-fixed.png` files
use the native special-stage palette line recorded in the manifest. Their explicit
semantic LUTs preserve red boots, white gloves and the scale value ramp. Jade
becomes blue: neither native Sonic line offers green. A character palette binding
is needed for colour-faithful originals. Do not install both palettes on one line.

S2 has 18 frames: 4 upright, 8 diagonal, 4 horizontal, 2 ball. Hurt reuses native
frames with flips. The hurt GIF includes those flips. S3K has 12 mapping entries,
including the blank; its 12-step run and jump sequences reuse them. There is no
separate turn pose bank. The earlier design's 24-frame estimate is corrected by
the labels and tables in the manifest. Blue Sphere GIF timings are illustrative;
the ROM computes phase from velocity. S1 reuses the core playable ROLL set and
needs no new maze drawing. Its five native frames versus four canonical phases
still need W2 integration; the comparison GIF is explicitly not a remapping.

Reviews include 1×/4× native comparisons and GIFs for each recorded sequence,
including both shared-palette and fixed-palette special-stage outputs. Review
repairs repositioned leaning crests, rounded curls and redrew Blue Sphere legs
for narrow lower pieces. Weak points: restrained arm/torso motion, simple ball
scales, and blockier Blue Sphere legs/boots than the core artwork. Palette binding,
GGFP bake and actual stage playback have not been verified.

This handoff stops after requested item 3. Slot bonus, all continue sets and the
data-select/team portrait remain unmade. No build, merge or push is part of this lane.

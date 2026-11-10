# MICA wave 3 art handoff

The initial handoff below is historical. Installed Blue Sphere art and verification
are described in the [2026-11-10 checkpoint](#blue-sphere-native-style-redraw-2026-11-10).

## Initial handoff (2026-11-08)

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

## Blue Sphere native-style redraw (2026-11-10)

All eleven nonblank Blue Sphere frames now use fresh native-pose GPT Image
candidates; selector 0 remains transparent. The approved concept supplies identity,
while the actual special-stage palette renders supply the drawing style: deep
jade contours, faceted upper-left highlights and shaded plate relief instead of
black comic scale borders. Standing/running use at most the native 8–9 visible
colours, detailed balls six and the blur five. Coral ears share the shoe colour;
cream edges and soles share the remaining warm slots. TALON is unchanged.

`blue-sphere-redo-generation.json` records prompts, attempts, selections, native
mapping/DPLC inventory, palette counts, guide boxes and independent areas.
`native-pose-blue_sphere.json` contains the final indexed rows. Rebuild and verify:

```bash
python3 examples/mica-and-talon/art/mica/wave3/native_pose_stages.py --rebuild blue_sphere --verify blue_sphere
```

The actor uses standing 1, running 2–8 and jumping 9–11; turning rotates the
stage and reuses the same body frames. Start/clear do not add a separate pose.
The native run and jump tables remain host-owned. At velocity 0x1000 their phase
interval is two 60 Hz ticks; this review states its velocity rather than calling
an illustrative fixed frame delay native timing.

Opaque-area square-root ratios: standing 0.969, run 0.988, jump 0.971, overall
0.983. Canvas, object origin, native mapping coverage and tile budgets are
unchanged. The narrow run phase 3 was re-reduced one pixel wider; only rectangular
mapping-mask overflow was removed. No native sprite pixels are retained.

External review: `$HOME/scratch/agent-tmp/mica-blue-redo-20261110/review.html`.
It includes unlabelled per-frame style gates, aligned guides, previous MICA and
unchanged TALON, exact-tick 60 fps movies and a 2× production capture entering
from CNZ at (11616,1612), then running, turning and jumping. The capture eventually
hits a red sphere; it is presentation evidence, not layout completion.
The coverage slots were already authored, so no fallback counts move.

# Native playable art preprocessing

Python 3 + Pillow (checked with Pillow 13.0.0); no Java build or ROM required.
Run from repository root:

```bash
python3 tools/modding/playable_art/pipeline.py examples/mica-and-talon/art/mica/recipe.json --review /tmp/mica-art-review-20261108
python3 tools/modding/playable_art/verify.py examples/mica-and-talon/art/mica
```

`recipe.json` selects equal-grid atlas cells and native dimensions. Sources may
have alpha or an explicit RGB `background` key for edge-connected flood removal.
The largest connected alpha component removes detached generation noise; use a
separate crop/source when a pose deliberately has detached parts. Downscaling uses
Lanczos, followed by thresholded alpha, nearest fixed-palette colour assignment
without dithering, and a one-pixel inside silhouette outline at index 1.
There are no partially transparent or off-palette output pixels.

`palette.yaml` contains transparent index 0 plus exactly 15 unique opaque colours,
all channels on the Mega Drive eight-level ladder. The pipeline never learns a
new palette per frame. Both YAML files use the JSON subset of YAML 1.2 so Python
stdlib can read them without PyYAML. Frame PNGs use indexed mode P, 4-bit indices,
and transparent index 0; every frame carries the same palette in the same order.

Standing `anchor` defaults to `[round(width * .64), height - 19]`, accounting for
MICA's long tail to the left. An explicit `[x,y]` is a source-local native hotspot;
`[-1,-1]` centres an airborne pose. Ball anchors are `[14,14]` in 29×29 pixels.
The temporary 80×80 alignment canvas is tight-cropped for output; exported
`hotspot` is the engine centre relative to that crop, in native pixels. Reconstruct
position with `top_left = engine_centre - hotspot`. A foot baseline at centre+19
is the **exclusive bottom edge**, so the last occupied row is centre+18.

`patches` contains hand-authored `[x,y,palette_index]` edits in the temporary native
80×80 canvas, whose engine centre is `[40,40]`. Patches refine eyes/laces and remove
the generated hanging bar. Final silhouette ink is reapplied after patches.
Derived frames use `derive` and a nearest-neighbour `angle` about the engine centre.
The recipe orders prerequisites before derived frames. Check patches and native
silhouettes again when replacing a source; coordinates are intentionally explicit.

The handoff `frames.yaml` records id, canonical animation name, order, file,
hotspot, angle set, notes and suggested 60Hz preview ticks. WALK is set-major:
8 flat, 8 up45, 8 steep, 8 inverted; RUN uses the same ordering with stride 4.
Angles are +0/+45/+90/+135 degrees visually counterclockwise, before engine facing
and orientation. These are rigid rotations, with repeated outline cleanup, rather
than separately drawn ground-contact deformations. Integration must check the W2
slope stride and direction contract before materializing the sheet.

`HOMING_DIVE` is a **custom handoff key**, not an existing `CanonicalAnimation`
enum member. The ability lane must bind the four scale-first poses explicitly.
STOP intentionally shares SKID artwork. Preview timings are illustrative; W2
supplies native speed-driven walk/run timing. HOLD/loop policy belongs to that
lane, particularly DEATH, HURT, SPRING, DUCK and LOOK_UP. No final GGFP, mapping
piece split, DPLC budget certification or gameplay integration is produced here.

Review generation writes 1×/4× contact sheets (nearest-neighbour hard pixels) and
4× GIFs for every animation and slope set. They are disposable, outside Git.
The verifier checks palette identity, indices, binary transparency, ink boundary,
standing and ball geometry, complete core coverage, keyed removal preserving
an enclosed eye white, and byte-for-byte frame regeneration.

## MICA review notes

Sources are built-in GPT Image edits against the approved concept; prompts and
accepted/rejected cells are recorded in the art's `generated/prompts.json`.
The checkpoint reviewed WAIT/WALK/ROLL at 1× before expanding. The first WALK
was replaced for subtle strides; the first dive was replaced because the muzzle
led the attack. Every final frame was checked on the full 1× contact sheet, with
larger eye/grip detail inspection. Standing WAIT and WALK are 39 pixels tall;
ROLL is 29×29. Crouch, sprint and airborne poses vary in bounds with their pose.

Weakest areas: slope rotations need engine contact validation; the first two RUN
phases are similar; SPRING has a small hood-shape shift between phases; very small
jade laces and scale lines merge at 1×. These are usable core art handoff frames,
not in-engine certification. Additional polish may follow runtime inspection.
Remaining W7 presentation/super/cutscene art is outside this core-set handoff.

## MICA presentation round 2

```bash
python3 -I tools/modding/playable_art/presentation.py --review /tmp/mica-art2-review-20261108
```

`presentation.py` is the reproducible native-pixel source for 18 original MICA
presentation images and `presentation/gilded.yaml`. It uses the existing palette
without reordering any indices. The two S1 supplementary HUD glyphs use the native
font's black shadow at 1 and yellow at 15; those two PNGs carry that explicit
palette exception. Their other indices are unused. M is 8×8 with an 8px advance;
A is 6×8 with a 6px advance. The decoded SONIC name supplies the 6px ink height,
heavy stems and down/right shadow; M needs a wider cell to keep those stems.

The JSON-subset `presentation.yaml` records mapping/art labels, native piece sizes,
canvas dimensions, palette line and composition. HUD/monitor faces are 16×16;
settled signs 48×32; angled spin faces 32×32. The optional S2 alternate face
replaces the native Tails side. S2/S3K monitors reference the same VRAM life-icon
tiles; S1 has distinct monitor art. Signs are **character-only overlays**: compose
with the native border, backing and pole, rather than replacing that hardware.
Spin3 is already flipped; do not flip it twice. The edge-on 8×32 frame contains
no readable face and remains native. No border, pole or native character pixels
are committed with these original overlays.

`--review` requires the pinned disassemblies. It decodes native Nemesis art with
the reference oracle, compares original and MICA at 1×/4×, and writes a Gilded WAIT
GIF. The oracle uses a zero lookahead guard after compressed input; the native
header still bounds output. Run with `-I`, as above. Normal generation uses no
ROM/disassembly assets. There is no Java build, GGFP bake or gameplay certification.

Gilded changes only jade indices 2–6, keeping ink, skin, gloves and boots stable.
It includes six fade frames and distinct dry Super cycles: S2 has nine steady
frames at 8 ticks, fade at 4 ticks; S3K has three at 7 ticks, fade at 2 ticks.
These follow the shipped `fixBugs = 0` / `FixBugs = 0` reset branches, which skip
the final source-table frame. S2's owning labels are `PalCycle_SuperSonic.normal`
and `CyclingPal_SSTransformation`; S3K's are
`SuperHyper_PalCycle_SuperSonic` and `PalCycle_SuperSonic`. The colours are MICA's
original moving gold highlight, not copied Sonic colours. Underwater, Hyper,
transformation poses and controller binding are later work.

Round 2 intentionally takes the presentation-first stopping point. All 36
WALK/RUN slope frames remain the original rigid rotations. Their remaining work
is hand-drawn limb/contact deformation with matched stride phases and hotspots;
outline filtering alone does not solve the tumbling read. The temporary slope
before/after sheet explicitly labels these frames unchanged. The presentation
head is intentionally simpler than the playable art at 16px; its scale/eye
clusters and the dithered spin faces still need in-engine background validation.

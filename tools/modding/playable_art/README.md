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
and orientation. The round 3 slope frames use explicit native pixel rows, with redrawn heads,
gloves, bent limbs and boots. Integration must check the W2 slope stride and
direction contract and runtime ground contact before materializing the sheet.

`HOMING_DIVE` is a **custom handoff key**, not an existing `CanonicalAnimation`
enum member. The ability lane must bind the four scale-first poses explicitly.
STOP intentionally shares SKID artwork. Preview timings are illustrative; W2
supplies native speed-driven walk/run timing. The assembler supplies the fixed
script timing and end actions. The reducer stops at indexed frames; the assembler below supplies mapping pieces
and converter input.

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
not a claim that every native route or presentation state is certified.
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

## MICA presentation round 3

The round 3 pixel source in `presentation.py` replaces the six life portraits,
four sign busts and six projected spin faces. Review is split into `faces`,
`signs` and `spins` 1×/4× sheets. Sign backing PNGs are separate explicit-palette
planes: S1 yellow is native line 0 index 15; S2 orange line 0 index 15;
S3K orange line 0 index 9; alternate S2 blue line 0 index 2. These colours cannot
fit the unchanged MICA character palette. Draw backing then character, retaining
the native pole; integration must reserve a distinct palette line for backing.
The backing files use their own small palette and are not character art. The
manifest records both the native source indices and this runtime requirement.
These are original redrawn frames, not copies of native sign pixels.

Round 3 slope revisions are stored as explicit `native_pixels` hex-index rows and
an engine-centre anchor in `recipe.json`. The pipeline places these native pixels
without rotating a bitmap; the common silhouette ink pass still applies.
The redraw retains upper-body/tail seeds and replaces the head and lower limbs,
using the matching flat frame's support/lift phase. The broad crest scales are
simpler than the flat artwork. Pixel validation does not certify runtime contact.

To regenerate all six slope comparison sheets (1×/4×) and GIFs without Java:

```bash
python3 -I tools/modding/playable_art/slope_review.py --review /tmp/mica-art3-review-20261108
```

This review-only decoder reads S1 `MS_Walk21`–`MS_Walk46`, `MS_Run21`–`MS_Run44`
and their `SonPLC_` entries. MICA has eight walk phases; S1 has six, so the sheets
label each native phase explicitly and repeat the first two for comparison.
They are size/contour references, not assertions of animation-phase parity.
All 36 revised slopes retain the unchanged shared palette. Their floor support
is authored at local centre+18 (last occupied row), before orientation. Runtime
contact remains an integration check. Arm motion is restrained and the simpler
head/crest may need another art pass beside the more detailed flat cycles.

## MICA native surfaces round 5

```bash
python3 -I examples/mica-and-talon/art/mica/wave3/round5.py --review /tmp/mica-art5-review-20261108
```

This item-1 checkpoint supplies five original 32×32 slot-stage curl frames,
normal Sonic mappings $96–$9A. `Obj_Sonic_RotatingSlotBonus` forces animation 2;
`AniSonic02`/03 repeat $96 between each of the other four mappings. There is no
separate native Sonic slot-stage art bank. Every frame retains the shared MICA
palette and its original indices; the engine must install that palette on the
player line. No fixed-palette variants are emitted.

`round5.py` reads the pinned S3K mapping/DPLC tables, draws original pixels and
checks palette identity, tile aliases and native DPLC capacity. Reference art
is decoded only for disposable side-by-side 1×/4× sheets and the eight-step GIF.
The preview uses five ticks per step (zero ground velocity); gameplay timing
remains native and speed-driven. GIF durations round to 10ms units.
The generator updates its surface while preserving earlier manifest entries.
The older `wave3/build.py` rebuilds the round-4 manifest from scratch: run it
**before** `round5.py` when regenerating both rounds. No Java build is needed.

The round-5 checkpoint stops after slot art. Continue screens, data-select solo/
team composition, ICZ snowboard and `endings-inventory.yaml` remain undelivered.
Curl scale travel is restrained, and the small eye/mitten clusters simplify at
1×. This is native-layout art handoff, without GGFP or in-engine certification.

## Assemble and bake the core sheet

```sh
python3 tools/modding/playable_art/assemble.py examples/mica-and-talon/art/mica examples/mica-and-talon/src/main/mod
python3 examples/mica-and-talon/build.py
python3 -m unittest discover -s tools/modding -p test_playable_art_assemble.py
```

The build reruns assembly before conversion. The atlas uses 8-bit indexed PNG
(the converter codec requires 8-bit storage), with only the same 16 indices. Replace/rebuild source frames, then
rerun it; no sheet coordinates are maintained by hand. Assembly preserves indexed
pixels and hotspots, pads each crop to tiles and cuts pieces at 32 pixels (4 tiles).
The current 98-frame sheet uses at most 36 tiles per frame. The converter's bank
report is the total resident source bank, not a 36-tile streaming bank: its current
full-frame runs retain distinct destination offsets. Do not confuse the two costs.

The 17 canonical animations use S2 Sonic as their authored script reference:
`SonAni_Walk` through `SonAni_Hurt` (s2.asm:38692-38754). Positive durations are
native timer bytes plus one. Host profiles supply WALK/RUN/ROLL/PUSH speed timing.
DUCK/LOOK_UP hold the last pose (the native one-frame repeat); SKID/STOP, SPRING
and GET_AIR switch to WALK. SPRING's two authored phases divide its native 48-tick
interval; GET_AIR retains four 12-tick steps with repeated art. Other original
cycles retain their reference's per-step duration and loop. WAIT uses six original poses and a 15-step looping script: breathing and a blink
play immediately for 186 ticks, followed by a glance and boot tap. The optional
`frames.yaml` `scripts.WAIT` contains symbolic frame IDs, positive tick durations
and a canonical `loop`/`hold`/`next` end action; assembly resolves IDs after packing.
These are authored pose adaptations, not an assertion of stock frame identity.
WALK/RUN slopes are ordered flat/up45/steep/inverted, native selectors 0/2/4/6.

`mica-custom-frames.json` records HOMING_DIVE mapping indices for the ability
lane. All four frames are in the image and GGFP; no canonical enum member is
invented, and no native animation selects them yet. This file is a build hook,
not converter schema. Re-read it after source manifest reordering.


## MICA motion feedback pass

```sh
python3 examples/mica-and-talon/art/mica/motion.py run
python3 examples/mica-and-talon/art/mica/motion.py spring
python3 examples/mica-and-talon/art/mica/motion.py wait
python3 tools/modding/playable_art/pipeline.py examples/mica-and-talon/art/mica/recipe.json
python3 tools/modding/playable_art/assemble.py examples/mica-and-talon/art/mica examples/mica-and-talon/src/main/mod
```

The native contour source replaces all 16 RUN poses, both SPRING poses and six
WAIT poses, with only two extra unique frames overall (98 total). RUN uses a
four-phase boot blur; slope contours are drawn in rotated coordinate bases about
the flat hotspot, without rotating a finished bitmap. SPRING retains one head
contour across its 24+24-tick launch. WAIT keeps boots planted during the 1px
crest/chest lift and tail sway; its occasional blink precedes the fidget at tick
186. The fidget loops back to breathing. The shared palette order and maximum
36-tile frame bound remain unchanged. The assembled source bank grows by two
frames; this is not a claim about streaming allocation or in-engine validation.

Disposable motion sheets at 1×/4× and GIFs are in
`/tmp/mica-art-motion-20261108/`. Native S1 art is comparison-only. RUN comparisons
use the fastest native one-tick step, rounded to 20ms by GIF's centisecond limit;
SPRING uses 400ms per phase, WAIT the actual assembled script. The generic
pipeline's per-pose review GIF is illustrative; `WAIT-script-*.gif` contains the
complete breathing/delay/fidget sequence. Runtime slope contact, the simpler
crest clusters and the small spring mouth remain the main visual checks for
integration. No Java build or GGFP bake was performed for this art-only pass.

## Native-pose transformation pilot (W7)

```sh
python3 -I tools/modding/playable_art/native_pose.py --output /tmp/mica-native-pose-20261108/native
# Built-in GPT Image: for EACH reference, pass <id>-8x.png + concept/mica.png.
# Save raw MICA PNGs to /tmp/mica-native-pose-20261108/raw/<id>.png and
# exact prompts as an id->prompt JSON object at /tmp/mica-native-pose-20261108/prompts.json.
python3 -I tools/modding/playable_art/native_pose_pilot.py --review /tmp/mica-native-pose-20261108
python3 -I tools/modding/playable_art/pipeline.py examples/mica-and-talon/art/mica/native-pose-pilot/recipe.json
python3 -I tools/modding/playable_art/test_native_pose.py
```

The reference exporter restricts output to `/tmp`, uses pinned S2 player
art/mappings/DPLC and the existing Nemesis oracle for S3K HUD/S2 sign tiles.
The playable bank is already uncompressed in the pinned disassembly; it is
assembled through its native DPLC and mapping pieces, never recompressed.
Nemesis uses the reference decoder's bounded-header output with an eight-byte
zero lookahead guard. `references.json` records mapping labels, native tight
size, relative bounding box and hotspot; the 8× PNGs use nearest-neighbour.
The sign reference includes native furniture as context, but the transformation
prompt requests only MICA. No native pixels may enter the pilot or generated
sources. Only transformed MICA pixel rows, final indexed PNGs, provenance,
exact prompts and cleanup patches are retained in the repository.

The pilot is isolated in `native-pose-pilot/` and is not assembled into the mod.
Its `recipe.json` captures the final reduced pixel rows, making ordinary pipeline
rebuilds independent of temporary raw generation files. Re-running reduction
requires the raw files and native references in `/tmp`. `patches.json` uses
reduced-frame-local coordinates. The review's raw column is the unquantized GPT
alpha crop normalized to the target bounds; full-resolution originals are in
`raw/`. Both sheet scales share engine-centre alignment, rather than aligning
by crop corners. Each frame also has a separate four-column sheet.

WAIT compares the existing authored MICA script, which is not stock S2 idle
script timing. WALK/RUN use the fastest native speed-driven one-tick interval;
there is no single fixed native walk period. GIF centiseconds are rounded
cumulatively (10/20ms steps), preserving total cycle time as closely as possible.
WAIT source poses are S2 mappings 1–6, not a claim that MICA's six semantic
poses map perfectly onto Sonic's. Both SPRING phases use mapping $5B, because
stock S2 has only one spring drawing. Presentation hotspots are canvas centres.
Full runtime timing, contact, tile capacity and presentation palette composition
remain integration work. This art-only workflow never builds Java.

## Native-pose core adoption

`native_pose.py --core --output /tmp/mica-np-core-20261108/native` exports 60
reference images: all 58 unique nonslope playable pose bases plus the two pilot
presentation references. STOP shares SKID. Every enlargement explicitly uses
nearest-neighbour. HOMING_DIVE is custom: its third pose uses an inverted spring
reference, fitted into the native ROLL-02 envelope to avoid a tall-frame pop.
`native-pose-core.json` distinguishes pose and geometry references in that case.

Before editing, snapshot `frames.yaml` as `previous-manifest.json`, and frame PNGs
under `previous/` in the temporary review directory. Supply per-frame built-in
GPT Image outputs under `raw/` and exact prompts in `prompts.json`. Then run:

```sh
python3 tools/modding/playable_art/native_pose_core.py --review /tmp/mica-np-core-20261108 wait walk run spring roll spindash push duck look_up skid balance hurt death drown get_air hang homing_dive
python3 tools/modding/playable_art/assemble.py examples/mica-and-talon/art/mica examples/mica-and-talon/src/main/mod
python3 tools/modding/playable_art/verify.py examples/mica-and-talon/art/mica
```

Pilot adoption also needs its raw review directory (`--pilot-review` defaults to
`/tmp/mica-native-pose-20261108`). The helper stores only original MICA rows,
source hashes, prompts, reference metadata and cleanup descriptions in Git;
ordinary `pipeline.py` regeneration needs no temporary references or GPT outputs.
The native boxes/hotspots supersede the earlier art anchors by owner direction.
`previous_size`, `previous_hotspot` and `material_box_change` flag capture review
obligations. This preserves IDs, order, script timing and all shared palette
indices; slope rows and PNGs are unchanged. Palette/ink verification checks the
new recorded native geometry instead of the historical standing/29x29 rules.

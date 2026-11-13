# Native playable art preprocessing

Python 3 + Pillow (checked with Pillow 13.0.0); no Java build or ROM required.
Run from repository root:

```bash
python3 tools/modding/playable_art/pipeline.py examples/mica-and-talon/production/mica/recipe.json --review /tmp/mica-art-review-20261108
python3 tools/modding/playable_art/verify.py examples/mica-and-talon/art/mica/sheet
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

`HOMING_DIVE` is an optional canonical strike key. Directional handoff keys
`HOMING_DIVE_FORWARD`, `HOMING_DIVE_FORWARD_UP`, `HOMING_DIVE_UP`,
`HOMING_DIVE_FORWARD_DOWN` and `HOMING_DIVE_DOWN` are also accepted by the
assembler, with two-tick looping frames. Normal facing mirrors leftward flight.
The engine selects current velocity direction, then falls back to `HOMING_DIVE`,
then native animation. Match cycle lengths/timing to preserve phase when turning;
see [character sheets](../../../docs/modding/characters.md) for sector boundaries.
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
python3 -I examples/mica-and-talon/production/mica/wave3/round5.py --review /tmp/mica-art5-review-20261108
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
python3 tools/modding/playable_art/assemble.py examples/mica-and-talon/art/mica/sheet target/mica-and-talon/generated
python3 examples/mica-and-talon/build.py
python3 -m unittest discover -s tools/modding -p test_playable_art_assemble.py
```

The build reruns assembly before conversion. The atlas uses 8-bit indexed PNG
(the converter codec requires 8-bit storage), with only the same 16 indices. Replace/rebuild source frames, then
rerun it; no sheet coordinates are maintained by hand. Assembly preserves indexed
pixels and hotspots, pads each crop to tiles and cuts pieces at 32 pixels (4 tiles).
The current 106-frame sheet uses at most 36 tiles per frame. The converter's bank
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
A handoff with only `flat` WALK/RUN frames assembles without `slopes`, and the
converter repeats the flat cycle on every slope. The tutorial's part 2 checkpoint
uses this, with `requiredAnimations` listing only WAIT, WALK, RUN and ROLL.

`mica-custom-frames.json` records HOMING_DIVE mapping indices for the ability
lane. All four frames are in the image and GGFP; no canonical enum member is
invented, and no native animation selects them yet. This file is a build hook,
not converter schema. Re-read it after source manifest reordering.


## MICA motion feedback pass

```sh
python3 examples/mica-and-talon/production/mica/motion.py run
python3 examples/mica-and-talon/production/mica/motion.py spring
python3 examples/mica-and-talon/production/mica/motion.py wait
python3 tools/modding/playable_art/pipeline.py examples/mica-and-talon/production/mica/recipe.json
python3 tools/modding/playable_art/assemble.py examples/mica-and-talon/art/mica/sheet target/mica-and-talon/generated
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
python3 -I tools/modding/playable_art/pipeline.py examples/mica-and-talon/production/mica/native-pose-pilot/recipe.json
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
python3 tools/modding/playable_art/assemble.py examples/mica-and-talon/art/mica/sheet target/mica-and-talon/generated
python3 tools/modding/playable_art/verify.py examples/mica-and-talon/art/mica/sheet
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

## Native-pose finishing checkpoint (2026-11-08)

The 36 slope recipes now use the corresponding S2 phase's native canvas and
hotspot, with integer contour/sole edits instead of another resize. Export still
removes transparent margins; `native-pose-core.json` records both the full native
reference and the resulting crop geometry. Each slope provenance file retains
its original prompt/hash and records the finish separately. `native_support` is
the furthest opaque native pixel along the slope normal, measured relative to
the native hotspot. The Python assembly regression checks every slope's exported
hotspot and support (no crossing; at most one diagonal pixel of clearance).

The S3K `native-pose.json` rows contain the transformation cleanup: flat gold
scale clusters, connected cream muzzle/eye and coral boots. The core recipe
contains the rounded `homing_dive-02` shell rows. Both rebuild without raw GPT
outputs. Assembly remains 98 frames / 18 animations / maximum 36 tiles per frame.

Headless review outputs are at `/tmp/mica-art-final-20261108/` (a symlink to this
task's disk-backed scratch directory because the shared tmpfs filled). GHZ1,
EHZ1 and AIZ1 title-first clips show movement; additional clips show powered
strikes, pushing, springs and a positioned AIZ vine. This checkpoint does **not**
certify all requested states: the AIZ vine's object-owned native `$90/$91`
mapping writes select MICA's inverted-walk aliases instead of HANG, and the
attempted EHZ spindash inputs jumped without charging. These clips document
remaining behavior, not successful hang/spindash pose acceptance. Tight native
boxes also leave truncated hood/tail contours in some steep phases; those need
a deliberate redraw rather than further cropping or scaling.

Validation: Python discovery 44 tests passed; native reference tests 3 passed;
core palette/geometry/regeneration and S3K 25-frame piece/palette verification
passed; queued `TestMicaCharacterIntegration` 18 passed, zero skips with explicit
verified ROM paths and surfaceless EGL. The first `surefire:test` attempt omitted
ROM properties (four path-setup errors); the explicit-path rerun passed.

Native palette audit: `python3 tools/modding/playable_art/palette_audit.py
--output $HOME/scratch/agent-tmp/<task>/audit` decodes representative
references beside the historical `/tmp/mica-np-*-20261108` inputs (`--used-root`
can relocate that archive). It writes review-only PNGs and `results.json` outside
Git and never regenerates MICA art. Surface palettes must follow scene-time
palette copies and mapping palette bits, rather than the art's usual player
bank. S2 photos use `Ending Photos.bin` on line 0 after ObjC9's copy; S1 continue
floor pieces select their own palette line. S3K transformation references accept
an explicit native Super cycle phase (default 6, first steady phase); a static
reference cannot certify every fade/Hyper cycle colour.

## Object-owned native hang poses

MICA's three HANG rows were redrawn from S3K normal-player mapping $90–$92 using
the native-pose method and the approved concept. `native-pose-core.json` records
the exact prompt, generation hash, native reference label, tight size and anchor.
The original rows are in `recipe.json`; regeneration needs no reference art or
raw generation. The assembler's `nativeFrames` table binds semantic `hang/0`–
`hang/2`; host vocabulary translation and canonical fallbacks are documented in
`docs/modding/characters.md` and the W2 follow-up inventory in the design.

## Steep contour completion

All twelve steep WALK/RUN recipes complete the previously clipped hood crown
and left/back attachment with original indexed pixel rows. The authored canvas
expands above/behind the character and the hotspot moves with it; the engine
centre and sole support remain fixed. This is contour drawing, without scaling
or rotating the finished frame. `native-pose-core.json` retains the original
native reference alongside the expanded authored geometry. Palette, inside ink,
regeneration, tile bounds and native support regression checks pass. Lower tail
crop edges in `walk-steep-00`, `walk-steep-04`, `run-steep-01` and `run-steep-02`
are completed as tapered jade tips on taller canvases; their anchors and X sole
support remain fixed. The indexed recipe rows are the reproducible source.

The optional recipe/`frames.yaml` `nativeFrames` map uses semantic keys and
symbolic frame IDs, e.g. `"hang/0": "hang-00"`. Reduction preserves the map;
assembly rejects unknown IDs and converts known IDs to packed mapping indices.
MICA also binds existing WAIT/ROLL art explicitly. A raw phase with no matching
art uses the host descriptor's documented canonical fallback.

## Reuse existing normal-mapping art

```sh
python3 examples/mica-and-talon/production/mica/reuse_native_art.py
python3 tools/modding/playable_art/pipeline.py examples/mica-and-talon/production/mica/recipe.json
python3 tools/modding/playable_art/assemble.py examples/mica-and-talon/art/mica/sheet target/mica-and-talon/generated
```

This preserves the original MICA pixels/hotspots of ending mappings $BA/$CA/$CB
the five normal-Sonic slot mappings $96–$9A, and continue player mappings
$24/$55–$57/$59–$5A in the normal sheet. It reads no
ROM/reference pixels. Fourteen `NATIVE_POSE` handoff frames are packed without an
ANIM script; that tag is not a `CanonicalAnimation` member. The sheet now has
112 frames, 18 animations and the unchanged maximum 36 tiles per frame.
`native-pose-core.json` records original asset hashes; verification rejects stale
reuse after those assets change. The shared palette and visible pixels remain
unchanged; transparent margins are cropped with the matching hotspot shift.


## Authored script endings

`frames.yaml` `scripts` overrides apply to both MICA and TALON, including finite
BLINK/GET_UP when frames exist. Use `end: loop`, `end: hold`,
`end: next` plus `next: WALK` (or another authored canonical key), or
`end: loop-back` plus `loopBackSteps: N`. The latter repeats the final N
**authored steps**, counting duplicates and retaining each duration. MICA's
expanded S2 WAIT uses 6 and TALON's Tails WAIT uses 28. No new art is needed.
Assembly resolves symbolic frame IDs; `ggfmod convert art --playable` writes
GGFP v2 end tags 0/1/2/3, with an unsigned 16-bit count following tag 3.
Materialization expands that count with duration normalization; binding preserves
it while translating only next-animation targets into native IDs. See
[character timing](../../../docs/modding/characters.md) for native ownership and
handoff timing. Reassemble each sheet with `assemble.py` after script edits.

## MICA early S3K object poses (batch 3, 2026-11-09)

After batches 2 and 3 the core sheet has 226 drawings / 52 canonical keys. Forty new semantic
`nativeFrames` bindings cover swing $78–$7F, tumble $69–$77, twist $58/$5B,
pole $5C–$62 and bar $63–$68/$94/$95. These are object byte aliases, not new
canonical scripts. The original MICA indexed rows live in `recipe.json`;
`object-poses/` records exact built-in GPT Image prompts, per-frame selections,
source hashes and manual head/torso landmark rectangles. `native-pose-core.json`
keeps native reference geometry separate from the exported tight crop/hotspot.

Native full-palette S3K references were enlarged 8× before redraw. Two attempts
per base pose were reduced uniformly into the native aspect, quantized to the
unchanged palette and given inside ink; there are no painted pixel edits. Seven
drawings use `derive_slopes.rotate` quarter-turns: $7A/$7C/$7E from $78,
$7B/$7D/$7F from $79, and $64 from $5D. Each native pair was checked for exact
RGBA equality under that rotation before deriving original MICA art. The other
33 poses change perspective or limbs and were generated independently.

External review: `$MICA_BATCH3_REVIEW_ROOT/review.html`.
It retains both attempts, lossless PNG animation at 60Hz, hotspot-aligned scale
panels and packaged-mod vine/tree/HCZ conveyor MP4s with state CSVs. Tumble scale
clusters simplify at 1×; twist front/back poses coexist with the older side
views. This covers the byte slots, not later-zone route certification.

## Shared family phases

Use `nativeFrames` keys `family-<canonical-family-in-lowercase>/<phase>` to author
a family once. Underscores become hyphens (`family-hurt-fall/0`). Module profiles
live in `src/main/resources/animation-families/`; a null family explicitly
marks unused or character-specific Sonic-table entries. An unnamed native script
may omit its canonical animation while retaining a known family. Profiles carry phase
correspondence only. Never copy ROM timer bytes into this metadata: the binder
uses the ROM-loaded script's delay, ordered repetitions and terminator.

MICA's recipe and handoff contain 208 source frames, 52 representative canonical
scripts and 106 family-phase aliases. Both recipe and handoff must be updated:
`verify.py` rebuilds the handoff from the recipe. Its WAIT partial loop was already
correct in the handoff; the formerly stale recipe now reproduces it too.

Use `--export-references` to decode disposable native reference PNGs from the
pinned development disassemblies when an external reference directory is absent.
These pixels never enter the runtime or repository.

The maintained review generator inventories all normal Sonic scripts and S2/S3K
Tails scripts, and optionally builds lossless native/MICA GIFs from external native
reference PNGs. References and decoded native pixels stay outside Git:

```bash
python3 tools/modding/animation_family_review.py <audit-json-directory> <review-directory> --references <native-reference-directory>
```

The CSV includes native frame order/count, delay, terminator, phase correspondence,
trigger summary and exact script-label citations. GIFs retain every repeated step;
LOOP_BACK previews show two tail repetitions. Speed-driven roll uses an illustrative
one-tick interval, not a gameplay speed claim. GIF centiseconds cumulatively round
60 Hz intervals without dropping frames or changing palette colours.

The review groups hosts under each family and marks absent host families N/A.
Tails is reference-only: its phase indices are local to each script, its blink
slots are distinct from Sonic's ending slots, and reserved scripts add no TALON
drawing obligation. Native Sonic scripts retained without a live selector remain
in the inventory with that limitation stated.

## TALON shared-family review

`examples/mica-and-talon/production/talon/family_review.py` is the maintained full-rate
review for TALON's shared families and flight/swim variants. It consumes the
coverage audit JSON and pinned disassemblies, writes decoded comparison pixels
only outside the repository, emits exact-palette full-tick GIFs and a common
engine-centre lineup, and displays recorded anatomical/reduction landmarks.

```sh
python3 examples/mica-and-talon/production/talon/family_review.py "$HOME/scratch/agent-tmp/talon-families-20261109"
```

The scratch input contains `baseline/` or `after/` audit JSON and `host-native/`
S1 reference exports from `animation_family_review.py --export-references`.
Tails profiles come from the per-game archetype view; native stock/donation
retains the original unscoped Sonic provider. BLANK is semantic transparency;
BURNT may share DROWN's shipped pose. Canonical ROLL2 requires native speed timing.
New flight/swim names are accepted directly by assembly. Native single-phase
flight bodies retain their clock at right; TALON's selected six-phase integrated
wing cycles run at the approved authored cadence at left, over a least-common-
multiple window. No detached rotor is copied. Every decoded GIF tick is checked
against the exact RGB source and cumulative 60Hz-to-centisecond duration. PUSH
uses its native stationary-wall 33-tick interval; other speed-driven samples are
labelled. Finite end actions are labelled; each review window restarts.

For cross-host phase correspondence, compare native pose views before assigning
family phases. Local dedup ordinals only inventory each host's unique drawings;
the S2 five-view float cycle needs `[0,2,4,5,7]` in the S3K eight-view family.
Register functional contacts (PUSH palms, carrier boots, carried grip) independently
of skull/torso scale. Anatomical scale is measured against approved WAIT, never
against wingspan or sprite bounds.

## Powered pose inventory research

`examples/mica-and-talon/production/mica/powered/inventory.py` expands the native S2/S3K
powered scripts, movement slope selection and S2 alternate walk frames before
counting references. Its [checkpoint notes](../../../examples/mica-and-talon/production/mica/powered/README.md)
distinguish native frame indices, shared scripts and actual accepted creator art.
The optional native-pixel export writes only to scratch. `--compare-poses` verifies
shared-script silhouettes at their hotspots; `powered/pose-identity.json` records
the non-pixel evidence. Installed powered handoffs and normal-phase aliases live
beside that inventory; the Java coverage audit probes their production binding.

## TALON presentation reference probe

`talon_presentation_reference.py` exports Tails' half-pipe, Blue Sphere, continue,
portrait and completion-pose native banks with labels, boxes/hotspots and 8× pose
sheets, plus life/sign/font references. S1's mod-defined continue uses the native
Sonic-host choreography. It reuses the existing reference decoders and requires
the optional pinned disassemblies; it is never a runtime or mod asset source.

```sh
python3 -I tools/modding/playable_art/talon_presentation_reference.py --output "$HOME/scratch/agent-tmp/talon-presentation-references"
```

Keep all output outside Git. The S2 sign uses S2's own player palette, not the
similarly named S3K palette (their index meanings differ). Native Tails' separate
stage appendage banks are deliberately excluded. Continue references still show
ordinary native fox tails; transformation prompts must explicitly exclude them.
Source manifests retain native dimensions/hotspots for independent area/placement
checks. Atlas previews can clip protrusions at cell edges; use the individual
native PNGs for exact geometry. The TALON presentation handoff records original
reduced rows, prompts/selections and aliases under `art/talon/presentation/`.


## Title-plane mascot masks

`title_masks.py` preserves host branding when the same mascot pixels occur in a
preceding mascot-only plane and a composed emblem plane. It compares exact RGBA
pixels inside an explicit native vertical bound and emits binary ownership only.
The MICA/TALON title lane uses the exported S3K C/D planes, with the portrait ending
at native row 136. Review the resulting mask over both native planes; equality is
an input-specific ownership probe, not automatic semantic segmentation.

```sh
python3 tools/modding/playable_art/title_masks.py --mascot /scratch/frame-c.png \
  --composition /scratch/frame-d.png --height 136 --out /scratch/title.mask
```

Native renders and raw generations remain outside Git. Authored title banks,
local palettes, selector/hotspot bindings and provenance live under each
character's `titles/` directory in the maintained example.


## Native material contours

Recipe frames may set `"outline_mode": "material"` to preserve selective
material-shadow contours in generated indexed rows. The default `"ink"`
continues to apply the historical inside silhouette outline. Record the
measured surface style and `outline_mode` in the frame's native-pose provenance;
`verify.py` checks material edges are varied and not dominated by charcoal,
alongside the unchanged palette, binary alpha, geometry and rebuild checks.
Do not use the option to bypass visual comparison: native real-palette 8×
references, fresh generations, locked anatomy scale and an unlabelled per-frame
side-by-side remain required. MICA/TALON carry provenance is a worked example of the
measurements; those frames returned to the ink silhouette on 2026-11-12, when the owner found
them washed out beside ink-outlined gameplay frames, so no shipped frame currently uses the option
(`production/talon/tonal_reshade.py`, `production/mica/carried_reshade.py`).

## Pixel noise audit and mode-reduced slopes

`noise_audit.py <sheet dir>...` measures every drawn frame of an indexed sheet and ranks it:
isolated pixels, one- and two-pixel flecks, short-run density, one-pixel anti-alias ramps,
transition density, mean luminance step and ink-silhouette coverage, each as a share of the
frame's non-ink body so sizes compare. `--summary` groups by animation and angle set; `--csv`
writes every frame. Read it against a reference frame: on 2026-11-12 both example sheets had a
median near 0.33 (reductions of generated drawings), while TALON's clean flight set scored 0.15.
Speckle that is uniform across a sheet still shows most in loops, because it boils frame to frame.

```bash
python3 tools/modding/playable_art/noise_audit.py examples/mica-and-talon/art/talon/sheet --summary
```

`derive_slopes.derive(..., method='mode', finish=..., ring=...)` is the 45° reduction every shipped
set uses. It lifts the flat's silhouette contour, enlarges with Scale2x three times, turns, keeps the
commonest index of each 8×8 block, hands the uninked 45° image to `finish` for the caller's material
cleanup, then draws the 1 px ink silhouette. `ring` names the indices that form the flat's contour:
ink on a normal sheet, plus the contour slot of a powered bank, whose caller moves the new ink back
onto that slot. Quarter turns stay exact. The older `nearest` reduction (one sample per block, edge
re-inked) left the flat's outline scattered inside the body, about four points more ink than the
flat; it stays selectable for comparison but nothing ships with it.

`despeckle.py` is the shared cleanup: isolated pixels, one-pixel anti-alias steps and two-pixel flecks
fold into the surrounding shade of their own material. The caller supplies the index-to-material map,
so nothing crosses a material, and unmapped detail and ink pass through untouched; protected materials
(white, cream) are never darkened. `lines=True` keeps a pixel or fleck that continues diagonally into
its own index: art whose detail is one-pixel diagonal lines (MICA's scale edges, her title portrait)
loses its shapes to the plain rules.

`examples/mica-and-talon/production/slope_sets.py --check` derives MICA's WALK/RUN slopes and
homing-dive directions, her Open Cone slopes and Solar TALON's slopes; TALON's normal WALK/RUN set is
`production/talon/walk_cleanup.py`, which also cleans its flats. `test_playable_art_slopes.py`
re-derives MICA's 36 slope frames from her flats and runs both scripts.

## Style audit against a sheet's own reference poses

`style_audit.py PROFILE` answers "which frames look like another artist drew them, and why". Poses
reduced one by one from separately drawn sources differ in line weight and in how many shading
bands survive, and an absolute threshold cannot tell a deliberate style from a damaged one. The
profile names, per sheet, its ramps (dark to light), the crisp reference animations, the animations
with no comparable drawing and the frames accepted as they are. Each frame is measured for outline
gaps, dark rim inside the ink (line weight), surviving bands, distance from the references' use of
each ramp, mean same-index patch size, isolated pixels and one-pixel steps; a measure more than three
reference spreads off on its bad side flags the frame. `--summary` groups by animation, `--json`
writes every row, and `--check` fails on a flagged frame the profile does not accept.

```bash
python3 tools/modding/playable_art/style_audit.py examples/mica-and-talon/production/style-audit.json --summary
```

The audit cannot see a different face or beak design; it found TALON's heavy rims and merged bands,
and `examples/mica-and-talon/production/talon/lineart_pass.py` fixed both those and the heads by
redrawing each off-style pose's head from WAIT's (placed on the pose's own goggle lens and beak,
turned from WAIT's source where the head is pitched) instead of reducing each pose's source again.

## Production special-stage character probe

`tools/modding/SpecialStageCharacterProbe.java` records real capture PNGs and a
separate per-tick `stage.csv` containing the native player mapping, motion,
palette rows/content and displayed fade. Unlike the level `state.csv` mapping,
this reads the special-stage actor itself. It never feeds comparison values into
simulation. Build the example first, then compile the probe against that checkout:

```bash
python3 examples/mica-and-talon/build.py
javac --release 25 -cp "target/classes:$(cat target/mica-classpath.txt)" \
  -d target/character-probe tools/modding/SpecialStageCharacterProbe.java
# Use Java's same classpath plus target/character-probe. Eight required arguments:
# game main sidekick jar-or-dash input output frame-count debug-complete [complete-tick]
```

Unset DISPLAY/WAYLAND_DISPLAY, select surfaceless EGL, and set an owned saveRoot,
as required by gameplay-capture. S3K uses the positioned CNZ act 1 giant-ring
recipe (11616,1612), rings 42, timer 9600; supply the same input to both actors.
An empty sidekick argument preserves the original solo-stage recipe. S2 declares
production stage admission at tick 240 from EHZ act 1; give both teams identical
input. Optional debug completion defaults to tick 465 (S3K) or 780 (S2); the ninth
argument overrides that declared trigger (470 keeps S2 actors visible during
the RGB fade).
These modes are presentation probes, not route certification. Native `boot` act
indices are zero-based. `overlay_alpha` is the black-fade alpha; white fades may
use RGB addition or palette presentation instead, so inspect the real frames and
palette columns rather than inferring white-fade progress from that field alone.
The appended `overlay_r/g/b` columns record `FadeManager.getFadeColor()`; S2
white fades add these RGB values to the whole frame with saturation.

Acceptance media must use the actual captured ticks at 60 fps, with nearest
scaling and lossless RGB if palette equality matters (`libx264rgb -crf 0`). Gate
colours by sampling unobscured actor pixels in real captures. A source PNG review
cannot detect later shadows, priority or fade errors.

`orientation.csv` additionally records every published mapping, native H/V flags,
selected creator compiler slot and remaining render flips. Pair captures by tick
and diff mapping, position, flags and stage motion before inspecting the pixels.
S3K native Tails solo uses palette line 1; creator logical line 0 is mapped to its
owned physical palette row. The probe records this distinction explicitly.

MICA/TALON's `oriented-stage/` banks contain fresh drawings for all native flipped
run orientations (S3K selectors 6–8; S2 upright/diagonal H/V/HV and horizontal H).
They are never offline flipped copies. `oriented_stage.py <directory> --check`
compares each packaged indexed image/palette/alpha against its selected generation
rows; omit `--check` to rebuild those original pixels. The generation manifest
retains prompts, alternatives, native inventory and size measurements. Never
ship the decoded native references or raw GPT Image outputs. A new orientation
bank still requires real-palette 8× native references, model/proportion guides,
fresh attempts, native head/torso scale, an unlabelled visual gate and real
60-Hz lockstep captures. Rebuilding rows is not a replacement for those gates.

## S2 native-role title capture review

`examples/mica-and-talon/production/mica/titles/s2_capture_review.py` builds the
lockstep S2 title checkpoint from real capture frames and read-only sprite
state rows. Inputs are paired900-tick captures, native-box area measurements,
and an external native logo alpha mask. It compares every recorded native
sprite state, inventories every distinct portrait difference, checks opaque
foreground branding over the whole rise, and writes3× strips, shared guides,
onion skins, all palm motion phases and a60fps paired film. No comparison
value is fed to production. Keep all input/output pixels outside Git.

```bash
python3 examples/mica-and-talon/production/mica/titles/s2_capture_review.py \
  --captures /scratch/s2-title-roles \
  --native-logo-mask /scratch/native-title-logo-alpha.png
```

The capture directory contains `paired/s2-stock` and `paired/s2-mod`, each with
`frames/00000.png` through `00899.png` and `title-state.csv` (frame,state), plus
`areas.json` (per-frame character, native_area and authored_area). Native sprite
state is active:selector:x:y for Sonic/Tails and their two independent hands,
joined with `/`. Python3/Pillow and FFmpeg are required. This is presentation
verification of the captured team, not gameplay-route or alternate-role
certification. Provenance and limits live in each character's title README.

## Locked special-stage identity, body motion and native limb phases

`locked_stage.py` reproduces the generated-part compositions under
`production/{mica,talon}/consistent-stage/`. Each surface has one canonical head, torso
and curl. The body-motion correction retains those source hashes: it does not
regenerate identities or repaint native pixels. `stage_body_motion.py` performs
two Scale2x passes, hip-pivot rotation and nearest reduction, propagating anatomical
labels alongside indexed pixels. Geometry, screen-space broad-material relighting
and the unchanged upper-left specular glyph are separate operations. All colours
remain in the native surface's indexed budget. No extra shear/squash is applied.
Native ball envelopes/glint phases still derive from their separate locked curl.

```bash
python3 tools/modding/playable_art/measure_stage_body.py --references /scratch/native --inventory /scratch/inventory.json --out /scratch/native-motion.json
python3 tools/modding/playable_art/locked_stage.py examples/mica-and-talon/production/mica/consistent-stage --check
python3 tools/modding/playable_art/locked_stage_review.py examples/mica-and-talon/production/mica/consistent-stage --out /scratch/mica-motion.csv
```

The native measurement consumes external indexed ROM-derived reference PNGs and
mapping hotspots; only numeric observations enter original-art manifests. PCA
uses primary-material pixels (S3K8–11, S2 2–7) in the headward half-plane at the
hip pivot, with pixel-centre coordinates. Same-colour upper-leg material crossing
the hip plane can enter this reproducible region; it is not hand segmentation.
Native Tails' separate appendage is excluded from body PCA. Positive angle means
the head leans right. S2 angles unwrap around their pipe-sector heading; native
second-moment major/minor spreads describe art morphology, not a squash timer.
S3K mappings6–8 already contain final mirrored piece geometry: never flip those
reference PNGs again. S2 H/V variants do require transforming image and hotspot.

The review sampler measures full and visible-composite body axes, sway/bob,
head/ear and torso IoU/RGB against the posed canonical, joint attachment gaps,
generated glove-white centroids and shoe-contact boundaries against native
landmarks. Arms/calves connect rotated shoulder/hip roots to tracked wrists/feet;
rear shoes/forearms may be behind the torso. Fitted boxes alone would misleadingly
report zero limb error. Tests rederive every pose/curl and bound measured errors.
The production native mapping clock, orientation API and draw path are unchanged.

Use real captures for motion and palette acceptance. A common fixed head mask
is appropriate only for an unchanged translated layer; after body rotation,
compare the posed anatomical labels in screen coordinates and retain unmasked
four-row strips. Moving gloves can share the plumage palette. Native Tails has
an earlier independent appendage; compare the native SAT union for fade pixels.
Stage objects can occlude S2 bodies during turns: classify a covered pixel only
when it exactly equals the stock capture at the same coordinate, and report that
count separately. Do not hide such differences as palette failures or discard them.

`SpecialStageCharacterProbe` explicitly enables the native scanline budget inside
its capture-session configuration scope and restores the caller on close. S2
white-fade proof compares same-tick completion and ordinary captures using the
logged additive RGB, not the black-overlay alpha. Preserve explicit declarations
of stage admission and debug completion in capture provenance.

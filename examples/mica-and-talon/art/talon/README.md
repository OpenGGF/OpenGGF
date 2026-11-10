# TALON core playable art

Original peregrine courier art generated with built-in GPT Image against the
approved `../../concept/talon.png`, using real-palette native poses (Tails in round 1, Sonic for round 2 WALK/RUN) at 8×
nearest-neighbour as choreography references. No native art is included here.
The exact prompts, raw hashes and scratch locations are in `prompts.json`;
`provenance.json` records selected cells and native mappings. The recipe stores
only reduced original TALON pixel rows, so regeneration needs no native assets.

```sh
python3 tools/modding/playable_art/pipeline.py examples/mica-and-talon/art/talon/recipe.json
python3 tools/modding/playable_art/assemble.py examples/mica-and-talon/art/talon examples/mica-and-talon/src/main/mod
python3 -m unittest discover -s tools/modding -p test_playable_art_assemble.py
```

The sheet has 282 indexed drawings and 47 animation keys: the 82 core drawings
below plus 87 shared-family drawings and two rare object poses (see the end of this file). All use one
transparent plus 15 opaque Mega Drive ladder colours. Core set:
WAIT 7; WALK 8×4 orientations; RUN 4×4; ROLL 3 plus blur; SPINDASH 3 plus blur;
SPRING/HURT/DEATH/DUCK/LOOK_UP one each; SKID 2; TAILS_FLY 6;
TAILS_FLY_TIRED 6. Canonical names follow `CanonicalAnimation`; `FLY_TIRED` is
not an accepted enum name. Flight wings are integrated; no tail sprite exists.
`talon-custom-frames.json` is intentionally empty: all supplied poses are canonical.
The example mod binds this sheet in TalonMod and the Solar tables in TalonSolar.

Round 1 source selection: WAIT and WALK strip attempt 2; RUN attempt 3; ROLL and
SPINDASH attempt 2; static poses attempt 1 except spring. Flight and tired flight
originally used attempt 2, superseded by the six-phase revision below. Each static
pose had two strip attempts. Spring then had two additional
alternatives in one generation, with the first selected. Its initial float-like
reference was rejected and replaced with actual spring mapping $59 (22×47).
The rejected second WALK request was blocked by the image service before output;
there are two usable WALK attempts, three requests. Raw attempts stay in scratch.

The reducer fits each generated cell to the native aspect before reduction;
it never squeezes a wide pose into a narrow cell. Round 1 used front/far
shoe palette shading on WALK 5–7 and RUN 0–1; round 2 supersedes those frames. Slope art is
exclusively `derive_slopes.derive` at +45/+90/+135 degrees around the final flat
hotspot, with integer support alignment. No slope generation was used.

## Native timing and explicit adaptations

S2 `TailsAni_Walk`, `TailsAni_Run`, `TailsAni_Wait`, `TailsAni_Stop`,
`TailsAni_Hurt2`, `TailsAni_Death`, `TailsAni_LookUp`, `TailsAni_Duck` own the
reference scripts. Preview WALK uses inertia $0400 (5 ticks); RUN $0600 (3);
ROLL $0600 (1). Runtime supplies speed timing, not the preview values.

WAIT follows the full 59-step, 8-tick S2 intro with seven unique drawings and its
native FE $1C final-28-step repeat recorded in provenance. Current converter
format can only restart the entire intro: **exact partial-loop playback remains
an engine-lane obligation**. The review labels this limitation explicitly.

Per owner direction, ROLL/SPINDASH insert a highlighted blur after each of their
three drawings. Native Tails has three body frames without that interleave;
these six-step sequences are intentional adaptations, not ROM parity claims.
SPRING is one 48-tick drawing per owner direction; S2 Tails actually alternates
$59/$5A twelve times at four ticks. HURT uses the single $5C hurt pose at four
ticks, DEATH $5D at four ticks. SKID retains the four-step 0/1/0/1 sequence at
eight ticks before WALK. Duck/look-up hold at 64 ticks.

S3K `AniTails20` and `AniTails24` supply flight/tired-flight body poses, with
`AniTails_Tail0B` supplying the two-step rotor at two ticks. TALON now replaces that rotor with six integrated wing phases: FLY uses 2 ticks
per frame (12 per beat), tired flight 4 ticks (24 per beat). These are authored
wingbeat timings, not the native rotor clock. Both cycles have a fixed generated
body/head/feet plate and zero bob. Two whole-strip attempts were made per cycle;
FLY uses attempts 2/1/2/2/1/2, tired uses attempt 2 (attempt 1 had a white halo).
Reduction uses a constant 480×480 crop and uniform 10:1 scale, translation from
the goggles only, the existing palette and a fixed [24,24] authoring hotspot.
Cleanup registers the generated body plate and removes tiny detached specks; no
wing contours were painted.

Flight review: `$HOME/scratch/agent-tmp/talon-flight-20261109/review.html`, with
full-tick GIFs, labelled strips, onion skin and body-region identity assertions.
`assemble.py` preserves the authored six-step scripts. `PlayableSheetMaterializer`
uses duration GCD and `PlayableSheetAnimationBinding` copies the full script
to native IDs $20/$24; no Tails frame-count limit was found. This is source
inspection and Python validation, without a Java build or runtime certification.

`presentation/solar.yaml` mirrors MICA's data-table form: six fade tables and
complete dry S2/S3K cycle tables, preserving non-plumage indices. White-gold
plumage and orange flame feather-tip slot 6 are bound through generated TalonSolar
tables (`solar_java.py --check`). No authored underwater or Hyper palette is claimed.

## Review and remaining art weaknesses

Review: `$HOME/scratch/agent-tmp/talon-art-20261109/review.html`.
Scratch includes the native exporter, reduction/adoption/review scripts, every
attempt, exact indexed comparison GIFs and per-frame strips. Each GIF emits all
60Hz ticks as full frames with an exact union palette and no delta optimization;
centisecond timing is rounded cumulatively. Browsers may clamp 10ms GIF delays.

Round 2 replaces only WALK/RUN flats and their derived slopes. Three WALK
attempts: Tails attempt 1 failed arm reversal; Sonic attempt 2 reversed arms but
kept the far forward boot too bright; Sonic attempt 3 won all eight cells.
Two RUN attempts use S2 Sonic's four-phase circular shoe loop; attempt 2 won
all four cells with swept-back wings in up/mid/down/mid flutter order.
Exact round 2 prompts, source hashes, selected mapping metadata and limb table
are recorded in prompts.json/provenance.json. Original generated rows are in
recipe.json. No decoded reference art is committed.

The per-frame review checks passing poses 0/4, near lit left foot with far shaded
right hand forward in WALK 1–3, and the reverse in 5–7. RUN far boots receive
palette-only shadow cleanup after reduction; no limb geometry is painted.
Native box/hotspot is the reduction input; normal pipeline transparent-margin
cropping shifts output hotspots while preserving engine-relative position.
Slopes come exclusively from derive_slopes.py using the new final flats.

Round 2 review: `$HOME/scratch/agent-tmp/talon-art-r2-20261109/review.html`.
All native ticks are full GIF frames with exact palettes. RUN phases 1/3 retain
similar blur silhouettes, distinguished by near/far shading and wing direction;
head height shifts slightly as native-aspect fitting accommodates wing tips.
Runtime slope contact remains an integration check. Other animations, including
the previously noted flight body-scale variation, are unchanged.

Validation: Python discovery under tools/modding passes 49 tests. Assembly
retains 74 frames, 13 animations and the 36-tile maximum. The generic verify.py
assumes MICA's 39px standing height and fails on unchanged TALON wait-00 in
both the baseline and this round; TALON palette/ink, native input geometry,
unchanged unrelated frames and deterministic regeneration are checked separately.
No Java build or GGFP bake was performed.

WALK and RUN were inspected frame by frame. Foot contact/lift phases are present,
but opposite-arm reversal remains too restrained, especially WALK 5–7 and RUN
2–3. WALK 0/4 passing poses are similar; leg crossings in WALK 6 are still small.
RUN 0/2 high forward contacts could be exaggerated further. Native envelopes
and hotspots are retained, so generated head position can move slightly despite
the steadier compact-wing selection. Flight scale drift is addressed by the fixed-crop revision above. These are reviewable core assets,
not a claim that the remaining motion issues or runtime slope contact are solved.

## RUN scale correction (round 3)

RUN 0 reuses round 2 generated strip attempt 2, cell 0, uniformly reduced at
88% of its original scale. Head width (rear goggle strap through beak) is now
11px instead of 13px; torso height is 8px instead of 9px, matching RUN 1–3.
The 27×30 frame uses hotspot [11,11] to align the goggles and beak with the
other phases. All RUN slopes derive from the selected flats; WALK and the
six-phase flight sets retain their approved frames and manifest metadata.
No pixels were painted. Exact source crop, scale and hash are in provenance.json.

## Character-wide consistency (2026-11-09)

WAIT and approved FLY lock the identity. WALK is uniformly re-reduced from its
round-2 original at 80%; HURT/SKID/SPINDASH at 90%, DUCK at 85% of their
original reductions. RUN retains its existing round-3 scale (frame 0: 88%;
others 100%). WALK/RUN slopes derive from final flats using `derive_slopes`.
ROLL, DEATH, LOOK_UP, WAIT and FLY retain their drawings. SPRING selects the
compact launch alternative from generation attempt 2 cell 1 (18 source pixels
per game pixel); tired flight selects attempt 1 individually for all six phases
(6.8:1). Tired attempt 2 and three spring requests remain in scratch for review.
No contours were painted and no native pixels enter these assets.

`consistency-landmarks.json` records manual anatomical landmarks for every
flat frame and source-flat landmarks for derived slopes. Hidden curl/blur
landmarks are null; crouch/curl torso and upward head pitch are explicit pose
exceptions. Comparable skull widths narrow from 10–14 to 10–12px; noncrouched
exposed torsos narrow from 8–21 to 8–10px. Manual placement uncertainty is about
one pixel. These are TALON identity checks: **per-frame native anatomical
landmarks are not certified to ±1px**. Native Tails/Sonic remain parallel pose
references. SPRING's selected raised wings differ from native tucked arms;
runtime slope contact has not been tested in this design pass.

The handoff remains 82 frames / 13 animations; script timing is preserved.
The stale WAIT recipe is reconciled with the existing manifest's loop-back 28,
so rebuilding no longer reverts that previously integrated behavior.

Standard review (native inputs remain outside Git):

```sh
python3 examples/mica-and-talon/art/talon/consistency_review.py \
  "$HOME/scratch/agent-tmp/talon-consistency-20261109"
```

This produces before/after/native 4× lineups, reference-height guides, manual
landmark tables and full-frame exact-palette 60Hz GIFs. Airborne frames use
hotspots; grounded flats use a common floor. GIF centisecond rounding may be
clamped by browsers. Sources, source hashes, reduction factors and selections
are in provenance/prompts; final original indexed rows live in recipe.json.
Review: `$HOME/scratch/agent-tmp/talon-consistency-20261109/review.html`.

## Shared families (2026-11-09)

The sheet now has **169 drawings and 47 canonical keys**: 87 additions, including
one transparent BLANK. `family-generation.json` records each selected generated
attempt, source hash, reference mapping, reduction factor and landmark correction.
There are three generated alternatives per phase, selected individually; PUSH,
HANG and reverse balances also have rejected earlier generations. Raw attempts
and decoded native poses remain outside Git in the review scratch directory.

New sets cover PUSH; all four directional ledge balances; eight FLOAT2 views;
HANG/HANG2; BUBBLE; HURT_FALL; BLINK/GET_UP; DROWN (shared by BURNT); VICTORY;
TAILS_CARRIED; SUPER_TRANSFORM; and flight ascend/carry/carry-ascend plus all four
swim variants. ROLL2/HURT2 and the other canonical synonyms reuse existing art.
New flight variants bind the native single-body phase and use TALON private
presentation scripts for six integrated wing phases at two ticks each. The
approved original FLY and tired FLY cycles retain their authored wingbeats.

Each family phase is supplied once through semantic aliases. Module-owned Tails
correspondences preserve ROM timing, repetitions, LOOP_BACK and terminators;
Sonic correspondences remain separate. `family-script-references.json` records
the representative canonical scripts' native terminators. The existing approved
core cycles retain their intentional adaptations. S1 has no native Tails table;
it uses its Sonic pose vocabulary and the declared creator flight implementation.

Review: `$HOME/scratch/agent-tmp/talon-families-20261109/review.html`.
`family_review.py` produces per-host native-timed, full-frame exact-palette GIFs,
a whole-character baseline lineup and the landmark table. GIF centisecond
rounding preserves total 60Hz timing; viewers can clamp 10ms frames. Speed-driven
PUSH uses the native stationary-wall interval of 33 ticks per phase. Native comparison pixels
are never runtime or repository assets. No runtime route certification is claimed.

Final registration keeps PUSH palms at the native wall plane, carrier boots on
the native handhold baseline and carried hands at the native grip top. The three
new flight variants play all six integrated wing phases at two ticks per phase,
like approved FLY. Host-native body bindings and terminators remain unchanged.
The review verifies every full GIF tick after encoding; speed-driven samples are
labelled, including stationary PUSH at 33 ticks per phase.


## Family scale correction (2026-11-10)

The eleven flagged families were re-reduced from their original GPT Image sources
at uniform anatomical scale, retaining palette/inside-outline cleanup only.
Approved WAIT (31px tall, 11px skull, 9px torso), all 82 core drawings and the
families accepted by the owner are unchanged. Skull readings below are manual
coordinate-panel measurements excluding crest and goggle strap, with about 1px
uncertainty. Torso folds/foreshortening are recorded as unmeasurable rather than
filled with the standing reference. The previous family landmark annotations
are retained only as explicitly unreliable history.

The independent size check is sqrt(mean TALON opaque area / mean reference opaque
area), using the exact original source-pose references, without the separate tail.
Some families use Sonic references (balances, victory, carried, transformation);
the table does not mislabel those as Tails. Anatomy, not area, controls reduction.

| Family | Before | After | Skull width (px) |
|---|---:|---:|---|
| BALANCE2 | 0.948 | 1.047 | 12 |
| BALANCE4 | 0.935 | 1.034 | 11–12 |
| BUBBLE | 0.847 | 1.062 | 12 |
| HURT_FALL | 0.811 | 1.052 | 12 |
| DROWN | 0.799 | 1.050 | 12 |
| FLOAT2 | 0.695 | 1.023 | 11–13 |
| VICTORY | 0.796 | 1.048 | 11 |
| TAILS_CARRIED | 0.900 | 1.047 | 11 |
| SUPER_TRANSFORM | 0.831 | 1.045 | 11–12 (flash hidden) |
| TAILS_SWIM | 0.869 | 1.058 | 11 |
| TAILS_SWIM_TIRED | 0.801 | 1.041 | 11–12 |

Two new BLINK drawings supply S3K $A5 upright yawn and $A6 seated stretch.
Built-in GPT Image supplied three alternatives per pose; the second row wins both,
with uniform 13.5:1 reduction. Exact prompt/source hash and selection are in
`scale-generation-20261110.json` and `family-generation.json`. The three approved
standing blink drawings remain unchanged. S3K's reserved $12 script references
walk mappings $01–$03, so its script-local aliases reuse approved WALK 2/3/4,
consistent with the existing raw-pose profile; S2 keeps its standing blink.

SUPER_TRANSFORM retains the curled wing-wrap anticipation: native S3K Tails $EB
also curls. Native $EC/$ED alternate upright/white flash, now selecting TALON's
existing upright/white drawings. The review decoder now obeys Tails_Load_PLC2's
extra-art bank switch at $D1; its previous main-bank decode was corrupt.
Animation order and timing are unchanged. Carried hands retain the native grip
plane; grounded corrected families use their native foot plane. No native pixels
are committed.

Review: `$HOME/scratch/agent-tmp/talon-scale-20261110/review.html`.
All GIF ticks are full frames, exact-palette RGB checked after encoding, at native
60Hz timing with cumulative centisecond rounding. `lineup-all.png` includes every
canonical animation; `lineup-source.png` includes every source phase, including
slope banks and extra blink poses. Both share engine-centre placement, baseline
and fixed WAIT skull/torso guide boxes. Browsers may clamp 10ms GIF delays.
This is an art/registration review, not gameplay carrying or route certification.

Validation: pipeline + assembly rebuild twice with identical output hashes
(169 drawings, 47 keys, maximum 30 tiles/frame); Python discovery
`python3 -m unittest discover -s tools/modding -p 'test_*.py'`: 52 passed.
Queued Java selection `TestAnimationFamilies,TestCanonicalPlayableSheet,TestMicaCharacterIntegration,TestCharacterArtCoverageAudit,TestNativeImageResourceGuard`
with `-Dmse=off -Dopenggf.headless.gl=surfaceless-egl` and DISPLAY/WAYLAND_DISPLAY
unset: 53 passed, zero failures/errors/skips, all ROM properties found.
This is the requested focused selection, not a full-suite claim.

## Reel portraits (2026-11-10)

See [reels](reels/README.md) for the native-pose-generated 32×32 head and Solar
palette alias, size measurements and exact provenance. Titles remain native.

## Competition miniature bank (2026-11-10)

The independent [Competition bank](competition/README.md) covers all 46 native
Tails 2P selectors: 44 authored, one alias and one empty sentinel. Integrated
wings replace the separate rotor/tail bank, whose 16 rows remain N/A. The native
miniature proportions and actual Competition palette are the reference; the
opaque-area size ratio is 1.045. The shared review includes native and custom
split-screen race captures and full-frame 60Hz motion comparisons.

## Rare native scripts (2026-11-10)

`rare-poses/` records two native S3K redraws ($82 hanging and $8D curling), each
chosen from two built-in GPT Image attempts. The prompt excludes fox tails and
rotors; feathers and wing-hands remain integrated. Their independent opaque-area
ratios are 1.043 and 0.981 relative to the native references.
S2 dummy $0A/$0C contain tail-only mappings and bind the approved blank; $0B
retains its get-up/get-up/blank sequence. Dummy $1D/$1E preserve native phase order
using existing idle/look-up drawings in S2 and walk drawings in S3K. See the
provenance for the mapping-level reasons; these are explicit bindings, not a
claim that legacy unused scripts are reached in ordinary play.

## Ending ownership (2026-11-10)

`TalonEndings` supplies eight S2 ending object poses, three monochrome search
photos, five 24×16 goggles-down cockpit crops, and S3K reunion selectors 3/4.
Built-in GPT Image transformed real-palette native references against the approved
model and WAIT. Native decoded pixels and raw candidates remain in external
scratch; provenance and indexed authored images live in `art/talon/endings/`.
The first configured sidekick owns photos and the new optional
`S2_TORNADO_PILOT` set. The ending and SCZ/WFZ renderers replace only the cockpit
piece, keeping native sequence clocks, vehicle pieces and DPLC decisions.
Missing sidekick/slot retains native art. The unpublished API remains 0.7.0.

Ending object/reunion sets apply to TALON's solo Tails route; MICA's native S3K
reunion does not include a sidekick portrait. S1 credits demos use the configured
team's ordinary playable sheet. S2 credits are text slides, and S3K scrolls ROM
text (`S3kCreditsState`); neither has a credits gameplay bank to redraw.
Presentation-lane merge must add `TalonEndings.load(assets)` to its endings map;
this lane leaves the character definition and TalonPresentation untouched.

The S3K reunion plane also has a separate sidekick pilot (native selector 12).
`S3K_TORNADO_PILOT` aliases the generated S2 side-view cockpit with hotspot
(12,8). Native alpha geometry is identical; native palettes differ. This keeps
TALON visible in MICA's team reunion without adding a reunion portrait actor.

## Presentation (2026-11-10)

`TalonPresentation` supplies independent host HUD/life and 1-up portraits, sign
faces/spins/backings (including S2 alternate), continue drawings, save portrait,
18 half-pipe mappings and 11 nonblank Blue Sphere mappings. The S3K slot bonus
uses approved ROLL aliases at native Tails mappings $96–$98, plus approved
duck/balance aliases at $99/$9A for the shipped goal-fade held-bank selectors; S1 maze play reuses
the canonical ROLL sheet. Mapping 0 in the
12-entry Blue Sphere bank is deliberately empty. The separate native tail banks
are N/A: wing motion belongs to TALON's body. Endings/competition remain other
lanes. The standalone Blue Sphere completion pose is authored reserve art; the
engine currently has no standalone Blue Sphere completion-screen host.

Art comes from two built-in GPT Image native-pose attempts per set, using the
model sheet and approved WAIT. `presentation/generation.json` records prompts
and selections; raw generations and native comparisons stay in
`$HOME/scratch/agent-tmp/talon-presentation-20261110/`. The selected original
indexed rows in `presentation/native-pose.json` reproduce all PNGs:

```sh
python3 examples/mica-and-talon/art/talon/presentation/rebuild.py --check
```

S1 adds generated 6×8 T/A/L HUD glyphs; native O/N complete TALON in 32 pixels.
S2 uses its native small-font TALON. S3K's native glyph widths are T=8, A=7,
L=6, O=7, N=7: TALON needs 35 pixels; TALO fits the native 32-pixel cell at
28 pixels. The composer deliberately gives native glyphs precedence, so
supplementary glyphs cannot silently replace or squeeze them. Results retain
full TALON. No API surface expansion is needed: the existing character-owned
`StageSpriteSet` contract now routes the native Tails actor independently.

S2/S3K select TALON's Tails banks when he leads solo and when he follows MICA.
MICA retains leader HUD, signs and results. Half-pipe team setup resolves declared
archetypes, and its domestic/overseas name banks remain independently owned. Continue follows each native actor,
including the native Sonic actor on Tails-only screens. Save-menu custom
portraits use canonical right-facing art and mirror the right team member inward.

Review: `review.html` in the scratch directory, with native comparisons,
per-tick motion previews, common origins and the independent area-ratio table.
Motion previews preserve native phase order; Blue Sphere speed is illustrative
because its production timer depends on velocity. They are not route certification.


## Object poses (2026-11-10)

The normal sheet adds 111 object views while retaining 47 canonical keys.
See [inventory](object-poses/inventory.md), [per-ID vocabulary](object-poses/native-inventory.csv),
[provenance](object-poses/provenance.json) and [size check](object-poses/size-check.json).
Native Tails body mappings use the real S2/S3K SonicAndTails palette, enlarged
8×; built-in GPT Image supplied two alternatives per base pose. The gravity
loop's first attempt lost back-facing orientation; the second is selected.
Five overhead-grip poses required a further two-alternative redraw, then
uniform re-reduction (1.15× hang / 1.12× bar-end) after the independent size check
flagged the first reductions. Hands remain registered to the native grip plane.
No contours were painted, and native reference pixels remain external.

Sixteen exact quarter-turn/mirror correspondences use the indexed rotation tool;
other native turns are perspective changes with distinct silhouettes, retained
as generated views. Approved idle, legacy hanging and curling drawings are
explicit aliases. Integrated wings replace fox tails and rotors. Raw flight-body
aliases use separate semantic keys so they cannot collapse the six-step wingbeat
through the canonical family's one-body native script.

Review and paired production EGL captures:
`$HOME/scratch/agent-tmp/talon-object-poses-20261110/review.html`.
Positioned native object encounters demonstrate binding, not complete routes.

The final audit replaces two blanket fallback rows with **68 S2** and **229 S3K**
direct aliases; only the six separately owned title rows remain fallback.
Validation: 123 selected Java tests passed, then 9 raw-alias/audit tests passed
after the final descriptor fallback correction, all with zero skips. Python
modding discovery passes 56 tests. The first Python run flagged the old 171-frame
count; its legitimate 282-frame assertion is updated. No API signatures changed.
Native Tails retains the same art object; sparse creator aliases use descriptor
fallbacks, unknown byte 255 uses WAIT, and byte zero remains hidden.

Final per-set area ratios range **0.956–1.088**; S2 tumble independently measures
**0.958** against S2 Tails. The review includes the full size table and native
comparison frames at 3×. Corkscrew, DEZ gravity hub, FBZ chain grip and S2 spiral
videos contain all 180 ticks at 60 Hz, encoded losslessly in RGB; a decoded
frame from every video matches its RGB source byte for byte. Native foreground
occlusion is retained. The corkscrew review adjusts only the rendered camera Y,
then restores it before the next simulation step; objects select every pose.

## Paired carry artwork (2026-11-10)

MICA's three hanging poses and TALON's six normal/ascent carrier phases were
redrawn together against the real-palette S3K pair at 8×, using fresh built-in
GPT Image candidates and approved model/WAIT references. Both white gloves meet
his shoe soles while his integrated wings flap; her legs remain clear below.
The native centre separation stays 28 pixels. No creator movement offset or
engine/API change is involved. S1/S2 have no native carry; their mod-defined
flight capability borrows S3K's model. Positioned GHZ/EHZ probes did not latch,
so only the AIZ recording is carry encounter evidence.

The carried script now follows `AniRaw_Tails_Carry`'s sixteen entries at twelve
ticks each, including its repeated phases, rather than the ordinary four-step
animation-table fallback. MICA omits the three family-phase aliases for this
one script so the existing binder retains the explicit authored raw order;
her direct `hang/0`–`hang/2` aliases remain. A separate canonical
`GLIDE_SLIDE` alias preserves the four-step Sonic-table $23 entry;
this brings MICA to 53 keys without adding drawings. Other hanging encounters retain
those same drawings. Native Sonic/Tails art and clocks are unchanged.

Fresh style candidates use selective material-shadow contours, compact
upper-left highlights and stepped clusters, with 12 MICA / 10 TALON opaque
colours. The reducer preserves these contours via explicit `outline_mode:
material`; historical recipes retain their ink default. All 36 normal/ascent
carrier/hanging combinations have touching grip windows. The independent
sqrt(mean opaque-area ratio) is MICA **0.984**, TALON **1.115**; his integrated
wings explain the extra area. These are existing authored slots: audit rows
move **0**, with no fallback-count change. Measurements, prompts, rejected
attempts and selected hashes are in `carry-generation.json`,
`carry-prompts.json` and MICA's `carry-contact-check.json`.

External review: `$HOME/scratch/agent-tmp/mt-carry-20261110/review.html`, including
unlabelled native/pair gates, onion skins, WAIT anatomy guides and before/after
60 fps lossless RGB AIZ carry clips. These verify a positioned encounter and
art binding, not complete routes or identical species anatomy.

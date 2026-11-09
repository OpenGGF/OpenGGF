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

82 indexed drawings, one transparent plus 15 opaque Mega Drive ladder colours:
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

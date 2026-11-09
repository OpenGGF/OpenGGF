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

74 indexed drawings, one transparent plus 15 opaque Mega Drive ladder colours:
WAIT 7; WALK 8×4 orientations; RUN 4×4; ROLL 3 plus blur; SPINDASH 3 plus blur;
SPRING/HURT/DEATH/DUCK/LOOK_UP one each; SKID 2; TAILS_FLY 2;
TAILS_FLY_TIRED 2. Canonical names follow `CanonicalAnimation`; `FLY_TIRED` is
not an accepted enum name. Flight wings are integrated; no tail sprite exists.
`talon-custom-frames.json` is intentionally empty: all supplied poses are canonical.
No character-class binding or Java changes are part of this handoff.

Round 1 source selection: WAIT and WALK strip attempt 2; RUN attempt 3; ROLL and
SPINDASH attempt 2; static poses attempt 1 except spring; flight and tired flight
attempt 2. Each static pose had two strip attempts. Spring then had two additional
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
`AniTails_Tail0B` supplying the two-step rotor at two ticks. TALON replaces that
rotor with two integrated up/down wing poses; tired body timing is 12 ticks.
Flight flapping is therefore deliberately very fast at native timing.

`presentation/solar.yaml` mirrors MICA's data-table form: six fade tables and
complete dry S2/S3K cycle tables, preserving non-plumage indices. White-gold
plumage and orange flame feather-tip slot 6 are data only; no binding, underwater,
Hyper or runtime visual certification is claimed.

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

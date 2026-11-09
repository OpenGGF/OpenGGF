# TALON core playable art

Original peregrine courier art generated with built-in GPT Image against the
approved `../../concept/talon.png`, using real-palette native Tails poses at 8×
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

Source selection: WAIT and WALK strip attempt 2; RUN attempt 3; ROLL and
SPINDASH attempt 2; static poses attempt 1 except spring; flight and tired flight
attempt 2. Each static pose had two strip attempts. Spring then had two additional
alternatives in one generation, with the first selected. Its initial float-like
reference was rejected and replaced with actual spring mapping $59 (22×47).
The rejected second WALK request was blocked by the image service before output;
there are two usable WALK attempts, three requests. Raw attempts stay in scratch.

The reducer fits each generated cell to the native aspect before reduction;
it never squeezes a wide pose into a narrow cell. Cleanup changes only front/far
shoe palette shading on WALK 5–7 and RUN 0–1; no limbs were painted. Slope art is
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

WALK and RUN were inspected frame by frame. Foot contact/lift phases are present,
but opposite-arm reversal remains too restrained, especially WALK 5–7 and RUN
2–3. WALK 0/4 passing poses are similar; leg crossings in WALK 6 are still small.
RUN 0/2 high forward contacts could be exaggerated further. Native envelopes
and hotspots are retained, so generated head position can move slightly despite
the steadier compact-wing selection. Flight up/down drawings still change body
scale somewhat when fitted into native bounds. These are reviewable core assets,
not a claim that the remaining motion issues or runtime slope contact are solved.

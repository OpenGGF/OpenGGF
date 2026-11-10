# MICA playable art

The 274-frame / 53-key sheet combines approved core, slope and earlier native
pose batches with the 2026-11-10 object-pose closure. Rebuild with the maintained
playable-art `pipeline.py`, then `assemble.py`; the recipe contains original
indexed pixels only. No native reference pixels or raw generations are packaged.

`object-poses-wave4/native-pose-provenance.json` records native mapping indices,
selected attempts, hashes and cleanup; `prompts.json` records the exact prompts.
Sixty new object drawings, one legacy inverted run and five normal transformation
phases are generated against native 8× nearest references and the approved model.
Three object aliases reuse existing spin/grip art. Frame/alias additions form a
single appended block to ease integration with the powered-art lane.

Corkscrew and gravity-loop references include perspective changes and distinct
limb placements; the outstanding native poses are not duplicate planar quarter
turns. Their silhouettes were checked for exact 90°/mirror equivalence before
selection. Existing approved slope and batch-3 deterministic rotations remain
unchanged. The S3K extra art bank supplies native mappings at/above $DA.

Reduction preserves aspect and uses the locked palette, alpha threshold and
inside ink. The $B5 overhead pose is uniformly reduced from 50 to 48 pixels to
fit the existing atlas; $93 is re-reduced 6% larger following the independent
opaque-area check. See `object-poses-wave4/size-check.json` for delivered set
measurements. Review raw attempts and native pixels stay in the task's external
scratch directory, linked in provenance. Native script timing and camera/facing
stay host-owned; these are artwork obligations, not all-route certification.

Blue Sphere's eleven nonblank rear-view poses use a fresh native-style generation
set with native per-frame colour budgets, exact native script previews, aligned
style gates and a positioned run/turn/jump capture. See [the wave3 handoff](wave3/README.md#blue-sphere-native-style-redraw-2026-11-10).
The set's independent area ratio is 0.983; native selectors and hotspots stay
unchanged. Coverage remains authored rather than claiming new fallback closure.

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

The Blue Sphere runtime follow-up fixes creator pixel ownership before the later
shadow pass; the PNG palette was already supplied correctly. The current review
uses real stock/creator lockstep captures, not the superseded source-PNG style
gate. Exact capture sampling matches all 879 opaque standing pixels; native
mapping selections and white-fade passes match. See the wave3 runtime checkpoint.


## Native special-stage orientation checkpoint (superseded art)

MICA and TALON now supply dedicated final-orientation drawings for Blue Sphere's
mirrored selectors 6–8 and all 40 S2 half-pipe H/V/HV combinations per character.
The host retains the native mapping sequence, motion, clocks and flip flags;
only the selected creator pixels change. Native Sonic/Tails still flip their
ROM art. The original sprite-list order, priority, palette and fades remain in
use. The additive unpublished-candidate API preserves the original one-component
`StageSpriteSet` record and adds immutable orientation helpers.

There are 86 explicit orientation drawings and 25 refreshed base poses. The
new orientation audit rows are separate from existing native-ID coverage:
86 ordinary-selector fallbacks become authored; no native IDs are added.
Fresh real-palette 8× native-pose generations, rejected attempts, indexed rows,
landmark/native origins and independent area measurements live in each
character's `oriented-stage/native-pose-generation.json`. Rebuild/check with
`python3 tools/modding/playable_art/oriented_stage.py <art-directory> --check`.

The external review is `$HOME/scratch/agent-tmp/mt-bluesphere-flip-20261110/review.html`.
Its acceptance gates use real capture pixels or the exact sampled capture
palette, full-60-Hz lossless RGB lockstep movies at 3×, every-tick two-cycle strips,
centroid plots and per-tick selector diffs. The positioned probes certify these
presentation encounters, not complete special-stage routes.

## Current locked special-stage body-motion bank (2026-11-10)

`consistent-stage/` supersedes `oriented-stage/`. The owner rejected independent
phase identities in `176d974b06`, then the static body in `7190f49ae4` / integration
`9318420b42`. This correction retains the same canonical generated dome, ears, scales, torso and short tail root.
Every upright pose now rotates about the hips, with native-measured sway and bob,
using Scale2x/nearest RotSprite-style reduction. Broad material lighting is
reprojected in screen coordinates; the original specular stays upper-left.
Generated limbs attach at the rotated shoulders/hips. Rear shoes and appendages
use ordinary body occlusion while camera-facing shoes retain native contacts.
Canonical source hashes and all native jump/curl drawings are unchanged.

Head/ear and torso IoU against the posed canonical is 1.000, with zero indexed
colour differences. Maximum full-layer lean error is 1.011/1.661° S3K/S2;
every layer and visible-composite angle is within ±2° of Sonic, with matching
sign. Shoe-contact boundary error is 1.803/2.500 px S3K/S2, below 3 px;
all attachment gaps are at most 1 px. Independent size ratios: 0.9988 S3K / 1.0101 S2.
No extra shear or squash is fitted. S2's native body also leans, so its upright,
diagonal, horizontal and H/V/HV phases receive the same correction.

Sources, immutable canonical hashes, numeric native PCA references, hip/shoulder
landmarks and deterministic recipes are in `consistent-stage/native-pose-generation.json`.
No native pixels or raw generations ship. Rebuild/check:
`python3 tools/modding/playable_art/locked_stage.py examples/mica-and-talon/art/mica/consistent-stage --check`.
Measure: `python3 tools/modding/playable_art/locked_stage_review.py examples/mica-and-talon/art/mica/consistent-stage --out /scratch/mica-motion.csv`.

Review: `$HOME/scratch/agent-tmp/mt-bluesphere-tilt-20261110/review.html`.
All 144 unlabelled native/new gates use real capture crops or their exactly
sampled palette. Native-motion tables/plots, four-row 6× cycles/turns/jumps,
stock/mod 60 fps MP4s at 3× and frame-by-frame fades accompany the colour,
foot/shadow and selector diffs. Native Tails' separate appendage remains visible
in comparison rows; TALON has folded tips. All palette/foot/fade checks pass,
with zero mapping/motion/fade timeline differences. No API or engine changes;
these authored replacements move 0 audit rows. See the design audit for exact
pixel counts, tests and positioned-encounter limits.

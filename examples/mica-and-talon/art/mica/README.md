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

## Current locked special-stage bank (2026-11-10)

Owner review of `176d974b06` rejected temporal head/ear and shading drift. The
current runtime bank is `consistent-stage/`, superseding `oriented-stage/`.
A fresh canonical dome/ears/scale rows/torso is reused exactly for every upright
phase. One separate canonical scale-ball supplies jumps, with native-measured
glint sizes/heights and blur envelopes. Generated calves, white fists and coral
boots track Sonic's native phases; the short tail stays behind the legs. S2
rotates the same identity geometry while keeping its broad light field fixed.

The per-frame head/ear layer IoU is 1.000, with zero RGB differences. Maximum glove
centroid errors are 0.959 px S3K/0.595 px S2; shoe-contact boundary errors reach
1.803/3.163 px. Independent size ratios are 0.9954 S3K and1.0079 S2. The source
components,32-candidate shared generation provenance, native numeric landmarks
and deterministic placement/glint recipes live in `consistent-stage/native-pose-generation.json`.
No native pixels or raw generations ship. Rebuild/check with
`python3 tools/modding/playable_art/locked_stage.py examples/mica-and-talon/art/mica/consistent-stage --check`.

The shared real-capture review is
`$HOME/scratch/agent-tmp/mt-bluesphere-consistency-20261110/review.html`.
It includes all 144 native/new style gates, four-row 6× strips,60 fps paired films,
head-material centroid plots, exact runtime colour samples, shadow/fade pixels
and zero per-tick main mapping/motion differences. The existing draw/API path is
unchanged; these replace authored rows, so coverage moves 0. Limits and the
rejected independent-generation approach are recorded in the design audit.

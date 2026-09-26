# S3K Mushroom Hill Act 1 coverage matrix

Canonical act `S3K_MUSHROOM_HILL_1`, ROM `$700`, engine zone 7 / act index 0.
Incoming normal level entry; outgoing seamless MHZ2 (`$701`) with coordinate
rebase, carried objects/resources and in-level title ownership. Status: integrated
implementation reviewed historically; current campaign act certification pending.

## Claims

| Claim | Evidence / state |
| --- | --- |
| Implemented | Original direct audit found no further missing Sonic/Tails feature; later miniboss lifetime, explosion and transition-identity fixes landed. |
| Cold-reachable | Fresh320px Sonic-solo controller route reaches the miniboss and MHZ2 (2026-09-23); no position/clock/ring/health seed. |
| Completable | Current controller route completes all six boss hits, sign/results, seamless MHZ2 load and released player movement. Other configurations remain open. |
| Native-accurate | Disassembly-backed review plus local regressions; frame-perfect/native visual certification not claimed. |
| Rewind/standard | Several live interaction and graph checks exist; breadth and complete before/active/after obligations remain incomplete. |

## Obligation mapping

| ID | Concrete contract and inspected source / historical evidence | Remaining obligation |
| --- | --- | --- |
| ENTRY | `TestSonic3kMHZEvents.act1LevelLoadPinsCameraAndMinXToRomMhzStart`; height-based minimum X matches `sub_54B80`, whose branch is standalone mode rather than character identity. | Current normal/alternate/checkpoint load and donor/team breadth. |
| OBJECT | Mushroom, vine, swing-bar, pulley, wall, pollen and badnik families listed in the original audit. `TestS3kMhzSwingBarLiveRewind.verticalSwingBarHangStateSurvivesRewindRestore` captures a real held player, recreates the bar and resumes through jump release; horizontal case mirrors its own control path. | Per-placement/subtype inventory and act-specific breadth; other families' before/active/after replay. |
| EVENT | `TestSonic3kMHZEvents.act1ScreenEventArmsMinibossArenaAtRomCameraThreshold`; cutscene `_unkFAA9` latch belongs to the captured runtime and resets on full reload. `TestMhz1CutsceneReferenceClosure` and `TestS3kMhzCutsceneGraphRewind` cover references. | Current production threshold/negative authority and reset matrix. |
| CAMERA | `act1MinibossSpecialEventLoopsArenaAtRomThreshold`, explicit repeat-participation regression and the local moving-camera presentation audit. | Every required width through arena lock/repeat/release plus rendering. |
| BOSS | `TestMhzBossObjects.mhzMinibossFatalHitQueuesRomFadeExplosionAndSignpostHandoff`; `TestMhzMinibossLifetime`, `TestMhzMinibossDefeat`, `TestMhzMinibossExplosionAllocation` address fatal update/slot order and explosions. | Current encounter rerun and representative controller fight, phase/rewind breadth. |
| LIFE | Full reload resets the door latch per original audit; death/checkpoint behavior cannot be inferred from object recreation alone. | Dedicated short checkpoint, death/restart and repeated-load checks. |
| LOAD | `act1BackgroundEventRequestsRomSeamlessAct2ReloadWhenResultsSignalArrives`; `$4200` rebase and in-level title handoff. `TestS3kFixedSstTransitionRewind` executes actual MHZ act replacement and retains one pollen owner/identity. | Re-run production handoff, readiness and supported configuration cross-product. |
| REWIND | Swing-bar held-state tests, flame/shard/cutscene graph tests and post-transition capture → restore → capture evidence. | All applicable spots, deterministic forward replay and width/donor/team axes. |
| PRESENT | `SwScrlMhzTest`, `TestS3kMhzPatternAnimation` and ROM-backed boss art tests; no native AnPal cycle is missing. User manually confirmed corrected miniboss explosions in the historical record. | Current captured moving gameplay, viewport edges and audio ownership. |
| ROUTE | Prior component sequence reaches results and MHZ2. | Current representative real entry → mandatory mechanics → boss → next playable act; component transitions are insufficient. |
| ORACLE | `mhz-analysis.md`, completion audit and focused correction records identify owning ROM routines and rejection evidence. | Current bounded regression evidence; respect stopped trace scope. |

Historical follow-ups:
[Miniboss lifetime](../2026-09-12-mhz1-boss-lifetime.md),
[miniboss defeat](../2026-09-12-mhz1-boss-defeat.md),
[transition identity](../2026-09-12-mhz1-transition-rewind.md).
The last records 348 focused passes on its stated candidate; it is not a current
branch execution receipt.

## Scope and evidence limits

Locked-on S3&K ROM, native `FixBugs=0`; Sonic, Tails and Sonic with Tails are the
accepted implementation scope. The [original completion audit](../../research/s3k-zones/mhz-completion-audit.md)
records the user's exclusions: no further Knuckles-route work after the camera
correction, no standalone S&K entry, and no further trace-driven development.
These are accepted scope limits, **not evidence that Knuckles is unsupported**.
The console pulley debug-menu unlock is excluded; engine debug controls remain.
Do not restart excluded trace work or expand the route scope by implication.

This matrix was assembled on 2026-09-23 from current test source and historical
validation records. **Current component execution:498 cases pass, zero skips, on2026-09-23 at19:21 BST**
(`b6c1147a2` plus campaign edits;37 explicitly selected non-trace MHZ classes).
The campaign audit records the selection and limits; cold route authoring is ongoing. Existing
unit/graph tests are candidates for the listed contracts, not proof of complete
act coverage. The historical 622-case focused pass and 656-case guards in the
original audit belong to their stated September 12 candidates. Historical full
runs were red/interrupted; their failures were not all attributed to a matched
baseline. They cannot be relabelled a green campaign baseline.

## Breadth ledger

| Dimension | Accepted scope / remaining evidence |
| --- | --- |
| Width | Supported presets are 320, 352, 400, 528, 800 through the supported configuration. Current per-act entry/lifecycle cross-product, meaningful traversal and rewind coverage are unassessed; historical local camera checks do not fill this row. |
| Donor | Native plus every currently supported S3K movement donor; resolve support from production before selecting cases. No per-act donor certification from the reviewed records. |
| Main | Sonic and Tails, plus Sonic/Tails native pairing; Knuckles implementation expansion is excluded as above. Existing locked-on Knuckles camera test remains a regression, not route coverage. |
| Team | Solo/native pair are reviewed implementation paths. Mixed, duplicate and maximum participant shapes need current contract-derived authority/lifecycle checks; absence of evidence is not an unsupported classification. |
| Lifecycle | Checkpoint respawn, death/restart, alternate entry where applicable, repeated loads, and exit behavior need their own short cases and width × supported-donor coverage. |

## Rewind ledger

Keep before/active/after spots independent of complete routes. Existing graph
reconstruction is useful but does not replace live forward replay. Required
remaining coverage includes camera lock/release, world/terrain/plane mutation,
checkpoint/death reset, resource work and team-sensitive ownership. Do not infer
full coverage from the generic object inventory or a class whose name contains
`Rewind`. Use the [level standard](../../../guide/contributing/level-test-standard.md)
for completion criteria and report skips/configuration explicitly.

## Next validation

Inspect the owning tests' actual fixtures/configuration, run a focused ROM-backed
selection through `tools/testing/maven_queue.py`, and record command, commit,
results and skips. Obtain representative controller-driven production completion
and presentation evidence without reinstating the excluded trace campaign.
Fill the breadth and lifecycle gaps above before claiming the act meets the
standard. The [campaign audit](../../audits/2026-09-22-sk-zone-bring-up.md) owns
combined integration/verification; this documentation does not supersede it.


### 2026-09-23 — fresh MHZ1 completion and delayed duplicate owner

`routes/s3k/mhz1-sonic-fresh-320.script/.bk2` preserves9301 controller passes
from normal locked-on level select, Sonic solo, donor off,320px. No position,
clock, emerald, ring or health seed is supplied. The route uses the actual
pulley grab/down-pull/release, sticky-vine spindash and terrain loops, reaches
the miniboss with47rings, lands all six hits, completes sign/results and moves
more than300px after the seamless Act2 handoff. There are no deaths. The
BK2 was compiled and round-tripped through the production authoring tool.

Input-authoring stalls were not runtime evidence: periodic jumps shed loop
momentum; running into the spring shaft skipped the adjacent pulley; the sticky
vine needs a spindash. A broad “near twisted vine” authoring heuristic also
suppressed a needed jump on a different vertical path and was removed. No
engine movement/collision or boss health was tuned to complete the route.

`TestS3kMhzAuthoredRoute` reached and replayed the pulley, vine charge, first
boss hit, last-hit approach and defeat. Its first post-load restore at frame9094
failed: the live object list contained the same Insta-Shield owner twice, while
restore retained one. The short `TestS3kFixedSstTransitionRewind` reproduction
now steps the player after manager replacement and fails in both MHZ and HCZ
(expected one owner, actual two). Earlier tests captured before this delayed
registration and missed it.

Root cause: `rebuildManagersForActTransition` already restores and reconciles
exact-SST owners. The subsequent `applySeamlessOffsets` marked Insta-Shield
unregistered for every policy except ALL_LIVE_SST, including PERSISTENT_EXACT_SST.
The next player update registered the identical object again. The flag reset
now applies only to legacy PERSISTENT_ONLY; both exact policies retain the
existing identity and their already-invalidated DPLC. The code explains that
ROM Load_Level retains the fixed shield slot outside Dynamic_object_RAM.
This is a shared policy correction, not an MHZ-specific exception.


Final focused verification at19:43 BST, Java21 and the absolute S3K ROM:
`maven_queue.py -Dmse=off -Dtest=TestS3kFixedSstTransitionRewind,TestS3kMhzAuthoredRoute,TestLevelSeamlessTransitionExecutor,TestFbzActTransitionHeadless,TestS3kLrzSeamlessActChangeHeadless,TestS3kDezSeamlessActChange test`
passes26cases, zero failures/errors/skips. This includes the full controller
route and45-input restore/replay at all six spots, the two delayed-owner
regressions, and neighbouring transition consumers. The earlier498-case MHZ
component selection preceded this one-condition shared-policy fix. Neither
selection is the campaign broad run.

The corrected external `mhz-bring-up/campaign-20260923-act1-cold-320-fixed`
movie covers frames7240..9300 (2061images) from9301ordinary input passes; all
rows have no death/follower. Its CSV is byte-identical to the pre-fix capture.
Full ffmpeg decode passes; fight, defeat and released-Act2 stills were inspected.
The input source/BK2 and provenance accompany it. Native visual parity, other
characters/widths/donors, full Act2 traversal and load-history isolation remain
separate obligations. No excluded Knuckles or trace work was reinstated.


Subsequent Act2 entrance fix (2026-09-23): the same9301 inputs now legitimately
finish inside Knuckles's press sequence. The earlier released-title spot still
proves incoming control release; the final pass is no longer required to remain
uncontrolled. The campaign's new leaf-blower capture extends to9901 passes,
showing the lift and upper-route movement. The Act2 matrix owns that coverage.


### 2026-09-26 — physical checkpoint lifecycle coverage

`TestMhzCheckpointRoutes` inventories all nine ROM-authored physical posts
(five in Act 1, four in Act 2) against the independent disassembly placement
tables. Each post has a declared local controller approach for Sonic, Tails and
Sonic with Tails, with whole-registry capture/restore and two deterministic
forward replays. These approaches do not establish cold reachability.

The lifecycle matrix uses physical contact, then two production pit-death
stimuli through `GameLoop` and its normal fade/title/reload flow. It covers
320/352/400/528/800 for the three accepted native rosters and the current
launch-profile-supported S1/S2 donor rosters. Assertions cover saved position
and index, object/runtime replacement and event binding, roster identity,
viewport, donor rules and decoded participant art, control release and outgoing
history isolation. Scripted checkpoints remain separate; the MHZ2 entrance
index 7 retains its existing cutscene/admission tests.

The final Act 2 post needs the existing upper-passage jumping approach from
(15008,704); a flat post-height setup misses its loading band, and moving that
flat setup farther left falls into the lower passage. No runtime workaround was
added. This closes the tested local physical-post lifecycle cross-product, not
all alternate-entry, mixed/duplicate/maximum-team, event-latch or presentation
obligations. The campaign audit records execution and integration evidence.

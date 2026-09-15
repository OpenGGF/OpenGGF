# FBZ Act 2 coverage matrix

Laser-room graphics follow-up (2026-09-14): the subboss child family retains
native high sprite priority. The reversed-plane renderer reads the moving
background and stationary foreground from their independent ROM coordinates.
`TestFbzAct2Subboss` checks the child priority contract; `TestFbzBossPlanePixels`
compares actual room-exit tile pixels with ROM layout/pattern/palette data.
See the [graphics audit](../../audits/2026-09-14-fbz2-laser-room-graphics.md)
for execution state and limits; the inherited full-route visual gaps remain open.

**Known donor challenge — leave unchanged (user decision, 2026-09-14):** the
right-to-left early `$0DC0` elevator crossing with S1 Sonic is feasible but
requires a demanding run-up and roll. The tested faster approach has a five-frame
safe arrival window (~0.083 seconds at 60 Hz). See the
[retained challenge and decision](../../research/s3k-zones/fbz-outstanding-actions.md#known-s1-donor-challenge-early-act-2-elevator-leave-unchanged).


Hanging-handle presentation follow-up (2026-09-14): horizontal `$72` grab regions
must not submit vertical chain art (`Obj_FBZChainLink` → `loc_3AA5A`).
`TestFbzRailAndChainPlatforms` covers all six used horizontal subtypes and
retains a positive vertical descent/mapping check. This local render-only change
preserves the existing interaction/rewind obligations and inherited visual gaps;
execution evidence is in the [completion record](../../plans/2026-09-14-fbz-completion.md).

Spike-art follow-up (2026-09-14): the ROM-backed
`TestSonic3kObjectArtProvider#fbzSpikeOrientationsUseTheirNativeTileBanksInBothActs`
passes all eight mapping frames in both acts, including piece dimensions/flips,
live upright `$200` and sideways `$494` pattern references, and exact refresh
range boundaries. The S1 elevator video is recaptured after this correction;
this local check does not close the remaining native visual checkpoint matrix.

Status: **partial; not certified**. Source inventory inspected on 2026-09-14
against develop `435ec2e68`, with focused completion evidence subsequently
recorded on `c31bdbd7a` plus local task changes. Only the explicit execution
ledger below makes current PASS claims; other named tests are source contracts.
See the [completion record](../../plans/2026-09-14-fbz-completion.md) for commands,
source changes and delivery status.
Inherited results and exact commands remain in [outstanding actions](../../research/s3k-zones/fbz-outstanding-actions.md)
and [test lanes](../2026-09-13-fbz-test-lanes.md); their commit/date limits apply.
Use the [level standard](../../../guide/contributing/level-test-standard.md).
ROM act indices are zero-based; displayed act numbers below are one-based.

Canonical slot: `S3K_FLYING_BATTERY_2`, FBZ ROM act 1. Entries include cold
level select, Act 1 results-owned reload and checkpoint respawn. Completion owns
the subboss, plane transition, end boss, final capsule and Sandopolis Act 1 request.
See [Act 1](s3k-fbz-act1.md) for shared transition and visual-tooling evidence.

## Character and configuration routes

| Route | Concrete existing contract and setup | Remaining obligation |
| --- | --- | --- |
| Sonic solo / Sonic + Tails | `TestFbzCompatibilityMatrix#configuredTeamSurvivesSharedPlaneAndBossState`; cold native start, ordinary inputs driven by live object geometry via `TestFbzAct2TraversalPreboss`. `nativePairCompletesRepresentativeRoute` runs in ordinary lane | Final eleven-route focused matrix PASS, including mandatory milestones and final SOZ request; broader act certification remains open |
| Mixed / maximum / duplicate followers | Same exhaustive method: Sonic with Tails+Knuckles, Tails+Knuckles+Sonic, or three duplicate Sonics; captures team identities and CPU ownership | All five team rows PASS with shipped sidekick death/respawn and final team-survival assertions; these are Sonic-main routes, not Tails/Knuckles-main completion |
| Tails solo | `TestFbzMainCharacterCompletion#tailsCompletesColdAct2ThroughCapsuleAndSandopolisRequest`; independently asserts concrete Tails leader, solo team and native cold ROM start, then every mandatory route/boss/capsule/SOZ milestone | PASS in the focused native-character run on `0a078cf87` plus controller/setup changes, zero skips for this method. This does not close all visual/rewind breadth |
| Knuckles solo | `TestFbzMainCharacterCompletion#knucklesCompletesColdAct2ThroughCapsuleAndSandopolisRequest`; concrete native Knuckles leader, cold ROM start, all mandatory interactions, boss defeat, capsule and SOZ request | PASS on `190cbd408` plus setup/boss/controller changes, zero skips. Native lower jump/glide inputs use live geometry; total route remains below 35,400 frames. Broader visual/rewind obligations remain open |
| Width axes | `TestFbzCompatibilityMatrix#viewportKeepsWorldThresholdsCullingAndBossContainment`: additional 400/512/640/800 complete routes; 320 represented by native team route | All four additional-width rows PASS; pixel width/culling acceptance remains separate from camera and gameplay assertions |
| Donor axes | `donatedMovementProfileCanReachTheMandatoryBossEntryWithoutSpindash`: S1/S2 ordinary-input complete route, actual donated rules and assist evidence; off covered by solo team | S1/S2 focused complete routes PASS (ledger below), replacing the inherited S1 squeeze frontier. Ordinary crossing must consume no assist and preserve no-spindash and acquired/exited-car evidence |
| Short lifecycle breadth | Standalone synchronous transition preflights sweep teams, widths 320/400/512/640/800 and off/S1/S2; Act 1 results test now includes 512/640 in its passing 105-case product plus native reset | `TestFbzEntryReloadResetMatrix` passes the full 2-act × 5-width × off/S1/S2 cold-entry/reload/reset product with concrete Sonic+Tails and two reset cycles per row. Other main-character/team products, checkpoint/death and traversal remain separate; see Act 1 matrix |

## Obligation map

| Obligation | Named test contract / independent oracle and setup authority | Limit or open work |
| --- | --- | --- |
| ENTRY / LOAD | Native-character entry; `TestFbzAct2RomRuntimeLifecycle#act2LevelEventInitializationClaimsNativeFirstDynamicSlotBeforePlacement`, `priorAuthoritativePlaneDoesNotLeakOwnershipIntoFreshAct2EventState`; ROM-backed fresh event binding | Cover cold and incoming-transition entries across required breadth with current execution evidence |
| INTERACT | `TestFbzAct2RouteHeadless#starpost5WaveExecutesTheLowerMagneticPlatformAndChain`; `TestFbzCompatibilityMatrix#configuredTeamOptionalInteractions`, `viewportOptionalInteractions`, `donatedOptionalInteractions`; independently seeded local scenarios | `TestFbzSqueezeOrdinaryRoll` now passes 96 S1 arrival delays and 15 width/donor rows with exact Obj28 car contact/exit and no assist. Audit other interactions' sensitivity and rewind spots; local squeeze is not a complete route |
| EVENTS / BOSS | `configuredTeamRetainsPlaneEventAuthority`, `configuredTeamKeepsBossSliceOwnership`, `viewportKeepsBossSliceThresholdsAndContainment`; independent event/checkpoint slices. `TestFbzAct2Subboss`, `TestFbzPlaneTransition`, `TestFbzEndBoss`, `TestFbzFinalEggCapsule`, `TestFbzToSandopolisTransition` pin ROM state machines | Isolated graphs/slices do not replace full subboss → carrier → eight-hit boss → capsule composition for each main character |
| CHECKPOINT / DEATH | `TestFbzCheckpointRoutes#everyNativeTeamDeathReloadsAtEverySupportedCheckpoint`: Act 2 posts 1–6 × four native teams, saved authored checkpoint followed by production death/respawn; inventory checks exact ROM placements | Physical checkpoint activation, required donor/width lifecycle breadth and repeated restart/timeline spots not established by this saved-state setup |
| REWIND: bosses | `TestFbzBossGraphRewind#act2LaserSubbossGraphRoundTripsAndReplaysDeterministically`, `bossCloudExitAndCapsuleGraphsRoundTripAndReplayDeterministically`; `TestFbzEndBossRewind#restoredGraphForwardReplayMatchesUninterruptedReplay`; `TestFbz2SubbossRewind#forcedReconstructionAtRawBeamCallbackPreservesOneShotRumbleAndExplosionAllocation` | Map before creation, active attacks, hit/phase, killing hit and cleanup individually, including all relevant player/control and child identities |
| REWIND: world/events | `TestFbzEventRewindRoundTrip#act2ActiveLayoutAndBackgroundRedrawWordsRoundTripThroughRuntimeOwner`; `TestFbzAct2RomRuntimeLifecycle#activeRedrawRestoresExactRetainedPlaneAndProgressThroughProductionReconcile` | Require before/active/after reversal, camera locks/release and collision-plane reconciliation; field roundtrip alone is not every forward-replay boundary |
| REWIND: interactions/load | `TestFbzSqueezeOrdinaryRoll#productionRegistryRestoresAndReplaysTheLocalCrossing` now passes before-entry, active-car and after-exit spots with two replay cycles; shared object/environment graph tests and `TestFbzActTransitionHeadless#realLiveRewindCannotCrossResultsReloadButCanSeekInsideAct2Segment` | `TestFbzSandopolisTimelineHeadless#productionExitResetsTimelineAndFreshDestinationRestoresAndReplaysTwice` passes the seeded local EXIT_READY → real boss request → GameLoop fade/load boundary: fresh LEVEL_LOAD resets frame zero, excludes outgoing FBZ history, and fresh SOZ registered state passes two restore/eight-frame replay cycles. This is native-width Sonic solo lifecycle evidence; held/riding/release and donor/width breadth remain open |
| PRESENT | `TestFbzBossPlaneRenderMode`, `TestFbzBossCloudDeform`, `TestFbzEndBossAudioAndPlc`, `TestFbz2SubbossArtHandoff`, `TestFbzPlcArtHandoffs` | Missing accepted native/engine checkpoint pairs and named comparisons: outdoor boundary, subboss, carrier/reversal, end boss, exit/capsule and time series. Compatibility capture remains rejected |
| ORACLE / ROUTE | `TestS3kFbzCompleteRunTraceReplay`, ROM disassembly-owned branch/clock contracts and complete compatibility route helper | Trace parity remains red; the completion record supersedes the inherited 5,666-error baseline with measured frontier advances. Do not reuse historical July near-green results or call route-controller progress parity |

## Execution and closure

Focused evidence on `c31bdbd7a` plus local task changes (the earlier two-donor
run used `9fa8fc0a0` plus controller changes; see completion record):

| Command selection | Executed outcome | Invocation wall time |
| --- | --- | --- |
| `TestFbzSqueezeOrdinaryRoll` plus Act 1 lifecycle method | 115 Surefire tests: 111 ordinary squeeze rows and the single Act 1 lifecycle method passed; three rewind rows failed. The 111 rows comprise 96 S1 arrival delays and 15 width × off/S1/S2 cases | 51.80 s |
| `TestFbzSqueezeOrdinaryRoll#productionRegistryRestoresAndReplaysTheLocalCrossing`, `TestObjectManagerRewindSnapshot`, `TestObjectManagerRewindDynamicClassification`, `TestFbzVisualExporterGuard` | **30 passed**, zero failures/errors/skips: three rewind spots, nine snapshot tests, seventeen classification tests and one exporter guard | 46.49 s |
| `-Pfbz-routes -Dtest=TestFbzCompatibilityMatrix#donatedMovementProfileCanReachTheMandatoryBossEntryWithoutSpindash` | **Two passed**, zero failures/errors/skips: S1 and S2 reach the final SOZ request | 56.20 s |
| `-Pfbz-routes -Dtest=TestFbzCompatibilityMatrix` | **Eleven passed**, zero failures/errors/skips: four additional widths (400/512/640/800), five teams and S1/S2 donors, through final handoff | 62.306 s |

The first three rewind failures exposed comparison bookkeeping and then a real
active-car contact restoration defect. The comparator now excludes only level
CoW epoch, render-bucket dirtiness and peak-slot telemetry; all gameplay snapshot
fields remain compared. `ObjectManager` now assigns captured slots to generic
dynamic recreations before execution-table registration, restoring the fallback
lookup that rebinds moving-car riding authority. The three spots pass two
capture/restore/replay cycles each after that correction. The no-ROM snapshot
regression independently verifies slot lookup, exact riding ownership and one
forward execution tick across two reconstruction cycles.

The final eleven-route compatibility run passed on `c31bdbd7a` plus the slot
restoration and local completion changes. This is a focused route-lane result,
not a broad-suite or pixel-parity pass. Complete native-main routes now pass;
other unassessed obligation rows and accepted visual evidence remain open. The [completion record](../../plans/2026-09-14-fbz-completion.md) owns final
command/source identities and reconciliation with inherited results.
`TestFbzCompatibilityMatrix` without `-Pfbz-routes` does not execute the eleven
exhaustive routes. Keep independently runnable local checks outside that lane.
The standard requires current passed obligations, configuration breadth and rewind
spot evidence; source inventory, historical reports and fixture presence do not
certify Act 2. Visual tooling/prerequisite limits are recorded in the Act 1 matrix.


Outgoing timeline follow-up (2026-09-14, `4090860e3` plus the standalone test):
`python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestFbzSandopolisTimelineHeadless -Ds3k.rom.path=/absolute/path/to/s3k.gen test`
passed one test with zero failures/errors/skips in 19.091 seconds (test body
0.728 seconds). The fixture seeds only the local end-boss EXIT_READY setup and
camera threshold; the production object update publishes the real transition
request, and GameLoop consumes it through fade and `loadZoneAndAct`. This is
`LEVEL_LOAD` frame-zero reset, not the Act 1 seamless reset-at-current-frame
policy. After seeking the new floor, SOZ remains loaded and the outgoing boss
cannot return. The fresh destination's complete registered snapshots compare
through two restore/eight-frame forward cycles, excluding only existing
nonsemantic CoW epoch/render-bucket dirtiness/peak-slot telemetry. No SOZ route,
visual, donor/width or full FBZ boss/capsule completion claim is added.


Entry/reload/reset follow-up (2026-09-14, `15976fff2` plus the standalone test):
`python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestFbzEntryReloadResetMatrix -Ds3k.rom.path=/absolute/path/to/s3k.gen -Dsonic1.rom.path=/absolute/path/to/s1.gen -Dsonic2.rom.path=/absolute/path/to/s2.gen test`
passed **30 rows**, zero failures/errors/skips, in 23.909 seconds (test bodies
3.764 seconds). The product is both acts × 320/400/512/640/800 × off/S1/S2 with
concrete Sonic P1 and CPU Tails linked to that leader. It checks the actual ROM
start, effective viewport and composed donor rules, current event/runtime
binding, then two cycles of opposite-act `loadZoneAndAct` followed by
`resetState` plus target-act load. Explicit old trigger/contact seeds, and
Act2-only shake/reversal seeds, do not leak to the fresh destination. The S1,
S2 REV01 and S3K ROM identities matched the repository's required CRC/SHA-1.
An initial import typo (14.733s) and invalid Act1 shake seed (20.215s) were test
setup failures, corrected before this pass. No runtime change was required.
This closes the named cold-entry/reload/reset configuration product, not
traversal, other main-character/team products, checkpoint/death or pixel parity.

### Final ordinary Act2 completion inputs (2026-09-14)

On runtime `16b8ef4e6` plus controller `af0f9facc`, all **13 complete Act2 routes passed**, zero failures/errors/skips. Command: `python3 tools/testing/maven_queue.py -Dmse=off -Pfbz-routes -Dtest=TestFbzCompatibilityMatrix,TestFbzMainCharacterCompletion test -B`, with the verified absolute S1 REV01, S2 REV01 and S3K ROM properties. Maven took 73 seconds; the 11 compatibility routes took 46.671 seconds and native Tails/Knuckles took 9.712 seconds. Five independent `TestFbzAct2TraversalPreboss#checkpointSixOrdinaryPlaneApproachReachesBossWithoutCrush` rows (native Sonic/Tails/Knuckles, Sonic with S1/S2 donation) also passed, zero skips, in 3.947 seconds.

The controller uses ordinary inputs and actual route geometry. Camping at carrier X−123 crushed all 13 routes under the fixed foreground floor at x3162/y543, before any pod hit. Prompt lower hops, crouch/charge where available, and the real 32FA/3070 springs reach the upper route. Knuckles uses glide; S1 uses the rising support and held jump. After that correction, five routes collided while blindly crossing west under the descending boss; waiting at the current-side wall, chosen from live player/pod positions, resolved those failures. No runtime state writes, weakened damage assertions or safety exemptions were added. This supersedes the old controller failures, while the matrix's untested visual/checkpoint products remain open.

Final compiled integration check on `14ce01d48`: queued `-Dmse=off -Pfbz-routes,trace-replay-r7 -Dtest=TestFbzCompatibilityMatrix,TestFbzMainCharacterCompletion,TestFbzEndBossFormalCorrections,TestS3kFbzCompleteRunTraceReplay,TestS3kSonicTailsFbzSegmentTraceReplay test -B`, all three verified absolute ROM properties, completed in 2:12. All 13 complete routes still pass, as do eight formal corrections and 30 other compatibility checks. The command runs 63 cases: 51 pass, ten older short checkpoint-to-boss slices fail at death, and both strict traces fail at the documented frontiers; no skips. Complete remains 16 groups / 44,144 comparison entries, independent 4,152 / 33,712, both zero warnings. The ten old short-slice inputs require separate correction before broad validation; the complete-route controller does not cover them.

The ten old short slices are corrected by `d982d0fba`: they reuse the accepted production-input route helper while retaining every observation, world threshold, camera lock, stage order, team identity and exact boss PRE_MUSIC assertion. Queued `-Dmse=off -B -Dtest=TestFbzCompatibilityMatrix#viewportKeepsBossSliceThresholdsAndContainment+configuredTeamKeepsBossSliceOwnership test` with all three absolute ROM properties passes all **ten rows**, zero failures/errors/skips, in **54.675 seconds** (class 5.950 seconds) on runtime `14ce01d48`. This supersedes the ten failures in the compiled combined run above. No production behavior or the passing complete-route controller changed.

Integrated verification on `f037a1218` (develop, destination `13bb3b165`): full ordinary selection completed 20,299 tests with 20,281 passes,18 inspected skips and no failures/errors (512.95s). All prior ordinary FBZ fixture/controller failures are absent. Guards complete 667 cases with only the two exact matched baseline source-guard failures, no errors/skips (173.30s). The strict/native visual gaps above remain independent of this ordinary-suite result; see the completion record for commands and diagnostics.

Early `$0DC0` elevator S1 feasibility (2026-09-14, `7610686a0`): a local
192-attempt phase sweep and actual gameplay video confirm ordinary run-up/roll
clearance. The 70-frame run-up from `$0CF0` has 17 safe phases per 96; the
84-frame run-up from `$0CC0` has 32. This temporary diagnostic and capture are
not a permanent route regression, other-width matrix, or native-parity claim.
Exact setup, commands, rejected attempts and limits are in the completion record.

Direction correction: the user's intended early `$0DC0` crossing is right-to-left.
On `31a9a6bce`, S1 ordinary rolls with 70/84-frame leftward run-ups have no safe
phase among 96 local starts each; 100 frames from `$0F10` has two safe phases.
Blocked, successful and four-frames-late crushed videos reproduce this distinction.
The prior 17/32-phase results cover left-to-right only. These are local diagnostic
observations, not permanent route tests or complete native-parity certification;
see the completion record for scripts, source identity and capture boundaries.

Matched-phase follow-up (`323e1d2ba`): actual car Ys 1985/2081, controller timer
79 and Sonic centre 3582/2033 match across ordinary reverse approaches. Entry
4.48 px/frame is crushed; 4.90 px/frame clears safely. The latter holds Left
six frames longer before releasing Left and pressing Down. Real synchronized
videos reproduce both on frame 207. This improves the demonstrated strategy;
it remains local feasibility evidence with the existing matrix limits.


Object graphics follow-up: the [laser-room audit](../../audits/2026-09-14-fbz2-laser-room-graphics.md#local-object-follow-up-2026-09-15)
records the normal-approach Robotnik/control-panel lifetime regression, cloud
coordinate/frame corrections, placement flips, and magnetic-chain end fitting.
All 46 focused checks passed; the same Wayland approach visibly restores the
room displays. Full moving-event, donor/team, and rewind route gaps remain.

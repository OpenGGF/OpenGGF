# FBZ Act 2 coverage matrix

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
| Short lifecycle breadth | Standalone synchronous transition preflights sweep teams, widths 320/400/512/640/800 and off/S1/S2; Act 1 results test now includes 512/640 in its passing 105-case product plus native reset | Full required width × donor entry/reload/reset evidence remains pending; the expanded results-boundary scenario alone does not cover every lifecycle; see Act 1 matrix |

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

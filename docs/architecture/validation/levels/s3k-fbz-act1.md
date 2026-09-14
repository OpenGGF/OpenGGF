# FBZ Act 1 coverage matrix

Spike-art follow-up (2026-09-14): the ROM-backed
`TestSonic3kObjectArtProvider#fbzSpikeOrientationsUseTheirNativeTileBanksInBothActs`
passes all eight mapping frames in both acts, including piece dimensions/flips,
live upright `$200` and sideways `$494` pattern references, and exact refresh
range boundaries. This local presentation check does not certify the act's
remaining native visual checkpoints; see the completion record for execution.

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

Canonical slot: `S3K_FLYING_BATTERY_1`, FBZ ROM act 0. The outgoing
results-owned reload enters FBZ act 1 (displayed Act 2), with world translation
and carried owners; it is part of this act's completion contract.

## Character and configuration routes

| Route | Concrete existing contract and setup | Remaining obligation |
| --- | --- | --- |
| Sonic solo / Sonic + Tails | `TestFbzAct1ColdRoute#sonicAndTailsReachAct2FromColdAct1ThroughRealBossAndResults` passed the ordinary cold Sonic + Tails route on the integrated runtime (six-impact boss → sign/results → Act 2 title teardown/control release); see exact source and limits below. `TestFbzNativeCharacterRoutes#everyNativeTeamCanEnterBothActsFromLevelSelectAtTheRomStart`: production level-select selection, ROM start, concrete sprite types, two idle frames | Representative full-route configuration breadth; Sonic solo full route remains open |
| Tails solo | Same entry method with Tails radius/character assertions; checkpoint method below | Complete Tails traversal and boss/results route; prove flight-dependent geometry and event progression |
| Knuckles solo | Same entry method with Knuckles character selection; checkpoint method below | Complete Knuckles route; independently establish any materially different geometry/event/boss progression |
| Width/donor/team lifecycle | `TestFbzAct1RouteHeadless#resultsOwnedReloadAndTitleLifecycleSupportsWidthsDonationsAndEveryTeamShape`: seeded boss-boundary fixture, real results/reload/title lifecycle; widths 320/352/400/512/528/640/800 × off/S1/S2 × five team shapes (105 cases), then native reset | 105-case product plus final native reset PASS in focused validation below. Not full traversal or rendered-width evidence |
| Current standard width/team/donor axes | `TestFbzCompatibilityMatrix#configuredTeamSynchronousTransitionPreflight`, `viewportSynchronousTransitionPreflight`, `donorSynchronousTransitionPreflight` | Independent axis sweeps do not establish each act's full 15-case width × donor entry/reload/reset product or meaningful Act 1 traversal |

## Obligation map

| Obligation | Named test contract / independent oracle and setup authority | Limit or open work |
| --- | --- | --- |
| ENTRY / LOAD | Native-character entry above; `TestFbzAct1RomRuntimeLifecycle#freshLevelEventInitializationClaimsNativeFirstDynamicSlotBeforePlacement` and `outdoorStartupPaletteAndRetainedRingSurviveRealFramePreparation`; real ROM load/frame preparation | Alternate-entry inventory, repeated resets and required breadth still need explicit executed rows |
| INTERACT / EVENTS / BOSS | `TestFbzAct1RouteHeadless#loadedFbz1PlacementStartsOnlyWhenP1ReallyStandsOnItsPlunger` and `p2AndExtraSidekickCanRideTheRealPlungerButOnlyP1CanStartIt`; real placed miniboss from local boundary setup. `TestFbzEventsAct1`, `TestFbzAct1Miniboss` pin ROM branch behavior | Seeded arena evidence does not cover reaching it. Map all encountered carriers, hazards and indoor/outdoor boundaries to local production-binding tests |
| CHECKPOINT / DEATH | `TestFbzCheckpointRoutes#romDecodedActsExposeTheCompleteAuthoredStarpostSet` and `everyNativeTeamDeathReloadsAtEverySupportedCheckpoint`: ROM placement oracle, save checkpoint then production death/reload; Act 1 posts 1–5 × four native teams. `TestFbzAct1RouteHeadless#checkpointDeathReloadRecreatesPristinePlacedBossAndClearsEncounterTransients` | Saved-state setup does not prove physical starpost activation; width/donor respawn product and repeated restart leak checks need mapping/evidence |
| LOAD / ROUTE boundary | `TestFbzActTransitionHeadless#productionReloadPreservesConcreteSstFamiliesAtExactBoundarySlotsAndLinks`, `synchronousReloadCarriesAndReadoptsTheExactOutdoorMotionSlot`, `screenEventReloadPreservesLiveTeamStateAndUsesShiftedWindows`; ROM-backed seeded transition state, production reload | Do not substitute this boundary for a complete Act 1 route |
| REWIND: boss/events | `TestFbzBossGraphRewind#act1MinibossFullNativeGraphRoundTripsAndReplaysDeterministically`; `TestFbzMinibossRewind#forcedReconstructionRelinksAllSimpleChildrenAfterAdverseChildFirstOrder`; constructed non-default object graphs and ROM-owned identities | Inventory before creation, active attack, hit, killing hit, cleanup spots individually; graph reconstruction alone is narrower than every player-contact/clock boundary |
| REWIND: real carried title initialization | `TestFbzAct1RouteHeadless#realBossTitleInitRewindsBeforeAndAfterItsFirstDispatch`: actual six-impact boss/sign/results; complete registry capture immediately before `TITLE_CARD_INIT`, after its dispatch, and after the following dispatch; two restore/replay cycles on each side | Passed in `0d4de81f1`; compare every registry owner and semantic field, including all 64 pattern pixels; only existing documented bookkeeping exclusions |
| REWIND: load/timeline | `TestFbzActTransitionHeadless#realLiveRewindRoundTripsAct1OwnersBeforeResultsPublication` and `realLiveRewindCannotCrossResultsReloadButCanSeekInsideAct2Segment`; real live rewind service | Results reload intentionally severs timeline: test old-history rejection and new Act 2 seek, not cross-boundary restoration |
| REWIND: interactions/world/camera | `TestFbzObjectRewind#eventsPolarityCarriersHazardsAndBadniksRoundTripAndReplayDeterministically`, `TestFbzEnvironmentalGraphRewind` provide local mechanisms | Assign before/contact/held/release and before/active/after redraw, palette and camera-lock spots to each applicable Act 1 binding; unassessed breadth remains open |
| PRESENT / ORACLE | `TestFbzScrollHandler#outdoorDeformUsesBobAndReadThenIncrementE00Drift`, `TestFbzAnimatedTiles`, `TestFbzPlcArtHandoffs`; ROM scripts and event constants. `TestS3kFbzCompleteRunTraceReplay` is the independent complete-run oracle | Numerical/asset tests do not prove rendered pixels. Current V5 trace is inherited red; fresh visual pairs/cadence acceptance absent |

## Visual acceptance and execution ledger

[Native validation](../../research/s3k-zones/fbz-validation.md) and the
[amendment](../../research/s3k-zones/fbz-visual-evidence-amendment-proposal.json)
remain authoritative. The new Python/BizHawk host captured a visible native start
and six-frame native candidates for all five AniPLC channels. The paired engine
start matches level frame 35 and raw animation counters after the production
setup pass; terrain raster alignment and cloud phase still differ. `$230` is
placed only in Act 2, as proven by ROM block/chunk/layout decoding. These are
candidate references, not completed paired cadence or checkpoint acceptance.
The frozen checkpoint manifest remains unchanged. See the current native
validation record for exact artifact hashes, regions and outstanding obligations.

Read-only prerequisite check on 2026-09-14:
`tools/tracechaser/bizhawk/preflight_bizhawk_2_11.sh --bizhawk-home $BIZHAWK_HOME`
passed: exact 2.11.0.0 and 30 Lua capabilities. Mono/ffmpeg/pwsh/Lua 5.4 and
the complete Sonic/Tails BK2 exist; display access and fresh pixels were not tested.
That initial preflight produced no accepted capture. Later native execution and its
exporter defects are recorded in the completion record; those PNGs do not close
visual acceptance.

Focused lifecycle evidence on `c31bdbd7a` plus local task changes:
`mvn -Dmse=off -B -Dtest=TestFbzSqueezeOrdinaryRoll,TestFbzAct1RouteHeadless#resultsOwnedReloadAndTitleLifecycleSupportsWidthsDonationsAndEveryTeamShape test`
with absolute verified ROM properties completed in 51.80 seconds. The Act 1
method passed all 105 width × donor × team combinations and the final native
reset. Surefire reports that loop as **one test**, not 106 independently reported
cases. The combined invocation reported 115 tests: 112 passed and three local
squeeze rewind failures; it was not a green run. Those rewind failures were
subsequently resolved and narrowly verified as recorded in the Act 2 ledger.
This proves the seeded results/reload/title lifecycle, not every Act 1 main-character
route, physical checkpoint interaction, complete rewind matrix or visual outcome.

The final eleven-route Act 2 compatibility matrix also passed (62.306 seconds,
zero failures/errors/skips) on the same candidate plus slot-restoration/local
changes; see the [Act 2 ledger](s3k-fbz-act2.md). Those routes begin at cold Act 2
entry and do not close Act 1 traversal or Tails/Knuckles-main route gaps.

## Ordinary cold-route work (base `51677cdd2`)

`TestFbzAct1ColdRoute` uses the ROM start, native Sonic + Tails, live production
hardware readiness, and pad input only. The committed BK2 supplies its opening
pad sequence; comparison rows and recorded readiness never supply route state.
The first ordinary run died at `$0A63/$0BE6` on input frame 2,970: stale
recorded RIGHT walked off a moving platform. Live geometry steering now clears
the `$0A78` chain and the `$0B10`, `$0BC0`, and `$0C90` platform sequence.
Circle forecasts use the actual level clock and native phase; future landing
reachability controls when ordinary jumping starts. Stable placement anchors
track progress, while current coordinates drive steering. Treating these two
coordinate domains as interchangeable reselected the same moving platform;
forecasting the first gap prematurely also regressed it. Both were rejected.
The `$0D90/$0A80` launcher now records actual P1 standing, acceleration and
release, followed by upper-route ascent. Continuous jumping previously skipped
its standing callback and looped below the upper route. Live LEFT steering also
reaches the horizontal wire-cage chain. Subsequent live input steering completes that trap, the upper snake-platform
tower, the `$08B0` trap, upper platform/pole section, screw-door descent,
`$1940` platform-to-wire gap and the `$1B20` rider-triggered rising platform.
Live polarity/clearance inputs now cross the magnetic corridor and physically
activate starpost 4. All three following magnetic carriers, the return plunger
and launcher, three upper rotating gaps, missile bursts and real five-impact
companion release also complete. The final carrier transfer now uses ordinary run-up to clear the low ceiling,
then rides both `$2C80` rotating families through the `$2CE0` floating platform.
The full native Sonic + Tails route **passed once** at `a9354be64` plus the
candidate: 1 test, zero failures/errors/skips, 23.334 seconds Maven / 5.126
seconds class time. It activates checkpoints 4 and 5, reaches the real miniboss,
observes all six scripted impacts and defeat, the falling sign and results, and
waits for actual Act 2 title teardown and released P1 control. The live boss
driver leaves during normal-attack alignment so later, accelerating fans cannot
catch P1 on the plunger. No runtime values were changed. The final integrated native repeat also passes (details below). Representative
configuration traversal is still open; this does not certify Tails-main or
Knuckles-main traversal.

Focused queued command: `python3 tools/testing/maven_queue.py -Dmse=off -B
-Pfbz-routes -Dtest=TestFbzAct1ColdRoute -Ds3k.rom.path=<absolute locked-on ROM>
test`. On `51677cdd2` plus the candidate, the initial diagnostic run had one
error (null-spawn failure formatting); seven subsequent controller checks each
reported one failure and zero errors/skips. Their successive frontiers were
2,970, 3,271, 2,986, 3,111, 3,271, 3,363, and the 35,400-frame route watchdog.
The last check survived the outdoor gaps without dying but did not complete:
23.659 seconds Maven / 6.024 seconds class time. These are red development
checks, not inherited or new passing route evidence.

After merging `46fe2152f`, five further queued one-test checks each failed
with zero errors/skips (Maven 21.037, 21.601, 19.779, 21.043, 24.444 seconds).
The first two prove launcher progression but stale pad continuation eventually
dies near the upper egg prison. The third reaches the wire cages and trap area.
The next two expose premature trap steering/jumping during the curved climb,
which drops P1 onto the lower floor. Survival is the current safety assertion;
continuous no-hurt/ring-loss is not yet certified.

Independent ending allocation regression on `46fe2152f` plus the candidate:
`python3 tools/testing/maven_queue.py -Dmse=off -B
-Dtest=TestFbzAct1RouteHeadless#realBossSignWaitsForGroundAndAllocatesResultsInEarlierFreeSlot
-Ds3k.rom.path=<absolute locked-on ROM> test` passed **1 test, zero failures,
errors or skips**, 50.886 seconds Maven / 1.481 seconds class time. This short
local boundary fixture uses the actual placed boss and all six automatic arm
impacts. It then reserves allocation slots (explicit test setup), makes an
ordinary jump across the real sign countdown, verifies grounded gating and
first-free lower-slot results publication, and observes initialization on the
next object dispatch. It does not substitute for cold traversal.

The subsequent real-title initialization regression is committed in `0d4de81f1`
(on merged `f14a27b51`, including `5f5a73d61`). Queued focused command selects
`TestFbzAct1RouteHeadless#realBossTitleInitRewindsBeforeAndAfterItsFirstDispatch`:
**1 test, zero failures/errors/skips**, 20.069 seconds Maven / 1.998 seconds
class time. The preceding paired run reported 2 tests with one comparison
failure and zero errors/skips (51.259 seconds): allocation passed, while title
forward replay compared newly created `Pattern` identities. The corrected test
compares every pattern's complete 64-byte pixel state, not object identity;
no title/gameplay field is dropped. Full-registry restoration and forward replay
now pass twice before and after actual title initialization.

`3db7af70d` updates the real converted-controller allocation regression after
native camera-worker ownership was corrected: exact 0–3 post-controller free
slots, exact worker prefix/slots, preserved unallocated targets, and four
ordinary updates including the first nonzero worker delta. Queued selection
`TestFbzAct1RouteHeadless#realConvertedEndSignControllerAllocatesExactWorkerPrefixAndRunsFirstTwoUpdates`
passed **4 tests, zero failures/errors/skips**, 23.001 seconds Maven / 3.969
seconds class time. Two preceding 4-row red checks exposed obsolete test setup:
`TITLE_CARD_INIT` is not an initialized provider, and the corrected rebase no
longer loads the former extra StillSprite into the allocation window. Neither
was addressed by weakening worker-prefix or camera-tail assertions.

Ordinary route controller lessons: on a small trap reached from curved terrain,
LEFT cannot brake during the real slope-slip movement lock and prevents neutral
friction. The verified input solution preserves neutral while that lock burns
down, then centres on the trap; it never changes the lock. Directional input
during the subsequent ceiling arc also loses the momentum needed to climb, so
that section retains neutral until the terrain releases P1. At the lower cage,
the route goes down to the floor and uses an ordinary charged roll up the curved
wall; a direct cage-to-platform jump was an incorrect route assumption. The
cold completion assertion now requires results retirement, title completion,
and released P1 control in Act 2, not merely the early seamless reload.


The independent `TestFbzEntryReloadResetMatrix#coldEntryAndRepeatedProductionReloadsIsolateActOwners`
passed **30 rows, zero failures/errors/skips** in `ad293afee` (executed on `15976fff2` plus the new test)
(23.909 seconds Maven, 3.764 seconds body). Its product is both FBZ acts × widths
320/400/512/640/800 × donors off/S1/S2, with concrete Sonic + Tails. Each row
checks the cold ROM start, then two opposite-act `loadZoneAndAct` →
`resetState` + target-load cycles: actual viewport, composed rules, fresh event
and runtime owners, and no seeded trigger/shake/reversal or standing/riding
contact leakage. Command: `python3 tools/testing/maven_queue.py -Dmse=off
-Dtest=TestFbzEntryReloadResetMatrix -Ds3k.rom.path=<absolute S3K ROM>
-Dsonic1.rom.path=<absolute S1 ROM>
-Dsonic2.rom.path=<absolute S2 ROM> test`. This is entry
and reset breadth, not full traversal, other-main-character coverage, death or
checkpoint restoration, or pixel acceptance.


### Integrated ordinary-route evidence and rejected breadth experiments

On `4d9bcf459` (including `74b91937d`, `1e8e86743`, and `c45a977ce`) plus
the final controller, the native 320-pixel Sonic + Tails cold route passed:
**1 test, zero failures/errors/skips**, 23.530 seconds Maven / 5.788 seconds
class time, **27,051 ordinary frames** through actual Act 2 control release.
The queued command was the cold-route command above. Configuration checks
verify actual camera and viewport width, concrete Sonic/Tails CPU ownership,
and donation state; session overrides and viewport are restored. A live
low-platform gate, safe-floor run-up and centre landing replace the third
magnetic carrier's previously phase-sensitive jump from rest.

The seven short encounter/ending cases separately passed on the preceding
`74b91937d` integration plus corrected assertions: **7 tests, zero
failures/errors/skips**, 6.437 seconds Maven / 5.730 seconds class time. Command:
`python3 tools/testing/maven_queue.py -Dmse=off -B
-Dtest=TestFbzAct1RouteHeadless#realConvertedEndSignControllerAllocatesExactWorkerPrefixAndRunsFirstTwoUpdates+realBossTitleInitRewindsBeforeAndAfterItsFirstDispatch+realBossSignWaitsForGroundAndAllocatesResultsInEarlierFreeSlot+placedBossAutomaticallyReachesSignLandingResultsCompletionAndEventsFg5
-Ds3k.rom.path=<absolute locked-on ROM> surefire:test`. The earlier combined
`-Pfbz-routes` invocation executed only the tagged cold test; it did **not**
execute these untagged short methods. The one short failure was the obsolete
publication-frame `+2` bound expectation: `FBZ1BGE_Normal` subtracts `$2E00`
from both current X bounds then branches directly to `FBZ1BGE_GoDeform`. The
correct `$20/$A0` bounds retain the rest of the actual publication/owner checks.

A four-case breadth assessment passed native Sonic + Tails but failed the
400-pixel camera setup (stale session), Sonic solo traversal near `$0CA5/$08AC`
at frame 25,362, and S2-donor traversal near `$09CD/$0A45` at frame 3,033
(4 tests, 3 failures, no errors/skips, 28.314 seconds Maven). Fixing width
setup exposed a real 400-pixel controller frontier at the early `$0A78` chain,
not width coverage. A common chain-height gate was rejected: the first version
required an unreachable height; the revised version cleared that chain but
changed native timing and exposed later upper-route failures (2 failures, no
errors/skips, 29.118 seconds Maven). The accepted native controller excludes
that experiment. Wider, solo, S1/S2-donor and other-main-character full routes
remain open; none are disabled or represented by a passing load-only test.

# FBZ Act 1 coverage matrix

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
| Sonic solo / Sonic + Tails | `TestFbzNativeCharacterRoutes#everyNativeTeamCanEnterBothActsFromLevelSelectAtTheRomStart`: production level-select selection, ROM start, concrete sprite types, two idle frames | Full start → mandatory interactions → miniboss → results → Act 2 route, with execution evidence |
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

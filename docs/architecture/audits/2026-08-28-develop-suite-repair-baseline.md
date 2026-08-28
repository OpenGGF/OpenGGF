# Develop Suite Repair Baseline

## Context

- Branch: `bugfix/ai-develop-suite-repair`
- Baseline commit: `a29d5fd7a`
- JVM: Maven running on JDK 21
- Reports were deleted before the run so every result below belongs to this invocation.

## Command and result

```bash
mkdir -p target/maven-tmp
mvn -Dmse=off -Dsurefire.runOrder=alphabetical \
  -Dsonic1.rom.path=s1.gen -Dsonic2.rom.path=s2.gen \
  -Ds3k.rom.path=s3k.gen test -B
```

Result: **14,867 tests; 45 failures; 6 errors; 36 skipped**.

This alphabetical result is the authoritative baseline. Earlier filesystem-order
totals were discarded because order-dependent shared state and stale Surefire
reports made them unsuitable for regression comparison.

## Categories

| Category | Failures | Errors | Characteristic symptoms |
|---|---:|---:|---|
| Frame, PLC, trace, and title-card lifecycle | 16 | 1 | Boundary order, row suppression, cursor ownership, title-card policy, missing object dispatch |
| S3K objects, events, art, and scrolling | 11 | 3 | ROM-state gates, art readiness, palette restore, boss/route timing |
| Sonic 2 objects, PLC, and player behavior | 8 | 0 | Object-slot timing, spring contact, PLC production, sidekick state |
| Rewind state and object graphs | 6 | 2 | Inventory drift, graph relinking, duplicate registration, restored super state |
| Touch/collision lifecycle | 2 | 0 | Prior collision-response coordinates and enemy bounce |
| Rendering | 1 | 0 | SAT priority batching |
| Packaged configuration test isolation | 1 | 0 | Test reads the worktree-linked user `config.yaml` instead of packaged defaults |

The category is a triage owner, not a presumed root cause. Shared lifecycle fixes
may resolve failures in several categories; focused changes must still be verified
against the complete baseline.

## Error inventory

- `TestGameLoopHardwareTimingBoundaries#admittedSpecialStageIterationSurroundsProviderScanWithAllBoundaries`: null PLC lifecycle phase reaches `PlcLifecycleFrame.claim`.
- `TestMgzDrillingRobotnikInstance#cleanupRestoresMgzPaletteLine1`: post-flee art queues an out-of-ROM KosM source (`0x3c3ebe`).
- `TestSpriteManagerDebugEmeraldGrant` (2 tests): duplicate rewind registration with a null diagnostic key during setup.
- `TestS3kAiz1FireCurtainHeadless` (2 tests): finish queue cannot find a prepared fire-overlay payload.

## Failure inventory

### Frame, PLC, trace, and title-card lifecycle

- `TestGameLoop#levelAndBonusTraceSuppressionIsClassifiedBeforeGenericTimers`
- `TestGameLoop#userRecordingPlaybackPolicyObservesAppliedMovieFrameBeforeCursorAdvance`
- `TestGameLoopTraceRunPostIteration#diagnosticsServiceExposesNoRegistrationOrCapableReference`
- `TestPlcVBlankOrdering#ordinaryLevelServicesPlcBeforeEventsAndObjects`
- `TestTitleCardPhysicsPolicy#sonic2RunsPlayerPhysicsDuringLockedTitleCardPhase`
- `TestDynamicArtDmaServiceModel#sonic2ServicesOnlyProcessDmaQueueEquivalentClaims`
- `TestLevelSeamlessTransitionExecutor` (2 transition bridge tests)
- `TestInitialPlayableProcessSpritesPass#p1InitializesTheTemporaryOffsetHistoryBeforeP2WithoutAdvancingItsCursor`
- `TestModeTracePickerLaunchStatus#launchWaitsUntilLoadingScreenHasRendered`
- `TestDynamicArtTransferTrace#advertisedCapabilityAcceptsKnownGenericNativeEvents`
- `TestTraceSuppressedRowClosure` (2 ordering tests)
- `TestTraceRunSpecialStageRows#s2WithRecordedPassesExposesAPassCursorFromControlStart`
- `TestS3kMgzLbzCarriedResultsTitleOwnership#mgzCarriedResultsIsTheOnlyTitlePublisherAndRetainsNativeDispatchTiming`
- `TestAiz2BossEndSequenceObjects#aizCapsuleResultsStartLocksSonicButDefersSidekickEndingPoseCheck`

### S3K objects, events, art, and scrolling

- `TestSonic3kLbzLaunchSignals#deathEggTerrainSwapAppliesRomBackedBlocksChunksAndArtOnce`
- `TestAizEndBossInstance#fireSignalTriggersBurnBridgeVariant`
- `TestLbzTubeElevatorInstance#waitExitStaysOpenWhileReleasedPlayerIsStillStandingOnElevator`
- `TestMantisBadnikInstance#waitOffscreenDefersInitializationUntilPlaceholderIsVisible`
- `TestMgzDrillingRobotnikInstance#endBossFinalHitEntersRomDefeatWaitBeforeCapsuleHandoff`
- `TestRhinobotBadnikInstance#waitOffscreenUsesRomPlaceholderWidth`
- `SwScrlMgzTest` (2 screen-shake tests)
- `TestS3kCnzEndBossHeadless#cameraGateStartsNativeBossAndLoadsRomPalette`
- `TestS3kCnzTeleporterRouteHeadless#groundedTeleporterWaitsForArtReadinessAndPublishesRomPalettePatch`
- `TestS3kMgzLbzCarriedResultsTitleOwnership#mgzCarriedResultsIsTheOnlyTitlePublisherAndRetainsNativeDispatchTiming`

### Sonic 2 objects, PLC, and player behavior

- `TestSonic2PlcProducerCoverage#titleCardOwnerPublishesWaterThenZoneAnimalAtTextExit`
- `TestCPZSpinTubeObjectInstance#lowerSlotDestinationHandoffPreservesOwnerOverwriteTiming`
- `TestSonic2ObjectBugFixes#collapsingPlatformFragmentFallKeepsVerticalOnlyOffscreenParentForCpuSlotRefresh`
- `TestSonic2TriggerParticipation#springAppliesCheckpointContactToQueryOnlySidekick`
- `TestSpringObjectInstance` (2 contact tests)
- `TestPlayableSpriteMovement#s2JumpTriggerStillActivatesSuperSonic`
- `TestS2PostLoadAssemblyHeadless#sidekickSpawnPositionWritesPreserveFractionalWords`

### Rewind state and object graphs

- `TestCheckpointStarpostGraphRewind#sonic1LamppostTwirlEndsAsOneCenteredBallNotADuplicate`
- `TestRemainingRewindTailInventory#remainingRoundTripTailMatchesInventory`
- `TestS3kBadnikChildGraphRewind` (2 graph-relink tests)
- `TestSonic3kSuperStateRewind` (2 state round-trip tests)

### Touch/collision lifecycle

- `TestObjectManagerLifecycle#s2ExecThenLoadBypassesVerticalFilterWithoutPreExecLoad`
- `TestTouchResponseManager` (2 prior-response-coordinate tests)

### Rendering and configuration

- `TestSatReplayBatching#satReplayBatchesIntoSingleInstancedCommandPreservingOrderAndPriority`
- `CaptureConfigDefaultsTest#captureDefaults`

## Regression rule

A repair is acceptable only when every test that passes on this baseline still
passes, and no baseline failure changes or worsens because of the repair. A lower
failure count alone is insufficient; focused tests, the ordinary suite, structural
guards, keep-green headless tests, and the trace-replay profile must agree.

## Repair result

The final alphabetical ordinary-suite run used the same command and a freshly
cleared Surefire report directory at the repaired tree:

- **14,868 tests; 30 failures; 3 errors; 36 skipped**.
- The extra test is `TestS2BridgeSegmentGraphRewind`, which passes and provides
  executable coverage for the parent-owned Obj11 bridge-segment graph.
- **15 failures and 3 errors were removed. No baseline-passing test failed, and
  no new failing test identity appeared.**

Resolved failures:

- `TestGameLoop` (2)
- `TestGameLoopTraceRunPostIteration`
- `TestPlcVBlankOrdering`
- `CaptureConfigDefaultsTest`
- `TestTitleCardPhysicsPolicy`
- `TestDynamicArtDmaServiceModel`
- `TestLevelSeamlessTransitionExecutor` (2)
- `TestDynamicArtTransferTrace`
- `TestTraceSuppressedRowClosure` (2)
- `TestSonic2PlcProducerCoverage`
- `TestCheckpointStarpostGraphRewind`
- `TestRemainingRewindTailInventory`

Resolved errors:

- `TestGameLoopHardwareTimingBoundaries`
- `TestSpriteManagerDebugEmeraldGrant` (2)

The 30 remaining failures and 3 errors are all baseline members. Categorized by
the subsystem that should own the next repair:

| Remaining owner | Failures | Errors | Examples |
|---|---:|---:|---|
| Frame/title/trace lifecycle | 5 | 0 | initial playable history, picker loading state, S2 pass cursor, carried-results timing, AIZ result eligibility |
| S3K objects, events, art, and scrolling | 10 | 3 | badnik child allocation, MGZ palette/art source, AIZ fire payload, LBZ/CNZ route signals |
| Sonic 2 objects and player behavior | 7 | 0 | spin-tube handoff, springs, collapsing platform, Super Sonic trigger, post-load sidekick fractions |
| Rewind state and object graphs | 4 | 0 | S3K Caterkiller/Mantis child relinking and super-state ring drain |
| Touch/collision lifecycle | 3 | 0 | ObjPosLoad materialization and previous-response-coordinate bounce |
| Rendering | 1 | 0 | SAT priority batching |

Additional validation:

- Required S3K keep-green set: **55 tests, 0 failures/errors**.
- Trace-replay profile: **870 tests, 6 failures, 0 errors, 7 skipped**;
  all six identities and first-error signatures match the recorded frontier.
- Structural guards initially caught `GameLoop` exceeding its size ratchet by
  three effective lines. Phase fallback was extracted to `PlcLifecyclePhase`;
  the focused architectural guard then passed. The complete guard profile is
  rerun as final release evidence after integration.

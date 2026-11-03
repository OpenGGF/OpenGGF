# Rewind budget implementation plan

Approved design: consumable 10-second allowance (or Unlimited), refill scope Off / Per checkpoint / Per act / Per zone, default Per act. Life-loss refill defaults on and occurs after completed respawn. Bonus and special-stage toggles default on and each visit gets an independent fresh pool using the shared maximum; the main pool is preserved. Rewinding never refunds spending or repeats a boundary refill. Exhaustion immediately ends held/coasting rewind. A denied press displays the empty plate for one second, under Opus 5.5 creative direction.

## Implementation

- [x] Runtime policy and lifecycle integration: `LiveRewindBudget`, `LiveRewindManager`, completed respawn boundary in `LevelManager`. Charge actual successful backward steps, outside snapshots; preserve scripted/trace rewind semantics.
- [x] Settings: catalog/defaults/timing, Gameplay selector and indented dependent rows, English ARB labels/help, ordered positive budgets plus Unlimited. Retain legacy enable gate for launch profiles and tools.
- [x] Opus HUD: extend existing `TransportHud` plate and `LiveRewindHudOverlay`, one row plus 2 px meter, empty response with shape/border distinction, captures at 320/400 and rubric evidence.
- [x] Focused behavior tests: policy pools, boundary repeat prevention, life-loss refill, speed/coast exhaustion, denied press duration, settings persistence/ordering/dependencies, HUD states.
- [x] Complete change-based smoke and affected focused checks; inspect skips and attribute exact failures.
Delivery follows the repository flow: integrate into the main workspace’s current develop branch, push develop only, and remove fully accounted task worktrees and branches. The final task response records that delivery outcome.

## Review focus

Preserve legacy disabled preferences and per-game overrides; unlimited is not unlimited history storage. No refill from rewind restoration, repeat checkpoints, generic reloads, or death animation. A settings edit cannot silently grant a pool through incidental history reinstall. Stage transitions must preserve the main allowance even when rewind is disabled for the visited stage. UI must remain clear of native HUD and simultaneous recording/status plates.

## Verification record (2026-11-03)

Pre-task destination: `92dd2135fb9ac376644e8001c268a21d1c6c2cea`. Feature `acc1db940f27a01f708b88bfcaadbde261f22c87` and upstream merge `57b41127a` were frozen for the broad run. Repairs are `b7508d43e` and `9836772f1`. The combined source tested at `3640a70f42b0f75de1aab9ce2f4b469c8ddd8b5b` incorporates develop `e4b8857a839e7d63e18f6b2cfa79db71190b4184`.

Upstream merged without conflicts. Against develop, LevelManager retains only the completed FULL_DEATH_RELOAD refill hook, after successful load/cleanup. The candidate API pin retains exactly the five appended configuration fields alongside upstream signatures, with no removals. The descriptor remains unchanged. The combined API signature checks passed.

### Completed selections

- Initial policy/manager/settings/HUD selection: 71 passed, zero skips.
- Expanded settings/stage/audio/localization/HUD selection: 126 passed, zero skips.
- Final original budget/ownership/pacing selection: 47 passed, zero skips. An earlier explicit lean attempt exhausted the 1 GiB compiler heap before tests ran; its normal-memory rerun passed.
- Bootstrap/pacing mock repairs: 48 passed; hub chips: six passed. Original failing method identities were checked in Surefire XML, with zero skips.
- Corrected display environment: eight S2 native competition/interlace/halfpipe EGL cases passed, zero skips. The broad invocation mistakenly inherited DISPLAY; these eight cases stopped at its absent-DISPLAY assertion. The corrected command used `env -u DISPLAY -u WAYLAND_DISPLAY`, the explicit EGL backend and the absolute original S2 ROM path.
- Final standalone/native-fixture repair selection: 58 passed, zero skips. Queue wait 557 seconds; Maven execution 1m42s.
- Combined-source lifecycle/API/native selection: **125 passed, zero failures/errors/skips** at `3640a70f4`. Queue wait 609 seconds; Maven execution 1m49s.
- Combined-source `TestRewindArchitectureGuard,TestObjectPhysicsStandardizationGuard`, under `-Pguards`: **36 passed, zero failures/errors/skips** at `3640a70f4`. Queue wait 33 seconds; Maven execution 35.122 seconds.

The 125-case command used the following exact selection, with `env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl`, all three absolute original ROM properties, and Maven `test`:

```text
TestLevelEntryProvenance,TestSonic3kLevelStartCamera,TestLiveRewindOwnerLifetime,TestLiveRewindBoundaryPolicy,TestModApiSignatureSurface,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestFbzNativeConfiguration,TestPhase3StandaloneSampleIntegration#realPackagedStandaloneLoadsWithoutRomAndExercisesCharacterLevelObjectAudioAndTerminalTopology,TestSamplePlatformerIntegration#realPackagedPlatformerLoadsWithoutRomAndExercisesCharacterLevelAudioAndTerminalTopology
```

Surefire XML confirms the required S3K classes ran with zero skips: AIZ skip eight cases, both LevelLoading classes (36 and seven), BootstrapResolver six and DecodingUtils three. The API surface class passed 12 cases. No unchanged broad run was repeated after the bounded repairs or upstream reconciliation.

### Repair decisions

Twelve failures were fixture assumptions about the old disabled default. Bootstrap/pacing mocks without capture payloads now explicitly opt out after configuration reset; the hub chip expects REWIND ON; FBZ native parity uses typed LaunchToggle.OFF because legacy boolean false means DEFAULT.

Two failures were real standalone launch regressions exposed by enabling live history by default. The native level adapter requires AbstractLevel COW geometry, whereas creator-owned custom Levels can be owner-bound proxies. Native live-history admission now checks that geometry contract; unsupported custom geometry remains playable without a native controller. Held-rewind pacing uses the same support check. Both real packaged startup tests explicitly exercise the enabled default. An initial gate placement touched level state while disabled; the ownership regression caught it, and moving the check into enabled history admission preserved that invariant. The corrected 58-case run passed.

### Broad smoke

Actual Java 25/Lua 5.4 preflight passed. The command was:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 92dd2135fb9ac376644e8001c268a21d1c6c2cea --workers 2 --headless-gl surfaceless-egl --rom-directory <main-workspace> --run
```

Run `20261102T151818Z-9913df0f`, source fingerprint `b63aadc27b2a8c3ed77f43577e513e916a885d92ac6dbc7d6e8de80de9bc7e83`, selected 4,397 candidate classes. All 89 ordinary batches and the separate guard batch completed:

| Lane | Tests | Failures | Errors | Skips | Execution |
| --- | ---: | ---: | ---: | ---: | ---: |
| Ordinary | 36,618 | 36 | 6 | 50 | 1,599.08 s |
| Guards | 820 | 2 | 0 | 0 | 149.67 s |

Preparation took 37.76 seconds. Total recorded execution including preparation was **29m46.51s**. Shared admission waiting is excluded from those execution figures and dominated wall time; aggregate queue waiting was not collected for this record.

The broad run was red. Its 42 ordinary failed cases are fully accounted for: 14 repaired and narrowly rechecked feature regressions, eight environment cases that passed corrected invocations, and 20 exact baseline failures. The two guard failures also reproduce on baseline, and their full classes pass after upstream reconciliation. All failed cases and skips were inspected; neither list omitted cases. This is bounded smoke/focused validation. Full-suite is release-only.

The 50 skips include explicit capture/profiling/soak/native-context/mod-artifact switches, isolated-display reel checks, and local audio reference/capture prerequisites. `TestCPZObjectBugs.testSpinTubeForcesRolling` skipped because its spin-tube capture/release assumption was not reached; that remains a coverage gap. There were no missing-ROM-path skips. Cross-game stills, Settings review, NTSC/PAL live-loop witnesses and the Opus overall-9 HUD rubric are recorded in the [HUD audit](../audits/2026-11-02-rewind-budget-hud-design.md).

Diagnostics acknowledged; the entire category-run directory was deleted after inspection.

### Exact pinned-baseline failures

All rows below reproduced on unchanged `a8f3f8647ed1e0920e7ee5119a8ce11a1d6a2ad6`, matching the frozen candidate by test identity and assertion. Only JVM object-identity suffixes and a diagnostic worktree root were normalized. Every matched baseline invocation skipped zero tests. The two final guard rows pass on the merged candidate; the unrelated ordinary cases were preserved.

| Test identity | Matching assertion |
| --- | --- |
| `com.openggf.TestGameLoopFreshLevelHandoff.realFadeCallbackKeepsItsOwnerAndDefersTheFourTitleArchives` | `LoadPalette_Immediate must clear HOLD_BLACK before deferred title init ==> expected: <NONE> but was: <HOLD_BLACK>` |
| `com.openggf.editor.project.TestEditorProjectLifecycle.cancellingNativeExitModalRestoresTheSameHeadlessPlaytestAndEditorView` | `the quit modal must suspend callbacks behind the native PLC/fade wrapper ==> expected: <0> but was: <1>` |
| `com.openggf.game.TestMasterTitleScreenAudio.cancellingRomLocationEmitsCancel` | `expected: <[UI_NAVIGATE, UI_CONFIRM, UI_CANCEL]> but was: <[UI_NAVIGATE, UI_CONFIRM]>` |
| `com.openggf.mods.code.TestRobotnikTowerDefenseScene.keyboardBuildsAndPauseAndHelpFreezeEveryGameplayTimer` | `expected: <120> but was: <150>` |
| `com.openggf.mods.code.TestRobotnikTowerDefenseScene.backspaceCancelsTheShopBeforeItsMappedStartCanPause` | `expected: <120> but was: <150>` |
| `com.openggf.mods.code.TestThreeIslandsScene.tailsJoinsInTheFieldAndTheTravellingShopReturnsToTheSameSpot` | `expected: <true> but was: <false>` |
| `com.openggf.mods.code.TestThreeIslandsScene.aSonic1ZonePlaysItsOwnRomSongWithoutPlaceholderSwitches` | `expected: <DUNGEON> but was: <MENU_ROOT>` |
| `com.openggf.mods.code.TestThreeIslandsScene.startupIsPlayableAndArrivalDialogueAndRestStayInTheField` | `expected: <FIELD> but was: <STORY>` |
| `com.openggf.mods.code.TestThreeIslandsScene.greenHillMysteriesUseRealInteractionsAndPersistThroughContinue` | `expected: <STORY> but was: <MENU_ROOT>` |
| `com.openggf.mods.code.TestThreeIslandsScene.physicalTrailsConnectFieldsAndBattleUsesTheEncounterField` | `cannot walk straight into Star Light ==> expected: <threeislands.screen.FieldScreen@<identity>> but was: <threeislands.screen.StoryScreen@<identity>>` |
| `com.openggf.mods.code.TestThreeIslandsScene.allStoryLandmarksHavePlayableInteriorsWithIndependentGatesSavesAndRewards` | `ghz enters through its actual doorway ==> expected: <DUNGEON> but was: <MENU_ROOT>` |
| `com.openggf.mods.code.TestThreeIslandsScene.travellerDialogueWaitsForTheDiscoveryOrRescueItDescribes` | `expected: <STORY> but was: <MENU_ROOT>` |
| `com.openggf.mods.code.TestThreeIslandsScene.explorationUsesTwoAxesAndStoryDiscoveriesPersistAcrossContinue` | `shield is explained by the party ==> expected: <STORY> but was: <MENU_ROOT>` |
| `com.openggf.mods.scene.host.TestPrivateLobbyScreenRendering.actualLobbyAndFullTextPixelsSupportMultipleReadablePages` | `Right reaches new full-text pixels ==> expected: <false> but was: <true>` |
| `com.openggf.tests.TestS3kMhzAct2AuthoredRoute.incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[1]` | `cold route death at 20958 ==> expected: <false> but was: <true>` |
| `com.openggf.tests.TestS3kMhzAct2AuthoredRoute.incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[2]` | `cold route death at 32674 ==> expected: <false> but was: <true>` |
| `com.openggf.tests.TestS3kMhzAct2AuthoredRoute.incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[3]` | `cold route death at 38950 ==> expected: <false> but was: <true>` |
| `com.openggf.tests.TestS3kMhzAct2EntryHeadless.coldEntryPressesLiftsAndReleasesNativeTeamWithReplay(WidescreenAspect, String, String)[12]` | `level event must preserve the cutscene-owned minimum ==> expected: <872> but was: <896>` |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture.incomingFinalFightRestoresAndReplaysEveryPhase(int)[1]` | `actual ending load follows the source request ==> expected: <13> but was: <12>` |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture.incomingFinalFightRestoresAndReplaysEveryPhase(int)[2]` | `actual ending load follows the source request ==> expected: <13> but was: <12>` |
| `com.openggf.game.rewind.TestRewindArchitectureGuard.objectRewindAnnotationsDoNotGrowWithoutExplicitBaselineTriage` | `Object packages should prefer central rewind policies/codecs. New @RewindTransient/@RewindDeferred annotations need explicit triage.; Unexpected growth:; src/main/java/com/openggf/game/sonic3k/objects/bosses/MhzEndBossExplosionController.java#@RewindTransient = 1` |
| `com.openggf.level.objects.TestObjectPhysicsStandardizationGuard.productionObjectLifecycleRawCallsMatchTheirCallerBaselines` | `Object lifecycle debt changed by caller/operation. Prefer ObjectLifetimeOps deletion or spawnChild/spawnFreeChild; tighten resolved baseline entries.;   com/openggf/game/sonic3k/objects/bosses/MhzEndBossExplosionController.java#MhzEndBossExplosionController#update(int,PlayableEntity)->setDestroyed(true) observed=1 baseline=0 (new/increased debt); Current inventory: <worktree>/target/diagnostics/architecture/object-lifecycle-call-current.txt ==> expected: <true> but was: <false>` |

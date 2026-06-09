# Temporary Release Review Fix Tracker

Status key: `open`, `in-progress`, `fixed`, `deferred`.

This file tracks the release-prep architecture/code review findings being fixed before committing to `develop`. It is intentionally temporary and should be removed or folded into permanent release docs before final release if no longer useful.

| ID | Severity | Status | Area | Finding | Primary Files |
| --- | --- | --- | --- | --- | --- |
| RRF-001 | High | fixed | Release policy | Direct `master` pushes can bypass branch-policy validation because release policy only runs on pull requests. | `.github/workflows/release.yml`, `.githooks/` |
| RRF-002 | High | fixed | Release trace gate | Release trace coverage requires only four reports despite the trace profile selecting many trace tests. | `.github/workflows/release.yml`, `pom.xml`, `src/test/java/com/openggf/tests/TestBuildToolingGuard.java` |
| RRF-003 | High | fixed | Trace policy | S3K AIZ full-run trace was regenerated, re-enabled as release-blocking, and now reaches the regenerated trace end without divergences. | `TraceReplayBootstrap.java`, `TestS3kAizTraceReplay.java`, `RingManager.java`, `CollisionSystem.java`, `TraceExecutionModel.java`, `docs/TRACE_FRONTIER_LOG.md` |
| RRF-004 | High | fixed | Trace bootstrap | S2 Tornado replay no longer applies metadata start position or primes player/Tornado ride state from route-shaped object state; remaining prelude work is native timing/object advancement only. | `TraceReplayBootstrap.java`, `TraceReplaySessionBootstrap.java` |
| RRF-005 | High | fixed | Runtime ownership | AIZ intro preload can use `BootstrapObjectServices` instead of active runtime-owned services. | `Sonic3k.java`, `AizIntroTerrainSwap.java`, `Sonic3kAIZEvents.java`, `AizPlaneIntroInstance.java` |
| RRF-006 | High | fixed | S3K progression | S3K special-stage entry rings now route Hidden Palace subtypes and all-chaos/all-super emerald state to HPZ instead of silently falling through. | `Sonic3kSSEntryRingObjectInstance.java`, `TestSonic3kSSEntryRingFormation.java` |
| RRF-007 | Medium | fixed | Object services | Production object constructors are guarded against `services()` calls while safe child construction continues through `ObjectConstructionContext`; the existing constructor/context guards now cover the release concern. | `TestNoServicesInObjectConstructors.java`, `TestConstructionContextGuard.java`, `TestObjectServicesMigrationGuard.java` |
| RRF-008 | Medium | fixed | Object services | Deprecated `DefaultObjectServices` fallback can create detached registries/pipeline when no runtime exists. | `DefaultObjectServices.java`, tests |
| RRF-009 | Medium | fixed | Trace reporting | Trace replay and credits-demo harnesses now fail warning-only reports by default, with an explicit diagnostic-only override for non-release fixtures. | `AbstractTraceReplayTest.java`, `AbstractCreditsDemoTraceReplayTest.java` |
| RRF-010 | Medium | fixed | Coordinate semantics | S3K Pachinko flipper launch distance now uses player centre X against ROM object X, with a regression test for same-centre/different-bounds sprites. | `PachinkoFlipperObjectInstance.java`, `TestPachinkoFlipperObjectInstance.java` |
| RRF-011 | Medium | fixed | Game completion | Advancing past the final configured zone now requests credits and preserves the terminal progression sentinel instead of wrapping to zone 0. | `LevelManager.java`, `TestLevelManagerEndProgression.java` |
| RRF-012 | Medium | fixed | Release guard tests | Build/release guard tests rely heavily on raw string checks and disabled-test guard only blocks one wording. | `TestBuildToolingGuard.java` |
| RRF-013 | Medium | fixed | Release automation | Manual release uses static `v0.6.prerelease` tag without an explicit tag-exists preflight. | `.github/workflows/release.yml`, `pom.xml` |
| RRF-014 | Medium | fixed | Runtime assets | S1 palette cycles, object mapping data, boss mappings, and related runtime tables now load from ROM-backed sources; provider-local mapping literals are zero and remaining tile-word remaps use `SpriteMappingPieces`. See RRF-014 asset notes below. | `Sonic1PaletteCycler.java`, `Sonic1BridgeObjectInstance.java`, `Sonic1ObjectPlacement.java`, `Sonic1ObjectArtProvider.java`, `SpriteMappingPieces.java`, `Sonic1LZConveyorObjectInstance.java`, `Sonic1SpinConveyorObjectInstance.java`, `TestArchitecturalSourceGuard.java`, related S1 assets |
| RRF-015 | Low | fixed | Presentation | S3K special-stage emerald handling now loads Super Emerald art/palette state and marks Super Emerald collection when the save state is in Super Emerald mode. | `Sonic3kSpecialStageManager.java`, `TestArchitecturalSourceGuard.java` |
| RRF-016 | Low | fixed | Presentation | S2 bridge stake subtypes 7/8 now render with the bridge renderer ground-edge frame instead of returning invisible. | `BridgeStakeObjectInstance.java`, `TestArchitecturalSourceGuard.java` |
| RRF-017 | Low | fixed | Diagnostics | S3K special-stage results audio helper failures now log warnings instead of being swallowed. | `S3kSpecialStageResultsScreen.java`, `TestArchitecturalSourceGuard.java` |
| RRF-018 | Critical | fixed | Release policy | Generated ROM-derived audio WAVs, visual PNGs, and EHZ `.kos`/`.raw` fixtures are no longer tracked; a build-tooling guard rejects them if they reappear in `git ls-files`. | `src/test/resources/`, `.gitignore`, `TestBuildToolingGuard.java`, `TestKosinskiDecompressor.java` |
| RRF-019 | High | fixed | Coordinate semantics | `CheckpointState` saves ROM/centre checkpoint positions and now restores them with playable centre-coordinate setters. | `CheckpointState.java`, `Sonic1LamppostObjectInstance.java`, `CheckpointObjectInstance.java`, `Sonic3kStarPostObjectInstance.java`, `Sonic3kAIZEvents.java`, `TestPostLoadAssemblyBehavior.java` |
| RRF-020 | High | fixed | Release trace gate | Release trace coverage now derives expected reports for the full `trace-replay` profile `Test*.java` surface, while retaining the ROM-backed `TraceReplay` execution proof. | `.github/workflows/release.yml`, `TestBuildToolingGuard.java` |
| RRF-021 | High | fixed | Native loading | Native-image LWJGL library discovery now trusts only executable-adjacent packaged libraries; the macOS env hint must canonicalize to that same directory and cwd/`target/native-libs` fallbacks are removed. | `Engine.java`, `TestEngineNativeLibDiscovery.java`, `TestBuildToolingGuard.java` |
| RRF-022 | High | fixed | Runtime ownership | S3K signpost/hidden-monitor and MHZ1 button/controller rendezvous now discovers live objects through `ObjectManager` instead of static active-instance bridges. | `S3kSignpostInstance.java`, `Mhz1CutsceneKnucklesInstance.java`, `ObjectManager.java` |
| RRF-023 | Medium | fixed | Rewind | MGZ top platform per-player grab state is held in an uncaptured `IdentityHashMap`. Fixed by giving the per-player grab helper an explicit rewind-state contract and allowing rewind collection/map codecs to restore concrete `RewindStateful` values keyed by player identity. | `MGZTopPlatformObjectInstance.java`, `GenericFieldCapturer.java` |
| RRF-024 | Medium | fixed | Save integrity | Save hash mismatches return loadable `HASH_WARNING` summaries and data select can launch them as normal slots. Fixed by distinguishing loadable slots from recoverable payloads and blocking data-select launch/clear-restart actions for hash-warning summaries. | `SaveManager.java`, `DataSelectHostProfile.java`, `DataSelectSessionController.java`, `S3kDataSelectPresentation.java` |
| RRF-025 | Medium | fixed | Config persistence | `config.yaml` writes are direct, not temp-file plus atomic move, so interrupted writes can corrupt user configuration. Fixed by publishing config saves through a sibling temp file with atomic move and cleanup fallback. | `SonicConfigurationService.java` |
| RRF-026 | Medium | fixed | Save recovery | Save/editor quarantine uses fixed `*.corrupt` names with replacement, destroying previous recovery copies on repeated failures. Fixed by moving corrupt gameplay/editor saves to the next available `.corrupt`, `.corrupt.1`, ... sibling without replacement. | `SaveManager.java`, `EditorSaveManager.java` |
| RRF-027 | Medium | fixed | CI policy | `develop` CI now runs on pull requests and direct pushes, with push events routed through `validate-policy.sh ci-push`. | `.github/workflows/ci.yml`, `TestBuildToolingGuard.java` |
| RRF-028 | Medium | fixed | Test guard | JUnit 5 migration guard now rejects wildcard/static legacy JUnit imports, `org.junit.rules` imports, and fully-qualified legacy annotations. | `TestJunit5MigrationGuard.java` |
| RRF-029 | Low | fixed | Release trace debt | Release validation no longer allowlists missing/skipped S3K AIZ replay reports, and the regenerated full-run test no longer enables the legacy diagnostic heuristic. The current branch exposes a genuine AIZ replay regression at frame 4008 instead of masking it as skipped/missing coverage. | `.github/workflows/release.yml`, `TestS3kAizTraceReplay.java`, `docs/TRACE_FRONTIER_LOG.md` |
| RRF-030 | Low | fixed | Resource loading | Shader loader now fails when a shader is absent from the runtime classpath instead of falling back to mutable `src/main/resources` files. | `ShaderLoader.java`, `TestShaderLoader.java` |
| RRF-031 | High | fixed | S3K trace parity | S3K AIZ sidekick follow/local push grace now models stale engine-local push grace as ROM-clear push state when delayed follow input, fast-leader speed, no live ride object, wide `dx`/`dy`, and near-expired grace all indicate FollowRight should nudge. The release-visible regenerated AIZ replay now reaches the trace end with no divergences. | `SidekickCpuController.java`, `TestSidekickCpuFollowParity.java`, `docs/TRACE_FRONTIER_LOG.md` |
| RRF-032 | Medium | fixed | Test parity guard | Full-suite run exposed that one CNZ sidekick support-grace regression test no longer held `Status_Push` on the setup frame, so the assertion measured two follow nudges instead of the intended clear-on-second-frame nudge. | `TestSidekickCpuFollowParity.java` |
| RRF-033 | High | fixed | S3K object parity | Full-suite run exposed that Madmole arcing side-drill and Mushmeanie jump/floor ROM parity tests depended on ambient terrain state; those tests now isolate the no-collision terrain condition they are asserting through. | `TestMadmoleBadnikInstance.java`, `TestMushmeanieBadnikInstance.java` |
| RRF-034 | Medium | fixed | Architecture ratchet | Editor save quarantine now keeps unique corrupt-filename selection inside editor persistence, removing the new `editor -> util` top-level edge. | `EditorSaveManager.java`, `TestArchUnitRules.java` |
| RRF-035 | High | fixed | Trace policy | S3K trace replay reads BK2 input without the same trace-input alignment validation used by non-S3K replay. | `AbstractTraceReplayTest.java`, trace tests |
| RRF-036 | High | fixed | Trace policy | S3K ring-count comparison can rewrite the expected trace row from next-row engine-matching diagnostics before comparison. | `TraceReplayBootstrap.java`, `TraceFrame.java`, `AbstractTraceReplayTest.java`, trace guard tests |
| RRF-037 | High | fixed | Rendering parity | S3K animated tile phases can read previous-frame parallax/deform runtime state because animated pattern updates run before parallax state publication. | `LevelRenderer.java`, `Sonic3kPatternAnimator.java`, scroll handlers, render tests |
| RRF-038 | High | fixed | Coordinate semantics | AIZ hollow-tree terrain reveal compares ROM `Player_1+y_pos` using centre `getCentreY()` instead of top-left `getY()`, with a regression test covering the reveal-control threshold. | `AizHollowTreeObjectInstance.java`, `TestAizHollowTreeObjectInstance.java` |
| RRF-039 | High | fixed | Runtime assets | S3K shared spikes, AIZ tree, AIZ zipline peg, AIZ foreground plant, monitor, and explosion mappings now load from ROM-parsed mapping frames instead of hardcoded/transcribed Java data. | `Sonic3kObjectArt.java`, `Sonic3kObjectArtProvider.java`, asset guard tests |
| RRF-040 | High | fixed | Release policy | ROM/asset inclusion policy ignores only root `*.gen`/`*.bin` and lacks hook/CI denylist checks for ROM-like files elsewhere. | `.gitignore`, `.githooks/validate-policy.*`, `TestBuildToolingGuard.java` |
| RRF-041 | Medium | fixed | Rendering cache | Runtime-controlled full-width background tilemap mode can go stale if `requiresFullWidthBgTilemap()` changes without another tilemap invalidation. | `LevelTilemapManager.java`, HTZ render/cache tests |
| RRF-042 | Medium | fixed | Runtime ownership | Editor-mode teardown resets only sprites/camera despite editor context creating level, collision, parallax, water, and game-state managers. | `EditorSessionFactory.java`, `EditorModeContext.java`, editor lifecycle tests |
| RRF-043 | Medium | fixed | Object lifecycle | AIZ boss/cutscene child, debris, and explosion objects now spawn through construction-context `spawnChild` suppliers instead of raw `ObjectManager.addDynamicObject(...)`, with a guard test covering the child paths. | `AizEndBossInstance.java`, `AizMinibossCutsceneInstance.java`, object guard tests |
| RRF-044 | Medium | fixed | Release packaging | Release workflow now smoke-validates assembled native archives before upload, including archive layout, config presence, JVM manifest bootstrap metadata, macOS version metadata, and platform launch entry points. | `.github/workflows/release.yml`, build tooling tests |
| RRF-045 | Low | fixed | Tooling | Worktree post-checkout hook still links legacy `config.json` instead of current `config.yaml`. | `.githooks/post-checkout`, build tooling tests |
| RRF-046 | Low | fixed | Packaging metadata | macOS bundle metadata now matches the Maven/release version `0.6.prerelease` and is guarded by build-tooling tests. | `src/packaging/Info.plist`, packaging scripts/tests |
| RRF-047 | Low | fixed | Packaging metadata | Stale checked-in manifest metadata was reduced to the engine entry point so old JOGL-era classpath entries cannot mislead packaging work. | `src/main/java/META-INF/MANIFEST.MF`, build tooling tests |
| RRF-048 | High | fixed | Release trace gate | Release trace coverage validation now mirrors the Maven `trace-replay` profile's diagnostic Debug/Probe source exclusions before deriving expected Surefire reports. | `.github/workflows/release.yml`, `pom.xml`, build tooling tests |
| RRF-049 | High | fixed | macOS packaging | Native config resolution now maps executables launched from `OpenGGF.app/Contents/MacOS` to the editable sibling `config.yaml` packaged beside the `.app`, while non-bundle native launches remain executable-adjacent. | `SonicConfigurationService.java`, `assemble-macos-app.sh`, release workflow/tests |
| RRF-050 | High | fixed | Runtime ownership | `GameplayModeContext` teardown now nulls disposable level managers/shared registries after reset, so a core-manager-only reattach cannot report runtime-ready with stale references. | `GameplayModeContext.java`, session lifecycle tests |
| RRF-051 | Medium | fixed | Runtime ownership | `GraphicsManager.clearRuntimeManagedReferences()` now clears the UI pipeline HUD manager along with restoring bootstrap camera/fade references. | `GraphicsManager.java`, `UiRenderPipeline.java`, session lifecycle tests |
| RRF-052 | High | open | Trace policy | Legacy S3K AIZ trace bootstrap still gates replay behavior by game/zone/act/checkpoint and frame windows instead of ROM state. | `TraceReplayBootstrap.java`, trace policy tests/docs |
| RRF-053 | High | fixed | Test/session lifecycle | Gameplay teardown and the central test reset baseline now clear `GroundSensor`'s static level-manager override so later sessions cannot scan stale level state. | `GroundSensor.java`, `TestEnvironment.java`, lifecycle tests |
| RRF-054 | Medium | open | CI policy | Direct pushes to `develop` route through the PR policy path with base/head both set to `develop`, requiring a README branch summary outside the documented merge/PR case. | `.githooks/validate-policy.sh`, `.github/workflows/ci.yml`, build tooling tests |
| RRF-055 | Medium | open | S3K animated tiles | LRZ AniPLC selection appears keyed to zone id `0x08` while the canonical LRZ zone id is `0x09`, leaving Lava Reef animated PLC setup miswired. | `Sonic3kPatternAnimator.java`, `Sonic3kZoneIds.java`, animation tests |
| RRF-056 | Medium | open | Object lifecycle | Remaining AIZ boss/cutscene object spawns still call raw `ObjectManager.addDynamicObject(...)` instead of construction-context helpers. | `AizBattleshipInstance.java`, `AizEndBossInstance.java`, `AizMinibossInstance.java`, `CutsceneKnucklesRockChild.java`, object guard tests |
| RRF-057 | Medium | open | Solid contacts | `ObjectSolidContactController.reset()` does not clear `inlineSupportedPlayers` or `forceAirOnStaleSupportLoss`, even though rewind restore clears both. | `ObjectSolidContactController.java`, solid-contact tests |
| RRF-058 | Medium | open | Object lifecycle | CNZ prize objects shadow `AbstractObjectInstance` destroyed state with local fields, risking lifecycle and rewind mismatch. | `RingPrizeObjectInstance.java`, `BombPrizeObjectInstance.java`, object lifecycle tests |

## RRF-014 asset notes

S1 runtime data moved to ROM-backed loading for palette cycles, conveyor waypoint
and child spawn tables, GHZ bridge bend tables, a small SLZ/support-object
mapping slice, all S1 boss mappings, and these object mappings:

- LZ: `Map_Jaws`, `Map_Burro`, `Map_Flap`, `Map_WFall`, `Map_Splash`,
  `Map_Gar`, `Map_LBlock`, `Map_LConv`, `Map_Bub`, `Map_MBlockLZ`, `Map_Harp`
- MZ/SLZ/SBZ: `Map_Fire`, `Map_Bas`, `Map_Glass`, `Map_CStom`, `Map_Geyser`,
  `Map_LWall`, `Map_CFlo`, `Map_MBlock`, `Map_Cat`
- GHZ/SYZ/SLZ: `Map_Hel`, `Map_Swing_GHZ`, `Map_Swing_SLZ`, `Map_Bump`,
  `Map_Spring`, `Map_Roll`, `Map_Yad`, `Map_Crab`, `Map_Moto`, `Map_Newt`,
  `Map_GBall`, `Map_SBall`, `Map_SBall2`, `Map_BBall`, `Map_Plat_GHZ`,
  `Map_Plat_SYZ`, `Map_Plat_SLZ`
- Shared/other: `Map_Poi`, `Map_Bonus`, `Map_GRing`, `Map_Flash`,
  `Map_BossBlock`, `Map_Push`, `Map_Jun`, `Map_Disc`, `Map_Brick`,
  `Map_Light`, `Map_Smab`, `Map_Elev`, `Map_Circ`, `Map_Stair`,
  `Map_UnkExplode`, `Map_Ledge`, `Map_LGrass`, `Map_FBlock`, `Map_VanP`,
  `Map_Buzz`, `Map_Missile`, `Map_Smash`, `Map_Orb`, `Map_Hog`, `Map_Bomb`,
  `Map_Shield`, `Map_Animal1`, `Map_Animal2`, `Map_Animal3`, `Map_Got`,
  `Map_SSR`, `Map_SSRC`, `Map_Pri`, `Map_But`
- SBZ/ending: `Map_Flame`, `Map_Saw`, `Map_Elec`, `Map_ADoor`, `Map_Gird`,
  `Map_Trap`, `Map_Spin`, `Map_Stomp`, `Map_FFloor`, `Map_ESon`,
  `Map_ECha`, `Map_ESth`

The legacy boss mapping helper file was removed.

## Trace-scope notes

- RRF-003: `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun` has been
  regenerated with Lua `6.25-s3k`, is no longer diagnostic-only, and passes the
  focused release-blocking replay through the HCZ handoff. The closed frontiers
  include duplicate placed-ring coordinates, S3K raw ring parsing/window
  semantics, S3K odd floor-lip angle fallback, AIZ miniboss/title-card handoff,
  AIZ2 bridge/capsule/camera timing, and S3K transition-mode replay
  classification.
- RRF-004 removed the committed trace-to-engine ride-state bootstrap. S2 SCZ/WFZ
  may expose earlier native frontiers once the wider workspace compiles and
  those ROM-backed traces can run.

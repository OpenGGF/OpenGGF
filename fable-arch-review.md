# OpenGGF Architecture & Code Review — develop @ `c4b45fe5f`

**Date:** 2026-06-09
**Scope:** Release-readiness review of `develop` (3,712 commits / ~3,214 files changed since `v0.5.20260411`; ~512k lines of main source across 1,808 files).
**Method:** 11 independent dimension-scoped review agents (service architecture, physics policy, mutation/rewind, object hygiene, resources, concurrency/lifecycle, test health, release sweep, recent-churn risk, save/config, structural metrics), followed by independent adversarial verification of every critical/high finding, followed by a completeness critic over packaging/CI/licensing. 23 agents, ~840 tool invocations. **All 11 critical/high findings survived adversarial verification (0 refuted)**; medium/low findings are reviewer-confirmed but not independently re-verified.

---

## Executive summary

The codebase is in **substantially better shape than its size and churn rate would suggest**. The big architecture migrations the project has invested in are real and hold under audit: the two-tier service model has an *empty* unmigrated baseline, the retired `GameRuntime` facade has zero survivors, gameplay teardown+rebuild nulls all 22 manager fields, GL/native resource lifecycle is consistently paired, the test suite has zero JUnit 4 contamination and unusually deep guard coverage (27+ scanner guards), mutation routing discipline holds everywhere it was checked, and the repo is verifiably clean of copyrighted asset bytes (including the non-obvious savestate-in-`.bk2` vector).

**The release is currently blocked by the repo's own policy**, and there are two structural problems that deserve attention before tagging 0.6:

1. **The S3K AIZ full-run trace replay — newly made a hard, non-skippable release gate — fails today** at frame 6203 (`rings expected=58 actual=59` after `aiz2_reload_resume`). By the policy the project itself just hardened, the 0.6 release pipeline fails.
2. **A genuine rewind-corruption bug**: the level snapshot copy-on-write epoch is bumped only on *restore*, never on *capture*, so layout mutations silently rewrite already-captured rewind keyframes — in exactly the S3K zones the release slice targets. A unit test masks it by manually bumping the epoch.
3. **The structural root cause of late-discovered breakage**: develop CI runs zero ROM-backed or trace-replay tests; the entire accuracy gate runs only against `master` (~3,169 commits behind). This is why the AIZ blocker and the guard-red LBZ merge window were discovered late.

Beyond those, the high findings cluster into: a silent argument-order bug in the cross-game physics hybrid, crash-to-desktop failure modes around bad ROMs and save restore, guard ratchets left so loose they enforce nothing, an uncited trace-fitted heuristic block in shared sidekick code, a default-suite test that deletes real user save data, and re-accretion of the two central coordinator god classes.

---

## 1. Release blockers (critical, verified)

### 1.1 Release-gated S3K AIZ full-run trace replay fails at frame 6203
`docs/TRACE_FRONTIER_LOG.md:25-37` · dimension: release-sweep · verifier confidence: high

The RRF-052 frontier entry (2026-06-09) records the full release replay reaching frame 6203 with first release-visible failure `rings expected=58 actual=59` after `aiz2_reload_resume`. The gate is real and has no escape hatch: `TestBuildToolingGuard#regeneratedS3kAizFullRunReplayIsReleaseBlocking` forbids `@Disabled`, the legacy diagnostic-heuristic opt-in is gone, and `release.yml` runs `mvn test -Ptrace-replay` which captures the test. The verifier confirmed no commit after `ab9388caf` touches anything related to ring collection — there is no silent fix at HEAD.

**Action:** Fix the ring-collection divergence around the `aiz2_reload_resume` segment (frontier log already isolates position/velocity/camera/zone state as aligned), or make an explicit, documented release decision to waive it. Do not silently re-add a coverage allowlist.

### 1.2 Rewind keyframes are silently corrupted by later layout mutations (capture-side epoch bump missing)
`src/main/java/com/openggf/level/rewind/LevelRewindSnapshotAdapter.java:30-56` · dimension: mutation-rewind · verifier confidence: high

`capture()` stores the live `Map` byte[] by reference; only `restore()` calls `level.bumpEpoch()` (the *only* production call site, repo-wide). `Map.cowEnsureWritable` clones only when `lastTouchedEpoch < currentEpoch`, and both initialize to 0 — so for the entire session before the first rewind restore, `cowEnsureWritable(0)` never clones and every gameplay map mutation writes in place into the array shared with the frame-0 keyframe and all periodic keyframes. The same applies between two mutations within any epoch. Every S3K layout-mutating event (`Sonic3kCNZEvents`, `Sonic3kLBZEvents`, `Sonic3kMGZEvents`, `Sonic3kMHZEvents`, `AizIntroTerrainSwap`, `S3kSeamlessMutationExecutor`) retroactively rewrites the layout inside previously captured keyframes: rewinding/seeking to a frame before a boss-arena or terrain mutation shows (and collides against) the post-mutation layout. The verifier confirmed the masking test (`TestLevelManagerRewindSnapshot.java:198-211`) manually calls `bumpEpoch()` between capture and mutate — simulating a step production never performs — and that rewind parity tests compare two runs that corrupt identically, so they cannot catch it. Note the bug is Map-specific: the Block/Chunk paths clone-and-replace unconditionally and are safe.

**Action:** Bump the epoch in `LevelRewindSnapshotAdapter.capture()` (or clone the map data into the snapshot at capture). Add a regression test that captures, mutates via `DirectLevelMutationSurface` *without* a manual `bumpEpoch()`, and asserts the snapshot is unchanged. Rewrite the masking test to drive mutation through the production flow (it should fail today, pinning the fix).

---

## 2. High-severity findings (verified)

### 2.1 Develop CI never executes any ROM-backed or trace-replay test
`.github/workflows/ci.yml:41-42` · dimension: completeness critic

The develop CI job runs `mvn -Dmse=off test -B` with no ROM path properties (every `@RequiresRom` test skips via assumption), and the default surefire config (`pom.xml:362-373`) excludes the entire `**/tests/trace/**` tree. The only workflow that runs ROM tests, the `-Ptrace-replay` profile, and the trace-coverage release gate is `release.yml`, which triggers exclusively on PRs/pushes to `master` — ~3,169 commits behind develop. Every PR merged into develop is validated with **zero physics-accuracy coverage** for a project whose stated critical requirement is pixel-for-pixel parity. This is the structural root cause of both the late-discovered AIZ blocker (§1.1) and the guard-red LBZ merge window (§2.8).

**Action:** Add a scheduled (nightly) + `workflow_dispatch` workflow on develop that reuses the existing self-hosted `release-fixtures` runner to run `mvn test -Ptrace-replay` with ROM paths and the coverage-assertion script. Also add a minimum-executed-test-count assertion to the develop CI job so a future surefire/skip misconfiguration cannot silently pass an empty run.

### 2.2 `CrossGameFeatureProvider.buildHybridFeatureSet()` passes 7 booleans out of order, corrupting two physics flags for S1/S2 hosts
`src/main/java/com/openggf/game/CrossGameFeatureProvider.java:467-474` · dimension: physics-policy · verifier confidence: high

`PhysicsFeatureSet` is a 71-component positional record with only its canonical constructor; the hybrid builder hand-copies it with a 7-slot rotation of boolean arguments (the inline comments even mislabel each slot as correct). With cross-game donation enabled and an S1/S2 base game: `levelBoundaryRightStrict` receives `levelBoundaryUsesCentreY`'s `true` (wrong boundary clamp semantics — ROM S2 uses non-strict `bls.s` per `s2.asm:36933`) and `levelBoundaryUsesCentreY` receives `false` (the human player's bottom kill-plane reverts to top-left Y, ~12–20px late; CPU sidekicks are mitigated by an `isCpuControlled()` OR at `PlayableSpriteMovement.java:2447`). The other five slots receive coincidentally correct values today, so any future flag change silently propagates to the wrong component. S3K hosts are unaffected only because all seven flags happen to be `true` there. No test pins the hybrid output: `TestHybridPhysicsFeatureSet` never invokes `buildHybridFeatureSet` and even hard-codes the wrong expectation. Mitigating: `CROSS_GAME_FEATURES_ENABLED` defaults to `false`, so default installs are unaffected.

**Action:** Fix the argument order; then eliminate the failure mode with a copy-with-overrides helper (only ~4 donor-driven fields actually change) so the hybrid never re-enumerates 71 positional args. Add a reflective test asserting `hybrid(base=SONIC_2, donor=SONIC_3K)` equals `SONIC_2` on every non-donor component. See also §3 (physics-policy) for the structural cause.

### 2.3 Default-suite test deletes the user's real S2 editor save file
`src/test/java/com/openggf/editor/TestEditorToggleIntegration.java:262` · dimension: test-health · verifier confidence: high

The test constructs `new EditorSaveManager(Path.of("saves"))` — the identical relative root production uses (`Engine.java:649`, `LevelManager.java:3082`) — resolves `saves/s2/edits/zone_1_act_1.json`, and `Files.deleteIfExists` it both before the test body and in `finally`. The class runs under plain `mvn test` (only gated on the S2 ROM, present in dev setups), `saves/` is gitignored, so deleted edits are unrecoverable. Silent user-data loss in a working tree shared by concurrent agent sessions; sibling tests already model the correct pattern (synthetic gameCodes / `@TempDir`).

**Action:** Root the test's save manager (and the Engine paths it exercises) in a `@TempDir`, or at minimum a test-only gameCode subdirectory. Never `deleteIfExists` a production save path from a test.

### 2.4 Unrecognized/corrupt ROM silently falls back to `Sonic2GameModule`, then crashes with a raw stack trace
`src/main/java/com/openggf/Engine.java:455-463` · dimension: save-config · verifier confidence: high

ROM detection failure logs a warning and proceeds with a guessed Sonic 2 module; the engine then interprets arbitrary bytes as S2 data until a bounds-checked read throws `IOException`, which is wrapped in `RuntimeException` and propagates uncaught (`run()` has try/finally only; no uncaught-exception handler exists anywhere in src/main; the master-title check is `File.exists` only). The process dies with a stack trace and no "unsupported or corrupt ROM" message — and user-supplied ROMs are the product's primary input.

**Action:** When `detectAndCreateModule` returns empty, do not proceed: return to the master title screen with an on-screen "ROM not recognized" message. Add a catch around Phase-2 init that surfaces a readable error.

### 2.5 Save restore with a failing zone loader throws inside the fade-complete callback and kills the app
`src/main/java/com/openggf/Engine.java:777-783` · dimension: save-config · verifier confidence: high

`loadLevelFromDataSelect` rethrows `IOException` as `RuntimeException`; the data-select action dispatch runs inside `FadeManager`'s fade-complete callback, which runs the callback bare (`FadeManager.java:470-474`), and the chain up through `Engine.loop()` and `main()` has no catch. `S3kDataSelectProfile.isPayloadValid` accepts zones 0–21 while the slice implements a subset, and real throw sites exist on the S3K load path (`Sonic3kLevel.java:228`, `Sonic3k.java:455`). Crash-on-continue with total session loss at the exact moment a user tries to resume their game; the companion save-slot read at `Engine.java:826` has the same exposure.

**Action:** Catch failures in the data-select launch dispatch and return to the data-select screen with an error indication. Cover `RuntimeException` from the loader, not just `IOException`.

### 2.6 Raw `addDynamicObject` ratchet budget left 19× above actual count (129 calls of slack)
`src/test/java/com/openggf/level/objects/TestObjectPhysicsStandardizationGuard.java:126` · dimension: object-hygiene · verifier confidence: high

`RAW_ADD_DYNAMIC_OBJECT_OBJECT_PACKAGE_BUDGET = 136` exactly matched the violation count when introduced (commit `0bf31e97a`, 2026-06-05), but the release-review burn-down reduced actual violations to 7 and the constant was never tightened — so the ratchet currently enforces nothing. The verifier confirmed this is the *sole* repo-wide control for raw insertion (the child-spawn guard covers only ~79 enumerated migrated files). The sibling `setDestroyed` budget (585 vs 581 actual) has already silently absorbed one undocumented new violation (§2.9). Related medium finding: the ratchet regex misses the `addDynamicObjectAfterCurrent/NextFrame/AfterSlot` variants entirely, a hole `MgzMinibossInstance.java:331` exploits today.

**Action:** Ratchet both budgets to actuals (7 / 581) as release prep; adopt the policy of lowering constants in the same commit as burn-down work; extend the regex to the sibling variants.

### 2.7 Shared `SidekickCpuController` gained a dense block of trace-fitted magic-threshold heuristics
`src/main/java/com/openggf/sprites/playable/SidekickCpuController.java:1593-1647` (constants `:47-58`), commit `552d4e65e` · dimension: churn-risk · verifier confidence: high

The RRF-031 trace fix added 8 threshold constants (dx band `[0x90,0xA0]`, dy gaps `0x40/0x50`, grace windows with an ad-hoc `+1`) and five OR'd dx/dy/grace bands to the sidekick follow-steering bypass — none carrying ROM citations, unlike the surrounding code which cites `sonic3k.asm`/`s2.asm` line ranges. The ROM Tails CPU has no such bands; RRF-031's own log describes heuristic grace modeling. The verifier tempered two claims: the quoted block is gated behind `sidekickPushBypassUsesGraceStatus` (S3K-only), so it is dead for S2 — though the sibling `suppressFastLeaderTinyFollowNudge` from the same commit *is* ungated on both nudge branches — and only one follow-up fixture repair was found, not two. Still high for the release: uncited curve-fits in shared steering code that the active CNZ/MGZ/ICZ slice traces will exercise, in direct tension with the repo's model-ROM-state-not-traces rule.

**Action:** Run the full S2+S3K sidekick-bearing `*TraceReplay` sweep and record it in `docs/TRACE_FRONTIER_LOG.md` as the regression baseline for this block. Medium-term, replace the bands with the ROM-visible state they approximate (delayed `Stat_table` status + `Ctrl_2` at Tails' CPU slot) or move them behind a provider-owned predicate so each band has a named ROM condition.

### 2.8 LBZ1 miniboss merge landed guard-red on develop; hot-fixed 10 minutes later *(medium in itself, but evidence for §2.1)*
`DefaultObjectRewindPolicies.java:142` (broken merge `492663b7d`, fix `c4b45fe5f`) · dimension: churn-risk

Between 18:14 and 18:24 on 2026-06-09, develop HEAD failed at least the rewind field-coverage audit. HCZ/ICZ/MGZ minibosses each needed the same post-merge classification fix — the boss-implementation workflow reliably produces this gap, and the pre-merge gate doesn't catch it (because of §2.1).

**Action:** Make `TestRewindFieldAudit` and the touch-response-profile guard part of the pre-merge PR gate, and add the `DefaultObjectRewindPolicies` classification step to the `s3k-implement-boss` skill checklist.

### 2.9 GameLoop and LevelManager have re-accreted into god classes
`GameLoop.java` (3,838 lines, ~205 members, 126 commits since 2026-03) · `LevelManager.java` (4,085 lines, 284 commits since 2026-03 — the hottest file in the repo) · dimension: structure-metrics · verifier confidence: high

GameLoop owns 10+ unrelated mode/screen concerns (special stage, bonus stage incl. water save/restore, results, title cards, master title, disclaimer, editor, audio pause arbitration, debug keys, checkpoint teleport) while its Javadoc still claims the original four responsibilities. LevelManager contradicts CLAUDE.md's "thin coordinator" description: real player/sidekick art-init, VRAM bank allocation, tile-descriptor collision lookups, and checkpoint/spawn logic live in it (the verifier noted ~5 of 17 cited methods are legitimate thin delegations, and that an architectural ratchet caps GameLoop at 3,844 lines — partial mitigation, but the budgets are bumpable and the conflict-surface problem stands for the documented multi-session workflow).

**Action (contained, not a grand refactor):** Extract GameLoop's bonus-stage and special-stage lifecycle blocks into mode-controller classes beside the existing `BootScreenModeController`/`MenuScreenModeController` pattern. Move LevelManager's player/sidekick art-init cluster (~lines 1190–1620) into a `PlayerArtInitializer` and route the tile-descriptor query family through `LevelTilemapManager`. Update the CLAUDE.md table if "thin coordinator" is retired — agents are told to trust it.

### 2.10 Shared `SidekickCpuController` embeds S3K constants via fully-qualified names invisible to import audits
`SidekickCpuController.java:2562, 2599-2608` · dimension: structure-metrics · verifier confidence: high

Six fully-qualified `com.openggf.game.sonic3k.constants.Sonic3kConstants.TAILS_*` references with no import line — the only code dependency from `sprites/` to `game.sonic*`, and the S2-reachable flight-auto-recovery path compile-depends on them. The repo's `CONCRETE_SONIC_REFERENCE` guard regex would catch these, but its scan scope is only `Engine.java`/`GameLoop.java`/`ObjectManager.java` — `sprites/playable` is unscanned. The same file already uses the sanctioned route (`PhysicsFeatureSet` carries `SIDEKICK_*_S3K`), so an extension point exists.

**Action:** Lift `TAILS_CATCH_UP_Y_OFFSET` / `TAILS_FLIGHT_*` onto `PhysicsFeatureSet` (or a `SidekickBehaviorProfile`), set values in the `SONIC_3K` constant, delete the FQN references. Extend the concrete-reference guard's scan scope to `sprites/`, `physics/`, `util/`.

---

## 3. Medium-severity findings (reviewer-confirmed)

Grouped by theme. File references in parentheses.

### Lifecycle / state leaks
- **`GumballMachineObjectInstance.currentInstance` static is never cleared** by any lifecycle reset — a destroyed machine and its entire injected `ObjectServices → GameplayModeContext` graph survives stage exit and session teardown; consumers null-check but can't detect staleness. Acknowledged in the static-state guard baseline but "reviewed ≠ reset". (`GumballMachineObjectInstance.java:290`; flagged independently by two dimensions)
- **`EditorModeContext.destroy()` leaves all manager references set** and `isEditorRuntimeReady()` returns `true` on a destroyed context — the fc6ec1649 stale-reference discipline was applied to GameplayModeContext but not the editor context. (`EditorModeContext.java:104`)
- **Static session-state inventory guard covers only the sonic3k package**; S1/S2 object statics rely on unguarded manual reset whitelists, and the guard verifies a field was *seen*, not that it has a teardown path. (`TestBuildToolingGuard.java:52`)

### Guards & tests that can't catch what they exist to catch
- **Scanner guards silently pass when scan roots are missing** — no minimum-scan assertion; two listed roots already don't exist today. A package rename turns the guards into green no-ops. (`TestObjectPhysicsStandardizationGuard.java:72`)
- **`TestNoDirectMapMutationsInGameplay` can be dodged by helper indirection** (Map-typed parameters/fields, raw `getData()` writes are invisible to its regexes), and three of its four allowlist entries point at a package that no longer exists. (`TestNoDirectMapMutationsInGameplay.java:32-55`)
- **The COW-isolation unit test masks the §1.2 bug** by manually calling `bumpEpoch()`, and uses bare `assert` statements (no-ops without `-ea`). (`TestLevelManagerRewindSnapshot.java:198-211`)
- **Lifecycle ratchet regex blind spot** for `addDynamicObjectAfterCurrent/NextFrame/AfterSlot` (exploited by `MgzMinibossInstance.java:331`, which should be `spawnChild(...)`). (`TestObjectPhysicsStandardizationGuard.java:46-53`)
- **Config tests boot `SonicConfigurationService` against the shared repo CWD** without `@TempDir`/`user.dir` isolation under forkCount=4 — can rewrite the developer's `config.yaml` mid-run. The isolated-bootstrap pattern exists in-tree but isn't applied consistently. (`TestConfigKeyNameResolution.java:16`)

### Physics flag framework
- **`PhysicsFeatureSet` is a 71-component positional record** constructed with long runs of unlabeled booleans — the structural cause of §2.2; a transposition compiles cleanly and only trace replay can catch it. Introduce a builder or cohesive sub-records. (`PhysicsFeatureSet.java:879-1004`)
- **Stale flag javadoc contradicts the actual `SONIC_2` value** for `solidObjectOffscreenGate`; the per-flag docs are the project's authoritative ROM-policy record and have drifted. Several flags are also documented as intentionally ROM-divergent to protect trace baselines — track that debt in `KNOWN_DISCREPANCIES.md`. (`PhysicsFeatureSet.java:297-303`)

### User-facing failure modes & data loss (slow paths)
- **`SaveManager` quarantines save files on *any* exception, including transient I/O errors** (Windows AV/cloud-sync lock → valid save renamed `.corrupt`, shown as empty slot) — while a genuine SHA-256 mismatch is only a warning. Quarantine on parse/validation failures only. (`SaveManager.java:83-90`)
- **Malformed `config.yaml` is silently replaced by defaults** on the next `saveConfig()` (e.g. pressing V in-game) with no backup — unlike the legacy JSON migration which preserves a `.bak`. (`SonicConfigurationService.java:51-59`)
- **Missing ROM crashes with a raw stack trace when the master title screen is disabled** (a documented config option). (`Engine.java:461-462`)
- **macOS native-image path hardening not applied to the master-title ROM check or saves directory** — availability UI, ROM open, and save persistence can disagree on the base directory on exactly the platform the fix targets. (`MasterTitleScreen.java:174`)
- **Debug HUD enabled by default in the release config** (bundled resource + code default; other debug defaults are correctly off). (`SonicConfigurationService.java:413`)

### Unbounded growth (long sessions)
- **`AudioCommandTimeline` grows unbounded for the whole gameplay session and is scanned O(N) every frame** — even with live rewind disabled; at odds with the explicitly capped sibling PCM ring. (`AudioCommandTimeline.java:8`)
- **Live-rewind keyframe store retains full-state snapshots with no eviction within a level** (opt-in feature, but silently unlimited memory on the state side while the audio side is capped). (`InMemoryKeyframeStore.java:11`)

### Parity/content gaps masked by TODOs
- **Bonus/special-stage return drops extra-life flags** — hardcoded 0 captured, field never restored; the 100/200-ring latch state is lost across the round trip. (`GameLoop.java:1618`)
- **S1 SBZ2 boss spawn + collapsing floor unimplemented** (documented TODO in core S1 endgame events). (`Sonic1SBZEvents.java:21-23`)
- **S2 MCZ boss `boss_hurt_sonic` never wired** — drill-contact ROM behavior missing. (`Sonic2MCZBossInstance.java:817-818`)
- *(All three: implement or record in `docs/KNOWN_DISCREPANCIES.md` / release notes so they're stated decisions.)*

### New code re-adding debt
- **LBZ1 miniboss adds raw `setDestroyed(true)` with no documented reason** (should be `ObjectLifetimeOps.destroyLatched`) **and four production `force*ForTest` mutators**; its headless test drives only the force-shortcuts, so the real escape→box-drop→spawn timing path is untested. Same-day sibling commit `72f953901` was removing exactly this debt class from CNZ. (`LbzMinibossInstance.java:447`, `Lbz1RobotnikEventController.java:257-279`)

### Duplication / layering erosion
- **Deprecated `WaterSystem` retains S1/S2 zone-id branching in the shared `level` package**, still reachable via the `LevelManager:784` fallback — exactly the pattern the anti-carve-out rule forbids, kept alive "for tests". (`WaterSystem.java:448-575`)
- **Bridge depression physics triplicated** across S1/S2/S3K (the S3K Javadoc admits "identical math to S1's"); extract a `BridgeDepressionHelper` into `level.objects`. (`TensionBridgeObjectInstance.java:29`)
- **S1/S2 data-select preview cache managers ~88% line-identical** after a game-token swap; hoist into `game.dataselect`. (`S1DataSelectImageCacheManager.java:37`)
- **`AbstractPlayableSprite` at 4,905 lines** and still growing — the ROM-SST mirror role explains the accessor bulk, but sensor/radii/water behavior dilutes the documented decomposition; freeze behavioral growth and extract the `updateSensors`/radii cluster next. (`AbstractPlayableSprite.java:63`)

---

## 4. Low-severity findings (brief)

- `SidewaysPformObjectInstance` MCZ mapping-load failure latches permanently with no reset (platforms invisible for the JVM lifetime after one bad read). (`SidewaysPformObjectInstance.java:230`)
- A few `PhysicsFeatureSet` flags identical across all three games (dead divergence axes); `waterSplashUsesFixedDustObject()` aliases `spindashEnabled` — incidental coupling that breaks under hybrid composition. (`PhysicsFeatureSet.java:1019-1021`)
- `DirectLevelMutationSurface` non-AbstractLevel fallback mutates snapshot-shared Chunk/Block in place — unreachable today, latent integrity trap. (`DirectLevelMutationSurface.java:49-51`)
- `TestNoServicesInObjectConstructors` exists twice (empty subclass) — the heaviest source-scan suite runs twice per build. (`TestNoServicesInObjectConstructors.java:3`)
- Two default-suite test classes contain only exception-only smoke tests with zero assertions. (`TestYm2612VoiceLengths.java:12`)
- OpenAL WAV-SFX fallback leaks one source per play and references `.wav` assets that aren't bundled (effectively dead code that can exhaust the ~256-source limit in degraded-audio states). (`LWJGLAudioBackend.java:285`)
- `ChunkDesc.EMPTY`/`PatternDesc.EMPTY` are public static **non-final mutable** sentinels shared engine-wide — nothing enforces the no-in-place-mutation invariant. (`ChunkDesc.java:40`)
- Two call sites hand-roll the construction-context ThreadLocal and *clear* rather than restore it (exception-safe today, wrong if ever nested). (`Sonic1EndingSonicObjectInstance.java:370`)
- `tearDownManagers()` nulls every disposable reference except `profiler`. (`GameplayModeContext.java:574-597`)
- `TraceReplayBootstrap` post-prefix latch-row classification keys off recorded state-equality rather than an explicit recorder marker — would absorb a recorder duplicate-row bug. (`TraceReplayBootstrap.java:596-613`)
- Deprecated `PsgChip` is fully dead code shipping in the JAR; one deprecated `WaterSystem.loadForLevel` fallback call remains. (`PsgChip.java:9-10`)
- `AbstractLevel.markAllDirty()` is a public no-op the rewind test stub calls in place of the production invalidation path. (`AbstractLevel.java:222-228`)
- `Sonic3kSpecialStageRomOffsets` ships `-1` "TODO: verify" placeholders shadowed by verified constants elsewhere. (`Sonic3kSpecialStageRomOffsets.java:156-164`)
- Master title screen navigation/confirm/error sounds are silent stubs — first-screen polish gap. (`MasterTitleScreen.java:539-541`)
- Data-select launch reads and hashes the save slot file twice per launch. (`Engine.java:754-755`)
- `util` package depends upward on `LevelManager`/`GameServices` (`LazyMappingHolder` silently empty outside a gameplay session). (`LazyMappingHolder.java:37`)
- **Packaging/CI (critic):** release packaging (assembly jar, GraalVM native image, LWJGL natives unpack) is never exercised on develop — first build of the shipping artifact happens during the release itself (mitigated by the strong per-platform smoke validator in `release.yml`). 152 MB of trace fixtures are tracked under `src/test/resources/traces` (233 MB pack) and grow with every regeneration — consider LFS or an artifact store. No gamepad/controller support exists at all (keyboard-only via one `glfwSetKeyCallback`) — add GLFW gamepad support or document keyboard-only as a stated 0.6 limitation.

---

## 5. What's verifiably healthy (positive assurance)

- **Service architecture:** `TestObjectServicesMigrationGuard` baseline is empty; all ~300 object subclasses are guard-scanned and clean; every `GameServices` bridge line individually documented; zero `GameRuntime`/`RuntimeManager` survivors; `tearDownManagers()` nulls all 22 fields; editor exit follows the design doc.
- **Physics policy:** no game-name branching and no zone/route/frame carve-outs anywhere in shared physics paths; divergences consistently flow through `PhysicsFeatureSet`/`PhysicsProfile` with disassembly citations.
- **Mutation routing:** every gameplay-path layout edit found routes through the pipeline/surface; only the exempt decoder touches `Map.setValue` directly. `GenericFieldCapturer` deep-clones correctly and forces explicit decisions on final fields.
- **Object hygiene:** `spawnChild` adoption near-universal; late service injection means no live invisible-spawn bug; sampled ENEMY touch handlers use ROM-accurate per-frame semantics.
- **Resources:** every sampled GL resource owner has paired, actually-invoked cleanup across engine/editor/act-transition teardown; native allocations freed in try/finally; saves written atomically (tmp + `ATOMIC_MOVE`); `System.exit` only in CLI tools.
- **Concurrency:** effectively single-threaded by design; the one real background thread (capture encoder) is correctly isolated with bounded queue + poison pill; `ObjectConstructionContext.with()` is exception-safe and restores (not clears) the previous context.
- **Tests:** zero JUnit 4 imports (guard enforced, fails loudly on missing root); only 2 `@Disabled` tests, both tracked and allowlist-pinned; ROM gating is assumption-based; 140/141 session-booting tests tear down properly.
- **Release hygiene:** only 38 TODO/FIXME occurrences across 15 of ~1,800 files; version flow coherent; CHANGELOG, README releases, both discrepancy docs, and the trace frontier log all current as of 2026-06-09; deprecation hygiene otherwise clean.
- **Licensing/assets (critic-verified):** no copyrighted-asset bytes tracked anywhere — no ROM-extension files, all 26 `.bk2` movies contain input logs only (no savestates), disasm/SMPS trees untracked, `*.gen` gitignored.

---

## 6. Recommended pre-release order of work

1. **Fix the AIZ frame-6203 ring divergence** (or make a documented waiver decision). *Release-gated today.* (§1.1)
2. **Fix the capture-side COW epoch bump** + regression test; un-mask the lying unit test. (§1.2)
3. **Fix the `buildHybridFeatureSet` argument rotation** + component-wise hybrid test. Small, silent, physics-corrupting. (§2.2)
4. **Stop the test that deletes real user saves** (`@TempDir`). One-line-class fix, data-loss class. (§2.3)
5. **Add user-facing failure handling for bad ROM / failed save restore** instead of process death. (§2.4, §2.5)
6. **Tighten the guard ratchets to actuals (7/581), close the `AfterCurrent` regex hole, add minimum-scan assertions** — cheap, and it re-arms the entire guard fleet before the release push. (§2.6, mediums)
7. **Flip the debug HUD default off** for the release package. (§3)
8. **Stand up nightly ROM-backed trace CI on develop** using the existing self-hosted runner — the structural fix that prevents the next late blocker. (§2.1)
9. **Record the sidekick-heuristic regression baseline** (full S2+S3K sidekick trace sweep into the frontier log) before more slice work lands on top of it. (§2.7)
10. Post-release: god-class contained extractions (§2.9), S3K-constant lift out of `sprites/` (§2.10), `WaterSystem` legacy removal, duplication hoists, unbounded audio/rewind history caps.

---

*Generated by a 23-agent review workflow (11 dimension reviewers → adversarial verification of all critical/high findings → completeness critic). All critical/high findings were independently re-verified against the code; medium/low findings are single-reviewer confirmed. Line numbers reference develop @ `c4b45fe5f`.*


---

# Addendum: Cross-validation of fix merge `6e2fa885a` (2026-06-10)

**Scope:** The 23-commit fix branch `bugfix/ai-release-review-fixes-20260609` merged into develop, validated by 8 adversarial fix-cluster verifiers plus a full-suite run in an isolated worktree (7,282 tests, ROMs present).

## Scorecard

| Cluster | Verdict | Headline |
|---|---|---|
| rewind-cow (§1.2 critical) | **fixed** (cluster partial) | Capture-side `bumpEpoch()` is correct for both failure cases; real regression test. The lying masking test was left in place, not rewritten. |
| AIZ frame-6203 blocker (§1.1) | **fixed** | Genuinely fixed (ring touch-phase + sidekick push change), honest frontier-log re-run chain (6203 → 14302 → GREEN full replay). No gate file weakened. |
| hybrid-physics (§2.2) | **fixed** (cluster partial) | All 78 args re-derived independently — rotation fixed; real reflective test. But no builder adopted, and the positional hazard was *re-widened* (record grew 71→78 with mid-list insertions; 6 new ints value-identical across games, so transpositions are undetectable). |
| crash-paths (§2.4/§2.5) | partial | §2.5 crash-on-continue fixed end-to-end. §2.4 half-fixed: silent Sonic2 fallback removed (clear exception) but still **process death on all three paths** — no catch, no on-screen message. |
| saves-config (§2.3 + mediums) | partial | Save-deleting test fixed for the write path; config quarantine real. But `LevelManager.java:3082` still hardcodes `Path.of("saves")` and resume tests can still quarantine-rename the real user file; debug HUD default **not** flipped (docs-only "clarify"); config-test CWD isolation not actually achieved (`createStandalone()` still hits the shared CWD); SaveManager transient-IO quarantine untouched. |
| guards-ratchets (§2.6) | **fixed_with_concerns** | Best cluster. Budgets now exact-equality at independently-reproduced actuals (581/10), regex covers all 4 variants, min-scan + missing-root failures added, sprites/ concrete-ref guard added. `f7e6948a5` contains **no loosening**. Residual: Mgz/Lbz violations counted-in rather than converted; standardization scan still has 2 dead roots; map-mutation guard still blind to indirection. |
| sidekick (§2.7/§2.10) | partial | §2.10 fixed (all six FQNs gone, constants on PhysicsFeatureSet, guard added). §2.7 **open**: FAST_LEADER band block untouched; `5b091cd67` added another uncited 0xE0 threshold and its "ROM-visible push state" framing is provably cosmetic (same `getPushing()` bit, lines 1362-1363); demanded S2+S3K sidekick trace sweep never recorded while `1594111e4` changes live S2 steering. |
| ci-docs (§2.1) | partial | The YAML is correct (right profile, ROM props, runner, strong coverage assertion, min-test-count on plain CI) **but the nightly will never fire**: schedules only run from the default branch (master, which lacks the trigger) and the checkout has no `ref: develop` — delivered automatic coverage today is zero. |
| off-review commits | partial | Centre-coordinate fixes (Obj23/Obj7B/HCZ fan/CPZ) are genuine, disasm-verified, with real discriminator tests. Ring touch-phase change is ROM-grounded. **But `44eb251d7` introduced a visual regression** (below). |
| Full suite | **green** | 7,282 run / 0 fail / 9 long-standing skips in isolated worktree; all updated guards pass; ROM-gated tests actually executed. (The suite cannot see the two highest residual risks: GLSL behavior and CI trigger semantics.) |

## New issues introduced by the merge

1. **[high] Parallax background broken at the default window scale** — `shader_parallax_bg.glsl:71` changed `floor((viewportX * 320.0) / ScreenWidth)` to `floor(viewportX)`, which is only correct at 1× scale. The default config (`DISPLAY_WINDOW_AUTOSIZE=true`, 2× baseline) runs at 2×, where physical-pixel `gameX` is subtracted from logical-pixel hScroll and sampled against the logical-resolution BG FBO — the parallax layer renders compressed ~2× and wraps in every level using the standard BG path. The new `TestVScrollColumnCount` *cements* the regression by string-asserting the old (correct-at-scale) expression is absent. Fix: add a `NativeWidth` uniform and compute `floor((viewportX * NativeWidth) / ScreenWidth)`.
2. **[high] Nightly develop trace CI is inert** — add `ref: develop` to the checkout and land the schedule trigger on master (or flip the default branch), else run via `gh workflow run ci.yml --ref develop` and treat the nightly as not yet operational. Docs currently overstate this as done.
3. **[high] Frontier log / changelog overstate `5b091cd67`** as "ROM-visible sidekick state" when it is another trace-fitted band; its new parity tests pin the heuristic threshold itself (0xE0), not ROM behavior — future principled replacement must rewrite them.
4. **[medium] `TestLevelManagerRewindSnapshot`'s stub now duplicates the OLD buggy capture logic** (no bumpEpoch), divergent from fixed production — 7 tests exercise a stale copy of the adapter.
5. **[medium] PhysicsFeatureSet positional-construction risk grew** (71→78, mid-list insertions, 6 indistinguishable ints) with no builder; the reflective hybrid test covers only the S2 base, and the old wrong fixture (`levelBoundaryUsesCentreY=false` + false comment) in `TestHybridPhysicsFeatureSet:100` was never corrected.
6. **[low]** New config quarantine inherits the transient-IO-rename flaw and has an overwrite gap when the quarantine move fails; ring `update()` 3-arg overload defaults to the retired double-collection path; CPZ water trigger models player-X (≈camera+0xA0) instead of the ROM camera-X condition and `0x508` vs ROM `$510`; Obj23/Obj7B proximity checks miss the Sidekick the ROM also tests.

## Remaining open from the original report

Not addressed anywhere in the merge: user-facing handling for bad-ROM/missing-ROM startup paths (§2.4 action, missing-ROM-with-title-disabled medium), SaveManager transient-IO quarantine, data-select double read, god-class extractions (§2.9), bonus-stage extra-life flags, S1 SBZ2 / MCZ boss parity gaps, audio/rewind unbounded history caps, `markAllDirty()` no-op, physics/util concrete-reference guard scope, EditorModeContext teardown discipline, GumballMachine static, gamepad support (or its documentation as a limitation).


---

# Addendum 2: Validation of follow-up commit `6835b89e2` (2026-06-10)

Single commit addressing the cross-validation findings. Validated by direct diff review plus a full-suite run in an isolated worktree.

## Verdict: all targeted fixes are genuine — and the release gate is honestly RED again

**The sidekick correction reopens §1.1/§2.7:** the uncited `0xE0` speed heuristic was removed rather than papered over. The frontier log now marks the prior "AIZ f14302 → GREEN" entry as *superseded* ("the speed-based branch ... was not disassembly-backed and the green-frontier claim was overstated") and records that `TestS3kAizTraceReplay` **fails at frame 3074** (`tails_g_speed expected=0x0000 actual=0x000C`), with the ROM-side evidence (`tailsCpu status=20`) showing a real `Status_Push` branch that still needs disassembly-backed modeling. The heuristic-pinning parity test was inverted to assert the honest post-removal behavior. CHANGELOG was rewritten to match. **0.6 remains blocked** until the push-bypass is modeled from ROM state (delayed `Stat_table`/`Ctrl_2`), this time with an accurate paper trail.

## Fixes confirmed genuine

| Issue (Addendum 1) | Fix | Status |
|---|---|---|
| Parallax shader broken at ≥2× (high) | New `ActiveDisplayWidth` uniform (logical `SCREEN_WIDTH_PIXELS`); `gameX = floor((viewportX * activeDisplayWidth) / ScreenWidth)`; column count from logical width; safe fallback when unset. Tests pin corrected mapping + renderer side. | **fixed** |
| Nightly develop trace CI inert (high) | `ref: develop` added to checkout; `TestBuildToolingGuard` now pins both the job and the ref. Residual: cron activates only once this `ci.yml` reaches master (default branch) — inherent to GitHub, documented. | **fixed (in-repo part)** |
| Frontier log overstates `5b091cd67` (high) | Entry superseded with explicit correction; honest RED status recorded; S2 EHZ1 bootstrap failure also logged as pre-existing. | **fixed** |
| Debug HUD default (medium, "clarify" was docs-only) | `DEBUG_VIEW_ENABLED` default now `false` in **both** code default and bundled `config.yaml`. | **fixed** |
| PhysicsFeatureSet positional hazard (medium) | `builderFrom()` with only the 5 donor fields overridable; single positional enumeration co-located with the record; **new source guard bans `new PhysicsFeatureSet(` outside the record across all of `src/`**; the 78-arg wrong fixture (incl. `levelBoundaryUsesCentreY=false`) deleted from `TestHybridPhysicsFeatureSet`. | **fixed** |
| `LevelManager.java:3082` hardcoded `Path.of("saves")` (medium) | Real `setEditorSaveManager` seam, wired on gameplay and editor rebind paths; null-guarded; reflection hack replaced. | **fixed** |
| §2.4 process death on bad ROM (medium) | Detection-empty **and** `IOException` branches route to `showStartupRomError` → rebuilds master title screen in its existing error-display state; no more throw through the fade callback. | **fixed** |

## Suite

`mvn test -Dmse=off` in isolated worktree at `6835b89e2`: **7,283 run / 4 failures / 0 errors / 9 long-standing skips**. All 4 failures are the known forkCount=4 zone-set session leak (`TestS3kCorkeyBadnik`, `TestS3kFlybot767Badnik` — both pass 5/5 in isolation; same root cause as the documented ICZ registry flake; neither class clears the session in setup). No regressions from the commit. All 8 directly-affected test classes pass.

## Still open (carried forward)

- **Release blocker:** AIZ trace RED at frame 3074 pending a ROM-grounded `Status_Push` bypass model; full S2+S3K sidekick trace sweep still not recorded as a baseline.
- The FAST_LEADER heuristic band block (§2.7 core) remains in place, uncited.
- Mediums from Addendum 1 untouched by this commit: `TestLevelManagerRewindSnapshot` masking test + stale stub copy, SaveManager transient-IO quarantine, config-test `createStandalone()` CWD isolation, Mgz `spawnChild` conversion, data-select launch error never rendered, §2.5 recovery state coherence, physics/util concrete-reference guard scope.
- Flake hygiene: add `SessionManager` clear to `TestS3kCorkeyBadnik` / `TestS3kFlybot767Badnik` (forkCount=4 zone-set leak).


---

# Addendum 3: Full review at develop @ `68198209d` (2026-06-10)

**Scope:** Release-readiness review at HEAD `68198209d` (one commit past Addendum 2): re-verification of every carried-forward open item, deep review of the new commit and the uncommitted working-tree slice, five fresh dimensions the original review covered thinly (graphics/shaders, audio, level editor, S3K slice-boundary coherence, trace infrastructure, build/packaging), and a full suite run in an isolated worktree. **Method:** 21 agents; every new critical/high finding adversarially verified by two independent verifiers (one refuter, one reproduction-path tracer). **All 4 new high findings confirmed, 0 refuted, 0 contested.**

## Release verdict

**0.6 remains blocked, and the new HEAD commit added a second trace-gate regression on top of the existing blocker.**

- The known blocker is unchanged: `TestS3kAizTraceReplay` RED at frame 3074 (`tails_g_speed 0x0000 vs 0x000C`, ROM `tailsCpu status=20` — `Status_Push` bypass still awaiting disassembly-backed modeling). The frontier log, CHANGELOG, and README are all honest about this; the gate machinery (release.yml trace-replay + coverage assertion, `TestBuildToolingGuard`, ci.yml `ref: develop` nightly) is intact and unweakened.
- **New (§A3.1):** `68198209d` structurally flipped the only three previously-green S2 trace gates (ehz1, scz, wfz) red and falsified the first-error frontier of 8 more red S2 traces to ~frame 0.
- Full default suite at HEAD: **7,283 run / 0 failures / 0 errors / 9 long-standing skips** (isolated worktree, ROMs present; totals verified by summing all 1,076 surefire XML reports). Even the 4 known forkCount=4 flakes passed this run — they remain flaky, not deterministic. The suite cannot see either trace-gate problem (trace tree excluded from the default profile).

## A3.1 New confirmed high findings

### 1. Unconditional per-frame `cpu_present` ERROR flips the previously-green S2 trace gates red and falsifies 8 red-trace frontiers *(introduced by `68198209d`; both verifiers: real, suggest medium since only the release profile sees it — but it directly deepens the release blocker)*
`TraceBinder.java:359-368` — `appendSidekickCpuComparisons` emits `compareFlag('…cpu_present', expected, actual)`, which is `Severity.ERROR` on any mismatch. `AbstractTraceReplayTest` now unconditionally captures the engine CPU view (non-null whenever a CPU Tails exists), while `trace.cpuStateForFrame()` is null for any fixture without `cpu_state` events — i.e. all 11 non-regenerated S2 sidekick fixtures. The three green S2 gates (`TestS2Ehz1TraceReplay`, `TestS2SczLevelSelectTraceReplay`, `TestS2WfzLevelSelectTraceReplay`) consume exactly those fixtures and now fail at the first comparison frame; the 8 already-red traces get their recorded first-error frame falsely rewritten to ~0 (masking e.g. cpz f3329). The commit ran no sweep over the green gates and logged no regression — violating the frontier-log policy for regressing traces.
**Action:** Only compare CPU state when the trace side advertises it (`expected != null` or `metadata.hasPerFrameCpuState()`); absence of an optional diagnostic stream in an older fixture must never be an ERROR. Re-run the three gates and record the episode in `TRACE_FRONTIER_LOG.md`.

### 2. Gameplay-driven layout mutations get permanently baked into editor save files *(verified end-to-end, both verifiers high confidence)*
`EditorSaveManager.java:98-126` persists everything in `MutableLevel.modified{Blocks,Chunks,MapCells}SinceBaseline` — but those BitSets record **all** mutations, not just editor strokes: when the active level is a `MutableLevel` (any editor playtest, and any normal session where a persisted edit file exists), `ZoneLayoutMutationPipeline` routes gameplay zone events / breakable terrain / AIZ intro terrain swap through `MutableLevelMutationSurface`, which sets the same since-baseline bits. Editor exit auto-saves unconditionally (`Engine.java:628-671`), so a single editor round trip after any layout event silently writes post-event terrain into `saves/<game>/edits/zone_X_act_Y.json`, which then re-applies as a permanent "edit" on every future load. Silent, persistent level corruption with no user-visible indication.
**Action:** Track editor-authored edits separately (mark mutations as edits only while `GameMode.EDITOR` is active / via editor commands), or diff against an editor-entry snapshot with gameplay mutations reverted. At minimum rebuild the level from ROM+persisted-edits on editor entry.

### 3. The shipped S3K route ends in a silent dead end at the CNZ2 egg capsule; implemented ICZ/LBZ1/MHZ content is unreachable in normal play
The working chain is AIZ→HCZ→MGZ→CNZ (all transitions verified). `CnzEndBossInstance` spawns `CnzEggCapsuleInstance` (`:141`), whose `update()` is an explicit no-op stub ("full button/open/results choreography remains deferred", `CnzEggCapsuleInstance.java:26-65`). No code anywhere requests ZONE_ICZ act 0 except the data-select table; there is no end-of-content handling anywhere in src/main. Every player finishing the shipped route reaches a capsule that never opens — while the already-built ICZ→LBZ handoff, LBZ1 miniboss, and the fully-built MHZ are unreachable. LBZ2 likewise dead-ends (no act-1 events/boss/exit), and MHZ's polished end-boss exit hard-transitions into a zero-object FBZ.
**Action:** Pick an explicit slice terminus: finish the CNZ2 capsule (closing the route through ICZ/LBZ1), or route the chosen last boss into a deliberate end-of-slice screen. Document the terminus in README/release notes either way (no document currently states it; CLAUDE.md's zone-handler list is also stale — LBZ/MHZ handlers exist).

### 4. Big-ring Hidden Palace routing crashes the app (`ZONE_HPZ` out of zone-registry range, unguarded fade callback)
`Sonic3kSSEntryRingObjectInstance.java:304-308` requests `requestZoneAndAct(ZONE_HPZ=0x16=22, 1)` when subtype bit 7 is set **or** the player holds 7 chaos + 7 super emeralds — earnable inside the slice (`Sonic3kSpecialStageManager.java:153,758-759` awards super emeralds from slice-zone big rings). `Sonic3kZoneRegistry` has exactly 22 entries (indices 0–21), so `levels.get(22)` throws `IndexOutOfBoundsException` inside `doZoneAct` (catches IOException only) inside `FadeManager`'s bare `callback.run()` — process death mid-fade on the completionist reward path. (The code's own comment also contradicts the constant: ROM HPZ is zone 0x17.) The clear-restart table offers the same destination (`S3kSaveProgressions.java:132`) — caught by the data-select guard, but permanently non-functional.
**Action:** Gate `shouldRouteToHiddenPalace()` until HPZ exists (fall through to the 50-ring path); add a zone-registry bounds check that fails soft; wrap the `doZoneAct`/`doNextAct`/`doNextZone` fade callbacks against `RuntimeException` the same way the data-select launch was fixed.

## A3.2 New medium findings

- **TraceBinder unit-mismatch comparisons** (same commit as §A3.1): `cpu_follow_ring` compares a ROM Pos_table *byte offset* to an engine *slot index* (0x28 vs 0x0A are the same slot) — the commit's own frontier-log baseline misdiagnoses this units bug as a prelude-seeding gap; `cpu_ctrl2_held/pressed` compare raw ROM button bits (A/B/C=0x70) against engine abstract input bits (INPUT_JUMP=0x10); the pre-existing bootstrap `player_history.pos` check has the same units bug (0x68 next-free byte offset == slot 0x19), now entrenched as the headline "release-blocking bootstrap drift". *(The uncommitted working-tree slice already fixes these three — verified against `s2.asm` `Sonic_RecordPos` — but not §A3.1.)* (`TraceBinder.java:384-501`)
- **Working-tree slice concerns** (uncommitted; review-before-land): a route-profile-gated `sprite.setAir(false)/setPushing(false)` force in production bootstrap code overrides the engine's own ground probe (`TraceReplaySessionBootstrap.java:483-493`); `AbstractTraceReplayTest:153` re-runs the full camera/level-event/ground-snap bootstrap a second time for *every* trace in the fleet; the binder semantic flips are test-coupled so partial landing leaves develop red — must land atomically, with the currently-missing CHANGELOG/frontier-log updates (and `docs/tmp-s2-sidekick-cpu-tasks.md` removed).
- **Virtual pattern ID collision:** S3K lightning-shield sparks and the surface-splash DPLC bank both claim `TRANSIENT_EFFECTS.base()` (0x48000) and overwrite each other's atlas texels — intra-range collision the enum governance can't catch, contradicting the splash code's own "never collides" comment.
- **SMPS interpreter has unbounded within-tick loops:** malformed/hostile ROM track data that jumps backward without yielding a duration hangs the game thread permanently; the analogous tick-count case is already capped for SFX (`maxTicks=2048`) but the within-tick case is not, and music has no cap at all. (`SmpsSequencer.java:951`)
- **Editor:** F5 fresh-start silently discards unsaved edits and the active stroke; persisted edits keep applying to gameplay (and in-engine trace sessions) even with `debug.flags.editor` disabled; `tryApplyEdits` silently drops invalid chunk/block entries yet reports APPLIED and marks the level saved.
- **MHZ end-boss completion hard-transitions into FBZ** — zero objects, no event handler, no boundary gate. (`MhzEndBossInstance.java:92`)
- **Release publish job has no version-bump gate:** a `workflow_dispatch` today would publish a permanent public release tagged `v0.6.prerelease` (pom still `0.6.prerelease`, CHANGELOG heading still "Current development snapshot"; only a tag-exists check). (`release.yml:396-424`)
- **Branch-policy CI gate (including the ROM-binary denylist) is fully bypassed for any PR into develop whose head branch is named `master`** — fork default branches qualify; no same-repo check. (`validate-policy.sh:529-539`)

## A3.3 New low findings (brief)

Regenerated arz/htz metadata advertise `cnz_slot_machine_state_per_frame` with no such events (guard has no stale-advertisement check); four shaders convert `gl_FragCoord` without subtracting the viewport offset (latent at integer-scale snapping; CNZ slot shader also hardcodes 320×224); `PatternAtlas` never reuses freed slots on non-last pages; `SoundTestApp` pumps `backend.update()` off-thread outside `streamLock`; `PcmHistoryRing` capacity int-overflow for `REWIND_AUDIO_HISTORY_SIZE_MB ≥ 4096` silently disables all audio; capture encoder worker dies silently on unchecked exceptions (no `abort()`, orphaned ffmpeg child); trace-session config overrides can be persisted to the user's `config.yaml` by an unrelated mid-session `saveConfig()`; `TraceCatalog` counts the CSV header as a frame (picker off-by-one); every level load deep-copies the entire level before checking whether an edit file exists; mouse stroke painting ignores the editor hierarchy depth/focus guards the keyboard path enforces; README-on-merge rule has no CI backstop on direct pushes to develop; maven-assembly-plugin 3.1.1 (2019) builds the shipping jar and the release job uses floating third-party action tags under `contents: write`; new `diagnosticGeneratedPressedInput` field not captured in `SidekickCpuRewindExtra`.

## A3.4 Carried-forward item status at HEAD

| Item | Status |
|---|---|
| AIZ release gate (f3074) / FAST_LEADER uncited bands / full sidekick sweep baseline | **open / open / partial** (S2 7-trace + EHZ1 + AIZ recorded; no post-correction S3K CNZ/MGZ or single sweep table) |
| Docs vs gate state; gate machinery integrity | fixed / fixed |
| Missing ROM with master title disabled | **fixed** (`showStartupRomError` covers detection-empty + IOException even with title disabled) |
| Data-select launch error message | open — `showLaunchError` stores the message; **no renderer reads it** (only a test does) |
| Failed-launch session coherence | partial — retry is safe, but the half-built `GameplayModeContext` is left bound, never closed |
| SaveManager transient-IO quarantine | open (and inherited by both new quarantine nets: config quarantines on any IOException incl. transient locks, with a confirmed overwrite-on-failed-quarantine vector via `DisplayColorProfileController.saveConfig`; editor apply quarantines on any exception) |
| Config-test CWD isolation (`createStandalone()`) | open — can still rewrite or even quarantine-rename the real repo `config.yaml` under forkCount=4 |
| macOS path hardening (master-title ROM check, saves dir) | open — bare `new File(romPath).exists()` + 6 CWD-relative `Path.of("saves")` sites |
| Rewind CoW production fix | fixed (no regression; `3d394d224` reviewed and correct) |
| `TestLevelManagerRewindSnapshot` masking test + stale stub | partial — a genuine production-flow CoW test now exists (`TestLevelRewindSnapshotAdapter`), but the old masking test and old-buggy-logic stub remain |
| `markAllDirty()` no-op / AudioCommandTimeline unbounded / InMemoryKeyframeStore unbounded / bonus-stage extra-life flags | all open, unchanged |
| Lifecycle ratchets (581/10, exact equality, min-scan) | **fixed** — independently recounted, no post-`cd19ef395` bumps |
| Concrete-Sonic guard scope | partial — sprites/ covered + missing-root fail-fast; physics/ and util/ unscanned (current exposure nil) |
| Map-mutation guard | partial — hardened at `a3a2930dc` (wider mutators, alias detection, scan-root asserts); still blind to cross-file indirection and `getData()` writes; 3 stale allowlist entries |
| Mgz `spawnChild` conversion / dead standardization scan roots / Corkey-Flybot session clear / GumballMachine static / EditorModeContext destroy discipline | all open, unchanged |
| God-class budgets | open — budgets unchanged (4905/4085/3844); GameLoop grew +5 lines, others at exactly budget |

## A3.5 What's verifiably healthy (new positive assurance)

- **Comparison-only invariant holds everywhere in committed code** — `68198209d`'s plumbing is read-only (diagnostic accessors consumed only by trace formatter/binder); legacy hydration entrypoints are hard no-ops; scanner guard + strict-tolerance pin run in the default suite. Fixture regeneration was clean (identical frame counts).
- **Graphics:** the ActiveDisplayWidth parallax fix is correct and test-pinned; physical→logical conversion correct in all three core scene shaders and the priority-FBO path; `PatternAtlasRange` enum ranges genuinely non-overlapping and runtime-enforced; no GL leaks on act-transition or editor-toggle; FBO owners destroy/recreate symmetrically.
- **Audio:** YM2612/PSG cores are faithful GPGX/libvgm ports with no stubs; deprecated `PsgChip` confirmed unreachable; loaders fail safe on malformed ROM bytes; rewind FIFO/PCM rings bounds-correct; OpenAL teardown hardened since the prior review.
- **Editor:** `14562b228` validation is sound and well tested; save pipeline atomic, hashed, quarantine-protected, traversal-free; teardown+rebuild round trip preserves the edited level.
- **Trace comparator:** exact-by-default tolerances; ring mismatches forced to error; warnings release-blocking; bootstrap frame-0 divergences counted. Fixture weight 152.7 MB (+1.8 MB over 10 commits).
- **Packaging:** the red AIZ trace genuinely blocks any master release PR/push/dispatch; dependencies CVE-clean; shipped jar contains only legitimate original assets; shipped config defaults safe; develop CI min-test-count assertion in place.
- **Suite:** 7,283 / 0 / 0 / 9 at HEAD in an isolated worktree.

## A3.6 Recommended pre-release order of work

1. **Fix the `cpu_present` comparator regression** (§A3.1) and land the working-tree units fixes atomically with their tests + frontier-log entry — this restores the three green S2 gates and un-falsifies 8 frontiers, and is a precondition for trusting any further frontier data.
2. **Fix the AIZ f3074 `Status_Push` bypass from ROM state** (delayed `Stat_table`/`Ctrl_2`) — the actual release gate; cite or replace the FAST_LEADER bands as part of it; then record the full S2+S3K sidekick sweep baseline.
3. **Gate the HPZ big-ring route + bounds-check zone loads + guard the zone-act fade callbacks** (§A3.1#4) — crash on the completionist path, small fix.
4. **Decide and implement the slice terminus** (§A3.1#3) — finish the CNZ2 capsule→ICZ handoff (unlocking already-built ICZ/LBZ1 content) or end deliberately at CNZ2 with a proper screen; document it.
5. **Separate editor edits from gameplay mutations** (§A3.1#2) — silent persistent corruption affecting anyone using the editor.
6. **Add a version-bump gate to the release publish job** and a same-repo check (or head-branch rename) for the master-branch policy bypass.
7. **Cap SMPS within-tick loops** (hostile-ROM hang; the SFX-side precedent already exists).
8. Carried-forward mediums in priority order: data-select error rendering + half-built session close; SaveManager/config transient-IO quarantine; config-test CWD isolation; Corkey/Flybot session clear; pattern-ID 0x48000 collision.

---

*Addendum 3 generated by a 21-agent review workflow (4 carry-forward verifiers, 8 dimension reviewers, 1 isolated-worktree suite run; 2 independent adversarial verifiers per critical/high finding — 4 confirmed, 0 refuted). Line numbers reference develop @ `68198209d`.*


---

# Addendum 4: CNZ2 route correction (2026-06-10)

The Addendum 3 finding "The shipped S3K route ends in a silent dead end at the CNZ2 egg capsule" is **superseded**. Current develop now has the intended CNZ2 post-capsule launcher route:

- `CnzEggCapsuleInstance` reuses the shared upright capsule button/open/results path and reports results completion back to `CnzEndBossInstance`.
- `CnzEndBossInstance` spawns `Obj_CNZCannon`, forces the cannon launch after the ROM wait, and requests `ZONE_ICZ` act 0 only after the camera/Y offscreen threshold.
- The frozen CNZ2 -> ICZ1 transition neutralizes and hides playable sprites so cannon velocity/visible launcher state does not leak into the ICZ load.

Verification on this worktree:

`mvn "-Dtest=com.openggf.tests.TestS3kCnzTeleporterRouteHeadless" test` passed with exit code 0. The focused test covers capsule completion, cannon spawn/capture, forced launch, `ZONE_ICZ` act 0 request, frozen-transition neutralization, and post-load velocity reset. The Maven Silent Extension output still lists cached broader release failures, but they are unrelated to this focused CNZ route gate.

Open release blockers remain AIZ sidekick trace parity and the non-CNZ high findings; CNZ2 should no longer be tracked as a dead-end slice terminus.

> **Addendum 5 correction:** this supersession was wrong. The CNZ2 capsule/cannon choreography exists, but it is reachable only through `forceDefeatForTest()` / `forceResultsCompleteForTest()` — the production CNZ end boss has no defeat path, so the route still dead-ends at CNZ2 in normal play. See §A5.2.


---

# Addendum 5: Full review at develop @ `6b89814fe` (2026-06-10)

**Scope:** Release-readiness review at HEAD `6b89814fe` (five commits past the Addendum 3 baseline `68198209d`): re-verification of all carried-forward open items, deep review of the new commits and the uncommitted AIZ frame-4679 working-tree slice, five fresh dimensions (sidekick CPU stack, camera/scroll parity, rewind subsystem, level-loading robustness, shipped-route coherence) plus performance and release-docs sweeps, and a suite + release-gate run in an isolated worktree. **Method:** 31 agents (1 suite runner, 4 carry-forward verifiers, 9 dimension reviewers, 2 independent adversarial verifiers per new critical/high finding, 1 completeness critic). All 8 new critical/high findings were confirmed by both verifiers (0 refuted, 0 contested); one inter-agent contradiction and one false suite-agent claim were caught by the critic and adjudicated below. **Release scope note:** MHZ+ is explicitly next-release scope and is not treated as a blocker anywhere below.

## Release verdict

**0.6 is not shippable from HEAD today, and the decision baseline is provisional: the AIZ blocker fix is mid-flight in the working tree.**

1. **The S3K AIZ release trace gate is RED at HEAD**: `TestS3kAizTraceReplay` fails 4 of 16 tests; `replayMatchesTrace` reports 1,747 errors, first at **frame 2679 `tails_cpu_respawn_counter expected=0x0031 actual=0x0000`** (the frontier moved *earlier* than Addendum 3's f3074 because `6b89814fe` exposed the flight auto-recovery timer as a newly compared field; the f3074 `Status_Push` bypass is still expected behind it). The uncommitted working-tree slice claims f4679. The frontier log honestly records the committed state — the suite agent's claim that the log was stale at f8941 was **refuted** by the critic (the f8941 hits are older historical entries) and is excluded from this report.
2. **Develop push CI is red right now** — the last 4 push runs of `ci.yml` failed (runs 27273839802–27281967558) on two guard tests that also fail in our isolated worktree run (§A5.1).
3. **The shipped route silently dead-ends at the CNZ2 end boss in normal play** (§A5.2) — Addendum 4's "superseded" verdict is reversed — and even once that is fixed, the LBZ1→LBZ2 act handoff behind it is a no-op (§A5.3).
4. Suite otherwise healthy: **7,297–7,299 run / 2 failures / 0 errors / 9 long-standing skips** in an isolated worktree (both failures are the §A5.1 guard reds; zero environmental noise this run); **all three S2 trace gates (EHZ1/SCZ/WFZ) PASS at HEAD**. Reminder: the default suite excludes `**/tests/trace/**` (pom.xml:369), so "suite green" never covers the release gates.

## A5.1 Critical: develop CI is red — two committed guard violations at HEAD

`gh run list` shows 4 consecutive failed push runs of `ci.yml` on develop; `--log-failed` confirms the cause is the same two failures reproduced in the isolated worktree:

- **`TestTraceReplayInvariantGuard#traceParserDataAndCatalogStayIndependentOfEngineRuntime`** — `TraceBinder.java:4` imports `com.openggf.sprites.playable.AbstractPlayableSprite` (introduced by `6bba2372f`'s ctrl2 normalization reading `INPUT_*` constants). This erodes the enforcement layer of the comparison-only invariant itself. Fix shape: lift the input-bit constants into a neutral package (keeping ROM citations) rather than rebaselining the guard.
- **`TestRewindFieldAudit#objectSubclassesDoNotStoreRunnableContinuations`** — `CnzEggCapsuleInstance.java:18` declares `private final Runnable resultsCompleteCallback;` (non-rewindable continuation on the shipped CNZ route). Replace with the rewind-safe completion pattern used elsewhere.

Neither commit author noticed because the develop push job was already red. **Action:** fix both before anything else lands; do not grow guard baselines.

## A5.2 Critical (adjudicated): the CNZ2 end boss has no production defeat path — the shipped route still dead-ends at CNZ2; Addendum 4 reversed

`CnzEndBossInstance.java:82-111` · dimension: delta-commits · both verifiers high confidence · independently re-verified by the completeness critic

The entire post-defeat sequence landed by `9caa923d2`/`6b89814fe` (capsule → results → cannon spawn → forced launch → `ZONE_ICZ` request) is gated on `defeatRequestedForTest`, whose **sole writer is `forceDefeatForTest()`, called only from `TestS3kCnzTeleporterRouteHeadless`**. The class Javadoc itself confirms no attack/damage state machine exists, and `Sonic3kCNZEvents` contains no ICZ request. The boss spawns on the shipped route (object 0xA7 via the S3KL registry) and then does nothing. Addendum 4's supersession — and the route-coherence agent's "all ten boundaries have completing bosses" claim — were both based on the headless test, which drives the test seams. The CHANGELOG entry for `9caa923d2` ("continues through the ROM cannon-launch handoff into ICZ1 instead of silently ending") overstates reality, as does `RELEASE_NOTES_v0.6.prerelease.md`.

**Action:** implement the ROM hit-count defeat choreography for `Obj_CNZEndBoss` (sonic3k.asm ~145990+) — the downstream choreography is already built and ROM-cited — or explicitly document CNZ2 as the 0.6 terminus with a deliberate ending. Correct CHANGELOG/release notes either way.

## A5.3 New confirmed high findings

### 1. LBZ1 post-miniboss act handoff is a no-op: "Act 2" title card + music, then the player is stranded in the act-1 layout
`S3kResultsScreenObjectInstance.java:525` · route-coherence · both verifiers high

Once A5.2 is fixed, the route runs AIZ1→…→ICZ2→LBZ1 (the ICZ2 snow-pile launch works). LBZ1's miniboss signpost/results flow takes the generic non-seamless act-1 exit — `hasSeamlessTransition = (act == 0) && (zone == 0x01 || zone == 0x02)` covers HCZ/MGZ only — so it sets `Apparent_act=2`, plays LBZ2 music and an Act-2 title card, resets level gamestate, **and never loads LBZ act 1** (no code in src/main requests it). The hardcoded zone-id literal list is also the structural cause: every newly completed zone will silently misroute the same way (flagged separately as a low — convert to a zone-provider predicate). Mitigation: the dead end cannot corrupt saves (currentAct stays 0; restart lands in implemented LBZ1).

**Action:** implement the LBZ1→LBZ2 handoff (or make LBZ1's miniboss the documented terminus and suppress the misleading Act-2 presentation). Replace the zone-literal list with a provider-owned predicate.

### 2. `SidekickCpuController.reset()` never clears `bootstrapPreludePlacementApplied` — every level (re)load after the first in a live session skips the leader Pos_table reset and clobbers zone-event air state
`SidekickCpuController.java:4356-4394 (reset()), 961-967, 1049, 1060-1076` · sidekick-stack · both verifiers high

The flag is set at the end of both placement paths (including the legacy non-bootstrap branch every live load takes) and survives `reset()`. On death respawn or any zone/act transition, the next INIT tick takes `applyLevelStartSidekickPlacementSkipPrefill`, which neither resets/prefills the leader position history (ROM refills `Pos_table` on every player init) nor preserves zone-event falling-intro air state — up to 16 frames of stale previous-level leader positions feed the delayed-follow ring on the shipped route. Trace replays can't see this because each segment bootstraps fresh sprites; `TestSidekickCpuControllerLevelStart` covers only the first load.

**Action:** clear the flag in `reset()`; make the skip-prefill path preserve air state and use the captured anchor; add a reset-then-second-INIT regression test.

### 3. Camera never models ROM `Camera_max_Y_pos_changing` — boss-arena boundary easing stalls while the player stands still
`Camera.java:295` · camera-scroll · both verifiers high

ROM `ScrollVerti` routes the no-scroll case through a forced boundary clamp while easing (s2.asm:18209-18213; sonic3k.asm:38462-38470). The engine sets the equivalent flag in `updateBoundaryEasing` (Camera.java:496) but `updatePosition` never reads it (`isMaxYChanging()` has zero production callers): with the player grounded at bias or airborne inside the ±0x20 window, the clamp at :295 is skipped and a maxY descent never progresses. Affects every boss-arena boundary ease on the shipped route when the player is stationary.

**Action:** in the vertical no-scroll cases, when the flag is set run the scroll-down clamp with zero delta and clear it (mirroring s2.asm `loc_D80E` / sonic3k.asm `loc_1C1C2`); add a headless stationary-player easing test.

### 4. Rewind: `RewindFieldPolicy.DEFERRED` has no restore machinery — seeking into the HCZ end-boss camera-lock window re-runs `onCameraLockComplete` and double-spawns boss children
`DefaultObjectRewindPolicies.java:129` · rewind-subsystem · both verifiers high

DEFERRED is consumed nowhere in any restore path — it only excludes fields from capture. A rewind seek recreates the boss with a fresh `S3kSharedBossCameraGate` (lockBounds null), which instantly reports complete and re-runs `onCameraLockComplete` (duplicate ship/turbine/blade spawn, skipped boss-music callback). Bounded by live rewind defaulting off (`rewind.liveEnabled=false`), but the feature is a documented selling point.

### 5. Rewind: S3K boss/miniboss children are silently dropped on restore — only CNZ miniboss children have dynamic-object recreate codecs
`Sonic3kObjectRegistry.java:67-83`, `ObjectManager.java:3236-3241` · rewind-subsystem · both verifiers high

`recreateDynamicObject(...).orElse(null)` drops every uncodec'd dynamic object with no log. Seeking to a frame after the HCZ lock window permanently deletes the boss's ship/turbine/blade hazards mid-fight. Same live-rewind-off mitigation as above. **Action:** add codecs (or a parent-driven respawn-on-missing-children pass) for route bosses' children, and log dropped entries.

### 6. Startup ROM error covers detection only — a recognized-but-corrupt/truncated ROM still kills the process raw; S3K `createGame` swallows the descriptive IOException into a null that NPEs
`Engine.java:452-464`, `Sonic3kGameModule.java:125-132` · level-loading · both verifiers high

Detection is header-name-string only, so a truncated/body-corrupt ROM (the most plausible bad input) passes detection; everything after detection (`openGameplaySession` → `initializeGameplayRuntime` → `enterConfiguredStartupMode`) is outside the `showStartupRomError` net. Worse, `Sonic3kGameModule.createGame` catches the descriptive "S3K address table validation failed" IOException and returns null, which NPEs later in `LevelManager.initAudio`. Related (medium): the mid-route `doNextAct/doNextZone/doZoneAct` fade callbacks still rethrow IOException as unchecked through bare `FadeManager` callbacks — including a previously unrecorded sibling on the new CNZ→ICZ seamless-transition path — the Addendum 3 hardening never landed.

**Action:** wrap the post-detection startup chain (IOException **and** RuntimeException) into `showStartupRomError`; delete the catch-and-return-null; wrap the zone-transition fade callbacks.

### 7. Rewind: HCZ2 wall-chase scroll-handler phase is outside the rewind snapshot universe — rewinding across the chase end corrupts BG collision coordinates
`SwScrlHcz.java:68` · camera-scroll · both verifiers high

`ParallaxSnapshot` is empty on the premise that handler state is derivable, but `hcz2Phase`/`wallChaseOffsetX`/`lastBgCameraX` are real state: rewinding from after `act2BgTransition` back into `act2BgRoutine=BG_WALL_MOVE` leaves the handler in NORMAL while `backgroundCollisionFlag` is restored true — `GroundSensor` then probes wrong BG coordinates. Broader medium: module-lifetime scroll handlers leak phase/shake/accumulator state across respawns, sessions, and editor round trips (only ICZ implements init).

## A5.4 Working-tree slice (uncommitted AIZ f4679 work) — pre-landing review

The slice is **disciplined and ROM-grounded**: every behavioral branch checked is driven by semantic ROM state (frame-start `Status_Push` capture, delayed `Stat_table` bits, provider predicates); no trace hydration; headline citations verify against skdisasm (Kill_Character 21136-21151, sub_13ECA 26800-26808, Tails_Display blink 26278-26282) and s2.asm cross-game consistency; the two new behavioral fields are correctly captured in `PlayerRewindExtra`. Before landing:

- **Dead machinery:** the airborne-push-handoff removal leaves `suppressNextAirbornePushFollowSteering` only ever assigned false yet still wired through conditions, diagnostics, and the rewind snapshot — delete it.
- **Docs obligations missing:** CHANGELOG and TRACE_FRONTIER_LOG are unstaged for a frontier move 2679→4679; `docs/tmp-0.6-release-tasks.md` should be removed at landing.
- **Must land atomically** (record/constructor/formatter compile-coupled); do **not** stage the three EOL-only-dirty files (`Camera.java`, `LevelManager.java`, `SkidDustObjectInstance.java`).
- The blink/invulnerability changes touch shared S1/S2 paths — **re-run the three green S2 gates before landing.**
- Minor: `wh=%02X/%02X` diagnostic prints width twice; new ctrl2 diagnostic latches not in `SidekickCpuRewindExtra`; one uncited routine-2 latch comment; `jumpPressHistory` ring now recorded but unconsumed.

## A5.5 Selected new medium findings

- **Delta-commit concerns (delta-commits dimension):** `CnzEndBossInstance` restarts CNZ2 music + re-releases players *every frame* during the post-capsule walk window (ROM calls `Restore_LevelMusic` once); `9d3065466`'s WFZ tornado timer flipped ROM-exact `>= 0` to `> 0`, contradicting the `bmi` it cites, hidden by a prelude-only +1 compensation; the same commit hand-feeds uncited ROM-sampled Y-waveform tables into the SCZ/WFZ prelude history (the restored green gates partly pin the transcription); the bootstrap object-slot "present/missing" warning was deleted unconditionally rather than visibility-gated; S3K `getDiagnosticInteractId()` pinned to constant 0 is a false-error landmine for the per-frame `tails_cpu_interact` comparison on the active blocker trace.
- **Sidekick (§2.7 status: grew again):** the direct ROM `loc_13DD0` push bypass is still restricted by an uncited `|dy|<0x80` band ROM does not have, plus `smallDxDelayedInputNudge`; transient released-carry cooldown double-decrements vs ROM `loc_14534` (CNZ1 solo-carry regrab window halved); ROM-faithful mid-run zone-entry preservation is gated on a trace-bootstrap-only flag, so live play takes the destructive spawn reset the code itself documents as non-ROM.
- **Rewind:** S3K invincibility-stars pending-restore entry queued under a base type the S3K class doesn't extend (captured state silently discarded); `DefaultPowerUpSpawner` restores with a no-identity-table context (latent crash for CAPTURED player refs); the field audit is static-only (override-declaring classes wholly exempt, final fields skipped, DEFERRED counts as covered) and dynamic validation runs only S2 EHZ1 — none of the §A5.3#4/#5 corruption is CI-visible.
- **Camera/scroll:** ICZ2 indoor/outdoor hysteresis inverts the ROM keep-indoor cases outside the `[$1000,$3600)` band; Tails rolling-camera compensation silently ships the disasm `fixBugs` variant via an `instanceof Tails` branch in shared camera code, undocumented.
- **Route:** ICZ1 snowboard intro gated to `SONIC_ALONE`; ROM plays it for `Player_mode < 2` (i.e. also the default Sonic+Tails) and has a Tails variant — undocumented divergence on the shipped route.
- **Performance (GPU-upload shaped, both on the shipped route):** every player/sidekick DPLC frame change re-uploads a full 1MB atlas page instead of ~1-3KB of changed tiles (`DynamicPatternBank.java:55-78`); wrapped-BG zones rebuild + re-upload the whole BG tilemap window every 16px of BG scroll (`LevelManager.java:1839-1846`). Lower-tier: per-frame `NumberFormatException` control flow in GameLoop key-binding lookups (SpriteManager already models the resolve-once fix); two more unbounded opt-in rewind structures (live-rewind input tape, audio keyframe TreeMap).
- **Release docs:** `RELEASE_NOTES_v0.6.prerelease.md:27-28` and `docs/release-architecture-review-issues.md` REL-043 both still claim the AIZ gate was resolved/green — stale since the gate honestly re-reddened; the HPZ 50-ring fallback landed as an intentional ROM divergence with `S3K-Known-Discrepancies: n/a` and is recorded in no discrepancy doc; the consciously-shipped gaps from the original review (bonus-stage extra-life flags, S2 MCZ `boss_hurt_sonic`, keyboard-only input) are still recorded nowhere; README's S3K support table materially understates the route ("AIZ substantially playable"); CLAUDE.md zone-handler list still omits LBZ/MHZ. **Correction to the original report:** S1 SBZ2 is in fact fully implemented at HEAD — the prior finding rested on a stale TODO Javadoc (`Sonic1SBZEvents.java:21-23`, delete it).

## A5.6 Carried-forward status at HEAD (consolidated)

| Item | Status |
|---|---|
| cpu_present comparator regression (§A3.1#1) + three TraceBinder units bugs | **fixed** (principled expected-side gating; converters cited; gates re-run green; episode honestly logged) — but the fix introduced the §A5.1 guard violation |
| Three S2 trace gates (EHZ1/SCZ/WFZ) | **green at HEAD** (verified by suite run) |
| Feared `setAir(false)/setPushing(false)` bootstrap force | **superseded** — never landed; replacement is metadata+routine-state gated (two uncited Y-offset tables remain, see A5.5) |
| HPZ big-ring crash | **partial** — trigger gated fail-soft (hardcoded `hiddenPalaceRouteAvailable()=false`); no zone-bounds soft-fail, fade callbacks still unwrapped, divergence undocumented |
| Frontier-log policy for the 5 new commits | **partial** — 4/5 compliant; `9caa923d2` has no entry (defensible); `6b89814fe` entry omits total error count |
| Full S2+S3K sidekick sweep baseline | **open** — never recorded; the 68198209d 7-trace baseline numbers are stale post-units-fix |
| Data-select launch error rendering / half-built context close | **open / partial** (recovery restores mode but never closes the half-built `GameplayModeContext`) |
| SaveManager transient-IO quarantine (+config inheritance) | **open** (only .corrupt-name collision fixed) |
| Config-test CWD isolation | **partial** — config package isolated; ≥8 other classes still construct against shared CWD |
| macOS path hardening / `Path.of("saves")` | **open** — now 7 sites (was 6; `Engine.java:105` is new) |
| Release publish version-bump gate | **open** — `workflow_dispatch` would publish `v0.6.prerelease` |
| `master`-named head-branch CI policy bypass (incl. ROM denylist) | **open** (both .sh and .ps1) |
| Nightly develop trace CI | **open / non-operational** — cron only fires from default branch (master, 3,739 commits behind, no schedule trigger there); zero scheduled runs have ever fired (`gh run list --event schedule` is empty) |
| Editor save baking of gameplay mutations | **open** — no provenance separation; unconditional auto-save on editor exit unchanged |
| Edits apply with `debug.flags.editor` disabled / F5 silent discard / mouse-stroke guard bypass | **all open** |
| `tryApplyEdits` silent drops | **partial** — null states + invalid map blockIndex now quarantine; out-of-range chunk/block indices still silently dropped with APPLIED |
| Rewind masking test + stale stub | **open** (stub comment falsely claims "copied from LevelManager") |
| GumballMachine static / EditorModeContext destroy / Corkey+Flybot session clear / `markAllDirty()` no-op / AudioCommandTimeline / InMemoryKeyframeStore / bonus-stage extra-life flags / pattern-ID 0x48000 collision / SMPS within-tick cap / Mgz `spawnChild` / dead scan roots / map-mutation guard indirection / data-select double read | **all open, unchanged** |
| S1 SBZ2 boss | **fixed** (was already implemented; stale TODO misled the original review) — S2 MCZ `boss_hurt_sonic` still open |
| God-class budgets | **open** — AbstractPlayableSprite 4,905 / LevelManager 4,085 / GameLoop 3,843 vs budgets 4,905/4,085/3,844: two at exactly budget, zero headroom |
| MHZ end-boss → FBZ hard transition | **open but out of shipped route** — reachable only via continuous play past LBZ; still undocumented |

## A5.7 What's verifiably healthy (new positive assurance)

- **Sidekick cross-game gating is clean:** every S3K/S2 divergence rides PhysicsFeatureSet accessors or provider predicates; §2.10 FQN constants fully gone; the S2 despawn/interact model is ROM-cited end-to-end; `hydrateFromRomCpuState` remains test-only; `SidekickCpuRewindExtra` captures all 52 gameplay-relevant fields; Tails/Knuckles contain zero sidekick logic (fully centralized).
- **Camera core is ROM-verified:** per-game fast-scroll caps, Fast_V_scroll_flag as a frame-scoped semantic request, look-delay gating, wrap handling, deadzone geometry; HCZ1 deform verified line-by-line against sonic3k.asm:105796-105980; CameraSnapshot rewind coverage broad and coherent; no zone-id carve-outs anywhere in this dimension.
- **Decompression/parsing hardened:** Kosinski/KosM and Nemesis output-capped with guaranteed loop progress; PlcParser bounds-checked both overloads; S3K layout/collision/placement parsing bounds-safe; overlay composition CoW-correct by construction; mid-route PLC application fails soft.
- **Trace/commit discipline:** all five commits carry complete trailers, accurate CHANGELOG entries, and (where trace-affecting) honest frontier-log entries including the regression episode; comparison-only invariant holds in all committed production code; CONFIGURATION.md matches the config catalog exactly; bundled config defaults all safe (debug HUD off held).
- **Performance steady state is good:** renderers allocation-clean with persistent buffers/cached uniforms; no glGet/glFinish stalls in the loop; rewind capture keyframe-gated; ObjectManager loops cache-backed; PatternAtlas hot path autoboxing-free.
- **Route data-select safety:** earnable destinations are capped at implemented content; the LBZ1 dead end cannot corrupt save destinations; bonus-stage entries are closed-loop.

## A5.8 Completeness-critic gaps (what no agent has verified yet)

1. **No executable 0.6 gate definition + most shipped-route trace gates never executed at HEAD:** the HCZ/CNZ/MGZ/ICZ/LBZ dedicated and complete-run trace classes exist on disk with no verified HEAD status. Write down the gate list; run the full sweep once at the RC commit.
2. **Zero runtime verification anywhere in Addenda 3–5:** no `mvn package`, no jar boot, no live playthrough of the route, no 60fps measurement. The repo's own `s3k-zone-validate`/`trace-capture` tooling went unused. Do a packaged-jar smoke of the route at the RC commit.
3. **S1 (and broad S2) playable status never assessed since Addendum 3** — and the working-tree slice touches shared S1/S2 blink/invulnerability paths.
4. **Save/load round-trip on the new ICZ/LBZ segments never exercised** (zone labels, restart rows, relaunch into the new zone ids).
5. **Release publish dry-run never executed** (version bump policy, artifact content, CHANGELOG heading gate).
6. **RC-commit policy undeclared:** every verdict above is pinned to `6b89814fe` and expires when the AIZ slice lands.

## A5.9 Recommended pre-release order of work

1. **Fix the two CI-red guard violations** (TraceBinder sprites import → lift INPUT_* constants to a neutral package; CnzEggCapsule Runnable → rewind-safe completion pattern). Develop CI must be green before anything else lands. (§A5.1)
2. **Land the AIZ working-tree slice** (after the §A5.4 pre-landing items: dead flag removal, CHANGELOG + frontier-log entries, atomic staging excluding the three EOL-dirty files, S2 gate re-run) and continue the f4679 frontier to green — the actual release gate.
3. **Implement the production CNZ2 end-boss defeat path** (ROM hit-count choreography; downstream is already built) or document CNZ2 as the deliberate terminus. Correct CHANGELOG/RELEASE_NOTES/REL-043 either way. (§A5.2)
4. **Decide the terminus**: if the route continues past CNZ2, fix the LBZ1→LBZ2 handoff (provider predicate, not a longer zone-literal list). Document the terminus + MHZ+ exclusion in README/release notes. (§A5.3#1)
5. **Fix `reset()` clearing `bootstrapPreludePlacementApplied`** — live-session correctness on every respawn/transition on the route. (§A5.3#2)
6. **Camera maxY-changing clamp** — small, ROM-cited, affects every boss arena. (§A5.3#3)
7. **Startup robustness:** wrap the post-detection chain + delete the S3K createGame null-swallow + wrap zone-transition fade callbacks. (§A5.3#6)
8. **Define and run the full 0.6 gate sweep + packaged-jar route smoke at the RC commit**; record in the frontier log and release notes. (§A5.8#1-2)
9. Ship-with-notes items to *record* (not necessarily fix) before tagging: HPZ fallback divergence, snowboard-intro gating, Tails fixBugs camera, rewind DEFERRED/children gaps (live rewind off by default), editor save baking (editor off by default), SaveManager quarantine, keyboard-only input.
10. Post-release: rewind restore machinery for DEFERRED + boss-child codecs, DPLC/BG upload optimizations, the carried god-class extractions, and the rest of the A5.6 open table.

---

*Addendum 5 generated by a 31-agent review workflow (1 isolated-worktree suite run, 4 carry-forward verifiers, 9 dimension reviewers, 2 independent adversarial verifiers per new critical/high finding — 8 confirmed, 0 refuted — and 1 completeness critic; ~1,200 tool invocations). One route-coherence claim and one suite-agent claim were refuted during cross-validation and corrected above. Line numbers reference develop @ `6b89814fe` (committed state; dirty files verified via `git show HEAD:`).*

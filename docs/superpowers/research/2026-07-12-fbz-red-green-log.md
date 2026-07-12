# FBZ RED/GREEN evidence log

## Task 1 — evidence and completeness gates

- Requirement: FBZ-INV-001 / FBZ-REG-001
- RED command: `mvn "-Dtest=TestFbzObjectInventory,TestFbzObjectRegistryCompleteness" test "-Ds3k.rom.path=s3k.gen" -Dmse=off`
- RED result (2026-07-12): 3 tests, 1 failure. The binary inventory test passed; `fbzProfileAllowlistMatchesTheCheckedConcreteFactoryInventory` failed because FBZ returned the broad S3KL set instead of the frozen 15-ID concrete set. The failure explicitly showed expected `[01,02,07,08,0F,26,28,2A,2F,33,34,6A,6B,80,85]` versus the broad S3KL allowlist.
- GREEN command: `mvn "-Dtest=TestFbzObjectInventory,TestFbzObjectRegistryCompleteness" test "-Ds3k.rom.path=s3k.gen"`
- GREEN result (2026-07-12): exit 0; fresh Surefire XML reports 3 tests, 0 failures, 0 errors, 0 skipped. MSE's session aggregate printed `passed=54 failed=0 errors=0 skipped=0`; the authoritative selected-class XML is 1 + 2 tests, and the registry classification loop exercises all 860 placement records.
- Test commit: pending conductor commit.
- Implementation commit: pending conductor commit.
- Reviewer verdict: pending delegated review.

No trace capture or replay was executed. Trace baseline values come only from
persisted `docs/TRACE_FRONTIER_LOG.md` evidence; missing measurements are
recorded as `unknown/not previously run` rather than inferred.

### Task 1 specification-review correction

- Review RED: persisted S3K sibling/MHZ trace measurements had incorrectly been
  marked unknown; checkpoint entries lacked executable state recipes; the
  inventory lacked the mandatory per-family/allocation contract and two badnik
  label-provenance rows; the terminator assertion checked only two bytes.
- Correction: froze the exact persisted HCZ/MGZ/CNZ/ICZ/LBZ tuples from the
  2026-07-02 sibling check and MHZ tuple from its immediately preceding entry,
  retaining null warning counts because warnings were not persisted. Added a
  source-cited setup recipe for every immutable checkpoint, including prior
  event state, both boundary approach directions, phase/timer state, and capture
  predicate/count. Added the one-row-per-placed-family contract, dynamic
  allocation policy, missing later-task label provenance, and full six-byte
  terminator assertion.
- Correction verification command: `mvn "-Dtest=TestFbzObjectInventory,TestFbzObjectRegistryCompleteness" test "-Ds3k.rom.path=s3k.gen"`
- Correction GREEN result (2026-07-12): focused command exited 0; fresh
  selected-class Surefire XML reports 3 tests, 0 failures, 0 errors, 0 skipped
  (MSE session aggregate: `passed=59 failed=0 errors=0 skipped=0`).

### Task 1 factual-review correction

- Review RED: several address-manifest entries used noun shorthand instead of
  exact mapping labels; Spring Plunger was incorrectly described as allocating
  children despite being five placed-only `$D0` objects; the capsule checkpoint
  cited `AfterBoss_FBZ`, which is an `rts`, rather than the transition routine.
- Correction: expanded every affected row to the exact S&K mapping label and
  RomOffsetFinder/disassembly address; documented `Obj_FBZSpringPlunger`'s
  allocation-free init/rider/`Sprite_CheckDelete` path at
  `sonic3k.asm:187094-187119`; and cited `loc_7092A`/`loc_70938` at
  `sonic3k.asm:148959-148968` for the `$720` camera gate and
  `StartNewLevel #$0800`.
- Verification (2026-07-12): focused Maven command exited 0; fresh selected
  Surefire XML remains 3 tests, 0 failures, 0 errors, 0 skipped (MSE aggregate
  `passed=59 failed=0 errors=0 skipped=0`). JSON and diff validation passed.

### Task 1 quality-review correction

- Review RED: AIZ complete-run was incorrectly listed green despite repeated
  persisted expected-red evidence at f1095 / 4319; AniPLC recipe aliases meant
  checkpoint/recipe set equality was not enforced; registry construction used
  the no-level S3KL fallback rather than an explicit FBZ zone id.
- Correction: moved `TestS3kAizCompleteRunTraceReplay` into `known_red` with
  `TRACE_FRONTIER_LOG.md:37268-37272,37387-37389` provenance and left
  `green_test_classes` empty. The final plan treats that empty list as a
  documented no-op. Renamed all recipe keys to their exact checkpoint IDs and
  added a focused JSON contract test for exact set equality and deterministic
  required fields. Registry classification now uses a test registry whose
  `currentRomZoneId()` is explicitly `ZONE_FBZ`, retaining concrete `$A8/$A9`
  S3KL remap rejection through the full placement loop.
- First correction run stopped at test compilation because the new manifest
  test was missing its `java.util.Set` import; no test or production behavior
  executed. After correcting the import, the focused command exited 0.
- GREEN evidence: fresh selected-class Surefire XML reports 4 tests, 0
  failures, 0 errors, 0 skipped (MSE aggregate
  `passed=60 failed=0 errors=0 skipped=0`).

## Task 2 — canonical background-plane collision provider

- Requirement: FBZ background-plane collision must be one gameplay-owned semantic
  authority and cover ordinary sensors, explicit-world/`CalcRoomInFront` scans,
  and scattered-ring floor/ceiling checks while retaining legacy HCZ/MGZ/CNZ
  state translation.
- RED command: `mvn "-Dtest=TestBackgroundPlaneCollisionProvider,TestFbzBackgroundPlaneCollision,TestFbzCalcRoomInFrontBackgroundCollision,TestFbzRingBackgroundCollision" test "-Ds3k.rom.path=s3k.gen" -Dmse=off`
- RED result (2026-07-12): expected test-compilation failure because
  `BackgroundPlaneCollisionProvider`, `DefaultBackgroundPlaneCollisionProvider`,
  the provider `State`/`Probe` semantics, and
  `ZoneRuntimeState.backgroundPlaneCollisionState()` did not exist. This proves
  the focused tests require the new contract rather than passing against the
  legacy `GroundSensor#doScan`-only implementation. Test-fixture interface
  conformance was corrected before production work (nested
  `ScrollHandlerProvider.ZoneConstants` and `getMaxScrollOffset`).
- GREEN command: `mvn "-Dtest=TestBackgroundPlaneCollisionProvider,TestFbzBackgroundPlaneCollision,TestFbzCalcRoomInFrontBackgroundCollision,TestFbzRingBackgroundCollision,TestS3kHcz2RaisedFloorWallCollisionHeadless,TestS3kCnzMinibossArenaHeadless,TestS3kMgz2BgRiseHeadless,TestS3kCnzBossScrollHandler" test "-Ds3k.rom.path=s3k.gen"`
- GREEN result (2026-07-12): exit 0. Fresh selected-class Surefire XML
  reports 47 tests, 0 failures, 0 errors: provider semantics (3), explicit
  floor/wall/ceiling (1), real `CalcRoomInFront` layer-1 selection (1), ring
  signed selection plus floor/reverse-gravity behavior (2), HCZ2 (1), CNZ
  miniboss (22), MGZ2 background rise (12), and CNZ boss scroll (5).
- Affected-guard command covered `TestGroundSensor`,
  `TestGroundSensorServiceResolution`, `TestObjectTerrainUtils`, both scoped
  object-service guards, `TestGameServicesNullableAccessors`, and
  `TestGameplayModeContextRewindRegistry`. All Task-2-affected classes passed
  after retaining private helper compatibility entry points, making provider
  construction automatic for manually assembled gameplay contexts, and
  resolving camera differences against the actual probe level. The aggregate
  also reported one unrelated pre-existing failure in
  `com.openggf.tests.TestNoServicesInObjectConstructors` for two
  `AizMinibossInstance` inline `AizAct2CameraResizeController` allocations;
  Task 2 does not touch those files or constructors.
- No trace capture or replay was executed.

### Task 2 specification-review correction

- Review RED: LEFT-wall background translation incorrectly used the generic
  `x - Camera_X_diff` transform. Native `FindWall` complements the low nibble
  before and after subtraction, which differs for signed/non-16-aligned camera
  differences. Coverage also stopped short of ordinary `GroundSensor#doScan`,
  the two production scattered-ring owners, lifecycle reset, and object-owned
  `LevelManager` resolution.
- Correction RED command: `mvn "-Dtest=TestBackgroundPlaneCollisionProvider,TestFbzBackgroundPlaneCollision,TestFbzRingBackgroundCollision" test "-Ds3k.rom.path=s3k.gen" -Dmse=off`
- Correction RED result (2026-07-12): expected test-compilation failure because
  the provider had no direction-aware probe API and `ObjectTerrainUtils` had no
  owned-`LevelManager` wall overload. This was observed before correction code.
- Correction GREEN result (2026-07-12): focused correction suite reports 11
  tests, 0 failures, 0 errors. The combined exact Task 2 suite plus affected
  production-path tests/guards reports 136 tests, 0 failures, 0 errors. Coverage
  now executes ordinary `GroundSensor#doScan`, actual `CalcRoomInFront`, signed
  non-16-aligned LEFT/RIGHT transforms through `GroundSensor` and
  `ObjectTerrainUtils`, private `RingManager.LostRingPool` probes,
  `LostRingObjectInstance` with its injected `LevelManager`, and gameplay
  context recreation after explicit state. No trace capture or replay ran.

### Task 2 quality-review correction

- Review RED: provider creation depended on manager attach order; object-owned
  probes still reached global level/provider/participant state; and the hot path
  allocated `Optional<Probe>` records while resolving provider state twice per
  sensor scan.
- Correction: centralized attach-order-independent provider creation from both
  level-manager and shared-registry attachment. Added an injected provider and
  explicit secondary-collision participant semantic to `ObjectServices` and
  `DefaultObjectServices`; `LostRingObjectInstance` now passes those with its
  injected `LevelManager`, while `RingManager.LostRingPool` retains its provider.
  Legacy global `ObjectTerrainUtils` entry points remain clearly separated from
  owned overloads. Replaced probe objects with primitive orientation-aware
  translation from one resolved `State`, and cached unchanged default-provider
  state identity/values.
- Quality GREEN command: combined exact Task 2 suite, both attach orders,
  object-service guards, ring/object-terrain tests, and sensor regressions.
- Quality GREEN result (2026-07-12): 144 tests, 0 failures, 0 errors, 0 skipped.
  Focused coverage proves one state lookup per ordinary sensor scan and stable
  cached state until semantic camera inputs change. No trace capture/replay ran.

## Task 3 — event-backed FBZ runtime state and registration

- Requirement: establish one event-backed owner for all FBZ event/runtime fields,
  serialize it only through `FbzZoneRuntimeState`, route object writes through a
  narrow bridge, register/reconcile the adapter, and preserve the established
  post-camera frame order and next-pass collision visibility.
- RED command: `mvn "-Dtest=TestFbzZoneRuntimeState,TestFbzRuntimeStateRegistration,TestFbzEventWriteSupport,TestFbzEventRewindRoundTrip,TestFbzFramePhaseOrdering,TestS3kZoneRuntimeStateAdapters,TestSonic3kLevelEventRewindSnapshot" test "-Ds3k.rom.path=s3k.gen" "-Dmse=off"`
- RED result (2026-07-12): exit 1 during focused test compilation on the intended
  absent `Sonic3kFBZEvents`, `FbzZoneRuntimeState`, `FbzObjectEventBridge`,
  `S3kFbzEventWriteSupport`, manager FBZ accessor, and `currentFbz` registration
  API. One test-only import typo was corrected before production implementation.
- GREEN command: identical to the RED command above.
- GREEN result (2026-07-12): exit 0; 52 tests, 0 failures, 0 errors, 0 skipped.
  The new focused classes contribute 9 tests; existing runtime-adapter and
  event-rewind regression classes contribute 43. Expected warning output comes
  only from existing malformed-sidecar negative tests.
- Guard result: `TestStaticStateRewindCoverageGuard`,
  `TestZoneEventRewindSchemaGuard`, and `TestObjectServicesMigrationGuard` are
  green (32 tests total). The combined guard command also found unrelated AIZ
  worktree failures: three new `AizIntroEmeraldGlowChild` final-scalar coverage
  keys and two existing unsafe `AizAct2CameraResizeController` inline-spawn
  findings. Task 3 adds no spawnable object or static gameplay manager and did
  not modify those AIZ files or guard baselines.
- No trace capture or replay was executed.

### Task 3 specification-review correction

- Review RED: cloud IDs were serialized but never resolved after object-manager
  restore; FBZ was missing from the standard `Events_fg_5` transition path;
  inherited dynamic-resize state was duplicated despite FBZ not owning that ROM
  field; reload seams and Act-2-only modes were under-tested and under-validated.
- Correction RED command: `mvn "-Dtest=TestFbzCloudReconciliation,TestFbzZoneRuntimeState,TestFbzRuntimeStateRegistration,TestFbzFramePhaseOrdering" test "-Ds3k.rom.path=s3k.gen" "-Dmse=off"`
- Correction RED result (2026-07-12): exit 1 on ten intended missing
  cloud-cleanup/reconciliation symbols before correction production code.
- Correction: added restored-object identity resolution and a narrow Task-15
  `FbzCloudRecreator` seam. Non-null missing pre-cleanup IDs recreate in original
  index order and failed/absent recreation throws; terminal cleanup leaves them
  absent. Added exact lifecycle/backing tests, FBZ transition flag routing,
  strict Act-2-only mode validation, impossible plane/collision rejection, and
  removed dynamic-resize state from FBZ serialization/authority.
- Correction GREEN command: `mvn "-Dtest=TestFbzZoneRuntimeState,TestFbzRuntimeStateRegistration,TestFbzEventWriteSupport,TestFbzEventRewindRoundTrip,TestFbzFramePhaseOrdering,TestFbzCloudReconciliation,TestS3kZoneRuntimeStateAdapters,TestSonic3kLevelEventRewindSnapshot" test "-Ds3k.rom.path=s3k.gen" "-Dmse=off"`
- Correction GREEN result (2026-07-12): exit 0; 62 tests, 0 failures,
  0 errors, 0 skipped. A final removal of the manager's redundant FBZ
  dynamic-resize branches plus its new checkpoint assertion was followed by a
  14-test affected rerun with 0 failures/errors/skips. No trace capture/replay ran.

### Task 3 second specification-review correction

- Review RED: plane assignment and collision mode were incorrectly coupled;
  stale-adapter repair reset already-restored bytes; terminal cloud cleanup
  retained identities; and cloud recreation neither returned nor verified a
  live rebound identity.
- RED command: `mvn "-Dtest=TestFbzCloudReconciliation,TestFbzZoneRuntimeState,TestFbzRuntimeStateRegistration,TestFbzEventWriteSupport,TestFbzFramePhaseOrdering" test "-Ds3k.rom.path=s3k.gen" "-Dmse=off"`
- RED result (2026-07-12): exit 1 on the intended missing separate
  plane/collision APIs, byte-preserving stale-state migration method, and
  ObjectRefId-returning recreator contract.
- Correction: plane and collision writes now validate independently so every
  ROM boss-transition intermediate is representable. Rewind reconciliation
  migrates compatible restored bytes transactionally onto the current handler
  before installing its adapter. Terminal cleanup atomically nulls all cloud
  IDs and terminal snapshots with present IDs are rejected. Recreation returns
  the rebound ID, writes it into the stable slot, and verifies it against a live
  ObjectManager identity query that is refreshed after each recreation.
- GREEN command: `mvn "-Dtest=TestFbzZoneRuntimeState,TestFbzRuntimeStateRegistration,TestFbzEventWriteSupport,TestFbzEventRewindRoundTrip,TestFbzFramePhaseOrdering,TestFbzCloudReconciliation,TestS3kZoneRuntimeStateAdapters,TestSonic3kLevelEventRewindSnapshot" test "-Ds3k.rom.path=s3k.gen" "-Dmse=off"`
- GREEN result (2026-07-12): exit 0; 64 tests, 0 failures, 0 errors,
  0 skipped. No trace capture/replay ran.

### Task 3 final transactional-reconciliation correction

- RED: five focused cloud-reconciliation tests ran; two failed because an
  unresolved rebound and a later-index recreation failure left an earlier slot
  mutated, changing both the ten-ID list and serialized runtime bytes.
- Correction: reconciliation now stages all ten resulting IDs in a cloned array,
  completes the ordered recreate/non-null/live verification loop, and commits
  the authoritative array only after every slot succeeds.
- Focused GREEN: `TestFbzCloudReconciliation` reports 5 tests, 0 failures,
  0 errors, 0 skipped.
- Full GREEN: the complete Task 3 command reports 65 tests, 0 failures,
  0 errors, 0 skipped. `git diff --check` is clean. No trace ran.

### Task 3 quality-review correction

- Review RED exposed that handler-ID staging did not roll back already-created
  live objects, accepted changed rebound identities, repeatedly rebuilt identity
  tables, retained stale factory closures across loads, and used source-string
  frame-order assertions.
- Correction replaces per-cloud recreation with one ordered batch transaction
  (`recreateAll`/`commit`/`rollback`). Any count, null, exact-ID, liveness, or
  later-slot failure rolls back the modeled live object graph and leaves handler
  IDs/bytes unchanged. Initial liveness uses one identity table; one explicit
  refresh occurs after batch commit. Public terminal writes reject, while atomic
  restore permits terminal-to-preterminal rewind. Level init clears batch
  closures on restart, act reload, and zone exit. Frame tests now execute
  `LevelFrameStep` with phase-logging mocks and a two-frame collision sample.
- Focused GREEN: 19 tests, 0 failures/errors/skips. Full Task 3 GREEN: 66 tests,
  0 failures/errors/skips. Fresh guards: 32 tests, 0 failures/errors/skips.
  `git diff --check` is clean. No trace ran.

## Task 4: visual-system foundation (incomplete at PLC corruption gate)

- Disassembly preflight: S&K `FBZ_Deform` at `sonic3k.asm:108859-108920`,
  exact deform/index arrays at `109229-109296`, `FBZ2_CloudDeform` at
  `109701-109760`, cloud position/frame data at `110038-110048`, AniPLC lists
  at `55812-55890`, and `AnPal_FBZ` at `3370-3376`. The plan-requested
  `docs/s3k-zones/fbz-visual-manifest.md` did not exist at Task 4 HEAD; the
  checked inventory manifest and disassembly were used instead.
- Scroll/render RED command: `mvn "-Dtest=TestFbzScrollHandler,TestFbzBossCloudDeform,TestFbzBossPlaneRenderMode" test "-Ds3k.rom.path=s3k.gen"`.
- Scroll/render RED result: missing `SwScrlFbz`, `FbzBossPlaneRenderMode`, and
  generic plane-assignment/independent V-scroll frame-state APIs. After the
  minimal implementation, one expectation was corrected from `-$48E` to the
  ROM scatter-order result `-$4F0` (slot zero is the eighth `$E00` drift write).
- Animation/polarity/art RED command: `mvn "-Dtest=TestFbzAnimatedTiles,TestFbzMagneticPolarity,TestFbzPlcArtHandoffs" test "-Ds3k.rom.path=s3k.gen"`.
- Animation/polarity/art RED result: 27 intended missing symbols covering both
  FBZ AniPLC addresses/channels, magnetic phase dispatch, line-4 ownership,
  consumer art keys, and PLC handoff contracts.
- Focused GREEN command: `mvn "-Dtest=TestFbzScrollHandler,TestFbzBossCloudDeform,TestFbzBossPlaneRenderMode,TestFbzAnimatedTiles,TestFbzMagneticPolarity,TestFbzPlcArtHandoffs,TestSonic3kPatternAnimatorRewindSnapshot,TestSonic3kPlcArtRewindSnapshot" test "-Ds3k.rom.path=s3k.gen"`.
- Focused result: every named Task 4 and snapshot class passed. The relaxed MSE
  aggregate reported 342 passed and two unrelated inherited AIZ guard failures
  (`AizIntroEmeraldGlowChild` final-scalar coverage and two
  `AizAct2CameraResizeController` inline-spawn findings).
- Mandatory PLC corruption guard first exposed the composite FBZ egg-capsule
  mapping; it was corrected to level-backed ownership because its mapping mixes
  FBZ misc/plunger tiles with PLC-loaded capsule tiles. The rerun then failed on
  `fbz_boss_pillar`: shared `Map_FBZ2Preboss` was incorrectly registered as a
  standalone sheet. Disassembly consumer tracing proves pillar uses default
  frame 0 and clouds explicitly use frames 1-3. Both are PLC-loaded level-art
  destinations (`$3D5` and `$3A3`) and now carry exact filters `{0}` and
  `{1,2,3}`. A focused test first failed on the absent split, then passed.
- The same guard identified exit door/hall as runtime PLCKosM level-art rather
  than standalone art; they now use native destinations `$3E5/$3F4`. The exact
  focused mapping test, PLC mapping-sanity guard, and engine corruption guard
  exit 0. The full Task 4 plus affected-regression rerun exits 0.
- Mandatory route-wave guard result: 48 tests, 46 passed, two inherited AIZ
  failures only (the three `AizIntroEmeraldGlowChild` final-scalar keys and two
  `AizAct2CameraResizeController` inline-spawn findings). No FBZ guard failed.
- Required `mvn -Dmse=off package -Ds3k.rom.path=s3k.gen -q` was run and exits 1
  on the worktree's broader pre-existing failures, including the same AIZ
  coverage/spawn issues and other non-FBZ branch failures. Task 4 focused and
  corruption verification remains green, but the foundation wave cannot be
  declared globally GREEN until those inherited gate failures are repaired.
  No trace capture or replay was executed. `git diff --check` was clean.
- Renderer-consumption follow-up RED: the behavior-level
  `advancedPlaneRoutingSwapsNametableSourcesOnceAndKeepsVScrollIndependent`
  test failed compilation on the absent generic `PlaneRouting` contract.
  `LevelRenderer` now swaps Plane A/B sources at the foreground/background
  tilemap boundary only, keeps normal routing unchanged, and applies the two
  V-scroll overrides independently to foreground, priority-mask, and background
  responsibilities. There is no FBZ branch in the renderer.
- Renderer/all-Task-4 GREEN rerun: 75 tests passed with no failures, errors, or
  skips (the eight Task 4 classes plus affected renderer, render-order,
  advanced-mode, rewind, and registration coverage). PLC registry/provider and
  corruption rerun: 80 tests passed with no failures, errors, or skips.
- Task 4 spec-review correction RED/GREEN: the runtime tests first exposed the
  frame-derived deform phase and duplicate magnetic-edge toggle; art tests first
  failed on 13 missing PLC dependency/raw-list symbols; strengthened AniPLC
  tests first failed on seven missing deterministic inspection symbols. The
  corrected implementation uses one event-owned, rewind-serialized 32-bit
  `HScroll_table+$1FC` accumulator across indoor/outdoor/boss modes, serializes
  the magnetic edge identity, publishes every exact PLC `$62-$6A`/`$6F`
  dependency without duplicate shared keys, and replaces the fabricated
  `[62,01,05]` handoff with the raw monitor PLC list bodies.
- Exact Nemesis header sizes used by the corrected FBZ boss-art guards are:
  subboss 74 tiles/2368 bytes, end boss 48/1536, Robotnik head 32/1024,
  stand 67/2144, run 87/2784, flame 68/2176, shared ship 82/2624,
  explosion 46/1472, and Egg Capsule 70/2240.
- Corrected combined Task 4 + Task 3 rewind + renderer + PLC/corruption suite:
  202 tests passed with no failures, errors, or skips. Mandatory ten-guard gate:
  46/48 passed; only the inherited AIZ emerald-glow final-scalar coverage gap
  and AIZ camera-resize inline-spawn guard failed. No FBZ finding was reported.
- Task 4 quality-review allocation/ownership correction: renderer and magnetic
  tests first failed compilation on the missing primitive routing helpers and
  typed adapter dispatch. `LevelRenderer` now resolves plane sources and both
  V-scroll values through allocation-free enum/primitive helpers; the former
  `PlaneRouting` record and its three per-frame allocations are gone.
  `SwScrlFbz` now reuses one list view and ten mutable cloud-position slots,
  removing the boss-frame list/record/copy allocations. `Sonic3kPaletteCycler`
  advances magnetic state only through `FbzZoneRuntimeState`, with no concrete
  event-manager lookup. The bounded Task 4 + renderer/runtime + PLC corruption
  regression set passed 168 tests with no failures, errors, or skips.

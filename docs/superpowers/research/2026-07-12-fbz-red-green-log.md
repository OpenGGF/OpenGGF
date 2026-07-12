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

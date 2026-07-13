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
- Task 5 Act 1 events RED: the focused `TestFbzEventsAct1`,
  `TestFbzAct1LayoutMutations`, and `TestFbzOutdoorBgMotion` compile failed on
  the deliberately absent Act 1 screen/background event API, exact Plane-A
  copy-plan model, staged redraw progression, and ordinary dynamic
  `FbzOutdoorBgMotionObjectInstance`. This proves the new tests do not pass on
  Task 4's event-state skeleton. No production file or trace was touched before
  observing this failure.
- Task 5 Act 1 events GREEN: 16 focused behavior tests now cover the exact six
  inclusive `FBZ1_LayoutModRange` rectangles and Plane-A donor/destination copy
  shapes, screen/background initialization, foreground/background hysteresis,
  death gating, four direction routines with trigger-frame drawing and 16-frame
  completion, palette/deform ownership, the `$2800/$80`
  `Gradual_SwingOffset` controller, persistence/rewind recreation, and
  same-frame controller-to-deformation bob consumption. The bounded Task 5 plus
  affected Task 3/4 runtime/deform/palette/rewind and direct-map guard suite
  passed 55/55. Four mandatory guards passed 19/21; the only two failures were
  the inherited AIZ emerald-glow final-scalar rewind gaps and AIZ miniboss
  inline camera-controller spawns already present before Task 5. No FBZ guard
  finding and no trace activity occurred.
- Task 5 staged Plane-B review RED/GREEN: the first review correctly found that
  event redraw state had no retained nametable consumer. A new cadence test
  first failed compilation on the missing delayed-position API. The corrected
  path uses a persistent 64x32 Plane-B cache, exact one-row/two-column 16-frame
  scheduling (33-block horizontal and 17-block vertical prefetch extents), one
  upload per changed batch, init source-X seeding (`0`/`$200`), normal
  `Draw_TileRow` crossing refresh, and rewind inverse-seed plus completed-strip
  replay. Delayed position, signed rowcount, anchor, and rounded BG Y are owned
  by the FBZ event serializer (version 4). MutableLevel dirty-map processing is
  now layer-aware so Plane-A swaps do not invalidate retained Plane B.
- Final bounded Task 5 plus affected Task 3/4, palette/rewind, incremental BG
  cache, and rewind-reset suite passed 88/88 with no failures, errors, or skips;
  `git diff --check` was clean. Mandatory guards remain 19/21 with only the
  already-recorded inherited AIZ failures and no FBZ findings. No trace was run.
- Task 5 final spec-review RED/GREEN: strengthened tests first failed on the
  multi-column redraw abstraction, missing allocation-attempt state, and absent
  exact `Draw_TileRow` position selector. The corrected implementation issues
  horizontal columns individually in ROM order (`$3F0,$3E0,...` or
  `$000,$010,...`), never adds the outdoor `$200` to those delayed/source X
  values, derives column source and retained-plane destination Y from effective
  BG Y, and follows the `$FF0`-masked old/new-row direction plus optional second
  update at 32-pixel crossings. `AllocateObject` is attempted exactly once even
  when it fails; attempted and successful states round-trip independently in
  FBZ rewind schema version 5.
- The same correction adds a persistent runtime-owned `Target_palette` staging
  surface. Outdoor `BackgroundInit` writes its 16 bytes there without touching
  normal palette ownership; runtime transitions submit a normal-palette write
  only on the trigger frame, while reconciliation deliberately reapplies the
  serialized target. Live `LevelManager` tests exercise the 64x32 retained
  buffer at nonzero Y, vertical wrapping, and inverse-seed plus completed-strip
  replay after mutation. The final affected suite passed 100/100; focused live
  retained-plane coverage passed 5/5. Mandatory guards remain 19/21 with only
  the inherited AIZ findings, `git diff --check` is clean, and no trace ran.
- Task 5 second re-review RED/GREEN: new high-Y tests exposed vertical staged
  redraw truncating source rows to eight bits. Source positions now retain the
  ROM `$FF0` mask (including `$A20`/`$AE0` test cases); only the 64x32 VDP
  destination wraps. A mid-transition mode-jump test also proved that inverse
  seed plus staged-prefix replay cannot reconstruct ordinary `Draw_TileRow`
  writes. FBZ rewind schema version 6 therefore captures the exact 8192-byte
  retained Plane-B descriptor image, validates it transactionally, and restores
  and uploads it once during reconciliation. The live-buffer test covers a
  staged column, a high-Y ordinary target-mode row, later mode mutation, and
  cell-for-cell restoration.
- Palette ownership rewind now includes the persistent target Sega bytes and
  compact target-owner ids/table. Snapshot construction and restoration validate
  fixed dimensions and owner references, all array access is defensive, and
  registry-only mutation/restore tests prove the target surface round-trips
  independently of normal frame ownership. Focused correction tests passed
  46/46; the final affected suite passed 111/111. No trace was run.
- Task 5 third re-review RED/GREEN: a real `LevelTilemapManager` test showed the
  retained Plane-B image needed an explicit authoritative lifecycle rather than
  the generic overlay invalidation path. FBZ row, column, seed, and restore
  writes now use retained-authoritative APIs: camera window preparation updates
  base bookkeeping without rebuilding or shifting those bytes, while genuine
  geometry/layout/full-width invalidations clear the mode and retain the normal
  full-build fallback. The real manager test writes and restores descriptors,
  runs next-frame ensure preparation, and preserves the image byte-for-byte.
- A pending FBZ Plane-B image now wins capture over the current live runtime
  until reconciliation installs and clears it; later captures read the live
  image. Packed palette snapshots additionally reject duplicate owner entries
  and tables beyond the one-byte 255-id capacity. The focused lifecycle and
  schema suite passed 48/48. No trace was run.
- Task 5 quality-review RED/GREEN: retained capture now extracts a strict 64x32
  VDP ring (8192 bytes) from any larger world cache and restore installs that
  exact ring. FBZ Act 2 no longer advertises persistent Plane B and its rewind
  payload is always empty. Horizontal redraw now treats `d2` as the ROM clipping
  window (`0..$1F0` indoors, `$200..$3F0` outdoors); delayed X is passed directly
  to column setup, so only the matching direction/mode pair writes columns.
- Because the engine implements level-entry fade as an opaque overlay rather
  than ROM palette interpolation, FBZ outdoor startup preserves its target-line
  write and also queues the same line-4 patch into Normal while the overlay is
  black, making the revealed post-fade palette correct. The unused registry
  controller factory was removed; the event allocation supplier is canonical.
  A live ObjectManager rewind graph test restores all three swing scalars along
  with the serialized attempted/spawned event state. Focused quality tests passed
  51/51. No trace was run.
- Task 5 final quality lifecycle correction: the startup target patch is now
  resolved into Normal immediately (while the black overlay is opaque), so a
  subsequent `beginFrame()` cannot discard it. A ROM-backed `@RequiresRom`
  FBZ1 test boots at X below `$180`, verifies displayed line-4 colors directly
  against `Pal_FBZBGOutdoors`, proves the real backing cache is taller than 32
  rows, captures the fixed 8192-byte VDP ring through `FbzZoneRuntimeState`,
  mutates it, restores/reconciles/uploads it, and verifies later camera/render
  preparation preserves the bytes. The lifecycle test passed 1/1 and the final
  affected set passed 116/116. Mandatory guards remain 19/21 with only the
  inherited AIZ findings; `git diff --check` is clean. No trace ran.
- Task 6 shared-placement RED: the new counted-subtype/completeness suite ran
  8 tests and failed exactly on the two missing concrete families/profile
  entries: FBZ still had 535 placeholder placements instead of 533, and placed
  `$00` Ring / `$3D` RetractingSpring resolved to placeholders. The same first
  pass corrected two test-fixture assumptions (raw placement metadata and the
  full locked-on hurt-block pointer labels) before accepting production GREEN.
- Task 6 GREEN: all counted shared FBZ subtypes resolve through the locked-on
  S3KL table and preserve their complete `ObjectSpawn` configuration. The real
  six-byte FBZ2 `$00` record now executes `Obj_Ring` collision `$47`, fixed
  right-channel ring SFX, all-eligible-player collection, four-frame sparkle,
  permanent collected lifetime and generic rewind recreation; the trailing
  `FFFF 0000 0000` records remain terminators, not rings. `$3D` reuses the
  canonical spring art/collision/launch path and implements the exact `$800`
  extension/retraction, `$2000` limit, 60-tick holds, borrow-at-zero phase
  change, moved-centre collision/render, and on-screen-only SpikeMove edge.
  Placeholder placements ratcheted from 535 to exactly 533 and no farther.
- Task 6 hardened focused coverage passed 8/8. The bounded affected
  ring/spring/registry/PLC/art-corruption/rewind/guard run passed 179/181; its
  only failures were the inherited AIZ emerald-glow final-scalar coverage gaps
  and the two inherited AIZ miniboss inline camera-controller constructor
  spawns. No FBZ finding and no trace activity occurred.
- Task 6 spec-review correction RED: 13 hardened shared-object tests produced
  exactly four behavioral failures: the placed ring ignored Player 1's ROM
  `$40` invulnerability-timer gate for sidekick contact, deleted sparkle on
  tick 24 instead of tick 25, dropped `make_art_tile(...,1,1)` priority, and
  bypassed native GiveRing threshold/cap behavior. A separate retained-visibility
  edge test was added for the retracting spring's prior `render_flags` bit 7.
- Task 6 correction GREEN: 14/14 focused tests now cover main-player timer 90
  vs 89 with an extra sidekick collector; 24 drawn sparkle ticks plus next-tick
  deletion; palette line 1, persistent priority, `$100/$80` buckets, and global
  ring phase delegation; and GiveRing transitions 99->100, 199->200,
  998->999, and 999->999. `Sonic3kRingAwardService` is the reusable award owner;
  level-scoped 100/200 flags are captured by the level rewind snapshot so ring
  loss and rewind cannot re-award a consumed threshold. Retracting-spring SFX
  reads a retained previous-render-pass visibility bit rather than live camera
  geometry. Subtype `$04` also has explicit canonical map/tile/palette,
  priority/16x16, red-up velocity, and `$0C/$0D` solid-bit assertions.
- The corrected bounded affected/guard run passed 201/203; only the same two
  inherited AIZ guard categories failed. No FBZ finding and no trace activity
  occurred.
- Task 6 final spill-reset RED/GREEN: a new 100-ring loss/recollection test
  first failed compilation on the deliberately missing named loss transition.
  `LevelState.resetRingsForLoss()` now models the Obj37 owner-only clear of both
  `Ring_count` and `Extra_life_flags`; `RingManager` calls it when the spill
  materializes. Ordinary `setRings(0)` remains independent so rewind/checkpoint
  restore can apply captured ring count and threshold flags in order. The test
  proves 99->100 awards life/music, spill clears both values, and the later
  99->100 correctly awards them again.
- Task 7 carrier/transport RED: four focused test owners failed compilation
  exactly because the eight locked-on FBZ factories/classes for S3KL IDs
  `$6F-$72,$75-$78` did not exist. Preflight reconciled 121 counted placements:
  `$6F` 13, `$70` 9, `$71` 22, `$72` 20, `$75` 8, `$76` 32, `$77` 6, and
  `$78` 11. The used subtype matrices, `AllocateObjectAfterCurrent` order,
  player-DPLC tables, S&K-side mapping addresses, fixed-point/trig constants,
  solid dimensions, control bits, SFX edges, cull/respawn paths, and eight
  snake routes were decoded from `sonic3k.asm` before production.
- Task 7 initial GREEN: the focused four-class suite passed 10/10 and the
  executable completeness gate passed 2/2. The FBZ placeholder count ratcheted
  only for these rows, from 533 to 412. Level-art mappings use S&K offsets
  `$3A742/$3AD8A/$3B6CE/$3B73C/$3B91A/$3BA8A`; wire cages intentionally keep
  player mappings/DPLC. Multi-sidekick participation is explicitly the native
  pair behavior extended to engine sidekicks with isolated primitive arrays.
  No trace was run.
- Task 7 hardening GREEN: the focused suite grew to 17 tests. Three eligible
  players acquire `$6F/$70/$72` independently, retain their complete 16-bit
  playable subpixel fractions through `NativePositionOps`, and release control
  without disturbing other owners. Actual mocked object-manager calls prove
  `$75` allocates exactly three after-current children in `$19/$31/$49` order;
  `$77` allocates five children for subtype `$00` in the remaining-radius order
  and one normal child for `$0C` after its special magnetic parent. `$78` emits
  one `FloorLauncher` edge for its 12-tick phase. All eight families pass the
  generic rewind field sweep. The hardened focused subsets passed 7/7 and 8/8.
  No trace was run.
- Task 7 bounded regression: all Task 7, inventory, completeness, Task 5/6 FBZ
  event/runtime/shared-object, PLC, renderer-corruption, static-rewind, and art
  assertions passed. The first rewind guard exposed only constructor-derived
  final configuration in the new classes; these fields are now explicitly
  transient and recreated from `ObjectSpawn`. Its rerun contains only the three
  inherited `AizIntroEmeraldGlowChild` gaps. Constructor guard contains only
  the two inherited `AizMinibossInstance` camera-controller spawns; the broader
  physics guard also reports unrelated pre-existing AIZ/S1 budget drift. No
  Task 7 guard finding remains, `git diff --check` is clean, and no trace ran.
- Task 7 ROM-parity correction RED/GREEN: five new behavior assertions first
  failed exactly on stationary-cage capture, global-oscillator platform motion,
  horizontal hand-over-hand movement, snake coarse culling, and subtype `$0C`
  rotating-platform touch response. The corrected implementations now follow
  the locked-on per-player `$70` state blocks and player mapping tables; all
  `$71` movement handlers including the exact mode-4 recurrence; `$72` capture,
  step frames/deltas, midpoint Grab edge, endpoint cleanup, and same-frame next
  cycle; `$75` route bounds/waypoints, sentinel teleport/wait/restart, and
  allocation-failure delay retention; and `$77` signed-radius trig plus the
  special first member's `$86` hurt response alongside solidity.
- Exact sequence hardening covers `$71` nibble-1 accumulator/velocity/direction/Y
  for every one of its 10 active ticks, `$72` command ticks 1/9/17/25 including
  the next cycle's same-tick first step, and `$75` route-0's 124 movement ticks,
  60-count wait, restart-tick movement, and fail/succeed/succeed child delays
  `$19/$19/$31`. The final bounded run executed 142 tests: all 140 in-scope
  Task 7/FBZ/event/runtime/PLC/art/static-rewind assertions passed; only the
  inherited AIZ rewind and constructor-guard categories failed. No trace ran.
- Final Task 7 self-review added two last exact edges: continuous `$72`
  horizontal input performs the next hand cycle's first `$04` displacement on
  the prior cycle's completion tick, and `$75` timers are per-instance
  countdowns rather than global-frame comparisons. Both strict tests failed on
  the old behavior and passed after correction; the parent timer `$01` now
  falls through to movement on its first update, while late-spawned children
  retain their own `$19/$31/$49` delays.
- External Task 7 spec-review correction replaced the remaining compressed
  behavior. Exact priority buckets/touch flags, `$6F` dual player machines,
  `$70` branch-specific capture/loop/cleanup, `$71` radius-64 mode 3 and `$8C`,
  `$72` input/release/DPLC state, `$75` initial solid gate, `$77` failed-slot
  retry, `$78` per-contact facing, `$76` full mapping subtype/coarse cull, and
  filtered rotating-special frame 0 now have direct behavior assertions.
  Carrier state moved from fixed 16-entry order arrays to a scalable,
  identity-keyed rewindable primitive table; a 20-duplicate-Sonic test proves
  independent ownership/release. Focused coverage passed 32/32. The bounded
  run passed all 124 in-scope Task 7/inventory/shared/PLC/art/static-rewind
  assertions; the only failures were inherited AIZ guard findings after marking
  Bent Pipe's constructor-derived size index rewind-transient. No trace ran.
- Task 8 mechanical-family RED began with 10 missing class symbols for the
  locked-on `$79-$7F/$E0` implementations. Focused correction tests then exposed
  the remaining compressed behavior: `$79` irregular-delay timing and rider
  release, `$7B` P1-only camera ownership, missile allocation-failure state,
  companion explosion retries, and `$E0` child fixed-point/offscreen ordering.
- Task 8 focused GREEN implements all 82 placed records across the eight IDs.
  `$79` follows the global phase gate and exact 6-frame non-solid / 121-frame
  solid cycle while releasing every participating rider; `$7B` keeps scalable
  simultaneous participant state but only a held P1 requests forced camera
  position. `$7C-$7E` retain their exact collision, moving-solid, and P1-trigger
  contracts without invented wind, breakage, buttons, or debris behavior.
- Task 8 missile graph RED first failed on missing companion relink accessors,
  then proved a real `$E0` double-shift bug (`$400` moved 1024 px instead of
  4 px) and clarified that slot replacement marks the old missile destroyed
  before normal ObjectManager cleanup. GREEN now round-trips a real `$7F`
  parent/companion/missile graph through snapshot, removal/divergence, and
  restore with exact roles, family slots, configuration, impact count, relinks,
  no duplicate children, and same-slot explosion replacement. Companion
  allocation failure preserves five impacts, explosion failure retries the same
  offset, and no non-ROM parent-destruction cascade is added. `$E0` children are
  parentless, toggle only on the four-frame boundary, and delete before motion
  when offscreen.
- Final Task 8 bounded coverage passed 21/21: disappearing/screw 5/5,
  pole/propeller/piston/blocks 6/6, missile behavior 7/7, real graph rewind 1/1,
  and registry/profile completeness 2/2. The executable placeholder ratchet is
  exactly 330. Rewind and constructor guards contain only the inherited AIZ
  emerald-glow final scalars and miniboss camera-controller spawns; no Task 8
  finding remains. `git diff --check` is clean. No trace or commit ran.
- Task 8 spec re-review RED added five direct regressions and all failed on the
  prior compressed behavior: ordinary `$7F` missiles skipped
  `ObjCheckFloorDist`, `$7B` treated held jump as a fresh logical press, the
  launcher companion culled from its displaced X and survived detonation,
  `$7C` used a 2D viewport test, and legacy `$7A` bit-4 incorrectly reversed
  the restored `$40` Y displacement.
- Task 8 spec re-review GREEN now probes ordinary missiles every frame with
  radius `$C`, snaps on strictly negative distance, and replaces the same slot
  with the explosion at snapped Y+4. The pole reads the canonical logical jump
  press edge, so a jump held before capture cannot launch. The companion stores
  the parent `$44` anchor, changes both X/anchor to `$7F00` on detonation, and
  executes its solid/cull tail in that same update. Propeller deletion is the
  exact coarse-X `$280` path, and legacy screw-door restore always adds `$40`
  Y regardless of direction bit 4. Focused correction coverage passed 28/28.
- Task 8 final branch re-review RED isolated two follow-on cases: a target-bit
  missile already in flight did not start floor probing after the fifth impact
  cleared its parent's target mode, and restored legacy horizontal doors still
  applied subtype X motion. GREEN makes every missile use the target branch only
  while its parent remains in target mode, otherwise falling through to the
  ordinary floor probe; restored legacy doors now return exactly placement X and
  placement Y+`$40`, bypassing all subtype motion. The two direct suites passed
  21/21 after both corrections.
- Task 8 quality-review RED isolated three implementation defects: spinning-pole
  rolling/standing radius swaps moved native `y_pos` and lost the intended
  distinction between the capture-time ROM radius delta and the launch-time
  no-movement swap; piston `update` duplicated the manager's custom-range check
  behind a broad `catch (Exception)`; and AllocateObjectAfterCurrent siblings
  used child-named rewind adoption despite having independent lifetimes. The
  three direct boundary tests failed before the corrections.
- Task 8 quality-review GREEN preserves full Y subpixel state, applies the
  explicit old-radius minus standing-radius native Y delta only when capturing
  a rolling player, and preserves native Y across launch into each character's
  rolling radii. Piston now leaves its null-safe custom predicate entirely to
  `ObjectManager`. The shared after-current allocator now serves structural
  children and independent siblings through neutral reconstruction-object
  adoption; direct tests prove same-frame ordering, allocation failure at slot
  127, probe-construction suppression, and absence of allocator lifetime
  cascade. The launcher family graph passes both the default in-place restore
  and forced reconstruction paths with exact roles, links, slots, same-slot
  replacement, and no duplicates.
- Mechanical formatting expanded all eleven Task 8 production classes and the
  four focused/graph tests into readable Java. Repeated `$FF80` coarse-X delete
  math is now a named shared helper without changing the ROM unsigned-distance
  behavior. Focused Task 8 plus sibling-allocation coverage passed 43/43;
  inventory/completeness/corruption coverage passed 6/6. The affected guard run
  added no Task 8 findings: its failures remain inherited AIZ emerald-glow
  rewind scalars, AIZ miniboss inline camera-controller spawns, and unrelated
  pre-existing physics/lifecycle ratchet drift. `git diff --check` is clean.
  No trace or commit ran.
- Final Task 8 quality normalization removed the last minified methods and
  inconsistent indentation from the disappearing-platform/screw-door test;
  its source now has no lines over 120 characters. The disappearing platform's
  phase masks and solid durations are named `static final` lookup tables, so
  neither construction nor the per-cycle script transition allocates a new
  array. The direct suite passed 9/9 and the complete Task 8 focused matrix
  passed 43/43 after this change; `git diff --check` remains clean. No trace or
  commit ran.
- Task 9 behavior RED began with five planned test owners failing compilation
  on 22 missing family symbols. The first implementation pass then made the
  focused object/polarity suite GREEN 9/9. Registry completeness independently
  failed at the exact expected boundary, proving the seven promoted IDs reduce
  live FBZ placeholders from 330 to 101 across 229 placed records.
- Task 9 oracle-review RED corrected the initial three-state magnetic model to
  the ROM's one-bit INACTIVE/ACTIVE state, moved AnPal ownership before dynamic
  objects, and added the fade-suppressed/lost-edge rule. Tests cover `$00FF`,
  `$0100`, `$0101`, `$01FF`, `$0200`, `$0201`, idempotent recomputation, and
  same-frame consumer visibility. The phase/completeness/object matrix reached
  GREEN 16/16 before the subsequent object-oracle hardening pass.
- Task 9 object-oracle REDs then tightened native 16:16 magnetic motion, exact
  `$73` subtype art/culls, `$74` chain shape, `$E3` terminal scripts, `$E4`
  independent 17-update flames/nozzle multisprites, `$E5` strict-P1 companion
  ownership, and `$FF` strict-P1 three-slot 8.8 pendulum geometry. The direct
  post-correction slice is GREEN 15/15. Guard failures remain the inherited AIZ
  emerald-glow scalars and AIZ miniboss camera-controller construction findings;
  Task 9 introduced no new reported guard key. No trace or commit ran.
- Task 9 rewind-graph validation exercises the real three-slot magnetic
  pendulum graph in both in-place and forced-reconstruction restore modes.
  Pivot, endpoint, and chain preserve their exact slots and bidirectional
  ownership links without duplicate allocation on the following update; the
  graph suite is GREEN 2/2. The FBZ runtime/event matrix is GREEN 19/19.
- Final art validation first RED-lined on the Task 8 wall-missile table. Its
  first two `dc.w` offsets point backward to shared projectile frames, exposing
  that `S3kSpriteDataLoader` treated relative mapping offsets as unsigned and
  that the registry lacked the disassembly-defined eight-frame count. A focused
  metadata regression failed with `expected 8, actual -1`, and the generic
  signed-address regression initially failed compilation. GREEN sign-extends
  every mapping frame offset, records the explicit eight-frame table length,
  and proves `$3C906 + (short)$FEC8 == $3C7CE`; both regressions pass 2/2.
  Exact ROM shape coverage for all seven Task 9 mapping tables passes 1/1, and
  the full all-zone mapping crawler plus renderer corruption guard passes 3/3.
  The final Task 9 object/event/runtime/completeness matrix passes 55/55. The
  environmental graph and architecture slice passes 18/18; the rewind inventory
  then RED-lined on the two Task 8 missile graph members and is GREEN after
  classifying both as graph-covered isolated probes backed by the existing
  in-place/forced-reconstruction `TestFbzMissileFamilyGraphRewind` evidence.
  The exhausted parent-dependent baseline remains empty. `git diff --check` is clean. No
  trace or commit ran.
- Final Task 9 spec-review RED corrected four frame/coordinate seams. The `$FF`
  attached-player position now reproduces the ROM's sequential signed longword
  shifts, yielding radii 148 standing and 143 rolling with correct negative
  diagonal rounding. `$74` probes its first landing with `y_radius=$0F`, changes
  to `$10` only on floor contact, and retains that radius for later floor and
  ceiling probes. `$E4` now executes a manual solid checkpoint immediately
  before its standing-trap routine, so landing, leave/reset, frame 2, and the
  60-update launch all consume current-frame contact bits. Parent, inline
  nozzles, and independent flames preserve the placement H-flip for rendering;
  lateral flame position and velocity use the same copied bit. The direct
  correction slice passes 11/11.
- Task 9 quality RED expanded executable evidence beyond decode assertions.
  Real `ObjectManager` rewind tests now cover `$74` chain and `$E5` companion
  links, exact slots, and no post-restore duplicate allocation in both in-place
  and forced-reconstruction modes alongside `$FF` (4/4). Mine coverage executes
  exact unsigned proximity/debug gates, the detection frame, 31 blinking
  updates, one armed collision frame, and next-update same-slot explosion/SFX.
  Trap-spring coverage launches three identity-distinct riders from prior-frame
  standing bits with exact `$00/$02` impulses and flip facing. Flamethrower
  coverage executes subtype/cadence/allocation-failure/current-contact behavior;
  Spider Crane executes capture through release while `$E5/$FF` leave three
  configured sidekicks untouched. The complete expanded Task 9 matrix passes
  69/69. No trace or commit ran.
- Final rewind-inventory integrity RED started at expected
  `836 total / 660 passed / 176 graph-covered` versus the aggregate FBZ branch's
  `872 / 693 / 178`, with `ScreenShakeTimerSlotObjectInstance` as the sole
  no-probe-constructor tail. That timer predates FBZ work (`fe0625c6c`); the
  missing `ObjectSpawn` probe constructor already has an established fix in
  separate commit `0dfda47b7`. Applying only that constructor moves it to the
  passing bucket, producing the transparent aggregate ratchet
  `872 / 694 / 178` with every tail bucket still zero. The inventory,
  parent-dependent guard, recreate-link guard, and graph-classification suite
  pass 26/26. Rewind coverage and constructor guards still report only inherited
  AIZ findings: three emerald-glow final scalars and two inline miniboss camera
  controller spawns. No Task 9 key appears in either failure.
- Task 10 planning review RED found that the short plan compressed 63 placed
  badniks into subtype-only behavior, incorrectly described TechnoSqueek as a
  firing family, omitted Blaster's placement-orientation magnetic split, and
  left the `$CF` subtype-2 falling badnik ownership boundary ambiguous. The
  disassembly oracle fixes the executable boundary at 24 Blasters plus 39
  TechnoSqueeks, so Task 10 alone must move completeness exactly `101 -> 38`.
- Task 10 planning GREEN now freezes the independent Y/render orientation bits:
  exactly 12 Blasters are ceiling/magnetic consumers and 12 are ordinary. It
  records P1-only initial facing versus closest-native-P1/P2 attack acquisition,
  the same-frame magnetic interrupt/resume of routines 2/4/6/8, one-shot
  after-current allocation order and independent-versus-parent-owned lifetime,
  exact fixed-point motion/culls, and the absence of attack/detach/impact SFX.
  Required evidence includes the MHZ SKL negative remap, more-than-two-sidekick
  targeting, exact mapping shapes, real-slot rewind reconstruction, allocation
  failure, and the `101 -> 38` ratchet. Task 10 owns concrete `89F16/89F24`
  falling entry forms; Task 17 only integrates them into `$CF` subtype 2. This
  was a planning/oracle correction only: no production code, tests, trace, or
  commit ran.
- Residual Task 10 planning review RED found three frame-order/lifetime errors:
  `89726` was incorrectly described as parent-deleting, TechnoSqueek's `$2E=$10`
  write was incorrectly treated as the child-freeze timer, and the plan omitted
  the offscreen-shim and same-frame after-current first-tick behavior. GREEN now
  records `89726` as parent-relative but independently `$F4`-terminating; bit 5
  clears only at the raw-animation `$F4`, leaving `89B24` frozen through 92
  movement updates and refreshing on the 93rd. The visibility frame only
  restores real object code, init occurs next frame, and successful after-current
  slots execute later in their creation frame: `89726` initializes without a
  draw; `8972E/89746` initialize then move, apply gravity, animate to frames 6/8,
  cull, and touch; `89B24` initializes and draws frame 2. Velocity wording is
  corrected to signed 8.8 values integrated into 16.16 positions. This remained
  a documentation-only correction; no production code, tests, trace, or commit
  ran.
- Task 10 implementation adjudication corrected that residual planning claim
  against the locked-on call graph. `loc_89940` writes `$2E=$10` and
  `$34=loc_89926`; every nonzero-velocity movement update tail-calls `Obj_Wait`,
  whose word predecrement reaches zero after 16 updates and underflows on update
  17. Therefore bit 5 clears before `89B24` runs on resumed-moving update 17;
  raw `$F4` reaches the same callback on update 93 but is redundant. RED tests
  also exposed a compressed 13-update turn versus the exact 33-update raw turn,
  ignored vertical-placement X flip, one-way parent/child rewind relinking,
  Blaster's secondary projectile two updates late, and an extra active frame in
  `89726`. The corrections pass Blaster cadence 2/2, TechnoSqueek 7/7, and both
  real-graph rewind modes 4/4; the rewind coverage guard retains only the three
  inherited AIZ emerald-glow scalar findings. No trace or commit ran.
- Task 10's final evidence-gap pass adds real slot-127 exhaustion for one-shot
  `89726/8972E/89746` and `89B24` failures, full setup-only/fixed-point/floor
  snap/in-place falling conversions for both badniks, and a Blaster rewind
  snapshot containing the attack effect plus both independent projectile roles
  in both restore modes. Magnetic coverage now interrupts and resumes exact
  phase signatures from routines 2/4/6/8 (`PATROL`, `WAIT_TURN`, `ATTACK_WAIT`,
  and `ATTACK`) and restores a compact-rewind `MAGNET_WAIT` snapshot before
  landing. The focused totals are Blaster 12/12, TechnoSqueek 8/8, and real
  graph/allocation rewind 5/5. No trace or commit ran.
- Final Task 10 spec RED corrected three remaining dispatcher details:
  `89746` now allocates at raw `anim_frame` offset 6 only after mapping 0's
  `$1F` delay loads; vertical TechnoSqueek raw FlipY commands mutate live render
  bit 1 at movement offset 6 and turn steps 3/7 without changing placement X
  flip, with `89B24` consuming those live axes; and both placed parents suppress
  rendering in the wait-offscreen and restored-code dispatches until their
  initialization update. The same pass removes per-animation-update array
  allocation from Blaster projectiles and the TechnoSqueek attachment. Focused
  parent/graph coverage passes 28/28. No trace or commit ran.
- Task 11 oracle RED corrected the Act 1 miniboss plan before implementation.
  The placed S3KL `$AA` occurs once at `$2F00,$05E0` and therefore reduces
  completeness by exactly one; SKL `$AA` remains Hyudoro. `ChildObjDat_6FA76`
  creates seven initial children, then each of the two arm controllers makes
  its own five-link `word_6FAA2` chain. The full persistent graph is therefore
  18 slots including the boss, temporarily 19 with the attack palette child,
  and the arm/chain endpoint links are cyclic. Every after-current table stops
  at its first failure and preserves a partial prefix without retry.
  The boss owns its camera activation and plunger start, uses the closest native
  pair only for its aimer but P1 only for outward lunge targeting, and takes six
  scripted self-damage cycles rather than direct player hits. The locked-on art
  path is direct Kosinski-Moduled art plus the shared boss-explosion PLC; S3 PLC
  `$5E` is unused. Defeat preserves its independent explosion/helper/animal/
  capsule-fragment allocations and per-routine lifetimes. The boss itself never
  publishes `Events_fg_5`: the later level-results flow publishes it and Task 12
  consumes it for the seamless background transition. The corrected tests now
  require exact palette/audio/art shapes, allocation-prefix and cyclic-link
  rewind, restart cleanup, multi-sidekick participation without retargeting,
  and widescreen world-coordinate camera locking. This was documentation-only;
  no production code, tests, trace, or commit ran.
- Residual Task 11 oracle review corrected native plunger authority and exact
  wait timing. Only plunger status bit 3 (P1 standing) sets the root start bit;
  P2 and additional sidekicks still ride/collide but cannot start the fight.
  `Obj_Wait` predecrements its word and invokes the callback only when negative,
  so `$78` reaches miniboss music on wait update 121 and the cover `$20/$20/$40`
  phases consume 33/33/65 wait updates. The distinct `$1F` phases consume 32.
  The art gate now spells the exact 18-frame piece-count vector as
  `4,1,1,2,2,2,2,4,6,6,6,6,6,6,6,6,6,2`. This remained documentation-only;
  no production code, tests, trace, or commit ran.
- Task 11 implementation ports the locked-on `$AA` Act 1 miniboss as the full
  18-slot persistent object graph: root, three covers, P1-authority plunger,
  native-pair aimer, two arm controllers, and two independent cyclic five-link
  chains. The 11 arm and 16 link callbacks preserve setup-only dispatch,
  asymmetric waits, unsigned byte-angle endpoints, native circular axes,
  phase-dependent priorities, one-shot attack palette requests, scripted
  terminal self-damage, and move/gravity/flicker defeat behavior. The root owns
  exact vertical/horizontal arena convergence, lock-time max-X storage, boss
  flags, music waits, six-hit flash cadence, conversion to a non-solid end-sign
  controller, independent fade/prison/animal/fragment allocations, after-current
  sign creation, and the later results/end-flag handoff without publishing
  `Events_fg_5` itself. Parent-free rewind shells restore scalars before a
  family-slot relinker rebuilds only captured contiguous prefixes and closes a
  cycle only at a real terminal link. Focused core/child/defeat/rewind/art/
  registry tests and the real ObjectManager route cover palette, allocation,
  camera, sign/results, restart, multi-sidekicks, widescreen, and donation-neutral
  standing activation. No complete-run trace was run; trace polish remains the
  final zone phase after broad FBZ implementation.
- Task 11 final verification is green across all 59 focused checks: 31 FBZ
  miniboss, real-route, child, defeat, rewind, art, and registry assertions;
  seven actual results/signpost checks; five object-profile checks; and 16
  static-state, service-migration, and renderer guard checks. The real route
  uses `ObjectManager` contact and the six terminal arm/link impacts, then
  observes the independently allocated defeat family and actual `S3kSignpost`
  through results clear and the act-end flag. It includes executable restart,
  arbitrary-sidekick, widescreen-camera, and donation-neutral coverage. The
  aggregate shared-worktree guard run was 151/155; its four failures belonged
  to concurrent AIZ/ObjectManager/other-FBZ-family changes, and no Task 11
  class remained in a violation. `git diff --check` was clean. Trace remained
  intentionally deferred to final broad-zone polish.
- Task 11 acceptance recovery corrected the Act 1 capsule fragments to use a
  dedicated, Act-1-only level-art key backed by generic `Map_EggCapsule` at
  `ArtTile_EggCapsule-$46`; the act-2 standalone capsule sheet and the distinct
  placed-FBZ-capsule mapping are no longer used. Render coverage observes the
  exact `2,3,$A,4,$B` frames and the obsolete per-fragment art-tile scalar was
  removed. Allocation coverage now exhausts the real S3K object pool at every
  ordinal of the seven-entry initial table and both independent five-link arm
  tables. All 40 partial-prefix cases capture, corrupt or remove, and restore in
  both in-place and forced-reconstruction modes, asserting stable SST slots,
  no retry after capacity returns, no missing-ordinal healing, and cycle closure
  only at link four. The focused art/defeat/rewind matrix passed 56/56. No trace
  or commit ran.

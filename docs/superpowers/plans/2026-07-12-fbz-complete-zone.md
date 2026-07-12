# Flying Battery Zone Complete Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to execute this plan task-by-task. Every implementation task also uses the named S3K specialist skill, `superpowers:test-driven-development`, and two fresh review passes (spec compliance, then code quality). Fix and re-review until both are GREEN before advancing.

**Goal:** Implement locked-on Sonic 3 & Knuckles Flying Battery Zone Acts 1 and 2 completely and pixel-accurately, then prove native parity, late complete-run trace parity, multi-sidekick safety, widescreen safety, and cross-game donation compatibility.

**Architecture:** `Sonic3kFBZEvents` is the canonical mutable event owner, exposed through an event-backed `FbzZoneRuntimeState`. Objects communicate through an FBZ event bridge; render, scroll, collision, animation, and palette systems consume the typed state through runtime-owned registries. Route slices are delivered in dependency order. Trace capture/replay is comparison-only and intentionally deferred until implementation and focused validation are substantially complete.

**Tech Stack:** Java 21, Maven, JUnit 5, S3K locked-on ROM data, `docs/skdisasm/sonic3k.asm`, runtime-owned zone frameworks, BizHawk 2.11, BK2 trace recorder/replay tooling.

---

## Execution contract

- Execute in an isolated worktree created with `superpowers:using-git-worktrees` from `feature/ai-fbz-complete`. Preserve the unrelated root-workspace edits listed in the design session.
- Before each object/badnik task, run the `s3k-implement-object` preflight. Before each boss task, run `s3k-implement-boss`. Use `s3k-plc-system`, `s3k-parallax`, `s3k-animated-tiles`, `s3k-palette-cycling`, and `s3k-zone-events` for their respective tasks.
- For every behavior: add a focused test, run it and record the expected RED result in `docs/superpowers/research/2026-07-12-fbz-red-green-log.md`, implement the minimum disassembly-backed behavior, run GREEN, refactor, run relevant regressions, then request spec and quality reviews from fresh agents. A task is not complete while either review has a blocker or important finding.
- Use S&K-side (`sonic3k.asm`, address `< 0x200000`) constants by default. A Sonic 3-half address requires an explicit verified no-S&K-equivalent note.
- Never add zone/route/frame trace carve-outs, trace-state hydration, raw game-name branches, direct gameplay map writes, object singleton access, or silent placeholder fallbacks.
- After every route wave, run the focused FBZ suite, the affected shared regressions, both rewind guards, and `mvn package`.
- Preserve the existing `LevelFrameStep` order: player/object physics; camera move/clamp; post-camera fixed objects and `Sonic3kFBZEvents.update()`; mutation flush; boundary easing; post-camera placement sync; remaining level systems; deformation/animation/palette/render registries; render; rewind capture. Do not reorder the generic pipeline for FBZ. A phase exception requires a cited ROM call site and an existing pre-physics, fixed-slot, or camera-driven hook.

## Source contracts

- Approved architecture: `docs/superpowers/specs/2026-07-12-fbz-complete-zone-design.md`
- Zone catalogue: `docs/s3k-zones/fbz-analysis.md`
- Counted inventory: `docs/s3k-zones/fbz-object-inventory.md`
- Authority: `docs/skdisasm/sonic3k.asm`, `docs/skdisasm/Levels/FBZ/`, and `docs/skdisasm/Levels/Misc/Object pointers - SK Set 1.asm`
- Inventory convention: the raw 421/441 records include one `$FFFF` terminator per act. Runtime placements are 420 + 440 = 860. All tests and completion ratios use runtime placements while retaining raw-record assertions.

## Mandatory route-wave gate

Run this after Tasks 4, 11, 12, 14, and 17, in addition to each task's focused command. The named task is not GREEN until this gate passes.

```powershell
$guards = @(
  'com.openggf.game.rewind.coverage.TestRewindCoverageGuard',
  'com.openggf.game.rewind.coverage.TestStaticStateRewindCoverageGuard',
  'com.openggf.game.mutation.TestNoDirectMapMutationsInGameplay',
  'com.openggf.game.TestZoneEventRuntimeAccessGuard',
  'com.openggf.game.sonic3k.TestS3kRuntimeStateReadGuard',
  'com.openggf.game.sonic3k.TestS3kTransitionBridgeGuard',
  'com.openggf.level.objects.TestObjectServicesMigrationGuard',
  'com.openggf.level.objects.TestNoServicesInObjectConstructors',
  'com.openggf.tests.TestNoServicesInObjectConstructors',
  'com.openggf.tests.TestTraceReplayInvariantGuard'
) -join ','
mvn "-Dtest=$guards" test "-Ds3k.rom.path=s3k.gen"
mvn package "-Ds3k.rom.path=s3k.gen"
```

### Task 1: Freeze the FBZ evidence and completeness gates

**Skills:** `s3k-zone-analysis`, `s3k-disasm-guide`, `s3k-implement-object`

**Files:**

- Verify/update: `docs/s3k-zones/fbz-object-inventory.md`
- Create: `src/test/java/com/openggf/game/sonic3k/objects/TestFbzObjectInventory.java`
- Create: `src/test/java/com/openggf/game/sonic3k/objects/TestFbzObjectRegistryCompleteness.java`
- Create: `docs/superpowers/research/2026-07-12-fbz-red-green-log.md`
- Create: `docs/superpowers/research/2026-07-12-fbz-trace-baseline.json`
- Create: `docs/s3k-zones/fbz-visual-checkpoints.json`
- Modify: `src/main/java/com/openggf/tools/Sonic3kObjectProfile.java`

**Steps:**

1. Add RED tests that decode both placement binaries and assert 421/441 raw records, one terminator per act, 420/440 runtime spawns, every ID/subtype count in the inventory, and 535 current placeholder placements.
2. Add a registry-completeness test whose allowlist is derived from the checked inventory. It must identify missing S3KL factories without accepting SKL remaps for `$A8/$A9`.
3. Extend the inventory with the audio-cue and VRAM/PLC handoff matrices from the design. Resolve every mapping/art/animation/PLC label used by later tasks with `RomOffsetFinder --game s3k`; record the verified side/address.
4. Without executing trace replay, freeze the last persisted result for every known-red trace as the lexicographic tuple `(firstErrorFrame, errorCount, warningCount)` and separately enumerate every currently green complete-run regression class in a `green_test_classes` JSON array. Record provenance and `unknown/not previously run` explicitly where no persisted result exists. Do not capture or replay the FBZ complete-run segment before Task 20.
5. GREEN the inventory tests without weakening existing profile guards. Commit the gate before object implementations begin.
6. Define immutable native visual checkpoints now, before render work: exact world coordinates/reference frames and assertions for act starts, every indoor/outdoor boundary, AniPLC/palette/parallax samples, bosses, plane transition, exit, and capsule. Task 19 performs the captures and records results; later implementations may not move checkpoints to fit their output.

**Verify:**

```powershell
mvn "-Dtest=TestFbzObjectInventory,TestFbzObjectRegistryCompleteness" test "-Ds3k.rom.path=s3k.gen"
```

### Task 2: Introduce the canonical background-plane collision provider

**Skills:** `superpowers:test-driven-development`, `superpowers:systematic-debugging`

**Files:**

- Create: `src/main/java/com/openggf/physics/BackgroundPlaneCollisionProvider.java`
- Create: `src/main/java/com/openggf/physics/DefaultBackgroundPlaneCollisionProvider.java`
- Modify: `src/main/java/com/openggf/game/zone/ZoneRuntimeState.java`
- Modify: `src/main/java/com/openggf/game/session/GameplayModeContext.java`
- Modify: `src/main/java/com/openggf/game/session/GameplaySessionFactory.java`
- Modify: `src/main/java/com/openggf/game/GameServices.java`
- Modify: `src/main/java/com/openggf/physics/GroundSensor.java`
- Modify: `src/main/java/com/openggf/physics/CollisionSystem.java`
- Modify: `src/main/java/com/openggf/level/rings/RingManager.java`
- Modify: `src/main/java/com/openggf/level/rings/LostRingObjectInstance.java`
- Create: `src/test/java/com/openggf/physics/TestBackgroundPlaneCollisionProvider.java`
- Create: `src/test/java/com/openggf/physics/TestFbzBackgroundPlaneCollision.java`
- Create: `src/test/java/com/openggf/physics/TestFbzCalcRoomInFrontBackgroundCollision.java`
- Create: `src/test/java/com/openggf/level/rings/TestFbzRingBackgroundCollision.java`

**Steps:**

1. Add RED provider tests for inactive foreground-only probes and active dual-layer floor/wall/ceiling/ring probes using ROM `Camera_X_diff`/`Camera_Y_diff`, including `GroundSensor.scanWorld` and `CalcRoomInFront`.
2. Make `GameplayModeContext` own one provider. Its default adapter translates the existing `GameStateManager.backgroundCollisionFlag` and active parallax camera differences so HCZ/MGZ/CNZ behavior is unchanged.
3. Add an optional explicit semantic state to `ZoneRuntimeState`; FBZ will later publish collision mode and camera differences through it.
4. Route every relevant terrain and ring probe through the provider. Select the nearer valid result with ROM signed-distance semantics and restore caller world coordinates.
5. Run HCZ/MGZ/CNZ focused regressions before accepting the shared migration.

**Verify:**

```powershell
mvn "-Dtest=TestBackgroundPlaneCollisionProvider,TestFbzBackgroundPlaneCollision,TestFbzCalcRoomInFrontBackgroundCollision,TestFbzRingBackgroundCollision,TestS3kHcz2RaisedFloorWallCollisionHeadless,TestS3kCnzMinibossArenaHeadless,TestS3kMgz2BgRiseHeadless,TestS3kCnzBossScrollHandler" test "-Ds3k.rom.path=s3k.gen"
```

### Task 3: Add event-backed FBZ runtime state and registration

**Skills:** `s3k-zone-events`, `superpowers:test-driven-development`

**Files:**

- Create: `src/main/java/com/openggf/game/sonic3k/events/Sonic3kFBZEvents.java`
- Create: `src/main/java/com/openggf/game/sonic3k/events/FbzObjectEventBridge.java`
- Create: `src/main/java/com/openggf/game/sonic3k/events/S3kFbzEventWriteSupport.java`
- Create: `src/main/java/com/openggf/game/sonic3k/runtime/FbzZoneRuntimeState.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kLevelEventManager.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/runtime/S3kRuntimeStates.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/constants/Sonic3kConstants.java`
- Create: `src/test/java/com/openggf/game/sonic3k/TestFbzZoneRuntimeState.java`
- Create: `src/test/java/com/openggf/game/sonic3k/TestFbzRuntimeStateRegistration.java`
- Create: `src/test/java/com/openggf/game/sonic3k/events/TestFbzEventWriteSupport.java`
- Create: `src/test/java/com/openggf/game/sonic3k/events/TestFbzEventRewindRoundTrip.java`
- Create: `src/test/java/com/openggf/game/sonic3k/events/TestFbzFramePhaseOrdering.java`

**Steps:**

1. RED-test handler-backed state installation, `isBackedBy`, bridge write routing, invalid stages/modes, and capture/restore/capture byte equality.
2. Model layout-region state, indoor/outdoor flags, redraw state/direction, bob offset, magnetic phase/polarity, Act 2 foreground/background stages and offsets, boss-load flag, plane/collision mode, shake inputs, and ten stable cloud rewind IDs.
3. Serialize authoritative FBZ handler fields only through `FbzZoneRuntimeState.captureBytes()/restoreBytes()`. Exclude those fields from `Sonic3kLevelEventManager`'s event sidecar; after zone-runtime restore, the manager only rebinds/reconciles the current handler and object IDs. RED-test that the same fields are not captured or restored twice.
4. Construct/install/reconcile the handler and adapter during level load, death, checkpoint, seamless reload, and rewind restoration. Do not register `ZoneRuntimeRegistry` a second time.
5. Keep event methods as the sole state writers; objects mutate through the bridge and consumers read through `FbzZoneRuntimeState`.
6. Pin and test the established `LevelFrameStep` phases. FBZ event state written post-camera affects the mutation flush/boundary phases that follow; polarity/object writes run in their proven object/fixed-slot phase; collision-mode writes become visible on the next player collision pass. Reject any implementation that moves the shared event pipeline.

**Verify:**

```powershell
mvn "-Dtest=TestFbzZoneRuntimeState,TestFbzRuntimeStateRegistration,TestFbzEventWriteSupport,TestFbzEventRewindRoundTrip,TestFbzFramePhaseOrdering,TestS3kZoneRuntimeStateAdapters,TestSonic3kLevelEventRewindSnapshot" test "-Ds3k.rom.path=s3k.gen"
```

### Task 4: Implement FBZ deform, plane rendering, AniPLC, polarity, palette, and art foundations

**Skills:** `s3k-parallax`, `s3k-animated-tiles`, `s3k-palette-cycling`, `s3k-plc-system`

**Files:**

- Create: `src/main/java/com/openggf/game/sonic3k/scroll/SwScrlFbz.java`
- Create: `src/main/java/com/openggf/game/sonic3k/render/FbzBossPlaneRenderMode.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/scroll/Sonic3kScrollHandlerProvider.java`
- Modify: `src/main/java/com/openggf/game/render/AdvancedRenderFrameState.java`
- Modify: `src/main/java/com/openggf/level/LevelRenderer.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kZoneFeatureProvider.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kPatternAnimator.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/S3kAnimatedTileChannels.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kPaletteCycler.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kObjectArtKeys.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kObjectArt.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kObjectArtProvider.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kPlcArtRegistry.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kPlcLoader.java`
- Create: `src/test/java/com/openggf/game/sonic3k/TestFbzScrollHandler.java`
- Create: `src/test/java/com/openggf/game/sonic3k/TestFbzBossCloudDeform.java`
- Create: `src/test/java/com/openggf/game/sonic3k/render/TestFbzBossPlaneRenderMode.java`
- Create: `src/test/java/com/openggf/game/sonic3k/TestFbzAnimatedTiles.java`
- Create: `src/test/java/com/openggf/game/sonic3k/TestFbzMagneticPolarity.java`
- Create: `src/test/java/com/openggf/game/sonic3k/TestFbzPlcArtHandoffs.java`

**Steps:**

1. RED-test exact representative H/V-scroll words for indoor 34-band scatter fill, outdoor eight-band `$0E00` drift/bob, and `FBZ2_CloudDeform` `$8000` drift/offset/shake paths from `FBZ_Deform`, its arrays/index tables, and `FBZCloud_PositionFrameData`.
2. Implement `SwScrlFbz` with `ScrollEffectComposer`, `DeformationPlan`, and `ScatterFillPlan`; register it by provider so reloads reconstruct it.
3. Add generic advanced-render state for Plane A/B assignment and independent foreground/background V-scroll overrides. Implement FBZ plane reversal as a typed-state contributor; keep collision and rendering as separate consumers of the same event mode.
4. Register all five Act 1 and Act 2 AniPLC channels at `$200-$247`. Assert script 3 is the first/live writer of `$200-$207`, while FBZ spikes only reference those tile IDs.
5. Dispatch `AnPal_FBZ`'s 256-frame magnetic phase into event-owned state. Do not invent color cycling. Implement event palette data/ownership for line 4 colors 2-9 and boss setup color 1.
6. Register verified gimmick, miniboss, subboss, cloud, pillar, end-boss, exit, and capsule art/PLC entries before any consumer task is enabled.
7. Run the mandatory route-wave gate. This is the **foundation wave boundary**.

**Verify:**

```powershell
mvn "-Dtest=TestFbzScrollHandler,TestFbzBossCloudDeform,TestFbzBossPlaneRenderMode,TestFbzAnimatedTiles,TestFbzMagneticPolarity,TestFbzPlcArtHandoffs,TestSonic3kPatternAnimatorRewindSnapshot,TestSonic3kPlcArtRewindSnapshot" test "-Ds3k.rom.path=s3k.gen"
```

### Task 5: Port Act 1 screen/background events and staged mutations

**Skills:** `s3k-zone-events`, `s3k-implement-object`

**Files:**

- Modify: `src/main/java/com/openggf/game/sonic3k/events/Sonic3kFBZEvents.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/runtime/FbzZoneRuntimeState.java`
- Create: `src/main/java/com/openggf/game/sonic3k/objects/FbzOutdoorBgMotionObjectInstance.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/Sonic3kObjectRegistry.java`
- Create: `src/test/java/com/openggf/game/sonic3k/events/TestFbzEventsAct1.java`
- Create: `src/test/java/com/openggf/game/sonic3k/events/TestFbzAct1LayoutMutations.java`
- Create: `src/test/java/com/openggf/game/sonic3k/objects/TestFbzOutdoorBgMotion.java`

**Steps:**

1. RED-test `FBZ1_ScreenInit`, all six `FBZ1_ScreenEvent` layout ranges, `FBZ1_BackgroundInit`, all background redraw directions/stages, palette patch timing, death gating, and deform-mode changes.
2. Port the exact threshold and copy dimensions from `FBZ1_LayoutModRange`. Submit gameplay writes through `ZoneLayoutMutationPipeline`; never mutate `Map` directly.
3. Resolve the palette ownership patch during the staged redraw and reapply it during state reconciliation.
4. Implement `Obj_FBZOutdoorBGMotion` as the ordinary dynamically allocated object it is in the ROM: execute it in the established dynamic-object slot phase before camera/events, publish `Events_bg+$08` through the bridge, and assert that the same frame's later deformation phase consumes the new bob value. Do not move it after post-camera placement sync or add an FBZ-specific generic-loop reorder.

**Verify:**

```powershell
mvn "-Dtest=TestFbzEventsAct1,TestFbzAct1LayoutMutations,TestFbzOutdoorBgMotion,TestNoDirectMapMutationsInGameplay" test "-Ds3k.rom.path=s3k.gen"
```

### Task 6: Validate shared FBZ placements and add the two missing shared factories

**Skills:** `s3k-implement-object`

**Files:**

- Modify only as required: existing shared object classes for IDs `$00,$01,$02,$07,$08,$0F,$26,$28,$2A,$2F,$33,$34,$3D,$6A,$6B,$80,$85`
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/Sonic3kObjectRegistry.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kPlcArtRegistry.java`
- Create: `src/test/java/com/openggf/game/sonic3k/objects/TestFbzSharedObjectSubtypes.java`

**Steps:**

1. RED-test every shared subtype listed in the counted inventory, including FBZ art/collision configuration and remembered-placement behavior.
2. Add correct S3KL factories/behavior for placed Ring `$00` and Retracting Spring `$3D`; do not treat the terminator records as rings.
3. Correct only proven subtype gaps. Preserve all non-FBZ shared behavior through data/profile configuration rather than zone checks in common physics.
4. Ratchet the completeness test from 535 to 533 placeholder placements.

**Verify:**

```powershell
mvn "-Dtest=TestFbzSharedObjectSubtypes,TestFbzObjectInventory,TestFbzObjectRegistryCompleteness" test "-Ds3k.rom.path=s3k.gen"
```

### Task 7: Implement route-critical carrier and transport families

**Skills:** `s3k-implement-object`

**Families/files:** create `FbzWireCageObjectInstance`, `FbzWireCageStationaryObjectInstance`, `FbzFloatingPlatformObjectInstance`, `FbzChainLinkObjectInstance`, `FbzSnakePlatformObjectInstance`, `FbzBentPipeObjectInstance`, `FbzRotatingPlatformObjectInstance`, and `FbzDezPlayerLauncherObjectInstance` under `src/main/java/com/openggf/game/sonic3k/objects/`; modify `src/main/java/com/openggf/game/sonic3k/constants/Sonic3kObjectIds.java`, `Sonic3kConstants.java`, `src/main/java/com/openggf/game/sonic3k/objects/Sonic3kObjectRegistry.java`, `src/main/java/com/openggf/game/sonic3k/Sonic3kObjectArtKeys.java`, `Sonic3kObjectArt.java`, `Sonic3kObjectArtProvider.java`, `Sonic3kPlcArtRegistry.java`, and `src/main/java/com/openggf/tools/Sonic3kObjectProfile.java`; create `TestFbzWireCages`, `TestFbzRailAndChainPlatforms`, `TestFbzSnakeAndRotatingPlatforms`, and `TestFbzPlayerTransportObjects`.

**Steps:**

1. For `$6F-$72,$75-$78`, decode every used subtype and child-allocation primitive from the inventory and `sonic3k.asm`; record preflight results in the RED/GREEN log.
2. RED-test fixed-point motion, exact trig/table steps, solid/riding state, grab/release/forced-control bits, player positioning through `NativePositionOps`, allocation order, offscreen lifetime, and all used subtype branches.
3. Implement with `ObjectServices`, `ObjectControlState`, explicit participation policies, `ObjectLifetimeOps`, and `spawnChild`/slot-aware replacement.
4. Test more than two eligible characters at shared solids/carriers now; native parity remains the acceptance authority.
5. Ratchet the completeness count for only these implemented inventory rows.

**Verify:**

```powershell
mvn "-Dtest=TestFbzWireCages,TestFbzRailAndChainPlatforms,TestFbzSnakeAndRotatingPlatforms,TestFbzPlayerTransportObjects,TestFbzObjectRegistryCompleteness" test "-Ds3k.rom.path=s3k.gen"
```

### Task 8: Implement mechanical platforms, doors, launchers, and missiles

**Skills:** `s3k-implement-object`

**Families/files:** create `FbzDisappearingPlatformObjectInstance`, `FbzScrewDoorObjectInstance`, `FbzSpinningPoleObjectInstance`, `FbzPropellerObjectInstance`, `FbzPistonObjectInstance`, `FbzPlatformBlocksObjectInstance`, `FbzMissileLauncherObjectInstance`, and `FbzWallMissileObjectInstance`, with concrete missile/companion child classes; modify `src/main/java/com/openggf/game/sonic3k/constants/Sonic3kObjectIds.java`, `Sonic3kConstants.java`, `src/main/java/com/openggf/game/sonic3k/objects/Sonic3kObjectRegistry.java`, `src/main/java/com/openggf/game/sonic3k/Sonic3kObjectArtKeys.java`, `Sonic3kObjectArt.java`, `Sonic3kObjectArtProvider.java`, `Sonic3kPlcArtRegistry.java`, and `src/main/java/com/openggf/tools/Sonic3kObjectProfile.java`; create `TestFbzDisappearingPlatformAndScrewDoor`, `TestFbzPolePropellerPistonAndBlocks`, and `TestFbzMissileObjects`.

**Steps:**

1. Cover `$79-$7F,$E0` and every used subtype from the inventory in RED tests.
2. Port animation timers, solidity windows, button/event dependencies, collision/hurt timing, missile cadence/trajectory, launch companion allocation, child-to-explosion replacement, and respawn semantics.
3. Verify mapping/animation addresses and exact art tile/palette/priority values before enabling renderers.
4. Ratchet the completeness gate and run the Act 1 event/transport suite.

**Verify:**

```powershell
mvn "-Dtest=TestFbzDisappearingPlatformAndScrewDoor,TestFbzPolePropellerPistonAndBlocks,TestFbzMissileObjects,TestFbzEventsAct1,TestFbzObjectRegistryCompleteness" test "-Ds3k.rom.path=s3k.gen"
```

### Task 9: Implement magnetic and environmental hazard families

**Skills:** `s3k-implement-object`, `s3k-palette-cycling`

**Families/files:** create `FbzMagneticSpikeBallObjectInstance`, `FbzMagneticPlatformObjectInstance`, `FbzMineObjectInstance`, `FbzTrapSpringObjectInstance`, `FbzFlamethrowerObjectInstance`, `FbzSpiderCraneObjectInstance`, and `FbzMagneticPendulumObjectInstance`, plus real child classes; modify `src/main/java/com/openggf/game/sonic3k/constants/Sonic3kObjectIds.java`, `Sonic3kConstants.java`, `src/main/java/com/openggf/game/sonic3k/objects/Sonic3kObjectRegistry.java`, `src/main/java/com/openggf/game/sonic3k/Sonic3kObjectArtKeys.java`, `Sonic3kObjectArt.java`, `Sonic3kObjectArtProvider.java`, `Sonic3kPlcArtRegistry.java`, and `src/main/java/com/openggf/tools/Sonic3kObjectProfile.java`; create `TestFbzMagneticObjects`, `TestFbzMine`, `TestFbzTrapSpring`, `TestFbzFlamethrower`, and `TestFbzSpiderCraneAndPendulum`.

**Steps:**

1. RED-test `$73,$74,$E1,$E3-$E5,$FF` across every used subtype, the exact 256-frame polarity edge, field/chain construction, damage cadence, claw/grab policies, and child allocation/deletion.
2. Read polarity only from `FbzZoneRuntimeState`; objects do not own competing timers.
3. Test death/restart/rewind around the polarity edge and three-or-more-character contact with hazards/grabbers.
4. Ratchet completeness and rerun animation/palette/rewind coverage.

**Verify:**

```powershell
mvn "-Dtest=TestFbzMagneticObjects,TestFbzMine,TestFbzTrapSpring,TestFbzFlamethrower,TestFbzSpiderCraneAndPendulum,TestFbzMagneticPolarity,TestRewindCoverageGuard" test "-Ds3k.rom.path=s3k.gen"
```

### Task 10: Implement Blaster and Technosqueek

**Skills:** `s3k-implement-object`

**Files:**

- Create: `src/main/java/com/openggf/game/sonic3k/objects/badniks/BlasterBadnikInstance.java`
- Create: `src/main/java/com/openggf/game/sonic3k/objects/badniks/TechnoSqueekBadnikInstance.java`
- Create concrete projectile/attached child classes from `ChildObjDat_89726`, `8972E`, `89746`, and `89B24`
- Modify: `src/main/java/com/openggf/game/sonic3k/constants/Sonic3kObjectIds.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/constants/Sonic3kConstants.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/Sonic3kObjectRegistry.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kObjectArtKeys.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kObjectArt.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kObjectArtProvider.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kPlcArtRegistry.java`
- Modify: `src/main/java/com/openggf/tools/Sonic3kObjectProfile.java`
- Create: `src/test/java/com/openggf/game/sonic3k/objects/badniks/TestFbzBlaster.java`
- Create: `src/test/java/com/openggf/game/sonic3k/objects/badniks/TestFbzTechnoSqueek.java`

**Steps:**

1. RED-test all placed `$A8` subtypes `08,20,30` and `$A9` subtypes `00,02,04`, explicitly resolving the S3KL names instead of the existing SKL remaps.
2. Port patrol/attach/fire cadence, child allocation, projectile physics, continuous ENEMY touch polling, destruction/animal/score, and offscreen respawn behavior.
3. Register S3KL factories without regressing the MHZ SKL implementations.

**Verify:**

```powershell
mvn "-Dtest=TestFbzBlaster,TestFbzTechnoSqueek,TestFbzObjectRegistryCompleteness,TestObjectServicesMigrationGuard" test "-Ds3k.rom.path=s3k.gen"
```

### Task 11: Implement the Act 1 miniboss graph

**Skills:** `s3k-implement-boss`, `s3k-plc-system`

**Files:** create `FbzMinibossInstance` and all separate-slot children/controllers represented by `ChildObjDat_6FA76`, nested repeated link table `word_6FAA2` (five `loc_6F3DE` objects), `6FAA8`, `6FAB0`, `89ED0`, and `86B7A` under the boss/object packages; add `FbzMinibossRewindLinks`; modify `src/main/java/com/openggf/game/sonic3k/constants/Sonic3kObjectIds.java`, `Sonic3kConstants.java`, `src/main/java/com/openggf/game/sonic3k/objects/Sonic3kObjectRegistry.java`, `src/main/java/com/openggf/game/sonic3k/Sonic3kObjectArtKeys.java`, `Sonic3kObjectArt.java`, `Sonic3kObjectArtProvider.java`, `Sonic3kPlcArtRegistry.java`, and `src/main/java/com/openggf/tools/Sonic3kObjectProfile.java`; create `TestFbzAct1Miniboss`, `TestFbzMinibossChildren`, `TestFbzMinibossRewind`, and `src/test/java/com/openggf/tests/TestFbzAct1RouteHeadless.java`.

**Steps:**

1. RED-test arena activation, exact slot/allocation order, the nested `word_6FAA2` five-object creation, total child count/linkage, attacks, hit/invulnerability timing, shield reactions, palette/music/SFX edges, defeat explosions, and `Events_fg_5` publication.
2. Port `Obj_FBZMiniboss` and its full reachable call graph from `sonic3k.asm`, including concrete mappings/art/palette/PLC data.
3. Recreate/relink children deterministically after rewind and checkpoint restart.
4. Prove the Act 1 route reaches and defeats the boss without placeholders or debug movement.
5. Run the mandatory route-wave gate. This is the **Act 1 traversal/boss wave boundary**.

**Verify:**

```powershell
mvn "-Dtest=TestFbzAct1Miniboss,TestFbzMinibossChildren,TestFbzMinibossRewind,TestFbzAct1RouteHeadless,TestFbzPlcArtHandoffs" test "-Ds3k.rom.path=s3k.gen"
```

### Task 12: Implement the seamless Act 1-to-Act 2 transition

**Skills:** `s3k-zone-events`, `superpowers:test-driven-development`

**Files:**

- Modify: `src/main/java/com/openggf/level/SeamlessLevelTransitionRequest.java`
- Modify: `src/main/java/com/openggf/level/LevelActTransitionExecutor.java`
- Modify: `src/main/java/com/openggf/level/LevelTransitionCoordinator.java`
- Modify: `src/main/java/com/openggf/level/LevelManager.java`
- Modify: `src/main/java/com/openggf/level/objects/ObjectManager.java`
- Modify: `src/main/java/com/openggf/level/objects/ObjectInstance.java`
- Modify: `src/main/java/com/openggf/level/objects/AbstractObjectInstance.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/events/Sonic3kFBZEvents.java`
- Create: `src/test/java/com/openggf/tests/TestFbzActTransitionHeadless.java`
- Create: `src/test/java/com/openggf/level/objects/TestFbzRomWorldSlotCarryPolicy.java`
- Create: `src/test/java/com/openggf/game/sonic3k/events/TestFbzTransitionRewind.java`

**Steps:**

1. RED-test `RELOAD_TARGET_LEVEL`, target FBZ2, no title card, no music restart, `preserveLevelGamestate=false`, `preserveRespawnState=false`, `preserveOffsetCameraPosition=true`, player and camera offsets `(-$2E00,0)`, ring/placement reload, and delayed transition deactivation until the current `FBZ1_BackgroundEvent` tail completes.
2. Add request-scoped `ROM_WORLD_SLOT_RANGE` with explicit start-inclusive/end-exclusive ROM slot bounds; preserve `PERSISTENT_ONLY` as the default.
3. Generalize level-space offset participation and add centre-coordinate/subpixel-preserving offset application. Test excluded dynamic slot zero, first included slot, last included fixed slot, excluded `Breathing_bubbles` boundary, render-flag bit 2, ordinary nonpersistent objects, in-range slot-backed `BossChildComponent` inclusion, and composite/non-slot children carried only with their owner.
4. Snapshot eligible objects before manager rebuild, restore them in original slot order, apply the offset exactly once, then run per-object anchor hooks.
5. Verify restart and rewind on both sides of the transition.
6. Run the mandatory route-wave gate. This is the **Act 1-to-Act 2 transition wave boundary**.

**Verify:**

```powershell
mvn "-Dtest=TestFbzActTransitionHeadless,TestFbzRomWorldSlotCarryPolicy,TestFbzTransitionRewind,TestActTransitionHeadless,TestSeamlessCarryExcludesBossChildren" test "-Ds3k.rom.path=s3k.gen"
```

### Task 13: Port Act 2 events and traversal, including the elevator

**Skills:** `s3k-zone-events`, `s3k-implement-object`

**Files:** modify `src/main/java/com/openggf/game/sonic3k/events/Sonic3kFBZEvents.java`, `src/main/java/com/openggf/game/sonic3k/runtime/FbzZoneRuntimeState.java`, `src/main/java/com/openggf/game/sonic3k/constants/Sonic3kObjectIds.java`, `Sonic3kConstants.java`, `src/main/java/com/openggf/game/sonic3k/objects/Sonic3kObjectRegistry.java`, `src/main/java/com/openggf/game/sonic3k/Sonic3kObjectArtKeys.java`, `Sonic3kObjectArt.java`, `Sonic3kObjectArtProvider.java`, `Sonic3kPlcArtRegistry.java`, and `src/main/java/com/openggf/tools/Sonic3kObjectProfile.java`; create `FbzElevatorObjectInstance` plus car children; create `TestFbzEventsAct2`, `TestFbzElevator`, and `TestFbzAct2TraversalPreboss`.

**Steps:**

1. RED-test `FBZ2_ScreenInit`, the ordinary Act 2 foreground region, `FBZ2_BackgroundInit/Event`, stage progression before `$2B30`, and checkpoint/death restoration.
2. Implement `$E2` subtypes `0F,1E,24,25,32,37,3B,4B`, including periodic car allocation, movement, solidity, culling, and rewind ownership.
3. Re-run every shared/mechanical/magnetic/badnik family used in Act 2 and prove the route reaches the subboss naturally.

**Verify:**

```powershell
mvn "-Dtest=TestFbzEventsAct2,TestFbzElevator,TestFbzAct2TraversalPreboss,TestFbzObjectRegistryCompleteness" test "-Ds3k.rom.path=s3k.gen"
```

### Task 14: Implement the Act 2 subboss and PLC restoration

**Skills:** `s3k-implement-boss`, `s3k-plc-system`

**Files:** create `Fbz2SubbossInstance` and all children from `ChildObjDat_703C8`, `703D0`, `703DE`, `703E4`, and `703EC`; add rewind links; modify `src/main/java/com/openggf/game/sonic3k/constants/Sonic3kObjectIds.java`, `Sonic3kConstants.java`, `src/main/java/com/openggf/game/sonic3k/objects/Sonic3kObjectRegistry.java`, `src/main/java/com/openggf/game/sonic3k/Sonic3kObjectArtKeys.java`, `Sonic3kObjectArt.java`, `Sonic3kObjectArtProvider.java`, `Sonic3kPlcArtRegistry.java`, `Sonic3kPlcLoader.java`, and `src/main/java/com/openggf/tools/Sonic3kObjectProfile.java`; create `TestFbzAct2Subboss`, `TestFbz2SubbossCharacterArt`, `TestFbz2SubbossArtHandoff`, `TestFbz2SubbossRewind`, and `src/test/java/com/openggf/tests/TestFbzAct2RouteToBossHeadless.java`.

**Steps:**

1. RED-test the full subboss routine graph, four/two repeated pieces, controllers, sprite mask, Robotnik/EggRobo character branch, hit counter/cadence, palette/music/SFX, defeat, and cleanup.
2. Load the correct Sonic/Tails or Knuckles PLC. On normal progression, the subboss defeat path must queue `ArtKosM_FBZCloud` and `ArtKosM_FBZBossPillar` through `PLCKosM_FBZ2Subboss` before the `$2B30` setup, then restore `PLC_Monitors` followed by `PLC_MonitorsSpikesSprings` at the disassembly-defined stages.
3. RED-test the normal cloud/pillar art readiness/order separately from checkpoint/re-entry. Verify the post-subboss route and rewind during each PLC handoff.
4. Run the mandatory route-wave gate. This is the **Act 2 traversal/subboss wave boundary**.

**Verify:**

```powershell
mvn "-Dtest=TestFbzAct2Subboss,TestFbz2SubbossCharacterArt,TestFbz2SubbossArtHandoff,TestFbz2SubbossRewind,TestFbzAct2RouteToBossHeadless" test "-Ds3k.rom.path=s3k.gen"
```

### Task 15: Implement the pre-boss plane-transition controller, pillars, and clouds

**Skills:** `s3k-zone-events`, `s3k-implement-object`, `s3k-parallax`

**Files:** create `FbzEndBossEventControlInstance`, `FbzBossPillarInstance`, `FbzCloudInstance`, and `FbzBossEventRewindLinks`; modify `src/main/java/com/openggf/game/sonic3k/events/Sonic3kFBZEvents.java`, `src/main/java/com/openggf/game/sonic3k/runtime/FbzZoneRuntimeState.java`, `src/main/java/com/openggf/game/sonic3k/scroll/SwScrlFbz.java`, `src/main/java/com/openggf/game/sonic3k/render/FbzBossPlaneRenderMode.java`, `src/main/java/com/openggf/physics/BackgroundPlaneCollisionProvider.java`, `src/main/java/com/openggf/game/sonic3k/constants/Sonic3kObjectIds.java`, `Sonic3kConstants.java`, `src/main/java/com/openggf/game/sonic3k/objects/Sonic3kObjectRegistry.java`, `src/main/java/com/openggf/game/sonic3k/Sonic3kObjectArtKeys.java`, `Sonic3kObjectArt.java`, `Sonic3kObjectArtProvider.java`, `Sonic3kPlcArtRegistry.java`, and `src/main/java/com/openggf/tools/Sonic3kObjectProfile.java`; create `TestFbzBossEventSetup`, `TestFbzBossCloudIdentity`, and `TestFbzPlaneTransition`.

**Steps:**

1. RED-test the exact normal `$2B30` `SetUp_FBZ2BossEvent` order only: create controller, create pillar, clear cloud-address pairs, create ten clouds from `FBZCloud_PositionFrameData`, and apply the palette patch. Assert that setup performs no cloud/pillar art queue and does not yet advance offsets, carry players, enable background collision, reverse planes, or refresh the destination.
2. RED-test checkpoint/re-entry at camera X `>= $2C40` as a distinct path: `FBZ2_ScreenInit` runs setup first, then queues `ArtKosM_FBZCloud` and `ArtKosM_FBZBossPillar`. Both paths must prove art readiness before rendering consumers.
3. Preserve original slot/allocation order. Store ten rewind IDs, never raw references; relink after `ObjectManager` restore and deterministically recreate missing pre-cleanup clouds.
4. RED-test the separately ordered controller/event phases: wait with no offset/collision change until player X `>= $2E80`; then advance `_unkEE98/_unkEE9C` and carry participants; enable background collision at the documented controller stage; drive plane reversal through the foreground/background event-stage handoff; refresh the destination; clear collision when both offsets reach their endpoints; and only later reach foreground stage `$0C`.
5. Publish collision mode during the established post-camera event phase and make it visible on the next frame's player collision pass. Test entry, `$2E80` threshold, steady state, offset endpoints, boss-load handoff, cleanup, death, restart, rewind, and `TestFbzFramePhaseOrdering`; do not reorder `LevelFrameStep`.
6. At foreground stage `$0C`, request the end-boss spawn; do not defer collision clearing to this spawn if the controller already cleared it at the offset endpoint. Clear remaining transient shake only at its documented stage.

**Verify:**

```powershell
mvn "-Dtest=TestFbzBossEventSetup,TestFbzBossCloudIdentity,TestFbzPlaneTransition,TestFbzBackgroundPlaneCollision,TestFbzBossCloudDeform,TestFbzBossPlaneRenderMode,TestFbzFramePhaseOrdering" test "-Ds3k.rom.path=s3k.gen"
```

### Task 16: Implement the Act 2 end-boss object graph and defeat

**Skills:** `s3k-implement-boss`, `s3k-plc-system`

**Files:** create `FbzEndBossInstance`, `FbzEndBossShipChild`, `FbzRobotnikHeadChild`, `FbzEndBossFlameChild`, concrete weapon/arm/projectile/debris children for `ChildObjDat_70EE0`, `70EF4`, `70EFC`, `70F04`, `70F0A`, and `70F24`, plus `FbzEndBossRewindLinks`; modify `src/main/java/com/openggf/game/sonic3k/constants/Sonic3kObjectIds.java`, `Sonic3kConstants.java`, `src/main/java/com/openggf/game/sonic3k/objects/Sonic3kObjectRegistry.java`, `src/main/java/com/openggf/game/sonic3k/Sonic3kObjectArtKeys.java`, `Sonic3kObjectArt.java`, `Sonic3kObjectArtProvider.java`, `Sonic3kPlcArtRegistry.java`, `Sonic3kPlcLoader.java`, and `src/main/java/com/openggf/tools/Sonic3kObjectProfile.java`; create `TestFbzEndBoss`, `TestFbzEndBossChildren`, `TestFbzEndBossAudioAndPlc`, and `TestFbzEndBossRewind`.

**Steps:**

1. RED-test the complete `$AC` routine graph: spawn slot, phases, movement tables, child order, Robotnik/EggRobo variants, hit/invulnerability/shield behavior, projectiles, flame, audio, palette, explosions, defeat, and cleanup.
2. Port only verified integer/fixed-point behavior and exact mappings/tiles/priorities. Use slot-aware children and identity relinking.
3. Queue `PLCKosM_FBZEndBoss_Exit` at the documented aftermath stage and expose the exit-ready event state.
4. Test native Sonic, Tails, Sonic+Tails, and Knuckles boss completion before compatibility extensions.

**Verify:**

```powershell
mvn "-Dtest=TestFbzEndBoss,TestFbzEndBossChildren,TestFbzEndBossAudioAndPlc,TestFbzEndBossRewind" test "-Ds3k.rom.path=s3k.gen"
```

### Task 17: Implement exit hall/door, placed prisons/plungers, final capsule, and Sandopolis handoff

**Skills:** `s3k-implement-object`, `s3k-plc-system`, `s3k-zone-events`

**Files:** create `FbzExitDoorInstance`, `FbzExitHallInstance`, `FbzEggPrisonInstance`, `FbzSpringPlungerInstance`, and their real children; configure the generic `Obj_EggCapsule` path without conflating it with `$CF`; modify `src/main/java/com/openggf/level/LevelTransitionCoordinator.java`, `src/main/java/com/openggf/game/sonic3k/constants/Sonic3kObjectIds.java`, `Sonic3kConstants.java`, `src/main/java/com/openggf/game/sonic3k/objects/Sonic3kObjectRegistry.java`, `src/main/java/com/openggf/game/sonic3k/Sonic3kObjectArtKeys.java`, `Sonic3kObjectArt.java`, `Sonic3kObjectArtProvider.java`, `Sonic3kPlcArtRegistry.java`, `Sonic3kPlcLoader.java`, and `src/main/java/com/openggf/tools/Sonic3kObjectProfile.java`; create `TestFbzExitDoor`, `TestFbzExitHall`, `TestFbzEggPrison`, `TestFbzFinalEggCapsule`, `TestFbzToSandopolisTransition`, and `src/test/java/com/openggf/tests/TestFbzAct2RouteHeadless.java`.

**Steps:**

1. RED-test `$CE`, `$8A` subtypes `00,04`, `$CF` subtypes `00,01,02`, `$D0`, their child tables, animals/explosions, art readiness, collision, animation, and lifecycle.
2. Keep placed `Obj_FBZEggPrison` behavior distinct from the dynamically spawned generic final `Obj_EggCapsule`.
3. Enforce exit PLC completion before consumers. Port the door/hall/capsule sequence and wait for camera Y `$720` before requesting `StartNewLevel #$0800` (Sandopolis Act 1).
4. GREEN the inventory/registry gate at zero live FBZ placeholder placements and prove both acts complete.
5. Run the mandatory route-wave gate. This is the **final route/exit wave boundary**.

**Verify:**

```powershell
mvn "-Dtest=TestFbzExitDoor,TestFbzExitHall,TestFbzEggPrison,TestFbzFinalEggCapsule,TestFbzToSandopolisTransition,TestFbzAct2RouteHeadless,TestFbzObjectRegistryCompleteness" test "-Ds3k.rom.path=s3k.gen"
```

### Task 18: Close rewind, lifecycle, architecture, and native route coverage

**Skills:** `superpowers:systematic-debugging`, `superpowers:verification-before-completion`

**Files:** modify only proven gaps in `src/main/java/com/openggf/game/rewind/schema/DefaultObjectRewindPolicies.java` and object recreate/relink code; create `TestFbzObjectRewind`, `TestFbzBossGraphRewind`, `TestFbzCheckpointRoutes`, and `TestFbzNativeCharacterRoutes`; extend the route tests created in Tasks 11, 14, and 17 with restart/rewind/native-character cases.

**Steps:**

1. Add capture/restore/capture equality and forward-replay tests for events, transitions, polarity, carriers, hazards, badniks, bosses, clouds, exit, and capsule.
2. Run both rewind coverage guards. Fix recreate paths, final mutable scalars, and object references instead of growing baselines unless parent recreation demonstrably owns a render-only child.
3. Test normal start, every supported checkpoint, death/reload, seamless transition, level select, and Sandopolis handoff for Sonic, Tails, Sonic+Tails, and Knuckles.
4. Run service, runtime-state, mutation, transition, trace-invariant, and architecture guards.

**Verify:**

```powershell
mvn "-Dtest=TestFbzObjectRewind,TestFbzBossGraphRewind,TestFbzCheckpointRoutes,TestFbzAct1RouteHeadless,TestFbzAct2RouteHeadless,TestFbzNativeCharacterRoutes,TestRewindCoverageGuard,TestStaticStateRewindCoverageGuard,TestNoDirectMapMutationsInGameplay,TestZoneEventRuntimeAccessGuard,TestS3kRuntimeStateReadGuard,TestS3kTransitionBridgeGuard,TestObjectServicesMigrationGuard,TestNoServicesInObjectConstructors,TestTraceReplayInvariantGuard" test "-Ds3k.rom.path=s3k.gen"
```

### Task 19: Perform native visual/audio validation before trace work

**Skills:** `s3k-zone-validate`

**Files:**

- Verify without moving coordinates/frames: `docs/s3k-zones/fbz-visual-checkpoints.json`
- Create/update: `docs/s3k-zones/fbz-validation.md`
- Create: `tools/validation/Validate-FbzVisualCheckpoints.ps1`

**Steps:**

1. Implement the reusable validation runner to read the immutable checkpoint manifest, capture reference/engine evidence, reject missing checkpoints, and append a named result section without changing expected coordinates/frames.
2. Capture reference and engine checkpoints for act starts, all indoor/outdoor thresholds, representative objects/badniks, miniboss, seamless transition, subboss/PLC restoration, plane transition, end boss, exit/capsule, and Sandopolis handoff.
3. Capture time series for AniPLC cadence, magnetic phase, palette changes, parallax drift/bob, clouds, shake, and plane reversal.
4. Record `PASS/FAIL` with evidence. Deterministic behavior may not remain `LIKELY`; no required checkpoint may be skipped.
5. Fix failures through their owning task and repeat both reviews before continuing.

**Verify:**

```powershell
& tools/validation/Validate-FbzVisualCheckpoints.ps1 -Mode native-pre-compat -ExtensionsDisabled -Output docs/s3k-zones/fbz-validation.md
```

### Task 20: Restore pinned BizHawk and run the late complete-run trace polish

**Skills:** `trace-replay-bug-fixing`, `s3k-zone-validate`

**Files:**

- Install ignored runtime: `docs/BizHawk-2.11-win-x64/`
- Source movie: `src/test/resources/traces/s3k/_movies/s3k-complete-sonic-tails.bk2`
- Create fixture directory: `src/test/resources/traces/s3k/fbz_completerun/`
- Create: `src/test/java/com/openggf/tests/trace/s3k/TestS3kFbzCompleteRunTraceFixture.java`
- Create: `src/test/java/com/openggf/tests/trace/s3k/TestS3kFbzCompleteRunTraceReplay.java`
- Create: `src/test/java/com/openggf/tests/trace/TestTraceBaselineRegression.java`
- Modify: `docs/TRACE_FRONTIER_LOG.md`

**Steps:**

1. Only now download `https://github.com/TASEmulators/BizHawk/releases/download/2.11/BizHawk-2.11-win-x64.zip`. Require size `91,301,556` and SHA-256 `722B5AAC5E1D89F890B2875B0150F4A86F5762D211F7CD47029CAC70434955C0`; extract under ignored `docs/BizHawk-2.11-win-x64` and verify `EmuHawk.exe`.
2. Record the BK2 through `tools/bizhawk/s3k_complete_run_recorder.lua` and install the segment by metadata offset, never directory-name assumptions.
3. RED/GREEN fixture assertions: `game=s3k`, `zone=fbz`, `zone_id=4`, `act=1`, `bk2_frame_offset=237913`, `trace_frame_count=44282` (frames 237913-282194), next-zone frame 282195, `source_bk2=s3k-complete-sonic-tails.bk2`, `characters=[sonic,tails]`, `main_character=sonic`, `sidekicks=[tails]`, schema/CSV 5, complete-run profile, recorder `6.28-s3k-completerun`, BizHawk 2.11, Genplus-gx, ROM checksum `C5B1C655C19F462ADE0AC4E17A844D10`, gzipped physics/aux files only, and catalog discovery.
4. Replay controller input only. Fix the first divergence using disassembly-backed state. Reject hydration, tolerance masking, and zone/route/frame exceptions.
5. Acceptance is zero errors and zero warnings. Update the frontier log whenever the frontier moves or an existing green trace regresses; rerun all previously green S3K complete-run traces after shared fixes.
6. Late in this task, run every `known_red` test class from the frozen JSON manifest. Expected Maven failures are allowed only to produce fresh `target/trace-reports/*_report.json` files. Then run `TestTraceBaselineRegression`, which fails for a missing/stale report, an earlier first-error frame, or—when the first frame is equal—a larger error count or warning count. A later frontier or fewer divergences is an improvement and must update `TRACE_FRONTIER_LOG.md`; trace data remains read-only.

**Verify:**

```powershell
mvn "-Dtest=TestS3kFbzCompleteRunTraceFixture,TestS3kFbzCompleteRunTraceReplay" test "-Ds3k.rom.path=s3k.gen"
$knownRed = ((Get-Content 'docs/superpowers/research/2026-07-12-fbz-trace-baseline.json' -Raw | ConvertFrom-Json).known_red.test_class) -join ','
if ([string]::IsNullOrWhiteSpace($knownRed)) { throw 'known_red test manifest is empty' }
mvn "-Dtest=$knownRed" test "-Ds3k.rom.path=s3k.gen"; $knownRedExit = $LASTEXITCODE
mvn "-Dtest=com.openggf.tests.trace.TestTraceBaselineRegression" test "-Ds3k.rom.path=s3k.gen" "-Dtrace.baseline.path=docs/superpowers/research/2026-07-12-fbz-trace-baseline.json"
```

### Task 21: Run the mandatory compatibility matrix, then re-prove native parity

**Skills:** `superpowers:test-driven-development`, `superpowers:systematic-debugging`

**Files:**

- Create: `src/test/java/com/openggf/tests/TestFbzCompatibilityMatrix.java`
- Create: `src/test/java/com/openggf/tests/TestFbzNativeConfiguration.java`
- Create: `docs/s3k-zones/fbz-compatibility.md`
- Modify provider/profile/rules code only if a proven donated capability blocks a mandatory route

**Steps:**

1. Add parameterized multi-sidekick cases: Sonic+none, Sonic+Tails, Sonic+Tails+Knuckles, Sonic+Tails+Knuckles+Sonic, and Sonic+Sonic+Sonic+Sonic. Cover shared event/boss state, solids, hazards, carriers, grabs, forced movement, bosses, transitions, duplicate art banks, and full completion.
2. Add widths 320, 352, 400, 528, and 800. For each width explicitly assert world-coordinate thresholds, camera locks/releases, spawn/culling, boss containment, premature activation absence, and no unsafe fall/death at both horizontal extremes.
3. Add donation `off`, `s1`, and `s2` on the S3K host. Identify mandatory-route ability blockers. If needed, add the smallest semantic capability/profile workaround with an explicit comment naming the blocked FBZ mechanic and donor capability; preserve donation-off behavior.
4. Persist every matrix row and evidence in `fbz-compatibility.md`.
5. Parameterize `TestFbzNativeConfiguration` across Sonic, Tails, Sonic+Tails, and Knuckles. For each configuration assert donation source `off`, extensions disabled, native S3K movement/rules and character roster, native sidekick mode, no extra configured sidekicks, and viewport width 320 before parity comparisons.
6. Rerun the strict native route, focused, and complete-run trace suites through those fixtures. Then execute the immutable stable-retro checkpoints with `-Mode native-post-compat -ExtensionsDisabled`; append a distinct `Post-compatibility native regression` section to `fbz-validation.md`. Compatibility work is rejected if any pre-compat native result changes.

**Verify:**

```powershell
mvn "-Dtest=TestFbzCompatibilityMatrix,TestFbzNativeConfiguration,TestFbzNativeCharacterRoutes,TestFbzAct1RouteHeadless,TestFbzAct2RouteHeadless,TestS3kFbzCompleteRunTraceReplay" test "-Ds3k.rom.path=s3k.gen"
& tools/validation/Validate-FbzVisualCheckpoints.ps1 -Mode native-post-compat -ExtensionsDisabled -Output docs/s3k-zones/fbz-validation.md
```

### Task 22: Final documentation and full verification

**Skills:** `superpowers:verification-before-completion`, `superpowers:requesting-code-review`, `superpowers:finishing-a-development-branch`

**Files:**

- Modify: `CHANGELOG.md`
- Modify: `docs/S3K_KNOWN_DISCREPANCIES.md` only for evidence-backed remaining limitations; full acceptance requires no FBZ gameplay waiver
- Finalize: `docs/s3k-zones/fbz-analysis.md`, `fbz-object-inventory.md`, `fbz-visual-checkpoints.json`, `fbz-validation.md`, `fbz-compatibility.md`
- Finalize: `docs/TRACE_FRONTIER_LOG.md`
- Finalize: `docs/superpowers/research/2026-07-12-fbz-red-green-log.md` and `2026-07-12-fbz-trace-baseline.json`

**Steps:**

1. Confirm every inventory row is concrete, every dynamic spawn is implemented, every deterministic validation checkpoint is PASS, the trace has zero errors/warnings, and all compatibility rows pass.
2. Run the complete FBZ suite, required S3K regressions, architecture/rewind guards, existing green S3K complete-run traces, and `mvn package` from a clean execution worktree.
3. Delegate final spec-compliance, ROM-parity, architecture, compatibility, and code-quality reviews. Fix every blocker/important finding and repeat until all are GREEN.
4. Inspect `git diff --check`, staged scope, and branch trailers. Do not stage the user's unrelated root-workspace changes.

**Final verification:**

```powershell
mvn "-Dtest=TestFbz*,TestS3kFbzCompleteRunTraceFixture,TestS3kFbzCompleteRunTraceReplay" test "-Ds3k.rom.path=s3k.gen"
$knownRed = ((Get-Content 'docs/superpowers/research/2026-07-12-fbz-trace-baseline.json' -Raw | ConvertFrom-Json).known_red.test_class) -join ','
if ([string]::IsNullOrWhiteSpace($knownRed)) { throw 'known_red test manifest is empty' }
mvn "-Dtest=$knownRed" test "-Ds3k.rom.path=s3k.gen"
mvn "-Dtest=com.openggf.tests.trace.TestTraceBaselineRegression" test "-Ds3k.rom.path=s3k.gen" "-Dtrace.baseline.path=docs/superpowers/research/2026-07-12-fbz-trace-baseline.json"
$greenFleet = ((Get-Content 'docs/superpowers/research/2026-07-12-fbz-trace-baseline.json' -Raw | ConvertFrom-Json).green_test_classes) -join ','
if ([string]::IsNullOrWhiteSpace($greenFleet)) { throw 'green_test_classes is empty' }
mvn "-Dtest=$greenFleet" test "-Ds3k.rom.path=s3k.gen"
$mustGreen = @(
  'com.openggf.tests.TestS3kAiz1SkipHeadless',
  'com.openggf.tests.TestSonic3kLevelLoading',
  'com.openggf.game.sonic3k.TestSonic3kLevelLoading',
  'com.openggf.game.sonic3k.TestSonic3kBootstrapResolver',
  'com.openggf.game.sonic3k.TestSonic3kDecodingUtils',
  'com.openggf.game.rewind.coverage.TestRewindCoverageGuard',
  'com.openggf.game.rewind.coverage.TestStaticStateRewindCoverageGuard',
  'com.openggf.game.mutation.TestNoDirectMapMutationsInGameplay',
  'com.openggf.game.TestZoneEventRuntimeAccessGuard',
  'com.openggf.game.sonic3k.TestS3kRuntimeStateReadGuard',
  'com.openggf.game.sonic3k.TestS3kTransitionBridgeGuard',
  'com.openggf.level.objects.TestObjectServicesMigrationGuard',
  'com.openggf.level.objects.TestNoServicesInObjectConstructors',
  'com.openggf.tests.TestNoServicesInObjectConstructors',
  'com.openggf.tests.TestTraceReplayInvariantGuard'
) -join ','
mvn "-Dtest=$mustGreen" test "-Ds3k.rom.path=s3k.gen"
mvn package "-Ds3k.rom.path=s3k.gen"
git diff --check
```

## Definition of done

- All 860 runtime placements and every reachable dynamic spawn have correct concrete implementations; raw file counts remain verified at 421/441 including terminators.
- Both acts, checkpoints, bosses, seamless transition, exit/capsule, and Sandopolis handoff match the locked-on disassembly for Sonic, Tails, Sonic+Tails, and Knuckles.
- Events, plane collision/rendering, parallax, AniPLC, palette, PLC/VRAM, art, mappings, audio, collision, object lifetime, and rewind behavior are deterministic and reviewed.
- Focused tests, route tests, native visual checkpoints, all guards, package build, and the late complete-run replay are GREEN with zero trace errors/warnings.
- Multi-sidekick, widescreen, and donation matrices pass; a final extension-disabled run proves native behavior remains unchanged.
- Every task's spec and quality review loop is GREEN, and documentation contains no unresolved required FBZ discrepancy.

# Develop Red-Suite Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn all 36 current develop reds green with ROM-driven behavior fixes, behavior-neutral extraction, or narrowly justified test/API reconciliation.

**Architecture:** Work in dependency order: child-graph rewind before rewind inventories, shared physics before object consumers, then isolated gameplay/rendering fixes. Each task owns one root cause, runs the named tests red then green, and produces one coherent commit.

**Tech Stack:** Java 17, Maven, JUnit 5, OpenGGF compact rewind schema, object-control/profile APIs, palette ownership, native-position helpers.

---

## D1: Rewind and architecture integrity

### Task D1.1: Restore the Spiker child slot graph

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/badniks/SpikerBadnikInstance.java`
- Modify only if the generic relink seam is missing: `src/main/java/com/openggf/level/objects/ObjectManager.java`
- Test: `src/test/java/com/openggf/game/rewind/TestS3kBadnikChildGraphRewind.java`

- [ ] Run the named test and preserve the exact-identity assertion. Expected red: Spiker's launcher slot is null after restore.

```powershell
mvn "-Dtest=com.openggf.game.rewind.TestS3kBadnikChildGraphRewind#spikerTopSpikeRestoresExactParentAndCooldownState" test
```

- [ ] Add an assertion that `leftLauncher` resolves to the exact recreated launcher identity; verify it fails before implementation.
- [ ] Restore Spiker's `leftLauncher`/top-spike slot from the recreated child id instead of accepting a structurally equal replacement.
- [ ] Run the named method, then the complete `TestS3kBadnikChildGraphRewind`; expected PASS.
- [ ] Commit as `fix: restore Spiker rewind child slot`, updating `CHANGELOG.md` and trailers.

### Task D1.2: Restore the Mantis managed child graph

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/badniks/MantisBadnikInstance.java`
- Modify only if the generic relink seam is missing: `src/main/java/com/openggf/level/objects/ObjectManager.java`
- Test: `src/test/java/com/openggf/game/rewind/TestS3kBadnikChildGraphRewind.java`

- [ ] Run `mvn "-Dtest=com.openggf.game.rewind.TestS3kBadnikChildGraphRewind#mantisChildRelinksToRestoredParentAndParentSlot" test`; expected red before rewind because two parents own zero managed children.
- [ ] Add a pre-rewind assertion that each parent owns exactly one managed child; verify it fails.
- [ ] Route child creation through `spawnChild(...)`, register rewind identity, and restore parent/slot links by identity.
- [ ] Re-run the named method and full graph class; expected PASS.
- [ ] Commit as `fix: restore Mantis rewind child graph`, updating `CHANGELOG.md` and trailers.

### Task D1.3: Reconcile MGZ boss compact policy and annotations

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/MgzEndBossKnuxInstance.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/MgzEndBossKnuxEggCapsuleInstance.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/MgzDrillingRobotnikInstance.java`
- Modify: `src/main/java/com/openggf/game/rewind/schema/RewindPolicyRegistry.java`
- Test: `src/test/java/com/openggf/game/rewind/TestS3kBadnikChildGraphRewind.java`
- Test: `src/test/java/com/openggf/game/rewind/schema/TestRewindFieldDispositionGuard.java`
- Test: `src/test/java/com/openggf/game/rewind/TestRewindArchitectureGuard.java`
- Test: `src/test/java/com/openggf/game/rewind/TestRewindTransientGuard.java`

- [ ] Run `TestRewindFieldDispositionGuard`, `TestRewindArchitectureGuard`, and `TestRewindTransientGuard`. Expected red: undisposed `childComponents`, one new deferred annotation, three transient annotations, including redundant `airZoomCueRenderer`.
- [ ] Add a graph round-trip assertion proving `childComponents` recreates the exact managed children and relationships.
- [ ] Register gameplay graph fields in the compact/reference policy; remove renderer/cache annotations already covered by central default-transient policy. Do not classify `childComponents` as transient.
- [ ] Run:

```powershell
mvn "-Dtest=TestRewindFieldDispositionGuard,TestRewindArchitectureGuard,TestRewindTransientGuard,TestS3kBadnikChildGraphRewind" test
```

Expected: PASS with no blanket baseline count increase.
- [ ] Commit as `fix: capture MGZ boss rewind graph`, updating `CHANGELOG.md` and trailers.

### Task D1.4: Reconcile parent-dependent and tail inventories

**Files:**
- Modify: `src/test/java/com/openggf/game/rewind/TestParentDependentGraphCoverageGuard.java`
- Modify: `src/test/java/com/openggf/game/rewind/TestRemainingRewindTailInventory.java`
- Modify: `src/test/resources/rewind/parent-dependent-graph-coverage-baseline.txt`
- Modify: `src/test/resources/rewind/round-trip-tail-inventory.txt`
- Test production graph classes named by each changed row.

- [ ] Run both guards. Expected red totals: tail moved from `842/664/178` to `858/672/178`, with new HCZ/MGZ no-probe and parent-dependent entries.
- [ ] For implemented HCZ/MGZ classes, add focused recreate/relink tests named in the inventory row and move them into the covered bucket only after those tests pass.
- [ ] For a genuinely unfinished S&K class, add one itemized debt row with class name, missing recreate path, owning unfinished zone, and reason; never ratchet only the aggregate totals.
- [ ] Run both guards plus every graph test named by a changed row; expected PASS and internally consistent totals.
- [ ] Commit as `test: reconcile rewind graph inventory` with `Changelog: n/a: rewind coverage inventory and tests`.

### Task D1.5: Declare canonical touch-response profiles

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic2/objects/BreakablePlatingObjectInstance.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/MgzMinibossInstance.java`
- Modify: `src/main/java/com/openggf/game/profiles/touchresponse/TouchResponseProfile.java`
- Test: `src/test/java/com/openggf/level/objects/TestObjectPhysicsStandardizationGuard.java`

- [ ] Run `mvn "-Dtest=com.openggf.level.objects.TestObjectPhysicsStandardizationGuard#productionObjectPhysicsStandardizationHasNoUnapprovedViolations" test`; expected violations name BreakablePlating and MgzMiniboss hooks.
- [ ] Add focused tests for continuous plating callbacks and miniboss shield deflect.
- [ ] Declare both behaviors through canonical `TouchResponseProfile` and consume that profile from the hooks; do not add a guard baseline.
- [ ] Run focused tests and the named guard; expected PASS.
- [ ] Commit as `fix: declare object touch-response profiles`, updating `CHANGELOG.md`.

### Task D1.6: Route playable writes through NativePositionOps

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/bonusstage/slots/S3kSlotPlayerRuntime.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/PachinkoMagnetOrbObjectInstance.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/S3kSlotBonusCageObjectInstance.java`
- Modify only if an operation is missing: `src/main/java/com/openggf/level/objects/NativePositionOps.java`
- Test: `src/test/java/com/openggf/level/objects/TestObjectPhysicsStandardizationGuard.java`

- [ ] Run `mvn "-Dtest=com.openggf.level.objects.TestObjectPhysicsStandardizationGuard#productionPlayableNativePositionRawPreserveSubpixelWriteFilesDoNotGrow" test`; expected five raw writes.
- [ ] Add focused integer/fraction assertions for slots X/Y, Pachinko Y, and cage X/Y.
- [ ] Replace the five raw writes with the semantically matching `NativePositionOps` calls.
- [ ] Run focused tests and named guard; expected PASS without a baseline change.
- [ ] Commit as `fix: route playable native position writes`, updating `CHANGELOG.md`.

### Task D1.7: Replace post-budget raw destruction calls

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/MgzEndBossFallingDebrisChild.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/MgzEndBossKnuxCollapseEmitter.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/MgzEndBossKnuxDefeatPart.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/MgzEndBossKnuxDrillChild.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/MgzEndBossKnuxInstance.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/bosses/HczEndBossBladeImpactExplosion.java`
- Modify only if an operation is missing: `src/main/java/com/openggf/level/objects/ObjectLifetimeOps.java`
- Test: `src/test/java/com/openggf/level/objects/TestObjectPhysicsStandardizationGuard.java`

- [ ] Run `mvn "-Dtest=com.openggf.level.objects.TestObjectPhysicsStandardizationGuard#productionObjectLifecycleRawCallCountsDoNotGrow" test`; expected count `585` versus `582`.
- [ ] Use baseline commit `0dfda47b77` to confirm eight additions and five removals (net +3) across the six listed files.
- [ ] Add a focused latched-versus-respawnable destruction test for each distinct lifecycle path, then replace all eight post-budget additions with the matching `ObjectLifetimeOps` operation.
- [ ] Run focused tests and named guard; expected PASS with budget still `582`.
- [ ] Commit as `fix: adopt MGZ object lifetime operations`, updating `CHANGELOG.md`.

### Task D1.8: Remove direct zone-event runtime access

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/events/Sonic3kHCZEvents.java`
- Modify only the existing event dependency surface needed to inject the service.
- Test: `src/test/java/com/openggf/game/TestZoneEventRuntimeAccessGuard.java`

- [ ] Add/retain a focused HCZ behavior test covering the operation currently reached through `GameServices`.
- [ ] Run the behavior test and guard; expected guard red names `Sonic3kHCZEvents`.
- [ ] Route the dependency through the event manager/context/provider pattern used by a passing event class; remove the direct `GameServices` reference.
- [ ] Run the focused HCZ test and complete guard; expected PASS.
- [ ] Commit as `refactor: inject HCZ event runtime dependency`, using explicit changelog justification if behavior is unchanged.

### Task D1.9: Split PlayerMovementRules at its narrow owner

**Files:**
- Modify: `src/main/java/com/openggf/game/rules/PlayerMovementRules.java`
- Modify: `src/main/java/com/openggf/game/rules/GameRules.java`
- Test: `src/test/java/com/openggf/tests/game/TestPerGameRuleArchitectureGuard.java`

- [ ] Run `typedRuleRecordsStaySmallEnoughToReview`; expected red: 23 components versus limit 22.
- [ ] Identify the newest component and add tests for its S1/S2/S3K values at the narrower provider/profile owner.
- [ ] Move that component into a cohesive existing/new sub-record and delegate from callers; do not raise the record-size limit or branch on game names.
- [ ] Run the guard and cross-game rule tests; expected PASS with identical values.
- [ ] Commit as `refactor: narrow player movement rule ownership` with documentation trailers.

### Task D1.10: Extract ObjectManager growth into its controller

**Files:**
- Modify: `src/main/java/com/openggf/level/objects/ObjectManager.java`
- Modify as owned by the extracted block: `src/main/java/com/openggf/level/objects/ObjectPlacementController.java`
- Modify as owned by the extracted block: `src/main/java/com/openggf/level/objects/ObjectTouchResponseController.java`
- Modify as owned by the extracted block: `src/main/java/com/openggf/level/objects/ObjectSolidContactController.java`
- Test: `src/test/java/com/openggf/tests/TestArchitecturalSourceGuard.java`

- [ ] Run `objectManagerFacadeStaysWithinExtractedCollaboratorBudget`; expected `2986 > 2914`.
- [ ] Before extraction, run the focused behavior tests for the newly grown section and add delegation assertions where none exist.
- [ ] Perform behavior-neutral extractions into existing collaborators; do not change budgets.
- [ ] Run focused behavior tests and the named guard; expected PASS.
- [ ] Commit as `refactor: extract object manager behavior` with explicit changelog justification.

### Task D1.11: Extract results/title-card transition preparation

**Files:**
- Modify: `src/main/java/com/openggf/GameLoop.java`
- Modify: `src/main/java/com/openggf/game/InLevelTitleCardCoordinator.java`
- Test: `src/test/java/com/openggf/tests/TestArchitecturalSourceGuard.java`

- [ ] Run `rootDispatchMethodsDoNotGrowBeyondCurrentBudgets`; expected `enterTitleCardFromResults` `98 > 91`.
- [ ] Add/retain transition delegation tests, perform behavior-neutral extraction, and keep the root method at or below 91.
- [ ] Run transition tests and named guard; expected PASS.
- [ ] Commit as `refactor: extract results title-card transition` with explicit changelog justification.

### Task D1.12: Extract playable-sprite growth into its controller

**Files:**
- Modify: `src/main/java/com/openggf/sprites/playable/AbstractPlayableSprite.java`
- Modify: `src/main/java/com/openggf/sprites/playable/PlayableSpriteController.java`
- Test: `src/test/java/com/openggf/tests/TestArchitecturalSourceGuard.java`

- [ ] Run `releaseCriticalLargeClassesDoNotGrowWithoutExtraction`; expected `3233 > 3159`.
- [ ] Add/retain delegation tests, perform behavior-neutral extraction, and keep the class at or below 3159.
- [ ] Run playable tests and named guard; expected PASS.
- [ ] Commit as `refactor: extract playable sprite behavior` with explicit changelog justification.

## D2: Shared physics and collision

### Task D2.1: Repair plane-aware terrain reflection tests

**Files:**
- Modify: `src/test/java/com/openggf/physics/TestObjectTerrainUtils.java`

- [ ] Run the class; expected four `NoSuchMethodException` errors because helpers request obsolete six-argument methods.
- [ ] Update floor and wall reflection signatures to append `byte.class`, and invoke with `(byte) 0`. Do not change production terrain math or expected distances/angles.
- [ ] Run the class; expected PASS including `-13`, `-5`, `-5`, and `-21` floor cases.
- [ ] Commit as `test: update terrain utility layer fixtures` with `Changelog: n/a: test API drift only`.

### Task D2.2: Preserve subpixel only for exact-edge side contact

**Files:**
- Modify: `src/main/java/com/openggf/level/objects/ObjectSolidContactController.java`
- Test: `src/test/java/com/openggf/level/objects/TestSolidObjectManager.java`

- [ ] Run the named test; expected red: nonzero overlap retains low word `0x8000` instead of snapping to zero.
- [ ] Keep exact `distX == 0` motion on the preserve-subpixel path. Route nonzero correction through the native integer-word snap unless the broader profile explicitly preserves edge subpixel motion.
- [ ] Run the named method, complete solid manager tests, and representative S1/S2/S3K solid-object tests; expected PASS.
- [ ] Commit as `fix: limit exact-edge subpixel preservation`, updating `CHANGELOG.md`.

### Task D2.3: Separate forced-spin roll animation from the aliased spindash byte

**Files:**
- Modify: `src/main/java/com/openggf/sprites/animation/ScriptedVelocityAnimationProfile.java`
- Modify only if ownership belongs there: `ForcedSpinObjectInstance.java`
- Test: `src/test/java/com/openggf/game/sonic2/objects/TestForcedSpinObjectInstance.java`

- [ ] Run the named test; expected red: resolver returns null instead of ROLL while rolling+pinball+aliased byte are set.
- [ ] Add a genuine charging-spindash regression test and retain normal pinball/roll coverage.
- [ ] Resolve roll when pinball mode owns the aliased byte; keep genuine charging spindash selection unchanged and do not clear movement state.
- [ ] Run the complete forced-spin and shared animation-profile suites; expected PASS.
- [ ] Commit as `fix: preserve forced-spin roll animation`, updating `CHANGELOG.md`.

### Task D2.4: Align floating-platform top-solid expectation with ROM profile

**Files:**
- Modify: `src/test/java/com/openggf/game/sonic3k/objects/TestTopSolidRoutineProfileAdoption.java`

- [ ] Run the class; expected red compares generic `topSolid(true)` with deliberate platform-snap=false, ground-height=true behavior.
- [ ] Replace the borrowed generic expected instance with independent assertions for the ROM `SolidObjectTop` flags (`height + 1`, relative `y_pos += d0 + 3`). Do not modify production behavior.
- [ ] Run floating-platform contact/headless tests and this guard; expected PASS.
- [ ] Commit as `test: assert floating-platform ROM top-solid profile`.

### Task D2.5: Restore rock sidekick push cadence

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/AizLrzRockObjectInstance.java`
- Test: `src/test/java/com/openggf/game/sonic3k/objects/TestAizLrzRockPlayerParticipation.java`

- [ ] Reproduce rock red (`0x1000` instead of `0x0FFF`) and identify the false sustained-contact gate for native P2; preserve the existing “first sidekick contact does not move main player” test.
- [ ] Make the selected participant consume the shared push cadence and write the rock centre with the intended integer/subpixel operation; do not substitute P1.
- [ ] Run the full rock class and shared participation tests; expected PASS.
- [ ] Commit as `fix: restore sidekick rock push cadence`, updating `CHANGELOG.md`.

### Task D2.6: Release invalid CNZ cylinder riders without jump setup

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/CnzCylinderInstance.java`
- Test: `src/test/java/com/openggf/tests/TestS3kCnzDirectedTraversalHeadless.java`

- [ ] Run the fully qualified named method; expected hurt rider remains object-controlled.
- [ ] Add/retain assertions for cleared slot/support/control, jumping=false, and ySpeed=0.
- [ ] Make forced invalid-rider release relinquish cylinder ownership unconditionally without invoking jump setup.
- [ ] Run neighboring release methods and the full class; expected PASS.
- [ ] Commit as `fix: release invalid CNZ cylinder rider`, updating `CHANGELOG.md`.

## D3: S3-era gameplay and rendering

### Task D3.1: Apply AIZ2 boss-activation player priority

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/events/Sonic3kAIZEvents.java`
- Test: `src/test/java/com/openggf/game/sonic3k/events/TestSonic3kAIZEvents.java`

- [ ] Run the fully qualified `aiz2EndBossActivationKeepsSonicHighPriorityAtWaterfall`; expected Sonic remains low priority.
- [ ] Assert priority changes on the native activation transition, then apply it through the event's player-participation surface.
- [ ] Run the full AIZ events class; expected PASS.
- [ ] Commit as `fix: restore AIZ2 boss player priority`, updating `CHANGELOG.md`.

### Task D3.2: Resolve the ICZ2 PalPointers palette restore

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/S3kBossDefeatSignpostFlow.java`
- Test: `src/test/java/com/openggf/tests/TestS3kIczMinibossObject.java`

- [x] Run the named PalPointers method; the assertion stopped after the installation call plus 119 `WAIT_FADE` decrements (call 120), one object call before cleanup (call 121).
- [x] Preserve the registry-backed `S3kPaletteWriteSupport`/immediate-resolution path and assert the ICZ miniboss owner on both ends of the restored line.
- [x] Run the full ICZ class and palette-ownership tests; expected PASS.
- [x] Commit as `test: align ICZ2 post-boss palette cleanup`, updating `CHANGELOG.md`.

### Task D3.3: Initialize the CNZ electric ball from Obj51

**Files:**
- Inspect: `src/main/java/com/openggf/game/sonic2/objects/bosses/CNZBossElectricBall.java`
- Test: `src/test/java/com/openggf/tests/TestCNZBossArtAndAnimation.java`

- [x] Run `electricBallUsesObj51ProjectileMappingFrames`; observed frame `0x12` at X=0 instead of `0x2A46`.
- [x] Trace Obj51 allocation ownership: production already captures the ROM-visible parent `x_pos` in the child `ObjectSpawn`; correct the synthetic fixture to use that canonical coordinate and assert attached native centre coordinates before first render. Existing split-position coverage remains in `TestSonic2CNZBossCollision`.
- [x] Run the full class, CNZ boss collision/position suite, and ROM-backed Obj51 mapping decoder; all 18 tests pass.
- [x] Commit as `test: align CNZ electric ball mapping fixture`, updating `CHANGELOG.md`.

### Task D3.4: Restore the S3K seamless-results ready handshake

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/S3kResultsScreenObjectInstance.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/events/S3kTransitionWriteSupport.java`
- Test: `src/test/java/com/openggf/game/sonic3k/objects/TestS3kResultsScreenObjectInstance.java`

- [x] Run the named HCZ/MGZ method; confirmed the native ready flag remains clear when no retained control owner exists.
- [x] Add a provider/runtime-state assertion for an armed seamless handoff, and set the ready flag from that state without raw zone comparisons.
- [x] Run the full results class plus transition bridge, HCZ event, CNZ event-flow, in-level title-card, and act-transition tests; all 48 tests pass.
- [x] Commit as `fix: restore seamless results handoff`, updating `CHANGELOG.md`.

### Task D3.5: Poll MGZ collapse rumble every 16 frames

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/events/Sonic3kMGZEvents.java`
- Test: `src/test/java/com/openggf/game/sonic3k/events/TestSonic3kMgz2QuakeEvents.java`

- [x] Run `collapsePlaysBigRumbleEvery16Frames`; reproduced the stale fixture's one-vs-two failure, then corrected the fixture from the ROM sequence so the pending request and positive `$14` startup shake require zero premature `BIG_RUMBLE` calls.
- [x] Preserve the pre-dispatch `RUMBLE_2` handoff poll, emit `BIG_RUMBLE` only from the initialized scrolling-collapse path, and retain the visible gameplay counter's ROM `(counter - 1) & $F` cadence; added startup, handoff, and off-cadence assertions.
- [x] Run the full 20-test quake class plus collapse, end-boss, event-rewind, event-schema, runtime-access, and architecture coverage; all 194 targeted tests pass.
- [x] Commit as `fix: restore MGZ collapse rumble cadence`, updating `CHANGELOG.md`.

### Task D3.6: Restore the MGZ miniboss return-swing routine boundary

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/MgzMinibossInstance.java`
- Test: `src/test/java/com/openggf/game/sonic3k/objects/TestMgzMinibossInstance.java`

- [ ] Run `upsideDownStatePersistsThroughDropAndRiseThenClearsBeforeTunnelUp`; expected an extra wait routine.
- [ ] Model the native callback transition so the flip persists through drop/rise and clears immediately before tunnel-up.
- [ ] Run the full class; expected PASS.
- [ ] Commit as `fix: restore MGZ miniboss return routine`, updating `CHANGELOG.md`.

### Task D3.7: Lock all MGZ defeat-camera bounds atomically

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/MgzMinibossInstance.java`
- Test: `src/test/java/com/openggf/game/sonic3k/objects/TestMgzMinibossInstance.java`

- [ ] Run `defeatCameraHelperLocksBothCameraBoundsWhileScrolling`; expected X/minX=`0x2E00`, maxX=`0x2DFF`.
- [ ] Update X, minX, and maxX atomically from the persistent camera owner until the same target is reached.
- [ ] Run the full class; expected PASS.
- [ ] Commit as `fix: lock MGZ defeat camera bounds`, updating `CHANGELOG.md`.

### Task D3.8: Order MGZ thruster flames around the body bucket

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/MgzDrillingRobotnikInstance.java`
- Test: `src/test/java/com/openggf/game/sonic3k/objects/TestMgzDrillingRobotnikInstance.java`

- [ ] Run `splitsThrusterFlamesIntoRomPriorityBuckets`; expected ship frame 9 is missing between rear and front flame.
- [ ] Preserve bucket 6→4 body tests and render rear flame, body/ship, front flame in ROM bucket order.
- [ ] Run the full class; expected PASS.
- [ ] Commit as `fix: restore MGZ thruster priority buckets`, updating `CHANGELOG.md`.

### Task D3.9: Assert the managed MGZ rear-drill child

**Files:**
- Modify: `src/test/java/com/openggf/tests/TestS3kMgzBossMusicTransition.java`
- Modify only if managed render order is wrong: `src/main/java/com/openggf/game/sonic3k/objects/MgzEndBossRenderChild.java`

- [ ] Run `endBossDrawsDrillPieceBehindMainBodyWhenItAppears`; expected obsolete parent-inline assertion fails.
- [ ] Replace it with a managed-graph assertion for `ROLE_STATIC_BACK`, frame 1, offset `(-0x14,+0x0F)` before parent body; verify production changes only if the managed order is wrong.
- [ ] Run the full class and render-child graph tests; expected PASS.
- [ ] Commit as `test: assert managed MGZ drill child` unless production changes are required.

### Task D3.10: Start MGZ boss music after exactly 120 updates

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/MgzDrillingRobotnikInstance.java`
- Test: `src/test/java/com/openggf/tests/TestS3kMgzBossMusicTransition.java`

- [ ] Run `drillingRobotnikFadesZoneMusicBeforeDelayedBossMusic`; expected boss music absent after update 120.
- [ ] Preserve fade-at-init assertion and correct native countdown/callback ordering.
- [ ] Run the full class; expected PASS.
- [ ] Commit as `fix: restore MGZ boss music timing`, updating `CHANGELOG.md`.

### Task D3.11: Drive thruster touch from the gameplay clock

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/MgzDrillingRobotnikInstance.java`
- Modify only if absent: the existing gameplay-clock accessor in `src/main/java/com/openggf/level/objects/ObjectServices.java`
- Test: `src/test/java/com/openggf/tests/TestS3kMgzBossMusicTransition.java`

- [ ] Run `endBossThrusterFlameTouchUsesGameplayFrameWithoutRenderPass`; expected null object-manager dereference.
- [ ] Add alternating phase assertions without drawing, then source phase from the runtime gameplay/VInt clock with a valid neutral value when no manager exists.
- [ ] Run the full class; expected PASS without test-only branches.
- [ ] Commit as `fix: drive MGZ thruster touch from gameplay clock`, updating `CHANGELOG.md`.

### Task D3.12: Expose MGZ multi-region touch dispatch

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/MgzDrillingRobotnikInstance.java`
- Modify only if canonical vocabulary lacks it: `src/main/java/com/openggf/game/profiles/touchresponse/TouchResponseProfile.java`
- Test: `src/test/java/com/openggf/tests/TestS3kMgzBossMusicTransition.java`

- [ ] Run `endBossTouchProfileExposesMultiRegionDispatch`; expected profile evaluation errors before returning its regions.
- [ ] Assert `multiRegionSource()` and stop policy after the gameplay-clock path, then declare/return the canonical multi-region profile.
- [ ] Run the full class and touch standardization guard; expected PASS.
- [ ] Commit as `fix: expose MGZ boss touch regions`, updating `CHANGELOG.md`.

## Literal Maven command contract

Each task uses its row below for the initial red run and final green regression run. Run commands as two separate PowerShell invocations; a row never abbreviates a class or method name.

| Task | Red command | Green regression command |
|---|---|---|
| D1.1 | `mvn "-Dtest=com.openggf.game.rewind.TestS3kBadnikChildGraphRewind#spikerTopSpikeRestoresExactParentAndCooldownState" test` | `mvn "-Dtest=com.openggf.game.rewind.TestS3kBadnikChildGraphRewind" test` |
| D1.2 | `mvn "-Dtest=com.openggf.game.rewind.TestS3kBadnikChildGraphRewind#mantisChildRelinksToRestoredParentAndParentSlot" test` | `mvn "-Dtest=com.openggf.game.rewind.TestS3kBadnikChildGraphRewind" test` |
| D1.3 | `mvn "-Dtest=com.openggf.game.rewind.schema.TestRewindFieldDispositionGuard#noNewSilentlyDroppedRewindFieldsBeyondBaseline,com.openggf.game.rewind.TestRewindArchitectureGuard#objectRewindAnnotationsDoNotGrowWithoutExplicitBaselineTriage,com.openggf.game.rewind.TestRewindTransientGuard#fieldsCoveredByDefaultTransientPolicyDoNotNeedExplicitAnnotations" test` | `mvn "-Dtest=com.openggf.game.rewind.schema.TestRewindFieldDispositionGuard,com.openggf.game.rewind.TestRewindArchitectureGuard,com.openggf.game.rewind.TestRewindTransientGuard,com.openggf.game.rewind.TestS3kBadnikChildGraphRewind" test` |
| D1.4 | `mvn "-Dtest=com.openggf.game.rewind.TestParentDependentGraphCoverageGuard#parentDependentBucketMatchesBaselineAndCoveredEntriesNameGraphTests,com.openggf.game.rewind.TestRemainingRewindTailInventory#remainingRoundTripTailMatchesInventory" test` | `mvn "-Dtest=com.openggf.game.rewind.TestParentDependentGraphCoverageGuard,com.openggf.game.rewind.TestRemainingRewindTailInventory" test` |
| D1.5 | `mvn "-Dtest=com.openggf.level.objects.TestObjectPhysicsStandardizationGuard#productionObjectPhysicsStandardizationHasNoUnapprovedViolations" test` | `mvn "-Dtest=com.openggf.level.objects.TestObjectPhysicsStandardizationGuard" test` |
| D1.6 | `mvn "-Dtest=com.openggf.level.objects.TestObjectPhysicsStandardizationGuard#productionPlayableNativePositionRawPreserveSubpixelWriteFilesDoNotGrow" test` | `mvn "-Dtest=com.openggf.level.objects.TestObjectPhysicsStandardizationGuard" test` |
| D1.7 | `mvn "-Dtest=com.openggf.level.objects.TestObjectPhysicsStandardizationGuard#productionObjectLifecycleRawCallCountsDoNotGrow" test` | `mvn "-Dtest=com.openggf.level.objects.TestObjectPhysicsStandardizationGuard" test` |
| D1.8 | `mvn "-Dtest=com.openggf.game.TestZoneEventRuntimeAccessGuard#zoneEventImplementations_shouldNotReferenceGameServicesDirectly" test` | `mvn "-Dtest=com.openggf.game.TestZoneEventRuntimeAccessGuard" test` |
| D1.9 | `mvn "-Dtest=com.openggf.tests.game.TestPerGameRuleArchitectureGuard#typedRuleRecordsStaySmallEnoughToReview" test` | `mvn "-Dtest=com.openggf.tests.game.TestPerGameRuleArchitectureGuard" test` |
| D1.10 | `mvn "-Dtest=com.openggf.tests.TestArchitecturalSourceGuard#objectManagerFacadeStaysWithinExtractedCollaboratorBudget" test` | `mvn "-Dtest=com.openggf.tests.TestArchitecturalSourceGuard" test` |
| D1.11 | `mvn "-Dtest=com.openggf.tests.TestArchitecturalSourceGuard#rootDispatchMethodsDoNotGrowBeyondCurrentBudgets" test` | `mvn "-Dtest=com.openggf.tests.TestArchitecturalSourceGuard" test` |
| D1.12 | `mvn "-Dtest=com.openggf.tests.TestArchitecturalSourceGuard#releaseCriticalLargeClassesDoNotGrowWithoutExtraction" test` | `mvn "-Dtest=com.openggf.tests.TestArchitecturalSourceGuard" test` |
| D2.1 | `mvn "-Dtest=com.openggf.physics.TestObjectTerrainUtils#floorFullTileEdgeChecksPreviousFullTileLikeSubF30C+floorFullTileEdgeKeepsPreviousCollisionAngleWhenItsSampleIsEmpty+floorRegressToEmptyPreviousTileMatchesSubF30CEmptyResult+floorRegressToPreviousSlopeUsesSingleRomTileOffset" test` | `mvn "-Dtest=com.openggf.physics.TestObjectTerrainUtils" test` |
| D2.2 | `mvn "-Dtest=com.openggf.level.objects.TestSolidObjectManager#zeroDistanceOnlyMotionHookPreservesExactEdgeWithoutChangingNonzeroCorrection" test` | `mvn "-Dtest=com.openggf.level.objects.TestSolidObjectManager" test` |
| D2.3 | `mvn "-Dtest=com.openggf.game.sonic2.objects.TestForcedSpinObjectInstance#forcedSpinEntryKeepsRollAnimationWhenPinballModeSharesSpindashByte" test` | `mvn "-Dtest=com.openggf.game.sonic2.objects.TestForcedSpinObjectInstance" test` |
| D2.4 | `mvn "-Dtest=com.openggf.game.sonic3k.objects.TestTopSolidRoutineProfileAdoption#floatingPlatformDeclaresTopSolidRoutineProfile" test` | `mvn "-Dtest=com.openggf.game.sonic3k.objects.TestTopSolidRoutineProfileAdoption" test` |
| D2.5 | `mvn "-Dtest=com.openggf.game.sonic3k.objects.TestAizLrzRockPlayerParticipation#sustainedSidekickPushMovesThatPlayerAndPreservesSubpixel" test` | `mvn "-Dtest=com.openggf.game.sonic3k.objects.TestAizLrzRockPlayerParticipation" test` |
| D2.6 | `mvn "-Dtest=com.openggf.tests.TestS3kCnzDirectedTraversalHeadless#cnzCylinderForcedReleaseClearsInvalidRiderStateWithoutUsingTheJumpPath" test` | `mvn "-Dtest=com.openggf.tests.TestS3kCnzDirectedTraversalHeadless" test` |
| D3.1 | `mvn "-Dtest=com.openggf.game.sonic3k.events.TestSonic3kAIZEvents#aiz2EndBossActivationKeepsSonicHighPriorityAtWaterfall" test` | `mvn "-Dtest=com.openggf.game.sonic3k.events.TestSonic3kAIZEvents" test` |
| D3.2 | `mvn "-Dtest=com.openggf.tests.TestS3kIczMinibossObject#icz2AfterBossCleanupRestoresObjectPaletteLineFromPalPointers" test` | `mvn "-Dtest=com.openggf.tests.TestS3kIczMinibossObject" test` |
| D3.3 | `mvn "-Dtest=com.openggf.tests.TestCNZBossArtAndAnimation#electricBallUsesObj51ProjectileMappingFrames" test` | `mvn "-Dtest=com.openggf.tests.TestCNZBossArtAndAnimation" test` |
| D3.4 | `mvn "-Dtest=com.openggf.game.sonic3k.objects.TestS3kResultsScreenObjectInstance#hczAndMgzSeamlessActOneExitSetsTransitionReadyFlag" test` | `mvn "-Dtest=com.openggf.game.sonic3k.objects.TestS3kResultsScreenObjectInstance" test` |
| D3.5 | `mvn "-Dtest=com.openggf.game.sonic3k.events.TestSonic3kMgz2QuakeEvents#collapsePlaysBigRumbleEvery16Frames" test` | `mvn "-Dtest=com.openggf.game.sonic3k.events.TestSonic3kMgz2QuakeEvents" test` |
| D3.6 | `mvn "-Dtest=com.openggf.game.sonic3k.objects.TestMgzMinibossInstance#upsideDownStatePersistsThroughDropAndRiseThenClearsBeforeTunnelUp" test` | `mvn "-Dtest=com.openggf.game.sonic3k.objects.TestMgzMinibossInstance" test` |
| D3.7 | `mvn "-Dtest=com.openggf.game.sonic3k.objects.TestMgzMinibossInstance#defeatCameraHelperLocksBothCameraBoundsWhileScrolling" test` | `mvn "-Dtest=com.openggf.game.sonic3k.objects.TestMgzMinibossInstance" test` |
| D3.8 | `mvn "-Dtest=com.openggf.game.sonic3k.objects.TestMgzDrillingRobotnikInstance#splitsThrusterFlamesIntoRomPriorityBuckets" test` | `mvn "-Dtest=com.openggf.game.sonic3k.objects.TestMgzDrillingRobotnikInstance" test` |
| D3.9 | `mvn "-Dtest=com.openggf.tests.TestS3kMgzBossMusicTransition#endBossDrawsDrillPieceBehindMainBodyWhenItAppears" test` | `mvn "-Dtest=com.openggf.tests.TestS3kMgzBossMusicTransition" test` |
| D3.10 | `mvn "-Dtest=com.openggf.tests.TestS3kMgzBossMusicTransition#drillingRobotnikFadesZoneMusicBeforeDelayedBossMusic" test` | `mvn "-Dtest=com.openggf.tests.TestS3kMgzBossMusicTransition" test` |
| D3.11 | `mvn "-Dtest=com.openggf.tests.TestS3kMgzBossMusicTransition#endBossThrusterFlameTouchUsesGameplayFrameWithoutRenderPass" test` | `mvn "-Dtest=com.openggf.tests.TestS3kMgzBossMusicTransition" test` |
| D3.12 | `mvn "-Dtest=com.openggf.tests.TestS3kMgzBossMusicTransition#endBossTouchProfileExposesMultiRegionDispatch" test` | `mvn "-Dtest=com.openggf.tests.TestS3kMgzBossMusicTransition,com.openggf.level.objects.TestObjectPhysicsStandardizationGuard" test` |

## Develop wave gate

- [ ] Run all 36 formerly red methods in one selection; expected zero failures/errors.
- [ ] Run every affected package/guard batch; expected zero failures/errors.
- [ ] Run `mvn test` twice consecutively; expected both complete develop runs green.
- [ ] Update the inventory and dispatch whole-change spec and quality reviews; loop until approved.

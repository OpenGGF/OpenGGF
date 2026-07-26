# S3K Initial Object Setup Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to implement this plan task-by-task, with a fresh implementation worker and two-stage review for every task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Model Sonic 3 & Knuckles' one post-title-card, pre-`LevelLoop` level-object setup pass as a production-owned lifecycle so standalone CNZ advances beyond frame 185 without trace identity, metadata selection, or replay-owned object scheduling.

**Architecture:** Correct the existing S3K title-card approximation first: locked title-card frames advance the native VBlank clock but do not age already-loaded level objects, because the ROM is still dispatching title-card SSTs at that point. A typed `LevelInitProfile` capability arms a private `LevelManager` lifecycle token on genuine S3K level assembly; live title-card release, ordinary first-frame execution, and comparison bootstrap may consume that token but cannot create it. Consumption invokes a dedicated S3K load-then-execute `ObjectManager` setup primitive that excludes player physics, touches, oscillation, and synthetic frame/VBlank advancement.

**Tech Stack:** Java 21, Maven, JUnit Jupiter, OpenGGF production level/title-card/object lifecycles, ROM-backed S3K trace replay, local `docs/skdisasm/sonic3k.asm`.

## Global Constraints

- The disassembly is the source of truth; cite `docs/skdisasm/sonic3k.asm:7737-7748`, `:7849-7855`, `:7889-7906`, and `:66747-66799` in production comments that encode ordering.
- Trace data remains comparison-only. Replay may consume a production token and apply its one-time frame-zero RNG/VInt bootstrap, but it must never arm the token from `TraceData`, `trace_profile`, filename, zone, route, frame, checkpoint, sidekick metadata, or an expected outcome.
- S1 and S2 retain their current title-card and startup behavior. The new `LevelInitProfile` capability defaults to `NONE`; only `Sonic3kLevelInitProfile` opts in.
- The S3K locked title-card correction must distinguish ROM title-card SST dispatch from the engine's already-loaded level `ObjectManager`.
- The setup pass is object-only because OpenGGF already models playable creation/reset separately. It must not tick Sonic/Tails physics, animation, CPU history, input, control, touch response, or ring collection.
- The setup pass must not advance `LevelManager.frameCounter`, `ObjectManager.vblaCounter`, lag, camera, level events, water, global oscillation, or animated tiles.
- The pending lifecycle is rewind/session state because it may span live title-card frames. Reset, teardown, failed load, restore, and fresh reload must leave a deterministic token.
- Keep current comparison-only bootstrap ordering: initialize live counter phases, consume the production token, then call `applyInitialRngSeedForReplay`.
- Non-goal: this work does not fix CNZ, ICZ, LBZ, or MHZ complete-run frame-zero gaps.
- Non-goal: this work does not replace or broaden the existing complete-run setup/restoration path.
- Non-goal: no recorder schema, fixture metadata, trace profile, diagnostic checkpoint, or trace identity becomes a lifecycle selector.
- No uncompressed trace fixture may be added.
- Update `CHANGELOG.md` and `docs/status/trace-frontier-log.md` when implementation moves the frontier.
- Every implementation commit must use the repository's required trailers and must not bypass hooks.

---

## Requirements

1. S3K's pre-level title-card wait must stop executing already-loaded level objects. The title provider continues to animate, and the production VBlank clock advances once per locked frame.
2. A genuine S3K post-load assembly arms exactly one initial level-object setup pass after player, sidekick, camera, event, and zone-player initialization have established the runtime state needed by object placement.
3. Only production lifecycle consumers may consume the pass:
   - live title-card release, immediately before the first ordinary level frame;
   - the first ordinary level-frame path when no title card is presented;
   - trace bootstrap after counter-phase initialization and before frame-zero RNG installation.
4. Consumption is one-shot and idempotent. A second consumer sees `false` and executes no objects.
5. The setup primitive follows S3K's `Load_Sprites` then `Process_Sprites` order:
   - update the two-axis placement cursor;
   - materialize the active spawn window;
   - execute current level-object slots once;
   - flush native child/dynamic allocations and capture the collision-response list for the following frame.
6. The setup primitive runs no player slot, touch response, ring collection, oscillator, camera, event, water, animated tile, level-frame, VBlank, or lag work.
7. `GameRng` remains native before and during setup. Trace bootstrap installs the recorded post-setup frame-zero seed only after token consumption.
8. Rewind capture/restoration preserves whether the setup pass is pending. Teardown and new load clear stale pending state before arming the new lifecycle.
9. Standalone CNZ must advance beyond frame 185 through production state only. Metadata variants cannot alter token arming or consumption.
10. S1/S2 traces and title-card tests, S3K AIZ/MGZ standards, all S3K complete-run frontiers, and S3K special stages must not regress.

## Exploration Synthesis

### ROM order

The S3K `Level` routine first creates `Obj_TitleCard` in dynamic slot 5 and loops at `loc_62CC`, calling `Process_Sprites` and `Render_Sprites` while waiting for the title-card object and decompression queues (`sonic3k.asm:7737-7748`). Level setup then creates the playable slots and zone state. At `sonic3k.asm:7849-7855`, the ROM calls:

```text
SpawnLevelMainSprites
Load_Sprites
Load_Rings
Draw_LRZ_Special_Rock_Sprites
Process_Sprites
Render_Sprites
Animate_Tiles
```

The ordinary loop is distinct: `Level_frame_counter` increments before `Process_Sprites`, with rendering and animated tiles afterward (`sonic3k.asm:7889-7906`).

CNZ balloons prove the setup pass is observable. `Obj_CNZBalloon` calls `Random_Number`, stores the low byte in `angle`, then uses and increments that angle during its first routine (`sonic3k.asm:66747-66799`). Replaying frame zero with an uninitialized balloon consumes the recorded frame-zero RNG seed one epoch too early. Historical commit `0f9b2c281` demonstrated that one object pass before RNG installation advances standalone CNZ from frame 185 to frame 1558, but selected it with `usesSidekickTitleCardSeedFrame(trace)`. That selection is prohibited and is not reused.

### Current engine divergence

`Sonic3kLevelInitProfile.levelLoadSteps` builds level objects, players, camera, events, sidekicks, and zone-player state before `RequestTitleCard`. During `GameLoop.updateTitleCardMode`, `Sonic3kTitleCardManager` inherits `TitleCardProvider.shouldRunLevelObjectsDuringLockedPhase() == true`, so every locked card frame calls `LevelManager.updateObjectPositions()` on already-loaded level objects.

`TestTitleCardObjectExecution` documents that this is divergent: ROM `loc_62CC` is still processing title-card SSTs, while the engine ages level objects. Nevertheless, `titleCardLegacyPath_s3kAiz1` currently pins a five-frame `ObjectManager` advance. Task 1 deliberately changes that expectation.

The existing S1 architecture supplies the useful precedent:

```java
@Override
public boolean shouldRunLevelObjectsDuringLockedPhase() {
    return false;
}

@Override
public int levelObjectPreludePassesAtRelease() {
    return 1;
}
```

S3K cannot copy the S1 provider count directly because headless replay suppresses title cards and because lifecycle ownership belongs to level assembly, not presentation metadata. Instead, `GameLoop` consumes a `LevelManager` token at release.

### Counter and placement hazards

`ObjectManager.update` increments both `frameCounter` and `vblaCounter` before dispatch, and `LevelManager.updateObjectPositions*` advances global oscillation after dispatch. Neither is suitable for the setup pass. S3K's existing normal update path already states the correct placement order for two-axis cursor placement: `placement.update(cameraX)`, `syncActiveSpawnsLoad(false)`, then execution. The dedicated primitive extracts that order without normal-frame counters or touches.

Locked title-card frames must still advance the VBlank clock even when they stop executing level objects. This requires a VBlank-only operation, rather than using `ObjectManager.update` as an accidental clock.

## Architecture Decision

### REVISED exact title-card dependency

The initial setup lifecycle depends on correcting S3K's locked title-card object population. These changes land in Task 1 before token arming or setup execution:

```java
// Sonic3kTitleCardManager
@Override
public boolean shouldRunLevelObjectsDuringLockedPhase() {
    return false;
}

@Override
public boolean shouldAdvanceVblankClockDuringLockedPhase() {
    return true;
}
```

Add the following default to `TitleCardProvider`:

```java
default boolean shouldAdvanceVblankClockDuringLockedPhase() {
    return false;
}
```

`GameLoop.updateTitleCardMode` calls `levelManager.advanceTitleCardVblankOnly()` when that capability is true. The method advances only `ObjectManager.vblaCounter`; it does not execute level objects or advance level-frame state.

The production lifecycle is:

```java
public enum InitialObjectSetupLifecycle {
    NONE,
    S3K_LOAD_THEN_EXECUTE_ONCE
}
```

```java
// LevelInitProfile
default InitialObjectSetupLifecycle initialObjectSetupLifecycle() {
    return InitialObjectSetupLifecycle.NONE;
}
```

```java
// Sonic3kLevelInitProfile
@Override
public InitialObjectSetupLifecycle initialObjectSetupLifecycle() {
    return InitialObjectSetupLifecycle.S3K_LOAD_THEN_EXECUTE_ONCE;
}
```

`LevelManager` owns:

```java
private InitialObjectSetupLifecycle pendingInitialObjectSetupLifecycle =
        InitialObjectSetupLifecycle.NONE;

public boolean consumePendingInitialObjectSetupPass() {
    InitialObjectSetupLifecycle pending = pendingInitialObjectSetupLifecycle;
    pendingInitialObjectSetupLifecycle = InitialObjectSetupLifecycle.NONE;
    if (pending != InitialObjectSetupLifecycle.S3K_LOAD_THEN_EXECUTE_ONCE) {
        return false;
    }
    objectManager.runInitialS3kLoadThenExecutePass(
            camera.getX(), mainPlayable(), spriteManager.getSidekicks());
    return true;
}
```

No value-taking arming setter exists. The named profile step after
`InitZonePlayerState` calls
`LevelManager.armInitialObjectSetupLifecycleFromActiveProfile(): void`. That
method takes no lifecycle, trace, or metadata argument; it clears stale state,
resolves the active module's `LevelInitProfile`, and copies the profile's typed
value.

The token may survive from load completion through locked title-card frames, so it is captured in `LevelSnapshot` through `LevelRewindSnapshotAdapter`. Three consumers race safely:

1. `GameLoop.updateTitleCardMode` consumes at title-card release before `exitTitleCard()` and before the first level-frame fallthrough.
2. `LevelFrameStep.execute` consumes at its entry for ordinary no-title production.
3. `TraceReplaySessionBootstrap.applyBootstrap` consumes after VInt/ring-floor phase initialization and before `applyInitialRngSeedForReplay`.

Only the first consumer executes the pass. Replay can call the consumer but cannot create the production token.

## Feature Design

### File and responsibility map

- Create `src/main/java/com/openggf/game/InitialObjectSetupLifecycle.java`: closed typed lifecycle values.
- Modify `src/main/java/com/openggf/game/LevelInitProfile.java`: default typed capability.
- Modify `src/main/java/com/openggf/game/sonic3k/Sonic3kLevelInitProfile.java`: S3K capability and named arming step after `InitZonePlayerState`.
- Modify `src/main/java/com/openggf/game/TitleCardProvider.java`: locked-phase VBlank-only capability.
- Modify `src/main/java/com/openggf/game/sonic3k/titlecard/Sonic3kTitleCardManager.java`: no level objects during locked pre-level card; VBlank-only clock enabled.
- Modify `src/main/java/com/openggf/GameLoop.java`: VBlank-only locked-card call and release token consumption.
- Modify `src/main/java/com/openggf/LevelFrameStep.java`: no-title first-frame token consumption.
- Modify `src/main/java/com/openggf/level/LevelManager.java`: private token, private arming step factory, public consume-only seam, VBlank-only operation, reset behavior.
- Modify `src/main/java/com/openggf/level/objects/ObjectManager.java`: dedicated S3K load-then-execute setup primitive.
- Modify `src/main/java/com/openggf/game/rewind/snapshot/LevelSnapshot.java`: pending lifecycle snapshot field using the enum value, not an ordinal.
- Modify `src/main/java/com/openggf/level/rewind/LevelRewindSnapshotAdapter.java`: capture and restore the pending lifecycle through `LevelManager` package-owned snapshot accessors.
- Modify `src/main/java/com/openggf/trace/replay/TraceReplaySessionBootstrap.java`: consume production token after counter setup and before RNG seed; do not add standalone S3K metadata selection.
- Modify `src/main/java/com/openggf/trace/TraceReplayBootstrap.java`: remove any remaining standalone S3K replay-owned setup selector; leave S1/S2 and existing complete-run contracts unchanged.
- Modify `src/test/java/com/openggf/tests/TestTitleCardObjectExecution.java`: corrected locked S3K policy and clock assertions.
- Create `src/test/java/com/openggf/tests/TestS3kInitialObjectSetupLifecycle.java`: ROM-backed production lifecycle integration tests.
- Create `src/test/java/com/openggf/level/objects/TestObjectManagerInitialS3kSetupPass.java`: isolated placement/dispatch/counter/touch tests.
- Modify `src/test/java/com/openggf/game/TestPostLoadAssemblyBehavior.java`: profile order and S1/S2 default assertions.
- Modify `src/test/java/com/openggf/level/rewind/TestLevelRewindSnapshotAdapter.java`: pending-token round trip and consumed-token state.
- Modify `src/test/java/com/openggf/tests/trace/s3k/TestS3kCnzTraceReplay.java`: standalone CNZ object/RNG closure without replay identity.
- Modify `src/test/java/com/openggf/tests/trace/TestTraceReplayStartPositionPolicy.java`: metadata-variant non-selection tests.
- Modify `CHANGELOG.md` and `docs/status/trace-frontier-log.md`: implementation and measured frontier evidence.

### Setup primitive contract

`ObjectManager.runInitialS3kLoadThenExecutePass` has this exact signature:

```java
public void runInitialS3kLoadThenExecutePass(
        int cameraX,
        PlayableEntity player,
        List<? extends PlayableEntity> sidekicks)
```

Its implementation extracts a private helper from the existing two-axis branch:

```java
private void runS3kLoadThenExecute(
        int cameraX,
        PlayableEntity player,
        List<? extends PlayableEntity> sidekicks,
        boolean incrementDispatchCounter)
```

For the initial setup pass, `incrementDispatchCounter` is `true` because `ObjectManager.frameCounter` counts object-dispatch passes, including the native setup `Process_Sprites`. `vblaCounter` remains unchanged because no VBlank is synthesized. The helper performs:

```java
frameCounter++;
updateCameraBounds();
placement.update(cameraX);
syncActiveSpawnsLoad(false);
cleanupDestroyedDynamicObjects();
runExecLoop(cameraX, player, sidekicks, false, false);
flushPostExecDynamicSpawns();
captureCollisionResponseListForNextFrame();
```

It does not call `touchResponses`, `solidContacts.beginInlineFrame`, `advanceGlobalOscillation`, `RingManager`, `Camera.updatePosition`, `LevelManager.update`, or playable sprite updates.

## Implementation Plan

### Task 1: Correct S3K Locked Title-Card Object and VBlank Ownership

**Files:**
- Modify: `src/main/java/com/openggf/game/TitleCardProvider.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/titlecard/Sonic3kTitleCardManager.java`
- Modify: `src/main/java/com/openggf/GameLoop.java`
- Modify: `src/main/java/com/openggf/level/LevelManager.java`
- Modify: `src/test/java/com/openggf/tests/TestTitleCardObjectExecution.java`

**Interfaces:**
- Produces: `TitleCardProvider.shouldAdvanceVblankClockDuringLockedPhase(): boolean`
- Produces: `LevelManager.advanceTitleCardVblankOnly(): void`
- Preserves: S1 `shouldRunLevelObjectsDuringLockedPhase() == false`
- Preserves: S2 full `LevelFrameStep` title-card path

- [ ] **Step 1: Write failing S3K locked-card tests**

Change `titleCardLegacyPath_s3kAiz1` to expect zero `ObjectManager.frameCounter` delta and a five-count `vblaCounter` delta. Snapshot the first active level object's routine/debug state and `GameServices.rng().getSeed()` before stepping, then assert both remain unchanged after five locked frames.

Add provider-policy assertions:

```java
assertFalse(provider.shouldRunLevelObjectsDuringLockedPhase());
assertTrue(provider.shouldAdvanceVblankClockDuringLockedPhase());
```

- [ ] **Step 2: Run the focused tests and confirm the old approximation fails**

Run:

```bash
mvn -Dmse=off \
  -Dtest=com.openggf.tests.TestTitleCardObjectExecution \
  -Ds3k.rom.path='Sonic and Knuckles & Sonic 3 (W) [!].gen' \
  -Ds1.rom.path='Sonic The Hedgehog (W) (REV01) [!].gen' \
  -Ds2.rom.path='Sonic The Hedgehog 2 (W) (REV01) [!].gen' test
```

Expected: S3K fails because object frame delta is 5 instead of 0 and no VBlank-only provider capability exists; S1/S2 assertions retain their prior results.

- [ ] **Step 3: Add the provider and VBlank-only production APIs**

Add the default method to `TitleCardProvider`, override both S3K methods, and add:

```java
public void advanceTitleCardVblankOnly() {
    if (objectManager != null) {
        objectManager.advanceVblaCounter();
    }
}
```

In the locked non-player branch of `GameLoop.updateTitleCardMode`, replace implicit clocking through object execution with:

```java
if (tcpCard.shouldRunLevelObjectsDuringLockedPhase()) {
    levelManager.updateObjectPositions();
} else if (tcpCard.shouldAdvanceVblankClockDuringLockedPhase()) {
    levelManager.advanceTitleCardVblankOnly();
}
```

Keep the existing oscillator suppression for paths that still execute objects; do not advance oscillator on the VBlank-only path.

- [ ] **Step 4: Run title-card and cross-game guards**

Run the Step 2 command and:

```bash
mvn -Dmse=off \
  -Dtest='com.openggf.tests.TestS3kAiz1SkipHeadless,com.openggf.tests.TestSonic3kLevelLoading,com.openggf.tests.TestArchitecturalSourceGuard' \
  -Ds3k.rom.path='Sonic and Knuckles & Sonic 3 (W) [!].gen' test
```

Expected: all selected tests pass; locked S3K level objects and RNG remain unchanged while VBlank advances exactly five.

- [ ] **Step 5: Commit Task 1**

```bash
git add src/main/java/com/openggf/game/TitleCardProvider.java \
  src/main/java/com/openggf/game/sonic3k/titlecard/Sonic3kTitleCardManager.java \
  src/main/java/com/openggf/GameLoop.java \
  src/main/java/com/openggf/level/LevelManager.java \
  src/test/java/com/openggf/tests/TestTitleCardObjectExecution.java
git commit -m "fix(s3k): separate title-card and level object dispatch"
```

### Task 2: Add the Dedicated S3K Initial Object Setup Primitive

**Files:**
- Modify: `src/main/java/com/openggf/level/objects/ObjectManager.java`
- Create: `src/test/java/com/openggf/level/objects/TestObjectManagerInitialS3kSetupPass.java`

**Interfaces:**
- Produces: `ObjectManager.runInitialS3kLoadThenExecutePass(int, PlayableEntity, List<? extends PlayableEntity>): void`
- Consumes: existing two-axis `ObjectPlacementController`, `syncActiveSpawnsLoad(false)`, `runExecLoop`, `flushPostExecDynamicSpawns`

- [ ] **Step 1: Write failing setup-pass isolation tests**

Build a two-axis S3K `ObjectManager` fixture with one visible probe spawn and one out-of-window spawn. The visible probe increments an update count, creates one dynamic child, and exposes whether touch response ran. Assert:

```java
assertEquals(1, visibleProbe.updateCount());
assertEquals(0, hiddenProbe.updateCount());
assertEquals(objectFrameBefore + 1, manager.getFrameCounter());
assertEquals(vblankBefore, manager.getVblaCounter());
assertEquals(0, touchProbe.touchCount());
assertTrue(manager.getActiveObjects().contains(dynamicChild));
```

Capture player and sidekick position, velocity, status, animation, mapping, CPU routine, and history cursor before the pass and assert byte-for-byte equality afterward.

- [ ] **Step 2: Run the new class and confirm the API is absent**

Run:

```bash
mvn -Dmse=off \
  -Dtest=com.openggf.level.objects.TestObjectManagerInitialS3kSetupPass test
```

Expected: test compilation fails because `runInitialS3kLoadThenExecutePass` is undefined.

- [ ] **Step 3: Extract the minimal load-then-execute helper**

Implement the signatures in Feature Design. Reuse the current two-axis placement branch instead of duplicating slot logic. Pass `enableTouchResponses=false`, `inlineSolidResolution=false`, and `solidPostMovement=false`; do not call normal `update`.

- [ ] **Step 4: Verify setup isolation and ordinary object updates**

Run:

```bash
mvn -Dmse=off \
  -Dtest='com.openggf.level.objects.TestObjectManagerInitialS3kSetupPass,com.openggf.level.objects.TestObjectPlacementManager,com.openggf.level.objects.TestObjectManagerVerticalPlacement,com.openggf.tests.TestTraceReplayInvariantGuard' test
```

Expected: every selected test passes; existing ordinary S3K update ordering is unchanged.

- [ ] **Step 5: Commit Task 2**

```bash
git add src/main/java/com/openggf/level/objects/ObjectManager.java \
  src/test/java/com/openggf/level/objects/TestObjectManagerInitialS3kSetupPass.java
git commit -m "feat(s3k): add initial level-object setup pass"
```

### Task 3: Add Production Lifecycle Ownership, Consumption, Convergence, and Rewind

**Files:**
- Create: `src/main/java/com/openggf/game/InitialObjectSetupLifecycle.java`
- Modify: `src/main/java/com/openggf/game/LevelInitProfile.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kLevelInitProfile.java`
- Modify: `src/main/java/com/openggf/level/LevelManager.java`
- Modify: `src/main/java/com/openggf/LevelFrameStep.java`
- Modify: `src/main/java/com/openggf/GameLoop.java`
- Modify: `src/main/java/com/openggf/game/rewind/snapshot/LevelSnapshot.java`
- Modify: `src/main/java/com/openggf/level/rewind/LevelRewindSnapshotAdapter.java`
- Modify: `src/test/java/com/openggf/game/TestPostLoadAssemblyBehavior.java`
- Create: `src/test/java/com/openggf/tests/TestS3kInitialObjectSetupLifecycle.java`
- Modify: `src/test/java/com/openggf/level/rewind/TestLevelRewindSnapshotAdapter.java`

**Interfaces:**
- Produces: `InitialObjectSetupLifecycle`
- Produces: `LevelInitProfile.initialObjectSetupLifecycle(): InitialObjectSetupLifecycle`
- Produces: `LevelManager.consumePendingInitialObjectSetupPass(): boolean`
- Produces rewind-specific snapshot accessors:

```java
public InitialObjectSetupLifecycle capturePendingInitialObjectSetupLifecycleForRewind()
public void restorePendingInitialObjectSetupLifecycleForRewind(
        InitialObjectSetupLifecycle lifecycle)
```

- Consumes: Task 2 `ObjectManager.runInitialS3kLoadThenExecutePass`

- [ ] **Step 1: Write failing profile and one-shot tests**

In `TestPostLoadAssemblyBehavior`, assert S1/S2 return `NONE`, S3K returns `S3K_LOAD_THEN_EXECUTE_ONCE`, and the S3K step order is:

```text
SpawnSidekick
InitZonePlayerState
ArmInitialObjectSetupLifecycle
RequestTitleCard
```

In `TestS3kInitialObjectSetupLifecycle`, load CNZ with a title-card request, assert the token is pending, consume once, assert one object dispatch occurred, and assert a second consume returns false without changing counters or object state.

- [ ] **Step 2: Run the new lifecycle tests and confirm missing types fail**

Run:

```bash
mvn -Dmse=off \
  -Dtest='com.openggf.game.TestPostLoadAssemblyBehavior,com.openggf.tests.TestS3kInitialObjectSetupLifecycle' \
  -Ds3k.rom.path='Sonic and Knuckles & Sonic 3 (W) [!].gen' test
```

Expected: compilation fails because the lifecycle enum, profile method, step, and consumer do not exist.

- [ ] **Step 3: Implement typed arming and one-shot consumption**

Create the enum and profile default exactly as specified under Architecture Decision. Add a protected S3K profile step:

```java
protected InitStep armInitialObjectSetupLifecycleStep() {
    return new InitStep(
            "ArmInitialObjectSetupLifecycle",
            "S3K: Load_Sprites then setup Process_Sprites before LevelLoop",
            GameServices.level()::armInitialObjectSetupLifecycleFromActiveProfile);
}
```

Expose `armInitialObjectSetupLifecycleFromActiveProfile` only at the narrowest visibility that permits the profile callback; it must take no trace or metadata argument. It clears stale state before copying the active profile value.

At the start of `LevelFrameStep.execute`, call `levelManager.consumePendingInitialObjectSetupPass()`. At title-card release in `GameLoop.updateTitleCardMode`, call the same method before `exitTitleCard()`.

- [ ] **Step 4: Add rewind capture and reset coverage**

Add `InitialObjectSetupLifecycle pendingInitialObjectSetupLifecycle` to `LevelSnapshot`. Capture and restore it in `LevelRewindSnapshotAdapter`. Add two tests:

```java
assertEquals(S3K_LOAD_THEN_EXECUTE_ONCE, restoredPendingLifecycle);
assertEquals(NONE, restoredConsumedLifecycle);
```

Extend teardown/reset tests to assert a failed/new load cannot consume the prior level's token.

- [ ] **Step 5: Prove live/headless convergence**

In `TestS3kInitialObjectSetupLifecycle`, create two fresh CNZ runtimes from the same configuration and RNG seed:

- visible path: advance the production title-card provider through locked frames, consume at release;
- headless path: initialize the same VBlank phase, consume before its first level frame.

After consumption and before ordinary gameplay, compare:

```java
assertEquals(visible.objectSnapshot(), headless.objectSnapshot());
assertEquals(visible.placementSnapshot(), headless.placementSnapshot());
assertEquals(visible.rngSeed(), headless.rngSeed());
assertEquals(visible.playerSnapshot(), headless.playerSnapshot());
assertEquals(visible.sidekickSnapshot(), headless.sidekickSnapshot());
```

The test must include a CNZ balloon and assert one RNG call/angle initialization in each path.

- [ ] **Step 6: Run lifecycle, rewind, title-card, and must-keep-green tests**

Run:

```bash
mvn -Dmse=off \
  -Dtest='com.openggf.tests.TestS3kInitialObjectSetupLifecycle,com.openggf.game.TestPostLoadAssemblyBehavior,com.openggf.level.rewind.TestLevelRewindSnapshotAdapter,com.openggf.tests.TestTitleCardObjectExecution,com.openggf.tests.TestS3kAiz1SkipHeadless,com.openggf.tests.TestSonic3kLevelLoading,com.openggf.game.sonic3k.TestSonic3kBootstrapResolver,com.openggf.game.sonic3k.TestSonic3kDecodingUtils' \
  -Ds3k.rom.path='Sonic and Knuckles & Sonic 3 (W) [!].gen' test
```

Expected: all selected tests pass with zero failures/errors.

- [ ] **Step 7: Commit Task 3**

```bash
git add src/main/java/com/openggf/game/InitialObjectSetupLifecycle.java \
  src/main/java/com/openggf/game/LevelInitProfile.java \
  src/main/java/com/openggf/game/sonic3k/Sonic3kLevelInitProfile.java \
  src/main/java/com/openggf/level/LevelManager.java \
  src/main/java/com/openggf/LevelFrameStep.java \
  src/main/java/com/openggf/GameLoop.java \
  src/main/java/com/openggf/game/rewind/snapshot/LevelSnapshot.java \
  src/main/java/com/openggf/level/rewind/LevelRewindSnapshotAdapter.java \
  src/test/java/com/openggf/game/TestPostLoadAssemblyBehavior.java \
  src/test/java/com/openggf/tests/TestS3kInitialObjectSetupLifecycle.java \
  src/test/java/com/openggf/level/rewind/TestLevelRewindSnapshotAdapter.java
git commit -m "feat(s3k): own initial object setup in level lifecycle"
```

### Task 4: Consume Production Setup in Replay and Prove Standalone CNZ

**Files:**
- Modify: `src/main/java/com/openggf/trace/replay/TraceReplaySessionBootstrap.java`
- Modify: `src/main/java/com/openggf/trace/TraceReplayBootstrap.java`
- Modify: `src/test/java/com/openggf/tests/trace/TestTraceReplayStartPositionPolicy.java`
- Modify: `src/test/java/com/openggf/tests/trace/s3k/TestS3kCnzTraceReplay.java`

**Interfaces:**
- Consumes: Task 3 `LevelManager.consumePendingInitialObjectSetupPass(): boolean`
- Preserves: `TraceReplaySessionBootstrap.applyInitialRngSeedForReplay(TraceMetadata): void`
- Removes: standalone S3K selection through `usesSidekickTitleCardSeedFrame`, trace profile, sidekick metadata, or `levelObjectTitleCardPreludeFramesForTraceReplay`

- [ ] **Step 1: Write failing comparison-only and metadata-variance tests**

Add a start-policy test that loads the same production S3K level lifecycle and varies these metadata values independently:

```text
trace_profile
sidekick_characters
checkpoint names
recording filename
rng_seed
start position
```

Assert the production token is pending before bootstrap and consumed exactly once afterward for every variant. Assert changing metadata without a production token never creates or executes a pass.

In `TestS3kCnzTraceReplay`, assert before frame zero:

```java
assertFalse(levelManager.consumePendingInitialObjectSetupPass());
assertEquals(recordedBalloonAngle, liveBalloonAngle);
assertEquals(trace.metadata().rngSeed(), GameServices.rng().getSeed());
```

The first assertion proves bootstrap already consumed the one-shot token.

- [ ] **Step 2: Run focused tests and confirm CNZ remains at frame 185**

Run:

```bash
mvn -Dmse=off \
  -Dtest='com.openggf.tests.trace.TestTraceReplayStartPositionPolicy,com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace' \
  -Dsurefire.argLine='-Xshare:off -Xmx6g' -Dsurefire.forkCount=1 \
  -Ds3k.rom.path='Sonic and Knuckles & Sonic 3 (W) [!].gen' test
```

Expected before implementation: metadata-variance assertions fail because replay does not consume a production token; CNZ remains expected-red at frame 185 `y_speed`.

- [ ] **Step 3: Consume the token at the correct bootstrap seam**

In `TraceReplaySessionBootstrap.applyBootstrap`, after ring-floor/VInt initialization and before `applyInitialRngSeedForReplay`, add:

```java
if (gameplayMode != null && gameplayMode.getLevelManager() != null) {
    gameplayMode.getLevelManager().consumePendingInitialObjectSetupPass();
}
applyInitialRngSeedForReplay(trace.metadata());
```

Delete standalone S3K replay-owned setup selection. Do not alter S1/S2 title-card preludes or the existing S3K complete-run restoration branch.

- [ ] **Step 4: Run focused CNZ and policy guards**

Run the Step 2 command plus:

```bash
mvn -Dmse=off \
  -Dtest='com.openggf.tests.TestArchitecturalSourceGuard,com.openggf.tests.TestTraceReplayInvariantGuard,com.openggf.tests.trace.TestTraceHydrateSwitchDefault,com.openggf.trace.TestPreludeFramesKnobsZero' test
```

Expected: policy/guard tests pass. Standalone CNZ advances beyond frame 185; the expected next frontier is frame 1558 `tails_cpu_interact`, matching the historical causal experiment without its forbidden selection predicate.

- [ ] **Step 5: Commit Task 4**

```bash
git add src/main/java/com/openggf/trace/replay/TraceReplaySessionBootstrap.java \
  src/main/java/com/openggf/trace/TraceReplayBootstrap.java \
  src/test/java/com/openggf/tests/trace/TestTraceReplayStartPositionPolicy.java \
  src/test/java/com/openggf/tests/trace/s3k/TestS3kCnzTraceReplay.java
git commit -m "fix(trace): consume production S3K setup lifecycle"
```

### Task 5: Cross-Game, Complete-Run, Rewind, and Documentation Gate

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `docs/status/trace-frontier-log.md`
- Modify only if a measured intentional discrepancy changes: `docs/status/known-discrepancies.md`

**Interfaces:**
- Consumes: Tasks 1-4 complete implementation
- Produces: exact verification evidence and frontier documentation

- [ ] **Step 1: Run focused lifecycle and policy suite**

```bash
mvn -Dmse=off \
  -Dtest='com.openggf.tests.TestS3kInitialObjectSetupLifecycle,com.openggf.level.objects.TestObjectManagerInitialS3kSetupPass,com.openggf.tests.TestTitleCardObjectExecution,com.openggf.game.TestPostLoadAssemblyBehavior,com.openggf.level.rewind.TestLevelRewindSnapshotAdapter,com.openggf.tests.TestArchitecturalSourceGuard,com.openggf.tests.TestTraceReplayInvariantGuard,com.openggf.tests.trace.TestTraceHydrateSwitchDefault,com.openggf.trace.TestPreludeFramesKnobsZero' \
  -Ds3k.rom.path='Sonic and Knuckles & Sonic 3 (W) [!].gen' test
```

Expected: zero failures/errors.

- [ ] **Step 2: Run standalone and must-keep-green S3K routes**

```bash
mvn -Dmse=off -Dtrace.frontierOnly=true -Dtrace.context.radius=20 \
  -Dtest='com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay,com.openggf.tests.trace.s3k.TestS3kAizTraceReplay,com.openggf.tests.trace.s3k.TestS3kMgzTraceReplay,com.openggf.tests.trace.s3k.TestS3kSpecialStageTraceReplay,com.openggf.tests.TestS3kAiz1SkipHeadless,com.openggf.tests.TestSonic3kLevelLoading,com.openggf.game.sonic3k.TestSonic3kBootstrapResolver,com.openggf.game.sonic3k.TestSonic3kDecodingUtils' \
  -Dsurefire.argLine='-Xshare:off -Xmx6g' -Dsurefire.forkCount=1 \
  -Ds3k.rom.path='Sonic and Knuckles & Sonic 3 (W) [!].gen' test
```

Expected: CNZ first error is later than frame 185; AIZ and MGZ retain their pre-change first frame/field; special-stage and must-keep-green classes pass.

- [ ] **Step 3: Prove complete-run non-goal did not regress**

Run each class in an isolated Maven invocation to avoid report basename and singleton contamination:

```bash
for test in \
  TestS3kAizCompleteRunTraceReplay \
  TestS3kCnzCompleteRunTraceReplay \
  TestS3kHczCompleteRunTraceReplay \
  TestS3kIczCompleteRunTraceReplay \
  TestS3kLbzCompleteRunTraceReplay \
  TestS3kMgzCompleteRunTraceReplay \
  TestS3kMhzCompleteRunTraceReplay
do
  mvn -Dmse=off -Dtrace.frontierOnly=true -Dtrace.context.radius=20 \
    -Dtest="com.openggf.tests.trace.s3k.${test}" \
    -Dsurefire.argLine='-Xshare:off -Xmx6g' -Dsurefire.forkCount=1 \
    -Ds3k.rom.path='Sonic and Knuckles & Sonic 3 (W) [!].gen' test
done
```

Expected: every class retains its established pre-change first frame/field. Record exact error count deltas; do not classify a downstream-count change as a frontier move.

- [ ] **Step 4: Run S1/S2 cross-game title-card and green traces**

```bash
mvn -Dmse=off \
  -Dtest='com.openggf.tests.TestTitleCardObjectExecution,com.openggf.tests.trace.s1.TestS1Ghz1TraceReplay,com.openggf.tests.trace.s2.TestS2Ehz1TraceReplay,com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay' \
  -Ds1.rom.path='Sonic The Hedgehog (W) (REV01) [!].gen' \
  -Ds2.rom.path='Sonic The Hedgehog 2 (W) (REV01) [!].gen' test
```

Expected: S1/S2 selected traces and title-card policies pass unchanged.

- [ ] **Step 5: Update documentation with measured evidence**

Add a concise `CHANGELOG.md` entry describing the production title-card/object lifecycle correction and standalone CNZ frontier move. Add a `docs/status/trace-frontier-log.md` entry containing:

- worktree, branch, and verified commit;
- every exact command above;
- before/after CNZ error count, first frame, field, expected value, and actual value;
- AIZ/MGZ and complete-run held frontiers;
- S1/S2 and special-stage pass counts;
- ROM citations;
- explicit statement that trace data remained comparison-only and complete-run f0 was not addressed.

Update `docs/status/known-discrepancies.md` only if the implementation changes a documented intentional contract.

- [ ] **Step 6: Run documentation and repository guards**

```bash
mvn -Dmse=off \
  -Dtest='com.openggf.tests.TestArchitecturalSourceGuard,com.openggf.tests.TestBuildToolingGuard,com.openggf.trace.TestTraceFixtureCompressionGuard,com.openggf.game.rewind.coverage.TestRewindCoverageGuard,com.openggf.game.rewind.coverage.TestStaticStateRewindCoverageGuard' test
git diff --check
```

Expected: all guards pass and `git diff --check` prints no output.

- [ ] **Step 7: Commit Task 5**

```bash
git add CHANGELOG.md docs/status/trace-frontier-log.md
git commit -m "docs(trace): record S3K setup lifecycle frontier"
```

When Step 5 changes the intentional-discrepancy contract, stage it explicitly before
the commit:

```bash
git add docs/status/known-discrepancies.md
```

## Integration Report

Implementation workers must append one immutable evidence block beneath this heading
before integration. The block is an execution contract whose populated values satisfy
these field definitions:

| Evidence field | Required recorded value |
|---|---|
| Implementation branch | Exact branch name and worktree path |
| Task commits in execution order | Full commit hashes for Tasks 1-5 |
| Final verified commit | Full hash used for the final commands |
| Dirty-state audit | Exact `git status --short` output classification |
| CNZ standalone before | Error count, first frame, field, expected value, actual value |
| CNZ standalone after | Error count, first frame, field, expected value, actual value |
| Title-card locked-phase result | S3K object delta, VBlank delta, RNG delta |
| Setup isolation result | Object, VBlank, level-frame, player, sidekick, touch, and oscillator deltas |
| Rewind result | Pending-token and consumed-token round-trip outcomes |
| AIZ standalone canary | Error count and first frame/field |
| MGZ standalone canary | Error count and first frame/field |
| Complete-run canaries | One line per class with error count and first frame/field |
| S3K special-stage result | Tests run, failures, errors, warnings |
| S1 canaries | Tests run, failures, errors, first divergence when expected-red |
| S2 canaries | Tests run, failures, errors, first divergence when expected-red |
| Policy and architecture guards | Command, test count, failures, errors |
| Documentation files updated | Exact staged paths |
| Regressions introduced | Empty list when none; otherwise class, old frontier, new frontier |
| Reviewer decision | `approve`, `revise`, or `reject`, plus the evidence-based reason |

Every value must come from a fresh command in Task 5. Expected-red traces are reported by their own Surefire class line and `target/trace-reports` first divergence, not Maven's aggregate exit status. If a command cannot run, the report records the command, exit status, environmental cause, and the narrower command that supplied replacement evidence.

## End-to-End Review Checklist

- [ ] The implementation used `superpowers:subagent-driven-development`, one implementation worker per task, followed by spec-compliance and code-quality review.
- [ ] `InitialObjectSetupLifecycle` is typed, defaults to `NONE`, and only S3K opts in.
- [ ] No trace/profile/zone/route/frame/sidekick/checkpoint/filename/outcome predicate arms or selects setup.
- [ ] Locked pre-level S3K title cards do not execute already-loaded level objects.
- [ ] Locked S3K title cards advance VBlank only; level frame, oscillator, player, events, camera scroll, touches, and RNG remain unchanged.
- [ ] Setup consumption follows S3K load-then-execute placement and runs exactly once.
- [ ] Setup increments only the audited object-dispatch counter and does not synthesize VBlank.
- [ ] Player and sidekick state are unchanged by the object-only pass.
- [ ] Replay initializes counter phases, consumes the production token, then installs frame-zero RNG.
- [ ] A replay without a production token cannot execute setup.
- [ ] Pending lifecycle rewind/reset/load-failure behavior is covered.
- [ ] Standalone CNZ advances beyond frame 185 through native object/RNG state.
- [ ] AIZ/MGZ standalone frontiers hold.
- [ ] Complete-run f0 gaps are unchanged and remain outside this feature's scope.
- [ ] S3K special stage remains green.
- [ ] S1/S2 title-card and green trace behavior remains unchanged.
- [ ] `CHANGELOG.md` and `docs/status/trace-frontier-log.md` contain exact measured evidence.
- [ ] Required source, rewind, trace, compression, and tooling guards pass.
- [ ] No ROM, trace payload, disassembly symlink, generated report, or unrelated dirty file is staged.
- [ ] All commit trailers satisfy `.githooks/run-policy`; no commit used `--no-verify`.

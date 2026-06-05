# Spilled-Ring Object Model (Obj37) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Model spilled (lost) rings as real ROM Obj37 objects that execute physics in the object loop at the ROM frame point and participate in the unified slot-ordered touch loop, so a lower-slot spilled ring suppresses a later hazard exactly as ROM does — fixing mtz2 f641 without regressing mcz1.

**Architecture:** Introduce a game-agnostic `LostRingObjectInstance` (Obj37) under `level.rings` that owns per-ring physics/collision and is driven through the normal dynamic-object exec + touch path. Keep the global spill-spin animation (`Ring_spill_anim_*`) in a shared owner. Add a type-keyed every-frame lost-ring touch branch (collect-if-not-invulnerable, then break) before the SPECIAL edge-trigger gate. Add a central `LostRingRewindCodec` for dynamic recreate-on-seek. The legacy `LostRingPool` per-ring physics/collection/rewind is removed only after the object path is green.

**Tech Stack:** Java 21, JUnit 5 (Jupiter only), Maven. Headless physics via `HeadlessTestRunner`/`SingletonResetExtension`. Trace replay via `*TraceReplay` against ROM captures.

**Spec:** `docs/superpowers/specs/2026-06-05-spilled-ring-object-model-design.md`

---

## Conventions used by every task

**Run a focused unit test (PowerShell, quote the selector):**
```
mvn "-Dtest=com.openggf.<pkg>.<Class>#<method>" test
```

**Run a trace replay (bash, flake-free single fork, parens-free ROM copy):**
```
mvn -q -Dmse=relaxed -Dsurefire.forkCount=1 -DreuseForks=true \
  '-Ds2.rom.path=C:\Users\farre\IdeaProjects\sonic-engine\s2.gen' \
  '-Dtest=TestS2Mtz2LevelSelectTraceReplay#replayMatchesTrace' test
```
Judge a trace by its per-class surefire `Tests run / First error: frame` line and `target/trace-reports/s2_<zone>_report.json` — NOT the MSE project-wide total.

**Commit trailers (every non-master commit):** fill `Changelog`, `Guide`, `Known-Discrepancies`, `S3K-Known-Discrepancies`, `Agent-Docs`, `Configuration-Docs`, `Skills` each with `updated` or `n/a: <reason>`. A `feat`/`fix` touching `src/main/` must set `Changelog: updated` and stage `CHANGELOG.md`. End engine commits with `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. Never `--no-verify`; never `git add -A` (shared repo).

---

## File Structure

| File | Responsibility | New/Modify |
|---|---|---|
| `src/main/java/com/openggf/level/rings/LostRingObjectInstance.java` | ROM Obj37 object: per-ring physics (`updateMovement`), `collision_flags=$47`, render, lost-ring marker. Game-agnostic. | **Create** |
| `src/main/java/com/openggf/level/rings/SpillAnimationState.java` | Global ROM spill-spin owner (`Ring_spill_anim_counter/accum/frame`), ticked once per frame; rewind-snapshottable. | **Create** |
| `src/main/java/com/openggf/level/rings/RingManager.java` | Slim `LostRingPool` to spawner + render registry + shared-spin owner; remove per-ring physics/collection/rewind once cutover is green. | **Modify** |
| `src/main/java/com/openggf/level/objects/ObjectManager.java` | Type-keyed lost-ring touch branch (every-frame collect+break) before edge-trigger gate; register `LostRingRewindCodec`. | **Modify** |
| `src/main/java/com/openggf/level/objects/LostRingRewindCodec.java` | `RewindDynamicObjectCodec` recreate factory for `LostRingObjectInstance`. | **Create** |
| `src/main/java/com/openggf/game/PhysicsFeatureSet.java` | Already exposes `ringFloorCheckMask()`; add any missing lost-ring feature accessors used by the object. | **Modify (if needed)** |
| `src/test/java/com/openggf/level/rings/TestLostRingObjectInstance.java` | Per-ring physics frame-exactness; capacity/allocation. | **Create** |
| `src/test/java/com/openggf/level/objects/TestLostRingTouchOrdering.java` | Ordering invariant, invuln gate, every-frame collect, type-keying guard. | **Create** |
| `src/test/java/com/openggf/level/rings/TestLostRingRewindCodec.java` | Rings reappear on seek; shared-spin sync. | **Create** |

---

## Stage 1 — `LostRingObjectInstance` + shared spill-animation owner

Goal: a real Obj37 object whose physics runs in the object exec loop, with the global spin in a shared owner. The legacy pool still runs collection/rewind in this stage (parallel), so no behavior changes yet — we are building the object path under test before cutover.

### Task 1.1: SpillAnimationState (shared spin owner)

**Files:**
- Create: `src/main/java/com/openggf/level/rings/SpillAnimationState.java`
- Test: `src/test/java/com/openggf/level/rings/TestLostRingObjectInstance.java`

- [ ] **Step 1: Write the failing test** (in a new test file; reset singletons not required — pure object)

```java
package com.openggf.level.rings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class TestLostRingObjectInstance {

    @Test
    void spillAnimationDeceleratesLikeRom() {
        // ROM ChangeRingFrame: accum += counter each frame; frame = (accum >> 9) & 3;
        // counter decrements; counter starts at 0xFF.
        SpillAnimationState anim = new SpillAnimationState();
        anim.reset();                 // counter=0xFF, accum=0, frame=0
        assertEquals(0xFF, anim.counter());
        anim.tick();                  // accum = 0xFF; frame = (0xFF>>9)&3 = 0; counter=0xFE
        assertEquals(0, anim.frame());
        assertEquals(0xFE, anim.counter());
        // advance enough to roll bits 10:9
        for (int i = 0; i < 3; i++) anim.tick();
        // accum after 4 ticks = 0xFF+0xFE+0xFD+0xFC = 0x03FA; (0x03FA>>9)&3 = 1
        assertEquals(1, anim.frame());
    }
}
```

- [ ] **Step 2: Run it to verify it fails** — `mvn "-Dtest=com.openggf.level.rings.TestLostRingObjectInstance#spillAnimationDeceleratesLikeRom" test` → FAIL (class not found).

- [ ] **Step 3: Implement `SpillAnimationState`** — extract the ROM spin math from `RingManager.LostRingPool.updatePhysics` (RingManager.java:1208-1216):

```java
package com.openggf.level.rings;

/**
 * Global ROM spilled-ring spin animation (Ring_spill_anim_counter / _accum / _frame).
 * Shared across all live spilled rings — NOT per-ring. The counter doubles as the
 * decelerating-spin speed input. Ported from RingManager.LostRingPool.updatePhysics
 * (s2.asm ChangeRingFrame). One instance per gameplay session; tick once per frame.
 */
public final class SpillAnimationState {
    static final int INITIAL_COUNTER = 0xFF;
    private int counter;
    private int accum;
    private int frame;

    public void reset() { counter = INITIAL_COUNTER; accum = 0; frame = 0; }

    /** Advance the shared spin one frame (no-op once the counter reaches 0). */
    public void tick() {
        if (counter > 0) {
            accum = (accum + counter) & 0xFFFF;   // ROM: add counter to accumulator
            frame = (accum >> 9) & 3;             // ROM: rol.w #7 / andi.w #3 → bits 10:9
            counter--;
        }
    }

    public int counter() { return counter; }
    public int accum() { return accum; }
    public int frame() { return frame; }

    // Rewind: small explicit snapshot (global state, not per-ring).
    public int[] snapshot() { return new int[] { counter, accum, frame }; }
    public void restore(int[] s) { counter = s[0]; accum = s[1]; frame = s[2]; }
}
```

- [ ] **Step 4: Run it to verify it passes** — same command → PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/openggf/level/rings/SpillAnimationState.java \
        src/test/java/com/openggf/level/rings/TestLostRingObjectInstance.java
git config core.hooksPath .githooks
git commit -m "feat(rings): extract shared spilled-ring spin owner (SpillAnimationState)

Changelog: updated
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a"
```
(Stage `CHANGELOG.md` with a one-line entry too; the hook requires it for a `feat` touching `src/main`.)

### Task 1.2: LostRingObjectInstance physics (object-loop `updateMovement`)

**Files:**
- Create: `src/main/java/com/openggf/level/rings/LostRingObjectInstance.java`
- Test: `src/test/java/com/openggf/level/rings/TestLostRingObjectInstance.java`

Mirror an existing minimal `AbstractObjectInstance` subclass for the boilerplate (constructor, `updateMovement`, `getCollisionSizeIndex`, render). Good reference: a simple dynamic object in `com.openggf.level.objects` (e.g. `AbstractPointsObjectInstance` subclasses) and `AbstractBadnikInstance`.

- [ ] **Step 1: Write the failing test** — port the per-ring physics assertions from `LostRingPool.updatePhysics` (RingManager.java:1240-1283). The object holds the same fields as `LostRing` (xSubpixel/ySubpixel/xVel/yVel/lifetime/phaseOffset).

```java
@Test
void ringBouncePhysicsMatchesLegacyPool() {
    // Flat-ground-free fall: velocity integrates, gravity adds each frame.
    LostRingObjectInstance ring = LostRingObjectInstance.forTest(
            /*x*/0x100, /*y*/0x100, /*xVel*/0x0200, /*yVel*/-0x0400, /*phase*/0, /*lifetime*/0xFF);
    int y0 = ring.getCentreY();
    ring.stepPhysicsForTest(/*gravity*/0x18, /*floorCheck*/false);
    // y += yVel (>>8 into pixels handled by subpixel), yVel += gravity
    assertEquals(0x100 + 0x0200 /*xVel pixels carry*/ , ring.getXSubpixelForTest() );
    assertEquals(-0x0400 + 0x18, ring.getYVelForTest());
}
```

(Use the same fixed-point contract as `LostRing`: `addXSubpixel(xVel)`, `addYSubpixel(yVel)`, `addYVel(gravity)`. Keep the test assertions identical to the legacy math so frame-exactness is provable.)

- [ ] **Step 2: Run → FAIL** (class/methods absent).

- [ ] **Step 3: Implement `LostRingObjectInstance`.** Extend `AbstractObjectInstance` (or the closest minimal base). Carry the `LostRing` fields. `updateMovement()` runs ONE ring physics step using the math relocated verbatim from `LostRingPool.updatePhysics` (velocity integrate, gravity, per-game floor-check via `services()` feature set, lifetime decrement, off-bottom/lifetime delete via `services().objectLifetimeOps()`/`setDestroyed`). The displayed mapping frame = `sharedSpillAnimFrame + phaseOffset` (read from the shared owner; injected). Expose `static forTest(...)` and small `*ForTest()` accessors plus `stepPhysicsForTest(gravity, floorCheck)` that calls the same private step with a fixed gravity and skips world-collision when `floorCheck` is false, so physics is unit-testable without a loaded level.

Key methods:
- `@Override public int getCollisionSizeIndex()` → size index `7` (so `collision_flags` low bits = `$07`).
- `public int getCollisionFlags()` → `0x47` while not collected, else `0` (mirrors `Sonic1RingInstance.java:167` pattern but on the lost-ring object).
- `public boolean isLostRingCollectible()` → `true` (the type marker used by the touch branch in Stage 2).
- `@Override protected void updateMovement()` → one physics step (the relocated math).
- Rendering: `appendRenderCommands(...)` using the shared spin frame + phase, mirroring `RingRenderer`’s spilled-ring draw.

- [ ] **Step 4: Run → PASS.**

- [ ] **Step 5: Commit** (`feat(rings): LostRingObjectInstance Obj37 with object-loop physics`).

### Task 1.3: Spawn LostRingObjectInstance objects (parallel to legacy, behind a flag)

**Files:**
- Modify: `src/main/java/com/openggf/level/rings/RingManager.java` (LostRingPool.spawnLostRings, ~1145)
- Modify: `src/main/java/com/openggf/level/objects/ObjectManager.java` (add a `spawnLostRingObject(...)` helper using `addDynamicObject` so rings enter `execOrder`)
- Test: `src/test/java/com/openggf/level/rings/TestLostRingObjectInstance.java`

- [ ] **Step 1: Write the failing test** — after `spawnLostRings(player, 4, frame)`, the ObjectManager exec set contains 4 `LostRingObjectInstance`s in ascending slot order, and the shared `SpillAnimationState` is reset (counter `0xFF`).

```java
@Test
void spawnRegistersRingObjectsInSlotOrder() {
    // Use a headless ObjectManager fixture (see TestObjectManagerChildSlotAllocation for setup).
    // ... spawn 4 rings ...
    List<LostRingObjectInstance> rings = objectManager.activeObjectsOfType(LostRingObjectInstance.class);
    assertEquals(4, rings.size());
    for (int i = 1; i < rings.size(); i++) {
        assertTrue(rings.get(i).getSlotIndex() > rings.get(i - 1).getSlotIndex());
    }
}
```

- [ ] **Step 2: Run → FAIL.**

- [ ] **Step 3: Implement.** In `LostRingPool.spawnLostRings`, after computing each ring's position/velocity/phase, construct a `LostRingObjectInstance` and register it via the new `ObjectManager.spawnLostRingObject(ring)` which calls the existing `addDynamicObject` path (so it allocates a slot and joins `execOrder`). Reset the shared `SpillAnimationState`. Keep the legacy `LostRing[]` pool populated too FOR NOW (parallel), gated so collection/rewind still use legacy until Stage 2/4 cut over. Add `ObjectManager.activeObjectsOfType(Class)` test accessor.

- [ ] **Step 4: Run → PASS.** Also run the full existing ring tests to ensure no break:
  `mvn "-Dtest=com.openggf.level.rings.*,com.openggf.level.objects.TestObjectManagerChildSlotAllocation" test` → PASS.

- [ ] **Step 5: Commit** (`feat(rings): spawn LostRingObjectInstance into the object exec loop (parallel to legacy)`).

---

## Stage 2 — Type-keyed every-frame lost-ring touch branch

Goal: the unified touch loop collects lost-ring objects (every frame, type-keyed) and breaks — making a lower-slot ring suppress a later hazard. Cut collection over to the object path; remove legacy `checkLostRingCollection`.

### Task 2.1: Ordering invariant + type-keyed branch

**Files:**
- Modify: `src/main/java/com/openggf/level/objects/ObjectManager.java` (`TouchResponses.processCollisionLoop`, dispatch block ~5392-5422)
- Test: `src/test/java/com/openggf/level/objects/TestLostRingTouchOrdering.java`

- [ ] **Step 1: Write the failing tests** (use the `TestTouchResponseManager` headless fixture pattern):

```java
package com.openggf.level.objects;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
// + fixture imports mirroring TestTouchResponseManager

class TestLostRingTouchOrdering {

    @Test
    void lowerSlotRingSuppressesLaterHazard() {
        // Player overlaps BOTH a lost ring (lower slot) and a HURT hazard (higher slot).
        // Expect: ring collected, hazard NOT triggered this frame (loop broke at the ring).
        // ... build fixture: spawn ring at slot N, hazard at slot N+1, both overlapping ...
        runTouchResponsesForPlayer(player);
        assertTrue(ring.isCollected());
        assertFalse(player.wasHurtThisFrame());
    }

    @Test
    void higherSlotRingDoesNotSuppressEarlierHazard() {
        // Ring at slot N+1, hazard at slot N → hazard fires first (loop breaks at hazard).
        runTouchResponsesForPlayer(player);
        assertTrue(player.wasHurtThisFrame());
        assertFalse(ring.isCollected());
    }

    @Test
    void overlapDuringInvulnerabilityBreaksWithoutCollecting() {
        player.setInvulnerableFrames(120); // >= 90
        runTouchResponsesForPlayer(player); // ring overlaps, lower slot than hazard
        assertFalse(ring.isCollected());
        assertFalse(player.wasHurtThisFrame()); // loop still broke at the ring
    }

    @Test
    void collectsOnceInvulnerabilityDropsWhileStillOverlapping() {
        player.setInvulnerableFrames(91);
        runTouchResponsesForPlayer(player); // frame 1: still invuln (>=90) → no collect, no edge
        assertFalse(ring.isCollected());
        player.setInvulnerableFrames(89);   // dropped below 90, STILL overlapping (no new edge)
        runTouchResponsesForPlayer(player); // frame 2: must collect (every-frame eval, not edge)
        assertTrue(ring.isCollected());
    }

    @Test
    void placedRingObjectNotHandledByLostRingBranch() {
        // An S1 placed ring (Sonic1RingInstance, collision_flags 0x47) must go through
        // its own listener path, NOT the lost-ring branch.
        Sonic1RingInstance placed = ...; // overlapping, in the object loop
        runTouchResponsesForPlayer(player);
        assertTrue(placed.wasCollectedViaListenerPath());      // existing path used
        assertFalse(lostRingBranchHandled(placed));            // NOT the lost-ring branch
    }
}
```

- [ ] **Step 2: Run → FAIL** (lost-ring branch absent; ordering not yet honored for ring objects).

- [ ] **Step 3: Implement the branch.** In `processCollisionLoop`, in the dispatch block (after `if (!overlap) continue; buildingSet.add(instance);`, before the `shouldTrigger` SPECIAL edge-trigger logic at ObjectManager.java:5392), add:

```java
// Type-keyed lost-ring collectible: ROM Touch_ChkValue ring branch (s2.asm:85196-85219).
// Evaluated EVERY frame on overlap (not edge-triggered) so the ring collects the frame
// invulnerable_time drops below 90 while the player is still continuously overlapping.
// Keyed on the LostRingObjectInstance marker, NOT the 0x47 byte shape — so S1 placed
// rings (Sonic1RingInstance, also 0x47) keep their own listener path.
if (instance instanceof LostRingObjectInstance lostRing && lostRing.isLostRingCollectible()) {
    if (player.getInvulnerableFrames() < LOST_RING_INVULNERABLE_THRESHOLD /*90*/
            && !lostRing.isCollected()) {
        lostRing.markCollected(frameCounterForTouch());
        player.addRings(1);
        services().audio()...playSfx(GameSound.RING); // mirror legacy collection SFX
    }
    break; // ROM: rts — the ring was the first overlapping object; stop the loop.
}
```

Place it so it runs for both player and sidekick paths (the sidekick does not collect rings in ROM? — confirm: ROM `TouchResponse` rings use MainCharacter/Sidekick invuln; the break still applies. Match legacy `cannotCollectRings`/sidekick behavior: a sidekick overlap still breaks but only the main character credits rings. Encode that with the existing `isSidekick` flag.)

- [ ] **Step 4: Run → PASS** (all five tests). Then run `TestTouchResponseManager` to confirm no SPECIAL regression:
  `mvn "-Dtest=com.openggf.level.objects.TestTouchResponseManager,com.openggf.level.objects.TestLostRingTouchOrdering" test` → PASS.

- [ ] **Step 5: Commit** (`feat(objects): type-keyed every-frame lost-ring touch branch (collect+break)`).

### Task 2.2: Cut collection over; remove legacy `checkLostRingCollection`

**Files:**
- Modify: `src/main/java/com/openggf/level/rings/RingManager.java` (remove `checkCollection` + `checkLostRingCollection`)
- Modify: `src/main/java/com/openggf/level/LevelManager.java:865` (drop the `checkLostRingCollection` call)
- Test: existing ring tests + the Stage 2.1 tests

- [ ] **Step 1:** Delete the `LevelManager.java:865` `ringManager.checkLostRingCollection(playable)` call and the `RingManager.checkLostRingCollection` / `LostRingPool.checkCollection` methods. Collection now happens only via the touch branch.
- [ ] **Step 2: Run** the Stage-2.1 tests + `mvn "-Dtest=com.openggf.level.rings.*" test` → PASS.
- [ ] **Step 3: Commit** (`refactor(rings): collection now via unified touch loop; remove legacy checkCollection`).

---

## Stage 3 — Atomic slot-allocation contract + 0x20 cap

**Files:**
- Modify: `src/main/java/com/openggf/level/rings/RingManager.java` (`LostRingPool.spawnLostRings`, `allocateSlotIndices` ~1321)
- Test: `src/test/java/com/openggf/level/rings/TestLostRingObjectInstance.java`

- [ ] **Step 1: Write the failing test:**

```java
@Test
void spawnStopsOnAllocationFailureAndCapsAt32() {
    // Pre-fill the dynamic slot pool so only 3 slots remain free.
    // Request 10 rings → exactly 3 LostRingObjectInstances exist, in ascending slot order,
    // and no ring exists for a failed (-1) allocation.
    objectManager.reserveAllButNFreeSlots(3);
    ringManager.spawnLostRings(player, 10, frame);
    var rings = objectManager.activeObjectsOfType(LostRingObjectInstance.class);
    assertEquals(3, rings.size());
    for (int i = 1; i < rings.size(); i++)
        assertTrue(rings.get(i).getSlotIndex() > rings.get(i-1).getSlotIndex());
}

@Test
void neverSpawnsMoreThanRomCapOf32() {
    objectManager.reserveAllButNFreeSlots(64);
    ringManager.spawnLostRings(player, 50, frame);
    assertEquals(0x20, objectManager.activeObjectsOfType(LostRingObjectInstance.class).size());
}
```

- [ ] **Step 2: Run → FAIL.**

- [ ] **Step 3: Implement the atomic contract** in `spawnLostRings`:

```java
int toSpawn = Math.min(ringCount, MAX_LOST_RINGS /*0x20*/);
int previousSlot = currentSpawningObjectSlot; // ROM: allocate after the spilling object
spillAnimation.reset();
int spawned = 0;
for (int i = 0; i < toSpawn; i++) {
    int slot = objectManager.allocateSlotAfter(previousSlot);
    if (slot < 0) {                 // ROM: no free slot → stop spilling
        // log() how many were truncated
        break;
    }
    // compute this ring's pos/vel/phase, construct the instance, register it ON this real slot
    LostRingObjectInstance ring = buildRing(slot, ...);
    objectManager.spawnLostRingObjectAtSlot(ring, slot);
    previousSlot = slot;
    spawned++;
}
```

Remove the old pre-allocate-then-spawn `allocateSlotIndices` two-phase path (which could allocate slots for rings that then fail to construct). Add `ObjectManager.reserveAllButNFreeSlots(n)` test helper.

- [ ] **Step 4: Run → PASS.**
- [ ] **Step 5: Commit** (`fix(rings): atomic stop-on-(-1) lost-ring slot allocation + 0x20 cap`).

---

## Stage 4 — `LostRingRewindCodec` + retire per-ring snapshot

Goal: rings survive rewind-seek via a dynamic recreate codec; retire the bespoke per-ring snapshot; keep the small shared-spin snapshot.

### Task 4.1: LostRingRewindCodec (recreate-on-seek)

**Files:**
- Create: `src/main/java/com/openggf/level/objects/LostRingRewindCodec.java`
- Modify: `src/main/java/com/openggf/level/objects/ObjectManager.java` (add to `BUILT_IN_REWIND_DYNAMIC_OBJECT_CODECS`, line ~70)
- Test: `src/test/java/com/openggf/level/rings/TestLostRingRewindCodec.java`

- [ ] **Step 1: Write the failing test** — spill rings, snapshot, advance/clear, restore, assert rings reappear with identical positions and spin frame:

```java
@Test
void ringsReappearOnSeekAcrossSpill() {
    ringManager.spawnLostRings(player, 6, frame);
    var before = positionsOf(objectManager.activeObjectsOfType(LostRingObjectInstance.class));
    var snap = rewind.captureObjectManagerSnapshot();
    objectManager.clearDynamicObjects();           // simulate seek wiping live state
    assertEquals(0, objectManager.activeObjectsOfType(LostRingObjectInstance.class).size());
    rewind.restoreObjectManagerSnapshot(snap);     // must recreate via the codec
    var after = positionsOf(objectManager.activeObjectsOfType(LostRingObjectInstance.class));
    assertEquals(before, after);                   // NOT empty → proves the codec recreates
    assertEquals(snap.spillAnimFrame(), spillAnimation.frame()); // shared spin restored
}
```

- [ ] **Step 2: Run → FAIL** (without a codec the rings are diagnostic-only and `after` is empty).

- [ ] **Step 3: Implement the codec** mirroring `pointsCodec` (ObjectManager.java:308-333): `supports(inst) == inst.getClass()==LostRingObjectInstance.class`, `className()`, and `recreate(context, entry)` that rebuilds the ring from `entry.spawn()` + restored fields via `context.objectServices()`. Register it in `BUILT_IN_REWIND_DYNAMIC_OBJECT_CODECS`. Ensure the ring's capturable fields (xSubpixel/ySubpixel/xVel/yVel/lifetime/collected/sparkleStartFrame/phaseOffset) round-trip via the generic field capture + the entry (add codecs/`@RewindTransient` as needed, consistent with other dynamic objects). Capture the shared `SpillAnimationState` in the object-manager (or ring-manager) snapshot via its `snapshot()`/`restore()`.

- [ ] **Step 4: Run → PASS.**
- [ ] **Step 5: Commit** (`feat(rewind): LostRingRewindCodec recreates spilled rings on seek`).

### Task 4.2: Retire legacy per-ring physics + snapshot; keep shared-spin snapshot

**Files:**
- Modify: `src/main/java/com/openggf/level/rings/RingManager.java` (remove `LostRingPool.updatePhysics` per-ring loop + per-ring `RingSnapshot.LostRingEntry` capture/restore at ~740-820; keep shared spin tick + render registry)
- Modify: `src/main/java/com/openggf/level/LevelManager.java:1020` (replace `ringManager.updateLostRingPhysics(frame+1)` with a shared-spin `tick()` only — physics now runs in the object loop)
- Test: `src/test/java/com/openggf/level/rings/TestRingManagerRewindSnapshot.java` (update expectations)

- [ ] **Step 1:** Remove the per-ring physics loop and per-ring snapshot; `RingManager.update`/`LevelManager.update` now only `spillAnimation.tick()` once per frame. Update `TestRingManagerRewindSnapshot` to assert the shared-spin snapshot only (per-ring is gone; rings are covered by the object rewind test).
- [ ] **Step 2: Run** `mvn "-Dtest=com.openggf.level.rings.*,com.openggf.game.rewind.*" test` → PASS.
- [ ] **Step 3: Commit** (`refactor(rings): retire legacy per-ring physics/snapshot; physics runs in object loop`).

---

## Stage 5 — Cross-game gating + occupancy oracle + trace verification

### Task 5.1: Cross-game floor cadence + S3K behaviors via PhysicsFeatureSet

**Files:**
- Modify: `src/main/java/com/openggf/level/rings/LostRingObjectInstance.java` (read `services()` feature set for floor mask + S3K reverse-gravity/lightning-shield)
- Modify: `src/main/java/com/openggf/game/PhysicsFeatureSet.java` (confirm `ringFloorCheckMask()` + any S3K ring flags exist; add only if missing)
- Test: `src/test/java/com/openggf/level/rings/TestLostRingObjectInstance.java`

- [ ] **Step 1: Write the failing tests** — S1 floor-check cadence mask `#3` vs S2/S3K `#7`; S3K reverse-gravity probes the ceiling. Assert the object reads the feature set (port the assertions from the legacy `updatePhysics` per-game branches at RingManager.java:1219-1278).
- [ ] **Step 2: Run → FAIL.**
- [ ] **Step 3: Implement** by relocating the per-game branches verbatim into `LostRingObjectInstance` using `services()`'s feature set (no `gameId` branches). Reverse-gravity uses the S3K flag; lightning-shield attraction preserved as Obj37 behavior gated on the existing S3K shield flag.
- [ ] **Step 4: Run → PASS.**
- [ ] **Step 5: Commit** (`feat(rings): per-game lost-ring cadence + S3K reverse-gravity/shield via PhysicsFeatureSet`).

### Task 5.2: Occupancy oracle — spilled rings occupy slots

**Files:**
- Test: `src/test/java/com/openggf/.../TestS2ObjectOccupancyOracle.java` (or its harness)

- [ ] **Step 1:** Confirm `TestS2ObjectOccupancyOracle` (comparison-only) now sees `Obj37` rings occupying slots consistent with ROM during a spill. If the oracle has an allowlist of slot-occupant ids, add `$37`. Run it: `mvn "-Dtest=...TestS2ObjectOccupancyOracle" test` → PASS (4/4).
- [ ] **Step 2: Commit** if the oracle/allowlist needed an update (`test(objects): oracle recognises Obj37 spilled rings in slots`), else skip.

### Task 5.3: Trace verification — mtz2 advances, mcz1 must not regress

**Files:** none (verification + frontier log).

- [ ] **Step 1: Run mtz2** (bash, single fork):
```
mvn -q -Dmse=relaxed -Dsurefire.forkCount=1 -DreuseForks=true \
  '-Ds2.rom.path=C:\Users\farre\IdeaProjects\sonic-engine\s2.gen' \
  '-Dtest=TestS2Mtz2LevelSelectTraceReplay#replayMatchesTrace' test
```
Expected: first error frame advances past **f641** (the Tails-hurt-timing divergence is gone).

- [ ] **Step 2: Run the regression-critical pair + greens:**
```
mvn -q -Dmse=relaxed -Dsurefire.forkCount=1 -DreuseForks=true \
  '-Ds2.rom.path=C:\Users\farre\IdeaProjects\sonic-engine\s2.gen' \
  '-Dtest=TestS2MczLevelSelectTraceReplay,TestS2Ehz1TraceReplay,TestS2SczLevelSelectTraceReplay,TestS2WfzLevelSelectTraceReplay' test
```
Expected: **mcz1 still first-diverges at f2757 (NOT regressed toward f1665)**; EHZ1/SCZ/WFZ green.

- [ ] **Step 3: Full S1/S2/S3K sweep** (the authoritative pass), parse `target/surefire-reports/*TraceReplay*.txt` for `First error: frame`. Expected: no green regresses (S1 ×10, S2 ehz1/scz/wfz); **no S2/S3K frontier moves backward** vs the pre-change snapshot in `docs/TRACE_FRONTIER_LOG.md`.

- [ ] **Step 4: Update `docs/TRACE_FRONTIER_LOG.md`** with the command, commit/worktree context, mtz2 before→after, the mcz1 hold, and the full-sweep result. **Commit** (`docs(trace): spilled-ring object model — mtz2 advance, mcz1 held, no regression`).

### Task 5.4: Remove dead legacy code

**Files:**
- Modify/Delete: `src/main/java/com/openggf/level/rings/LostRing.java` (delete if fully unused), legacy `LostRing[] ringPool`, `RingSnapshot.LostRingEntry` (if unused).

- [ ] **Step 1:** Delete `LostRing.java` and the legacy pool array / per-ring snapshot record only once nothing references them (grep first: `grep -rn "LostRing\b" src/main`). Keep `LostRingObjectInstance`, `SpillAnimationState`, and the slim spawner.
- [ ] **Step 2: Run** the full ring + object + rewind unit set + the trace pair → PASS.
- [ ] **Step 3: Commit** (`refactor(rings): remove dead legacy LostRing pool after object-model cutover`).

---

## Self-Review (run before execution)

- **Spec coverage:** Stage 1 ↔ object + shared spin; Stage 2 ↔ type-keyed every-frame branch + S1 guard; Stage 3 ↔ atomic allocation/cap; Stage 4 ↔ codec + retire per-ring snapshot, keep shared-spin; Stage 5 ↔ cross-game + oracle + trace bar. All spec sections mapped.
- **Type consistency:** `LostRingObjectInstance` (`isLostRingCollectible()`, `isCollected()`, `markCollected(int)`, `getSlotIndex()`, `getCollisionFlags()→0x47`), `SpillAnimationState` (`reset/tick/frame/counter/accum/snapshot/restore`), `ObjectManager` (`spawnLostRingObjectAtSlot`, `activeObjectsOfType`, `reserveAllButNFreeSlots`, `LOST_RING_INVULNERABLE_THRESHOLD=90`), `LostRingRewindCodec` (mirrors `pointsCodec`). Names are consistent across tasks.
- **Cutover safety:** legacy collection removed only in Stage 2.2 (after the object branch is green); legacy physics/snapshot removed only in Stage 4.2 (after the codec is green); dead code deleted only in Stage 5.4 (after grep-confirmed unused).
- **Verification bar gates the end:** Stage 5.3 enforces mtz2-advances / mcz1-no-regress / no-frontier-regression before the work is considered done.

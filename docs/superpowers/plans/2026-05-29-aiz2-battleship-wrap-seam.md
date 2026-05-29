# AIZ2 Battleship Wrap-Seam Faithful Fix — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the `$80` post-bombing wrap-distance approximation in AIZ Act 2 with the true ROM `$200` camera/object wrap, made visually seamless by phase-locking the AIZ2 forest background to a `$200`-periodic looping source — removing the documented discrepancy.

**Architecture:** Keep the ROM `$200` camera/object renormalization (physics/trace-faithful). During the post-bombing forest loop only, route AIZ through the existing wrapped-BG build path (mirroring the CNZ-boss precedent) and normalize the BG *source-layout query* with an origin-anchored modulo of the loop period, so a `$200` window jump re-samples identical forest columns. All AIZ-specific decisions live in `Sonic3kAIZEvents` / `AizZoneRuntimeState` / `Sonic3kZoneFeatureProvider`; shared BG code consumes semantic predicates only.

**Tech Stack:** Java 21, Maven, JUnit 5 (Jupiter), `HeadlessTestFixture` / `HeadlessTestRunner`, S3K ROM-gated tests (`-Ds3k.rom.path`). Reference: `docs/superpowers/specs/2026-05-29-aiz2-battleship-wrap-seam-design.md`.

---

## File Structure

- `src/main/java/com/openggf/game/sonic3k/events/Sonic3kAIZEvents.java` — restore `$200` wrap; add `isBattleshipForestLoopActive()`, `getForestLoopBgWrapPeriod()`, `getForestLoopBgOrigin()`; remove `$80` constant.
- `src/main/java/com/openggf/game/sonic3k/runtime/AizZoneRuntimeState.java` — delegate the three new signals (mirrors existing delegations).
- `src/main/java/com/openggf/game/sonic3k/Sonic3kZoneFeatureProvider.java` — add a narrow AIZ clause to `bgWrapsHorizontally()` (mirror `isCnzBossBackgroundWindowActive`).
- `src/main/java/com/openggf/level/LevelTilemapManager.java` — extract a pure query-offset helper and use the origin-anchored modulo when the AIZ forest loop is active.
- `src/main/java/com/openggf/level/LevelManager.java` — set `currentBgPeriodWidth` to the loop period during the loop (in `applyBackgroundTilemapWindowSelection`).
- `src/test/java/com/openggf/tests/TestS3kAiz2SidekickBoundsSync.java` — reinstate exact `0x44C0` wrap assertion.
- `src/test/java/com/openggf/tests/TestS3kAiz2ForestLoopSeam.java` *(new)* — pure-helper unit tests + tilemap-build seam guard.
- `docs/S3K_KNOWN_DISCREPANCIES.md`, `CHANGELOG.md`, `docs/TRACE_FRONTIER_LOG.md` — cleanup/attestation.

---

## Task 1: Investigation spike + go/no-go gate (no behavioral change)

This task produces three concrete values that later tasks encode, and decides whether to proceed. **Do not change runtime behavior in this task.**

**Files:**
- Temporary instrumentation only (reverted before commit): `Sonic3kAIZEvents.java`, `LevelTilemapManager.java`.
- Findings note: append to the design spec or a scratch note.

- [ ] **Step 1: Add temporary logging across a wrap frame.** In `Sonic3kAIZEvents.updateBattleshipAutoScroll` (around line 1625, the wrap branch) and in `LevelTilemapManager` (around line 365, where `bgXQueryOffset` is computed), add temporary `LOG.info` lines printing `getBgCameraX()`, `bgTilemapBaseX`, `bgXQueryOffset`, `currentBgPeriodWidth`, `bgContiguousWidthPx`, `battleshipSmoothScrollX`, and the sampled source `mapX`.

- [ ] **Step 2: Drive a headless AIZ2 post-bombing run.** Reuse the `HeadlessTestFixture` AIZ2 setup from `TestS3kAiz2SidekickBoundsSync`. Force the post-bombing state (`onBattleshipComplete()` so `battleshipWrapX == 0x46C0`), then step frames through at least one `$200` wrap with the *current* `$80` build, and capture the log.

Run: `mvn -Dmse=off "-Dsurefire.forkCount=1" "-Ds3k.rom.path=Sonic and Knuckles & Sonic 3 (W) [!].gen" "-Dtest=com.openggf.tests.TestS3kAiz2SidekickBoundsSync" test "-DfailIfNoTests=false"`
Expected: log shows `bgTilemapBaseX` jumping by the wrap distance while `battleshipSmoothScrollX` stays continuous (confirms the window/deform phase break).

- [ ] **Step 3: Determine PERIOD and ORIGIN.** Inspect the AIZ2 level-2 (BG) map data for the forest region: find the horizontal repeat period of the forest strip in source-layout pixels (`FOREST_LOOP_BG_PERIOD`, expected `0x200`) and the source-X at which that repeat begins (`FOREST_LOOP_BG_ORIGIN`). Cross-check against the ROM Plane B forest fill. Record both literal values in the findings note.

- [ ] **Step 4: Confirm `bgWrap` reachability.** Verify that during the post-bombing loop, once `bgWrapsHorizontally()` returns true for AIZ (Task 4), `LevelTilemapManager.bgWrap` (line 341) will be true — i.e. `zoneRuntimeRequiresFullWidthBgTilemap()` does **not** return true in the loop phase (it is the AIZ *intro* full-width path). Record the confirmation.

- [ ] **Step 5: Gate decision.** If PERIOD is a clean repeat from a stable ORIGIN and `bgWrap` is reachable → proceed. If not (forest not periodic, or full-width path suppresses the wrap) → STOP, record the blocker in the findings note, and surface to the user before continuing. Do not force a `$200` wrap onto non-periodic data.

- [ ] **Step 6: Revert all temporary instrumentation.** Confirm `git diff` is empty (no committed change from this task).

Run: `git diff --stat`
Expected: no changes.

---

## Task 2: Restore the ROM `$200` post-bombing wrap

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/events/Sonic3kAIZEvents.java` (wrap-delta selection ~line 1625; constant ~line 200)
- Test: `src/test/java/com/openggf/tests/TestS3kAiz2SidekickBoundsSync.java` (line ~105)

- [ ] **Step 1: Tighten the test to the true ROM value (red).** In `TestS3kAiz2SidekickBoundsSync.aizBattleshipPrePhysicsWrapRefreshesSidekickBoundsBeforeMovement`, replace the relaxed boundary check with the exact ROM wrap result. Replace:

```java
        int wrappedCameraMaxX = fixture.camera().getMaxX() & 0xFFFF;
        assertTrue(wrappedCameraMaxX < 0x46C0,
                "AIZ2_DoShipLoop must wrap Camera_max_X_pos back before Process_Sprites");
        assertEquals(wrappedCameraMaxX, controller.getMaxXBound(Integer.MIN_VALUE) & 0xFFFF,
                "S3K sidekick boundary mirror must be refreshed immediately after the pre-physics camera wrap");
```

with:

```java
        // ROM AIZ2_DoShipLoop subtracts $200 on wrap; from boundary $46C0 the
        // post-wrap Camera_max_X_pos is $46C0 - $200 = $44C0.
        assertEquals(0x44C0, fixture.camera().getMaxX() & 0xFFFF,
                "AIZ2_DoShipLoop wraps Camera_max_X_pos back by the ROM $200 before Process_Sprites");
        assertEquals(0x44C0, controller.getMaxXBound(Integer.MIN_VALUE) & 0xFFFF,
                "S3K sidekick boundary mirror must be refreshed immediately after the pre-physics camera wrap");
```

- [ ] **Step 2: Run the test to verify it fails.**

Run: `mvn -Dmse=off "-Dsurefire.forkCount=1" "-Ds3k.rom.path=Sonic and Knuckles & Sonic 3 (W) [!].gen" "-Dtest=com.openggf.tests.TestS3kAiz2SidekickBoundsSync#aizBattleshipPrePhysicsWrapRefreshesSidekickBoundsBeforeMovement" test "-DfailIfNoTests=false"`
Expected: FAIL — actual `0x4640` (current `$80` wrap), expected `0x44C0`.

- [ ] **Step 3: Restore the `$200` wrap.** In `Sonic3kAIZEvents.updateBattleshipAutoScroll`, replace the phase-dependent wrap delta:

```java
            int wrapDelta = battleshipWrapX == BATTLESHIP_WRAP_X_BOMBING
                    ? BATTLESHIP_WRAP_DIST
                    : BATTLESHIP_WRAP_DIST_POST_BOMBING;
```

with the single ROM value:

```java
            int wrapDelta = BATTLESHIP_WRAP_DIST; // ROM AIZ2_DoShipLoop subtracts $200 in both phases
```

Delete the now-unused constant declaration `private static final int BATTLESHIP_WRAP_DIST_POST_BOMBING = 0x80;` (~line 200).

- [ ] **Step 4: Run the test to verify it passes.**

Run: `mvn -Dmse=off "-Dsurefire.forkCount=1" "-Ds3k.rom.path=Sonic and Knuckles & Sonic 3 (W) [!].gen" "-Dtest=com.openggf.tests.TestS3kAiz2SidekickBoundsSync" test "-DfailIfNoTests=false"`
Expected: PASS (both methods).

- [ ] **Step 5: Commit.**

```bash
git add src/main/java/com/openggf/game/sonic3k/events/Sonic3kAIZEvents.java src/test/java/com/openggf/tests/TestS3kAiz2SidekickBoundsSync.java
git commit -m "fix(s3k): restore ROM \$200 AIZ2 battleship post-bombing wrap

Removes the BATTLESHIP_WRAP_DIST_POST_BOMBING (\$80) visual approximation so
the post-bombing camera/object wrap matches ROM AIZ2_DoShipLoop (\$200). Seam
removal follows in the BG-source-loop tasks.

Changelog: n/a
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Add the forest-loop semantic signals

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/events/Sonic3kAIZEvents.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/runtime/AizZoneRuntimeState.java`
- Test: `src/test/java/com/openggf/game/sonic3k/events/TestSonic3kAizForestLoopSignals.java` *(new)*

- [ ] **Step 1: Write the failing unit test.** Create `src/test/java/com/openggf/game/sonic3k/events/TestSonic3kAizForestLoopSignals.java`. This test exercises the predicate against the raw battleship setters already on `Sonic3kAIZEvents` (e.g. `setBattleshipAutoScrollActiveRaw`, `setBattleshipWrapX`), no ROM required:

```java
package com.openggf.game.sonic3k.events;

import com.openggf.game.sonic3k.Sonic3kLoadBootstrap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic3kAizForestLoopSignals {

    @Test
    void forestLoopActiveOnlyDuringPostBombingAutoScroll() {
        Sonic3kAIZEvents events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);

        // Not active when auto-scroll is off.
        events.setBattleshipAutoScrollActiveRaw(false);
        events.setBattleshipWrapX(0x46C0);
        assertFalse(events.isBattleshipForestLoopActive(),
                "No loop while auto-scroll is inactive");

        // Not active during the bombing phase (wrapX still $4440).
        events.setBattleshipAutoScrollActiveRaw(true);
        events.setBattleshipWrapX(0x4440);
        assertFalse(events.isBattleshipForestLoopActive(),
                "No forest loop during the bombing phase");

        // Active once onBattleshipComplete has moved the wrap boundary to $46C0.
        events.setBattleshipWrapX(0x46C0);
        assertTrue(events.isBattleshipForestLoopActive(),
                "Forest loop active post-bombing with auto-scroll running");
    }

    @Test
    void forestLoopPeriodAndOriginAreExposed() {
        Sonic3kAIZEvents events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        assertEquals(0x200, events.getForestLoopBgWrapPeriod(),
                "Forest loop BG period is the ROM $200");
        // FOREST_LOOP_BG_ORIGIN literal comes from Task 1 findings.
        assertEquals(Sonic3kAIZEvents.FOREST_LOOP_BG_ORIGIN, events.getForestLoopBgOrigin());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails.**

Run: `mvn -Dmse=off "-Dsurefire.forkCount=1" "-Dtest=com.openggf.game.sonic3k.events.TestSonic3kAizForestLoopSignals" test "-DfailIfNoTests=false"`
Expected: FAIL — methods/constant do not exist (compile error).

- [ ] **Step 3: Add the signals.** In `Sonic3kAIZEvents.java`, near the other battleship constants (~line 200), add (use the ORIGIN literal from Task 1):

```java
    /** AIZ2 forest loop BG source repeat period (ROM Plane B forest strip). */
    public static final int FOREST_LOOP_BG_PERIOD = 0x200;
    /** AIZ2 forest loop BG source origin in layout pixels (verified in Task 1 spike). */
    public static final int FOREST_LOOP_BG_ORIGIN = /* <ORIGIN from Task 1 findings> */ 0x0;
```

Near the other battleship accessors (~line 1739, by `isBattleshipAutoScrollActive`), add:

```java
    /**
     * True only while the post-bombing $200-periodic forest loop is running:
     * auto-scroll active AND the wrap boundary has moved to the post-bombing
     * $46C0 (set by {@link #onBattleshipComplete()}). Deliberately narrower than
     * {@link #isBattleshipForestFrontPhaseActive()} (a display-bucket predicate
     * spanning $4380..$4880) so the BG source-loop override is not applied during
     * the non-looping forest handoff.
     */
    public boolean isBattleshipForestLoopActive() {
        return battleshipAutoScrollActive
                && battleshipWrapX == BATTLESHIP_WRAP_X_POST_BOMBING;
    }

    public int getForestLoopBgWrapPeriod() {
        return FOREST_LOOP_BG_PERIOD;
    }

    public int getForestLoopBgOrigin() {
        return FOREST_LOOP_BG_ORIGIN;
    }
```

- [ ] **Step 4: Delegate via `AizZoneRuntimeState`.** In `AizZoneRuntimeState.java`, after `isBattleshipForestFrontPhaseActive()` (line 30), add:

```java
    public boolean isBattleshipForestLoopActive() { return events.isBattleshipForestLoopActive(); }
    public int getForestLoopBgWrapPeriod() { return events.getForestLoopBgWrapPeriod(); }
    public int getForestLoopBgOrigin() { return events.getForestLoopBgOrigin(); }
```

- [ ] **Step 5: Run the test to verify it passes.**

Run: `mvn -Dmse=off "-Dsurefire.forkCount=1" "-Dtest=com.openggf.game.sonic3k.events.TestSonic3kAizForestLoopSignals" test "-DfailIfNoTests=false"`
Expected: PASS.

- [ ] **Step 6: Commit.**

```bash
git add src/main/java/com/openggf/game/sonic3k/events/Sonic3kAIZEvents.java src/main/java/com/openggf/game/sonic3k/runtime/AizZoneRuntimeState.java src/test/java/com/openggf/game/sonic3k/events/TestSonic3kAizForestLoopSignals.java
git commit -m "feat(s3k): expose AIZ2 forest-loop predicate + BG period/origin signals

Adds narrow isBattleshipForestLoopActive() (post-bombing, wrapX==\$46C0) plus
getForestLoopBgWrapPeriod()/getForestLoopBgOrigin(), delegated through
AizZoneRuntimeState. Consumed by the BG window/source-loop wiring next.

Changelog: n/a
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Route AIZ through the wrapped-BG path during the loop

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kZoneFeatureProvider.java` (`bgWrapsHorizontally`, ~line 117)
- Test: `src/test/java/com/openggf/game/sonic3k/TestSonic3kAizBgWrapActivation.java` *(new)*

- [ ] **Step 1: Write the failing test.** Mirror the structure of existing `Sonic3kZoneFeatureProvider` tests (ROM-gated; needs an active AIZ runtime state). Create `src/test/java/com/openggf/game/sonic3k/TestSonic3kAizBgWrapActivation.java`:

```java
package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.game.sonic3k.runtime.AizZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kAizBgWrapActivation {

    @AfterEach
    void tearDown() { com.openggf.game.session.SessionManager.clear(); }

    @Test
    void bgWrapsHorizontallyOnlyDuringAizForestLoop() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_AIZ, 1)
                .build();
        Sonic3kZoneFeatureProvider provider =
                (Sonic3kZoneFeatureProvider) GameServices.module().getZoneFeatureProvider();
        AizZoneRuntimeState aiz =
                S3kRuntimeStates.currentAiz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        // getLevelEventProvider() returns the Sonic3kLevelEventManager; the AIZ
        // handler is reached via getAizEvents().
        Sonic3kAIZEvents events =
                ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getAizEvents();

        events.setBattleshipAutoScrollActiveRaw(false);
        assertFalse(provider.bgWrapsHorizontally(),
                "AIZ must not wrap BG horizontally outside the forest loop");

        events.setBattleshipAutoScrollActiveRaw(true);
        events.setBattleshipWrapX(0x46C0);
        assertTrue(aiz.isBattleshipForestLoopActive(), "precondition");
        assertTrue(provider.bgWrapsHorizontally(),
                "AIZ must wrap BG horizontally during the post-bombing forest loop");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails.**

Run: `mvn -Dmse=off "-Dsurefire.forkCount=1" "-Ds3k.rom.path=Sonic and Knuckles & Sonic 3 (W) [!].gen" "-Dtest=com.openggf.game.sonic3k.TestSonic3kAizBgWrapActivation" test "-DfailIfNoTests=false"`
Expected: FAIL — `bgWrapsHorizontally()` returns false for AIZ during the loop.

- [ ] **Step 3: Add the AIZ clause.** In `Sonic3kZoneFeatureProvider.java`, extend `bgWrapsHorizontally()` (line 117-126) to include a narrow AIZ clause mirroring `isCnzBossBackgroundWindowActive`:

```java
    @Override
    public boolean bgWrapsHorizontally() {
        var levelManager = GameServices.levelOrNull();
        if (levelManager == null) {
            return false;
        }
        int zoneId = levelManager.getFeatureZoneId();
        return zoneId == Sonic3kZoneIds.ZONE_MGZ
                || zoneId == Sonic3kZoneIds.ZONE_ICZ
                || isCnzBossBackgroundWindowActive(zoneId)
                || isAizForestLoopBackgroundWindowActive(zoneId);
    }

    private boolean isAizForestLoopBackgroundWindowActive(int zoneId) {
        if (zoneId != Sonic3kZoneIds.ZONE_AIZ || !GameServices.hasRuntime()) {
            return false;
        }
        return S3kRuntimeStates.currentAiz(GameServices.zoneRuntimeRegistry())
                .map(AizZoneRuntimeState::isBattleshipForestLoopActive)
                .orElse(false);
    }
```

Add imports if missing: `com.openggf.game.sonic3k.runtime.AizZoneRuntimeState` (S3kRuntimeStates is already imported for the CNZ clause).

- [ ] **Step 4: Verify the full-width path does not suppress the loop wrap.** Per the Task 1 findings, confirm `zoneRuntimeRequiresFullWidthBgTilemap()` does not return true during the post-bombing loop (it covers the AIZ intro). If it would, add the loop-phase exclusion here. Add an assertion to the test that during the loop the BG build path actually takes `bgWrap == true` — extend Step 1's test:

```java
        // The wrapped path must be reachable: a tilemap rebuild during the loop
        // must not be forced full-width by the intro path.
        GameServices.level().ensureBackgroundTilemapData();
        assertFalse(GameServices.level().backgroundTilemapForcedFullWidthForTest(),
                "AIZ intro full-width path must not suppress the forest-loop wrap");
```

If `backgroundTilemapForcedFullWidthForTest()` does not exist, add a small package-visible test accessor on `LevelManager` that returns whether the last BG build used the full-width path (derived from `zoneRuntimeRequiresFullWidthBgTilemap()`), rather than asserting on private state.

- [ ] **Step 5: Run the test to verify it passes.**

Run: `mvn -Dmse=off "-Dsurefire.forkCount=1" "-Ds3k.rom.path=Sonic and Knuckles & Sonic 3 (W) [!].gen" "-Dtest=com.openggf.game.sonic3k.TestSonic3kAizBgWrapActivation" test "-DfailIfNoTests=false"`
Expected: PASS.

- [ ] **Step 6: Commit.**

```bash
git add src/main/java/com/openggf/game/sonic3k/Sonic3kZoneFeatureProvider.java src/test/java/com/openggf/game/sonic3k/TestSonic3kAizBgWrapActivation.java
# include LevelManager.java if a test accessor was added
git commit -m "feat(s3k): enable wrapped-BG build path for AIZ2 forest loop

Adds a narrow AIZ clause to bgWrapsHorizontally() (active only during
isBattleshipForestLoopActive()), mirroring the CNZ-boss precedent, so the BG
source-loop normalization is actually exercised. Verifies the AIZ intro
full-width path does not suppress the loop wrap.

Changelog: n/a
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Origin-anchored modulo normalization of the BG source query

**Files:**
- Modify: `src/main/java/com/openggf/level/LevelTilemapManager.java` (`bgXQueryOffset`, ~line 361-368; add pure helper)
- Modify: `src/main/java/com/openggf/level/LevelManager.java` (`applyBackgroundTilemapWindowSelection`, ~line 1815-1839 — set loop period)
- Test: `src/test/java/com/openggf/level/TestForestLoopQueryOffset.java` *(new, pure unit test)*

- [ ] **Step 1: Write the failing pure-helper unit test.** Create `src/test/java/com/openggf/level/TestForestLoopQueryOffset.java`:

```java
package com.openggf.level;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestForestLoopQueryOffset {

    @Test
    void normalizesToOriginAnchoredPeriodSoA200JumpMapsToSameColumn() {
        int origin = 0x4000;
        int period = 0x200;

        int before = LevelTilemapManager.forestLoopQueryOffset(0x46C0, origin, period);
        int afterWrap = LevelTilemapManager.forestLoopQueryOffset(0x46C0 - 0x200, origin, period);

        // A $200 jump in the base maps back to the same normalized source column.
        assertEquals(before, afterWrap,
                "Origin-anchored modulo must map a $200 base jump to the same source column");
    }

    @Test
    void resultStaysWithinOnePeriodAboveOrigin() {
        int origin = 0x4000;
        int period = 0x200;
        for (int base = origin; base < origin + 4 * period; base += 0x37) {
            int off = LevelTilemapManager.forestLoopQueryOffset(base, origin, period);
            assertEquals(true, off >= origin && off < origin + period,
                    "Normalized offset must stay within [origin, origin+period)");
        }
    }

    @Test
    void handlesBaseBelowOrigin() {
        int origin = 0x4000;
        int period = 0x200;
        int off = LevelTilemapManager.forestLoopQueryOffset(origin - 0x10, origin, period);
        assertEquals(true, off >= origin && off < origin + period,
                "Bases below origin still normalize into [origin, origin+period)");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails.**

Run: `mvn -Dmse=off "-Dsurefire.forkCount=1" "-Dtest=com.openggf.level.TestForestLoopQueryOffset" test "-DfailIfNoTests=false"`
Expected: FAIL — `forestLoopQueryOffset` not defined.

- [ ] **Step 3: Add the pure helper.** In `LevelTilemapManager.java`, add a package-visible static method:

```java
    /**
     * Origin-anchored modulo of a BG tilemap base offset to a loop period, used by
     * the AIZ2 forest loop so a $200 camera renormalization re-samples the same
     * source column. Result is in [origin, origin + period).
     */
    static int forestLoopQueryOffset(int bgTilemapBaseX, int origin, int period) {
        int rel = ((bgTilemapBaseX - origin) % period + period) % period;
        return origin + rel;
    }
```

- [ ] **Step 4: Run the helper test to verify it passes.**

Run: `mvn -Dmse=off "-Dsurefire.forkCount=1" "-Dtest=com.openggf.level.TestForestLoopQueryOffset" test "-DfailIfNoTests=false"`
Expected: PASS.

- [ ] **Step 5: Wire the helper into the BG build.** In `LevelTilemapManager` BG-build (the `bgXQueryOffset` computation at ~line 361-368), when the AIZ forest loop is active use the origin-anchored normalization instead of the `bgContiguousWidthPx` wrap. The loop state is reached through the zone feature provider (already a collaborator here). Replace:

```java
        int bgXQueryOffset = 0;
        int bgContiguousWidthPx = geometry.bgContiguousWidthPx();
        if (layerIndex == 1 && bgWrap && bgLinearRowOverflow) {
            bgXQueryOffset = bgTilemapBaseX;
        } else if (layerIndex == 1 && bgWrap && bgContiguousWidthPx > 0) {
            bgXQueryOffset = ((bgTilemapBaseX % bgContiguousWidthPx) + bgContiguousWidthPx)
                    % bgContiguousWidthPx;
        }
```

with:

```java
        int bgXQueryOffset = 0;
        int bgContiguousWidthPx = geometry.bgContiguousWidthPx();
        int forestLoopPeriod = zoneFeatureProvider != null
                ? zoneFeatureProvider.backgroundLoopSourcePeriod(currentZone, currentAct)
                : -1;
        if (layerIndex == 1 && bgWrap && forestLoopPeriod > 0) {
            int forestLoopOrigin = zoneFeatureProvider.backgroundLoopSourceOrigin(currentZone, currentAct);
            bgXQueryOffset = forestLoopQueryOffset(bgTilemapBaseX, forestLoopOrigin, forestLoopPeriod);
        } else if (layerIndex == 1 && bgWrap && bgLinearRowOverflow) {
            bgXQueryOffset = bgTilemapBaseX;
        } else if (layerIndex == 1 && bgWrap && bgContiguousWidthPx > 0) {
            bgXQueryOffset = ((bgTilemapBaseX % bgContiguousWidthPx) + bgContiguousWidthPx)
                    % bgContiguousWidthPx;
        }
```

- [ ] **Step 6: Add the provider hooks (default -1; AIZ loop returns period/origin).** In the `ZoneFeatureProvider` interface (`src/main/java/com/openggf/game/ZoneFeatureProvider.java`, package `com.openggf.game`, next to `backgroundLoopBandBaseY` at ~line 222), add default methods:

```java
    default int backgroundLoopSourcePeriod(int zoneIndex, int actIndex) { return -1; }
    default int backgroundLoopSourceOrigin(int zoneIndex, int actIndex) { return 0; }
```

In `Sonic3kZoneFeatureProvider`, override them to return the AIZ forest-loop period/origin only while the loop is active (reuse `isAizForestLoopBackgroundWindowActive`):

```java
    @Override
    public int backgroundLoopSourcePeriod(int zoneIndex, int actIndex) {
        if (!isAizForestLoopBackgroundWindowActive(getFeatureZoneId())) {
            return -1;
        }
        return S3kRuntimeStates.currentAiz(GameServices.zoneRuntimeRegistry())
                .map(AizZoneRuntimeState::getForestLoopBgWrapPeriod).orElse(-1);
    }

    @Override
    public int backgroundLoopSourceOrigin(int zoneIndex, int actIndex) {
        return S3kRuntimeStates.currentAiz(GameServices.zoneRuntimeRegistry())
                .map(AizZoneRuntimeState::getForestLoopBgOrigin).orElse(0);
    }
```

- [ ] **Step 7: Set the loop period as the cache width.** In `LevelManager.applyBackgroundTilemapWindowSelection` (~line 1815), when the AIZ forest loop is active, set `newBgPeriodWidth` to the loop period so the rendered cache + shader wrap at the loop window. After the `newBgPeriodWidth` initialization (line 1815-1817) add:

```java
        if (zoneFeatureProvider != null) {
            int loopPeriod = zoneFeatureProvider.backgroundLoopSourcePeriod(currentZone, currentAct);
            if (loopPeriod > 0) {
                newBgPeriodWidth = loopPeriod;
            }
        }
```

- [ ] **Step 8: Run the full focused suite to verify no compile/logic regressions.**

Run: `mvn -Dmse=off "-Dsurefire.forkCount=1" "-Ds3k.rom.path=Sonic and Knuckles & Sonic 3 (W) [!].gen" "-Dtest=com.openggf.level.TestForestLoopQueryOffset,com.openggf.game.sonic3k.TestSonic3kAizBgWrapActivation,com.openggf.tests.TestSonic3kLevelLoading" test "-DfailIfNoTests=false"`
Expected: PASS.

- [ ] **Step 9: Commit.**

```bash
git add src/main/java/com/openggf/level/LevelTilemapManager.java src/main/java/com/openggf/level/LevelManager.java src/main/java/com/openggf/game/ZoneFeatureProvider.java src/main/java/com/openggf/game/sonic3k/Sonic3kZoneFeatureProvider.java src/test/java/com/openggf/level/TestForestLoopQueryOffset.java
git commit -m "feat(s3k): origin-anchored BG source-loop normalization for AIZ2 forest

Normalizes the BG source-layout query to an origin-anchored \$200 period during
the AIZ2 forest loop so a \$200 camera renormalization re-samples identical
forest columns, and sets the rendered cache period to the loop window. Driven by
new ZoneFeatureProvider hooks gated on the AIZ forest-loop predicate.

Changelog: n/a
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

> Note: `Sonic3kZoneFeatureProvider` implements the `ZoneFeatureProvider`
> interface at `src/main/java/com/openggf/game/ZoneFeatureProvider.java` (package
> `com.openggf.game`) — the same interface that already declares
> `backgroundLoopBandBaseY`.

---

## Task 6: Tilemap-level seam guard (integration)

**Files:**
- Test: `src/test/java/com/openggf/tests/TestS3kAiz2ForestLoopSeam.java` *(new, ROM-gated)*
- Possibly modify: `src/main/java/com/openggf/level/LevelManager.java` (small test accessor for the sampled BG source column of the built window, if none exists)

- [ ] **Step 1: Write the failing seam guard.** A raw `getBlockAtPosition` comparison is insufficient (it wraps at full layer width and bypasses the window/period/cache path). Assert against the actual build-path query offset across a `$200` wrap. Create `src/test/java/com/openggf/tests/TestS3kAiz2ForestLoopSeam.java`:

```java
package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kAiz2ForestLoopSeam {

    @AfterEach
    void tearDown() { com.openggf.game.session.SessionManager.clear(); }

    @Test
    void forestLoopBgVisibleColumnsIdenticalAcrossThe200Wrap() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_AIZ, 1)
                .build();
        Sonic3kAIZEvents events =
                ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getAizEvents();

        // Enter the post-bombing forest loop and place the camera just below the wrap boundary.
        events.onBattleshipComplete();          // wrapX -> $46C0
        events.setBattleshipAutoScrollActiveRaw(true);
        assertTrue(events.isBattleshipForestLoopActive(), "precondition: forest loop active");

        GameServices.camera().setX((short) (0x46C0 - 0x10));
        GameServices.level().ensureBackgroundTilemapData();
        int[] before = GameServices.level().bgVisibleSourceColumnsForTest();

        // Renormalize the camera back by the ROM $200 (as AIZ2_DoShipLoop does).
        GameServices.camera().setX((short) (0x46C0 - 0x10 - 0x200));
        GameServices.level().ensureBackgroundTilemapData();
        int[] after = GameServices.level().bgVisibleSourceColumnsForTest();

        assertArrayEquals(before, after,
                "Forest BG source columns must be identical across the $200 wrap (no seam)");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails or needs the accessor.**

Run: `mvn -Dmse=off "-Dsurefire.forkCount=1" "-Ds3k.rom.path=Sonic and Knuckles & Sonic 3 (W) [!].gen" "-Dtest=com.openggf.tests.TestS3kAiz2ForestLoopSeam" test "-DfailIfNoTests=false"`
Expected: FAIL — accessor missing, or (if accessor pre-added) columns differ before the Task 5 normalization is active.

- [ ] **Step 3: Add the test accessor if needed.** If `bgVisibleSourceColumnsForTest()` does not exist, add a package/test-visible method on `LevelManager` that returns, for the current built BG window, the array of source-layout column indices the visible window maps to — computed via the same `bgTilemapBaseX` + `bgXQueryOffset` path the renderer uses (not via raw `getBlockAtPosition`). Keep it a thin read of already-computed build state.

- [ ] **Step 4: Run the test to verify it passes (with Task 5 in place).**

Run: `mvn -Dmse=off "-Dsurefire.forkCount=1" "-Ds3k.rom.path=Sonic and Knuckles & Sonic 3 (W) [!].gen" "-Dtest=com.openggf.tests.TestS3kAiz2ForestLoopSeam" test "-DfailIfNoTests=false"`
Expected: PASS — identical columns across the wrap.

- [ ] **Step 5: Commit.**

```bash
git add src/test/java/com/openggf/tests/TestS3kAiz2ForestLoopSeam.java
# include LevelManager.java if a test accessor was added
git commit -m "test(s3k): tilemap-level seam guard for AIZ2 forest \$200 wrap

Asserts the built BG window's source columns are identical across the ROM \$200
camera renormalization (the real defect surface), not just raw map blocks.

Changelog: n/a
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Trace check + frontier log

**Files:**
- Modify (if frontier moves): `docs/TRACE_FRONTIER_LOG.md`

- [ ] **Step 1: Baseline the AIZ frontier without the change.** `git stash` the working tree, run the AIZ trace, record first-error frame; `git stash pop`.

Run (stashed): `mvn -Dmse=off "-Dsurefire.forkCount=1" "-Ds3k.rom.path=Sonic and Knuckles & Sonic 3 (W) [!].gen" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
Expected: records the baseline first-error frame (currently f8941).

- [ ] **Step 2: Run the AIZ trace with the change.**

Run: same command, unstashed.
Expected: a first-error frame. Acceptance: **no new earlier mismatch than baseline unless explained by the intended ROM-faithful `$200` wrap change.** Moving later (or unchanged) is fine; moving earlier requires the divergence be attributable to the `$200` coordinate change (inspect the first-error field/frame).

- [ ] **Step 3: Update the frontier log if it moved.** If the first-error frame/field changed, append a dated entry to `docs/TRACE_FRONTIER_LOG.md` with the command, commit/worktree context, pass/fail, error count, and first-error frame/field, plus the `$200`-wrap explanation. If unchanged, no edit needed.

- [ ] **Step 4: Commit (only if the log changed).**

```bash
git add docs/TRACE_FRONTIER_LOG.md
git commit -m "docs(s3k): record AIZ trace frontier after \$200 wrap restore

Changelog: n/a
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: Cleanup, discrepancy removal, CHANGELOG, full suite

**Files:**
- Modify: `docs/S3K_KNOWN_DISCREPANCIES.md` (remove the AIZ2 wrap entry + TOC line)
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Remove the discrepancy entry.** In `docs/S3K_KNOWN_DISCREPANCIES.md`, delete the "AIZ2 Battleship Post-Bombing Wrap Distance" section (the `## AIZ2 Battleship Post-Bombing Wrap Distance` block and its preceding `---`) and remove its Table-of-Contents line (`8. [AIZ2 Battleship Post-Bombing Wrap Distance](...)`).

- [ ] **Step 2: Add a CHANGELOG entry.** Under `## Unreleased` in `CHANGELOG.md`, add:

```markdown
- **AIZ2 battleship background loop is now seamless at the true ROM $200 wrap.**
  Restored the ROM AIZ2_DoShipLoop $200 camera/object renormalization (was a $80
  visual approximation) and made the AIZ2 forest background a $200-periodic
  looping source via an origin-anchored BG source-query normalization, so the
  wrap re-samples identical forest columns with no seam. Removes the former
  "AIZ2 Battleship Post-Bombing Wrap Distance" S3K known-discrepancy.
```

- [ ] **Step 3: Run the full default suite.**

Run: `mvn clean test -Dmse=relaxed`
Expected: BUILD/`MSE:OK` — 0 failed, 0 errors (ArchUnit unaffected; no new shared→game dependency — BG window selection already depends on zone state via the MGZ/CNZ precedent).

- [ ] **Step 4: Run the S3K guard + seam + frontier-green tests together.**

Run: `mvn -Dmse=off "-Dsurefire.forkCount=1" "-Ds3k.rom.path=Sonic and Knuckles & Sonic 3 (W) [!].gen" "-Dtest=com.openggf.tests.TestS3kAiz2SidekickBoundsSync,com.openggf.tests.TestS3kAiz2ForestLoopSeam,com.openggf.game.sonic3k.TestSonic3kAizBgWrapActivation,com.openggf.tests.TestS3kAiz1SkipHeadless,com.openggf.tests.TestSonic3kLevelLoading" test "-DfailIfNoTests=false"`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add docs/S3K_KNOWN_DISCREPANCIES.md CHANGELOG.md
git commit -m "docs(s3k): retire AIZ2 wrap-distance discrepancy after faithful fix

Changelog: updated
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: updated
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review notes

- **Spec coverage:** Phase 1 spike → Task 1; restore `$200` → Task 2; semantic signals (narrow predicate, period, origin) → Task 3; wrapped-BG activation + full-width-suppression check → Task 4; source-query origin-anchored modulo + cache period → Task 5; tilemap-level seam guard → Task 6; relaxed trace acceptance + frontier log → Task 7; cleanup/discrepancy/CHANGELOG → Task 8. All spec sections mapped.
- **Spike-determined literal:** `FOREST_LOOP_BG_ORIGIN` is defined as a named constant in Task 3 with its literal supplied by the Task 1 findings (go/no-go gated); it is referenced consistently in Tasks 3 and 5 and never left as a bare TODO.
- **Type/name consistency:** `isBattleshipForestLoopActive()`, `getForestLoopBgWrapPeriod()`, `getForestLoopBgOrigin()`, `forestLoopQueryOffset(base, origin, period)`, `backgroundLoopSourcePeriod()`, `backgroundLoopSourceOrigin()`, `isAizForestLoopBackgroundWindowActive()` are used identically across tasks.
- **Open implementation confirmations (flagged in-task, not placeholders):** exact `ZoneFeatureProvider` interface path (Task 5 note); whether a `LevelManager` test accessor for the full-width flag / visible source columns already exists (Tasks 4, 6) — add a thin one only if absent.

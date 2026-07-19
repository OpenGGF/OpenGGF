# Multi-Stage Trace Runs — Plan (c): Chained Run Driver + Boundary Assertions

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the continuous-engine chained run driver (spec §Chained run driver), the `LevelTransitionCoordinator` non-consuming peeks (spec engine-side addition #1), and the skip-if-missing chain test for the gumball/pachinko round trip — per plan (c) of `docs/superpowers/specs/2026-07-18-multi-stage-trace-runs-design.md`.

**Architecture (verified seams, 2026-07-19 exploration):** ONE real `GameLoop` stepped headlessly via `loop.step()` on a `HeadlessTestFixture` ROM boot (the `TestPachinkoTitleCardIntegration` precedent — real mode transitions fire organically under `loop.step()`, `TestPachinkoTitleCardIntegration.java:44-85`). Level/bonus segments are fed and compared through the existing live stack: `TraceReplayDriver.start(zone, act)` (UI-agnostic bootstrap, `TraceReplayDriver.java:74-154`) + `PlaybackDebugManager` BK2 cursor (input via `syncPlaybackInputBridge`, `GameLoop.java:1610-1631`, headless-safe `setForcedInputMask`) + `LiveTraceComparator` (headless-safe `PlaybackFrameObserver`, `LiveTraceComparator.java:35-156`). The walker swaps the comparator per segment at each segment's `bk2_frame_offset`; transition presentation frames stay uncompared; boundary organic-entry assertion uses the new coordinator peeks; boundary state asserts against `BonusStageState`/`GameStateManager.getEmeraldCount()`. `applyBonusStageEntry` is NOT used in the chain — the engine enters the bonus zone organically via GameLoop (that seam is for standalone per-segment replay only).

**Tech Stack:** Java 21 + JUnit 5 (Jupiter only). No lua changes.

**Load-bearing discovery (round-1 review):** `PlaybackDebugManager` is hard-gated to `GameMode.LEVEL` — `isDriving` rejects `BONUS_STAGE` (`PlaybackDebugManager.java:139-141`) and `GameLoop.updateBonusStageMode` never calls `onLevelFrameAdvanced()`/`shouldSkipCurrentGameplayTick()` (only `updateLevelMode` does, `GameLoop.java:1375,1417`), so without Task 2's bridge the bonus interior gets NO recorded input and a FROZEN cursor, desyncing everything after the first bonus entry. Task 2 is spec engine-side addition #8.

## Global Constraints

- **Spec:** `docs/superpowers/specs/2026-07-18-multi-stage-trace-runs-design.md`. Plan (c) scope: peeks (addition #1) + chained driver + boundary assertions, gumball/pachinko chain first. SS chaining, slots, visual (plan d) are out of scope.
- **Comparison-only invariant:** the walker feeds ONLY recorded BK2 inputs and compares; no trace-derived state hydration. Boundary assertions are comparisons of engine state vs manifest records.
- **Boundary-window tolerance is ONE global constant** (`TraceRunReplayWalker.BOUNDARY_WINDOW_FRAMES = 600`) — explicitly not per-zone/route/transition tunable (no-carve-out rule). Semantics: the peek must be observed non-null at some engine frame whose BK2 cursor index `f` satisfies `recordedEdge - BOUNDARY_WINDOW_FRAMES <= f <= recordedEdge`, where `recordedEdge` is the transition's `mode_change_bk2_frame`. (For bonus entries the recorder stamps the ARM frame — after fade/load — so the engine's request always fires EARLIER; 600 frames covers fade + load + title card with margin on any route.)
- JUnit 5 only; commit policy as plans (a)/(b) (trailer block; `feat`/`fix` touching `src/main/` → `Changelog: updated` + CHANGELOG.md staged, CRLF-verified; stage exact paths; every commit message ends with `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>` and `Claude-Session: https://claude.ai/code/session_01LPPPMPSUQBgYpxpA82bad5`).
- Recordings still absent: the chain test lands skip-if-missing on `src/test/resources/traces/s3k/runs/s3k-aiz-gumball-roundtrip/` (and pachinko). Walker control-flow is unit-tested TODAY against the plan-(a) synthetic run fixture.
- Guard awareness (learned in plan (b)): new bootstrap-policy-looking gates in `src/main` trace code may trip `TestBuildToolingGuard`; new `*TraceReplay`-named test classes must extend a base registered in `TestTraceReplayInvariantGuard`. The walker/test naming below deliberately avoids the `*TraceReplay` suffix pattern where the guard's rule doesn't fit; if a guard fires anyway, register per its convention with justification — never weaken.

---

### Task 1: Coordinator peeks (spec addition #1) + coordinator javadoc ring-range fix

**Files:**
- Modify: `src/main/java/com/openggf/level/LevelTransitionCoordinator.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kBonusStageCoordinator.java` (javadoc only)
- Test: `src/test/java/com/openggf/level/TestLevelTransitionCoordinatorPeeks.java` (new)

**Interfaces:**
- Consumes: fields `specialStageRequestedFromCheckpoint` (`LevelTransitionCoordinator.java:16`), `bonusStageRequested` (`:23`, type `BonusStageType`); the peek precedent `isRespawnRequested()` (`:382-384`).
- Produces: `public boolean isSpecialStageRequested()` (returns the flag, no clear) and `public BonusStageType peekBonusStageRequest()` (returns the pending type or null, no clear). Task 2's walker consumes both.

- [ ] **Step 1: Failing test:**

```java
package com.openggf.level;

import com.openggf.game.BonusStageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestLevelTransitionCoordinatorPeeks {

    @Test
    void bonusPeekDoesNotConsume() {
        LevelTransitionCoordinator c = new LevelTransitionCoordinator();
        assertNull(c.peekBonusStageRequest());
        c.requestBonusStageEntry(BonusStageType.GUMBALL);
        assertEquals(BonusStageType.GUMBALL, c.peekBonusStageRequest());
        assertEquals(BonusStageType.GUMBALL, c.peekBonusStageRequest()); // still pending
        assertEquals(BonusStageType.GUMBALL, c.consumeBonusStageRequest()); // consumer still works
        assertNull(c.peekBonusStageRequest()); // cleared by consume, not by peek
    }

    @Test
    void specialStagePeekDoesNotConsume() {
        LevelTransitionCoordinator c = new LevelTransitionCoordinator();
        assertFalse(c.isSpecialStageRequested());
        c.requestSpecialStageFromCheckpoint();
        assertTrue(c.isSpecialStageRequested());
        assertTrue(c.isSpecialStageRequested()); // still pending
        assertTrue(c.consumeSpecialStageRequest());
        assertFalse(c.isSpecialStageRequested());
    }
}
```

(Adjust the constructor/request-method spellings ONLY if the real class differs — read it first; `requestBonusStageEntry`/`requestSpecialStageFromCheckpoint`/`consume*` names were verified present. If the coordinator needs collaborators to construct, use the minimal real construction other unit tests in `src/test/java/com/openggf/level/` use.)

- [ ] **Step 2:** Run `mvn "-Dtest=com.openggf.level.TestLevelTransitionCoordinatorPeeks" test` — COMPILE FAILURE.

- [ ] **Step 3: Implement** — mirror `isRespawnRequested`'s placement and javadoc style:

```java
    /**
     * Non-consuming peek at a pending special-stage entry request. The LEVEL
     * tick's {@link #consumeSpecialStageRequest()} remains the only consumer;
     * this exists so trace-run replay can observe an organically raised
     * transition without swallowing it (spec 2026-07-18, addition #1).
     */
    public boolean isSpecialStageRequested() {
        return specialStageRequestedFromCheckpoint;
    }

    /**
     * Non-consuming peek at a pending bonus-stage entry request; null when
     * none is pending. Mirrors {@link #isRespawnRequested()}.
     */
    public BonusStageType peekBonusStageRequest() {
        return bonusStageRequested;
    }
```

Also ADD ring-range prose examples to `Sonic3kBonusStageCoordinator`'s class javadoc (verified: the existing javadoc lists only the correct remainder→type mapping and has NO wrong ranges — this adds the concrete ranges as documentation): 20–34 → SLOT_MACHINE, 35–49 → GLOWING_SPHERE, 50–64 → GUMBALL.

- [ ] **Step 4:** Test green.
- [ ] **Step 5:** CHANGELOG line `- Trace replay: non-consuming transition peeks for chained run replay.` + commit:

```bash
git add src/main/java/com/openggf/level/LevelTransitionCoordinator.java src/main/java/com/openggf/game/sonic3k/Sonic3kBonusStageCoordinator.java src/test/java/com/openggf/level/TestLevelTransitionCoordinatorPeeks.java CHANGELOG.md
git commit -m "feat(trace): non-consuming transition peeks for chained replay" -m "Changelog: updated
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a"
```

---

### Task 2: BONUS_STAGE playback bridge (spec addition #8)

**Files:**
- Modify: `src/main/java/com/openggf/debug/playback/PlaybackDebugManager.java` (`isDriving`, ~line 139)
- Modify: `src/main/java/com/openggf/GameLoop.java` (`updateBonusStageMode`, ~line 1488)
- Test: `src/test/java/com/openggf/TestBonusStagePlaybackBridge.java` (new)

**Interfaces:**
- Consumes: `PlaybackDebugManager.isDriving(GameMode)` (`:139-141`, currently `mode == GameMode.LEVEL`), the two playback calls in `updateLevelMode` (`GameLoop.java:1375` `shouldSkipCurrentGameplayTick()` gate, `:1417` `onLevelFrameAdvanced()`), `getCursorFrame()` (`:351`), `startSession(movie, int)` (`:226`).
- Produces: `isDriving` returns true for `GameMode.LEVEL` OR `GameMode.BONUS_STAGE` when a session is active; `updateBonusStageMode` mirrors `updateLevelMode`'s two calls at the equivalent points (skip-gate before the gameplay tick, `onLevelFrameAdvanced()` after a completed tick). With no active playback session both are no-ops — normal play unchanged.

- [ ] **Step 1: Failing test.** Read `TestGameLoopSpecialStageSkipGate` first for the reflection idioms (GameLoop construction, `setField`, invoking private mode-update methods) and `PlaybackDebugManager`'s session API. The test (ROM-free) builds a `GameLoop`, installs a `PlaybackDebugManager` session over a small synthetic `Bk2Movie` (reuse however `TestGameLoopSpecialStageSkipGate` or `PlaybackDebugManager`'s own tests fabricate movies — if a helper exists use it; else construct via `Bk2MovieLoader` on a synthetic bk2 from test resources), forces `currentGameMode = GameMode.BONUS_STAGE` via reflection, invokes `updateBonusStageMode()` twice, and asserts `getCursorFrame()` advanced by 2. A second test asserts `isDriving(GameMode.BONUS_STAGE)` is true with an active session and false without.

- [ ] **Step 2:** COMPILE/assert-fail run, then implement:

In `PlaybackDebugManager.isDriving`: `return sessionActive() && (mode == GameMode.LEVEL || mode == GameMode.BONUS_STAGE);` (match the real current-body shape — read it; keep whatever session-activity predicate it already uses).

In `GameLoop.updateBonusStageMode`, mirror `updateLevelMode`'s two call sites at the equivalent positions relative to `LevelFrameStep.execute` (`:1505`): the skip-gate check before the gameplay tick (advance cursor/bookkeeping without ticking on a lag row) and `onLevelFrameAdvanced()` after a completed tick. Copy the exact call pattern from `updateLevelMode:1375,1417` including any surrounding null-guard.

- [ ] **Step 3:** Tests green; also re-run `mvn "-Dtest=com.openggf.game.sonic3k.TestPachinkoTitleCardIntegration" test` (uses `updateBonusStageMode` with NO session — proves the no-op path unchanged).
- [ ] **Step 4:** CHANGELOG line `- Trace replay: BONUS_STAGE playback bridge (cursor/input feed during bonus interiors).` + commit:

```bash
git add src/main/java/com/openggf/debug/playback/PlaybackDebugManager.java src/main/java/com/openggf/GameLoop.java src/test/java/com/openggf/TestBonusStagePlaybackBridge.java CHANGELOG.md
git commit -m "feat(trace): BONUS_STAGE playback bridge for chained replay" -m "Changelog: updated
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a"
```

---

### Task 3: `TraceRunReplayWalker` (test-tree chained driver)

**Files:**
- Create: `src/test/java/com/openggf/tests/trace/runs/TraceRunReplayWalker.java`
- Test: `src/test/java/com/openggf/tests/trace/runs/TestTraceRunReplayWalkerControlFlow.java` (unit-level, synthetic fixture, no ROM)

**Interfaces:**
- Consumes: `TraceRunManifest.load/validate` + `Segment`/`Transition` (plan (a)); `TraceData.load(Path)`; `LevelTransitionCoordinator.isSpecialStageRequested()/peekBonusStageRequest()` (Task 1). At integration time (Task 3): `TraceReplayDriver`, `LiveTraceComparator`, `PlaybackDebugManager`, `GameLoop`.
- Produces: `TraceRunReplayWalker` with:
  - `public static final int BOUNDARY_WINDOW_FRAMES = 600;`
  - `public record SegmentPlan(TraceRunManifest.Segment segment, TraceData trace, TraceRunManifest.Transition entryBoundary, TraceRunManifest.Transition exitBoundary)` — `entryBoundary`/`exitBoundary` null when the segment starts/ends the run or abuts a plain level→level boundary.
  - `public static java.util.List<SegmentPlan> plan(TraceRunManifest run, java.nio.file.Path runDir) throws java.io.IOException` — validates the manifest, loads each segment's `TraceData`, and pairs transitions to segments by their explicit `from_segment`/`to_segment` indices (NEVER by list position).
  - `public static boolean withinBoundaryWindow(int observedBk2Frame, int recordedEdge)` — the tolerance predicate above.
  - `public interface EngineHooks` — the small seam the integration test implements against the real engine: `int currentBk2Frame(); BonusStageType peekBonusRequest(); boolean isSpecialStageRequested(); GameMode currentMode();` (keeps the walker's decision logic unit-testable without an engine).
  - `public static BoundaryObservation awaitBoundary(EngineHooks hooks, TraceRunManifest.Transition boundary, Runnable stepOneFrame)` — steps frames until the matching peek fires or the window is exhausted; returns `record BoundaryObservation(boolean observed, int observedBk2Frame)`. For `entry_kind` `starpost_bonus` it polls `peekBonusRequest() != null`; for `giant_ring`/`starpost_special` it polls `isSpecialStageRequested()`; for `stage_exit` it polls `currentMode() == GameMode.LEVEL` (the return re-arm). Steps STOP at `recordedEdge` (cursor past the window = failure observation, never an exception).

- [ ] **Step 1: Failing control-flow test** (uses the plan-(a) synthetic fixture at `src/test/resources/traces/synthetic/run_aiz_gumball_3seg`):

```java
package com.openggf.tests.trace.runs;

import com.openggf.trace.TraceRunManifest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestTraceRunReplayWalkerControlFlow {

    private static final Path RUN_DIR =
        Path.of("src", "test", "resources", "traces", "synthetic", "run_aiz_gumball_3seg");

    @Test
    void plansSegmentsWithExplicitTransitionPairing() throws Exception {
        TraceRunManifest run = TraceRunManifest.load(RUN_DIR.resolve("run_manifest.json"));
        List<TraceRunReplayWalker.SegmentPlan> plans = TraceRunReplayWalker.plan(run, RUN_DIR);
        assertEquals(3, plans.size());
        assertNull(plans.get(0).entryBoundary());
        assertEquals("starpost_bonus", plans.get(0).exitBoundary().entryKind());
        assertEquals("starpost_bonus", plans.get(1).entryBoundary().entryKind());
        assertEquals("stage_exit", plans.get(1).exitBoundary().entryKind());
        assertEquals("stage_exit", plans.get(2).entryBoundary().entryKind());
        assertNull(plans.get(2).exitBoundary());
    }

    @Test
    void boundaryWindowSemantics() {
        assertTrue(TraceRunReplayWalker.withinBoundaryWindow(1500, 1750));
        assertTrue(TraceRunReplayWalker.withinBoundaryWindow(1750, 1750));
        assertFalse(TraceRunReplayWalker.withinBoundaryWindow(1751, 1750));  // past the edge
        assertFalse(TraceRunReplayWalker.withinBoundaryWindow(
            1750 - TraceRunReplayWalker.BOUNDARY_WINDOW_FRAMES - 1, 1750)); // before the window
    }

    @Test
    void awaitBoundaryObservesPeekWithinWindow() {
        var hooks = new StubHooks();               // small inner stub advancing a frame counter
        hooks.bonusRequestAtFrame = 1700;          // peek turns non-null at 1700
        var boundary = boundaryOfKind("starpost_bonus", 1750);
        var obs = TraceRunReplayWalker.awaitBoundary(hooks, boundary, hooks::step);
        assertTrue(obs.observed());
        assertEquals(1700, obs.observedBk2Frame());
    }

    @Test
    void awaitBoundaryFailsClosedWhenWindowExhausted() {
        var hooks = new StubHooks();               // peek never fires
        var boundary = boundaryOfKind("starpost_bonus", 1750);
        var obs = TraceRunReplayWalker.awaitBoundary(hooks, boundary, hooks::step);
        assertFalse(obs.observed());
    }
    // StubHooks + boundaryOfKind helper: implement inline in this test class;
    // StubHooks implements EngineHooks with an int frame counter, step() { frame++; },
    // peekBonusRequest() returning GUMBALL once frame >= bonusRequestAtFrame (if set),
    // currentMode() LEVEL, isSpecialStageRequested() false.
    // boundaryOfKind builds a TraceRunManifest.Transition via its canonical constructor
    // with the given entry_kind and mode_change_bk2_frame, nulls elsewhere.
}
```

- [ ] **Step 2:** COMPILE FAILURE run, then implement the walker exactly per the Produces contract. `plan(...)` pairing rule: for each transition `t`, it is `plans[t.fromSegment()].exitBoundary` and `plans[t.toSegment()].entryBoundary`; a segment index not named by any transition keeps null (plain level→level boundaries carry no record — plan (a) invariant).
- [ ] **Step 3:** Tests green. Also fix the plan-(a) synthetic fixture's selector inconsistency while here (deferred item): in `run_aiz_gumball_3seg`, change manifest `rings_before` 25 → 55 AND manifest `rings_after` 40 → 70; `seg00_aiz/physics.csv` rings column is currently `0000` on both rows — set it to `0037` (55); `seg02_aiz` rows' rings `0028` → `0046` (70). (Verified: `TestTraceRunSyntheticFixture` asserts only frame counts and offset non-overlap, never ring values, so nothing else changes.) Note: the synthetic manifest's bonus-entry `mode_change_bk2_frame` 1750 is stamped PRE-fade (before seg01's offset 1900), whereas the real recorder stamps the ARM frame — both directions sit inside the 600-frame window; leave 1750 as-is but keep this asymmetry note for when real recordings land. Re-run `TestTraceRunSyntheticFixture` + this class green.
- [ ] **Step 4:** Commit (test-only + fixture):

```bash
git add src/test/java/com/openggf/tests/trace/runs/ src/test/resources/traces/synthetic/run_aiz_gumball_3seg
git commit -m "test(trace): chained run walker control flow + fixture selector consistency" -m "Changelog: n/a
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a"
```

---

### Task 4: Chain integration test (skip-if-missing)

**Files:**
- Create: `src/test/java/com/openggf/tests/trace/runs/TestS3kBonusRoundTripChain.java`

**Interfaces:**
- Consumes: everything above plus the live stack — model the engine setup on `TestPachinkoTitleCardIntegration` (`HeadlessTestFixture` + real `GameLoop` + `loop.step()`, `@RequiresRom(SonicGame.SONIC_3K)`) and the level-segment feeding on `TraceReplayDriver.start(zone, act)` + `PlaybackDebugManager.startSession(movie, startIndex)` + `LiveTraceComparator` attached via `playback.setFrameObserver(comparator)` (`TraceReplayDriver.java:126,153`). Read all three before writing code.

- [ ] **Step 1: Write the test.** Skeleton contract (one method per run id, gumball + pachinko):

1. `Assumptions.assumeTrue(Files.isDirectory(RUN_DIR))` for `src/test/resources/traces/s3k/runs/s3k-aiz-gumball-roundtrip/` — skip until the recording lands. Load + validate manifest; `TraceRunReplayWalker.plan(...)`.
2. Boot: `TraceReplayDriver`-style session for segment 0 (`zone/act` from the segment), BK2 = the run's `source_bk2`, playback cursor started at segment 0's `bk2_frame_offset`; attach a `LiveTraceComparator` bound to segment 0's `TraceData`.
3. Step `loop.step()` while comparing; from `bk2Frame >= exitBoundary.modeChangeBk2Frame() - BOUNDARY_WINDOW_FRAMES`, poll the coordinator peek each frame via `TraceRunReplayWalker.awaitBoundary` with real `EngineHooks` (coordinator reached via `GameServices.level().getTransitions()`; current BK2 frame via the playback manager's cursor accessor — read `PlaybackDebugManager` for the exact getter). Assert `observed`.
4. Detach the comparator (`playback.setFrameObserver(null)`), keep stepping uncompared through fade → TITLE_CARD → BONUS_STAGE; when mode == BONUS_STAGE and `bk2Frame >= segment1.bk2FrameOffset()`, attach a fresh comparator bound to segment 1's `TraceData`. Continue comparing through the bonus segment.
5. At the bonus exit boundary: detach, step until mode returns to LEVEL (stage_exit await), then assert boundary state per the spec split: rings (`GameServices.level().getLevelGamestate().getRings()`) vs the transition's `rings_after`; star-post restore via `LevelTransitionCoordinator.getBonusStageReturnCheckpointIndex()` (`:180`) vs the transition's `last_star_post_hit`; `GameStateManager.getEmeraldCount()` vs `emeralds_after` when present. (Extra-life flags: deferred per spec.)
6. Attach segment 2's comparator at its offset; compare to end of its frames; assert both comparators' divergence reports are emitted to `target/trace-reports/` with run-segment-suffixed names (follow `LiveTraceComparator`'s existing report emission — read how the visual path finalizes reports; if the comparator exposes a summary/report accessor, write the JSON the same way `AbstractTraceReplayTest.writeReport` does, suffixed `_seg0/_seg1/_seg2`).
7. The test is red-allowed on comparison content (MVP posture) but MUST fail hard on: boundary not observed, mode never reaching BONUS_STAGE/LEVEL, or manifest validation errors. Use explicit assertions for those.

- [ ] **Step 2:** Compile + run: both methods SKIP (missing run dirs), failed=0. This is the honest gate available without recordings; the class is exercised for real the day the recordings land.
- [ ] **Step 3:** Commit (test-only, subject `test(trace): S3K bonus round-trip chain test (skip-if-missing)`).

---

### Task 5: Gate + docs

- [ ] **Step 1:** Full suite (`mvn test`, sandbox off, detached with a log monitor if >10 min): no NEW failures vs baseline; expect +2 skips (chain tests). Watch specifically for `TestBuildToolingGuard`/`TestTraceReplayInvariantGuard`/`TestArchitecturalSourceGuard` reacting to the new files; register per convention with justification if they fire (never weaken).
- [ ] **Step 2:** `docs/TRACE_FRONTIER_LOG.md` entry: plan-(c) chained driver landed; chain tests skip pending the same two recordings (named); walker control flow green on synthetic fixture; peeks landed.
- [ ] **Step 3:** Commit docs; merge-time README reminder stands.

## Plan-level notes

- The walker's `EngineHooks` seam keeps decision logic unit-tested without fabricating engine behavior; the integration test wires the REAL hooks. No mock-engine "chain simulation" beyond the boundary-decision unit tests — honesty per the plan-(b) precedent.
- SS boundaries (`giant_ring` polling `isSpecialStageRequested`) are implemented in the walker now (cheap, symmetric) but exercised only when SS runs exist (blue-spheres plan).
- Deferred items intentionally picked up here: coordinator javadoc ring ranges (Task 1), synthetic fixture selector consistency (Task 2).

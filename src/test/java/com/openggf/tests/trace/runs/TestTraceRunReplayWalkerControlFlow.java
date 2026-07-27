package com.openggf.tests.trace.runs;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
import com.openggf.trace.TraceRunManifest;
import com.openggf.trace.replay.runs.TraceRunReplayWalker;
import com.openggf.trace.replay.runs.TraceRunReplayWalker.BoundaryEntryMode;
import com.openggf.trace.replay.runs.TraceRunReplayWalker.BoundaryPairing;
import com.openggf.trace.replay.runs.TraceRunReplayWalker.ReturnAssertionMode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestTraceRunReplayWalkerControlFlow {

    private static final Path RUN_DIR =
        Path.of("src", "test", "resources", "traces", "synthetic", "run_aiz_gumball_3seg");

    /** The committed 25-segment S3K run (with plain level->level gaps + repeat dirs). */
    private static final Path S3K_RUN_MANIFEST = Path.of(
        "src", "test", "resources", "traces", "s3k", "runs",
        "s3-knux-multibonus-ss", "run_manifest.json");

    // A high cap so the frozen-cursor guard never trips in tests that exercise
    // the window-edge / peek / persistent-mode paths instead.
    private static final int NO_CAP = 100_000;

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

    /**
     * Manifest-driven iteration with NO hardcoded count: the committed 25-segment
     * S3K run pairs transitions to segments by explicit from/to indices, leaving
     * null on the plain level->level boundaries that carry no transition record
     * (AIZ->HCZ seg 8->9, HCZ->MGZ seg 18->19). Pure -- no ROM, no trace load.
     */
    @Test
    void pairBoundariesHandlesGapsAndRepeatDirsForFullRun() throws Exception {
        TraceRunManifest run = TraceRunManifest.load(S3K_RUN_MANIFEST);
        assertEquals(25, run.segments().size(), "sanity: the committed S3K run has 25 segments");

        BoundaryPairing pairing = TraceRunReplayWalker.pairBoundaries(run);
        assertEquals(25, pairing.entryBoundaries().length);
        assertEquals(25, pairing.exitBoundaries().length);

        // Segment 0 is the run start: no entry boundary; exits via starpost_bonus.
        assertNull(pairing.entryBoundaries()[0]);
        assertEquals("starpost_bonus", pairing.exitBoundaries()[0].entryKind());

        // Interior 1 (gumball) is bounded by starpost_bonus (entry) / stage_exit.
        assertEquals("starpost_bonus", pairing.entryBoundaries()[1].entryKind());
        assertEquals("stage_exit", pairing.exitBoundaries()[1].entryKind());

        // Plain level->level gaps carry NO transition record -> null both sides.
        assertNull(pairing.exitBoundaries()[8], "AIZ->HCZ (8->9) is a plain level boundary");
        assertNull(pairing.entryBoundaries()[9], "HCZ has no entry transition record");
        assertNull(pairing.exitBoundaries()[19], "HCZ->MGZ (19->20) is a plain level boundary");
        assertNull(pairing.entryBoundaries()[20], "MGZ has no entry transition record");

        // A giant_ring special-stage entry is paired by explicit index (11->12).
        assertEquals("giant_ring", pairing.exitBoundaries()[11].entryKind());
        assertEquals("giant_ring", pairing.entryBoundaries()[12].entryKind());

        // Last segment ends the run: no exit boundary.
        assertNull(pairing.exitBoundaries()[24]);
    }

    @Test
    void boundaryEntryModeMapsEveryEntryKind() {
        assertEquals(BoundaryEntryMode.BONUS_REQUEST,
            TraceRunReplayWalker.boundaryEntryMode("starpost_bonus"));
        assertEquals(BoundaryEntryMode.SPECIAL_STAGE_REQUEST,
            TraceRunReplayWalker.boundaryEntryMode("giant_ring"));
        // S2 starpost_special maps to the special-stage request signal (new shape).
        assertEquals(BoundaryEntryMode.SPECIAL_STAGE_REQUEST,
            TraceRunReplayWalker.boundaryEntryMode("starpost_special"));
        assertEquals(BoundaryEntryMode.LEVEL_MODE,
            TraceRunReplayWalker.boundaryEntryMode("stage_exit"));
        assertThrows(IllegalArgumentException.class,
            () -> TraceRunReplayWalker.boundaryEntryMode("nonsense"));
    }

    @Test
    void returnAssertionModeIsDataDriven() {
        assertEquals(ReturnAssertionMode.POSITIONAL_RESTORE,
            TraceRunReplayWalker.returnAssertionMode(entryTransition("starpost_special", 3568)));
        assertEquals(ReturnAssertionMode.CHECKPOINT_RESTORE,
            TraceRunReplayWalker.returnAssertionMode(entryTransition("starpost_bonus", 10104)));
        // giant_ring split on saved_x_pos presence: S1 (none) -> NEXT_ACT;
        // S3K SS (present) -> RINGS_EMERALDS_ONLY. Manifest data, not a game name.
        assertEquals(ReturnAssertionMode.NEXT_ACT,
            TraceRunReplayWalker.returnAssertionMode(entryTransition("giant_ring", null)));
        assertEquals(ReturnAssertionMode.RINGS_EMERALDS_ONLY,
            TraceRunReplayWalker.returnAssertionMode(entryTransition("giant_ring", 2528)));
        // A stage_exit is not an interior-entry transition.
        assertThrows(IllegalArgumentException.class,
            () -> TraceRunReplayWalker.returnAssertionMode(entryTransition("stage_exit", null)));
    }

    @Test
    void expectedModeAndUncomparedInteriorAreKindDriven() {
        assertEquals(GameMode.LEVEL, TraceRunReplayWalker.expectedMode(segment("level")));
        assertEquals(GameMode.BONUS_STAGE, TraceRunReplayWalker.expectedMode(segment("bonus_stage")));
        assertEquals(GameMode.SPECIAL_STAGE, TraceRunReplayWalker.expectedMode(segment("special_stage")));

        // SS-interior policy v1: only special_stage is advance-uncompared.
        assertTrue(TraceRunReplayWalker.isUncomparedInterior(segment("special_stage")));
        assertFalse(TraceRunReplayWalker.isUncomparedInterior(segment("bonus_stage")));
        assertFalse(TraceRunReplayWalker.isUncomparedInterior(segment("level")));
    }

    @Test
    void activeLevelSegmentConvertsManifestActAndRejectsOtherPhases() {
        var level = segmentAt("mz1", "level", 27467);
        assertTrue(TraceRunReplayWalker.isActiveLevelSegment(level, 0, 0));
        assertFalse(TraceRunReplayWalker.isActiveLevelSegment(level, 0, 1));
        assertFalse(TraceRunReplayWalker.isActiveLevelSegment(
                segmentAt("ss", "special_stage", 0), 0, 0));
    }

    @Test
    void newActiveLevelRequiresLifecycleChangeEvenForSameZoneAndAct() {
        var level = segmentAt("mz1_restart", "level", 31086);
        Object beforeDeath = new Object();
        assertFalse(TraceRunReplayWalker.isNewActiveLevelSegment(
                level, 0, 0, beforeDeath, beforeDeath));
        assertTrue(TraceRunReplayWalker.isNewActiveLevelSegment(
                level, 0, 0, beforeDeath, new Object()));
    }

    @Test
    void allLagSameLevelContinuationRebindsWithoutAnotherModeCycle() {
        var first = segmentAt("ghz2", "level", 8705);
        var continuation = segmentAt("ghz2_2", "level", 9741);
        assertTrue(TraceRunReplayWalker.isLagOnlySameLevelContinuation(
                first, continuation, 800, 799));
        assertFalse(TraceRunReplayWalker.isLagOnlySameLevelContinuation(
                first, continuation, 800, 798));
        var otherAct = new TraceRunManifest.Segment(
                "ghz3", "level", "profile", 18719, 10, 0, 2, null, null);
        assertFalse(TraceRunReplayWalker.isLagOnlySameLevelContinuation(
                first, otherAct, 800, 799));
    }

    @Test
    void interSegmentStepCapIsMaxGapPlusWindow() {
        TraceRunManifest run = new TraceRunManifest(
            1, "s3k", "syn", "syn.bk2", "cs", "lua",
            List.of(segmentAt("a", "level", 0),
                    segmentAt("b", "bonus_stage", 100),
                    segmentAt("c", "level", 1000)),
            List.of());
        // gaps: 100, 900 -> max 900; + BOUNDARY_WINDOW_FRAMES (600) = 1500.
        assertEquals(900 + TraceRunReplayWalker.BOUNDARY_WINDOW_FRAMES,
            TraceRunReplayWalker.interSegmentStepCap(run));
    }

    @Test
    void boundaryWindowSemantics() {
        assertTrue(TraceRunReplayWalker.withinBoundaryWindow(1500, 1750));
        assertTrue(TraceRunReplayWalker.withinBoundaryWindow(1750, 1750));
        assertTrue(TraceRunReplayWalker.withinBoundaryWindow(
            1750 + TraceRunReplayWalker.LATE_BOUNDARY_GRACE_FRAMES, 1750));
        assertFalse(TraceRunReplayWalker.withinBoundaryWindow(
            1750 + TraceRunReplayWalker.LATE_BOUNDARY_GRACE_FRAMES + 1, 1750));
        assertFalse(TraceRunReplayWalker.withinBoundaryWindow(
            1750 - TraceRunReplayWalker.BOUNDARY_WINDOW_FRAMES - 1, 1750)); // before the window
    }

    @Test
    void awaitBoundaryObservesTransientPeekDuringCallbackOnly() {
        var hooks = new StubHooks();               // simulates the real engine's transient peek:
        hooks.bonusRequestDuringFrame = 1700;      // peek non-null ONLY inside frame 1700's
                                                   // observer callback; null before AND after
        var probe = new TraceRunReplayWalker.BoundaryProbe(hooks);
        hooks.installedProbe = probe;              // stub's step() invokes probe.afterFrameAdvanced
        var boundary = boundaryOfKind("starpost_bonus", 1750);
        var obs = TraceRunReplayWalker.awaitBoundary(probe, boundary, NO_CAP, hooks::step);
        assertTrue(obs.observed());
        assertEquals(1700, obs.observedBk2Frame());
        // Post-step polling would have missed it — assert the stub really is transient:
        assertNull(hooks.peekBonusRequest());
    }

    @Test
    void awaitBoundaryObservesSpecialStageRequestRaisedAfterFrameCallback() {
        var hooks = new StubHooks();
        hooks.specialRequestAfterCallbackFrame = 1700;
        var probe = new TraceRunReplayWalker.BoundaryProbe(hooks);
        hooks.installedProbe = probe;

        var obs = TraceRunReplayWalker.awaitBoundary(
            probe, boundaryOfKind("giant_ring", 1750), NO_CAP, hooks::step);

        assertTrue(obs.observed());
        assertEquals(1700, obs.observedBk2Frame());
        assertFalse(hooks.isSpecialStageRequested(),
            "the durable event marker must work after the live request was consumed");
    }

    @Test
    void normalSpecialStagePeekKeepsPostAdvanceClockAnchor() {
        var hooks = new StubHooks();
        hooks.frame = 1699;
        hooks.specialRequestDuringFrame = 1700;
        var probe = new TraceRunReplayWalker.BoundaryProbe(hooks);
        probe.arm(boundaryOfKind("giant_ring", 1750));

        probe.onSpecialStageRequestRaised();
        hooks.frame = 1700;
        hooks.inCallback = true;
        try {
            probe.afterFrameAdvanced(null, false);
        } finally {
            hooks.inCallback = false;
        }

        assertTrue(probe.latched());
        assertEquals(1700, probe.observation().observedBk2Frame(),
            "the ordinary frame callback must supersede the pre-advance fallback marker");
    }

    @Test
    void awaitBoundaryFailsClosedWhenWindowExhausted() {
        var hooks = new StubHooks();               // peek never fires
        var probe = new TraceRunReplayWalker.BoundaryProbe(hooks);
        hooks.installedProbe = probe;
        var boundary = boundaryOfKind("starpost_bonus", 1750);
        var obs = TraceRunReplayWalker.awaitBoundary(probe, boundary, NO_CAP, hooks::step);
        assertFalse(obs.observed());
    }

    @Test
    void stageExitObservedByPersistentModePoll() {
        var hooks = new StubHooks();
        hooks.modeBecomesLevelAtFrame = 2850;      // persistent condition; NO observer callback
        var probe = new TraceRunReplayWalker.BoundaryProbe(hooks);
        hooks.installedProbe = probe;
        var obs = TraceRunReplayWalker.awaitBoundary(
            probe, boundaryOfKind("stage_exit", 2800 + 600), NO_CAP, hooks::step);
        assertTrue(obs.observed());
        assertEquals(2850, obs.observedBk2Frame());  // not vacuous at window start
    }

    /**
     * Step-cap firing: a FROZEN cursor (never advances) with a mode that never
     * settles must throw a diagnostic {@code BoundaryStepCapExceededException}
     * instead of hanging. The peek never fires and the cursor never passes the
     * edge, so ONLY the step cap can end the loop.
     */
    @Test
    void awaitBoundaryThrowsWhenStepCapExceededOnFrozenCursor() {
        var hooks = new StubHooks();
        hooks.freezeCursor = true;                 // step() never advances the frame
        var probe = new TraceRunReplayWalker.BoundaryProbe(hooks);
        hooks.installedProbe = probe;
        var boundary = boundaryOfKind("starpost_bonus", 1750);
        var thrown = assertThrows(
            TraceRunReplayWalker.BoundaryStepCapExceededException.class,
            () -> TraceRunReplayWalker.awaitBoundary(probe, boundary, 50, hooks::step));
        assertTrue(thrown.getMessage().contains("step cap 50"),
            "diagnostic names the cap: " + thrown.getMessage());
        assertTrue(thrown.getMessage().contains("starpost_bonus"),
            "diagnostic names the entry_kind: " + thrown.getMessage());
    }

    @Test
    void probeDelegatesSkipGateToAttachedComparator() {
        var hooks = new StubHooks();
        var probe = new TraceRunReplayWalker.BoundaryProbe(hooks);
        assertFalse(probe.shouldSkipGameplayTick(null),
            "detached probe must not skip gameplay ticks");
        probe.setDelegate(new AlwaysSkipDelegate());   // tiny inline stub delegate
        assertTrue(probe.shouldSkipGameplayTick(null),
            "attached delegate's lag gating must flow through the probe");
    }

    @Test
    void remainingSegmentFramesSubtractsAlreadyConsumedFallthroughRows() {
        assertEquals(799, TraceRunReplayWalker.remainingSegmentFrames(800, 1));
        assertEquals(800, TraceRunReplayWalker.remainingSegmentFrames(800, 0));
        assertEquals(0, TraceRunReplayWalker.remainingSegmentFrames(800, 800));
    }

    @Test
    void unspecifiedMovieEndModeSkipsTerminalTailReplayAndAssertion() {
        var plan = TraceRunReplayWalker.planTerminalMovieTail(
                TraceRunManifest.ExpectedMovieEndMode.UNSPECIFIED, 120, 100);

        assertFalse(plan.shouldReplay());
        assertFalse(plan.shouldAssertExpectedMode());
    }

    @Test
    void declaredMovieEndModesReplayRemainingRowsAndAssertTheirMode() {
        var level = TraceRunReplayWalker.planTerminalMovieTail(
                TraceRunManifest.ExpectedMovieEndMode.LEVEL, 8, 11);
        var titleScreen = TraceRunReplayWalker.planTerminalMovieTail(
                TraceRunManifest.ExpectedMovieEndMode.TITLE_SCREEN, 8, 11);

        assertTrue(level.shouldReplay());
        assertTrue(level.shouldAssertExpectedMode());
        assertEquals(3, level.rowsToReplay());
        assertEquals(GameMode.LEVEL, level.expectedMode());
        assertTrue(titleScreen.shouldReplay());
        assertTrue(titleScreen.shouldAssertExpectedMode());
        assertEquals(3, titleScreen.rowsToReplay());
        assertEquals(GameMode.TITLE_SCREEN, titleScreen.expectedMode());
    }

    @Test
    void declaredMovieEndModeRejectsTailPastMovieWithDiagnostic() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> TraceRunReplayWalker.planTerminalMovieTail(
                        TraceRunManifest.ExpectedMovieEndMode.LEVEL, 12, 11));

        assertTrue(thrown.getMessage().contains("tail start 12"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("movie frame count 11"), thrown.getMessage());
    }

    @Test
    void declaredMovieEndModeAtMovieBoundaryStillAssertsWithoutReplayingRows() {
        var plan = TraceRunReplayWalker.planTerminalMovieTail(
                TraceRunManifest.ExpectedMovieEndMode.TITLE_SCREEN, 11, 11);

        assertFalse(plan.shouldReplay());
        assertTrue(plan.shouldAssertExpectedMode());
        assertEquals(0, plan.rowsToReplay());
        assertEquals(GameMode.TITLE_SCREEN, plan.expectedMode());
    }

    @Test
    void interLevelVblankBudgetUsesMovieGapAndProfiledNonAdvancingRows() {
        var ghz2 = new TraceRunManifest.Segment(
                "ghz2", "level", "profile", 8705, 800, 0, 1, null, null);
        var continuation = new TraceRunManifest.Segment(
                "ghz2_2", "level", "profile", 9741, 7440, 0, 1, null, null);
        assertEquals(230, TraceRunReplayWalker.interLevelVblankBudget(
                ghz2, continuation, 0, 6));

        var ghz3Tail = new TraceRunManifest.Segment(
                "ghz3_2", "level", "profile", 18719, 8520, 0, 2, null, null);
        var mz1 = new TraceRunManifest.Segment(
                "mz1", "level", "profile", 27467, 3391, 2, 0, null, null);
        assertEquals(223, TraceRunReplayWalker.interLevelVblankBudget(
                ghz3Tail, mz1, 1, 6));

        var mz2Death = new TraceRunManifest.Segment(
                "mz2", "level", "profile", 42308, 542, 2, 1, null, null);
        var mz2Restart = new TraceRunManifest.Segment(
                "mz2_2", "level", "profile", 43078, 3728, 2, 1, null, null);
        assertEquals(222, TraceRunReplayWalker.interLevelVblankBudget(
                mz2Death, mz2Restart, 0, 6));
    }

    @Test
    void interLevelVblankBudgetRejectsOverlappingMovieRanges() {
        var current = new TraceRunManifest.Segment(
                "a", "level", "profile", 100, 50, 0, 0, null, null);
        var next = new TraceRunManifest.Segment(
                "b", "level", "profile", 140, 50, 0, 1, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> TraceRunReplayWalker.interLevelVblankBudget(current, next, 0, 0));
    }

    @Test
    void uncomparedInteriorReturnVblankBudgetCountsEveryMovieRowSinceLevelTail() {
        var mz1Tail = new TraceRunManifest.Segment(
                "mz1_2", "level", "profile", 31086, 8684, 2, 0, null, null);
        var mz2 = new TraceRunManifest.Segment(
                "mz2", "level", "profile", 42308, 542, 2, 1, null, null);

        assertEquals(2539, TraceRunReplayWalker.uncomparedInteriorReturnVblankBudget(
                mz1Tail, mz2));
    }

    @Test
    void sourceTailVblankIsProjectedFromObservedMovieCursor() {
        var source = new TraceRunManifest.Segment(
                "mz1_2", "level", "profile", 31086, 8684, 2, 0, null, null);

        assertEquals(0x99F9, TraceRunReplayWalker.sourceTailVblankAtBoundary(
                source, 39773, 0x99FC));
        assertEquals(0x99F9, TraceRunReplayWalker.sourceTailVblankAtBoundary(
                source, 39769, 0x99F8));
    }


    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private static TraceRunManifest.Transition boundaryOfKind(String entryKind, int modeChangeBk2Frame) {
        return new TraceRunManifest.Transition(
            0, 0, entryKind, modeChangeBk2Frame,
            null, null, null, null, null, null, null, null);
    }

    private static TraceRunManifest.Transition entryTransition(String entryKind, Integer savedXPos) {
        return new TraceRunManifest.Transition(
            0, 1, entryKind, 0,
            null, savedXPos, savedXPos == null ? null : 100, null, null, null, null, null);
    }

    private static TraceRunManifest.Segment segment(String kind) {
        return segmentAt("dir", kind, 0);
    }

    private static TraceRunManifest.Segment segmentAt(String dir, String kind, int bk2FrameOffset) {
        return new TraceRunManifest.Segment(
            dir, kind, "profile", bk2FrameOffset, 10, 0, 1, null, null);
    }

    /**
     * Models the real coordinator's TRANSIENT peek semantics: the bonus stage
     * request is visible ONLY during the observer callback of the frame that
     * raises it, plus the persistent LEVEL mode-poll used for stage_exit
     * boundaries, plus an optional FROZEN cursor (step() never advances) for the
     * step-cap firing test.
     */
    private static final class StubHooks implements TraceRunReplayWalker.EngineHooks {
        int frame;
        int bonusRequestDuringFrame = -1;
        int specialRequestDuringFrame = -1;
        int specialRequestAfterCallbackFrame = -1;
        int modeBecomesLevelAtFrame = -1;
        boolean freezeCursor;
        boolean inCallback;
        TraceRunReplayWalker.BoundaryProbe installedProbe;

        void step() {
            if (!freezeCursor) {
                frame++;
            }
            inCallback = true;
            try {
                installedProbe.afterFrameAdvanced(null, false);
            } finally {
                inCallback = false;
            }
            if (frame == specialRequestAfterCallbackFrame) {
                installedProbe.onSpecialStageRequestRaised();
            }
        }

        @Override
        public int currentBk2Frame() {
            return frame;
        }

        @Override
        public BonusStageType peekBonusRequest() {
            return inCallback && frame == bonusRequestDuringFrame ? BonusStageType.GUMBALL : null;
        }

        @Override
        public boolean isSpecialStageRequested() {
            return inCallback && frame == specialRequestDuringFrame;
        }

        @Override
        public GameMode currentMode() {
            if (modeBecomesLevelAtFrame >= 0 && frame < modeBecomesLevelAtFrame) {
                return GameMode.BONUS_STAGE;
            }
            return GameMode.LEVEL;
        }
    }

    private static final class AlwaysSkipDelegate implements PlaybackDebugManager.PlaybackFrameObserver {
        @Override
        public boolean shouldSkipGameplayTick(Bk2FrameInput frame) {
            return true;
        }

        @Override
        public void afterFrameAdvanced(Bk2FrameInput frame, boolean wasSkipped) {
            // Unused by this test.
        }
    }
}

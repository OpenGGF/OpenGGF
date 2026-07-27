package com.openggf;

import com.openggf.game.GameMode;
import com.openggf.trace.TraceRunManifest;
import com.openggf.trace.replay.runs.TraceRunReplayWalker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-level coverage for {@link RunSegmentAdvancer}, the segment-advance
 * state machine driving a visual multi-stage trace run session (Task 3,
 * spec: docs/architecture/designs/2026-07-18-multi-stage-trace-runs-design.md).
 * No ROM/engine involved — feeds synthetic (mode, cursorFrame) sequences
 * against the real {@code run_aiz_gumball_3seg} synthetic fixture's
 * SegmentPlans (level/bonus_stage/level, offsets 500/1900/2900, 2 trace
 * frames each) and asserts the emitted events.
 */
class TestTraceSessionLauncherRunBranch {

    private static final Path RUN_DIR =
            Path.of("src", "test", "resources", "traces", "synthetic", "run_aiz_gumball_3seg");

    private List<TraceRunReplayWalker.SegmentPlan> segments;

    @BeforeEach
    void loadFixture() throws Exception {
        TraceRunManifest run = TraceRunManifest.load(RUN_DIR.resolve("run_manifest.json"));
        segments = TraceRunReplayWalker.plan(run, RUN_DIR);
    }

    @Test
    void staysComparingWhileModeMatchesSegmentZero() {
        RunSegmentAdvancer advancer = new RunSegmentAdvancer(segments);
        assertNull(advancer.onFrame(GameMode.LEVEL, 500));
        assertNull(advancer.onFrame(GameMode.LEVEL, 600));
        assertEquals(0, advancer.currentSegmentIndex());
    }

    @Test
    void entersTransitionWhenModeLeavesSegmentZero() {
        RunSegmentAdvancer advancer = new RunSegmentAdvancer(segments);
        advancer.onFrame(GameMode.LEVEL, 1750);
        assertNull(advancer.onFrame(GameMode.TITLE_CARD, 1751));
        assertEquals(0, advancer.currentSegmentIndex());
    }

    @Test
    void emitsAdvanceActionWhenBonusStageReached() {
        RunSegmentAdvancer advancer = new RunSegmentAdvancer(segments);
        advancer.onFrame(GameMode.LEVEL, 1750);
        advancer.onFrame(GameMode.TITLE_CARD, 1751);
        RunSegmentAdvancer.Event event = advancer.onFrame(GameMode.BONUS_STAGE, 1900);
        assertTrue(event instanceof RunSegmentAdvancer.AdvanceAction);
        RunSegmentAdvancer.AdvanceAction action = (RunSegmentAdvancer.AdvanceAction) event;
        assertEquals(1900, action.reseekOffset());
        assertEquals(1, action.nextSegmentIndex());
        assertEquals(1, advancer.currentSegmentIndex());
    }

    @Test
    void modeFlickerDuringTransitionEmitsNothing() {
        RunSegmentAdvancer advancer = new RunSegmentAdvancer(segments);
        advancer.onFrame(GameMode.LEVEL, 1750);
        advancer.onFrame(GameMode.TITLE_CARD, 1751);
        // TITLE_CARD -> TITLE_CARD flicker mid-transition: not the next
        // segment's expected mode (BONUS_STAGE), so nothing is emitted and
        // the advancer stays mid-transition on segment 0.
        assertNull(advancer.onFrame(GameMode.TITLE_CARD, 1752));
        assertEquals(0, advancer.currentSegmentIndex());
    }

    @Test
    void wrongModeDuringTransitionKeepsWaiting() {
        RunSegmentAdvancer advancer = new RunSegmentAdvancer(segments);
        advancer.onFrame(GameMode.LEVEL, 1750);
        advancer.onFrame(GameMode.TITLE_CARD, 1751);
        // SPECIAL_STAGE is not segment 1's expected mode (BONUS_STAGE):
        // never throws, just keeps waiting.
        assertNull(advancer.onFrame(GameMode.SPECIAL_STAGE, 1755));
        assertEquals(0, advancer.currentSegmentIndex());
    }

    @Test
    void fullChainReachesEndOfRun() {
        RunSegmentAdvancer advancer = new RunSegmentAdvancer(segments);
        assertNull(advancer.onFrame(GameMode.LEVEL, 1000));

        advancer.onFrame(GameMode.LEVEL, 1750);
        advancer.onFrame(GameMode.TITLE_CARD, 1751);
        RunSegmentAdvancer.Event toBonus = advancer.onFrame(GameMode.BONUS_STAGE, 1900);
        assertEquals(new RunSegmentAdvancer.AdvanceAction(1900, 1), toBonus);
        assertEquals(1, advancer.currentSegmentIndex());

        assertNull(advancer.onFrame(GameMode.BONUS_STAGE, 2000));
        advancer.onFrame(GameMode.TITLE_CARD, 2800);
        RunSegmentAdvancer.Event toLevel = advancer.onFrame(GameMode.LEVEL, 2900);
        assertEquals(new RunSegmentAdvancer.AdvanceAction(2900, 2), toLevel);
        assertEquals(2, advancer.currentSegmentIndex());

        // Segment 2 (offset 2900, 2 trace frames): still comparing before
        // the last frame is exhausted.
        assertNull(advancer.onFrame(GameMode.LEVEL, 2901));
        RunSegmentAdvancer.Event end = advancer.onFrame(GameMode.LEVEL, 2902);
        assertSame(RunSegmentAdvancer.EndOfRun.INSTANCE, end);
    }

    @Test
    void staysDoneAfterEndOfRun() {
        RunSegmentAdvancer advancer = new RunSegmentAdvancer(segments);
        advancer.onFrame(GameMode.LEVEL, 1750);
        advancer.onFrame(GameMode.TITLE_CARD, 1751);
        advancer.onFrame(GameMode.BONUS_STAGE, 1900);
        advancer.onFrame(GameMode.TITLE_CARD, 2800);
        advancer.onFrame(GameMode.LEVEL, 2900);
        advancer.onFrame(GameMode.LEVEL, 2902);
        assertNull(advancer.onFrame(GameMode.LEVEL, 3000));
    }
}

package com.openggf;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.timing.HardwareReadinessAdmissionPolicy;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.trace.TraceRunManifest;
import com.openggf.trace.replay.TraceReplayFixture;
import com.openggf.trace.replay.runs.TraceRunReplayWalker;
import com.openggf.trace.timing.HardwareTimingReplayPort;
import com.openggf.trace.timing.HardwareTimingSchedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-level coverage for {@link RunSegmentAdvancer}, the segment-advance
 * state machine driving a visual multi-stage trace run session (Task 3,
 * spec: docs/superpowers/specs/2026-07-18-multi-stage-trace-runs-design.md).
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

    @AfterEach
    void clearSession() {
        SessionManager.clear();
    }

    @Test
    void failedRunLaunchDoesNotLeakRecordedPolicyIntoNextGameplayContext() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        SessionManager.armNextGameplayAdmissionPolicy(
                HardwareReadinessAdmissionPolicy.RECORDED);

        TraceSessionLauncher.restoreFailedLaunch(null, false);
        var next = SessionManager.openGameplaySession(new Sonic2GameModule());

        assertEquals(HardwareReadinessAdmissionPolicy.LIVE,
                next.hardwareTiming().admissionPolicy());
    }

    @Test
    void admissionLatchesComparedLevelBonusLevelRowsAcrossStructuralHandoffs() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        Bk2Movie movie = new Bk2Movie(
                Path.of("synthetic-run.bk2"),
                "logkey",
                Map.of(),
                List.of(frame(500), frame(1900), frame(2900)),
                3);
        TraceSessionLauncher session =
                new TraceSessionLauncher(null, movie, segments, null);
        RecordingTimingFixture fixture = new RecordingTimingFixture();
        RunSegmentAdvancer advancer = new RunSegmentAdvancer(segments);
        var first = HardwareTimingSchedule.empty();
        var bonus = HardwareTimingSchedule.empty();
        var returnedLevel = HardwareTimingSchedule.empty();
        var coordinator = new TraceRunReplayWalker.HardwareTimingCoordinator(
                fixture,
                List.of(
                        new TraceRunReplayWalker.HardwareTimingSegment(
                                500, List.of(100), first),
                        new TraceRunReplayWalker.HardwareTimingSegment(
                                1900, List.of(200), bonus),
                        new TraceRunReplayWalker.HardwareTimingSegment(
                                2900, List.of(300), returnedLevel)));
        setField(session, "fixture", fixture);
        setField(session, "runAdvancer", advancer);
        setField(session, "runHardwareTiming", coordinator);

        GameServices.playbackDebug().startSession(movie, 0);
        try {
            session.prepareHardwareTimingForAdmission(GameMode.LEVEL);

            session.runAdvanceTickIfActive(GameMode.TITLE_CARD, 501);
            session.runAdvanceTickIfActive(GameMode.BONUS_STAGE, 1900);
            GameServices.playbackDebug().seekSessionFrame(1, true);
            session.prepareHardwareTimingForAdmission(GameMode.BONUS_STAGE);

            session.runAdvanceTickIfActive(GameMode.TITLE_CARD, 1901);
            session.runAdvanceTickIfActive(GameMode.LEVEL, 2900);
            GameServices.playbackDebug().seekSessionFrame(2, true);
            session.prepareHardwareTimingForAdmission(GameMode.LEVEL);
        } finally {
            GameServices.playbackDebug().endSession();
        }

        assertEquals(List.of(100, 200, 300), fixture.rawFrames);
        assertEquals(List.of(bonus, returnedLevel), fixture.handoffs);
        assertEquals(0, fixture.gaps);
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

    private static Bk2FrameInput frame(int index) {
        return new Bk2FrameInput(index, 0, 0, false, "");
    }

    private static void setField(
            TraceSessionLauncher session, String fieldName, Object value) {
        try {
            Field field = TraceSessionLauncher.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(session, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static final class RecordingTimingFixture
            implements TraceReplayFixture {
        private final List<Integer> rawFrames = new ArrayList<>();
        private final List<HardwareTimingSchedule> handoffs = new ArrayList<>();
        private int gaps;

        @Override
        public void beginTraceRow(int traceIndex, int rawFrame) {
            rawFrames.add(rawFrame);
        }

        @Override
        public void enterHardwareTimingGap() {
            gaps++;
        }

        @Override
        public void handoffHardwareTimingReplay(
                HardwareTimingSchedule nextSchedule) {
            handoffs.add(nextSchedule);
        }

        @Override
        public AbstractPlayableSprite sprite() {
            return null;
        }

        @Override
        public GameplayModeContext gameplayMode() {
            return null;
        }

        @Override
        public void installHardwareTimingReplay(
                HardwareTimingReplayPort replayPort) {
        }

        @Override
        public void verifyHardwareTimingSegmentEdges() {
        }

        @Override
        public void closeHardwareTimingReplayRun() {
        }

        @Override
        public int stepFrameFromRecording() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int skipFrameFromRecording() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void advancePlayableAnimationsOnly() {
        }

        @Override
        public void suppressFirstSidekickAnimationOnce() {
        }

        @Override
        public int consumeRecordingFrameInputOnly() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void advanceRecordingCursor(int frameCount) {
            throw new UnsupportedOperationException();
        }
    }
}

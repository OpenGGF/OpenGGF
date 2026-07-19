package com.openggf.tests.trace.runs;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
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
    void awaitBoundaryObservesTransientPeekDuringCallbackOnly() {
        var hooks = new StubHooks();               // simulates the real engine's transient peek:
        hooks.bonusRequestDuringFrame = 1700;      // peek non-null ONLY inside frame 1700's
                                                   // observer callback; null before AND after
        var probe = new TraceRunReplayWalker.BoundaryProbe(hooks);
        hooks.installedProbe = probe;              // stub's step() invokes probe.afterFrameAdvanced
        var boundary = boundaryOfKind("starpost_bonus", 1750);
        var obs = TraceRunReplayWalker.awaitBoundary(probe, boundary, hooks::step);
        assertTrue(obs.observed());
        assertEquals(1700, obs.observedBk2Frame());
        // Post-step polling would have missed it — assert the stub really is transient:
        assertNull(hooks.peekBonusRequest());
    }

    @Test
    void awaitBoundaryFailsClosedWhenWindowExhausted() {
        var hooks = new StubHooks();               // peek never fires
        var probe = new TraceRunReplayWalker.BoundaryProbe(hooks);
        hooks.installedProbe = probe;
        var boundary = boundaryOfKind("starpost_bonus", 1750);
        var obs = TraceRunReplayWalker.awaitBoundary(probe, boundary, hooks::step);
        assertFalse(obs.observed());
    }

    @Test
    void stageExitObservedByPersistentModePoll() {
        var hooks = new StubHooks();
        hooks.modeBecomesLevelAtFrame = 2850;      // persistent condition; NO observer callback
        var probe = new TraceRunReplayWalker.BoundaryProbe(hooks);
        hooks.installedProbe = probe;
        var obs = TraceRunReplayWalker.awaitBoundary(probe, boundaryOfKind("stage_exit", 2800 + 600), hooks::step);
        assertTrue(obs.observed());
        assertEquals(2850, obs.observedBk2Frame());  // not vacuous at window start
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

    private static TraceRunManifest.Transition boundaryOfKind(String entryKind, int modeChangeBk2Frame) {
        return new TraceRunManifest.Transition(
            0, 0, entryKind, modeChangeBk2Frame,
            null, null, null, null, null, null, null, null);
    }

    /**
     * Models the real coordinator's TRANSIENT peek semantics: the bonus
     * stage request is visible ONLY during the observer callback of the
     * frame that raises it (mirrors GameLoop's request-set-then-consume
     * ordering within a single loop.step()), and the persistent LEVEL
     * mode-poll used for stage_exit boundaries.
     */
    private static final class StubHooks implements TraceRunReplayWalker.EngineHooks {
        int frame;
        int bonusRequestDuringFrame = -1;
        int modeBecomesLevelAtFrame = -1;
        boolean inCallback;
        TraceRunReplayWalker.BoundaryProbe installedProbe;

        void step() {
            frame++;
            inCallback = true;
            try {
                installedProbe.afterFrameAdvanced(null, false);
            } finally {
                inCallback = false;
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
            return false;
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

package com.openggf.tests.trace.runs;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs the committed S1 emerald route through the production visual-session
 * owners rather than the chain fixture, so a defect that only the windowed
 * Trace Test Mode reaches is reproducible without a window.
 */
@RequiresRom(SonicGame.SONIC_1)
class TestS1CompleteEmeraldVisualRun {
    private static final Path RUN_DIR = Path.of(
            "src", "test", "resources", "traces", "s1", "runs",
            "s1-sonic-complete-withemeralds");

    @AfterEach
    void tearDown() {
        VisualRunReplayHarness.tearDown();
    }

    /**
     * The current frontier: GHZ1, the giant-ring handoff, the whole special
     * stage, and admission of the GHZ2 presentation bridge the stage returns
     * through.
     * <p>
     * It stops there deliberately. The bridge now hands back to GHZ2 gameplay
     * (see {@code TestTraceRunPlaybackCoordinator}), but the run then pauses on
     * its first comparison error: the manifest records Sonic's DPLC art load in
     * the gap after the bridge ({@code movie_logical_frame 9715}) because the
     * ROM loads GHZ2 AFTER its 800-row results screen -- the bridge holds
     * {@code Level_frame_counter} at 0x1009 and gameplay resumes at 1 -- while
     * the engine loads before the bridge and produces no gap edges at all
     * ({@code run_gap.edge_count} 2 vs 0). Raise the target here when the
     * engine's post-special-stage load moves to the recorded side of the
     * bridge.
     */
    @Test
    void replaysThroughTheSpecialStageAndItsReturnBridgeAdmission() throws Exception {
        VisualRunReplayHarness.Result result =
                VisualRunReplayHarness.replay(
                        RUN_DIR, VisualRunReplayHarness.stopAfterSegment(2));

        assertEquals(2, result.currentSegmentIndex(),
                "visual run stalled before the GHZ2 return bridge: " + result);
    }
}

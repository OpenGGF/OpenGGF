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
     * stage, and the GHZ2 presentation bridge the stage returns through, up to
     * and including the bridge handing off to GHZ2 gameplay.
     * <p>
     * It stops there deliberately. GHZ2's first gameplay row after the bridge
     * fails dynamic-art publication, and so does the headless chain
     * ({@code assertChainReplayThroughSegmentRow(RUN_DIR, 3, 1)} raises
     * "advertised special-stage row 2894 was not published atomically for
     * generation 5"), so that row is an unexplored frontier in both adapters
     * rather than a visual-only defect. Raise the target here when it moves.
     */
    @Test
    void replaysThroughTheSpecialStageReturnBridgeHandoff() throws Exception {
        VisualRunReplayHarness.Result result =
                VisualRunReplayHarness.replay(
                        RUN_DIR, VisualRunReplayHarness.stopAfterSegment(3));

        assertEquals(3, result.currentSegmentIndex(),
                "visual run stalled before GHZ2 gameplay was admitted: " + result);
    }
}

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
     * It stops there deliberately. The bridge hands back to GHZ2 gameplay and
     * the return gap now emits Sonic's DPLC pair with matching identity at the
     * title card's release, but the run still pauses on
     * {@code run_gap.edge[N].movie_logical_frame}: recorded 9,715 against the
     * engine's release row 9,656. The remaining 59 rows are GM_Level's
     * 22-frame {@code PaletteFadeOut} before {@code AddPLC}
     * (docs/s1disasm/sonic.asm:2710-2737) plus ~37 rows of real load time
     * (NemDec under disabled interrupts, Hud_Base, level-data KosDec) that
     * needs this run re-recorded with the v5 hardware-timing stream. Raise the
     * target here when that lands.
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

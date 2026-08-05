package com.openggf.tests.trace.runs;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /** Enough steps to clear segment 3's terminal physical row (BK2 13,347). */
    private static final int GHZ2_ACT_BODY_STEPS = 13_000;

    @AfterEach
    void tearDown() {
        VisualRunReplayHarness.tearDown();
    }

    /**
     * The current frontier: GHZ1, the giant-ring handoff, the whole special
     * stage, the GHZ2 presentation bridge the stage returns through, and
     * admission of the GHZ2 gameplay the bridge hands back to.
     * <p>
     * The return gap's Sonic DPLC pair now matches the recording on identity
     * and row. Its row is the first frame of {@code GM_Level}'s counted
     * pre-main-loop tail — {@code Level_Delay}'s 4 frames plus
     * {@code PalFadeIn_Alt}'s 22 (docs/s1disasm/sonic.asm:2956-2969) — i.e.
     * main-loop admission minus 26, not the title card's release row. The
     * un-timed load steps between the two ({@code Hud_Base},
     * {@code LevelDataLoad}, {@code LoadTilesFromStart}) take real hardware
     * time in the recording, but no compared field observes their span: its
     * right edge is pinned by the counted tail and its left by the modelled
     * PLC drain. Raise the target here as later segments come in.
     */
    @Test
    void replaysThroughTheSpecialStageAndItsReturnBridgeAdmission() throws Exception {
        VisualRunReplayHarness.Result result =
                VisualRunReplayHarness.replay(
                        RUN_DIR, VisualRunReplayHarness.stopAfterSegment(3));

        assertEquals(3, result.currentSegmentIndex(),
                "visual run stalled before the GHZ2 return bridge: " + result);
    }

    /**
     * Walks every compared row of the returned GHZ2 act. The segment's own last
     * physical row is BK2 13,347, so a budget past it proves no row inside the
     * act self-paused the session.
     * <p>
     * The pin above stops the instant segment 3 is admitted, so it never
     * reaches the act body. This one does: row 107 is the act's first PLC
     * completion that lands on an iteration held into a lag V-blank, where the
     * ROM's own {@code RunPLC} (docs/s1disasm/sonic.asm:3032) runs on the lag
     * closure rather than the row that completed the previous entry. The budget
     * deliberately stops inside the following transition gap, whose giant-ring
     * boundary is a separate open frontier.
     */
    @Test
    void replaysEveryComparedRowOfTheReturnedGhz2Act() throws Exception {
        VisualRunReplayHarness.Result result =
                VisualRunReplayHarness.replay(RUN_DIR, GHZ2_ACT_BODY_STEPS);

        assertTrue(result.sharedCursor() > 13_347,
                "visual run stopped inside the returned GHZ2 act: " + result);
        assertEquals(3, result.currentSegmentIndex(),
                "visual run left segment 3 unexpectedly: " + result);
    }
}

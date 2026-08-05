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
     * Walks every compared row of the returned GHZ2 act, the route's SECOND
     * giant ring, all 4,337 rows of the special stage behind it, its results
     * bridge, the GHZ3 presentation bridge it returns through, and then all
     * 8,520 rows of the GHZ3 act itself -- its boss, its prison capsule, the
     * animal window, and the end-of-act card. The pin above stops the instant
     * segment 3 is admitted, so it never reaches an act body; this one stops
     * only once segment 6 has published its LAST row, so a self-pause anywhere
     * inside four boundaries and three acts fails this instead.
     * <p>
     * Four defects live on this stretch. Row 107 of the GHZ2 act is its first
     * PLC completion that lands on an iteration held into a lag V-blank, where
     * the ROM's own {@code RunPLC} (docs/s1disasm/sonic.asm:3032) runs on the
     * lag closure rather than the row that completed the previous entry. The
     * giant ring is the second entry to a special stage in the run, and so the
     * first whose index ({@code special_stage_index} 1) differs from the stage
     * the provider still has loaded when the ring is touched. Inside the stage,
     * row 2,237's Sonic DPLC is the first observable consequence of an UP/DOWN
     * block that rewrote itself even when it could not change the rotation
     * speed (09 Sonic in Special Stage.asm:853-862, 888-897). And the GHZ3 act
     * is the first the run reaches through TWO results-screen bridges, so it is
     * the first whose object V-blank clock had accumulated both bridges' worth
     * of missing V-ints -- which de-phased the prison capsule's animal spawn
     * and escape-direction gates and armed the end-of-act PLC nine rows early.
     */
    @Test
    void replaysTheSecondGiantRingAndTheSpecialStageBehindIt() throws Exception {
        VisualRunReplayHarness.Result result =
                VisualRunReplayHarness.replay(
                        RUN_DIR, VisualRunReplayHarness.stopAfterSegmentBody(6));

        assertTrue(result.sharedCursor() > 27_238,
                "visual run stopped inside the returned GHZ3 act: " + result);
        assertEquals(6, result.currentSegmentIndex(),
                "visual run stalled before the returned GHZ3 act: " + result);
    }
}

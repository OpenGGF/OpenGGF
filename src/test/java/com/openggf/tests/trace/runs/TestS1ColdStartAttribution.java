package com.openggf.tests.trace.runs;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Attribution control for the tail of {@code s1-sonic-complete-withemeralds}.
 *
 * <p>When a change lets the chain reach segments it never reached before, the
 * ordinary chain gives no control arm for those segments: the unchanged code
 * stops earlier and never executes them, so their divergences cannot be
 * attributed either way. Segments 22, 23 and 24 became visible exactly that
 * way, and were undetermined as a result.
 *
 * <p>Segments 23 and 24 previously led with
 * {@code queue.s1_nemesis_plc.remaining_work rom=17 engine=20} at frame 1 --
 * the title-card release step consuming the destination's first recorded row.
 * The release now reports {@code SETUP_ONLY} when it ran a
 * pre-{@code Level_MainLoop} object pass, and both pins moved on to the next
 * divergence in their segment.
 *
 * <p>This boots the run <em>at</em> segment 22 via
 * {@link AbstractRunChainTest#assertChainReplayFromSegment}, carrying none of
 * the state segments 0-21 would have left. All three segments diverge
 * identically to the full chain -- same first-error frame, same field, same
 * ROM and engine values, and the same error counts (15,564 / 16 / 18,684).
 * They are therefore owned by their own entry conditions, are not carry-in,
 * and are not caused by whatever let the chain arrive there.
 *
 * <p><b>This is a characterisation pin, in the shape of
 * {@link TestS1CompleteEmeraldRunPrefix}'s ratcheting pins.</b> It asserts that
 * the defects are still <em>present and unchanged from a cold start</em>, which
 * is what makes them attributable. Fixing a defect will fail this test: update
 * the pin, and never relax one to keep it quiet. It is not a claim that the
 * divergences are acceptable.
 */
@RequiresRom(SonicGame.SONIC_1)
class TestS1ColdStartAttribution extends AbstractRunChainTest {

    private static final Path RUN_DIR = Path.of(
            "src", "test", "resources", "traces", "s1", "runs",
            "s1-sonic-complete-withemeralds");

    /** Segment 22 (`syz3_2`); segments 23 and 24 are `lz1` and `lz1_2`. */
    private static final int START_SEGMENT = 22;

    /**
     * The {@code lz1_2 -> lz2} handoff, which the full chain fails with
     * "Destination playback cursor advanced -216 frames", is <em>clean</em> from
     * a cold start at segment 24: the same {@code prepareAcrossLevelBoundary}
     * branch runs and lands the cursor exactly on the destination offset.
     *
     * <p>That makes the -216 the first item on this frontier shown to depend on
     * <em>arrival</em> state rather than on its own entry conditions -- the
     * opposite result to segments 22-24 above, from the same instrument. This
     * guards that asymmetry: if a cold start ever starts failing this handoff
     * too, the -216 stops being carry-in and the diagnosis changes.
     */
    @Test
    void coldStartCrossesTheLz1ToLz2HandoffCleanly() throws Exception {
        AssertionError failure = null;
        try {
            assertChainReplayFromSegment(RUN_DIR, 24);
        } catch (AssertionError e) {
            failure = e;
        }
        String report = failure == null ? "" : failure.getMessage();
        assertTrue(
                !report.contains("during level-load handoff (lz1_2 -> lz2"),
                "lz1_2 -> lz2 must still hand off cleanly from a cold start; "
                        + "if it does not, the -216 is no longer carry-in. "
                        + "Report:\n" + report);
    }

    @Test
    void segments22To24DivergeIdenticallyFromAColdStart() throws Exception {
        AssertionError failure = null;
        try {
            assertChainReplayFromSegment(RUN_DIR, START_SEGMENT);
        } catch (AssertionError e) {
            failure = e;
        }
        // Re-based: segment 22 boots as 0, so 23 and 24 are 1 and 2.
        String report = failure == null ? "" : failure.getMessage();
        // The boot segment's own comparison is written to its report but is
        // NOT raised as a chain axis -- a real limitation of booting at a
        // segment, and the reason this reads segment 0 from disk rather than
        // from the failure message. Read the report, not the axis list.
        String bootReport = Files.readString(Path.of(
                "target", "trace-reports",
                "s1-sonic-complete-withemeralds_seg0_report.json"));
        assertTrue(
                bootReport.contains("\"errorCount\" : 15564")
                        && bootReport.contains("\"frame\" : 8115")
                        && bootReport.contains("\"field\" : \"x_sub\""),
                "segment 22 must still diverge identically from a cold start; "
                        + "if it was fixed, update this pin. Report:\n"
                        + bootReport);
        assertTrue(
                report.contains("segment 1 of s1-sonic-complete-withemeralds "
                        + "diverged: 16 physics comparator errors, first "
                        + "non-camera mismatch at frame 3124 field "
                        + "dynamic_art.edges rom=[1738, 1739] engine=[]"),
                "segment 23 must still diverge identically from a cold start; "
                        + "if it was fixed, update this pin. Report:\n" + report);
        // Updated when the water splash stopped consuming a level-object SST
        // slot: the LZ door now loads into ROM slot 34 instead of 45, so its
        // ascent no longer leads the ROM's by one 2px step and the frame-1337
        // edge-phase lead is gone. The remaining errors are a LATER, opposite-
        // signed divergence (engine now behind), not the same defect.
        assertTrue(
                report.contains("segment 2 of s1-sonic-complete-withemeralds "
                        + "diverged: 18221 physics comparator errors, first "
                        + "non-camera mismatch at frame 3182 field "
                        + "dynamic_art.edges rom=[1778, 1779] engine=[]"),
                "segment 24 must still diverge identically from a cold start; "
                        + "if it was fixed, update this pin. Report:\n" + report);
    }
}

package com.openggf.tests.trace.s1;

import com.openggf.data.Rom;
import com.openggf.game.sonic1.specialstage.Sonic1SpecialStageComparisonState;
import com.openggf.game.sonic1.specialstage.Sonic1SpecialStageTraceData;
import com.openggf.game.sonic1.specialstage.Sonic1SpecialStageTraceFrame;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.trace.DivergenceReport;
import com.openggf.trace.FieldComparison;
import com.openggf.trace.FrameComparison;
import com.openggf.trace.Severity;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Headless replay comparator for a Sonic 1 special-stage (maze) trace. Drives
 * the production
 * {@link com.openggf.game.sonic1.specialstage.Sonic1SpecialStageProvider}
 * through {@link S1SpecialStageReplayHarness}, comparing each stepped frame
 * against the recorded ROM trace and emitting a divergence report to
 * {@code target/trace-reports/s1_special_stage_&lt;index&gt;_report.json}.
 * Modeled on {@code AbstractS3kSpecialStageTraceReplayTest}, remapped for the
 * S1 maze's single-player physics-only trace columns.
 *
 * <h2>Replay semantics</h2>
 * <ul>
 *   <li><b>VBlank-paced.</b> Every non-lag trace row is stepped through
 *       {@link S1SpecialStageReplayHarness#stepFrame(int)}; lag rows are
 *       skipped (consumed without stepping) since they advance nothing
 *       engine-side.</li>
 *   <li><b>Comparison-only.</b> Trace values are read for input + expectation
 *       only; engine state is never hydrated from the trace.</li>
 *   <li><b>Terminal exit boundary.</b> The S1 maze trace has no in-segment
 *       completion marker analogous to S3K's {@code fade_timer} 0&rarr;
 *       nonzero&rarr;0 cycle -- the recorded segment simply ends at the ROM's
 *       {@code $10} mode exit (ROM leaves the special-stage game mode via the
 *       exit ramp, {@code v_ssrotate} -&gt; {@code $1800},
 *       {@code sonic.asm} {@code SS_MainLoop} exit). Instead of an anchor
 *       frame lookup, a single {@code exit_state_at_end} check is appended
 *       after the loop at the last trace frame index, asserting that by the
 *       final captured engine state the exit sequence has been raised
 *       ({@code state.exitTriggered() || state.finished()}). MVP
 *       red-allowed.</li>
 * </ul>
 *
 * <h2>Release-ratcheted comparator surface</h2>
 * <p>Every mismatch below is a report ERROR (Tier-1), one comparison-only
 * read of {@link Sonic1SpecialStageComparisonState} per CSV column, with
 * equality checked after masking BOTH sides (sign-agnostic):
 * <ul>
 *   <li>{@code x_pos}/{@code y_pos}: {@code tf.xPos() & 0xFFFFFFFFL} vs
 *       {@code state.sonicPosX() & 0xFFFFFFFFL} (compared as
 *       {@code Long.toString}); same for {@code y_pos}/{@code sonicPosY}.</li>
 *   <li>{@code vel_x}/{@code vel_y}/{@code inertia}: {@code & 0xFFFF} both
 *       sides.</li>
 *   <li>{@code status_facing_left}: {@code (tf.status() & 0x1) != 0} vs
 *       {@code state.sonicFacingLeft()}; {@code status_airborne}:
 *       {@code (tf.status() & 0x2) != 0} vs {@code state.sonicAirborne()}.
 *       Only bits 0-1 are modeled here; the raw trace {@code status} column
 *       retains the full byte so a follow-up campaign can wire additional
 *       bits without re-recording.</li>
 *   <li>{@code ss_angle}/{@code ss_rotate}/{@code bg_anim}: {@code & 0xFFFF}
 *       both sides (engine fields may be updated with signed arithmetic; ROM
 *       word equality is what matters).</li>
 *   <li>{@code rings}: <b>delta-based</b> -- expected
 *       {@code (tf.rings() - trace.getFrame(0).rings()) & 0xFFFF} vs actual
 *       {@code state.ringsCollected()}. ROM {@code v_rings} may carry a
 *       pre-SS value into the maze while the engine's {@code ringsCollected}
 *       always starts at 0 for a fresh stage entry; the per-frame delta
 *       against the trace's own first row is the comparable quantity. (If the
 *       first capture shows {@code v_rings} actually starts at 0 in the
 *       maze, the baseline subtracts 0 and this is an exact comparison.)</li>
 *   <li>{@code emeralds}: expected boolean
 *       {@code tf.emeralds() != trace.getFrame(0).emeralds()} vs actual
 *       {@code state.emeraldCollected()}.</li>
 * </ul>
 *
 * <p>The pipeline writes a complete report and
 * {@link #assertNoReleaseBlockingDivergences} rejects any comparator ERROR.
 */
public abstract class AbstractS1SpecialStageTraceReplayTest {

    /** Location of the committed trace (when one exists). */
    static final Path TRACE_DIRECTORY =
            Path.of("src", "test", "resources", "traces", "s1", "special_stage");

    private static final int CONTEXT_RADIUS = 8;

    /** Directory of the trace to replay. */
    protected abstract Path traceDirectory();

    @Test
    void replayProducesFaithfulReport() throws Exception {
        File romFile = RomTestUtils.ensureSonic1RomAvailable();
        assumeTrue(romFile != null,
                "s1.gen ROM required for S1 special-stage trace replay");

        Path dir = traceDirectory();
        assumeTrue(Files.exists(dir.resolve("metadata.json")),
                "No S1 special-stage trace committed yet at " + dir);

        Sonic1SpecialStageTraceData trace = Sonic1SpecialStageTraceData.load(dir);

        // Pipeline assertion: the trace loads with the expected profile + frames.
        assertEquals("s1_special_stage", trace.metadata().traceProfile(),
                "SS trace must carry the s1_special_stage profile");
        assertTrue(trace.frameCount() > 0, "SS trace should have frames");

        S1SpecialStageReplayHarness harness = bootHarness(trace, dir, romFile);
        DivergenceReport report = compareReplay(trace, harness);

        int ssIndex = specialStageIndex(trace);
        writeReport(report, ssIndex);

        // Pipeline assertion: the report file was written where consumers expect.
        Path jsonPath = reportDir().resolve("s1_special_stage_" + ssIndex + "_report.json");
        assertTrue(Files.exists(jsonPath), "report JSON should be written to " + jsonPath);

        assertNoReleaseBlockingDivergences(report);
    }

    /**
     * Release-gate ratchet for Tier-1 fields, which use ERROR severity.
     */
    protected void assertNoReleaseBlockingDivergences(DivergenceReport report) {
        assertFalse(report.hasErrors(), report.toAssertionSummary());
    }

    // ==================== Boot ====================

    static S1SpecialStageReplayHarness bootHarness(Sonic1SpecialStageTraceData trace,
                                                    Path dir,
                                                    File romFile) throws IOException {
        // Headless graphics so the SS manager's pattern/renderer setup is safe
        // without a GL context.
        GraphicsManager.getInstance().resetState();
        GraphicsManager.getInstance().initHeadless();

        Rom rom = new Rom();
        rom.open(romFile.getAbsolutePath());
        // Installs the Sonic1GameModule, wires GameServices.rom(), and rebuilds a
        // fresh gameplay mode. Resets configuration to defaults, so team config is
        // (re)applied inside the harness ctor afterwards.
        TestEnvironment.configureRomFixture(rom);
        GraphicsManager.getInstance().initHeadless();

        int offset = trace.metadata().bk2FrameOffset();
        int ssIndex = specialStageIndex(trace);
        Path bk2 = dir.resolve(trace.metadata().sourceBk2());
        return new S1SpecialStageReplayHarness(bk2, offset, ssIndex);
    }

    // ==================== Comparator ====================

    static DivergenceReport compareReplay(Sonic1SpecialStageTraceData trace,
                                          S1SpecialStageReplayHarness harness) {
        int compareEnd = trace.frameCount();
        int baselineRings = trace.getFrame(0).rings();
        int baselineEmeralds = trace.getFrame(0).emeralds();

        List<FrameComparison> comparisons = new ArrayList<>();
        Sonic1SpecialStageComparisonState lastState = null;
        int lastFrame = -1;

        for (int f = 0; f < compareEnd; f++) {
            Sonic1SpecialStageTraceFrame tf = trace.getFrame(f);
            if (tf.lag()) {
                continue;
            }
            harness.stepFrame(f);

            Sonic1SpecialStageComparisonState state = harness.capture();
            lastState = state;
            lastFrame = f;

            Map<String, FieldComparison> fields = new LinkedHashMap<>();
            addFields(fields, tf, state, baselineRings, baselineEmeralds);
            comparisons.add(new FrameComparison(f, fields));
        }

        if (lastState != null) {
            String actual = String.valueOf(lastState.exitTriggered() || lastState.finished());
            Map<String, FieldComparison> fields = new LinkedHashMap<>();
            fields.put("exit_state_at_end",
                    cmp("exit_state_at_end", "true", actual, Severity.ERROR));
            comparisons.add(new FrameComparison(lastFrame, fields));
        }

        return new DivergenceReport(comparisons);
    }

    private static void addFields(Map<String, FieldComparison> fields,
                                  Sonic1SpecialStageTraceFrame tf,
                                  Sonic1SpecialStageComparisonState state,
                                  int baselineRings,
                                  int baselineEmeralds) {
        // Tier-1. x_pos/y_pos keep the manager's full 16.16 fixed-point
        // longword layout; mask both sides & 0xFFFFFFFFL and compare as
        // Long.toString so a sign-extended long on either side still
        // compares equal (sign-agnostic).
        fields.put("x_pos", cmp("x_pos",
                Long.toString(tf.xPos() & 0xFFFFFFFFL),
                Long.toString(state.sonicPosX() & 0xFFFFFFFFL), Severity.ERROR));
        fields.put("y_pos", cmp("y_pos",
                Long.toString(tf.yPos() & 0xFFFFFFFFL),
                Long.toString(state.sonicPosY() & 0xFFFFFFFFL), Severity.ERROR));

        fields.put("vel_x", cmp("vel_x",
                str(tf.velX() & 0xFFFF), str(state.sonicVelX() & 0xFFFF), Severity.ERROR));
        fields.put("vel_y", cmp("vel_y",
                str(tf.velY() & 0xFFFF), str(state.sonicVelY() & 0xFFFF), Severity.ERROR));
        fields.put("inertia", cmp("inertia",
                str(tf.inertia() & 0xFFFF), str(state.sonicInertia() & 0xFFFF), Severity.ERROR));

        // Only bits 0-1 of the raw ROM status byte are modeled by the
        // comparison state today; the trace's full status column is kept for
        // a future bit-coverage campaign.
        fields.put("status_facing_left", cmp("status_facing_left",
                bool((tf.status() & 0x1) != 0), bool(state.sonicFacingLeft()), Severity.ERROR));
        fields.put("status_airborne", cmp("status_airborne",
                bool((tf.status() & 0x2) != 0), bool(state.sonicAirborne()), Severity.ERROR));

        fields.put("ss_angle", cmp("ss_angle",
                str(tf.ssAngle() & 0xFFFF), str(state.ssAngle() & 0xFFFF), Severity.ERROR));
        fields.put("ss_rotate", cmp("ss_rotate",
                str(tf.ssRotate() & 0xFFFF), str(state.ssRotate() & 0xFFFF), Severity.ERROR));
        fields.put("bg_anim", cmp("bg_anim",
                str(tf.bgAnim() & 0xFFFF), str(state.bgAnimState() & 0xFFFF), Severity.ERROR));

        // Delta-based: ROM v_rings may carry a pre-SS value into the maze
        // while the engine's ringsCollected always starts at 0 for a fresh
        // stage entry, so the per-frame delta against the trace's own first
        // row is the comparable quantity.
        int expectedRingsDelta = (tf.rings() - baselineRings) & 0xFFFF;
        fields.put("rings", cmp("rings",
                str(expectedRingsDelta), str(state.ringsCollected()), Severity.ERROR));

        boolean expectedEmeraldCollected = tf.emeralds() != baselineEmeralds;
        fields.put("emeralds", cmp("emeralds",
                bool(expectedEmeraldCollected), bool(state.emeraldCollected()), Severity.ERROR));
    }

    private static FieldComparison cmp(String name, String expected, String actual,
                                       Severity mismatchSeverity) {
        boolean match = Objects.equals(expected, actual);
        Severity severity = match ? Severity.MATCH : mismatchSeverity;
        return new FieldComparison(name, expected, actual, severity, numericDelta(expected, actual));
    }

    private static int numericDelta(String expected, String actual) {
        try {
            return Integer.parseInt(actual) - Integer.parseInt(expected);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String str(int value) {
        return Integer.toString(value);
    }

    private static String bool(boolean value) {
        return Boolean.toString(value);
    }

    // ==================== Report output ====================

    static void writeReport(DivergenceReport report, int ssIndex) throws IOException {
        Path outDir = reportDir();
        Files.createDirectories(outDir);
        String prefix = "s1_special_stage_" + ssIndex;
        Path jsonPath = outDir.resolve(prefix + "_report.json");
        Files.writeString(jsonPath, report.toJson());
        if (report.hasErrors()) {
            Path contextPath = outDir.resolve(prefix + "_context.txt");
            int firstErrorFrame = report.errors().isEmpty()
                    ? 0
                    : report.errors().get(0).startFrame();
            Files.writeString(contextPath, report.getContextWindow(firstErrorFrame, CONTEXT_RADIUS));
        }
    }

    static Path reportDir() {
        return Path.of("target", "trace-reports");
    }

    static int specialStageIndex(Sonic1SpecialStageTraceData trace) {
        Integer index = trace.metadata().specialStageIndex();
        return index != null ? index : 0;
    }
}

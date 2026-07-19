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
 *   <li><b>Multi-frame lag boundary (torn-capture guard).</b> The non-lag row
 *       that immediately precedes a run of two or more lag frames is a partial
 *       RAM snapshot: the 68k was still mid-iteration when that VBlank sample
 *       fired, so its columns straddle two game-logic steps. Such a row is
 *       still <em>stepped</em> (the engine's cumulative game-logic advances
 *       must stay aligned -- it re-syncs at the next compared row) but is
 *       omitted from the report, since no coherent engine frame can equal a
 *       torn row on every field at once. Keyed on the recorded {@code lag}
 *       column, not a frame number -- see {@code isTornLagBoundaryRow}.</li>
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
 *       word equality is what matters). These are special-stage <em>globals</em>
 *       ({@code v_ssangle}/{@code v_ssrotate}) and are compared on every
 *       stepped row, including the pre-start hold, where both sides read 0.</li>
 *   <li>{@code rings}: <b>direct from 0</b> -- expected {@code tf.rings() &
 *       0xFFFF} vs actual {@code state.ringsCollected()}. {@code GM_Special}'s
 *       setup block clears the ring counter unconditionally
 *       ({@code clr.w (v_rings).w}, {@code docs/s1disasm/sonic.asm:3286})
 *       before the maze runs, so the live {@code v_rings} is a fresh count
 *       from 0 directly comparable to the engine's {@code ringsCollected}.
 *       (An earlier delta-vs-frame-0 basis was wrong: frame 0 is recorded
 *       during the pre-start fade and still holds the <em>pre-clear</em>
 *       leftover {@code v_rings} of the previous zone -- {@code $55}/85 in the
 *       committed maze trace -- so subtracting it drove every live value
 *       negative and masked it into a huge {@code u16}. See the pre-start-hold
 *       note below; {@code rings} is only compared once setup has run.)</li>
 *   <li>{@code emeralds}: expected boolean
 *       {@code tf.emeralds() != trace.getFrame(0).emeralds()} vs actual
 *       {@code state.emeraldCollected()} (emeralds persist across stages, so
 *       the frame-0 baseline detects a newly-collected emerald in this run).</li>
 * </ul>
 *
 * <h2>Recorded ROM pre-start hold (comparison-only observation)</h2>
 * <p>{@code GM_Special} runs {@code PaletteWhiteOut}
 * ({@code docs/s1disasm/sonic.asm:3224}) <em>before</em> its instant setup
 * block clears the previous zone's object RAM
 * ({@code clearRAM v_objspace}, {@code sonic.asm:3243}), clears the ring
 * counter ({@code sonic.asm:3286}), sets the stage rotation speed
 * ({@code move.w #ss_rotatespeed,(v_ssrotate).w}, {@code $40},
 * {@code sonic.asm:3268}), and finally lets {@code SS_MainLoop}'s first
 * {@code jsr (ExecuteObjects).l} tick Obj09 for the first time
 * ({@code sonic.asm:3306}). So while the fade is still running, the
 * object-owned physics columns ({@code x_pos}/{@code y_pos}/{@code vel_x}/
 * {@code vel_y}/{@code inertia}/{@code status} bits) and the ring counter
 * hold <em>pre-clear leftover</em> RAM from the preceding zone, not live
 * special-stage state -- the committed trace's frame-0 {@code x_pos}
 * {@code $25AB0300} / {@code vel_x} -34 / {@code status $07} is the previous
 * GHZ Sonic curled into the giant ring, which a standalone special-stage
 * segment cannot reproduce. {@code v_ssrotate} is written to {@code $40}
 * exactly once at setup and only ever <em>increases</em> thereafter (up to
 * {@code $1800}/{@code $3000} on the exit ramp,
 * {@code docs/s1disasm/_incObj/09 Sonic in Special Stage.asm:385}), so a
 * recorded {@code ss_rotate == 0} uniquely marks the pre-setup fade among the
 * compared (non-lag) rows. This comparator therefore gates the object-owned
 * physics columns and the ring counter out of the report while
 * {@code ss_rotate == 0}: it observes a recorded ROM pre-start hold, it does
 * not skip N frames by number and never hydrates engine state.
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

            // Torn-capture guard (see class javadoc "Multi-frame lag boundary"
            // note): the single non-lag row that immediately precedes a run of
            // two or more lag frames is a partial RAM snapshot -- the 68k was
            // still mid-iteration when the VBlank sample fired -- so its columns
            // straddle two game-logic steps and no coherent engine state can
            // equal it on every field at once. The engine is still stepped
            // above (its cumulative game-logic advances must stay aligned; it
            // re-syncs exactly at the next compared row), but the incoherent
            // row itself is omitted from the report rather than scored against
            // a whole engine frame.
            if (isTornLagBoundaryRow(trace, f, compareEnd)) {
                continue;
            }

            Map<String, FieldComparison> fields = new LinkedHashMap<>();
            addFields(fields, tf, state, baselineEmeralds);
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

    /**
     * True when non-lag trace row {@code f} is the immediate predecessor of a
     * lag run of length &ge; 2, marking it as a torn (partial) RAM capture that
     * must not be scored against a coherent engine frame.
     *
     * <p>BizHawk flags a frame as a lag frame when the console did not complete
     * a full game-loop iteration (input was not polled) during that emulated
     * frame. A single lag frame is harmless: the preceding sample is a
     * completed frame and the lag frame merely re-samples the same settled RAM.
     * A run of two or more consecutive lag frames means one game-logic
     * iteration spanned three or more VBlank sample points, so the Lua RAM
     * snapshot taken at the VBlank immediately <em>before</em> the burst can
     * catch that iteration's writes partway through -- some object fields
     * already updated, others not. The committed maze trace's sole
     * &ge;2 lag burst (frames 1768-1769) is preceded by exactly such a torn row
     * (frame 1767): its {@code vel_x}/{@code vel_y}/{@code inertia} already hold
     * the next step's values while {@code x_pos} is partway and
     * {@code y_pos}/{@code ss_angle} still hold the previous step, so it is not
     * a coherent ROM frame and cannot be reproduced by any single engine tick.
     * The guard keys purely on the recorded {@code lag} column (a recording
     * semantic the replay loop already respects when it skips lag rows) and
     * generalises to any such boundary; it is not a frame-number carve-out.
     */
    private static boolean isTornLagBoundaryRow(Sonic1SpecialStageTraceData trace,
                                                int f, int compareEnd) {
        return f + 2 < compareEnd
                && trace.getFrame(f + 1).lag()
                && trace.getFrame(f + 2).lag();
    }

    private static void addFields(Map<String, FieldComparison> fields,
                                  Sonic1SpecialStageTraceFrame tf,
                                  Sonic1SpecialStageComparisonState state,
                                  int baselineEmeralds) {
        // Recorded ROM pre-start hold marker (see class javadoc): GM_Special
        // sets v_ssrotate = ss_rotatespeed ($40) exactly once, in its instant
        // setup block after PaletteWhiteOut (docs/s1disasm/sonic.asm:3268),
        // and it only ever increases thereafter (_incObj/09 Sonic in Special
        // Stage.asm:385). So a recorded ss_rotate of 0 uniquely marks the
        // pre-setup fade, during which v_objspace/v_rings are still uncleared
        // (sonic.asm:3243,3286) and Obj09 has not been executed
        // (first ExecuteObjects at sonic.asm:3306). While that holds, the
        // object-owned physics columns and the ring counter carry leftover
        // previous-zone RAM, not live special-stage state, so they are not
        // comparable and are omitted from the report until setup has run.
        boolean preStartHold = (tf.ssRotate() & 0xFFFF) == 0;

        // Special-stage globals: cleared/set by GM_Special's setup and read as
        // 0 by both sides during the fade -- always comparable.
        fields.put("ss_angle", cmp("ss_angle",
                str(tf.ssAngle() & 0xFFFF), str(state.ssAngle() & 0xFFFF), Severity.ERROR));
        fields.put("ss_rotate", cmp("ss_rotate",
                str(tf.ssRotate() & 0xFFFF), str(state.ssRotate() & 0xFFFF), Severity.ERROR));
        fields.put("bg_anim", cmp("bg_anim",
                str(tf.bgAnim() & 0xFFFF), str(state.bgAnimState() & 0xFFFF), Severity.ERROR));

        // Emeralds persist across special stages (not cleared by setup), so the
        // frame-0 baseline detects a newly-collected emerald in this run.
        boolean expectedEmeraldCollected = tf.emeralds() != baselineEmeralds;
        fields.put("emeralds", cmp("emeralds",
                bool(expectedEmeraldCollected), bool(state.emeraldCollected()), Severity.ERROR));

        if (preStartHold) {
            return;
        }

        // ---- Obj09-owned physics (live only once ExecuteObjects has run) ----
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

        // Direct from 0: GM_Special clears v_rings (docs/s1disasm/sonic.asm:3286)
        // before the maze runs, so the live recorded count is a fresh tally
        // from 0 directly comparable to the engine's ringsCollected.
        fields.put("rings", cmp("rings",
                str(tf.rings() & 0xFFFF), str(state.ringsCollected()), Severity.ERROR));
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

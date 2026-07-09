package com.openggf.tests.trace.s2;

import com.openggf.data.Rom;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageComparisonState;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageComparisonState.PlayerState;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.trace.DivergenceReport;
import com.openggf.trace.FieldComparison;
import com.openggf.trace.FrameComparison;
import com.openggf.trace.Severity;
import com.openggf.trace.SpecialStageTraceData;
import com.openggf.trace.SpecialStageTraceFrame;
import com.openggf.trace.SpecialStageTraceFrame.CharacterState;
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
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Headless replay comparator for a Sonic 2 special-stage trace. Drives the
 * production {@link com.openggf.game.sonic2.Sonic2SpecialStageProvider} through
 * {@link S2SpecialStageReplayHarness}, comparing each stepped frame against the
 * recorded ROM trace and emitting a divergence report to
 * {@code target/trace-reports/s2_special_stage_&lt;index&gt;_report.json}.
 *
 * <h2>Replay semantics</h2>
 * <ul>
 *   <li><b>Trace-paced.</b> Iterate trace frames {@code 0..compareEnd-1}. A lag
 *       row advances nothing engine-side (the row is skipped entirely); a
 *       non-lag row is stepped exactly once. The press-edge diff still uses the
 *       previous <em>physical</em> BK2 row (see the harness), so skipping lag
 *       rows does not corrupt edge detection.</li>
 *   <li><b>Comparison-only.</b> Trace values are read for input + expectation
 *       only; engine state is never hydrated from the trace.</li>
 *   <li><b>Finish boundary.</b> {@code compareEnd = stageFinishedFrame().orElse(
 *       frameCount())}. A Tier-1 {@code finished_transition_frame} check asserts
 *       the engine's {@code isFinished()} first becomes true exactly at the
 *       recorded stage-finished frame.</li>
 * </ul>
 *
 * <h2>Comparator tiers</h2>
 * <p>Tier-1 mismatches are report ERRORs, Tier-2 are WARNINGs. Only fields
 * exposed by {@link Sonic2SpecialStageComparisonState} are wired; trace columns
 * with no engine counterpart in that snapshot (per-player rings,
 * {@code rings_togo_bcd}, {@code swap_positions_flag}, hurt/slide/flip timers)
 * are not compared, and {@code player_anim_frame_timer} is intentionally never
 * compared (its engine counterpart is a constant).
 * <ul>
 *   <li><b>Tier-1</b>: per-player {@code present}, {@code ss_x}, {@code ss_y},
 *       {@code ss_z}, {@code angle}, {@code routine} (mapped ROM byte → engine
 *       {@code RoutineState} name), {@code hurt} ({@code routine_secondary==2});
 *       {@code combined_rings}, {@code speed_factor}, {@code current_segment},
 *       {@code track_anim_frame}, {@code finished}, and the
 *       {@code finished_transition_frame} boundary check.</li>
 *   <li><b>Tier-2</b>: {@code track_drawing_index}, {@code track_duration_timer}
 *       (ROM counts down from the {@code SSAnim_Base_Duration} for the current
 *       speed factor; engine counts up — compared via
 *       {@code duration - romTimer == engineCounter}), and
 *       {@code tails_control_counter}.</li>
 * </ul>
 *
 * <p>This is a red-allowed MVP: divergences are recorded, not asserted. Known
 * engine gaps (control-lock timing, Tails CPU, intro timing) legitimately
 * diverge and must NOT be papered over by editing engine code — the deliverable
 * is a faithful report. The pipeline itself (trace loads, harness steps to
 * {@code compareEnd} without exceptions, report written) IS asserted, and
 * {@link #assertNoReleaseBlockingDivergences} is the one-line ratchet to flip
 * on once parity lands (see {@code docs/TRACE_FRONTIER_LOG.md}).
 */
public abstract class AbstractS2SpecialStageTraceReplayTest {

    /** Location of the committed MVP trace (also used by the determinism test). */
    static final Path TRACE_DIRECTORY =
            Path.of("src", "test", "resources", "traces", "s2", "special_stage");

    /** {@code SSAnim_Base_Duration} (s2.asm), indexed by {@code (speed_factor>>1)&7}. */
    private static final int[] ANIM_BASE_DURATIONS = {60, 30, 15, 10, 8, 6, 5, 0};

    /**
     * ROM Obj09/Obj10 (SS Sonic/Tails) routine index → engine
     * {@code RoutineState.name()}. From s2.asm {@code Obj09_Index}: 0 Init,
     * 2 MdNormal, 4 MdJump, 6 invalid, 8 MdAir.
     */
    private static final Map<Integer, String> ROUTINE_NAMES = Map.of(
            0, "INIT",
            2, "NORMAL",
            4, "JUMPING",
            8, "AIRBORNE");

    private static final String ABSENT = "absent";
    private static final int CONTEXT_RADIUS = 8;

    /** Directory of the trace to replay. */
    protected abstract Path traceDirectory();

    @Test
    void replayProducesFaithfulReport() throws Exception {
        File romFile = RomTestUtils.ensureSonic2RomAvailable();
        assumeTrue(romFile != null && Files.exists(Path.of("s2.gen")),
                "s2.gen ROM required for S2 special-stage trace replay");

        Path dir = traceDirectory();
        SpecialStageTraceData trace = SpecialStageTraceData.load(dir);

        // Pipeline assertion: the trace loads with the expected profile + frames.
        assertEquals("s2_special_stage", trace.metadata().traceProfile(),
                "SS trace must carry the s2_special_stage profile");
        assertTrue(trace.frameCount() > 0, "SS trace should have frames");

        S2SpecialStageReplayHarness harness = bootHarness(trace, dir, romFile);
        DivergenceReport report = compareReplay(trace, harness);

        int ssIndex = specialStageIndex(trace);
        writeReport(report, ssIndex);

        // Pipeline assertion: the report file was written where consumers expect.
        Path jsonPath = reportDir().resolve("s2_special_stage_" + ssIndex + "_report.json");
        assertTrue(Files.exists(jsonPath), "report JSON should be written to " + jsonPath);

        assertNoReleaseBlockingDivergences(report);
    }

    /**
     * Release-gate ratchet, intentionally disabled for the red-allowed MVP.
     *
     * <p>The engine currently diverges from the recorded ROM trace on known
     * fronts (control-lock timing, Tails CPU behaviour, intro timing); those are
     * captured in the report rather than failing the test. When the engine
     * reaches parity, replace the body with
     * {@code assertFalse(report.hasErrors(), report.toAssertionSummary());} and
     * record the frontier move in {@code docs/TRACE_FRONTIER_LOG.md}.
     */
    protected void assertNoReleaseBlockingDivergences(DivergenceReport report) {
        // Disabled ratchet — see docs/TRACE_FRONTIER_LOG.md.
    }

    // ==================== Boot ====================

    static S2SpecialStageReplayHarness bootHarness(SpecialStageTraceData trace,
                                                   Path dir,
                                                   File romFile) throws IOException {
        // Headless graphics so the SS manager's pattern/renderer setup is safe
        // without a GL context.
        GraphicsManager.getInstance().resetState();
        GraphicsManager.getInstance().initHeadless();

        Rom rom = new Rom();
        rom.open(romFile.getAbsolutePath());
        // Installs the Sonic2GameModule, wires GameServices.rom(), and rebuilds a
        // fresh gameplay mode. Resets configuration to defaults, so team config is
        // (re)applied inside the harness ctor afterwards.
        TestEnvironment.configureRomFixture(rom);
        GraphicsManager.getInstance().initHeadless();

        int offset = trace.metadata().bk2FrameOffset();
        int ssIndex = specialStageIndex(trace);
        Path bk2 = dir.resolve(trace.metadata().sourceBk2());
        return new S2SpecialStageReplayHarness(bk2, offset, ssIndex);
    }

    // ==================== Comparator ====================

    static DivergenceReport compareReplay(SpecialStageTraceData trace,
                                          S2SpecialStageReplayHarness harness) {
        OptionalInt ssFinished = trace.stageFinishedFrame();
        int compareEnd = ssFinished.orElse(trace.frameCount());

        List<FrameComparison> comparisons = new ArrayList<>();
        int firstEngineFinished = -1;

        for (int f = 0; f < compareEnd; f++) {
            SpecialStageTraceFrame tf = trace.getFrame(f);
            if (tf.lag()) {
                continue; // lag row: nothing steps engine-side
            }
            harness.stepFrame(f);
            Sonic2SpecialStageComparisonState state = harness.capture();
            if (firstEngineFinished < 0 && state.finished()) {
                firstEngineFinished = f;
            }

            Map<String, FieldComparison> fields = new LinkedHashMap<>();
            addManagerFields(fields, tf, state, false);
            addPlayerFields(fields, "sonic", tf.sonic(), state.sonic());
            addPlayerFields(fields, "tails", tf.tails(), state.tails());
            comparisons.add(new FrameComparison(f, fields));
        }

        // Tier-1 finish transition: step the recorded finish frame so we can
        // observe whether the engine flips isFinished() at the same frame.
        if (ssFinished.isPresent()) {
            int sf = ssFinished.getAsInt();
            if (sf >= 0 && sf < trace.frameCount() && !trace.getFrame(sf).lag()) {
                harness.stepFrame(sf);
                if (firstEngineFinished < 0 && harness.isFinished()) {
                    firstEngineFinished = sf;
                }
            }
            Map<String, FieldComparison> fields = new LinkedHashMap<>();
            String expected = String.valueOf(sf);
            String actual = firstEngineFinished < 0 ? "never" : String.valueOf(firstEngineFinished);
            fields.put("finished_transition_frame",
                    cmp("finished_transition_frame", expected, actual, Severity.ERROR));
            comparisons.add(new FrameComparison(sf, fields));
        }

        return new DivergenceReport(comparisons);
    }

    private static void addManagerFields(Map<String, FieldComparison> fields,
                                         SpecialStageTraceFrame tf,
                                         Sonic2SpecialStageComparisonState state,
                                         boolean finishedExpected) {
        // Tier-1
        fields.put("speed_factor",
                cmp("speed_factor", str(tf.speedFactor()), str(state.speedFactor()), Severity.ERROR));
        fields.put("current_segment",
                cmp("current_segment", str(tf.currentSegment()), str(state.currentSegmentIndex()), Severity.ERROR));
        fields.put("track_anim_frame",
                cmp("track_anim_frame", str(tf.trackAnimFrame()), str(state.trackAnimFrame()), Severity.ERROR));
        int combinedTraceRings = ringsBinary(tf.sonic()) + ringsBinary(tf.tails());
        fields.put("combined_rings",
                cmp("combined_rings", str(combinedTraceRings), str(state.combinedRings()), Severity.ERROR));
        fields.put("finished",
                cmp("finished", String.valueOf(finishedExpected), String.valueOf(state.finished()), Severity.ERROR));

        // Tier-2
        fields.put("track_drawing_index",
                cmp("track_drawing_index", str(tf.trackDrawingIndex()), str(state.drawingIndex()), Severity.WARNING));
        int expectedCounter = mapTrackDurationElapsed(tf.speedFactor(), tf.trackDurationTimer());
        fields.put("track_duration_timer",
                cmp("track_duration_timer", str(expectedCounter), str(state.trackFrameDelayCounter()), Severity.WARNING));
        fields.put("tails_control_counter",
                cmp("tails_control_counter", str(tf.tailsControlCounter()), str(state.tailsControlCounter()), Severity.WARNING));
    }

    static int mapTrackDurationElapsed(int speedFactor, int rawDurationTimer) {
        if (speedFactor == 0 && rawDurationTimer == 0) {
            return 0;
        }
        int duration = ANIM_BASE_DURATIONS[(speedFactor >> 1) & 7];
        return duration - rawDurationTimer;
    }

    private static void addPlayerFields(Map<String, FieldComparison> fields,
                                        String prefix,
                                        CharacterState tc,
                                        PlayerState ps) {
        boolean tracePresent = tc != null && tc.present();
        boolean engPresent = ps != null;
        fields.put(prefix + "_present",
                cmp(prefix + "_present", bool(tracePresent), bool(engPresent), Severity.ERROR));
        if (!tracePresent && !engPresent) {
            return;
        }

        String expRoutine = tracePresent ? mapRoutine(tc.routine()) : ABSENT;
        String engRoutine = engPresent ? ps.routine() : ABSENT;
        fields.put(prefix + "_routine",
                cmp(prefix + "_routine", expRoutine, engRoutine, Severity.ERROR));

        fields.put(prefix + "_ss_x", cmp(prefix + "_ss_x",
                intOr(tracePresent, tracePresent ? tc.ssX() : 0),
                intOr(engPresent, engPresent ? ps.ssX() : 0), Severity.ERROR));
        fields.put(prefix + "_ss_y", cmp(prefix + "_ss_y",
                intOr(tracePresent, tracePresent ? tc.ssY() : 0),
                intOr(engPresent, engPresent ? ps.ssY() : 0), Severity.ERROR));
        fields.put(prefix + "_ss_z", cmp(prefix + "_ss_z",
                intOr(tracePresent, tracePresent ? tc.ssZ() : 0),
                intOr(engPresent, engPresent ? ps.ssZ() : 0), Severity.ERROR));
        fields.put(prefix + "_angle", cmp(prefix + "_angle",
                intOr(tracePresent, tracePresent ? tc.angle() : 0),
                intOr(engPresent, engPresent ? ps.angle() : 0), Severity.ERROR));

        boolean traceHurt = tracePresent && tc.routineSecondary() == 2;
        boolean engHurt = engPresent && ps.routineSecondary() == 2;
        fields.put(prefix + "_hurt",
                cmp(prefix + "_hurt", bool(traceHurt), bool(engHurt), Severity.ERROR));
    }

    private static String mapRoutine(int romRoutineByte) {
        String mapped = ROUTINE_NAMES.get(romRoutineByte);
        return mapped != null
                ? mapped
                : String.format("ROM_0x%02X", romRoutineByte & 0xFF);
    }

    private static int ringsBinary(CharacterState c) {
        return c != null && c.present() ? c.ringsBinary() : 0;
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

    private static String intOr(boolean present, int value) {
        return present ? Integer.toString(value) : ABSENT;
    }

    // ==================== Report output ====================

    static void writeReport(DivergenceReport report, int ssIndex) throws IOException {
        Path outDir = reportDir();
        Files.createDirectories(outDir);
        String prefix = "s2_special_stage_" + ssIndex;
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

    static int specialStageIndex(SpecialStageTraceData trace) {
        Integer index = trace.metadata().specialStageIndex();
        return index != null ? index : 0;
    }
}

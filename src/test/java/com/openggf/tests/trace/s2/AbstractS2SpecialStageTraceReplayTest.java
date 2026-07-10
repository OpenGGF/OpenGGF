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
import com.openggf.trace.SpecialStageExpectedState;
import com.openggf.trace.SpecialStageTraceFrame;
import com.openggf.trace.SpecialStageRunObjectsPassBinder;
import com.openggf.trace.SpecialStageRunObjectsPassBinder.CompletedPass;
import com.openggf.trace.TraceEvent;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 *   <li><b>ROM-pass-paced after control unlock.</b> Startup retains the verified
 *       VBlank/non-lag pacing. Once {@code SpecialStage_Started} is observed,
 *       each observation executes exactly its ordered {@code run_objects_end}
 *       events: zero events means zero engine updates and two events means two.
 *       Each pass identifies the current and previous BK2 rows sampled by its
 *       preceding {@code Vint_S2SS}; held and pressed inputs are derived from
 *       those movie rows. Raw auxiliary held values are diagnostics only and
 *       are rejected if they disagree with the identified BK2 rows.</li>
 *   <li><b>Comparison-only.</b> Trace values are read for input + expectation
 *       only; engine state is never hydrated from the trace.</li>
 *   <li><b>Atomic object-pass expectations.</b> A VBlank CSV row can bisect
 *       the following ROM {@code RunObjects} pass. When the recorder provides
 *       ordered {@code run_objects_end} events, the pass binder selects the
 *       latest completed atomic result visible at that observation; player,
 *       ring, and Tails-control fields come from that snapshot, while track and
 *       finish/results fields remain on their CSV observation.</li>
 *   <li><b>Finish boundary.</b> {@code compareEnd = stageFinishedFrame().orElse(
 *       frameCount())}. A Tier-1 {@code finished_transition_frame} check asserts
 *       the engine's {@code isFinished()} first becomes true exactly at the
 *       final-checkpoint logical observation. The later Obj6F
 *       {@code results_started} event and results tail are recorded but not
 *       compared.</li>
 * </ul>
 *
 * <h2>Comparator tiers</h2>
 * <p>Tier-1 mismatches are report ERRORs, Tier-2 are WARNINGs. Only fields
 * exposed by {@link Sonic2SpecialStageComparisonState} are wired; trace columns
 * with no engine counterpart in that snapshot ({@code rings_togo_bcd}) are not
 * compared, and
 * {@code player_anim_frame_timer} is intentionally never compared (its engine
 * counterpart is a constant).
 * <ul>
 *   <li><b>Tier-1</b>: per-player {@code present}, {@code ss_x}, {@code ss_y},
 *       {@code ss_z}, {@code angle}, {@code routine} (mapped ROM byte → engine
 *       {@code RoutineState} name), {@code hurt} ({@code routine_secondary==2});
 *       per-player {@code sonic_rings}/{@code tails_rings},
 *       {@code combined_rings}, {@code speed_factor}, {@code current_segment},
 *       {@code track_anim_frame}, {@code swap_positions_flag}, per-player
 *       {@code hurt_timer}/{@code slide_timer}, {@code finished}, and the
 *       {@code finished_transition_frame} boundary check.</li>
 *   <li><b>Tier-2</b>: {@code track_drawing_index}, {@code track_duration_timer}
 *       (ROM counts down from the {@code SSAnim_Base_Duration} for the current
 *       speed factor; engine counts up — compared via
 *       {@code duration - romTimer == engineCounter}), and
 *       {@code tails_control_counter}, and per-player {@code flip_timer}.</li>
 * </ul>
 * <p>Player timers are compared only from atomic {@code run_objects_end}
 * snapshots: a raw VBlank row can interrupt the Obj09→Obj10 scan and contain
 * Sonic's post-pass timer beside Tails's pre-pass timer.</p>
 *
 * <p>Tier-1 parity is release-gated: the pipeline writes a complete report and
 * {@link #assertNoReleaseBlockingDivergences} rejects any ERROR divergence.
 * Tier-2 WARNING fields remain visible in the report while their state and
 * ratchets are implemented (see {@code docs/TRACE_FRONTIER_LOG.md}).
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
     * Release-gate ratchet for Tier-1 fields, which use ERROR severity.
     */
    protected void assertNoReleaseBlockingDivergences(DivergenceReport report) {
        assertFalse(report.hasErrors(), report.toAssertionSummary());
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
        OptionalInt ssFinishedObserved = trace.stageFinishedObservedFrame();
        int compareEnd = ssFinished.orElse(trace.frameCount());

        List<FrameComparison> comparisons = new ArrayList<>();
        int firstEngineFinished = -1;
        SpecialStageRunObjectsPassBinder passBinder =
                new SpecialStageRunObjectsPassBinder(
                        trace.runObjectsEndSnapshots(),
                        trace.frameCount(),
                        frame -> !trace.getFrame(frame).lag()
                                || (ssFinishedObserved.isPresent()
                                && frame == ssFinishedObserved.getAsInt()),
                        trace.metadata().bk2FrameOffset(),
                        harness.movieFrames());
        int passPacingStart = trace.controlStateTransitions().stream()
                .filter(SpecialStageTraceData.ControlStateTransition::started)
                .mapToInt(SpecialStageTraceData.ControlStateTransition::frame)
                .findFirst()
                .orElse(Integer.MAX_VALUE);

        for (int f = 0; f < compareEnd; f++) {
            SpecialStageTraceFrame tf = trace.getFrame(f);
            List<CompletedPass> completedPasses = passBinder.passesForObservation(f);
            if (f < passPacingStart) {
                if (tf.lag()) {
                    continue;
                }
                harness.stepFrame(f);
            } else {
                for (CompletedPass pass : completedPasses) {
                    harness.stepPass(pass);
                }
                if (tf.lag()) {
                    continue;
                }
            }
            Sonic2SpecialStageComparisonState state = harness.capture();
            if (firstEngineFinished < 0 && state.finished()) {
                firstEngineFinished = f;
            }

            Map<String, FieldComparison> fields = new LinkedHashMap<>();
            List<TraceEvent> passEnd = f >= passPacingStart
                    ? passBinder.latestCompleted()
                            .map(CompletedPass::snapshot)
                            .<List<TraceEvent>>map(List::of)
                            .orElseGet(List::of)
                    : List.of();
            SpecialStageExpectedState expected =
                    SpecialStageExpectedState.from(tf, passEnd);
            addManagerFields(fields, expected, state, false);
            addPlayerFields(fields, "sonic", expected.sonic(), state.sonic(),
                    expected.hasRunObjectsEnd());
            addPlayerFields(fields, "tails", expected.tails(), state.tails(),
                    expected.hasRunObjectsEnd());
            comparisons.add(new FrameComparison(f, fields));
        }

        // Tier-1 finish transition: step the recorded finish frame so we can
        // observe whether the engine flips isFinished() at the same frame.
        if (ssFinished.isPresent()) {
            int sf = ssFinished.getAsInt();
            String finishTransitionActual = "never";
            if (sf >= 0 && sf < trace.frameCount()) {
                List<CompletedPass> completedPasses = passBinder.passesForObservation(sf);
                if (sf < passPacingStart) {
                    if (!trace.getFrame(sf).lag()) {
                        harness.stepFrame(sf);
                    }
                } else {
                    completedPasses.forEach(harness::stepPass);
                }
            }
            // The recorder labels finish with the last logical non-lag frame,
            // while publishing the finish-causing pass at the following raw lag
            // observation. The engine must still be unfinished immediately
            // before that pass, and must become finished because of it.
            boolean finishedBeforeTerminal = firstEngineFinished >= 0 || harness.isFinished();
            if (finishedBeforeTerminal) {
                finishTransitionActual = "before-terminal-pass@" + sf;
            }
            int observed = ssFinishedObserved.orElse(sf);
            if (observed <= sf || observed >= trace.frameCount()) {
                throw new IllegalStateException(
                        "stage finish must have one later raw observation; logical="
                                + sf + " observed=" + observed);
            }
            List<CompletedPass> finishPasses = passBinder.passesForObservation(observed);
            if (finishPasses.size() != 1) {
                throw new IllegalStateException(
                        "stage finish observation must own exactly one completed pass; got "
                                + finishPasses.size());
            }
            finishPasses.forEach(harness::stepPass);
            if (!finishedBeforeTerminal && harness.isFinished()) {
                finishTransitionActual = String.valueOf(sf);
            }
            if (passBinder.hasRemaining()) {
                throw new IllegalStateException(
                        "run_objects_end passes remain after the terminal finish observation");
            }
            Map<String, FieldComparison> fields = new LinkedHashMap<>();
            String expected = String.valueOf(sf);
            fields.put("finished_transition_frame",
                    cmp("finished_transition_frame", expected, finishTransitionActual,
                            Severity.ERROR));
            comparisons.add(new FrameComparison(sf, fields));
        }

        return new DivergenceReport(comparisons);
    }

    private static void addManagerFields(Map<String, FieldComparison> fields,
                                         SpecialStageExpectedState expected,
                                         Sonic2SpecialStageComparisonState state,
                                         boolean finishedExpected) {
        SpecialStageTraceFrame tf = expected.csv();
        SpecialStageExpectedState.RunObjectsEndState pass = expected.runObjectsEnd();
        int speedFactor = pass != null ? pass.speedFactor() : tf.speedFactor();
        int currentSegment = pass != null ? pass.currentSegment() : tf.currentSegment();
        int trackAnimFrame = pass != null ? pass.trackAnimFrame() : tf.trackAnimFrame();
        int trackDrawingIndex = pass != null ? pass.trackDrawingIndex() : tf.trackDrawingIndex();
        int trackDurationTimer = pass != null ? pass.trackDurationTimer() : tf.trackDurationTimer();
        // Tier-1
        fields.put("speed_factor",
                cmp("speed_factor", str(speedFactor), str(state.speedFactor()), Severity.ERROR));
        fields.put("current_segment",
                cmp("current_segment", str(currentSegment), str(state.currentSegmentIndex()), Severity.ERROR));
        fields.put("track_anim_frame",
                cmp("track_anim_frame", str(trackAnimFrame), str(state.trackAnimFrame()), Severity.ERROR));
        fields.put("combined_rings",
                cmp("combined_rings", str(expected.combinedRings()), str(state.combinedRings()), Severity.ERROR));
        fields.put("finished",
                cmp("finished", String.valueOf(finishedExpected), String.valueOf(state.finished()), Severity.ERROR));

        // Tier-2
        fields.put("track_drawing_index",
                cmp("track_drawing_index", str(trackDrawingIndex), str(state.drawingIndex()), Severity.WARNING));
        int expectedCounter = mapTrackDurationElapsed(speedFactor, trackDurationTimer);
        fields.put("track_duration_timer",
                cmp("track_duration_timer", str(expectedCounter), str(state.trackFrameDelayCounter()), Severity.WARNING));
        fields.put("tails_control_counter",
                cmp("tails_control_counter", str(expected.tailsControlCounter()), str(state.tailsControlCounter()), Severity.WARNING));
        int swapPositionsFlag = pass != null ? pass.swapPositionsFlag() : tf.swapPositionsFlag();
        fields.put("swap_positions_flag",
                cmp("swap_positions_flag", str(swapPositionsFlag),
                        str(state.swapPositionsFlag()), Severity.ERROR));
    }

    /** Focused comparison seam used by atomic RunObjects-end mapping tests. */
    static Map<String, FieldComparison> compareExpectedFrame(
            SpecialStageExpectedState expected,
            Sonic2SpecialStageComparisonState state) {
        Map<String, FieldComparison> fields = new LinkedHashMap<>();
        addManagerFields(fields, expected, state, false);
        addPlayerFields(fields, "sonic", expected.sonic(), state.sonic(),
                expected.hasRunObjectsEnd());
        addPlayerFields(fields, "tails", expected.tails(), state.tails(),
                expected.hasRunObjectsEnd());
        return fields;
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
                                        PlayerState ps,
                                        boolean atomicPassEnd) {
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
                intOr(tracePresent, tracePresent ? signedWord(tc.ssX()) : 0),
                intOr(engPresent, engPresent ? ps.ssX() : 0), Severity.ERROR));
        fields.put(prefix + "_ss_y", cmp(prefix + "_ss_y",
                intOr(tracePresent, tracePresent ? signedWord(tc.ssY()) : 0),
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
        fields.put(prefix + "_rings", cmp(prefix + "_rings",
                intOr(tracePresent, tracePresent ? tc.ringsBinary() : 0),
                intOr(engPresent, engPresent ? ps.rings() : 0), Severity.ERROR));
        // A VBlank CSV sample can bisect RunObjects between Obj09 and Obj10
        // (the committed trace does so at f160 and f183). Player-owned timers
        // are therefore compared only against the recorder's atomic pass-end
        // snapshot, never against a mixed raw observation.
        if (atomicPassEnd) {
            fields.put(prefix + "_hurt_timer", cmp(prefix + "_hurt_timer",
                    intOr(tracePresent, tracePresent ? tc.hurtTimer() : 0),
                    intOr(engPresent, engPresent ? ps.hurtTimer() : 0), Severity.ERROR));
            fields.put(prefix + "_slide_timer", cmp(prefix + "_slide_timer",
                    intOr(tracePresent, tracePresent ? tc.slideTimer() : 0),
                    intOr(engPresent, engPresent ? ps.slideTimer() : 0), Severity.ERROR));
            fields.put(prefix + "_flip_timer", cmp(prefix + "_flip_timer",
                    intOr(tracePresent, tracePresent ? tc.flipTimer() : 0),
                    intOr(engPresent, engPresent ? ps.flipTimer() : 0), Severity.WARNING));
        }
    }

    /**
     * Maps a raw 68000 word to the signed value used by the ROM's track-space
     * coordinate arithmetic. {@code ss_x_pos}/{@code ss_y_pos} are the integer
     * words of 16.16 positions and are consumed by sign branches and signed
     * multiplies; {@code ss_z_pos} is a separate positive depth word and is not
     * routed through this mapping (s2.asm:69290-69303, 69324-69374,
     * 69450-69458, 69718-69750).
     */
    private static int signedWord(int rawWord) {
        return (short) rawWord;
    }

    private static String mapRoutine(int romRoutineByte) {
        String mapped = ROUTINE_NAMES.get(romRoutineByte);
        return mapped != null
                ? mapped
                : String.format("ROM_0x%02X", romRoutineByte & 0xFF);
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

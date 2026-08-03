package com.openggf.tests.trace.s2;

import com.openggf.data.Rom;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageComparisonState;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageComparisonState.PlayerState;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.trace.TraceFixtureRoot;
import com.openggf.tests.trace.TraceReportWriter;
import com.openggf.trace.DivergenceReport;
import com.openggf.trace.DynamicArtSpecialStageComparator;
import com.openggf.trace.DynamicArtSpillNormalization;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import static com.openggf.tests.trace.TraceFieldComparisons.bool;
import static com.openggf.tests.trace.TraceFieldComparisons.cmp;
import static com.openggf.tests.trace.TraceFieldComparisons.str;
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
 *   <li><b>Started boundary pass.</b> The observation that first exposes
 *       {@code SpecialStage_Started} follows Obj5F's terminal pre-start object
 *       pass. Replay completes that already-pending pass without a new VInt;
 *       recorder pass sequence 0 begins with the following VInt sample.</li>
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
 * <h2>Release-ratcheted comparator surface</h2>
 * <p>Every mismatch below is a report ERROR. Every field is a comparison-only
 * read of {@link Sonic2SpecialStageComparisonState}.
 * <ul>
 *   <li>Per-player {@code present}, {@code ss_x}, {@code ss_y},
 *       {@code ss_z}, {@code angle}, {@code routine} (mapped ROM byte → engine
 *       {@code RoutineState} name), {@code hurt} ({@code routine_secondary==2});
 *       per-player {@code sonic_rings}/{@code tails_rings},
 *       {@code combined_rings}, {@code speed_factor}, {@code current_segment},
 *       {@code track_anim_frame}, {@code swap_positions_flag}, per-player
 *       {@code hurt_timer}/{@code slide_timer}/{@code flip_timer},
 *       {@code player_anim_frame_timer}, refresh-gated decoded
 *       {@code rings_togo_bcd}, {@code finished}, and the
 *       {@code finished_transition_frame} boundary check.</li>
 *   <li>{@code track_drawing_index}, {@code track_duration_timer}
 *       (ROM counts down from the {@code SSAnim_Base_Duration} for the current
 *       speed factor; engine counts up — compared via
 *       {@code duration - romTimer == engineCounter}), and
 *       {@code tails_control_counter}.</li>
 * </ul>
 * <p>Player timers are compared only from atomic {@code run_objects_end}
 * snapshots: a raw VBlank row can interrupt the Obj09→Obj10 scan and contain
 * Sonic's post-pass timer beside Tails's pre-pass timer.</p>
 *
 * <p>The pipeline writes a complete report and
 * {@link #assertNoReleaseBlockingDivergences} rejects any comparator divergence.
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

    /**
     * Iterations of {@code Pal_FadeToWhite}, the ROM's first act once the
     * special stage's {@code SS_Check_Rings_flag} breaks the main loop
     * (docs/s2disasm/s2.asm:6725-6745). The routine loads {@code d4} with
     * {@code $15} and runs one {@code VintID_Fade} V-blank per {@code dbf}
     * iteration (s2.asm:3570-3581) — {@code $15 + 1} of them. {@code Vint_Fade}
     * never reaches {@code ProcessDMAQueue} (only the real per-mode handlers do:
     * s2.asm:781, 899, 1000, 1046, 1083, 1138), so the terminal pass's queued
     * player art survives the whole fade, and then the interrupts-disabled
     * results-screen setup that follows (s2.asm:3546-3600 region: {@code move
     * #$2700,sr}, ClearScreen, PalLoad_Now, LoadPLC2, NemDec, clearRAM), until
     * the results loop asks for {@code VintID_Level} and waits on it
     * (s2.asm:6800-6806). That first {@code Vint_Level} is the retirement
     * V-blank.
     */
    static final int PAL_FADE_TO_WHITE_FRAMES = 0x15 + 1;

    /** Directory of the trace to replay. */
    protected abstract Path traceDirectory();

    @Test
    void replayProducesFaithfulReport() throws Exception {
        File romFile = RomTestUtils.ensureSonic2RomAvailable();
        assumeTrue(romFile != null && Files.exists(Path.of("s2.gen")),
                "s2.gen ROM required for S2 special-stage trace replay");

        Path dir = TraceFixtureRoot.resolve(traceDirectory());
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
        Set<Integer> ringsToGoRefreshFrames = ringsToGoRefreshFrames(trace);

        List<FrameComparison> comparisons = new ArrayList<>();
        // One replay owns one dynamic-art delivery-id origin.
        DynamicArtSpecialStageComparator dynamicArt =
                new DynamicArtSpecialStageComparator();
        // Rebind recorder submission edges that spilled past a frame boundary
        // (lag rows) back to the pass that produced them; the engine publishes
        // each RunObjects pass atomically. See DynamicArtSpillNormalization.
        Map<Integer, TraceEvent.DynamicArtTransferState> expectedDynamicArtRows =
                normalizedDynamicArtRows(trace);
        boolean[] dynamicArtCompared = new boolean[trace.frameCount()];
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
                    harness.stepLagRow();
                    addDynamicArtComparison(
                            comparisons, trace, expectedDynamicArtRows, harness, f,
                            new LinkedHashMap<>(), dynamicArtCompared, dynamicArt);
                    continue;
                }
                harness.stepFrame(f);
            } else {
                harness.stepPasses(
                        completedPasses,
                        isTerminalPreStartPassFrame(trace, f),
                        tf.lag(),
                        f);
                if (tf.lag()) {
                    addDynamicArtComparison(
                            comparisons, trace, expectedDynamicArtRows, harness, f,
                            new LinkedHashMap<>(), dynamicArtCompared, dynamicArt);
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
            addManagerFields(fields, expected, state, false,
                    ringsToGoRefreshFrames.contains(f));
            addPlayerFields(fields, "sonic", expected.sonic(), state.sonic(),
                    expected.hasRunObjectsEnd());
            addPlayerFields(fields, "tails", expected.tails(), state.tails(),
                    expected.hasRunObjectsEnd());
            addDynamicArtComparison(
                    comparisons, trace, expectedDynamicArtRows, harness, f, fields,
                    dynamicArtCompared, dynamicArt);
        }

        // Tier-1 finish transition: step the recorded finish frame so we can
        // observe whether the engine flips isFinished() at the same frame.
        if (ssFinished.isPresent()) {
            int sf = ssFinished.getAsInt();
            String finishTransitionActual = "never";
            if (sf >= 0 && sf < trace.frameCount()) {
                List<CompletedPass> completedPasses = passBinder.passesForObservation(sf);
                if (sf < passPacingStart) {
                    if (trace.getFrame(sf).lag()) {
                        harness.stepLagRow();
                    } else {
                        harness.stepFrame(sf);
                    }
                } else {
                    harness.stepPasses(
                            completedPasses, false,
                            trace.getFrame(sf).lag(),
                            sf);
                }
                addDynamicArtComparison(
                        comparisons, trace, expectedDynamicArtRows, harness, sf,
                        new LinkedHashMap<>(), dynamicArtCompared, dynamicArt);
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
            for (int frame = sf + 1; frame < observed; frame++) {
                harness.stepIdleRow(trace.getFrame(frame).lag());
                addDynamicArtComparison(
                        comparisons, trace, expectedDynamicArtRows, harness, frame,
                        new LinkedHashMap<>(), dynamicArtCompared, dynamicArt);
            }
            List<CompletedPass> finishPasses = passBinder.passesForObservation(observed);
            if (finishPasses.size() != 1) {
                throw new IllegalStateException(
                        "stage finish observation must own exactly one completed pass; got "
                                + finishPasses.size());
            }
            CompletedPass terminalPass = finishPasses.get(0);
            harness.stepPasses(
                    List.of(terminalPass), false,
                    trace.getFrame(observed).lag(),
                    observed);
            Sonic2SpecialStageComparisonState terminalState = harness.capture();
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

            SpecialStageExpectedState terminalExpected = SpecialStageExpectedState.from(
                    trace.getFrame(observed), List.of(terminalPass.snapshot()));
            Map<String, FieldComparison> terminalFields = new LinkedHashMap<>();
            addRingsToGoField(terminalFields, terminalExpected, terminalState);
            addDynamicArtComparison(
                    comparisons, trace, expectedDynamicArtRows, harness, observed, terminalFields,
                    dynamicArtCompared, dynamicArt);
        }

        if (trace.metadata().hasPerFrameDynamicArtTransferState()) {
            int fadeRowsLeft = ssFinished.isPresent() ? PAL_FADE_TO_WHITE_FRAMES : 0;
            for (int frame = 0; frame < trace.frameCount(); frame++) {
                if (dynamicArtCompared[frame]) {
                    continue;
                }
                PlcLifecyclePhase phase = PlcLifecyclePhase.SPECIAL_STAGE_RESULTS;
                if (fadeRowsLeft > 0 && !trace.getFrame(frame).lag()) {
                    phase = PlcLifecyclePhase.PALETTE_FADE;
                    fadeRowsLeft--;
                }
                harness.stepIdleRow(trace.getFrame(frame).lag(), phase);
                addDynamicArtComparison(
                        comparisons, trace, expectedDynamicArtRows, harness, frame,
                        new LinkedHashMap<>(), dynamicArtCompared, dynamicArt);
            }
        }
        return new DivergenceReport(comparisons);
    }

    static Map<Integer, TraceEvent.DynamicArtTransferState> normalizedDynamicArtRows(
            SpecialStageTraceData trace) {
        if (!trace.metadata().hasPerFrameDynamicArtTransferState()) {
            return Map.of();
        }
        Map<Integer, TraceEvent.DynamicArtTransferState> states = new LinkedHashMap<>();
        for (int f = 0; f < trace.frameCount(); f++) {
            TraceEvent.DynamicArtTransferState state =
                    trace.dynamicArtTransferStateForFrame(f);
            if (state != null) {
                states.put(f, state);
            }
        }
        int pacingStart = trace.controlStateTransitions().stream()
                .filter(SpecialStageTraceData.ControlStateTransition::started)
                .mapToInt(SpecialStageTraceData.ControlStateTransition::frame)
                .findFirst()
                .orElse(trace.frameCount());
        java.util.Set<Integer> cursorPrecededRows = new java.util.HashSet<>();
        List<DynamicArtSpillNormalization.PassBinding> passBindings = new ArrayList<>();
        for (TraceEvent.StateSnapshot snapshot : trace.runObjectsEndSnapshots()) {
            int boundRow = snapshot.frame();
            Object cursor = snapshot.fields().get("completion_cursor_frame");
            if (cursor == null) {
                continue;
            }
            int cursorFrame = Integer.parseInt(String.valueOf(cursor));
            passBindings.add(new DynamicArtSpillNormalization.PassBinding(
                    cursorFrame, boundRow));
            if (cursorFrame < boundRow) {
                cursorPrecededRows.add(boundRow);
            }
        }
        return DynamicArtSpillNormalization.rebindSubmissionSpills(
                states, trace.frameCount(), pacingStart,
                f -> trace.getFrame(f).lag(),
                cursorPrecededRows::contains,
                passBindings);
    }

    private static void addDynamicArtComparison(
            List<FrameComparison> comparisons,
            SpecialStageTraceData trace,
            Map<Integer, TraceEvent.DynamicArtTransferState> expectedDynamicArt,
            S2SpecialStageReplayHarness harness,
            int frame,
            Map<String, FieldComparison> fields,
            boolean[] compared,
            DynamicArtSpecialStageComparator dynamicArt) {
        if (trace.metadata().hasPerFrameDynamicArtTransferState()) {
            if (frame == trace.frameCount() - 1) {
                harness.finishDynamicArtSegment();
            }
            fields.putAll(dynamicArt.compare(
                    expectedDynamicArt.getOrDefault(frame,
                            trace.dynamicArtTransferStateForFrame(frame)),
                    harness.captureDynamicArt()).fields());
            compared[frame] = true;
        }
        if (!fields.isEmpty()) {
            comparisons.add(new FrameComparison(frame, fields));
        }
    }

    private static void addManagerFields(Map<String, FieldComparison> fields,
                                         SpecialStageExpectedState expected,
                                         Sonic2SpecialStageComparisonState state,
                                         boolean finishedExpected,
                                         boolean ringsToGoRefreshActive) {
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

        fields.put("track_drawing_index",
                cmp("track_drawing_index", str(trackDrawingIndex), str(state.drawingIndex()), Severity.ERROR));
        int expectedCounter = mapTrackDurationElapsed(speedFactor, trackDurationTimer);
        fields.put("track_duration_timer",
                cmp("track_duration_timer", str(expectedCounter), str(state.trackFrameDelayCounter()), Severity.ERROR));
        fields.put("tails_control_counter",
                cmp("tails_control_counter", str(expected.tailsControlCounter()), str(state.tailsControlCounter()), Severity.ERROR));
        int playerAnimFrameTimer = pass != null
                ? pass.playerAnimFrameTimer() : tf.playerAnimFrameTimer();
        fields.put("player_anim_frame_timer",
                cmp("player_anim_frame_timer", str(playerAnimFrameTimer),
                        str(state.playerAnimFrameTimer()), Severity.ERROR));
        if (ringsToGoRefreshActive) {
            addRingsToGoField(fields, expected, state);
        }
        int swapPositionsFlag = pass != null ? pass.swapPositionsFlag() : tf.swapPositionsFlag();
        fields.put("swap_positions_flag",
                cmp("swap_positions_flag", str(swapPositionsFlag),
                        str(state.swapPositionsFlag()), Severity.ERROR));
    }

    private static void addRingsToGoField(
            Map<String, FieldComparison> fields,
            SpecialStageExpectedState expected,
            Sonic2SpecialStageComparisonState state) {
        SpecialStageExpectedState.RunObjectsEndState pass = expected.runObjectsEnd();
        int ringsToGoBcd = pass != null
                ? pass.ringsToGoBcd() : expected.csv().ringsToGoBcd();
        fields.put("rings_togo_bcd",
                cmp("rings_togo_bcd", str(decodeRingsToGoBcd(ringsToGoBcd)),
                        str(state.ringsToGo()), Severity.ERROR));
    }

    /** Focused comparison seam used by atomic RunObjects-end mapping tests. */
    static Map<String, FieldComparison> compareExpectedFrame(
            SpecialStageExpectedState expected,
            Sonic2SpecialStageComparisonState state) {
        return compareExpectedFrame(expected, state, false);
    }

    static Map<String, FieldComparison> compareExpectedFrame(
            SpecialStageExpectedState expected,
            Sonic2SpecialStageComparisonState state,
            boolean ringsToGoRefreshActive) {
        Map<String, FieldComparison> fields = new LinkedHashMap<>();
        addManagerFields(fields, expected, state, false, ringsToGoRefreshActive);
        addPlayerFields(fields, "sonic", expected.sonic(), state.sonic(),
                expected.hasRunObjectsEnd());
        addPlayerFields(fields, "tails", expected.tails(), state.tails(),
                expected.hasRunObjectsEnd());
        return fields;
    }

    /** Decodes the ROM's packed three-digit {@code SS_RingsToGoBCD} word. */
    static int decodeRingsToGoBcd(int packedBcd) {
        if ((packedBcd & ~0x0FFF) != 0) {
            throw new IllegalArgumentException(
                    "rings-to-go BCD has bits outside 12-bit field: 0x"
                            + Integer.toHexString(packedBcd));
        }
        int hundreds = (packedBcd >> 8) & 0xF;
        int tens = (packedBcd >> 4) & 0xF;
        int units = packedBcd & 0xF;
        if (hundreds > 9 || tens > 9 || units > 9) {
            throw new IllegalArgumentException(
                    "rings-to-go field is not packed BCD: 0x"
                            + Integer.toHexString(packedBcd));
        }
        return hundreds * 100 + tens * 10 + units;
    }

    static boolean isRingsToGoRefreshFrame(SpecialStageTraceData trace, int frame) {
        return ringsToGoRefreshFrames(trace).contains(frame);
    }

    /**
     * The observation that first exposes {@code SpecialStage_Started} follows
     * the terminal pre-start {@code RunObjects} pass which set it. Recorder
     * pass identity begins with the next VInt sample, so replay must publish
     * this one semantic boundary pass through the native startup path.
     */
    static boolean isTerminalPreStartPassFrame(SpecialStageTraceData trace, int frame) {
        return trace.controlStateTransitions().stream()
                .anyMatch(transition -> transition.started() && transition.frame() == frame);
    }

    /**
     * Selects only observations where the ROM cell is known to have been
     * refreshed. Clearing {@code SS_TriggerRingsToGo} enables Obj5A's
     * calculation, so the first completed object pass strictly after that
     * transition is comparable. Later passes are not: a ring in a later SST
     * slot can change the live total after Obj5A already stored the BCD cell.
     * A rising {@code SS_Check_Rings_flag} is itself an explicit refresh gate
     * (s2.asm:71411-71582,71814-71842).
     */
    private static Set<Integer> ringsToGoRefreshFrames(SpecialStageTraceData trace) {
        List<TriggerSample> triggerSamples = new ArrayList<>();
        List<Integer> checkRingsFlags = new ArrayList<>();
        List<Integer> finishObservedFrames = new ArrayList<>();
        for (int frame = 0; frame < trace.frameCount(); frame++) {
            checkRingsFlags.add(trace.getFrame(frame).checkRingsFlag());

            for (TraceEvent event : trace.getEventsForFrame(frame)) {
                if (!(event instanceof TraceEvent.StateSnapshot snapshot)) {
                    continue;
                }
                if ("message_state".equals(snapshot.fields().get("type"))) {
                    Object rawTrigger = snapshot.fields().get("trigger_rings_to_go");
                    if (rawTrigger != null) {
                        triggerSamples.add(new TriggerSample(
                                frame, numericTraceValue(rawTrigger)));
                    }
                }
                if ("stage_finished".equals(snapshot.fields().get("type"))) {
                    Object rawObserved = snapshot.fields().get("observed_frame");
                    if (rawObserved == null) {
                        throw new IllegalStateException(
                                "stage_finished is missing observed_frame");
                    }
                    finishObservedFrames.add(numericTraceValue(rawObserved));
                }
            }
        }

        return discoverRingsToGoRefreshFrames(
                triggerSamples,
                trace.runObjectsEndSnapshots().stream()
                        .map(TraceEvent.StateSnapshot::frame)
                        .toList(),
                checkRingsFlags,
                finishObservedFrames);
    }

    record TriggerSample(int frame, int value) {
    }

    static Set<Integer> discoverRingsToGoRefreshFrames(
            List<TriggerSample> triggerSamples,
            List<Integer> completedPassFrames,
            List<Integer> checkRingsFlags,
            List<Integer> finishObservedFrames) {
        if (triggerSamples.isEmpty() || triggerSamples.get(0).value() != 0xFF) {
            throw new IllegalStateException(
                    "rings-to-go artifact is missing initial FF trigger sample");
        }

        List<Integer> triggerClearFrames = new ArrayList<>();
        int previousTrigger = 0xFF;
        int previousSampleFrame = -1;
        for (TriggerSample sample : triggerSamples) {
            if (sample.frame() <= previousSampleFrame) {
                throw new IllegalStateException(
                        "duplicate or out-of-order rings-to-go trigger sample at frame "
                                + sample.frame());
            }
            previousSampleFrame = sample.frame();
            if (sample.value() != 0 && sample.value() != 0xFF) {
                throw new IllegalStateException(
                        "ambiguous rings-to-go trigger value " + sample.value()
                                + " at frame " + sample.frame());
            }
            if (sample.value() == previousTrigger) {
                continue;
            }
            if (previousTrigger == 0xFF && sample.value() == 0) {
                triggerClearFrames.add(sample.frame());
            } else if (previousTrigger != 0 || sample.value() != 0xFF) {
                throw new IllegalStateException(
                        "ambiguous rings-to-go trigger transition at frame "
                                + sample.frame());
            }
            previousTrigger = sample.value();
        }
        if (triggerClearFrames.isEmpty() || previousTrigger != 0) {
            throw new IllegalStateException(
                    "rings-to-go artifact is missing a completed FF-to-00 trigger clear");
        }

        Set<Integer> refreshFrames = new LinkedHashSet<>();
        for (int gateFrame : triggerClearFrames) {
            int refreshFrame = completedPassFrames.stream()
                    .mapToInt(Integer::intValue)
                    .filter(frame -> frame > gateFrame)
                    .min()
                    .orElseThrow(() -> new IllegalStateException(
                            "rings-to-go trigger clear at frame " + gateFrame
                                    + " has no following completed pass"));
            long passCountAtObservation = completedPassFrames.stream()
                    .filter(frame -> frame == refreshFrame)
                    .count();
            if (passCountAtObservation != 1) {
                throw new IllegalStateException(
                        "rings-to-go refresh observation " + refreshFrame
                                + " owns multiple completed passes ("
                                + passCountAtObservation + ")");
            }
            if (!refreshFrames.add(refreshFrame)) {
                throw new IllegalStateException(
                        "multiple rings-to-go trigger clears map to pass frame "
                                + refreshFrame);
            }
        }

        List<Integer> checkRiseFrames = new ArrayList<>();
        int previousCheckRings = 0;
        for (int frame = 0; frame < checkRingsFlags.size(); frame++) {
            int checkRings = checkRingsFlags.get(frame);
            if (previousCheckRings == 0 && checkRings != 0) {
                checkRiseFrames.add(frame);
            }
            previousCheckRings = checkRings;
        }
        if (checkRiseFrames.size() != 1) {
            throw new IllegalStateException(
                    "rings-to-go artifact requires exactly one terminal check-ring rise; got "
                            + checkRiseFrames.size());
        }
        if (finishObservedFrames.size() != 1) {
            throw new IllegalStateException(
                    "rings-to-go artifact requires exactly one stage_finished observation; got "
                            + finishObservedFrames.size());
        }
        int checkRiseFrame = checkRiseFrames.get(0);
        int finishObservedFrame = finishObservedFrames.get(0);
        if (checkRiseFrame != finishObservedFrame) {
            throw new IllegalStateException(
                    "terminal check-ring rise frame " + checkRiseFrame
                            + " does not match stage_finished observed frame "
                            + finishObservedFrame);
        }
        refreshFrames.add(checkRiseFrame);
        return refreshFrames;
    }

    private static int numericTraceValue(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(raw);
        return text.startsWith("0x") || text.startsWith("0X")
                ? Integer.parseUnsignedInt(text.substring(2), 16)
                : Integer.parseInt(text);
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
                    intOr(engPresent, engPresent ? ps.flipTimer() : 0), Severity.ERROR));
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

    private static String intOr(boolean present, int value) {
        return present ? Integer.toString(value) : ABSENT;
    }

    // ==================== Report output ====================

    static void writeReport(DivergenceReport report, int ssIndex) throws IOException {
        TraceReportWriter.writeSpecialStageReport(
                report, reportDir(), "s2_special_stage_" + ssIndex, CONTEXT_RADIUS);
    }

    static Path reportDir() {
        return Path.of("target", "trace-reports");
    }

    static int specialStageIndex(SpecialStageTraceData trace) {
        Integer index = trace.metadata().specialStageIndex();
        return index != null ? index : 0;
    }
}

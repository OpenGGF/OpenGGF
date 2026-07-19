package com.openggf.tests.trace.s3k;

import com.openggf.data.Rom;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageComparisonState;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.trace.DivergenceReport;
import com.openggf.trace.FieldComparison;
import com.openggf.trace.FrameComparison;
import com.openggf.trace.S3kSpecialStageTraceData;
import com.openggf.trace.S3kSpecialStageTraceFrame;
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
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Headless replay comparator for a Sonic 3&amp;K special-stage (blue
 * spheres) trace. Drives the production
 * {@link com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageProvider}
 * through {@link S3kSpecialStageReplayHarness}, comparing each stepped frame
 * against the recorded ROM trace and emitting a divergence report to
 * {@code target/trace-reports/s3k_special_stage_&lt;index&gt;_report.json}.
 * Modeled on {@code AbstractS2SpecialStageTraceReplayTest}, simplified for
 * the S3K SS's single ROM pacing model (no RunObjects-pass binder, no lag
 * compensator to disable).
 *
 * <h2>Replay semantics</h2>
 * <ul>
 *   <li><b>VBlank-paced.</b> Every non-lag trace row is stepped through
 *       {@link S3kSpecialStageReplayHarness#stepFrame(int)}; lag rows are
 *       skipped (consumed without stepping) since they advance nothing
 *       engine-side.</li>
 *   <li><b>Comparison-only.</b> Trace values are read for input + expectation
 *       only; engine state is never hydrated from the trace.</li>
 *   <li><b>Finish boundary.</b> The engine's {@code finished} flag flips only
 *       when the exit-spin animation completes ({@code fade_timer} rises
 *       from 0 to nonzero, then returns to 0 --
 *       {@code Sonic3kSpecialStageManager.java:604-625}), on BOTH the
 *       success (emerald collected) and failure (landed on a red sphere,
 *       {@code Sonic3kSpecialStageManager.java:699-706}) exit paths. The
 *       trace's {@code clear_routine} column reaching its ROM terminal state
 *       ({@link #CLEAR_ROUTINE_TERMINAL}) is NOT that boundary: on the
 *       success path {@code clear_routine} jumps to its terminal value the
 *       instant the player reaches the emerald cell
 *       ({@code Sonic3kSpecialStageManager.collectEmerald()},
 *       ROM {@code sub_9B62}/sonic3k.asm:12530-12664), roughly 96+ frames
 *       before {@code finished} actually flips, and the failure path never
 *       touches {@code clear_routine} at all. A Tier-1
 *       {@code finished_transition_frame} check therefore anchors on
 *       {@link #exitSpinCompletionFrame} (the trace's own {@code fade_timer}
 *       0&rarr;nonzero&rarr;0 cycle) and asserts the engine's
 *       {@code captureComparisonState().finished()} first becomes true at
 *       that frame.</li>
 * </ul>
 *
 * <h2>Release-ratcheted comparator surface</h2>
 * <p>Every mismatch below is a report ERROR, one comparison-only read of
 * {@link Sonic3kSpecialStageComparisonState} per CSV column:
 * {@code player_x}/{@code player_y}/{@code angle}/{@code velocity}/
 * {@code turning}/{@code jumping}/{@code fade_timer}/{@code started}/
 * {@code spheres_left}/{@code ring_count}/{@code rings_left}/
 * {@code clear_routine}/{@code clear_timer}, plus {@code frame_counter}
 * (stepped non-lag row count vs the engine's own counter) and the
 * {@code finished_transition_frame} boundary check.
 *
 * <p>{@code rate}, {@code rate_timer}, and {@code anim_frame} are recorded as
 * WARNING-severity rows: the comparison state has no engine-side counterpart
 * for these columns yet (multi-stage trace run spec addition #3 MVP), so
 * they are surfaced in the report for a follow-up campaign rather than
 * silently dropped.
 *
 * <p>The pipeline writes a complete report and
 * {@link #assertNoReleaseBlockingDivergences} rejects any comparator ERROR.
 */
public abstract class AbstractS3kSpecialStageTraceReplayTest {

    /** Location of the committed trace (when one exists). */
    static final Path TRACE_DIRECTORY =
            Path.of("src", "test", "resources", "traces", "s3k", "special_stage");

    /**
     * ROM clear-routine terminal state (sub_9B62, sonic3k.asm:12530-12664):
     * 0=normal play, 1=fly-away timer, 2=emerald-art-load wait,
     * 3=emerald approach, 4=complete. Set the instant the player reaches the
     * emerald cell in {@code collectEmerald()}
     * (Sonic3kSpecialStageManager.java:777). Documented for reference only
     * (compared per-frame like every other {@code clear_routine} column
     * value) -- it is NOT the finish-boundary anchor: it precedes the
     * engine's {@code finished} flip by 96+ frames on the success path, and
     * the failure (red-sphere) exit path never sets {@code clear_routine} at
     * all. See {@link #exitSpinCompletionFrame} for the actual boundary.
     */
    private static final int CLEAR_ROUTINE_TERMINAL = 4;

    private static final String ABSENT = "absent";
    private static final int CONTEXT_RADIUS = 8;

    /** Directory of the trace to replay. */
    protected abstract Path traceDirectory();

    @Test
    void replayProducesFaithfulReport() throws Exception {
        File romFile = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(romFile != null,
                "s3k.gen ROM required for S3K special-stage trace replay");

        Path dir = traceDirectory();
        assumeTrue(Files.exists(dir.resolve("metadata.json")),
                "No S3K special-stage trace committed yet at " + dir);

        S3kSpecialStageTraceData trace = S3kSpecialStageTraceData.load(dir);

        // Pipeline assertion: the trace loads with the expected profile + frames.
        assertEquals("s3k_special_stage", trace.metadata().traceProfile(),
                "SS trace must carry the s3k_special_stage profile");
        assertTrue(trace.frameCount() > 0, "SS trace should have frames");

        S3kSpecialStageReplayHarness harness = bootHarness(trace, dir, romFile);
        DivergenceReport report = compareReplay(trace, harness);

        int ssIndex = specialStageIndex(trace);
        writeReport(report, ssIndex);

        // Pipeline assertion: the report file was written where consumers expect.
        Path jsonPath = reportDir().resolve("s3k_special_stage_" + ssIndex + "_report.json");
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

    static S3kSpecialStageReplayHarness bootHarness(S3kSpecialStageTraceData trace,
                                                     Path dir,
                                                     File romFile) throws IOException {
        // Headless graphics so the SS manager's pattern/renderer setup is safe
        // without a GL context.
        GraphicsManager.getInstance().resetState();
        GraphicsManager.getInstance().initHeadless();

        Rom rom = new Rom();
        rom.open(romFile.getAbsolutePath());
        // Installs the Sonic3kGameModule, wires GameServices.rom(), and rebuilds a
        // fresh gameplay mode. Resets configuration to defaults, so team config is
        // (re)applied inside the harness ctor afterwards.
        TestEnvironment.configureRomFixture(rom);
        GraphicsManager.getInstance().initHeadless();

        int offset = trace.metadata().bk2FrameOffset();
        int ssIndex = specialStageIndex(trace);
        Path bk2 = dir.resolve(trace.metadata().sourceBk2());
        return new S3kSpecialStageReplayHarness(bk2, offset, ssIndex);
    }

    // ==================== Comparator ====================

    static DivergenceReport compareReplay(S3kSpecialStageTraceData trace,
                                          S3kSpecialStageReplayHarness harness) {
        OptionalInt finishFrame = exitSpinCompletionFrame(trace);
        int compareEnd = trace.frameCount();

        List<FrameComparison> comparisons = new ArrayList<>();
        int firstEngineFinished = -1;
        int steppedNonLagCount = 0;

        for (int f = 0; f < compareEnd; f++) {
            S3kSpecialStageTraceFrame tf = trace.getFrame(f);
            if (tf.lag()) {
                continue;
            }
            harness.stepFrame(f);
            steppedNonLagCount++;

            Sonic3kSpecialStageComparisonState state = harness.capture();
            if (firstEngineFinished < 0 && state.finished()) {
                firstEngineFinished = f;
            }

            Map<String, FieldComparison> fields = new LinkedHashMap<>();
            addFields(fields, tf, state, steppedNonLagCount);
            comparisons.add(new FrameComparison(f, fields));
        }

        if (finishFrame.isPresent()) {
            int ff = finishFrame.getAsInt();
            String actual = firstEngineFinished >= 0 ? String.valueOf(firstEngineFinished) : "never";
            Map<String, FieldComparison> fields = new LinkedHashMap<>();
            fields.put("finished_transition_frame",
                    cmp("finished_transition_frame", String.valueOf(ff), actual, Severity.ERROR));
            comparisons.add(new FrameComparison(ff, fields));
        }

        return new DivergenceReport(comparisons);
    }

    private static void addFields(Map<String, FieldComparison> fields,
                                  S3kSpecialStageTraceFrame tf,
                                  Sonic3kSpecialStageComparisonState state,
                                  int steppedNonLagCount) {
        // Tier-1
        fields.put("player_x", cmp("player_x",
                str(signedWord(tf.xPos())), str(state.playerX()), Severity.ERROR));
        fields.put("player_y", cmp("player_y",
                str(signedWord(tf.yPos())), str(state.playerY()), Severity.ERROR));
        fields.put("angle", cmp("angle", str(tf.angle()), str(state.angle()), Severity.ERROR));
        fields.put("velocity", cmp("velocity",
                str(signedWord(tf.velocity())), str(state.velocity()), Severity.ERROR));
        fields.put("turning", cmp("turning", str(tf.turning()), str(state.turning()), Severity.ERROR));
        fields.put("jumping", cmp("jumping", str(tf.jumping()), str(state.jumping()), Severity.ERROR));
        fields.put("fade_timer",
                cmp("fade_timer", str(tf.fadeTimer()), str(state.fadeTimer()), Severity.ERROR));
        fields.put("started", cmp("started", bool(tf.started()), bool(state.started()), Severity.ERROR));
        fields.put("spheres_left",
                cmp("spheres_left", str(tf.spheresLeft()), str(state.spheresLeft()), Severity.ERROR));
        fields.put("ring_count",
                cmp("ring_count", str(tf.ringCount()), str(state.ringsCollected()), Severity.ERROR));
        fields.put("rings_left",
                cmp("rings_left", str(tf.ringsLeft()), str(state.ringsLeft()), Severity.ERROR));
        fields.put("clear_routine",
                cmp("clear_routine", str(tf.clearRoutine()), str(state.clearRoutine()), Severity.ERROR));
        fields.put("clear_timer",
                cmp("clear_timer", str(tf.clearTimer()), str(state.clearTimer()), Severity.ERROR));
        fields.put("frame_counter",
                cmp("frame_counter", str(steppedNonLagCount), str(state.frameCounter()), Severity.ERROR));

        // Recorded-not-compared for MVP (multi-stage trace run spec addition
        // #3): Sonic3kSpecialStageComparisonState has no counterpart field
        // yet for these three columns. Recorded as WARNING so a follow-up
        // campaign can wire real comparands (Sonic3kSpecialStagePlayer
        // exposes getMappingFrame() if anim_frame mapping is wanted) without
        // the columns silently disappearing from the report.
        fields.put("rate", cmp("rate", str(tf.rate()), ABSENT, Severity.WARNING));
        fields.put("rate_timer", cmp("rate_timer", str(tf.rateTimer()), ABSENT, Severity.WARNING));
        fields.put("anim_frame", cmp("anim_frame", str(tf.animFrame()), ABSENT, Severity.WARNING));
    }

    /**
     * The trace frame where the exit-spin animation completes: the first
     * return of {@code fade_timer} to 0 after its first 0&rarr;nonzero rise.
     * Covers both exit paths -- {@code fade_timer} is set to 1 by
     * {@code collectEmerald()} on success (Sonic3kSpecialStageManager.java:778)
     * and by the {@code RED_SPHERE} case on failure
     * (Sonic3kSpecialStageManager.java:701) -- and matches the engine's own
     * finish condition ({@code exitSpinStarted && fadeTimer == 0},
     * Sonic3kSpecialStageManager.java:619-625).
     */
    private static OptionalInt exitSpinCompletionFrame(S3kSpecialStageTraceData trace) {
        int spinStartFrame = -1;
        int previousFadeTimer = 0;
        for (int f = 0; f < trace.frameCount(); f++) {
            int fadeTimer = trace.getFrame(f).fadeTimer();
            if (spinStartFrame < 0) {
                if (previousFadeTimer == 0 && fadeTimer != 0) {
                    spinStartFrame = f;
                }
            } else if (fadeTimer == 0) {
                return OptionalInt.of(f);
            }
            previousFadeTimer = fadeTimer;
        }
        return OptionalInt.empty();
    }

    /**
     * Maps a raw 68000 word to the signed value used by the ROM's SS-space
     * coordinate/velocity arithmetic.
     */
    private static int signedWord(int rawWord) {
        return (short) rawWord;
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
        String prefix = "s3k_special_stage_" + ssIndex;
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

    static int specialStageIndex(S3kSpecialStageTraceData trace) {
        Integer index = trace.metadata().specialStageIndex();
        return index != null ? index : 0;
    }
}

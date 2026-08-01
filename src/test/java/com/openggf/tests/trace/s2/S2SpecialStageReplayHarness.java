package com.openggf.tests.trace.s2;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.GameServices;
import com.openggf.game.SpecialStageInputMapper;
import com.openggf.game.SpecialStageStartupPolicy;
import com.openggf.game.resources.DynamicArtDiagnosticsSnapshot;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2SpecialStageProvider;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageComparisonState;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageReplayTestBridge;
import com.openggf.trace.SpecialStageRunObjectsPassBinder.CompletedPass;
import com.openggf.tests.trace.RecordedInputRows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Headless replay driver for a Sonic 2 special-stage BizHawk trace.
 *
 * <h2>Bootstrap pattern (Step 1 research)</h2>
 * <p>The special-stage runtime is booted through the production
 * {@link Sonic2SpecialStageProvider}, exactly as the live GameLoop does, rather
 * than a parallel test-only stack. The surrounding fixture is responsible for
 * installing a ROM + engine services before this harness is constructed. The
 * verified boot recipe (mirrored from
 * {@code TestS3kHcz2SpindashStuckRegression} for the ROM install, from
 * {@code Sonic1SpecialStageManagerTest} for the headless graphics install, and
 * from {@code Sonic2SpecialStageComparisonStateTest} for the two-key team
 * config) is:
 * <ol>
 *   <li>{@code GraphicsManager.getInstance().initHeadless()} — GL calls become
 *       no-ops so the manager's pattern/renderer setup is safe without a GL
 *       context.</li>
 *   <li>Load {@code s2.gen} into a {@link com.openggf.data.Rom} and install it
 *       via {@code TestEnvironment.configureRomFixture(rom)} (selects the
 *       {@code Sonic2GameModule}, wires {@code GameServices.rom()} and a fresh
 *       gameplay mode).</li>
 *   <li>Set {@code MAIN_CHARACTER_CODE="sonic"} + {@code SIDEKICK_CHARACTER_CODE
 *       ="tails"} on {@code GameServices.configuration()} (done in this ctor)
 *       so {@code setupPlayers()} spawns the recorded Sonic+Tails team.</li>
 *   <li>{@code provider.initializeStage(index, TRACE_ACCURATE)} →
 *       {@code manager.reset()} + {@code manager.initialize(index)} (loads the
 *       SS data from ROM while retaining observable startup cadence).</li>
 *   <li>{@code provider.setLagCompensation(0)} — replay is trace-paced, so the
 *       runtime must not apply its own frame-drop compensation.</li>
 * </ol>
 *
 * <h2>Input injection (modelled on {@code SpecialStageStepper.step})</h2>
 * <p>Per stepped frame the harness overrides the {@link InputHandler}'s logical
 * snapshot with the recorded BK2 row via
 * {@link RecordedInputSnapshots#fromBk2}, maps it with
 * {@link SpecialStageInputMapper}, forwards P1 held/pressed and P2 held/logical
 * to the provider, then ticks {@code provider.update()}. Trace data is only ever
 * used as an input source and pacing signal — no engine state is hydrated from
 * the trace (comparison-only invariant).
 *
 * <h2>BK2 indexing and the press-edge rule</h2>
 * <p>Trace frame {@code f} maps to absolute BK2 input-log row
 * {@code bk2FrameOffset + f} (0-based into {@link Bk2Movie#getFrames()}). The
 * {@code previous} row handed to {@code fromBk2} is ALWAYS the immediately
 * preceding <em>physical</em> BK2 row ({@code bk2FrameOffset + f - 1}), never
 * "the last frame the harness stepped". This keeps press-edge detection
 * ({@code held & ~previousHeld}) aligned with the ROM, whose V-int reads the
 * true prior controller state regardless of lag frames.
 */
final class S2SpecialStageReplayHarness {

    private final Bk2Movie movie;
    private final RecordedInputRows recordedInputs;
    private final InputHandler inputHandler;
    private final Sonic2SpecialStageProvider provider;
    private final List<Integer> steppedPassSequences = new ArrayList<>();
    private int forceFinishedAfterPassSequenceForTest = -1;

    S2SpecialStageReplayHarness(Path bk2, int bk2FrameOffset, int specialStageIndex)
            throws IOException {
        // Recorded team: the SS trace was captured with Sonic + Tails. Set the
        // standard two-key config before initializeStage() runs setupPlayers().
        GameServices.configuration()
                .setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        GameServices.configuration()
                .setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");

        this.movie = new Bk2MovieLoader().load(Objects.requireNonNull(bk2, "bk2"));
        this.recordedInputs = new RecordedInputRows(movie, bk2FrameOffset);
        this.inputHandler = new InputHandler();
        this.provider = new Sonic2SpecialStageProvider();
        this.provider.initializeStage(
                specialStageIndex, SpecialStageStartupPolicy.TRACE_ACCURATE);
        // Trace-paced replay: disable the runtime's own lag compensation.
        this.provider.setLagCompensation(0);
    }

    /**
     * Steps one special-stage logic frame using the BK2 row for
     * {@code traceFrame}, diffing against the previous physical BK2 row for
     * press-edge detection. Callers must NOT invoke this for a lag row — a lag
     * row advances nothing engine-side (the row is simply skipped).
     */
    void stepFrame(int traceFrame) {
        runProductionRow(PlcLifecyclePhase.SPECIAL_STAGE,
                () -> stepFrameBody(traceFrame));
    }

    private void stepFrameBody(int traceFrame) {
        recordedInputs.withLogicalOverride(traceFrame, inputHandler, () -> {
            SpecialStageInputMapper.MappedInput mapped =
                    SpecialStageInputMapper.map(inputHandler.logical());
            provider.handleInput(mapped.p1Held(), mapped.p1Pressed());
            provider.handlePlayer2Input(
                    mapped.p2Held(), mapped.p2Logical());
            provider.update();
        });
    }

    /**
     * Steps one recurring ROM object pass using only the current/previous BK2
     * row identities captured by the preceding Vint_S2SS ReadJoypads call.
     * Auxiliary held values are diagnostics validated by the pass binder; they
     * are never used to drive the engine.
     */
    void stepPass(CompletedPass pass) {
        runProductionRow(PlcLifecyclePhase.SPECIAL_STAGE,
                () -> stepPassBody(pass));
    }

    void stepPasses(
            List<CompletedPass> passes,
            boolean completeTerminalPreStartPass,
            boolean lagged,
            int observationFrame) {
        runProductionRow(
                lagged ? PlcLifecyclePhase.LAG
                        : PlcLifecyclePhase.SPECIAL_STAGE,
                () -> {
            if (completeTerminalPreStartPass) {
                completeTerminalPreStartPassBody();
            }
            for (CompletedPass pass : passes) {
                stepPassBody(pass);
                if (pass.completionCursorFrame() < observationFrame) {
                    // The pass finished before this observation's V-int on
                    // hardware, so that V-blank already ran ProcessDMAQueue
                    // over its queued work: submissions and completions
                    // surface together on the bound row, while later passes'
                    // work in this same observation stays pending. See
                    // DynamicArtLifecycleService.serviceVblankBeforeBoundObservation.
                    var lifecycle = GameServices.dynamicArtLifecycleOrNull();
                    if (lifecycle != null && lifecycle.isRunActive()) {
                        lifecycle.serviceVblankBeforeBoundObservation();
                    }
                }
            }
        });
    }

    private void stepPassBody(CompletedPass pass) {
        SpecialStageInputMapper.MappedInput mapped = mappedInputForPass(movie, pass);
        provider.handleInput(mapped.p1Held(), mapped.p1Pressed());
        provider.handlePlayer2Input(mapped.p2Held(), mapped.p2Logical());
        provider.bindPendingRecurringPassInput(
                mapped.p1Held(), mapped.p1Pressed(), mapped.p2Held(), mapped.p2Logical());
        provider.update();
        steppedPassSequences.add(pass.sequence());
        if (pass.sequence() == forceFinishedAfterPassSequenceForTest) {
            provider.getManager().markCompleted(true);
        }
    }

    /** Publishes Obj5F's terminal pre-start object pass without a new VInt. */
    void completeTerminalPreStartPass() {
        runProductionRow(PlcLifecyclePhase.SPECIAL_STAGE,
                this::completeTerminalPreStartPassBody);
    }

    private void completeTerminalPreStartPassBody() {
        Sonic2SpecialStageReplayTestBridge.completeTerminalPreStartPassWithoutVint(
                provider.getManager());
    }

    void stepLagRow() {
        runProductionRow(PlcLifecyclePhase.LAG, () -> {
        });
    }

    void stepIdleRow(boolean lagged) {
        runProductionRow(
                lagged ? PlcLifecyclePhase.LAG
                        : PlcLifecyclePhase.SPECIAL_STAGE,
                () -> {
                });
    }

    DynamicArtDiagnosticsSnapshot captureDynamicArt() {
        return GameServices.captureDynamicArtDiagnostics();
    }

    void finishDynamicArtSegment() {
        var lifecycle =
                SessionManager.getCurrentGameplayMode().dynamicArtLifecycle();
        if (lifecycle.isComparisonSegmentOpen()) {
            lifecycle.closeComparisonSegment();
        }
    }

    private static void runProductionRow(
            PlcLifecyclePhase phase, Runnable body) {
        SessionManager.getCurrentGameplayMode().plcFrameLifecycle()
                .runLogicalIteration(() -> {
                }, frame -> {
                    frame.claim(phase);
                    body.run();
                    frame.prepareAfterLoop(phase);
                    return null;
                });
    }

    void forceFinishedAfterPassForTest(int sequence) {
        forceFinishedAfterPassSequenceForTest = sequence;
    }

    List<Integer> steppedPassSequencesForTest() {
        return List.copyOf(steppedPassSequences);
    }

    static SpecialStageInputMapper.MappedInput mappedInputForPass(
            Bk2Movie movie, CompletedPass pass) {
        List<Bk2FrameInput> frames = movie.getFrames();
        Bk2FrameInput current = frames.get(pass.inputSampleBk2Frame());
        Bk2FrameInput previous = frames.get(pass.previousInputSampleBk2Frame());
        return SpecialStageInputMapper.map(
                RecordedInputSnapshots.fromBk2(current, previous));
    }

    List<Bk2FrameInput> movieFrames() {
        return movie.getFrames();
    }

    /** Read-only comparison snapshot of the current engine SS state. */
    Sonic2SpecialStageComparisonState capture() {
        return provider.getManager().captureComparisonState();
    }

    boolean isFinished() {
        return provider.isFinished();
    }
}

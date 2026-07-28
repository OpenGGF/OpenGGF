package com.openggf;

import com.openggf.camera.Camera;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.GameRng;
import com.openggf.game.GameStateManager;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.SpecialStageStartupPolicy;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.timing.HardwareReadinessAdmissionPolicy;
import com.openggf.graphics.FadeManager;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.timer.TimerManager;
import com.openggf.trace.SpecialStageTraceData;
import com.openggf.trace.catalog.TraceEntry;
import com.openggf.trace.replay.TraceReplayFixture;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import com.openggf.trace.timing.HardwareTimingReplayPort;
import com.openggf.trace.timing.HardwareTimingSchedule;
import com.openggf.trace.timing.TraceHardwareTimingBoundaryObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TestSpecialStageHardwareTimingLifecycle {
    private static final String HEADER =
            "frame,input,input_p2,lag,speed_factor,track_anim,track_anim_frame,"
                    + "track_drawing_index,track_orientation,track_duration_timer,"
                    + "current_segment,player_anim_frame_timer,rings_togo_bcd,"
                    + "check_rings_flag,tails_control_counter,swap_positions_flag,"
                    + "sonic_present,sonic_ss_x,sonic_ss_x_sub,sonic_ss_y,"
                    + "sonic_ss_y_sub,sonic_ss_z,sonic_angle,sonic_routine,"
                    + "sonic_routine_secondary,sonic_status,sonic_anim,"
                    + "sonic_anim_frame,sonic_rings_bcd,sonic_hurt_timer,"
                    + "sonic_slide_timer,sonic_flip_timer,tails_present,tails_ss_x,"
                    + "tails_ss_x_sub,tails_ss_y,tails_ss_y_sub,tails_ss_z,"
                    + "tails_angle,tails_routine,tails_routine_secondary,tails_status,"
                    + "tails_anim,tails_anim_frame,tails_rings_bcd,tails_hurt_timer,"
                    + "tails_slide_timer,tails_flip_timer";

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void timingEnabledStandaloneSpecialStageOwnsPolicyLatchRewindAndClose(
            @TempDir Path dir) throws Exception {
        writeTrace(dir, true);
        SpecialStageTraceData trace = SpecialStageTraceData.load(dir);
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());

        TraceSessionLauncher.armSpecialStageAdmissionPolicy(trace);
        GameplayModeContext context =
                SessionManager.openGameplaySession(new Sonic2GameModule());
        context.attachGameplayManagers(
                new Camera(), new TimerManager(), new GameStateManager(),
                new FadeManager(), new GameRng(GameRng.Flavour.S1_S2),
                new DefaultSolidExecutionRegistry());
        TimingFixture fixture = new TimingFixture(context);
        TraceSessionLauncher session = session(dir, trace);

        session.installSpecialStageHardwareTiming(fixture);
        session.prepareHardwareTimingForAdmission(GameMode.SPECIAL_STAGE);

        assertEquals(HardwareReadinessAdmissionPolicy.RECORDED,
                context.hardwareTiming().admissionPolicy());
        assertNotNull(fixture.port);
        assertEquals(0, fixture.port.capture().rawFrameLatch());
        var snapshot = context.getRewindRegistry().capture();
        fixture.enterHardwareTimingGap();
        context.getRewindRegistry().restore(snapshot);
        assertEquals(0, fixture.port.capture().rawFrameLatch());

        session.advanceSpecialStageTraceCursorIfActive(null);

        assertEquals(HardwareReadinessAdmissionPolicy.LIVE,
                context.hardwareTiming().admissionPolicy());
        assertFalse(context.getRewindRegistry().capture().entries()
                .containsKey(HardwareTimingReplayPort.REWIND_KEY));
    }

    @Test
    void legacyStandaloneSpecialStageKeepsLivePolicyAndNoReplayPort(
            @TempDir Path dir) throws Exception {
        writeTrace(dir, false);
        SpecialStageTraceData trace = SpecialStageTraceData.load(dir);
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());

        TraceSessionLauncher.armSpecialStageAdmissionPolicy(trace);
        GameplayModeContext context =
                SessionManager.openGameplaySession(new Sonic2GameModule());
        context.attachGameplayManagers(
                new Camera(), new TimerManager(), new GameStateManager(),
                new FadeManager(), new GameRng(GameRng.Flavour.S1_S2),
                new DefaultSolidExecutionRegistry());
        TimingFixture fixture = new TimingFixture(context);

        session(dir, trace).installSpecialStageHardwareTiming(fixture);

        assertEquals(HardwareReadinessAdmissionPolicy.LIVE,
                context.hardwareTiming().admissionPolicy());
        assertEquals(null, fixture.port);
    }

    @Test
    void initializationFailureSuppressesStrictCloseAndLeavesNextContextLive(
            @TempDir Path dir) throws Exception {
        writeTrace(dir, true, true);
        SpecialStageTraceData trace = SpecialStageTraceData.load(dir);
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        var config = GameServices.configuration();
        TraceReplaySessionBootstrap.ConfigSnapshot configSnapshot =
                TraceReplaySessionBootstrap.snapshotGameplayConfig();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");

        TraceSessionLauncher.armSpecialStageAdmissionPolicy(trace);
        GameplayModeContext failedContext =
                SessionManager.openGameplaySession(new Sonic2GameModule());
        attachCoreManagers(failedContext);
        var failedRegistry = failedContext.getRewindRegistry();
        TraceSessionLauncher session =
                session(dir, trace, configSnapshot);
        GameLoop loop = mock(GameLoop.class);
        IllegalStateException primary =
                new IllegalStateException("primary special-stage initialization failure");
        doThrow(primary).when(loop).doEnterSpecialStage(
                any(SpecialStageProvider.class),
                anyInt(),
                anyBoolean(),
                any(SpecialStageStartupPolicy.class));

        session.finishSpecialStageLaunch(loop);

        assertEquals(1, primary.getSuppressed().length);
        assertTrue(primary.getSuppressed()[0].getMessage()
                        .contains("unconsumed hardware completion edge"),
                () -> primary.getSuppressed()[0].getMessage());
        assertEquals(configSnapshot.mainCharacterCode(),
                config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE));
        assertNull(TraceSessionLauncher.active());
        assertEquals(HardwareReadinessAdmissionPolicy.LIVE,
                failedContext.hardwareTiming().admissionPolicy());
        assertFalse(failedRegistry.capture().entries()
                .containsKey(HardwareTimingReplayPort.REWIND_KEY));
        verify(loop).returnToMasterTitle();

        GameplayModeContext nextContext =
                SessionManager.openGameplaySession(new Sonic2GameModule());
        attachCoreManagers(nextContext);
        assertEquals(HardwareReadinessAdmissionPolicy.LIVE,
                nextContext.hardwareTiming().admissionPolicy());
        assertFalse(nextContext.getRewindRegistry().capture().entries()
                .containsKey(HardwareTimingReplayPort.REWIND_KEY));
    }

    private static TraceSessionLauncher session(
            Path dir, SpecialStageTraceData trace) {
        return session(dir, trace, null);
    }

    private static TraceSessionLauncher session(
            Path dir,
            SpecialStageTraceData trace,
            TraceReplaySessionBootstrap.ConfigSnapshot configSnapshot) {
        Bk2Movie movie = new Bk2Movie(
                dir.resolve("synthetic.bk2"), "logkey", Map.of(),
                List.of(new Bk2FrameInput(0, 0, 0, false, "")), 1);
        TraceEntry entry = new TraceEntry(
                dir, "s2", 0, 0, 1, 0, 0, null,
                dir.resolve("synthetic.bk2"), trace.metadata());
        return new TraceSessionLauncher(entry, movie, trace, configSnapshot);
    }

    private static void writeTrace(Path dir, boolean timing)
            throws Exception {
        writeTrace(dir, timing, false);
    }

    private static void writeTrace(
            Path dir, boolean timing, boolean unconsumedEdge)
            throws Exception {
        Files.writeString(dir.resolve("metadata.json"), """
                {
                  "game":"s2",
                  "trace_profile":"s2_special_stage",
                  "trace_schema":%d,
                  "csv_version":1,
                  %s
                  "act":1,
                  "bk2_frame_offset":0,
                  "trace_frame_count":1,
                  "start_x":"0x0000",
                  "start_y":"0x0000"
                }
                """.formatted(
                timing ? 7 : 1,
                timing ? "\"hardware_timing_schema\":1," : ""));
        Files.writeString(dir.resolve("physics.csv"),
                HEADER + "\n0," + "0,".repeat(46) + "0\n");
        if (timing) {
            Files.writeString(
                    dir.resolve("hardware_timing.jsonl"),
                    unconsumedEdge
                            ? """
                              {"event":"hardware_work_completed","raw_frame":0,"boundary":"post_objects","kind":"kos_module_queue","ordinal":0,"submission_fingerprint":"sha256:%s"}
                              """.formatted("a".repeat(64))
                            : "");
        }
    }

    private static void attachCoreManagers(GameplayModeContext context) {
        context.attachGameplayManagers(
                new Camera(), new TimerManager(), new GameStateManager(),
                new FadeManager(), new GameRng(GameRng.Flavour.S1_S2),
                new DefaultSolidExecutionRegistry());
    }

    private static final class TimingFixture
            implements TraceReplayFixture {
        private final GameplayModeContext context;
        private HardwareTimingReplayPort port;
        private TraceHardwareTimingBoundaryObserver observer;

        private TimingFixture(GameplayModeContext context) {
            this.context = context;
        }

        @Override
        public GameplayModeContext gameplayMode() {
            return context;
        }

        @Override
        public void installHardwareTimingReplay(
                HardwareTimingReplayPort replayPort) {
            port = replayPort;
            observer = new TraceHardwareTimingBoundaryObserver(replayPort);
            context.getRewindRegistry().register(replayPort);
            context.setHardwareTimingBoundaryObserver(observer);
        }

        @Override
        public void beginTraceRow(int traceIndex, int rawFrame) {
            observer.beginRawFrame(rawFrame);
        }

        @Override
        public void enterHardwareTimingGap() {
            observer.enterUnrepresentedGap();
        }

        @Override
        public void closeHardwareTimingReplayRun() {
            port.verifyRunComplete();
            context.setHardwareTimingBoundaryObserver(null);
            context.getRewindRegistry().deregister(
                    HardwareTimingReplayPort.REWIND_KEY);
        }

        @Override
        public AbstractPlayableSprite sprite() {
            return null;
        }

        @Override
        public void verifyHardwareTimingSegmentEdges() {
        }

        @Override
        public void handoffHardwareTimingReplay(
                HardwareTimingSchedule nextSchedule) {
        }

        @Override
        public int stepFrameFromRecording() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int skipFrameFromRecording() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void advancePlayableAnimationsOnly() {
        }

        @Override
        public void suppressFirstSidekickAnimationOnce() {
        }

        @Override
        public int consumeRecordingFrameInputOnly() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void advanceRecordingCursor(int frameCount) {
            throw new UnsupportedOperationException();
        }
    }
}

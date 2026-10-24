package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioManagerTestDiagnostics;
import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.AbstractSmpsLoader;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.data.Game;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.game.GameDataSource;
import com.openggf.physics.CollisionSystem;
import com.openggf.level.WaterSystem;
import com.openggf.level.ParallaxManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.level.LevelManager;
import com.openggf.trace.replay.runs.TraceRunSegmentDescriptor;

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
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.openggf.tests.RomTestUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.util.Optional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestSpecialStageHardwareTimingLifecycle {

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void standaloneAudioInstallsOnlyItsMatchedProfileRomPair() throws Exception {
        try (var setup = matchedSourceSetup()) {
            long before = AudioManagerTestDiagnostics.baseSmpsSourceBinding(setup.audio())
                    .generation();
            var session = new TraceSessionLauncher(null, null,
                    List.<TraceRunSegmentDescriptor>of(), null, null);

            session.configureStandaloneSpecialStageAudio();

            assertSelectedSource(setup, before);
            session.configureStandaloneSpecialStageAudio();
            assertSelectedSource(setup, before);
        }
    }

    @Test
    void levelInitAudioInstallsOnlyItsMatchedProfileRomPair() throws Exception {
        try (var setup = matchedSourceSetup()) {
            long before = AudioManagerTestDiagnostics.baseSmpsSourceBinding(setup.audio())
                    .generation();

            setup.level().initAudio(0);

            assertSelectedSource(setup, before);
            verify(setup.game()).getMusicId(0);
            setup.level().configureAudio();
            assertSelectedSource(setup, before);
        }
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
    void standaloneSpecialStageWithoutTimingKeepsLivePolicyAndNoReplayPort(
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
    @RequiresRom(SonicGame.SONIC_2)
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

        List<Throwable> launchFailures = new ArrayList<>();
        Logger launchLogger = Logger.getLogger(TraceSessionLauncher.class.getName());
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getThrown() != null) {
                    launchFailures.add(record.getThrown());
                }
            }

            @Override
            public void flush() { }

            @Override
            public void close() { }
        };
        launchLogger.addHandler(handler);
        try {
            session.finishSpecialStageLaunch(loop);
        } finally {
            launchLogger.removeHandler(handler);
            handler.close();
        }

        verify(loop).doEnterSpecialStage(
                any(SpecialStageProvider.class),
                anyInt(),
                anyBoolean(),
                any(SpecialStageStartupPolicy.class));
        assertEquals(1, launchFailures.size());
        assertSame(primary, launchFailures.getFirst(),
                "the handled failure must be the intended special-stage entry failure");

        assertEquals(0, primary.getSuppressed().length,
                "incomplete launch abort must not perform strict edge verification");
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

    @Test
    void fatalInitializationErrorAbortsPartialTimingInstallThenRethrows(
            @TempDir Path dir) throws Exception {
        // finishSpecialStageLaunch opens the Sonic 2 ROM in
        // configureStandaloneSpecialStageAudio() before it reaches the mocked
        // doEnterSpecialStage; without the ROM that IOException is swallowed by
        // abortLaunchFailure (only Errors rethrow) and the fatal path under test
        // is never entered. Skip, rather than error, on a ROM-less runner.
        java.io.File romFile = RomTestUtils.ensureSonic2RomAvailable();
        Assumptions.assumeTrue(romFile != null,
                "Sonic 2 ROM not available — fatal SS launch path needs it");
        writeTrace(dir, true);
        SpecialStageTraceData trace = SpecialStageTraceData.load(dir);
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TraceSessionLauncher.armSpecialStageAdmissionPolicy(trace);
        GameplayModeContext failedContext =
                SessionManager.openGameplaySession(new Sonic2GameModule());
        attachCoreManagers(failedContext);
        // Install the ROM explicitly: production RomManager otherwise opens the
        // configured default path relative to the working directory, which only
        // exists on checkouts that keep a ROM beside the sources.
        com.openggf.data.Rom rom = new com.openggf.data.Rom();
        Assumptions.assumeTrue(rom.open(romFile.getAbsolutePath()),
                "Sonic 2 ROM could not be opened: " + romFile);
        com.openggf.data.RomManager.getInstance().setRom(rom);
        var failedRegistry = failedContext.getRewindRegistry();
        TraceSessionLauncher session = session(
                dir, trace, TraceReplaySessionBootstrap.snapshotGameplayConfig());
        GameLoop loop = mock(GameLoop.class);
        AssertionError primary = new AssertionError("fatal special-stage bootstrap");
        doThrow(primary).when(loop).doEnterSpecialStage(
                any(SpecialStageProvider.class),
                anyInt(),
                anyBoolean(),
                any(SpecialStageStartupPolicy.class));

        AssertionError thrown = assertThrows(AssertionError.class,
                () -> session.finishSpecialStageLaunch(loop));

        assertSame(primary, thrown);
        assertNull(TraceSessionLauncher.active());
        assertFalse(failedContext.isGameplayRuntimeReady());
        assertEquals(HardwareReadinessAdmissionPolicy.LIVE,
                failedContext.hardwareTiming().admissionPolicy());
        assertFalse(failedRegistry.capture().entries()
                .containsKey(HardwareTimingReplayPort.REWIND_KEY));
        verify(loop).returnToMasterTitle();
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
                  "trace_schema":5,
                  "act":1,
                  "bk2_frame_offset":0,
                  "trace_frame_count":1,
                  "start_x":"0x0000",
                  "start_y":"0x0000"
                }
                """);
        Files.writeString(dir.resolve("physics.csv"),
                "0,".repeat(47) + "0\n");
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

    private static MatchedSourceSetup matchedSourceSetup() throws Exception {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        AudioManager audio = GameServices.audio();
        // Construct every mock/module before starting any when(...).thenReturn(...).
        Rom previousRom = mock(Rom.class);
        Rom selectedRom = mock(Rom.class);
        PairProfile previousProfile = new PairProfile(previousRom);
        PairProfile selectedProfile = new PairProfile(selectedRom);
        GameDataSource dataSource = mock(GameDataSource.class);
        Game game = mock(Game.class);
        SpriteManager sprites = mock(SpriteManager.class);
        ParallaxManager parallax = mock(ParallaxManager.class);
        CollisionSystem collision = mock(CollisionSystem.class);
        WaterSystem water = mock(WaterSystem.class);
        Sonic2GameModule module = new Sonic2GameModule() {
            @Override public GameAudioProfile getAudioProfile() { return selectedProfile; }
        };
        Optional<Rom> selected = Optional.of(selectedRom);
        when(previousRom.isOpen()).thenReturn(true);
        when(selectedRom.isOpen()).thenReturn(true);
        when(dataSource.rom()).thenReturn(selected);
        when(game.getSoundMap()).thenReturn(Map.of());
        when(game.getMusicId(0)).thenReturn(-1);
        RomManager.getInstance().setRom(selectedRom);
        GameplayModeContext context = SessionManager.openGameplaySession(
                module, module, dataSource, null);
        attachCoreManagers(context);
        LevelManager level = new LevelManager(new Camera(), sprites, parallax, collision,
                water, new GameStateManager(), EngineServices.current(), context.getWorldSession());
        Field moduleField = LevelManager.class.getDeclaredField("gameModule");
        moduleField.setAccessible(true);
        moduleField.set(level, module);
        Field gameField = LevelManager.class.getDeclaredField("game");
        gameField.setAccessible(true);
        gameField.set(level, game);
        // The old tuple is installed AFTER context construction/attachment.
        var setup = new MatchedSourceSetup(audio, selectedRom, previousProfile,
                selectedProfile, level, game);
        try {
            audio.resetState();
            audio.setBackend(new NullAudioBackend());
            audio.setAudioProfile(previousProfile);
            audio.setRom(previousRom);
            previousProfile.attempts.clear();
            return setup;
        } catch (RuntimeException | Error failure) {
            try { setup.close(); }
            catch (RuntimeException | Error cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            throw failure;
        }
    }

    private static void assertSelectedSource(MatchedSourceSetup setup, long before)
            throws Exception {
        var binding = AudioManagerTestDiagnostics.baseSmpsSourceBinding(setup.audio());
        assertSame(setup.selectedRom(), binding.rom());
        assertSame(setup.selectedRom(), binding.loaderRom());
        assertSame(setup.selectedProfile(), binding.profile());
        assertSame(setup.selectedProfile().loader, binding.loader());
        assertSame(AudioTestFixtures.EMPTY_DAC, baseSourcePart(setup.audio(), "dac"));
        assertSame(setup.selectedProfile().config, baseSourcePart(setup.audio(), "config"));
        assertEquals(before + 1, binding.generation());
        assertEquals(List.of(), setup.previousProfile().attempts);
        assertEquals(List.of(setup.selectedRom()), setup.selectedProfile().attempts);
    }

    private static Object baseSourcePart(AudioManager audio, String name)
            throws ReflectiveOperationException {
        Field field = AudioManager.class.getDeclaredField("baseAudioSource");
        field.setAccessible(true);
        Object source = field.get(audio);
        var accessor = source.getClass().getDeclaredMethod(name);
        accessor.setAccessible(true);
        return accessor.invoke(source);
    }

    private record MatchedSourceSetup(AudioManager audio, Rom selectedRom,
            PairProfile previousProfile, PairProfile selectedProfile,
            LevelManager level, Game game) implements AutoCloseable {
        @Override public void close() {
            try {
                audio.resetState();
            } finally {
                try { SessionManager.clear(); }
                finally { RomManager.getInstance().setRom(null); }
            }
        }
    }

    private static final class PairProfile extends AudioTestFixtures.StubAudioProfile {
        private final Rom expected;
        private final PairLoader loader;
        private final SmpsSequencerConfig config = new SmpsSequencerConfig.Builder().build();
        private final List<Rom> attempts = new ArrayList<>();

        private PairProfile(Rom expected) {
            this(expected, new PairLoader(expected));
        }
        private PairProfile(Rom expected, PairLoader loader) {
            super(loader);
            this.expected = expected;
            this.loader = loader;
        }
        @Override public SmpsLoader createSmpsLoader(Rom rom) {
            attempts.add(rom);
            if (rom != expected) {
                throw new IllegalStateException("factory received an unmatched ROM/profile pair");
            }
            return loader;
        }
        @Override public SmpsSequencerConfig getSequencerConfig() { return config; }
    }

    private static final class PairLoader extends AbstractSmpsLoader {
        private PairLoader(Rom rom) { super(rom); }
        @Override public AbstractSmpsData loadMusic(int id) { return null; }
        @Override public AbstractSmpsData loadSfx(int id) { return null; }
        @Override public DacData loadDacData() { return AudioTestFixtures.EMPTY_DAC; }
        @Override protected boolean isValidSfxId(int id) { return false; }
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
        public void abortHardwareTimingReplayRun() {
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
        public void advancePlayableFixedSlotsOnly() {
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

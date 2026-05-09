package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.game.RuntimeManager;
import com.openggf.game.rewind.InMemoryKeyframeStore;
import com.openggf.game.rewind.InputSource;
import com.openggf.game.rewind.PlaybackController;
import com.openggf.game.rewind.RewindController;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSpeedController;
import com.openggf.game.session.EngineContext;
import com.openggf.graphics.FadeManager;
import com.openggf.trace.TraceData;
import com.openggf.trace.live.LiveTraceComparator;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import com.openggf.trace.catalog.TraceEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.mockito.Mockito.mock;

class TestTraceSessionLauncherRewindPresentation {
    private SonicConfigurationService config;
    private AudioManager audio;
    private RecordingReverseAudioBackend backend;

    @BeforeEach
    void setUp() {
        RuntimeManager.configureEngineServices(EngineContext.fromLegacySingletonsForBootstrap());
        RuntimeManager.createGameplay();
        config = SonicConfigurationService.getInstance();
        audio = AudioManager.getInstance();
        audio.resetState();
        backend = new RecordingReverseAudioBackend();
        audio.setBackend(backend);
    }

    @AfterEach
    void tearDown() {
        audio.resetState();
        RuntimeManager.destroyCurrent();
    }

    @Test
    void traceRealtimeRewindFreezesFadePresentationUntilReleaseCleanup() throws Exception {
        TraceSessionLauncher launcher = newLauncher();
        RewindController rewindController = new RewindController(
                new RewindRegistry(),
                new InMemoryKeyframeStore(),
                new FakeInputSource(10),
                in -> {},
                2,
                AudioManager.getInstance());
        for (int i = 0; i < 5; i++) {
            rewindController.recordExternalStep();
        }
        setField(launcher, "rewindController", rewindController);
        setField(launcher, "rewindPlaybackController", new PlaybackController(rewindController));
        setField(launcher, "comparator", mock(LiveTraceComparator.class));
        setField(launcher, "rewindMovieBaseFrame", 0);
        setField(launcher, "rewindTraceBaseFrame", 0);
        FadeManager fadeManager = RuntimeManager.getCurrent().getFadeManager();
        InputHandler input = new InputHandler();
        input.handleKeyEvent(config.getInt(SonicConfiguration.TRACE_REWIND_KEY), GLFW_PRESS);

        assertTrue(launcher.handleRealtimeRewindInput(input));

        assertTrue(fadeManager.isReversePresentationActive());
        assertTrue(backend.calls.contains("beginReversePresentation"));
        assertTrue(backend.calls.contains("update"));

        input.handleKeyEvent(config.getInt(SonicConfiguration.TRACE_REWIND_KEY), GLFW_RELEASE);
        assertFalse(launcher.handleRealtimeRewindInput(input));

        assertFalse(fadeManager.isReversePresentationActive());
        assertTrue(backend.calls.contains("endReversePresentation"));
    }

    @Test
    void traceRealtimeRewindUsesConfiguredTapeCoastCurve() throws Exception {
        config.setConfigValue(SonicConfiguration.REWIND_TAPE_COAST_ENABLED, true);
        config.setConfigValue(SonicConfiguration.REWIND_TAPE_COAST_ACCELERATION, 1.0);
        config.setConfigValue(SonicConfiguration.REWIND_TAPE_COAST_DECELERATION, 0.5);
        config.setConfigValue(SonicConfiguration.REWIND_TAPE_COAST_MAX_STEPS, 1.99);
        TraceSessionLauncher launcher = newLauncher();
        RewindController rewindController = new RewindController(
                new RewindRegistry(),
                new InMemoryKeyframeStore(),
                new FakeInputSource(20),
                in -> {},
                2,
                AudioManager.getInstance());
        for (int i = 0; i < 10; i++) {
            rewindController.recordExternalStep();
        }
        setField(launcher, "rewindController", rewindController);
        setField(launcher, "rewindPlaybackController", new PlaybackController(rewindController));
        setField(launcher, "rewindSpeedController", RewindSpeedController.fromConfig(config));
        setField(launcher, "comparator", mock(LiveTraceComparator.class));
        InputHandler input = new InputHandler();

        input.handleKeyEvent(config.getInt(SonicConfiguration.TRACE_REWIND_KEY), GLFW_PRESS);
        assertTrue(launcher.handleRealtimeRewindInput(input));
        assertEquals(9, rewindController.currentFrame(),
                "trace rewind must not exceed one reverse step per visual frame");

        assertTrue(launcher.handleRealtimeRewindInput(input));
        assertEquals(8, rewindController.currentFrame(),
                "continued held trace-rewind frames must remain capped at one reverse step");

        input.handleKeyEvent(config.getInt(SonicConfiguration.TRACE_REWIND_KEY), GLFW_RELEASE);
        assertTrue(launcher.handleRealtimeRewindInput(input),
                "release should continue consuming frames while tape coast has remaining charge");
        assertEquals(7, rewindController.currentFrame());
        assertTrue(backend.reverseRates.stream().anyMatch(rate -> rate > 0.0 && rate < 1.0),
                "release coast should lower reverse presentation pitch below the held 1x rate");

        assertFalse(launcher.handleRealtimeRewindInput(input));
        assertEquals(7, rewindController.currentFrame());
    }

    @Test
    void traceRealtimeRewindAtBufferStartConsumesInputWithoutStartingReverseAudio() throws Exception {
        TraceSessionLauncher launcher = newLauncher();
        RewindController rewindController = new RewindController(
                new RewindRegistry(),
                new InMemoryKeyframeStore(),
                new FakeInputSource(10),
                in -> {},
                2,
                AudioManager.getInstance());
        setField(launcher, "rewindController", rewindController);
        setField(launcher, "rewindPlaybackController", new PlaybackController(rewindController));
        setField(launcher, "comparator", mock(LiveTraceComparator.class));
        InputHandler input = new InputHandler();

        input.handleKeyEvent(config.getInt(SonicConfiguration.TRACE_REWIND_KEY), GLFW_PRESS);

        assertTrue(launcher.handleRealtimeRewindInput(input),
                "held rewind at the start should still consume the frame so trace playback stays paused");
        assertFalse(backend.calls.contains("beginReversePresentation"),
                "reverse audio must not start when no rewind frame can be consumed");
        assertFalse(backend.calls.contains("update"),
                "audio update would keep draining/recycling reverse presentation at the rewind boundary");
    }

    private static TraceSessionLauncher newLauncher() throws Exception {
        Constructor<TraceSessionLauncher> constructor = TraceSessionLauncher.class.getDeclaredConstructor(
                TraceEntry.class,
                TraceData.class,
                Bk2Movie.class,
                TraceReplaySessionBootstrap.ConfigSnapshot.class);
        constructor.setAccessible(true);
        return constructor.newInstance(null, null, null, null);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class FakeInputSource implements InputSource {
        private final int frames;

        FakeInputSource(int frames) {
            this.frames = frames;
        }

        @Override
        public int frameCount() {
            return frames;
        }

        @Override
        public Bk2FrameInput read(int frame) {
            return new Bk2FrameInput(frame, 0, 0, false, "fake");
        }
    }

    private static final class RecordingReverseAudioBackend extends NullAudioBackend {
        private final List<String> calls = new ArrayList<>();
        private final List<Double> reverseRates = new ArrayList<>();

        @Override
        public void beginReversePresentation() {
            calls.add("beginReversePresentation");
        }

        @Override
        public void setReversePresentationRate(double rate) {
            calls.add("setReversePresentationRate:" + rate);
            reverseRates.add(rate);
        }

        @Override
        public void update() {
            calls.add("update");
        }

        @Override
        public void endReversePresentation() {
            calls.add("endReversePresentation");
        }
    }
}

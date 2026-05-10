package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.game.rewind.InMemoryKeyframeStore;
import com.openggf.game.rewind.InputSource;
import com.openggf.game.rewind.PlaybackController;
import com.openggf.game.rewind.RewindController;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.FadeManager;
import com.openggf.tests.TestEnvironment;
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
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.activeGameplayMode();
        config = SonicConfigurationService.getInstance();
        audio = AudioManager.getInstance();
        audio.resetState();
        backend = new RecordingReverseAudioBackend();
        audio.setBackend(backend);
    }

    @AfterEach
    void tearDown() {
        audio.resetState();
        SessionManager.clear();
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
        GameplayModeContext gameplayMode = TestEnvironment.activeGameplayMode();
        FadeManager fadeManager = gameplayMode.getFadeManager();
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

        @Override
        public void beginReversePresentation() {
            calls.add("beginReversePresentation");
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

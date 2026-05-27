package com.openggf.game.rewind;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioTestFixtures;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameMode;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.FadeManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestLiveRewindManagerAudioCleanup {
    private SonicConfigurationService config;
    private AudioManager audio;
    private AudioTestFixtures.RecordingAudioBackend backend;

    @BeforeEach
    void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        audio = AudioManager.getInstance();
        audio.resetState();
        backend = new AudioTestFixtures.RecordingAudioBackend();
        audio.setBackend(backend);
    }

    @AfterEach
    void tearDown() {
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, false);
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_TAPE_COAST_ENABLED, false);
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_TAPE_COAST_MIN_STEPS, 0.25);
        audio.resetState();
        SessionManager.clear();
    }

    @Test
    void defaultLiveRewindStepsOneFramePerHeldVisualFrame() throws Exception {
        TestEnvironment.activeGameplayMode();
        LiveRewindManager manager = new LiveRewindManager(config);
        RewindController controller = new TestControllerBuilder().atFrame(5);
        installTestController(manager, controller);
        InputHandler input = new InputHandler();
        assertEquals(5, controller.currentFrame());

        input.handleKeyEvent(config.getInt(SonicConfiguration.LIVE_REWIND_KEY), GLFW_PRESS);

        assertTrue(manager.handleRealtimeRewindInput(GameMode.LEVEL, input));

        assertEquals(4, controller.currentFrame());
    }

    @Test
    void heldLiveRewindFreezesFadePresentationUntilReleaseCleanup() throws Exception {
        FadeManager fadeManager = TestEnvironment.activeGameplayMode().getFadeManager();
        LiveRewindManager manager = new LiveRewindManager(config);
        RewindController controller = new TestControllerBuilder().atFrame(5);
        installTestController(manager, controller);
        InputHandler input = new InputHandler();
        input.handleKeyEvent(config.getInt(SonicConfiguration.LIVE_REWIND_KEY), GLFW_PRESS);

        assertTrue(manager.handleRealtimeRewindInput(GameMode.LEVEL, input));

        assertTrue(fadeManager.isReversePresentationActive());

        input.handleKeyEvent(config.getInt(SonicConfiguration.LIVE_REWIND_KEY), GLFW_RELEASE);
        assertFalse(manager.handleRealtimeRewindInput(GameMode.LEVEL, input));

        assertFalse(fadeManager.isReversePresentationActive());
    }

    @Test
    void tapeCoastDelaysTransientAudioCleanupUntilCoastEnds() throws Exception {
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_TAPE_COAST_ENABLED, true);
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_TAPE_COAST_MIN_STEPS, 2.0);
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_TAPE_COAST_ACCELERATION, 1.0);
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_TAPE_COAST_DECELERATION, 0.5);
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_TAPE_COAST_MAX_STEPS, 3.0);
        TestEnvironment.activeGameplayMode();
        LiveRewindManager manager = new LiveRewindManager(config);
        RewindController controller = new TestControllerBuilder().atFrame(8);
        installTestController(manager, controller);
        InputHandler input = new InputHandler();
        input.handleKeyEvent(config.getInt(SonicConfiguration.LIVE_REWIND_KEY), GLFW_PRESS);
        assertTrue(manager.handleRealtimeRewindInput(GameMode.LEVEL, input));

        backend.clear();
        input.handleKeyEvent(config.getInt(SonicConfiguration.LIVE_REWIND_KEY), GLFW_RELEASE);

        assertTrue(manager.handleRealtimeRewindInput(GameMode.LEVEL, input));
        assertFalse(backend.calls.contains("stopAllSfx"),
                "release should keep reverse presentation active while coast still has rewind steps");

        while (manager.handleRealtimeRewindInput(GameMode.LEVEL, input)) {
            // drain coast
        }
        assertTrue(backend.calls.contains("stopAllSfx"),
                "transient cleanup should run after the coast has fully ended");
    }

    @Test
    void leavingLevelWhileRewindingStopsAllPresentationAudio() throws Exception {
        LiveRewindManager manager = new LiveRewindManager(config);
        RewindController controller = new RewindController(
                new RewindRegistry(),
                new InMemoryKeyframeStore(),
                new FakeInputSource(4),
                in -> {},
                2,
                audio);
        setField(manager, "rewindController", controller);
        setField(manager, "rewinding", true);

        manager.handleRealtimeRewindInput(GameMode.TITLE_SCREEN, new InputHandler());

        assertEquals(java.util.List.of("stopAllSfx", "stopPlayback"), backend.calls);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void installTestController(LiveRewindManager manager, RewindController controller) throws Exception {
        setField(manager, "installedGameplayMode", TestEnvironment.activeGameplayMode());
        setField(manager, "inputSource", new LiveRewindInputSource());
        setField(manager, "rewindController", controller);
        setField(manager, "speedController",
                RewindSpeedController.fromConfig(SonicConfigurationService.getInstance()));
    }

    private static final class TestControllerBuilder {
        RewindController atFrame(int frame) {
            RewindController controller = new RewindController(
                    new RewindRegistry(),
                    new InMemoryKeyframeStore(),
                    new FakeInputSource(frame + 10),
                    in -> {},
                    2,
                    AudioManager.getInstance());
            for (int i = 0; i < frame; i++) {
                controller.recordExternalStep();
            }
            return controller;
        }
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
}

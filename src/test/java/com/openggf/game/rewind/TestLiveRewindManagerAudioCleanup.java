package com.openggf.game.rewind;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.HeadlessSmpsAudioBackend;
import com.openggf.audio.rewind.AudioBackendLogicalSnapshot;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.runtime.DeterministicAudioRuntime;
import com.openggf.audio.runtime.NoOpDeterministicAudioRuntime;
import com.openggf.audio.smps.SmpsSequencerConfig;
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
import java.lang.reflect.Method;

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
    void releasingHeldRewindWhileAnOverrideIsStillActiveDoesNotEndItEarly() throws Exception {
        // Real backend (not the call-recording fake): the bug only surfaces
        // through the actual music-override-stack pop/restore state machine,
        // which NullAudioBackend/RecordingAudioBackend don't implement. Always
        // restore the class's shared fake backend afterward so this AudioManager
        // singleton doesn't leak a heavier real backend into other test classes
        // sharing the same JVM/fork.
        HeadlessSmpsAudioBackend realBackend = new HeadlessSmpsAudioBackend(config, null);
        realBackend.init();
        audio.setBackend(realBackend);
        // setBackend() auto-installs a real StreamBackedDeterministicAudioRuntime
        // for backends that support presentation PCM (HeadlessSmpsAudioBackend
        // does, for PCM rewind-history capture). That runtime pumps its own
        // submitted-command queue independently of this test's frame script and
        // is unrelated to what this test exercises (the STOP_TRANSIENT_SFX
        // policy's music-override-stack behavior); force it back to a no-op so
        // only RewindController/AudioManager's own logical-restore path runs.
        installDeterministicAudioRuntime(audio, NoOpDeterministicAudioRuntime.INSTANCE);
        try {
            int zoneMusicId = 0x82;
            int invincibilityMusicId = 0x2A;
            AudioTestFixtures.StubSmpsData zone = new AudioTestFixtures.StubSmpsData("zone");
            zone.setId(zoneMusicId);
            realBackend.prepareLogicalMusicSource(AudioSourceDescriptor.baseMusic(zoneMusicId));
            realBackend.playSmps(zone, AudioTestFixtures.EMPTY_DAC, smpsConfig(), false);

            AudioTestFixtures.StubSmpsData invincibility = new AudioTestFixtures.StubSmpsData("invincibility");
            invincibility.setId(invincibilityMusicId);
            realBackend.prepareLogicalMusicSource(AudioSourceDescriptor.baseMusic(invincibilityMusicId));
            realBackend.playSmps(invincibility, AudioTestFixtures.EMPTY_DAC, smpsConfig(), true);

            TestEnvironment.activeGameplayMode();
            LiveRewindManager manager = new LiveRewindManager(config);
            RewindController controller = new TestControllerBuilder().atFrame(8);
            installTestController(manager, controller);

            InputHandler input = new InputHandler();
            int rewindKey = config.getInt(SonicConfiguration.LIVE_REWIND_KEY);
            input.handleKeyEvent(rewindKey, GLFW_PRESS);
            assertTrue(manager.handleRealtimeRewindInput(GameMode.LEVEL, input));

            input.handleKeyEvent(rewindKey, GLFW_RELEASE);
            assertFalse(manager.handleRealtimeRewindInput(GameMode.LEVEL, input));

            AudioBackendLogicalSnapshot after = realBackend.captureLogicalSnapshot();
            assertEquals(invincibilityMusicId, after.currentMusic().id(),
                    "releasing a held rewind while still invincible must not end the invincibility override");
            assertFalse(after.overrideStack().isEmpty(),
                    "the saved zone music must remain on the override stack while the override is still active");
            // restoreMusic() only queues a deferred pop (doRestoreMusic() applies
            // it on the backend's own next real update() cycle); the bug is
            // calling restoreMusic() at all here, which this observes directly
            // rather than depending on when that next cycle happens to run.
            assertFalse(after.pendingRestore(),
                    "release cleanup must not queue a music-stack pop while the override is still legitimately active");
        } finally {
            audio.setBackend(backend);
        }
    }

    private static SmpsSequencerConfig smpsConfig() {
        return new SmpsSequencerConfig.Builder().build();
    }

    /** setDeterministicAudioRuntime is package-private on AudioManager (com.openggf.audio). */
    private static void installDeterministicAudioRuntime(AudioManager audio, DeterministicAudioRuntime runtime)
            throws Exception {
        Method method = AudioManager.class.getDeclaredMethod("setDeterministicAudioRuntime", DeterministicAudioRuntime.class);
        method.setAccessible(true);
        method.invoke(audio, runtime);
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

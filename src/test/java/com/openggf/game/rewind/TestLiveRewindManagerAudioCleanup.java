package com.openggf.game.rewind;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioManagerTestDiagnostics;
import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.AudioBackend;
import com.openggf.audio.HeadlessSmpsAudioBackend;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.rewind.AudioBackendLogicalSnapshot;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
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

        assertTrue(manager.handleRealtimeRewindInput(GameMode.LEVEL, false, input));

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

        assertTrue(manager.handleRealtimeRewindInput(GameMode.LEVEL, false, input));

        assertTrue(fadeManager.isReversePresentationActive());

        input.handleKeyEvent(config.getInt(SonicConfiguration.LIVE_REWIND_KEY), GLFW_RELEASE);
        assertFalse(manager.handleRealtimeRewindInput(GameMode.LEVEL, false, input));

        assertFalse(fadeManager.isReversePresentationActive());
    }

    @Test
    void failedReleaseRetainsLiveHostStateAndRetriesBeforeGameplayResumes()
            throws Exception {
        FailingCommitBackend failingBackend = new FailingCommitBackend();
        audio.setBackend(failingBackend);
        FadeManager fadeManager =
                TestEnvironment.activeGameplayMode().getFadeManager();
        LiveRewindManager manager = new LiveRewindManager(config);
        RewindController controller = new TestControllerBuilder().atFrame(5);
        installTestController(manager, controller);
        InputHandler input = new InputHandler();
        int rewindKey =
                config.getInt(SonicConfiguration.LIVE_REWIND_KEY);
        input.handleKeyEvent(rewindKey, GLFW_PRESS);
        assertTrue(manager.handleRealtimeRewindInput(
                GameMode.LEVEL, false, input));
        input.handleKeyEvent(rewindKey, GLFW_RELEASE);
        failingBackend.failNextCommit = true;

        assertTrue(manager.handleRealtimeRewindInput(
                        GameMode.LEVEL, false, input),
                "failed release must consume the frame instead of resuming gameplay");
        assertTrue(audio.isReverseAudioPresentationActive());
        assertTrue(fadeManager.isReversePresentationActive());
        assertTrue((boolean) getField(manager, "rewinding"));
        assertEquals(1, failingBackend.prepareCalls);
        assertEquals(1, failingBackend.rollbackCalls);

        assertFalse(manager.handleRealtimeRewindInput(
                        GameMode.LEVEL, false, input),
                "a later owner boundary should finish the retained release");
        assertFalse(audio.isReverseAudioPresentationActive());
        assertFalse(fadeManager.isReversePresentationActive());
        assertFalse((boolean) getField(manager, "rewinding"));
        assertEquals(1, failingBackend.prepareCalls,
                "the previously prepared token must be retained for retry");
        assertEquals(2, failingBackend.commitCalls);
        assertEquals(1, failingBackend.rollbackCalls);
        assertEquals(0, failingBackend.discardCalls);
    }

    @Test
    void unsupportedModeReleaseFailureKeepsGameplayFrozenAndRetainsClearOwner()
            throws Exception {
        FailingCommitBackend failingBackend = new FailingCommitBackend();
        audio.setBackend(failingBackend);
        FadeManager fadeManager =
                TestEnvironment.activeGameplayMode().getFadeManager();
        LiveRewindManager manager = new LiveRewindManager(config);
        RewindController controller = new TestControllerBuilder().atFrame(5);
        installTestController(manager, controller);
        LiveRewindInputSource inputSource =
                (LiveRewindInputSource) getField(manager, "inputSource");
        InputHandler heldInput = new InputHandler();
        heldInput.handleKeyEvent(
                config.getInt(SonicConfiguration.LIVE_REWIND_KEY), GLFW_PRESS);
        assertTrue(manager.handleRealtimeRewindInput(
                GameMode.LEVEL, false, heldInput));
        failingBackend.failNextCommit = true;

        assertTrue(manager.handleRealtimeRewindInput(
                        GameMode.TITLE_SCREEN, false, new InputHandler()),
                "failed unsupported-mode cleanup must not resume gameplay");
        assertTrue(audio.isReverseAudioPresentationActive());
        assertTrue(fadeManager.isReversePresentationActive());
        assertTrue((boolean) getField(manager, "rewinding"));
        assertEquals(controller, getField(manager, "rewindController"));
        assertEquals(inputSource, getField(manager, "inputSource"));

        assertFalse(manager.handleRealtimeRewindInput(
                        GameMode.TITLE_SCREEN, false, new InputHandler()),
                "the retained clear owner must finish on a later boundary");
        assertFalse(audio.isReverseAudioPresentationActive());
        assertFalse(fadeManager.isReversePresentationActive());
        assertFalse((boolean) getField(manager, "rewinding"));
        assertEquals(null, getField(manager, "rewindController"));
        assertEquals(1, failingBackend.prepareCalls);
        assertEquals(2, failingBackend.commitCalls);
        assertEquals(1, failingBackend.rollbackCalls);
        assertEquals(0, failingBackend.discardCalls);
    }

    @Test
    void failedLevelLoadReleaseDoesNotResetHistoryBeforeRetry()
            throws Exception {
        assertBoundaryReleaseFailureIsGated(
                RewindBoundary.LEVEL_LOAD, 0, 0);
    }

    @Test
    void failedSeamlessReleaseDoesNotRerootHistoryBeforeRetry()
            throws Exception {
        assertBoundaryReleaseFailureIsGated(
                RewindBoundary.SEAMLESS_LEVEL_TRANSITION, 4, 4);
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
        assertTrue(manager.handleRealtimeRewindInput(GameMode.LEVEL, false, input));

        input.handleKeyEvent(config.getInt(SonicConfiguration.LIVE_REWIND_KEY), GLFW_RELEASE);

        assertTrue(manager.handleRealtimeRewindInput(GameMode.LEVEL, false, input));
        assertTrue(audio.isReverseAudioPresentationActive(),
                "release should keep reverse presentation active while coast still has rewind steps");

        while (manager.handleRealtimeRewindInput(GameMode.LEVEL, false, input)) {
            // drain coast
        }
        assertFalse(audio.isReverseAudioPresentationActive(),
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
            assertTrue(manager.handleRealtimeRewindInput(GameMode.LEVEL, false, input));

            input.handleKeyEvent(rewindKey, GLFW_RELEASE);
            assertFalse(manager.handleRealtimeRewindInput(GameMode.LEVEL, false, input));

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
        audio.beginReverseAudioPresentation();

        manager.handleRealtimeRewindInput(GameMode.TITLE_SCREEN, false, new InputHandler());

        assertFalse(audio.isReverseAudioPresentationActive());
        assertTrue(AudioManagerTestDiagnostics.producerFingerprint(audio)
                .voiceIdentities().isEmpty());
        assertEquals(java.util.List.of(), backend.calls,
                "cleanup must not dispatch to the retired backend presenter");
    }

    @Test
    void pendingNonRewindableTransitionStopsPresentationAudioLikeModeExit() throws Exception {
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
        audio.beginReverseAudioPresentation();

        // currentGameMode is still LEVEL (e.g. a special-stage entry fade is in
        // flight) but the composite freeze predicate the GameLoop caller computes
        // has flipped true -- the widened gate must reject exactly like the
        // mode != LEVEL case above, reusing the same clear() teardown.
        boolean engaged = manager.handleRealtimeRewindInput(GameMode.LEVEL, true, new InputHandler());

        assertFalse(engaged, "engagement must be rejected while a transition is pending");
        assertFalse(audio.isReverseAudioPresentationActive());
        assertTrue(AudioManagerTestDiagnostics.producerFingerprint(audio)
                .voiceIdentities().isEmpty());
        assertEquals(java.util.List.of(), backend.calls,
                "cleanup must not dispatch to the retired backend presenter");
        assertFalse((boolean) getField(manager, "rewinding"));
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private void assertBoundaryReleaseFailureIsGated(
            RewindBoundary boundary,
            int expectedRetriedFrame,
            int expectedRetriedEarliestFrame) throws Exception {
        FailingCommitBackend failingBackend = new FailingCommitBackend();
        audio.setBackend(failingBackend);
        FadeManager fadeManager =
                TestEnvironment.activeGameplayMode().getFadeManager();
        LiveRewindManager manager = new LiveRewindManager(config);
        RewindController controller = new TestControllerBuilder().atFrame(5);
        installTestController(manager, controller);
        LiveRewindInputSource inputSource =
                (LiveRewindInputSource) getField(manager, "inputSource");
        InputHandler heldInput = new InputHandler();
        heldInput.handleKeyEvent(
                config.getInt(SonicConfiguration.LIVE_REWIND_KEY), GLFW_PRESS);
        assertTrue(manager.handleRealtimeRewindInput(
                GameMode.LEVEL, false, heldInput));
        int frameBefore = controller.currentFrame();
        int earliestBefore = controller.earliestAvailableFrame();
        int inputCountBefore = inputSource.frameCount();
        failingBackend.failNextCommit = true;

        manager.markBoundary(boundary);

        assertTrue(audio.isReverseAudioPresentationActive());
        assertTrue(fadeManager.isReversePresentationActive());
        assertTrue((boolean) getField(manager, "rewinding"));
        assertEquals(frameBefore, controller.currentFrame(),
                "failed release must not move the controller boundary");
        assertEquals(earliestBefore, controller.earliestAvailableFrame(),
                "failed release must not reroot controller history");
        assertEquals(inputCountBefore, inputSource.frameCount(),
                "failed release must not reset or trim input history");

        manager.markBoundary(boundary);

        assertFalse(audio.isReverseAudioPresentationActive());
        assertFalse(fadeManager.isReversePresentationActive());
        assertFalse((boolean) getField(manager, "rewinding"));
        assertEquals(expectedRetriedFrame, controller.currentFrame());
        assertEquals(expectedRetriedEarliestFrame,
                controller.earliestAvailableFrame());
        assertEquals(1, failingBackend.prepareCalls,
                "boundary retry must retain the exact prepared token");
        assertEquals(2, failingBackend.commitCalls);
        assertEquals(1, failingBackend.rollbackCalls);
        assertEquals(0, failingBackend.discardCalls);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void installTestController(LiveRewindManager manager, RewindController controller) throws Exception {
        setField(manager, "installedGameplayMode", TestEnvironment.activeGameplayMode());
        setInstalledStepperKind(manager, "LEVEL_FRAME");
        setField(manager, "inputSource", new LiveRewindInputSource());
        setField(manager, "rewindController", controller);
        setField(manager, "speedController",
                RewindSpeedController.fromConfig(SonicConfigurationService.getInstance()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setInstalledStepperKind(LiveRewindManager manager, String kindName) throws Exception {
        Class<?> kindClass = Class.forName("com.openggf.game.rewind.LiveRewindManager$StepperKind");
        Object kind = Enum.valueOf((Class<? extends Enum>) kindClass.asSubclass(Enum.class), kindName);
        setField(manager, "installedStepperKind", kind);
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

    private static final class FailingCommitBackend
            extends NullAudioBackend {
        private boolean failNextCommit;
        private int prepareCalls;
        private int commitCalls;
        private int rollbackCalls;
        private int discardCalls;

        @Override
        public AudioBackend.PreparedLogicalRestore prepareLogicalRestore(
                AudioBackendLogicalSnapshot snapshot,
                SmpsDriverSnapshot.DependencyResolver resolver,
                boolean preservePresentationQueue) {
            prepareCalls++;
            return new Prepared(snapshot);
        }

        @Override
        public void commitLogicalRestore(
                AudioBackend.PreparedLogicalRestore prepared) {
            commitCalls++;
            if (failNextCommit) {
                failNextCommit = false;
                throw new IllegalStateException(
                        "injected production-host commit failure");
            }
        }

        @Override
        public void rollbackLogicalRestore(
                AudioBackend.PreparedLogicalRestore prepared) {
            rollbackCalls++;
        }

        @Override
        public void discardLogicalRestore(
                AudioBackend.PreparedLogicalRestore prepared) {
            discardCalls++;
        }

        private record Prepared(AudioBackendLogicalSnapshot snapshot)
                implements AudioBackend.PreparedLogicalRestore {
        }
    }
}

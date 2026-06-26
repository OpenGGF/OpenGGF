package com.openggf.game.rewind;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameSound;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.rewind.AudioBackendLogicalSnapshot;
import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.audio.rewind.AudioPresentationPolicy;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameMode;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
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

/**
 * Phase 3B: held-rewind audio restore deferral. While reverse audio
 * presentation is active, {@link RewindController#stepBackward()} must skip
 * the per-frame logical audio restore (presentation reads PcmHistoryRing
 * instead), and every path that ends the held rewind must land exactly one
 * logical restore whose result is identical to what per-frame restores would
 * have produced at the committed frame.
 */
class TestHeldRewindAudioRestoreDeferral {

    private AudioManager audio;
    private CountingRestoreBackend backend;

    @BeforeEach
    void setUp() {
        audio = AudioManager.getInstance();
        audio.resetState();
        backend = new CountingRestoreBackend();
        audio.setBackend(backend);
    }

    @AfterEach
    void tearDown() {
        audio.endReverseAudioPresentation();
        audio.resetState();
    }

    @Test
    void heldRewindDefersIntermediateRestoresAndCommitsExactlyOnceOnRelease() {
        RewindController controller = newController(scriptedStepper(), 4);
        for (int i = 0; i < 8; i++) {
            controller.step();
        }
        backend.logicalRestores = 0;

        audio.beginReverseAudioPresentation();
        for (int i = 0; i < 3; i++) {
            assertTrue(controller.stepBackward());
        }
        assertEquals(0, backend.logicalRestores,
                "held backward steps must not rebuild logical audio state per frame");

        controller.commitDeferredAudioRestore();
        audio.afterRewindRestore(controller.currentFrame(),
                AudioPresentationPolicy.STOP_TRANSIENT_SFX_RESYNC_MUSIC);

        assertEquals(1, backend.logicalRestores,
                "release must land exactly one logical restore at the committed frame");
        assertEquals(5, controller.currentFrame());

        controller.commitDeferredAudioRestore();
        assertEquals(1, backend.logicalRestores, "commit must be one-shot");
    }

    @Test
    void stepBackwardWithoutReversePresentationKeepsPerFrameRestores() {
        RewindController controller = newController(scriptedStepper(), 4);
        for (int i = 0; i < 8; i++) {
            controller.step();
        }
        backend.logicalRestores = 0;

        assertTrue(controller.stepBackward());
        assertTrue(controller.stepBackward());

        assertEquals(2, backend.logicalRestores,
                "non-held backward stepping (trace tooling) must keep eager restores");
    }

    @Test
    void releasedHeldRewindStateMatchesPerFrameRestoresAndFreshReplay() {
        // Per-frame baseline: reverse presentation inactive -> eager restores.
        RewindController baseline = newController(scriptedStepper(), 4);
        for (int i = 0; i < 8; i++) {
            baseline.step();
        }
        for (int i = 0; i < 3; i++) {
            assertTrue(baseline.stepBackward());
        }
        assertEquals(5, baseline.currentFrame());
        AudioLogicalSnapshot perFrameState = audio.captureLogicalSnapshot();

        // Deferred run: identical script, held reverse presentation, single
        // commit on release.
        audio.resetState();
        audio.setBackend(backend);
        RewindController deferred = newController(scriptedStepper(), 4);
        for (int i = 0; i < 8; i++) {
            deferred.step();
        }
        audio.beginReverseAudioPresentation();
        for (int i = 0; i < 3; i++) {
            assertTrue(deferred.stepBackward());
        }
        deferred.commitDeferredAudioRestore();
        audio.endReverseAudioPresentation();
        AudioLogicalSnapshot deferredState = audio.captureLogicalSnapshot();

        assertEquals(perFrameState, deferredState,
                "released held rewind must land the exact per-frame-restore logical state");

        // Fresh replay to the committed frame.
        audio.resetState();
        audio.setBackend(backend);
        RewindController fresh = newController(scriptedStepper(), 4);
        for (int i = 0; i < 5; i++) {
            fresh.step();
        }
        AudioLogicalSnapshot freshState = audio.captureLogicalSnapshot();

        assertEquals(freshState.ringLeft(), deferredState.ringLeft());
        assertEquals(freshState.commandTimelineFrame(), deferredState.commandTimelineFrame());
        assertEquals(freshState.commandTimelineNextOrder(), deferredState.commandTimelineNextOrder());
        assertEquals(freshState.commandEntryCount(), deferredState.commandEntryCount());
        // NullAudioBackend reports an empty backend snapshot, so this only
        // asserts the AudioManager-level component here; backend (SmpsDriver/
        // synth) restore equivalence is proven bit-exactly by
        // TestLWJGLAudioBackendSnapshot.restoreLogicalSnapshotReusesExistingDriverInstanceBitExactly.
        assertEquals(freshState.backend(), deferredState.backend());
    }

    @Test
    void levelLoadDuringHeldRewindKeepsFreshAudioAndDropsStaleDeferredRestore() {
        // Reproduces the fade-completion-mid-hold interleaving:
        // LevelManager.loadLevel -> initAudio (fresh new-level state) ->
        // resetToFrameZero. The re-root must NOT commit the stale pre-rewind
        // logical state over the freshly initialized new-level audio.
        RewindController controller = newController(scriptedStepper(), 4);
        for (int i = 0; i < 8; i++) {
            controller.step();
        }
        audio.beginReverseAudioPresentation();
        for (int i = 0; i < 3; i++) {
            assertTrue(controller.stepBackward());
        }
        // Deferred committed value at frame 5 would be ringLeft=false; the
        // fresh-init marker (standing in for initAudio's reinit) sets the
        // distinguishable value true.
        audio.resetRingSound();
        backend.logicalRestores = 0;

        controller.resetToFrameZero();

        assertEquals(0, backend.logicalRestores,
                "level-boundary re-root must drop, not commit, the stale deferred restore");
        assertTrue(audio.captureLogicalSnapshot().ringLeft(),
                "freshly initialized new-level audio state must survive the re-root");

        controller.commitDeferredAudioRestore();
        assertEquals(0, backend.logicalRestores,
                "dropped deferral must not resurrect on a later commit");
    }

    @Test
    void resetBufferAtCurrentFrameAlsoDropsStaleDeferredRestore() {
        RewindController controller = newController(scriptedStepper(), 4);
        for (int i = 0; i < 8; i++) {
            controller.step();
        }
        audio.beginReverseAudioPresentation();
        for (int i = 0; i < 3; i++) {
            assertTrue(controller.stepBackward());
        }
        audio.resetRingSound();
        backend.logicalRestores = 0;

        controller.resetBufferAtCurrentFrame();

        assertEquals(0, backend.logicalRestores,
                "seamless-transition re-root must drop, not commit, the stale deferred restore");
        assertTrue(audio.captureLogicalSnapshot().ringLeft(),
                "post-transition audio state must survive the re-root");
    }

    @Test
    void recordExternalStepCommitsDeferredRestoreBeforeResumingForwardPlay() {
        RewindController controller = newController(scriptedStepper(), 4);
        for (int i = 0; i < 8; i++) {
            controller.step();
        }
        backend.logicalRestores = 0;
        audio.beginReverseAudioPresentation();
        for (int i = 0; i < 3; i++) {
            assertTrue(controller.stepBackward());
        }
        audio.endReverseAudioPresentation();

        assertTrue(controller.recordExternalStep());

        assertEquals(1, backend.logicalRestores,
                "forward resume must commit the deferred restore before recording live frames");
        assertEquals(6, controller.currentFrame());
    }

    @Test
    void seekToSupersedesDeferredRestoreWithItsOwnSingleRestore() {
        RewindController controller = newController(scriptedStepper(), 4);
        for (int i = 0; i < 8; i++) {
            controller.step();
        }
        backend.logicalRestores = 0;
        audio.beginReverseAudioPresentation();
        assertTrue(controller.stepBackward());
        assertEquals(0, backend.logicalRestores);

        controller.seekTo(3);

        assertEquals(1, backend.logicalRestores, "seek commit lands exactly one restore");
        controller.commitDeferredAudioRestore();
        assertEquals(1, backend.logicalRestores,
                "seek must clear the deferred flag so no second restore can fire");
    }

    @Test
    void liveRewindManagerReleasePathCommitsDeferredRestoreBeforePresentationCleanup() throws Exception {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        try {
            TestEnvironment.activeGameplayMode();
            LiveRewindManager manager = new LiveRewindManager(config);
            RewindController controller = newController(scriptedStepper(), 4);
            for (int i = 0; i < 8; i++) {
                controller.step();
            }
            installTestController(manager, controller);
            InputHandler input = new InputHandler();
            int rewindKey = config.getInt(SonicConfiguration.LIVE_REWIND_KEY);

            backend.logicalRestores = 0;
            input.handleKeyEvent(rewindKey, GLFW_PRESS);
            assertTrue(manager.handleRealtimeRewindInput(GameMode.LEVEL, input));
            assertTrue(manager.handleRealtimeRewindInput(GameMode.LEVEL, input));
            assertEquals(0, backend.logicalRestores,
                    "held live rewind must defer logical restores while reverse presentation runs");

            input.handleKeyEvent(rewindKey, GLFW_RELEASE);
            assertFalse(manager.handleRealtimeRewindInput(GameMode.LEVEL, input));

            assertEquals(1, backend.logicalRestores,
                    "live rewind release must land exactly one committed logical restore");
            int restoreIndex = backend.calls.indexOf("restoreLogicalSnapshot");
            int stopSfxIndex = backend.calls.indexOf("stopAllSfx");
            assertTrue(restoreIndex >= 0 && stopSfxIndex > restoreIndex,
                    "committed restore must precede presentation cleanup, calls=" + backend.calls);
        } finally {
            config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, false);
            SessionManager.clear();
        }
    }

    @Test
    void liveRewindManagerLevelLoadBoundaryDropsDeferredRestoreBeforePresentationCleanup()
            throws Exception {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        try {
            TestEnvironment.activeGameplayMode();
            LiveRewindManager manager = liveManagerWithControllerAtFrame(config, 8);
            InputHandler input = new InputHandler();
            input.handleKeyEvent(config.getInt(SonicConfiguration.LIVE_REWIND_KEY), GLFW_PRESS);
            assertTrue(manager.handleRealtimeRewindInput(GameMode.LEVEL, input));
            assertEquals(0, backend.logicalRestores);
            audio.resetRingSound();
            backend.logicalRestores = 0;
            backend.calls.clear();

            manager.markBoundary(RewindBoundary.LEVEL_LOAD);

            assertEquals(0, backend.logicalRestores,
                    "level-load boundary must drop stale deferred restore before cleanup");
            assertTrue(backend.calls.contains("stopAllSfx"),
                    "boundary must still clean transient reverse SFX presentation");
            assertTrue(backend.calls.contains("restoreMusic"),
                    "boundary must still resync music after reverse presentation");
            assertFalse(audio.isReverseAudioPresentationActive(),
                    "boundary cleanup must end reverse audio presentation");
            assertTrue(audio.captureLogicalSnapshot().ringLeft(),
                    "fresh boundary audio marker must survive the dropped restore");
        } finally {
            config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, false);
            SessionManager.clear();
        }
    }

    @Test
    void liveRewindManagerSeamlessBoundaryDropsDeferredRestoreBeforePresentationCleanup()
            throws Exception {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        try {
            TestEnvironment.activeGameplayMode();
            LiveRewindManager manager = liveManagerWithControllerAtFrame(config, 8);
            InputHandler input = new InputHandler();
            input.handleKeyEvent(config.getInt(SonicConfiguration.LIVE_REWIND_KEY), GLFW_PRESS);
            assertTrue(manager.handleRealtimeRewindInput(GameMode.LEVEL, input));
            assertEquals(0, backend.logicalRestores);
            audio.resetRingSound();
            backend.logicalRestores = 0;
            backend.calls.clear();

            manager.markBoundary(RewindBoundary.SEAMLESS_LEVEL_TRANSITION);

            assertEquals(0, backend.logicalRestores,
                    "seamless boundary must drop stale deferred restore before cleanup");
            assertTrue(backend.calls.contains("stopAllSfx"),
                    "boundary must still clean transient reverse SFX presentation");
            assertTrue(backend.calls.contains("restoreMusic"),
                    "boundary must still resync music after reverse presentation");
            assertFalse(audio.isReverseAudioPresentationActive(),
                    "boundary cleanup must end reverse audio presentation");
            assertTrue(audio.captureLogicalSnapshot().ringLeft(),
                    "post-transition audio marker must survive the dropped restore");
        } finally {
            config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, false);
            SessionManager.clear();
        }
    }

    private RewindController newController(EngineStepper stepper, int keyframeInterval) {
        return new RewindController(
                new RewindRegistry(),
                new InMemoryKeyframeStore(),
                new FakeInputSource(40),
                stepper,
                keyframeInterval,
                audio);
    }

    /**
     * Ring alternation script: frames 1/2/5/6 collect a ring, frame 3 resets
     * the alternation. ringLeft is therefore true at frame 4 and false at
     * frame 5 — distinct values around the committed rewind target.
     */
    private EngineStepper scriptedStepper() {
        return in -> {
            switch (in.frameIndex()) {
                case 1, 2, 5, 6 -> audio.playSfx(GameSound.RING);
                case 3 -> audio.resetRingSound();
                default -> { }
            }
        };
    }

    private static void installTestController(LiveRewindManager manager, RewindController controller)
            throws Exception {
        setField(manager, "installedGameplayMode", TestEnvironment.activeGameplayMode());
        setField(manager, "inputSource", new LiveRewindInputSource());
        setField(manager, "rewindController", controller);
        setField(manager, "speedController",
                RewindSpeedController.fromConfig(SonicConfigurationService.getInstance()));
    }

    private LiveRewindManager liveManagerWithControllerAtFrame(
            SonicConfigurationService config,
            int frame) throws Exception {
        LiveRewindManager manager = new LiveRewindManager(config);
        RewindController controller = newController(scriptedStepper(), 4);
        for (int i = 0; i < frame; i++) {
            controller.step();
        }
        installTestController(manager, controller);
        return manager;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class CountingRestoreBackend extends NullAudioBackend {
        final java.util.List<String> calls = new java.util.ArrayList<>();
        int logicalRestores;

        @Override
        public void restoreLogicalSnapshot(AudioBackendLogicalSnapshot snapshot) {
            logicalRestores++;
            calls.add("restoreLogicalSnapshot");
        }

        @Override
        public void stopAllSfx() {
            calls.add("stopAllSfx");
        }

        @Override
        public void restoreMusic() {
            calls.add("restoreMusic");
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

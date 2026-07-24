package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioManagerTestDiagnostics;
import com.openggf.audio.AudioBackend;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.rewind.AudioBackendLogicalSnapshot;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.runtime.DeterministicAudioRuntime;
import com.openggf.audio.runtime.FrameAudioMode;
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
import java.lang.reflect.Method;
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
    private RecordingReverseAudioRuntime runtime;

    @BeforeEach
    void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.activeGameplayMode();
        config = SonicConfigurationService.getInstance();
        audio = AudioManager.getInstance();
        audio.resetState();
        backend = new RecordingReverseAudioBackend();
        audio.setBackend(backend);
        runtime = new RecordingReverseAudioRuntime();
        setAudioRuntime(audio, runtime);
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

        assertTrue(launcher.handleRealtimeRewindInput(false, input));

        assertTrue(fadeManager.isReversePresentationActive());
        assertTrue(audio.isReverseAudioPresentationActive());
        assertTrue(AudioManagerTestDiagnostics.producerFingerprint(audio)
                .reverseActive());

        input.handleKeyEvent(config.getInt(SonicConfiguration.TRACE_REWIND_KEY), GLFW_RELEASE);
        assertFalse(launcher.handleRealtimeRewindInput(false, input));

        assertFalse(fadeManager.isReversePresentationActive());
        assertFalse(audio.isReverseAudioPresentationActive());
        assertFalse(AudioManagerTestDiagnostics.producerFingerprint(audio)
                .reverseActive());
    }

    @Test
    void failedReleaseRetainsTraceHostAndPlaybackUntilLaterOwnerBoundary()
            throws Exception {
        TraceSessionLauncher launcher = newLauncher();
        RewindController rewindController = new RewindController(
                new RewindRegistry(),
                new InMemoryKeyframeStore(),
                new FakeInputSource(10),
                in -> {},
                2,
                audio);
        for (int i = 0; i < 5; i++) {
            rewindController.recordExternalStep();
        }
        PlaybackController playbackController =
                new PlaybackController(rewindController);
        setField(launcher, "rewindController", rewindController);
        setField(launcher, "rewindPlaybackController", playbackController);
        setField(launcher, "comparator", mock(LiveTraceComparator.class));
        setField(launcher, "rewindMovieBaseFrame", 0);
        setField(launcher, "rewindTraceBaseFrame", 0);
        FadeManager fadeManager =
                TestEnvironment.activeGameplayMode().getFadeManager();
        InputHandler input = new InputHandler();
        int rewindKey =
                config.getInt(SonicConfiguration.TRACE_REWIND_KEY);
        input.handleKeyEvent(rewindKey, GLFW_PRESS);
        assertTrue(launcher.handleRealtimeRewindInput(false, input));
        input.handleKeyEvent(rewindKey, GLFW_RELEASE);
        backend.failNextCommit = true;

        assertTrue(launcher.handleRealtimeRewindInput(false, input),
                "failed release must keep Trace gameplay frozen");
        assertTrue(audio.isReverseAudioPresentationActive());
        assertTrue(fadeManager.isReversePresentationActive());
        assertEquals(PlaybackController.State.REWINDING,
                playbackController.state());
        assertTrue((boolean) getField(launcher, "realtimeRewinding"));
        assertEquals(1, backend.prepareCalls);
        assertEquals(1, backend.rollbackCalls);

        assertFalse(launcher.handleRealtimeRewindInput(false, input));
        assertFalse(audio.isReverseAudioPresentationActive());
        assertFalse(fadeManager.isReversePresentationActive());
        assertEquals(PlaybackController.State.PLAYING,
                playbackController.state());
        assertFalse((boolean) getField(launcher, "realtimeRewinding"));
        assertEquals(1, backend.prepareCalls,
                "the retained prepared token must be retried in place");
        assertEquals(2, backend.commitCalls);
        assertEquals(1, backend.rollbackCalls);
        assertEquals(0, backend.discardCalls);
    }

    @Test
    void failedTeardownReleaseRetainsActiveSessionUntilLaterRetry()
            throws Exception {
        TraceSessionLauncher launcher = newLauncher();
        RewindController rewindController = new RewindController(
                new RewindRegistry(),
                new InMemoryKeyframeStore(),
                new FakeInputSource(10),
                in -> {},
                2,
                audio);
        for (int i = 0; i < 5; i++) {
            rewindController.recordExternalStep();
        }
        setField(launcher, "rewindController", rewindController);
        setField(launcher, "rewindPlaybackController",
                new PlaybackController(rewindController));
        setField(launcher, "comparator", mock(LiveTraceComparator.class));
        setStaticField(TraceSessionLauncher.class, "activeSession", launcher);
        audio.beginReverseAudioPresentation();
        audio.restoreLogicalSnapshot(audio.captureLogicalSnapshot());
        assertTrue(audio.commitDeferredReverseLogicalRestore());
        backend.failNextCommit = true;

        invokeTeardown(launcher);

        assertEquals(launcher, TraceSessionLauncher.active(),
                "failed teardown release must retain the active retry host");
        assertTrue(audio.isReverseAudioPresentationActive());
        assertEquals(rewindController, getField(launcher, "rewindController"));

        invokeTeardown(launcher);

        assertEquals(null, TraceSessionLauncher.active());
        assertFalse(audio.isReverseAudioPresentationActive());
        assertEquals(1, backend.prepareCalls,
                "teardown retry must retain the exact prepared token");
        assertEquals(2, backend.commitCalls);
        assertEquals(1, backend.rollbackCalls);
        assertEquals(0, backend.discardCalls);
    }

    /**
     * Wave 3, Fix 2: the same softlock {@code LiveRewindManager} was gated against
     * (commit {@code 26fb7debd}) also reaches Trace Test Mode's realtime rewind, which
     * that commit deliberately left untouched ("its own realtime-rewind path was not
     * covered by this investigation"). A held rewind in Trace Test Mode during a
     * special/bonus-stage/ending/zone-act transition window (still {@code GameMode.LEVEL}
     * throughout) could keep walking backward through pre-transition history and desync
     * the trace comparator the same way. Mirrors
     * {@code TestLiveRewindManagerAudioCleanup}'s equivalent pending-transition case.
     */
    @Test
    void pendingNonRewindableTransitionRejectsRealtimeRewindAndTearsDownPresentation() throws Exception {
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

        // pending=false: engage normally first, matching the report's "already held
        // when the transition fires" worst case.
        assertTrue(launcher.handleRealtimeRewindInput(false, input));
        assertTrue(fadeManager.isReversePresentationActive());

        // pending=true (still held): must reject and tear down, not keep walking
        // backward through pre-transition history.
        assertFalse(launcher.handleRealtimeRewindInput(true, input));
        assertFalse(fadeManager.isReversePresentationActive(),
                "a pending non-rewindable transition must tear down the reverse fade "
                        + "presentation, not leave it active while the frame is rejected");
        assertFalse(audio.isReverseAudioPresentationActive(),
                "rejecting on a pending transition must run the same cleanup as a normal "
                        + "release, not silently leave the audio reverse-presentation stuck");
    }

    @Test
    void traceBoundaryExternalFrameRerootsRewindBuffer() throws Exception {
        TraceSessionLauncher launcher = newLauncher();
        RewindController rewindController = new RewindController(
                new RewindRegistry(),
                new InMemoryKeyframeStore(),
                new FakeInputSource(20),
                in -> {},
                3,
                AudioManager.getInstance());
        for (int i = 0; i < 5; i++) {
            rewindController.recordExternalStep();
        }
        setField(launcher, "rewindController", rewindController);

        launcher.recordExternalRewindFrameAtBoundary();

        assertEquals(6, rewindController.currentFrame());
        assertEquals(6, rewindController.earliestAvailableFrame());
        assertFalse(rewindController.stepBackward(),
                "Trace rewind must not cross a seamless transition boundary");
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

    private static void setStaticField(
            Class<?> target, String fieldName, Object value) throws Exception {
        Field field = target.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static void invokeTeardown(TraceSessionLauncher launcher)
            throws Exception {
        Method method = TraceSessionLauncher.class.getDeclaredMethod("teardown");
        method.setAccessible(true);
        method.invoke(launcher);
    }

    private static Object getField(Object target, String fieldName)
            throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setAudioRuntime(
            AudioManager audio, DeterministicAudioRuntime runtime) {
        try {
            var method = AudioManager.class.getDeclaredMethod(
                    "setDeterministicAudioRuntime",
                    DeterministicAudioRuntime.class);
            method.setAccessible(true);
            method.invoke(audio, runtime);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
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

    private static final class RecordingReverseAudioBackend extends NullAudioBackend {
        private final List<String> calls = new ArrayList<>();
        private boolean failNextCommit;
        private int prepareCalls;
        private int commitCalls;
        private int rollbackCalls;
        private int discardCalls;

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
                        "injected Trace host commit failure");
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

    private static final class RecordingReverseAudioRuntime implements DeterministicAudioRuntime {
        private final List<String> calls = new ArrayList<>();

        @Override
        public void advanceFrame(long frame, FrameAudioMode mode) {
        }

        @Override
        public void beginReversePresentation() {
            calls.add("beginReversePresentation");
        }

        @Override
        public void endReversePresentation() {
            calls.add("endReversePresentation");
        }
    }
}

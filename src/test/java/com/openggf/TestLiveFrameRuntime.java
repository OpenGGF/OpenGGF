package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioManagerTestDiagnostics;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameMode;
import com.openggf.game.LegalDisclaimerScreen;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.pipeline.UiRenderPipeline;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.mockito.Mockito.*;

/** Actual Engine/GameLoop/input/fade/audio caller; substitutes GL resources and picture endpoint only. */
@Isolated
class TestLiveFrameRuntime {
    private static final long SECOND = 1_000_000_000L;
    private Engine engine;
    private InputHandler input;
    private AudioManager audio;
    private ObservedScreen screen;
    private FadeManager fade;
    private int pictures, swaps;

    @AfterEach void close() {
        if (engine != null) engine.getGameLoop().closePresence();
        Engine.clearGlobalInstance();
        TestEnvironment.resetEngineOnly();
    }

    private LiveFrameRuntime open(int hz, int fps) throws Exception {
        TestEnvironment.resetEngineOnly();
        var original = EngineServices.current();
        var config = original.configuration();
        config.setConfigValue(SonicConfiguration.REGION, hz == 50 ? "PAL" : "NTSC");
        config.setConfigValue(SonicConfiguration.FPS, fps);
        config.setConfigValue(SonicConfiguration.P1_A, GLFW_KEY_SPACE);
        config.setConfigValue(SonicConfiguration.PAUSE_KEY, GLFW_KEY_F9);
        config.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, GLFW_KEY_F8);
        audio = original.audio();
        audio.setBackend(new NullAudioBackend());
        audio.resetState();
        var graphics = mock(GraphicsManager.class);
        fade = new FadeManager();
        var pipeline = new UiRenderPipeline(graphics);
        pipeline.setFadeManager(fade);
        when(graphics.getUiRenderPipeline()).thenReturn(pipeline);
        when(graphics.getFadeManager()).thenReturn(fade);
        engine = new Engine(new EngineContext(config, graphics, audio, original.roms(),
                original.profiler(), original.debugOverlay(), original.playbackDebug(),
                original.romDetection(), original.crossGameFeatures()));
        input = new InputHandler(InputBindingFactory.supplier(config));
        engine.setInputHandler(input);
        engine.getGameLoop().setGameMode(GameMode.LEGAL_DISCLAIMER);
        screen = new ObservedScreen();
        field("legalDisclaimerScreen", screen);
        return engine.createLiveFrameRuntime(capture -> pictures++, () -> swaps++);
    }

    @ParameterizedTest @CsvSource({"50,30", "50,50", "50,60", "50,120", "50,144",
            "60,30", "60,50", "60,60", "60,120", "60,144"})
    void actualCallerHasIdenticalPerTickStateAndOneSecondOfPcmAcrossPictureSchedules(int hz, int fps)
            throws Exception {
        var runtime = open(hz, fps);
        runtime.iterate(0);
        pictures = swaps = 0;
        var expected = new ArrayList<Long>();
        long state = 0;
        for (int tick = 0; tick < hz; tick++) {
            state = state * 17 + tick % 7;
            expected.add(state);
        }
        screen.after = () -> input.setLogicalOverride(sample(screen.samples.size() % 7));
        input.setLogicalOverride(sample(0));
        fade.startFadeFromBlack(null);
        try (var tap = audio.beginLiveCaptureAudio(hz)) {
            long previous = 0;
            for (int picture = 1; picture <= fps; picture++) {
                long now = (picture * SECOND + fps - 1) / fps;
                runtime.iterate(now - previous);
                previous = now;
            }
            assertEquals(expected, screen.states, "all completed real GameLoop samples, not only a final counter");
            assertEquals(hz, screen.samples.size());
            assertEquals(fps, swaps);
            assertEquals(fps, pictures);
            assertEquals(hz, AudioManagerTestDiagnostics.shadowParitySnapshot(audio).presentedFrames());
            short[] packet = new short[2 * tap.maxStereoFramesPerPacket()];
            for (int tick = 0; tick < hz; tick++) tap.drainPresentationFrame(packet);
            assertEquals(48_000, tap.totalStereoFrames(), "region PCM duration cannot follow the display cap");
            assertEquals(0, runtime.statistics().pendingTicks());
            assertEquals(0, runtime.statistics().discardedWallNanos());
        }
    }

    @Test void zeroTickPicturesCannotAcknowledgeTwoReleasedInputTaps() throws Exception {
        var runtime = open(60, 144);
        runtime.iterate(0);
        input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_PRESS);
        input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_RELEASE);
        input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_PRESS);
        input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_RELEASE);
        assertEquals(0, runtime.iterate(7_000_000).services());
        assertEquals(0, runtime.iterate(7_000_000).services());
        assertTrue(screen.samples.isEmpty());
        assertEquals(3, runtime.iterate(36_000_000).services()); // 50 ms admits three ticks.
        runtime.iterate(16_666_667);
        assertEquals(List.of(1, 0, 1, 0), screen.samples.stream()
                .map(s -> s.player1().actionPressedMask()).toList());
        assertEquals(List.of(1, 0, 1, 0), screen.samples.stream()
                .map(s -> s.player1().actionHeldMask()).toList());
    }

    @Test void shortStallRetainsDebtAndLongStallBoundsEachRealCallerBatch() throws Exception {
        var runtime = open(60, 30);
        runtime.iterate(0);
        assertEquals(4, runtime.iterate(100_000_000).services());
        assertEquals(2, runtime.statistics().pendingTicks());
        assertEquals(2, runtime.iterate(0).services());
        assertEquals(6, screen.samples.size());
        assertEquals(4, runtime.iterate(5 * SECOND).services());
        assertEquals(4_750_000_000L, runtime.statistics().discardedWallNanos());
        assertEquals(11, runtime.statistics().pendingTicks());
        for (int batch = 0; batch < 3; batch++) assertTrue(runtime.iterate(0).services() <= 4);
        assertEquals(21, screen.samples.size(), "discarded wall time never invents skipped semantic identities");
        assertEquals(0, runtime.statistics().pendingTicks());
    }

    @Test void pauseBoundaryStopsCatchUpAndResumeCannotSpendThePausedInterval() throws Exception {
        var runtime = open(60, 144);
        runtime.iterate(0);
        // The actual host pause owner is active only in gameplay-capable modes. A boot
        // screen update toggles through that owner here, without manufacturing a world.
        screen.after = () -> engine.getGameLoop().toggleUserPause();
        assertEquals(1, runtime.iterate(100_000_000).services());
        assertTrue(engine.getGameLoop().isUserPaused());
        assertEquals(0, runtime.statistics().pendingTicks());
        screen.after = () -> { };
        engine.getGameLoop().toggleUserPause();
        assertEquals(0, runtime.iterate(10 * SECOND).services(), "external pause boundary rebases before admission");
        assertEquals(1, runtime.iterate(16_666_667).services());
        assertEquals(2, screen.samples.size());
    }

    @Test void forcedScenePictureEndsTheBatchBeforeAnotherServiceCanParkIt() throws Exception {
        open(60, 30);
        var capture = mock(GameLoopSuspension.CaptureEffects.class);
        when(capture.captureViewportThumbnail()).thenReturn(new com.openggf.graphics.RgbaImage(1, 1, new int[]{0xFFFFFFFF}));
        when(capture.thumbnailWidth()).thenReturn(1);
        when(capture.thumbnailHeight()).thenReturn(1);
        var owner = engine.getGameLoop().suspension;
        var runtime = engine.createLiveFrameRuntime(output -> {
            pictures++;
            owner.afterScene(capture);
        }, () -> swaps++);
        runtime.iterate(0);
        var request = GameLoopSuspension.class.getDeclaredField("suspendThumbnailRequested");
        request.setAccessible(true);
        screen.after = () -> {
            try { request.setBoolean(owner, true); }
            catch (IllegalAccessException failure) { throw new AssertionError(failure); }
        };
        int before = swaps;
        assertEquals(1, runtime.iterate(100_000_000).services(), "a thumbnail opportunity ends the old batch");
        screen.after = () -> { };
        assertFalse(owner.requiresScenePicture(), "the real after-scene owner consumes its request");
        assertEquals(before + 1, swaps, "the full-size picture is shown before another service can park the world");
        assertEquals(0, runtime.iterate(0).services(), "old reserved allowances cannot run after that boundary");
    }

    @Test void sustainedOverloadStillPollsBetweenBoundedRealCallerBatchesAndAdmitsEachTapOnce() throws Exception {
        var runtime = open(60, 30);
        runtime.iterate(0);
        for (int batch = 0; batch < 20; batch++) {
            input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_PRESS);
            input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_RELEASE);
            assertEquals(4, runtime.iterate(500_000_000).services());
            var samples = screen.samples.subList(batch * 4, batch * 4 + 4);
            assertEquals(List.of(1, 0, 0, 0), samples.stream().map(x -> x.player1().actionPressedMask()).toList());
            assertTrue(runtime.statistics().pendingTicks() <= 11);
        }
        assertEquals(80, screen.samples.size());
        assertTrue(runtime.statistics().discardedWallNanos() > 8 * SECOND);
    }

    @Test void failedSemanticServiceStopsTheBatchBeforeAnyPictureOrSwap() {
        var owner = mock(LiveFrameRuntime.Owner.class);
        when(owner.simulationHz()).thenReturn(60);
        when(owner.presentationHz()).thenReturn(30);
        when(owner.focused()).thenReturn(true);
        when(owner.boundary()).thenReturn("visit");
        when(owner.draw(anyBoolean())).thenReturn(true);
        var runtime = new LiveFrameRuntime(owner);
        runtime.iterate(0);
        clearInvocations(owner);
        when(owner.tick()).thenReturn(false);
        assertEquals(0, runtime.iterate(100_000_000).services());
        verify(owner, times(1)).tick();
        verify(owner, never()).draw(anyBoolean());
        verify(owner, never()).swap();
        assertEquals(0, runtime.statistics().pendingTicks());
    }

    @Test void transitionWorkIsExcludedFromTheNewBoundaryWallAnchor() {
        var owner = mock(LiveFrameRuntime.Owner.class);
        var boundary = new java.util.concurrent.atomic.AtomicReference<>("old");
        when(owner.simulationHz()).thenReturn(60);
        when(owner.presentationHz()).thenReturn(30);
        when(owner.focused()).thenReturn(true);
        when(owner.boundary()).thenAnswer(invocation -> boundary.get());
        when(owner.draw(anyBoolean())).thenReturn(true);
        when(owner.tick()).thenAnswer(invocation -> { boundary.set("new"); return true; });
        var runtime = new LiveFrameRuntime(owner);
        runtime.iterate(0);

        var transition = runtime.iterate(100_000_000L);
        assertEquals(1, transition.services());
        assertEquals(0, runtime.statistics().pendingTicks());
        long completed = 1_100_000_000L; // one second spent in the transitioning service
        long anchor = transition.wallAnchor(100_000_000L, completed);
        assertEquals(completed, anchor);
        var next = runtime.iterate(completed + 16_666_667L - anchor);
        assertEquals(1, next.services());
        assertEquals(0, runtime.statistics().pendingTicks());
        assertEquals(200L, next.wallAnchor(200L, 300L),
                "ordinary work and swap time remain part of the following interval");
    }

    @Test void focusLossAndRegainInOnePollStillInvalidateOldWallDebt() throws Exception {
        var runtime = open(60, 144);
        runtime.iterate(0);
        engine.observeWindowActivation();
        assertTrue(Engine.applyWindowActivation(false, input, engine.getGameLoop()::pause, engine.getGameLoop()::resume));
        engine.observeWindowActivation();
        assertFalse(Engine.applyWindowActivation(true, input, engine.getGameLoop()::pause, engine.getGameLoop()::resume));
        assertEquals(0, runtime.iterate(10 * SECOND).services(), "the final focused boolean alone misses this discontinuity");
        assertEquals(1, runtime.iterate(16_666_667).services());
    }

    @Test void deviceOnlyServiceCannotRetireAudioCommandsOrProduceHistory() throws Exception {
        open(60, 144);
        long frame = audio.commandTimeline().currentFrame();
        for (int poll = 0; poll < 20; poll++) com.openggf.audio.AudioManagerInternalAccess.serviceDevice(audio);
        assertEquals(frame, audio.commandTimeline().currentFrame());
        assertEquals(0, AudioManagerTestDiagnostics.shadowParitySnapshot(audio).presentedFrames());
    }

    @Test void focusAndClockDiscontinuitiesRebaseWithoutForwardAdmission() throws Exception {
        var runtime = open(50, 120);
        runtime.iterate(0);
        field("paused", true);
        assertEquals(0, runtime.iterate(20 * SECOND).services());
        field("paused", false);
        assertEquals(0, runtime.iterate(20 * SECOND).services());
        assertEquals(1, runtime.iterate(20_000_000).services());
        assertEquals(0, runtime.iterate(-1).services());
        assertEquals(1, runtime.iterate(20_000_000).services());
        assertEquals(2, screen.samples.size());
    }

    @Test void modalHostPassAcknowledgesWithoutAWorldOrPictureAndKeepsRegionAudioSilent() throws Exception {
        var runtime = open(60, 30);
        runtime.iterate(0);
        var picker = new com.openggf.graphics.shaderlib.DisplayShaderPickerController(
                new com.openggf.graphics.shaderlib.DisplayShaderSelectionModel(List.of(
                        com.openggf.graphics.shaderlib.DisplayShaderPresetRef.OFF)), GLFW_KEY_F6);
        // Constructor shape deliberately comes from the same production fixture as Engine advance.
        field("displayShaderPickerController", picker);
        input.handleKeyEvent(GLFW_KEY_F6, GLFW_PRESS);
        runtime.iterate(16_666_667);
        assertTrue(screen.samples.isEmpty());
        assertEquals(1, AudioManagerTestDiagnostics.shadowParitySnapshot(audio).silentFrames());
        assertEquals(1, pictures, "the initial picture only: host UI service does not require a swap");
    }

    @ParameterizedTest @CsvSource({"50,30,false", "50,144,true", "60,30,true", "60,144,false"})
    void actualScheduledCaptureKeepsOutputTimestampsPicturesAndPcmIndependentOfSwaps(int hz, int fps, boolean deferred)
            throws Exception {
        open(hz, fps);
        int[] sourceSample = {0};
        audio.setScenePcmSource((target, frames) -> {
            for (int i = 0; i < frames; i++) {
                short value = (short) (sourceSample[0]++ % 30_000);
                target[2 * i] = value; target[2 * i + 1] = (short) -value;
            }
        });
        var encoder = new CollectingEncoder();
        var finalizer = java.util.concurrent.Executors.newSingleThreadExecutor();
        var viewport = new com.openggf.capture.CaptureViewport(0, 0, 1, 1);
        var controller = new com.openggf.capture.LiveCaptureController(new com.openggf.capture.LiveCaptureController.Dependencies(
                audio::beginLiveCaptureAudio, audio::outputSampleRate,
                ignored -> new com.openggf.capture.VideoFrameGrabber() {
                    final java.util.ArrayDeque<byte[]> pending = new java.util.ArrayDeque<>();
                    public int width() { return 1; }
                    public int height() { return 1; }
                    public boolean deferredReadback() { return deferred; }
                    public byte[] grab() { return java.nio.ByteBuffer.allocate(4).putInt(screen.samples.size()).array(); }
                    public void beginReadback() { pending.add(grab()); }
                    public void grabInto(byte[] target) {
                        byte[] pixels = deferred ? pending.remove() : grab();
                        System.arraycopy(pixels, 0, target, 0, target.length);
                    }
                }, (ignored, rate) -> new com.openggf.capture.CaptureRecorder(encoder,
                        com.openggf.capture.BackpressurePolicy.BLOCK, 2, java.nio.file.Path.of("target"), "live-scheduled", "test"),
                finalizer, java.time.Duration.ofSeconds(5)));
        try (controller) {
            field("liveCaptureController", controller);
            field("liveCaptureFrameRate", hz);
            var presentation = new com.openggf.capture.LiveCapturePresentationCoordinator(controller);
            var runtime = engine.createLiveFrameRuntime(capture -> presentation.present(viewport, capture, () -> { }, () -> { }),
                    () -> swaps++);
            controller.start(viewport, hz);
            runtime.iterate(0);
            long previous = 0;
            for (int picture = 1; picture <= fps; picture++) {
                long now = (picture * SECOND + fps - 1) / fps;
                runtime.iterate(now - previous); previous = now;
            }
            controller.requestStop(com.openggf.capture.LiveCaptureController.StopReason.USER);
            finalizer.submit(() -> { }).get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(com.openggf.capture.LiveCaptureController.State.INACTIVE, controller.state());
            assertNull(controller.lastFailure());
            assertEquals(hz, encoder.fps);
            assertEquals(hz, encoder.frames.size(), "one actual final-viewport picture for every output interval");
            int pcmIndex = 0;
            for (int index = 0; index < hz; index++) {
                var frame = encoder.frames.get(index);
                assertEquals(index, frame.frameIndex(), "CFR timestamp is ordinal / region output Hz");
                assertEquals(index + 1, java.nio.ByteBuffer.wrap(frame.rgba()).getInt(), "capture's current semantic picture");
                for (int sample = 0; sample < frame.sampleCount(); sample++) {
                    short value = (short) (pcmIndex++ % 30_000);
                    assertEquals(value, frame.pcm()[2 * sample]);
                    assertEquals((short) -value, frame.pcm()[2 * sample + 1]);
                }
            }
            assertEquals(48_000, pcmIndex, "video end time and PCM duration both span exactly one second");
            assertEquals(48_000, sourceSample[0], "capture/presentation repeats cannot synthesize audio");
            assertEquals(fps + 1, swaps, "capture draws cannot silently raise the window swap rate");
        }
    }

    private static final class CollectingEncoder implements com.openggf.capture.CaptureEncoder {
        final java.util.List<com.openggf.capture.CapturedFrame> frames = new java.util.ArrayList<>();
        int fps;
        public void open(java.nio.file.Path output, int width, int height, int fps, int sampleRate) { this.fps = fps; }
        public void encode(com.openggf.capture.CapturedFrame frame) {
            frames.add(new com.openggf.capture.CapturedFrame(frame.rgba(), frame.width(), frame.height(),
                    java.util.Arrays.copyOf(frame.pcm(), 2 * frame.sampleCount()), frame.sampleCount(), frame.frameIndex()));
        }
        public java.nio.file.Path finish() { return java.nio.file.Path.of("target", "scheduled-test.mkv"); }
        public void abort() { }
    }

    private void field(String name, Object value) throws Exception {
        Field f = Engine.class.getDeclaredField(name); f.setAccessible(true); f.set(engine, value);
    }
    private static LogicalInputSnapshot sample(int action) {
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(0, 0, action, action, false, false), PlayerInputState.neutral());
    }
    private static final class ObservedScreen extends LegalDisclaimerScreen {
        ObservedScreen() { super(new FadeManager()); }
        final List<LogicalInputSnapshot> samples = new ArrayList<>();
        final List<Long> states = new ArrayList<>();
        long state;
        Runnable after = () -> { };
        @Override public void update(InputHandler input) {
            var sample = input.logical(); samples.add(sample);
            state = state * 17 + sample.player1().actionHeldMask(); states.add(state);
            after.run();
        }
        @Override public boolean isDismissed() { return false; }
    }
}

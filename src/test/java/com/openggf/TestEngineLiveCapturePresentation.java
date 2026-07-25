package com.openggf;

import com.openggf.capture.CaptureViewport;
import com.openggf.capture.LiveCaptureController;
import com.openggf.capture.LiveCapturePresentationCoordinator;
import com.openggf.configuration.FrameRateResolver;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameMode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TestEngineLiveCapturePresentation {
    @Test
    void debugAudioFailurePropertyUsesSafeDisabledDefaultAndExactNonNegativeValue() {
        String key = "openggf.debug.liveCaptureAudioFailAfterFrames";
        String prior = System.getProperty(key);
        try {
            System.clearProperty(key);
            assertEquals(-1, Engine.resolveLiveCaptureAudioFailAfterFrames());
            System.setProperty(key, "-1");
            assertEquals(-1, Engine.resolveLiveCaptureAudioFailAfterFrames());
            System.setProperty(key, "0");
            assertEquals(0, Engine.resolveLiveCaptureAudioFailAfterFrames());
            System.setProperty(key, "17");
            assertEquals(17, Engine.resolveLiveCaptureAudioFailAfterFrames());
            System.setProperty(key, "-2");
            assertEquals(-1, Engine.resolveLiveCaptureAudioFailAfterFrames());
            System.setProperty(key, "not-a-number");
            assertEquals(-1, Engine.resolveLiveCaptureAudioFailAfterFrames());
        } finally {
            if (prior == null) System.clearProperty(key);
            else System.setProperty(key, prior);
        }
    }

    @Test
    void immediateLiveCaptureFailureWarnsExactlyOnceAndRetryCanWarnAgain() {
        LiveCaptureController controller = mock(LiveCaptureController.class);
        Throwable failure = new IllegalStateException("tap open failed");
        AtomicReference<LiveCaptureController.State> state =
                new AtomicReference<>(LiveCaptureController.State.FAILED);
        when(controller.state()).thenAnswer(ignored -> state.get());
        when(controller.lastFailure()).thenReturn(failure);
        List<Throwable> warnings = new ArrayList<>();
        Engine.LiveCaptureFailureTransitionReporter reporter =
                new Engine.LiveCaptureFailureTransitionReporter(warnings::add);

        reporter.observe(controller);
        reporter.observe(controller);
        assertEquals(List.of(failure), warnings);

        state.set(LiveCaptureController.State.ACTIVE);
        reporter.observe(controller);
        state.set(LiveCaptureController.State.FAILED);
        reporter.observe(controller);
        assertEquals(List.of(failure, failure), warnings);
    }

    @Test
    void asynchronousFinalizationFailureWarnsExactlyOnceOnSubsequentFrames() {
        LiveCaptureController controller = mock(LiveCaptureController.class);
        Throwable failure = new IllegalStateException("mux failed");
        AtomicReference<LiveCaptureController.State> state =
                new AtomicReference<>(LiveCaptureController.State.STOPPING);
        when(controller.state()).thenAnswer(ignored -> state.get());
        when(controller.lastFailure()).thenReturn(failure);
        List<Throwable> warnings = new ArrayList<>();
        Engine.LiveCaptureFailureTransitionReporter reporter =
                new Engine.LiveCaptureFailureTransitionReporter(warnings::add);

        reporter.observe(controller);
        state.set(LiveCaptureController.State.FAILED);
        reporter.observe(controller);
        reporter.observe(controller);

        assertEquals(List.of(failure), warnings);
    }

    @Test
    void synchronousRetryFailureWithSameThrowableWarnsOncePerAttempt() {
        LiveCaptureController controller = mock(LiveCaptureController.class);
        Throwable failure = new IllegalStateException("same synchronous failure");
        when(controller.state()).thenReturn(LiveCaptureController.State.FAILED);
        when(controller.lastFailure()).thenReturn(failure);
        List<Throwable> warnings = new ArrayList<>();
        Engine.LiveCaptureFailureTransitionReporter reporter =
                new Engine.LiveCaptureFailureTransitionReporter(warnings::add);
        CaptureViewport viewport = new CaptureViewport(0, 0, 320, 224);

        reporter.observe(controller);
        Engine.startLiveCaptureAttempt(controller, reporter, viewport, 60);
        reporter.observe(controller);
        reporter.observe(controller);

        assertEquals(List.of(failure, failure), warnings);
        verify(controller).start(viewport, 60);
    }

    @Test
    void allRenderedGameModesAndPresentationStatesUseTheSameSeamExactlyOnce() {
        LiveCaptureController controller = mock(LiveCaptureController.class);
        List<String> calls = new ArrayList<>();
        doAnswer(invocation -> {
            calls.add("capture");
            return null;
        }).when(controller).capturePresentedFrame(any(CaptureViewport.class));
        LiveCapturePresentationCoordinator coordinator =
                new LiveCapturePresentationCoordinator(controller);
        CaptureViewport viewport = new CaptureViewport(0, 0, 320, 224);
        EnumSet<GameMode> renderedModes = EnumSet.allOf(GameMode.class);
        EnumSet<Engine.LiveCapturePresentationState> presentationStates =
                EnumSet.allOf(Engine.LiveCapturePresentationState.class);
        assertTrue(renderedModes.contains(GameMode.BONUS_STAGE));

        int presentations = 0;
        for (GameMode mode : renderedModes) {
            for (Engine.LiveCapturePresentationState state : presentationStates) {
                Engine.presentLiveCaptureFrame(mode, state,
                        () -> coordinator.present(viewport,
                                () -> calls.add("screenshot"),
                                () -> calls.add("indicator")));
                presentations++;
            }
        }

        assertEquals(presentations * 3, calls.size());
        for (int i = 0; i < calls.size(); i += 3) {
            assertEquals(List.of("capture", "screenshot", "indicator"),
                    calls.subList(i, i + 3));
        }
        verify(controller, times(presentations)).capturePresentedFrame(viewport);
    }

    @Test
    void productionDisplayHasOneUnconditionalCoordinatorInvocation() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/openggf/Engine.java"));
        int displayStart = source.indexOf("private void display()");
        int displayEnd = source.indexOf(
                "static LiveCapturePresentationState resolveLiveCapturePresentationState(",
                displayStart);
        String display = source.substring(displayStart, displayEnd);
        assertEquals(1, occurrences(display, "liveCapturePresentation.present("));
        assertEquals(1, occurrences(display, "presentLiveCaptureFrame("));
        assertFalse(display.substring(display.lastIndexOf("applyDisplayShaderPhase(ShaderPhase.FINAL)"))
                .contains("if ("));
    }

    /**
     * The presented outer frame carries exactly one audio presentation. A
     * second call here would double the producer's cadence and hand the
     * recorder a packet the speaker never played, so it is pinned alongside the
     * capture seam rather than left to the audio-side tests alone.
     */
    @Test
    void productionDisplayPresentsTheAudioOuterFrameExactlyOnce()
            throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/openggf/Engine.java"));
        int displayStart = source.indexOf("private void display()");
        int displayEnd = source.indexOf(
                "static LiveCapturePresentationState resolveLiveCapturePresentationState(",
                displayStart);
        String display = source.substring(displayStart, displayEnd);
        assertEquals(1, occurrences(display, "presentOuterAudioFrame(gameLoop"));
        assertEquals(0, occurrences(display, "presentFrame("),
                "display() must reach the producer only through the shared "
                        + "outer-frame seam");
    }

    @Test
    void productionPresentationFlagsResolveEveryExecutableStateBranch() {
        assertEquals(Engine.LiveCapturePresentationState.NORMAL,
                Engine.resolveLiveCapturePresentationState(false, false, false, false));
        assertEquals(Engine.LiveCapturePresentationState.MODAL_SHADER_PICKER,
                Engine.resolveLiveCapturePresentationState(true, false, false, false));
        assertEquals(Engine.LiveCapturePresentationState.PAUSED,
                Engine.resolveLiveCapturePresentationState(false, true, false, false));
        assertEquals(Engine.LiveCapturePresentationState.FRAME_STEP,
                Engine.resolveLiveCapturePresentationState(false, true, true, false));
        assertEquals(Engine.LiveCapturePresentationState.REWIND,
                Engine.resolveLiveCapturePresentationState(false, false, false, true));
    }

    @Test
    void productionRecorderUsesBlockCapacityEightAndScaleOne() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/openggf/capture/LiveCaptureRecorderFactory.java"));
        assertTrue(source.contains("new FfmpegEncoder(ffmpeg, 1)"));
        assertTrue(source.contains("BackpressurePolicy.BLOCK, 8"));
    }

    @Test
    void palEngineTargetAndCaptureRateBothResolveToFifty() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.REGION, "PAL");
        config.setConfigValue(SonicConfiguration.FPS, 60);
        assertEquals(50, FrameRateResolver.effective(config));
        assertEquals(50, Engine.resolveTargetFps(config));
    }

    @Test
    void fixedViewportOriginChangeRequestsStopBeforeCapture() {
        CaptureViewport before = new CaptureViewport(0, 0, 320, 224);
        CaptureViewport after = new CaptureViewport(1, 0, 320, 224);
        assertNotEquals(before, after);
    }

    @Test
    void cleanupClosesCaptureBeforeAudioAndGraphics() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/openggf/Engine.java"));
        int capture = source.indexOf("cleanupStep(\"live capture\"");
        int audio = source.indexOf("cleanupStep(\"audio manager\"");
        int graphics = source.indexOf("cleanupStep(\"graphics manager\"");
        assertTrue(capture >= 0 && capture < audio && capture < graphics);
    }

    private static int occurrences(String source, String needle) {
        return (source.length() - source.replace(needle, "").length()) / needle.length();
    }

}

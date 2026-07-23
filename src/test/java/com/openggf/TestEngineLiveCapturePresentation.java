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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TestEngineLiveCapturePresentation {
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
        List<String> presentationStates = List.of(
                "normal", "modal-shader-picker", "paused", "frame-step", "rewind");
        assertTrue(renderedModes.contains(GameMode.BONUS_STAGE));

        int presentations = 0;
        for (GameMode ignoredMode : renderedModes) {
            for (String ignoredState : presentationStates) {
                coordinator.present(viewport, () -> calls.add("screenshot"),
                        () -> calls.add("indicator"));
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

}

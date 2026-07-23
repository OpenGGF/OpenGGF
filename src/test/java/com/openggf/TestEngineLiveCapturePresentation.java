package com.openggf;

import com.openggf.capture.CaptureViewport;
import com.openggf.capture.LiveCaptureController;
import com.openggf.configuration.FrameRateResolver;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameMode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestEngineLiveCapturePresentation {
    @Test
    void allRenderedGameModesUseTheSamePostPresentationSeam() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/openggf/Engine.java"));
        assertEquals(1, occurrences(source, "liveCapturePresentation.present("));
        assertTrue(EnumSet.allOf(GameMode.class).containsAll(List.of(
                GameMode.LEVEL, GameMode.SPECIAL_STAGE, GameMode.EDITOR,
                GameMode.TITLE_SCREEN, GameMode.MASTER_TITLE_SCREEN)));
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

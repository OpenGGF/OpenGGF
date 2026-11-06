package com.openggf.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrameRateResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void autoDefaultResolvesLiveAndDisplayFreeRates() {
        var config = SonicConfigurationService.createStandalone(tempDir);
        assertEquals("AUTO", config.getString(SonicConfiguration.FPS));
        assertEquals(60, config.getInt(SonicConfiguration.FPS));
        assertEquals(60, FrameRateResolver.presentation(config));
        assertEquals(144, FrameRateResolver.presentation(config, 144));
        config.setConfigValue(SonicConfiguration.REGION, "PAL");
        assertEquals(50, FrameRateResolver.presentation(config, 0));
        config.setSessionOverride(SonicConfiguration.FPS, 60);
        assertEquals(60, FrameRateResolver.presentation(config, 240));
    }

    @Test
    void autoYamlAndCustomNumberRoundTrip() throws Exception {
        Files.writeString(tempDir.resolve("config.yaml"), "display:\n  fps: aUtO\n");
        var config = SonicConfigurationService.createStandalone(tempDir);
        assertEquals(165, FrameRateResolver.presentation(config, 165));
        var draft = new EngineSettingsDraft(config);
        draft.set(SonicConfiguration.FPS, "137");
        draft.apply();
        config = SonicConfigurationService.createStandalone(tempDir);
        assertEquals(137, FrameRateResolver.presentation(config, 240));
        draft = new EngineSettingsDraft(config);
        draft.set(SonicConfiguration.FPS, "auto");
        draft.apply();
        assertEquals(240, FrameRateResolver.presentation(SonicConfigurationService.createStandalone(tempDir), 240));
    }

    @Test
    void malformedValueRetainsNumericFallback() {
        var config = SonicConfigurationService.createStandalone(tempDir);
        config.setConfigValue(SonicConfiguration.FPS, "garbage");
        assertEquals(60, FrameRateResolver.presentation(config, 240));
    }

    @Test
    void palResolvesToFiftyEvenWhenConfiguredFpsIsSixty() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
        config.setConfigValue(SonicConfiguration.REGION, "PAL");
        config.setConfigValue(SonicConfiguration.FPS, 60);

        assertEquals(50, FrameRateResolver.effective(config));
    }

    @Test
    void ntscSeparatesRegionRateFromConfiguredFps() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
        config.setConfigValue(SonicConfiguration.REGION, "NTSC");
        config.setConfigValue(SonicConfiguration.FPS, 120);

        assertEquals(60, FrameRateResolver.effective(config));
        assertEquals(120, FrameRateResolver.presentation(config));
    }

    @Test
    void zeroLoadedFpsIsSanitizedBeforeResolution() throws Exception {
        Files.writeString(tempDir.resolve("config.yaml"), """
                display:
                  fps: 0
                audio:
                  region: NTSC
                """);
        SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);

        assertEquals(1, config.getInt(SonicConfiguration.FPS));
        assertEquals(60, FrameRateResolver.effective(config));
        assertEquals(1, FrameRateResolver.presentation(config));
    }

    @Test
    void negativeLoadedFpsIsSanitizedBeforeResolution() throws Exception {
        Files.writeString(tempDir.resolve("config.yaml"), """
                display:
                  fps: -60
                audio:
                  region: NTSC
                """);
        SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);

        assertEquals(1, config.getInt(SonicConfiguration.FPS));
        assertEquals(60, FrameRateResolver.effective(config));
        assertEquals(1, FrameRateResolver.presentation(config));
    }

    @Test
    void excessiveLoadedFpsIsBoundedBeforeSchedulerResolution() throws Exception {
        Files.writeString(tempDir.resolve("config.yaml"), "display:\n  fps: 1000000001\n");
        SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
        assertEquals(1_000_000_000, config.getInt(SonicConfiguration.FPS));
        assertEquals(1_000_000_000, FrameRateResolver.presentation(config));
    }
}

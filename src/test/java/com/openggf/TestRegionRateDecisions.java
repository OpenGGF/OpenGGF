package com.openggf;

import com.openggf.configuration.FrameRateResolver;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestRegionRateDecisions {
    @AfterEach void close() { TestEnvironment.resetEngineOnly(); }

    @ParameterizedTest @CsvSource({"NTSC,30,60", "NTSC,144,60", "PAL,30,50", "PAL,144,50"})
    void displayLimitCannotChangeRegionServiceRate(String region, int fps, int hz) {
        TestEnvironment.resetEngineOnly();
        var config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.REGION, region);
        config.setConfigValue(SonicConfiguration.FPS, fps);
        assertEquals(hz, FrameRateResolver.effective(config), "audio/semantic rate is region owned");
        assertEquals(fps, Engine.resolveTargetFps(config), "window pictures have a separate limit");
    }
}

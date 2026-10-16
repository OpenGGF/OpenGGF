package com.openggf.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestConfigEnumValidation {

    @TempDir
    Path tempDir;

    @Test
    void invalidDisplayPresentationValuesFallBackToSafeDefaults() throws Exception {
        Files.writeString(tempDir.resolve("config.yaml"),
                "display:\n  windowMode: UNKNOWN\n  scaling: STRETCH\n");
        SonicConfigurationService svc = SonicConfigurationService.createStandalone(tempDir);
        assertEquals("WINDOWED", svc.getString(SonicConfiguration.DISPLAY_WINDOW_MODE));
        assertEquals("INTEGER", svc.getString(SonicConfiguration.DISPLAY_SCALING));
    }

    @Test
    void illegalEnumValueFallsBackToDefault() throws Exception {
        Files.writeString(tempDir.resolve("config.yaml"), "audio:\n  region: KLINGON\n");
        SonicConfigurationService svc = SonicConfigurationService.createStandalone(tempDir);
        assertEquals("NTSC", svc.getString(SonicConfiguration.REGION));
    }

    @Test
    void legalEnumValueIsKept() throws Exception {
        Files.writeString(tempDir.resolve("config.yaml"), "audio:\n  region: PAL\n");
        SonicConfigurationService svc = SonicConfigurationService.createStandalone(tempDir);
        assertEquals("PAL", svc.getString(SonicConfiguration.REGION));
    }
}

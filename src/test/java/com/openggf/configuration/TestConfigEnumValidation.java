package com.openggf.configuration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestConfigEnumValidation {

    @TempDir
    Path tempDir;
    private String originalUserDir;

    @BeforeEach
    void setup() {
        originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
    }

    @AfterEach
    void teardown() {
        if (originalUserDir != null) {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void illegalEnumValueFallsBackToDefault() throws Exception {
        Files.writeString(tempDir.resolve("config.yaml"), "audio:\n  region: KLINGON\n");
        SonicConfigurationService svc = SonicConfigurationService.createStandalone();
        assertEquals("NTSC", svc.getString(SonicConfiguration.REGION));
    }

    @Test
    void legalEnumValueIsKept() throws Exception {
        Files.writeString(tempDir.resolve("config.yaml"), "audio:\n  region: PAL\n");
        SonicConfigurationService svc = SonicConfigurationService.createStandalone();
        assertEquals("PAL", svc.getString(SonicConfiguration.REGION));
    }
}

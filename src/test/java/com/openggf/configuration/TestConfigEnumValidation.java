package com.openggf.configuration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestConfigEnumValidation {

    private final File yaml = new File("config.yaml");

    @AfterEach
    void cleanup() {
        yaml.delete();
    }

    @Test
    void illegalEnumValueFallsBackToDefault() throws Exception {
        Files.writeString(yaml.toPath(), "audio:\n  region: KLINGON\n");
        SonicConfigurationService svc = SonicConfigurationService.createStandalone();
        // REGION default is NTSC (see applyDefaults); the bogus value is rejected.
        assertEquals("NTSC", svc.getString(SonicConfiguration.REGION));
    }

    @Test
    void legalEnumValueIsKept() throws Exception {
        Files.writeString(yaml.toPath(), "audio:\n  region: PAL\n");
        SonicConfigurationService svc = SonicConfigurationService.createStandalone();
        assertEquals("PAL", svc.getString(SonicConfiguration.REGION));
    }
}

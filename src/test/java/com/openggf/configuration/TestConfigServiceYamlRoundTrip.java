package com.openggf.configuration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestConfigServiceYamlRoundTrip {

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
    void saveWritesGroupedYaml() throws Exception {
        Path yaml = tempDir.resolve("config.yaml");
        SonicConfigurationService svc = SonicConfigurationService.createStandalone();
        svc.saveConfig();
        assertTrue(Files.exists(yaml), "saveConfig must write config.yaml");
        String text = Files.readString(yaml);
        assertTrue(text.contains("display:"), text);
        assertTrue(text.contains("debug:"), text);
        assertFalse(text.contains("SCREEN_WIDTH_PIXELS"), "derived keys must not be persisted");
    }

    @Test
    void loadReadsBackWrittenValues() throws Exception {
        SonicConfigurationService svc = SonicConfigurationService.createStandalone();
        svc.setConfigValue(SonicConfiguration.AUDIO_ENABLED, false);
        svc.saveConfig();

        SonicConfigurationService reloaded = SonicConfigurationService.createStandalone();
        assertFalse(reloaded.getBoolean(SonicConfiguration.AUDIO_ENABLED));
    }
}

package com.openggf.configuration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class TestConfigServiceYamlRoundTrip {

    private final File yaml = new File("config.yaml");

    @AfterEach
    void cleanup() {
        yaml.delete();
    }

    @Test
    void saveWritesGroupedYaml() throws Exception {
        SonicConfigurationService svc = SonicConfigurationService.createStandalone();
        svc.saveConfig();
        assertTrue(yaml.exists(), "saveConfig must write config.yaml");
        String text = Files.readString(yaml.toPath());
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

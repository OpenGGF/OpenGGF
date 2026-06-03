package com.openggf.configuration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class TestLegacyConfigMigration {

    private final File json = new File("config.json");
    private final File yaml = new File("config.yaml");
    private final File bak = new File("config.json.bak");

    @AfterEach
    void cleanup() {
        json.delete();
        yaml.delete();
        bak.delete();
    }

    @Test
    void legacyFlatJsonMigratesToYamlAndBacksUp() throws Exception {
        Files.writeString(json.toPath(), "{ \"AUDIO_ENABLED\": false, \"FPS\": 50 }");

        SonicConfigurationService svc = SonicConfigurationService.createStandalone();

        assertFalse(svc.getBoolean(SonicConfiguration.AUDIO_ENABLED), "migrated value preserved");
        assertEquals(50, svc.getInt(SonicConfiguration.FPS));
        assertTrue(yaml.exists(), "config.yaml written");
        assertTrue(bak.exists(), "old config.json renamed to .bak");
        assertFalse(json.exists(), "original config.json removed");
        String text = Files.readString(yaml.toPath());
        assertTrue(text.contains("audio:"), text);
    }
}

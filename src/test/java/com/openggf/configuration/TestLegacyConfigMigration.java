package com.openggf.configuration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestLegacyConfigMigration {

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
    void legacyFlatJsonMigratesToYamlAndBacksUp() throws Exception {
        Path json = tempDir.resolve("config.json");
        Path yaml = tempDir.resolve("config.yaml");
        Path bak = tempDir.resolve("config.json.bak");
        Files.writeString(json, "{ \"AUDIO_ENABLED\": false, \"FPS\": 50 }");

        SonicConfigurationService svc = SonicConfigurationService.createStandalone();

        assertFalse(svc.getBoolean(SonicConfiguration.AUDIO_ENABLED), "migrated value preserved");
        assertEquals(50, svc.getInt(SonicConfiguration.FPS));
        assertTrue(Files.exists(yaml), "config.yaml written");
        assertTrue(Files.exists(bak), "old config.json renamed to .bak");
        assertFalse(Files.exists(json), "original config.json removed");
        String text = Files.readString(yaml);
        assertTrue(text.contains("audio:"), text);
    }
}

package com.openggf.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestLegacyConfigMigration {

    @TempDir
    Path tempDir;

    @Test
    void legacyFlatJsonMigratesToYamlAndBacksUp() throws Exception {
        Path json = tempDir.resolve("config.json");
        Path yaml = tempDir.resolve("config.yaml");
        Path bak = tempDir.resolve("config.json.bak");
        Files.writeString(json, "{ \"AUDIO_ENABLED\": false, \"FPS\": 50 }");

        SonicConfigurationService svc = SonicConfigurationService.createStandalone(tempDir);

        assertFalse(svc.getBoolean(SonicConfiguration.AUDIO_ENABLED), "migrated value preserved");
        assertEquals(50, svc.getInt(SonicConfiguration.FPS));
        assertTrue(Files.exists(yaml), "config.yaml written");
        assertTrue(Files.exists(bak), "old config.json renamed to .bak");
        assertFalse(Files.exists(json), "original config.json removed");
        String text = Files.readString(yaml);
        assertTrue(text.contains("audio:"), text);
    }

    @Test
    void legacyMigrationPreservesExistingBackupWithUniqueName() throws Exception {
        Path json = tempDir.resolve("config.json");
        Path yaml = tempDir.resolve("config.yaml");
        Path bak = tempDir.resolve("config.json.bak");
        Path secondBak = tempDir.resolve("config.json.bak.1");
        Files.writeString(json, "{ \"AUDIO_ENABLED\": false, \"FPS\": 50 }");
        Files.writeString(bak, "previous backup");

        SonicConfigurationService svc = SonicConfigurationService.createStandalone(tempDir);

        assertFalse(svc.getBoolean(SonicConfiguration.AUDIO_ENABLED), "migrated value preserved");
        assertTrue(Files.exists(yaml), "config.yaml written");
        assertEquals("previous backup", Files.readString(bak),
                "existing backup must not be overwritten");
        assertTrue(Files.exists(secondBak), "legacy config should move to a unique backup path");
        assertFalse(Files.exists(json), "original config.json removed");
    }
}

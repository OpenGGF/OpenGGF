package com.openggf.configuration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonicConfigurationFileBootstrap {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    @TempDir
    Path tempDir;

    @Test
    void getInstance_backfillsMissingDefaultsIntoExistingFile() throws IOException {
        String originalUserDir = System.getProperty("user.dir");
        Path configPath = tempDir.resolve("config.json");

        try {
            Map<String, Object> sparseConfig = new java.util.HashMap<>();
            sparseConfig.put(SonicConfiguration.UP.name(), "W");
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), sparseConfig);

            System.setProperty("user.dir", tempDir.toString());
            SonicConfigurationService.resetStaticInstance();

            SonicConfigurationService service = SonicConfigurationService.getInstance();

            Map<String, Object> persisted = OBJECT_MAPPER.readValue(configPath.toFile(), MAP_TYPE);
            assertEquals("W", persisted.get(SonicConfiguration.UP.name()),
                    "Existing value should be preserved");
            assertEquals(640, ((Number) persisted.get(SonicConfiguration.SCREEN_WIDTH.name())).intValue(),
                    "Missing default should be backfilled into the file");
            assertEquals("DOWN", persisted.get(SonicConfiguration.DOWN.name()),
                    "Missing key binding default should be backfilled into the file");
            assertTrue(persisted.containsKey(SonicConfiguration.DEFAULT_ROM.name()),
                    "Missing string default should be backfilled into the file");
            assertEquals(service.getInt(SonicConfiguration.SCREEN_WIDTH),
                    ((Number) persisted.get(SonicConfiguration.SCREEN_WIDTH.name())).intValue());
        } finally {
            if (originalUserDir != null) {
                System.setProperty("user.dir", originalUserDir);
            }
            SonicConfigurationService.resetStaticInstance();
        }
    }

    @Test
    void ensureConfigFileExists_createsDefaultConfigWhenMissing() throws IOException {
        String originalUserDir = System.getProperty("user.dir");
        Path configPath = tempDir.resolve("config.json");

        try {
            System.setProperty("user.dir", tempDir.toString());
            SonicConfigurationService.resetStaticInstance();

            SonicConfigurationService service = SonicConfigurationService.getInstance();

            assertFalse(Files.exists(configPath));

            service.ensureConfigFileExists();

            assertTrue(Files.exists(configPath), "First startup should materialize config.json");

            Map<String, Object> savedConfig = OBJECT_MAPPER.readValue(configPath.toFile(), MAP_TYPE);
            assertEquals(640, ((Number) savedConfig.get(SonicConfiguration.SCREEN_WIDTH.name())).intValue());
            assertEquals(320, ((Number) savedConfig.get(SonicConfiguration.SCREEN_WIDTH_PIXELS.name())).intValue());
            assertEquals(service.getString(SonicConfiguration.DEFAULT_ROM),
                    savedConfig.get(SonicConfiguration.DEFAULT_ROM.name()));
            assertEquals("Q", savedConfig.get(SonicConfiguration.FRAME_STEP_KEY.name()));
            assertEquals("", savedConfig.get(SonicConfiguration.PLAYBACK_MOVIE_PATH.name()));
            assertEquals(Boolean.FALSE, savedConfig.get(SonicConfiguration.LIVE_REWIND_ENABLED.name()));
            assertEquals("R", savedConfig.get(SonicConfiguration.LIVE_REWIND_KEY.name()));
            assertEquals(Boolean.TRUE, savedConfig.get(SonicConfiguration.TITLE_SCREEN_ON_STARTUP.name()));
            assertEquals(Boolean.FALSE, savedConfig.get(SonicConfiguration.LEVEL_SELECT_ON_STARTUP.name()));
            assertEquals(Boolean.TRUE, savedConfig.get(SonicConfiguration.MASTER_TITLE_SCREEN_ON_STARTUP.name()));
            assertTrue(savedConfig.containsKey(SonicConfiguration.DEBUG_VIEW_ENABLED.name()));
            assertEquals(Boolean.FALSE, savedConfig.get(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED.name()));
            assertEquals(Boolean.TRUE, savedConfig.get(SonicConfiguration.DISCORD_RICH_PRESENCE_SHOW_TIMER.name()));
            assertEquals(Boolean.TRUE, savedConfig.get(SonicConfiguration.DISCORD_RICH_PRESENCE_SHOW_ZONE.name()));
        } finally {
            if (originalUserDir != null) {
                System.setProperty("user.dir", originalUserDir);
            }
            SonicConfigurationService.resetStaticInstance();
        }
    }
}

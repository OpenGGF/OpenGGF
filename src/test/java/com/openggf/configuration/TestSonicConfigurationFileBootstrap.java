package com.openggf.configuration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonicConfigurationFileBootstrap {
    private static final YAMLMapper YAML_MAPPER = new YAMLMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    @TempDir
    Path tempDir;

    /**
     * Reads a saved config.yaml (grouped/nested) back to a flat map using the same
     * flattening logic the service uses on load.
     */
    private static Map<String, Object> readFlatYaml(Path path) throws IOException {
        Map<String, Object> nested = YAML_MAPPER.readValue(path.toFile(), MAP_TYPE);
        return ConfigFlattener.flatten(nested).flat();
    }

    @Test
    void getInstance_backfillsMissingDefaultsIntoExistingFile() throws IOException {
        Path configPath = tempDir.resolve("config.yaml");

        // Write a minimal nested YAML: UP lives at input.player1.up
        Map<String, Object> player1 = new LinkedHashMap<>();
        player1.put("up", "W");
        Map<String, Object> inputSection = new LinkedHashMap<>();
        inputSection.put("player1", player1);
        Map<String, Object> sparseConfig = new LinkedHashMap<>();
        sparseConfig.put("input", inputSection);
        YAML_MAPPER.writeValue(configPath.toFile(), sparseConfig);

        SonicConfigurationService service = SonicConfigurationService.createStandalone(tempDir);

        Map<String, Object> persisted = readFlatYaml(configPath);
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
    }

    @Test
    void ensureConfigFileExists_createsDefaultConfigWhenMissing() throws IOException {
        Path configPath = tempDir.resolve("config.yaml");

        SonicConfigurationService service = SonicConfigurationService.createStandalone(tempDir);

        assertFalse(Files.exists(configPath));

        service.ensureConfigFileExists();

        assertTrue(Files.exists(configPath), "First startup should materialize config.yaml");

        Map<String, Object> savedConfig = readFlatYaml(configPath);
        assertEquals(640, ((Number) savedConfig.get(SonicConfiguration.SCREEN_WIDTH.name())).intValue());
        // SCREEN_WIDTH_PIXELS is DERIVED — ConfigYamlWriter never persists it
        assertFalse(savedConfig.containsKey(SonicConfiguration.SCREEN_WIDTH_PIXELS.name()),
                "SCREEN_WIDTH_PIXELS is derived and must not be persisted");
        assertEquals(service.getString(SonicConfiguration.DEFAULT_ROM),
                savedConfig.get(SonicConfiguration.DEFAULT_ROM.name()));
        assertEquals("Q", savedConfig.get(SonicConfiguration.FRAME_STEP_KEY.name()));
        assertEquals("", savedConfig.get(SonicConfiguration.PLAYBACK_MOVIE_PATH.name()));
        assertEquals(Boolean.FALSE, savedConfig.get(SonicConfiguration.LIVE_REWIND_ENABLED.name()));
        assertEquals(Boolean.FALSE, savedConfig.get(SonicConfiguration.LIVE_REWIND_DETERMINISM_AUDIT.name()));
        assertEquals("R", savedConfig.get(SonicConfiguration.LIVE_REWIND_KEY.name()));
        assertEquals(Boolean.TRUE, savedConfig.get(SonicConfiguration.TITLE_SCREEN_ON_STARTUP.name()));
        assertEquals(Boolean.FALSE, savedConfig.get(SonicConfiguration.LEVEL_SELECT_ON_STARTUP.name()));
        assertEquals(Boolean.TRUE, savedConfig.get(SonicConfiguration.MASTER_TITLE_SCREEN_ON_STARTUP.name()));
        assertTrue(savedConfig.containsKey(SonicConfiguration.DEBUG_VIEW_ENABLED.name()));
        assertEquals(Boolean.FALSE, savedConfig.get(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED.name()));
        assertEquals(Boolean.TRUE, savedConfig.get(SonicConfiguration.DISCORD_RICH_PRESENCE_SHOW_TIMER.name()));
        assertEquals(Boolean.TRUE, savedConfig.get(SonicConfiguration.DISCORD_RICH_PRESENCE_SHOW_ZONE.name()));
    }

    @Test
    void malformedConfigIsQuarantinedBeforeSavingDefaults() throws IOException {
        Path configPath = tempDir.resolve("config.yaml");
        Path corruptPath = tempDir.resolve("config.yaml.corrupt");
        String malformed = "debug: [";

        Files.writeString(configPath, malformed);

        SonicConfigurationService service = SonicConfigurationService.createStandalone(tempDir);
        service.saveConfig();

        assertTrue(Files.exists(corruptPath), "malformed config should be quarantined");
        assertEquals(malformed, Files.readString(corruptPath),
                "quarantine copy must preserve the unreadable config bytes");
        assertTrue(Files.exists(configPath), "saving should create a fresh config.yaml");
        Map<String, Object> savedConfig = readFlatYaml(configPath);
        assertEquals("s2", savedConfig.get(SonicConfiguration.DEFAULT_ROM.name()));
    }

    @Test
    void transientConfigReadFailureLeavesExistingFileInPlace() throws IOException {
        Path configPath = tempDir.resolve("config.yaml");
        Files.writeString(configPath, "audio:\n  enabled: false\n");

        SonicConfigurationService service = SonicConfigurationService.createStandalone(tempDir, file -> {
            throw new IOException("sharing violation");
        });

        assertEquals("audio:\n  enabled: false\n", Files.readString(configPath));
        assertFalse(Files.exists(tempDir.resolve("config.yaml.corrupt")),
                "transient I/O must not quarantine config.yaml");
        assertTrue(service.getBoolean(SonicConfiguration.AUDIO_ENABLED),
                "service falls back to bundled defaults when the file is temporarily unreadable");
    }
}

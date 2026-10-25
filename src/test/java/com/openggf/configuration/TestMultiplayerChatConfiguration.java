package com.openggf.configuration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static com.openggf.configuration.SonicConfiguration.MULTIPLAYER_CENSOR_PROFANITY;
import static com.openggf.configuration.SonicConfiguration.MULTIPLAYER_CHAT_DISABLED;
import static org.junit.jupiter.api.Assertions.*;

class TestMultiplayerChatConfiguration {
    @TempDir Path directory;

    @Test
    void defaultsTemplateMetadataTimingAndSettingsCategoryAgree() throws Exception {
        var config = SonicConfigurationService.createStandalone(directory);
        Map<String, Object> template;
        try (var stream = getClass().getResourceAsStream("/config.yaml")) {
            assertNotNull(stream);
            template = ConfigFlattener.flatten(new YAMLMapper().readValue(stream,
                    new TypeReference<Map<String, Object>>() { })).flat();
        }
        Map<SonicConfiguration, Boolean> defaults = Map.of(
                MULTIPLAYER_CENSOR_PROFANITY, true, MULTIPLAYER_CHAT_DISABLED, false);
        Map<SonicConfiguration, String> paths = Map.of(
                MULTIPLAYER_CENSOR_PROFANITY, "multiplayer.censorProfanity",
                MULTIPLAYER_CHAT_DISABLED, "multiplayer.disableChat");
        var draft = new EngineSettingsDraft(config);
        for (var entry : defaults.entrySet()) {
            SonicConfiguration key = entry.getKey();
            assertEquals(entry.getValue(), config.getDefaultValue(key));
            assertEquals(entry.getValue(), config.getBoolean(key));
            assertEquals(entry.getValue(), template.get(key.name()));
            ConfigKeyMeta meta = ConfigCatalog.meta(key);
            assertTrue(meta.persisted());
            assertEquals(ConfigType.BOOL, meta.type());
            assertEquals(paths.get(key), meta.path());
            assertSame(key, ConfigCatalog.byPath(paths.get(key)));
            assertTrue(ConfigCatalog.emitOrder().contains(key));
            assertEquals(SettingTiming.NOW, SettingTiming.of(key));
            assertEquals(EngineSettingsDraft.Category.GAMEPLAY, EngineSettingsDraft.category(key));
            assertTrue(draft.keys(EngineSettingsDraft.Category.GAMEPLAY).contains(key));
        }
    }

    @Test
    void draftChangesApplyOnSavePersistGloballyAndResetToDefaults() throws Exception {
        var config = SonicConfigurationService.createStandalone(directory);
        var draft = new EngineSettingsDraft(config);
        draft.set(MULTIPLAYER_CENSOR_PROFANITY, "false");
        draft.set(MULTIPLAYER_CHAT_DISABLED, "true");
        assertTrue(config.getBoolean(MULTIPLAYER_CENSOR_PROFANITY));
        assertFalse(config.getBoolean(MULTIPLAYER_CHAT_DISABLED));
        draft.apply();
        assertFalse(draft.dirty());
        assertFalse(config.getBoolean(MULTIPLAYER_CENSOR_PROFANITY));
        assertTrue(config.getBoolean(MULTIPLAYER_CHAT_DISABLED));
        var reloaded = SonicConfigurationService.createStandalone(directory);
        assertFalse(reloaded.getBoolean(MULTIPLAYER_CENSOR_PROFANITY));
        assertTrue(reloaded.getBoolean(MULTIPLAYER_CHAT_DISABLED));
        String yaml = Files.readString(directory.resolve("config.yaml"));
        assertTrue(yaml.contains("multiplayer:"), yaml);
        assertTrue(yaml.contains("censorProfanity: false"), yaml);
        assertTrue(yaml.contains("disableChat: true"), yaml);

        draft.reset(MULTIPLAYER_CENSOR_PROFANITY);
        draft.reset(MULTIPLAYER_CHAT_DISABLED);
        draft.apply();
        yaml = Files.readString(directory.resolve("config.yaml"));
        // Existing settings publication retains explicitly saved defaults. Reset changes
        // the effective preference; it does not remove a user's explicit choice.
        assertFalse(draft.nonDefault(MULTIPLAYER_CENSOR_PROFANITY));
        assertFalse(draft.nonDefault(MULTIPLAYER_CHAT_DISABLED));
        assertTrue(yaml.contains("censorProfanity: true"), yaml);
        assertTrue(yaml.contains("disableChat: false"), yaml);
        reloaded = SonicConfigurationService.createStandalone(directory);
        assertTrue(reloaded.getBoolean(MULTIPLAYER_CENSOR_PROFANITY));
        assertFalse(reloaded.getBoolean(MULTIPLAYER_CHAT_DISABLED));
    }

    @Test
    void olderConfigurationWithoutMultiplayerKeysGetsPersonalDefaultsWithoutLosingPreferences() throws Exception {
        Files.writeString(directory.resolve("config.yaml"), "configFormat: 2\naudio:\n  hubMusicVolume: 35\n");
        var config = SonicConfigurationService.createStandalone(directory);
        assertTrue(config.getBoolean(MULTIPLAYER_CENSOR_PROFANITY));
        assertFalse(config.getBoolean(MULTIPLAYER_CHAT_DISABLED));
        assertEquals(35, config.getInt(SonicConfiguration.AUDIO_HUB_MUSIC_VOLUME));
        assertThrows(IllegalArgumentException.class,
                () -> new EngineSettingsDraft(config).set(MULTIPLAYER_CHAT_DISABLED, "maybe"));
    }
}

package com.openggf.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestConfigServiceYamlRoundTrip {

    @TempDir
    Path tempDir;

    @Test void interpolationDefaultsOffAndRoundTrips() throws Exception {
        var service = SonicConfigurationService.createStandalone(tempDir);
        assertFalse(service.getBoolean(SonicConfiguration.DISPLAY_INTERPOLATION));
        service.setConfigValue(SonicConfiguration.DISPLAY_INTERPOLATION, true);
        service.saveConfig();
        assertTrue(SonicConfigurationService.createStandalone(tempDir)
                .getBoolean(SonicConfiguration.DISPLAY_INTERPOLATION));
        assertTrue(Files.readString(tempDir.resolve("config.yaml")).contains("interpolation: true"));
        assertEquals(SettingTiming.NOW, SettingTiming.of(SonicConfiguration.DISPLAY_INTERPOLATION));
    }

    @Test void interpolationShortcutRoundTripsAndHasNoDefaultBindingConflict() throws Exception {
        var service = SonicConfigurationService.createStandalone(tempDir);
        var key = SonicConfiguration.DISPLAY_INTERPOLATION_TOGGLE_KEY;
        assertEquals("N", service.getDefaultValue(key));
        var draft = new EngineSettingsDraft(service);
        assertTrue(draft.bindingConflicts(key).isEmpty());
        for (var toggle : com.openggf.debug.DebugOverlayToggle.values()) {
            assertNotEquals(toggle.keyCode(), service.getInt(key), toggle.name());
        }
        assertEquals(EngineSettingsDraft.category(SonicConfiguration.DISPLAY_COLOR_PROFILE_TOGGLE_KEY),
                EngineSettingsDraft.category(key));
        assertEquals(SettingTiming.RESTART, SettingTiming.of(key));
        service.setConfigValue(key, "F8");
        service.saveConfig();
        assertEquals(org.lwjgl.glfw.GLFW.GLFW_KEY_F8,
                SonicConfigurationService.createStandalone(tempDir).getInt(key));
        assertTrue(Files.readString(tempDir.resolve("config.yaml")).contains("interpolationToggleKey: F8"));
    }

    @Test
    void saveWritesGroupedYaml() throws Exception {
        Path yaml = tempDir.resolve("config.yaml");
        SonicConfigurationService svc = SonicConfigurationService.createStandalone(tempDir);
        svc.setConfigValue(SonicConfiguration.FPS, 50);
        svc.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, true);
        svc.saveConfig();
        assertTrue(Files.exists(yaml), "saveConfig must write config.yaml");
        String text = Files.readString(yaml);
        assertTrue(text.contains("display:"), text);
        assertTrue(text.contains("debug:"), text);
        assertFalse(text.contains("audio:"), "sections without a user setting are not written");
        assertFalse(text.contains("SCREEN_WIDTH_PIXELS"), "derived keys must not be persisted");
    }

    @Test
    void loadReadsBackWrittenValues() throws Exception {
        SonicConfigurationService svc = SonicConfigurationService.createStandalone(tempDir);
        svc.setConfigValue(SonicConfiguration.AUDIO_ENABLED, false);
        svc.saveConfig();

        SonicConfigurationService reloaded = SonicConfigurationService.createStandalone(tempDir);
        assertFalse(reloaded.getBoolean(SonicConfiguration.AUDIO_ENABLED));
    }
}

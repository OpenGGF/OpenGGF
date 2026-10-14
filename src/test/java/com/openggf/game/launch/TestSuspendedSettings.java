package com.openggf.game.launch;

import com.openggf.configuration.EngineSettingsDraft;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSuspendedSettings {
    @TempDir Path directory;

    @Test
    void hubRunsOnSavedPreferencesWhileTheGameIsSuspended() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(directory);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");

        SuspendedSettings.suspend(config);

        assertFalse(config.hasSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE),
                "the hub must not show the paused game's per-game overrides as preferences");
    }

    @Test
    void settingsSavedInTheHubWaitForTheNextLaunch() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(directory);
        boolean audioBefore = config.getBoolean(SonicConfiguration.AUDIO_ENABLED);
        SuspendedSettings paused = SuspendedSettings.suspend(config);

        EngineSettingsDraft draft = new EngineSettingsDraft(config);
        draft.set(SonicConfiguration.AUDIO_ENABLED, Boolean.toString(!audioBefore));
        draft.apply();
        assertEquals(!audioBefore, config.getBoolean(SonicConfiguration.AUDIO_ENABLED), "saved in the hub");

        paused.resume(config);
        assertEquals(audioBefore, config.getBoolean(SonicConfiguration.AUDIO_ENABLED),
                "the resumed game keeps the value it was running with");

        // The next launch starts from cleared overrides, as MasterTitleLaunchCoordinator.prepareExit does.
        config.clearSessionOverrides();
        assertEquals(!audioBefore, config.getBoolean(SonicConfiguration.AUDIO_ENABLED));
        assertEquals(!audioBefore, SonicConfigurationService.createStandalone(directory)
                .getBoolean(SonicConfiguration.AUDIO_ENABLED), "the hub save is persisted");
    }

    @Test
    void resumeRestoresTheGamesOwnOverridesAndAspect() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(directory);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "WIDE_16_9");
        config.resolveDisplayAspect();
        int gameWidth = config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS);
        SuspendedSettings paused = SuspendedSettings.suspend(config);
        assertEquals(320, config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS), "the hub uses the saved 4:3");

        paused.resume(config);

        assertTrue(gameWidth > 320, "the widescreen fixture must change the width");
        assertEquals("knuckles", config.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        assertEquals(gameWidth, config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS));
        assertFalse(config.hasSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS),
                "derived picture sizes come from the pinned aspect, never a pin of their own");
    }

    @Test
    void unchangedSettingsAreNotPinned() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(directory);
        java.util.Set<SonicConfiguration> before = overridden(config);
        SuspendedSettings paused = SuspendedSettings.suspend(config);

        paused.resume(config);

        assertEquals(before, paused.overridesForTest().keySet());
        assertEquals(before, overridden(config), "resume adds no overrides when nothing changed");
    }

    private static java.util.Set<SonicConfiguration> overridden(SonicConfigurationService config) {
        java.util.Set<SonicConfiguration> keys = java.util.EnumSet.noneOf(SonicConfiguration.class);
        for (SonicConfiguration key : SonicConfiguration.values()) if (config.hasSessionOverride(key)) keys.add(key);
        return keys;
    }
}

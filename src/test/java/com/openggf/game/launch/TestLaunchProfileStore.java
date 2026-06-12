package com.openggf.game.launch;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.MasterTitleScreen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.openggf.game.MasterTitleScreen.GameEntry.SONIC_1;
import static com.openggf.game.MasterTitleScreen.GameEntry.SONIC_2;
import static com.openggf.game.MasterTitleScreen.GameEntry.SONIC_3K;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TestLaunchProfileStore {

    @TempDir
    Path tempDir;

    @Test
    void profileRoundTripsForEachGame() {
        assertRoundTrip(SONIC_1, new LaunchProfile(true, "s2", true, "WIDE_16_10", "tails", "knuckles"));
        assertRoundTrip(SONIC_2, new LaunchProfile(true, "s3k", true, "WIDE_16_9", "knuckles", "none"));
        assertRoundTrip(SONIC_3K, new LaunchProfile(true, "s1", true, "ULTRA_21_9", "tails", "sonic"));
    }

    @Test
    void outOfSetValuesAreClampedByCatalogValidation() throws Exception {
        Files.writeString(tempDir.resolve("config.yaml"), """
                launch:
                  s1:
                    aspect: CINEMASCOPE
                """);

        SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
        LaunchProfile loaded = new LaunchProfileStore(config).load(SONIC_1);

        assertEquals("global", loaded.aspect());
    }

    @Test
    void donorEqualToLaunchedGameClampsToOffOnLoad() throws Exception {
        Files.writeString(tempDir.resolve("config.yaml"), """
                launch:
                  s2:
                    crossGameSource: s2
                """);

        SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
        LaunchProfile loaded = new LaunchProfileStore(config).load(SONIC_2);

        assertEquals("off", loaded.crossGameSource());
    }

    @Test
    void saveWritesOnlyLaunchKeysAndCallsSaveOnce() {
        SonicConfigurationService config = spy(SonicConfigurationService.createStandalone(tempDir));
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, false);
        config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, true);
        config.setConfigValue(SonicConfiguration.CROSS_GAME_SOURCE, "s3k");
        config.setConfigValue(SonicConfiguration.DEBUG_VIEW_ENABLED, false);
        config.setConfigValue(SonicConfiguration.DISPLAY_ASPECT, "SUPER_32_9");
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");

        new LaunchProfileStore(config).save(SONIC_1,
                new LaunchProfile(true, "s2", true, "WIDE_16_9", "tails", "none"));

        verify(config, times(1)).saveConfig();
        assertFalse(config.getBoolean(SonicConfiguration.LIVE_REWIND_ENABLED));
        assertEquals(true, config.getBoolean(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED));
        assertEquals("s3k", config.getString(SonicConfiguration.CROSS_GAME_SOURCE));
        assertFalse(config.getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED));
        assertEquals("SUPER_32_9", config.getString(SonicConfiguration.DISPLAY_ASPECT));
        assertEquals("sonic", config.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        assertEquals("tails", config.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE));
    }

    private void assertRoundTrip(MasterTitleScreen.GameEntry entry, LaunchProfile profile) {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
        new LaunchProfileStore(config).save(entry, profile);

        SonicConfigurationService reloaded = SonicConfigurationService.createStandalone(tempDir);
        LaunchProfile loaded = new LaunchProfileStore(reloaded).load(entry);

        assertEquals(profile, loaded);
    }
}

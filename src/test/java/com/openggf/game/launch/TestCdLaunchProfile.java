package com.openggf.game.launch;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.MasterTitleScreen.GameEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class TestCdLaunchProfile {
    @TempDir Path directory;

    @Test void hostedLaunchAppliesTheSavedDonorAndTeam() {
        var config = SonicConfigurationService.createStandalone(directory);
        var store = new LaunchProfileStore(config);
        assertEquals("none", store.load(GameEntry.SONIC_CD).sidekick());
        var profile = new LaunchProfile(LaunchToggle.DEFAULT, "s3k", LaunchToggle.DEFAULT,
                "global", "knuckles", "none");
        store.save(GameEntry.SONIC_CD, profile);
        assertEquals(profile, store.load(GameEntry.SONIC_CD));
        var coordinator = new MasterTitleLaunchCoordinator(config, store, new LaunchProfileApplier(config));
        coordinator.prepareHostedExit("scd");
        assertTrue(config.getBoolean(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED));
        assertEquals("s3k", config.getString(SonicConfiguration.CROSS_GAME_SOURCE));
        assertEquals("knuckles", config.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        assertEquals("", config.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE));
    }
}

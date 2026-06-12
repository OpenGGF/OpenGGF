package com.openggf.game.launch;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.MasterTitleScreen;

import java.util.Objects;
import java.util.logging.Logger;

public class LaunchProfileStore {
    private static final Logger LOGGER = Logger.getLogger(LaunchProfileStore.class.getName());

    private final SonicConfigurationService configService;

    public LaunchProfileStore(SonicConfigurationService configService) {
        this.configService = Objects.requireNonNull(configService, "configService");
    }

    public LaunchProfile load(MasterTitleScreen.GameEntry entry) {
        Keys keys = keysFor(entry);
        String crossGameSource = configService.getString(keys.crossGameSource());
        if (LaunchProfile.gameId(entry).equals(crossGameSource)) {
            LOGGER.warning("Invalid launch profile donor for " + entry.gameId + ": " + crossGameSource
                    + "; replacing with off.");
            crossGameSource = "off";
        }
        return new LaunchProfile(
                configService.getBoolean(keys.rewind()),
                crossGameSource,
                configService.getBoolean(keys.debugTools()),
                configService.getString(keys.aspect()),
                configService.getString(keys.mainCharacter()),
                configService.getString(keys.sidekick()));
    }

    public void save(MasterTitleScreen.GameEntry entry, LaunchProfile profile) {
        Objects.requireNonNull(profile, "profile");
        Keys keys = keysFor(entry);
        configService.setConfigValue(keys.rewind(), profile.rewind());
        configService.setConfigValue(keys.crossGameSource(), profile.crossGameSource());
        configService.setConfigValue(keys.debugTools(), profile.debugTools());
        configService.setConfigValue(keys.aspect(), profile.aspect());
        configService.setConfigValue(keys.mainCharacter(), profile.mainCharacter());
        configService.setConfigValue(keys.sidekick(), profile.sidekick());
        configService.saveConfig();
    }

    private static Keys keysFor(MasterTitleScreen.GameEntry entry) {
        Objects.requireNonNull(entry, "entry");
        return switch (entry) {
            case SONIC_1 -> new Keys(
                    SonicConfiguration.LAUNCH_S1_REWIND,
                    SonicConfiguration.LAUNCH_S1_CROSS_GAME_SOURCE,
                    SonicConfiguration.LAUNCH_S1_DEBUG_TOOLS,
                    SonicConfiguration.LAUNCH_S1_ASPECT,
                    SonicConfiguration.LAUNCH_S1_MAIN_CHARACTER,
                    SonicConfiguration.LAUNCH_S1_SIDEKICK);
            case SONIC_2 -> new Keys(
                    SonicConfiguration.LAUNCH_S2_REWIND,
                    SonicConfiguration.LAUNCH_S2_CROSS_GAME_SOURCE,
                    SonicConfiguration.LAUNCH_S2_DEBUG_TOOLS,
                    SonicConfiguration.LAUNCH_S2_ASPECT,
                    SonicConfiguration.LAUNCH_S2_MAIN_CHARACTER,
                    SonicConfiguration.LAUNCH_S2_SIDEKICK);
            case SONIC_3K -> new Keys(
                    SonicConfiguration.LAUNCH_S3K_REWIND,
                    SonicConfiguration.LAUNCH_S3K_CROSS_GAME_SOURCE,
                    SonicConfiguration.LAUNCH_S3K_DEBUG_TOOLS,
                    SonicConfiguration.LAUNCH_S3K_ASPECT,
                    SonicConfiguration.LAUNCH_S3K_MAIN_CHARACTER,
                    SonicConfiguration.LAUNCH_S3K_SIDEKICK);
        };
    }

    private record Keys(
            SonicConfiguration rewind,
            SonicConfiguration crossGameSource,
            SonicConfiguration debugTools,
            SonicConfiguration aspect,
            SonicConfiguration mainCharacter,
            SonicConfiguration sidekick) {
    }
}

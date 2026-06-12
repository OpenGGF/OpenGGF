package com.openggf.game.launch;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;

import java.util.Objects;

public class LaunchProfileApplier {
    private final SonicConfigurationService configService;

    public LaunchProfileApplier(SonicConfigurationService configService) {
        this.configService = Objects.requireNonNull(configService, "configService");
    }

    public void apply(LaunchProfile profile) {
        Objects.requireNonNull(profile, "profile");
        configService.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, profile.rewind());
        if ("off".equals(profile.crossGameSource())) {
            configService.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
            configService.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE,
                    configService.getDefaultValue(SonicConfiguration.CROSS_GAME_SOURCE));
        } else {
            configService.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, true);
            configService.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE, profile.crossGameSource());
        }
        configService.setSessionOverride(SonicConfiguration.DEBUG_VIEW_ENABLED, profile.debugTools());
        configService.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, profile.mainCharacter());
        configService.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                "none".equals(profile.sidekick()) ? "" : profile.sidekick());
        if (!"global".equals(profile.aspect())) {
            configService.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, profile.aspect());
        }
    }
}

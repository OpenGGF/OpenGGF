package com.openggf.game;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;

public final class FrameRateResolver {
    private FrameRateResolver() {
    }

    public static int effective(SonicConfigurationService config) {
        return "PAL".equalsIgnoreCase(config.getString(SonicConfiguration.REGION))
                ? 50
                : Math.max(1, config.getInt(SonicConfiguration.FPS));
    }
}

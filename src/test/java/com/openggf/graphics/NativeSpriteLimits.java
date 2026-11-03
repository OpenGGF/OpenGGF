package com.openggf.graphics;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;

/** Native image probes opt into ROM budgets and restore their caller's display preference. */
public final class NativeSpriteLimits implements AutoCloseable {
    private final SonicConfigurationService configuration;
    private final Object original;

    private NativeSpriteLimits() {
        configuration = GameServices.configuration();
        original = configuration.getConfigValue(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT);
        configuration.setConfigValue(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT, true);
    }

    public static NativeSpriteLimits enable() { return new NativeSpriteLimits(); }

    @Override public void close() {
        configuration.setConfigValue(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT, original);
    }
}

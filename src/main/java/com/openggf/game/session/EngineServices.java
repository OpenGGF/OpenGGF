package com.openggf.game.session;

import java.util.Objects;

/**
 * Process-wide engine service root.
 */
public final class EngineServices {
    private static EngineContext current;

    private EngineServices() {
    }

    public static synchronized void configure(EngineContext services) {
        current = Objects.requireNonNull(services, "services");
        bindGraphicsPreferences(current);
    }

    public static synchronized EngineContext current() {
        if (current == null) {
            current = EngineContext.fromLegacySingletonsForBootstrap();
            bindGraphicsPreferences(current);
        }
        return current;
    }

    private static void bindGraphicsPreferences(EngineContext services) {
        var configuration = services.configuration();
        com.openggf.graphics.SpriteSatInternalAccess.bindLineLimitSource(services.graphics(),
                () -> configuration.getBoolean(
                        com.openggf.configuration.SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT));
    }
}

package com.openggf.game.launch;

import com.openggf.configuration.ConfigurationPersistence;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;

import java.io.IOException;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

/** Remembers menu focus immediately and coalesces disk writes on the menu thread. */
public final class HubProgressStore {
    private static final Logger LOGGER = Logger.getLogger(HubProgressStore.class.getName());
    private static final long SETTLE_NANOS = 750_000_000L;
    private final SonicConfigurationService config;
    private final LongSupplier clock;
    private String pendingGame;
    private long changedAt;

    public HubProgressStore(SonicConfigurationService config) { this(config, System::nanoTime); }

    HubProgressStore(SonicConfigurationService config, LongSupplier clock) {
        this.config = config;
        this.clock = clock;
    }

    public String lastGame() {
        return pendingGame != null ? pendingGame : config.getString(SonicConfiguration.HUB_LAST_GAME);
    }

    public void rememberGame(String gameId) {
        if (gameId == null || gameId.equals(lastGame())) return;
        pendingGame = gameId.equals(config.getString(SonicConfiguration.HUB_LAST_GAME)) ? null : gameId;
        changedAt = clock.getAsLong();
    }

    /** Called after menu input, so continued navigation postpones the write. */
    public void update() {
        if (pendingGame != null && clock.getAsLong() - changedAt >= SETTLE_NANOS) flush();
    }

    /** Flush on launch or leaving the hub, without introducing concurrent configuration writes. */
    public void flush() {
        if (pendingGame == null) return;
        try {
            ConfigurationPersistence.apply(config, Map.of(SonicConfiguration.HUB_LAST_GAME, pendingGame));
            pendingGame = null;
        } catch (IOException failure) {
            // Retain the selection and throttle retries, rather than writing/logging every frame.
            changedAt = clock.getAsLong();
            LOGGER.warning("Could not save hub history: " + failure.getMessage());
        }
    }
}

package com.openggf.game.launch;

import com.openggf.configuration.ConfigurationPersistence;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

/** Persists the last focused game in the host menu. */
public final class HubProgressStore {
    private static final Logger LOGGER = Logger.getLogger(HubProgressStore.class.getName());
    private final SonicConfigurationService config;
    public HubProgressStore(SonicConfigurationService config) { this.config = config; }

    public String lastGame() { return config.getString(SonicConfiguration.HUB_LAST_GAME); }

    public void rememberGame(String gameId) {
        if (gameId != null && !gameId.equals(lastGame()))
            persist(Map.of(SonicConfiguration.HUB_LAST_GAME, gameId));
    }

    private void persist(Map<SonicConfiguration, Object> changes) {
        try { ConfigurationPersistence.apply(config, changes); }
        catch (IOException failure) { LOGGER.warning("Could not save hub history: " + failure.getMessage()); }
    }
}

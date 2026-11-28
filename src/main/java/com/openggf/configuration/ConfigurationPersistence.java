package com.openggf.configuration;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/** Internal persistence boundary for engine-owned, already validated preference edits. */
public final class ConfigurationPersistence {
    private ConfigurationPersistence() { }

    /**
     * Whether the player's configuration file already existed when it was loaded (an earlier
     * install), as opposed to one this run created; read once at startup by the hub's setup guide.
     */
    public static boolean loadedExistingFile(SonicConfigurationService config) {
        return Objects.requireNonNull(config, "config").loadedFromExistingFile();
    }

    /** Publish the candidate file before exposing any changes to live configuration readers. */
    public static void apply(SonicConfigurationService config, Map<SonicConfiguration, Object> changes)
            throws IOException {
        Objects.requireNonNull(config, "config");
        Map<SonicConfiguration, Object> candidate = Map.copyOf(changes);
        for (SonicConfiguration key : candidate.keySet()) {
            if (!ConfigCatalog.meta(key).persisted()) throw new IllegalArgumentException("Derived setting: " + key);
        }
        config.applySettings(candidate);
    }
}

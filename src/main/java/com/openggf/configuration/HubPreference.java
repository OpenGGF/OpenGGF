package com.openggf.configuration;

/** Hub metadata kept in the player configuration and outside the creator setting keys. */
public enum HubPreference {
    KEPT_MOD_GAMES("keptModGames", "Mod game IDs kept on the shelf"),
    RECENT_MOD_GAMES("recentModGames", "Recently played mod game IDs"),
    MODS_OFF("modsOff", "Stock game IDs with external mods off");

    private final ConfigKeyMeta metadata;

    HubPreference(String leaf, String description) {
        metadata = ConfigKeyMeta.of("hub", leaf, ConfigType.STRING, description);
    }

    ConfigKeyMeta metadata() { return metadata; }

    String storageKey() { return metadata.path(); }

    static HubPreference atPath(String path) {
        for (HubPreference key : values()) {
            if (key.storageKey().equals(path)) return key;
        }
        return null;
    }

    public String read(SonicConfigurationService config) { return config.hubPreference(this); }
}

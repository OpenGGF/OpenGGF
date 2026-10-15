package com.openggf.game.launch;

import com.openggf.configuration.ConfigurationPersistence;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.MasterTitleScreen.GameEntry;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.sonic1.Sonic1ZoneRegistry;
import com.openggf.game.sonic2.Sonic2ZoneRegistry;
import com.openggf.game.sonic3k.Sonic3kZoneRegistry;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/** Host menu history. Continue restarts a reached stock act; it is not a gameplay snapshot. */
public final class HubProgressStore {
    private static final Logger LOGGER = Logger.getLogger(HubProgressStore.class.getName());
    private final SonicConfigurationService config;
    private static final Map<GameEntry, ZoneRegistry> REGISTRIES = Map.of(
            GameEntry.SONIC_1, new Sonic1ZoneRegistry(), GameEntry.SONIC_2, new Sonic2ZoneRegistry(),
            GameEntry.SONIC_3K, new Sonic3kZoneRegistry());

    public record Progress(int zone, int act, String label, String character) { }

    public HubProgressStore(SonicConfigurationService config) { this.config = config; }

    public String lastGame() { return config.getString(SonicConfiguration.HUB_LAST_GAME); }

    public void rememberGame(String gameId) {
        if (gameId != null && !gameId.equals(lastGame()))
            persist(Map.of(SonicConfiguration.HUB_LAST_GAME, gameId));
    }

    public Optional<Progress> load(GameEntry game) {
        int zone = config.getInt(zoneKey(game)), act = config.getInt(actKey(game));
        if (!valid(game, zone, act)) return Optional.empty();
        return Optional.of(new Progress(zone, act, registry(game).getZoneName(zone) + " " + (act + 1),
                config.getString(characterKey(game))));
    }

    public Optional<Progress> load(GameEntry game, String character) {
        return load(game).filter(progress -> progress.character().equals(character));
    }

    String currentCharacter() { return config.getString(SonicConfiguration.MAIN_CHARACTER_CODE); }

    /** Called only for successful ordinary gameplay in a session launched from the hub. */
    public void reached(GameEntry game, int zone, int act) { reached(game, zone, act, currentCharacter()); }

    public void reached(GameEntry game, int zone, int act, String character) {
        if (!valid(game, zone, act) || character == null || character.isBlank()) return;
        if (config.getInt(zoneKey(game)) == zone && config.getInt(actKey(game)) == act
                && character.equals(config.getString(characterKey(game)))) return;
        persist(Map.of(zoneKey(game), zone, actKey(game), act, characterKey(game), character));
    }

    public static boolean valid(GameEntry game, int zone, int act) {
        ZoneRegistry zones = registry(game);
        // Stock routes only; Doomsday needs emerald state which zone-start Continue does not restore.
        int count = switch (game) { case SONIC_1 -> 7; case SONIC_2 -> 11; case SONIC_3K -> 12; };
        return zone >= 0 && zone < Math.min(count, zones.getZoneCount())
                && act >= 0 && act < zones.getActCount(zone);
    }

    private static ZoneRegistry registry(GameEntry game) {
        return REGISTRIES.get(game);
    }

    private static SonicConfiguration zoneKey(GameEntry game) {
        return switch (game) {
            case SONIC_1 -> SonicConfiguration.HUB_CONTINUE_S1_ZONE;
            case SONIC_2 -> SonicConfiguration.HUB_CONTINUE_S2_ZONE;
            case SONIC_3K -> SonicConfiguration.HUB_CONTINUE_S3K_ZONE;
        };
    }

    private static SonicConfiguration actKey(GameEntry game) {
        return switch (game) {
            case SONIC_1 -> SonicConfiguration.HUB_CONTINUE_S1_ACT;
            case SONIC_2 -> SonicConfiguration.HUB_CONTINUE_S2_ACT;
            case SONIC_3K -> SonicConfiguration.HUB_CONTINUE_S3K_ACT;
        };
    }

    private static SonicConfiguration characterKey(GameEntry game) {
        return switch (game) {
            case SONIC_1 -> SonicConfiguration.HUB_CONTINUE_S1_CHARACTER;
            case SONIC_2 -> SonicConfiguration.HUB_CONTINUE_S2_CHARACTER;
            case SONIC_3K -> SonicConfiguration.HUB_CONTINUE_S3K_CHARACTER;
        };
    }

    private void persist(Map<SonicConfiguration, Object> changes) {
        try { ConfigurationPersistence.apply(config, changes); }
        catch (IOException failure) { LOGGER.warning("Could not save hub history: " + failure.getMessage()); }
    }
}

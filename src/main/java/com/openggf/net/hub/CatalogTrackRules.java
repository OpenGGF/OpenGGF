package com.openggf.net.hub;

import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.TrackKey;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Properties;

/** Reads exported game catalogue facts without loading the game implementation. */
final class CatalogTrackRules implements TrackRulesProvider {
    static final CatalogTrackRules SHIPPED = new CatalogTrackRules();
    private final Properties facts = new Properties();
    private CatalogTrackRules() {
        try (var input = CatalogTrackRules.class.getResourceAsStream("/net/track-rules.properties")) {
            if (input == null) throw new IllegalStateException("Missing track rules catalogue");
            facts.load(input);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    private int number(String key) { return Integer.parseInt(facts.getProperty(key, "0")); }
    public boolean legal(TrackKey key, String mode) {
        if ("ARENA".equals(key.kind())) return "COMPETITION".equals(mode)
                && key.zone() < number(key.gameId() + ".arenas");
        return "TIME_ATTACK".equals(mode) || "COMPETITION".equals(mode)
                && Boolean.parseBoolean(facts.getProperty(key.gameId() + ".competition", "true"));
    }
    public boolean monitorsLegal(ControlMessage.RoundConfig config) {
        if ("TIME_ATTACK".equals(config.mode())) return "NORMAL".equals(config.monitors());
        if ("ARENA".equals(config.trackKind())) return "ITEMS".equals(config.monitors()) || "OFF".equals(config.monitors());
        if ("NORMAL".equals(config.monitors()) || "OFF".equals(config.monitors())) return true;
        String tracks = facts.getProperty(config.gameId() + ".swapActs", "");
        return java.util.Arrays.asList(tracks.split(",")).contains(config.zone() + ":" + config.act())
                && "RANDOM".equals(config.monitors()); // Obj2E_Init's two-player draw; ITEMS is arena-only.
    }
    public int clockStartFrame(TrackKey key) {
        return "ARENA".equals(key.kind()) ? number(key.gameId() + ".arenaClockStart") : 0;
    }
}

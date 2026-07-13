package com.openggf.game;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

/** Internal routing predicates derived from a module's typed identity. */
public final class GameModuleRouting {
    private static final EnumSet<GameId> STANDALONE_IDS = EnumSet.of(GameId.STANDALONE);
    private static final Map<GameId, String> PRESENCE_NAMES = presenceNames();

    private GameModuleRouting() { }

    public static boolean isStandalone(GameModule module) {
        return STANDALONE_IDS.contains(Objects.requireNonNull(module, "module").getGameId());
    }

    public static String presenceDisplayName(GameModule module) {
        Objects.requireNonNull(module, "module");
        String stockName = PRESENCE_NAMES.get(module.getGameId());
        return stockName != null ? stockName : module.getGameCode();
    }

    private static Map<GameId, String> presenceNames() {
        EnumMap<GameId, String> names = new EnumMap<>(GameId.class);
        names.put(GameId.S1, "Sonic the Hedgehog");
        names.put(GameId.S2, "Sonic 2");
        names.put(GameId.S3K, "Sonic 3 & Knuckles");
        return Map.copyOf(names);
    }
}

package com.openggf.game.internal;

import com.openggf.net.protocol.TrackKey;

/** Game-owned catalogue policy for the shipped race tracks and monitor sets. */
public final class CompetitionTrackRules {
    private CompetitionTrackRules() { }
    public static boolean legal(TrackKey key, String mode) {
        if ("ARENA".equals(key.kind())) return "s3k".equals(key.gameId()) && key.zone() < 5 && "COMPETITION".equals(mode);
        return "TIME_ATTACK".equals(mode) || "COMPETITION".equals(mode) && !"s1".equals(key.gameId());
    }
    public static boolean monitorsLegal(com.openggf.net.protocol.ControlMessage.RoundConfig config) {
        if ("TIME_ATTACK".equals(config.mode())) return "NORMAL".equals(config.monitors());
        if ("ARENA".equals(config.trackKind())) return "ITEMS".equals(config.monitors()) || "OFF".equals(config.monitors());
        if ("NORMAL".equals(config.monitors()) || "OFF".equals(config.monitors())) return true;
        // S2 first acts with signpost exits. Event-only and final/boss acts cannot swap.
        return "s2".equals(config.gameId()) && ((config.act() == 0 && config.zone() <= 7) || config.zone() == 7 && config.act() == 1)
                && ("RANDOM".equals(config.monitors()) || "ITEMS".equals(config.monitors()));
    }
}

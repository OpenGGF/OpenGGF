package com.openggf.net.hub;

import com.openggf.net.protocol.TrackKey;

/** Shared room and relay eligibility, independent of a client's catalog. */
public final class TrackRules {
    private TrackRules() { }
    public static boolean legal(TrackKey key, String mode) {
        if ("ARENA".equals(key.kind())) return "s3k".equals(key.gameId()) && key.zone() < 5 && "COMPETITION".equals(mode);
        return "TIME_ATTACK".equals(mode) || "COMPETITION".equals(mode) && !"s1".equals(key.gameId());
    }
    static boolean monitorsLegal(com.openggf.net.protocol.ControlMessage.RoundConfig config) {
        if ("TIME_ATTACK".equals(config.mode())) return "NORMAL".equals(config.monitors());
        if ("ARENA".equals(config.trackKind())) return "ITEMS".equals(config.monitors()) || "OFF".equals(config.monitors());
        if ("NORMAL".equals(config.monitors()) || "OFF".equals(config.monitors())) return true;
        // S2 first acts with signpost exits. Event-only and final/boss acts cannot swap.
        return "s2".equals(config.gameId()) && ((config.act() == 0 && config.zone() <= 7) || config.zone() == 7 && config.act() == 1)
                && ("RANDOM".equals(config.monitors()) || "ITEMS".equals(config.monitors()));
    }
    static int delta(int value, int wrap) {
        return wrap > 0 ? Math.floorMod(value + wrap / 2, wrap) - wrap / 2 : value;
    }
}

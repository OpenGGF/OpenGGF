package com.openggf.net.hub;

import com.openggf.net.protocol.TrackKey;

/** Shared room and relay eligibility, independent of a client's catalog. */
public final class TrackRules {
    private TrackRules() { }
    public static TrackRulesProvider shipped() { return CatalogTrackRules.SHIPPED; }
    public static boolean legal(TrackKey key, String mode) {
        return CatalogTrackRules.SHIPPED.legal(key, mode);
    }
    static boolean monitorsLegal(com.openggf.net.protocol.ControlMessage.RoundConfig config) {
        return CatalogTrackRules.SHIPPED.monitorsLegal(config);
    }
    static boolean swapsEnabled(com.openggf.net.protocol.ControlMessage.RoundConfig config) {
        return "COMPETITION".equals(config.mode()) && monitorsLegal(config)
                && ("RANDOM".equals(config.monitors()) || "ITEMS".equals(config.monitors()));
    }
    /**
     * Ghost centres are the ROM's 16-bit x_pos/y_pos words, so an axis without a track wrap still
     * differs by word arithmetic: a player above a level's top edge reads $FFxx, as in the ROM.
     */
    static int delta(int value, int wrap) {
        return wrap > 0 ? Math.floorMod(value + wrap / 2, wrap) - wrap / 2 : (short) value;
    }
}

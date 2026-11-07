package com.openggf.net.protocol;

/** Canonical track identity; arena indices occupy zone and have act zero. */
@com.openggf.game.ModApi
public record TrackKey(String gameId, String kind, int zone, int act) {
    public TrackKey {
        if (gameId == null || !gameId.matches("[a-z0-9]{1,8}") || !("ACT".equals(kind) || "ARENA".equals(kind)) || zone < 0 || zone > 999 || act < 0 || act > 999 || "ARENA".equals(kind) && act != 0) throw new IllegalArgumentException("invalid track key");
    }
    public static TrackKey parse(String value) {
        if (value == null || !value.matches("[a-z0-9]{1,8}:(?:[0-9]{1,3}:[0-9]{1,3}|arena:[0-9]{1,3})")) throw new IllegalArgumentException("invalid track key");
        String[] parts = value.split(":");
        return "arena".equals(parts[1]) ? new TrackKey(parts[0], "ARENA", Integer.parseInt(parts[2]), 0) : new TrackKey(parts[0], "ACT", Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }
    public String format() { return gameId + ":" + ("ARENA".equals(kind) ? "arena:" + zone : zone + ":" + act); }
}

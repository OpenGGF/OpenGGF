package com.openggf.game.modzone;

import com.openggf.game.ModApi;
import com.openggf.game.ZoneKey;
import com.openggf.level.Level;

import java.util.Objects;

/** Engine-selected destination and live level supplied to a contributed runtime factory. */
@ModApi
public record ModZoneRuntimeContext(ZoneKey.Mod destination, String hostGameId,
        int zoneIndex, int actIndex, Level level) {
    public ModZoneRuntimeContext {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(hostGameId, "hostGameId");
        Objects.requireNonNull(level, "level");
        if (zoneIndex < 0 || actIndex < 0) throw new IllegalArgumentException("Invalid runtime destination");
    }
}

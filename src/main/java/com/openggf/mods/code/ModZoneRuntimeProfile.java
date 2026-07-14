package com.openggf.mods.code;

import com.openggf.game.ModApi;

/** Runtime feature profile selected by the host adapter for an additive zone. */
@ModApi
public record ModZoneRuntimeProfile(
        ScrollPolicy scroll,
        boolean animatedTiles,
        boolean plcLoads,
        boolean specialRenderEffects,
        boolean advancedRenderModes) {

    public static ModZoneRuntimeProfile flatEmpty() {
        return new ModZoneRuntimeProfile(ScrollPolicy.FLAT, false, false, false, false);
    }

    @ModApi
    public enum ScrollPolicy {
        FLAT
    }
}

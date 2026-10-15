package com.openggf.game.sonic3k.difficulty;

import com.openggf.game.ModApi;

/** Authored standing-support lifetime for a ROM-backed AIZ brittle log. */
@ModApi
public record AizLogBridgeRecipe(int supportTicks) {
    public AizLogBridgeRecipe {
        if (supportTicks < 8 || supportTicks > 24) {
            throw new IllegalArgumentException("Brittle log support must last 8..24 object updates");
        }
    }
}

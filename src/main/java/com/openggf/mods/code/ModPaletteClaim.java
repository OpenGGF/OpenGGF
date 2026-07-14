package com.openggf.mods.code;

import com.openggf.game.ModApi;

/** One creator-owned color cell in the host palette. */
@ModApi
public record ModPaletteClaim(int line, int color, int segaColor) {
    public ModPaletteClaim {
        if (line < 1 || line > 3) {
            throw new IllegalArgumentException("creator palette line must be 1..3");
        }
        if (color < 0 || color > 15) {
            throw new IllegalArgumentException("palette color must be 0..15");
        }
        if ((segaColor & ~0x0EEE) != 0) {
            throw new IllegalArgumentException("invalid Genesis color");
        }
    }
}

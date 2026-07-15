package com.openggf.game.palette;

/** Host-owned palette composition installed for one custom level. */
@com.openggf.game.ModApi
public interface CustomZonePaletteBridge {
    void submitFrameClaims(PaletteOwnershipRegistry registry);
}

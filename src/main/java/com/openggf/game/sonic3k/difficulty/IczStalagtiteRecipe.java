package com.openggf.game.sonic3k.difficulty;

/**
 * Bounded native ICZ Stalagtite presets. The native owner keeps its solid body,
 * falling hurt box, debris and landing state; a preset only selects when the
 * fall is triggered and how long the visible shake warns before release.
 */
@com.openggf.game.ModApi
public enum IczStalagtiteRecipe {
    /** Shipped {@code loc_8B1AE} range {@code $70} (112 px) and shake timer {@code $0F} (16 updates). */
    STOCK(0x70, 0x0F),
    /**
     * Trigger range {@code $A0} (160 px) and shake timer {@code $2F} (48
     * updates). The icicle falls from further away but gives a long, visible
     * warning, and the native landed solid then closes that approach.
     */
    WARNED(0xA0, 0x2F);

    private final int triggerRange;
    private final int shakeTimerStart;

    IczStalagtiteRecipe(int triggerRange, int shakeTimerStart) {
        this.triggerRange = triggerRange;
        this.shakeTimerStart = shakeTimerStart;
    }

    public int triggerRange() { return triggerRange; }
    public int shakeTimerStart() { return shakeTimerStart; }
}

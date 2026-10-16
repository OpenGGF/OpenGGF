package com.openggf.game.sonic3k.difficulty;

/**
 * Bounded native CNZ Clamer presets. The native owner keeps its ROM art, hidden
 * spring child, launch behaviour and projectile child; a preset only selects the
 * auto-close commitment range and the extra tell hold. No arbitrary delay,
 * range, velocity or projectile count is accepted, and the native owner never
 * infers a preset from the original record's identity: the creator recipe
 * chooses which original records receive which preset.
 *
 * <p>Values are written as hexadecimal ROM units with decimal in parentheses.
 */
@com.openggf.game.ModApi
public enum ClamerRecipe {
    /** Shipped {@code loc_88FEC} range {@code $60} (96 px), no extra tell hold. */
    STOCK(0x60, 0),
    /** Commitment range widened to {@code $90} (144 px); stock tell hold. */
    CADENCE(0x90, 0),
    /**
     * Range {@code $90} (144 px) and the open-mouth tell frame ({@code byte_89185}
     * index 3, stock delay {@code $2F} = 47) held {@code $20} (32) updates longer
     * to {@code $4F} (79). A creator pairs it with {@link #CADENCE} on an adjacent
     * Clamer so the two release alternately.
     */
    STAGGERED_CADENCE(0x90, 0x20);

    private final int autoCloseRange;
    private final int tellHold;

    ClamerRecipe(int autoCloseRange, int tellHold) {
        this.autoCloseRange = autoCloseRange;
        this.tellHold = tellHold;
    }

    /** Horizontal distance below which an on-side player starts the auto-close. */
    public int autoCloseRange() { return autoCloseRange; }

    /** Extra updates added to the native tell-frame delay. */
    public int tellHold() { return tellHold; }
}

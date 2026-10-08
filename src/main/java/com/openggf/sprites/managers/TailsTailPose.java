package com.openggf.sprites.managers;

/** Shared native held-pose scripts for the live controller and read-only scene projection. */
public final class TailsTailPose {
    // S2 Obj05Ani_Swish/Spindash; separate-art AniTails_Tail01/07 uses its own mappings.
    static final int[] SWISH_S2 = { 0x09, 0x0A, 0x0B, 0x0C, 0x0D };
    static final int[] SWISH_SEPARATE = { 0x22, 0x23, 0x24, 0x25, 0x26 };
    static final int[] SPINDASH_S2 = { 0x81, 0x82, 0x83, 0x84 };
    static final int[] SPINDASH_SEPARATE = { 1, 2, 3, 4 };
    static final int SWISH_DELAY = 7, SPINDASH_DELAY = 2;

    private TailsTailPose() { }

    /** S2 Obj05AniSelection maps Wait/Duck to Swish and Spindash to Spindash.
     * No parent, animation clock or DPLC owner is changed by this value lookup. */
    public static int frame(boolean separateArt, boolean spindash, long tick) {
        if (tick < 0) throw new IllegalArgumentException("Negative tail pose time");
        int[] frames = spindash
                ? (separateArt ? SPINDASH_SEPARATE : SPINDASH_S2)
                : (separateArt ? SWISH_SEPARATE : SWISH_S2);
        int delay = spindash ? SPINDASH_DELAY : SWISH_DELAY;
        return frames[(int) ((tick / (delay + 1)) % frames.length)];
    }
}

package com.openggf.game.sonic3k;

/**
 * Runtime-owned S3K animation words advanced by {@code ChangeRingFrame}, and the
 * AnPal RAM bank every {@code AnPal_*} routine shares.
 */
final class Sonic3kGlobalAnimationState {
    private static final int AIZ_VINE_ANGLE_STEP = 0x180;
    /** {@code Palette_cycle_counters: ds.b $C} (sonic3k.constants.asm:596). */
    static final int PALETTE_CYCLE_COUNTERS_BYTES = 0xC;
    /** Counter0 and counter1 words, then the counters bytes. */
    static final int PALETTE_CYCLE_SNAPSHOT_BYTES = 4 + PALETTE_CYCLE_COUNTERS_BYTES;
    /** {@code Level/loc_64DC}: {@code move.w #$16,(Palette_fade_timer).w} (sonic3k.asm:7877). */
    static final int FRESH_LEVEL_PALETTE_FADE_FRAMES = 0x16;

    private int aizVineAngle;
    private int ringTimer;
    private int ringFrame;
    /** {@code Palette_fade_timer}: level frames left before {@code Animate_Palette} reaches AnPal. */
    private int paletteFadeTimer;
    /**
     * {@code Palette_cycle_counter0}/{@code Palette_cycle_counter1} words and
     * {@code Palette_cycle_counters} (sonic3k.constants.asm:580-596). Every AnPal routine
     * reads and writes this one bank. Level's loc_60DE clears it
     * ({@code clearRAM Lag_frame_count,(Nem_decomp_queue-Lag_frame_count)}, sonic3k.asm:7652); a seamless act reload never
     * reaches loc_60DE, so the next act's AnPal continues from the previous act's values.
     */
    private int paletteCycleCounter0;
    private int paletteCycleCounter1;
    private final byte[] paletteCycleCounters = new byte[PALETTE_CYCLE_COUNTERS_BYTES];
    /** {@code Level_frame_counter} of a LevelLoop iteration whose Animate_Palette ran before its reload. */
    private int animatePaletteBeforeReloadFrame = NO_FRAME;
    private static final int NO_FRAME = Integer.MIN_VALUE;

    void advanceChangeRingFrame() {
        // ChangeRingFrame: byte SUBQ/BPL; initial zero expires on the first
        // ordinary LevelLoop and reloads7. This clock is independent of LFC.
        ringTimer = (ringTimer - 1) & 0xFF;
        if ((byte) ringTimer < 0) {
            ringTimer = 7;
            ringFrame = (ringFrame + 1) & 3;
        }
        aizVineAngle = (aizVineAngle + AIZ_VINE_ANGLE_STEP) & 0xFFFF;
    }

    int ringTimer() { return ringTimer; }
    int ringFrame() { return ringFrame; }
    void restoreRingAnimation(int timer, int frame) {
        ringTimer = timer & 0xFF;
        ringFrame = frame & 3;
    }
    void resetFreshLevelRingAnimation() {
        // loc_60DE clears Oscillating_table through (excluding) AIZ_vine_angle.
        // Ring timer/frame lie inside this range; seamless reload skips it.
        restoreRingAnimation(0, 0);
    }

    /**
     * {@code Level/loc_64DC}: {@code move.w #$16,(Palette_fade_timer).w} before LevelLoop
     * (sonic3k.asm:7877). Seamless reloads never reach it.
     */
    void armFreshLevelPaletteFade() {
        paletteFadeTimer = FRESH_LEVEL_PALETTE_FADE_FRAMES;
    }

    /**
     * {@code Animate_Palette} (sonic3k.asm:5018): while the timer is non-zero the frame runs
     * Pal_FromBlack/Pal_FromWhite and decrements it instead of calling AnPal_Load.
     *
     * @return true when this level frame spends the fade instead of animating palettes
     */
    boolean consumePaletteFadeFrame() {
        if (paletteFadeTimer <= 0) {
            return false;
        }
        paletteFadeTimer--;
        return true;
    }

    int paletteFadeTimer() { return paletteFadeTimer; }
    void restorePaletteFadeTimer(int value) { paletteFadeTimer = (short)value; }

    int paletteCycleCounter0() { return paletteCycleCounter0; }
    void setPaletteCycleCounter0(int value) { paletteCycleCounter0 = value & 0xFFFF; }
    int paletteCycleCounter1() { return paletteCycleCounter1; }

    /** Word at {@code Palette_cycle_counters+offset}. */
    int paletteCycleWord(int offset) {
        return ((paletteCycleCounters[offset] & 0xFF) << 8) | (paletteCycleCounters[offset + 1] & 0xFF);
    }

    void setPaletteCycleWord(int offset, int value) {
        paletteCycleCounters[offset] = (byte) (value >> 8);
        paletteCycleCounters[offset + 1] = (byte) value;
    }

    /**
     * {@code subq.w #1,(Palette_cycle_counter1).w / bpl.s skip / move.w #reload}.
     *
     * @return true when the timer expired and the routine runs this call
     */
    boolean expirePaletteCycleCounter1(int reload) {
        paletteCycleCounter1 = (paletteCycleCounter1 - 1) & 0xFFFF;
        if ((short) paletteCycleCounter1 >= 0) {
            return false;
        }
        paletteCycleCounter1 = reload & 0xFFFF;
        return true;
    }

    /** The same word timer at {@code Palette_cycle_counters+offset}. */
    boolean expirePaletteCycleWord(int offset, int reload) {
        int value = (paletteCycleWord(offset) - 1) & 0xFFFF;
        if ((short) value >= 0) {
            setPaletteCycleWord(offset, value);
            return false;
        }
        setPaletteCycleWord(offset, reload);
        return true;
    }

    void markAnimatePaletteBeforeReload(int levelFrame) { animatePaletteBeforeReloadFrame = levelFrame; }

    /** @return true when this LevelLoop iteration's Animate_Palette already ran for the outgoing act */
    boolean consumeAnimatePaletteBeforeReload(int levelFrame) {
        boolean ran = animatePaletteBeforeReloadFrame != NO_FRAME && animatePaletteBeforeReloadFrame == levelFrame;
        animatePaletteBeforeReloadFrame = NO_FRAME;
        return ran;
    }

    /** Level loc_60DE clears {@code Lag_frame_count} up to {@code Nem_decomp_queue} (sonic3k.asm:7652). */
    void resetFreshLevelPaletteCycles() {
        paletteCycleCounter0 = 0;
        paletteCycleCounter1 = 0;
        java.util.Arrays.fill(paletteCycleCounters, (byte) 0);
    }

    void capturePaletteCycles(java.nio.ByteBuffer buffer) {
        buffer.putShort((short) paletteCycleCounter0).putShort((short) paletteCycleCounter1)
                .put(paletteCycleCounters);
    }

    void restorePaletteCycles(java.nio.ByteBuffer buffer) {
        paletteCycleCounter0 = buffer.getShort() & 0xFFFF;
        paletteCycleCounter1 = buffer.getShort() & 0xFFFF;
        buffer.get(paletteCycleCounters);
    }

    int aizVineAngleWord() {
        return aizVineAngle;
    }

    void restoreAizVineAngleWord(int value) {
        aizVineAngle = value & 0xFFFF;
    }
}

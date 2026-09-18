package com.openggf.game.sonic3k;

/**
 * Runtime-owned S3K animation words advanced by {@code ChangeRingFrame}.
 */
final class Sonic3kGlobalAnimationState {
    private static final int AIZ_VINE_ANGLE_STEP = 0x180;

    private int aizVineAngle;
    private int ringTimer;
    private int ringFrame;
    /** {@code Palette_fade_timer}: level frames left before {@code Animate_Palette} reaches AnPal. */
    private int paletteFadeTimer;

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
        paletteFadeTimer = 0x16;
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
    void restorePaletteFadeTimer(int value) { paletteFadeTimer = Math.max(0, value); }

    int aizVineAngleWord() {
        return aizVineAngle;
    }

    void restoreAizVineAngleWord(int value) {
        aizVineAngle = value & 0xFFFF;
    }
}

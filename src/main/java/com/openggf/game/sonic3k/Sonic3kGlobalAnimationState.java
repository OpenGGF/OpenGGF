package com.openggf.game.sonic3k;

/**
 * Runtime-owned S3K animation words advanced by {@code ChangeRingFrame}.
 */
final class Sonic3kGlobalAnimationState {
    private static final int AIZ_VINE_ANGLE_STEP = 0x180;

    private int aizVineAngle;
    private int ringTimer;
    private int ringFrame;

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

    int aizVineAngleWord() {
        return aizVineAngle;
    }

    void restoreAizVineAngleWord(int value) {
        aizVineAngle = value & 0xFFFF;
    }
}

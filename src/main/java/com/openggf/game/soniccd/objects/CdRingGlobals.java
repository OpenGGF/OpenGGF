package com.openggf.game.soniccd.objects;

/**
 * Immutable native Ring-visible globals, not fresh-load defaults or an independent mutable bank.
 * Pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, src/_Include/Main CPU Variables.inc:56-65,156-160.
 * Parent owns their one authoritative store/capture and all other native writers.
 */
public record CdRingGlobals(int rings, int lives, int livesFlags, int updateHudRings, int updateHudLives,
                            int timeStopTimer, int ringAnimationTimer, int ringAnimationFrame) {
    public CdRingGlobals {
        requireWidth(rings, 0xFFFF, "rings");
        requireWidth(lives, 0xFF, "lives");
        requireWidth(livesFlags, 0xFF, "livesFlags");
        requireWidth(updateHudRings, 0xFF, "updateHudRings");
        requireWidth(updateHudLives, 0xFF, "updateHudLives");
        requireWidth(timeStopTimer, 0xFFFF, "timeStopTimer");
        requireWidth(ringAnimationTimer, 0xFF, "ringAnimationTimer");
        requireWidth(ringAnimationFrame, 0xFF, "ringAnimationFrame");
    }

    /**
     * Only UpdateGlobalAnims.Rings (Level/Main.asm:520-525). Parent calls once at the
     * unpaused native loop tail, after sprite build/art work (:421-430), never per Ring.
     * SUBQ.B/BPL tests the resulting sign bit, including $80->$7F and $81->$80.
     * timeStopTimer is neither read nor decremented by this routine. Other global anims are unowned.
     */
    public CdRingGlobals advanceGlobalRingAnimation() {
        int timer = (ringAnimationTimer - 1) & 0xFF;
        int frame = ringAnimationFrame;
        if ((timer & 0x80) != 0) {
            timer = 7;
            frame = (frame + 1) & 3;
        }
        return new CdRingGlobals(rings, lives, livesFlags, updateHudRings, updateHudLives,
                timeStopTimer, timer, frame);
    }

    private static void requireWidth(int value, int mask, String name) {
        if ((value & ~mask) != 0) { throw new IllegalArgumentException(name + " exceeds native width"); }
    }
}

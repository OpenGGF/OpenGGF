package com.openggf.game.soniccd.objects;

/**
 * Immutable native ring-spill globals: ringLossAnimTimer, ringLossAnimAccum and ringLossAnimFrame
 * (src/_Include/Main CPU Variables.inc:163-165 at pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff).
 * Not a fresh-load default, reset policy or independent mutable bank: the parent owns the one store,
 * its capture/restore and the level-init clear (.ClearFlowers, Level/Main.asm:178-184).
 * ObjLostRing_Init writes only the timer byte; ObjLostRing_Main reads the timer and frame.
 * Rings, lives and HUD bytes stay in CdRingGlobals.
 */
public record CdRingSpillGlobals(int timerByte, int accumWord, int frameByte) {
    public CdRingSpillGlobals {
        requireWidth(timerByte, 0xFF, "ringLossAnimTimer");
        requireWidth(accumWord, 0xFFFF, "ringLossAnimAccum");
        requireWidth(frameByte, 0xFF, "ringLossAnimFrame");
    }

    /**
     * Only UpdateGlobalAnims.RingSpill (Level/Main.asm:536-549). The parent calls it once per unpaused
     * native loop tail, after .Rings (:520-525), never per ring, render, restore or pause.
     */
    public CdRingSpillGlobals advanceTail() {
        if (timerByte == 0) { return this; }                   // TST.B ringLossAnimTimer / BEQ.S .End
        int d0 = (timerByte + accumWord) & 0xFFFF;             // MOVEQ #0,d0 / MOVE.B timer,d0: zero-extended / ADD.W accum,d0
        int rotated = ((d0 << 7) | (d0 >>> 9)) & 0xFFFF;       // MOVE.W d0,accum / ROL.W #7,d0
        return new CdRingSpillGlobals((timerByte - 1) & 0xFF,  // SUBQ.B #1,ringLossAnimTimer
                d0, rotated & 3);                              // ANDI.W #3,d0 / MOVE.B d0,ringLossAnimFrame
    }

    private static void requireWidth(int value, int mask, String name) {
        if ((value & ~mask) != 0) { throw new IllegalArgumentException(name + " exceeds native width"); }
    }
}

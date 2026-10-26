package com.openggf.game.soniccd.player;

/**
 * Detached actor value for one normal-sized R11A MODE call, not ObjSonic RAM or a frame.
 * Source: scddisasm
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Level/_Objects/Sonic/Main.asm:675-2345.
 * Complete centre longs include all 16 fraction bits. Speeds/move lock are signed
 * words; remaining fields are unsigned bytes. No implicit source initialization.
 * previousPrimary/SecondaryAngle are the actor's PREVIOUS copies, consumed by
 * BalanceGround. MODE never performs Main's later global-to-actor angle copy.
 * Restore by reusing this value with the corresponding Environment and input latch.
 */
public record CdPlayerMotionState(
        int centreX16_16, int centreY16_16, int xVelocity, int yVelocity, int groundVelocity,
        int routine, int angle, int flags, int spriteFlags, int xRadius, int yRadius,
        int animation, int previousAnimation, int playerJump, int charge, int moveLock,
        int stick, int playerControl, int previousPrimaryAngle, int previousSecondaryAngle) {
    public CdPlayerMotionState {
        signedWord(xVelocity); signedWord(yVelocity); signedWord(groundVelocity); signedWord(moveLock);
        byteValue(routine); byteValue(angle); byteValue(flags); byteValue(spriteFlags);
        byteValue(xRadius); byteValue(yRadius); byteValue(animation); byteValue(previousAnimation);
        byteValue(playerJump); byteValue(charge); byteValue(stick); byteValue(playerControl);
        byteValue(previousPrimaryAngle); byteValue(previousSecondaryAngle);
    }
    private static void byteValue(int value) {
        if ((value & ~0xFF) != 0) throw new IllegalArgumentException("Expected an unsigned native byte");
    }
    private static void signedWord(int value) {
        if (value != (short) value) throw new IllegalArgumentException("Expected a signed native word");
    }
}

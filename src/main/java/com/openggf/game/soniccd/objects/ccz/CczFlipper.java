package com.openggf.game.soniccd.objects.ccz;

/**
 * The part of the pinball flipper object ({@code ObjFlipper}, object {@code $1E}, owner {@code shared}) that the player's
 * launch routine reads and writes (Level/_Objects/Sonic/Main.asm:3112-3158, {@code ObjSonic_ChkFlipper}). The shared flipper
 * implements it so {@link CczFlipperLaunch} never names the class.
 */
public interface CczFlipper {
    /** {@code oX} of the flipper. */
    int flipperX();

    /** {@code oY} of the flipper. */
    int flipperY();

    /** {@code oWidth} of the flipper. */
    int flipperWidth();

    /** {@code btst #0,oFlags(a1)}: the flipper is flipped horizontally. */
    boolean flipperFlippedHorizontally();

    /** {@code move.b #1,oAnim(a1)}: start the flipper's swing animation. */
    void flipperSwing();
}

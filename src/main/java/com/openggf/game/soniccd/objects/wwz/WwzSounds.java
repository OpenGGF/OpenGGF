package com.openggf.game.soniccd.objects.wwz;

/**
 * FM sound IDs the Wacky Workbench objects request: {@code FM_START} is $90 and each name follows
 * the last (Sound Drivers/FM/_Sound IDs.inc:7-49, 84).
 */
final class WwzSounds {
    /** {@code FM_JUMP} (_Sound IDs.inc:12). */
    static final int JUMP = 0x92;
    /** {@code FM_EXPLODE} (_Sound IDs.inc:24). */
    static final int EXPLODE = 0x9E;
    /** {@code FM_B2}, the electric beams' charge (_Sound IDs.inc:44). */
    static final int B2 = 0xB2;
    /** {@code FM_B4}, the bouncing platform's bounce (_Sound IDs.inc:46). */
    static final int B4 = 0xB4;
    /** {@code FM_B7}, the Minomusi's spikes (_Sound IDs.inc:49). */
    static final int B7 = 0xB7;

    private WwzSounds() { }
}

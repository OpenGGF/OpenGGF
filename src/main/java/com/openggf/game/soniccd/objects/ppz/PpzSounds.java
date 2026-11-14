package com.openggf.game.soniccd.objects.ppz;

/**
 * The FM sound IDs Palmtree Panic objects request (Sound Drivers/FM/_Sound IDs.inc:9-60, {@code rsset $90}),
 * sent through the object services as native IDs like {@code CdSoundRequests.SHIELD}.
 */
final class PpzSounds {
    private PpzSounds() { }

    /** {@code FM_A0}: the spinning disc's jump (ObjSpinningDisc_CheckJump, Spinning Disc/Main.asm:157-190). */
    static final int FM_A0 = 0xA0;
    /** {@code FM_A3}: the collapsing platform's break and the Sonic hole's mark (ObjCollapsePlatform_Main, Collapsing Platform/Main.asm:81-84; ObjSonicHole_Main, Tunnel Path/Main.asm:50-77). */
    static final int FM_A3 = 0xA3;
    /** {@code FM_B0}: the breakable wall's break (ObjBreakableWall_Main .BreakUp, Breakable Wall/Main.asm:56-58). */
    static final int FM_B0 = 0xB0;
}

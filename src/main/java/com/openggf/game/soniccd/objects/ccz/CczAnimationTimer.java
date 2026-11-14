package com.openggf.game.soniccd.objects.ccz;

/**
 * The read side of {@code aniArtTimers}, the byte {@code UpdateAnimTiles} counts down from 10 in the Collision Chaos
 * stages with animated terrain tiles (Animated Tiles Update (Act 2).asm:48-85). The drum platform only moves on frames
 * where it is zero (Drum Platform/Main.asm:158-160, 184-186, 220-222). The stage's animated-pattern manager implements this
 * interface (zone lane: {@code CczAnimatedTiles}); without it the byte is 0, as in the stages whose
 * {@code UpdateAnimTiles} is the one-instruction stub ((Act 1 Present).asm:17-18).
 */
public interface CczAnimationTimer {
    /** {@code aniArtTimers}. */
    int aniArtTimer();
}

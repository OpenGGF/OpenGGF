package com.openggf.game.internal;

/**
 * Game service marker for a layout walk that precedes every slot, including the player.
 * It uses the camera left by the previous frame.
 *
 * <p>All Sonic CD stage loops make these calls in the same order:
 * {@code Level_MainLoop} (Level/Main.asm:400-403) for r1, r3 and one r6 program;
 * {@code StageMainLoopUpdate} (common/main.asm:406-410) for r5 and r6 variants and r7 and r8;
 * {@code StageMainLoopUpdate} (r4/main.asm:379-383) for r4;
 * and {@code loc_2018A6} (r5/main.asm:353-357) for the other r5 programs.
 */
public interface SpawnerBeforeObjectPass {
}

package com.openggf.game.internal;

/**
 * Engine-only: a level whose player collision reads choose the collision chunk of a loop
 * chunk from stage state as well as from the player's loop path. The shared layout lookup
 * asks it for every player probe of the foreground, on or off the loop path; objects keep
 * the plain lookup. Sonic CD's {@code GetLevelBlock} is the implementer: its loop-chunk
 * branch differs per zone and reads the stage's {@code layer} byte (scddisasm
 * src/Level/Collision Floor.asm:483-485, 557-650).
 */
public interface PlayerLoopChunks {
    /**
     * The block index the player's probe at layout cell ({@code mapX}, {@code mapY}) reads,
     * given the raw index there and whether the player is on the loop path.
     */
    int playerCollisionBlockIndex(int rawBlockIndex, int mapX, int mapY, boolean onLoopPath);
}

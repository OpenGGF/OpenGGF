package com.openggf.game.internal;

/**
 * Engine-only: a level whose collision routines decide "blank" by comparing the block pointer
 * with the chunk data's base. Sonic CD's {@code GetLevelBlock} returns that base for a blank
 * layout cell, and the block of chunk {@code n} at cell (cx, cy) lies at
 * {@code base + (n - 1) * $200 + cy * $20 + cx * 2}, so chunk $01's top-left block has the same
 * pointer: {@code FindLevelFloor}, {@code FindLevelFloor2}, {@code FindLevelWall} and
 * {@code FindLevelWall2} all take {@code .IsBlank} on {@code cmpi.l #LevelChunks,d1} for it
 * (scddisasm src/Level/Collision Floor.asm:481-484, 513-515, 654-667, 687-688, 778-779, 876-877,
 * 958-959; FixBugs = 0). The level manager's windowed collision lookup reads that block as blank
 * for every probe, the player's and the objects'.
 */
public interface CollisionChunkOriginReadsBlank {
    /** The chunk whose block at cell (0, 0) aliases the blank chunk's pointer. */
    int ALIASED_CHUNK = 1;
}

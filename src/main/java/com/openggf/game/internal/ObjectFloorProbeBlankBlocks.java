package com.openggf.game.internal;

import com.openggf.game.GameModule;

/**
 * What an object's downward floor probe returns when the block it starts in and the block
 * below are both blank (or read as blank). The ROM's {@code FindFloor2 .isblank} returns
 * {@code $F - (y & $F)} and {@code FindFloor} adds {@code $10}, so the distance is
 * {@code $1F - (y & $F)}; the engine's object probe has reported "no surface" there instead.
 * A host whose objects are certified on the ROM's distance registers
 * {@link #NEXT_BLOCK_DISTANCE} (scddisasm src/Level/Collision Floor.asm:685-702, 776-793).
 */
public enum ObjectFloorProbeBlankBlocks {
    /** {@code TerrainCheckResult.noCollision()}: the model the other games are certified on. */
    NO_SURFACE,
    /** {@code $1F - (y & $F)}, with the angle the last solid block read wrote (else 0). */
    NEXT_BLOCK_DISTANCE;

    public static ObjectFloorProbeBlankBlocks forHost(GameModule host) {
        ObjectFloorProbeBlankBlocks model = host == null ? null : host.getGameService(ObjectFloorProbeBlankBlocks.class);
        return model == null ? NO_SURFACE : model;
    }
}

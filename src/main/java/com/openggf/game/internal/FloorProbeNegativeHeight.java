package com.openggf.game.internal;

import com.openggf.game.GameModule;

/**
 * What a downward floor probe makes of a tile column whose height is negative (solid from the
 * tile's top edge). Sonic 1/2/3&amp;K's {@code FindFloor} adds the probe's offset in the tile and
 * either extends to the next tile or regresses to the one above
 * (docs/s1disasm/_incObj/sub FindNearestTile &amp; FindFloor &amp; FindWall.asm:174-185). A host
 * whose floor routine treats it as a flat full-height block registers {@link #FULL_HEIGHT_FLAT}.
 */
public enum FloorProbeNegativeHeight {
    /** The probe offset decides between the next tile and the tile above. */
    OFFSET_DECIDES,
    /** Height $10 and angle 0 at this tile, with no regress to the tile above. */
    FULL_HEIGHT_FLAT;

    public static FloorProbeNegativeHeight forHost(GameModule host) {
        FloorProbeNegativeHeight model = host == null ? null : host.getGameService(FloorProbeNegativeHeight.class);
        return model == null ? OFFSET_DECIDES : model;
    }
}

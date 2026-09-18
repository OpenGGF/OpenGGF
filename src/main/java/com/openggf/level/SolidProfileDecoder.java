package com.openggf.level;

import java.util.Arrays;

/** Internal construction of independent collision profiles from decoded byte arrays. */
public final class SolidProfileDecoder {
    private SolidProfileDecoder() {}

    public static SolidTile[] decode(byte[] heights, byte[] widths, byte[] angles) {
        SolidTile[] tiles = new SolidTile[angles.length];
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = new SolidTile(i,
                    Arrays.copyOfRange(heights, i * SolidTile.TILE_SIZE_IN_ROM,
                            (i + 1) * SolidTile.TILE_SIZE_IN_ROM),
                    Arrays.copyOfRange(widths, i * SolidTile.TILE_SIZE_IN_ROM,
                            (i + 1) * SolidTile.TILE_SIZE_IN_ROM), angles[i]);
        }
        return tiles;
    }
}

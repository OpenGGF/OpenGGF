package com.openggf.level.render;

/**
 * One sprite piece from a mapping frame.
 */
@com.openggf.game.ModApi
public record SpriteMappingPiece(
        int xOffset,
        int yOffset,
        int widthTiles,
        int heightTiles,
        int tileIndex,
        boolean hFlip,
        boolean vFlip,
        int paletteIndex,
        boolean priority
) implements SpriteFramePiece {

    /**
     * Backward-compatible constructor without priority flag (defaults to false).
     */
    public SpriteMappingPiece(int xOffset, int yOffset, int widthTiles, int heightTiles,
            int tileIndex, boolean hFlip, boolean vFlip, int paletteIndex) {
        this(xOffset, yOffset, widthTiles, heightTiles, tileIndex, hFlip, vFlip, paletteIndex, false);
    }
}

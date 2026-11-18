package com.openggf.game.soniccd.special;

import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.SpecialStageViewport;

/** Immutable colour indices beside the original floor projection. */
final class CdSpecialStageFloorSides {
    static final int TILE_EDGE = 8;
    private final int width;
    private final byte[] left;
    private final byte[] right;

    private CdSpecialStageFloorSides(CdSpecialStage stage, int width) {
        this.width = width;
        left = new byte[width * CdStampFloor.IMAGE_HEIGHT];
        right = new byte[left.length];
        stage.floor().renderRange(stage.trace(), -width, left);
        stage.floor().renderRange(stage.trace(), CdStampFloor.IMAGE_WIDTH, right);
    }

    static CdSpecialStageFloorSides project(CdSpecialStage stage, int logicalWidth) {
        if (logicalWidth <= SpecialStageViewport.NATIVE_WIDTH) return null;
        int width = Math.min(logicalWidth, WidescreenAspect.SUPER_32_9.pixelWidth());
        // Round both sides up to full tiles. The viewport clips any extra columns.
        int sideWidth = ((width - CdStampFloor.IMAGE_WIDTH + 15) / 16) * TILE_EDGE;
        return new CdSpecialStageFloorSides(stage, sideWidth);
    }

    int width() { return width; }

    void readTile(boolean rightSide, int x, int y, byte[] tile) {
        byte[] pixels = rightSide ? right : left;
        for (int row = 0; row < TILE_EDGE; row++) {
            int start = (y + row) * width + x;
            for (int col = 0; col < TILE_EDGE; col += 2) {
                tile[row * 4 + col / 2] = (byte) ((pixels[start + col] << 4) | pixels[start + col + 1]);
            }
        }
    }
}

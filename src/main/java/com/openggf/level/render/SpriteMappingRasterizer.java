package com.openggf.level.render;

import com.openggf.level.Pattern;
import java.util.List;

/**
 * Draws one Mega Drive sprite mapping frame into detached {@code 0xAARRGGBB} pixels.
 * Tiles are laid out column by column, mirrored per piece and coloured from the
 * piece's palette line. Colour 0 is transparent; earlier pieces cover later ones.
 * This CPU-only helper reads caller-supplied art and colours without touching live
 * rendering services or ROM state. Engine-internal.
 */
public final class SpriteMappingRasterizer {
    private SpriteMappingRasterizer() {
    }

    /** Newly allocated row-major pixels and the sprite origin measured from their top-left. */
    public record RasterizedFrame(int width, int height, int originX, int originY, int[] argb) {
    }

    /** {@code tiles} is the art (or the frame's DPLC bank); {@code palette} has 64 ARGB colours. */
    public static RasterizedFrame rasterize(SpriteMappingFrame frame, Pattern[] tiles, int[] palette,
            int paletteLine, int tileOffset) {
        List<SpriteMappingPiece> pieces = frame == null ? List.of() : frame.pieces();
        if (pieces.isEmpty()) {
            return new RasterizedFrame(1, 1, 0, 0, new int[1]);
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (SpriteMappingPiece p : pieces) {
            minX = Math.min(minX, p.xOffset());
            minY = Math.min(minY, p.yOffset());
            maxX = Math.max(maxX, p.xOffset() + p.widthTiles() * 8);
            maxY = Math.max(maxY, p.yOffset() + p.heightTiles() * 8);
        }
        int width = Math.max(1, maxX - minX);
        int height = Math.max(1, maxY - minY);
        int[] out = new int[width * height];
        // The first piece in a mapping frame has the highest priority, so draw it last.
        for (int i = pieces.size() - 1; i >= 0; i--) {
            SpriteMappingPiece p = pieces.get(i);
            int line = (paletteLine + p.paletteIndex()) & 3;
            for (int col = 0; col < p.widthTiles(); col++) {
                for (int row = 0; row < p.heightTiles(); row++) {
                    int tileIndex = p.tileIndex() - tileOffset + col * p.heightTiles() + row;
                    if (tileIndex < 0 || tileIndex >= tiles.length || tiles[tileIndex] == null) {
                        continue;
                    }
                    Pattern tile = tiles[tileIndex];
                    int destCol = p.hFlip() ? p.widthTiles() - 1 - col : col;
                    int destRow = p.vFlip() ? p.heightTiles() - 1 - row : row;
                    int baseX = p.xOffset() - minX + destCol * 8;
                    int baseY = p.yOffset() - minY + destRow * 8;
                    for (int py = 0; py < 8; py++) {
                        for (int px = 0; px < 8; px++) {
                            int index = tile.getPixel(p.hFlip() ? 7 - px : px, p.vFlip() ? 7 - py : py) & 0x0F;
                            if (index == 0) {
                                continue;
                            }
                            int x = baseX + px;
                            int y = baseY + py;
                            if (x >= 0 && y >= 0 && x < width && y < height) {
                                out[y * width + x] = palette[line * 16 + index];
                            }
                        }
                    }
                }
            }
        }
        return new RasterizedFrame(width, height, -minX, -minY, out);
    }
}

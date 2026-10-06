package com.openggf.level.render;

import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Level;
import com.openggf.level.LevelConstants;
import com.openggf.level.Map;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;

/**
 * Draws layout layers into {@code 0xAARRGGBB} pixels on the CPU, for presentation copies of
 * a level (mod scene backdrops and overviews). It follows the hardware's own nesting, as
 * {@code tools.PlaneOpacityProbe} does: layout cell, 128px block, 16x16 chunk descriptor and
 * 8x8 pattern descriptor, each with its H/V flip, then the descriptor's palette line. Colour 0
 * is transparent and finally shows {@code backdropArgb}, as on the VDP. Priority is ignored.
 * Tiles come from a caller-supplied {@link TileSource} (the level's tiles with any presentation
 * overrides), so no level, GPU or live service is modified. A cell naming a block or chunk the
 * level does not have, or a pattern the source lacks, throws rather than rendering garbage.
 */
public final class PlaneRasterizer {
    /** Layout layer 0: the foreground plane. */
    public static final int FOREGROUND = 0;
    /** Layout layer 1: the background plane. */
    public static final int BACKGROUND = 1;

    private PlaneRasterizer() {
    }

    /**
     * The 8x8 tile a plane shows for a pattern index. Presentation copies may vary it by
     * position where the game swaps art as the camera moves; null means no such tile.
     */
    @FunctionalInterface
    public interface TileSource {
        Pattern tile(int layer, int patternIndex, int layoutX);

        /** The same tiles everywhere. */
        static TileSource of(Pattern[] tiles) {
            return (layer, index, x) -> index < tiles.length ? tiles[index] : null;
        }
    }

    /**
     * Rasterises {@code width x height} pixels of {@code layer} starting at layout pixel
     * ({@code x}, {@code y}), both inside the layer's layout.
     */
    public static int[] rasterize(Level level, TileSource patterns, int layer, int x, int y, int width, int height,
            int backdropArgb) {
        int[][] colours = palette(level);
        TileRow row = new TileRow(x, width);
        int[] out = new int[width * height];
        int resolvedTileRow = Integer.MIN_VALUE;
        for (int py = y; py < y + height; py++) {
            if ((py >> 3) != resolvedTileRow) {
                resolvedTileRow = py >> 3;
                row.resolve(level, patterns, layer, resolvedTileRow << 3, false);
            }
            int base = (py - y) * width;
            for (int px = x; px < x + width; px++) {
                int colour = row.colour(px, py, colours);
                out[base + px - x] = colour == 0 ? backdropArgb : colour;
            }
        }
        return out;
    }

    /**
     * How the background repeats behind an overview. It sits at the same world coordinates as
     * the foreground and repeats vertically every {@code height} rows (at most the layout's
     * height). Background rows {@code fixedTop .. fixedBottom - 1} repeat every
     * {@code fixedPeriod} pixels, as the plane does where the game draws them from a fixed X;
     * other rows repeat at the layout's width.
     */
    public record BackgroundWrap(int height, int fixedTop, int fixedBottom, int fixedPeriod) {
        public BackgroundWrap {
            if (height <= 0 || height % Pattern.PATTERN_HEIGHT != 0 || fixedPeriod <= 0
                    || fixedPeriod % Pattern.PATTERN_WIDTH != 0) {
                throw new IllegalArgumentException("Invalid background wrap");
            }
        }

        /** The background layout as it is: no fixed rows. */
        public static BackgroundWrap layout(Level level) {
            int size = level.getBlockPixelSize();
            return new BackgroundWrap(level.getLayerHeightBlocks(BACKGROUND) * size, 0, 0,
                    level.getLayerWidthBlocks(BACKGROUND) * size);
        }
    }

    /**
     * The world rectangle ({@code x}, {@code y}, {@code width}, {@code height}) of the
     * foreground composited over the background (repeating as {@code wrap} says), then shrunk
     * by {@code divisor} with box averaging: each output pixel is the rounded mean colour of
     * its {@code divisor x divisor} block (edge blocks average the pixels they have). Returns
     * {@code ceil(width / divisor) x ceil(height / divisor)} opaque pixels, built row by row
     * without a full-size intermediate image.
     */
    public static int[] compositeOverview(Level level, TileSource patterns, int x, int y, int width, int height,
            int divisor, int backdropArgb, BackgroundWrap wrap) {
        if (divisor < 1) {
            throw new IllegalArgumentException("divisor must be positive");
        }
        int[][] colours = palette(level);
        int blockSize = level.getBlockPixelSize();
        int bgWidth = level.getLayerWidthBlocks(BACKGROUND) * blockSize;
        int bgHeight = Math.min(wrap.height(), level.getLayerHeightBlocks(BACKGROUND) * blockSize);
        int outWidth = Math.ceilDiv(width, divisor);
        int outHeight = Math.ceilDiv(height, divisor);
        int[] out = new int[outWidth * outHeight];
        long[] red = new long[outWidth];
        long[] green = new long[outWidth];
        long[] blue = new long[outWidth];
        int[] count = new int[outWidth];
        TileRow front = new TileRow(x, width);
        TileRow back = new TileRow(0, bgWidth);
        int resolvedTileRow = Integer.MIN_VALUE;
        for (int py = y; py < y + height; py++) {
            int by = Math.floorMod(py, bgHeight);
            int period = by >= wrap.fixedTop() && by < wrap.fixedBottom()
                    ? Math.min(wrap.fixedPeriod(), bgWidth) : bgWidth;
            if ((py >> 3) != resolvedTileRow) {
                resolvedTileRow = py >> 3;
                front.resolve(level, patterns, FOREGROUND, resolvedTileRow << 3, true);
                back.resolve(level, patterns, BACKGROUND, by & ~7, false);
            }
            for (int px = x; px < x + width; px++) {
                int colour = front.colour(px, py, colours);
                if (colour == 0) {
                    colour = back.colour(Math.floorMod(px, period), by, colours);
                }
                if (colour == 0) {
                    colour = backdropArgb;
                }
                int column = (px - x) / divisor;
                red[column] += (colour >> 16) & 0xFF;
                green[column] += (colour >> 8) & 0xFF;
                blue[column] += colour & 0xFF;
                count[column]++;
            }
            int outRow = (py - y) / divisor;
            if (py == y + height - 1 || (py + 1 - y) / divisor != outRow) {
                for (int column = 0; column < outWidth; column++) {
                    int n = count[column];
                    out[outRow * outWidth + column] = 0xFF000000 | (int) ((red[column] + n / 2) / n) << 16
                            | (int) ((green[column] + n / 2) / n) << 8 | (int) ((blue[column] + n / 2) / n);
                }
                java.util.Arrays.fill(red, 0);
                java.util.Arrays.fill(green, 0);
                java.util.Arrays.fill(blue, 0);
                java.util.Arrays.fill(count, 0);
            }
        }
        return out;
    }

    /** One palette entry as opaque {@code 0xFFRRGGBB}. */
    public static int argb(Palette.Color color) {
        return 0xFF000000 | ((color.r & 0xFF) << 16) | ((color.g & 0xFF) << 8) | (color.b & 0xFF);
    }

    /** Palette lines as ARGB; colour 0 of each line is left 0 (transparent). */
    private static int[][] palette(Level level) {
        int[][] colours = new int[4][16];
        for (int line = 0; line < 4; line++) {
            Palette palette = level.getPalette(line);
            for (int i = 1; i < 16; i++) {
                colours[line][i] = argb(palette.getColor(i));
            }
        }
        return colours;
    }

    /**
     * The 8x8 tiles of one tile row across a span of layout X, resolved once per row: the
     * pattern and the combined chunk/pattern flips and palette line of each tile.
     */
    private static final class TileRow {
        private final int firstTileX;
        private final Pattern[] tiles;
        private final int[] attributes;
        private int tileY;

        TileRow(int x, int width) {
            this.firstTileX = x >> 3;
            int count = ((x + width - 1) >> 3) - firstTileX + 1;
            this.tiles = new Pattern[count];
            this.attributes = new int[count];
        }

        void resolve(Level level, TileSource patterns, int layer, int y, boolean lenientOutsideLayout) {
            tileY = y >> 3;
            Map map = level.getMap();
            int blockSize = level.getBlockPixelSize();
            for (int i = 0; i < tiles.length; i++) {
                int x = (firstTileX + i) << 3;
                tiles[i] = null;
                int column = x / blockSize;
                int row = y / blockSize;
                if (column >= level.getLayerWidthBlocks(layer) || row >= level.getLayerHeightBlocks(layer)) {
                    if (lenientOutsideLayout) {
                        continue;
                    }
                    throw unresolved("layout cell", column, x, y);
                }
                int blockId = map.getValue(layer, column, row) & 0xFF;
                if (blockId >= level.getBlockCount()) {
                    throw unresolved("block", blockId, x, y);
                }
                Block block = level.getBlock(blockId);
                ChunkDesc chunkDesc = block.getChunkDesc((x % blockSize) / LevelConstants.CHUNK_WIDTH,
                        (y % blockSize) / LevelConstants.CHUNK_HEIGHT);
                int chunkId = chunkDesc.getChunkIndex();
                if (chunkId >= level.getChunkCount()) {
                    throw unresolved("chunk", chunkId, x, y);
                }
                // The tile's half of the 16x16 chunk, mirrored by the chunk's own flip.
                int halfX = (x % LevelConstants.CHUNK_WIDTH) / Pattern.PATTERN_WIDTH;
                int halfY = (y % LevelConstants.CHUNK_HEIGHT) / Pattern.PATTERN_HEIGHT;
                if (chunkDesc.getHFlip()) {
                    halfX = 1 - halfX;
                }
                if (chunkDesc.getVFlip()) {
                    halfY = 1 - halfY;
                }
                PatternDesc patternDesc = level.getChunk(chunkId).getPatternDesc(halfX, halfY);
                int patternIndex = patternDesc.getPatternIndex();
                Pattern pattern = patterns.tile(layer, patternIndex, x);
                if (pattern == null) {
                    throw unresolved("pattern", patternIndex, x, y);
                }
                boolean hFlip = chunkDesc.getHFlip() ^ patternDesc.getHFlip();
                boolean vFlip = chunkDesc.getVFlip() ^ patternDesc.getVFlip();
                tiles[i] = pattern;
                attributes[i] = (hFlip ? 1 : 0) | (vFlip ? 2 : 0) | (patternDesc.getPaletteIndex() & 3) << 2;
            }
        }

        /** The pixel's ARGB colour, or 0 when it is transparent or outside the layout. */
        int colour(int x, int y, int[][] colours) {
            int i = (x >> 3) - firstTileX;
            Pattern tile = tiles[i];
            if (tile == null || (y >> 3) != tileY) {
                return 0;
            }
            int attribute = attributes[i];
            int px = (attribute & 1) != 0 ? 7 - (x & 7) : x & 7;
            int py = (attribute & 2) != 0 ? 7 - (y & 7) : y & 7;
            int index = tile.getPixel(px, py) & 0xF;
            return index == 0 ? 0 : colours[attribute >> 2][index];
        }
    }

    private static IllegalStateException unresolved(String what, int index, int x, int y) {
        return new IllegalStateException("Layout pixel (" + x + ", " + y + ") names missing " + what + " " + index);
    }
}

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
import com.openggf.level.SolidTile;
import java.util.Objects;

/**
 * An act's building blocks for presentation copies that assemble their own terrain (mod scenes'
 * {@code SceneRomArt.levelKit}): the foreground layout as a grid of layout blocks (128 or 256
 * pixels square), every block rendered on its own with transparent sky, every block's
 * primary-path collision as a pixel mask, the playable area, the background and the palette.
 * Built from a detached level and a {@link PlaneRasterizer.TileSource}; it never touches a live
 * level, graphics or settings. Block pictures and masks are computed on request and not cached.
 */
public final class DetachedLevelKit {
    /** Mask value: no collision. */
    public static final byte EMPTY = 0;
    /** Mask value: a floor that can be stood on from above only. */
    public static final byte TOP_SOLID = 1;
    /** Mask value: solid from every side. */
    public static final byte SOLID = 2;

    /** Primary path: top solidity (floors) and left/right/bottom solidity (walls, ceilings). */
    private static final int TOP_SOLID_BIT = 0x0C;
    private static final int LRB_SOLID_BIT = 0x0D;
    private static final int CELL = LevelConstants.CHUNK_WIDTH;

    private final Level level;
    private final PlaneRasterizer.TileSource tiles;
    private final int[] playable;
    private final ZonePictureSource.Backdrop backdrop;

    /**
     * @param playable the area the camera can show, {@code {x, y, width, height}} in level pixels
     * @param backdrop the zone's background, or null for the background plane as laid out
     */
    public DetachedLevelKit(Level level, PlaneRasterizer.TileSource tiles, int[] playable,
            ZonePictureSource.Backdrop backdrop) {
        this.level = Objects.requireNonNull(level, "level");
        this.tiles = Objects.requireNonNull(tiles, "tiles");
        if (playable == null || playable.length != 4 || playable[2] < 1 || playable[3] < 1) {
            throw new IllegalArgumentException("Invalid playable area");
        }
        this.playable = playable.clone();
        this.backdrop = backdrop;
    }

    /** The side of one layout block in pixels: 128, or 256 in Sonic 1. */
    public int blockSize() {
        return level.getBlockPixelSize();
    }

    /** Foreground layout columns. */
    public int columns() {
        return level.getLayerWidthBlocks(PlaneRasterizer.FOREGROUND);
    }

    /** Foreground layout rows. */
    public int rows() {
        return level.getLayerHeightBlocks(PlaneRasterizer.FOREGROUND);
    }

    /** How many layout blocks the act has; block ids run from 0. */
    public int blockCount() {
        return level.getBlockCount();
    }

    /** The block id at a foreground layout cell, or 0 outside the layout. */
    public int blockAt(int column, int row) {
        if (column < 0 || row < 0 || column >= columns() || row >= rows()) {
            return 0;
        }
        Map map = level.getMap();
        int id = Byte.toUnsignedInt(map.getValue(PlaneRasterizer.FOREGROUND, column, row));
        return id < level.getBlockCount() ? id : 0;
    }

    /** The playable area, {@code {x, y, width, height}} in level pixels. */
    public int[] playable() {
        return playable.clone();
    }

    /** The four palette lines the act loads with, 64 {@code 0xFFRRGGBB} colours. */
    public int[] palette() {
        int[] out = new int[64];
        for (int line = 0; line < 4; line++) {
            Palette palette = level.getPalette(line);
            for (int i = 0; i < 16; i++) {
                out[line * 16 + i] = PlaneRasterizer.argb(palette.getColor(i));
            }
        }
        return out;
    }

    /** The background: the zone's own backdrop when the game has one, otherwise its plane. */
    public ZonePictureSource.Backdrop backdrop() {
        if (backdrop != null) {
            return backdrop;
        }
        int size = level.getBlockPixelSize();
        int width = Math.min(1024, Math.max(8, level.getLayerWidthBlocks(PlaneRasterizer.BACKGROUND) * size));
        int height = Math.min(512, Math.max(8, level.getLayerHeightBlocks(PlaneRasterizer.BACKGROUND) * size));
        int colour = PlaneRasterizer.argb(level.getPalette(2).getColor(0));
        int[] argb;
        try {
            argb = PlaneRasterizer.rasterize(level, tiles, PlaneRasterizer.BACKGROUND, 0, 0, width, height, colour);
        } catch (IllegalStateException unresolved) {
            argb = new int[width * height];
            java.util.Arrays.fill(argb, colour);
        }
        return new ZonePictureSource.Backdrop(new ZonePictureSource.Picture(width, height, argb),
                java.util.List.of(new ZonePictureSource.Band(0, height, 0.25, 0)));
    }

    /**
     * One layout block drawn on its own, {@code blockSize}² {@code 0xAARRGGBB} pixels row by row,
     * transparent where the plane shows what is behind it. Tiles the act does not load are left
     * transparent.
     */
    public int[] blockPixels(int blockId) {
        int size = blockSize();
        int[] out = new int[size * size];
        if (blockId <= 0 || blockId >= level.getBlockCount()) {
            return out;
        }
        int[][] colours = new int[4][16];
        for (int line = 0; line < 4; line++) {
            Palette palette = level.getPalette(line);
            for (int i = 1; i < 16; i++) {
                colours[line][i] = PlaneRasterizer.argb(palette.getColor(i));
            }
        }
        int layoutX = representativeX(blockId);
        Block block = level.getBlock(blockId);
        int cells = size / CELL;
        for (int cy = 0; cy < cells; cy++) {
            for (int cx = 0; cx < cells; cx++) {
                ChunkDesc desc = block.getChunkDesc(cx, cy);
                int chunkId = desc.getChunkIndex();
                if (chunkId < 0 || chunkId >= level.getChunkCount()) {
                    continue;
                }
                Chunk chunk = level.getChunk(chunkId);
                for (int half = 0; half < 4; half++) {
                    int hx = half & 1;
                    int hy = half >> 1;
                    int sx = desc.getHFlip() ? 1 - hx : hx;
                    int sy = desc.getVFlip() ? 1 - hy : hy;
                    PatternDesc pd = chunk.getPatternDesc(sx, sy);
                    Pattern pattern = tiles.tile(PlaneRasterizer.FOREGROUND, pd.getPatternIndex(), layoutX);
                    if (pattern == null) {
                        continue;
                    }
                    boolean hFlip = desc.getHFlip() ^ pd.getHFlip();
                    boolean vFlip = desc.getVFlip() ^ pd.getVFlip();
                    int[] line = colours[pd.getPaletteIndex() & 3];
                    int ox = cx * CELL + hx * 8;
                    int oy = cy * CELL + hy * 8;
                    for (int py = 0; py < 8; py++) {
                        for (int px = 0; px < 8; px++) {
                            int index = pattern.getPixel(hFlip ? 7 - px : px, vFlip ? 7 - py : py) & 0xF;
                            if (index != 0) {
                                out[(oy + py) * size + ox + px] = line[index];
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    /**
     * One layout block's primary-path collision, {@code blockSize}² values row by row:
     * {@link #EMPTY}, {@link #TOP_SOLID} or {@link #SOLID}. Each 16x16 cell's solid tile height
     * fills its column from the bottom (or from the top when the cell is flipped vertically),
     * as a floor or ceiling sensor sees it.
     */
    public byte[] blockSolidity(int blockId) {
        int size = blockSize();
        byte[] out = new byte[size * size];
        if (blockId <= 0 || blockId >= level.getBlockCount()) {
            return out;
        }
        Block block = level.getBlock(blockId);
        int cells = size / CELL;
        for (int cy = 0; cy < cells; cy++) {
            for (int cx = 0; cx < cells; cx++) {
                ChunkDesc desc = block.getChunkDesc(cx, cy);
                boolean top = desc.isSolidityBitSet(TOP_SOLID_BIT);
                boolean full = desc.isSolidityBitSet(LRB_SOLID_BIT);
                if (!top && !full) {
                    continue;
                }
                int chunkId = desc.getChunkIndex();
                if (chunkId < 0 || chunkId >= level.getChunkCount()) {
                    continue;
                }
                int solidIndex = level.getChunk(chunkId).getSolidTileIndex();
                if (solidIndex == 0 || solidIndex >= level.getSolidTileCount()) {
                    continue;
                }
                SolidTile tile = level.getSolidTile(solidIndex);
                byte value = full ? SOLID : TOP_SOLID;
                for (int column = 0; column < CELL; column++) {
                    int source = desc.getHFlip() ? CELL - 1 - column : column;
                    int metric = tile.getHeightAt((byte) source);
                    if (metric == 0) {
                        continue;
                    }
                    boolean fromTop = metric < 0 ^ desc.getVFlip();
                    int height = Math.min(CELL, Math.abs(metric));
                    for (int row = 0; row < height; row++) {
                        int y = fromTop ? row : CELL - 1 - row;
                        out[(cy * CELL + y) * size + cx * CELL + column] = value;
                    }
                }
            }
        }
        return out;
    }

    /** A layout X where the block appears (for tile sources that vary art by position). */
    private int representativeX(int blockId) {
        int columns = columns();
        int rows = rows();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if (blockAt(column, row) == blockId) {
                    return column * blockSize();
                }
            }
        }
        return Integer.MAX_VALUE / 2;
    }
}

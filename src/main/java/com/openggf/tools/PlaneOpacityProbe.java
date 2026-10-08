package com.openggf.tools;

import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Level;
import com.openggf.level.LevelConstants;
import com.openggf.level.Map;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;

/**
 * How much of a level plane is opaque, straight from the decoded ROM layout.
 *
 * <p>Purpose: answer "can the plane behind this one ever show through here?" without rendering
 * anything. A Genesis plane pixel with pattern index 0 is transparent, so a screen whose plane A
 * has no index-0 pixel can never show plane B, however the background scroll is computed. That
 * distinguishes a background-window defect (wrong pixels drawn) from plain occlusion (no pixels
 * reachable), which a before/after frame capture cannot tell apart -- both look byte-identical.
 *
 * <p>Inputs: the decoded {@link Level} (chunks, blocks, patterns) and its {@link Map} layout.
 * Nothing here reads the renderer, the camera or any runtime state, so a result is a statement
 * about ROM data at a coordinate, not about a particular frame.
 *
 * <p>Originating task: Lava Reef bring-up slice 5 follow-up, 2026-09-18 -- the act 1 dome
 * background lock draws no visible pixels. See
 * {@code docs/architecture/plans/2026-09-17-lrz-bring-up.md} and the Lava Reef entry in
 * {@code docs/status/s3k-known-bugs.md}.
 */
public final class PlaneOpacityProbe {

    /** Returned by {@link #pixelAt} when the coordinate falls outside the layout. */
    public static final int OUTSIDE_LAYOUT = -1;

    private PlaneOpacityProbe() {
    }

    /**
     * One sampled rectangle.
     *
     * @param sampledPixels     pixels inside the layout
     * @param outsideLayout     pixels whose layout cell does not exist
     * @param emptyChunkPixels  pixels whose 16x16 layout cell is chunk id 0
     * @param blankTilePixels   pixels whose 8x8 pattern index is 0
     * @param transparentPixels pixels whose pattern pixel is index 0 inside a real tile
     */
    public record Coverage(int sampledPixels,
                           int outsideLayout,
                           int emptyChunkPixels,
                           int blankTilePixels,
                           int transparentPixels) {

        /** Pixels through which the plane behind this one can be seen. */
        public int seeThroughPixels() {
            return outsideLayout + emptyChunkPixels + blankTilePixels + transparentPixels;
        }

        /** True when nothing behind this plane can appear anywhere in the sampled rectangle. */
        public boolean fullyOpaque() {
            return seeThroughPixels() == 0;
        }
    }

    /**
     * Samples every pixel of {@code width} x {@code height} at layout origin
     * {@code (originX, originY)} on {@code layer} (0 foreground, 1 background).
     */
    public static Coverage coverage(Level level, Map map, int layer,
                                    int originX, int originY, int width, int height) {
        int outside = 0;
        int emptyChunk = 0;
        int blankTile = 0;
        int transparent = 0;
        int sampled = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int value = pixelAt(level, map, layer, originX + x, originY + y);
                sampled++;
                switch (value) {
                    case OUTSIDE_LAYOUT -> outside++;
                    case EMPTY_CHUNK -> emptyChunk++;
                    case BLANK_TILE -> blankTile++;
                    case 0 -> transparent++;
                    default -> { }
                }
            }
        }
        return new Coverage(sampled, outside, emptyChunk, blankTile, transparent);
    }

    /**
     * A layout cell's pixel size, from the level's own block grid: S3K uses 8x8 chunks of 16px
     * (128px), Sonic 1 uses 16x16 (256px). Taken from the data rather than assumed.
     */
    public static int blockPixelSize(Level level) {
        for (int index = 1; index < level.getBlockCount(); index++) {
            Block block = level.getBlock(index);
            if (block != null && block.getGridSide() > 0) {
                return block.getGridSide() * LevelConstants.CHUNK_WIDTH;
            }
        }
        return 0;
    }

    /** {@link #pixelAt} result for a layout cell holding chunk id 0. */
    public static final int EMPTY_CHUNK = -2;
    /** {@link #pixelAt} result for a 16x16 chunk cell whose 8x8 pattern index is 0. */
    public static final int BLANK_TILE = -3;

    /**
     * The plane's pattern pixel at a world coordinate: 0 is transparent, 1-15 opaque, and the
     * negative constants above say why no tile pixel exists there. Resolution follows the ROM's
     * own nesting: layout cell -> 128px block -> 16x16 chunk descriptor -> 8x8 pattern
     * descriptor -> pattern, applying each level's H/V flip in turn.
     */
    public static int pixelAt(Level level, Map map, int layer, int worldX, int worldY) {
        if (level == null || map == null || worldX < 0 || worldY < 0) {
            return OUTSIDE_LAYOUT;
        }
        int blockSize = blockPixelSize(level);
        if (blockSize <= 0) {
            return OUTSIDE_LAYOUT;
        }
        int column = worldX / blockSize;
        int row = worldY / blockSize;
        if (column >= map.getWidth() || row >= map.getHeight()) {
            return OUTSIDE_LAYOUT;
        }
        int blockId = map.getValue(layer, column, row) & 0xFF;
        if (blockId <= 0 || blockId >= level.getBlockCount()) {
            return EMPTY_CHUNK;
        }
        Block block = level.getBlock(blockId);
        if (block == null) {
            return EMPTY_CHUNK;
        }
        int inBlockX = worldX % blockSize;
        int inBlockY = worldY % blockSize;
        ChunkDesc chunkDesc = block.getChunkDesc(inBlockX / LevelConstants.CHUNK_WIDTH,
                inBlockY / LevelConstants.CHUNK_HEIGHT);
        if (chunkDesc == null) {
            return EMPTY_CHUNK;
        }
        int chunkId = chunkDesc.getChunkIndex();
        if (chunkId <= 0 || chunkId >= level.getChunkCount()) {
            return EMPTY_CHUNK;
        }
        Chunk chunk = level.getChunk(chunkId);
        if (chunk == null) {
            return EMPTY_CHUNK;
        }
        int inChunkX = worldX % LevelConstants.CHUNK_WIDTH;
        int inChunkY = worldY % LevelConstants.CHUNK_HEIGHT;
        if (chunkDesc.getHFlip()) {
            inChunkX = LevelConstants.CHUNK_WIDTH - 1 - inChunkX;
        }
        if (chunkDesc.getVFlip()) {
            inChunkY = LevelConstants.CHUNK_HEIGHT - 1 - inChunkY;
        }
        PatternDesc patternDesc = chunk.getPatternDesc(inChunkX / Pattern.PATTERN_WIDTH,
                inChunkY / Pattern.PATTERN_HEIGHT);
        if (patternDesc == null) {
            return BLANK_TILE;
        }
        int patternIndex = patternDesc.getPatternIndex();
        if (patternIndex <= 0 || patternIndex >= level.getPatternCount()) {
            return BLANK_TILE;
        }
        Pattern pattern = level.getPattern(patternIndex);
        if (pattern == null) {
            return BLANK_TILE;
        }
        int inPatternX = inChunkX % Pattern.PATTERN_WIDTH;
        int inPatternY = inChunkY % Pattern.PATTERN_HEIGHT;
        if (patternDesc.getHFlip()) {
            inPatternX = Pattern.PATTERN_WIDTH - 1 - inPatternX;
        }
        if (patternDesc.getVFlip()) {
            inPatternY = Pattern.PATTERN_HEIGHT - 1 - inPatternY;
        }
        return pattern.getPixel(inPatternX, inPatternY) & 0x0F;
    }
}

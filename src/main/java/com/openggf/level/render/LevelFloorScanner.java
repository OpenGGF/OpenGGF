package com.openggf.level.render;

import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Level;
import com.openggf.level.SolidTile;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds runs of floor in a level from its collision, for presentation copies (mod scenes
 * standing characters on the real level). Reads the foreground layout on the primary collision
 * path the way a floor sensor does: layout cell, 128px block, 16x16 chunk descriptor with its
 * flips and solidity bits, then the chunk's solid tile height for the column (mirrored by H-flip,
 * negated by V-flip; index 0 is no collision). A floor is the top of a top-solid ({@code $C})
 * shape with nothing solid ({@code $C} or {@code $D}) for {@code headroom} pixels above it.
 * Pure: it reads only the level passed in.
 */
public final class LevelFloorScanner {
    /** Primary path: top solidity (floors) and left/right/bottom solidity (walls, ceilings). */
    private static final int TOP_SOLID_BIT = 0x0C;
    private static final int LRB_SOLID_BIT = 0x0D;
    private static final int CELL = 16;
    /** Columns sampled while searching; a gap or bump narrower than this can go unnoticed. */
    static final int COLUMN_STEP = 4;

    /**
     * A run of floor: its left edge and width, its median floor row, and the floor row of every
     * pixel column ({@code floor[i]} for column {@code x + i}), in level pixels.
     */
    public record Stage(int x, int floorY, int width, int[] floor) {
    }

    private LevelFloorScanner() {
    }

    /**
     * Runs of floor at least {@code width} wide inside the given area, left to right and not
     * overlapping. Every sampled column of a run has a floor with {@code headroom} clear rows
     * above it (inside the area), each continuing from its neighbour's, and the run's highest
     * and lowest floor rows are at most {@code maxRise} apart.
     */
    public static List<Stage> stages(Level level, int left, int top, int right, int bottom, int width,
            int headroom, int maxRise) {
        int columns = Math.max(0, (right - left) / COLUMN_STEP);
        int[][] floors = new int[columns][];
        for (int i = 0; i < columns; i++) {
            floors[i] = floors(level, left + i * COLUMN_STEP, top, bottom, headroom);
        }
        int span = Math.ceilDiv(width, COLUMN_STEP);
        List<Stage> stages = new ArrayList<>();
        int i = 0;
        while (i + span <= columns) {
            int[] run = null;
            for (int y : floors[i]) {
                run = follow(floors, i, span, y, maxRise);
                if (run != null) {
                    break;
                }
            }
            if (run != null) {
                stages.add(stage(level, left + i * COLUMN_STEP, run, top, bottom, headroom));
                i += span;
            } else {
                i++;
            }
        }
        return stages;
    }

    /**
     * The sampled floor rows of a run starting at column {@code first} on row {@code y}: each
     * column takes its floor nearest the previous one that keeps the run within
     * {@code maxRise}; null when some column has none.
     */
    private static int[] follow(int[][] floors, int first, int span, int y, int maxRise) {
        int[] run = new int[span];
        run[0] = y;
        int min = y;
        int max = y;
        for (int k = 1; k < span; k++) {
            int best = Integer.MIN_VALUE;
            for (int candidate : floors[first + k]) {
                if (Math.max(max, candidate) - Math.min(min, candidate) <= maxRise
                        && (best == Integer.MIN_VALUE
                        || Math.abs(candidate - run[k - 1]) < Math.abs(best - run[k - 1]))) {
                    best = candidate;
                }
            }
            if (best == Integer.MIN_VALUE) {
                return null;
            }
            run[k] = best;
            min = Math.min(min, best);
            max = Math.max(max, best);
        }
        return run;
    }

    /**
     * A found run with its floor read in every pixel column: the column's floor nearest the
     * sampled one to its left (within a chunk's height), or that sample where it has none.
     */
    private static Stage stage(Level level, int x, int[] run, int top, int bottom, int headroom) {
        int width = run.length * COLUMN_STEP;
        int[] floor = new int[width];
        for (int i = 0; i < width; i++) {
            int sampled = run[i / COLUMN_STEP];
            floor[i] = sampled;
            if (i % COLUMN_STEP == 0) {
                continue;
            }
            int nearest = Integer.MAX_VALUE;
            for (int candidate : floors(level, x + i, top, bottom, headroom)) {
                int distance = Math.abs(candidate - sampled);
                if (distance <= CELL && distance < nearest) {
                    floor[i] = candidate;
                    nearest = distance;
                }
            }
        }
        int[] sorted = floor.clone();
        java.util.Arrays.sort(sorted);
        return new Stage(x, sorted[width / 2], width, floor);
    }

    /** The floors in one pixel column with {@code headroom} clear rows above, top to bottom. */
    static int[] floors(Level level, int x, int top, int bottom, int headroom) {
        List<Integer> found = new ArrayList<>();
        int clearFrom = top;
        int solidEnd = Integer.MIN_VALUE;
        for (int cellY = Math.floorDiv(top, CELL) * CELL; cellY < bottom; cellY += CELL) {
            ChunkDesc desc = chunkDesc(level, x, cellY);
            if (desc == null) {
                continue;
            }
            boolean floor = desc.isSolidityBitSet(TOP_SOLID_BIT);
            if (!floor && !desc.isSolidityBitSet(LRB_SOLID_BIT)) {
                continue;
            }
            int metric = heightMetric(level, desc, x);
            if (metric == 0) {
                continue;
            }
            // Positive: the column's bottom rows are solid; negative: its top rows.
            int solidTop = metric > 0 ? cellY + CELL - metric : cellY;
            int solidBottom = metric > 0 ? cellY + CELL : cellY - metric;
            if (solidTop != solidEnd && floor && solidTop - clearFrom >= headroom && solidTop >= top) {
                found.add(solidTop);
            }
            solidEnd = solidBottom;
            clearFrom = solidBottom;
        }
        int[] out = new int[found.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = found.get(i);
        }
        return out;
    }

    /** The column's height in its chunk's primary solid tile, oriented for a downward scan. */
    private static int heightMetric(Level level, ChunkDesc desc, int x) {
        int chunkIndex = desc.getChunkIndex();
        if (chunkIndex < 0 || chunkIndex >= level.getChunkCount()) {
            return 0;
        }
        Chunk chunk = level.getChunk(chunkIndex);
        int solidIndex = chunk.getSolidTileIndex();
        if (solidIndex == 0 || solidIndex >= level.getSolidTileCount()) {
            return 0;
        }
        SolidTile tile = level.getSolidTile(solidIndex);
        int column = desc.getHFlip() ? 15 - (x & 15) : x & 15;
        int metric = tile.getHeightAt((byte) column);
        if (metric != 0 && metric != CELL && desc.getVFlip()) {
            metric = -metric;
        }
        return metric;
    }

    private static ChunkDesc chunkDesc(Level level, int x, int y) {
        int blockSize = level.getBlockPixelSize();
        if (x < 0 || y < 0 || blockSize <= 0) {
            return null;
        }
        int mapX = x / blockSize;
        int mapY = y / blockSize;
        if (mapX >= level.getLayerWidthBlocks(PlaneRasterizer.FOREGROUND)
                || mapY >= level.getLayerHeightBlocks(PlaneRasterizer.FOREGROUND)) {
            return null;
        }
        int raw = Byte.toUnsignedInt(level.getMap().getValue(PlaneRasterizer.FOREGROUND, mapX, mapY));
        int blockIndex = level.resolveCollisionBlockIndex(raw, mapX, mapY);
        if (blockIndex < 0 || blockIndex >= level.getBlockCount()) {
            return null;
        }
        Block block = level.getBlock(blockIndex);
        int cellX = (x % blockSize) / CELL;
        int cellY = (y % blockSize) / CELL;
        if (cellX >= block.getGridSide() || cellY >= block.getGridSide()) {
            return null;
        }
        return block.getChunkDesc(cellX, cellY);
    }
}

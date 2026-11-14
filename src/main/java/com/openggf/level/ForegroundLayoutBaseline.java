package com.openggf.level;

import java.util.Arrays;
import java.util.BitSet;

/**
 * The layout a flat foreground tilemap image was drawn from, kept so a redraw
 * request can rewrite only the layout cells whose tiles changed.
 *
 * <p>A flat foreground image is a pure function of layer 0 of the layout map,
 * the block table (chunk descriptors) and the chunk table (pattern descriptors).
 * After any edit to those inputs (a layout write, a block or chunk definition
 * restore or overlay, or a rewind restore that swaps them back) the changed cells
 * are exactly: cells whose block index changed, cells placing a block whose
 * chunk descriptors changed, and cells placing a block that references a chunk
 * whose pattern descriptors changed. Comparing against the recorded inputs finds
 * that set without knowing which writer made the edit, so the redraw equals a
 * full rebuild of the same layout by construction. A block or chunk index at or
 * beyond the table size draws nothing, exactly like a null entry, so the tables
 * may grow or shrink between redraws (runtime art loads append chunks).
 *
 * <p>Tiles written straight into the image ({@code setForegroundTileDescriptorAtWorld})
 * do not come from the layout; their cells are recorded so the next redraw replaces
 * them, as a full rebuild would.
 *
 * <p>A streamed image ({@link #beginStream}) starts with no cell column drawn. Because
 * this record holds every input of a full build, an undrawn column is later drawn from
 * the record ({@link #cellBlock}, {@link #blockDescriptor}, {@link #chunkPattern}), not
 * from the live level: a layout written without a redraw request between the load and
 * the column's turn cannot reach the image, exactly as with a whole-level build at the
 * load. A redraw request updates the record first, so the column then shows the edit.
 */
final class ForegroundLayoutBaseline {
    /** Pattern-slot value recorded for a null chunk; real descriptors are 16-bit. */
    private static final int NULL_CHUNK = -1;
    /** Descriptor value recorded for every slot of a null block. */
    private static final int NULL_BLOCK = Integer.MIN_VALUE;

    private byte[] image;
    private Level level;
    private int widthPx;
    private int heightPx;
    private int blockPixelSize;
    private int gridSide;
    private int cellsWide;
    private int cellsHigh;
    // Recorded table entries; entries at or beyond the live count hold the null values.
    private int blockSlots;
    private int chunkSlots;
    private byte[] cells = new byte[0];
    private int[] blockDescriptors = new int[0];
    private int[] chunkPatterns = new int[0];

    private final BitSet overwrittenCells = new BitSet();
    // Cell columns of a streamed image not drawn yet; empty once complete.
    private final BitSet undrawnColumns = new BitSet();
    private final BitSet changedChunks = new BitSet();
    private final BitSet changedBlocks = new BitSet();
    private int[] changedCells = new int[16];
    private int changedCellCount;

    /** Forgets the recorded layout (the next redraw request must rebuild the image). */
    void invalidate() {
        image = null;
        level = null;
        overwrittenCells.clear();
        undrawnColumns.clear();
    }

    /** Records the layout {@code image} (a full flat build of {@code level}) was drawn from. */
    void capture(Level level, byte[] image, int widthPx, int heightPx, int blockPixelSize) {
        Map map = level.getMap();
        int side = blockPixelSize / LevelConstants.CHUNK_WIDTH;
        int wide = (widthPx + blockPixelSize - 1) / blockPixelSize;
        int high = (heightPx + blockPixelSize - 1) / blockPixelSize;
        if (map == null || side <= 0 || blockPixelSize % LevelConstants.CHUNK_WIDTH != 0
                || map.getLayerCount() < 1 || map.getWidth() < wide || map.getHeight() < high) {
            invalidate();
            return;
        }
        this.level = level;
        this.image = image;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.blockPixelSize = blockPixelSize;
        this.gridSide = side;
        this.cellsWide = wide;
        this.cellsHigh = high;
        int blockCount = level.getBlockCount();
        int chunkCount = level.getChunkCount();
        this.blockSlots = blockCount;
        this.chunkSlots = chunkCount;
        overwrittenCells.clear();
        // A capture describes a complete image; beginStream() marks it undrawn.
        undrawnColumns.clear();

        if (cells.length != wide * high) cells = new byte[wide * high];
        for (int cy = 0; cy < high; cy++) {
            for (int cx = 0; cx < wide; cx++) {
                cells[cy * wide + cx] = map.getValue(0, cx, cy);
            }
        }
        int perBlock = side * side;
        if (blockDescriptors.length != blockCount * perBlock) blockDescriptors = new int[blockCount * perBlock];
        for (int b = 0; b < blockCount; b++) {
            Block block = level.getBlock(b);
            for (int i = 0; i < perBlock; i++) {
                blockDescriptors[b * perBlock + i] = descriptor(block, i);
            }
        }
        if (chunkPatterns.length != chunkCount * Chunk.PATTERNS_PER_CHUNK) {
            chunkPatterns = new int[chunkCount * Chunk.PATTERNS_PER_CHUNK];
        }
        for (int c = 0; c < chunkCount; c++) {
            Chunk chunk = level.getChunk(c);
            for (int p = 0; p < Chunk.PATTERNS_PER_CHUNK; p++) {
                chunkPatterns[c * Chunk.PATTERNS_PER_CHUNK + p] = pattern(chunk, p);
            }
        }
    }

    /** True when {@code image} is the recorded image of the same level and geometry. */
    boolean describes(Level level, byte[] image, int widthPx, int heightPx, int blockPixelSize) {
        if (this.image == null || this.image != image || this.level != level
                || this.widthPx != widthPx || this.heightPx != heightPx
                || this.blockPixelSize != blockPixelSize) {
            return false;
        }
        Map map = level.getMap();
        return map != null && map.getWidth() >= cellsWide && map.getHeight() >= cellsHigh;
    }

    /** Records that tile ({@code tileX}, {@code tileY}) was written straight into the image. */
    void markOverwrittenTile(int tileX, int tileY) {
        if (image == null) {
            return;
        }
        int cx = tileX * 8 / blockPixelSize;
        int cy = tileY * 8 / blockPixelSize;
        if (cx >= 0 && cy >= 0 && cx < cellsWide && cy < cellsHigh) {
            overwrittenCells.set(cy * cellsWide + cx);
        }
    }

    /**
     * Finds the cells whose tiles differ from the recorded layout and records the live
     * layout in their place. Returns the number of changed cells (read them with
     * {@link #changedCell(int)}), or -1 once more than {@code limit} cells changed: the
     * caller must then rebuild the image, which recaptures this baseline.
     */
    int collectChangedCells(Level level, int limit) {
        changedCellCount = 0;
        int liveChunks = level.getChunkCount();
        int chunkSpan = Math.max(liveChunks, chunkSlots);
        if (chunkSpan > chunkSlots) {
            chunkPatterns = Arrays.copyOf(chunkPatterns, chunkSpan * Chunk.PATTERNS_PER_CHUNK);
            Arrays.fill(chunkPatterns, chunkSlots * Chunk.PATTERNS_PER_CHUNK, chunkPatterns.length, NULL_CHUNK);
            chunkSlots = chunkSpan;
        }
        changedChunks.clear();
        for (int c = 0; c < chunkSpan; c++) {
            Chunk chunk = c < liveChunks ? level.getChunk(c) : null;
            int base = c * Chunk.PATTERNS_PER_CHUNK;
            for (int p = 0; p < Chunk.PATTERNS_PER_CHUNK; p++) {
                int value = pattern(chunk, p);
                if (chunkPatterns[base + p] != value) {
                    chunkPatterns[base + p] = value;
                    changedChunks.set(c);
                }
            }
        }
        int perBlock = gridSide * gridSide;
        int liveBlocks = level.getBlockCount();
        int blockSpan = Math.max(liveBlocks, blockSlots);
        if (blockSpan > blockSlots) {
            blockDescriptors = Arrays.copyOf(blockDescriptors, blockSpan * perBlock);
            Arrays.fill(blockDescriptors, blockSlots * perBlock, blockDescriptors.length, NULL_BLOCK);
            blockSlots = blockSpan;
        }
        changedBlocks.clear();
        for (int b = 0; b < blockSpan; b++) {
            Block block = b < liveBlocks ? level.getBlock(b) : null;
            int base = b * perBlock;
            boolean changed = false;
            for (int i = 0; i < perBlock; i++) {
                int value = descriptor(block, i);
                if (blockDescriptors[base + i] != value) {
                    blockDescriptors[base + i] = value;
                    changed = true;
                } else if (!changed && value != NULL_BLOCK && !changedChunks.isEmpty()) {
                    int chunkIndex = value >> 2;
                    changed = chunkIndex >= 0 && changedChunks.get(chunkIndex);
                }
            }
            if (changed) changedBlocks.set(b);
        }
        Map map = level.getMap();
        for (int cy = 0; cy < cellsHigh; cy++) {
            for (int cx = 0; cx < cellsWide; cx++) {
                int index = cy * cellsWide + cx;
                byte value = map.getValue(0, cx, cy);
                boolean changed = cells[index] != value;
                if (changed) {
                    cells[index] = value;
                } else {
                    changed = changedBlocks.get(value & 0xFF) || overwrittenCells.get(index);
                }
                if (!changed) continue;
                if (changedCellCount == limit) {
                    return -1;
                }
                if (changedCellCount == changedCells.length) {
                    changedCells = Arrays.copyOf(changedCells, changedCellCount * 2);
                }
                changedCells[changedCellCount++] = (cy << 16) | cx;
            }
        }
        overwrittenCells.clear();
        return changedCellCount;
    }

    /**
     * Marks every cell column of the just-captured image undrawn; the image bytes are
     * then filled column by column from this record.
     */
    void beginStream() {
        if (image == null) {
            return;
        }
        undrawnColumns.set(0, cellsWide);
    }

    /** True while some cell column of the recorded image has not been drawn yet. */
    boolean streaming() {
        return image != null && !undrawnColumns.isEmpty();
    }

    boolean columnDrawn(int cellX) {
        return !undrawnColumns.get(cellX);
    }

    void markColumnDrawn(int cellX) {
        undrawnColumns.clear(cellX);
    }

    int undrawnColumnCount() {
        return undrawnColumns.cardinality();
    }

    /**
     * The undrawn cell column nearest {@code focusCellX} (ties go right), or -1 when
     * every column is drawn.
     */
    int nearestUndrawnColumn(int focusCellX) {
        int focus = Math.max(0, Math.min(cellsWide - 1, focusCellX));
        int right = undrawnColumns.nextSetBit(focus);
        int left = focus == 0 ? -1 : undrawnColumns.previousSetBit(focus - 1);
        if (right < 0) {
            return left;
        }
        if (left < 0) {
            return right;
        }
        return right - focus <= focus - left ? right : left;
    }

    /** The next undrawn cell column after {@code cellX}, or -1. */
    int nextUndrawnColumnAfter(int cellX) {
        return undrawnColumns.nextSetBit(cellX + 1);
    }

    int cellsWide() {
        return cellsWide;
    }

    int cellsHigh() {
        return cellsHigh;
    }

    int widthPx() {
        return widthPx;
    }

    int heightPx() {
        return heightPx;
    }

    /** The recorded block index (0-255) placed at cell ({@code cellX}, {@code cellY}). */
    int cellBlock(int cellX, int cellY) {
        return cells[cellY * cellsWide + cellX] & 0xFF;
    }

    /**
     * The recorded chunk descriptor ({@code chunkIndex << 2 | vFlip << 1 | hFlip}) in
     * slot {@code (slotX, slotY)} of {@code block}, or {@link #NULL_BLOCK} when the block
     * was absent (beyond the table or null): such a block draws empty chunks.
     */
    int blockDescriptor(int block, int slotX, int slotY) {
        if (block >= blockSlots) {
            return NULL_BLOCK;
        }
        return blockDescriptors[block * gridSide * gridSide + slotY * gridSide + slotX];
    }

    /**
     * The recorded pattern descriptor of {@code chunk} at ({@code x}, {@code y}), or a
     * negative value when the chunk was absent (beyond the table or null): such a
     * chunk draws empty tiles.
     */
    int chunkPattern(int chunk, int x, int y) {
        if (chunk < 0 || chunk >= chunkSlots) {
            return NULL_CHUNK;
        }
        return chunkPatterns[chunk * Chunk.PATTERNS_PER_CHUNK + y * 2 + x];
    }

    static boolean isNullBlockDescriptor(int descriptor) {
        return descriptor == NULL_BLOCK;
    }

    /**
     * Records that the image now belongs to {@code level}, which replaced the recorded
     * level with the same geometry (a reload of the same act). The recorded inputs are
     * kept, so the next {@link #collectChangedCells} finds exactly the cells where the
     * new level's layout differs from what the image shows.
     */
    void rebind(Level level) {
        if (image != null) {
            this.level = level;
        }
    }

    /** Moves {@code other}'s record (and its image reference) here, leaving {@code other} empty. */
    void takeFrom(ForegroundLayoutBaseline other) {
        image = other.image;
        level = other.level;
        widthPx = other.widthPx;
        heightPx = other.heightPx;
        blockPixelSize = other.blockPixelSize;
        gridSide = other.gridSide;
        cellsWide = other.cellsWide;
        cellsHigh = other.cellsHigh;
        blockSlots = other.blockSlots;
        chunkSlots = other.chunkSlots;
        cells = other.cells;
        blockDescriptors = other.blockDescriptors;
        chunkPatterns = other.chunkPatterns;
        overwrittenCells.clear();
        overwrittenCells.or(other.overwrittenCells);
        undrawnColumns.clear();
        undrawnColumns.or(other.undrawnColumns);
        other.cells = new byte[0];
        other.blockDescriptors = new int[0];
        other.chunkPatterns = new int[0];
        other.invalidate();
    }

    /** Packed {@code (cellY << 16) | cellX} of the {@code i}th changed cell. */
    int changedCell(int i) {
        return changedCells[i];
    }

    int cellCount() {
        return cellsWide * cellsHigh;
    }

    /** The fields the tilemap writer reads from one chunk descriptor: index and flips. */
    private int descriptor(Block block, int slot) {
        if (block == null) {
            return NULL_BLOCK;
        }
        ChunkDesc desc = block.getChunkDesc(slot % gridSide, slot / gridSide);
        return (desc.getChunkIndex() << 2) | (desc.getVFlip() ? 2 : 0) | (desc.getHFlip() ? 1 : 0);
    }

    private static int pattern(Chunk chunk, int slot) {
        return chunk == null ? NULL_CHUNK : chunk.getPatternDesc(slot & 1, slot >> 1).get();
    }
}

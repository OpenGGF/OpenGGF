package com.openggf.game.soniccd.level;

import java.util.Objects;

/**
 * Immutable native CD terrain format with explicitly reviewed source shapes.
 * Shape selects finite data extents, not a runtime zone rule or media identity.
 * Native N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Palmtree Panic/Load Level Data.asm:57-98; Block Draw.asm:117-175,202-230;
 * Collision Floor.asm:685-737. Present/Past source extents are independently
 * authenticated in the external R11B census; this decoder never opens it.
 * No collision algorithm, renderer, mutable engine world, RAM owner or fallback.
 */
public final class CdStageTerrain {
    /** Exact asset-format profiles; no arbitrary/default dimensions or runtime admission. */
    public enum Shape {
        PPZ1_PRESENT("R11A", 6432, 25600, 61952, 42, 8, 6, 8, 900),
        PPZ1_PAST("R11B", 5280, 23872, 54272, 42, 4, 10, 2, 800);

        private final String label;
        private final int blockBytes;
        private final int patternBytes;
        private final int chunkBytes;
        private final int foregroundColumns;
        private final int foregroundRows;
        private final int backgroundColumns;
        private final int backgroundRows;
        private final int collisionIds;

        Shape(String label, int blockBytes, int patternBytes, int chunkBytes,
              int foregroundColumns, int foregroundRows, int backgroundColumns, int backgroundRows,
              int collisionIds) {
            this.label = label; this.blockBytes = blockBytes; this.patternBytes = patternBytes;
            this.chunkBytes = chunkBytes; this.foregroundColumns = foregroundColumns;
            this.foregroundRows = foregroundRows; this.backgroundColumns = backgroundColumns;
            this.backgroundRows = backgroundRows; this.collisionIds = collisionIds;
        }
    }

    public enum Plane { FOREGROUND, BACKGROUND }

    public record PatternReference(int rawWord) {
        public PatternReference { checkWord(rawWord); }
        public int patternIndex() { return rawWord & 0x7FF; }
        public boolean flipX() { return (rawWord & 0x800) != 0; }
        public boolean flipY() { return (rawWord & 0x1000) != 0; }
        public int paletteLine() { return (rawWord >>> 13) & 3; }
        public boolean priority() { return (rawWord & 0x8000) != 0; }
    }

    /**
     * Preserve the native word. GetBlockData masks 0x3FF; FindLevelFloor masks 0x7FF.
     * DrawBlock tests bits 11/12; collision tests the original solidity bits 13/14.
     * Owners: Block Draw.asm:GetBlockDataAbsXY/DrawBlock; Collision Floor.asm:FindLevelFloor.
     */
    public record BlockReference(int rawWord) {
        public BlockReference { checkWord(rawWord); }
        public int renderBlockIndex() { return rawWord & 0x3FF; }
        public int collisionBlockIndex() { return rawWord & 0x7FF; }
        public boolean flipX() { return (rawWord & 0x800) != 0; }
        public boolean flipY() { return (rawWord & 0x1000) != 0; }
        public int solidity() { return (rawWord >>> 13) & 3; }
    }

    public record LayoutCell(int rawByte) {
        public LayoutCell {
            if ((rawByte & ~0xFF) != 0) { throw new IllegalArgumentException("Not a layout byte"); }
        }
        public int chunkId() { return rawByte & 0x7F; }
        public boolean loopFlag() { return (rawByte & 0x80) != 0; }
        public boolean blank() { return chunkId() == 0; }
    }

    public static final class Layout {
        private final Shape shape;
        private final CdAssetBytes ram;

        private Layout(Shape shape, byte[] foreground, byte[] background) {
            this.shape = shape;
            byte[] bytes = new byte[0x800];
            copyPlane(foreground, shape.foregroundColumns, shape.foregroundRows, 0, bytes, shape);
            copyPlane(background, shape.backgroundColumns, shape.backgroundRows, 0x40, bytes, shape);
            ram = CdAssetBytes.copyOf(bytes);
        }

        // Native clear $800; minus-one dimensions; FG+$00/BG+$40; row stride $80. Never repeat rows.
        private static void copyPlane(byte[] source, int width, int height, int base, byte[] ram, Shape shape) {
            if (source.length != 2 + width * height || Byte.toUnsignedInt(source[0]) + 1 != width
                    || Byte.toUnsignedInt(source[1]) + 1 != height) {
                throw new IllegalArgumentException("Unsupported " + shape.label + " layout dimensions or extent");
            }
            for (int row = 0; row < height; row++) {
                System.arraycopy(source, 2 + row * width, ram, base + row * 0x80, width);
            }
        }

        public int columns(Plane plane) {
            return Objects.requireNonNull(plane) == Plane.FOREGROUND ? shape.foregroundColumns : shape.backgroundColumns;
        }
        public int rows(Plane plane) {
            return Objects.requireNonNull(plane) == Plane.FOREGROUND ? shape.foregroundRows : shape.backgroundRows;
        }
        public CdAssetBytes ram() { return ram; }
        public LayoutCell cell(Plane plane, int x, int y) {
            Objects.checkIndex(x, columns(plane));
            Objects.checkIndex(y, rows(plane));
            return nativeCell(plane, x, y);
        }
        /** Includes native cleared cells outside the supplied dimensions. */
        public LayoutCell nativeCell(Plane plane, int x, int y) {
            Objects.requireNonNull(plane);
            Objects.checkIndex(x, 0x40);
            Objects.checkIndex(y, 0x10);
            return new LayoutCell(ram.unsignedByte((plane == Plane.FOREGROUND ? 0 : 0x40) + y * 0x80 + x));
        }
    }

    public static final class Collision {
        private final CdAssetBytes ids;
        private final CdAssetBytes angles;
        private final CdAssetBytes heights;
        private final CdAssetBytes widths;

        private Collision(Shape shape, byte[] ids, byte[] angles, byte[] heights, byte[] widths) {
            this.ids = exact(ids, shape.collisionIds, "collision IDs", shape);
            this.angles = exact(angles, 256, "collision angles", shape);
            this.heights = exact(heights, 4096, "height samples", shape);
            this.widths = exact(widths, 4096, "width samples", shape);
        }
        public int idForBlock(int blockIndex) { return ids.unsignedByte(blockIndex); }
        public int angle(int collisionId) { return angles.unsignedByte(collisionId); }
        public int height(int collisionId, int sample) { return sample(heights, collisionId, sample); }
        public int width(int collisionId, int sample) { return sample(widths, collisionId, sample); }
        private static int sample(CdAssetBytes data, int id, int sample) {
            Objects.checkIndex(id, 256);
            Objects.checkIndex(sample, 16);
            return data.signedByte(id * 16 + sample);
        }
        public CdAssetBytes ids() { return ids; }
        public CdAssetBytes angles() { return angles; }
        public CdAssetBytes heights() { return heights; }
        public CdAssetBytes widths() { return widths; }
    }

    private final Shape shape;
    private final CdAssetBytes blocks;
    private final CdAssetBytes patterns;
    private final CdAssetBytes chunks;
    private final Layout layout;
    private final Collision collision;

    private CdStageTerrain(Shape shape, byte[] blocks, byte[] patterns, byte[] chunks, byte[] fg, byte[] bg,
                           byte[] ids, byte[] angles, byte[] heights, byte[] widths) {
        this.shape = Objects.requireNonNull(shape, "explicit terrain source shape");
        this.blocks = exact(blocks, shape.blockBytes, "decoded blocks", shape);
        this.patterns = exact(patterns, shape.patternBytes, "decoded level patterns", shape);
        this.chunks = exact(chunks, shape.chunkBytes, "raw chunks", shape);
        this.layout = new Layout(shape, fg, bg);
        this.collision = new Collision(shape, ids, angles, heights, widths);
        validateReferences();
    }

    /** Pure component decoder; a shape is not a media/release or gameplay capability. */
    public static CdStageTerrain decode(Shape shape, byte[] blocks, byte[] patterns, byte[] chunks, byte[] foreground,
                                        byte[] background, byte[] ids, byte[] angles, byte[] heights, byte[] widths) {
        return new CdStageTerrain(shape, blocks, patterns, chunks, foreground, background, ids, angles, heights, widths);
    }

    private void validateReferences() {
        for (int at = 0; at < blocks.length(); at += 2) {
            if ((blocks.unsignedWord(at) & 0x7FF) >= patternCount()) {
                throw new IllegalArgumentException("Block references an unloaded level pattern");
            }
        }
        boolean[] visited = new boolean[chunkCount() + 1];
        for (Plane plane : Plane.values()) {
            for (int y = 0; y < layout.rows(plane); y++) {
                for (int x = 0; x < layout.columns(plane); x++) {
                    int id = layout.cell(plane, x, y).chunkId();
                    if (id > chunkCount()) { throw new IllegalArgumentException("Layout chunk exceeds supplied extent"); }
                    if (id == 0 || visited[id]) { continue; }
                    visited[id] = true;
                    for (int cy = 0; cy < 16; cy++) {
                        for (int cx = 0; cx < 16; cx++) {
                            BlockReference word = chunkWord(id, cx, cy);
                            if (word.renderBlockIndex() >= blockCount() || word.collisionBlockIndex() >= shape.collisionIds) {
                                throw new IllegalArgumentException("Referenced chunk requires unloaded block/collision data");
                            }
                        }
                    }
                }
            }
        }
    }

    public Shape shape() { return shape; }
    public int blockCount() { return shape.blockBytes / 8; }
    public int patternCount() { return shape.patternBytes / 32; }
    public int chunkCount() { return shape.chunkBytes / 512; }
    public CdAssetBytes blocks() { return blocks; }
    public CdAssetBytes patterns() { return patterns; }
    public CdAssetBytes chunks() { return chunks; }
    public Layout layout() { return layout; }
    public Collision collision() { return collision; }
    public PatternReference blockWord(int zeroBasedBlockId, int x, int y) {
        Objects.checkIndex(zeroBasedBlockId, blockCount());
        Objects.checkIndex(x, 2);
        Objects.checkIndex(y, 2);
        return new PatternReference(blocks.unsignedWord(zeroBasedBlockId * 8 + (y * 2 + x) * 2));
    }
    public BlockReference chunkWord(int oneBasedChunkId, int x, int y) {
        if (oneBasedChunkId < 1 || oneBasedChunkId > chunkCount()) {
            throw new IndexOutOfBoundsException("Chunk ID must be 1.." + chunkCount() + "; 0 is a blank layout cell");
        }
        Objects.checkIndex(x, 16);
        Objects.checkIndex(y, 16);
        return new BlockReference(chunks.unsignedWord((oneBasedChunkId - 1) * 512 + (y * 16 + x) * 2));
    }
    public int pixel(int patternIndex, int x, int y) {
        Objects.checkIndex(patternIndex, patternCount());
        Objects.checkIndex(x, 8);
        Objects.checkIndex(y, 8);
        int packed = patterns.unsignedByte(patternIndex * 32 + y * 4 + x / 2);
        return (x & 1) == 0 ? packed >>> 4 : packed & 15;
    }

    private static CdAssetBytes exact(byte[] input, int size, String name, Shape shape) {
        if (input.length != size) { throw new IllegalArgumentException("Wrong " + shape.label + " " + name + " extent"); }
        return CdAssetBytes.copyOf(input);
    }
    private static void checkWord(int value) {
        if ((value & ~0xFFFF) != 0) { throw new IllegalArgumentException("Not a native word"); }
    }
}

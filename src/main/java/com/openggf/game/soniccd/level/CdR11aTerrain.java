package com.openggf.game.soniccd.level;

import java.util.Objects;

/**
 * Immutable R11A terrain, using CD's native block/chunk terminology.
 * Blocks are 16x16 (four pattern words); chunks are 256x256 (16x16 block words).
 * No mutable engine Block/Chunk objects or cartridge format conversion is involved.
 */
public final class CdR11aTerrain {
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
        private final CdAssetBytes ram;

        private Layout(byte[] foreground, byte[] background) {
            byte[] bytes = new byte[0x800];
            copyPlane(foreground, 42, 8, 0, bytes);
            copyPlane(background, 6, 8, 0x40, bytes);
            ram = CdAssetBytes.copyOf(bytes);
        }

        // LoadLevelLayout (Palmtree Panic/Load Level Data.asm): clear $800,
        // minus-one dimensions, FG+$00/BG+$40, row stride $80. Do not repeat rows.
        private static void copyPlane(byte[] source, int width, int height, int base, byte[] ram) {
            if (source.length != 2 + width * height || Byte.toUnsignedInt(source[0]) + 1 != width
                    || Byte.toUnsignedInt(source[1]) + 1 != height) {
                throw new IllegalArgumentException("Unsupported R11A layout dimensions or extent");
            }
            for (int row = 0; row < height; row++) {
                System.arraycopy(source, 2 + row * width, ram, base + row * 0x80, width);
            }
        }

        public int columns(Plane plane) { return Objects.requireNonNull(plane) == Plane.FOREGROUND ? 42 : 6; }
        public int rows(Plane plane) { Objects.requireNonNull(plane); return 8; }
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

        private Collision(byte[] ids, byte[] angles, byte[] heights, byte[] widths) {
            this.ids = exact(ids, 900, "collision IDs");
            this.angles = exact(angles, 256, "collision angles");
            this.heights = exact(heights, 4096, "height samples");
            this.widths = exact(widths, 4096, "width samples");
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

    private final CdAssetBytes blocks;
    private final CdAssetBytes patterns;
    private final CdAssetBytes chunks;
    private final Layout layout;
    private final Collision collision;

    private CdR11aTerrain(byte[] blocks, byte[] patterns, byte[] chunks, byte[] fg, byte[] bg,
                          byte[] ids, byte[] angles, byte[] heights, byte[] widths) {
        this.blocks = exact(blocks, 6432, "decoded blocks");
        this.patterns = exact(patterns, 25600, "decoded level patterns");
        this.chunks = exact(chunks, 61952, "raw chunks");
        this.layout = new Layout(fg, bg);
        this.collision = new Collision(ids, angles, heights, widths);
        validateReferences();
    }

    /** Pure component decoder, not a media/release authentication boundary. */
    public static CdR11aTerrain decode(byte[] blocks, byte[] patterns, byte[] chunks, byte[] foreground,
                                       byte[] background, byte[] ids, byte[] angles, byte[] heights, byte[] widths) {
        return new CdR11aTerrain(blocks, patterns, chunks, foreground, background, ids, angles, heights, widths);
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
                            if (word.renderBlockIndex() >= blockCount() || word.collisionBlockIndex() >= 900) {
                                throw new IllegalArgumentException("Referenced chunk requires unloaded block/collision data");
                            }
                        }
                    }
                }
            }
        }
    }

    public int blockCount() { return 804; }
    public int patternCount() { return 800; }
    public int chunkCount() { return 121; }
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
            throw new IndexOutOfBoundsException("Chunk ID must be 1..121; 0 is a blank layout cell");
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

    private static CdAssetBytes exact(byte[] input, int size, String name) {
        if (input.length != size) { throw new IllegalArgumentException("Wrong R11A " + name + " extent"); }
        return CdAssetBytes.copyOf(input);
    }
    private static void checkWord(int value) {
        if ((value & ~0xFFFF) != 0) { throw new IllegalArgumentException("Not a native word"); }
    }
}

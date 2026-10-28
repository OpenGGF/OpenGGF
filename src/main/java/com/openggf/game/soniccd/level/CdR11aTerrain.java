package com.openggf.game.soniccd.level;

import java.util.Objects;

/**
 * Strict immutable R11A terrain facade over the shared native CD format model.
 * The original public nested types and return signatures remain unchanged.
 * Only the reviewed Present shape enters through decode; the separate Past
 * asset owner must bind its own source before using CdStageTerrain directly.
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
        private final CdStageTerrain.Layout delegate;

        private Layout(CdStageTerrain.Layout delegate) { this.delegate = delegate; }
        public int columns(Plane plane) { return delegate.columns(plane(plane)); }
        public int rows(Plane plane) { return delegate.rows(plane(plane)); }
        public CdAssetBytes ram() { return delegate.ram(); }
        public LayoutCell cell(Plane plane, int x, int y) { return new LayoutCell(delegate.cell(plane(plane), x, y).rawByte()); }
        /** Includes native cleared cells outside the supplied dimensions. */
        public LayoutCell nativeCell(Plane plane, int x, int y) {
            return new LayoutCell(delegate.nativeCell(plane(plane), x, y).rawByte());
        }
    }

    public static final class Collision {
        private final CdStageTerrain.Collision delegate;

        private Collision(CdStageTerrain.Collision delegate) { this.delegate = delegate; }
        public int idForBlock(int blockIndex) { return delegate.idForBlock(blockIndex); }
        public int angle(int collisionId) { return delegate.angle(collisionId); }
        public int height(int collisionId, int sample) { return delegate.height(collisionId, sample); }
        public int width(int collisionId, int sample) { return delegate.width(collisionId, sample); }
        public CdAssetBytes ids() { return delegate.ids(); }
        public CdAssetBytes angles() { return delegate.angles(); }
        public CdAssetBytes heights() { return delegate.heights(); }
        public CdAssetBytes widths() { return delegate.widths(); }
    }

    private final CdStageTerrain delegate;
    private final Layout layout;
    private final Collision collision;

    private CdR11aTerrain(byte[] blocks, byte[] patterns, byte[] chunks, byte[] fg, byte[] bg,
                          byte[] ids, byte[] angles, byte[] heights, byte[] widths) {
        delegate = CdStageTerrain.decode(CdStageTerrain.Shape.PPZ1_PRESENT,
                blocks, patterns, chunks, fg, bg, ids, angles, heights, widths);
        layout = new Layout(delegate.layout());
        collision = new Collision(delegate.collision());
    }

    /** Pure component decoder, not a media/release authentication boundary. */
    public static CdR11aTerrain decode(byte[] blocks, byte[] patterns, byte[] chunks, byte[] foreground,
                                       byte[] background, byte[] ids, byte[] angles, byte[] heights, byte[] widths) {
        return new CdR11aTerrain(blocks, patterns, chunks, foreground, background, ids, angles, heights, widths);
    }

    // One internal model, with no second decode/copy, for the shared engine-geometry adapter.
    CdStageTerrain stageTerrain() { return delegate; }

    public int blockCount() { return delegate.blockCount(); }
    public int patternCount() { return delegate.patternCount(); }
    public int chunkCount() { return delegate.chunkCount(); }
    public CdAssetBytes blocks() { return delegate.blocks(); }
    public CdAssetBytes patterns() { return delegate.patterns(); }
    public CdAssetBytes chunks() { return delegate.chunks(); }
    public Layout layout() { return layout; }
    public Collision collision() { return collision; }
    public PatternReference blockWord(int zeroBasedBlockId, int x, int y) {
        return new PatternReference(delegate.blockWord(zeroBasedBlockId, x, y).rawWord());
    }
    public BlockReference chunkWord(int oneBasedChunkId, int x, int y) {
        return new BlockReference(delegate.chunkWord(oneBasedChunkId, x, y).rawWord());
    }
    public int pixel(int patternIndex, int x, int y) { return delegate.pixel(patternIndex, x, y); }

    private static CdStageTerrain.Plane plane(Plane plane) {
        return Objects.requireNonNull(plane) == Plane.FOREGROUND
                ? CdStageTerrain.Plane.FOREGROUND : CdStageTerrain.Plane.BACKGROUND;
    }
    private static void checkWord(int value) {
        if ((value & ~0xFFFF) != 0) { throw new IllegalArgumentException("Not a native word"); }
    }
}

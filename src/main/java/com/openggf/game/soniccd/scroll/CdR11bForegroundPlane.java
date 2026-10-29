package com.openggf.game.soniccd.scroll;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.level.CdStageTerrain;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.Camera;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.State;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Pure finite R11B foreground name-table algorithm, shipped FixBugs=0.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * PP Scroll (Act 1 Past).asm DrawLevelFG:642-686, InitLevelDraw:843-855,
 * InitLevelDrawFG:868-886; Block Draw.asm DrawBlockRow:20-39,
 * DrawBlockCol:85-102, DrawBlock:117-180, GetBlockData:196-235,
 * GetBlockVDPCmd:376-392. Plane offsets translate the native wrapping writes
 * into the existing finite immutable 64x32-word value, without a VDP protocol.
 *
 * Only immutable prepared source bytes are borrowed. Actual Module layout/block
 * production, inherited metadata knowledge, invocation order and selected current
 * pixels remain caller obligations. A result or stopped prefix executes no load,
 * scroll, Body, clock, art decode, renderer upload or replay.
 */
public final class CdR11bForegroundPlane {
    public static final int VRAM_BASE = 0xC000;

    public enum Phase { INITIAL_ROW, TOP_ROW, BOTTOM_ROW, LEFT_COLUMN, RIGHT_COLUMN }
    public enum Boundary { LAYOUT_READ, CHUNK_READ, METADATA_BYTE, BLOCK_READ, PLANE_WRITE }

    /**
     * Only the inherited byte used by DrawBlock, or a cursor into this exact immutable
     * chunk source. This is finite source data, never a register or arbitrary RAM owner.
     */
    public sealed interface Metadata permits CallerMetadata, ChunkMetadata { }

    /** Unknown is preserved until a blank layout actually reaches the two metadata bit tests. */
    public record CallerMetadata(OptionalInt highByte) implements Metadata {
        public CallerMetadata {
            Objects.requireNonNull(highByte, "explicit inherited metadata knowledge");
            if (highByte.isPresent() && (highByte.getAsInt() & ~0xFF) != 0) {
                throw new IllegalArgumentException("Inherited metadata must be an unsigned byte");
            }
        }
    }

    /** Even an unavailable caller-supplied address is checked only when its byte is reached. */
    public record ChunkMetadata(int byteOffset) implements Metadata { }

    /** Completed immutable observations only; no draw/upload commands or authority. */
    public record BlockRead(Phase phase, int relativeX, int relativeY, int worldXWord,
                            int worldYWord, int layoutByteOffset, int rawLayoutByte,
                            Metadata metadata, int metadataHighByte, int blockByteOffset,
                            int upperPlaneByteOffset, int lowerPlaneByteOffset) { }

    public record Result(State state, int flagsWord, Metadata metadata, List<BlockRead> blocks) {
        public Result {
            Objects.requireNonNull(state, "current foreground plane");
            word(flagsWord);
            Objects.requireNonNull(metadata, "inherited or reached metadata");
            blocks = List.copyOf(blocks);
        }
    }

    /**
     * The exact reached native prefix, including consumed flag bits, changed metadata and
     * a possible upper LONG already written. The input State remains immutable.
     */
    public static final class InvalidPlane extends IllegalArgumentException {
        private final Boundary boundary;
        private final int byteOffset;
        private final Phase phase;
        private final Result completedPrefix;

        private InvalidPlane(Boundary boundary, int byteOffset, Work work) {
            super(boundary + " at byte " + byteOffset + " during " + work.phase);
            this.boundary = boundary;
            this.byteOffset = byteOffset;
            phase = work.phase;
            completedPrefix = work.result();
        }
        public Boundary boundary() { return boundary; }
        public int byteOffset() { return byteOffset; }
        public Phase phase() { return phase; }
        public Result completedPrefix() { return completedPrefix; }
    }

    private final CdAssetBytes blocks;
    private final CdAssetBytes chunks;
    private final CdAssetBytes layout;

    private CdR11bForegroundPlane(CdAssetBytes blocks, CdAssetBytes chunks, CdAssetBytes layout) {
        this.blocks = Objects.requireNonNull(blocks, "prepared block bytes");
        this.chunks = Objects.requireNonNull(chunks, "original chunk bytes");
        this.layout = Objects.requireNonNull(layout, "current layout image");
    }

    /** Borrows the authenticated aggregate; it neither runs LoadLevelData nor makes bytes current. */
    public static CdR11bForegroundPlane bind(CdR11bAssets assets) {
        return fromTerrain(Objects.requireNonNull(assets, "actual Past assets").terrain());
    }

    /** Explicit immutable format binding; a shape is not native load or media authority. */
    public static CdR11bForegroundPlane fromTerrain(CdStageTerrain terrain) {
        Objects.requireNonNull(terrain, "Past terrain");
        if (terrain.shape() != CdStageTerrain.Shape.PPZ1_PAST) {
            throw new IllegalArgumentException("Expected explicit PPZ1 Past terrain");
        }
        return fromTables(terrain.blocks(), terrain.chunks(), terrain.layout().ram());
    }

    /** Constructed-format seam. No eager scan, padding, implicit RAM clear or fallback is supplied. */
    public static CdR11bForegroundPlane fromTables(CdAssetBytes blocks, CdAssetBytes chunks,
                                                  CdAssetBytes layout) {
        return new CdR11bForegroundPlane(blocks, chunks, layout);
    }

    /** Sixteen rows, 32 blocks per row, X relative0 and Y relative-16..224; no flags are consumed. */
    public Result initialize(State before, Camera liveCamera, int flagsWord, Metadata inherited) {
        var work = begin(before, liveCamera, flagsWord, inherited);
        work.phase = Phase.INITIAL_ROW;
        for (int y = -16; y <= 224; y += 16) {
            row(work, liveCamera, 0, y, 32);
        }
        return work.result();
    }

    /** All four HIGH-byte flags are independent and run top, bottom, left, right in source order. */
    public Result incremental(State before, Camera copiedCamera, int flagsWord, Metadata inherited) {
        var work = begin(before, copiedCamera, flagsWord, inherited);
        // Native TST.B sees the high byte. Bits4..7 and the complete low byte are nonwrites.
        if ((flagsWord & 0xFF00) == 0) return work.result();
        for (int bit = 0; bit < 4; bit++) {
            int mask = 1 << (bit + 8);
            boolean reached = (work.flags & mask) != 0;
            work.flags &= ~mask; // BCLR executes before the selected draw or its first unavailable read.
            if (!reached) continue;
            switch (bit) {
                case 0 -> { work.phase = Phase.TOP_ROW; row(work, copiedCamera, -16, -16, 22); }
                case 1 -> { work.phase = Phase.BOTTOM_ROW; row(work, copiedCamera, -16, 224, 22); }
                case 2 -> { work.phase = Phase.LEFT_COLUMN; column(work, copiedCamera, -16); }
                case 3 -> { work.phase = Phase.RIGHT_COLUMN; column(work, copiedCamera, 320); }
                default -> throw new AssertionError("Finite foreground flag");
            }
        }
        return work.result();
    }

    private static Work begin(State before, Camera camera, int flagsWord, Metadata inherited) {
        Objects.requireNonNull(before, "current foreground plane");
        Objects.requireNonNull(camera, "actual live or copied camera");
        word(flagsWord);
        Objects.requireNonNull(inherited, "actual inherited metadata knowledge");
        return new Work(before, flagsWord, inherited);
    }

    private void row(Work work, Camera camera, int firstX, int y, int count) {
        int worldY = (camera.yWord() + y) & 0xFFFF;
        int firstWorldX = (camera.xWord() + firstX) & 0xFFFF;
        int first = planeOffset(firstWorldX, worldY);
        for (int i = 0; i < count; i++) {
            int x = firstX + i * 16;
            int worldX = (camera.xWord() + x) & 0xFFFF;
            // DrawBlockRow2's ADDQ.B4 / ANDI.B7F wraps only the row-command byte.
            int upper = (first & ~0x7F) | ((first + i * 4) & 0x7F);
            block(work, x, y, worldX, worldY, upper);
        }
    }

    private void column(Work work, Camera camera, int x) {
        int worldX = (camera.xWord() + x) & 0xFFFF;
        int firstWorldY = (camera.yWord() - 16) & 0xFFFF;
        int first = planeOffset(worldX, firstWorldY);
        for (int i = 0; i < 16; i++) {
            int y = -16 + i * 16;
            int worldY = (camera.yWord() + y) & 0xFFFF;
            // DrawBlockCol's ADDI.W100 / ANDI.WFFF retains its exact plane ring.
            int upper = (first + i * 0x100) & 0xFFF;
            block(work, x, y, worldX, worldY, upper);
        }
    }

    private void block(Work work, int x, int y, int worldX, int worldY, int upper) {
        int layoutOffset = ((worldY >>> 1) & 0x380) + ((worldX >>> 8) & 0x7F);
        int rawLayout = byteAt(layout, layoutOffset, Boundary.LAYOUT_READ, work);
        int blockOffset = 0;
        if (rawLayout != 0) {
            // Test the original raw BYTE. 80 is nonblank and selects chunk128.
            int chunkOffset = ((rawLayout - 1) & 0x7F) * 512
                    + ((worldY * 2) & 0x1E0) + ((worldX >>> 3) & 0x1E);
            work.metadata = new ChunkMetadata(chunkOffset); // MOVEA precedes the actual WORD read.
            int rawBlock = wordAt(chunks, chunkOffset, Boundary.CHUNK_READ, work);
            blockOffset = (rawBlock & 0x3FF) * 8;
        }
        // A blank GetBlockData leaves the inherited A0 association untouched and uses block0.
        int high = metadataByte(work);
        int lower = upper + 0x80;
        boolean flipX = (high & 8) != 0;
        boolean flipY = (high & 16) != 0;
        int first = longAt(blocks, blockOffset, Boundary.BLOCK_READ, work);
        if (flipY) {
            // Native Y/XY reads both original LONGs before its first plane write.
            int second = longAt(blocks, blockOffset + 4, Boundary.BLOCK_READ, work);
            pair(work, upper, second, flipX, 0x1000 | (flipX ? 0x800 : 0));
            pair(work, lower, first, flipX, 0x1000 | (flipX ? 0x800 : 0));
        } else {
            // Native normal/X commits the upper LONG before reading the lower source LONG.
            pair(work, upper, first, flipX, flipX ? 0x800 : 0);
            int second = longAt(blocks, blockOffset + 4, Boundary.BLOCK_READ, work);
            pair(work, lower, second, flipX, flipX ? 0x800 : 0);
        }
        work.reads.add(new BlockRead(work.phase, x, y, worldX, worldY, layoutOffset,
                rawLayout, work.metadata, high, blockOffset, upper, lower));
    }

    private int metadataByte(Work work) {
        if (work.metadata instanceof CallerMetadata caller) {
            if (caller.highByte().isEmpty()) throw new InvalidPlane(Boundary.METADATA_BYTE, -1, work);
            return caller.highByte().getAsInt();
        }
        return byteAt(chunks, ((ChunkMetadata) work.metadata).byteOffset(), Boundary.METADATA_BYTE, work);
    }

    private static void pair(Work work, int at, int bits, boolean flipX, int xor) {
        int left = (bits >>> 16) & 0xFFFF;
        int right = bits & 0xFFFF;
        if (flipX) { int old = left; left = right; right = old; }
        if (at < 0 || at > work.ram.length - 4) throw new InvalidPlane(Boundary.PLANE_WRITE, at, work);
        left ^= xor; right ^= xor;
        work.ram[at] = (byte) (left >>> 8); work.ram[at + 1] = (byte) left;
        work.ram[at + 2] = (byte) (right >>> 8); work.ram[at + 3] = (byte) right;
        work.wrote = true;
    }

    private static int planeOffset(int x, int y) {
        return ((y & 0xF0) << 4) + ((x & 0x1F0) >>> 2);
    }
    private static int byteAt(CdAssetBytes bytes, int at, Boundary boundary, Work work) {
        check(bytes, at, 1, boundary, work); return bytes.unsignedByte(at);
    }
    private static int wordAt(CdAssetBytes bytes, int at, Boundary boundary, Work work) {
        check(bytes, at, 2, boundary, work); return bytes.unsignedWord(at);
    }
    private static int longAt(CdAssetBytes bytes, int at, Boundary boundary, Work work) {
        check(bytes, at, 4, boundary, work); return (int) bytes.unsignedLong(at);
    }
    private static void check(CdAssetBytes bytes, int at, int width, Boundary boundary, Work work) {
        if (at < 0 || at > bytes.length() - width) throw new InvalidPlane(boundary, at, work);
    }
    private static void word(int value) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Expected an unsigned flag WORD");
    }

    private static final class Work {
        private final State before;
        private final byte[] ram;
        private final ArrayList<BlockRead> reads = new ArrayList<>();
        private int flags;
        private Metadata metadata;
        private Phase phase = Phase.INITIAL_ROW;
        private boolean wrote;

        private Work(State before, int flags, Metadata metadata) {
            this.before = before;
            ram = before.nameTable().copy();
            this.flags = flags;
            this.metadata = metadata;
        }
        private Result result() {
            return new Result(wrote ? new State(CdAssetBytes.copyOf(ram)) : before, flags, metadata, reads);
        }
    }
}

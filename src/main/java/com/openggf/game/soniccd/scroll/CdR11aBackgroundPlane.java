package com.openggf.game.soniccd.scroll;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAsset;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.level.CdStageTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure bounded R11A native Plane B name-table owner. Native source pin: 1ecc5f3a.
 * InitLevelDrawBG / DrawLevelBG1 / DrawBGBlockRow (PP Scroll:892-995,1092-1198),
 * GetBlockData / GetBlockVDPCmd / DrawBlock (Block Draw.asm:111-234,376-395).
 * Shipped FixBugs=0 behavior; no generic renderer, camera tick, PLC or publication service.
 * Only immutable terrain and source views are retained. Output observations describe a
 * completed RAM transformation, and are not an upload/replay command stream.
 * The strict R11A factories keep their original bounds. A package-private Past binding
 * shares this algorithm with a separately named facade, without decoding or retaining another terrain owner.
 */
public final class CdR11aBackgroundPlane {
    // InitVDP/VDPInitRegs (Functions (General).asm:149,162,173-174), Main.asm:229-231.
    public static final int WIDTH_WORDS = 64;
    public static final int HEIGHT_WORDS = 32;
    public static final int NAME_TABLE_BYTES = 4096;
    public static final int VRAM_BASE = 0xE000;

    public enum Boundary {
        STATE_SHAPE, TABLE_SHAPE, VIEW_BINDING, SECTION_READ, CAMERA_POINTER_READ,
        CAMERA_ADDRESS, LAYOUT_READ, CHUNK_READ, BLOCK_READ, PLANE_WRITE
    }

    /** Stops at the actual missing source/read/write; no partial State is published. */
    public static final class InvalidPlane extends IllegalArgumentException {
        private final Boundary boundary;
        private final int byteOffset;
        private InvalidPlane(Boundary boundary, int byteOffset) {
            super(boundary + " at byte " + byteOffset);
            this.boundary = boundary;
            this.byteOffset = byteOffset;
        }
        public Boundary boundary() { return boundary; }
        public int byteOffset() { return byteOffset; }
    }

    /** Exact caller-supplied native RAM, never implicitly cleared at construction/init/restore. */
    public record State(CdAssetBytes nameTable) {
        public State {
            Objects.requireNonNull(nameTable, "nameTable");
            shape(nameTable, NAME_TABLE_BYTES, Boundary.STATE_SHAPE);
        }
        public int word(int x, int y) {
            Objects.checkIndex(x, WIDTH_WORDS);
            Objects.checkIndex(y, HEIGHT_WORDS);
            return nameTable.unsignedWord((y * WIDTH_WORDS + x) * 2);
        }
    }

    /** Complete native fixed-point bits; helpers read only the unsigned integer words. */
    public record Camera(int xLong, int yLong) {
        public int xWord() { return xLong >>> 16; }
        public int yWord() { return yLong >>> 16; }
    }

    /** The initial BGCameraSects table points here. Also supplies live cameraBgY for ID lookup. */
    public record LiveCameras(Camera bg1, Camera bg2, Camera bg3) {
        public LiveCameras {
            Objects.requireNonNull(bg1, "bg1");
            Objects.requireNonNull(bg2, "bg2");
            Objects.requireNonNull(bg3, "bg3");
        }
    }

    /** Explicit service/capture cut; this class never copies or updates live cameras itself. */
    public record CopiedCameras(Camera bg1, Camera bg2, Camera bg3) {
        public CopiedCameras {
            Objects.requireNonNull(bg1, "bg1");
            Objects.requireNonNull(bg2, "bg2");
            Objects.requireNonNull(bg3, "bg3");
        }
    }

    /** BCLR/TST/CLR at (a2) consume only BG1's HIGH byte. Unused BG2/BG3 words survive. */
    public record FlagWords(int bg1, int bg2, int bg3) {
        public FlagWords {
            if ((bg1 & ~0xFFFF) != 0 || (bg2 & ~0xFFFF) != 0 || (bg3 & ~0xFFFF) != 0) {
                throw new IllegalArgumentException("Expected three unsigned native flag words");
            }
        }
    }

    public enum Phase { INITIAL_ROW, INCREMENTAL_ROW, INCREMENTAL_COLUMN }
    public enum MetadataSource { SECTION_IDS, CHUNKS }
    /** Addresses are native RAM associations, not MMD offsets or an emulated address space. */
    public enum CameraSource {
        LIVE_BG1(0xFFF708), LIVE_BG2(0xFFF710), LIVE_BG3(0xFFF718),
        COPIED_BG1(0xFF192E), COPIED_BG2(0xFF1936), COPIED_BG3(0xFF193E);
        private final int nativeAddress;
        CameraSource(int nativeAddress) { this.nativeAddress = nativeAddress; }
        public int nativeAddress() { return nativeAddress; }
    }

    /** Every actual ID read, including skipped column rows; static selector zero is not a null pointer. */
    public record SectionRead(Phase phase, int tableIndex, int selector, int relativeY, boolean drawn) { }
    /** Only the high byte is observed by DrawBlock's two memory BTSTs. */
    public record MetadataRead(MetadataSource source, int byteOffset, int highByte) { }
    public record BlockRead(Phase phase, CameraSource camera, int relativeX, int relativeY,
                            int worldXWord, int worldYWord, int layoutByteOffset, int layoutRawByte,
                            MetadataRead metadata, int blockByteOffset,
                            int upperVdpCommand, int lowerVdpCommand) { }
    public record Result(State state, FlagWords flags, List<SectionRead> sections, List<BlockRead> blocks) {
        public Result {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(flags, "flags");
            sections = List.copyOf(sections);
            blocks = List.copyOf(blocks);
        }
    }

    private final CdAssetBytes terrainBlocks;
    private final CdAssetBytes terrainChunks;
    private final CdAssetBytes terrainLayout;
    private final int initialRowMask;
    private final CdAssetBytes ids;
    private final CdAssetBytes livePointers;

    private CdR11aBackgroundPlane(CdR11aTerrain terrain, CdAssetBytes ids, CdAssetBytes livePointers) {
        Objects.requireNonNull(terrain, "terrain");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.livePointers = Objects.requireNonNull(livePointers, "livePointers");
        shape(ids, 130, Boundary.TABLE_SHAPE);
        shape(livePointers, 16, Boundary.TABLE_SHAPE);
        this.terrainBlocks = terrain.blocks();
        this.terrainChunks = terrain.chunks();
        this.terrainLayout = terrain.layout().ram();
        this.initialRowMask = 0x7F0;
    }

    private CdR11aBackgroundPlane(CdStageTerrain terrain, CdAssetBytes ids, CdAssetBytes livePointers) {
        Objects.requireNonNull(terrain, "terrain");
        if (terrain.shape() != CdStageTerrain.Shape.PPZ1_PAST) {
            throw new IllegalArgumentException("Expected the explicit PPZ1 Past terrain format");
        }
        this.ids = Objects.requireNonNull(ids, "ids");
        this.livePointers = Objects.requireNonNull(livePointers, "livePointers");
        shape(ids, 34, Boundary.TABLE_SHAPE);
        shape(livePointers, 16, Boundary.TABLE_SHAPE);
        this.terrainBlocks = terrain.blocks();
        this.terrainChunks = terrain.chunks();
        this.terrainLayout = terrain.layout().ram();
        this.initialRowMask = 0x1F0;
    }

    // Past InitLevelDrawBG:899-914, BGCameraSectIDs/BGCameraSects:934-947, N1ecc5f3a.
    // Same DrawLevelBG1/Block Draw algorithm; only source shapes and initial row mask differ.
    static CdR11aBackgroundPlane forPastTables(CdStageTerrain terrain, CdAssetBytes ids, CdAssetBytes pointers) {
        return new CdR11aBackgroundPlane(terrain, ids, pointers);
    }

    static CdR11aBackgroundPlane forPastViews(CdStageTerrain terrain, CdR11bAssets.View ids,
                                             CdR11bAssets.View pointers) {
        checkPastView(ids, CdR11bAssets.Asset.BG_CAMERA_SECT_IDS, 34);
        checkPastView(pointers, CdR11bAssets.Asset.BG_CAMERA_SECTS, 16);
        return forPastTables(terrain, ids.bytes(), pointers.bytes());
    }

    private static void checkPastView(CdR11bAssets.View view, CdR11bAssets.Asset asset, int length) {
        Objects.requireNonNull(view, "view");
        if (view.asset() != asset || view.range() == null || view.bytes() == null
                || view.range().length() != length || view.bytes().length() != length) {
            throw new InvalidPlane(Boundary.VIEW_BINDING, 0);
        }
    }

    /** Uses existing authenticated byte-pipeline products; never opens media or disassembly. */
    public static CdR11aBackgroundPlane bind(CdR11aAssets assets) {
        Objects.requireNonNull(assets, "assets");
        return fromViews(assets.terrain(), assets.view(CdR11aAsset.BG_CAMERA_SECT_IDS),
                assets.view(CdR11aAsset.BG_CAMERA_SECTS));
    }

    /** Shape/selector binding only. Constructed Views cannot establish media authority. */
    public static CdR11aBackgroundPlane fromViews(CdR11aTerrain terrain, CdR11aAssets.View ids,
                                                  CdR11aAssets.View pointers) {
        checkView(ids, CdR11aAsset.BG_CAMERA_SECT_IDS, 130);
        checkView(pointers, CdR11aAsset.BG_CAMERA_SECTS, 16);
        return fromTables(terrain, ids.bytes(), pointers.bytes());
    }

    /** Unauthenticated pure format-fixture seam; bytes retain their immutable borrow semantics. */
    public static CdR11aBackgroundPlane fromTables(CdR11aTerrain terrain, CdAssetBytes ids, CdAssetBytes pointers) {
        return new CdR11aBackgroundPlane(terrain, ids, pointers);
    }

    /** InitLevelDrawBG: exactly 16 rows at -16..224; flags and unaddressed RAM remain supplied. */
    public Result initialize(State before, LiveCameras live, FlagWords flags) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(live, "live");
        Objects.requireNonNull(flags, "flags");
        Work work = new Work(before);
        for (int y = -16; y <= 224; y += 16) {
            int index = 1 + (((live.bg1().yWord() + y) & initialRowMask) >>> 4);
            int selector = section(index);
            work.sections.add(new SectionRead(Phase.INITIAL_ROW, index, selector, y, true));
            Selection selection = initialSelection(selector, live);
            drawRow(work, Phase.INITIAL_ROW, y, selector == 0, selection);
        }
        return work.finish(before, flags);
    }

    /**
     * DrawLevelBG1 uses LIVE BG1 Y for all ID reads, then its LOCAL .CameraSects
     * chooses COPIED X/Y for block/VDP helpers. Top beats bottom; right beats all left bits.
     * No synthetic frame readiness, lock, tick, flag generation or copy service is added.
     */
    public Result incremental(State before, LiveCameras live, CopiedCameras copied, FlagWords flags) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(live, "live");
        Objects.requireNonNull(copied, "copied");
        Objects.requireNonNull(flags, "flags");
        Work work = new Work(before);
        int high = flags.bg1() >>> 8;
        int y = -16;
        boolean row = (high & 1) != 0;
        high &= ~1;
        if (!row) {
            row = (high & 2) != 0;
            high &= ~2;
            y = 224;
        }
        if (row) {
            int index = 1 + (((short) ((live.bg1().yWord() + y) & 0xFFF0)) >> 4);
            int selector = section(index);
            work.sections.add(new SectionRead(Phase.INCREMENTAL_ROW, index, selector, y, true));
            // EXT.W then ADD.W; static BEQ tests ADD's zero flag (MOVEA does not change it).
            Selection selection = copiedSelection((byte) selector, copied);
            drawRow(work, Phase.INCREMENTAL_ROW, y, selector == 0, selection);
        }
        if (high != 0) {
            int right = high & 0xA8;
            int x = -16;
            if (right != 0) {
                high = right >>> 1; // Native MOVE.B discards every other pending bit.
                x = 320;
            }
            int start = ((short) (live.bg1().yWord() & 0xFFF0)) >> 4;
            for (int i = 0; i < 16; i++) {
                int index = start + i;
                int selector = section(index);
                boolean draw = selector != 0 && (high & (1 << (selector & 7))) != 0;
                int relativeY = -16 + i * 16;
                work.sections.add(new SectionRead(Phase.INCREMENTAL_COLUMN, index, selector, relativeY, draw));
                if (draw) {
                    Selection selection = copiedSelection(selector, copied);
                    int wx = (selection.camera().xWord() + x) & 0xFFFF;
                    int wy = (selection.camera().yWord() + relativeY) & 0xFFFF;
                    // A0 already advanced by MOVE.B (a0)+. Preserve exactly that source cursor.
                    drawBlock(work, Phase.INCREMENTAL_COLUMN, selection, x, relativeY, wx, wy,
                            planeOffset(wx, wy), new MetadataCursor(MetadataSource.SECTION_IDS, index + 1));
                }
            }
            high = 0; // CLR.B only after all 16 ID reads completed.
        }
        FlagWords remaining = new FlagWords((high << 8) | (flags.bg1() & 0xFF), flags.bg2(), flags.bg3());
        return work.finish(before, remaining);
    }

    private void drawRow(Work work, Phase phase, int y, boolean absoluteX, Selection selection) {
        int firstX = absoluteX ? 0 : -16;
        int count = absoluteX ? 32 : 22;
        int worldY = (selection.camera().yWord() + y) & 0xFFFF;
        int worldX = (absoluteX ? 0 : selection.camera().xWord() + firstX) & 0xFFFF;
        int firstOffset = planeOffset(worldX, worldY);
        // Before the first GetBlockData the caller's A0 is always BGCameraSectIDs+1.
        MetadataCursor metadata = new MetadataCursor(MetadataSource.SECTION_IDS, 1);
        for (int i = 0; i < count; i++) {
            int relativeX = firstX + i * 16;
            int wx = (absoluteX ? relativeX : selection.camera().xWord() + relativeX) & 0xFFFF;
            // ADDQ.B #4 then ANDI.B #$7F: ring only the low seven command bits.
            int offset = (firstOffset & ~0x7F) | ((firstOffset + i * 4) & 0x7F);
            metadata = drawBlock(work, phase, selection, relativeX, y, wx, worldY, offset, metadata);
        }
    }

    private MetadataCursor drawBlock(Work work, Phase phase, Selection selection, int relativeX, int relativeY,
                                      int worldX, int worldY, int offset, MetadataCursor metadata) {
        // GetBlockDataAbsXY: BG base+$40, masked native row stride and seven-bit column.
        // Columns >=64 really can address the following FG half within retained $800 RAM.
        int layoutOffset = 0x40 + ((worldY >>> 1) & 0x380) + ((worldX >>> 8) & 0x7F);
        int rawLayout = readByte(terrainLayout, layoutOffset, Boundary.LAYOUT_READ);
        int blockOffset = 0;
        if (rawLayout != 0) {
            // The native BEQ tests the original BYTE, before removing the loop flag.
            // $80 is therefore chunk 128, not a blank semantic chunk ID.
            int chunkOffset = ((rawLayout - 1) & 0x7F) * 512
                    + ((worldY * 2) & 0x1E0) + ((worldX >>> 3) & 0x1E);
            int rawBlock = readWord(terrainChunks, chunkOffset, Boundary.CHUNK_READ);
            metadata = new MetadataCursor(MetadataSource.CHUNKS, chunkOffset);
            blockOffset = (rawBlock & 0x3FF) * 8;
        }
        // Blank layout leaves A0 unchanged and A1 at blockBuffer. Do not fabricate zero tiles
        // or read an entire metadata word: DrawBlock observes just two bits in its high byte.
        int high = metadata.source() == MetadataSource.CHUNKS
                ? readByte(terrainChunks, metadata.byteOffset(), Boundary.CHUNK_READ)
                : section(metadata.byteOffset());
        int flip = ((high & 8) != 0 ? 0x800 : 0) | ((high & 16) != 0 ? 0x1000 : 0);
        int[] words = new int[4];
        for (int i = 0; i < 4; i++) { words[i] = readWord(terrainBlocks, blockOffset + i * 2, Boundary.BLOCK_READ); }
        // Equivalent to native long swaps / XORs, retaining palette, priority and existing flips.
        int topLeft = words[((flip & 0x1000) != 0 ? 2 : 0) + ((flip & 0x800) != 0 ? 1 : 0)] ^ flip;
        int topRight = words[((flip & 0x1000) != 0 ? 2 : 0) + ((flip & 0x800) != 0 ? 0 : 1)] ^ flip;
        int bottomLeft = words[((flip & 0x1000) != 0 ? 0 : 2) + ((flip & 0x800) != 0 ? 1 : 0)] ^ flip;
        int bottomRight = words[((flip & 0x1000) != 0 ? 0 : 2) + ((flip & 0x800) != 0 ? 0 : 1)] ^ flip;
        int upper = ((0x6000 | offset) << 16) | 3;
        int lower = upper + 0x800000;
        store(work.ram, upper, topLeft, topRight);
        store(work.ram, lower, bottomLeft, bottomRight);
        work.blocks.add(new BlockRead(phase, selection.source(), relativeX, relativeY, worldX, worldY,
                layoutOffset, rawLayout, new MetadataRead(metadata.source(), metadata.byteOffset(), high),
                blockOffset, upper, lower));
        return metadata;
    }

    private Selection initialSelection(int selector, LiveCameras live) {
        int offset = selector * 2;
        checkRead(livePointers, offset, 4, Boundary.CAMERA_POINTER_READ);
        long pointer = livePointers.unsignedLong(offset);
        // Initial table supplies actual address longs. Never infer static from a null pointer.
        if (pointer == CameraSource.LIVE_BG1.nativeAddress()) { return new Selection(live.bg1(), CameraSource.LIVE_BG1); }
        if (pointer == CameraSource.LIVE_BG2.nativeAddress()) { return new Selection(live.bg2(), CameraSource.LIVE_BG2); }
        if (pointer == CameraSource.LIVE_BG3.nativeAddress()) { return new Selection(live.bg3(), CameraSource.LIVE_BG3); }
        throw new InvalidPlane(Boundary.CAMERA_ADDRESS, offset);
    }

    private static Selection copiedSelection(int selector, CopiedCameras copied) {
        // Semantic bindings to the distinct .CameraSects longs (PP Scroll:962-966),
        // Main CPU Variables.inc:169-174: $FF192E / $FF1936 / $FF193E.
        // No literal runtime table is fabricated; unsupported accesses stop at this finite association.
        return switch (selector * 2) {
            case 0, 4 -> new Selection(copied.bg1(), CameraSource.COPIED_BG1);
            case 8 -> new Selection(copied.bg2(), CameraSource.COPIED_BG2);
            case 12 -> new Selection(copied.bg3(), CameraSource.COPIED_BG3);
            default -> throw new InvalidPlane(Boundary.CAMERA_POINTER_READ, selector * 2);
        };
    }

    private int section(int index) { return readByte(ids, index, Boundary.SECTION_READ); }
    private static int planeOffset(int x, int y) { return ((y & 0xF0) << 4) + ((x & 0x1F0) >>> 2); }
    private static void store(byte[] ram, int command, int left, int right) {
        int vramAddress = ((command >>> 16) & 0x3FFF) | ((command & 3) << 14);
        int at = vramAddress - VRAM_BASE;
        if (at < 0 || at > ram.length - 4 || (at & 1) != 0) { throw new InvalidPlane(Boundary.PLANE_WRITE, at); }
        ram[at] = (byte) (left >>> 8);
        ram[at + 1] = (byte) left;
        ram[at + 2] = (byte) (right >>> 8);
        ram[at + 3] = (byte) right;
    }
    private static int readByte(CdAssetBytes bytes, int offset, Boundary boundary) {
        checkRead(bytes, offset, 1, boundary);
        return bytes.unsignedByte(offset);
    }
    private static int readWord(CdAssetBytes bytes, int offset, Boundary boundary) {
        checkRead(bytes, offset, 2, boundary);
        return bytes.unsignedWord(offset);
    }
    private static void checkRead(CdAssetBytes bytes, int offset, int count, Boundary boundary) {
        if (offset < 0 || offset > bytes.length() - count) { throw new InvalidPlane(boundary, offset); }
    }
    private static void shape(CdAssetBytes bytes, int length, Boundary boundary) {
        if (bytes.length() != length) { throw new InvalidPlane(boundary, bytes.length()); }
    }
    private static void checkView(CdR11aAssets.View view, CdR11aAsset asset, int length) {
        Objects.requireNonNull(view, "view");
        if (view.asset() != asset || view.range() == null || view.bytes() == null
                || view.range().length() != length || view.bytes().length() != length) {
            throw new InvalidPlane(Boundary.VIEW_BINDING, 0);
        }
    }
    private record Selection(Camera camera, CameraSource source) { }
    private record MetadataCursor(MetadataSource source, int byteOffset) { }
    /** Invocation-local scratch only; never retained by the owner, input or result. */
    private static final class Work {
        private final byte[] ram;
        private final List<SectionRead> sections = new ArrayList<>();
        private final List<BlockRead> blocks = new ArrayList<>();
        private Work(State before) { ram = before.nameTable().copy(); }
        private Result finish(State before, FlagWords flags) {
            State after = blocks.isEmpty() ? before : new State(CdAssetBytes.copyOf(ram));
            return new Result(after, flags, sections, blocks);
        }
    }
}

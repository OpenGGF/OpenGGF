package com.openggf.game.soniccd.level;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded Sonic visual prerequisites. This does not execute player animation, DPLC uploads or sprites. */
public final class CdSonicVisualAssets {
    public record MappingFrame(int relativeOffset, int pieceCount, CdAssetBytes bytes) { }
    public record ArtPiece(int rawWord) {
        public ArtPiece {
            if ((rawWord & ~0xFFFF) != 0) { throw new IllegalArgumentException("Not a DPLC word"); }
        }
        public int artByteOffset() { return (rawWord & 0xFFF) * 32; }
        public int tileCount() { return (rawWord >>> 12) + 1; }
        public int byteLength() { return tileCount() * 32; }
    }
    public record DplcFrame(int relativeOffset, List<ArtPiece> pieces, int outputBytes, CdAssetBytes bytes) {
        public DplcFrame { pieces = List.copyOf(pieces); }
    }

    /** A format may prove each mapping against this frame's upload, or require retained staging knowledge. */
    private enum MappingTiles { FORMAT_ONLY, SELECTED_DPLC_OUTPUT }

    /** Fixed native source layouts, not a caller-defined frame count or inferred padding policy. */
    public enum SourceFormat {
        FRAMES_198_WITH_EVEN_BYTE(198, 3004, 1636, 69471, 1, 69472),
        FRAMES_167(167, 2443, 1338, 57120, 0, 57120),
        // N1ecc5f3: sprites/r4/player.asm and player_gfx.asm; gfx/r4/player.unc.
        // Mapping include's enclosing EVEN is separate; raw art is already tile-aligned.
        // Full DPLC reads retain following mapping/DPLC/Points/Capsule bytes, not invented padding.
        // .GfxScript29 (src/sprites/r4/player_gfx.asm:1195-1210),
        // .GfxScriptAd (src/sprites/r4/player_gfx.asm:1248-1286).
        FRAMES_188(188, 3493, 1862, 76928, 0, 82944),
        // N1ecc5f3: sprites/r8/player.asm / player_gfx.asm, objects/player.asm:2890-2928.
        // Same WORD directories, BYTE mapping counts/five-byte pieces and WORD DPLCs; independent extents.
        // All selected pieces are inside canonical art; no adjacent bytes or retained staging needed.
        FRAMES_188_COMPACT_ART(188, 2639, 1454, 59232, 0, 59232, MappingTiles.SELECTED_DPLC_OUTPUT);

        private final int frameCount, mappingMaximum, dplcMaximum, rawArtBytes, alignmentBytes, artReadBytes;
        private final MappingTiles mappingTiles;

        SourceFormat(int frameCount, int mappingMaximum, int dplcMaximum, int rawArtBytes, int alignmentBytes, int artReadBytes) {
            this(frameCount, mappingMaximum, dplcMaximum, rawArtBytes, alignmentBytes, artReadBytes, MappingTiles.FORMAT_ONLY);
        }
        SourceFormat(int frameCount, int mappingMaximum, int dplcMaximum, int rawArtBytes, int alignmentBytes, int artReadBytes,
                     MappingTiles mappingTiles) {
            this.mappingTiles = mappingTiles;
            this.frameCount = frameCount;
            this.mappingMaximum = mappingMaximum;
            this.dplcMaximum = dplcMaximum;
            this.rawArtBytes = rawArtBytes;
            this.alignmentBytes = alignmentBytes;
            this.artReadBytes = artReadBytes;
        }
    }

    private final SourceFormat sourceFormat;
    private final CdAssetBytes mappings;
    private final CdAssetBytes dplcs;
    private final CdAssetBytes art;
    private final List<MappingFrame> mappingFrames;
    private final List<DplcFrame> dplcFrames;

    private CdSonicVisualAssets(byte[] mapping, byte[] dplc, byte[] art) {
        this(SourceFormat.FRAMES_198_WITH_EVEN_BYTE, mapping, dplc, art);
    }

    private CdSonicVisualAssets(SourceFormat format, byte[] mapping, byte[] dplc, byte[] art) {
        this.sourceFormat = Objects.requireNonNull(format);
        // The 167/188 mapping includes can end on their byte count; enclosing EVEN is separate.
        int mappingMinimum = format.frameCount * 2 + (format == SourceFormat.FRAMES_198_WITH_EVEN_BYTE ? 2 : 1);
        this.mappings = boundedTable(mapping, mappingMinimum, format.mappingMaximum, "mappings");
        this.dplcs = boundedTable(dplc, format.frameCount * 2 + 2, format.dplcMaximum, "DPLCs");
        if (format == SourceFormat.FRAMES_198_WITH_EVEN_BYTE) {
            if (art.length != 69472 || art[69471] != 0) {
                throw new IllegalArgumentException("Sonic art requires the actual 69471 bytes plus one zero EVEN byte");
            }
        } else if (art.length != format.artReadBytes) {
            throw new IllegalArgumentException(format == SourceFormat.FRAMES_167
                    ? "Sonic 167-frame art requires the actual 57120 bytes without padding"
                    : format == SourceFormat.FRAMES_188
                    ? "Sonic 188-frame art requires the actual 82944-byte file-backed read view (76928 raw bytes)"
                    : "Sonic art requires the actual " + format.artReadBytes + "-byte file-backed read view (" + format.rawArtBytes + " raw bytes)");
        }
        this.art = CdAssetBytes.copyOf(art);
        List<MappingFrame> maps = new ArrayList<>();
        List<DplcFrame> frames = new ArrayList<>();
        for (int frame = 0; frame < format.frameCount; frame++) {
            int mapOffset = indexedOffset(mappings, frame, format.frameCount * 2);
            int count = mappings.unsignedByte(mapOffset);
            // The native five-byte mapping pieces are retained uninterpreted.
            // _Objects/Sonic/Data/Mappings.asm:Local_Map (198), sprites/player.asm:.Sprites (167).
            // R4 sprites/r4/player.asm:.Sprites (188) uses the same piece encoding.
            // All use signed word directory offsets and byte counts of native five-byte pieces.
            CdAssetBytes mapBody = body(mappings, mapOffset, 1 + count * 5);
            maps.add(new MappingFrame(mapOffset, count, mapBody));

            int dplcOffset = indexedOffset(dplcs, frame, format.frameCount * 2);
            int pieces = body(dplcs, dplcOffset, 2).unsignedWord(0);
            // LoadSonicDynPLC (Sonic/Main.asm:3055): low-byte SUBQ/BMI count,
            // high nibble + 1 tiles, low twelve bits * $20, sonicArtBuf capacity $300.
            if (pieces > 127) { throw new IllegalArgumentException("Unsupported native Sonic DPLC count"); }
            CdAssetBytes dplcBody = body(dplcs, dplcOffset, 2 + pieces * 2);
            List<ArtPiece> entries = new ArrayList<>();
            int outputBytes = 0;
            for (int piece = 0; piece < pieces; piece++) {
                var entry = new ArtPiece(dplcBody.unsignedWord(2 + piece * 2));
                if (entry.artByteOffset() > art.length - entry.byteLength()) {
                    throw new IllegalArgumentException(format.alignmentBytes == 1
                            ? "DPLC reads beyond file-backed Sonic art including actual EVEN byte"
                            : format == SourceFormat.FRAMES_167
                            ? "DPLC reads beyond file-backed Sonic art without padding"
                            : "DPLC reads beyond actual file-backed Sonic source view");
                }
                entries.add(entry);
                outputBytes += entry.byteLength();
            }
            if (outputBytes > 0x300) { throw new IllegalArgumentException("DPLC frame exceeds native Sonic art buffer"); }
            if (format.mappingTiles == MappingTiles.SELECTED_DPLC_OUTPUT) {
                validateSelectedMapping(mapBody, count, outputBytes / 32);
            }
            frames.add(new DplcFrame(dplcOffset, entries, outputBytes, dplcBody));
        }
        mappingFrames = List.copyOf(maps);
        dplcFrames = List.copyOf(frames);
    }

    /** Pure data decoder; the production loader separately authenticates the owning MMD and variant. */
    public static CdSonicVisualAssets decode(byte[] mappings, byte[] dplcs, byte[] artIncludingActualAlignment) {
        return new CdSonicVisualAssets(mappings, dplcs, artIncludingActualAlignment);
    }
    /** Explicit source layout; authentication of the owning programme remains the loader's responsibility. */
    public static CdSonicVisualAssets decode(SourceFormat format, byte[] mappings, byte[] dplcs, byte[] artReadView) {
        return new CdSonicVisualAssets(format, mappings, dplcs, artReadView);
    }
    public SourceFormat sourceFormat() { return sourceFormat; }
    public CdAssetBytes mappings() { return mappings; }
    public CdAssetBytes dplcs() { return dplcs; }
    public CdAssetBytes rawArt() { return art.slice(0, sourceFormat.rawArtBytes); }
    public CdAssetBytes artReadView() { return art; }
    /** The actual source alignment byte, or a failure when this source has no such byte. */
    public int alignmentByte() {
        if (sourceFormat.alignmentBytes == 0) { throw new IllegalStateException("Sonic source has no alignment byte"); }
        return art.unsignedByte(sourceFormat.rawArtBytes);
    }
    public List<MappingFrame> mappingFrames() { return mappingFrames; }
    public List<DplcFrame> dplcFrames() { return dplcFrames; }
    public CdAssetBytes artPiece(int frame, int piece) {
        ArtPiece entry = dplcFrames.get(frame).pieces().get(piece);
        return art.slice(entry.artByteOffset(), entry.byteLength());
    }

    private static void validateSelectedMapping(CdAssetBytes body, int count, int selectedTiles) {
        if (count > 128) { throw new IllegalArgumentException("Unsupported native Sonic mapping count"); }
        for (int piece = 0; piece < count; piece++) {
            int at = 1 + piece * 5;
            int size = body.unsignedByte(at + 1);
            if ((size & ~15) != 0) { throw new IllegalArgumentException("Sonic mapping size exceeds native piece format"); }
            int tileEnd = (body.unsignedWord(at + 2) & 0x7FF) + ((size >>> 2) + 1) * ((size & 3) + 1);
            if (tileEnd > 0x300 / 32) { throw new IllegalArgumentException("Sonic mapping exceeds native art buffer"); }
            if (tileEnd > selectedTiles) {
                throw new UnsupportedOperationException("Sonic mapping requires unqualified retained staging tiles");
            }
        }
    }

    private static CdAssetBytes boundedTable(byte[] input, int minimum, int maximum, String name) {
        if (input.length < minimum || input.length > maximum) { throw new IllegalArgumentException("Sonic " + name + " extent"); }
        return CdAssetBytes.copyOf(input);
    }
    private static int indexedOffset(CdAssetBytes table, int frame, int directoryBytes) {
        int offset = (short) table.unsignedWord(frame * 2);
        if (offset < directoryBytes || offset >= table.length() || (offset & 1) != 0) {
            throw new IllegalArgumentException("Sonic frame relative pointer outside its data include");
        }
        return offset;
    }
    private static CdAssetBytes body(CdAssetBytes table, int offset, int count) {
        if (count > table.length() - offset) { throw new IllegalArgumentException("Sonic frame body exceeds include"); }
        return table.slice(offset, count);
    }
}

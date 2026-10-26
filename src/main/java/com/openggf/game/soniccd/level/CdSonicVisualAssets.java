package com.openggf.game.soniccd.level;

import java.util.ArrayList;
import java.util.List;

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

    private final CdAssetBytes mappings;
    private final CdAssetBytes dplcs;
    private final CdAssetBytes art;
    private final List<MappingFrame> mappingFrames;
    private final List<DplcFrame> dplcFrames;

    private CdSonicVisualAssets(byte[] mapping, byte[] dplc, byte[] art) {
        this.mappings = boundedTable(mapping, 3004, "mappings");
        this.dplcs = boundedTable(dplc, 1636, "DPLCs");
        if (art.length != 69472 || art[69471] != 0) {
            throw new IllegalArgumentException("Sonic art requires the actual 69471 bytes plus one zero EVEN byte");
        }
        this.art = CdAssetBytes.copyOf(art);
        List<MappingFrame> maps = new ArrayList<>();
        List<DplcFrame> frames = new ArrayList<>();
        for (int frame = 0; frame < 198; frame++) {
            int mapOffset = indexedOffset(mappings, frame);
            int count = mappings.unsignedByte(mapOffset);
            // The native five-byte mapping pieces are retained uninterpreted.
            // _Objects/Sonic/Data/Mappings.asm:Local_Map (198 signed relative entries).
            CdAssetBytes mapBody = body(mappings, mapOffset, 1 + count * 5);
            maps.add(new MappingFrame(mapOffset, count, mapBody));

            int dplcOffset = indexedOffset(dplcs, frame);
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
                    throw new IllegalArgumentException("DPLC reads beyond file-backed Sonic art including actual EVEN byte");
                }
                entries.add(entry);
                outputBytes += entry.byteLength();
            }
            if (outputBytes > 0x300) { throw new IllegalArgumentException("DPLC frame exceeds native Sonic art buffer"); }
            frames.add(new DplcFrame(dplcOffset, entries, outputBytes, dplcBody));
        }
        mappingFrames = List.copyOf(maps);
        dplcFrames = List.copyOf(frames);
    }

    /** Pure data decoder; the production loader separately authenticates the owning MMD and variant. */
    public static CdSonicVisualAssets decode(byte[] mappings, byte[] dplcs, byte[] artIncludingActualAlignment) {
        return new CdSonicVisualAssets(mappings, dplcs, artIncludingActualAlignment);
    }
    public CdAssetBytes mappings() { return mappings; }
    public CdAssetBytes dplcs() { return dplcs; }
    public CdAssetBytes rawArt() { return art.slice(0, 69471); }
    public CdAssetBytes artReadView() { return art; }
    public int alignmentByte() { return art.unsignedByte(69471); }
    public List<MappingFrame> mappingFrames() { return mappingFrames; }
    public List<DplcFrame> dplcFrames() { return dplcFrames; }
    public CdAssetBytes artPiece(int frame, int piece) {
        ArtPiece entry = dplcFrames.get(frame).pieces().get(piece);
        return art.slice(entry.artByteOffset(), entry.byteLength());
    }

    private static CdAssetBytes boundedTable(byte[] input, int maximum, String name) {
        if (input.length < 398 || input.length > maximum) { throw new IllegalArgumentException("Sonic " + name + " extent"); }
        return CdAssetBytes.copyOf(input);
    }
    private static int indexedOffset(CdAssetBytes table, int frame) {
        int offset = (short) table.unsignedWord(frame * 2);
        if (offset < 396 || offset >= table.length() || (offset & 1) != 0) {
            throw new IllegalArgumentException("Sonic frame relative pointer outside its data include");
        }
        return offset;
    }
    private static CdAssetBytes body(CdAssetBytes table, int offset, int count) {
        if (count > table.length() - offset) { throw new IllegalArgumentException("Sonic frame body exceeds include"); }
        return table.slice(offset, count);
    }
}

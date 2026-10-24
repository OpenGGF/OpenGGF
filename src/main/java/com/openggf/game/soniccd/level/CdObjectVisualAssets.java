package com.openggf.game.soniccd.level;

import java.util.ArrayList;
import java.util.List;

/**
 * Finite CD object visual data, not an animator, DPLC owner or renderer.
 * Object Functions.asm:333-340,394-412 reads signed relative mapping words and five-byte pieces;
 * Object Animate.asm:29-65 reads signed relative scripts, five-bit frames and flip bits.
 * Only the FF/FC script endings consumed by Ring and Mosqui are admitted here.
 */
public final class CdObjectVisualAssets {
    public enum Terminal { LOOP_FF, ADVANCE_ROUTINE_FC }
    public record Piece(int y, int sizeByte, int rawTileWord, int x) {
        public Piece {
            if (y < -128 || y > 127 || x < -128 || x > 127 || (sizeByte & ~15) != 0
                    || (rawTileWord & ~0xFFFF) != 0) {
                throw new IllegalArgumentException("Not a native five-byte object mapping piece");
            }
        }
        public int tileOffset() { return rawTileWord & 0x7FF; }
        public int widthTiles() { return (sizeByte >>> 2) + 1; }
        public int heightTiles() { return (sizeByte & 3) + 1; }
        public int tileCount() { return widthTiles() * heightTiles(); }
    }
    public record MappingFrame(int relativeOffset, List<Piece> pieces, CdAssetBytes bytes) {
        public MappingFrame { pieces = List.copyOf(pieces); }
    }
    /** frameBytes retain native flip bits. bytes includes a real optional trailing zero, never invented padding. */
    public record AnimationScript(int relativeOffset, int delay, List<Integer> frameBytes,
                                    Terminal terminal, CdAssetBytes bytes) {
        public AnimationScript { frameBytes = List.copyOf(frameBytes); }
    }

    private final CdAssetBytes mappings;
    private final CdAssetBytes animationTable;
    private final List<MappingFrame> mappingFrames;
    private final List<AnimationScript> animations;

    private CdObjectVisualAssets(byte[] mappings, int frameCount, byte[] scripts, int animationCount, int artTileCount) {
        if (frameCount < 1 || frameCount > 32 || animationCount < 1 || animationCount > 256
                || artTileCount < 1 || artTileCount > 2048) {
            throw new IllegalArgumentException("Object visual counts exceed the bounded native contract");
        }
        this.mappings = table(mappings, frameCount);
        this.animationTable = table(scripts, animationCount);
        int[] mapOffsets = offsets(this.mappings, frameCount);
        var frames = new ArrayList<MappingFrame>();
        for (int offset : mapOffsets) {
            int end = nextDistinct(mapOffsets, offset, this.mappings.length());
            int count = this.mappings.unsignedByte(offset);
            // Native SUBQ.B/BMI admits up to 128 pieces. Higher count bytes are unsupported here.
            int length = 1 + count * 5;
            if (count > 128 || length > end - offset) {
                throw new IllegalArgumentException("Object mapping crosses its next distinct body or include end");
            }
            trailingZero(this.mappings, offset + length, end);
            var pieces = new ArrayList<Piece>();
            for (int i = 0; i < count; i++) {
                int at = offset + 1 + i * 5;
                var piece = new Piece(this.mappings.signedByte(at), this.mappings.unsignedByte(at + 1),
                        this.mappings.unsignedWord(at + 2), this.mappings.signedByte(at + 4));
                if (piece.tileOffset() > artTileCount - piece.tileCount()) {
                    throw new IllegalArgumentException("Object mapping reads beyond its associated decoded art source");
                }
                pieces.add(piece);
            }
            frames.add(new MappingFrame(offset, pieces, this.mappings.slice(offset, length)));
        }
        mappingFrames = List.copyOf(frames);
        int[] scriptOffsets = offsets(animationTable, animationCount);
        var parsedScripts = new ArrayList<AnimationScript>();
        for (int offset : scriptOffsets) {
            int end = nextDistinct(scriptOffsets, offset, animationTable.length());
            int cursor = offset + 1;
            var frameBytes = new ArrayList<Integer>();
            Terminal terminal = null;
            while (cursor < end) {
                int value = animationTable.unsignedByte(cursor++);
                if (value < 0x80) {
                    if ((value & 31) >= frameCount) {
                        throw new IllegalArgumentException("Object animation selects an absent mapping frame");
                    }
                    frameBytes.add(value);
                } else {
                    terminal = switch (value) {
                        case 0xFF -> Terminal.LOOP_FF;
                        case 0xFC -> Terminal.ADVANCE_ROUTINE_FC;
                        default -> throw new IllegalArgumentException("Object animation command has no reviewed consumer");
                    };
                    break;
                }
            }
            if (terminal == null || frameBytes.isEmpty()) {
                throw new IllegalArgumentException("Object script lacks frames or a bounded FF/FC terminal");
            }
            trailingZero(animationTable, cursor, end);
            parsedScripts.add(new AnimationScript(offset, animationTable.unsignedByte(offset), frameBytes,
                    terminal, animationTable.slice(offset, end - offset)));
        }
        animations = List.copyOf(parsedScripts);
    }

    /** Pure component decoder for constructed format inputs; does not authenticate media or create an asset owner. */
    public static CdObjectVisualAssets decode(byte[] mappings, int frameCount, byte[] scripts,
                                                int animationCount, int artTileCount) {
        return new CdObjectVisualAssets(mappings, frameCount, scripts, animationCount, artTileCount);
    }
    public CdAssetBytes mappings() { return mappings; }
    public CdAssetBytes animationTable() { return animationTable; }
    public List<MappingFrame> mappingFrames() { return mappingFrames; }
    public List<AnimationScript> animations() { return animations; }

    private static CdAssetBytes table(byte[] bytes, int count) {
        if (bytes.length < count * 2 + 1 || bytes.length > 0x7FFF) {
            throw new IllegalArgumentException("Object visual include extent");
        }
        return CdAssetBytes.copyOf(bytes);
    }
    private static int[] offsets(CdAssetBytes bytes, int count) {
        int[] offsets = new int[count];
        int minimum = bytes.length();
        for (int i = 0; i < count; i++) {
            int offset = (short) bytes.unsignedWord(i * 2);
            if (offset < count * 2 || offset >= bytes.length()) {
                throw new IllegalArgumentException("Object signed relative pointer is outside its include bodies");
            }
            // Bodies consist of bytes: actual Mosqui mapping pointers include odd offsets.
            offsets[i] = offset;
            minimum = Math.min(minimum, offset);
        }
        if (minimum != count * 2) { throw new IllegalArgumentException("Unreviewed gap after object visual index"); }
        return offsets;
    }
    private static int nextDistinct(int[] offsets, int start, int end) {
        for (int offset : offsets) { if (offset > start && offset < end) { end = offset; } }
        return end;
    }
    private static void trailingZero(CdAssetBytes bytes, int start, int end) {
        if (end != start && (end != start + 1 || bytes.unsignedByte(start) != 0)) {
            throw new IllegalArgumentException("Unreviewed bytes after object visual body");
        }
    }
}

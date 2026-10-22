package com.openggf.game.sonic2.specialstage;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Whole native sprite pieces for the retail S2 special stage.
 *
 * <p>These are mapping coordinates and destination tile words, before the object's
 * art tile, render flips or DPLC/source-art translation. A 4-by-4 piece remains
 * one piece even when its source art is rendered using sixteen separate tiles.
 * Piece order is the ROM's sprite emission order.
 *
 * <p>Stock S2 uses signed word offsets relative to the mapping table, a word
 * piece count, and eight-byte pieces: signed byte Y, size byte, 1P tile word,
 * 2P tile word, signed word X. See {@code spriteHeader}/{@code spritePiece} in
 * {@code mappings/MapMacros.asm} and {@code BuildSprites_DrawSprite} in
 * {@code s2.asm}. This format differs from the six-byte KiS2 mappings.
 */
public final class Sonic2SpecialStageRomMappings {
    private static final int PIECE_BYTES = 8;

    private Sonic2SpecialStageRomMappings() {
    }

    /**
     * Native table selections in S2 World REV01, verified against their callers'
     * mapping pointers and the supplied retail ROM. Counts are explicit because
     * frame data need not be stored in pointer order.
     */
    public enum Family {
        SONIC(0x34212, 18),             // Obj09_MapUnc_34212, obj09.asm
        TAILS(0x34B3E, 18),             // Obj10_MapUnc_34B3E, obj10.asm
        TAILS_TAILS(0x34DA8, 21),       // Obj88_MapUnc_34DA8, obj88.asm
        // Obj63_MapUnc_34492: first offset is $96, not the table's byte length $3C.
        SHADOW(0x34492, 30),
        HUD(0x7070, 4),                 // Obj5E_MapUnc_7070, obj5E.asm
        START_BANNER(0x7240, 9),        // Obj5F_MapUnc_7240, obj5F_a.asm
        DIGITS(0x72D2, 11),             // Obj5F_MapUnc_72D2, obj5F_b.asm
        MESSAGE(0x35E1E, 22),           // Obj5A_MapUnc_35E1E, obj5A.asm
        EMERALD(0x3625A, 10),           // Obj59_MapUnc_3625A, obj59.asm
        RING(0x3632A, 33),              // Obj5A_Obj5B_Obj60_MapUnc_3632A, obj5A_5B_60.asm
        BOMB(0x36508, 13);              // Obj61_MapUnc_36508, obj61.asm

        private final int tableAddress;
        private final int frameCount;

        Family(int tableAddress, int frameCount) {
            this.tableAddress = tableAddress;
            this.frameCount = frameCount;
        }

        public int tableAddress() {
            return tableAddress;
        }

        public int frameCount() {
            return frameCount;
        }
    }

    /** Immutable geometry and raw attributes of one hardware sprite piece. */
    public record Piece(int xOffset, int yOffset, int sizeByte,
                        int tileWord, int twoPlayerTileWord) {
        public int widthTiles() {
            return ((sizeByte >>> 2) & 3) + 1;
        }

        public int heightTiles() {
            return (sizeByte & 3) + 1;
        }

        public int tileIndex() {
            return tileWord & 0x7FF;
        }

        public boolean hFlip() {
            return (tileWord & 0x800) != 0;
        }

        public boolean vFlip() {
            return (tileWord & 0x1000) != 0;
        }

        public int paletteIndex() {
            return (tileWord >>> 13) & 3;
        }

        public boolean priority() {
            return (tileWord & 0x8000) != 0;
        }
    }

    /** ROM address identifies the count word; pieces retain their native order. */
    public record Frame(int romAddress, List<Piece> pieces) {
        public Frame {
            pieces = List.copyOf(pieces);
        }
    }

    /** Reads from the engine's supplied ROM pipeline; no reference-file fallback. */
    public static List<Frame> load(Rom rom, Family family) throws IOException {
        return load(RomByteReader.fromRom(rom), family);
    }

    /** Reads a family from immutable ROM bytes, including patched logical ROMs. */
    public static List<Frame> load(RomByteReader reader, Family family) {
        Objects.requireNonNull(family, "family");
        return readTable(reader, family.tableAddress(), family.frameCount());
    }

    /**
     * Reads an explicit number of stock S2 mapping pointers. Offsets may be
     * negative, reordered or shared; a zero piece count is an empty frame.
     * Truncated tables or records fail bounds checking rather than substituting
     * static geometry. The returned frames do not retain mutable reader state.
     */
    public static List<Frame> readTable(RomByteReader reader, int tableAddress, int frameCount) {
        Objects.requireNonNull(reader, "reader");
        if (frameCount < 0) {
            throw new IllegalArgumentException("frameCount must not be negative");
        }
        Objects.checkFromIndexSize(tableAddress, Math.multiplyExact(frameCount, 2), reader.size());
        List<Frame> frames = new ArrayList<>(frameCount);
        for (int frame = 0; frame < frameCount; frame++) {
            int address = Math.addExact(tableAddress, reader.readS16BE(tableAddress + frame * 2));
            int pieceCount = reader.readU16BE(address);
            int piecesAddress = address + 2;
            Objects.checkFromIndexSize(piecesAddress, pieceCount * PIECE_BYTES, reader.size());
            List<Piece> pieces = new ArrayList<>(pieceCount);
            for (int piece = 0; piece < pieceCount; piece++) {
                int cursor = piecesAddress + piece * PIECE_BYTES;
                pieces.add(new Piece(reader.readS16BE(cursor + 6), (byte) reader.readU8(cursor),
                        reader.readU8(cursor + 1), reader.readU16BE(cursor + 2),
                        reader.readU16BE(cursor + 4)));
            }
            frames.add(new Frame(address, pieces));
        }
        return List.copyOf(frames);
    }
}

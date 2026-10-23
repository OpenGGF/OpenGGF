package com.openggf.game.sonic2.specialstage;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageRomMappings.Frame;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageRomMappings.Piece;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestSonic2SpecialStageRomMappings {
    @Test
    void readsSignedReorderedAndAliasedPointersWithoutInferringTheTableLength() {
        var frames = Sonic2SpecialStageRomMappings.readTable(syntheticRom(), 0x40, 4);

        assertEquals(List.of(0x10, 0x60, 0x10, 0), frames.stream().map(Frame::romAddress).toList());
        assertEquals(List.of(2, 1, 2, 0), frames.stream().map(f -> f.pieces().size()).toList());
        assertEquals(frames.get(0), frames.get(2));
        assertEquals(List.of(new Piece(-32477, -128, 0x0B, 0xFD23, 0x1234),
                        new Piece(300, 127, 0x04, 0x2666, 0xAB89)),
                frames.getFirst().pieces());
        assertEquals(new Piece(-1, -1, 0, 0x0842, 0x8001), frames.get(1).pieces().getFirst());
    }

    @Test
    void retainsWholePieceSizeAndDecodesEveryTileWordAttribute() {
        var pieces = Sonic2SpecialStageRomMappings.readTable(syntheticRom(), 0x40, 4)
                .getFirst().pieces();
        Piece first = pieces.getFirst();
        assertEquals(3, first.widthTiles());
        assertEquals(4, first.heightTiles());
        assertEquals(0x523, first.tileIndex());
        assertTrue(first.hFlip());
        assertTrue(first.vFlip());
        assertEquals(3, first.paletteIndex());
        assertTrue(first.priority());

        Piece second = pieces.get(1);
        assertEquals(2, second.widthTiles());
        assertEquals(1, second.heightTiles());
        assertEquals(0x666, second.tileIndex());
        assertFalse(second.hFlip());
        assertFalse(second.vFlip());
        assertEquals(1, second.paletteIndex());
        assertFalse(second.priority());
        assertEquals(2, pieces.size(), "Whole pieces must not expand into their source-art tiles");
    }

    @Test
    void pieceCountIsAnUnsignedWordRatherThanAByteOrDbfCounter() {
        byte[] bytes = new byte[4 + 256 * 8];
        bytes[1] = 2; // Frame pointer.
        bytes[2] = 1; // Literal count $0100, not $00 and not $0101.
        var frames = Sonic2SpecialStageRomMappings.readTable(RomByteReader.fromBytes(bytes), 0, 1);
        assertEquals(256, frames.getFirst().pieces().size());
        assertEquals(new Piece(0, 0, 0, 0, 0), frames.getFirst().pieces().getLast());
    }

    @Test
    void framesAndPieceListsAreImmutableIncludingCallerSuppliedLists() {
        var frames = Sonic2SpecialStageRomMappings.readTable(syntheticRom(), 0x40, 4);
        assertThrows(UnsupportedOperationException.class, () -> frames.clear());
        assertThrows(UnsupportedOperationException.class, () -> frames.getFirst().pieces().clear());
        var source = new ArrayList<>(frames.getFirst().pieces());
        var snapshot = new Frame(123, source);
        source.clear();
        assertEquals(2, snapshot.pieces().size());
    }

    @Test
    void rejectsTruncatedPointersCountsAndPiecesRatherThanReturningStaticFallbacks() {
        assertThrows(IndexOutOfBoundsException.class,
                () -> Sonic2SpecialStageRomMappings.readTable(syntheticRom(), 0x7F, 1));
        assertThrows(IndexOutOfBoundsException.class,
                () -> Sonic2SpecialStageRomMappings.readTable(hexReader("0004 0000 00"), 0, 1));
        assertThrows(IndexOutOfBoundsException.class,
                () -> Sonic2SpecialStageRomMappings.readTable(hexReader("0002 0001 FF00 0000 0000 FF"), 0, 1));
        assertThrows(IndexOutOfBoundsException.class,
                () -> Sonic2SpecialStageRomMappings.readTable(hexReader("FFFE"), 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> Sonic2SpecialStageRomMappings.readTable(syntheticRom(), 0, -1));
    }

    private static RomByteReader syntheticRom() {
        byte[] bytes = new byte[0x80];
        // Deliberately distinct 1P/2P words and signed coordinates outside the byte X range.
        putHex(bytes, 0x10, "0002 800B FD23 1234 8123 7F04 2666 AB89 012C");
        putHex(bytes, 0x60, "0001 FF00 0842 8001 FFFF");
        putHex(bytes, 0x40, "FFD0 0020 FFD0 FFC0");
        return RomByteReader.fromBytes(bytes);
    }

    private static RomByteReader hexReader(String hex) {
        return RomByteReader.fromBytes(HexFormat.of().parseHex(hex.replace(" ", "")));
    }

    private static void putHex(byte[] bytes, int address, String hex) {
        byte[] data = HexFormat.of().parseHex(hex.replace(" ", ""));
        System.arraycopy(data, 0, bytes, address, data.length);
    }
}

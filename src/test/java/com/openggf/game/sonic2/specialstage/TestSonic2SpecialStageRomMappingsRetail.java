package com.openggf.game.sonic2.specialstage;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageRomMappings.Family;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageRomMappings.Frame;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageRomMappings.Piece;
import com.openggf.tests.RomTestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Retail byte oracles; opens only ROM data, without booting any game or graphics services. */
class TestSonic2SpecialStageRomMappingsRetail {
    private static RomByteReader reader;
    private static final Map<Family, List<Frame>> TABLES = new EnumMap<>(Family.class);

    @BeforeAll
    static void loadSuppliedRom() throws Exception {
        File file = RomTestUtils.ensureSonic2RomAvailable();
        assumeTrue(file != null, "S2 World REV01 ROM required (sonic2.rom.path)");
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(file.getAbsolutePath()));
            reader = RomByteReader.fromRom(rom);
            for (Family family : Family.values()) {
                TABLES.put(family, Sonic2SpecialStageRomMappings.load(rom, family));
            }
        }
    }

    @Test
    void suppliedRetailIdentityMatchesTheFrozenMappingOracles() throws Exception {
        byte[] bytes = reader.slice(0, reader.size());
        CRC32 crc = new CRC32();
        crc.update(bytes);
        assertEquals(0x7B905383L, crc.getValue());
        assertEquals("8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9",
                HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes)));
    }

    @Test
    void preservesFrozenWholePieceCountsForEveryNativeFamily() {
        assertCounts(Family.SONIC, 3,3,3,3,4,5,5,5,4,5,5,6,3,3,3,3,4,4);
        assertCounts(Family.TAILS, 3,4,4,5,3,4,5,4,4,5,5,4,2,3,2,3,2,2);
        assertCounts(Family.TAILS_TAILS, ones(21));
        assertCounts(Family.SHADOW, ones(30));
        assertCounts(Family.HUD, 4,4,4,2);
        assertCounts(Family.START_BANNER, 7,1,1,1,1,1,1,1,1);
        assertCounts(Family.DIGITS, ones(11));
        int[] messages = ones(22);
        messages[20] = 8;
        messages[21] = 4;
        assertCounts(Family.MESSAGE, messages);
        assertCounts(Family.EMERALD, ones(10));
        int[] rings = ones(33);
        rings[30] = rings[31] = rings[32] = 2;
        assertCounts(Family.RING, rings);
        int[] bombs = ones(13);
        bombs[11] = 2;
        bombs[12] = 3;
        assertCounts(Family.BOMB, bombs);
    }

    @Test
    void sonicAndTailsKeepNativeGeometryPriorityAndDestinationTileWords() {
        assertEquals(0x34236, frame(Family.SONIC, 0).romAddress());
        assertEquals(List.of(new Piece(-16, -28, 0x0F, 0x8000, 0x8000),
                        new Piece(-16, 4, 0x0A, 0x8010, 0x8008),
                        new Piece(8, 4, 0x01, 0x8019, 0x800C)),
                frame(Family.SONIC, 0).pieces());
        assertGeometry(piece(Family.SONIC, 0, 0), -16, -28, 4, 4);
        assertGeometry(piece(Family.SONIC, 3, 0), -10, -28, 3, 3);
        assertTrue(piece(Family.SONIC, 3, 0).hFlip());
        assertTrue(piece(Family.SONIC, 0, 0).priority());
        assertEquals(List.of(new Piece(-12, -24, 0x0A, 0x8000, 0x8000),
                        new Piece(-12, 0, 0x09, 0x8009, 0x8004),
                        new Piece(-12, 16, 0, 0x800F, 0x8007)),
                frame(Family.TAILS, 0).pieces());
        assertGeometry(piece(Family.TAILS_TAILS, 0, 0), -6, -6, 2, 3);
    }

    @Test
    void shadowUsesThirtyPointersInTheirNativeNonmonotonicOrder() {
        assertEquals(0x96, reader.readU16BE(0x34492));
        assertEquals(30, TABLES.get(Family.SHADOW).size());
        assertEquals(0x34528, frame(Family.SHADOW, 0).romAddress());
        assertEquals(0x344CE, frame(Family.SHADOW, 27).romAddress());
        assertGeometry(piece(Family.SHADOW, 0, 0), -16, -8, 4, 2);
        assertGeometry(piece(Family.SHADOW, 27, 0), -4, -4, 1, 1);
    }

    @Test
    void hudBannerAndDigitsRetainPalettesMirroringAliasesAndWideLastDigit() {
        Piece hud = piece(Family.HUD, 0, 2);
        assertEquals(new Piece(-104, 24, 0x0D, 0xC00A, 0xC005), hud);
        assertGeometry(hud, -104, 24, 4, 2);
        assertEquals(2, hud.paletteIndex());
        assertEquals(frame(Family.START_BANNER, 1), frame(Family.START_BANNER, 2));
        assertEquals(0x728C, frame(Family.START_BANNER, 1).romAddress());
        Piece banner = piece(Family.START_BANNER, 8, 0);
        assertEquals(new Piece(40, 0, 0x0F, 0xA800, 0xA800), banner);
        assertTrue(banner.hFlip());
        assertEquals(1, banner.paletteIndex());
        for (int frame = 0; frame < 10; frame++) {
            assertGeometry(piece(Family.DIGITS, frame, 0), -4, -8, 1, 2);
        }
        assertEquals(new Piece(-8, -4, 0x04, 0x803A, 0x801D), piece(Family.DIGITS, 10, 0));
        assertGeometry(piece(Family.DIGITS, 10, 0), -8, -4, 2, 1);
    }

    @Test
    void messagesEmeraldsRingsAndBombsRetainMultiPieceOrderAndVerticalFlip() {
        assertEquals(new Piece(-4, -8, 0x01, 0x8004, 0x8002), piece(Family.MESSAGE, 0, 0));
        assertEquals(new Piece(-24, -16, 0x0B, 0x803A, 0x801D), piece(Family.MESSAGE, 21, 0));
        assertGeometry(piece(Family.EMERALD, 9, 0), -12, -12, 3, 3);
        assertEquals(List.of(new Piece(-16, -16, 0x02, 0x8019, 0x800C),
                        new Piece(-8, -8, 0x0A, 0x801C, 0x800E)),
                frame(Family.RING, 32).pieces());
        assertEquals(List.of(new Piece(-40, -32, 0x0F, 0x8020, 0x8010),
                        new Piece(-16, -40, 0x0F, 0x9020, 0x9010),
                        new Piece(8, -32, 0x0F, 0x8820, 0x8810)),
                frame(Family.BOMB, 12).pieces());
        assertTrue(piece(Family.BOMB, 12, 1).vFlip());
        assertFalse(piece(Family.BOMB, 12, 1).hFlip());
        assertTrue(piece(Family.BOMB, 12, 2).hFlip());
    }

    @Test
    void familySelectionReadsPatchedPointersCountsGeometryAndAttributesFromMemory() {
        byte[] patched = reader.slice(0, reader.size());
        // Redirect Sonic frame zero to frame seventeen, then replace that frame's
        // count and first record. No generated static table can satisfy this.
        putWord(patched, 0x34212, 0x025E);
        putWord(patched, 0x34470, 1);
        byte[] record = HexFormat.of().parseHex("A0067ABCDEF0FED4");
        System.arraycopy(record, 0, patched, 0x34472, record.length);
        var changed = Sonic2SpecialStageRomMappings.load(RomByteReader.fromBytes(patched), Family.SONIC);
        assertEquals(0x34470, changed.getFirst().romAddress());
        assertEquals(List.of(new Piece(-300, -96, 0x06, 0x7ABC, 0xDEF0)), changed.getFirst().pieces());
        assertGeometry(changed.getFirst().pieces().getFirst(), -300, -96, 2, 3);
        assertFalse(changed.getFirst().pieces().getFirst().priority());
        assertEquals(3, frame(Family.SONIC, 0).pieces().size(), "Original asset snapshot remains unchanged");
    }

    private static Frame frame(Family family, int index) {
        return TABLES.get(family).get(index);
    }

    private static Piece piece(Family family, int frame, int piece) {
        return frame(family, frame).pieces().get(piece);
    }

    private static void assertCounts(Family family, int... expected) {
        assertEquals(Arrays.stream(expected).boxed().toList(),
                TABLES.get(family).stream().map(f -> f.pieces().size()).toList(), family.name());
    }

    private static int[] ones(int count) {
        int[] values = new int[count];
        Arrays.fill(values, 1);
        return values;
    }

    private static void assertGeometry(Piece piece, int x, int y, int width, int height) {
        assertEquals(x, piece.xOffset());
        assertEquals(y, piece.yOffset());
        assertEquals(width, piece.widthTiles());
        assertEquals(height, piece.heightTiles());
    }

    private static void putWord(byte[] bytes, int address, int word) {
        bytes[address] = (byte) (word >>> 8);
        bytes[address + 1] = (byte) word;
    }
}

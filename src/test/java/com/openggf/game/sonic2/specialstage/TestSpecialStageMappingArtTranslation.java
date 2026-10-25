package com.openggf.game.sonic2.specialstage;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Independent logical-ROM example: one SAT piece spans disjoint DPLC sources. */
class TestSpecialStageMappingArtTranslation {
    @Test
    void keepsOneNativeFootprintWhileTranslatingEveryDestinationTile() {
        byte[] bytes = new byte[0x40000];
        int mappings = Sonic2SpecialStageRomMappings.Family.SONIC.tableAddress();
        int cues = Sonic2SpecialStageConstants.PLAYER_DPLC_TABLE_OFFSET;
        for (int frame = 0; frame < 18; frame++) {
            word(bytes, mappings + frame * 2, 0x24);
            word(bytes, cues + frame * 2, 0x100);
        }
        word(bytes, mappings + 0x24, 1);
        // Native 4x1, H-flipped, starting at destination tile zero.
        byte[] piece = { -8, 12, (byte) 0x88, 0, 0, 0, (byte) 0xFF, (byte) 0xF0 };
        System.arraycopy(piece, 0, bytes, mappings + 0x26, piece.length);
        word(bytes, cues + 0x100, 2);
        word(bytes, cues + 0x102, 0x1030); // two tiles at source 3
        word(bytes, cues + 0x104, 0x10A0); // two tiles at source 10
        for (int sources : new int[]{Sonic2SpecialStageConstants.SONIC_PLAYER_DPLC_SOURCE_TABLE_OFFSET,
                Sonic2SpecialStageConstants.TAILS_PLAYER_DPLC_SOURCE_TABLE_OFFSET,
                Sonic2SpecialStageConstants.TAILS_TAILS_DPLC_SOURCE_TABLE_OFFSET}) {
            for (int section = 0; section < 4; section++) {
                bytes[sources + section * 4 + 1] = (byte) 0xFF;
            }
        }
        try (Rom rom = Rom.fromReader(RomByteReader.fromBytes(bytes), "disjoint SS DPLC example")) {
            var loader = new Sonic2SpecialStageDataLoader(rom);
            var nativeFrame = loader.getNativeMainPlayerFrame(0);
            assertEquals(1, nativeFrame.pieces().size());
            assertEquals(4, nativeFrame.pieces().getFirst().widthTiles());
            var art = loader.getMainPlayerFrame(0).pieces;
            assertEquals(4, art.length);
            assertArrayEquals(new int[]{11, 10, 4, 3}, Arrays.stream(art).mapToInt(p -> p.tileIndex).toArray());
            assertArrayEquals(new int[]{-16, -8, 0, 8}, Arrays.stream(art).mapToInt(p -> p.xOffset).toArray());
            assertTrue(Arrays.stream(art).allMatch(p -> p.widthTiles == 1 && p.heightTiles == 1 && p.hFlip));
            assertSame(nativeFrame, loader.getNativeMainPlayerFrame(0));
        }
    }

    private static void word(byte[] bytes, int address, int value) {
        bytes[address] = (byte) (value >>> 8);
        bytes[address + 1] = (byte) value;
    }
}

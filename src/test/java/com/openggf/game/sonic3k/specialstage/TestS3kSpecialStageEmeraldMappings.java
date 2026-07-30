package com.openggf.game.sonic3k.specialstage;

import com.openggf.game.GameServices;
import com.openggf.level.Pattern;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ROM checks for the Chaos/Super Emerald sprite mappings the special stage draws
 * once the last blue sphere is cleared (MapPtr_A10A entries $0B and $0D).
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kSpecialStageEmeraldMappings {

    private static final int FRAME_COUNT = 16;

    @Test
    void superEmeraldFrameZeroMatchesDisassembly() throws IOException {
        byte[] map = loader().getSuperEmeraldMap();

        // Map_SStageSuperEmerald frame pointers (word_A762 first).
        assertEquals(0x20, readWord(map, 0));
        assertEquals(0x3A, readWord(map, 2));

        // word_A762: left half, bottom strip, then the H-flipped right half.
        int frameOff = readWord(map, 0);
        assertEquals(4, readWord(map, frameOff));
        assertArrayEquals(new byte[] {
                        (byte) 0xEC, 0x0B, 0x00, 0x00, (byte) 0xFF, (byte) 0xE8,
                        0x0C, 0x08, 0x00, 0x0C, (byte) 0xFF, (byte) 0xE8,
                        (byte) 0xEC, 0x0B, 0x08, 0x00, 0x00, 0x00,
                        0x0C, 0x08, 0x08, 0x0C, 0x00, 0x00
                },
                java.util.Arrays.copyOfRange(map, frameOff + 2, frameOff + 2 + 24));
    }

    @Test
    void chaosEmeraldFramesAreSinglePieces() throws IOException {
        byte[] map = loader().getChaosEmeraldMap();

        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            int frameOff = readWord(map, frame * 2);
            assertEquals(1, readWord(map, frameOff), "frame " + frame + " piece count");
        }
    }

    @Test
    void everyEmeraldMappingPieceResolvesInsideItsArt() throws IOException {
        Sonic3kSpecialStageDataLoader loader = loader();
        assertPiecesFitArt(loader.getChaosEmeraldMap(), loader.getChaosEmeraldArt(), "Chaos");
        assertPiecesFitArt(loader.getSuperEmeraldMap(), loader.getSuperEmeraldArt(), "Super");
    }

    private static void assertPiecesFitArt(byte[] map, Pattern[] art, String name) {
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            int frameOff = readWord(map, frame * 2);
            int pieceCount = readWord(map, frameOff);
            assertTrue(pieceCount > 0 && pieceCount <= 8,
                    name + " frame " + frame + " piece count " + pieceCount);
            for (int piece = 0; piece < pieceCount; piece++) {
                int po = frameOff + 2 + piece * 6;
                int sizeByte = map[po + 1] & 0xFF;
                int tiles = (((sizeByte >> 2) & 3) + 1) * ((sizeByte & 3) + 1);
                int tileBase = readWord(map, po + 2) & 0x07FF;
                assertTrue(tileBase + tiles <= art.length,
                        name + " frame " + frame + " piece " + piece + " ends at tile "
                                + (tileBase + tiles) + " but art has " + art.length);
            }
        }
    }

    private static Sonic3kSpecialStageDataLoader loader() throws IOException {
        return new Sonic3kSpecialStageDataLoader(GameServices.rom().getRom());
    }

    private static int readWord(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }
}

package com.openggf.game.sonic2.competition;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.ZoneAct;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestSonic2CompetitionNativeArt {
    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void allSixActsKeepTheShippedAnimatedMapOverrunAndOriginalTerrain() throws Exception {
        var rom = TestEnvironment.currentRom();
        int[][] expected = {
                {0, 0, 119, 0xA788, 0x4280, 0x1398, 0xA874, 0xC000, 0x7240},
                {0, 1, 119, 0xA788, 0x4280, 0x1398, 0xA874, 0xC000, 0x7240},
                {11, 0, 0, 0, 0, 0, 0, 0, 0x7500},
                {11, 1, 0, 0, 0, 0, 0, 0, 0x7500},
                {12, 0, 127, 0xA760, 0x03A8, 0x13FC, 0xA85C, 0, 0x6600},
                {12, 1, 127, 0xA760, 0x03A8, 0x13FC, 0xA85C, 0, 0x6600}
        };
        for (int[] e : expected) {
            var profile = Sonic2CompetitionLevelProfile.load(rom, e[0], e[1]);
            var art = Sonic2CompetitionNativeArt.load(rom, profile);
            assertEquals(e[2], art.animationCopiedWords(), profile.zoneAct().toString());
            assertEquals(0x1800, art.blockTableBytes().length);
            assertArrayEquals(art.baseTerrain().chunkBytes(),
                    Arrays.copyOfRange(art.workRamBytes(), 0, 0x8000));
            assertArrayEquals(art.baseTerrain().layoutBytes(),
                    Arrays.copyOfRange(art.workRamBytes(), 0x8000, 0x9000));
            assertEquals(0x300, art.baseTerrain().primaryCollisionBytes().length);
            assertEquals(0x300, art.baseTerrain().secondaryCollisionBytes().length);
            assertEquals(e[8], art.baseTerrain().patternBytes().length);
            assertArrayEquals(art.baseTerrain().patternBytes(),
                    Arrays.copyOf(art.vram().bytes(), e[8]));
            // Initial zone art is all this image owns: no fabricated PLC, DPLC or SAT uploads.
            assertArrayEquals(new byte[0x10000 - e[8]],
                    Arrays.copyOfRange(art.vram().bytes(), e[8], 0x10000));
            if (e[2] != 0) {
                assertEquals(e[4], art.readWord(e[3]));
                assertEquals(e[5], art.readWord(0xA800));
                assertEquals(e[7], art.readWord(e[6]));
                assertEquals(0, art.readWord(e[6] + 2), "copy stops after the zero tile index");
            } else {
                assertEquals(0, art.readWord(0xA800));
            }
        }
    }

    @Test
    void eachSourceWordControlsTheCounterAndTileOneTerminatesAfterItsWrite() throws Exception {
        byte[] bytes = emptyTerrainRom(0x30);
        word(bytes, 0x10, 0x20);
        word(bytes, 0x12, 0); // A declared one-word copy still executes three native iterations.
        word(bytes, 0x14, 0xF81B);
        word(bytes, 0x16, 0xDFFD);
        word(bytes, 0x18, 0x0001);
        word(bytes, 0x1A, 0x0555);
        try (var rom = Rom.fromReader(new RomByteReader(bytes), "bounded native animation")) {
            var art = Sonic2CompetitionNativeArt.load(rom, syntheticProfile(0x10));
            assertEquals(3, art.animationCopiedWords());
            assertEquals(0xF80D, art.readWord(0x9020));
            assertEquals(0xDBFE, art.readWord(0x9022));
            assertEquals(0, art.readWord(0x9024));
            assertEquals(0, art.readWord(0x9026));
            byte[] copy = art.workRamBytes();
            copy[0x9020] = 0;
            byte[] blocks = art.blockTableBytes();
            blocks[0x20] = 0;
            assertEquals(0xF80D, art.readWord(0x9020), "published images cannot mutate native RAM");
        }
    }

    @Test
    void ramReadsAreBoundedAndDoNotWrapLikeVram() throws Exception {
        try (var rom = Rom.fromReader(new RomByteReader(emptyTerrainRom(0x20)), "empty native terrain")) {
            var art = Sonic2CompetitionNativeArt.load(rom, syntheticProfile(0x10));
            assertEquals(0, art.readWord(0xFFFE));
            assertEquals(0, art.ramByte(0xFFFF));
            assertThrows(IndexOutOfBoundsException.class, () -> art.ramByte(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> art.ramByte(0x10000));
            assertThrows(IndexOutOfBoundsException.class, () -> art.readWord(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> art.readWord(0xFFFF));
        }
    }

    @Test
    void malformedAnimationCannotWritePastWorkRam() throws Exception {
        byte[] bytes = emptyTerrainRom(0x20);
        word(bytes, 0x10, 0x7FFE); // Signed ADDA.W from $9000 would select $10FFE.
        word(bytes, 0x14, 0);
        try (var rom = Rom.fromReader(new RomByteReader(bytes), "invalid native RAM destination")) {
            assertThrows(IndexOutOfBoundsException.class,
                    () -> Sonic2CompetitionNativeArt.load(rom, syntheticProfile(0x10)));
        }
    }

    @Test
    void missingAnimationTerminatorFailsAtTheRomBoundary() throws Exception {
        byte[] bytes = emptyTerrainRom(0x18);
        word(bytes, 0x10, 0x20);
        word(bytes, 0x14, 2);
        word(bytes, 0x16, 2);
        try (var rom = Rom.fromReader(new RomByteReader(bytes), "unterminated native animation")) {
            assertThrows(IndexOutOfBoundsException.class,
                    () -> Sonic2CompetitionNativeArt.load(rom, syntheticProfile(0x10)));
        }
    }

    private static byte[] emptyTerrainRom(int size) {
        byte[] bytes = new byte[size];
        // A real standard Kosinski stream containing only its full-match terminator.
        bytes[0] = 2;
        bytes[3] = (byte) 0xF8;
        return bytes;
    }

    private static void word(byte[] bytes, int address, int value) {
        bytes[address] = (byte) (value >>> 8);
        bytes[address + 1] = (byte) value;
    }

    private static Sonic2CompetitionLevelProfile syntheticProfile(int animationAddress) {
        return new Sonic2CompetitionLevelProfile(new ZoneAct(0, 0),
                new Sonic2CompetitionLevelProfile.Art(0, 0, 0, 0, 0, 0),
                0, 0, 0, 0, 0, 0, 0,
                new Sonic2CompetitionLevelProfile.Bounds(0, 0, 0, 0),
                new Sonic2CompetitionLevelProfile.Position(0, 0),
                new Sonic2CompetitionLevelProfile.Position(0, 0),
                new Sonic2CompetitionLevelProfile.AnimationPatch(animationAddress, 0, 0, true),
                List.of(), List.of());
    }
}

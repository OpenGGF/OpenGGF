package com.openggf.game.sonic2.competition;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.Sonic2ObjectPlacement;
import com.openggf.game.sonic2.ZoneAct;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionLevelProfile {
    @Test void allSixActsUseTheNativeTablesAndOriginalSignposts() throws Exception {
        var rom = TestEnvironment.currentRom();
        var profiles = Sonic2CompetitionLevelProfile.loadAll(rom);
        int[][] expected = {
                {0, 0, 0xE684A, 135, 226, 0x45AC4, 0x28F, 0x2A40, 0x2E3},
                {0, 1, 0xE6B7A, 158, 225, 0x45C84, 0x2AF, 0x29C0, 0x429},
                {11, 0, 0xE968E, 130, 183, 0x47B24, 0x6AC, 0x2450, 0x56C},
                {11, 1, 0xE99A0, 148, 220, 0x47D24, 0x5AC, 0x21A0, 0x668},
                {12, 0, 0x1802A, 187, 232, 0x47FF4, 0x2AC, 0x2840, 0x688},
                {12, 1, 0x18492, 168, 234, 0x483C4, 0x58C, 0x2740, 0x668}
        };
        assertEquals(6, profiles.size());
        for (int i = 0; i < expected.length; i++) {
            var p = profiles.get(i);
            int[] e = expected[i];
            assertEquals(new ZoneAct(e[0], e[1]), p.zoneAct());
            assertEquals(e[2], p.objectAddress());
            assertEquals(e[3], p.objects().size());
            assertEquals(e[4], p.rings().size());
            assertEquals(e[5], p.layoutAddress());
            assertEquals(new Sonic2CompetitionLevelProfile.Position(0x60, e[6]), p.sonicStart());
            assertEquals(new Sonic2CompetitionLevelProfile.Position(0x40, e[6] + 4), p.tailsStart());
            assertEquals(1, p.signposts().size());
            assertEquals(e[7], p.signposts().getFirst().x());
            assertEquals(e[8], p.signposts().getFirst().y());
        }
        assertThrows(UnsupportedOperationException.class, () -> profiles.clear());
        assertThrows(IllegalArgumentException.class, () -> Sonic2CompetitionLevelProfile.load(rom, 13, 0));
        assertThrows(IllegalArgumentException.class, () -> Sonic2CompetitionLevelProfile.load(rom, 0, 2));
    }

    @Test void casinoUsesSeparateNativeTwoPlayerLists() throws Exception {
        var reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        for (int act = 0; act < 2; act++) {
            var p = Sonic2CompetitionLevelProfile.load(TestEnvironment.currentRom(), 12, act);
            var ordinary = new Sonic2ObjectPlacement(reader).load(new ZoneAct(12, act));
            assertNotEquals(ordinary, p.objects());
            assertEquals(0xFFFF, reader.readU16BE(p.objectAddress() + p.objects().size() * 6));
            assertEquals(0x406BE, p.animationPatch().address());
            assertEquals(0x1760, p.animationPatch().destinationByteOffset());
            assertEquals(80, p.animationPatch().declaredWordCount());
            assertTrue(p.animationPatch().overwritesLoopCounter());
        }
    }

    @Test void terrainSourcesDecodeAndBasePatternIndicesUseNativeInterlaceUnits() throws Exception {
        var rom = TestEnvironment.currentRom();
        int[][] expected = {
                {0, 0x95C24, 0x94E74, 0x99D34, 4, 5, 4, 29248, 4000},
                {11, 0xA9D74, 0xA8D04, 0xAD454, 26, 27, 15, 29952, 4800},
                {12, 0xB0894, 0xAFFC4, 0xB2CF4, 28, 29, 16, 26112, 3040}
        };
        for (int[] e : expected) {
            var p = Sonic2CompetitionLevelProfile.load(rom, e[0], 0);
            assertEquals(new Sonic2CompetitionLevelProfile.Art(e[1], e[2], e[3], e[4], e[5], e[6]), p.art());
            var decoded = p.decodeBaseTerrain(rom);
            assertEquals(e[7], decoded.patternBytes().length);
            assertEquals(e[8] / 2, decoded.nativeBlockWords().size());
            assertEquals(32768, decoded.chunkBytes().length);
            assertEquals(4096, decoded.layoutBytes().length);
            assertFalse(decoded.animationPatchApplied());
            assertFalse(p.primaryPlc(rom).entries().isEmpty());
            assertFalse(p.secondaryPlc(rom).entries().isEmpty());
            assertEquals(96, p.paletteSize());
        }
        assertEquals(0xF80D, Sonic2CompetitionLevelProfile.nativePatternWord(0xF81B));
        assertEquals(0xDBFE, Sonic2CompetitionLevelProfile.nativePatternWord(0xDFFD));
        assertEquals(0x40372, Sonic2CompetitionLevelProfile.load(rom, 0, 0).animationPatch().address());
        assertEquals(0, Sonic2CompetitionLevelProfile.load(rom, 11, 0).animationPatch().declaredWordCount());
    }
}

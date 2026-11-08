package com.openggf.game.sonic2.competition.view;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.data.RomByteReader;
import com.openggf.data.RomChannel;
import com.openggf.data.compression.KosinskiReader;
import com.openggf.game.sonic2.competition.Sonic2CompetitionLevelProfile;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionRom {
    @Test
    void allCompetitionZonesUseRealKosinskiArtInNativeSixtyFourBytePairs() throws Exception {
        var rom = TestEnvironment.currentRom();
        for (var source : new int[][] {{0, 0x95C24, 29248}, {11, 0xA9D74, 29952}, {12, 0xB0894, 26112}}) {
            byte[] bytes;
            try (var channel = RomChannel.at(rom, source[1])) {
                bytes = KosinskiReader.decompress(channel, false);
            }
            assertEquals(source[2], bytes.length);
            var profile = Sonic2CompetitionLevelProfile.load(rom, source[0], 0);
            assertEquals(source[1], profile.art().patternAddress());
            var art = new Sonic2CompetitionArtResidency(rom, new Sonic2PlcService(rom));
            art.loadZoneArt(profile);
            bytes = java.util.Arrays.copyOf(bytes, 65536);
            var fields = art.fields().live();
            int colored = 0, differingRows = 0;
            for (int cell = 0; cell < bytes.length / 64; cell++)
                for (int y = 0; y < 16; y++)
                    for (int x = 0; x < 8; x++) {
                        int expected = Im2FieldOracle.pixel(bytes, cell, x, y);
                        assertEquals(expected, fields[(y % 2) * 1024 + cell].getPixel(x, y / 2));
                        if (expected != 0)
                            colored++;
                        if ((y & 1) != 0 && expected != Im2FieldOracle.pixel(bytes, cell, x, y - 1))
                            differingRows++;
                    }
            assertTrue(colored > 1000);
            assertTrue(differingRows > 1000, "scaling ordinary tiles must fail");
            var terrain = profile.decodeBaseTerrain(rom);
            assertFalse(terrain.animationPatchApplied());
            assertTrue(terrain.nativeBlockWords().stream().anyMatch(w -> (w & 0xF800) != 0));
            for (int word : terrain.nativeBlockWords()) {
                var name = new Sonic2CellWord(word);
                assertEquals(word & 1023, name.patternIndex());
                for (int y = 0; y < 8; y++) {
                    int expected = Im2FieldOracle.pixel(
                            bytes, word & 1023, (word & 0x800) == 0 ? 0 : 7, (word & 0x1000) == 0 ? 2 * y : 15 - 2 * y);
                    int index = ((word & 0x1000) == 0 ? 0 : 1024) | (word & 1023);
                    assertEquals(expected,
                            fields[index].getPixel((word & 0x800) == 0 ? 0 : 7, (word & 0x1000) == 0 ? y : 7 - y));
                }
            }
        }
    }
    @Test
    void realSpringMappingsKeepTheirNativeTwoPlayerWordAndSize() throws Exception {
        var reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        int base = Sonic2Constants.MAP_UNC_SPRING_ADDR;
        var frame = Sonic2CompetitionSpriteMapper.readFrame(reader, base, 0);
        int address = base + reader.readS16BE(base);
        assertEquals(reader.readU16BE(address), frame.size());
        assertFalse(frame.isEmpty());
        for (int i = 0; i < frame.size(); i++) {
            int piece = address + 2 + i * 8;
            assertEquals(reader.readU16BE(piece + 4), frame.get(i).nativeTileWord());
            assertEquals(reader.readS16BE(piece + 6), frame.get(i).x());
            assertEquals((byte) reader.readU8(piece), frame.get(i).y());
            var sat = Sonic2CompetitionSpriteMapper
                              .map(frame.subList(i, i + 1), 0, 224, Sonic2CompetitionSpriteMapper.nativeArtWord(0x4580),
                                      false, false, false)
                              .getFirst();
            int size = reader.readU8(piece + 1);
            assertEquals((size & 12) | ((size & 3) >> 1), sat.size());
            assertEquals((0x42C0 + reader.readU16BE(piece + 4)) & 65535, sat.name().value());
        }
    }
}

package com.openggf.game.sonic2.competition.interlace;

import com.openggf.data.RomByteReader;
import com.openggf.data.RomChannel;
import com.openggf.data.compression.KosinskiReader;
import com.openggf.game.sonic2.competition.Sonic2CompetitionLevelProfile;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.openggf.game.sonic2.competition.interlace.Sonic2InterlaceDecoder.*;
import static com.openggf.game.sonic2.competition.interlace.TestSonic2InterlaceDecoder.*;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2InterlaceRom {
    @Test void allCompetitionZonesUseRealKosinskiArtInNativeSixtyFourBytePairs() throws Exception {
        var rom = TestEnvironment.currentRom();
        int[][] sources = {{0, 0x95C24, 29248}, {11, 0xA9D74, 29952}, {12, 0xB0894, 26112}};
        for (var source : sources) {
            byte[] bytes;
            try (var channel = RomChannel.at(rom, source[1])) { bytes = KosinskiReader.decompress(channel, false); }
            assertEquals(source[2], bytes.length);
            var profile = Sonic2CompetitionLevelProfile.load(rom, source[0], 0);
            assertEquals(source[1], profile.art().patternAddress());
            var image = Sonic2InterlaceVram.fromZoneArt(rom, profile);
            byte[] imageBytes = image.bytes();
            assertArrayEquals(bytes, Arrays.copyOf(image.bytes(), bytes.length));
            var producer = new Sonic2InterlaceProducer(0, 8192);
            var atlas = uploadedPatterns(producer, image);
            int differingRows = 0, colored = 0;
            for (int pattern = 0; pattern < bytes.length / 64; pattern++) {
                byte[] decoded = decodePattern(image, pattern);
                for (int y = 0; y < 16; y++) for (int x = 0; x < 8; x++) {
                    int expected = referencePixel(bytes, pattern, x, y);
                    assertEquals(expected, decoded[y * 8 + x]);
                    assertEquals(expected, atlas.get((y % 2) * 1024 + pattern)[(y / 2) * 8 + x]);
                    if (expected != 0) colored++;
                    if ((y & 1) != 0 && expected != referencePixel(bytes, pattern, x, y - 1)) differingRows++;
                }
            }
            assertTrue(colored > 1000, "real art must contain colored pixels");
            assertTrue(differingRows > 1000, "scaled ordinary tiles must fail the parity oracle");
            // The block loader halves only the native cell number, retaining every flag.
            var terrain = profile.decodeBaseTerrain(rom);
            assertFalse(terrain.animationPatchApplied());
            assertTrue(terrain.nativeBlockWords().stream().anyMatch(w -> (w & 0xF800) != 0));
            for (int raw : terrain.nativeBlockWords()) {
                var name = new NameWord(raw);
                assertEquals(raw & 1023, name.patternIndex());
                for (var field : Field.values()) for (int row = 0; row < 8; row++) {
                    int physical = row * 2 + field.parity();
                    int expected = referencePixel(imageBytes, raw & 1023,
                            (raw & 0x800) == 0 ? 0 : 7, (raw & 0x1000) == 0 ? physical : 15 - physical);
                    assertEquals(expected, sampleCell(image, name, 0, physical).color());
                }
            }
        }
    }

    @Test void realSpringMappingsKeepTheirNativeTwoPlayerWordAndSize() throws Exception {
        var reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        int base = Sonic2Constants.MAP_UNC_SPRING_ADDR;
        var frame = Sonic2InterlaceSpriteMapper.readFrame(reader, base, 0);
        int address = base + reader.readS16BE(base);
        assertEquals(reader.readU16BE(address), frame.size());
        assertFalse(frame.isEmpty());
        for (int i = 0; i < frame.size(); i++) {
            int piece = address + 2 + i * 8;
            assertEquals(reader.readU16BE(piece + 4), frame.get(i).nativeTileWord());
            assertEquals(reader.readS16BE(piece + 6), frame.get(i).x());
            assertEquals((byte) reader.readU8(piece), frame.get(i).y());
            var sat = Sonic2InterlaceSpriteMapper.map(frame.subList(i, i + 1), 0, 224,
                    Sonic2InterlaceSpriteMapper.nativeArtWord(0x4580), false, false, false).getFirst();
            int size = reader.readU8(piece + 1);
            assertEquals((size & 12) | ((size & 3) >> 1), sat.size());
            assertEquals((0x42C0 + reader.readU16BE(piece + 4)) & 65535, sat.name().value());
        }
    }
}

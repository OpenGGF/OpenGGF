package com.openggf.game.sonic2.competition.specialstage;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** Independent retail-byte and native stream contracts, without an ordinary SS session. */
@RequiresRom(SonicGame.SONIC_2)
class TestCompetitiveHalfpipeRomData {
    @Test
    void injectedRetailBytesSupplyEveryRequiredAsset() throws Exception {
        Rom rom = TestEnvironment.currentRom();
        assertEquals("8bca5dcef1af3e00098666fd892dc1c2a76333f9",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(rom.readAllBytes())));
        CompetitiveHalfpipeRomData data = new CompetitiveHalfpipeRomData(rom);
        RomByteReader bytes = data.rom();
        assertEquals(0x000C8001, bytes.readU32BE(0xE34EE));
        assertEquals(0xFF7F000E, bytes.readU32BE(0xE35F2));
        assertEquals(384, data.loader().getLevelLayouts().length);
        assertEquals(6880, data.loader().getObjectLocations().length);
        assertEquals(6908, data.perspective().length);
        assertEquals(2048, data.backgroundMappings().length);
        assertEquals(77, data.skydomeScroll().length);
        for (int frame = 0; frame < 56; frame++) assertTrue(data.trackFrame(frame).length > 0);

        // Nemesis pattern counts are independently encoded in each ROM header.
        assertPatternHeader(bytes, 0xDCD68, data.backgroundPatterns());
        assertPatternHeader(bytes, 0xDEEAE, data.playerPatterns());
        assertPatternHeader(bytes, 0xDDA7E, data.ringPatterns());
        assertPatternHeader(bytes, 0xDE4BC, data.bombPatterns());
        assertPatternHeader(bytes, 0xDDFA4, data.shadowHorizPatterns());
        assertPatternHeader(bytes, 0xDE05A, data.shadowDiagPatterns());
        assertPatternHeader(bytes, 0xDE120, data.shadowVertPatterns());
        assertPatternHeader(bytes, 0xDD48A, data.hudPatterns());
        assertPatternHeader(bytes, 0xE247E, data.tailsTextPatterns());
        assertPatternHeader(bytes, 0xDD790, data.startPatterns());
        assertPatternHeader(bytes, 0xDEAF4, data.messagesPatterns());
        assertPatternHeader(bytes, 0xDD8CE, data.starsPatterns());
        assertPatternHeader(bytes, 0xDE188, data.explosionPatterns());
        assertPatternHeader(bytes, 0xDD9C8, data.playerVsPlayerPatterns());
        assertTrue(data.trackPatterns().length > 0);
    }

    @Test
    void allSevenStreamsHaveIndependentSectorsAndNativeFinalMessages() throws Exception {
        CompetitiveHalfpipeRomData data = new CompetitiveHalfpipeRomData(TestEnvironment.currentRom());
        int[] layoutOffsets = {14, 60, 104, 148, 204, 248, 310};
        int[] objectOffsets = {14, 503, 1226, 2254, 3097, 4036, 5389};
        int[] geometryCounts = {46, 44, 44, 56, 44, 62, 74};
        int[] ringCounts = {206, 260, 205, 252, 403, 342, 492};
        int[] bombCounts = {16, 80, 286, 142, 44, 304, 225};
        int[][] checkpoints = {{12, 26}, {13, 26}, {18, 30}, {15, 34}, {15, 25}, {19, 35}, {19, 34}};
        int[][] notices = {{10, 23, 37}, {9, 23, 34}, {14, 24, 36}, {9, 31, 47},
                {12, 23, 37}, {15, 31, 53}, {14, 31, 45}};
        int[] finalMessages = {41, 39, 41, 51, 40, 58, 49};
        byte[] layouts = data.loader().getLevelLayouts();
        byte[] objects = data.loader().getObjectLocations();
        for (int stage = 0; stage < 7; stage++) {
            assertEquals(layoutOffsets[stage], word(layouts, stage * 2));
            assertEquals(objectOffsets[stage], word(objects, stage * 2));
            List<CompetitiveHalfpipeRomData.Segment> segments = data.segments(stage);
            assertEquals(geometryCounts[stage], segments.size());
            assertEquals(ringCounts[stage], segments.stream().flatMap(segment -> segment.items().stream())
                    .filter(item -> !item.bomb()).count());
            assertEquals(bombCounts[stage], segments.stream().flatMap(segment -> segment.items().stream())
                    .filter(CompetitiveHalfpipeRomData.Item::bomb).count());
            assertArrayEquals(checkpoints[stage], markerIndices(segments, 0xFE));
            assertArrayEquals(notices[stage], markerIndices(segments, 0xFC));
            assertArrayEquals(new int[]{finalMessages[stage]}, markerIndices(segments, 0xFD));
            for (int segment = 0; segment < segments.size(); segment++) {
                int raw = layouts[layoutOffsets[stage] + segment] & 0xFF;
                assertEquals(raw & 0x7F, segments.get(segment).geometry());
                assertEquals((raw & 0x80) != 0, segments.get(segment).flipped());
                if (segment > finalMessages[stage]) {
                    assertEquals(0xFF, segments.get(segment).marker());
                    assertTrue(segments.get(segment).items().isEmpty());
                }
            }
        }
        assertEquals(new CompetitiveHalfpipeRomData.Item(false, 0, 0x40), data.segments(0).get(5).items().getFirst());
        assertEquals(new CompetitiveHalfpipeRomData.Item(false, 8, 0x28), data.segments(4).get(4).items().getFirst());
        assertEquals(new CompetitiveHalfpipeRomData.Item(false, 8, 0x40), data.segments(6).get(4).items().getFirst());
    }

    @Test
    void competitivePaletteIdsFollowNativeFlagBranchForEveryStage() throws Exception {
        CompetitiveHalfpipeRomData data = new CompetitiveHalfpipeRomData(TestEnvironment.currentRom());
        int[] selectedIds = {0x1B, 0x1C, 0x20, 0x21, 0x22, 0x23, 0x24};
        int[] selectedPointers = {0x31C2, 0x31E2, 0x3262, 0x3282, 0x32A2, 0x32C2, 0x32E2};
        for (int stage = 0; stage < 7; stage++) {
            int nativeIndex = stage < 2 ? stage : stage + 3;
            assertEquals(selectedIds[stage], data.rom().readU16BE(0x778E + nativeIndex * 2));
            assertEquals(selectedPointers[stage], data.rom().readU32BE(0x2782 + selectedIds[stage] * 8));
            Palette[] palettes = data.palettes(stage);
            for (int line = 0; line < 4; line++) {
                int address = line < 3 ? 0x3162 + line * 32 : selectedPointers[stage];
                for (int color = 0; color < 16; color++) {
                    assertColor(data.rom().readU16BE(address + color * 2), palettes[line].getColor(color));
                }
            }
        }
    }

    @Test
    void injectedInstancesAndReturnedMutableAssetsAreIsolated() throws Exception {
        Rom original = TestEnvironment.currentRom();
        byte[] changed = original.readAllBytes();
        // Deliberately different bytes in the supplied ROM prove that no global
        // source or static special-stage palette cache can satisfy these reads.
        changed[0x3164] = 0x0E;
        changed[0x3165] = (byte) 0xEE;
        changed[0x32A4] = 0x00;
        changed[0x32A5] = 0x02;
        try (Rom alternate = Rom.fromReader(new RomByteReader(changed), "palette isolation probe")) {
            CompetitiveHalfpipeRomData first = new CompetitiveHalfpipeRomData(original);
            CompetitiveHalfpipeRomData second = new CompetitiveHalfpipeRomData(alternate);
            assertColor(0xEEE, second.palettes(4)[0].getColor(1));
            assertColor(0x002, second.palettes(4)[3].getColor(1));
            assertFalse(first.palettes(4)[0].dataEquals(second.palettes(4)[0]));
            assertFalse(first.palettes(4)[3].dataEquals(second.palettes(4)[3]));
            Palette originalPalette = first.palettes(4)[0];
            Palette[] returned = first.palettes(4);
            returned[0].getColor(1).r ^= 0x7F;
            assertTrue(first.palettes(4)[0].dataEquals(originalPalette));
            byte[] frame = first.trackFrame(0);
            byte originalByte = frame[0];
            frame[0] ^= 0x7F;
            assertEquals(originalByte, first.trackFrame(0)[0]);
            Pattern[] art = first.playerPatterns();
            byte pixel = art[0].getPixel(0, 0);
            art[0].setPixel(0, 0, (byte) (pixel ^ 0xF));
            assertEquals(pixel, first.playerPatterns()[0].getPixel(0, 0));
            assertThrows(UnsupportedOperationException.class, () -> first.segments(0).clear());
            assertThrows(UnsupportedOperationException.class, () -> first.segments(0).get(5).items().clear());
        }
    }

    private static void assertPatternHeader(RomByteReader bytes, int address, Pattern[] patterns) {
        assertEquals(bytes.readU16BE(address) & 0x7FFF, patterns.length);
        assertTrue(patterns.length > 0);
    }

    private static int[] markerIndices(List<CompetitiveHalfpipeRomData.Segment> segments, int marker) {
        return IntStream.range(0, segments.size()).filter(index -> segments.get(index).marker() == marker).toArray();
    }

    private static int word(byte[] bytes, int address) {
        return ((bytes[address] & 0xFF) << 8) | (bytes[address + 1] & 0xFF);
    }

    private static void assertColor(int word, Palette.Color color) {
        assertEquals((((word >>> 1) & 7) * 255 + 3) / 7, color.r & 0xFF);
        assertEquals((((word >>> 5) & 7) * 255 + 3) / 7, color.g & 0xFF);
        assertEquals((((word >>> 9) & 7) * 255 + 3) / 7, color.b & 0xFF);
    }
}

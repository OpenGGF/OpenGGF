package com.openggf.game.soniccd.device;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static com.openggf.game.soniccd.device.CdStampRenderRequest.MapSize.PIXELS_256;
import static com.openggf.game.soniccd.device.CdStampRenderRequest.Priority.NORMAL;
import static com.openggf.game.soniccd.device.CdStampRenderRequest.StampSize.PIXELS_16;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed public-behavior checks; no disc, native core, clocks, or graphics context. */
class TestCdStampGraphics {
    private static final int MAP = 0x20000;
    private static final int TRACE = 0x30000;
    private static final int IMAGE = 0x10000;

    @ParameterizedTest(name = "{0}-pixel stamp, orientation {1}")
    @CsvSource(delimiter = '|', textBlock = """
            16|0|7ad147ad8be258be
            16|1|d38d38d349e49e49
            16|2|da741da7c963fc96
            16|3|72c72c721b61b61b
            16|4|eb852eb8da741da7
            16|5|94e94e943d83d83d
            16|6|69cf369c7ad147ad
            16|7|b16b16b127c27c27
            32|0|7ad147ad8be258be9cf369cfad147ad1
            32|1|f5af5af56b16b16bc27c27c238d38d38
            32|2|c963fc96b852eb85a741da74963fc963
            32|3|4e94e94ed83d83d872c72c721b61b61b
            32|4|1da741dafc963fc9eb852eb8da741da7
            32|5|83d83d832c72c72cb61b61b65fa5fa5f
            32|6|369cf36947ad147a58be258b69cf369c
            32|7|b16b16b127c27c278d38d38de49e49e4
            """)
    void rendersOrientationsAcrossCellAndPixelBoundaries(int edge, int orientation, String expectedRow) {
        byte[] ram = memory();
        var size = edge == 16 ? PIXELS_16 : CdStampRenderRequest.StampSize.PIXELS_32;
        // Unequal cell, row, and column contributions distinguish cell order from pixel order.
        for (int y = 0; y < edge; y++) {
            for (int x = 0; x < edge; x++) {
                stampPixel(ram, 4, edge, x, y, 1 + (3 * x + 5 * y + 7 * (x / 8) + 11 * (y / 8)) % 15);
            }
        }
        word(ram, MAP, (orientation << 13) | 4);
        vector(ram, TRACE, 0, 11 * 8, 0x0800, 0);
        var request = new CdStampRenderRequest(size, PIXELS_256, false, NORMAL, MAP, 5, edge);
        byte[] expected = ram.clone();
        // Literal rows of the constructed stamp after each of the eight geometric transforms.
        // They deliberately cross 8-, 16-, and (for 32-pixel stamps) 24-pixel cell boundaries.
        for (int x = 0; x < edge; x++) {
            nibble(expected, 2 * IMAGE + (x / 8) * 320 + x % 8,
                    Character.digit(expectedRow.charAt(x), 16));
        }

        var result = CdStampGraphics.executeLines(ram, start(request, TRACE, 2L * IMAGE, 1), 1);

        assertArrayEquals(expected, ram);
        assertEquals(1, result.completedLines());
        assertEquals(0, result.remainingLines());
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            NORMAL,     160,   5,   5
            NORMAL,      11, 192, 192
            NORMAL,     171,   0,   0
            UNDERWRITE, 160,   5, 165
            UNDERWRITE,  11, 192, 203
            UNDERWRITE, 171, 205, 171
            OVERWRITE,  160,   5, 165
            OVERWRITE,   11, 192, 203
            OVERWRITE,  171, 205, 205
            PRESERVE,   160,   5, 160
            PRESERVE,    11, 192,  11
            PRESERVE,   171, 205, 171
            """)
    void appliesPrioritySeparatelyToEachNibble(CdStampRenderRequest.Priority priority,
                                               int previous, int incoming, int expectedByte) {
        byte[] ram = memory();
        word(ram, MAP, 1);
        ram[128] = (byte) incoming;
        ram[IMAGE] = (byte) previous;
        vector(ram, TRACE, 0, 0, 0x0800, 0);
        byte[] expected = ram.clone();
        expected[IMAGE] = (byte) expectedByte;

        int modeWord = switch (priority) {
            case NORMAL -> 0;
            case UNDERWRITE -> 8;
            case OVERWRITE -> 16;
            case PRESERVE -> 24;
        };
        var decoded = CdStampRenderRequest.fromRegisterWords(0, modeWord, MAP / 4, 0, 2);
        CdStampGraphics.executeLines(ram, start(decoded, TRACE, 2L * IMAGE, 1), 1);

        assertArrayEquals(expected, ram);
    }

    @ParameterizedTest
    @EnumSource(CdStampRenderRequest.Priority.class)
    void preservesTheOtherNibbleWhenWritingAnOddDestination(CdStampRenderRequest.Priority priority) {
        byte[] ram = memory();
        word(ram, MAP, 1);
        ram[128] = 0x50;
        ram[IMAGE] = (byte) 0xa0;
        vector(ram, TRACE, 0, 0, 0, 0);
        byte[] expected = ram.clone();
        expected[IMAGE] = (byte) (priority == CdStampRenderRequest.Priority.PRESERVE ? 0xa0 : 0xa5);

        CdStampGraphics.executeLines(ram, start(request(priority, 1), TRACE, 2L * IMAGE + 1, 1), 1);

        assertArrayEquals(expected, ram);
    }

    @ParameterizedTest
    @CsvSource({"16,256,false", "16,256,true", "32,256,false", "32,256,true",
            "16,4096,false", "16,4096,true", "32,4096,false", "32,4096,true"})
    void repeatsOrClipsBothAxesAndWrapsThe24BitSourcePosition(int edge, int mapEdge, boolean repeat) {
        byte[] ram = memory();
        var stampSize = edge == 16 ? PIXELS_16 : CdStampRenderRequest.StampSize.PIXELS_32;
        var mapSize = mapEdge == 256 ? PIXELS_256 : CdStampRenderRequest.MapSize.PIXELS_4096;
        int cells = mapEdge / edge;
        word(ram, MAP, 4);
        word(ram, MAP + 2 * (cells - 1), 8);
        word(ram, MAP + 2 * (cells - 1) * cells, 12);
        fillStamp(ram, 4, edge, 3);
        fillStamp(ram, 8, edge, 7);
        fillStamp(ram, 12, edge, 9);
        // Right edge, negative X, bottom edge, negative Y, and 8191.875 -> 0 at 24-bit overflow.
        vector(ram, TRACE, (mapEdge - 1) * 8, 0, 0x0800, 0);
        vector(ram, TRACE + 8, 0, 0, -0x0800, 0);
        vector(ram, TRACE + 16, 0, (mapEdge - 1) * 8, 0, 0x0800);
        vector(ram, TRACE + 24, 0, 0, 0, -0x0800);
        vector(ram, TRACE + 32, 0xffff, 0, 0x0100, 0);
        var request = new CdStampRenderRequest(stampSize, mapSize, repeat, NORMAL, MAP, 1, 2);
        int[][] expectedRows = repeat
                ? new int[][]{{7, 3}, {3, 7}, {9, 3}, {3, 9}, {7, 3}}
                : new int[][]{{7, 0}, {3, 0}, {9, 0}, {3, 0}, {0, 3}};
        byte[] expected = ram.clone();
        for (int y = 0; y < expectedRows.length; y++) {
            expected[IMAGE + 4 * y] = (byte) ((expectedRows[y][0] << 4) | expectedRows[y][1]);
        }

        CdStampGraphics.executeLines(ram, start(request, TRACE, 2L * IMAGE, 5), 5);

        assertArrayEquals(expected, ram);
    }

    @Test
    void signedFractionalDeltasCrossStampBoundariesWithoutLosingSubpixels() {
        byte[] ram = memory();
        word(ram, MAP, 1);
        word(ram, MAP + 2, 2);
        fillStamp(ram, 1, 16, 4);
        fillStamp(ram, 2, 16, 10);
        vector(ram, TRACE, 127, 0, 0x0100, 0);  // 15.875, 16.000, 16.125
        vector(ram, TRACE + 8, 128, 0, -0x0100, 0); // 16.000, 15.875, 15.750
        byte[] expected = ram.clone();
        expected[IMAGE] = 0x4a;
        expected[IMAGE + 1] = (byte) 0xa0;
        expected[IMAGE + 4] = (byte) 0xa4;
        expected[IMAGE + 5] = 0x40;

        CdStampGraphics.executeLines(ram, start(request(NORMAL, 3), TRACE, 2L * IMAGE, 2), 2);

        assertArrayEquals(expected, ram);
    }

    @Test
    void usesTheCompleteSignedFiveDotElevenDeltaRangeOnBothAxes() {
        byte[] ram = memory();
        word(ram, MAP, 1);
        word(ram, MAP + 2, 2);
        word(ram, MAP + 4, 3);
        word(ram, MAP + 32, 4);
        fillStamp(ram, 1, 16, 4);
        fillStamp(ram, 2, 16, 10);
        fillStamp(ram, 3, 16, 14);
        fillStamp(ram, 4, 16, 7);
        vector(ram, TRACE, 16 * 8, 0, -0x8000, 0);
        vector(ram, TRACE + 8, 1, 0, 0x7fff, 0);
        vector(ram, TRACE + 16, 0, 16 * 8, 0, -0x8000);
        byte[] expected = ram.clone();
        expected[IMAGE] = (byte) 0xa4;
        expected[IMAGE + 4] = 0x4a;
        expected[IMAGE + 5] = (byte) 0xe0;
        expected[IMAGE + 8] = 0x74;

        CdStampGraphics.executeLines(ram, start(request(NORMAL, 3), TRACE, 2L * IMAGE, 3), 3);

        assertArrayEquals(expected, ram);
    }

    @Test
    void masksLowTwoStampNumberBitsIn32PixelModeAndNeverReadsStampZeroArt() {
        byte[] ram = memory();
        Arrays.fill(ram, 0, 512, (byte) 0xff); // Nonzero art in the physically reserved zero stamp.
        fillStamp(ram, 4, 32, 9);
        for (int line = 0; line < 8; line++) {
            word(ram, MAP + 2 * line, line | 0x1800); // Unused map bits 11 and 12 also do not select art.
            vector(ram, TRACE + 8 * line, line * 32 * 8, 0, 0, 0);
        }
        var request = new CdStampRenderRequest(CdStampRenderRequest.StampSize.PIXELS_32,
                PIXELS_256, false, NORMAL, MAP, 1, 1);
        byte[] expected = ram.clone();
        for (int line = 4; line < 8; line++) { expected[IMAGE + line * 4] = (byte) 0x90; }

        CdStampGraphics.executeLines(ram, start(request, TRACE, 2L * IMAGE, 8), 8);

        assertArrayEquals(expected, ram);
    }

    @Test
    void usesTheHighestStampAndLastMapEntryWithoutCrossingWordRam() {
        byte[] ram = memory();
        // A 4096-pixel map of 16-pixel stamps can end exactly at byte 0x40000.
        word(ram, 0x3fffe, 0x7ff);
        // This map word also supplies the highest stamp's last pixel: its low nibble is F.
        vector(ram, TRACE, 4095 * 8, 4095 * 8, 0, 0);
        var request = new CdStampRenderRequest(PIXELS_16, CdStampRenderRequest.MapSize.PIXELS_4096,
                false, NORMAL, MAP, 1, 1);
        byte[] expected = ram.clone();
        expected[IMAGE] = (byte) 0xf0;

        CdStampGraphics.executeLines(ram, start(request, TRACE, 2L * IMAGE, 1), 1);

        assertArrayEquals(expected, ram);
    }

    @Test
    void laysOutColumnsByVerticalCellsAndPreservesNeighborsOfAnUnalignedImage() {
        byte[] ram = memory();
        word(ram, MAP, 1);
        word(ram, MAP + 2, 2);
        fillStamp(ram, 1, 16, 12);
        fillStamp(ram, 2, 16, 9);
        vector(ram, TRACE, 0, 0, 0, 0);
        vector(ram, TRACE + 8, 16 * 8, 0, 0, 0);
        Arrays.fill(ram, IMAGE, IMAGE + 128, (byte) 0xab);
        var request = new CdStampRenderRequest(PIXELS_16, PIXELS_256, false, NORMAL, MAP, 3, 11);
        byte[] expected = ram.clone();
        int[] firstLine = {5, 6, 7, 192, 193, 194, 195, 196, 197, 198, 199};
        int[] secondLine = {13, 14, 15, 200, 201, 202, 203, 204, 205, 206, 207};
        for (int dot : firstLine) { nibble(expected, 2 * IMAGE + dot, 12); }
        for (int dot : secondLine) { nibble(expected, 2 * IMAGE + dot, 9); }

        CdStampGraphics.executeLines(ram, start(request, TRACE, 2L * IMAGE + 5, 2), 2);

        assertArrayEquals(expected, ram);
    }

    @Test
    void wrapsTraceVectorsAndDestinationBytesWhilePreservingUnsignedCursorProgress() {
        byte[] ram = memory();
        word(ram, MAP, 1);
        word(ram, MAP + 2, 2);
        fillStamp(ram, 1, 16, 4);
        fillStamp(ram, 2, 16, 10);
        vector(ram, 0x3fff8, 0, 0, 0, 0);
        vector(ram, 0, 16 * 8, 0, 0, 0);
        byte[] expected = ram.clone();
        // A vector is read in full before this line overwrites its delta words.
        for (int dot : new int[]{0x7fffc, 0x7fffd, 0x7fffe, 0x7ffff, 56, 57, 58, 59}) {
            nibble(expected, dot, 4);
        }
        for (int dot : new int[]{4, 5, 6, 7, 64, 65, 66, 67}) { nibble(expected, dot, 10); }
        var initial = start(request(NORMAL, 8), 0x3fff8, 0xfffffffcL, 2);

        var oneLine = CdStampGraphics.executeLines(ram, initial, 1);
        assertEquals(0, oneLine.traceByteCursor());
        assertEquals(4, oneLine.imageNibbleCursor());
        var complete = CdStampGraphics.executeLines(ram, oneLine, 1);

        assertEquals(8, complete.traceByteCursor());
        assertEquals(12, complete.imageNibbleCursor());
        assertEquals(2, complete.completedLines());
        assertEquals(0, complete.remainingLines());
        assertEquals(0xfffffffcL, initial.imageNibbleCursor());
        assertArrayEquals(expected, ram);
    }

    @Test
    void takesEachSubsequentVectorFromTheCurrentCallerMemory() {
        byte[] ram = memory();
        word(ram, MAP, 1);
        fillStamp(ram, 1, 16, 5);
        vector(ram, TRACE, 0, 0, 0, 0);
        vector(ram, TRACE + 8, 0, 0, 0x0800, 0);
        // The first output nibble changes the next vector's X word from 0 to 0x5000.
        // A device that predecodes every vector would incorrectly render another 5.
        byte[] expected = ram.clone();
        expected[TRACE + 8] = 0x50;
        var state = start(request(NORMAL, 1), TRACE, 2L * (TRACE + 8), 2);

        CdStampGraphics.executeLines(ram, state, 2);

        assertArrayEquals(expected, ram);
    }

    @Test
    void continuesOnlyCapturedRemainingLinesAndCanRecreateAStateOnRestoredMemory() {
        byte[] ram = memory();
        word(ram, MAP, 1);
        fillStamp(ram, 1, 16, 12);
        for (int line = 0; line < 5; line++) { vector(ram, TRACE + 8 * line, 0, 0, 0, 0); }
        ram[IMAGE] = (byte) 0xe0;
        ram[IMAGE + 4] = (byte) 0xd0;
        var captured = new CdStampRenderState(request(NORMAL, 1), TRACE + 16, 2L * IMAGE + 16, 2, 3);
        byte[] ingress = ram.clone();
        assertEquals(captured, CdStampGraphics.executeLines(ram, captured, 0));
        assertArrayEquals(ingress, ram);

        var oneLine = CdStampGraphics.executeLines(ram, captured, 1);
        byte[] snapshotMemory = ram.clone();
        var recreated = new CdStampRenderState(oneLine.request(), oneLine.traceByteCursor(),
                oneLine.imageNibbleCursor(), oneLine.completedLines(), oneLine.remainingLines());
        var complete = CdStampGraphics.executeLines(ram, oneLine, Integer.MAX_VALUE);
        var replayed = CdStampGraphics.executeLines(snapshotMemory, recreated, 2);
        byte[] expected = ingress.clone();
        for (int line = 2; line < 5; line++) { expected[IMAGE + 4 * line] = (byte) 0xc0; }

        assertArrayEquals(expected, ram);
        assertArrayEquals(ram, snapshotMemory);
        assertEquals(complete, replayed);
        assertEquals(5, complete.completedLines());
        assertEquals(TRACE + 40, complete.traceByteCursor());
        assertEquals(2L * IMAGE + 40, complete.imageNibbleCursor());
        assertEquals(2, captured.completedLines());
        assertEquals(3, captured.remainingLines());
        assertEquals(3, oneLine.completedLines());
        assertEquals(2, oneLine.remainingLines());
        assertEquals(complete, CdStampGraphics.executeLines(ram, complete, 4));
        assertArrayEquals(expected, ram);
    }

    @Test
    void consumesZeroWidthVectorsWithoutWritingPixelsAndDoesNoWorkForZeroHeight() {
        byte[] ram = memory();
        byte[] expected = ram.clone();
        var zeroWidth = start(request(NORMAL, 0), 0x3fff8, 0x7fffc, 2);

        var result = CdStampGraphics.executeLines(ram, zeroWidth, 255);

        assertEquals(8, result.traceByteCursor());
        assertEquals(0x8000c, result.imageNibbleCursor());
        assertEquals(2, result.completedLines());
        assertEquals(0, result.remainingLines());
        assertArrayEquals(expected, ram);
        var zeroHeight = start(request(NORMAL, 511), TRACE, 2L * IMAGE, 0);
        assertEquals(zeroHeight, CdStampGraphics.executeLines(ram, zeroHeight, 255));
        assertArrayEquals(expected, ram);
    }

    @ParameterizedTest
    @CsvSource({"0,261632", "2,262016", "4,131072", "6,229376"})
    void decodesRegisterMasksIntoBoundedSemanticParameters(int mode, int alignedMapByteAddress) {
        var decoded = CdStampRenderRequest.fromRegisterWords(0xfff8 | mode | 1, 0xffef,
                0xffff, 0xffff, 0xffff);
        assertEquals(alignedMapByteAddress, decoded.mapByteAddress());
        assertEquals(32, decoded.imageColumnStrideCells());
        assertEquals(511, decoded.widthPixels());
        assertEquals(CdStampRenderRequest.Priority.UNDERWRITE, decoded.priority());
        assertTrue(decoded.repeat());
        assertEquals((mode & 2) == 0 ? PIXELS_16 : CdStampRenderRequest.StampSize.PIXELS_32,
                decoded.stampSize());
        assertEquals((mode & 4) == 0 ? PIXELS_256 : CdStampRenderRequest.MapSize.PIXELS_4096,
                decoded.mapSize());
        var initial = CdStampRenderState.fromStartRegisterWords(decoded, 0xffff, 0xffff, 0xfffd, 0xff02);
        assertEquals(0x3fff8, initial.traceByteCursor());
        assertEquals(0x7fffd, initial.imageNibbleCursor());
        assertEquals(0, initial.completedLines());
        assertEquals(2, initial.remainingLines());
    }

    @Test
    void rejectsInvalidDescriptorsCursorsAndMemoryBeforeMutatingCallerBytes() {
        var request = request(NORMAL, 1);
        assertThrows(NullPointerException.class,
                () -> new CdStampRenderRequest(null, PIXELS_256, false, NORMAL, MAP, 1, 1));
        assertThrows(NullPointerException.class,
                () -> new CdStampRenderRequest(PIXELS_16, null, false, NORMAL, MAP, 1, 1));
        assertThrows(NullPointerException.class,
                () -> new CdStampRenderRequest(PIXELS_16, PIXELS_256, false, null, MAP, 1, 1));
        for (int address : new int[]{-1, 2, 0x40000}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new CdStampRenderRequest(PIXELS_16, PIXELS_256, false, NORMAL, address, 1, 1));
        }
        for (int cells : new int[]{0, 33}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new CdStampRenderRequest(PIXELS_16, PIXELS_256, false, NORMAL, MAP, cells, 1));
        }
        for (int width : new int[]{-1, 512}) {
            assertThrows(IllegalArgumentException.class, () -> request(NORMAL, width));
        }
        for (int cursor : new int[]{-8, 4, 0x40000}) {
            assertThrows(IllegalArgumentException.class, () -> start(request, cursor, 0, 1));
        }
        for (long cursor : new long[]{-1, 0x1_0000_0000L}) {
            assertThrows(IllegalArgumentException.class, () -> start(request, 0, cursor, 1));
        }
        assertThrows(NullPointerException.class, () -> start(null, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> start(request, 0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> start(request, 0, 0, 256));
        assertThrows(IllegalArgumentException.class, () -> new CdStampRenderState(request, 0, 0, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdStampRenderState(request, 0, 0, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> new CdStampRenderState(request, 0, 0, 255, 1));
        for (int badWord : new int[]{-1, 65536}) {
            assertThrows(IllegalArgumentException.class,
                    () -> CdStampRenderRequest.fromRegisterWords(badWord, 0, 0, 0, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> CdStampRenderRequest.fromRegisterWords(0, badWord, 0, 0, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> CdStampRenderRequest.fromRegisterWords(0, 0, badWord, 0, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> CdStampRenderRequest.fromRegisterWords(0, 0, 0, badWord, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> CdStampRenderRequest.fromRegisterWords(0, 0, 0, 0, badWord));
            assertThrows(IllegalArgumentException.class,
                    () -> CdStampRenderState.fromStartRegisterWords(request, badWord, 0, 0, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> CdStampRenderState.fromStartRegisterWords(request, 0, badWord, 0, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> CdStampRenderState.fromStartRegisterWords(request, 0, 0, badWord, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> CdStampRenderState.fromStartRegisterWords(request, 0, 0, 0, badWord));
        }
        byte[] ram = memory();
        ram[0] = 42;
        byte[] expected = ram.clone();
        var state = start(request, 0, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> CdStampGraphics.executeLines(ram, state, -1));
        assertThrows(NullPointerException.class, () -> CdStampGraphics.executeLines(ram, null, 1));
        assertThrows(NullPointerException.class, () -> CdStampGraphics.executeLines(null, state, 1));
        assertArrayEquals(expected, ram);
        for (int length : new int[]{0, 0x3ffff, 0x40001}) {
            byte[] wrongSize = new byte[length];
            assertThrows(IllegalArgumentException.class, () -> CdStampGraphics.executeLines(wrongSize, state, 1));
            assertArrayEquals(new byte[length], wrongSize);
        }
    }

    private static CdStampRenderRequest request(CdStampRenderRequest.Priority priority, int width) {
        return new CdStampRenderRequest(PIXELS_16, PIXELS_256, false, priority, MAP, 1, width);
    }

    private static CdStampRenderState start(CdStampRenderRequest request, int traceBytes,
                                             long imageNibbles, int lines) {
        return CdStampRenderState.start(request, traceBytes, imageNibbles, lines);
    }

    private static byte[] memory() { return new byte[0x40000]; }

    private static void word(byte[] ram, int address, int value) {
        ram[address] = (byte) (value >>> 8);
        ram[address + 1] = (byte) value;
    }

    private static void vector(byte[] ram, int address, int x13_3, int y13_3, int dx5_11, int dy5_11) {
        word(ram, address, x13_3);
        word(ram, address + 2, y13_3);
        word(ram, address + 4, dx5_11);
        word(ram, address + 6, dy5_11);
    }

    private static void nibble(byte[] ram, int address, int value) {
        int index = address / 2;
        int previous = Byte.toUnsignedInt(ram[index]);
        ram[index] = (byte) ((address & 1) == 0 ? (previous & 15) | (value << 4) : (previous & 240) | value);
    }

    private static void stampPixel(byte[] ram, int index16, int edge, int x, int y, int value) {
        // Physical art is 8x8 cells in column-major order, with row-major pixels inside a cell.
        int cell = (x / 8) * (edge / 8) + y / 8;
        nibble(ram, index16 * 256 + cell * 64 + (y % 8) * 8 + x % 8, value);
    }

    private static void fillStamp(byte[] ram, int index16, int edge, int value) {
        Arrays.fill(ram, index16 * 128, index16 * 128 + edge * edge / 2, (byte) ((value << 4) | value));
    }
}

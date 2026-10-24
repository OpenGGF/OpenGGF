package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdSonicVisualAssets;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteMappingPiece;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Constructed native-format cases through the real decoder; not media authentication or rendering. */
class TestCdPlayerVisualAdapter {
    @Test
    void fiveBytePiecesKeepOrderSignedOffsetsAndTheEntireTileWord() {
        var f = new Fixture();
        f.mapping(0,
                0x80, 0x0E, 0xFE, 0xAB, 0x7F,
                0x7F, 0x01, 0x01, 0x23, 0x80,
                0xFF, 0x04, 0x20, 0x07, 0xFF,
                0x00, 0x00, 0x57, 0xFF, 0x00);
        var frames = CdPlayerVisualAdapter.mappingFrames(f.decode());
        assertEquals(List.of(
                new SpriteMappingPiece(127, -128, 4, 3, 0x6AB, true, true, 3, true),
                new SpriteMappingPiece(-128, 127, 1, 2, 0x123, false, false, 0, false),
                new SpriteMappingPiece(-1, -1, 2, 1, 7, false, false, 1, false),
                new SpriteMappingPiece(0, 0, 1, 1, 0x7FF, false, true, 2, false)),
                frames.getFirst().pieces());
        assertTrue(frames.get(1).pieces().isEmpty());
    }

    @Test
    void everyFlipPaletteAndPriorityCombinationRemainsIndependentOfTheTileIndex() {
        var f = new Fixture();
        int[] pieces = new int[32 * 5];
        int cursor = 0;
        for (boolean priority : new boolean[] {false, true}) {
            for (int palette = 0; palette < 4; palette++) {
                for (boolean vFlip : new boolean[] {false, true}) {
                    for (boolean hFlip : new boolean[] {false, true}) {
                        int tileWord = 0x56A + (hFlip ? 0x800 : 0) + (vFlip ? 0x1000 : 0)
                                + palette * 0x2000 + (priority ? 0x8000 : 0);
                        pieces[cursor++] = 23;
                        pieces[cursor++] = 6; // Two tiles wide, three high.
                        pieces[cursor++] = tileWord >>> 8;
                        pieces[cursor++] = tileWord & 0xFF;
                        pieces[cursor++] = -17;
                    }
                }
            }
        }
        f.mapping(1, pieces);
        var converted = CdPlayerVisualAdapter.mappingFrames(f.decode()).get(1).pieces();
        assertEquals(32, converted.size());
        int index = 0;
        for (boolean priority : new boolean[] {false, true}) {
            for (int palette = 0; palette < 4; palette++) {
                for (boolean vFlip : new boolean[] {false, true}) {
                    for (boolean hFlip : new boolean[] {false, true}) {
                        assertEquals(new SpriteMappingPiece(-17, 23, 2, 3, 0x56A,
                                hFlip, vFlip, palette, priority), converted.get(index++));
                    }
                }
            }
        }
    }

    @Test
    void allSixteenSizesUseNativeWidthBitsAboveHeightBits() {
        var f = new Fixture();
        int[] pieces = new int[16 * 5];
        for (int size = 0; size < 16; size++) {
            pieces[size * 5 + 1] = size;
        }
        f.mapping(0, pieces);
        int[] widths = {1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4};
        int[] heights = {1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3, 4};
        var converted = CdPlayerVisualAdapter.mappingFrames(f.decode()).getFirst().pieces();
        for (int size = 0; size < 16; size++) {
            assertEquals(widths[size], converted.get(size).widthTiles(), "width for size " + size);
            assertEquals(heights[size], converted.get(size).heightTiles(), "height for size " + size);
        }
    }

    @Test
    void everyCoordinateByteIsSignExtendedAndBothEndpointFramesAreAvailable() {
        var f = new Fixture();
        int[] negativeHalf = new int[128 * 5];
        int[] positiveHalf = new int[128 * 5];
        for (int signedX = -128; signedX <= 127; signedX++) {
            int[] pieces = signedX < 0 ? negativeHalf : positiveHalf;
            int index = (signedX < 0 ? signedX + 128 : signedX) * 5;
            pieces[index] = -1 - signedX;
            pieces[index + 4] = signedX;
        }
        f.mapping(0, negativeHalf);
        f.mapping(197, positiveHalf);
        var converted = CdPlayerVisualAdapter.mappingFrames(f.decode());
        assertEquals(198, converted.size());
        for (int signedX = -128; signedX <= 127; signedX++) {
            var piece = converted.get(signedX < 0 ? 0 : 197).pieces()
                    .get(signedX < 0 ? signedX + 128 : signedX);
            assertEquals(signedX, piece.xOffset());
            assertEquals(-1 - signedX, piece.yOffset());
        }
    }

    @Test
    void aliasedAndEmptyFramesRemainIndexedAndBothListLevelsAreImmutable() {
        var f = new Fixture();
        f.mapping(3, -20, 8, 0, 0, -16, 4, 8, 0, 11, -16);
        f.alias(4, 3);
        f.alias(197, 3);
        var converted = CdPlayerVisualAdapter.mappingFrames(f.decode());
        assertEquals(198, converted.size());
        assertTrue(converted.getFirst().pieces().isEmpty());
        assertEquals(converted.get(3), converted.get(4));
        assertEquals(converted.get(3), converted.get(197));
        assertEquals(List.of(
                new SpriteMappingPiece(-16, -20, 3, 1, 0, false, false, 0, false),
                new SpriteMappingPiece(-16, 4, 3, 1, 11, false, false, 0, false)),
                converted.get(197).pieces());
        assertThrows(UnsupportedOperationException.class, () -> converted.set(0, converted.get(3)));
        assertThrows(UnsupportedOperationException.class, () -> converted.get(3).pieces().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> converted.getFirst().pieces().add(converted.get(3).pieces().getFirst()));
        assertThrows(IndexOutOfBoundsException.class, () -> converted.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> converted.get(198));
    }

    @Test
    void realDecoderBoundsAreRequiredBeforeConversion() {
        var f = new Fixture();
        for (int badPointer : new int[] {394, 397, 398, 0xFFFE}) {
            word(f.mappings, 0, badPointer);
            assertThrows(IllegalArgumentException.class,
                    () -> CdPlayerVisualAdapter.mappingFrames(f.decode()));
        }
        word(f.mappings, 0, 396);
        f.mappings[396] = 1; // The complete five-byte body is absent.
        assertThrows(IllegalArgumentException.class,
                () -> CdPlayerVisualAdapter.mappingFrames(f.decode()));
        f.mappings[396] = 0;
        assertThrows(IllegalArgumentException.class, () -> CdPlayerVisualAdapter.sourcePatterns(
                CdSonicVisualAssets.decode(Arrays.copyOf(f.mappings, 398),
                        Arrays.copyOf(f.dplcs, 398), Arrays.copyOf(f.art, 69471))));
        f.art[69471] = 1;
        assertThrows(IllegalArgumentException.class,
                () -> CdPlayerVisualAdapter.sourcePatterns(f.decode()));
    }

    @Test
    void completeSourceArtDecodesHighNibbleFirstIncludingTheActualEvenByte() {
        var f = new Fixture();
        Arrays.fill(f.art, (byte) 0x5B);
        int[] pairBytes = {0x01, 0x23, 0x45, 0x67, 0x89, 0xAB, 0xCD, 0xEF};
        for (int i = 0; i < 32; i++) {
            f.art[i] = (byte) pairBytes[i % pairBytes.length];
        }
        Arrays.fill(f.art, 69440, 69471, (byte) 0xC3);
        f.art[69470] = (byte) 0xA9;
        f.art[69471] = 0;
        var patterns = CdPlayerVisualAdapter.sourcePatterns(f.decode());
        assertEquals(2171, patterns.length);
        assertPixels(patterns[0],
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
        for (int tile = 1; tile < 2170; tile++) {
            assertAlternatingPixels(patterns[tile], 5, 11);
        }
        assertPixels(patterns[2170],
                12, 3, 12, 3, 12, 3, 12, 3, 12, 3, 12, 3, 12, 3, 12, 3,
                12, 3, 12, 3, 12, 3, 12, 3, 12, 3, 12, 3, 12, 3, 12, 3,
                12, 3, 12, 3, 12, 3, 12, 3, 12, 3, 12, 3, 12, 3, 12, 3,
                12, 3, 12, 3, 12, 3, 12, 3, 12, 3, 12, 3, 10, 9, 0, 0);
    }

    @Test
    void residencyUsesAllTwentyFourExplicitResidentTilesWithoutServicingDirtyCpuRam() {
        byte[] resident = filled(0x300, 0x79);
        Arrays.fill(resident, 0x2E0, 0x300, (byte) 0xCB);
        var state = new CdPlayerArtRuntime.State(filled(0x300, 0xE1), resident, 255, true);
        var patterns = CdPlayerVisualAdapter.residentPatterns(state);
        assertEquals(24, patterns.length);
        for (int tile = 0; tile < 23; tile++) {
            assertAlternatingPixels(patterns[tile], 7, 9);
        }
        assertAlternatingPixels(patterns[23], 12, 11);
        assertArrayEquals(resident, state.residentBank().copy());
        assertArrayEquals(filled(0x300, 0xE1), state.cpuBuffer().copy());
        assertEquals(255, state.lastFrame());
        assertTrue(state.dirty());
    }

    @Test
    void sourcePatternsAreFreshWithinAndAcrossCallsAndArtSets() {
        var f = new Fixture();
        Arrays.fill(f.art, 0, 69471, (byte) 0x5B);
        var assets = f.decode();
        var first = CdPlayerVisualAdapter.sourcePatterns(assets);
        var second = CdPlayerVisualAdapter.sourcePatterns(assets);
        var set = CdPlayerVisualAdapter.artSet(assets, 0x12400);
        var otherSet = CdPlayerVisualAdapter.artSet(assets, 0x12400);
        assertNotSame(first, second);
        assertNotSame(set.artTiles(), otherSet.artTiles());
        for (int tile = 0; tile < 2171; tile++) {
            assertNotSame(first[tile], second[tile]);
            assertNotSame(first[tile], set.artTiles()[tile]);
            assertNotSame(set.artTiles()[tile], otherSet.artTiles()[tile]);
            if (tile > 0) assertNotSame(first[0], first[tile]);
        }
        first[0].clear();
        first[1] = new Pattern();
        set.artTiles()[0].setPixel(0, 0, (byte) 2);
        assertAlternatingPixels(first[2], 5, 11);
        assertAlternatingPixels(second[0], 5, 11);
        assertAlternatingPixels(second[1], 5, 11);
        assertAlternatingPixels(otherSet.artTiles()[0], 5, 11);
        assertAlternatingPixels(CdPlayerVisualAdapter.sourcePatterns(assets)[0], 5, 11);
        assertEquals(0x5B, assets.artReadView().unsignedByte(0));
    }

    @Test
    void residentPatternsHaveFreshMutableOwnershipForEveryOwnerAndCall() {
        var state = new CdPlayerArtRuntime.State(filled(0x300, 0x35), filled(0x300, 0x79), 1, false);
        var first = CdPlayerVisualAdapter.residentPatterns(state);
        var second = CdPlayerVisualAdapter.residentPatterns(state);
        var otherState = new CdPlayerArtRuntime.State(filled(0x300, 0x12), filled(0x300, 0xCB), 1, false);
        var other = CdPlayerVisualAdapter.residentPatterns(otherState);
        assertNotSame(first, second);
        for (int tile = 0; tile < 24; tile++) {
            assertNotSame(first[tile], second[tile]);
            assertNotSame(first[tile], other[tile]);
            if (tile > 0) assertNotSame(first[0], first[tile]);
        }
        first[0].clear();
        first[23] = new Pattern();
        assertAlternatingPixels(first[1], 7, 9);
        assertAlternatingPixels(second[0], 7, 9);
        assertAlternatingPixels(second[23], 7, 9);
        assertAlternatingPixels(other[0], 12, 11);
        assertAlternatingPixels(CdPlayerVisualAdapter.residentPatterns(state)[23], 7, 9);
        assertEquals(0x79, state.residentBank().unsignedByte(0));
    }

    @Test
    void factoryHasNoRendererCuesOrAnimationAndDoesNotChooseAnInitialFrame() {
        var f = new Fixture();
        f.cue(0, 0x0005); // Even a nonempty frame-zero cue is not initialization.
        f.mapping(197, -20, 0, 0, 0, -16);
        Arrays.fill(f.art, 5 * 32, 6 * 32, (byte) 0x62);
        var assets = f.decode();
        var ingress = new CdPlayerArtRuntime.State(filled(0x300, 0x35), filled(0x300, 0x79), 255, true);
        var runtime = new CdPlayerArtRuntime(assets, ingress);
        var set = CdPlayerVisualAdapter.artSet(assets, 0x12400);
        assertEquals(0x12400, set.basePatternIndex());
        assertEquals(24, set.bankSize());
        assertEquals(2171, set.artTiles().length);
        assertEquals(198, set.mappingFrames().size());
        assertTrue(set.mappingFrames().getFirst().pieces().isEmpty());
        assertEquals(1, set.mappingFrames().get(197).pieces().size());
        assertTrue(set.dplcFrames().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> set.dplcFrames().add(null));
        assertEquals(0, set.paletteIndex());
        assertEquals(0, set.frameDelay());
        assertNull(set.animationProfile());
        assertNull(set.animationSet());
        assertAlternatingPixels(CdPlayerVisualAdapter.residentPatterns(runtime.capture())[0], 7, 9);
        assertSame(ingress, runtime.capture());
        // Only the explicit runtime service drains the already pending dirty RAM.
        assertTrue(runtime.serviceVInt(false, -1).isPresent());
        assertAlternatingPixels(CdPlayerVisualAdapter.residentPatterns(runtime.capture())[0], 3, 5);
        assertAlternatingPixels(CdPlayerVisualAdapter.residentPatterns(runtime.capture())[23], 7, 9);
    }

    @Test
    void conversionAfterExplicitRuntimeServiceAndRestorePreservesFixedDmaSuffixAndResidentTail() {
        var f = new Fixture();
        f.cue(1, 0x0005);
        Arrays.fill(f.art, 5 * 32, 6 * 32, (byte) 0x62);
        var ingress = new CdPlayerArtRuntime.State(filled(0x300, 0x35), filled(0x300, 0x79), 0, false);
        var runtime = new CdPlayerArtRuntime(f.decode(), ingress);
        var before = CdPlayerVisualAdapter.residentPatterns(runtime.capture());
        assertTrue(runtime.serviceVInt(1).isPresent());
        var after = CdPlayerVisualAdapter.residentPatterns(runtime.capture());
        assertAlternatingPixels(after[0], 6, 2);
        for (int tile = 1; tile < 23; tile++) {
            assertAlternatingPixels(after[tile], 3, 5);
        }
        assertAlternatingPixels(after[23], 7, 9);
        assertAlternatingPixels(before[0], 7, 9);
        runtime.restore(ingress);
        var restored = CdPlayerVisualAdapter.residentPatterns(runtime.capture());
        for (var tile : restored) assertAlternatingPixels(tile, 7, 9);
        assertSame(ingress, runtime.capture());
        assertAlternatingPixels(after[0], 6, 2);
    }

    @Test
    void decoderOwnedSourceStorageStaysDetachedFromCallerArrays() {
        var f = new Fixture();
        f.mapping(1, -20, 8, 0, 0, -16);
        Arrays.fill(f.art, 0, 69471, (byte) 0x5B);
        var assets = f.decode();
        Arrays.fill(f.mappings, (byte) 0xEE);
        Arrays.fill(f.dplcs, (byte) 0xEE);
        Arrays.fill(f.art, (byte) 0xEE);
        assertEquals(List.of(new SpriteMappingPiece(-16, -20, 3, 1, 0, false, false, 0, false)),
                CdPlayerVisualAdapter.mappingFrames(assets).get(1).pieces());
        assertAlternatingPixels(CdPlayerVisualAdapter.sourcePatterns(assets)[0], 5, 11);
        assertEquals(0, assets.alignmentByte());
    }

    @Test
    void virtualBaseMustFitTheWholeLogicalBankWithoutImposingNativeVramAddresses() {
        var assets = new Fixture().decode();
        assertEquals(0, CdPlayerVisualAdapter.artSet(assets, 0).basePatternIndex());
        assertEquals(Integer.MAX_VALUE - 23,
                CdPlayerVisualAdapter.artSet(assets, Integer.MAX_VALUE - 23).basePatternIndex());
        for (int invalid : new int[] {-1, Integer.MIN_VALUE, Integer.MAX_VALUE - 22, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> CdPlayerVisualAdapter.artSet(assets, invalid));
        }
    }

    @Test
    void absentAssetsOrStateCannotCreateDefaultVisualData() {
        assertThrows(NullPointerException.class, () -> CdPlayerVisualAdapter.mappingFrames(null));
        assertThrows(NullPointerException.class, () -> CdPlayerVisualAdapter.sourcePatterns(null));
        assertThrows(NullPointerException.class, () -> CdPlayerVisualAdapter.residentPatterns(null));
        assertThrows(NullPointerException.class, () -> CdPlayerVisualAdapter.artSet(null, 0));
    }

    private static void assertPixels(Pattern pattern, int... expected) {
        assertEquals(64, expected.length);
        assertNotNull(pattern);
        for (int pixel = 0; pixel < expected.length; pixel++) {
            assertEquals(expected[pixel], pattern.getPixel(pixel % 8, pixel / 8), "pixel " + pixel);
        }
    }

    private static void assertAlternatingPixels(Pattern pattern, int high, int low) {
        assertNotNull(pattern);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x += 2) {
                assertEquals(high, pattern.getPixel(x, y));
                assertEquals(low, pattern.getPixel(x + 1, y));
            }
        }
    }

    private static byte[] filled(int length, int value) {
        byte[] result = new byte[length];
        Arrays.fill(result, (byte) value);
        return result;
    }

    private static void word(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 8);
        bytes[offset + 1] = (byte) value;
    }

    private static final class Fixture {
        final byte[] mappings = new byte[3004];
        final byte[] dplcs = new byte[1636];
        final byte[] art = new byte[69472];
        int mappingEnd = 398;
        int dplcEnd = 398;

        Fixture() {
            for (int frame = 0; frame < 198; frame++) {
                word(mappings, frame * 2, 396);
                word(dplcs, frame * 2, 396);
            }
        }

        void mapping(int frame, int... pieces) {
            if (pieces.length % 5 != 0) throw new IllegalArgumentException("Test pieces must have five bytes");
            word(mappings, frame * 2, mappingEnd);
            mappings[mappingEnd] = (byte) (pieces.length / 5);
            for (int i = 0; i < pieces.length; i++) mappings[mappingEnd + 1 + i] = (byte) pieces[i];
            mappingEnd = (mappingEnd + 1 + pieces.length + 1) & ~1;
        }

        void alias(int frame, int sourceFrame) {
            mappings[frame * 2] = mappings[sourceFrame * 2];
            mappings[frame * 2 + 1] = mappings[sourceFrame * 2 + 1];
        }

        void cue(int frame, int... entries) {
            word(dplcs, frame * 2, dplcEnd);
            word(dplcs, dplcEnd, entries.length);
            for (int i = 0; i < entries.length; i++) word(dplcs, dplcEnd + 2 + i * 2, entries[i]);
            dplcEnd += 2 + entries.length * 2;
        }

        CdSonicVisualAssets decode() {
            return CdSonicVisualAssets.decode(Arrays.copyOf(mappings, mappingEnd),
                    Arrays.copyOf(dplcs, dplcEnd), art);
        }
    }
}

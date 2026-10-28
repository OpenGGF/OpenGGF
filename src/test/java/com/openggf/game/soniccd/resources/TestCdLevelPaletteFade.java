package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Literal native colour-step witnesses; no VSync, driver, transition or rendering is simulated. */
class TestCdLevelPaletteFade {
    private static Stream<Arguments> whiteSteps() {
        return Stream.of(Arguments.of(0x0000, 0x0002), Arguments.of(0x000C, 0x000E),
                Arguments.of(0x000E, 0x002E), Arguments.of(0x00CE, 0x00EE),
                Arguments.of(0x00EE, 0x02EE), Arguments.of(0x0CEE, 0x0EEE),
                Arguments.of(0x0EEE, 0x0EEE), Arguments.of(0x0001, 0x0003),
                Arguments.of(0xF00E, 0xF02E), Arguments.of(0x8EEE, 0x8EEE),
                Arguments.of(0xFFFF, 0xFFFF));
    }

    private static Stream<Arguments> blackSteps() {
        return Stream.of(Arguments.of(0x0000, 0x0000), Arguments.of(0x0002, 0x0000),
                Arguments.of(0x0EEE, 0x0EEC), Arguments.of(0x0EE0, 0x0EC0),
                Arguments.of(0x0E00, 0x0C00), Arguments.of(0x0200, 0x0000),
                Arguments.of(0x0001, 0x0001), Arguments.of(0xF002, 0xF000),
                Arguments.of(0x8000, 0x8000), Arguments.of(0xFFFF, 0xFFFD));
    }

    @ParameterizedTest @MethodSource("whiteSteps")
    void whiteUsesNativeRedThenGreenThenBlueWithoutNormalizingDirtyBits(int before, int after) {
        var input = new CdLevelPaletteFade.Palettes(filled(before), filled(0x0000));
        var result = CdLevelPaletteFade.stepToWhite(input);
        assertArrayEquals(filled(after).copy(), result.palette().copy());
        assertArrayEquals(filled(0x0002).copy(), result.waterPalette().copy());
        assertArrayEquals(filled(before).copy(), input.palette().copy());
        assertArrayEquals(filled(0x0000).copy(), input.waterPalette().copy());
    }

    @ParameterizedTest @MethodSource("blackSteps")
    void blackUsesNativeRedThenGreenThenBlueWithoutNormalizingDirtyBits(int before, int after) {
        var input = new CdLevelPaletteFade.Palettes(filled(before), filled(0x0EEE));
        var result = CdLevelPaletteFade.stepToBlack(input);
        assertArrayEquals(filled(after).copy(), result.palette().copy());
        assertArrayEquals(filled(0x0EEC).copy(), result.waterPalette().copy());
        assertArrayEquals(filled(before).copy(), input.palette().copy());
        assertArrayEquals(filled(0x0EEE).copy(), input.waterPalette().copy());
    }

    @Test void eachCallChangesEveryOneOfThe64WordsInBothDistinctBuffers() {
        byte[] palette = filled(0).copy(), water = filled(0x0EEE).copy();
        for (int at : new int[]{0, 2, 62, 64, 126}) {
            put(palette, at, 0x00EE); put(water, at, 0x0EE0);
        }
        var input = new CdLevelPaletteFade.Palettes(CdAssetBytes.copyOf(palette), CdAssetBytes.copyOf(water));
        byte[] whitePalette = filled(2).copy(), whiteWater = filled(0x0EEE).copy();
        byte[] blackPalette = filled(0).copy(), blackWater = filled(0x0EEC).copy();
        for (int at : new int[]{0, 2, 62, 64, 126}) {
            put(whitePalette, at, 0x02EE); put(whiteWater, at, 0x0EE2);
            put(blackPalette, at, 0x00EC); put(blackWater, at, 0x0EC0);
        }
        var white = CdLevelPaletteFade.stepToWhite(input); var black = CdLevelPaletteFade.stepToBlack(input);
        assertArrayEquals(whitePalette, white.palette().copy()); assertArrayEquals(whiteWater, white.waterPalette().copy());
        assertArrayEquals(blackPalette, black.palette().copy()); assertArrayEquals(blackWater, black.waterPalette().copy());
        assertArrayEquals(palette, input.palette().copy()); assertArrayEquals(water, input.waterPalette().copy());
    }

    @Test void native22StepCountHas21ColourChangesThenTheFinalNoOpAndPreservesRawNonColourBits() {
        var white = new CdLevelPaletteFade.Palettes(filled(0), filled(0xF001));
        var black = new CdLevelPaletteFade.Palettes(filled(0x0EEE), filled(0xFFFF));
        for (int step = 1; step <= 22; step++) {
            var previousWhite = white; var previousBlack = black;
            white = CdLevelPaletteFade.stepToWhite(white); black = CdLevelPaletteFade.stepToBlack(black);
            if (step == 7) {
                assertEquals(0x000E, white.palette().unsignedWord(0));
                assertEquals(0x0EE0, black.palette().unsignedWord(0));
            } else if (step == 14) {
                assertEquals(0x00EE, white.palette().unsignedWord(0));
                assertEquals(0x0E00, black.palette().unsignedWord(0));
            } else if (step >= 21) {
                assertArrayEquals(filled(0x0EEE).copy(), white.palette().copy());
                assertArrayEquals(filled(0xFEEF).copy(), white.waterPalette().copy());
                assertArrayEquals(filled(0).copy(), black.palette().copy());
                assertArrayEquals(filled(0xF111).copy(), black.waterPalette().copy());
                if (step == 22) { assertEquals(previousWhite, white); assertEquals(previousBlack, black); }
            }
        }
    }

    @Test void retainedCutsAreValuesAndReplayingOneStepDoesNotConsumeTheSourceOrMutatePriorResults() {
        var cut = new CdLevelPaletteFade.Palettes(filled(0x00EE), filled(0x0EE0));
        var sameBytes = new CdLevelPaletteFade.Palettes(filled(0x00EE), filled(0x0EE0));
        assertEquals(cut, sameBytes); assertEquals(cut.hashCode(), sameBytes.hashCode());
        var first = CdLevelPaletteFade.stepToWhite(cut);
        var after = CdLevelPaletteFade.stepToBlack(first);
        assertEquals(first, CdLevelPaletteFade.stepToWhite(sameBytes));
        assertEquals(after, CdLevelPaletteFade.stepToBlack(first));
        byte[] hostileCopy = first.palette().copy(); Arrays.fill(hostileCopy, (byte) 0);
        assertEquals(0x02EE, first.palette().unsignedWord(0)); assertEquals(0x00EE, cut.palette().unsignedWord(0));
        assertNotEquals(cut, first); assertNotEquals(cut, new CdLevelPaletteFade.Palettes(cut.palette(), filled(0)));
    }

    @Test void fullNative003fRangeRequiresTwoExplicit128ByteBuffersAndNoImplicitWaterDefault() {
        for (int length : new int[]{0, 1, 126, 127, 129, 256}) {
            var bad = CdAssetBytes.copyOf(new byte[length]);
            assertThrows(IllegalArgumentException.class, () -> new CdLevelPaletteFade.Palettes(bad, filled(0)));
            assertThrows(IllegalArgumentException.class, () -> new CdLevelPaletteFade.Palettes(filled(0), bad));
        }
        assertThrows(NullPointerException.class, () -> new CdLevelPaletteFade.Palettes(null, filled(0)));
        assertThrows(NullPointerException.class, () -> new CdLevelPaletteFade.Palettes(filled(0), null));
        assertThrows(NullPointerException.class, () -> CdLevelPaletteFade.stepToWhite(null));
        assertThrows(NullPointerException.class, () -> CdLevelPaletteFade.stepToBlack(null));
    }

    private static CdAssetBytes filled(int word) {
        byte[] bytes = new byte[128];
        for (int at = 0; at < bytes.length; at += 2) put(bytes, at, word);
        return CdAssetBytes.copyOf(bytes);
    }
    private static void put(byte[] bytes, int at, int word) { bytes[at] = (byte) (word >>> 8); bytes[at + 1] = (byte) word; }
}

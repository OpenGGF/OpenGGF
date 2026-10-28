package com.openggf.game.soniccd.scene;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/** Literal/native-source component vectors only: no scene pulse, clock, live owner or script replay. */
class TestCdWarpPalette {
    @Test void revealUsesEightExplicitIntensitiesForEachOfThreeChannels() {
        var current = fill(0x0EEE); var target = fill(0x0246);
        int[] targets = {6, 4, 2}; int expected = 0x0EEE;
        for (int component = 0; component < 3; component++) for (int intensity = 14; intensity >= 0; intensity -= 2) {
            current = CdWarpPalette.fadeFromWhite(current, target, component, intensity);
            int shift = component * 4; expected = (expected & ~(14 << shift)) | (Math.max(targets[component], intensity) << shift);
            assertWords(expected, current);
        }
        assertWords(0x0246, current); assertWords(0x0246, target);
    }

    @Test void whiteExitUsesEightPassesPerComponentAndCapsRatherThanCarrying() {
        var current = fill(0); int expected = 0;
        for (int component = 0; component < 3; component++) for (int pass = 1; pass <= 8; pass++) {
            current = CdWarpPalette.fadeToWhite(current, component);
            int shift = component * 4; expected = (expected & ~(14 << shift)) | (Math.min(14, pass * 2) << shift);
            assertWords(expected, current);
        }
        assertWords(0x0EEE, current);
    }

    @Test void whiteRevealUsesTargetOnlyForSelectedChannelAndCurrentForTheOthers() {
        assertWords(0x0A26, CdWarpPalette.fadeFromWhite(fill(0x0A24), fill(0x0826), 0, 2));
        assertWords(0x0824, CdWarpPalette.fadeFromWhite(fill(0x0A24), fill(0x0826), 2, 0));
        assertWords(0x0AC4, CdWarpPalette.fadeFromWhite(fill(0x0A24), fill(0x0826), 1, 12));
        assertWords(0x0EEE, CdWarpPalette.fadeFromWhite(fill(0x0EEE), fill(0), 0, 14));
    }

    @Test void nativeRotationsRetainDirtyWordBitsAndClearExactlyTheNativeMasks() {
        for (int component = 0; component < 3; component++) {
            assertWords(0xEEEE, CdWarpPalette.fadeToWhite(fill(0xFFFF), component));
            assertWords(0xEEEE, CdWarpPalette.fadeFromWhite(fill(0xFFFF), fill(0xFFFF), component, 0));
        }
        int[] zeros = {0xEEE0, 0xEE0E, 0xE0EE}, reveal = {0x8004, 0x8020, 0x8200}, white = {0x8002, 0x8020, 0x8200};
        for (int component = 0; component < 3; component++) {
            assertWords(zeros[component], CdWarpPalette.fadeFromWhite(fill(0xFFFF), fill(0), component, 0));
            assertWords(reveal[component], CdWarpPalette.fadeFromWhite(fill(0x8001), fill(0x1234), component, 2));
            assertWords(white[component], CdWarpPalette.fadeToWhite(fill(0x8001), component));
        }
    }

    @Test void wholePalettePassesVisitEveryWordAndNeverMutateTheirInputs() {
        byte[] raw = new byte[128]; for (int at = 0; at < 128; at += 2) { raw[at] = (byte) (at + 1); raw[at + 1] = (byte) (at + 2); }
        var current = CdAssetBytes.copyOf(raw); var target = fill(0x0642); var result = CdWarpPalette.fadeFromWhite(current, target, 1, 4);
        assertArrayEquals(raw, current.copy()); assertWords(0x0642, target); assertNotSame(current, result);
        assertNotEquals(current.unsignedWord(126), result.unsignedWord(126));
        assertArrayEquals(raw, current.copy()); CdWarpPalette.fadeToWhite(current, 2); assertArrayEquals(raw, current.copy());
    }

    @Test void paletteShapeComponentsAndIntensityRejectInvalidHostValues() {
        var good = fill(0); assertThrows(NullPointerException.class, () -> CdWarpPalette.fadeToWhite(null, 0));
        assertThrows(NullPointerException.class, () -> CdWarpPalette.fadeFromWhite(good, null, 0, 0));
        for (int bytes : new int[]{0, 126, 127, 129, 130}) {
            var bad = CdAssetBytes.copyOf(new byte[bytes]);
            assertThrows(IllegalArgumentException.class, () -> CdWarpPalette.fadeToWhite(bad, 0));
            assertThrows(IllegalArgumentException.class, () -> CdWarpPalette.fadeFromWhite(good, bad, 0, 0));
        }
        for (int component : new int[]{-1, 3, 16}) {
            assertThrows(IllegalArgumentException.class, () -> CdWarpPalette.fadeToWhite(good, component));
            assertThrows(IllegalArgumentException.class, () -> CdWarpPalette.fadeFromWhite(good, good, component, 0));
        }
        for (int intensity : new int[]{-2, -1, 1, 3, 15, 16, 65536})
            assertThrows(IllegalArgumentException.class, () -> CdWarpPalette.fadeFromWhite(good, good, 0, intensity));
    }

    @Test void cycleFirstEvenPassWritesFourteenWordsAndOnlyThoseWords() {
        var before = fill(0x0124); var data = cycleData(); var after = CdWarpPalette.cycle(before, 0xFFFF, 0, data);
        assertTrue(after.updated()); assertEquals(0, after.timerWord()); assertEquals(1, after.indexWord());
        for (int index = 0; index < 64; index++)
            assertEquals(index >= 17 && index < 31 ? data.unsignedWord((index - 17) * 2) : 0x0124, after.palette().unsignedWord(index * 2));
        assertWords(0x0124, before); assertNotSame(before, after.palette());
    }

    @Test void cycleOddPassDoesNotReadMissingOrMalformedColourSourceOrNormalizeIndex() {
        var before = fill(0x0ACE);
        for (var data : Arrays.asList((CdAssetBytes) null, CdAssetBytes.copyOf(new byte[0]), CdAssetBytes.copyOf(new byte[1]))) {
            var after = CdWarpPalette.cycle(before, 0, 0xFFFF, data);
            assertFalse(after.updated()); assertSame(before, after.palette()); assertEquals(1, after.timerWord()); assertEquals(0xFFFF, after.indexWord());
        }
    }

    @Test void cycleWrapUsesOldIndexWindowBeforeResettingTheStoredIndex() {
        var before = fill(0); var data = cycleData(); var after = CdWarpPalette.cycle(before, 1, 13, data);
        assertEquals(2, after.timerWord()); assertEquals(0, after.indexWord());
        for (int colour = 0; colour < 14; colour++) assertEquals(data.unsignedWord((13 + colour) * 2), after.palette().unsignedWord((17 + colour) * 2));
        var dirty14 = CdWarpPalette.cycle(before, 1, 14, data);
        assertEquals(0, dirty14.indexWord());
        for (int colour = 0; colour < 14; colour++) assertEquals(data.unsignedWord((14 + colour) * 2), dirty14.palette().unsignedWord((17 + colour) * 2));
    }

    @Test void cycleWordAdditionAndDoublingRetainUnsignedAndSignedWrap() {
        var before = fill(0); var data = cycleData();
        var wrapped = CdWarpPalette.cycle(before, 0xFFFF, 0x8000, data);
        assertEquals(0, wrapped.timerWord()); assertEquals(0, wrapped.indexWord());
        for (int i = 0; i < 14; i++) assertEquals(data.unsignedWord(i * 2), wrapped.palette().unsignedWord((17 + i) * 2));
        var highTimer = CdWarpPalette.cycle(before, 0x7FFF, 0, data); assertEquals(0x8000, highTimer.timerWord()); assertTrue(highTimer.updated());
        var held = CdWarpPalette.cycle(before, 0xFFFE, 0xABCD, null); assertEquals(0xFFFF, held.timerWord()); assertFalse(held.updated()); assertEquals(0xABCD, held.indexWord());
    }

    @Test void negativePcAliasesUseTheActualUpdatedIndexAndTimerWords() {
        var before = fill(0x0CCC); var data = cycleData();
        for (int oldIndex : new int[]{0x7FFF, 0xFFFF}) {
            var atIndex = CdWarpPalette.cycle(before, 1, oldIndex, data); assertEquals(0, atIndex.indexWord());
            assertEquals(0, atIndex.palette().unsignedWord(17 * 2));
            for (int i = 1; i < 14; i++) assertEquals(data.unsignedWord((i - 1) * 2), atIndex.palette().unsignedWord((17 + i) * 2));
        }
        for (int oldIndex : new int[]{0x7FFE, 0xFFFE}) {
            var atTimer = CdWarpPalette.cycle(before, 1, oldIndex, data); assertEquals(0, atTimer.indexWord());
            assertEquals(2, atTimer.palette().unsignedWord(17 * 2)); assertEquals(0, atTimer.palette().unsignedWord(18 * 2));
            for (int i = 2; i < 14; i++) assertEquals(data.unsignedWord((i - 2) * 2), atTimer.palette().unsignedWord((17 + i) * 2));
        }
    }

    @Test void evenCycleStopsAtUnavailableFiniteSourceRatherThanWrappingItsPeriod() {
        var before = fill(0); var good = cycleData();
        assertThrows(NullPointerException.class, () -> CdWarpPalette.cycle(before, 1, 0, null));
        for (int count : new int[]{0, 1, 27}) {
            var shortData = good.slice(0, count);
            assertThrows(IndexOutOfBoundsException.class, () -> CdWarpPalette.cycle(before, 1, 0, shortData));
        }
        for (int index : new int[]{15, 0x4000, 0x7FFD, 0xFFFD})
            assertThrows(IndexOutOfBoundsException.class, () -> CdWarpPalette.cycle(before, 1, index, good));
        assertWords(0, before);
    }

    @Test void cycleRejectsNonWordHostCountersBeforePerformingAColourPass() {
        var before = fill(0); var data = cycleData();
        for (int bad : new int[]{-1, 65536, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> CdWarpPalette.cycle(before, bad, 0, data));
            assertThrows(IllegalArgumentException.class, () -> CdWarpPalette.cycle(before, 0, bad, data));
        }
        assertThrows(NullPointerException.class, () -> CdWarpPalette.cycle(null, 0, 0, data));
        assertThrows(IllegalArgumentException.class, () -> CdWarpPalette.cycle(CdAssetBytes.copyOf(new byte[127]), 0, 0, data));
    }

    @Test void cycleValueEqualityIsBytewiseAndDefensiveCopiesCannotChangeAReplay() {
        var one = CdWarpPalette.cycle(fill(0x0EEE), 0xFFFF, 0, cycleData());
        var two = CdWarpPalette.cycle(fill(0x0EEE), 0xFFFF, 0, cycleData());
        assertEquals(one, two); assertEquals(one.hashCode(), two.hashCode());
        byte[] detached = one.palette().copy(); detached[0] ^= 1; assertEquals(two, one);
        assertNotEquals(one, CdWarpPalette.cycle(fill(0x0EEE), 1, 1, cycleData()));
        assertThrows(IllegalArgumentException.class, () -> new CdWarpPalette.Cycle(fill(0), -1, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new CdWarpPalette.Cycle(fill(0), 0, 65536, false));
        assertThrows(NullPointerException.class, () -> new CdWarpPalette.Cycle(null, 0, 0, false));
    }

    private static CdAssetBytes cycleData() { return CdAssetBytes.copyOf(HexFormat.of().parseHex("00e002e204e406e608e80aea0cec0eee0cec0aea08e806e604e402e200e002e204e406e608e80aea0cec0eee0cec0aea08e806e604e402e2")); }
    private static CdAssetBytes fill(int word) { byte[] bytes = new byte[128]; for (int at = 0; at < 128; at += 2) { bytes[at] = (byte) (word >>> 8); bytes[at + 1] = (byte) word; } return CdAssetBytes.copyOf(bytes); }
    private static void assertWords(int expected, CdAssetBytes actual) { assertEquals(128, actual.length()); for (int at = 0; at < 128; at += 2) assertEquals(expected, actual.unsignedWord(at), "Palette WORD byte offset" + at); }
}

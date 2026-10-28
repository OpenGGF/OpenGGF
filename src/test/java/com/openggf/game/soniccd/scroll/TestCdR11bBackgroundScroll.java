package com.openggf.game.soniccd.scroll;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll.State;
import static org.junit.jupiter.api.Assertions.*;

/** Source-derived component values only; no World, clock, media or invocation authority. */
class TestCdR11bBackgroundScroll {
    @Test void ordinarySurfaceReusesTheExistingCapturedValuesWithoutRetainedFields() {
        assertEquals(0, CdR11bBackgroundScroll.class.getDeclaredFields().length);
        assertEquals(1, CdR11bBackgroundScroll.class.getDeclaredConstructors().length);
        var constructor = CdR11bBackgroundScroll.class.getDeclaredConstructors()[0];
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        assertEquals(0, constructor.getParameterCount());
        assertEquals(2, Arrays.stream(CdR11bBackgroundScroll.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers())).count());
        assertEquals(List.of("cameraXWord", "cameraYWord", "scrollXDiff", "scrollYDiff", "scrollLockByte"),
                Arrays.stream(CdR11bBackgroundScroll.Input.class.getRecordComponents()).map(c -> c.getName()).toList());
    }

    @Test void inputKeepsUnsignedCameraWordsSignedNativeDiffWordsAndTheActualLockByte() {
        var input = new CdR11bBackgroundScroll.Input(0xFFFF, 0x8000, -32768, 32767, 0xFF);
        assertEquals(0xFFFF, input.cameraXWord()); assertEquals(0x8000, input.cameraYWord());
        assertEquals(-32768, input.scrollXDiff()); assertEquals(32767, input.scrollYDiff());
        assertEquals(0xFF, input.scrollLockByte());
        for (int field = 0; field < 5; field++) {
            int index = field;
            int[] bad = field < 2 ? new int[]{-1, 65536} : field < 4 ? new int[]{-32769, 32768} : new int[]{-1, 256};
            for (int value : bad) {
                int[] row = {0, 0, 0, 0, 0}; row[index] = value;
                assertThrows(IllegalArgumentException.class,
                        () -> new CdR11bBackgroundScroll.Input(row[0], row[1], row[2], row[3], row[4]));
            }
        }
    }

    @Test void initPublishesLiteralCameraWordsAndPreservesEveryOtherField() {
        State before = state(0x8123A1B2, 0x82345678, 0x9245C3D4, 0xB456E5F6, 0xA3671020, 0xC5673040);
        var fg = foreground(0x0F60, 0x00A0, 0xCAFE);
        State actual = CdR11bBackgroundScroll.init(before, fg);
        byte[] deform = before.deformationRam().copy(); Arrays.fill(deform, 0, 16, (byte) 0);
        State expected = new State(0x07B0A1B2, 0x00140000, 0x02E2C3D4, 0x0014E5F6,
                0x01EC1020, 0x00143040, before.crossingRam(), before.scrollFlagsBg(),
                before.scrollFlagsBg2(), before.scrollFlagsBg3(), before.cloudRam(),
                CdAssetBytes.copyOf(deform), before.hscrollRam(), before.vscroll());
        assertValue(expected, actual);
        assertSame(before.crossingRam(), actual.crossingRam());
        assertSame(before.cloudRam(), actual.cloudRam()); assertSame(before.hscrollRam(), actual.hscrollRam());
        assertEquals(0x82345678, before.cameraBgY());
        assertEquals(0x00A0CAFE, fg.cameraY16_16());
    }

    @Test void initRoundsBeforeTheNativeTripleRatherThanMultiplyingTheForegroundFirst() {
        int[][] rows = {{15, 7, 0, 1}, {31, 15, 3, 3}, {65535, 0x7FFF, 0x2FFD, 0x1FFF}};
        for (int[] row : rows) {
            State before = state(0x00001111, 0, 0x00002222, 0x00003333, 0x00004444, 0x00005555);
            State after = CdR11bBackgroundScroll.init(before, foreground(row[0], 0, 0));
            assertEquals((row[1] << 16) | 0x1111, after.cameraBgX());
            assertEquals((row[2] << 16) | 0x2222, after.cameraBg2X());
            assertEquals((row[3] << 16) | 0x4444, after.cameraBg3X());
        }
    }

    @Test void initUsesSignedIntegerYAndNeverBorrowsTheForegroundMemoryFraction() {
        int[][] rows = {{0x0061, 0x000C2000}, {0x7FFF, 0x0FFFE000}, {0x8000, 0xF0000000}, {0xFFFF, 0xFFFFE000}};
        for (int[] row : rows) {
            State before = state(0, 0x76543210, 0, 0x1111ABCD, 0, 0x2222DCBA);
            State first = CdR11bBackgroundScroll.init(before, foreground(0, row[0], 0x1357));
            State second = CdR11bBackgroundScroll.init(before, foreground(0, row[0], 0xECA8));
            assertValue(first, second);
            assertEquals(row[1], first.cameraBgY());
            assertEquals((row[1] & 0xFFFF0000) | 0xABCD, first.cameraBg2Y());
            assertEquals((row[1] & 0xFFFF0000) | 0xDCBA, first.cameraBg3Y());
        }
    }

    @Test void initClearsOnlyTheFourDeformationLongsAndNotTheSeparateCloudAllocation() {
        State before = state(0, 0, 0, 0, 0, 0);
        byte[] originalDeform = pattern(512, 17); byte[] originalClouds = pattern(512, 39);
        before = buffers(before, originalClouds, originalDeform, pattern(1024, 73));
        State after = CdR11bBackgroundScroll.init(before, foreground(0, 0, 0));
        byte[] expected = originalDeform.clone(); Arrays.fill(expected, 0, 16, (byte) 0);
        assertArrayEquals(expected, after.deformationRam().copy());
        assertArrayEquals(originalClouds, after.cloudRam().copy());
        assertArrayEquals(originalDeform, before.deformationRam().copy());
        assertArrayEquals(before.hscrollRam().copy(), after.hscrollRam().copy());
    }

    @Test void nonzeroHighByteLockReturnsTheSameCompleteStateWithoutClearsOrCloudTicks() {
        State before = state(0x12345678, 0x23456789, 0x3456789A, 0x456789AB, 0x56789ABC, 0x6789ABCD);
        for (int lock : new int[]{1, 0x80, 0xFF}) {
            assertSame(before, CdR11bBackgroundScroll.step(before,
                    new CdR11bBackgroundScroll.Input(0xFFFF, 0x8000, -32768, 32767, lock)));
        }
    }

    @Test void unlockedZeroDiffsStillClearWholeFlagWordsAndTestEveryCrossing() {
        State before = state(0, 0, 0, 0, 0, 0);
        before = buffers(before, before.cloudRam().copy(), filled(512, 0x69), filled(1024, 0xA5));
        byte[] deform = before.deformationRam().copy();
        putLong(deform, 0, 0x696A6969); putLong(deform, 4, 0x696A2969);
        putLong(deform, 8, 0x6969E969); putLong(deform, 12, 0x6969A969);
        // This state has deliberately nonzero accumulators; cloud high words matter in the table.
        words(deform, 16, 2, 0x9696); words(deform, 20, 2, 0x9696);
        words(deform, 24, 2, 0x9697); words(deform, 28, 2, 0x9697);
        words(deform, 32, 10, 0); words(deform, 52, 24, 0);
        byte[] hscroll = before.hscrollRam().copy();
        lines(hscroll, 0, 32, 0x00009696); lines(hscroll, 32, 32, 0x00009697);
        lines(hscroll, 64, 176, 0);
        State after = CdR11bBackgroundScroll.step(before, input(0, 0, 0, 0));
        State expected = new State(0, 0, 0, 0, 0, 0,
                CdAssetBytes.copyOf(new byte[]{0x10, 0x10, 0x10, 0x33, 0x10, 0x55, 0x66, 0x77}),
                0xAA00, 0, 0, before.cloudRam(), CdAssetBytes.copyOf(deform), CdAssetBytes.copyOf(hscroll), 0);
        assertValue(expected, after);
    }

    @Test void dirtyCrossingBytesCompareTheirEntireByteWithoutNormalizingHighOrLowBits() {
        for (byte[] crossed : new byte[][]{
                {(byte) 0xA0, (byte) 0xB0, (byte) 0xC0, 0x33, (byte) 0xD0, 0x55, 0x66, 0x77},
                {1, 1, 1, 0x33, 1, 0x55, 0x66, 0x77}}) {
            State before = crossings(state(0, 0, 0, 0, 0, 0), crossed);
            State after = CdR11bBackgroundScroll.step(before, input(0, 0, 0, 0));
            assertArrayEquals(crossed, after.crossingRam().copy());
            assertEquals(0, after.scrollFlagsBg());
            assertEquals(0, after.scrollFlagsBg2()); assertEquals(0, after.scrollFlagsBg3());
        }
    }

    @Test void negativeDiffsSetNativeLeftUpBitsAndPreserveSecondaryYFractions() {
        State before = state(0x00200000, 0x00200000, 0x00200000, 0x00FF1234, 0x00200000, 0xFFFF5678);
        before = crossings(before, new byte[]{0x10, 0x10, 0x10, 0x33, 0x10, 0x55, 0x66, 0x77});
        State after = CdR11bBackgroundScroll.step(before, input(0x1234, 0xABCD, -0x100, -0x100));
        cameras(after, 0x001F8000, 0x001FE000, 0x001FD000, 0x001F1234, 0x001FE000, 0x001F5678);
        assertEquals(0x5500, after.scrollFlagsBg());
        assertEquals(0xABCD001F, after.vscroll());
        assertArrayEquals(new byte[]{0, 0, 0, 0x33, 0, 0x55, 0x66, 0x77}, after.crossingRam().copy());
        assertEquals(0, after.scrollFlagsBg2()); assertEquals(0, after.scrollFlagsBg3());
    }

    @Test void positiveDiffsSetNativeRightDownBitsAfterTheFixedPointCarries() {
        State before = state(0x000F8000, 0x000FE000, 0x000FD000, 0x00FF1234, 0x000FE000, 0xFFFF5678);
        before = crossings(before, new byte[]{0x10, 0x10, 0x10, 0x33, 0x10, 0x55, 0x66, 0x77});
        State after = CdR11bBackgroundScroll.step(before, input(0xFFFF, 0xABC1, 0x100, 0x100));
        cameras(after, 0x00100000, 0x00100000, 0x00100000, 0x00101234, 0x00100000, 0x00105678);
        assertEquals(0xAA00, after.scrollFlagsBg()); assertEquals(0xABC10010, after.vscroll());
        assertEquals(0x0001, after.hscrollRam().unsignedWord(0));
    }

    @Test void positiveWrappingLongDifferencesDoNotBecomeNegativeDirectionFlags() {
        State before = state(0x7FFF8000, 0x7FFFE000, 0x7FFFD000, 0x11111234, 0x7FFFE000, 0x22225678);
        State after = CdR11bBackgroundScroll.step(before, input(0, 0x1234, 0x100, 0x100));
        cameras(after, 0x80000000, 0x80000000, 0x80000000, 0x80001234, 0x80000000, 0x80005678);
        assertEquals(0xAA00, after.scrollFlagsBg()); assertEquals(0x12348000, after.vscroll());
    }

    @Test void negativeWrappingLongDifferencesDoNotBecomePositiveDirectionFlags() {
        State before = state(0x80000000, 0x80000000, 0x80000000, 0x11111234, 0x80000000, 0x22225678);
        before = crossings(before, new byte[]{0x10, 0x10, 0x10, 0x33, 0x10, 0x55, 0x66, 0x77});
        State after = CdR11bBackgroundScroll.step(before, input(0, 0x1234, -0x100, -0x100));
        cameras(after, 0x7FFF8000, 0x7FFFE000, 0x7FFFD000, 0x7FFF1234, 0x7FFFE000, 0x7FFF5678);
        assertEquals(0x5500, after.scrollFlagsBg()); assertEquals(0x12347FFF, after.vscroll());
    }

    @Test void verticalMotionAccumulatesTheSignedDiffInsteadOfRecomputingFromForegroundY() {
        State before = state(0, 0x00123456, 0, 0x5555ABCD, 0, 0x6666DCBA);
        before = buffers(before, before.cloudRam().copy(), new byte[512], before.hscrollRam().copy());
        State first = CdR11bBackgroundScroll.step(before, input(0, 0x7FFF, 0, 0x80));
        State second = CdR11bBackgroundScroll.step(first, input(0, 0x7FFF, 0, 0x80));
        assertEquals(0x00124456, first.cameraBgY()); assertEquals(0x00125456, second.cameraBgY());
        assertEquals(0x0012ABCD, second.cameraBg2Y()); assertEquals(0x0012DCBA, second.cameraBg3Y());
        assertEquals(0x7FFF0012, second.vscroll());
        assertEquals(0x00020000L, second.deformationRam().unsignedLong(0));
        assertEquals(0x00018000L, second.deformationRam().unsignedLong(4));
        assertEquals(0x00010000L, second.deformationRam().unsignedLong(8));
        assertEquals(0x00008000L, second.deformationRam().unsignedLong(12));
    }

    @Test void cloudCarriesUseDeformationLongsAndBuildTheLiteralFortyTwoWords() {
        State before = state(0x00300000, 0, 0x00200000, 0, 0x00100000, 0);
        byte[] deform = filled(512, 0xA5);
        putLong(deform, 0, 0x7FFF8000); putLong(deform, 4, 0x00004000);
        putLong(deform, 8, 0xFFFFFFFF); putLong(deform, 12, 0xFFFFC000);
        before = buffers(before, pattern(512, 23), deform, before.hscrollRam().copy());
        byte[] expected = deform.clone();
        putLong(expected, 0, 0x80008000); putLong(expected, 4, 0x00010000);
        putLong(expected, 8, 0x00007FFF); putLong(expected, 12, 0);
        words(expected, 16, 2, 0x7FF0); words(expected, 20, 2, 0xFFEF);
        words(expected, 24, 2, 0xFFF0); words(expected, 28, 2, 0xFFF0);
        words(expected, 32, 10, 0xFFF0); words(expected, 52, 24, 0xFFE0);
        State after = CdR11bBackgroundScroll.step(before, input(0, 0, 0, 0));
        assertArrayEquals(expected, after.deformationRam().copy());
        assertSame(before.cloudRam(), after.cloudRam());
        assertArrayEquals(pattern(512, 23), after.cloudRam().copy());
        assertArrayEquals(deform, before.deformationRam().copy());
    }

    @Test void fourAllOnesCloudLongsWrapIndependentlyWithTheirOwnFractions() {
        State before = state(0, 0, 0, 0, 0, 0);
        byte[] deform = filled(512, 0xA5); Arrays.fill(deform, 0, 16, (byte) 0xFF);
        before = buffers(before, before.cloudRam().copy(), deform, before.hscrollRam().copy());
        State after = CdR11bBackgroundScroll.step(before, input(0, 0, 0, 0));
        assertEquals(0x0000FFFFL, after.deformationRam().unsignedLong(0));
        assertEquals(0x0000BFFFL, after.deformationRam().unsignedLong(4));
        assertEquals(0x00007FFFL, after.deformationRam().unsignedLong(8));
        assertEquals(0x00003FFFL, after.deformationRam().unsignedLong(12));
        for (int offset = 16; offset < 100; offset += 2) assertEquals(0, after.deformationRam().unsignedWord(offset));
        assertArrayEquals(Arrays.copyOfRange(deform, 100, 512), Arrays.copyOfRange(after.deformationRam().copy(), 100, 512));
    }

    @Test void allEightLowYPhasesKeepTheExact240MinusPhaseLongsAndTheSuppliedTail() {
        for (int phase = 0; phase < 8; phase++) {
            State before = state(0x00300000, (phase << 16) | 0x6BEE, 0x00020000, 0x77771234, 0x00010000, 0x88885678);
            before = buffers(before, before.cloudRam().copy(), new byte[512], filled(1024, 0x5A));
            State after = CdR11bBackgroundScroll.step(before, input(0x0124, 0x3456, 0, 0));
            byte[] expected = filled(1024, 0x5A);
            lines(expected, 0, 16 - phase, 0xFEDCFFFE);
            lines(expected, 16 - phase, 128, 0xFEDCFFFF);
            lines(expected, 144 - phase, 96, 0xFEDCFFFE);
            assertArrayEquals(expected, after.hscrollRam().copy(), "phase=" + phase);
            assertEquals((0x3456 << 16) | phase, after.vscroll());
            assertArrayEquals(filled(1024, 0x5A), before.hscrollRam().copy());
        }
    }

    @Test void highYReadsTheActualUnwrittenDeformationTailWithoutPaddingOrAFalseReadFailure() {
        State before = state(0, 0x01F81234, 0, 0, 0, 0);
        byte[] deform = filled(512, 0xA5);
        for (int i = 0; i < 30; i++) putWord(deform, 142 + i * 2, 0x6000 + i);
        before = buffers(before, before.cloudRam().copy(), deform, filled(1024, 0x5A));
        State after = CdR11bBackgroundScroll.step(before, input(0x1234, 0xABCD, 0, 0));
        int[] selected = {0xEDCC6000,0xEDCC6001,0xEDCC6002,0xEDCC6003,0xEDCC6004,0xEDCC6005,
                0xEDCC6006,0xEDCC6007,0xEDCC6008,0xEDCC6009,0xEDCC600A,0xEDCC600B,0xEDCC600C,0xEDCC600D,
                0xEDCC600E,0xEDCC600F,0xEDCC6010,0xEDCC6011,0xEDCC6012,0xEDCC6013,0xEDCC6014,0xEDCC6015,
                0xEDCC6016,0xEDCC6017,0xEDCC6018,0xEDCC6019,0xEDCC601A,0xEDCC601B,0xEDCC601C,0xEDCC601D};
        byte[] expected = filled(1024, 0x5A);
        for (int i = 0; i < selected.length; i++) lines(expected, i * 8, 8, selected[i]);
        assertArrayEquals(expected, after.hscrollRam().copy());
        assertArrayEquals(Arrays.copyOfRange(deform, 100, 512), Arrays.copyOfRange(after.deformationRam().copy(), 100, 512));
        assertEquals(0xABCD01F8, after.vscroll());
    }

    @Test void backgroundYHighBitsAreMaskedWhileTheirRawWordStillPublishesToVScroll() {
        State before = state(0, 0x01F80000, 0, 0, 0, 0);
        State negative = state(0, 0xFFF80000, 0, 0, 0, 0);
        before = buffers(before, before.cloudRam().copy(), pattern(512, 41), filled(1024, 0x5A));
        negative = buffers(negative, negative.cloudRam().copy(), pattern(512, 41), filled(1024, 0x5A));
        State first = CdR11bBackgroundScroll.step(before, input(0x1234, 0x5678, 0, 0));
        State second = CdR11bBackgroundScroll.step(negative, input(0x1234, 0x5678, 0, 0));
        assertArrayEquals(first.hscrollRam().copy(), second.hscrollRam().copy());
        assertEquals(0x567801F8, first.vscroll()); assertEquals(0x5678FFF8, second.vscroll());
    }

    @Test void sourceSetupAndZeroStepPairHasTheLiteralFullStateAndBufferRuns() {
        State before = state(0x8123A1B2, 0x82345678, 0x9245C3D4, 0xB456E5F6, 0xA3671020, 0xC5673040);
        State initialized = CdR11bBackgroundScroll.init(before, foreground(0x0F60, 0x00A0, 0xCAFE));
        State actual = CdR11bBackgroundScroll.step(initialized, input(0x0F60, 0x00A0, 0, 0));
        byte[] deform = before.deformationRam().copy();
        putLong(deform, 0, 0x00010000); putLong(deform, 4, 0x0000C000);
        putLong(deform, 8, 0x00008000); putLong(deform, 12, 0x00004000);
        words(deform, 16, 2, 0xFE13); words(deform, 20, 16, 0xFE14); words(deform, 52, 24, 0xFD1E);
        byte[] hscroll = before.hscrollRam().copy();
        lines(hscroll, 0, 124, 0xF0A0FE14); lines(hscroll, 124, 112, 0xF0A0FD1E);
        State expected = new State(0x07B0A1B2, 0x00140000, 0x02E2C3D4, 0x0014E5F6, 0x01EC1020, 0x00143040,
                CdAssetBytes.copyOf(new byte[]{0, 0, 0x10, 0x33, 0x10, 0x55, 0x66, 0x77}),
                0xA000, 0, 0, before.cloudRam(), CdAssetBytes.copyOf(deform), CdAssetBytes.copyOf(hscroll), 0x00A00014);
        assertValue(expected, actual);
        assertEquals(0x8123A1B2, before.cameraBgX());
    }

    @Test void signedWordExtremesKeepNativeEightEightUnitsAndWrappingLongSums() {
        int[][] rows = {{32767, -32768, 0x003FFF80, 0xFFF00000, 0x0017FFD0, 0x000FFFE0},
                {-32768, 32767, 0xFFC00000, 0x000FFFE0, 0xFFE80000, 0xFFF00000}};
        for (int[] row : rows) {
            State after = CdR11bBackgroundScroll.step(state(0, 0, 0, 0, 0, 0), input(0, 0x1234, row[0], row[1]));
            assertEquals(row[2], after.cameraBgX()); assertEquals(row[3], after.cameraBgY());
            assertEquals(row[4], after.cameraBg2X()); assertEquals(row[5], after.cameraBg3X());
            assertEquals((0x1234 << 16) | (row[3] >>> 16), after.vscroll());
        }
    }

    @Test void existingStateConstructorStillRejectsMalformedBuffersWithoutAnotherStateOwner() {
        State s = state(0, 0, 0, 0, 0, 0);
        for (int size : new int[]{0, 7, 9}) assertThrows(IllegalArgumentException.class,
                () -> new State(0, 0, 0, 0, 0, 0, CdAssetBytes.copyOf(new byte[size]), 0, 0, 0,
                        s.cloudRam(), s.deformationRam(), s.hscrollRam(), 0));
        for (int size : new int[]{0, 511, 513}) {
            CdAssetBytes wrong = CdAssetBytes.copyOf(new byte[size]);
            assertThrows(IllegalArgumentException.class, () -> new State(0, 0, 0, 0, 0, 0,
                    s.crossingRam(), 0, 0, 0, wrong, s.deformationRam(), s.hscrollRam(), 0));
            assertThrows(IllegalArgumentException.class, () -> new State(0, 0, 0, 0, 0, 0,
                    s.crossingRam(), 0, 0, 0, s.cloudRam(), wrong, s.hscrollRam(), 0));
        }
        for (int size : new int[]{0, 1023, 1025}) assertThrows(IllegalArgumentException.class,
                () -> new State(0, 0, 0, 0, 0, 0, s.crossingRam(), 0, 0, 0,
                        s.cloudRam(), s.deformationRam(), CdAssetBytes.copyOf(new byte[size]), 0));
    }

    @Test void nullArgumentsCannotStepOrInitializeAnOwner() {
        State s = state(0, 0, 0, 0, 0, 0); var fg = foreground(0, 0, 0);
        assertThrows(NullPointerException.class, () -> CdR11bBackgroundScroll.init(null, fg));
        assertThrows(NullPointerException.class, () -> CdR11bBackgroundScroll.init(s, null));
        assertThrows(NullPointerException.class, () -> CdR11bBackgroundScroll.step(null, input(0, 0, 0, 0)));
        assertThrows(NullPointerException.class, () -> CdR11bBackgroundScroll.step(s, null));
    }

    @Test void replayingTheSameExplicitValueCutProducesEqualBytesWithoutMutatingThatCut() {
        State before = state(0x12345678, 0x23456789, 0x3456789A, 0x456789AB, 0x56789ABC, 0x6789ABCD);
        byte[] crossing = before.crossingRam().copy(), clouds = before.cloudRam().copy();
        byte[] deform = before.deformationRam().copy(), hscroll = before.hscrollRam().copy();
        var input = input(0x3456, 0x4567, -0x1234, 0x2345);
        State first = CdR11bBackgroundScroll.step(before, input);
        State second = CdR11bBackgroundScroll.step(before, input);
        assertValue(first, second);
        assertArrayEquals(crossing, before.crossingRam().copy()); assertArrayEquals(clouds, before.cloudRam().copy());
        assertArrayEquals(deform, before.deformationRam().copy()); assertArrayEquals(hscroll, before.hscrollRam().copy());
        assertEquals(0x12345678, before.cameraBgX()); assertEquals(0x23456789, before.cameraBgY());
    }

    private static CdR11bBackgroundScroll.Input input(int x, int y, int dx, int dy) {
        return new CdR11bBackgroundScroll.Input(x, y, dx, dy, 0);
    }
    private static CdR11aForegroundScroll.State foreground(int x, int yWord, int fraction) {
        return new CdR11aForegroundScroll.State(x, (yWord << 16) | fraction, 0xA0, 0xB0, 0xCDEF, 0xFEDC,
                0xABCD, 0xBEEF, 0x1234, 0xD678, 0x9ABC);
    }
    private static State state(int... positions) {
        assertEquals(6, positions.length);
        return new State(positions[0], positions[1], positions[2], positions[3], positions[4], positions[5],
                CdAssetBytes.copyOf(new byte[]{0, 0, 0, 0x33, 0, 0x55, 0x66, 0x77}),
                0xFFFF, 0xA1B2, 0xC3D4, CdAssetBytes.copyOf(filled(512, 0x5E)),
                CdAssetBytes.copyOf(filled(512, 0x69)), CdAssetBytes.copyOf(filled(1024, 0xA5)), 0x89ABCDEF);
    }
    private static State crossings(State s, byte[] crossing) {
        return new State(s.cameraBgX(), s.cameraBgY(), s.cameraBg2X(), s.cameraBg2Y(), s.cameraBg3X(), s.cameraBg3Y(),
                CdAssetBytes.copyOf(crossing), s.scrollFlagsBg(), s.scrollFlagsBg2(), s.scrollFlagsBg3(),
                s.cloudRam(), s.deformationRam(), s.hscrollRam(), s.vscroll());
    }
    private static State buffers(State s, byte[] clouds, byte[] deform, byte[] hscroll) {
        return new State(s.cameraBgX(), s.cameraBgY(), s.cameraBg2X(), s.cameraBg2Y(), s.cameraBg3X(), s.cameraBg3Y(),
                s.crossingRam(), s.scrollFlagsBg(), s.scrollFlagsBg2(), s.scrollFlagsBg3(), CdAssetBytes.copyOf(clouds),
                CdAssetBytes.copyOf(deform), CdAssetBytes.copyOf(hscroll), s.vscroll());
    }
    private static byte[] filled(int size, int value) { byte[] out = new byte[size]; Arrays.fill(out, (byte) value); return out; }
    private static byte[] pattern(int size, int seed) {
        byte[] out = new byte[size]; for (int i = 0; i < size; i++) out[i] = (byte) (seed + i * 37); return out;
    }
    private static void putWord(byte[] bytes, int offset, int value) { bytes[offset] = (byte) (value >>> 8); bytes[offset + 1] = (byte) value; }
    private static void putLong(byte[] bytes, int offset, int value) { putWord(bytes, offset, value >>> 16); putWord(bytes, offset + 2, value); }
    private static void words(byte[] bytes, int offset, int count, int value) { for (int i = 0; i < count; i++) putWord(bytes, offset + i * 2, value); }
    private static void lines(byte[] bytes, int line, int count, int value) { for (int i = 0; i < count; i++) putLong(bytes, (line + i) * 4, value); }
    private static void cameras(State actual, int... expected) {
        assertEquals(6, expected.length);
        assertEquals(expected[0], actual.cameraBgX()); assertEquals(expected[1], actual.cameraBgY());
        assertEquals(expected[2], actual.cameraBg2X()); assertEquals(expected[3], actual.cameraBg2Y());
        assertEquals(expected[4], actual.cameraBg3X()); assertEquals(expected[5], actual.cameraBg3Y());
    }
    private static void assertValue(State expected, State actual) {
        cameras(actual, expected.cameraBgX(), expected.cameraBgY(), expected.cameraBg2X(), expected.cameraBg2Y(), expected.cameraBg3X(), expected.cameraBg3Y());
        assertEquals(expected.scrollFlagsBg(), actual.scrollFlagsBg()); assertEquals(expected.scrollFlagsBg2(), actual.scrollFlagsBg2());
        assertEquals(expected.scrollFlagsBg3(), actual.scrollFlagsBg3()); assertEquals(expected.vscroll(), actual.vscroll());
        assertArrayEquals(expected.crossingRam().copy(), actual.crossingRam().copy());
        assertArrayEquals(expected.cloudRam().copy(), actual.cloudRam().copy());
        assertArrayEquals(expected.deformationRam().copy(), actual.deformationRam().copy());
        assertArrayEquals(expected.hscrollRam().copy(), actual.hscrollRam().copy());
    }
}

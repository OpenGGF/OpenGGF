package com.openggf.game.soniccd.scroll;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAsset;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aProfile;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static com.openggf.game.soniccd.level.CdR11aAsset.*;
import static com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests authored and frozen BEFORE production in background-scroll-owner-codex-r1.
 * Source-only: not compiled or executed by the author. OpenAI Codex GPT-6.1 Sol.
 * Native pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, shipped FixBugs=0.
 * PP Scroll = src/Level/Palmtree Panic/Scroll (Act 1 Present).asm.
 * All default tables/RAM below are CONSTRUCTED FORMAT FIXTURES, not retail assets.
 * RLE expectations and closed-form water samples are independent of the owner's loops.
 */
class TestCdR11aBackgroundScroll {
    // Source-shaped constructed fixtures, PP Scroll CloudSectSizes:402-423 and WaterDeformSects:426-429.
    private static final byte[] TOP = {1, 3, 5, 7, 7, 7, 3, 5, 5, 3};
    private static final byte[] UPSIDE = {1, 3, 5, 7, 15, 3, 9, 3, 1};
    private static final byte[] WATER = {2, (byte) 0x80, 0, (byte) 0xE0,
            7, (byte) 0x80, 0, (byte) 0x80, 0x7F, (byte) 0xFF, 3, 0x60};

    @Test
    void initPreservesFullD0UpperBitsFractionsPaddingFlagsAndEveryUnwrittenBufferByte() {
        var before = state(0x11223344, 0x55667788, 0x99AABBCC, 0xDDEEFF11, 0x12345678, 0xABCDEF01);
        var after = init(before, new InitRegisters(0xA55A0710, 0xC33C0123, 0x7FF));
        assertEquals(0x00913344, after.cameraBgX());
        assertEquals(0x0710A55A, after.cameraBgY());
        assertEquals(0x0036BBCC, after.cameraBg2X());
        assertEquals(0x0710FF11, after.cameraBg2Y());
        assertEquals(0x006C5678, after.cameraBg3X());
        assertEquals(0x0710EF01, after.cameraBg3Y());
        byte[] expectedCloud = before.cloudRam().copy();
        Arrays.fill(expectedCloud, 0, 76, (byte) 0);
        assertArrayEquals(expectedCloud, after.cloudRam().copy());
        assertArrayEquals(before.crossingRam().copy(), after.crossingRam().copy());
        assertEquals(before.scrollFlagsBg(), after.scrollFlagsBg());
        assertEquals(before.scrollFlagsBg2(), after.scrollFlagsBg2());
        assertEquals(before.scrollFlagsBg3(), after.scrollFlagsBg3());
        assertArrayEquals(before.deformationRam().copy(), after.deformationRam().copy());
        assertArrayEquals(before.hscrollRam().copy(), after.hscrollRam().copy());
        assertEquals(before.vscroll(), after.vscroll());
        assertEquals(0x55667788, before.cameraBgY());
    }

    @Test
    void initUsesLogicalWordXShiftsAndPreservesOtherCameraFractions() {
        var after = init(state(0x11112222, 0, 0x33334444, 0x55556666, 0x77778888, 0x9999AAAA),
                new InitRegisters(0xDEAD0710, 0xBEEFFFFF, 0x800));
        assertEquals(0x7FFF2222, after.cameraBgX());
        assertEquals(0x2FFD4444, after.cameraBg2X());
        assertEquals(0x5FFD8888, after.cameraBg3X());
        assertEquals(0x0478DEAD, after.cameraBgY());
        assertEquals(0x04786666, after.cameraBg2Y());
        assertEquals(0x0478AAAA, after.cameraBg3Y());
    }

    @ParameterizedTest
    @CsvSource({"1808,2047,1808", "1808,2048,1144", "479,2048,479", "480,2048,480",
            "481,2048,480", "65535,65535,33007"})
    void initYUsesUnsignedPlayerXBorrowAndWordHalving(int y, int playerX, int expected) {
        assertEquals((expected << 16) | 0xABCD,
                init(state(0, 0, 0, 0, 0, 0), new InitRegisters(0xABCD0000 | y, 0, playerX)).cameraBgY());
    }

    @Test
    void auditRampLiteralsUseActualSignedXVelocityAndReturnAllThreeLongDeltas() {
        var result = fixture().step(state(0, 0x1234, 0, 0x5678, 0, 0x9ABC), input(0, 0, 0x600, 0x600, 0x600, 2));
        assertEquals(0x60000, result.rampOffset());
        assertEquals(0x90000, result.bgDelta());
        assertEquals(0xD2000, result.bg2Delta());
        assertEquals(0x6C000, result.bg3Delta());
        assertEquals(0x90000, result.state().cameraBgX());
        assertEquals(0xD2000, result.state().cameraBg2X());
        assertEquals(0x6C000, result.state().cameraBg3X());
        assertEquals(0x1234, result.state().cameraBgY());
        assertEquals(0x5678, result.state().cameraBg2Y());
        assertEquals(0x9ABC, result.state().cameraBg3Y());
    }

    @ParameterizedTest
    @CsvSource({"0,1536,0,196608,73728,49152", "1,1536,0,196608,73728,49152",
            "4,1536,0,196608,73728,49152", "2,0,0,0,0,0", "130,1536,393216,589824,860160,442368"})
    void rampOffsetRequiresOnlyBitOneAndNonzeroDiff(int control, int diff, int ramp, int bg, int bg2, int bg3) {
        var result = fixture().step(state(0, 0, 0, 0, 0, 0), input(0, 0, diff, 0x600, 0, control));
        assertEquals(ramp, result.rampOffset());
        assertEquals(bg, result.state().cameraBgX());
        assertEquals(bg2, result.state().cameraBg2X());
        assertEquals(bg3, result.state().cameraBg3X());
        assertEquals(0x00010000, result.state().cloudRam().unsignedLong(0) - state(0, 0, 0, 0, 0, 0).cloudRam().unsignedLong(0));
    }

    @Test
    void negativeDiffAndVelocityRemainSignedBeforeLongAdds() {
        var result = fixture().step(state(0, 0, 0, 0, 0, 0), input(0, 0, -0x600, -0x600, 0, 2));
        assertEquals(-0x60000, result.rampOffset());
        assertEquals(0xFFF70000, result.state().cameraBgX());
        assertEquals(0xFFF2E000, result.state().cameraBg2X());
        assertEquals(0xFFF94000, result.state().cameraBg3X());
    }

    @Test
    void oppositeVelocityAndDiffSignsAreIndependentInputs() {
        var result = fixture().step(state(0, 0, 0, 0, 0, 0), input(0, 0, 0x600, -0x600, 0, 2));
        assertEquals(-0x30000, result.state().cameraBgX());
        assertEquals(-0xAE000, result.state().cameraBg2X());
        assertEquals(-0x54000, result.state().cameraBg3X());
    }

    @Test
    void cameraLongAddsWrapAcrossSignedBoundaryAndRetainFractions() {
        var before = state(0x7FFFFFF0, 0, 0x7FFFFFF0, 0, 0x7FFFFFF0, 0);
        var plus = fixture().step(before, input(0, 0, 32767, 32767, 0, 2));
        assertEquals(0x80BFFE70, plus.state().cameraBgX());
        assertEquals(0x8117FDC0, plus.state().cameraBg2X());
        assertEquals(0x808FFED0, plus.state().cameraBg3X());
        var minus = fixture().step(before, input(0, 0, -32768, -32768, 0, 2));
        assertEquals(0x7F3FFFF0, minus.state().cameraBgX());
        assertEquals(0x7EE7FFF0, minus.state().cameraBg2X());
        assertEquals(0x7F6FFFF0, minus.state().cameraBg3X());
    }

    @Test
    void rightAndDownCrossingsMergeOnlyHighFlagBytesAndPreserveFourPaddingBytes() {
        var before = withCrossings(state(0x000F8000, 0x00001234, 0x000FD000, 0x5678, 0x000FE000, 0x9ABC),
                new byte[]{0x10, 0x10, 0x10, (byte) 0xAB, 0x10, (byte) 0xBC, (byte) 0xCD, (byte) 0xDE});
        var after = fixture().step(before, input(0, 0x10, 0x100, 0, 0, 0)).state();
        assertEquals(0xAA00, after.scrollFlagsBg());
        assertEquals(0, after.scrollFlagsBg2());
        assertEquals(0, after.scrollFlagsBg3());
        assertArrayEquals(new byte[]{0, 0, 0, (byte) 0xAB, 0, (byte) 0xBC, (byte) 0xCD, (byte) 0xDE}, after.crossingRam().copy());
        assertEquals(0x00101234, after.cameraBgY());
        assertEquals(0x00105678, after.cameraBg2Y());
        assertEquals(0x00109ABC, after.cameraBg3Y());
    }

    @Test
    void leftAndUpCrossingsUseSignedLongOrWordDifferenceAndNativeHighByteBits() {
        var before = withCrossings(state(0x00108000, 0x00201234, 0x00103000, 0, 0x00102000, 0),
                new byte[]{0x10, 0x10, 0x10, 7, 0x10, 8, 9, 10});
        var after = fixture().step(before, input(0, 0x10, -0x100, 0, 0, 0)).state();
        assertEquals(0x5500, after.scrollFlagsBg());
        assertEquals(0x00100000, after.cameraBgX());
        assertEquals(0x00100000, after.cameraBg2X());
        assertEquals(0x00100000, after.cameraBg3X());
    }

    @Test
    void crossingChecksRunAtZeroDeltaAndDoNotNormalizeDirtyCrossingBytes() {
        var zero = withCrossings(state(0, 0, 0, 0, 0, 0), new byte[8]);
        assertEquals(0xAA00, fixture().step(zero, input(0, 0, 0, 0, 0, 0)).state().scrollFlagsBg());
        var dirty = withCrossings(zero, new byte[]{(byte) 0x90, (byte) 0x90, (byte) 0x90, 1, (byte) 0x90, 2, 3, 4});
        var after = fixture().step(dirty, input(0, 0, 0, 0, 0, 0)).state();
        assertEquals(0, after.scrollFlagsBg());
        assertEquals(0, after.scrollFlagsBg2());
        assertEquals(0, after.scrollFlagsBg3());
        assertArrayEquals(dirty.crossingRam().copy(), after.crossingRam().copy());
    }

    @Test
    void allNineteenCloudLongsAdvanceOnceWithOverflowAndUnclearedTailRetained() {
        var before = state(0, 0, 0, 0, 0, 0);
        byte[] cloud = before.cloudRam().copy();
        var words = ByteBuffer.wrap(cloud);
        for (int i = 0; i < 19; i++) words.putInt(i * 4, 0xFFFFFF00);
        before = withRam(before, before.crossingRam(), bytes(cloud), before.deformationRam(), before.hscrollRam());
        var after = fixture().step(before, input(0, 0, 0, 0x600, 0, 2)).state();
        int[] expected = {0xFF00, 0xDF00, 0xBF00, 0x9F00, 0x7F00, 0x5F00, 0x4700, 0x3F00, 0x2700,
                0x1F00, 0x1F00, 0x3F00, 0x7F00, 0xBF00, 0xFF00, 0xBF00, 0x7F00, 0x3F00, 0x1F00};
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], (int) after.cloudRam().unsignedLong(i * 4), "cloud " + i);
        assertArrayEquals(Arrays.copyOfRange(cloud, 76, 512), Arrays.copyOfRange(after.cloudRam().copy(), 76, 512));
        assertEquals(0xFFFFFF00, (int) before.cloudRam().unsignedLong(0));
    }

    @ParameterizedTest
    @CsvSource({"1808,2047,1808", "1808,2048,1144", "0,2048,0", "479,2048,479", "480,2048,480", "481,2048,480"})
    void stepYHasExactRampThresholdBorrowFractionPreservationAndFinalVscroll(int y, int playerX, int expected) {
        var before = state(0, 0x00201234, 0, 0x00405678, 0, 0x00609ABC);
        var after = fixture().step(before, input(0x345, y, 0, 0, playerX, 0)).state();
        assertEquals((expected << 16) | 0x1234, after.cameraBgY());
        assertEquals((expected << 16) | 0x5678, after.cameraBg2Y());
        assertEquals((expected << 16) | 0x9ABC, after.cameraBg3Y());
        assertEquals((y << 16) | expected, after.vscroll());
    }

    @Test
    void fullDeformationRamHasIndependentlyExpandedTopUpsideDownBottomAndWaterRuns() {
        var before = scene();
        var result = fixture().step(before, input(0x110, 0, 0, 0, 0, 0));
        assertEquals(256, result.deformationWordsWritten());
        assertArrayEquals(expectedDeformation(), result.state().deformationRam().copy());
    }

    @ParameterizedTest
    @CsvSource({"0,240", "3,237", "864,240", "880,240", "1152,240", "1584,240"})
    void fullHscrollImageMatchesIndependentEightPixelSamplingAndPreservedUnusedTail(int y, int count) {
        var before = scene();
        var result = fixture().step(before, input(0x110, y, 0, 0, 0, 0));
        byte[] expected = before.hscrollRam().copy();
        var deform = ByteBuffer.wrap(expectedDeformation());
        var output = ByteBuffer.wrap(expected);
        for (int line = 0; line < count; line++) {
            output.putInt(line * 4, packed(0x110, deform.getShort(((y + line) / 8) * 2)));
        }
        assertEquals(count, result.hscrollLongsWritten());
        assertArrayEquals(expected, result.state().hscrollRam().copy());
    }

    @ParameterizedTest
    @CsvSource({"640,0,224,0,16,240", "641,0,223,1,24,247", "639,1,224,0,8,233",
            "863,0,1,223,240,241", "1808,112,120,0,0,232"})
    void fullWaterImagesRetainFixBugsZeroBlockAccountingRatherThanClampTo240(int y, int prefix,
            int waterLines, int depth, int suffix, int count) {
        var before = scene();
        var result = fixture().step(before, input(0x110, y, 0, 0, 0, 0));
        byte[] expected = before.hscrollRam().copy();
        var output = ByteBuffer.wrap(expected);
        var deform = ByteBuffer.wrap(expectedDeformation());
        for (int line = 0; line < prefix; line++) {
            output.putInt(line * 4, packed(0x110, deform.getShort(((y + line) / 8) * 2)));
        }
        // Independent rational form: trunc(256*(272-16)/224)=292, then 16.16 rate 292*256.
        for (int line = 0; line < waterLines; line++) {
            long fixed = (16L << 16) + (depth + (long) line) * 292 * 256;
            output.putInt((prefix + line) * 4, packed(0x110, -(int) (fixed >> 16)));
        }
        for (int line = 0; line < suffix; line++) {
            output.putInt((prefix + waterLines + line) * 4, packed(0x110, -16));
        }
        assertEquals(prefix + waterLines + suffix, count);
        assertEquals(count, result.hscrollLongsWritten());
        assertArrayEquals(expected, result.state().hscrollRam().copy());
    }

    @ParameterizedTest
    @CsvSource({"8,-9", "272,292", "32783,-256", "32784,0"})
    void waterUsesSignedTruncatingDivsAndUnchangedDividendOnQuotientOverflow(int fgX, int effectiveQuotient) {
        var before = scene();
        var result = fixture().step(before, input(fgX, 0x280, 0, 0, 0, 0));
        byte[] expected = before.hscrollRam().copy();
        var output = ByteBuffer.wrap(expected);
        for (int line = 0; line < 224; line++) {
            // Effective quotient is a literal derived separately for each DIVS case.
            long fixed = (16L << 16) + (long) line * effectiveQuotient * 256;
            output.putInt(line * 4, packed(fgX, -(int) (fixed >> 16)));
        }
        for (int line = 224; line < 240; line++) output.putInt(line * 4, packed(fgX, -16));
        assertEquals(240, result.hscrollLongsWritten());
        assertArrayEquals(expected, result.state().hscrollRam().copy());
    }

    @Test
    void constructedDifferentCloudSizesAreConsumedAndUnwrittenDeformationTailIsPreserved() {
        var owner = fromTables(bytes(new byte[10]), bytes(new byte[9]), bytes(WATER));
        var before = state(0x00100000, 0, 0x00200000, 0, 0x00300000, 0);
        before = withRam(before, before.crossingRam(), bytes(new byte[512]), before.deformationRam(), before.hscrollRam());
        var result = owner.step(before, input(0x110, 0, 0, 0, 0, 0));
        byte[] expected = before.deformationRam().copy();
        var out = ByteBuffer.wrap(expected);
        out.putShort((short) -49);
        for (int i = 1; i < 10; i++) out.putShort((short) -48);
        for (int i = 0; i < 20; i++) out.putShort((short) -32);
        for (int i = 0; i < 64; i++) out.putShort((short) -16);
        for (int i = 0; i < 20; i++) out.putShort((short) -32);
        for (int i = 0; i < 9; i++) out.putShort((short) (i == 4 ? -49 : -48));
        for (int i = 0; i < 20; i++) out.putShort((short) -32);
        for (int i = 0; i < 20; i++) out.putShort((short) -16);
        assertEquals(326, out.position());
        assertEquals(163, result.deformationWordsWritten());
        assertArrayEquals(expected, result.state().deformationRam().copy());
    }

    @Test
    void suppliedWaterTableControlsSectionSelectionWithoutSourceFallback() {
        byte[] changed = WATER.clone();
        changed[0] = 0x10; changed[1] = 0;
        changed[4] = 0x20; changed[5] = 0;
        var before = scene();
        var result = fromTables(bytes(TOP), bytes(UPSIDE), bytes(changed)).step(before, input(0x110, 0x280, 0, 0, 0, 0));
        byte[] expected = before.hscrollRam().copy();
        var output = ByteBuffer.wrap(expected);
        for (int line = 0; line < 240; line++) output.putInt(line * 4, packed(0x110, -16));
        assertEquals(240, result.hscrollLongsWritten());
        assertArrayEquals(expected, result.state().hscrollRam().copy());
    }

    @Test
    void scrollLockReturnsIdenticalStateBeforeFlagClearsCloudTicksOrBufferWrites() {
        var before = scene();
        var result = fixture().step(before, new Input(0x110, 0x7F8, 0x600, 0x600, 0x600, 2, 1));
        assertSame(before, result.state());
        assertEquals(Boundary.SCROLL_LOCKED, result.boundary());
        assertEquals(0, result.deformationWordsWritten());
        assertEquals(0, result.hscrollLongsWritten());
        assertEquals(0, result.rampOffset());
        assertEquals(0, result.bgDelta());
        assertEquals(0, result.bg2Delta());
        assertEquals(0, result.bg3Delta());
    }

    @Test
    void stateAndTablesRemainDetachedFromCallerArraysAndStepNeverMutatesInputRam() {
        byte[] source = pattern(512, 19);
        var owned = bytes(source);
        var before = state(0, 0, 0, 0, 0, 0);
        before = withRam(before, before.crossingRam(), owned, before.deformationRam(), before.hscrollRam());
        byte[] cloudBefore = owned.copy();
        byte[] deformBefore = before.deformationRam().copy();
        byte[] hscrollBefore = before.hscrollRam().copy();
        Arrays.fill(source, (byte) 0);
        var after = fixture().step(before, input(0, 0, 0, 0, 0, 0)).state();
        Arrays.fill(after.cloudRam().copy(), (byte) 0);
        Arrays.fill(after.hscrollRam().copy(), (byte) 0);
        assertArrayEquals(cloudBefore, before.cloudRam().copy());
        assertArrayEquals(deformBefore, before.deformationRam().copy());
        assertArrayEquals(hscrollBefore, before.hscrollRam().copy());
        assertNotEquals((int) before.cloudRam().unsignedLong(0), (int) after.cloudRam().unsignedLong(0));
        assertArrayEquals(Arrays.copyOfRange(cloudBefore, 76, 512), Arrays.copyOfRange(after.cloudRam().copy(), 76, 512));
    }

    @Test
    void viewBindingRequiresExactSelectorsAndShapesButDoesNotClaimToAuthenticateConstructedViews() {
        var profile = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        var top = new CdR11aAssets.View(CLOUD_SECT_SIZES, profile.range(CLOUD_SECT_SIZES), bytes(TOP));
        var upside = new CdR11aAssets.View(CLOUD_UPSIDE_DOWN_SECT_SIZES, profile.range(CLOUD_UPSIDE_DOWN_SECT_SIZES), bytes(UPSIDE));
        var water = new CdR11aAssets.View(WATER_DEFORM_SECTS, profile.range(WATER_DEFORM_SECTS), bytes(WATER));
        var result = fromViews(top, upside, water).step(scene(), input(0x110, 0, 0, 0, 0, 0));
        assertArrayEquals(expectedDeformation(), result.state().deformationRam().copy());
        assertFailure(Failure.VIEW_SELECTOR, () -> fromViews(upside, top, water));
        var wrongLength = new CdR11aAssets.View(CLOUD_SECT_SIZES, profile.range(CLOUD_SECT_SIZES), bytes(new byte[9]));
        assertFailure(Failure.TABLE_SHAPE, () -> fromViews(wrongLength, upside, water));
        var wrongRange = new CdR11aAssets.View(CLOUD_SECT_SIZES, profile.range(WATER_DEFORM_SECTS), bytes(TOP));
        assertFailure(Failure.TABLE_SHAPE, () -> fromViews(wrongRange, upside, water));
    }

    @Test
    void malformedTablesAndOversizedCloudExpansionHaveTypedFailures() {
        assertFailure(Failure.TABLE_SHAPE, () -> fromTables(bytes(new byte[9]), bytes(UPSIDE), bytes(WATER)));
        assertFailure(Failure.TABLE_SHAPE, () -> fromTables(bytes(TOP), bytes(new byte[10]), bytes(WATER)));
        assertFailure(Failure.TABLE_SHAPE, () -> fromTables(bytes(TOP), bytes(UPSIDE), bytes(new byte[11])));
        byte[] tooMany = TOP.clone(); tooMany[0] = (byte) 255;
        assertFailure(Failure.DEFORMATION_CAPACITY, () -> fromTables(bytes(tooMany), bytes(UPSIDE), bytes(WATER)));
    }

    @Test
    void malformedStateRamAndFlagWordsAreRejectedWithoutInventingDefaults() {
        var s = scene();
        assertFailure(Failure.STATE_RAM_SHAPE, () -> withRam(s, bytes(new byte[7]), s.cloudRam(), s.deformationRam(), s.hscrollRam()));
        assertFailure(Failure.STATE_RAM_SHAPE, () -> withRam(s, s.crossingRam(), bytes(new byte[76]), s.deformationRam(), s.hscrollRam()));
        assertFailure(Failure.STATE_RAM_SHAPE, () -> withRam(s, s.crossingRam(), s.cloudRam(), bytes(new byte[510]), s.hscrollRam()));
        assertFailure(Failure.STATE_RAM_SHAPE, () -> withRam(s, s.crossingRam(), s.cloudRam(), s.deformationRam(), bytes(new byte[960])));
        assertFailure(Failure.WORD_SHAPE, () -> new State(0, 0, 0, 0, 0, 0, s.crossingRam(), -1, 0, 0,
                s.cloudRam(), s.deformationRam(), s.hscrollRam(), 0));
    }

    @Test
    void explicitInputWordsBytesAndSignedVelocityCannotBeSilentlyNarrowed() {
        assertFailure(Failure.WORD_SHAPE, () -> input(-1, 0, 0, 0, 0, 0));
        assertFailure(Failure.WORD_SHAPE, () -> input(0, 65536, 0, 0, 0, 0));
        assertFailure(Failure.WORD_SHAPE, () -> input(0, 0, 0, 0, 65536, 0));
        assertFailure(Failure.SIGNED_WORD_SHAPE, () -> input(0, 0, 32768, 0, 0, 0));
        assertFailure(Failure.SIGNED_WORD_SHAPE, () -> input(0, 0, 0, -32769, 0, 0));
        assertFailure(Failure.BYTE_SHAPE, () -> input(0, 0, 0, 0, 0, 256));
        assertFailure(Failure.BYTE_SHAPE, () -> new Input(0, 0, 0, 0, 0, 0, 256));
        assertFailure(Failure.WORD_SHAPE, () -> new InitRegisters(0, 0, -1));
    }

    @Test
    void unownedAdjacentRamAndExhaustedSectionTableStopAtActualReadWithoutPartialResult() {
        var before = scene();
        byte[] original = before.hscrollRam().copy();
        var read = assertThrows(InvalidScroll.class, () -> fixture().step(before, input(0, 0x7F8, 0, 0, 0, 0)));
        assertEquals(Failure.DEFORMATION_READ, read.failure());
        assertEquals(512, read.offset());
        assertArrayEquals(original, before.hscrollRam().copy());
        var noSentinel = fromTables(bytes(TOP), bytes(UPSIDE), bytes(new byte[12]));
        var table = assertThrows(InvalidScroll.class, () -> noSentinel.step(before, input(0, 0, 0, 0, 0, 0)));
        assertEquals(Failure.WATER_TABLE_READ, table.failure());
        assertEquals(12, table.offset());
    }

    @Test
    void arithmeticCompletionExplicitlyLeavesNativeDrawingResidencyAndPublicationUnowned() {
        assertEquals(Set.of(UnownedBoundary.BG_TILE_STREAMING_WITH_UNBOUND_CAMERA_SECTIONS,
                UnownedBoundary.SECTION_ART_AND_PLC_SERVICE, UnownedBoundary.RENDER_AND_SPRITE_PUBLICATION), unownedBoundaries());
        assertEquals(Boundary.ARITHMETIC_COMPLETE,
                fixture().step(scene(), input(0, 0, 0, 0, 0, 0)).boundary());
    }

    /** Opt-in real authenticated asset binding only; deliberately NOT EXECUTED by this source-only task. */
    @Test
    void optInSuppliedUsaMediaBindsExistingViewsAndConsumesTheirBytesAfterClosingDisc() throws Exception {
        String configured = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(), "Set soniccd.media.root for supplied-media binding");
        Path cue;
        try (var files = Files.list(Path.of(configured).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size());
            cue = cues.getFirst();
        }
        CdR11aAssets assets;
        try (var disc = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            assets = CdR11aAssetLoader.load(disc, CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        }
        assertArrayEquals(TOP, assets.view(CLOUD_SECT_SIZES).bytes().copy());
        assertArrayEquals(UPSIDE, assets.view(CLOUD_UPSIDE_DOWN_SECT_SIZES).bytes().copy());
        assertArrayEquals(WATER, assets.view(WATER_DEFORM_SECTS).bytes().copy());
        var result = bind(assets).step(scene(), input(0x110, 0, 0, 0, 0, 0));
        assertArrayEquals(expectedDeformation(), result.state().deformationRam().copy());
        assertEquals(240, result.hscrollLongsWritten());
    }

    // Act2 source vectors, N1ecc5f3a Scroll (Act 2 Present).asm:153-177,212-368.
    // Appended source-only; no Java execution by this author. No ROM table or Act1 owner is needed.
    @Test
    void act2InitUsesFullSwappedArithmeticAndClearsOnlyThreeLongsInDeformationRam() {
        var before = state(0x11223344, 0x55667788, 0x99AABBCC, 0xDDEEFF11, 0x12345678, 0xABCDEF01);
        var after = initAct2Present(before, new InitRegisters(0xA55A0125, 0xC33C0123, 0xFFFF));
        byte[] deform = before.deformationRam().copy();
        Arrays.fill(deform, 0, 12, (byte) 0);
        var expected = new State(0x00913344, 0x00370EFF, 0x0036BBCC, 0x0037FF11,
                0x00245678, 0x0037EF01, before.crossingRam(), 0xF5A5, 0xA55A, 0x5AC3,
                before.cloudRam(), bytes(deform), before.hscrollRam(), 0x76543210);
        assertAct2StateValue(expected, after);
        assertSame(before.cloudRam(), after.cloudRam());
        assertSame(before.crossingRam(), after.crossingRam());
        assertSame(before.hscrollRam(), after.hscrollRam());
        assertEquals(0x55667788, before.cameraBgY());
        assertArrayEquals(pattern(512, 0x79), before.deformationRam().copy());
    }

    @Test
    void act2InitKeepsSignedAsrAndNativeWordShiftTruncationAtTheRawEdges() {
        var before = state(0x11112222, 0, 0x33334444, 0x55556666, 0x77778888, 0x9999AAAA);
        var after = initAct2Present(before, new InitRegisters(0x12348000, 0xBEEFFFFF, 0));
        assertEquals(0x7FFF2222, after.cameraBgX());
        assertEquals(0x2FFD4444, after.cameraBg2X());
        assertEquals(0x1FFF8888, after.cameraBg3X());
        assertEquals(0xE8000369, after.cameraBgY());
        assertEquals(0xE8006666, after.cameraBg2Y());
        assertEquals(0xE800AAAA, after.cameraBg3Y());
        var allBits = initAct2Present(before, new InitRegisters(-1, 0, 0xFFFF));
        assertEquals(0xFFFFFFFD, allBits.cameraBgY());
        assertEquals(0xFFFF6666, allBits.cameraBg2Y());
        assertEquals(0xFFFFAAAA, allBits.cameraBg3Y());
    }

    @Test
    void act2StepMatchesAllLiteralBandsFullBuffersAndEveryUnwrittenStateComponent() {
        var before = act2Scene(0);
        byte[] oldDeform = before.deformationRam().copy();
        byte[] oldHscroll = before.hscrollRam().copy();
        var result = stepAct2Present(before, input(0x110, 0x345, 0, 0x600, 0xFFFF, 2), 0);
        byte[] deform = act2ExpectedDeformation(before);
        byte[] hscroll = act2ExpectedHscroll(before, 0x110,
                new int[][]{{32, -51}, {32, -54}, {160, -48}}, 8, 32, 240);
        var expected = new State(0x00100091, 0x1234, 0x00200082, 0x5678, 0x00300073, 0x9ABC,
                bytes(new byte[]{0, 0x10, 0x10, 0x23, 0, 0x45, 0x67, (byte) 0x89}),
                0x8800, 0, 0, before.cloudRam(), bytes(deform), bytes(hscroll), 0x03450000);
        assertAct2StateValue(expected, result.state());
        assertEquals(Boundary.ARITHMETIC_COMPLETE, result.boundary());
        assertEquals(36, result.deformationWordsWritten());
        assertEquals(232, result.hscrollLongsWritten());
        assertEquals(0, result.rampOffset());
        assertEquals(0, result.bgDelta()); assertEquals(0, result.bg2Delta()); assertEquals(0, result.bg3Delta());
        assertSame(before.cloudRam(), result.state().cloudRam());
        assertArrayEquals(oldDeform, before.deformationRam().copy());
        assertArrayEquals(oldHscroll, before.hscrollRam().copy());
    }

    @Test
    void act2SignedXAndYDiffsRetainFractionsAndMergeAllRightDownHighByteFlags() {
        var before = state(0x000F8000, 0x000FD000, 0x000FD000, 0xABCD5678, 0x000FE000, 0xDCBA9ABC);
        var result = stepAct2Present(before, input(0x123, 0x456, 0x100, 32767, 0xFFFF, 0xFF), 0x100);
        var after = result.state();
        assertEquals(0x00100000, after.cameraBgX());
        assertEquals(0x00100000, after.cameraBg2X());
        assertEquals(0x00100000, after.cameraBg3X());
        assertEquals(0x00100000, after.cameraBgY());
        assertEquals(0x00105678, after.cameraBg2Y());
        assertEquals(0x00109ABC, after.cameraBg3Y());
        assertEquals(0xAA00, after.scrollFlagsBg());
        assertEquals(0, after.scrollFlagsBg2()); assertEquals(0, after.scrollFlagsBg3());
        assertArrayEquals(new byte[]{0, 0, 0, 0x23, 0, 0x45, 0x67, (byte) 0x89}, after.crossingRam().copy());
        assertEquals(0x04560010, after.vscroll());
        assertEquals(0x8000, result.bgDelta()); assertEquals(0x3000, result.bg2Delta());
        assertEquals(0x2000, result.bg3Delta()); assertEquals(0, result.rampOffset());
    }

    @Test
    void act2LeftUpChecksUseLongSubtractionAndPreservePaddingAndBg2Bg3YFractions() {
        var before = state(0x00108000, 0x00103000, 0x00103000, 0xABCD5678, 0x00102000, 0xDCBA9ABC);
        var after = stepAct2Present(before, input(0, 0, -0x100, -32768, 0, 2), -0x100).state();
        assertEquals(0x00100000, after.cameraBgX()); assertEquals(0x00100000, after.cameraBgY());
        assertEquals(0x00100000, after.cameraBg2X()); assertEquals(0x00100000, after.cameraBg3X());
        assertEquals(0x00105678, after.cameraBg2Y()); assertEquals(0x00109ABC, after.cameraBg3Y());
        assertEquals(0x5500, after.scrollFlagsBg());
        assertEquals(0, after.scrollFlagsBg2()); assertEquals(0, after.scrollFlagsBg3());
        assertArrayEquals(new byte[]{0, 0, 0, 0x23, 0, 0x45, 0x67, (byte) 0x89}, after.crossingRam().copy());
    }

    @Test
    void act2WordDiffExtremesAndLongWrapDoNotClampOrBorrowAct1RampVelocity() {
        var before = state(0x7FFFFFF0, 0x7FFFFFF0, 0x7FFFFFF0, 0x5678, 0x7FFFFFF0, 0x9ABC);
        var plus = stepAct2Present(before, input(0, 0, 32767, -32768, 0xFFFF, 2), -32768);
        assertEquals(0x803FFF70, plus.state().cameraBgX());
        assertEquals(0x8017FFC0, plus.state().cameraBg2X());
        assertEquals(0x800FFFD0, plus.state().cameraBg3X());
        assertEquals(0x7FE7FFF0, plus.state().cameraBgY());
        var minus = stepAct2Present(before, input(0, 0, -32768, 32767, 0xFFFF, 2), 32767);
        assertEquals(0x7FBFFFF0, minus.state().cameraBgX());
        assertEquals(0x7FE7FFF0, minus.state().cameraBg2X());
        assertEquals(0x7FEFFFF0, minus.state().cameraBg3X());
        assertEquals(0x8017FFC0, minus.state().cameraBgY());
        var wrap = state(0xFFFFFFF0, 0xFFFFF000, 0xFFFFFFF0, 0x1234, 0xFFFFFFF0, 0x5678);
        var wrapped = stepAct2Present(wrap, input(0, 0, 0x100, 0, 0, 0), 0x100).state();
        assertEquals(0x00007FF0, wrapped.cameraBgX());
        assertEquals(0x00002FF0, wrapped.cameraBg2X());
        assertEquals(0x00001FF0, wrapped.cameraBg3X());
        assertEquals(0x00002000, wrapped.cameraBgY());
        assertEquals(0x1234, wrapped.cameraBg2Y()); assertEquals(0x5678, wrapped.cameraBg3Y());
    }

    @Test
    void act2CloudCountersWrapInSharedDeformationPrefixWhileSeparateCloudRamNeverChanges() {
        var before = act2Scene(0);
        byte[] raw = before.deformationRam().copy();
        var buffer = ByteBuffer.wrap(raw);
        buffer.putInt(0, 0xFFFF8000).putInt(4, 0xFFFF8000).putInt(8, 0xFFFF8000);
        before = withRam(before, before.crossingRam(), before.cloudRam(), bytes(raw), before.hscrollRam());
        var after = stepAct2Present(before, input(0, 0, 0, 0, 0, 0), 0).state();
        assertEquals(0x00008000L, after.deformationRam().unsignedLong(0));
        assertEquals(0x00004000L, after.deformationRam().unsignedLong(4));
        assertEquals(0, after.deformationRam().unsignedLong(8));
        assertSame(before.cloudRam(), after.cloudRam());
        assertArrayEquals(Arrays.copyOfRange(raw, 84, 512), Arrays.copyOfRange(after.deformationRam().copy(), 84, 512));
        assertArrayEquals(raw, before.deformationRam().copy());
    }

    @Test
    void act2ZeroDeltasStillCheckDirtyCrossingsAndClearAllThreeOldFlagWords() {
        var before = withCrossings(act2Scene(0), new byte[]{(byte) 0x90, (byte) 0x90, (byte) 0x90,
                0x23, (byte) 0x90, 0x45, 0x67, (byte) 0x89});
        var after = stepAct2Present(before, input(0, 0, 0, 0, 0, 0), 0).state();
        assertArrayEquals(before.crossingRam().copy(), after.crossingRam().copy());
        assertEquals(0, after.scrollFlagsBg()); assertEquals(0, after.scrollFlagsBg2()); assertEquals(0, after.scrollFlagsBg3());
        var clean = withCrossings(state(0, 0, 0, 0, 0, 0), new byte[8]);
        assertEquals(0xAA00, stepAct2Present(clean, input(0, 0, 0, 0, 0, 0), 0).state().scrollFlagsBg());
    }

    @Test
    void act2HscrollTraversesPartialCloudsWaterfallLastBandAndUnsignedBorrowWithoutClamping() {
        int[] y = {3, 72, 280, 288, 511, 515};
        int[] counts = {229, 232, 232, 224, 224, 229};
        int[] interpolated = {8, 16, 224, 224, 224, 8};
        int[][][] runs = {{{29, -51}, {32, -54}, {160, -48}}, {{184, -48}, {32, -32}},
                {{8, -32}}, {}, {}, {{29, -51}, {32, -54}, {160, -48}}};
        for (int i = 0; i < y.length; i++) {
            var before = act2Scene(y[i]);
            var result = stepAct2Present(before, input(0x110, 0x345, 0, 0, 0, 0), 0);
            assertEquals(counts[i], result.hscrollLongsWritten(), "BG Y " + y[i]);
            assertArrayEquals(act2ExpectedHscroll(before, 0x110, runs[i], interpolated[i], 32, 240),
                    result.state().hscrollRam().copy(), "BG Y " + y[i]);
            assertArrayEquals(act2ExpectedDeformation(before), result.state().deformationRam().copy());
        }
    }

    @Test
    void act2InterpolationWrapsSubWordBeforeSignedDivsAndAccumulatesTheFullFraction() {
        var before = state(0x00100091, 0x01201234, 0x7FF00082, 0x5678, 0x00300073, 0x9ABC);
        var result = stepAct2Present(before, input(0x10, 0, 0, 0, 0, 0), 0);
        assertEquals(224, result.hscrollLongsWritten());
        assertArrayEquals(act2ExpectedHscroll(before, 0x10, new int[][]{}, 224, 0x7FF0, -32736),
                result.state().hscrollRam().copy());
        assertEquals(packed(0x10, -32752), ByteBuffer.wrap(result.state().hscrollRam().copy()).getInt(0));
        assertEquals(packed(0x10, -32624), ByteBuffer.wrap(result.state().hscrollRam().copy()).getInt(4));
        var minimum = state(0, 0x01200000, 0, 0, 0, 0);
        var minResult = stepAct2Present(minimum, input(0x8000, 0, 0, 0, 0, 0), 0);
        assertArrayEquals(act2ExpectedHscroll(minimum, 0x8000, new int[][]{}, 224, 0, -32768),
                minResult.state().hscrollRam().copy());
    }

    @Test
    void act2IgnoresAllAct1PlayerRampOperandsAndRepeatedPreparationCannotAdvanceTheInputState() {
        var before = act2Scene(0);
        var first = stepAct2Present(before, input(0x110, 0x345, 0x600, -32768, 0, 0), -0x100);
        var other = stepAct2Present(before, input(0x110, 0x345, 0x600, 32767, 0xFFFF, 0xFF), -0x100);
        assertAct2StateValue(first.state(), other.state());
        assertEquals(first.bgDelta(), other.bgDelta()); assertEquals(first.bg2Delta(), other.bg2Delta());
        assertEquals(first.bg3Delta(), other.bg3Delta()); assertEquals(0, first.rampOffset());
        assertAct2StateValue(first.state(), stepAct2Present(before,
                input(0x110, 0x345, 0x600, -32768, 0, 0), -0x100).state());
        assertEquals(0x0002FFFFL, before.deformationRam().unsignedLong(0));
    }

    @Test
    void act2OrdinaryForwardReverseContinuationUsesOnlyExplicitCompletedDeltas() {
        var initial = initAct2Present(state(0, 0, 0, 0, 0, 0), new InitRegisters(293, 0, 80));
        assertEquals(0x0036F000, initial.cameraBgY());
        var first = stepAct2Present(initial, input(1, 294, 0x100, 0, 80, 0), 0x100).state();
        assertEquals(0x00008000, first.cameraBgX()); assertEquals(0x00003000, first.cameraBg2X());
        assertEquals(0x00002000, first.cameraBg3X()); assertEquals(0x00372000, first.cameraBgY());
        var second = stepAct2Present(first, input(0, 293, -0x100, 0, 80, 0), -0x100).state();
        assertEquals(0, second.cameraBgX()); assertEquals(0, second.cameraBg2X()); assertEquals(0, second.cameraBg3X());
        assertEquals(0x0036F000, second.cameraBgY());
        assertEquals(0x00020000L, second.deformationRam().unsignedLong(0));
        assertEquals(0x00018000L, second.deformationRam().unsignedLong(4));
        assertEquals(0x00010000L, second.deformationRam().unsignedLong(8));
        assertSame(initial.cloudRam(), second.cloudRam());
        assertArrayEquals(Arrays.copyOfRange(initial.deformationRam().copy(), 84, 512),
                Arrays.copyOfRange(second.deformationRam().copy(), 84, 512));
    }

    @Test
    void act2ScrollLockReturnsActualSameStateBeforeAnyNativeWritesForEveryNonzeroByte() {
        var before = act2Scene(0);
        for (int lock : new int[]{1, 0x80, 0xFF}) {
            var result = stepAct2Present(before, new Input(0x110, 0x345, 32767, -32768, 0xFFFF, 0xFF, lock), 32767);
            assertSame(before, result.state()); assertEquals(Boundary.SCROLL_LOCKED, result.boundary());
            assertEquals(0, result.deformationWordsWritten()); assertEquals(0, result.hscrollLongsWritten());
            assertEquals(0, result.bgDelta()); assertEquals(0, result.bg2Delta()); assertEquals(0, result.bg3Delta());
            assertEquals(0, result.rampOffset());
        }
    }

    @Test
    void act2NewEntrypointsRejectNullAndNonSignedWordYWithoutTouchingExistingValues() {
        var before = act2Scene(0); byte[] prior = before.deformationRam().copy();
        assertThrows(NullPointerException.class, () -> initAct2Present(null, new InitRegisters(0, 0, 0)));
        assertThrows(NullPointerException.class, () -> initAct2Present(before, null));
        assertThrows(NullPointerException.class, () -> stepAct2Present(null, input(0, 0, 0, 0, 0, 0), 0));
        assertThrows(NullPointerException.class, () -> stepAct2Present(before, null, 0));
        assertFailure(Failure.SIGNED_WORD_SHAPE, () -> stepAct2Present(before, input(0, 0, 0, 0, 0, 0), -32769));
        assertFailure(Failure.SIGNED_WORD_SHAPE, () -> stepAct2Present(before, input(0, 0, 0, 0, 0, 0), 32768));
        assertArrayEquals(prior, before.deformationRam().copy());
    }

    private static State act2Scene(int bgY) {
        var s = state(0x00100091, (bgY << 16) | 0x1234, 0x00200082, 0x5678, 0x00300073, 0x9ABC);
        byte[] deform = s.deformationRam().copy();
        ByteBuffer.wrap(deform).putInt(0, 0x0002FFFF).putInt(4, 0x0005FFFF).putInt(8, 0xFFFFFF00);
        return withRam(s, s.crossingRam(), s.cloudRam(), bytes(deform), s.hscrollRam());
    }

    private static byte[] act2ExpectedDeformation(State before) {
        byte[] expected = before.deformationRam().copy();
        var output = ByteBuffer.wrap(expected);
        output.putInt(0, 0x0003FFFF).putInt(4, 0x0006BFFF).putInt(8, 0x00007F00);
        output.position(12);
        // Literal native scene: 4top/4middle/4bottom cloud WORDs,20mountain,4waterfall.
        for (int[] run : new int[][]{{4, -51}, {4, -54}, {24, -48}, {4, -32}}) {
            for (int i = 0; i < run[0]; i++) output.putShort((short) run[1]);
        }
        assertEquals(84, output.position());
        return expected;
    }

    private static byte[] act2ExpectedHscroll(State before, int foregroundX, int[][] literalRuns,
                                             int interpolationLines, int initialBg2Word, int signedQuotient) {
        byte[] expected = before.hscrollRam().copy();
        var output = ByteBuffer.wrap(expected);
        for (int[] run : literalRuns) {
            for (int i = 0; i < run[0]; i++) output.putInt(packed(foregroundX, run[1]));
        }
        // Independent closed form, not the production SWAP/add loop or source-table traversal.
        for (int line = 0; line < interpolationLines; line++) {
            long fixed = ((long) initialBg2Word << 16) + (long) line * signedQuotient * 256;
            output.putInt(packed(foregroundX, -(int) (fixed >> 16)));
        }
        return expected;
    }

    private static void assertAct2StateValue(State expected, State actual) {
        assertEquals(expected.cameraBgX(), actual.cameraBgX(), "cameraBgX");
        assertEquals(expected.cameraBgY(), actual.cameraBgY(), "cameraBgY");
        assertEquals(expected.cameraBg2X(), actual.cameraBg2X(), "cameraBg2X");
        assertEquals(expected.cameraBg2Y(), actual.cameraBg2Y(), "cameraBg2Y");
        assertEquals(expected.cameraBg3X(), actual.cameraBg3X(), "cameraBg3X");
        assertEquals(expected.cameraBg3Y(), actual.cameraBg3Y(), "cameraBg3Y");
        assertArrayEquals(expected.crossingRam().copy(), actual.crossingRam().copy(), "crossingRam");
        assertEquals(expected.scrollFlagsBg(), actual.scrollFlagsBg(), "scrollFlagsBg");
        assertEquals(expected.scrollFlagsBg2(), actual.scrollFlagsBg2(), "scrollFlagsBg2");
        assertEquals(expected.scrollFlagsBg3(), actual.scrollFlagsBg3(), "scrollFlagsBg3");
        assertArrayEquals(expected.cloudRam().copy(), actual.cloudRam().copy(), "cloudRam");
        assertArrayEquals(expected.deformationRam().copy(), actual.deformationRam().copy(), "deformationRam");
        assertArrayEquals(expected.hscrollRam().copy(), actual.hscrollRam().copy(), "hscrollRam");
        assertEquals(expected.vscroll(), actual.vscroll(), "vscroll");
    }

    private static CdR11aBackgroundScroll fixture() { return fromTables(bytes(TOP), bytes(UPSIDE), bytes(WATER)); }
    private static CdAssetBytes bytes(byte[] value) { return CdAssetBytes.copyOf(value); }
    private static Input input(int x, int y, int diff, int velocity, int playerX, int control) {
        return new Input(x, y, diff, velocity, playerX, control, 0);
    }
    private static State state(int x, int y, int x2, int y2, int x3, int y3) {
        return new State(x, y, x2, y2, x3, y3,
                bytes(new byte[]{0x10, 0x10, 0x10, 0x23, 0x10, 0x45, 0x67, (byte) 0x89}),
                0xF5A5, 0xA55A, 0x5AC3, bytes(pattern(512, 0x35)), bytes(pattern(512, 0x79)),
                bytes(pattern(1024, 0xBD)), 0x76543210);
    }
    private static State withRam(State s, CdAssetBytes crossings, CdAssetBytes clouds, CdAssetBytes deform, CdAssetBytes hscroll) {
        return new State(s.cameraBgX(), s.cameraBgY(), s.cameraBg2X(), s.cameraBg2Y(), s.cameraBg3X(), s.cameraBg3Y(),
                crossings, s.scrollFlagsBg(), s.scrollFlagsBg2(), s.scrollFlagsBg3(), clouds, deform, hscroll, s.vscroll());
    }
    private static State withCrossings(State s, byte[] crossings) {
        return withRam(s, bytes(crossings), s.cloudRam(), s.deformationRam(), s.hscrollRam());
    }
    private static State scene() {
        var s = state(0x00100091, 0x1234, 0x00200082, 0x5678, 0x00300073, 0x9ABC);
        byte[] clouds = s.cloudRam().copy();
        var buffer = ByteBuffer.wrap(clouds);
        for (int i = 0; i < 19; i++) buffer.putInt(i * 4, (((i + 1) * 3) << 16) | 0xFFFF);
        return withRam(s, s.crossingRam(), bytes(clouds), s.deformationRam(), s.hscrollRam());
    }
    private static byte[] pattern(int count, int seed) {
        byte[] data = new byte[count];
        for (int i = 0; i < count; i++) data[i] = (byte) (seed + i * 37);
        return data;
    }
    private static int packed(int fgX, int background) { return ((-fgX & 0xFFFF) << 16) | (background & 0xFFFF); }
    private static void assertFailure(Failure expected, org.junit.jupiter.api.function.Executable operation) {
        assertEquals(expected, assertThrows(InvalidScroll.class, operation).failure());
    }
    private static byte[] expectedDeformation() {
        // Independently derived 256-word picture: reversed table spans, camera words 48/32/16,
        // cloud integer after one update = 3*(index+1)+1. No production helper is called.
        int[][] runs = {{4,-52},{6,-55},{6,-58},{4,-61},{8,-64},{8,-67},{8,-70},{6,-73},{4,-76},{2,-79},
                {20,-32},{64,-16},{20,-32},{2,-82},{4,-85},{10,-88},{4,-91},{16,-94},{8,-97},
                {6,-100},{4,-103},{2,-106},{20,-32},{20,-16}};
        var output = ByteBuffer.allocate(512);
        for (int[] run : runs) for (int i = 0; i < run[0]; i++) output.putShort((short) run[1]);
        assertEquals(512, output.position());
        return output.array();
    }
}

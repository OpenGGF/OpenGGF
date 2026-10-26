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

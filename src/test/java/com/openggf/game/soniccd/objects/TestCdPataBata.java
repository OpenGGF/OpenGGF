package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.level.CdR11aPlayerAssets;
import com.openggf.game.soniccd.level.CdSonicVisualAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.player.CdPlayerMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdPataBata.NativeCall.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests authored and frozen with CdPataBata.java absent. NOT compiled or executed by this worker.
 * Source pin N = 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff; helper pin J =
 * 05c5c47c6d5c61a49109396d31d286f9147bf3ed. Native vectors cite Pata-Bata/Main.asm:7-80,
 * Functions (Misc).asm:69-76, Object Time Check.asm:12-44 and Object Spawner.asm:388-446.
 * Literal arithmetic vectors are source reasoning, not an independent native oracle/parity claim.
 * Constructed layouts, mappings, scripts and sparse sine bytes authenticate NO media.
 */
class TestCdPataBata {
    private static final int SLOT = 32;
    private static final int NORMAL_MAP = 0x203000; // Constructed pointer identities, not retail offsets.
    private static final int DAMAGED_MAP = 0x204000;
    private static final byte[] SCRIPTS = bytes(0, 4, 0, 11, 7, 0, 0, 1, 2, 1, 0xFF,
            3, 0, 0, 1, 2, 1, 0xFF);

    @Test
    void normalInitFallsThroughAndRetainsTheNegativeDeltaHighWordInYFraction() {
        var h = new Harness(0);
        var before = h.state();
        var result = h.run();
        var s = result.state();
        assertEquals(2, s.routine());
        assertEquals(0x00FF8000, s.longBits(X_POSITION));
        assertEquals(0x06FF1FFF, s.longBits(Y_POSITION)); // FE -> -12; FFF4FFFF ASR 3 + 07008000.
        assertEquals(0x0100, s.word(PATA_BATA_ANCHOR_X));
        assertEquals(0x07008000, s.longBits(PATA_BATA_ANCHOR_Y));
        assertEquals(-0x8000, s.longBits(PATA_BATA_X_DELTA));
        assertEquals(0xFE00, s.word(PATA_BATA_PHASE));
        assertEquals(0xFE00, s.word(PATA_BATA_PHASE_SPEED));
        assertEquals(3, s.word(PATA_BATA_SHIFT));
        assertEquals(0x23B0, s.word(TILE));
        assertEquals(NORMAL_MAP, s.longBits(MAPPINGS));
        assertEquals(0x2A, s.unsignedByte(COLLISION_TYPE));
        assertEquals(3, s.unsignedByte(PRIORITY));
        assertEquals(0x10, s.unsignedByte(X_RADIUS));
        assertEquals(0x10, s.unsignedByte(Y_RADIUS));
        assertEquals(0x10, s.unsignedByte(WIDTH));
        assertEquals(7, s.unsignedByte(ANIMATION_TIME));
        assertEquals(1, s.unsignedByte(ANIMATION_FRAME));
        assertEquals(0, s.unsignedByte(MAP_FRAME));
        assertEquals(successCalls(), result.effects());
        assertEquals(0x01000000, before.longBits(X_POSITION));
        assertSame(h.runtime.pool(), h.draw.pool);
        assertEquals(new CdObjectState.Identity(SLOT), h.draw.observed.getFirst().identity());
    }

    @Test
    void everyNonzeroSubtypeSelectsDamagedInitWithoutASeparateRoutineGate() {
        for (int subtype : new int[]{1, 2, 0x7F, 0x80, 0xFF}) {
            var h = new Harness(subtype);
            var s = h.run().state();
            assertEquals(0x00FFC000, s.longBits(X_POSITION));
            assertEquals(0x07002FFF, s.longBits(Y_POSITION)); // FF -> -6; FFFAFFFF ASR 4.
            assertEquals(-0x4000, s.longBits(PATA_BATA_X_DELTA));
            assertEquals(0xFF00, s.word(PATA_BATA_PHASE_SPEED));
            assertEquals(4, s.word(PATA_BATA_SHIFT));
            assertEquals(1, s.unsignedByte(ANIMATION));
            assertEquals(1, s.unsignedByte(PREVIOUS_ANIMATION));
            assertEquals(3, s.unsignedByte(ANIMATION_TIME));
            assertEquals(DAMAGED_MAP, s.longBits(MAPPINGS));
            assertEquals(subtype, s.subtype());
        }
    }

    @Test
    void dirtyInitPreservesEveryUnwrittenByteAndTheOriginalXFractionForBothForms() {
        for (int subtype : new int[]{0, 0xFF}) {
            var h = new Harness(subtype);
            var dirty = h.state();
            for (var f : CdObjectState.ByteField.values()) dirty = dirty.moveByte(f, 0xA5);
            for (var f : CdObjectState.WordField.values()) dirty = dirty.moveWord(f, 0xA55A);
            for (var f : CdObjectState.LongField.values()) dirty = dirty.moveLong(f, 0x89ABCDEF);
            dirty = dirty.moveByte(ID, 0x3E).moveByte(ROUTINE, 0).moveByte(SUBTYPE, subtype)
                    .moveByte(SUBTYPE2, 0).moveByte(SAVED_FLAGS_ID, 1)
                    .moveByte(SPRITE_FLAGS, 0xD2).moveByte(FLAGS, 0xA3)
                    .moveByte(PREVIOUS_ANIMATION, 0x77)
                    .moveLong(X_POSITION, 0x01004000).moveLong(Y_POSITION, 0x07001234)
                    .moveWord(PATA_BATA_PHASE, 0x1234);
            h.put(dirty);
            byte[] expected = dirty.copyBytes();
            expected[0x24] = 2; expected[1] = 0x57; expected[0x18] = 3; expected[0x20] = 0x2A;
            expected[0x17] = expected[0x19] = expected[0x16] = 0x10;
            word(expected, 0x2A, 0x100); longword(expected, 0x2C, 0x07008000);
            word(expected, 2, 0x23B0);
            longword(expected, 4, subtype == 0 ? NORMAL_MAP : DAMAGED_MAP);
            longword(expected, 0x30, subtype == 0 ? -0x8000 : -0x4000);
            word(expected, 0x36, subtype == 0 ? 0xFE00 : 0xFF00);
            word(expected, 0x38, subtype == 0 ? 3 : 4);
            expected[0x1C] = expected[0x1D] = (byte) (subtype == 0 ? 0 : 1);
            expected[0x1B] = 1; expected[0x1E] = (byte) (subtype == 0 ? 7 : 3); expected[0x1A] = 0;
            word(expected, 0x34, subtype == 0 ? 0x1034 : 0x1134);
            longword(expected, 8, subtype == 0 ? 0x00FFC000 : 0x01000000);
            longword(expected, 0x0C, subtype == 0 ? 0x07009FFF : 0x07008FFF);
            byte[] old = dirty.copyBytes();
            assertArrayEquals(expected, h.run().state().copyBytes());
            assertArrayEquals(old, dirty.copyBytes());
        }
    }

    @Test
    void all255NonzeroRoutineBytesEnterMainAndRetainUninitializedBodyFields() {
        for (int routine = 1; routine <= 255; routine++) {
            var h = new Harness(0);
            h.put(main(h.state()).moveByte(ROUTINE, routine).moveWord(TILE, 0xBEEF)
                    .moveLong(MAPPINGS, 0xDEADBEEF).moveByte(PRIORITY, 0xE5));
            var s = h.run().state();
            assertEquals(routine, s.routine());
            assertEquals(0xBEEF, s.word(TILE));
            assertEquals(0xDEADBEEF, s.longBits(MAPPINGS));
            assertEquals(0xE5, s.unsignedByte(PRIORITY));
            assertEquals(0, s.longBits(PATA_BATA_X_DELTA));
        }
    }

    @Test
    void calcSineReplacesOnlyD0LowWordAndSwapExposesThePriorFullDeltaHighWord() {
        int[][] cases = {
                {0x12345678, 0x1334, 0x13345678, 0x02069234},
                {0xFFFF8000, 0x00FF, 0x00FF8000, 0x02077FFF},
                {0x00008000, 0x0100, 0x01008000, 0x02068000},
                {0x80000000, 0x8100, 0x81000000, 0x02070000}
        };
        for (int[] c : cases) {
            var h = new Harness(0);
            h.put(main(h.state()).moveLong(PATA_BATA_X_DELTA, c[0])
                    .moveWord(PATA_BATA_ANCHOR_X, c[1]).moveWord(PATA_BATA_PHASE, 0x0100)
                    .moveWord(PATA_BATA_SHIFT, 0));
            // Keep the anchor inside its actual pre-scroll camera window; test register lanes, not despawn.
            var result = h.body.run(SLOT, new CdPataBata.Globals(0, 1, 0, c[1]));
            assertFalse(result.deleted());
            var s = result.state();
            assertEquals(c[2], s.longBits(X_POSITION));
            assertEquals(c[3], s.longBits(Y_POSITION));
        }
    }

    @Test
    void negativeSineUsesRegisterAsrCountsZero31_32_63AndDirtySixBitCounts() {
        int[][] cases = {{0, 0x01017FFF}, {31, 0x02007FFF}, {32, 0x02007FFF},
                {63, 0x02007FFF}, {64, 0x01017FFF}, {0x801F, 0x02007FFF},
                {0x8020, 0x02007FFF}, {0x803F, 0x02007FFF}, {0xFFC0, 0x01017FFF},
                {0xFFFF, 0x02007FFF}};
        for (int[] c : cases) {
            var h = new Harness(0);
            h.put(main(h.state()).moveLong(PATA_BATA_X_DELTA, -0x8000)
                    .moveWord(PATA_BATA_ANCHOR_X, 0xFF).moveWord(PATA_BATA_PHASE, 0xC000)
                    .moveWord(PATA_BATA_SHIFT, c[0]));
            assertEquals(c[1], h.run().state().longBits(Y_POSITION), "shift word " + c[0]);
        }
    }

    @Test
    void positiveSineSignFillsWithZeroInsteadOfJavaModulo32Shifts() {
        int[][] cases = {{0, 0x03009234}, {31, 0x02008000}, {32, 0x02008000},
                {63, 0x02008000}, {64, 0x03009234}, {0xFFFF, 0x02008000}};
        for (int[] c : cases) {
            var h = new Harness(0);
            h.put(main(h.state()).moveLong(PATA_BATA_X_DELTA, 0x12345678)
                    .moveWord(PATA_BATA_ANCHOR_X, 0x1334).moveWord(PATA_BATA_PHASE, 0x4000)
                    .moveWord(PATA_BATA_SHIFT, c[0]));
            // The large dirty delta/anchor is valid; match its real camera window to avoid native deletion.
            var result = h.body.run(SLOT, new CdPataBata.Globals(0, 1, 0, 0x1334));
            assertFalse(result.deleted());
            assertEquals(c[1], result.state().longBits(Y_POSITION));
        }
    }

    @Test
    void positiveReversalAddsNewDeltaOnceFlipsBeforeAnimationAndRestartsPhaseInSameDispatch() {
        var h = new Harness(0);
        h.put(main(h.state()).moveLong(X_POSITION, 0x017F8000).moveWord(PATA_BATA_ANCHOR_X, 0x100)
                .moveLong(PATA_BATA_X_DELTA, 0x8000).moveWord(PATA_BATA_PHASE, 0x4567)
                .moveWord(PATA_BATA_PHASE_SPEED, 0xFF00).moveWord(PATA_BATA_SHIFT, 3)
                .moveLong(PATA_BATA_ANCHOR_Y, 0x07008000));
        var s = h.run().state();
        assertEquals(0x017F8000, s.longBits(X_POSITION));
        assertEquals(-0x8000, s.longBits(PATA_BATA_X_DELTA));
        assertEquals(0xFF00, s.word(PATA_BATA_PHASE));
        assertEquals(0x06FFDFFF, s.longBits(Y_POSITION));
        assertEquals(1, s.flags() & 1);
        assertEquals(1, s.spriteFlags() & 1);
        assertEquals(1, h.draw.observed.getFirst().spriteFlags() & 1);
    }

    @Test
    void negativeReversalUsesTheReversedPositiveDeltaResidueAndNeverLoops() {
        var h = new Harness(0);
        h.put(main(h.state()).moveLong(X_POSITION, 0x00808000).moveWord(PATA_BATA_ANCHOR_X, 0x100)
                .moveLong(PATA_BATA_X_DELTA, -0x8000).moveWord(PATA_BATA_PHASE_SPEED, 0xFF00)
                .moveWord(PATA_BATA_SHIFT, 3).moveLong(PATA_BATA_ANCHOR_Y, 0x07008000));
        var s = h.run().state();
        assertEquals(0x00808000, s.longBits(X_POSITION));
        assertEquals(0x8000, s.longBits(PATA_BATA_X_DELTA));
        assertEquals(0x06FFC000, s.longBits(Y_POSITION));
        assertEquals(1, s.flags() & 1);
    }

    @Test
    void wrappedWordAbsoluteDifferenceUsesSignedBltIncludingUnchanged8000() {
        int[][] cases = {{0x00FF, 0x0080, 0}, {0x0100, 0x0080, 1}, {0x0080, 0x0100, 1},
                {0x8000, 0, 0}, {0, 0xFFFF, 0}, {0xFFFF, 0, 0}, {0, 0xFF80, 1}};
        for (int[] c : cases) {
            var h = new Harness(0);
            h.put(main(h.state()).moveWord(X, c[0]).moveWord(PATA_BATA_ANCHOR_X, c[1])
                    .moveWord(PATA_BATA_PHASE, 0x1234));
            h.draw.setBit7 = true; // Preserve far-anchor actors so this checks Main, not deletion.
            var s = h.run().state();
            assertEquals(c[0], s.centreX());
            assertEquals(c[2], s.flags() & 1);
            assertEquals(c[2] == 1 ? 0 : 0x1234, s.word(PATA_BATA_PHASE));
        }
    }

    @Test
    void minimumLongDeltaNegationWrapsAndTheSecondAddReturnsToTheOriginalX() {
        var h = new Harness(0);
        h.put(main(h.state()).moveLong(PATA_BATA_X_DELTA, Integer.MIN_VALUE)
                .moveWord(PATA_BATA_ANCHOR_X, 0x8000).moveWord(PATA_BATA_PHASE, 0x0100));
        h.draw.setBit7 = true;
        var s = h.run().state();
        assertEquals(0x01000000, s.longBits(X_POSITION));
        assertEquals(Integer.MIN_VALUE, s.longBits(PATA_BATA_X_DELTA));
        assertEquals(1, s.flags() & 1);
        assertEquals(0, s.word(PATA_BATA_PHASE));
    }

    @Test
    void phaseIsWordAdditionButTheSineAngleIsItsBigEndianHighByte() {
        int[][] cases = {{0x00FF, 1, 0x0100, 0x02068000}, {0x0000, 0xFFFF, 0xFFFF, 0x01FA8000},
                {0xFFFF, 1, 0, 0x02008000}, {0x4001, 0, 0x4001, 0x03008000}};
        for (int[] c : cases) {
            var h = new Harness(0);
            h.put(main(h.state()).moveWord(PATA_BATA_PHASE, c[0])
                    .moveWord(PATA_BATA_PHASE_SPEED, c[1]).moveWord(PATA_BATA_SHIFT, 0));
            var s = h.run().state();
            assertEquals(c[2], s.word(PATA_BATA_PHASE));
            assertEquals(c[3], s.longBits(Y_POSITION));
        }
    }

    @Test
    void suppliedSparseSineOwnerChangesTheActualReadAndIsImmutableAfterDecode() {
        var f = new MathFixture();
        f.sineWord(1, 37);
        var assets = assets(f.owner());
        f.sineWord(1, -99);
        var h = new Harness(0, assets);
        h.put(main(h.state()).moveWord(PATA_BATA_PHASE, 0x0100).moveWord(PATA_BATA_SHIFT, 0));
        assertEquals(0x02258000, h.run().state().longBits(Y_POSITION));
        var ordinary = new Harness(0);
        ordinary.put(main(ordinary.state()).moveWord(PATA_BATA_PHASE, 0x0100)
                .moveWord(PATA_BATA_SHIFT, 0));
        assertEquals(0x02068000, ordinary.run().state().longBits(Y_POSITION));
    }

    @Test
    void sharedNativeAnimationRunsBeforeDrawForBothCompleteLoopLifetimes() {
        for (int animation : new int[]{0, 1}) {
            var h = new Harness(0);
            h.put(main(h.state()).moveByte(ANIMATION, animation).moveByte(PREVIOUS_ANIMATION, 0x7F));
            int stride = animation == 0 ? 8 : 4;
            int[] expectedFrames = {0, 0, 1, 2, 1, 0};
            for (int dispatch = 1; dispatch <= 1 + stride * 5; dispatch++) {
                var result = h.run();
                var seen = h.draw.observed.getLast();
                assertEquals(result.state().unsignedByte(MAP_FRAME), seen.unsignedByte(MAP_FRAME));
                if ((dispatch - 1) % stride == 0) {
                    assertEquals(expectedFrames[(dispatch - 1) / stride], seen.unsignedByte(MAP_FRAME));
                    assertEquals(stride - 1, seen.unsignedByte(ANIMATION_TIME));
                }
            }
        }
    }

    @Test
    void initDoesNotResetAnUnchangedAnimationTimerIndexMapOrDirtyPhase() {
        var h = new Harness(0);
        h.put(h.state().moveByte(ANIMATION, 0).moveByte(PREVIOUS_ANIMATION, 0)
                .moveByte(ANIMATION_TIME, 5).moveByte(ANIMATION_FRAME, 0x77).moveByte(MAP_FRAME, 0xEE)
                .moveWord(PATA_BATA_PHASE, 0x0201));
        var s = h.run().state();
        assertEquals(4, s.unsignedByte(ANIMATION_TIME));
        assertEquals(0x77, s.unsignedByte(ANIMATION_FRAME));
        assertEquals(0xEE, s.unsignedByte(MAP_FRAME));
        assertEquals(1, s.word(PATA_BATA_PHASE));
        assertEquals(0x07009FFF, s.longBits(Y_POSITION)); // Angle 00, NOT low-byte 01.
    }

    @Test
    void tileSubtype2UsesTheWholeByteAndMayReadTheNextNativeSelectorBody() {
        var a = assets(new MathFixture().owner());
        byte[] table = a.objectTileIds().copy();
        word(table, 2, 38); word(table, 38, 0x1234); word(table, 40, 0x5678);
        var h = new Harness(0, withTiles(a, table));
        h.put(h.state().moveByte(SUBTYPE2, 1));
        assertEquals(0x5678, h.run().state().word(TILE));
        var high = new Harness(0, withTiles(a, table));
        high.put(high.state().moveByte(SUBTYPE2, 0x80));
        var failure = assertThrows(CdPataBata.UnsupportedPataBata.class, high::run);
        assertEquals(CdPataBata.UnsupportedBoundary.SET_OBJECT_TILE_ID, failure.boundary());
        assertEquals(2, high.state().routine());
    }

    @Test
    void selectedTileReadFailureKeepsOnlyTheCompletedInitPrefix() {
        var a = assets(new MathFixture().owner());
        byte[] malformed = a.objectTileIds().copy(); word(malformed, 2, 0x7FFF);
        var h = new Harness(0, withTiles(a, malformed));
        var before = h.state().moveWord(TILE, 0xBEEF).moveLong(MAPPINGS, 0x12345678)
                .moveLong(PATA_BATA_X_DELTA, 0x11223344).moveWord(PATA_BATA_PHASE, 0x55AA);
        h.put(before);
        var failure = assertThrows(CdPataBata.UnsupportedPataBata.class, h::run);
        byte[] expected = before.copyBytes();
        expected[0x24] = 2; expected[1] = 4; expected[0x18] = 3; expected[0x20] = 0x2A;
        expected[0x17] = expected[0x19] = expected[0x16] = 0x10;
        word(expected, 0x2A, 0x100); longword(expected, 0x2C, 0x07008000);
        assertArrayEquals(expected, h.state().copyBytes());
        assertEquals(SLOT, failure.wholeIndex());
        assertEquals(List.of(call(DESTROY_ON_GOOD_FUTURE)), failure.completedEffects());
        assertNotNull(failure.getCause());
        assertTrue(h.draw.observed.isEmpty());
    }

    @Test
    void unselectedMalformedTileDirectoryAndAllTileBytesOnMainAreUnreached() {
        var a = assets(new MathFixture().owner());
        byte[] table = new byte[74]; word(table, 2, 38); word(table, 38, 0xCAFE);
        var h = new Harness(0, withTiles(a, table));
        assertEquals(0xCAFE, h.run().state().word(TILE));
        var noTiles = new Harness(0, withTiles(a, new byte[0]));
        noTiles.put(main(noTiles.state()).moveByte(SUBTYPE2, 0xFF));
        assertDoesNotThrow(noTiles::run);
    }

    @Test
    void goodFutureConversionClearsAll64BytesRestoresOnlyWordsAndSkipsDrawDespawnAndFm() {
        var h = new Harness(0xFF);
        var s = h.state();
        for (var f : CdObjectState.LongField.values()) s = s.moveLong(f, 0xDEADBEEF);
        for (var f : CdObjectState.WordField.values()) s = s.moveWord(f, 0xCAFE);
        for (var f : CdObjectState.ByteField.values()) s = s.moveByte(f, 0xA5);
        s = s.moveByte(ID, 0x3E).moveByte(SUBTYPE, 0xFF).moveByte(SAVED_FLAGS_ID, 1)
                .moveByte(SPRITE_FLAGS, 0xFF).moveLong(X_POSITION, 0x1234ABCD)
                .moveLong(Y_POSITION, 0xFFB01234);
        h.put(s);
        var result = h.body.run(SLOT, new CdPataBata.Globals(1, 2, 0xFF, 0xFFFF));
        byte[] expected = new byte[64]; expected[0] = 0x18;
        word(expected, 8, 0x1234); word(expected, 0x0C, 0xFFB0);
        assertArrayEquals(expected, result.state().copyBytes());
        assertEquals(List.of(new CdPataBata.ExplosionConversion(0x1234, 0xFFB0)), result.effects());
        assertTrue(result.converted()); assertFalse(result.deleted());
        assertEquals(0x80, h.runtime.savedFlag(1, 1));
        assertTrue(h.draw.observed.isEmpty());
    }

    @Test
    void conversionPredicateUsesRawTimeZoneEqualityAndAnyNonzeroGoodFuture() {
        int[][] cases = {{0, 0xFF, 0xFF, 0}, {1, 1, 0, 0}, {0xFF, 1, 1, 1},
                {1, 0x81, 0, 1}, {1, 0, 0, 1}, {1, 2, 0, 1}, {1, 0xFF, 0, 1}};
        for (int[] c : cases) {
            var h = new Harness(c[2]);
            h.draw.setBit7 = true;
            var result = h.body.run(SLOT, new CdPataBata.Globals(c[0], c[1], 0, 0));
            assertEquals(c[3] == 1, result.converted());
            assertEquals(c[3] == 1 ? 0 : 1, h.draw.observed.size());
        }
    }

    @Test
    void conversionPrecedesMalformedTilesOddRoutineAnimationAndInvalidSavedBankAccess() {
        var a = assets(new MathFixture().owner());
        var h = new Harness(1, withTiles(a, new byte[0]));
        h.put(h.state().moveByte(ROUTINE, 0xFF).moveByte(ANIMATION, 0xFF)
                .moveByte(SAVED_FLAGS_ID, 0xFF).moveByte(SUBTYPE2, 0xFF));
        assertTrue(h.body.run(SLOT, new CdPataBata.Globals(1, 0xFF, 0x80, 0xFFFF)).converted());
        assertEquals(0x18, h.state().id());
        assertTrue(h.draw.observed.isEmpty());
    }

    @Test
    void drawReceivesActualPoolIdentityAndCompletedMotionAndAnimationBeforeAnchorDespawn() {
        var h = new Harness(0);
        h.put(main(h.state()).moveWord(PATA_BATA_ANCHOR_X, 0x600).moveByte(SPRITE_FLAGS, 0x84));
        var result = h.run();
        assertEquals(1, h.draw.observed.size());
        var seen = h.draw.observed.getFirst();
        assertSame(h.runtime.pool(), h.draw.pool);
        assertEquals(0x3E, seen.id());
        assertEquals(1, seen.unsignedByte(ANIMATION_FRAME));
        assertEquals(0x02008000, seen.longBits(Y_POSITION));
        assertTrue(result.deleted());
        assertArrayEquals(new byte[64], result.state().copyBytes());
        assertEquals(0, h.runtime.savedFlag(1, 1));
    }

    @Test
    void actualCpuDrawQueueClearsBit7AndRetainsPhysicalPointerThroughBodyDespawn() {
        var h = new Harness(0);
        h.put(main(h.state()).moveWord(PATA_BATA_ANCHOR_X, 0x600)
                .moveByte(SPRITE_FLAGS, 0x80).moveByte(PRIORITY, 3));
        var pool = h.runtime.pool();
        var queue = new CdObjectDrawQueue(new CdObjectDrawQueue.CdDrawSlots() {
            @Override public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref) {
                var s = pool.state(ref.wholeIndex());
                return new CdObjectDrawQueue.LiveDrawSlot(s.id(), s.spriteFlags(), s.centreX(), s.centreY(),
                        s.word(X_FRACTION), s.longBits(MAPPINGS), s.unsignedByte(MAP_FRAME), s.word(TILE));
            }
            @Override public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref, boolean value) {
                var s = pool.state(ref.wholeIndex());
                pool.write(s.moveByte(SPRITE_FLAGS, (s.spriteFlags() & 0x7F) | (value ? 0x80 : 0)));
            }
        }, (address, single, frame) -> { fail("deleted physical pointer must skip mappings"); return null; },
                new byte[CdObjectDrawQueue.SAT_BYTE_SIZE]);
        queue.beginPass();
        var body = new CdPataBata(h.runtime, h.body.assets(), (actual, slot) -> {
            assertSame(pool, actual);
            var s = actual.state(slot.wholeIndex());
            assertEquals(CdObjectDrawQueue.EnqueueResult.QUEUED, queue.drawObject(
                    new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(slot.wholeIndex()),
                            s.spriteFlags(), s.centreX(), s.centreY(), s.unsignedByte(Y_RADIUS),
                            s.unsignedByte(WIDTH), s.unsignedByte(PRIORITY)),
                    new CdObjectDrawQueue.CameraWords(0, 0)));
        });
        assertTrue(body.run(SLOT, globals()).deleted());
        var camera = new CdObjectDrawQueue.CameraWords(0, 0);
        assertEquals(0, queue.finishAfterCamera(new CdObjectDrawQueue.DrawCameras(camera, camera, camera)).spriteCount());
        assertArrayEquals(new byte[64], pool.state(SLOT).copyBytes());
    }

    @Test
    void despawnReadsPostDrawBit7RatherThanRetainingThePreCallImage() {
        var keep = new Harness(0); keep.draw.setBit7 = true;
        keep.put(main(keep.state()).moveWord(PATA_BATA_ANCHOR_X, 0x600));
        assertFalse(keep.run().deleted());
        var remove = new Harness(0);
        remove.put(main(remove.state()).moveWord(PATA_BATA_ANCHOR_X, 0x600).moveByte(SPRITE_FLAGS, 0x80));
        assertTrue(remove.run().deleted());
    }

    @Test
    void anchorDespawnUsesUnsignedChunkDifferenceWithEqualityAt280Kept() {
        int[][] cases = {{0x200, 0, 0}, {0x280, 0, 1}, {0, 0x100, 1},
                {0xFF80, 0, 0}, {0, 0xFFFF, 0}};
        for (int[] c : cases) {
            var h = new Harness(0);
            h.put(main(h.state()).moveWord(PATA_BATA_ANCHOR_X, c[0]));
            assertEquals(c[2] == 1,
                    h.body.run(SLOT, new CdPataBata.Globals(0, 1, 0, c[1])).deleted());
        }
        var moving = new Harness(0);
        moving.put(main(moving.state()).moveWord(X, 0x600).moveWord(PATA_BATA_ANCHOR_X, 0));
        assertFalse(moving.run().deleted(), "moving X is not the wrapper anchor");
    }

    @Test
    void timeWarpUsesSignedDirectionOldPeriodClampAndClearsOnlyLoadedBit() {
        int[][] cases = {{0x81, 1, 0}, {0x81, 0xFF, 2}, {0x80, 1, 0}, {0x82, 0xFF, 2},
                {0x81, 0x80, 2}, {0x81, 0x7F, 0}, {0xFF, 0xFF, 2}, {0x81, 0, 1}};
        for (int[] c : cases) {
            var h = new Harness(0); h.draw.setBit7 = true;
            h.put(main(h.state()));
            for (int period = 0; period < 3; period++) {
                h.runtime.bsetSavedFlag(1, period, CdObjectPlacementRuntime.SavedBit.LOADED);
                h.runtime.bsetSavedFlag(1, period, CdObjectPlacementRuntime.SavedBit.MEMBER_2);
            }
            var result = h.body.run(SLOT, new CdPataBata.Globals(0, c[0], c[1], 0));
            assertTrue(result.deleted());
            assertTrue(result.effects().contains(new CdPataBata.Despawn(CdPataBata.DespawnEntry.TIME_WARP, 1, c[2])));
            for (int period = 0; period < 3; period++)
                assertEquals(period == c[2] ? 2 : 0x82, h.runtime.savedFlag(1, period));
        }
    }

    @Test
    void invalidRawPeriodIsUnreachedUntilAFlaggedDespawnAndSavedIdZeroSkipsIt() {
        var keep = new Harness(0); keep.draw.setBit7 = true;
        keep.put(main(keep.state()));
        assertFalse(keep.body.run(SLOT, new CdPataBata.Globals(0, 3, 0, 0)).deleted());
        var free = new Harness(0);
        free.put(main(free.state()).moveWord(PATA_BATA_ANCHOR_X, 0x600).moveByte(SAVED_FLAGS_ID, 0));
        assertTrue(free.body.run(SLOT, new CdPataBata.Globals(0, 3, 0, 0)).deleted());
        var flagged = new Harness(0);
        flagged.put(main(flagged.state()).moveWord(PATA_BATA_ANCHOR_X, 0x600));
        var failure = assertThrows(CdPataBata.UnsupportedPataBata.class,
                () -> flagged.body.run(SLOT, new CdPataBata.Globals(0, 3, 0, 0)));
        assertEquals(CdPataBata.UnsupportedBoundary.CHECK_OBJ_DESPAWN2, failure.boundary());
        assertEquals(successCalls().subList(0, 3), failure.completedEffects());
        assertEquals(0x3E, flagged.state().id());
        assertEquals(0x80, flagged.runtime.savedFlag(1, 1));
    }

    @Test
    void callbackFailurePreservesItsCommittedWritesAndTheCompletedNativePrefix() {
        var h = new Harness(0);
        var cause = new IllegalStateException("owned draw failure");
        var body = new CdPataBata(h.runtime, h.body.assets(), (pool, identity) -> {
            var s = pool.state(identity.wholeIndex());
            pool.write(s.moveByte(SPRITE_FLAGS, s.spriteFlags() | 0x80).moveByte(COLLISION_STATUS, 0x55));
            throw cause;
        });
        var failure = assertThrows(CdPataBata.UnsupportedPataBata.class, () -> body.run(SLOT, globals()));
        assertEquals(CdPataBata.UnsupportedBoundary.DRAW_OBJECT, failure.boundary());
        assertSame(cause, failure.getCause());
        assertEquals(successCalls().subList(0, 2), failure.completedEffects());
        assertEquals(0x55, h.state().unsignedByte(COLLISION_STATUS));
        assertEquals(0x84, h.state().spriteFlags());
        assertEquals(0x06FF1FFF, h.state().longBits(Y_POSITION));
        assertEquals(0x80, h.runtime.savedFlag(1, 1));
    }

    @Test
    void malformedAnimationFailureOccursAfterMotionButPublishesNoPartialAnimatorImage() {
        var a = assets(new MathFixture().owner());
        var bad = new CdObjectAnimation.AnimationTable(CdAssetBytes.copyOf(bytes(0, 4, 0, 100, 7, 0, 0xFF)), 0, 2);
        var h = new Harness(0, withAnimations(a, bad));
        h.put(main(h.state()).moveByte(ANIMATION, 1).moveByte(PREVIOUS_ANIMATION, 0x77)
                .moveByte(ANIMATION_TIME, 0x55).moveByte(ANIMATION_FRAME, 0x66).moveByte(MAP_FRAME, 0xEE)
                .moveLong(PATA_BATA_X_DELTA, -0x8000).moveWord(PATA_BATA_ANCHOR_X, 0xFF)
                .moveWord(PATA_BATA_PHASE, 0x0100).moveWord(PATA_BATA_SHIFT, 0));
        var failure = assertThrows(CdPataBata.UnsupportedPataBata.class, h::run);
        assertEquals(CdPataBata.UnsupportedBoundary.ANIMATE_OBJECT, failure.boundary());
        assertEquals(List.of(call(DESTROY_ON_GOOD_FUTURE)), failure.completedEffects());
        assertEquals(0x00FF8000, h.state().longBits(X_POSITION));
        assertEquals(0x02077FFF, h.state().longBits(Y_POSITION));
        assertEquals(0x77, h.state().unsignedByte(PREVIOUS_ANIMATION));
        assertEquals(0x55, h.state().unsignedByte(ANIMATION_TIME));
        assertEquals(0x66, h.state().unsignedByte(ANIMATION_FRAME));
        assertEquals(0xEE, h.state().unsignedByte(MAP_FRAME));
        assertTrue(h.draw.observed.isEmpty());
    }

    @Test
    void malformedAnimationIdCanRemainHeldWithoutAnUnreachedDirectoryRead() {
        var h = new Harness(0);
        h.put(main(h.state()).moveByte(ANIMATION, 0xFF).moveByte(PREVIOUS_ANIMATION, 0xFF)
                .moveByte(ANIMATION_TIME, 1).moveByte(MAP_FRAME, 0xEE));
        assertEquals(0xEE, h.run().state().unsignedByte(MAP_FRAME));
        assertThrows(CdPataBata.UnsupportedPataBata.class, h::run);
    }

    @Test
    void completedDrawWritesAreReloadedForTheAnchorAndSavedIdAtTheTail() {
        var h = new Harness(0);
        h.put(main(h.state()).moveWord(PATA_BATA_ANCHOR_X, 0x600));
        var body = new CdPataBata(h.runtime, h.body.assets(), (pool, identity) -> {
            var s = pool.state(identity.wholeIndex());
            pool.write(s.moveWord(PATA_BATA_ANCHOR_X, 0).moveByte(SAVED_FLAGS_ID, 0));
        });
        assertFalse(body.run(SLOT, globals()).deleted());
        assertEquals(0, h.state().word(PATA_BATA_ANCHOR_X));
        assertEquals(0, h.state().savedFlagsId());
        assertEquals(0x80, h.runtime.savedFlag(1, 1));
    }

    @Test
    void fullPoolAndSavedBankReplayIsDeterministicAndOldResultsRemainImmutable() {
        var h = new Harness(0);
        var cut = h.runtime.capture();
        var first = new ArrayList<CdPataBata.Result>();
        for (int i = 0; i < 160; i++) first.add(h.run());
        var poolBytes = h.runtime.capture().pool().slots().stream().map(CdObjectState::copyBytes).toList();
        byte[] bank = h.runtime.capture().savedFlags().copy();
        byte[] oldResult = first.getFirst().state().copyBytes();
        h.runtime.restore(cut);
        for (int i = 0; i < 160; i++) {
            var again = h.run();
            assertArrayEquals(first.get(i).state().copyBytes(), again.state().copyBytes());
            assertEquals(first.get(i).effects(), again.effects());
        }
        var end = h.runtime.capture();
        for (int i = 0; i < 96; i++) assertArrayEquals(poolBytes.get(i), end.pool().slots().get(i).copyBytes());
        assertArrayEquals(bank, end.savedFlags().copy());
        assertArrayEquals(oldResult, first.getFirst().state().copyBytes());
        byte[] exposed = first.getFirst().state().copyBytes(); exposed[0] = 0;
        assertEquals(0x3E, first.getFirst().state().id());
        assertThrows(UnsupportedOperationException.class, () -> first.getFirst().effects().clear());
    }

    @Test
    void completedDespawnMetadataIsNotADeferredRequestAndSeparateOwnersShareNoState() {
        var one = new Harness(0); var two = new Harness(0);
        one.put(main(one.state()).moveWord(PATA_BATA_ANCHOR_X, 0x600));
        var result = one.run();
        assertTrue(result.deleted());
        assertEquals(0, one.runtime.savedFlag(1, 1));
        one.runtime.bsetSavedFlag(1, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
        assertTrue(result.deleted()); // Reading metadata performs no second bank write.
        assertEquals(0x80, one.runtime.savedFlag(1, 1));
        assertEquals(0x3E, two.state().id());
        assertEquals(0x80, two.runtime.savedFlag(1, 1));
        assertTrue(one.runtime.sourceIdentity().isEmpty());
        assertTrue(two.runtime.sourceIdentity().isEmpty());
    }

    @Test
    void exactInputWidthsAndMandatoryOwnersFailWithoutDispatchWrites() {
        var h = new Harness(0); byte[] before = h.state().copyBytes();
        assertThrows(IllegalArgumentException.class, () -> new CdPataBata.Globals(256, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdPataBata.Globals(0, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdPataBata.Globals(0, 1, 256, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdPataBata.Globals(0, 1, 0, 65536));
        assertDoesNotThrow(() -> new CdPataBata.Globals(255, 255, 255, 65535));
        assertThrows(NullPointerException.class, () -> new CdPataBata(null, h.body.assets(), h.draw));
        assertThrows(NullPointerException.class, () -> new CdPataBata(h.runtime, null, h.draw));
        assertThrows(NullPointerException.class, () -> new CdPataBata(h.runtime, h.body.assets(), null));
        assertThrows(NullPointerException.class, () -> h.body.run(SLOT, null));
        assertArrayEquals(before, h.state().copyBytes());
        h.put(h.state().moveByte(ID, 0x3D)); before = h.state().copyBytes();
        assertThrows(IllegalArgumentException.class, h::run);
        assertArrayEquals(before, h.state().copyBytes());
        assertTrue(h.draw.observed.isEmpty());
    }

    /** Declared only: Root owns actual supplied-media execution and positive evidence. */
    @Test
    void optionalUsaFactoryUsesTheActualObjectAndPlayerByteOwners() throws Exception {
        var level = loadUsa();
        var objects = level.openingObjects();
        var a = CdPataBata.Assets.fromObjectAssets(objects, level.player());
        assertSame(objects.pataBataNormal(), a.normal());
        assertSame(objects.pataBataDamaged(), a.damaged());
        assertArrayEquals(objects.view(CdR11aObjectAssets.Table.PATA_BATA_ANIMATIONS).bytes().copy(),
                a.animations().bytes().copy());
        assertArrayEquals(SCRIPTS, a.animations().bytes().copy());
        assertEquals(objects.view(CdR11aObjectAssets.Table.PATA_BATA_NORMAL_MAPPINGS).range().nativeAddress(),
                a.normalMappingsAddress());
        assertEquals(objects.view(CdR11aObjectAssets.Table.PATA_BATA_DAMAGED_MAPPINGS).range().nativeAddress(),
                a.damagedMappingsAddress());
        assertEquals(74, a.objectTileIds().length());
        assertEquals(3, a.normal().mappingFrames().size());
        assertEquals(3, a.damaged().mappingFrames().size());
        assertEquals(-12, a.math().calcSine(0xFE).sine());
        assertEquals(-6, a.math().calcSine(0xFF).sine());
        var h = new Harness(0, a);
        assertEquals(0x06FF1FFF, h.run().state().longBits(Y_POSITION));
        assertTrue(h.runtime.sourceIdentity().isEmpty(), "constructed placement never becomes authenticated");
    }

    private static CdObjectState main(CdObjectState s) {
        return s.moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 4).moveByte(FLAGS, 0)
                .moveWord(PATA_BATA_ANCHOR_X, 0x100).moveLong(PATA_BATA_ANCHOR_Y, 0x02008000)
                .moveLong(PATA_BATA_X_DELTA, 0).moveWord(PATA_BATA_PHASE, 0)
                .moveWord(PATA_BATA_PHASE_SPEED, 0).moveWord(PATA_BATA_SHIFT, 3);
    }

    private static CdPataBata.Globals globals() { return new CdPataBata.Globals(0, 1, 0, 0); }
    private static CdPataBata.Effect call(CdPataBata.NativeCall call) { return new CdPataBata.CallReturned(call); }
    private static List<CdPataBata.Effect> successCalls() {
        return List.of(call(DESTROY_ON_GOOD_FUTURE), call(ANIMATE_OBJECT), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2));
    }

    private static CdPataBata.Assets assets(CdPlayerMath math) {
        byte[] tiles = new byte[74];
        for (int i = 0; i < 18; i++) word(tiles, i * 2, 36);
        word(tiles, 36, 0x23B0);
        // Deliberately empty counted frames: format fixtures, not copied or authenticated native mappings.
        byte[] maps = bytes(0, 6, 0, 7, 0, 8, 0, 0, 0);
        var normal = CdObjectVisualAssets.decode(maps, 3, SCRIPTS, 2, 1);
        var damaged = CdObjectVisualAssets.decode(maps, 3, SCRIPTS, 2, 1);
        return new CdPataBata.Assets(CdAssetBytes.copyOf(tiles), normal, damaged,
                new CdObjectAnimation.AnimationTable(CdAssetBytes.copyOf(SCRIPTS), 0, 2),
                NORMAL_MAP, DAMAGED_MAP, math);
    }

    private static CdPataBata.Assets withTiles(CdPataBata.Assets a, byte[] tiles) {
        return new CdPataBata.Assets(CdAssetBytes.copyOf(tiles), a.normal(), a.damaged(), a.animations(),
                a.normalMappingsAddress(), a.damagedMappingsAddress(), a.math());
    }

    private static CdPataBata.Assets withAnimations(CdPataBata.Assets a, CdObjectAnimation.AnimationTable animations) {
        return new CdPataBata.Assets(a.objectTileIds(), a.normal(), a.damaged(), animations,
                a.normalMappingsAddress(), a.damagedMappingsAddress(), a.math());
    }

    private static final class Harness {
        final CdObjectPlacementRuntime runtime;
        final Draw draw = new Draw();
        final CdPataBata body;
        Harness(int subtype) { this(subtype, assets(new MathFixture().owner())); }
        Harness(int subtype, CdPataBata.Assets assets) {
            int nullAt = 28;
            var b = ByteBuffer.allocate(34);
            b.putShort((short) 12).putShort((short) nullAt).putShort((short) -1).putInt(0).putShort((short) 0);
            b.putShort((short) 0x100).putShort((short) 0x700).put((byte) 0xBE).put((byte) subtype)
                    .put((byte) 0xE0).put((byte) 0);
            b.putShort((short) -1).putInt(0).putShort((short) 0);
            b.putShort((short) -1).putInt(0);
            runtime = new CdObjectPlacementRuntime(CdObjectPlacements.decodeLayout(b.array()));
            runtime.spawnObjects(0, 1);
            body = new CdPataBata(runtime, assets, draw);
        }
        CdObjectState state() { return runtime.pool().state(SLOT); }
        void put(CdObjectState state) { runtime.pool().write(state); }
        CdPataBata.Result run() { return body.run(SLOT, globals()); }
    }

    /** Explicit callback fixture, not a substitute certification of the production draw owner. */
    private static final class Draw implements CdPataBata.DrawObjectCall {
        final List<CdObjectState> observed = new ArrayList<>();
        CdObjectPool pool;
        boolean setBit7;
        @Override public void drawObject(CdObjectPool actual, CdObjectState.Identity identity) {
            pool = actual;
            var s = actual.state(identity.wholeIndex()); observed.add(s);
            actual.write(s.moveByte(SPRITE_FLAGS, (s.spriteFlags() & 0x7F) | (setBit7 ? 0x80 : 0)));
        }
    }

    /** Existing player-math decoder fed sparse constructed words; no trig or generated sine curve. */
    private static final class MathFixture {
        final byte[] sine = new byte[640];
        MathFixture() {
            sineWord(1, 6); sineWord(0x40, 256); sineWord(0xC0, -256);
            sineWord(0xFE, -12); sineWord(0xFF, -6);
        }
        void sineWord(int at, int value) {
            word(sine, at * 2, value);
            if (at < 64) word(sine, (at + 256) * 2, value);
        }
        CdPlayerMath owner() {
            byte[] anim = new byte[578];
            for (int i = 0; i < 60; i++) word(anim, i * 2, 120);
            anim[122] = (byte) 0xFF;
            byte[] maps = new byte[398]; byte[] dplc = new byte[398];
            for (int i = 0; i < 198; i++) { word(maps, i * 2, 396); word(dplc, i * 2, 396); }
            var sonic = CdSonicVisualAssets.decode(maps, dplc, new byte[69472]);
            byte[] atan = new byte[257]; Arrays.fill(atan, 1, 257, (byte) 0x20);
            return new CdPlayerMath(CdR11aPlayerAssets.decode(anim, new byte[60],
                    bytes(1, 2, 3, 4, 5, 0xFF), new byte[4], sine, atan, sonic));
        }
    }

    private static CdR11aAssets loadUsa() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Root supplies soniccd.media.root for media checks");
        Path cue;
        try (var files = Files.list(Path.of(root).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "USA needs exactly one actual CUE"); cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            return CdR11aAssetLoader.load(source, CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        }
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) out[i] = (byte) values[i];
        return out;
    }
    private static void word(byte[] bytes, int at, int value) {
        bytes[at] = (byte) (value >>> 8); bytes[at + 1] = (byte) value;
    }
    private static void longword(byte[] bytes, int at, int value) {
        word(bytes, at, value >>> 16); word(bytes, at + 2, value);
    }
}

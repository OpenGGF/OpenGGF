package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Independently authored native instruction/branch vectors, not a 5256 replay or pixel proof.
 * Native pin: 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * Level/Palmtree Panic/Objects/Log Shadow/Main.asm:7-70 and Data/Mappings.asm:1-15;
 * Object Spawner.asm:385-446 and Object Functions.asm:137-196,228-237.
 * Fixtures are constructed through public owners; no private snapshot/RAM forgery.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdLogShadow {
    private static final int WHOLE = 47;
    private static final int MAP = 0x002089EE;
    // Literal native dc.w list (:60-64), not values calculated by the implementation.
    private static final byte[] BASE_WORDS = hex("03cb045e041803f00428038f0428037f");
    // Literal two counted frames (Mappings.asm:1-15); no animation or invented padding.
    private static final byte[] MAPS = hex("0004000a01f00f0000f001f80d0000f0");

    @Test void constructedPresentPlacementPublishesInitThenMainDrawAndSharedTail() {
        var placement = new CdObjectPlacementRuntime(layout(0x1548, 0x0088, 0x33, 0, 0x40, 0));
        placement.spawnObjects(0x1300, 1);
        var h = new Harness(placement, 32, assets());
        var before = h.slot();
        assertEquals(0x33, before.id());
        assertEquals(0x1548, before.centreX());
        assertEquals(0x0088, before.centreY());
        assertEquals(0, before.savedFlagsId());
        var result = h.run(globals(1, 0, 0, 0, 0x1300));
        assertArrayEquals(initOracle(before, MAP, 0x4418), h.draw.seen.getFirst().copyBytes());
        assertArrayEquals(initOracle(before, MAP, 0x4418), result.state().copyBytes());
        assertSame(h.pool(), h.draw.pools.getFirst());
        assertEquals(before.identity(), h.draw.identities.getFirst());
        assertEquals(initEffects(false), result.effects());
        assertFalse(result.deleted());
        // Camera/distance are constructed here; no partial failed5256 cut is inferred.
    }

    @Test void dirtyInitPreservesAll64BytesOutsideTheNativeWritesAndDrawBitClear() {
        var h = new Harness();
        h.write(dirty(h.slot()).moveByte(ID, 0x33).moveByte(ROUTINE, 0)
                .moveByte(SPRITE_FLAGS, 0xE3).moveByte(SAVED_FLAGS_ID, 0)
                .moveByte(SUBTYPE, 0xFF).moveByte(SUBTYPE2, 0xB7)
                .moveWord(X, 0x1000).moveWord(Y, 0x0200));
        var before = h.slot();
        var result = h.run(world());
        byte[] expected = initOracle(before, MAP, 0x4418);
        assertArrayEquals(expected, h.draw.seen.getFirst().copyBytes());
        expected[1] &= 0x7F;
        assertArrayEquals(expected, result.state().copyBytes(), "all 64 bytes, including zero unnamed $14/$15/$27");
        assertEquals(0x67, result.state().spriteFlags());
        assertEquals(0xFF, result.state().unsignedByte(MAP_FRAME));
        assertEquals(before.unsignedByte(ROUTINE_2), result.state().unsignedByte(ROUTINE_2));
        assertNotEquals(0, result.state().unsignedByte(COLLISION_TYPE));
        assertNotEquals(0, result.state().word(X_VELOCITY));
        assertNotEquals(0, result.state().word(Y_FRACTION));
    }

    @Test void initCopiesTheFullSubtypeByteAndRawMappingLongWithoutSelectingAFrame() {
        for (int subtype : new int[]{0, 1, 2, 31, 32, 128, 255}) {
            var h = new Harness(new CdObjectPlacementRuntime(layout(0x1000, 0x200, 0x33, 0, 0xE0, 0)),
                    WHOLE, new CdLogShadow.Assets(CdAssetBytes.copyOf(BASE_WORDS), visual(), 0xA12089EE));
            h.edit(s -> s.moveByte(SUBTYPE, subtype).moveByte(SUBTYPE2, 0xFF));
            var before = h.slot();
            var result = h.run(world());
            assertEquals(subtype, h.draw.seen.getFirst().unsignedByte(MAP_FRAME));
            assertEquals(0xA12089EE, result.state().longBits(MAPPINGS));
            assertArrayEquals(initOracle(before, 0xA12089EE, 0x4418), result.state().copyBytes());
            assertEquals(1, h.draw.seen.size());
        }
    }

    @Test void everyDeclaredBaseWordHasAnIndependentLiteralResult() {
        // timeZone, goodFuture, act, expected ORI.W result. Source :41-64.
        int[][] cases = {
                {0, 0, 0, 0x43CB}, {0, 0, 1, 0x445E},
                {1, 0, 0, 0x4418}, {1, 0, 1, 0x43F0},
                {2, 0, 0, 0x4428}, {2, 0, 1, 0x438F},
                {2, 1, 0, 0x4428}, {2, 1, 1, 0x437F}
        };
        for (int[] c : cases) {
            var h = new Harness();
            var result = h.run(globals(c[0], c[1], c[2], 0, 0x1080));
            assertEquals(c[3], result.state().word(TILE), List.of(c[0], c[1], c[2]).toString());
            assertEquals(c[3], h.draw.seen.getFirst().word(TILE));
        }
    }

    @Test void rawByteArithmeticMasksOnlyTimeZoneBit7AndNeverBooleanizesGoodFuture() {
        int[][] cases = {
                {0x80, 0xFF, 0, 0x43CB}, {0x81, 0xFF, 0, 0x4418},
                {0, 0xFF, 1, 0x445E}, {1, 0xFF, 0xFE, 0x43CB},
                {2, 0xFE, 0, 0x43CB}, {2, 0xFF, 0, 0x4418},
                {3, 0xFF, 0, 0x4428}, {3, 0xFF, 0xFA, 0x43CB},
                {0xFF, 0xFF, 2, 0x43CB}
        };
        for (int[] c : cases) {
            var h = new Harness();
            var result = h.run(globals(c[0], c[1], c[2], 0, 0x1080));
            assertEquals(c[3], h.draw.seen.getFirst().word(TILE), List.of(c[0], c[1], c[2]).toString());
            assertEquals((c[0] & 0x80) != 0, result.deleted());
        }
    }

    @Test void selectedWordKeepsEveryAttributeBitBeforeOring4000() {
        var h = new Harness(new CdObjectPlacementRuntime(layout(0x1000, 0x200, 0x33, 0, 0xE0, 0)),
                WHOLE, new CdLogShadow.Assets(CdAssetBytes.copyOf(hex("a987")), visual(), MAP));
        assertEquals(0xE987, h.run(globals(0, 0, 0, 0, 0x1080)).state().word(TILE));
    }

    @Test void shortAndOddViewsSucceedWhenTheReachedWordIsComplete() {
        int[][] cases = {{2, 0, 0, 0, 0x43CB}, {3, 0, 0, 0, 0x43CB},
                {6, 1, 0, 0, 0x4418}, {7, 1, 0, 0, 0x4418}, {15, 2, 0, 1, 0x438F}};
        for (int[] c : cases) {
            var h = withBase(java.util.Arrays.copyOf(BASE_WORDS, c[0]));
            assertEquals(c[4], h.run(globals(c[1], c[2], c[3], 0, 0x1080)).state().word(TILE));
        }
    }

    @Test void reachedShortOddAndOutOfDirectoryReadsKeepExactlyTheSevenStorePrefix() {
        // view length, raw timeZone, raw goodFuture, raw act, reached BYTE offset.
        // $1FE +.B 2 -> $100, then +.W itself -> $200, not zero or $400.
        int[][] cuts = {
                {0, 0, 0, 0, 0}, {1, 0, 0, 0, 0},
                {4, 1, 0, 0, 4}, {5, 1, 0, 0, 4}, {15, 2, 1, 1, 14},
                {18, 4, 0, 0, 16}, {1024, 2, 0xFD, 2, 0x200},
                {16, 2, 0x7E, 0, 0x200}, {16, 0x7F, 0, 0, 0x1FC}
        };
        for (int[] c : cuts) {
            var h = withBase(java.util.Arrays.copyOf(BASE_WORDS, c[0]));
            h.edit(s -> s.moveWord(TILE, 0xBA65).moveByte(SPRITE_FLAGS, 0xA3)
                    .moveByte(SUBTYPE, 0xEE).moveByte(SAVED_FLAGS_ID, 0xFF));
            var before = h.slot();
            byte[] bank = h.placement.capture().savedFlags().copy();
            var stop = assertThrows(CdLogShadow.UnsupportedLogShadow.class,
                    () -> h.run(globals(c[1], c[2], c[3], 0, 0x1080)));
            assertEquals(CdLogShadow.UnsupportedBoundary.BASE_TILE_ADDRESS, stop.boundary());
            assertEquals(WHOLE, stop.wholeIndex());
            assertEquals(c[4], stop.value());
            assertArrayEquals(prefixOracle(before, MAP), h.slot().copyBytes());
            assertEquals(0xBA65, h.slot().word(TILE));
            assertTrue(h.draw.seen.isEmpty());
            assertArrayEquals(bank, h.placement.capture().savedFlags().copy());
        }
    }

    @Test void mainNeverReadsInitSelectorsOrEvenAnEmptyBaseViewAndNeverReinitializesDirtyState() {
        var h = withBase(new byte[0]);
        h.write(dirty(h.slot()).moveByte(ID, 0x33).moveByte(ROUTINE, 2)
                .moveByte(SAVED_FLAGS_ID, 0xFF).moveByte(SPRITE_FLAGS, 0xE3).moveWord(X, 0x1000));
        var before = h.slot();
        var result = h.run(globals(0x7F, 0xFF, 0xFF, 0xFF, 0x1080));
        assertArrayEquals(before.copyBytes(), h.draw.seen.getFirst().copyBytes());
        byte[] expected = before.copyBytes(); expected[1] &= 0x7F;
        assertArrayEquals(expected, result.state().copyBytes());
        assertEquals(mainEffects(false), result.effects());
        assertFalse(result.deleted());
        assertArrayEquals(expected, h.run(globals(0, 0, 0, 0, 0x1080)).state().copyBytes());
        assertEquals(2, h.draw.seen.size());
    }

    @Test void unsupportedRoutineDirectoryReadsFailBeforeEveryBodyWriteAndDraw() {
        for (int routine : new int[]{1, 3, 4, 0xFE, 0xFF}) {
            var h = new Harness();
            h.edit(s -> s.moveByte(ROUTINE, routine).moveByte(SPRITE_FLAGS, 0xFF));
            var before = h.slot();
            var stop = assertThrows(CdLogShadow.UnsupportedLogShadow.class, () -> h.run(world()));
            assertEquals(CdLogShadow.UnsupportedBoundary.ROUTINE_DISPATCH, stop.boundary());
            assertEquals(routine, stop.value()); assertEquals(WHOLE, stop.wholeIndex());
            assertArrayEquals(before.copyBytes(), h.slot().copyBytes());
            assertTrue(h.draw.seen.isEmpty());
        }
    }

    @Test void entryRequiresTheRealDynamicSlotAndItsCurrentIdBeforeDispatch() {
        var h = new Harness(); h.edit(s -> s.moveByte(ID, 0));
        var before = h.slot();
        assertThrows(IllegalArgumentException.class, () -> h.run(world()));
        assertThrows(IndexOutOfBoundsException.class, () -> h.body.run(31, world()));
        assertThrows(IndexOutOfBoundsException.class, () -> h.body.run(128, world()));
        assertArrayEquals(before.copyBytes(), h.slot().copyBytes());
        assertTrue(h.draw.seen.isEmpty());
    }

    @Test void sharedDespawnUsesUnsignedWrappedChunkDistanceAndKeepsEquality() {
        // X values around the equality cut; chunk masking makes the distance a multiple of $80.
        int[][] cases = {
                {0x127F, 0x1080, 0}, {0x1280, 0x1080, 0}, {0x1281, 0x1080, 0},
                {0x1300, 0x1080, 1}, {0x0FFF, 0x1080, 1},
                {0, 0, 0}, {0x0200, 0, 0}, {0x0280, 0, 1}, {0xFFFF, 0x0080, 1}
        };
        for (int[] c : cases) {
            var h = new Harness(); h.main();
            h.edit(s -> s.moveWord(X, c[0]).moveByte(SPRITE_FLAGS, 4).moveByte(SAVED_FLAGS_ID, 0));
            var before = h.slot();
            var result = h.run(globals(0x7F, 0xFF, 0xFF, 0xFF, c[1]));
            assertEquals(c[2] != 0, result.deleted(), List.of(c[0], c[1]).toString());
            assertArrayEquals(c[2] != 0 ? new byte[64] : before.copyBytes(), result.state().copyBytes());
            assertEquals(1, h.draw.seen.size());
        }
    }

    @Test void postDrawNegativeSpriteBypassesDistanceButStillTakesRawWarpDeletion() {
        var h = new Harness(); h.main();
        h.edit(s -> s.moveWord(X, 0xFFFF).moveByte(SAVED_FLAGS_ID, 0xFF));
        h.draw.after = (pool, slot) -> pool.write(pool.state(slot.wholeIndex()).moveByte(SPRITE_FLAGS, 0x80));
        var result = h.run(globals(0x7F, 0xFF, 0xFF, 0xFF, 0x1080));
        assertFalse(result.deleted()); assertEquals(0x80, result.state().spriteFlags());
        assertEquals(0xFFFF, result.state().centreX());

        var warped = new Harness(); warped.main();
        warped.edit(s -> s.moveWord(X, 0xFFFF).moveByte(SAVED_FLAGS_ID, 0));
        warped.draw.after = h.draw.after;
        assertTrue(warped.run(globals(0xFF, 0xFF, 0xFF, 0x80, 0x1080)).deleted());
        assertArrayEquals(new byte[64], warped.slot().copyBytes());
    }

    @Test void ordinaryOutsideDeletionClearsOnlyTheSelectedLoadedBitInTheRealBank() {
        var h = new Harness(); h.main();
        h.write(dirty(h.slot()).moveByte(ID, 0x33).moveByte(ROUTINE, 2)
                .moveByte(SPRITE_FLAGS, 4).moveByte(SAVED_FLAGS_ID, 7).moveWord(X, 0x1300));
        for (int period = 0; period < 3; period++) mark(h.placement, 7, period, 0xFF);
        byte[] expectedBank = h.placement.capture().savedFlags().copy();
        expectedBank[24] = 0x7F; // 2 + 3*7 + Present1.
        var result = h.run(world());
        assertTrue(result.deleted());
        assertArrayEquals(new byte[64], h.slot().copyBytes());
        assertArrayEquals(expectedBank, h.placement.capture().savedFlags().copy());
        assertEquals(mainEffects(true), result.effects());
    }

    @Test void warpSubtractsSignedDirectionWithWordArithmeticAndClampsOnlyOnWarp() {
        int[][] cases = {
                {0x81, 1, 0}, {0x81, 0xFF, 2}, {0x80, 1, 0}, {0x82, 0xFF, 2},
                {0x81, 0, 1}, {0xFF, 0x80, 2}, {0x80, 0x80, 2}, {0x82, 0x7F, 0}
        };
        for (int[] c : cases) {
            var h = new Harness(); h.main();
            h.edit(s -> s.moveByte(SAVED_FLAGS_ID, 7));
            for (int period = 0; period < 3; period++) mark(h.placement, 7, period, 0xFF);
            byte[] expected = h.placement.capture().savedFlags().copy();
            expected[23 + c[2]] = 0x7F;
            h.draw.after = (pool, slot) -> pool.write(pool.state(slot.wholeIndex()).moveByte(SPRITE_FLAGS, 0x80));
            var result = h.run(globals(c[0], 0xFF, 0xFF, c[1], 0x1080));
            assertTrue(result.deleted());
            assertArrayEquals(expected, h.placement.capture().savedFlags().copy());
            assertArrayEquals(new byte[64], result.state().copyBytes());
        }
    }

    @Test void rawNonWarpPeriodSelectsItsNativeBankByteWithoutPeriodNormalization() {
        var h = new Harness(); h.main();
        h.edit(s -> s.moveWord(X, 0x1300).moveByte(SAVED_FLAGS_ID, 1));
        mark(h.placement, 43, 1, 0xFD);
        mark(h.placement, 1, 2, 0xFF);
        byte[] expected = h.placement.capture().savedFlags().copy();
        expected[132] = 0x7D; // 2 + 3*1 + $7F, publicly addressed as 43/1.
        assertTrue(h.run(globals(0x7F, 0, 0, 0x80, 0x1080)).deleted());
        assertArrayEquals(expected, h.placement.capture().savedFlags().copy());
    }

    @Test void lastActualBankByteIsAddressableIncludingARawPeriodAlias() {
        for (int[] c : new int[][]{{254, 1}, {253, 4}}) {
            var h = new Harness(); h.main();
            h.edit(s -> s.moveWord(X, 0x1300).moveByte(SAVED_FLAGS_ID, c[0]));
            mark(h.placement, 254, 1, 0xD5);
            byte[] expected = h.placement.capture().savedFlags().copy();
            assertEquals(766, expected.length);
            expected[765] = 0x55;
            assertTrue(h.run(globals(c[1], 0xFF, 0xFF, 0xFF, 0x1080)).deleted());
            assertArrayEquals(expected, h.placement.capture().savedFlags().copy());
        }
    }

    @Test void reachedSavedBankAddressStopsAfterDrawAndBeforeAnyBankClearOrDelete() {
        int[][] cuts = {
                {254, 2, 0, 766}, {255, 0, 0, 767}, {255, 1, 0, 768},
                {254, 0x7F, 0, 891}, {254, 0xFF, 0x80, 766}, {255, 0x81, 1, 767}
        };
        for (int[] c : cuts) {
            var h = new Harness(); h.main();
            h.edit(s -> s.moveWord(X, 0x1300).moveByte(SAVED_FLAGS_ID, c[0]));
            var before = h.slot(); byte[] bank = h.placement.capture().savedFlags().copy();
            h.draw.after = (pool, slot) -> pool.write(pool.state(slot.wholeIndex()).moveByte(COLLISION_STATUS, 0x71));
            var stop = assertThrows(CdLogShadow.UnsupportedLogShadow.class,
                    () -> h.run(globals(c[1], 0xFF, 0xFF, c[2], 0x1080)));
            assertEquals(CdLogShadow.UnsupportedBoundary.SAVED_FLAGS_ADDRESS, stop.boundary());
            assertEquals(c[3], stop.value()); assertEquals(WHOLE, stop.wholeIndex());
            byte[] expected = before.copyBytes(); expected[1] &= 0x7F; expected[0x21] = 0x71;
            assertArrayEquals(expected, h.slot().copyBytes());
            assertArrayEquals(bank, h.placement.capture().savedFlags().copy());
            assertEquals(1, h.draw.seen.size());
        }
    }

    @Test void initAndTileRemainPublishedWhenTheLaterSavedAddressIsUnsupported() {
        var h = new Harness();
        h.edit(s -> s.moveByte(SAVED_FLAGS_ID, 255).moveByte(SPRITE_FLAGS, 0x83).moveWord(X, 0x1300));
        var before = h.slot();
        h.draw.after = (pool, slot) -> pool.write(pool.state(slot.wholeIndex()).moveByte(COLLISION_STATUS, 0x72));
        var stop = assertThrows(CdLogShadow.UnsupportedLogShadow.class, () -> h.run(world()));
        assertEquals(CdLogShadow.UnsupportedBoundary.SAVED_FLAGS_ADDRESS, stop.boundary());
        assertEquals(768, stop.value());
        byte[] expected = initOracle(before, MAP, 0x4418);
        expected[1] &= 0x7F; expected[0x21] = 0x72;
        assertArrayEquals(expected, h.slot().copyBytes());
        assertEquals(1, h.draw.seen.size());
    }

    @Test void zeroSavedIdSkipsEvenAnOtherwiseUnsupportedRawPeriodAddress() {
        var h = new Harness(); h.main();
        h.edit(s -> s.moveWord(X, 0x1300).moveByte(SAVED_FLAGS_ID, 0));
        byte[] bank = h.placement.capture().savedFlags().copy();
        assertTrue(h.run(globals(0x7F, 0xFF, 0xFF, 0x80, 0x1080)).deleted());
        assertArrayEquals(bank, h.placement.capture().savedFlags().copy());
    }

    @Test void drawReceivesPublishedTileAndTailReloadsAllLiveMutationsWithoutIdRepair() {
        var h = new Harness();
        h.edit(s -> s.moveByte(SAVED_FLAGS_ID, 255)); // would fail if eagerly addressed.
        h.draw.after = (pool, slot) -> {
            assertSame(h.pool(), pool); assertEquals(WHOLE, slot.wholeIndex());
            assertEquals(0x4418, pool.state(WHOLE).word(TILE));
            pool.write(pool.state(WHOLE).moveByte(ID, 0x12).moveByte(ROUTINE, 0xA5)
                    .moveByte(SAVED_FLAGS_ID, 0).moveByte(SPRITE_FLAGS, 0x80)
                    .moveLong(MAPPINGS, 0xD1234567).moveWord(X, 0xFFFF));
        };
        var result = h.run(world());
        assertEquals(0x12, result.state().id()); assertEquals(0xA5, result.state().routine());
        assertEquals(0xD1234567, result.state().longBits(MAPPINGS));
        assertEquals(0xFFFF, result.state().centreX()); assertEquals(0x80, result.state().spriteFlags());
        assertFalse(result.deleted()); assertEquals(initEffects(false), result.effects());
    }

    @Test void liveXAfterDrawCanBothTriggerAndAvoidDespawn() {
        for (int[] c : new int[][]{{0x1000, 0x1300, 1}, {0x1300, 0x1000, 0}}) {
            var h = new Harness(); h.main(); h.edit(s -> s.moveWord(X, c[0]));
            h.draw.after = (pool, slot) -> pool.write(pool.state(slot.wholeIndex()).moveWord(X, c[1]));
            var result = h.run(world());
            assertEquals(c[2] != 0, result.deleted());
            assertEquals(c[2] != 0 ? 0 : c[1], result.state().centreX());
        }
    }

    @Test void tailUsesTheSavedIdWrittenByDrawAtItsOwnReachedCut() {
        var h = new Harness(); h.main(); h.edit(s -> s.moveByte(SAVED_FLAGS_ID, 255));
        mark(h.placement, 254, 1, 0xFF);
        byte[] expected = h.placement.capture().savedFlags().copy(); expected[765] = 0x7F;
        h.draw.after = (pool, slot) -> pool.write(pool.state(slot.wholeIndex())
                .moveByte(SAVED_FLAGS_ID, 254).moveWord(X, 0x1300));
        assertTrue(h.run(world()).deleted());
        assertArrayEquals(expected, h.placement.capture().savedFlags().copy());
    }

    @Test void callbackDeletionIsNotRepairedAndDeletedMetadataReportsOnlyTheCompletedTail() {
        var h = new Harness();
        h.draw.after = (pool, slot) -> pool.deleteObject(slot.wholeIndex());
        var result = h.run(globals(1, 0, 0, 0, 0x80));
        assertArrayEquals(new byte[64], result.state().copyBytes());
        assertFalse(result.deleted()); // zeroed X is in range; the tail itself did not delete.
        assertEquals(initEffects(false), result.effects());
    }

    @Test void drawRuntimeExceptionIdentityAndItsMutationPrefixSurviveWithoutTailWork() {
        var h = new Harness();
        h.edit(s -> s.moveByte(SAVED_FLAGS_ID, 255).moveByte(SPRITE_FLAGS, 0xE3));
        var before = h.slot(); byte[] bank = h.placement.capture().savedFlags().copy();
        var failure = new IllegalStateException("original draw failure");
        h.draw.after = (pool, slot) -> {
            pool.write(pool.state(slot.wholeIndex()).moveByte(COLLISION_STATUS, 0x74));
            throw failure;
        };
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> h.run(globals(0x81, 0, 0, 0, 0x1080))));
        byte[] expected = initOracle(before, MAP, 0x4418);
        expected[1] &= 0x7F; expected[0x21] = 0x74;
        assertArrayEquals(expected, h.slot().copyBytes());
        assertArrayEquals(bank, h.placement.capture().savedFlags().copy());
        assertEquals(1, h.draw.seen.size());
    }

    @Test void drawErrorIdentityAlsoSurvivesWithItsPublishedPrefixAndNoDeletion() {
        var h = new Harness(); h.edit(s -> s.moveByte(SAVED_FLAGS_ID, 255));
        var before = h.slot(); var failure = new AssertionError("original draw error");
        h.draw.after = (pool, slot) -> { throw failure; };
        assertSame(failure, assertThrows(AssertionError.class, () -> h.run(world())));
        assertArrayEquals(initOracle(before, MAP, 0x4418), h.slot().copyBytes());
        assertEquals(1, h.draw.seen.size());
    }

    @Test void unhandledSynchronousReentryStopsBeforeANestedDispatchAndPreservesOuterPrefix() {
        var h = new Harness(); h.edit(s -> s.moveByte(SPRITE_FLAGS, 0x83).moveByte(SAVED_FLAGS_ID, 255));
        var before = h.slot();
        h.draw.after = (pool, slot) -> h.body.run(slot.wholeIndex(), world());
        assertThrows(IllegalStateException.class, () -> h.run(world()));
        byte[] expected = initOracle(before, MAP, 0x4418); expected[1] &= 0x7F;
        assertArrayEquals(expected, h.slot().copyBytes());
        assertEquals(1, h.draw.seen.size());
    }

    @Test void aCaughtReentryDoesNotDispatchAndTheOuterRunCanFinishExactlyOnce() {
        var h = new Harness();
        h.draw.after = (pool, slot) ->
                assertThrows(IllegalStateException.class, () -> h.body.run(slot.wholeIndex(), world()));
        var result = h.run(world());
        assertEquals(initEffects(false), result.effects()); assertEquals(1, h.draw.seen.size());
    }

    @Test void transientGuardIsReleasedAfterAPortFailureWithoutRetainingSlotOrEffects() {
        var h = new Harness();
        var failure = new IllegalStateException("first constructed dispatch");
        h.draw.after = (pool, slot) -> { throw failure; };
        assertSame(failure, assertThrows(IllegalStateException.class, () -> h.run(world())));
        // Another constructed physical visit, not a resumption of a failed Runtime/world.
        h.pool().write(h.pool().state(48).moveByte(ID, 0x33).moveByte(ROUTINE, 2).moveWord(X, 0x1000));
        h.draw.after = (pool, slot) -> {};
        var result = h.body.run(48, world());
        assertEquals(48, result.state().identity().wholeIndex());
        assertEquals(mainEffects(false), result.effects()); assertEquals(2, h.draw.seen.size());
    }

    @Test void realSharedDrawCullClearsBit7AndDoesNotSpeculateAMappingRead() {
        var h = new Harness(); h.edit(s -> s.moveByte(SPRITE_FLAGS, 0xE3).moveWord(Y, 0x1000));
        var before = h.slot();
        var camera = new CdObjectDrawQueue.CameraWords(0x1000, 0x0200);
        var outcomes = new ArrayList<CdObjectDrawQueue.EnqueueResult>();
        try (var queue = queue(h.pool())) {
            queue.beginPass();
            var body = new CdLogShadow(h.placement, assets(), (pool, slot) ->
                    outcomes.add(queue.drawObject(cut(pool.state(slot.wholeIndex())), camera)));
            var result = body.run(WHOLE, globals(1, 0, 0, 0, 0x1000));
            assertEquals(List.of(CdObjectDrawQueue.EnqueueResult.CULLED), outcomes);
            byte[] expected = initOracle(before, MAP, 0x4418); expected[1] &= 0x7F;
            assertArrayEquals(expected, result.state().copyBytes());
            assertEquals(0, queue.finishAfterCamera(new CdObjectDrawQueue.DrawCameras(camera, camera, camera)).spriteCount());
        }
    }

    @Test void realSharedDrawFullBucketStillClearsBit7BeforeTheTail() {
        var h = new Harness(); h.edit(s -> s.moveByte(SPRITE_FLAGS, 0x83));
        h.pool().write(h.pool().state(48).moveByte(ID, 0x33).moveByte(PRIORITY, 6));
        var camera = new CdObjectDrawQueue.CameraWords(0x1000, 0x0200);
        var outcomes = new ArrayList<CdObjectDrawQueue.EnqueueResult>();
        try (var queue = queue(h.pool())) {
            queue.beginPass();
            for (int i = 0; i < 63; i++) {
                assertEquals(CdObjectDrawQueue.EnqueueResult.QUEUED, queue.drawObject(cut(h.pool().state(48)), camera));
            }
            var body = new CdLogShadow(h.placement, assets(), (pool, slot) ->
                    outcomes.add(queue.drawObject(cut(pool.state(slot.wholeIndex())), camera)));
            var result = body.run(WHOLE, globals(1, 0, 0, 0, 0x1000));
            assertEquals(List.of(CdObjectDrawQueue.EnqueueResult.BUCKET_FULL), outcomes);
            assertEquals(7, result.state().spriteFlags());
            assertEquals(0x4418, result.state().word(TILE)); assertFalse(result.deleted());
            // Explicit queue close; the filler mappings are deliberately outside this component check.
        }
    }

    @Test void realQueuedPhysicalPointerSeesTheSharedTailDeleteBeforeLaterBuild() {
        var h = new Harness();
        var camera = new CdObjectDrawQueue.CameraWords(0x1000, 0x0200);
        var outcomes = new ArrayList<CdObjectDrawQueue.EnqueueResult>();
        try (var queue = queue(h.pool())) {
            queue.beginPass();
            var body = new CdLogShadow(h.placement, assets(), (pool, slot) ->
                    outcomes.add(queue.drawObject(cut(pool.state(slot.wholeIndex())), camera)));
            var result = body.run(WHOLE, globals(0x81, 0, 0, 0, 0x1000));
            assertEquals(List.of(CdObjectDrawQueue.EnqueueResult.QUEUED), outcomes);
            assertTrue(result.deleted()); assertArrayEquals(new byte[64], result.state().copyBytes());
            var sat = queue.finishAfterCamera(new CdObjectDrawQueue.DrawCameras(camera, camera, camera));
            assertEquals(0, sat.spriteCount()); // resolver would fail if a stale occupant were used.
        }
    }

    @Test void effectsAreImmutableCompletedObservationsAndForgedMetadataCannotExecuteWork() {
        var h = new Harness();
        var result = h.run(world());
        var before = h.slot(); byte[] bank = h.placement.capture().savedFlags().copy();
        assertThrows(UnsupportedOperationException.class, () -> result.effects().add(new CdLogShadow.DespawnChecked(true)));
        var supplied = new ArrayList<CdLogShadow.Effect>();
        supplied.add(new CdLogShadow.DespawnChecked(true));
        var forged = new CdLogShadow.Result(before, supplied);
        supplied.clear();
        assertTrue(forged.deleted());
        assertEquals(1, forged.effects().size());
        assertArrayEquals(before.copyBytes(), h.slot().copyBytes());
        assertArrayEquals(bank, h.placement.capture().savedFlags().copy());
        assertEquals(1, h.draw.seen.size());
    }

    @Test void globalsValidateOnlyUnsignedWidthsWithoutNormalizingRawPeriodsOrActs() {
        assertDoesNotThrow(() -> globals(255, 255, 255, 255, 65535));
        for (int bad : new int[]{-1, 256}) {
            assertThrows(IllegalArgumentException.class, () -> globals(bad, 0, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> globals(0, bad, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> globals(0, 0, bad, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> globals(0, 0, 0, bad, 0));
        }
        for (int bad : new int[]{-1, 65536}) {
            assertThrows(IllegalArgumentException.class, () -> globals(0, 0, 0, 0, bad));
        }
    }

    @Test void assetShapeValidationDoesNotReadOrRequireTheBaseDirectory() {
        assertDoesNotThrow(() -> new CdLogShadow.Assets(CdAssetBytes.copyOf(new byte[0]), visual(), 0xF1234567));
        var oneFrame = CdObjectVisualAssets.decodeStatic(hex("000201f00f0000f0"), 1, 16);
        assertThrows(IllegalArgumentException.class,
                () -> new CdLogShadow.Assets(CdAssetBytes.copyOf(BASE_WORDS), oneFrame, MAP));
        var scripted = CdObjectVisualAssets.decode(MAPS, 2, hex("00020100ff"), 1, 16);
        assertThrows(IllegalArgumentException.class,
                () -> new CdLogShadow.Assets(CdAssetBytes.copyOf(BASE_WORDS), scripted, MAP));
        byte[] beyondArt = MAPS.clone(); beyondArt[8] = 1;
        var tooWide = CdObjectVisualAssets.decodeStatic(beyondArt, 2, 17);
        assertThrows(IllegalArgumentException.class,
                () -> new CdLogShadow.Assets(CdAssetBytes.copyOf(BASE_WORDS), tooWide, MAP));
        assertThrows(NullPointerException.class, () -> new CdLogShadow.Assets(null, visual(), MAP));
        assertThrows(NullPointerException.class, () -> new CdLogShadow.Assets(CdAssetBytes.copyOf(BASE_WORDS), null, MAP));
    }

    // Direct BYTE/WORD/LONG offsets from native Init :23-29; not production field constants.
    private static byte[] prefixOracle(CdObjectState before, int pointer) {
        byte[] b = before.copyBytes();
        b[0x24] += 2; b[1] |= 4; b[0x18] = 6;
        b[4] = (byte) (pointer >>> 24); b[5] = (byte) (pointer >>> 16);
        b[6] = (byte) (pointer >>> 8); b[7] = (byte) pointer;
        b[0x1A] = b[0x28]; b[0x19] = 0x10; b[0x16] = 0x10;
        return b;
    }

    private static byte[] initOracle(CdObjectState before, int pointer, int finalTile) {
        byte[] b = prefixOracle(before, pointer);
        b[2] = (byte) (finalTile >>> 8); b[3] = (byte) finalTile;
        return b;
    }

    private static List<CdLogShadow.Effect> initEffects(boolean deleted) {
        return List.of(new CdLogShadow.RoutineEntered(CdLogShadow.NativeRoutine.INIT),
                new CdLogShadow.RoutineEntered(CdLogShadow.NativeRoutine.MAIN),
                new CdLogShadow.CallReturned(CdLogShadow.NativeCall.DRAW_OBJECT),
                new CdLogShadow.DespawnChecked(deleted));
    }

    private static List<CdLogShadow.Effect> mainEffects(boolean deleted) {
        return List.of(new CdLogShadow.RoutineEntered(CdLogShadow.NativeRoutine.MAIN),
                new CdLogShadow.CallReturned(CdLogShadow.NativeCall.DRAW_OBJECT),
                new CdLogShadow.DespawnChecked(deleted));
    }

    private static CdLogShadow.Globals world() { return globals(1, 0, 0, 0, 0x1080); }
    private static CdLogShadow.Globals globals(int timeZone, int goodFuture, int act, int direction, int camera) {
        return new CdLogShadow.Globals(timeZone, goodFuture, act, direction, camera);
    }
    private static CdObjectVisualAssets visual() { return CdObjectVisualAssets.decodeStatic(MAPS, 2, 16); }
    private static CdLogShadow.Assets assets() {
        return new CdLogShadow.Assets(CdAssetBytes.copyOf(BASE_WORDS), visual(), MAP);
    }
    private static Harness withBase(byte[] bytes) {
        return new Harness(new CdObjectPlacementRuntime(layout(0x1000, 0x200, 0x33, 0, 0xE0, 0)),
                WHOLE, new CdLogShadow.Assets(CdAssetBytes.copyOf(bytes), visual(), MAP));
    }
    private static byte[] hex(String s) { return HexFormat.of().parseHex(s); }

    /** Public typed state writers only. No private constructor, reflection, raw RAM or capture import. */
    private static CdObjectState dirty(CdObjectState state) {
        for (var f : CdObjectState.LongField.values()) state = state.moveLong(f, 0xA5C37E19 ^ f.byteOffset() * 0x01010101);
        for (var f : CdObjectState.WordField.values()) state = state.moveWord(f, 0xD15B ^ f.byteOffset() * 0x0101);
        for (var f : CdObjectState.ByteField.values()) state = state.moveByte(f, 0x51 + f.byteOffset() * 7);
        return state;
    }

    private static void mark(CdObjectPlacementRuntime placement, int id, int period, int value) {
        var bits = CdObjectPlacementRuntime.SavedBit.values();
        for (int bit = 0; bit < 8; bit++) {
            if ((value & (1 << bit)) != 0) placement.bsetSavedFlag(id, period, bits[bit]);
        }
    }

    private static CdObjectPlacements layout(int x, int y, int id, int subtype, int periods, int subtype2) {
        var out = ByteBuffer.allocate(34);
        out.putShort((short) 12).putShort((short) 28).putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        out.putShort((short) x).putShort((short) y).put((byte) id).put((byte) subtype)
                .put((byte) periods).put((byte) subtype2);
        out.putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        out.putShort((short) 0xFFFF).putInt(0);
        return CdObjectPlacements.decodeLayout(out.array());
    }

    private static CdObjectDrawQueue.DrawCallCut cut(CdObjectState s) {
        return new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(s.identity().wholeIndex()),
                s.spriteFlags(), s.centreX(), s.centreY(), s.unsignedByte(Y_RADIUS),
                s.unsignedByte(WIDTH), s.unsignedByte(PRIORITY));
    }

    /** Uses the real shared CPU queue; no mapping/pixel or Runtime authority is supplied here. */
    private static CdObjectDrawQueue queue(CdObjectPool pool) {
        return new CdObjectDrawQueue(new CdObjectDrawQueue.CdDrawSlots() {
            @Override public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref) {
                var s = pool.state(ref.wholeIndex());
                return new CdObjectDrawQueue.LiveDrawSlot(s.id(), s.spriteFlags(), s.centreX(), s.centreY(),
                        s.word(X_FRACTION), s.longBits(MAPPINGS), s.unsignedByte(MAP_FRAME), s.word(TILE));
            }
            @Override public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref, boolean value) {
                var s = pool.state(ref.wholeIndex());
                pool.write(s.moveByte(SPRITE_FLAGS, (s.spriteFlags() & 0x7F) | (value ? 0x80 : 0)));
            }
        }, (pointer, staticPiece, frame) -> { throw new AssertionError("mapping must not be reached in this vector"); },
                new byte[CdObjectDrawQueue.SAT_BYTE_SIZE]);
    }

    private static final class Draw implements CdLogShadow.DrawObjectCall {
        final List<CdObjectState> seen = new ArrayList<>();
        final List<CdObjectPool> pools = new ArrayList<>();
        final List<CdObjectState.Identity> identities = new ArrayList<>();
        CdLogShadow.DrawObjectCall after = (pool, slot) -> {};
        @Override public void drawObject(CdObjectPool pool, CdObjectState.Identity slot) {
            var live = pool.state(slot.wholeIndex());
            seen.add(live); pools.add(pool); identities.add(slot);
            pool.write(live.moveByte(SPRITE_FLAGS, live.spriteFlags() & 0x7F));
            after.drawObject(pool, slot);
        }
    }

    private static final class Harness {
        final CdObjectPlacementRuntime placement;
        final int whole;
        final Draw draw = new Draw();
        final CdLogShadow body;
        Harness() {
            this(new CdObjectPlacementRuntime(layout(0x1000, 0x200, 0x33, 0, 0xE0, 0)), WHOLE, assets());
        }
        Harness(CdObjectPlacementRuntime placement, int whole, CdLogShadow.Assets assets) {
            this.placement = placement; this.whole = whole;
            if (slot().id() == 0) {
                write(slot().moveByte(ID, 0x33).moveWord(X, 0x1000).moveWord(Y, 0x200));
            }
            body = new CdLogShadow(placement, assets, draw);
        }
        CdObjectPool pool() { return placement.pool(); }
        CdObjectState slot() { return pool().state(whole); }
        void write(CdObjectState state) { pool().write(state); }
        void edit(UnaryOperator<CdObjectState> edit) { write(edit.apply(slot())); }
        void main() { edit(s -> s.moveByte(ROUTINE, 2)); }
        CdLogShadow.Result run(CdLogShadow.Globals globals) { return body.run(whole, globals); }
    }
}

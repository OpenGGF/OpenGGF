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
 * Constructed Scenery vectors with source-derived Init/arithmetic and shared Spawner/Draw controls.
 * Native pin: 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * Level/Palmtree Panic/Objects/Scenery/Main.asm:7-67 and Data/Mappings.asm:1-60;
 * Object Spawner.asm:385-446 and Object Functions.asm:137-196,228-237.
 * Fixtures are constructed through public owners; no private snapshot/RAM forgery.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdScenery {
    private static final int WHOLE = 47;
    private static final int MAP = 0x002086D8;
    // Literal native six-word list (:60-62), not values calculated by the implementation.
    private static final byte[] BASE_WORDS = hex("03db046e0438039f0438038f");
    // Literal three counted frames including actual explicit zero tails; no scripts.
    private static final byte[] MAPS = hex("00060012002802f4040000f0fc0d0002f00004ec04080000f409000af0f400001008040d0002f00004f000000004f8000800f40000000004f0020001fc00");

    @Test void constructedPresentPlacementPublishesInitThenMainDrawAndSharedTail() {
        var placement = new CdObjectPlacementRuntime(layout(0x1548, 0x0088, 0x32, 0, 0x40, 0));
        placement.spawnObjects(0x1300, 1);
        var h = new Harness(placement, 32, assets());
        var before = h.slot();
        assertEquals(0x32, before.id());
        assertEquals(0x1548, before.centreX());
        assertEquals(0x0088, before.centreY());
        assertEquals(0, before.savedFlagsId());
        var result = h.run(globals(1, 0, 0, 0, 0x1300));
        assertArrayEquals(initOracle(before, MAP, 0x4438), h.draw.seen.getFirst().copyBytes());
        assertArrayEquals(initOracle(before, MAP, 0x4438), result.state().copyBytes());
        assertSame(h.pool(), h.draw.pools.getFirst());
        assertEquals(before.identity(), h.draw.identities.getFirst());
        assertEquals(initEffects(false), result.effects());
        assertFalse(result.deleted());
        // Camera/distance are constructed here; no live World or route is inferred.
    }

    @Test void dirtyInitPreservesAll64BytesOutsideTheNativeWritesAndDrawBitClear() {
        var h = new Harness();
        h.write(dirty(h.slot()).moveByte(ID, 0x32).moveByte(ROUTINE, 0)
                .moveByte(SPRITE_FLAGS, 0xE3).moveByte(SAVED_FLAGS_ID, 0)
                .moveByte(SUBTYPE, 0xFF).moveByte(SUBTYPE2, 0xB7)
                .moveWord(X, 0x1000).moveWord(Y, 0x0200));
        var before = h.slot();
        var result = h.run(world());
        byte[] expected = initOracle(before, MAP, 0x4438);
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
            var h = new Harness(new CdObjectPlacementRuntime(layout(0x1000, 0x200, 0x32, 0, 0xE0, 0)),
                    WHOLE, new CdScenery.Assets(CdAssetBytes.copyOf(BASE_WORDS), visual(), 0xA12086D8));
            h.edit(s -> s.moveByte(SUBTYPE, subtype).moveByte(SUBTYPE2, 0xFF));
            var before = h.slot();
            var result = h.run(world());
            assertEquals(subtype, h.draw.seen.getFirst().unsignedByte(MAP_FRAME));
            assertEquals(0xA12086D8, result.state().longBits(MAPPINGS));
            assertArrayEquals(initOracle(before, 0xA12086D8, 0x4438), result.state().copyBytes());
            assertEquals(1, h.draw.seen.size());
        }
    }

    @Test void everyDeclaredBaseWordHasAnIndependentLiteralResult() {
        // Future d0 is RESET TO1 before ADD.B goodFuture; this is not LogShadow's2+goodFuture.
        int[][] cases={{0,0,0,0x43DB},{0,0,1,0x446E},{1,0,0,0x4438},{1,0,1,0x439F},
                {2,0,0,0x4438},{2,0,1,0x439F},{2,1,0,0x4438},{2,1,1,0x438F}};
        for(int[] c:cases){var h=new Harness();var result=h.run(globals(c[0],c[1],c[2],0,0x1080));assertEquals(c[3],result.state().word(TILE),List.of(c[0],c[1],c[2]).toString());assertEquals(c[3],h.draw.seen.getFirst().word(TILE));}
    }

    @Test void rawByteArithmeticMasksOnlyTimeZoneBit7AndNeverBooleanizesGoodFuture() {
        int[][] cases={{0x80,0xFF,0,0x43DB},{0x81,0xFF,0,0x4438},{0,0xFF,1,0x446E},
                {1,0xFF,0xFE,0x43DB},{2,0xFF,0,0x43DB},{2,0xFF,1,0x446E},
                {2,0,0xFE,0x43DB},{2,1,0xFC,0x43DB},{3,0xFF,0xFA,0x43DB},{0xFF,0xFF,2,0x43DB}};
        for(int[] c:cases){var h=new Harness();var result=h.run(globals(c[0],c[1],c[2],0,0x1080));assertEquals(c[3],h.draw.seen.getFirst().word(TILE),List.of(c[0],c[1],c[2]).toString());assertEquals((c[0]&0x80)!=0,result.deleted());}
    }

    @Test void selectedWordKeepsEveryAttributeBitBeforeOring4000() {
        var h = new Harness(new CdObjectPlacementRuntime(layout(0x1000, 0x200, 0x32, 0, 0xE0, 0)),
                WHOLE, new CdScenery.Assets(CdAssetBytes.copyOf(hex("a987")), visual(), MAP));
        assertEquals(0xE987, h.run(globals(0, 0, 0, 0, 0x1080)).state().word(TILE));
    }

    @Test void shortAndOddViewsSucceedWhenTheReachedWordIsComplete() {
        int[][] cases={{2,0,0,0,0x43DB},{3,0,0,0,0x43DB},{6,1,0,0,0x4438},
                {7,1,0,0,0x4438},{12,2,1,1,0x438F}};
        for(int[] c:cases){var h=withBase(java.util.Arrays.copyOf(BASE_WORDS,c[0]));assertEquals(c[4],h.run(globals(c[1],c[2],c[3],0,0x1080)).state().word(TILE));}
    }

    @Test void reachedShortOddAndOutOfDirectoryReadsKeepExactlyTheSixStorePrefix() {
        // view length, raw period, goodFuture, act, reached BYTE offset. ADD.B cannot carry or clear bit8.
        int[][] cuts={{0,0,0,0,0},{1,0,0,0,0},{4,1,0,0,4},{5,1,0,0,4},{11,2,1,1,10},
                {14,3,0,0,12},{1024,2,0xFE,2,0x200},{12,2,0x7F,0,0x200},{12,0x7F,0,0,0x1FC},{12,2,2,0,12}};
        for(int[] c:cuts){var h=withBase(java.util.Arrays.copyOf(BASE_WORDS,c[0]));h.edit(v->v.moveWord(TILE,0xBA65).moveByte(SPRITE_FLAGS,0xA3).moveByte(SUBTYPE,0xEE).moveByte(SAVED_FLAGS_ID,0xFF));var before=h.slot();byte[] bank=h.placement.capture().savedFlags().copy();
            var stop=assertThrows(CdScenery.UnsupportedScenery.class,()->h.run(globals(c[1],c[2],c[3],0,0x1080)));assertEquals(CdScenery.UnsupportedBoundary.BASE_TILE_ADDRESS,stop.boundary());assertEquals(WHOLE,stop.wholeIndex());assertEquals(c[4],stop.value());
            assertArrayEquals(prefixOracle(before,MAP),h.slot().copyBytes());assertEquals(0xBA65,h.slot().word(TILE));assertTrue(h.draw.seen.isEmpty());assertArrayEquals(bank,h.placement.capture().savedFlags().copy());}
    }

    @Test void mainNeverReadsInitSelectorsOrEvenAnEmptyBaseViewAndNeverReinitializesDirtyState() {
        var h = withBase(new byte[0]);
        h.write(dirty(h.slot()).moveByte(ID, 0x32).moveByte(ROUTINE, 2)
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
            var stop = assertThrows(CdScenery.UnsupportedScenery.class, () -> h.run(world()));
            assertEquals(CdScenery.UnsupportedBoundary.ROUTINE_DISPATCH, stop.boundary());
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
        h.write(dirty(h.slot()).moveByte(ID, 0x32).moveByte(ROUTINE, 2)
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
            var stop = assertThrows(CdScenery.UnsupportedScenery.class,
                    () -> h.run(globals(c[1], 0xFF, 0xFF, c[2], 0x1080)));
            assertEquals(CdScenery.UnsupportedBoundary.SAVED_FLAGS_ADDRESS, stop.boundary());
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
        var stop = assertThrows(CdScenery.UnsupportedScenery.class, () -> h.run(world()));
        assertEquals(CdScenery.UnsupportedBoundary.SAVED_FLAGS_ADDRESS, stop.boundary());
        assertEquals(768, stop.value());
        byte[] expected = initOracle(before, MAP, 0x4438);
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
            assertEquals(0x4438, pool.state(WHOLE).word(TILE));
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
        byte[] expected = initOracle(before, MAP, 0x4438);
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
        assertArrayEquals(initOracle(before, MAP, 0x4438), h.slot().copyBytes());
        assertEquals(1, h.draw.seen.size());
    }

    @Test void unhandledSynchronousReentryStopsBeforeANestedDispatchAndPreservesOuterPrefix() {
        var h = new Harness(); h.edit(s -> s.moveByte(SPRITE_FLAGS, 0x83).moveByte(SAVED_FLAGS_ID, 255));
        var before = h.slot();
        h.draw.after = (pool, slot) -> h.body.run(slot.wholeIndex(), world());
        assertThrows(IllegalStateException.class, () -> h.run(world()));
        byte[] expected = initOracle(before, MAP, 0x4438); expected[1] &= 0x7F;
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
        h.pool().write(h.pool().state(48).moveByte(ID, 0x32).moveByte(ROUTINE, 2).moveWord(X, 0x1000));
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
            var body = new CdScenery(h.placement, assets(), (pool, slot) ->
                    outcomes.add(queue.drawObject(cut(pool.state(slot.wholeIndex())), camera)));
            var result = body.run(WHOLE, globals(1, 0, 0, 0, 0x1000));
            assertEquals(List.of(CdObjectDrawQueue.EnqueueResult.CULLED), outcomes);
            byte[] expected = initOracle(before, MAP, 0x4438); expected[1] &= 0x7F;
            assertArrayEquals(expected, result.state().copyBytes());
            assertEquals(0, queue.finishAfterCamera(new CdObjectDrawQueue.DrawCameras(camera, camera, camera)).spriteCount());
        }
    }

    @Test void realSharedDrawFullBucketStillClearsBit7BeforeTheTail() {
        var h = new Harness(); h.edit(s -> s.moveByte(SPRITE_FLAGS, 0x83).moveByte(PRIORITY, 6));
        h.pool().write(h.pool().state(48).moveByte(ID, 0x32).moveByte(PRIORITY, 6));
        var camera = new CdObjectDrawQueue.CameraWords(0x1000, 0x0200);
        var outcomes = new ArrayList<CdObjectDrawQueue.EnqueueResult>();
        try (var queue = queue(h.pool())) {
            queue.beginPass();
            for (int i = 0; i < 63; i++) {
                assertEquals(CdObjectDrawQueue.EnqueueResult.QUEUED, queue.drawObject(cut(h.pool().state(48)), camera));
            }
            var body = new CdScenery(h.placement, assets(), (pool, slot) ->
                    outcomes.add(queue.drawObject(cut(pool.state(slot.wholeIndex())), camera)));
            var result = body.run(WHOLE, globals(1, 0, 0, 0, 0x1000));
            assertEquals(List.of(CdObjectDrawQueue.EnqueueResult.BUCKET_FULL), outcomes);
            assertEquals(7, result.state().spriteFlags());
            assertEquals(0x4438, result.state().word(TILE)); assertFalse(result.deleted());
            // Explicit queue close; the filler mappings are deliberately outside this component check.
        }
    }

    @Test void realQueuedPhysicalPointerSeesTheSharedTailDeleteBeforeLaterBuild() {
        var h = new Harness();
        var camera = new CdObjectDrawQueue.CameraWords(0x1000, 0x0200);
        var outcomes = new ArrayList<CdObjectDrawQueue.EnqueueResult>();
        try (var queue = queue(h.pool())) {
            queue.beginPass();
            var body = new CdScenery(h.placement, assets(), (pool, slot) ->
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
        assertThrows(UnsupportedOperationException.class, () -> result.effects().add(new CdScenery.DespawnChecked(true)));
        var supplied = new ArrayList<CdScenery.Effect>();
        supplied.add(new CdScenery.DespawnChecked(true));
        var forged = new CdScenery.Result(before, supplied);
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
        assertDoesNotThrow(()->new CdScenery.Assets(CdAssetBytes.copyOf(new byte[0]),visual(),0xF1234567));
        var one=CdObjectVisualAssets.decodeStatic(hex("000201f00f0000f0"),1,16);
        assertThrows(IllegalArgumentException.class,()->new CdScenery.Assets(CdAssetBytes.copyOf(BASE_WORDS),one,MAP));
        var scripted=CdObjectVisualAssets.decode(MAPS,3,hex("00020100ff"),1,17);
        assertThrows(IllegalArgumentException.class,()->new CdScenery.Assets(CdAssetBytes.copyOf(BASE_WORDS),scripted,MAP));
        byte[] wider=MAPS.clone();wider[10]=17;var beyond=CdObjectVisualAssets.decodeStatic(wider,3,19);
        assertThrows(IllegalArgumentException.class,()->new CdScenery.Assets(CdAssetBytes.copyOf(BASE_WORDS),beyond,MAP));
        assertThrows(NullPointerException.class,()->new CdScenery.Assets(null,visual(),MAP));assertThrows(NullPointerException.class,()->new CdScenery.Assets(CdAssetBytes.copyOf(BASE_WORDS),null,MAP));
    }


    @Test void constructedAct2PastUsesItsActualSecondBaseWordAndPreservesPriorityAcrossAllPhysicalSlots() {
        var h=new Harness();
        for(int whole=32;whole<128;whole++)h.pool().write(dirty(h.pool().state(whole)).moveByte(ID,whole==WHOLE?0x32:0).moveByte(ROUTINE,whole==WHOLE?0:2));
        h.edit(s->s.moveWord(X,0x1000).moveByte(SAVED_FLAGS_ID,0).moveByte(SPRITE_FLAGS,0x23).moveByte(PRIORITY,0xD7));
        mark(h.placement,7,0,0xD5);mark(h.placement,7,1,0xA3);
        var before=h.placement.capture();var source=h.slot();
        var result=h.run(globals(0,0,1,0,0x1080));
        byte[] expected=initOracle(source,MAP,0x446E);expected[1]&=0x7F;
        assertArrayEquals(expected,result.state().copyBytes());assertEquals(0xD7,result.state().unsignedByte(PRIORITY));
        assertEquals(before.status(),h.placement.status());assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());
        for(int whole=32;whole<128;whole++)if(whole!=WHOLE)assertArrayEquals(before.pool().slots().get(whole-32).copyBytes(),h.pool().state(whole).copyBytes(),"unrelated physical slot"+whole);
    }

    @Test void staticVisualHullRetainsPastSeventeenTilesAndFutureFrameFourWithoutArtAdmission() {
        var visual=visual();assertEquals(3,visual.mappingFrames().size());assertTrue(visual.animations().isEmpty());assertEquals(62,visual.mappings().length());
        int[] ends={10,17,4};for(int i=0;i<3;i++)assertEquals(ends[i],visual.mappingFrames().get(i).pieces().stream().mapToInt(p->p.tileOffset()+p.tileCount()).max().orElse(0));
        assertEquals("5c73de36900992cd776411f9b2b2e9c2007ee3fc070d74cf1a842db6e4217a12",sha(MAPS));
        assertEquals("90b119329f4db25def8b3f2b246da7076cf0cee8b236806ad54079b7c5371227",sha(BASE_WORDS));
    }

    @Test void originalObservedInitReceiptsBorrowActualFullPlacementCutsAndExposeTheSixStoreThenTwoTileCuts() {
        var h=new Harness();h.write(dirty(h.slot()).moveByte(ID,0x32).moveByte(ROUTINE,0).moveByte(SPRITE_FLAGS,0xA3).moveByte(SAVED_FLAGS_ID,0).moveWord(X,0x1000));
        var source=h.slot();var pubs=new ArrayList<CdScenery.Publication>();var draws=new ArrayList<CdScenery.DrawCall>();var events=new ArrayList<String>();var body=new CdScenery[1];
        var observer=new Observer(){
            @Override public void beforePublication(CdScenery.Publication call){assertTrue(body[0].isCurrentCall(call));assertCut(h.placement,call.cut());assertEquals(WHOLE,call.source().identity().wholeIndex());assertEquals(pubs.size()+1,call.ordinal());assertFalse(body[0].isCurrentCall(clonePublication(call)));pubs.add(call);events.add("before"+call.ordinal());}
            @Override public void afterPublication(CdScenery.Publication call){assertTrue(body[0].isCurrentCall(call));assertArrayEquals(call.output().copyBytes(),h.slot().copyBytes());events.add("after"+call.ordinal());}
            @Override public void beforeCall(Object operation){assertTrue(body[0].isCurrentCall(operation));var call=assertInstanceOf(CdScenery.DrawCall.class,operation);assertCut(h.placement,call.cut());draws.add(call);events.add("beforeDraw");}
            @Override public void afterCall(Object operation){assertSame(draws.getFirst(),operation);assertTrue(body[0].isCurrentCall(operation));events.add("afterDraw");}
        };
        body[0]=new CdScenery(h.placement,assets(),observer,call->{assertSame(draws.getFirst(),call);assertTrue(body[0].isCurrentCall(call));assertFalse(body[0].isCurrentCall(new CdScenery.DrawCall(call.cut(),call.source())));h.pool().write(h.slot().moveByte(SPRITE_FLAGS,h.slot().spriteFlags()&0x7F));events.add("draw");});
        var result=body[0].run(WHOLE,world());assertEquals(3,pubs.size());assertEquals(1,draws.size());
        assertArrayEquals(prefixOracle(source,MAP),pubs.get(0).output().copyBytes());assertArrayEquals(initOracle(source,MAP,0x0438),pubs.get(1).output().copyBytes());assertArrayEquals(initOracle(source,MAP,0x4438),pubs.get(2).output().copyBytes());
        assertEquals(List.of("before1","after1","before2","after2","before3","after3","beforeDraw","draw","afterDraw"),events);assertEquals(initEffects(false),result.effects());
        for(var call:pubs)assertFalse(body[0].isCurrentCall(call));assertFalse(body[0].isCurrentCall(draws.getFirst()));assertFalse(body[0].isCurrentCall(null));
    }

    @Test void observedSavedBitAndDeleteUseSuccessiveActualBankCutsAndOriginalOperations() {
        var h=new Harness();h.main();h.edit(s->s.moveWord(X,0x1300).moveByte(SAVED_FLAGS_ID,7));mark(h.placement,7,1,0xD5);
        var calls=new ArrayList<CdScenery.Publication>();var body=new CdScenery[1];
        var observer=new Observer(){
            @Override public void beforePublication(CdScenery.Publication call){assertTrue(body[0].isCurrentCall(call));assertCut(h.placement,call.cut());calls.add(call);assertFalse(body[0].isCurrentCall(clonePublication(call)));}
            @Override public void afterPublication(CdScenery.Publication call){assertTrue(body[0].isCurrentCall(call));}
        };
        body[0]=new CdScenery(h.placement,assets(),observer,call->h.pool().write(h.slot().moveByte(SPRITE_FLAGS,h.slot().spriteFlags()&0x7F)));
        var result=body[0].run(WHOLE,world());assertTrue(result.deleted());assertEquals(2,calls.size());
        var saved=calls.get(0);var deletion=calls.get(1);assertEquals(1,saved.ordinal());assertEquals(24,saved.savedIndex());assertSame(saved.source(),saved.output());assertEquals(0xD5,saved.cut().savedFlags().unsignedByte(24));
        assertEquals(2,deletion.ordinal());assertEquals(-1,deletion.savedIndex());assertNull(deletion.output());assertEquals(0x55,deletion.cut().savedFlags().unsignedByte(24));
        assertArrayEquals(new byte[64],result.state().copyBytes());assertEquals(0x55,h.placement.capture().savedFlags().unsignedByte(24));assertFalse(body[0].isCurrentCall(saved));assertFalse(body[0].isCurrentCall(deletion));
    }

    @Test void observedMissingWordRetainsOnlyTheSixStorePrefixThenReleasesTheComponentScope() {
        var h=new Harness();var source=h.slot();var publications=new ArrayList<CdScenery.Publication>();var draws=new int[1];var body=new CdScenery[1];
        var observer=new Observer(){@Override public void beforePublication(CdScenery.Publication call){assertTrue(body[0].isCurrentCall(call));publications.add(call);}};
        body[0]=new CdScenery(h.placement,new CdScenery.Assets(CdAssetBytes.copyOf(new byte[0]),visual(),MAP),observer,call->draws[0]++);
        var failure=assertThrows(CdScenery.UnsupportedScenery.class,()->body[0].run(WHOLE,world()));assertEquals(CdScenery.UnsupportedBoundary.BASE_TILE_ADDRESS,failure.boundary());assertEquals(4,failure.value());
        assertEquals(1,publications.size());assertArrayEquals(prefixOracle(source,MAP),h.slot().copyBytes());assertEquals(0,draws[0]);assertFalse(body[0].isCurrentCall(publications.getFirst()));
        assertEquals(mainEffects(false),body[0].run(WHOLE,world()).effects());assertEquals(1,draws[0]);
    }

    @Test void observerBeforeAndAfterPublicationFailuresPreserveTheirExactReachedStoreCutAndOriginalFailure() {
        for(boolean before:new boolean[]{true,false}){
            var h=new Harness();var source=h.slot();var failure=new IllegalStateException("observer"+before);var held=new CdScenery.Publication[1];var body=new CdScenery[1];
            var observer=new Observer(){
                @Override public void beforePublication(CdScenery.Publication call){held[0]=call;assertTrue(body[0].isCurrentCall(call));if(before)throw failure;}
                @Override public void afterPublication(CdScenery.Publication call){throw failure;}
            };
            body[0]=new CdScenery(h.placement,assets(),observer,call->{throw new AssertionError("Draw must not run");});
            assertSame(failure,assertThrows(IllegalStateException.class,()->body[0].run(WHOLE,world())));
            assertArrayEquals(before?source.copyBytes():prefixOracle(source,MAP),h.slot().copyBytes());assertFalse(body[0].isCurrentCall(held[0]));
        }
    }

    @Test void observedDrawFailuresAtBeforePortInsidePortAndAfterPortKeepTheOriginalFailureAndBypassTheSavedTail() {
        for(int failureCut=0;failureCut<3;failureCut++){
            int cut=failureCut;var h=new Harness();h.edit(s->s.moveWord(X,0x1300).moveByte(SAVED_FLAGS_ID,7));mark(h.placement,7,1,0xD5);var source=h.slot();var bank=h.placement.capture().savedFlags().copy();var failure=new IllegalStateException("draw"+cut);var held=new CdScenery.DrawCall[1];var body=new CdScenery[1];
            var observer=new Observer(){
                @Override public void beforeCall(Object operation){held[0]=(CdScenery.DrawCall)operation;assertTrue(body[0].isCurrentCall(operation));if(cut==0)throw failure;}
                @Override public void afterCall(Object operation){assertTrue(body[0].isCurrentCall(operation));if(cut==2)throw failure;}
            };
            body[0]=new CdScenery(h.placement,assets(),observer,call->{assertSame(held[0],call);h.pool().write(h.slot().moveByte(COLLISION_STATUS,0x73));if(cut==1)throw failure;});
            assertSame(failure,assertThrows(IllegalStateException.class,()->body[0].run(WHOLE,world())));byte[] expected=initOracle(source,MAP,0x4438);if(cut!=0)expected[0x21]=0x73;assertArrayEquals(expected,h.slot().copyBytes());assertArrayEquals(bank,h.placement.capture().savedFlags().copy());assertFalse(body[0].isCurrentCall(held[0]));
        }
    }

    @Test void equalClonesWrongBodyAndCaughtOtherWholeReentryNeverBecomeCurrentOrPoisonTheOuterOperation() {
        var h=new Harness();var other=new Harness();h.pool().write(h.pool().state(48).moveByte(ID,0x32).moveByte(ROUTINE,2));var neighbor=h.pool().state(48);var original=new ArrayList<Object>();var body=new CdScenery[1];
        var observer=new Observer(){
            @Override public void beforePublication(CdScenery.Publication call){original.add(call);var clone=clonePublication(call);assertEquals(call,clone);assertFalse(body[0].isCurrentCall(clone));assertFalse(other.body.isCurrentCall(call));assertThrows(IllegalStateException.class,()->body[0].run(48,world()));assertTrue(body[0].isCurrentCall(call));}
            @Override public void beforeCall(Object call){original.add(call);assertThrows(IllegalStateException.class,()->body[0].run(WHOLE,world()));}
        };
        body[0]=new CdScenery(h.placement,assets(),observer,call->{assertTrue(body[0].isCurrentCall(call));assertFalse(body[0].isCurrentCall(new CdScenery.DrawCall(call.cut(),call.source())));assertThrows(IllegalStateException.class,()->body[0].run(48,world()));});
        assertEquals(initEffects(false),body[0].run(WHOLE,world()).effects());assertArrayEquals(neighbor.copyBytes(),h.pool().state(48).copyBytes());for(var call:original)assertFalse(body[0].isCurrentCall(call));
    }

    private static CdScenery.Publication clonePublication(CdScenery.Publication call){return new CdScenery.Publication(call.ordinal(),call.cut(),call.source(),call.output(),call.savedIndex());}
    private static void assertCut(CdObjectPlacementRuntime placement,CdObjectPlacementRuntime.Snapshot cut){placement.validateRestore(cut);assertEquals(placement.status(),cut.status());assertEquals(placement.pool().capture().slots(),cut.pool().slots());assertArrayEquals(placement.capture().savedFlags().copy(),cut.savedFlags().copy());}
    private static String sha(byte[] bytes){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    private static class Observer implements CdScenery.DispatchObserver{
        @Override public void beforePublication(CdScenery.Publication call){}
        @Override public void afterPublication(CdScenery.Publication call){}
        @Override public void beforeCall(Object call){}
        @Override public void afterCall(Object call){}
    }

    // Direct BYTE/WORD/LONG offsets from native Scenery Init :23-28; not production field constants.
    private static byte[] prefixOracle(CdObjectState before, int pointer) {
        byte[] b = before.copyBytes();
        b[0x24] += 2; b[1] |= 4; // Scenery never writes PRIORITY.
        b[4] = (byte) (pointer >>> 24); b[5] = (byte) (pointer >>> 16);
        b[6] = (byte) (pointer >>> 8); b[7] = (byte) pointer;
        b[0x1A] = b[0x28]; b[0x19] = 0x10; b[0x16] = 0x18;
        return b;
    }

    private static byte[] initOracle(CdObjectState before, int pointer, int finalTile) {
        byte[] b = prefixOracle(before, pointer);
        b[2] = (byte) (finalTile >>> 8); b[3] = (byte) finalTile;
        return b;
    }

    private static List<CdScenery.Effect> initEffects(boolean deleted) {
        return List.of(new CdScenery.RoutineEntered(CdScenery.NativeRoutine.INIT),
                new CdScenery.RoutineEntered(CdScenery.NativeRoutine.MAIN),
                new CdScenery.CallReturned(CdScenery.NativeCall.DRAW_OBJECT),
                new CdScenery.DespawnChecked(deleted));
    }

    private static List<CdScenery.Effect> mainEffects(boolean deleted) {
        return List.of(new CdScenery.RoutineEntered(CdScenery.NativeRoutine.MAIN),
                new CdScenery.CallReturned(CdScenery.NativeCall.DRAW_OBJECT),
                new CdScenery.DespawnChecked(deleted));
    }

    private static CdScenery.Globals world() { return globals(1, 0, 0, 0, 0x1080); }
    private static CdScenery.Globals globals(int timeZone, int goodFuture, int act, int direction, int camera) {
        return new CdScenery.Globals(timeZone, goodFuture, act, direction, camera);
    }
    private static CdObjectVisualAssets visual() { return CdObjectVisualAssets.decodeStatic(MAPS, 3, 17); }
    private static CdScenery.Assets assets() {
        return new CdScenery.Assets(CdAssetBytes.copyOf(BASE_WORDS), visual(), MAP);
    }
    private static Harness withBase(byte[] bytes) {
        return new Harness(new CdObjectPlacementRuntime(layout(0x1000, 0x200, 0x32, 0, 0xE0, 0)),
                WHOLE, new CdScenery.Assets(CdAssetBytes.copyOf(bytes), visual(), MAP));
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

    private static final class Draw implements CdScenery.DrawObjectCall {
        final List<CdObjectState> seen = new ArrayList<>();
        final List<CdObjectPool> pools = new ArrayList<>();
        final List<CdObjectState.Identity> identities = new ArrayList<>();
        CdScenery.DrawObjectCall after = (pool, slot) -> {};
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
        final CdScenery body;
        Harness() {
            this(new CdObjectPlacementRuntime(layout(0x1000, 0x200, 0x32, 0, 0xE0, 0)), WHOLE, assets());
        }
        Harness(CdObjectPlacementRuntime placement, int whole, CdScenery.Assets assets) {
            this.placement = placement; this.whole = whole;
            if (slot().id() == 0) {
                write(slot().moveByte(ID, 0x32).moveWord(X, 0x1000).moveWord(Y, 0x200));
            }
            body = new CdScenery(placement, assets, draw);
        }
        CdObjectPool pool() { return placement.pool(); }
        CdObjectState slot() { return pool().state(whole); }
        void write(CdObjectState state) { pool().write(state); }
        void edit(UnaryOperator<CdObjectState> edit) { write(edit.apply(slot())); }
        void main() { edit(s -> s.moveByte(ROUTINE, 2)); }
        CdScenery.Result run(CdScenery.Globals globals) { return body.run(whole, globals); }
    }
}

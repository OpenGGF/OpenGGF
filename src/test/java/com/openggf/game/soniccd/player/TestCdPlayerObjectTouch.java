package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.objects.CdObjectPool;
import com.openggf.game.soniccd.objects.CdObjectState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.player.CdPlayerObjectTouch.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

/** Written before the implementation. All tests UNRUN by the external author. */
class TestCdPlayerObjectTouch {
    private static final CdPlayerObjectTouch.PlayerCut STANDING = cut(0x100, 0x200, 0x13, 0, 0);

    @Test
    void spriteSignAndZeroTypeSkipBeforeAnyTableLookupWithoutAnIdGate() {
        var pool = new CdObjectPool();
        pool.write(object(pool, 32, 0x7F, 0x40, 0x100, 0x200)); // Invalid selector, skipped flag.
        pool.write(object(pool, 33, 0, 0xC0, 0x100, 0x200));
        pool.write(object(pool, 34, 0xFF, 0, 0x100, 0x200)); // Type zero, skipped lookup.
        var dirtyFree = object(pool, 35, 0x80, 0x47, 0x100, 0x200).moveByte(ID, 0);
        pool.write(dirtyFree);
        var before = pool.capture();

        var result = touch().scan(STANDING, before);

        assertEquals(ITEM, result.kind());
        assertSame(dirtyFree, result.selectedSlot().orElseThrow());
        assertEquals(0, result.changedSlot().orElseThrow().id());
        assertEquals(2, result.changedSlot().orElseThrow().routine());
        assertEquals(before.slots(), pool.capture().slots());
    }

    @Test
    void everyPhysicalSlotIncluding127IsScannedRegardlessOfId() {
        for (int whole = 32; whole < 128; whole++) {
            var pool = new CdObjectPool();
            for (int earlier = 32; earlier < whole; earlier++) {
                pool.write(object(pool, earlier, 0x80, 0x47, 0x4000, 0x200));
            }
            var selected = object(pool, whole, 0x80, 0x47, 0x100, 0x200).moveByte(ID, 0);
            pool.write(selected);

            var result = touch().scan(STANDING, pool.capture());

            assertEquals(ITEM, result.kind(), "whole slot " + whole);
            assertSame(selected, result.selectedSlot().orElseThrow());
            var changed = result.changedSlot().orElseThrow();
            assertSame(selected.identity(), changed.identity());
            assertEquals(whole, changed.identity().wholeIndex());
            assertEquals(whole - 32, changed.identity().dynamicIndex());
            assertEquals(0xFFD000 + whole * 64, changed.identity().nativeAddress24());
        }
    }

    @Test
    void firstPhysicalOverlapReturnsEvenForAliasedIdsSavedIdsAndReverseInsertion() {
        var pool = new CdObjectPool();
        pool.write(object(pool, 127, 0x80, 0x47, 0x100, 0x200).moveByte(SAVED_FLAGS_ID, 3));
        pool.write(object(pool, 32, 0x80, 0x47, 0x100, 0x200).moveByte(SAVED_FLAGS_ID, 3));
        pool.write(object(pool, 33, 0x80, 0x40, 0x100, 0x200)); // Must never look up after return.
        var snapshot = pool.capture();

        var result = touch().scan(STANDING, snapshot);

        assertEquals(32, result.selectedSlot().orElseThrow().identity().wholeIndex());
        assertEquals(2, result.changedSlot().orElseThrow().routine());
        assertEquals(0, pool.state(127).routine());
        assertEquals(snapshot.slots(), pool.capture().slots());
    }

    @Test
    void hurtThresholdIsTheUnsignedPostDisplayWordAndStillReturnsFirstOverlap() {
        var pool = new CdObjectPool();
        pool.write(object(pool, 32, 0x80, 0x47, 0x100, 0x200).moveByte(ROUTINE, 2));
        pool.write(object(pool, 33, 0x80, 0x41, 0x100, 0x200).moveByte(ROUTINE, 6));
        for (int hurt : new int[]{0, 89, 90, 91, 0x8000, 0xFFFF}) {
            var result = touch().scan(cut(0x100, 0x200, 0x13, 0, hurt), pool.capture());
            assertInstanceOf(CdPlayerObjectTouch.Item.class, result);
            assertEquals(32, result.selectedSlot().orElseThrow().identity().wholeIndex());
            if (hurt < 90) {
                assertEquals(4, result.changedSlot().orElseThrow().routine());
            } else {
                assertTrue(result.changedSlot().isEmpty());
            }
        }
        assertEquals(2, pool.state(32).routine());
        assertEquals(6, pool.state(33).routine());
    }

    @Test
    void allOtherGroup40SelectorsAreItemsRatherThanOnlyRing47() {
        for (int type = 0x41; type <= 0x7F; type++) {
            if (type == 0x46) { continue; }
            var pool = new CdObjectPool();
            pool.write(object(pool, 32, 0x80, type, 0x100, 0x200));
            var result = touch().scan(STANDING, pool.capture());
            assertEquals(ITEM, result.kind(), "collision type " + type);
            assertEquals(2, result.changedSlot().orElseThrow().routine());
        }
    }

    @Test
    void monitorEntryPrecedesHurtGateAndEveryUnsupportedGroupIsExplicit() {
        int[] types = {0x01, 0x06, 0x2B, 0x46, 0x81, 0x86, 0xC1, 0xC6};
        CdPlayerObjectTouch.Kind[] kinds = {ENEMY, ENEMY, ENEMY, MONITOR, HAZARD, HAZARD, SPECIAL, SPECIAL};
        for (int i = 0; i < types.length; i++) {
            var pool = new CdObjectPool();
            var selected = object(pool, 32, 0x80, types[i], 0x100, 0x200);
            pool.write(selected);
            pool.write(object(pool, 33, 0x80, 0x47, 0x100, 0x200));
            var before = pool.capture();

            var result = touch().scan(cut(0x100, 0x200, 0x13, 0, 0xFFFF), before);

            assertInstanceOf(CdPlayerObjectTouch.HandlerBoundary.class, result);
            assertEquals(kinds[i], result.kind());
            assertSame(selected, result.selectedSlot().orElseThrow());
            assertTrue(result.changedSlot().isEmpty());
            assertEquals(before.slots(), pool.capture().slots());
        }
    }

    @Test
    void itemRoutineWrapsOnlyItsByteAndRetainsAllOther64ByteStorageAndAliases() {
        for (int routine : new int[]{0xFE, 0xFF}) {
            var pool = new CdObjectPool();
            var original = object(pool, 127, 0xFF, 0x47, 0x100, 0x200)
                    .moveByte(ID, 0).moveByte(ROUTINE, routine).moveByte(ROUTINE_2, 0xD9)
                    .moveLong(X_POSITION, 0x0100ABCD).moveLong(Y_POSITION, 0x0200FEDC)
                    .moveLong(MAPPINGS, 0x20A0FA).moveWord(TILE, 0xA7AE)
                    .moveWord(MOSQUI_ANCHOR_X, 0x1234).moveLong(MOSQUI_X_DELTA, 0x89ABCDEF)
                    .moveLong(MOSQUI_ANIMATION_SCRIPT, 0xFEDC9876).moveByte(RING_MEMBER, 0xA3)
                    .moveByte(SUBTYPE, 0x99).moveByte(SUBTYPE2, 0xB1).moveByte(COLLISION_STATUS, 0xD2);
            pool.write(original);
            byte[] expected = original.copyBytes();
            expected[0x24] = (byte) (routine == 0xFE ? 0 : 1);

            var result = touch().scan(STANDING, pool.capture());

            var changed = result.changedSlot().orElseThrow();
            assertArrayEquals(expected, changed.copyBytes());
            assertEquals(routine == 0xFE ? 0 : 1, changed.unsignedByte(SOLID_TYPE));
            assertSame(original.identity(), changed.identity());
            assertSame(original, pool.state(127));
        }
    }

    @Test
    void allFourTouchEdgesAreInclusiveAndOneWordBeyondEachMisses() {
        // Player left/right $F8/$108, top/bottom $1F0/$210; item half-size 6,6.
        int[][] contacts = {
                {0xF2, 0x200}, {0x10E, 0x200}, {0x100, 0x1EA}, {0x100, 0x216},
                {0xF2, 0x1EA}, {0x10E, 0x216}
        };
        for (int[] point : contacts) { assertContact(STANDING, point[0], point[1], 0x47, true); }
        int[][] misses = {{0xF1, 0x200}, {0x10F, 0x200}, {0x100, 0x1E9}, {0x100, 0x217}};
        for (int[] point : misses) { assertContact(STANDING, point[0], point[1], 0x47, false); }
    }

    @Test
    void wordBorrowCarryAndDiscardedFirstSubtractionFlagsAreLiteralAcrossXWrap() {
        // centreX, objectX, hit. First SUB.W half-size wraps before the player-left subtraction.
        int[][] cases = {
                {0, 0, 1}, {0, 5, 1}, {0, 6, 0}, {0, 0xFFFF, 1},
                {8, 0, 0}, {8, 6, 1},
                {0xFFFF, 0, 1}, {0xFFFF, 6, 0},
                {0x8008, 0x7FFA, 1}, {0x8008, 0x7FF9, 0},
                {0x8008, 0x8016, 1}, {0x8008, 0x8017, 0}
        };
        for (int[] row : cases) {
            assertContact(cut(row[0], 0x200, 0x13, 0, 0), row[1], 0x200, 0x47, row[2] != 0);
        }
    }

    @Test
    void wordBorrowCarryAndDiscardedFirstSubtractionFlagsAreLiteralAcrossYWrap() {
        int[][] cases = {
                {0, 0, 1}, {0, 5, 1}, {0, 6, 0}, {0, 0xFFF0, 1},
                {16, 0, 0}, {16, 6, 1},
                {0x8000, 0x7FEA, 1}, {0x8000, 0x7FE9, 0},
                {0x8000, 0x8016, 1}, {0x8000, 0x8017, 0}
        };
        for (int[] row : cases) {
            assertContact(cut(0x100, row[0], 0x13, 0, 0), 0x100, row[1], 0x47, row[2] != 0);
        }
    }

    @Test
    void everyRadiusByteKeepsMoveqZeroAndSubqByteWrapBeforeWordHeightDoubling() {
        // Literal adjusted radii: 253,254,255,0,1,2,...,252. No signed byte extension.
        int[] adjusted = {
                253,254,255,0,1,2,3,4,5,6,7,8,9,10,11,12,
                13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,
                29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,
                45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,
                61,62,63,64,65,66,67,68,69,70,71,72,73,74,75,76,
                77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,
                93,94,95,96,97,98,99,100,101,102,103,104,105,106,107,108,
                109,110,111,112,113,114,115,116,117,118,119,120,121,122,123,124,
                125,126,127,128,129,130,131,132,133,134,135,136,137,138,139,140,
                141,142,143,144,145,146,147,148,149,150,151,152,153,154,155,156,
                157,158,159,160,161,162,163,164,165,166,167,168,169,170,171,172,
                173,174,175,176,177,178,179,180,181,182,183,184,185,186,187,188,
                189,190,191,192,193,194,195,196,197,198,199,200,201,202,203,204,
                205,206,207,208,209,210,211,212,213,214,215,216,217,218,219,220,
                221,222,223,224,225,226,227,228,229,230,231,232,233,234,235,236,
                237,238,239,240,241,242,243,244,245,246,247,248,249,250,251,252
        };
        for (int radius = 0; radius < 256; radius++) {
            var player = cut(0x100, 0x400, radius, 0, 0);
            int halfHeight = adjusted[radius];
            // $70 selects the actual table's zero-size entry 48, so there is no object extent to hide an error.
            assertContact(player, 0x100, 0x400 - halfHeight, 0x70, true);
            assertContact(player, 0x100, 0x400 - halfHeight - 1, 0x70, false);
            assertContact(player, 0x100, 0x400 + halfHeight, 0x70, true);
            assertContact(player, 0x100, 0x400 + halfHeight + 1, 0x70, false);
        }
    }

    @Test
    void duckFrame39AddsTwelveToOriginalTopThenReplacesHalfHeightWithTen() {
        var duck = cut(0x100, 0x200, 0x13, 0x39, 0); // top $1FC, bottom $210.
        assertContact(duck, 0x100, 0x1F6, 0x47, true);
        assertContact(duck, 0x100, 0x1F5, 0x47, false);
        assertContact(duck, 0x100, 0x216, 0x47, true);
        assertContact(duck, 0x100, 0x217, 0x47, false);
        assertContact(cut(0x100, 0x200, 0x13, 0x38, 0), 0x100, 0x1F5, 0x47, true);
        assertContact(cut(0x100, 0x200, 0x13, 0x3A, 0), 0x100, 0x1F5, 0x47, true);
        // Radius 0 wraps to 253 before duck's +12: top $30F, bottom $323, not centre minus ten.
        var wrappedRadiusDuck = cut(0x100, 0x400, 0, 0x39, 0);
        assertContact(wrappedRadiusDuck, 0x100, 0x30F, 0x70, true);
        assertContact(wrappedRadiusDuck, 0x100, 0x30E, 0x70, false);
        assertContact(wrappedRadiusDuck, 0x100, 0x323, 0x70, true);
        assertContact(wrappedRadiusDuck, 0x100, 0x324, 0x70, false);
        assertContact(cut(0x100, 0xFFF8, 3, 0x39, 0), 0x100, 10, 0x47, true);
        assertContact(cut(0x100, 0xFFF8, 3, 0x39, 0), 0x100, 4, 0x47, false);
    }

    @Test
    void tableHalfSizesAreUnsignedAndInitialWordWrapIsNotRepaired() {
        assertContact(STANDING, 0x80, 0x200, 0x55, true); // Actual selector 21: half-width $80.
        assertContact(STANDING, 0x78, 0x200, 0x55, false); // First subtraction wraps to $FFF8.
        var dimensions = new ArrayList<>(actualSizes());
        dimensions.set(6, new CdR11aObjectAssets.TouchSize(255, 255));
        var selector = new CdPlayerObjectTouch(dimensions);
        var pool = new CdObjectPool();
        pool.write(object(pool, 32, 0x80, 0x47, 0x200, 0x300));
        var player = cut(0x109, 0x201, 3, 0, 0); // Left $101 and zero-height top $201.
        assertEquals(ITEM, selector.scan(player, pool.capture()).kind()); // Both object minima equal the player minima.
    }

    @Test
    void invalidLowSelectorFailsAtLookupBeforeGeometryOrHurtButNotAfterEarlierReturn() {
        for (int type : new int[]{0x40, 0x80, 0xC0}) {
            var pool = new CdObjectPool();
            pool.write(object(pool, 32, 0x80, type, 0x4000, 0x5000));
            var before = pool.capture();
            assertThrows(IllegalArgumentException.class,
                    () -> touch().scan(cut(0x100, 0x200, 0x13, 0, 0xFFFF), before));
            assertEquals(before.slots(), pool.capture().slots());
            pool.write(object(pool, 32, 0x80, 0x47, 0x100, 0x200));
            pool.write(object(pool, 33, 0x80, type, 0x100, 0x200));
            assertEquals(ITEM, touch().scan(STANDING, pool.capture()).kind());
        }
    }

    @Test
    void discardedItemResultAndLaterUnsupportedHandlerCannotLeakOrRetainAPatch() {
        var selector = touch();
        var pool = new CdObjectPool();
        var ring = object(pool, 32, 0x80, 0x47, 0x100, 0x200).moveByte(ROUTINE, 2);
        pool.write(ring);
        var oldSnapshot = pool.capture();
        var staged = selector.scan(STANDING, oldSnapshot);
        assertEquals(4, staged.changedSlot().orElseThrow().routine());
        assertSame(ring, pool.state(32)); // Parent can discard this if the rest of Body fails.

        pool.write(ring.moveByte(COLLISION_TYPE, 0x2B));
        pool.write(object(pool, 33, 0x80, 0x47, 0x100, 0x200));
        var beforeBoundary = pool.capture();
        var boundary = selector.scan(STANDING, beforeBoundary);
        assertInstanceOf(CdPlayerObjectTouch.HandlerBoundary.class, boundary);
        assertEquals(ENEMY, boundary.kind());
        assertTrue(boundary.changedSlot().isEmpty());
        assertEquals(beforeBoundary.slots(), pool.capture().slots());
        assertEquals(4, staged.changedSlot().orElseThrow().routine());
        assertEquals(4, selector.scan(STANDING, oldSnapshot).changedSlot().orElseThrow().routine());
    }

    @Test
    void snapshotsAreDetachedAndPureConsumerMakesNoForeignWorldAuthenticationClaim() {
        var selector = touch();
        var pool = new CdObjectPool();
        pool.write(object(pool, 32, 0x80, 0x47, 0x100, 0x200));
        var frozen = pool.capture();
        assertThrows(UnsupportedOperationException.class, () -> frozen.slots().clear());
        pool.clearDynamicSlots();
        byte[] escaped = frozen.slots().getFirst().copyBytes();
        Arrays.fill(escaped, (byte) 0);
        var result = selector.scan(STANDING, frozen);
        assertEquals(2, result.changedSlot().orElseThrow().routine());
        assertEquals(NONE, selector.scan(STANDING, pool.capture()).kind());

        var foreign = new CdObjectPool();
        foreign.write(object(foreign, 32, 0x80, 0x47, 0x100, 0x200));
        var foreignBefore = foreign.capture();
        var foreignResult = selector.scan(STANDING, foreignBefore);
        assertEquals(ITEM, foreignResult.kind()); // No source/world owner is bound by this format consumer.
        assertThrows(IllegalArgumentException.class, () -> pool.write(foreignResult.changedSlot().orElseThrow()));
        assertThrows(IllegalArgumentException.class, () -> foreign.write(result.changedSlot().orElseThrow()));
        assertEquals(foreignBefore.slots(), foreign.capture().slots());
        foreign.write(foreignResult.changedSlot().orElseThrow()); // Explicit later commit, never scan side effect.
        assertEquals(2, foreign.state(32).routine());
    }

    @Test
    void repeatScanIsDeterministicAndEmptyResultsCarryNoPriorSelection() {
        var selector = touch();
        var pool = new CdObjectPool();
        pool.write(object(pool, 127, 0x80, 0x47, 0x100, 0x200).moveByte(ROUTINE, 8));
        var snapshot = pool.capture();
        var first = selector.scan(STANDING, snapshot);
        for (int i = 0; i < 3; i++) {
            var again = selector.scan(STANDING, snapshot);
            assertEquals(first.kind(), again.kind());
            assertEquals(first.selectedSlot(), again.selectedSlot());
            assertEquals(first.changedSlot(), again.changedSlot());
            assertEquals(10, again.changedSlot().orElseThrow().routine());
        }
        var empty = selector.scan(STANDING, new CdObjectPool().capture());
        assertEquals(NONE, empty.kind());
        assertTrue(empty.selectedSlot().isEmpty());
        assertTrue(empty.changedSlot().isEmpty());
        assertEquals(snapshot.slots(), pool.capture().slots());
    }

    @Test
    void formatConstructorRequiresExactly63ByteWidthPairsAndOwnsItsImmutableSequence() {
        var dimensions = new ArrayList<>(actualSizes());
        var selector = new CdPlayerObjectTouch(dimensions);
        dimensions.set(6, new CdR11aObjectAssets.TouchSize(0, 0));
        var pool = new CdObjectPool();
        pool.write(object(pool, 32, 0x80, 0x47, 0xF2, 0x200));
        assertEquals(ITEM, selector.scan(STANDING, pool.capture()).kind());

        for (int capacity : new int[]{0, 1, 62, 64, 96}) {
            var wrong = new ArrayList<CdR11aObjectAssets.TouchSize>();
            for (int i = 0; i < capacity; i++) { wrong.add(new CdR11aObjectAssets.TouchSize(6, 6)); }
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerObjectTouch(wrong));
        }
        for (var invalid : List.of(new CdR11aObjectAssets.TouchSize(-1, 6),
                new CdR11aObjectAssets.TouchSize(256, 6), new CdR11aObjectAssets.TouchSize(6, -1),
                new CdR11aObjectAssets.TouchSize(6, 256))) {
            var wrong = new ArrayList<>(actualSizes());
            wrong.set(62, invalid);
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerObjectTouch(wrong));
        }
        var nullPair = new ArrayList<>(actualSizes());
        nullPair.set(62, null);
        assertThrows(NullPointerException.class, () -> new CdPlayerObjectTouch(nullPair));
        assertThrows(NullPointerException.class, () -> new CdPlayerObjectTouch(null));
        assertThrows(NullPointerException.class, () -> CdPlayerObjectTouch.fromAssets(null));
    }

    @Test
    void cutRejectsNonNativeWidthsAndScanRejectsAbsentInputs() {
        for (int invalid : new int[]{-1, 0x10000}) {
            assertThrows(IllegalArgumentException.class, () -> cut(invalid, 0, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> cut(0, invalid, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> cut(0, 0, 0, 0, invalid));
        }
        for (int invalid : new int[]{-1, 256}) {
            assertThrows(IllegalArgumentException.class, () -> cut(0, 0, invalid, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> cut(0, 0, 0, invalid, 0));
        }
        assertThrows(NullPointerException.class, () -> touch().scan(null, new CdObjectPool().capture()));
        assertThrows(NullPointerException.class, () -> touch().scan(STANDING, null));
    }

    private static CdPlayerObjectTouch.PlayerCut cut(int x, int y, int radius, int frame, int hurt) {
        return new CdPlayerObjectTouch.PlayerCut(x, y, radius, frame, hurt);
    }

    private static CdObjectState object(CdObjectPool pool, int whole, int flags, int type, int x, int y) {
        return pool.state(whole).moveByte(ID, 0x10).moveByte(SPRITE_FLAGS, flags)
                .moveByte(COLLISION_TYPE, type).moveWord(X, x).moveWord(Y, y);
    }

    private static void assertContact(CdPlayerObjectTouch.PlayerCut player, int x, int y, int type, boolean hit) {
        var pool = new CdObjectPool();
        pool.write(object(pool, 32, 0x80, type, x, y));
        var result = touch().scan(player, pool.capture());
        assertEquals(hit ? ITEM : NONE, result.kind(), () -> player + " object=" + x + "," + y + " type=" + type);
    }

    private static CdPlayerObjectTouch touch() { return new CdPlayerObjectTouch(actualSizes()); }

    private static List<CdR11aObjectAssets.TouchSize> actualSizes() {
        // Literal ObjColSizes (Player Object Collision.asm:398-461), pin 1ecc5f3; test data only.
        return CdR11aObjectAssets.decodeTouchDimensions(new byte[]{
                0x14,0x14, 0x12,0x0C, 0x0C,0x10, 4,0x10, 0x0C,0x12, 0x10,0x10, 6,6,
                0x18,0x0C, 0x0C,0x10, 0x10,0x0C, 8,8, 0x14,0x10, 0x14,8, 0x0E,0x0E,
                0x18,0x18, 0x28,0x10, 0x10,0x18, 8,0x10, 0x20,0x70, 0x40,0x20,
                (byte)0x80,0x20, 0x20,0x20, 8,8, 4,4, 0x20,8, 0x0C,0x0C, 8,4,
                0x18,4, 0x28,4, 4,8, 4,0x18, 4,0x28, 4,0x20, 0x18,0x18, 0x0C,0x18,
                0x48,8, 8,0x0C, 0x10,8, 0x20,0x10, 0x20,0x10, 8,0x10, 0x10,0x10,
                0x0C,0x0C, 0x10,0x10, 4,4, 0x10,0x10, 0x16,0x1A,
                0,0, 0,0, 0,0, 0,0, 0,0, 0,0, 0,0, 0,0, 0,0, 0,0,
                0x28,0x24, 0x12,0x11, 0x20,0x18, 0x0C,0x14, 0x20,0x0C, 0x0C,0x10
        });
    }
}

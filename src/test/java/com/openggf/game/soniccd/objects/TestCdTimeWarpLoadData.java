package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;

import static com.openggf.game.soniccd.objects.CdTimeWarpLoadData.*;
import static com.openggf.game.soniccd.objects.CdTimeWarpSaveData.SavedField;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-derived literal cases, ALL UNRUN by this author. Frozen before production.
 * N 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Load Saved Data.asm:7-40.
 * Owner below is a disposable scalar/CAS stand-in, never an engine adapter.
 * Its callbacks observe publication checks, not interception of immutable State.read.
 */
class TestCdTimeWarpLoadData {
    private static final List<NativeStore> NORMAL = List.of(
            NativeStore.RESTORE_X, NativeStore.RESTORE_Y, NativeStore.RESTORE_FLAGS,
            NativeStore.RESTORE_GVEL, NativeStore.RESTORE_X_VELOCITY, NativeStore.RESTORE_Y_VELOCITY,
            NativeStore.RESTORE_RINGS, NativeStore.RESTORE_LIVES_FLAGS, NativeStore.RESTORE_TIME,
            NativeStore.RESTORE_WATER_ROUTINE, NativeStore.RESTORE_BOTTOM, NativeStore.RESTORE_DEST_BOTTOM,
            NativeStore.RESTORE_FG_X, NativeStore.RESTORE_FG_Y, NativeStore.RESTORE_BG_X, NativeStore.RESTORE_BG_Y,
            NativeStore.RESTORE_BG2_X, NativeStore.RESTORE_BG2_Y, NativeStore.RESTORE_BG3_X, NativeStore.RESTORE_BG3_Y);
    private static final List<StoreTarget> TARGETS = List.of(
            StoreTarget.PRIMARY_X, StoreTarget.PRIMARY_Y, StoreTarget.PRIMARY_FLAGS,
            StoreTarget.PRIMARY_GVEL, StoreTarget.PRIMARY_X_VELOCITY, StoreTarget.PRIMARY_Y_VELOCITY,
            StoreTarget.RINGS, StoreTarget.LIVES_FLAGS, StoreTarget.TIME_LONG, StoreTarget.WATER_ROUTINE,
            StoreTarget.BOTTOM_BOUND, StoreTarget.DEST_BOTTOM_BOUND,
            StoreTarget.FG_X, StoreTarget.FG_Y, StoreTarget.BG_X, StoreTarget.BG_Y,
            StoreTarget.BG2_X, StoreTarget.BG2_Y, StoreTarget.BG3_X, StoreTarget.BG3_Y,
            StoreTarget.MINI_SONIC, StoreTarget.LEFT_BOUND);
    private static final int[] ADDRESSES = {
            0xFFD008, 0xFFD00C, 0xFFD022, 0xFFD014, 0xFFD010, 0xFFD012,
            0xFF1512, 0xFF150D, 0xFF1514, 0xFFF64D, 0xFFF72E, 0xFFF726,
            0xFFF700, 0xFFF704, 0xFFF708, 0xFFF70C, 0xFFF710, 0xFFF714,
            0xFFF718, 0xFFF71C, 0xFF195B, 0xFFF728};
    private static final int[] WIDTHS = {
            2, 2, 1, 2, 2, 2, 2, 1, 4, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 2};
    private static final int[] LINES = {
            8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 30, 37};
    private static final SavedField[] SOURCES = {
            SavedField.PRIMARY_X, SavedField.PRIMARY_Y, SavedField.PRIMARY_FLAGS,
            SavedField.PRIMARY_GVEL, SavedField.PRIMARY_X_VELOCITY, SavedField.PRIMARY_Y_VELOCITY,
            SavedField.RINGS, SavedField.LIVES_FLAGS, SavedField.TIME_LONG, SavedField.WATER_ROUTINE,
            SavedField.BOTTOM_BOUND, SavedField.BOTTOM_BOUND,
            SavedField.FG_X, SavedField.FG_Y, SavedField.BG_X, SavedField.BG_Y,
            SavedField.BG2_X, SavedField.BG2_Y, SavedField.BG3_X, SavedField.BG3_Y,
            SavedField.MINI_SONIC, SavedField.PRIMARY_X};
    private static final int[] VALUES = {
            0x2345, 0xFEDC, 0xFF, 0x8000, 0xFFFF, 0x1234, 0xA123, 0xD6, 0x89ABCDEF, 0xA5,
            0x6789, 0x6789, 0x1111, 0x2222, 0x3333, 0x4444, 0x5555, 0x6666, 0x7777, 0x8888};
    private static final int[] SOURCE_PACKED_OFFSETS = {
            4, 6, 8, 31, 33, 35, 0, 2, 37, 29, 9, 9, 11, 13, 15, 17, 19, 21, 23, 25};
    private static final int[] PACKED_ADDRESSES = addresses();

    @Test
    void literalMetadataPinsTwentyNormalAndTwoConditionalNativeStores() {
        assertEquals(TARGETS, List.of(StoreTarget.values()));
        assertEquals(plan(true, true), List.of(NativeStore.values()));
        for (int i = 0; i < 22; i++) {
            NativeStore instruction = NativeStore.values()[i];
            assertEquals(TARGETS.get(i), instruction.target());
            assertEquals(SOURCES[i], instruction.savedField());
            assertEquals(LINES[i], instruction.nativeLine());
            assertEquals(ADDRESSES[i], TARGETS.get(i).nativeAddress());
            assertEquals(WIDTHS[i], TARGETS.get(i).width());
        }
        assertEquals(List.of(LiveRead.ZONE, LiveRead.SPAWN_MODE), List.of(LiveRead.values()));
        assertEquals(0xFF1506, LiveRead.ZONE.nativeAddress());
        assertEquals(0xFF1522, LiveRead.SPAWN_MODE.nativeAddress());
        assertEquals(1, LiveRead.ZONE.width());
        assertEquals(1, LiveRead.SPAWN_MODE.width());
        assertEquals(28, LiveRead.ZONE.nativeLine());
        assertEquals(33, LiveRead.SPAWN_MODE.nativeLine());
        assertEquals(SavedField.BOTTOM_BOUND, NativeStore.RESTORE_BOTTOM.savedField());
        assertEquals(SavedField.BOTTOM_BOUND, NativeStore.RESTORE_DEST_BOTTOM.savedField());
        assertEquals(SavedField.PRIMARY_X, NativeStore.SET_LEFT_BOUND.savedField());
    }

    @Test
    void ppLoadPublishesLiteralTwentyStoresAndLeavesSavedPayloadUntouched() {
        Owner actual = owner(0, 2);
        var before = actual.saved;
        Result result = load(before, actual);
        assertEquals(NORMAL, result.completedNativeStores());
        assertPrefix(actual, NORMAL, 20);
        for (int i = 0; i < 20; i++) {
            StoreCall call = actual.published.get(i);
            assertEquals(NORMAL.get(i), call.instruction());
            assertEquals(TARGETS.get(i), call.target());
            assertEquals(OptionalInt.of(0), call.expectedValue());
            assertEquals(VALUES[i], call.value());
            assertEquals(OptionalInt.of(VALUES[i]), actual.raw(TARGETS.get(i)));
        }
        assertEquals(List.of(new LiveObservation(LiveRead.ZONE, 20, OptionalInt.of(0)),
                new LiveObservation(LiveRead.SPAWN_MODE, 20, OptionalInt.of(2))), actual.liveReads);
        assertSame(before, actual.saved);
        assertEquals(0xF1, before.read(SavedField.SPAWN_MODE));
        assertEquals(0x89ABCDEF, actual.raw(StoreTarget.TIME_LONG).orElseThrow());
        assertEquals(0xFF, actual.raw(StoreTarget.PRIMARY_FLAGS).orElseThrow());
    }

    @Test
    void ppLoadSkipsExactlyTheFiveUnconsumedSavedBytes() {
        var saved = saved(0x2345, 0x89ABCDEF, 3, 27, 28, 30, 41);
        Owner actual = new Owner(saved, 0, 2);
        assertEquals(NORMAL, load(saved, actual).completedNativeStores());
        assertEquals(OptionalInt.empty(), saved.knownByte(0xFF1546));
        assertEquals(OptionalInt.empty(), saved.knownByte(0xFF1560));
        assertEquals(OptionalInt.empty(), saved.knownByte(0xFF1561));
        assertEquals(OptionalInt.empty(), saved.knownByte(0xFF1563));
        assertEquals(OptionalInt.empty(), saved.knownByte(0xFF1593));
        assertFalse(actual.attempted.stream().anyMatch(c -> c.target() == StoreTarget.MINI_SONIC));
        int known = 0;
        for (byte bit : saved.knownMask().copy()) known += bit;
        assertEquals(37, known);
    }

    @Test
    void zoneSixAddsMiniAfterTheNormalPrefixWithoutOtherConditionalSavedReads() {
        var saved = saved(0x2345, 0x89ABCDEF, 3, 27, 28, 30);
        Owner actual = new Owner(saved, 6, 2);
        assertEquals(plan(true, false), load(saved, actual).completedNativeStores());
        assertEquals(OptionalInt.of(0xE1), actual.raw(StoreTarget.MINI_SONIC));
        assertEquals(List.of(new LiveObservation(LiveRead.ZONE, 20, OptionalInt.of(6)),
                new LiveObservation(LiveRead.SPAWN_MODE, 21, OptionalInt.of(2))), actual.liveReads);
        for (int otherZone : new int[]{0, 2, 255}) {
            Owner other = new Owner(saved, otherZone, 2);
            assertEquals(NORMAL, load(saved, other).completedNativeStores());
            assertEquals(OptionalInt.of(0), other.raw(StoreTarget.MINI_SONIC));
        }
    }

    @Test
    void unknownMiniStopsAfterTwentyBeforeTheLaterLiveSpawnRead() {
        var saved = saved(0x2345, 0x89ABCDEF, 41);
        Owner actual = new Owner(saved, 6, 0x80);
        var failure = assertThrows(CdTimeWarpSaveData.UnknownSavedByte.class, () -> load(saved, actual));
        assertEquals(0xFF1593, failure.nativeAddress());
        assertPrefix(actual, NORMAL, 20);
        assertEquals(List.of(new LiveObservation(LiveRead.ZONE, 20, OptionalInt.of(6))), actual.liveReads);
    }

    @Test
    void negativeLiveSpawnAppendsWordWrappingLeftBoundWithoutRestoringSavedSpawn() {
        int[][] vectors = {{0, 0xFF60}, {159, 0xFFFF}, {160, 0}, {0xFFFF, 0xFF5F}};
        for (int spawn : new int[]{0x80, 0xFF}) {
            for (int[] vector : vectors) {
                var saved = saved(vector[0], 0xFFFFFFFF, 3, 27, 28, 30);
                Owner actual = new Owner(saved, 6, spawn);
                assertEquals(plan(true, true), load(saved, actual).completedNativeStores());
                assertEquals(OptionalInt.of(vector[1]), actual.raw(StoreTarget.LEFT_BOUND));
                assertEquals(OptionalInt.of(spawn), actual.live.get(LiveRead.SPAWN_MODE));
                assertEquals(vector[0], saved.read(SavedField.PRIMARY_X));
                assertEquals(-1, actual.raw(StoreTarget.TIME_LONG).orElseThrow());
                assertEquals(22, actual.published.size());
            }
        }
    }

    @Test
    void liveSpawnIsReadAfterStoresAndBothLateDirectionChangesTakeEffect() {
        for (boolean makeNegative : new boolean[]{true, false}) {
            Owner actual = owner(0, makeNegative ? 2 : 0x80);
            actual.afterCommit = call -> {
                if (call.instruction() == NativeStore.RESTORE_BG3_Y)
                    actual.live.put(LiveRead.SPAWN_MODE, OptionalInt.of(makeNegative ? 0x80 : 2));
            };
            assertEquals(plan(false, makeNegative), load(actual.saved, actual).completedNativeStores());
            assertEquals(new LiveObservation(LiveRead.SPAWN_MODE, 20,
                    OptionalInt.of(makeNegative ? 0x80 : 2)), actual.liveReads.get(1));
            assertEquals(OptionalInt.of(makeNegative ? 0x22A5 : 0), actual.raw(StoreTarget.LEFT_BOUND));
        }
    }

    @Test
    void liveZoneIsReadAtItsNativeLateBoundaryRatherThanPrefetched() {
        for (boolean becomeSix : new boolean[]{true, false}) {
            var saved = saved(0x2345, 0x89ABCDEF, becomeSix ? new int[0] : new int[]{41});
            Owner actual = new Owner(saved, becomeSix ? 0 : 6, 2);
            actual.afterCommit = call -> {
                if (call.instruction() == NativeStore.RESTORE_BG3_Y)
                    actual.live.put(LiveRead.ZONE, OptionalInt.of(becomeSix ? 6 : 0));
            };
            assertEquals(plan(becomeSix, false), load(saved, actual).completedNativeStores());
            assertEquals(new LiveObservation(LiveRead.ZONE, 20,
                    OptionalInt.of(becomeSix ? 6 : 0)), actual.liveReads.get(0));
        }
    }

    @Test
    void unknownLiveZoneFailsOnlyAfterTheTwentyStorePrefix() {
        Owner actual = owner(0, 2);
        actual.live.put(LiveRead.ZONE, OptionalInt.empty());
        var failure = assertThrows(UnknownLiveValue.class, () -> load(actual.saved, actual));
        assertEquals(LiveRead.ZONE, failure.liveRead());
        assertEquals(28, failure.nativeLine());
        assertPrefix(actual, NORMAL, 20);
        assertEquals(1, actual.liveReads.size());
    }

    @Test
    void unknownLiveSpawnFailsAfterTwentyOrTwentyOneActualStores() {
        for (int zone : new int[]{0, 6}) {
            Owner actual = owner(zone, 2);
            actual.live.put(LiveRead.SPAWN_MODE, OptionalInt.empty());
            var failure = assertThrows(UnknownLiveValue.class, () -> load(actual.saved, actual));
            assertEquals(LiveRead.SPAWN_MODE, failure.liveRead());
            assertEquals(33, failure.nativeLine());
            assertPrefix(actual, plan(zone == 6, false), zone == 6 ? 21 : 20);
            assertEquals(2, actual.liveReads.size());
        }
    }

    @Test
    void everyConsumedSavedByteFailsAtItsFirstNativeStoreWithTheEarlierPrefixRetained() {
        Set<Integer> checked = new HashSet<>();
        for (int cut = 0; cut < 20; cut++) {
            for (int part = 0; part < WIDTHS[cut]; part++) {
                int packed = SOURCE_PACKED_OFFSETS[cut] + part;
                if (!checked.add(packed)) continue;
                var saved = saved(0x2345, 0x89ABCDEF, packed);
                Owner actual = new Owner(saved, 0, 2);
                var failure = assertThrows(CdTimeWarpSaveData.UnknownSavedByte.class, () -> load(saved, actual));
                assertEquals(PACKED_ADDRESSES[packed], failure.nativeAddress());
                assertPrefix(actual, NORMAL, cut);
                assertEquals(cut, actual.attempted.size());
                assertTrue(actual.liveReads.isEmpty());
                assertSame(saved, actual.saved);
            }
        }
        assertEquals(37, checked.size());
        assertFalse(checked.contains(3));
        assertFalse(checked.contains(27));
        assertFalse(checked.contains(28));
        assertFalse(checked.contains(30));
        assertFalse(checked.contains(41));
    }

    @Test
    void callbackFaultsKeepTheExactPublishedPrefixAndOriginalException() {
        List<NativeStore> complete = plan(true, true);
        for (int cut = 0; cut < 22; cut++) {
            for (boolean after : new boolean[]{false, true}) {
                Owner actual = owner(6, 0x80);
                if (after) actual.failAfterCommit = cut;
                else actual.failBeforeCommit = cut;
                List<Result> returned = new ArrayList<>();
                assertSame(actual.fault, assertThrows(Marker.class,
                        () -> returned.add(load(actual.saved, actual))));
                assertTrue(returned.isEmpty());
                assertPrefix(actual, complete, cut + (after ? 1 : 0));
                assertEquals(cut + 1, actual.attempted.size());
            }
        }
        for (boolean afterPublication : new boolean[]{false, true}) {
            Owner actual = owner(0, 2);
            actual.readHook = target -> {
                if (target == StoreTarget.PRIMARY_FLAGS
                        && actual.readCounts.get(target) == (afterPublication ? 2 : 1)) throw actual.fault;
            };
            assertSame(actual.fault, assertThrows(Marker.class, () -> load(actual.saved, actual)));
            assertPrefix(actual, NORMAL, afterPublication ? 3 : 2);
        }
        Owner savedFault = owner(0, 2);
        savedFault.savedHook = () -> { throw savedFault.fault; };
        assertSame(savedFault.fault, assertThrows(Marker.class, () -> load(savedFault.saved, savedFault)));
        assertTrue(savedFault.readCounts.isEmpty());
        for (LiveRead source : List.of(LiveRead.ZONE, LiveRead.SPAWN_MODE)) {
            Owner actual = owner(0, 2);
            actual.liveHook = read -> { if (read == source) throw actual.fault; };
            assertSame(actual.fault, assertThrows(Marker.class, () -> load(actual.saved, actual)));
            assertPrefix(actual, NORMAL, 20);
        }
    }

    @Test
    void changedSavedOwnersAndPrivateDestinationCasConflictsCannotHideBehindLaterStores() {
        Owner stale = owner(0, 2);
        var requested = stale.saved;
        stale.saved = CdTimeWarpSaveData.State.unknown();
        assertThrows(IllegalStateException.class, () -> load(requested, stale));
        assertTrue(stale.readCounts.isEmpty());
        assertTrue(stale.attempted.isEmpty());
        for (boolean afterPublication : new boolean[]{false, true}) {
            Owner actual = owner(0, 2);
            var before = actual.saved;
            actual.readHook = target -> {
                if (target == StoreTarget.PRIMARY_FLAGS
                        && actual.readCounts.get(target) == (afterPublication ? 2 : 1))
                    actual.saved = CdTimeWarpSaveData.State.unknown();
            };
            assertThrows(IllegalStateException.class, () -> load(before, actual));
            assertPrefix(actual, NORMAL, afterPublication ? 3 : 2);
        }
        Owner duringCommit = owner(0, 2);
        var beforeCommit = duringCommit.saved;
        duringCommit.afterCommit = call -> {
            if (call.instruction() == NativeStore.RESTORE_FLAGS)
                duringCommit.saved = CdTimeWarpSaveData.State.unknown();
        };
        assertThrows(IllegalStateException.class, () -> load(beforeCommit, duringCommit));
        assertPrefix(duringCommit, NORMAL, 3);
        for (LiveRead source : List.of(LiveRead.ZONE, LiveRead.SPAWN_MODE)) {
            Owner actual = owner(6, 2);
            var before = actual.saved;
            actual.liveHook = read -> {
                if (read == source) actual.saved = CdTimeWarpSaveData.State.unknown();
            };
            assertThrows(IllegalStateException.class, () -> load(before, actual));
            assertPrefix(actual, plan(true, false), source == LiveRead.ZONE ? 20 : 21);
        }
        Owner conflict = owner(0, 2);
        conflict.beforeCommit = call -> {
            if (call.instruction() == NativeStore.RESTORE_FLAGS)
                conflict.fields.put(StoreTarget.PRIMARY_FLAGS, OptionalInt.of(0x55));
        };
        assertSame(conflict.casFault, assertThrows(Marker.class, () -> load(conflict.saved, conflict)));
        assertPrefix(conflict, NORMAL, 2);
        assertEquals(OptionalInt.of(0x55), conflict.raw(StoreTarget.PRIMARY_FLAGS));
    }

    @Test
    void rawByteAndWordViolationsFailAtReachedObservationsWhileLongBitsRemainRaw() {
        for (int bad : new int[]{-1, 256}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new StoreCall(NativeStore.RESTORE_FLAGS, OptionalInt.of(bad), 0));
            assertThrows(IllegalArgumentException.class,
                    () -> new StoreCall(NativeStore.RESTORE_FLAGS, OptionalInt.empty(), bad));
            Owner actual = owner(0, 2);
            actual.fields.put(StoreTarget.PRIMARY_FLAGS, OptionalInt.of(bad));
            assertThrows(IllegalArgumentException.class, () -> load(actual.saved, actual));
            assertPrefix(actual, NORMAL, 2);
        }
        for (int bad : new int[]{-1, 65536}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new StoreCall(NativeStore.RESTORE_X, OptionalInt.of(bad), 0));
            assertThrows(IllegalArgumentException.class,
                    () -> new StoreCall(NativeStore.RESTORE_X, OptionalInt.empty(), bad));
            Owner actual = owner(0, 2);
            actual.fields.put(StoreTarget.PRIMARY_X, OptionalInt.of(bad));
            assertThrows(IllegalArgumentException.class, () -> load(actual.saved, actual));
            assertTrue(actual.attempted.isEmpty());
        }
        for (LiveRead source : List.of(LiveRead.ZONE, LiveRead.SPAWN_MODE)) {
            for (int bad : new int[]{-1, 256}) {
                Owner actual = owner(6, 2);
                actual.live.put(source, OptionalInt.of(bad));
                assertThrows(IllegalArgumentException.class, () -> load(actual.saved, actual));
                assertPrefix(actual, plan(true, false), source == LiveRead.ZONE ? 20 : 21);
            }
        }
        assertDoesNotThrow(() -> new StoreCall(NativeStore.RESTORE_TIME, OptionalInt.of(-1), 0x80000000));
        Owner rawLong = new Owner(saved(0x2345, 0xFFFFFFFF), 0, 2);
        rawLong.fields.put(StoreTarget.TIME_LONG, OptionalInt.of(0x80000000));
        load(rawLong.saved, rawLong);
        assertEquals(OptionalInt.of(0x80000000), rawLong.attempted.get(8).expectedValue());
        assertEquals(OptionalInt.of(-1), rawLong.raw(StoreTarget.TIME_LONG));
    }

    @Test
    void unknownPreimagesAreLegalButMissingOrWrongReturnedPublicationIsRejected() {
        Owner unknown = owner(0, 2);
        for (StoreTarget target : TARGETS) unknown.fields.put(target, OptionalInt.empty());
        assertEquals(NORMAL, load(unknown.saved, unknown).completedNativeStores());
        assertTrue(unknown.published.stream().allMatch(call -> call.expectedValue().isEmpty()));
        for (boolean wrong : new boolean[]{false, true}) {
            Owner actual = owner(0, 2);
            actual.fields.put(StoreTarget.FG_X, OptionalInt.empty());
            if (wrong) actual.wrongPublicationAt = 12;
            else actual.noOpAt = 12;
            assertThrows(IllegalStateException.class, () -> load(actual.saved, actual));
            assertEquals(13, actual.attempted.size());
            assertPrefix(actual, NORMAL, wrong ? 13 : 12);
            assertEquals(wrong ? OptionalInt.of(0x1110) : OptionalInt.empty(), actual.raw(StoreTarget.FG_X));
        }
    }

    @Test
    void sameValueNoOpCannotBeCertifiedAsAnExactlyOnceActualWrite() {
        Owner dishonest = owner(0, 2);
        for (int i = 0; i < 20; i++) dishonest.fields.put(TARGETS.get(i), OptionalInt.of(VALUES[i]));
        dishonest.noOpAll = true;
        Result result = load(dishonest.saved, dishonest);
        assertEquals(NORMAL, result.completedNativeStores());
        assertEquals(20, dishonest.attempted.size());
        assertTrue(dishonest.published.isEmpty(),
                "Observable equality cannot establish actual same-value write adoption or live authority");
    }

    @Test
    void actualPortReentryGuardRejectsNestedLoadWithoutAnOuterResultOrLaterStores() {
        Owner actual = owner(0, 2);
        boolean[] insideCommit = {false};
        actual.savedHook = () -> { if (insideCommit[0]) throw actual.reentryFault; };
        actual.beforeCommit = call -> {
            if (call.instruction() == NativeStore.RESTORE_LIVES_FLAGS) {
                insideCommit[0] = true;
                try { load(actual.saved, actual); }
                finally { insideCommit[0] = false; }
            }
        };
        List<Result> returned = new ArrayList<>();
        assertSame(actual.reentryFault, assertThrows(Marker.class,
                () -> returned.add(load(actual.saved, actual))));
        assertTrue(returned.isEmpty());
        assertPrefix(actual, NORMAL, 7);
        assertEquals(8, actual.attempted.size());
        assertTrue(actual.liveReads.isEmpty());
    }

    @Test
    void resultAndSavedKnowledgeRemainImmutableWithoutAddingAnyOwnerOrReplayAuthority() {
        var saved = saved(0x2345, 0x89ABCDEF, 3, 27, 28, 30);
        byte[] knowledge = saved.knownMask().copy();
        List<OptionalInt> bytes = Arrays.stream(PACKED_ADDRESSES).mapToObj(saved::knownByte).toList();
        Owner actual = new Owner(saved, 6, 0x80);
        Result result = load(saved, actual);
        assertEquals(plan(true, true), result.completedNativeStores());
        assertSame(saved, actual.saved);
        assertArrayEquals(knowledge, saved.knownMask().copy());
        assertEquals(bytes, Arrays.stream(PACKED_ADDRESSES).mapToObj(saved::knownByte).toList());
        assertThrows(UnsupportedOperationException.class, () -> result.completedNativeStores().clear());
        List<NativeStore> input = new ArrayList<>(NORMAL);
        Result detached = new Result(input);
        input.clear();
        assertEquals(NORMAL, detached.completedNativeStores());
        assertThrows(NullPointerException.class, () -> new Result(null));
        assertThrows(NullPointerException.class, () -> new Result(Arrays.asList((NativeStore) null)));
        assertThrows(NullPointerException.class,
                () -> new StoreCall(null, OptionalInt.empty(), 0));
        assertThrows(NullPointerException.class,
                () -> new StoreCall(NativeStore.RESTORE_X, null, 0));
        assertThrows(NullPointerException.class, () -> load(null, actual));
        assertThrows(NullPointerException.class, () -> load(saved, null));
    }

    private static List<NativeStore> plan(boolean mini, boolean left) {
        List<NativeStore> result = new ArrayList<>(NORMAL);
        if (mini) result.add(NativeStore.RESTORE_MINI);
        if (left) result.add(NativeStore.SET_LEFT_BOUND);
        return List.copyOf(result);
    }

    private static void assertPrefix(Owner actual, List<NativeStore> schedule, int count) {
        assertEquals(schedule.subList(0, count),
                actual.published.stream().map(StoreCall::instruction).toList());
    }

    private static Owner owner(int zone, int spawn) {
        return new Owner(saved(0x2345, 0x89ABCDEF), zone, spawn);
    }

    private static CdTimeWarpSaveData.State saved(int x, int time, int... unknownPacked) {
        byte[] bytes = new byte[42], mask = new byte[42];
        Arrays.fill(mask, (byte) 1);
        put(bytes, 0, 2, 0xA123);
        put(bytes, 2, 1, 0xD6);
        put(bytes, 3, 1, 0xF1);
        put(bytes, 4, 2, x);
        put(bytes, 6, 2, 0xFEDC);
        put(bytes, 8, 1, 0xFF);
        put(bytes, 9, 2, 0x6789);
        put(bytes, 11, 2, 0x1111);
        put(bytes, 13, 2, 0x2222);
        put(bytes, 15, 2, 0x3333);
        put(bytes, 17, 2, 0x4444);
        put(bytes, 19, 2, 0x5555);
        put(bytes, 21, 2, 0x6666);
        put(bytes, 23, 2, 0x7777);
        put(bytes, 25, 2, 0x8888);
        put(bytes, 27, 2, 0xBEEF);
        put(bytes, 29, 1, 0xA5);
        put(bytes, 30, 1, 0x5A);
        put(bytes, 31, 2, 0x8000);
        put(bytes, 33, 2, 0xFFFF);
        put(bytes, 35, 2, 0x1234);
        put(bytes, 37, 4, time);
        put(bytes, 41, 1, 0xE1);
        for (int packed : unknownPacked) mask[packed] = 0;
        return CdTimeWarpSaveData.State.supplied(CdAssetBytes.copyOf(bytes), CdAssetBytes.copyOf(mask));
    }

    private static void put(byte[] bytes, int offset, int width, int value) {
        for (int part = 0; part < width; part++)
            bytes[offset + part] = (byte) (value >>> (8 * (width - part - 1)));
    }

    private static int[] addresses() {
        List<Integer> result = new ArrayList<>();
        for (int[] span : new int[][]{{0xFF1528, 0xFF152A}, {0xFF1545, 0xFF1547},
                {0xFF1548, 0xFF154D}, {0xFF154E, 0xFF156A}, {0xFF1570, 0xFF1574}, {0xFF1593, 0xFF1594}})
            for (int address = span[0]; address < span[1]; address++) result.add(address);
        return result.stream().mapToInt(Integer::intValue).toArray();
    }

    private record LiveObservation(LiveRead source, int publishedCount, OptionalInt value) { }

    private static final class Marker extends RuntimeException {
        private static final long serialVersionUID = 1L;
        Marker(String message) { super(message); }
    }

    private static final class Owner implements Port {
        CdTimeWarpSaveData.State saved;
        final EnumMap<StoreTarget, OptionalInt> fields = new EnumMap<>(StoreTarget.class);
        final EnumMap<LiveRead, OptionalInt> live = new EnumMap<>(LiveRead.class);
        final EnumMap<StoreTarget, Integer> readCounts = new EnumMap<>(StoreTarget.class);
        final List<StoreCall> attempted = new ArrayList<>();
        final List<StoreCall> published = new ArrayList<>();
        final List<LiveObservation> liveReads = new ArrayList<>();
        final Marker fault = new Marker("original callback fault");
        final Marker casFault = new Marker("actual scalar owner rejected stale preimage");
        final Marker reentryFault = new Marker("actual owner rejects callback reentry");
        Consumer<StoreTarget> readHook = target -> { };
        Consumer<LiveRead> liveHook = source -> { };
        Consumer<StoreCall> beforeCommit = call -> { };
        Consumer<StoreCall> afterCommit = call -> { };
        Runnable savedHook = () -> { };
        int failBeforeCommit = -1, failAfterCommit = -1, noOpAt = -1, wrongPublicationAt = -1;
        boolean noOpAll;

        Owner(CdTimeWarpSaveData.State saved, int zone, int spawn) {
            this.saved = saved;
            for (StoreTarget target : TARGETS) fields.put(target, OptionalInt.of(0));
            live.put(LiveRead.ZONE, OptionalInt.of(zone));
            live.put(LiveRead.SPAWN_MODE, OptionalInt.of(spawn));
        }

        @Override public CdTimeWarpSaveData.State currentSaved() {
            savedHook.run();
            return saved;
        }

        @Override public OptionalInt read(StoreTarget target) {
            readCounts.merge(target, 1, Integer::sum);
            readHook.accept(target);
            return raw(target);
        }

        @Override public OptionalInt readLive(LiveRead source) {
            liveHook.accept(source);
            OptionalInt value = live.get(source);
            liveReads.add(new LiveObservation(source, published.size(), value));
            return value;
        }

        @Override public void commit(StoreCall call) {
            attempted.add(call);
            int index = attempted.size() - 1;
            if (index == failBeforeCommit) throw fault;
            beforeCommit.accept(call);
            if (!Objects.equals(raw(call.target()), call.expectedValue())) throw casFault;
            if (!noOpAll && index != noOpAt) {
                fields.put(call.target(), OptionalInt.of(index == wrongPublicationAt ? call.value() ^ 1 : call.value()));
                published.add(call);
            }
            afterCommit.accept(call);
            if (index == failAfterCommit) throw fault;
        }

        OptionalInt raw(StoreTarget target) { return fields.get(target); }
    }
}

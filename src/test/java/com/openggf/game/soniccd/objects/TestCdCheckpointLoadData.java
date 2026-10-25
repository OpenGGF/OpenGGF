package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import static com.openggf.game.soniccd.objects.CdCheckpointLoadData.*;
import static com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Literal source-derived cases, ALL UNRUN by this author. Frozen before production.
 * N 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * src/Level/Load Saved Data.asm:44-87; variables/definitions pin raw addresses.
 * The test Port is a disposable owner/CAS stand-in, never an engine adapter.
 * Its ledger observes validation calls, not an interception of immutable State.read.
 */
class TestCdCheckpointLoadData {
    private static final List<NativeStore> SCHEDULE = List.of(
            NativeStore.RESTORE_SPAWN, NativeStore.RESTORE_X, NativeStore.RESTORE_Y,
            NativeStore.CLEAR_RINGS, NativeStore.CLEAR_LIVES_FLAGS, NativeStore.RESTORE_TIME,
            NativeStore.SET_FRAMES_59, NativeStore.DECREMENT_SECONDS,
            NativeStore.RESTORE_WATER_ROUTINE_FIRST, NativeStore.RESTORE_BOTTOM,
            NativeStore.RESTORE_DEST_BOTTOM, NativeStore.RESTORE_FG_X, NativeStore.RESTORE_FG_Y,
            NativeStore.RESTORE_BG_X, NativeStore.RESTORE_BG_Y,
            NativeStore.RESTORE_BG2_X, NativeStore.RESTORE_BG2_Y,
            NativeStore.RESTORE_BG3_X, NativeStore.RESTORE_BG3_Y,
            NativeStore.RESTORE_MINI, NativeStore.RESTORE_WATER_HEIGHT,
            NativeStore.RESTORE_WATER_ROUTINE_SECOND, NativeStore.RESTORE_WATER_FULLSCREEN,
            NativeStore.SET_LEFT_BOUND);
    private static final List<StoreTarget> TARGETS = List.of(
            StoreTarget.SPAWN_MODE, StoreTarget.PRIMARY_X, StoreTarget.PRIMARY_Y,
            StoreTarget.RINGS, StoreTarget.LIVES_FLAGS, StoreTarget.TIME_LONG,
            StoreTarget.TIME_FRAMES, StoreTarget.TIME_SECONDS, StoreTarget.WATER_ROUTINE,
            StoreTarget.BOTTOM_BOUND, StoreTarget.DEST_BOTTOM_BOUND,
            StoreTarget.FG_X, StoreTarget.FG_Y, StoreTarget.BG_X, StoreTarget.BG_Y,
            StoreTarget.BG2_X, StoreTarget.BG2_Y, StoreTarget.BG3_X, StoreTarget.BG3_Y,
            StoreTarget.MINI_SONIC, StoreTarget.WATER_HEIGHT2,
            StoreTarget.WATER_FULLSCREEN, StoreTarget.LEFT_BOUND);
    private static final int[] ADDRESSES = {
            0xFF1522, 0xFFD008, 0xFFD00C, 0xFF1512, 0xFF150D,
            0xFF1514, 0xFF1517, 0xFF1516, 0xFFF64D, 0xFFF72E, 0xFFF726,
            0xFFF700, 0xFFF704, 0xFFF708, 0xFFF70C, 0xFFF710, 0xFFF714,
            0xFFF718, 0xFFF71C, 0xFF195B, 0xFFF648, 0xFFF64E, 0xFFF728};
    private static final int[] WIDTHS = {
            1, 2, 2, 2, 1, 4, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 2, 1, 2};
    private static final int[] LINES = {
            49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64,
            65, 66, 67, 70, 75, 76, 77, 84};
    private static final SavedField[] SOURCES = {
            SavedField.SPAWN_MODE, SavedField.PRIMARY_X, SavedField.PRIMARY_Y,
            null, null, SavedField.TIME_LONG, null, null, SavedField.WATER_ROUTINE,
            SavedField.BOTTOM_BOUND, SavedField.BOTTOM_BOUND,
            SavedField.FG_X, SavedField.FG_Y, SavedField.BG_X, SavedField.BG_Y,
            SavedField.BG2_X, SavedField.BG2_Y, SavedField.BG3_X, SavedField.BG3_Y,
            SavedField.MINI_SONIC, SavedField.WATER_HEIGHT_WORD,
            SavedField.WATER_ROUTINE, SavedField.WATER_FULLSCREEN, SavedField.PRIMARY_X};

    @Test
    void targetsAreOnlyThe23AddressedNativeFields() {
        assertEquals(TARGETS, List.of(StoreTarget.values()));
        for (int i = 0; i < TARGETS.size(); i++) {
            assertEquals(ADDRESSES[i], TARGETS.get(i).nativeAddress());
            assertEquals(WIDTHS[i], TARGETS.get(i).width());
        }
    }

    @Test
    void instructionMetadataPinsAll24NativeCutsAndRepeatedSavedReads() {
        assertEquals(SCHEDULE, List.of(NativeStore.values()));
        for (int i = 0; i < SCHEDULE.size(); i++) {
            NativeStore step = SCHEDULE.get(i);
            assertEquals(LINES[i], step.nativeLine());
            assertEquals(java.util.Optional.ofNullable(SOURCES[i]), step.savedField());
            int targetIndex = i <= 20 ? i : i == 21 ? 8 : i == 22 ? 21 : 22;
            assertEquals(TARGETS.get(targetIndex), step.target());
        }
        assertEquals(SavedField.BOTTOM_BOUND, SOURCES[9]);
        assertEquals(SavedField.BOTTOM_BOUND, SOURCES[10]);
        assertEquals(SavedField.WATER_ROUTINE, SOURCES[8]);
        assertEquals(SavedField.WATER_ROUTINE, SOURCES[21]);
        assertEquals(SavedField.PRIMARY_X, SOURCES[23]);
    }

    @Test
    void zoneZeroHasExactOrderedValidationReadStoreLedger() {
        Owner port = owner(0, 1);
        Result result = load(1, 0, port.saved, port);
        assertEquals(plan(0, 1), result.completedNativeStores());
        List<String> expected = new ArrayList<>();
        for (NativeStore step : plan(0, 1)) {
            String t = step.target().name();
            expected.addAll(List.of("S", "R:" + t, "S", "C:" + t, "S", "R:" + t, "S"));
        }
        expected.addAll(List.of("S", "R:SPAWN_MODE", "S"));
        assertEquals(expected, port.ledger);
        assertEquals(19, port.applied.size());
        assertEquals(0x0004113B, port.rawTime);
    }

    @Test
    void firstUnknownAtEachOf31SavedBytesRetainsExactlyEarlierStores() {
        Set<Integer> checked = new HashSet<>();
        for (int zone : new int[]{0, 2, 6}) {
            List<NativeStore> sequence = plan(zone, 0x80);
            for (int cut = 0; cut < sequence.size(); cut++) {
                SavedField source = SOURCES[SCHEDULE.indexOf(sequence.get(cut))];
                if (source == null) continue;
                for (int part = 0; part < source.width(); part++) {
                    int address = source.nativeAddress() + part;
                    if (!checked.add(address)) continue;
                    CdCheckpointSaveData.State saved = saved(0x80, 0x2345, 0x0004122A, address);
                    Owner port = new Owner(saved, zone, 0x80);
                    CdCheckpointSaveData.UnknownSavedByte failure = assertThrows(
                            CdCheckpointSaveData.UnknownSavedByte.class,
                            () -> load(1, zone, saved, port));
                    assertEquals(address, failure.nativeAddress());
                    assertEquals(sequence.subList(0, cut), port.appliedSteps());
                    assertEquals(saved, port.saved);
                }
            }
        }
        assertEquals(31, checked.size());
    }

    @Test
    void zoneZeroDoesNotReadUnknownMiniHeightOrFull() {
        CdCheckpointSaveData.State saved = saved(1, 0x2345, 0x0004122A,
                0xFF1591, 0xFF1542, 0xFF1544);
        Owner port = new Owner(saved, 0, 1);
        assertEquals(19, load(1, 0, saved, port).completedNativeStores().size());
        assertFalse(port.ledger.contains("R:MINI_SONIC"));
        assertFalse(port.ledger.contains("R:WATER_HEIGHT2"));
        assertFalse(port.ledger.contains("R:WATER_FULLSCREEN"));
        assertEquals(OptionalInt.of(0x56), port.raw(StoreTarget.WATER_ROUTINE));
    }

    @Test
    void zoneTwoRestores1256AliasAndBothRoutineInstructions() {
        Owner port = owner(2, 1);
        Result result = load(1, 2, port.saved, port);
        assertEquals(plan(2, 1), result.completedNativeStores());
        assertEquals(OptionalInt.of(0x1256), port.raw(StoreTarget.WATER_HEIGHT2));
        assertEquals(OptionalInt.of(0x56), port.raw(StoreTarget.WATER_ROUTINE));
        assertEquals(OptionalInt.of(0x9A), port.raw(StoreTarget.WATER_FULLSCREEN));
        assertEquals(2, port.applied.stream().filter(c -> c.target() == StoreTarget.WATER_ROUTINE).count());
        assertEquals(List.of(NativeStore.RESTORE_WATER_ROUTINE_FIRST,
                        NativeStore.RESTORE_WATER_ROUTINE_SECOND),
                result.completedNativeStores().stream().filter(s -> s.target() == StoreTarget.WATER_ROUTINE).toList());
        StoreCall second = port.applied.get(20);
        assertEquals(OptionalInt.of(0x56), second.expectedValue());
        assertEquals(0x56, second.value());
    }

    @Test
    void zoneSixReadsOnlyMiniConditionalAndKeepsOtherUnknownSavedFields() {
        CdCheckpointSaveData.State saved = saved(1, 0x2345, 0x0004122A, 0xFF1542, 0xFF1544);
        Owner port = new Owner(saved, 6, 1);
        Result result = load(1, 6, saved, port);
        assertEquals(plan(6, 1), result.completedNativeStores());
        assertEquals(OptionalInt.of(0xE1), port.raw(StoreTarget.MINI_SONIC));
        assertEquals(20, port.applied.size());
    }

    @Test
    void everyUnsignedZoneIsStructurallyAcceptedWithoutWorldAdmission() {
        for (int zone = 0; zone <= 255; zone++) {
            Owner port = owner(zone, 1);
            assertEquals(plan(zone, 1), load(1, zone, port.saved, port).completedNativeStores());
        }
    }

    @Test
    void everyUnsignedIncomingSpawnIncludingRawTwoHasTheNativeEntryBranch() {
        for (int incoming = 0; incoming <= 255; incoming++) {
            int rawIncoming = incoming;
            Owner port = owner(0, 0x80);
            if (incoming == 2) {
                assertThrows(WarpBoundary.class, () -> load(rawIncoming, 0, port.saved, port));
                assertTrue(port.ledger.isEmpty());
            } else {
                assertEquals(plan(0, 0x80), load(incoming, 0, port.saved, port).completedNativeStores());
            }
        }
        assertThrows(WarpBoundary.class, () -> load(2, -1, null, null));
    }

    @Test
    void everyUnsignedSavedSpawnControlsTheFinalSignGate() {
        for (int restored = 0; restored <= 255; restored++) {
            Owner port = owner(0, restored);
            Result result = load(1, 0, port.saved, port);
            assertEquals(restored >= 128, result.completedNativeStores().contains(NativeStore.SET_LEFT_BOUND));
            assertEquals(OptionalInt.of(restored), port.raw(StoreTarget.SPAWN_MODE));
        }
    }

    @Test
    void savedSpawnTwoDoesNotReenterWarpAndIncomingNegativeDoesNotDriveLeftGate() {
        Owner two = owner(0, 2);
        assertEquals(19, load(1, 0, two.saved, two).completedNativeStores().size());
        Owner positive = owner(0, 1);
        assertEquals(19, load(0xFF, 0, positive.saved, positive).completedNativeStores().size());
    }

    @Test
    void rawTimeLeadingByteAndSecondsUnderflowRemainUnnormalized() {
        int[][] cases = {
                {0x00050000, 0x0005FF3B}, {0x0004122A, 0x0004113B},
                {0xAABBCCDD, 0xAABBCB3B}, {0x80000000, 0x8000FF3B},
                {0xFF00FFEE, 0xFF00FE3B}, {0x00FF003B, 0x00FFFF3B},
                {0xFFFFFFFF, 0xFFFFFE3B}};
        for (int[] c : cases) {
            CdCheckpointSaveData.State saved = saved(1, 0x2345, c[0]);
            Owner port = new Owner(saved, 0, 1);
            load(1, 0, saved, port);
            assertEquals(c[1], port.rawTime);
            assertEquals(c[0], port.applied.get(5).value());
            assertEquals(59, port.applied.get(6).value());
            assertEquals(OptionalInt.of((c[0] >>> 8) & 255), port.applied.get(7).expectedValue());
        }
    }

    @Test
    void negativeRestoredSpawnLeftSubtractionWrapsOnlyTheWord() {
        int[][] cases = {{0, 0xFF60}, {159, 0xFFFF}, {160, 0}, {0xFFFF, 0xFF5F}};
        for (int[] c : cases) {
            CdCheckpointSaveData.State saved = saved(0x80, c[0], 0x00050000);
            Owner port = new Owner(saved, 0, 0x80);
            load(1, 0, saved, port);
            assertEquals(OptionalInt.of(c[1]), port.raw(StoreTarget.LEFT_BOUND));
            assertEquals(c[0], saved.read(SavedField.PRIMARY_X));
        }
    }

    @Test
    void moveClrAndImmediateStoresCanDefineUnknownDestinations() {
        Owner port = owner(2, 0x80);
        port.known.clear();
        load(1, 2, port.saved, port);
        assertEquals(OptionalInt.empty(), port.applied.get(0).expectedValue());
        assertEquals(OptionalInt.empty(), port.applied.get(3).expectedValue());
        assertEquals(OptionalInt.empty(), port.applied.get(5).expectedValue());
        assertEquals(OptionalInt.of(0x12), port.applied.get(7).expectedValue());
        assertEquals(OptionalInt.of(0x56), port.applied.get(20).expectedValue());
        assertEquals(OptionalInt.of(0x22A5), port.raw(StoreTarget.LEFT_BOUND));
    }

    @Test
    void unknownLiveTimeBeforeMoveLongDoesNotBlockNativeRestore() {
        Owner port = owner(0, 1);
        port.known.remove(StoreTarget.TIME_LONG);
        load(1, 0, port.saved, port);
        assertEquals(OptionalInt.empty(), port.applied.get(5).expectedValue());
        assertEquals(0x0004113B, port.rawTime);
    }

    @Test
    void unknownDestinationNoOpCannotClaimSynchronousPublication() {
        Owner port = owner(0, 1);
        port.known.remove(StoreTarget.TIME_LONG);
        port.noOpAt = 5;
        assertThrows(IllegalStateException.class, () -> load(1, 0, port.saved, port));
        assertEquals(6, port.attempted.size());
        assertEquals(5, port.applied.size());
        assertEquals(OptionalInt.empty(), port.raw(StoreTarget.TIME_LONG));
    }

    @Test
    void nativeSecondsRmwRequiresKnowledgeAtLine56Only() {
        Owner port = owner(0, 1);
        port.obscureAt = 7;
        port.obscureTarget = StoreTarget.TIME_SECONDS;
        UnknownLiveValue failure = assertThrows(UnknownLiveValue.class,
                () -> load(1, 0, port.saved, port));
        assertEquals(StoreTarget.TIME_SECONDS, failure.target());
        assertEquals(56, failure.nativeLine());
        assertEquals(plan(0, 1).subList(0, 7), port.appliedSteps());
        assertEquals(0x0004123B, port.rawTime);
    }

    @Test
    void finalNativeSpawnTstRequiresKnowledgeAtLine80AfterAllPriorStores() {
        Owner port = owner(0, 0x80);
        port.obscureAt = 19;
        port.obscureTarget = StoreTarget.SPAWN_MODE;
        UnknownLiveValue failure = assertThrows(UnknownLiveValue.class,
                () -> load(1, 0, port.saved, port));
        assertEquals(StoreTarget.SPAWN_MODE, failure.target());
        assertEquals(80, failure.nativeLine());
        assertEquals(19, port.applied.size());
        assertFalse(port.appliedSteps().contains(NativeStore.SET_LEFT_BOUND));
    }

    @Test
    void sameValueStoresStillCommitAndHavePostReturnObservations() {
        Owner port = owner(2, 1);
        port.fields.put(StoreTarget.RINGS, 0);
        port.fields.put(StoreTarget.LIVES_FLAGS, 0);
        load(1, 2, port.saved, port);
        assertEquals(OptionalInt.of(0), port.applied.get(3).expectedValue());
        assertEquals(0, port.applied.get(3).value());
        assertEquals(22, port.applied.size());
        assertEquals(4, port.ledger.stream().filter("R:WATER_ROUTINE"::equals).count());
        assertEquals(2, port.ledger.stream().filter("R:RINGS"::equals).count());
    }

    @Test
    void changedValueNoOpFailsAfterReturnedCommitWithoutRollback() {
        Owner port = owner(0, 1);
        port.noOpAt = 2;
        assertThrows(IllegalStateException.class, () -> load(1, 0, port.saved, port));
        assertEquals(3, port.attempted.size());
        assertEquals(2, port.applied.size());
        assertEquals(OptionalInt.of(0x2345), port.raw(StoreTarget.PRIMARY_X));
    }

    @Test
    void stalePostPublicationObservationFailsAndActualCommittedPrefixRemains() {
        Owner port = owner(0, 1);
        port.staleAt = 11;
        assertThrows(IllegalStateException.class, () -> load(1, 0, port.saved, port));
        assertEquals(12, port.applied.size());
        assertEquals(OptionalInt.of(0x1111), port.raw(StoreTarget.FG_X));
        assertEquals(0x0004113B, port.rawTime);
    }

    @Test
    void throwingAtEveryReachedStorePropagatesTheSameExceptionAndPrefix() {
        for (int zone : new int[]{0, 2, 6}) {
            List<NativeStore> expected = plan(zone, 0x80);
            for (int cut = 0; cut < expected.size(); cut++) {
                Owner port = owner(zone, 0x80);
                port.throwAt = cut;
                assertSame(port.failure, assertThrows(Marker.class,
                        () -> load(1, zone, port.saved, port)));
                assertEquals(expected.subList(0, cut), port.appliedSteps());
                assertEquals(cut + 1, port.attempted.size());
            }
        }
    }

    @Test
    void actualReadExceptionsPropagateBeforeThatStore() {
        Owner port = owner(0, 1);
        port.failReadAt = 2;
        assertSame(port.failure, assertThrows(Marker.class,
                () -> load(1, 0, port.saved, port)));
        assertEquals(2, port.applied.size());
        assertEquals(2, port.attempted.size());
    }

    @Test
    void currentSavedExceptionPropagatesWithoutTargetReads() {
        Owner port = owner(0, 1);
        port.failSaved = true;
        assertSame(port.failure, assertThrows(Marker.class,
                () -> load(1, 0, port.saved, port)));
        assertEquals(List.of("S"), port.ledger);
    }

    @Test
    void staleSavedStateFailsBeforeFirstReachedSourceAndStore() {
        Owner port = owner(0, 1);
        CdCheckpointSaveData.State requested = port.saved;
        port.saved = CdCheckpointSaveData.State.unknown();
        assertThrows(IllegalStateException.class, () -> load(1, 0, requested, port));
        assertEquals(List.of("S"), port.ledger);
        assertTrue(port.applied.isEmpty());
    }

    @Test
    void savedChangesAfterACommitStopBeforeNextNativeInstruction() {
        Owner port = owner(0, 1);
        CdCheckpointSaveData.State requested = port.saved;
        port.changeSavedAfter = 9;
        assertThrows(IllegalStateException.class, () -> load(1, 0, requested, port));
        assertEquals(10, port.applied.size());
        assertEquals(OptionalInt.of(0x0789), port.raw(StoreTarget.BOTTOM_BOUND));
        assertFalse(port.appliedSteps().contains(NativeStore.RESTORE_DEST_BOTTOM));
    }

    @Test
    void savedChangesDuringValidationReadPreventThatCommit() {
        Owner port = owner(0, 1);
        CdCheckpointSaveData.State requested = port.saved;
        port.changeSavedDuringReadAt = 2;
        assertThrows(IllegalStateException.class, () -> load(1, 0, requested, port));
        assertEquals(2, port.applied.size());
        assertEquals(2, port.attempted.size());
    }

    @Test
    void rootPrivateCasRejectsChangedActualOwnerAtTheReachedInstruction() {
        Owner port = owner(0, 1);
        port.casConflictAt = 11;
        assertThrows(IllegalStateException.class, () -> load(1, 0, port.saved, port));
        assertEquals(11, port.applied.size());
        assertEquals(12, port.attempted.size());
        assertNotEquals(OptionalInt.of(0x1111), port.raw(StoreTarget.FG_X));
    }

    @Test
    void resultAndCaptureAreImmutableMetadataAndGiveNoReplayAuthority() {
        Owner port = owner(2, 0x80);
        CdCheckpointSaveData.State saved = port.saved;
        Result result = load(1, 2, saved, port);
        Map<StoreTarget, OptionalInt> capture = port.capture();
        int count = port.applied.size();
        assertThrows(UnsupportedOperationException.class, () -> result.completedNativeStores().clear());
        assertThrows(UnsupportedOperationException.class, () -> capture.clear());
        assertEquals(saved, port.saved);
        port.open = false;
        StoreCall retained = port.applied.get(0);
        assertThrows(IllegalStateException.class, () -> port.commit(retained));
        assertEquals(count, port.applied.size());
        assertEquals(capture, port.capture());
        Owner foreign = owner(0, 1);
        foreign.open = false;
        assertThrows(IllegalStateException.class, () -> foreign.commit(retained));
        assertTrue(foreign.applied.isEmpty());
    }

    @Test
    void resultDefensivelyCopiesAndRejectsNullMetadata() {
        List<NativeStore> mutable = new ArrayList<>(List.of(NativeStore.CLEAR_RINGS));
        Result result = new Result(mutable);
        mutable.clear();
        assertEquals(List.of(NativeStore.CLEAR_RINGS), result.completedNativeStores());
        assertThrows(NullPointerException.class, () -> new Result(null));
        assertThrows(NullPointerException.class, () -> new Result(Arrays.asList((NativeStore) null)));
    }

    @Test
    void storeCallsRetainOptionalKnowledgeAndValidateOnlyKnownWidths() {
        StoreCall unknown = new StoreCall(StoreTarget.SPAWN_MODE, OptionalInt.empty(), 0xFF);
        assertTrue(unknown.expectedValue().isEmpty());
        assertThrows(NullPointerException.class, () -> new StoreCall(StoreTarget.RINGS, null, 0));
        assertThrows(NullPointerException.class, () -> new StoreCall(null, OptionalInt.empty(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new StoreCall(StoreTarget.SPAWN_MODE, OptionalInt.of(-1), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new StoreCall(StoreTarget.RINGS, OptionalInt.empty(), 0x10000));
        assertThrows(IllegalArgumentException.class,
                () -> new StoreCall(StoreTarget.SPAWN_MODE, OptionalInt.empty(), 256));
        StoreCall raw = new StoreCall(StoreTarget.TIME_LONG, OptionalInt.of(0xFFFFFFFF), 0x80000000);
        assertEquals(0x80000000, raw.value());
    }

    @Test
    void malformedScalarsAndNullArgumentsDoNotNormalizeIntoNativeEntry() {
        Owner port = owner(0, 1);
        for (int bad : new int[]{-1, 256, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> load(bad, 0, port.saved, port));
            assertThrows(IllegalArgumentException.class, () -> load(1, bad, port.saved, port));
        }
        assertTrue(port.ledger.isEmpty());
        assertThrows(NullPointerException.class, () -> load(1, 0, null, port));
        assertThrows(NullPointerException.class, () -> load(1, 0, port.saved, null));
    }

    @Test
    void noSavedOrNonownedFieldsFractionsOrExtraOwnerWritesChange() {
        Owner port = owner(0, 0x80);
        CdCheckpointSaveData.State before = port.saved;
        byte[] mask = before.knownMask().copy();
        int unrelated = port.nonOwnedSentinel;
        Map<StoreTarget, Integer> fractions = Map.copyOf(port.fractions);
        Map<StoreTarget, OptionalInt> prior = port.capture();
        load(1, 0, before, port);
        assertSame(before, port.saved);
        assertArrayEquals(mask, before.knownMask().copy());
        assertEquals(unrelated, port.nonOwnedSentinel);
        assertEquals(fractions, port.fractions);
        for (StoreTarget skipped : List.of(StoreTarget.MINI_SONIC,
                StoreTarget.WATER_HEIGHT2, StoreTarget.WATER_FULLSCREEN)) {
            assertEquals(prior.get(skipped), port.raw(skipped));
        }
        assertEquals(0x0004122A, before.read(SavedField.TIME_LONG));
        assertEquals(0x2345, before.read(SavedField.PRIMARY_X));
    }

    @Test
    void observationCannotProveExecutionOfADishonestSameValueNoOp() {
        Owner port = owner(0, 1);
        port.fields.put(StoreTarget.RINGS, 0);
        port.noOpAt = 3;
        Result result = load(1, 0, port.saved, port);
        // Deliberately invalid Port: unchanged value observations cannot prove an instruction ran.
        // The real Root adapter must own once-only native cursor/phase/CAS authority.
        assertTrue(result.completedNativeStores().contains(NativeStore.CLEAR_RINGS));
        assertEquals(18, port.applied.size());
        assertEquals(19, port.attempted.size());
    }

    @Test
    void rootPrivateCursorRejectsWrongOrderAndRetainedDuplicateCalls() {
        Owner port = owner(0, 1);
        assertThrows(IllegalStateException.class, () -> port.commit(
                new StoreCall(StoreTarget.RINGS, port.raw(StoreTarget.RINGS), 0)));
        assertTrue(port.applied.isEmpty());
        StoreCall first = new StoreCall(StoreTarget.SPAWN_MODE, port.raw(StoreTarget.SPAWN_MODE), 1);
        port.commit(first);
        assertThrows(IllegalStateException.class, () -> port.commit(first));
        assertEquals(1, port.applied.size());
    }

    private static List<NativeStore> plan(int zone, int savedSpawn) {
        List<NativeStore> steps = new ArrayList<>(SCHEDULE.subList(0, 19));
        if (zone == 6) steps.add(SCHEDULE.get(19));
        if (zone == 2) steps.addAll(SCHEDULE.subList(20, 23));
        if ((savedSpawn & 0x80) != 0) steps.add(SCHEDULE.get(23));
        return List.copyOf(steps);
    }

    private static Owner owner(int zone, int savedSpawn) {
        return new Owner(saved(savedSpawn, 0x2345, 0x0004122A), zone, savedSpawn);
    }

    private static CdCheckpointSaveData.State saved(int spawn, int x, int time, int... missing) {
        byte[] bytes = new byte[31];
        byte[] known = new byte[31];
        Arrays.fill(known, (byte) 1);
        put(bytes, 0xFF1523, 1, spawn);
        put(bytes, 0xFF1524, 2, x);
        put(bytes, 0xFF1526, 2, 0xFEDC);
        put(bytes, 0xFF152A, 4, time);
        put(bytes, 0xFF1530, 2, 0x0789);
        int[] cameras = {0x1111, 0x2222, 0x3333, 0x4444, 0x5555, 0x6666, 0x7777, 0x8888};
        for (int i = 0; i < cameras.length; i++) put(bytes, 0xFF1532 + 2 * i, 2, cameras[i]);
        put(bytes, 0xFF1542, 2, 0x1256);
        put(bytes, 0xFF1544, 1, 0x9A);
        put(bytes, 0xFF1591, 1, 0xE1);
        for (int address : missing) known[index(address)] = 0;
        return CdCheckpointSaveData.State.supplied(CdAssetBytes.copyOf(bytes), CdAssetBytes.copyOf(known));
    }

    private static void put(byte[] bytes, int address, int width, int value) {
        for (int part = 0; part < width; part++) {
            bytes[index(address + part)] = (byte) (value >>> (8 * (width - part - 1)));
        }
    }

    private static int index(int address) {
        if (address >= 0xFF1523 && address <= 0xFF1527) return address - 0xFF1523;
        if (address >= 0xFF152A && address <= 0xFF152D) return 5 + address - 0xFF152A;
        if (address >= 0xFF1530 && address <= 0xFF1544) return 9 + address - 0xFF1530;
        if (address == 0xFF1591) return 30;
        throw new IllegalArgumentException("test saved address");
    }

    private static final class Marker extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    /** Disposable test owners only: no real Frame, Presentation, RAM, capture or load phase. */
    private static final class Owner implements Port {
        private CdCheckpointSaveData.State saved;
        private final EnumMap<StoreTarget, Integer> fields = new EnumMap<>(StoreTarget.class);
        private final EnumMap<StoreTarget, Integer> fractions = new EnumMap<>(StoreTarget.class);
        private final EnumSet<StoreTarget> known = EnumSet.allOf(StoreTarget.class);
        private final List<NativeStore> expected;
        private final List<String> ledger = new ArrayList<>();
        private final List<StoreCall> attempted = new ArrayList<>();
        private final List<StoreCall> applied = new ArrayList<>();
        private final List<NativeStore> appliedInstructions = new ArrayList<>();
        private final Marker failure = new Marker();
        private int cursor;
        private int rawTime = 0xDDEEAA11;
        private int nonOwnedSentinel = 0xF0E1D2C3;
        private int throwAt = -1;
        private int noOpAt = -1;
        private int staleAt = -1;
        private int failReadAt = -1;
        private int obscureAt = -1;
        private StoreTarget obscureTarget;
        private int changeSavedAfter = -1;
        private int changeSavedDuringReadAt = -1;
        private int casConflictAt = -1;
        private boolean failSaved;
        private boolean open = true;
        private StoreCall last;

        private Owner(CdCheckpointSaveData.State saved, int zone, int restoredSpawn) {
            this.saved = saved;
            this.expected = plan(zone, restoredSpawn);
            for (StoreTarget target : StoreTarget.values()) {
                fields.put(target, target.width() == 1 ? 0xA5 : 0x0A5A);
                if (target.width() == 2) fractions.put(target, 0xBEEF);
            }
        }

        @Override
        public CdCheckpointSaveData.State currentSaved() {
            ledger.add("S");
            if (failSaved) throw failure;
            return saved;
        }

        @Override
        public OptionalInt read(StoreTarget target) {
            ledger.add("R:" + target.name());
            if (cursor == failReadAt) throw failure;
            if (cursor == changeSavedDuringReadAt) saved = CdCheckpointSaveData.State.unknown();
            if (cursor == obscureAt && target == obscureTarget) return OptionalInt.empty();
            if (last != null && cursor == staleAt + 1 && target == last.target()) return last.expectedValue();
            return raw(target);
        }

        private OptionalInt raw(StoreTarget target) {
            if (!known.contains(target)) return OptionalInt.empty();
            return OptionalInt.of(switch (target) {
                case TIME_LONG -> rawTime;
                case TIME_FRAMES -> rawTime & 255;
                case TIME_SECONDS -> (rawTime >>> 8) & 255;
                default -> fields.get(target);
            });
        }

        @Override
        public void commit(StoreCall call) {
            ledger.add("C:" + call.target().name());
            if (!open || cursor >= expected.size() || call.target() != expected.get(cursor).target()) {
                throw new IllegalStateException("test owner phase/cursor rejected plain data");
            }
            attempted.add(call);
            if (cursor == casConflictAt) fields.put(call.target(), fields.get(call.target()) ^ 1);
            if (!raw(call.target()).equals(call.expectedValue())) {
                throw new IllegalStateException("test owner actual-current CAS rejected");
            }
            if (cursor == throwAt) throw failure;
            if (cursor != noOpAt) {
                apply(call.target(), call.value());
                applied.add(call);
                appliedInstructions.add(expected.get(cursor));
            }
            if (cursor == changeSavedAfter) saved = CdCheckpointSaveData.State.unknown();
            cursor++;
            last = call;
        }

        private void apply(StoreTarget target, int value) {
            known.add(target);
            switch (target) {
                case TIME_LONG -> {
                    rawTime = value;
                    known.add(StoreTarget.TIME_FRAMES);
                    known.add(StoreTarget.TIME_SECONDS);
                }
                case TIME_FRAMES -> rawTime = (rawTime & 0xFFFFFF00) | value;
                case TIME_SECONDS -> rawTime = (rawTime & 0xFFFF00FF) | (value << 8);
                default -> fields.put(target, value);
            }
        }

        private List<NativeStore> appliedSteps() {
            return List.copyOf(appliedInstructions);
        }

        private Map<StoreTarget, OptionalInt> capture() {
            EnumMap<StoreTarget, OptionalInt> values = new EnumMap<>(StoreTarget.class);
            for (StoreTarget target : StoreTarget.values()) values.put(target, raw(target));
            return Map.copyOf(values);
        }
    }
}

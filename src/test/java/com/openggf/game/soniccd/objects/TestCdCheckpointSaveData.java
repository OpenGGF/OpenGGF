package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.objects.CdCheckpointSaveData.Port;
import com.openggf.game.soniccd.objects.CdCheckpointSaveData.Result;
import com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField;
import com.openggf.game.soniccd.objects.CdCheckpointSaveData.Source;
import com.openggf.game.soniccd.objects.CdCheckpointSaveData.State;
import com.openggf.game.soniccd.objects.CdCheckpointSaveData.Store;
import com.openggf.game.soniccd.objects.CdCheckpointSaveData.StoreCall;
import com.openggf.game.soniccd.objects.CdCheckpointSaveData.UnknownSavedByte;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ROM-free literal oracle for N 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * Checkpoint/Main.asm:18-43 and Load Saved Data.asm:44-87. All tests UNRUN by author.
 * Frozen before production existed; no runtime, body, restart or media proof.
 */
class TestCdCheckpointSaveData {
    private static final int[] OWNED = {
            0xFF1523, 0xFF1524, 0xFF1525, 0xFF1526, 0xFF1527,
            0xFF152A, 0xFF152B, 0xFF152C, 0xFF152D,
            0xFF1530, 0xFF1531, 0xFF1532, 0xFF1533, 0xFF1534, 0xFF1535,
            0xFF1536, 0xFF1537, 0xFF1538, 0xFF1539, 0xFF153A, 0xFF153B,
            0xFF153C, 0xFF153D, 0xFF153E, 0xFF153F, 0xFF1540, 0xFF1541,
            0xFF1542, 0xFF1543, 0xFF1544, 0xFF1591
    };
    private static final Source[] READS = {
            Source.SPAWN_MODE, Source.PRIMARY_X, Source.PRIMARY_Y, Source.WATER_ROUTINE,
            Source.BOTTOM_BOUND, Source.FG_X, Source.FG_Y, Source.BG_X, Source.BG_Y,
            Source.BG2_X, Source.BG2_Y, Source.BG3_X, Source.BG3_Y,
            Source.WATER_HEIGHT2, Source.WATER_ROUTINE, Source.WATER_FULLSCREEN,
            Source.TIME_LONG, Source.MINI_SONIC
    };
    private static final Store[] STORES = {
            Store.SAVED_SPAWN, Store.SAVED_X, Store.SAVED_Y, Store.WATER_ROUTINE_FIRST,
            Store.BOTTOM_BOUND, Store.FG_X, Store.FG_Y, Store.BG_X, Store.BG_Y,
            Store.BG2_X, Store.BG2_Y, Store.BG3_X, Store.BG3_Y,
            Store.WATER_HEIGHT_WORD, Store.WATER_ROUTINE_SECOND, Store.WATER_FULLSCREEN,
            Store.TIME_LONG, Store.MINI_SONIC
    };
    private static final int[] DESTINATIONS = {
            0xFF1523, 0xFF1524, 0xFF1526, 0xFF1543, 0xFF1530,
            0xFF1532, 0xFF1534, 0xFF1536, 0xFF1538, 0xFF153A,
            0xFF153C, 0xFF153E, 0xFF1540, 0xFF1542, 0xFF1543,
            0xFF1544, 0xFF152A, 0xFF1591
    };
    private static final int[] STORE_WIDTHS = {
            1, 2, 2, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 4, 1
    };
    private static final int[] RAW = {
            0x01, 0xABCD, 0xFEDC, 0x56, 0x0710,
            0x0123, 0x0456, 0x1357, 0x2468, 0xAAAA, 0xBBBB, 0xCCCC, 0xDDDD,
            0x1234, 0x56, 0x9A, 0x00050101, 0xE1
    };
    private static final int[] SAVED = {
            0x01, 0xAB, 0xCD, 0xFE, 0xDC, 0x00, 0x05, 0x00, 0x00,
            0x07, 0x10, 0x01, 0x23, 0x04, 0x56, 0x13, 0x57, 0x24, 0x68,
            0xAA, 0xAA, 0xBB, 0xBB, 0xCC, 0xCC, 0xDD, 0xDD, 0x12, 0x56, 0x9A, 0xE1
    };

    @Test
    void literalSourceMetadataHasSeventeenActualReads() {
        Source[] sources = {
                Source.SPAWN_MODE, Source.PRIMARY_X, Source.PRIMARY_Y, Source.WATER_ROUTINE,
                Source.BOTTOM_BOUND, Source.FG_X, Source.FG_Y, Source.BG_X, Source.BG_Y,
                Source.BG2_X, Source.BG2_Y, Source.BG3_X, Source.BG3_Y,
                Source.WATER_HEIGHT2, Source.WATER_FULLSCREEN, Source.TIME_LONG, Source.MINI_SONIC
        };
        int[] addresses = {
                0xFF1522, 0xFFD008, 0xFFD00C, 0xFFF64D, 0xFFF72E,
                0xFFF700, 0xFFF704, 0xFFF708, 0xFFF70C, 0xFFF710,
                0xFFF714, 0xFFF718, 0xFFF71C, 0xFFF648, 0xFFF64E, 0xFF1514, 0xFF195B
        };
        int[] widths = {1, 2, 2, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 4, 1};
        assertArrayEquals(sources, Source.values());
        for (int i = 0; i < 17; i++) {
            assertEquals(addresses[i], sources[i].nativeAddress());
            assertEquals(widths[i], sources[i].width());
        }
    }

    @Test
    void literalStoreDirectoryIsEighteenNativeOrderedInstructions() {
        assertArrayEquals(STORES, Store.values());
        for (int i = 0; i < 18; i++) {
            assertEquals(READS[i], STORES[i].source());
            assertEquals(DESTINATIONS[i], STORES[i].nativeAddress());
            assertEquals(STORE_WIDTHS[i], STORES[i].width());
        }
    }

    @Test
    void literalSavedReadsIncludeTheOverlappingWord() {
        SavedField[] fields = {
                SavedField.SPAWN_MODE, SavedField.PRIMARY_X, SavedField.PRIMARY_Y,
                SavedField.WATER_ROUTINE, SavedField.BOTTOM_BOUND, SavedField.FG_X, SavedField.FG_Y,
                SavedField.BG_X, SavedField.BG_Y, SavedField.BG2_X, SavedField.BG2_Y,
                SavedField.BG3_X, SavedField.BG3_Y, SavedField.WATER_HEIGHT_WORD,
                SavedField.WATER_FULLSCREEN, SavedField.TIME_LONG, SavedField.MINI_SONIC
        };
        int[] addresses = {
                0xFF1523, 0xFF1524, 0xFF1526, 0xFF1543, 0xFF1530,
                0xFF1532, 0xFF1534, 0xFF1536, 0xFF1538, 0xFF153A,
                0xFF153C, 0xFF153E, 0xFF1540, 0xFF1542, 0xFF1544, 0xFF152A, 0xFF1591
        };
        int[] widths = {1, 2, 2, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 4, 1};
        assertArrayEquals(fields, SavedField.values());
        for (int i = 0; i < 17; i++) {
            assertEquals(addresses[i], fields[i].nativeAddress());
            assertEquals(widths[i], fields[i].width());
        }
    }

    @Test
    void fullNativeSaveCompletesExactlyThirtyOneOwnedBytes() {
        State before = State.unknown();
        RecordingPort port = new RecordingPort(before, RAW);
        Result result = CdCheckpointSaveData.save(before, port);
        assertEquals(Arrays.asList(STORES), result.completedStores());
        assertEquals(Arrays.asList(READS), port.reads);
        assertEquals(18, port.attempts.size());
        assertEquals(18, port.published.size());
        assertSame(port.state, result.state());
        for (int i = 0; i < 31; i++) {
            assertEquals(OptionalInt.empty(), before.knownByte(OWNED[i]));
            assertEquals(OptionalInt.of(SAVED[i]), result.state().knownByte(OWNED[i]));
        }
        assertArrayEquals(ones(), result.state().knownMask().copy());
        assertEquals(0x1256, result.state().read(SavedField.WATER_HEIGHT_WORD));
        assertEquals(0x56, result.state().read(SavedField.WATER_ROUTINE));
        assertEquals(0x9A, result.state().read(SavedField.WATER_FULLSCREEN));
        assertEquals(0x00050000, result.state().read(SavedField.TIME_LONG));
        assertArrayEquals(RAW, port.values);
    }

    @Test
    void eachReadObservesExactlyThePreviouslyPublishedNativePrefix() {
        RecordingPort port = new RecordingPort(State.unknown(), RAW);
        CdCheckpointSaveData.save(port.state, port);
        for (int i = 0; i < 18; i++) {
            assertEquals(oracle(new byte[31], new byte[31], i, RAW), port.observed.get(i));
            StoreCall call = port.attempts.get(i);
            assertEquals(STORES[i], call.store());
            assertEquals(i == 16 ? 0x00050000 : RAW[i], call.rawValue());
            assertEquals(port.observed.get(i), call.expectedState());
            assertEquals(oracle(new byte[31], new byte[31], i + 1, RAW), call.finalState());
        }
    }

    @Test
    void unknownFieldsRejectTheirFirstActualByte() {
        SavedField[] fields = {
                SavedField.SPAWN_MODE, SavedField.PRIMARY_X, SavedField.PRIMARY_Y,
                SavedField.WATER_ROUTINE, SavedField.BOTTOM_BOUND, SavedField.FG_X, SavedField.FG_Y,
                SavedField.BG_X, SavedField.BG_Y, SavedField.BG2_X, SavedField.BG2_Y,
                SavedField.BG3_X, SavedField.BG3_Y, SavedField.WATER_HEIGHT_WORD,
                SavedField.WATER_FULLSCREEN, SavedField.TIME_LONG, SavedField.MINI_SONIC
        };
        int[] first = {
                0xFF1523, 0xFF1524, 0xFF1526, 0xFF1543, 0xFF1530,
                0xFF1532, 0xFF1534, 0xFF1536, 0xFF1538, 0xFF153A,
                0xFF153C, 0xFF153E, 0xFF1540, 0xFF1542, 0xFF1544, 0xFF152A, 0xFF1591
        };
        for (int i = 0; i < 17; i++) {
            SavedField field = fields[i];
            UnknownSavedByte failure = assertThrows(UnknownSavedByte.class, () -> State.unknown().read(field));
            assertEquals(first[i], failure.nativeAddress());
        }
    }

    @Test
    void partialWordsAndLongsFailAtTheFirstUnknownAddressInReadOrder() {
        byte[] data = new byte[31];
        byte[] mask = ones();
        mask[2] = 0;
        State xLowUnknown = supplied(data, mask);
        assertEquals(0xFF1525, assertThrows(UnknownSavedByte.class,
                () -> xLowUnknown.read(SavedField.PRIMARY_X)).nativeAddress());
        mask[1] = 0;
        State xBothUnknown = supplied(data, mask);
        assertEquals(0xFF1524, assertThrows(UnknownSavedByte.class,
                () -> xBothUnknown.read(SavedField.PRIMARY_X)).nativeAddress());
        mask = ones();
        mask[7] = 0;
        mask[8] = 0;
        State time = supplied(data, mask);
        assertEquals(0xFF152C, assertThrows(UnknownSavedByte.class,
                () -> time.read(SavedField.TIME_LONG)).nativeAddress());
        mask = ones();
        mask[27] = 0;
        data[28] = 0x56;
        State water = supplied(data, mask);
        assertEquals(0x56, water.read(SavedField.WATER_ROUTINE));
        assertEquals(0xFF1542, assertThrows(UnknownSavedByte.class,
                () -> water.read(SavedField.WATER_HEIGHT_WORD)).nativeAddress());
        mask[27] = 1;
        mask[28] = 0;
        State lowWater = supplied(data, mask);
        assertEquals(0xFF1543, assertThrows(UnknownSavedByte.class,
                () -> lowWater.read(SavedField.WATER_HEIGHT_WORD)).nativeAddress());
    }

    @Test
    void nonzeroPartiallyKnownSeedPreservesEveryUnwrittenByteAndMask() {
        byte[] data = new byte[31];
        byte[] mask = new byte[31];
        for (int i = 0; i < 31; i++) {
            data[i] = (byte) (0x80 + i);
            mask[i] = (byte) (i % 2);
        }
        State before = supplied(data, mask);
        RecordingPort port = new RecordingPort(before, RAW);
        Result result = CdCheckpointSaveData.save(before, port);
        for (int i = 0; i < 18; i++) {
            assertEquals(oracle(data, mask, i, RAW), port.observed.get(i));
            assertEquals(oracle(data, mask, i + 1, RAW), port.attempts.get(i).finalState());
        }
        assertEquals(oracle(data, mask, 18, RAW), result.state());
        assertEquals(supplied(data, mask), before);
    }

    @Test
    void secondRoutineReadActuallyOccursAfterTheAliasingWordStore() {
        int[] values = RAW.clone();
        values[3] = 0x78;
        values[14] = 0x56;
        RecordingPort port = new RecordingPort(State.unknown(), values);
        Result result = CdCheckpointSaveData.save(port.state, port);
        assertEquals(0x78, port.observed.get(4).read(SavedField.WATER_ROUTINE));
        assertEquals(0x34, port.observed.get(14).read(SavedField.WATER_ROUTINE));
        assertEquals(0x1234, port.observed.get(14).read(SavedField.WATER_HEIGHT_WORD));
        assertEquals(0x1256, port.observed.get(15).read(SavedField.WATER_HEIGHT_WORD));
        assertEquals(0x1256, result.state().read(SavedField.WATER_HEIGHT_WORD));
        assertEquals(List.of(Source.WATER_ROUTINE, Source.WATER_ROUTINE),
                List.of(port.reads.get(3), port.reads.get(14)));
    }

    @Test
    void timeClampsUsingUnsignedLongBitsWithoutChangingLiveTime() {
        int[] inputs = {0, 0x0004FFFE, 0x0004FFFF, 0x00050000, 0x00050001, 0x80000000, 0xFFFFFFFF};
        int[] outputs = {0, 0x0004FFFE, 0x0004FFFF, 0x00050000, 0x00050000, 0x00050000, 0x00050000};
        for (int i = 0; i < inputs.length; i++) {
            int[] values = RAW.clone();
            values[16] = inputs[i];
            RecordingPort port = new RecordingPort(State.unknown(), values);
            assertEquals(outputs[i], CdCheckpointSaveData.save(port.state, port).state().read(SavedField.TIME_LONG));
            assertEquals(inputs[i], port.values[16]);
        }
    }

    @Test
    void unsignedByteAndWordMaximaAreAcceptedAtEveryRead() {
        int[] values = {
                0xFF, 0xFFFF, 0xFFFF, 0xFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF,
                0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFF, 0xFF, 0xFFFFFFFF, 0xFF
        };
        RecordingPort port = new RecordingPort(State.unknown(), values);
        State state = CdCheckpointSaveData.save(port.state, port).state();
        assertEquals(0xFF, state.read(SavedField.SPAWN_MODE));
        assertEquals(0xFFFF, state.read(SavedField.PRIMARY_X));
        assertEquals(0xFFFF, state.read(SavedField.PRIMARY_Y));
        assertEquals(oracle(new byte[31], new byte[31], 18, values), state);
    }

    @Test
    void widerOrNegativeByteAdapterValuesRejectAtTheirReachedRead() {
        int[] positions = {0, 3, 14, 15, 17};
        for (int position : positions) {
            for (int invalid : new int[]{-1, 0x100, Integer.MAX_VALUE}) {
                assertInvalidAdapter(position, invalid);
            }
        }
    }

    @Test
    void widerOrNegativeWordAdapterValuesRejectAtTheirReachedRead() {
        int[] positions = {1, 2, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13};
        for (int position : positions) {
            for (int invalid : new int[]{-1, 0x10000, Integer.MAX_VALUE}) {
                assertInvalidAdapter(position, invalid);
            }
        }
    }

    @Test
    void sourceExceptionAtEachOfEighteenReadsRetainsExactlyTheCompletedPrefix() {
        for (int position = 0; position < 18; position++) {
            RecordingPort port = new RecordingPort(State.unknown(), RAW);
            port.failReadAt = position;
            assertSame(port.failure, assertThrows(RuntimeException.class,
                    () -> CdCheckpointSaveData.save(State.unknown(), port)));
            assertPrefix(port, position, position + 1, position);
        }
    }

    @Test
    void publisherExceptionAtEachOfEighteenStoresRetainsExactlyTheCompletedPrefix() {
        for (int position = 0; position < 18; position++) {
            RecordingPort port = new RecordingPort(State.unknown(), RAW);
            port.failStoreAt = position;
            assertSame(port.failure, assertThrows(RuntimeException.class,
                    () -> CdCheckpointSaveData.save(State.unknown(), port)));
            assertPrefix(port, position, position + 1, position + 1);
        }
    }

    @Test
    void exceptionAfterActualPublicationPropagatesWithoutRollingBack() {
        for (int position = 0; position < 18; position++) {
            RecordingPort port = new RecordingPort(State.unknown(), RAW);
            port.failAfterPublicationAt = position;
            assertSame(port.failure, assertThrows(RuntimeException.class,
                    () -> CdCheckpointSaveData.save(State.unknown(), port)));
            assertPrefix(port, position + 1, position + 1, position + 1);
        }
    }

    @Test
    void missingWaterFailsAfterSavedSpawnAndBothPositionWords() {
        RecordingPort port = new RecordingPort(State.unknown(), RAW);
        port.failReadAt = 3;
        assertSame(port.failure, assertThrows(RuntimeException.class,
                () -> CdCheckpointSaveData.save(State.unknown(), port)));
        assertEquals(List.of(Source.SPAWN_MODE, Source.PRIMARY_X, Source.PRIMARY_Y, Source.WATER_ROUTINE), port.reads);
        assertEquals(List.of(Store.SAVED_SPAWN, Store.SAVED_X, Store.SAVED_Y), port.published);
        assertEquals(0xABCD, port.state.read(SavedField.PRIMARY_X));
        assertEquals(0xFEDC, port.state.read(SavedField.PRIMARY_Y));
        assertEquals(OptionalInt.empty(), port.state.knownByte(0xFF1543));
    }

    @Test
    void missingTimeFailsAfterTheCompleteWaterAndCameraPrefix() {
        RecordingPort port = new RecordingPort(State.unknown(), RAW);
        port.failReadAt = 16;
        assertThrows(RuntimeException.class, () -> CdCheckpointSaveData.save(State.unknown(), port));
        assertPrefix(port, 16, 17, 16);
        assertEquals(0x1256, port.state.read(SavedField.WATER_HEIGHT_WORD));
        assertEquals(OptionalInt.empty(), port.state.knownByte(0xFF152A));
        assertEquals(OptionalInt.empty(), port.state.knownByte(0xFF1591));
    }

    @Test
    void noOpPublicationAtEveryChangingStorePreventsASuccessResult() {
        for (int position = 0; position < 18; position++) {
            RecordingPort port = new RecordingPort(State.unknown(), RAW);
            port.noOpAt = position;
            assertThrows(IllegalStateException.class, () -> CdCheckpointSaveData.save(State.unknown(), port));
            assertPrefix(port, position, position + 1, position + 1);
        }
    }

    @Test
    void mismatchedPublicationAtEveryStorePreventsASuccessResult() {
        for (int position = 0; position < 18; position++) {
            RecordingPort port = new RecordingPort(State.unknown(), RAW);
            port.wrongAt = position;
            assertThrows(IllegalStateException.class, () -> CdCheckpointSaveData.save(State.unknown(), port));
            assertEquals(position + 1, port.reads.size());
            assertEquals(position + 1, port.attempts.size());
            assertEquals(State.unknown(), port.state);
        }
    }

    @Test
    void staleOrMissingInitialPublicationRejectsBeforeAnySourceRead() {
        RecordingPort stale = new RecordingPort(supplied(new byte[31], ones()), RAW);
        assertThrows(IllegalStateException.class, () -> CdCheckpointSaveData.save(State.unknown(), stale));
        assertTrue(stale.reads.isEmpty());
        assertTrue(stale.attempts.isEmpty());
        RecordingPort missing = new RecordingPort(null, RAW);
        assertThrows(IllegalStateException.class, () -> CdCheckpointSaveData.save(State.unknown(), missing));
        assertTrue(missing.reads.isEmpty());
        assertTrue(missing.attempts.isEmpty());
    }

    @Test
    void sameValueStoresAreNotElidedEvenWhenEveryStateIsEqual() {
        State before = supplied(new byte[31], ones());
        RecordingPort port = new RecordingPort(before, new int[18]);
        Result result = CdCheckpointSaveData.save(before, port);
        assertEquals(before, result.state());
        assertEquals(Arrays.asList(STORES), port.published);
        assertEquals(Arrays.asList(STORES), result.completedStores());
        assertEquals(Arrays.asList(READS), port.reads);
        for (StoreCall call : port.attempts) {
            assertEquals(before, call.expectedState());
            assertEquals(before, call.finalState());
        }
    }

    @Test
    void retainedSnapshotsAndFreshReplayFromTheSameBeforeAreValueOnly() {
        byte[] data = new byte[31];
        Arrays.fill(data, (byte) 0xA5);
        State before = supplied(data, ones());
        int beforeHash = before.hashCode();
        RecordingPort first = new RecordingPort(before, RAW);
        Result firstResult = CdCheckpointSaveData.save(before, first);
        State retainedPrefix = first.attempts.get(2).finalState();
        RecordingPort fresh = new RecordingPort(supplied(data, ones()), RAW);
        Result replay = CdCheckpointSaveData.save(fresh.state, fresh);
        assertEquals(firstResult, replay);
        assertEquals(first.attempts, fresh.attempts);
        assertEquals(oracle(data, ones(), 3, RAW), retainedPrefix);
        assertEquals(supplied(data, ones()), before);
        assertEquals(beforeHash, before.hashCode());
        assertEquals(18, fresh.reads.size());
    }

    @Test
    void suppliedBytesAndExportedMaskHaveNoMutableAlias() {
        byte[] data = new byte[31];
        byte[] mask = ones();
        data[0] = (byte) 0xE7;
        CdAssetBytes immutableData = CdAssetBytes.copyOf(data);
        CdAssetBytes immutableMask = CdAssetBytes.copyOf(mask);
        State state = State.supplied(immutableData, immutableMask);
        data[0] = 0;
        mask[0] = 0;
        byte[] exported = state.knownMask().copy();
        exported[0] = 0;
        assertEquals(OptionalInt.of(0xE7), state.knownByte(0xFF1523));
        assertEquals(1, state.knownMask().unsignedByte(0));
        assertEquals(31, state.knownMask().length());
    }

    @Test
    void equalityAndHashIncludeKnowledgeAndCanonicalizeUnknownBackingBytes() {
        byte[] nonzero = new byte[31];
        Arrays.fill(nonzero, (byte) 0xFF);
        State unknownNonzero = supplied(nonzero, new byte[31]);
        assertEquals(State.unknown(), unknownNonzero);
        assertEquals(State.unknown().hashCode(), unknownNonzero.hashCode());
        assertNotEquals(State.unknown(), supplied(new byte[31], ones()));
        assertNotEquals(supplied(new byte[31], ones()), supplied(nonzero, ones()));
        State equal = supplied(nonzero.clone(), ones());
        assertEquals(equal, supplied(nonzero, ones()));
        assertEquals(equal.hashCode(), supplied(nonzero, ones()).hashCode());
        assertNotEquals(equal, null);
        assertNotEquals(equal, "saved RAM");
    }

    @Test
    void knownZeroIsDifferentFromUnknownWithoutExportingUnknownRam() {
        State known = supplied(new byte[31], ones());
        assertEquals(OptionalInt.of(0), known.knownByte(0xFF1523));
        assertEquals(OptionalInt.empty(), State.unknown().knownByte(0xFF1523));
        assertEquals(0, known.read(SavedField.PRIMARY_X));
        assertThrows(UnknownSavedByte.class, () -> State.unknown().read(SavedField.PRIMARY_X));
    }

    @Test
    void nonownedGapsAndNoncanonicalAddressesAreBadInput() {
        int[] outside = {
                0, 0xFF1522, 0xFF1528, 0xFF1529, 0xFF152E, 0xFF152F,
                0xFF1545, 0xFF1590, 0xFF1592, 0xFFFF1523, 0x01FF1523, -1
        };
        for (int address : outside) {
            assertThrows(IllegalArgumentException.class, () -> State.unknown().knownByte(address));
        }
        assertEquals(31, OWNED.length);
    }

    @Test
    void factoriesRejectNullWrongLengthsAndNonbinaryMasks() {
        CdAssetBytes valid = CdAssetBytes.copyOf(new byte[31]);
        assertThrows(NullPointerException.class, () -> State.supplied(null, valid));
        assertThrows(NullPointerException.class, () -> State.supplied(valid, null));
        for (int length : new int[]{0, 30, 32}) {
            CdAssetBytes wrong = CdAssetBytes.copyOf(new byte[length]);
            assertThrows(IllegalArgumentException.class, () -> State.supplied(wrong, valid));
            assertThrows(IllegalArgumentException.class, () -> State.supplied(valid, wrong));
        }
        for (int value : new int[]{2, 0x7F, 0x80, 0xFF}) {
            byte[] mask = ones();
            mask[30] = (byte) value;
            assertThrows(IllegalArgumentException.class, () -> State.supplied(valid, CdAssetBytes.copyOf(mask)));
        }
    }

    @Test
    void publicCallsRejectNullAndInvalidRawWidths() {
        State state = State.unknown();
        assertThrows(NullPointerException.class, () -> state.read(null));
        assertThrows(NullPointerException.class, () -> CdCheckpointSaveData.save(null, new RecordingPort(state, RAW)));
        assertThrows(NullPointerException.class, () -> CdCheckpointSaveData.save(state, null));
        assertThrows(NullPointerException.class, () -> new StoreCall(null, 0, state, state));
        assertThrows(NullPointerException.class, () -> new StoreCall(Store.SAVED_X, 0, null, state));
        assertThrows(NullPointerException.class, () -> new StoreCall(Store.SAVED_X, 0, state, null));
        for (int invalid : new int[]{-1, 0x100}) {
            assertThrows(IllegalArgumentException.class, () -> new StoreCall(Store.SAVED_SPAWN, invalid, state, state));
        }
        for (int invalid : new int[]{-1, 0x10000}) {
            assertThrows(IllegalArgumentException.class, () -> new StoreCall(Store.SAVED_X, invalid, state, state));
        }
        assertEquals(-1, new StoreCall(Store.TIME_LONG, -1, state, state).rawValue());
        assertThrows(NullPointerException.class, () -> new Result(null, List.of()));
        assertThrows(NullPointerException.class, () -> new Result(state, null));
        assertThrows(NullPointerException.class, () -> new Result(state, Arrays.asList(Store.SAVED_X, null)));
    }

    @Test
    void resultCompletedMetadataIsDefensivelyCopiedAndImmutable() {
        List<Store> caller = new ArrayList<>(List.of(Store.SAVED_SPAWN));
        Result result = new Result(State.unknown(), caller);
        caller.clear();
        assertEquals(List.of(Store.SAVED_SPAWN), result.completedStores());
        assertThrows(UnsupportedOperationException.class, () -> result.completedStores().add(Store.SAVED_X));
    }

    @Test
    void suppliedSavedLongReadsPreserveEveryIntBitWithoutClamping() {
        byte[] data = new byte[31];
        data[5] = (byte) 0x89;
        data[6] = (byte) 0xAB;
        data[7] = (byte) 0xCD;
        data[8] = (byte) 0xEF;
        assertEquals(0x89ABCDEF, supplied(data, ones()).read(SavedField.TIME_LONG));
    }

    @Test
    void initialPublicationComparisonUsesContentRatherThanObjectIdentity() {
        State before = State.unknown();
        RecordingPort port = new RecordingPort(State.unknown(), RAW);
        assertNotSame(before, port.state);
        assertEquals(18, CdCheckpointSaveData.save(before, port).completedStores().size());
    }

    private static void assertInvalidAdapter(int position, int invalid) {
        int[] values = RAW.clone();
        values[position] = invalid;
        RecordingPort port = new RecordingPort(State.unknown(), values);
        assertThrows(IllegalArgumentException.class, () -> CdCheckpointSaveData.save(State.unknown(), port));
        assertPrefix(port, position, position + 1, position);
    }

    private static void assertPrefix(RecordingPort port, int completed, int readCount, int attemptCount) {
        assertEquals(oracle(new byte[31], new byte[31], completed, RAW), port.state);
        assertEquals(Arrays.asList(READS).subList(0, readCount), port.reads);
        assertEquals(Arrays.asList(STORES).subList(0, completed), port.published);
        assertEquals(attemptCount, port.attempts.size());
    }

    /** Independent literal address/width oracle, never a production enum directory walk. */
    private static State oracle(byte[] seed, byte[] knowledge, int completed, int[] values) {
        byte[] bytes = seed.clone();
        byte[] mask = knowledge.clone();
        for (int step = 0; step < completed; step++) {
            int value = values[step];
            if (step == 16 && Integer.toUnsignedLong(value) >= 0x00050000L) {
                value = 0x00050000;
            }
            for (int part = 0; part < STORE_WIDTHS[step]; part++) {
                int address = DESTINATIONS[step] + part;
                int index = -1;
                for (int i = 0; i < 31; i++) {
                    if (OWNED[i] == address) {
                        index = i;
                        break;
                    }
                }
                if (index < 0) {
                    throw new AssertionError("Oracle address outside literal save spans");
                }
                bytes[index] = (byte) (value >>> (8 * (STORE_WIDTHS[step] - 1 - part)));
                mask[index] = 1;
            }
        }
        return supplied(bytes, mask);
    }

    private static State supplied(byte[] bytes, byte[] mask) {
        return State.supplied(CdAssetBytes.copyOf(bytes), CdAssetBytes.copyOf(mask));
    }

    private static byte[] ones() {
        byte[] bytes = new byte[31];
        Arrays.fill(bytes, (byte) 1);
        return bytes;
    }

    private static final class RecordingPort implements Port {
        private State state;
        private final int[] values;
        private final List<Source> reads = new ArrayList<>();
        private final List<State> observed = new ArrayList<>();
        private final List<StoreCall> attempts = new ArrayList<>();
        private final List<Store> published = new ArrayList<>();
        private final RuntimeException failure = new RuntimeException("literal reached boundary");
        private int failReadAt = -1;
        private int failStoreAt = -1;
        private int failAfterPublicationAt = -1;
        private int noOpAt = -1;
        private int wrongAt = -1;

        private RecordingPort(State state, int[] values) {
            this.state = state;
            this.values = values.clone();
        }

        @Override
        public State currentState() {
            return state;
        }

        @Override
        public int read(Source source) {
            int step = reads.size();
            assertEquals(READS[step], source);
            reads.add(source);
            observed.add(state);
            if (step == failReadAt) {
                throw failure;
            }
            return values[step];
        }

        @Override
        public void commit(StoreCall call) {
            int step = attempts.size();
            attempts.add(call);
            assertEquals(STORES[step], call.store());
            assertEquals(state, call.expectedState());
            if (step == failStoreAt) {
                throw failure;
            }
            if (step == noOpAt) {
                return;
            }
            if (step == wrongAt) {
                state = State.unknown();
                return;
            }
            state = call.finalState();
            published.add(call.store());
            if (step == failAfterPublicationAt) {
                throw failure;
            }
        }
    }
}

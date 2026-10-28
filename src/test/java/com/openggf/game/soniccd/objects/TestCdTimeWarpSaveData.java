package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** Native source vectors, not a live Frame, media authentication or publication-authority proof. */
class TestCdTimeWarpSaveData {
    // Independently transcribed from N SaveTimeWarpData (Sonic/Main.asm:532-567).
    private static final int[] SOURCE_ADDRESSES = {
            0xFF1522, 0xFFD008, 0xFFD00C, 0xFFD014, 0xFFD010, 0xFFD012, 0xFFD022,
            0xFFF64D, 0xFFF72E, 0xFFF700, 0xFFF704, 0xFFF708, 0xFFF70C,
            0xFFF710, 0xFFF714, 0xFFF718, 0xFFF71C, 0xFFF648, 0xFFF64E,
            0xFF1512, 0xFF150D, 0xFF1514, 0xFF195B};
    private static final int[] SOURCE_WIDTHS = {1,2,2,2,2,2,1,1,2,2,2,2,2,2,2,2,2,2,1,2,1,4,1};
    private static final int[] STORE_ADDRESSES = {
            0xFF1546,0xFF1548,0xFF154A,0xFF1564,0xFF1566,0xFF1568,0xFF154C,0xFF154C,0xFF154C,
            0xFF1562,0xFF154E,0xFF1550,0xFF1552,0xFF1554,0xFF1556,0xFF1558,0xFF155A,0xFF155C,
            0xFF155E,0xFF1560,0xFF1562,0xFF1563,0xFF1528,0xFF1545,0xFF1570,0xFF1593};
    private static final int[] STORE_WIDTHS = {1,2,2,2,2,2,1,1,1,1,2,2,2,2,2,2,2,2,2,2,1,1,2,1,4,1};
    // -1 marks a read/modify/write of the SAVED flags; it is not a live source read.
    private static final int[] STORE_SOURCE = {0,1,2,3,4,5,6,-1,-1,7,8,9,10,11,12,13,14,15,16,17,7,18,19,20,21,22};
    private static final int[] SOURCE_VALUES = {
            0x81,0x1234,0xFEDC,0x8000,0xFFFF,0x7FFF,0xFF,0x12,0x7F00,0x8123,0x0ABC,
            0xFFFF,0x1001,0x2233,0x4455,0x6677,0x8899,0xBEEF,0x80,0xFFFE,0xA6,0x0004FFFF,0x80};
    private static final int[] EXPECTED_VALUES = {
            0x81,0x1234,0xFEDC,0x8000,0xFFFF,0x7FFF,0xFF,0xF7,0xB7,0xA5,0x7F00,0x8123,
            0x0ABC,0xFFFF,0x1001,0x2233,0x4455,0x6677,0x8899,0xBEEF,0x5A,0x80,0xFFFE,0xA6,0x0004FFFF,0x80};
    private static final String[] SOURCE_NAMES = ("SPAWN_MODE PRIMARY_X PRIMARY_Y PRIMARY_GVEL PRIMARY_X_VELOCITY "
            + "PRIMARY_Y_VELOCITY PRIMARY_FLAGS WATER_ROUTINE BOTTOM_BOUND FG_X FG_Y BG_X BG_Y BG2_X BG2_Y "
            + "BG3_X BG3_Y WATER_HEIGHT2 WATER_FULLSCREEN RINGS LIVES_FLAGS TIME_LONG MINI_SONIC").split(" ");
    private static final String[] STORE_NAMES = ("SAVED_SPAWN SAVED_X SAVED_Y GROUND_VELOCITY X_VELOCITY Y_VELOCITY "
            + "PLAYER_FLAGS PLAYER_FLAGS_CLEAR_STANDING PLAYER_FLAGS_CLEAR_WATER WATER_ROUTINE_FIRST BOTTOM_BOUND "
            + "FG_X FG_Y BG_X BG_Y BG2_X BG2_Y BG3_X BG3_Y WATER_HEIGHT_WORD WATER_ROUTINE_SECOND WATER_FULLSCREEN "
            + "RINGS LIVES_FLAGS TIME_LONG MINI_SONIC").split(" ");
    private static final int[] SAVED_ADDRESSES = {
            0xFF1546,0xFF1548,0xFF154A,0xFF1564,0xFF1566,0xFF1568,0xFF154C,0xFF1562,0xFF154E,
            0xFF1550,0xFF1552,0xFF1554,0xFF1556,0xFF1558,0xFF155A,0xFF155C,0xFF155E,
            0xFF1560,0xFF1563,0xFF1528,0xFF1545,0xFF1570,0xFF1593};
    private static final int[] PACKED_ADDRESSES = addresses();

    @Test void nativeCensusPinsEverySourceStoreFieldAndRepeatedOperation() {
        var sources = CdTimeWarpSaveData.Source.values(); var stores = CdTimeWarpSaveData.Store.values();
        var fields = CdTimeWarpSaveData.SavedField.values();
        assertEquals(23, sources.length); assertEquals(26, stores.length); assertEquals(23, fields.length);
        for (int i = 0; i < sources.length; i++) {
            assertEquals(SOURCE_NAMES[i], sources[i].name());
            assertEquals(SOURCE_ADDRESSES[i], sources[i].nativeAddress()); assertEquals(SOURCE_WIDTHS[i], sources[i].width());
            assertEquals(SOURCE_NAMES[i], fields[i].name());
            assertEquals(SAVED_ADDRESSES[i], fields[i].nativeAddress()); assertEquals(SOURCE_WIDTHS[i], fields[i].width());
        }
        for (int i = 0; i < stores.length; i++) {
            assertEquals(STORE_NAMES[i], stores[i].name());
            assertEquals(STORE_ADDRESSES[i], stores[i].nativeAddress()); assertEquals(STORE_WIDTHS[i], stores[i].width());
            assertEquals(STORE_SOURCE[i] < 0 ? java.util.Optional.empty() : java.util.Optional.of(sources[STORE_SOURCE[i]]),
                    stores[i].source());
        }
        assertEquals(42, Arrays.stream(PACKED_ADDRESSES).distinct().count());
    }

    @Test void unknownIsKnowledgeAbsenceAndSavedReadsReportTheFirstActualByte() {
        var unknown = CdTimeWarpSaveData.State.unknown(); assertEquals(42, unknown.knownMask().length());
        for (int address : PACKED_ADDRESSES) assertEquals(OptionalInt.empty(), unknown.knownByte(address));
        for (var field : CdTimeWarpSaveData.SavedField.values()) {
            var failure = assertThrows(CdTimeWarpSaveData.UnknownSavedByte.class, () -> unknown.read(field));
            assertEquals(field.nativeAddress(), failure.nativeAddress());
        }
        for (var field : CdTimeWarpSaveData.SavedField.values()) for (int missing = 0; missing < field.width(); missing++) {
            byte[] mask = filled(42, 1); mask[index(field.nativeAddress() + missing)] = 0;
            var state = supplied(filled(42, 0xA5), mask);
            var failure = assertThrows(CdTimeWarpSaveData.UnknownSavedByte.class, () -> state.read(field));
            assertEquals(field.nativeAddress() + missing, failure.nativeAddress());
        }
    }

    @Test void suppliedKnowledgeIsDefensiveAndUnknownBackingHasOneCanonicalValue() {
        byte[] bytes = filled(42, 0xA5), mask = new byte[42]; mask[0] = mask[41] = 1;
        var state = supplied(bytes, mask); var equal = supplied(bytes, mask);
        bytes[0] = 0; mask[0] = 0;
        assertEquals(OptionalInt.of(0xA5), state.knownByte(0xFF1528)); assertEquals(state, equal);
        assertEquals(state.hashCode(), equal.hashCode());
        byte[] returned = state.knownMask().copy(); returned[0] = 0;
        assertEquals(1, state.knownMask().unsignedByte(0));
        assertEquals(CdTimeWarpSaveData.State.unknown(), supplied(filled(42, 0xFF), new byte[42]));
        assertEquals(CdTimeWarpSaveData.State.unknown().hashCode(), supplied(filled(42, 0x55), new byte[42]).hashCode());
        assertNotEquals(state, CdTimeWarpSaveData.State.unknown());
    }

    @Test void stateRejectsSizesNonbinaryKnowledgeNullsAndEveryHoleWithoutAddressWrapping() {
        assertThrows(IllegalArgumentException.class, () -> supplied(new byte[41], new byte[42]));
        assertThrows(IllegalArgumentException.class, () -> supplied(new byte[42], new byte[43]));
        for (int bad : new int[]{2, 0x80, 0xFF}) {
            byte[] mask = new byte[42]; mask[20] = (byte) bad;
            assertThrows(IllegalArgumentException.class, () -> supplied(new byte[42], mask));
        }
        assertThrows(NullPointerException.class, () -> CdTimeWarpSaveData.State.supplied(null, CdAssetBytes.copyOf(new byte[42])));
        assertThrows(NullPointerException.class, () -> CdTimeWarpSaveData.State.supplied(CdAssetBytes.copyOf(new byte[42]), null));
        var state = CdTimeWarpSaveData.State.unknown();
        for (int address : new int[]{-1,0,0xFF1527,0xFF152A,0xFF1544,0xFF1547,0xFF154D,
                0xFF156A,0xFF156F,0xFF1574,0xFF1591,0xFF1592,0xFF1594,0x1000000,0xFFFF1548})
            assertThrows(IllegalArgumentException.class, () -> state.knownByte(address));
        assertThrows(NullPointerException.class, () -> state.read(null));
    }

    @Test void completeSaveUsesTheLiteralOrderAndMakesOnlyTheFortyTwoOwnedBytesKnown() {
        Port port = new Port(); var result = CdTimeWarpSaveData.save(port.state, port);
        assertEquals(Arrays.asList(CdTimeWarpSaveData.Store.values()), result.completedStores());
        assertEquals(port.state, result.state()); assertEquals(26, port.published.size());
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < STORE_NAMES.length; i++) {
            if (STORE_SOURCE[i] >= 0) expected.add("R:" + SOURCE_NAMES[STORE_SOURCE[i]]);
            expected.add("W:" + STORE_NAMES[i]);
        }
        assertEquals(expected, port.events); assertEquals(24, port.reads.size());
        for (int count = 0; count <= 26; count++) assertPrefix(count == 0 ? port.initial : port.published.get(count - 1), count);
        assertArrayEquals(filled(42, 1), result.state().knownMask().copy());
        assertEquals(port.initial, CdTimeWarpSaveData.State.unknown());
    }

    @Test void eachReachedStoreCarriesTheExactExpectedValueAndOnlyItsOwnNativeWidth() {
        Port port = new Port(); CdTimeWarpSaveData.save(port.state, port);
        for (int i = 0; i < 26; i++) {
            var call = port.attempted.get(i);
            assertEquals(CdTimeWarpSaveData.Store.values()[i], call.store()); assertEquals(EXPECTED_VALUES[i], call.rawValue());
            assertEquals(i == 0 ? port.initial : port.published.get(i - 1), call.expectedState());
            assertEquals(port.published.get(i), call.finalState());
            for (int address : PACKED_ADDRESSES) if (address < STORE_ADDRESSES[i] || address >= STORE_ADDRESSES[i] + STORE_WIDTHS[i])
                assertEquals(call.expectedState().knownByte(address), call.finalState().knownByte(address), "native nonwrite " + address);
        }
    }

    @Test void flagsAreReadOnceThenClearedInTheSavedByteAtTwoSeparateStores() {
        Port port = new Port(); CdTimeWarpSaveData.save(port.state, port);
        assertEquals(1, port.reads.stream().filter(s -> s == CdTimeWarpSaveData.Source.PRIMARY_FLAGS).count());
        assertEquals(0xFF, port.published.get(6).read(CdTimeWarpSaveData.SavedField.PRIMARY_FLAGS));
        assertEquals(0xF7, port.published.get(7).read(CdTimeWarpSaveData.SavedField.PRIMARY_FLAGS));
        assertEquals(0xB7, port.published.get(8).read(CdTimeWarpSaveData.SavedField.PRIMARY_FLAGS));
        assertEquals(0xFF, port.values.get(CdTimeWarpSaveData.Source.PRIMARY_FLAGS));
        assertEquals(0x8000, port.state.read(CdTimeWarpSaveData.SavedField.PRIMARY_GVEL));
        assertEquals(0xFFFF, port.state.read(CdTimeWarpSaveData.SavedField.PRIMARY_X_VELOCITY));
    }

    @Test void duplicateWaterRoutineReadsAreFreshAndNeverAliasTheWaterHeightWord() {
        Port port = new Port(); CdTimeWarpSaveData.save(port.state, port);
        assertEquals(0xA5, port.published.get(9).read(CdTimeWarpSaveData.SavedField.WATER_ROUTINE));
        assertEquals(0x5A, port.published.get(20).read(CdTimeWarpSaveData.SavedField.WATER_ROUTINE));
        assertEquals(0xBEEF, port.state.read(CdTimeWarpSaveData.SavedField.WATER_HEIGHT2));
        assertEquals(2, port.reads.stream().filter(s -> s == CdTimeWarpSaveData.Source.WATER_ROUTINE).count());
    }

    @Test void fullUnsignedTimeLongCapsWithoutChangingItsLiveSourceOrReadingItTwice() {
        for (int value : new int[]{0,1,0x0004FFFF,0x00050000,0x00050001,0x7FFFFFFF,0x80000000,0xFFFFFFFF}) {
            Port port = new Port(); port.values.put(CdTimeWarpSaveData.Source.TIME_LONG, value);
            var result = CdTimeWarpSaveData.save(port.state, port);
            assertEquals(Integer.compareUnsigned(value, 0x50000) < 0 ? value : 0x50000,
                    result.state().read(CdTimeWarpSaveData.SavedField.TIME_LONG));
            assertEquals(value, port.values.get(CdTimeWarpSaveData.Source.TIME_LONG));
            assertEquals(1, port.reads.stream().filter(s -> s == CdTimeWarpSaveData.Source.TIME_LONG).count());
        }
    }

    @Test void suppliedRawLongRetainsAllBitsBeforeAnySaveClampRuns() {
        byte[] bytes = new byte[42]; bytes[37] = (byte) 0x80; bytes[40] = 1;
        assertEquals(0x80000001, supplied(bytes, filled(42, 1)).read(CdTimeWarpSaveData.SavedField.TIME_LONG));
    }

    @Test void everyReachedReadFaultRetainsExactlyTheEarlierActualStoresAndOriginalFailure() {
        int[] beforeRead = {0,1,2,3,4,5,6,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25};
        for (int read = 0; read < beforeRead.length; read++) {
            Port port = new Port(); port.failRead = read;
            assertSame(port.fault, assertThrows(IllegalStateException.class, () -> CdTimeWarpSaveData.save(port.initial, port)));
            assertEquals(read + 1, port.reads.size()); assertEquals(beforeRead[read], port.published.size());
            assertEquals(beforeRead[read], port.attempted.size()); assertPrefix(port.state, beforeRead[read]);
        }
    }

    @Test void everyCommitFaultBeforePublicationRetainsOnlyItsEarlierPrefix() {
        for (int store = 0; store < 26; store++) {
            Port port = new Port(); port.failCommit = store;
            assertSame(port.fault, assertThrows(IllegalStateException.class, () -> CdTimeWarpSaveData.save(port.initial, port)));
            assertEquals(store + 1, port.attempted.size()); assertEquals(store, port.published.size()); assertPrefix(port.state, store);
        }
    }

    @Test void everyCommitFaultAfterPublicationKeepsThatStoreAndDoesNotReturnAResult() {
        for (int store = 0; store < 26; store++) {
            Port port = new Port(); port.failAfterCommit = store;
            List<CdTimeWarpSaveData.Result> returned = new ArrayList<>();
            assertSame(port.fault, assertThrows(IllegalStateException.class,
                    () -> returned.add(CdTimeWarpSaveData.save(port.initial, port))));
            assertTrue(returned.isEmpty()); assertEquals(store + 1, port.attempted.size());
            assertEquals(store + 1, port.published.size()); assertPrefix(port.state, store + 1);
        }
    }

    @Test void returnedNoOpCommitIsRejectedBeforeAnyLaterNativeRead() {
        Port port = new Port(); port.noOpCommit = true;
        var fault = assertThrows(IllegalStateException.class, () -> CdTimeWarpSaveData.save(port.initial, port));
        assertNotSame(port.fault, fault); assertEquals(List.of(CdTimeWarpSaveData.Source.SPAWN_MODE), port.reads);
        assertEquals(1, port.attempted.size()); assertTrue(port.published.isEmpty()); assertEquals(port.initial, port.state);
    }

    @Test void staleInitialStateRejectsBeforeAReadAndEqualIndependentValuesRemainObservational() {
        Port stale = new Port(); stale.state = supplied(new byte[42], filled(42, 1));
        assertThrows(IllegalStateException.class, () -> CdTimeWarpSaveData.save(stale.initial, stale));
        assertTrue(stale.reads.isEmpty()); assertTrue(stale.attempted.isEmpty());
        Port equal = new Port();
        var independent = CdTimeWarpSaveData.State.unknown(); assertNotSame(equal.initial, independent);
        assertEquals(CdTimeWarpSaveData.save(independent, equal).state(), equal.state,
                "Value equality is a publication check, not invocation or exactly-once authority");
    }

    @Test void alreadyEqualKnownValuesCannotAuthenticateWhetherSameValueStoresActuallyRan() {
        Port seed = new Port(); seed.waterSequence = false; seed.values.put(CdTimeWarpSaveData.Source.PRIMARY_FLAGS, 0);
        var complete = CdTimeWarpSaveData.save(seed.state, seed).state();
        Port noOp = new Port(); noOp.waterSequence = false; noOp.values.put(CdTimeWarpSaveData.Source.PRIMARY_FLAGS, 0);
        noOp.state = complete; noOp.noOpCommit = true;
        var result = CdTimeWarpSaveData.save(complete, noOp);
        assertEquals(complete, result.state()); assertEquals(26, noOp.attempted.size()); assertTrue(noOp.published.isEmpty());
        assertEquals(26, result.completedStores().size(),
                "Exactly-once and producer authority belong to the actual port, not observational value equality");
    }

    @Test void aReadThatChangesSavedStateIsRejectedBeforeItsFollowingStore() {
        Port port = new Port(); port.duringRead = source -> port.state = supplied(new byte[42], filled(42, 1));
        assertThrows(IllegalStateException.class, () -> CdTimeWarpSaveData.save(port.initial, port));
        assertEquals(1, port.reads.size()); assertTrue(port.attempted.isEmpty());
        assertArrayEquals(filled(42, 1), port.state.knownMask().copy());
    }

    @Test void everyNonLongSourceRejectsItsReachedUnsignedWidthViolation() {
        for (var source : CdTimeWarpSaveData.Source.values()) if (source.width() != 4) {
            for (int invalid : new int[]{-1, 1 << (8 * source.width())}) {
                Port port = new Port(); port.waterSequence = false; port.values.put(source, invalid);
                assertThrows(IllegalArgumentException.class, () -> CdTimeWarpSaveData.save(port.initial, port));
                assertEquals(source, port.reads.getLast());
                assertEquals(port.published.size(), port.attempted.size(), "A bad source value stops before its store");
            }
        }
    }

    @Test void storeCallAndResultValuesAreImmutableAndWidthCheckedWithoutGrantingAuthority() {
        var state = CdTimeWarpSaveData.State.unknown();
        assertThrows(NullPointerException.class, () -> new CdTimeWarpSaveData.StoreCall(null, 0, state, state));
        assertThrows(NullPointerException.class, () -> new CdTimeWarpSaveData.StoreCall(CdTimeWarpSaveData.Store.SAVED_X, 0, null, state));
        assertThrows(NullPointerException.class, () -> new CdTimeWarpSaveData.StoreCall(CdTimeWarpSaveData.Store.SAVED_X, 0, state, null));
        assertThrows(IllegalArgumentException.class, () -> new CdTimeWarpSaveData.StoreCall(CdTimeWarpSaveData.Store.SAVED_X, -1, state, state));
        assertThrows(IllegalArgumentException.class, () -> new CdTimeWarpSaveData.StoreCall(CdTimeWarpSaveData.Store.SAVED_SPAWN, 256, state, state));
        assertDoesNotThrow(() -> new CdTimeWarpSaveData.StoreCall(CdTimeWarpSaveData.Store.TIME_LONG, -1, state, state));
        List<CdTimeWarpSaveData.Store> stores = new ArrayList<>(); stores.add(CdTimeWarpSaveData.Store.SAVED_X);
        var result = new CdTimeWarpSaveData.Result(state, stores); stores.clear();
        assertEquals(List.of(CdTimeWarpSaveData.Store.SAVED_X), result.completedStores());
        assertThrows(UnsupportedOperationException.class, () -> result.completedStores().clear());
        assertThrows(NullPointerException.class, () -> new CdTimeWarpSaveData.Result(null, List.of()));
        assertThrows(NullPointerException.class, () -> new CdTimeWarpSaveData.Result(state, Arrays.asList((CdTimeWarpSaveData.Store) null)));
        assertThrows(NullPointerException.class, () -> CdTimeWarpSaveData.save(null, new Port()));
        assertThrows(NullPointerException.class, () -> CdTimeWarpSaveData.save(state, null));
    }

    @Test void retainedEarlierStatesAndSourceValuesRemainIndependentOfTheCompletedSave() {
        Port port = new Port(); var live = new EnumMap<>(port.values); CdTimeWarpSaveData.save(port.state, port);
        assertEquals(live, port.values); assertPrefix(port.initial, 0); assertPrefix(port.published.get(6), 7);
        assertPrefix(port.published.get(7), 8); assertPrefix(port.published.get(8), 9); assertPrefix(port.published.get(25), 26);
        assertNotEquals(port.initial, port.state);
    }

    @Test void portOwnedReentryFailureEscapesWithoutAnOuterResultOrLaterStores() {
        Port port = new Port(); var reentry = new IllegalStateException("port rejects reentry");
        boolean[] inRead = {false};
        port.duringRead = source -> {
            if (inRead[0]) throw reentry;
            inRead[0] = true;
            try { CdTimeWarpSaveData.save(port.state, port); }
            finally { inRead[0] = false; }
        };
        assertSame(reentry, assertThrows(IllegalStateException.class, () -> CdTimeWarpSaveData.save(port.initial, port)));
        assertEquals(2, port.reads.size()); assertTrue(port.attempted.isEmpty()); assertEquals(port.initial, port.state);
    }

    private static final class Port implements CdTimeWarpSaveData.Port {
        final CdTimeWarpSaveData.State initial = CdTimeWarpSaveData.State.unknown();
        CdTimeWarpSaveData.State state = initial;
        final EnumMap<CdTimeWarpSaveData.Source,Integer> values = new EnumMap<>(CdTimeWarpSaveData.Source.class);
        final List<CdTimeWarpSaveData.Source> reads = new ArrayList<>();
        final List<CdTimeWarpSaveData.StoreCall> attempted = new ArrayList<>();
        final List<CdTimeWarpSaveData.State> published = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        final IllegalStateException fault = new IllegalStateException("original native-cut callback failure");
        int failRead = -1, failCommit = -1, failAfterCommit = -1, waterReads;
        boolean noOpCommit, waterSequence = true;
        Consumer<CdTimeWarpSaveData.Source> duringRead = source -> {};
        Port() {
            var sources = CdTimeWarpSaveData.Source.values();
            for (int i = 0; i < sources.length; i++) values.put(sources[i], SOURCE_VALUES[i]);
        }
        @Override public CdTimeWarpSaveData.State currentState() { return state; }
        @Override public int read(CdTimeWarpSaveData.Source source) {
            reads.add(source); events.add("R:" + source.name());
            if (reads.size() - 1 == failRead) throw fault;
            duringRead.accept(source);
            if (source == CdTimeWarpSaveData.Source.WATER_ROUTINE && waterSequence) return waterReads++ == 0 ? 0xA5 : 0x5A;
            return values.get(source);
        }
        @Override public void commit(CdTimeWarpSaveData.StoreCall call) {
            attempted.add(call); events.add("W:" + call.store().name());
            if (attempted.size() - 1 == failCommit) throw fault;
            assertEquals(state, call.expectedState());
            if (!noOpCommit) { state = call.finalState(); published.add(state); }
            if (attempted.size() - 1 == failAfterCommit) throw fault;
        }
    }

    private static void assertPrefix(CdTimeWarpSaveData.State state, int count) {
        byte[] expected = new byte[42], known = new byte[42];
        for (int store = 0; store < count; store++) for (int part = 0; part < STORE_WIDTHS[store]; part++) {
            int at = index(STORE_ADDRESSES[store] + part); known[at] = 1;
            expected[at] = (byte) (EXPECTED_VALUES[store] >>> (8 * (STORE_WIDTHS[store] - part - 1)));
        }
        assertArrayEquals(known, state.knownMask().copy());
        for (int i = 0; i < 42; i++) assertEquals(known[i] == 0 ? OptionalInt.empty() : OptionalInt.of(expected[i] & 255),
                state.knownByte(PACKED_ADDRESSES[i]), "packed native address " + Integer.toHexString(PACKED_ADDRESSES[i]));
    }
    private static int index(int address) {
        for (int i = 0; i < PACKED_ADDRESSES.length; i++) if (PACKED_ADDRESSES[i] == address) return i;
        throw new AssertionError("Native test address is outside six saved spans: " + Integer.toHexString(address));
    }
    private static int[] addresses() {
        List<Integer> result = new ArrayList<>();
        for (int[] span : new int[][]{{0xFF1528,0xFF152A},{0xFF1545,0xFF1547},{0xFF1548,0xFF154D},
                {0xFF154E,0xFF156A},{0xFF1570,0xFF1574},{0xFF1593,0xFF1594}})
            for (int a = span[0]; a < span[1]; a++) result.add(a);
        return result.stream().mapToInt(Integer::intValue).toArray();
    }
    private static byte[] filled(int count, int value) { byte[] bytes = new byte[count]; Arrays.fill(bytes, (byte) value); return bytes; }
    private static CdTimeWarpSaveData.State supplied(byte[] bytes, byte[] mask) {
        return CdTimeWarpSaveData.State.supplied(CdAssetBytes.copyOf(bytes), CdAssetBytes.copyOf(mask));
    }
}

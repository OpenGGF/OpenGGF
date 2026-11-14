package com.openggf.game.soniccd.audio;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;

import java.nio.ReadOnlyBufferException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ROM-free, explicitly SYNTHETIC format fixtures; no authenticated asset owner is forged.
 * Native pin N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * _Driver.asm ProcSoundQueue/.ProcSlot:1001-1041, GetList:41-49,
 * ReadHLPtr:75-81, DriverInfo:1874-1886; FM/Data.asm:17-182;
 * FM/_Variables.inc Variables/sndPlay/sndQueues/rValue/curPriority:70-83.
 * Source paths are under src/Sound Drivers/FM/. Tests specify UNCOMMITTED plans.
 * Authored before production; execution and actual-media assertions belong to Root.
 */
class TestCdFmSoundArbitration {
    private static final int PLAY = 0x1C09;
    private static final int Q1 = 0x1C0A;
    private static final int Q2 = 0x1C0B;
    private static final int Q3 = 0x1C0C;
    private static final int R = 0x1C17;
    private static final int PRIORITY = 0x1C18;
    private static final int SYNTHETIC_INFO = 0x40;
    private static final int SYNTHETIC_INDEX = 0x54;
    private static final int SYNTHETIC_BASE = 0xF4;

    @Test
    void emptyArbitrationPreservesDirectPendingAndPriorityButPlansTheExplicitRStore() {
        var before = state(0xE0, 0, 0, 0, 0xFE, 0xA3);
        assertPlan(plan(before, priorities(0x70), 0xD5),
                state(0xE0, 0, 0, 0, 0xFE, 0xD5), w(R, 0xD5));
        assertEquals(state(0xE0, 0, 0, 0, 0xFE, 0xA3), before);
    }

    @Test
    void invalidFirstCellIsRereadThreeTimesWithoutClearingOrAdvancing() {
        for (int invalid : new int[] {0, 1, 0x7F, 0x80, 0x8F}) {
            var before = state(0x93, invalid, 0x95, 0x96, 0x30, 0x12);
            assertPlan(plan(before, priorities(0x70), 0x34),
                    state(0x93, invalid, 0x95, 0x96, 0x30, 0x34), w(R, 0x34));
        }
    }

    @Test
    void invalidSecondCellBlocksThirdAfterFirstCellWins() {
        for (int invalid : new int[] {0, 1, 0x7F, 0x80, 0x8F}) {
            assertPlan(plan(state(0x93, 0x95, invalid, 0x96, 0x30, 0x12), priorities(0x70), 0x34),
                    state(0x95, 0, invalid, 0x96, 0x70, 0x34),
                    w(R, 0x34), w(PLAY, 0x95), w(PRIORITY, 0x70), w(Q1, 0));
        }
    }

    @Test
    void rejectedFirstCellStillClearsAndAdvancesBeforeInvalidSecondCell() {
        assertPlan(plan(state(0x93, 0x95, 0x80, 0x96, 0x71, 0), priorities(0x70), 0xFF),
                state(0x93, 0, 0x80, 0x96, 0x71, 0xFF), w(R, 0xFF), w(Q1, 0));
    }

    @Test
    void allThreeValidCellsUseNativeWriteOrderAndLastWinnerRemainsPending() {
        var view = priorities(0, 0x90, 0x10, 0x91, 0x20, 0x92, 0x30);
        assertPlan(plan(state(0xE0, 0x90, 0x91, 0x92, 0, 0x12), view, 0x88),
                state(0x92, 0, 0, 0, 0x30, 0x88), w(R, 0x88),
                w(PLAY, 0x90), w(PRIORITY, 0x10), w(Q1, 0),
                w(PLAY, 0x91), w(PRIORITY, 0x20), w(Q2, 0),
                w(PLAY, 0x92), w(PRIORITY, 0x30), w(Q3, 0));
    }

    @Test
    void laterEqualPrioritiesWinAndReplaceTheDirectPendingByte() {
        assertPlan(plan(state(0x93, 0x95, 0x98, 0x96, 0x70, 0), priorities(0x70), 1),
                state(0x96, 0, 0, 0, 0x70, 1), w(R, 1),
                w(PLAY, 0x95), w(PRIORITY, 0x70), w(Q1, 0),
                w(PLAY, 0x98), w(PRIORITY, 0x70), w(Q2, 0),
                w(PLAY, 0x96), w(PRIORITY, 0x70), w(Q3, 0));
    }

    @Test
    void lowerPrioritiesClearEveryReachedCellWithoutChangingDirectPending() {
        assertPlan(plan(state(0xE0, 0x95, 0x98, 0x96, 0x71, 7), priorities(0x70), 8),
                state(0xE0, 0, 0, 0, 0x71, 8), w(R, 8), w(Q1, 0), w(Q2, 0), w(Q3, 0));
    }

    @Test
    void comparesFullRawPriorityBeforeMaskAndUsesMaskedLatchForNextCell() {
        var view = priorities(0, 0x90, 0xF0, 0x91, 0x71, 0x92, 0x70);
        assertPlan(plan(state(0x93, 0x90, 0x91, 0x92, 0x80, 1), view, 2),
                state(0x91, 0, 0, 0, 0x71, 2), w(R, 2),
                w(PLAY, 0x90), w(PRIORITY, 0x70), w(Q1, 0),
                w(PLAY, 0x91), w(PRIORITY, 0x71), w(Q2, 0), w(Q3, 0));
    }

    @Test
    void currentPriorityAndRawCandidateAreUnsignedBytesIncludingHighBitAndEquality() {
        int[][] vectors = {
                {0x80, 0x7F, 0}, {0xFF, 0x80, 0}, {0xFF, 0xFF, 1},
                {0x80, 0x80, 1}, {0, 0, 1}, {0x81, 0xFF, 1}
        };
        for (int[] v : vectors) {
            var actual = plan(state(0x93, 0x90, 0, 0, v[0], 3), priorities(v[1]), 4);
            if (v[2] == 1) {
                assertPlan(actual, state(0x90, 0, 0, 0, v[1] & 0x7F, 4),
                        w(R, 4), w(PLAY, 0x90), w(PRIORITY, v[1] & 0x7F), w(Q1, 0));
            } else {
                assertPlan(actual, state(0x93, 0, 0, 0, v[0], 4), w(R, 4), w(Q1, 0));
            }
        }
    }

    @Test
    void sameValueRPendingAndPriorityStoresAreRetainedInOrder() {
        assertPlan(plan(state(0x95, 0x95, 0, 0, 0x70, 0xFF), priorities(0x70), 0xFF),
                state(0x95, 0, 0, 0, 0x70, 0xFF),
                w(R, 0xFF), w(PLAY, 0x95), w(PRIORITY, 0x70), w(Q1, 0));
    }

    @Test
    void callerSuppliedRIsStoredOnceBeforeArbitrationAndIsNeverDerivedFromOtherBytes() {
        for (int rawR = 0; rawR <= 0xFF; rawR++) {
            assertPlan(plan(state(0x12, 0, 0x95, 0x96, 0xAB, 0xCD), priorities(0x70), rawR),
                    state(0x12, 0, 0x95, 0x96, 0xAB, rawR), w(R, rawR));
        }
    }

    @Test
    void all256RawQueueValuesFollowBitSevenThenSubtractionWithNoUpperBound() {
        for (int raw = 0; raw <= 0xFF; raw++) {
            var actual = plan(state(0x55, raw, 0, 0x96, 0x20, 0x12), priorities(0x70), 0xAB);
            if (raw < 0x90) {
                assertPlan(actual, state(0x55, raw, 0, 0x96, 0x20, 0xAB), w(R, 0xAB));
            } else {
                assertPlan(actual, state(raw, 0, 0, 0x96, 0x70, 0xAB),
                        w(R, 0xAB), w(PLAY, raw), w(PRIORITY, 0x70), w(Q1, 0));
            }
        }
    }

    @Test
    void raw90DfE0AndFfReadTheirOwnSyntheticNeighborBytesRatherThanAn80RowClamp() {
        var view = priorities(0, 0x90, 0x11, 0xDF, 4, 0xE0, 0xE7, 0xFF, 0xFE);
        int[][] vectors = {{0x90, 0x11}, {0xDF, 4}, {0xE0, 0xE7}, {0xFF, 0xFE}};
        for (int[] v : vectors) {
            assertEquals(v[1], view.rawPriorityByte(v[0]));
            assertPlan(plan(state(0x55, v[0], 0, 0, 0, 1), view, 2),
                    state(v[0], 0, 0, 0, v[1] & 0x7F, 2),
                    w(R, 2), w(PLAY, v[0]), w(PRIORITY, v[1] & 0x7F), w(Q1, 0));
        }
    }

    @Test
    void syntheticFormatDerivesLittleEndianPointersAndKeepsAll112IndexedBytes() {
        byte[] bank = syntheticBank();
        var view = CdFmSoundArbitration.fromBankFormat(CdAssetBytes.copyOf(bank));
        assertEquals(SYNTHETIC_BASE, view.baseAddress());
        assertEquals(112, view.indexedBytes().length());
        assertArrayEquals(Arrays.copyOfRange(bank, SYNTHETIC_BASE, SYNTHETIC_BASE + 112),
                view.indexedBytes().copy());
        for (int id = 0x90; id <= 0xFF; id++) {
            assertEquals(Byte.toUnsignedInt(bank[SYNTHETIC_BASE + id - 0x90]), view.rawPriorityByte(id));
        }
        assertEquals(4, view.rawPriorityByte(0xDF)); // Synthetic following header, NOT a declared row80.
        assertEquals(0xE7, view.rawPriorityByte(0xE0));
        assertEquals(0xFE, view.rawPriorityByte(0xFF));
    }

    @Test
    void malformedFormatRejectsShortOversizeOrWrongGetListAndInfoDomains() {
        for (int size : new int[] {0, 8, 9, 17, 0x1C01, 0x2000}) {
            assertThrows(IllegalArgumentException.class,
                    () -> CdFmSoundArbitration.fromBankFormat(CdAssetBytes.copyOf(new byte[size])));
        }
        for (int address : new int[] {8, 11, 12, 13, 14, 15, 16, 17}) {
            byte[] bad = syntheticBank(); bad[address] ^= 1;
            assertThrows(IllegalArgumentException.class,
                    () -> CdFmSoundArbitration.fromBankFormat(CdAssetBytes.copyOf(bad)));
        }
        for (int pointer : new int[] {0, 8, 17, 0x3FF, 0xFFFF}) {
            byte[] bad = syntheticBank(); le(bad, 9, pointer);
            assertThrows(IllegalArgumentException.class,
                    () -> CdFmSoundArbitration.fromBankFormat(CdAssetBytes.copyOf(bad)));
        }
    }

    @Test
    void malformedFormatRejectsWrongInfoWordsIndexExtentAndTruncatedNeighborWindow() {
        int[][] replacements = {
                {0, SYNTHETIC_BASE + 1}, {2, 1}, {4, SYNTHETIC_INDEX + 1},
                {6, SYNTHETIC_INDEX + 2}, {8, 1}, {10, 1}, {12, 0x91},
                {14, 1}, {16, 0x1C01}, {18, 1}
        };
        for (int[] replacement : replacements) {
            byte[] bad = syntheticBank(); le(bad, SYNTHETIC_INFO + replacement[0], replacement[1]);
            assertThrows(IllegalArgumentException.class,
                    () -> CdFmSoundArbitration.fromBankFormat(CdAssetBytes.copyOf(bad)));
        }
        byte[] badHeader = syntheticBank(); le(badHeader, SYNTHETIC_INDEX, SYNTHETIC_BASE + 80);
        assertThrows(IllegalArgumentException.class,
                () -> CdFmSoundArbitration.fromBankFormat(CdAssetBytes.copyOf(badHeader)));
        var exact = Arrays.copyOf(syntheticBank(), SYNTHETIC_BASE + 112);
        assertEquals(112, CdFmSoundArbitration.fromBankFormat(CdAssetBytes.copyOf(exact)).indexedBytes().length());
        var shortBank = Arrays.copyOf(exact, exact.length - 1);
        assertThrows(IllegalArgumentException.class,
                () -> CdFmSoundArbitration.fromBankFormat(CdAssetBytes.copyOf(shortBank)));
    }

    @Test
    void priorityViewRejectsEveryForeignLengthAndAddressWithoutWrapping() {
        for (int length : new int[] {0, 79, 80, 111, 113}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new CdFmSoundArbitration.PriorityView(0, CdAssetBytes.copyOf(new byte[length])));
        }
        for (int address : new int[] {-1, 0x1F91, 0x2000, 0xA0078B, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new CdFmSoundArbitration.PriorityView(address, CdAssetBytes.copyOf(new byte[112])));
        }
        assertEquals(0x1F90,
                new CdFmSoundArbitration.PriorityView(0x1F90, CdAssetBytes.copyOf(new byte[112])).baseAddress());
        assertEquals(0, priorities(0).baseAddress());
    }

    @Test
    void priorityReadsRejectNonBytesAndIdsBelow90InsteadOfMaskingOrClamping() {
        var view = priorities(0x70);
        for (int id : new int[] {-1, 0, 0x7F, 0x80, 0x8F, 0x100, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> view.rawPriorityByte(id));
        }
        assertEquals(0x70, view.rawPriorityByte(0x90));
        assertEquals(0x70, view.rawPriorityByte(0xFF));
    }

    @Test
    void stateChecksAllSixUnsignedByteWidthsAndKeepsTheirDistinctMeanings() {
        for (int invalid : new int[] {-1, 0x100, 0xFFFF, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> state(invalid, 0, 0, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> state(0, invalid, 0, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> state(0, 0, invalid, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> state(0, 0, 0, invalid, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> state(0, 0, 0, 0, invalid, 0));
            assertThrows(IllegalArgumentException.class, () -> state(0, 0, 0, 0, 0, invalid));
        }
        var explicit = state(1, 2, 3, 4, 0xFF, 0x80);
        assertEquals(1, explicit.sndPlay()); assertEquals(2, explicit.arbitration1());
        assertEquals(3, explicit.arbitration2()); assertEquals(4, explicit.arbitration3());
        assertEquals(0xFF, explicit.curPriority()); assertEquals(0x80, explicit.rValue());
    }

    @Test
    void rawRRejectsForeignWidthsEvenWhenArbitrationCannotReachAnyPriority() {
        for (int invalid : new int[] {-1, 0x100, 0xFFFF, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> plan(state(1, 0, 0, 0, 2, 3), priorities(0), invalid));
        }
    }

    @Test
    void plannedWritesAdmitOnlySixOwnedZ80ByteAddressesAndCanonicalByteValues() {
        for (int address : new int[] {PLAY, Q1, Q2, Q3, R, PRIORITY}) {
            assertEquals(address, w(address, 0).address());
            assertEquals(0xFF, w(address, 0xFF).value());
            for (int invalid : new int[] {-1, 0x100, 0xFFFF, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                assertThrows(IllegalArgumentException.class, () -> w(address, invalid));
            }
        }
        for (int address : new int[] {-1, 0, 0x1C08, 0x1C0D, 0x1C16, 0x1C19, 0x2000,
                0xA01C09, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> w(address, 0));
        }
    }

    @Test
    void nullInputsAndNullResultPartsFailInsteadOfBecomingDefaults() {
        var initial = state(1, 0, 0, 0, 2, 3);
        assertThrows(NullPointerException.class, () -> plan(null, priorities(0), 0));
        assertThrows(NullPointerException.class, () -> plan(initial, null, 0));
        assertThrows(NullPointerException.class, () -> CdFmSoundArbitration.fromAssets(null));
        assertThrows(NullPointerException.class, () -> CdFmSoundArbitration.fromBankFormat(null));
        assertThrows(NullPointerException.class, () -> new CdFmSoundArbitration.PriorityView(0, null));
        assertThrows(NullPointerException.class, () -> new CdFmSoundArbitration.Result(null, List.of()));
        assertThrows(NullPointerException.class, () -> new CdFmSoundArbitration.Result(initial, null));
        assertThrows(NullPointerException.class,
                () -> new CdFmSoundArbitration.Result(initial, Collections.singletonList(null)));
    }

    @Test
    void priorityBytesAndFormatSourceRemainImmutableThroughCopiesAndReadOnlyViews() {
        byte[] raw = new byte[112]; Arrays.fill(raw, (byte) 0x70);
        var view = new CdFmSoundArbitration.PriorityView(0x200, CdAssetBytes.copyOf(raw));
        Arrays.fill(raw, (byte) 0);
        var copy = view.indexedBytes().copy(); copy[0] = 0;
        assertEquals(0x70, view.rawPriorityByte(0x90));
        assertThrows(ReadOnlyBufferException.class, () -> view.indexedBytes().buffer().put(0, (byte) 0));
        byte[] bank = syntheticBank();
        var ownedBank = CdAssetBytes.copyOf(bank);
        var fromFormat = CdFmSoundArbitration.fromBankFormat(ownedBank);
        var before = ownedBank.copy(); Arrays.fill(bank, (byte) 0);
        fromFormat.indexedBytes().copy()[0] = 0;
        assertArrayEquals(before, ownedBank.copy());
        assertEquals(0x11, fromFormat.rawPriorityByte(0x90));
    }

    @Test
    void resultCopiesMutableListsAndExposesOnlyUnmodifiablePlannedWrites() {
        var initial = state(1, 0, 0, 0, 2, 3);
        var writes = new ArrayList<CdFmSoundArbitration.PlannedByteWrite>(); writes.add(w(R, 3));
        var result = new CdFmSoundArbitration.Result(initial, writes); writes.clear();
        assertPlan(result, initial, w(R, 3));
        assertThrows(UnsupportedOperationException.class, () -> result.plannedWrites().add(w(R, 4)));
        assertThrows(UnsupportedOperationException.class, () -> result.plannedWrites().set(0, w(R, 4)));
        assertThrows(UnsupportedOperationException.class, () -> result.plannedWrites().remove(0));
        var computed = plan(initial, priorities(0), 5);
        assertThrows(UnsupportedOperationException.class, () -> computed.plannedWrites().clear());
    }

    @Test
    void plansDoNotMutateInputsAndIndependentBranchesLeaveEarlierPlansIntact() {
        var before = state(0x93, 0x95, 0x98, 0x96, 0x20, 3);
        var view = priorities(0x70); var bytes = view.indexedBytes().copy();
        var left = plan(before, view, 4); var right = plan(before, priorities(0x10), 5);
        assertEquals(state(0x93, 0x95, 0x98, 0x96, 0x20, 3), before);
        assertArrayEquals(bytes, view.indexedBytes().copy());
        assertEquals(state(0x96, 0, 0, 0, 0x70, 4), left.plannedState());
        assertEquals(state(0x93, 0, 0, 0, 0x20, 5), right.plannedState());
        assertEquals(left, plan(before, view, 4));
    }

    @Test
    void replayUsesOnlyExplicitStatePriorityViewAndCallerRWithoutConsumingPending() {
        var view = priorities(0x70);
        var before = state(0x93, 0x95, 0x98, 0x96, 0x20, 3);
        var first = plan(before, view, 0x80);
        var second = plan(first.plannedState(), view, 0xFF);
        assertPlan(second, state(0x96, 0, 0, 0, 0x70, 0xFF), w(R, 0xFF));
        assertEquals(List.of(first, second), List.of(plan(before, view, 0x80),
                plan(plan(before, view, 0x80).plannedState(), view, 0xFF)));
        assertPlan(plan(second.plannedState(), view, 0xFF), second.plannedState(), w(R, 0xFF));
    }

    private static CdFmSoundArbitration.State state(int pending, int q1, int q2, int q3, int priority, int r) {
        return new CdFmSoundArbitration.State(pending, q1, q2, q3, priority, r);
    }

    private static CdFmSoundArbitration.PlannedByteWrite w(int address, int value) {
        return new CdFmSoundArbitration.PlannedByteWrite(address, value);
    }

    private static CdFmSoundArbitration.Result plan(CdFmSoundArbitration.State state,
                                                  CdFmSoundArbitration.PriorityView view, int rawR) {
        return CdFmSoundArbitration.procSoundQueue(state, view, rawR);
    }

    private static void assertPlan(CdFmSoundArbitration.Result actual, CdFmSoundArbitration.State expected,
                                   CdFmSoundArbitration.PlannedByteWrite... writes) {
        assertEquals(expected, actual.plannedState());
        assertEquals(List.of(writes), actual.plannedWrites());
    }

    private static CdFmSoundArbitration.PriorityView priorities(int fill, int... overrides) {
        byte[] bytes = new byte[112]; Arrays.fill(bytes, (byte) fill);
        for (int i = 0; i < overrides.length; i += 2) bytes[overrides[i] - 0x90] = (byte) overrides[i + 1];
        return new CdFmSoundArbitration.PriorityView(0, CdAssetBytes.copyOf(bytes));
    }

    /** Arbitrary synthetic addresses, priorities and neighbor bytes, never a retail-byte certificate. */
    private static byte[] syntheticBank() {
        byte[] bank = new byte[0x400];
        byte[] getList = {0x21, 0x40, 0, 6, 0, 9, 8, (byte) 0xE7, 8, (byte) 0xC9};
        System.arraycopy(getList, 0, bank, 8, getList.length);
        le(bank, SYNTHETIC_INFO, SYNTHETIC_BASE);
        le(bank, SYNTHETIC_INFO + 4, SYNTHETIC_INDEX);
        le(bank, SYNTHETIC_INFO + 6, SYNTHETIC_INDEX);
        le(bank, SYNTHETIC_INFO + 12, 0x90);
        le(bank, SYNTHETIC_INFO + 16, 0x1C00);
        le(bank, SYNTHETIC_INDEX, SYNTHETIC_BASE + 79);
        for (int i = 0; i < 112; i++) bank[SYNTHETIC_BASE + i] = (byte) (i ^ 0x5A);
        bank[SYNTHETIC_BASE] = 0x11;
        bank[SYNTHETIC_BASE + 79] = 4;
        bank[SYNTHETIC_BASE + 80] = (byte) 0xE7;
        bank[SYNTHETIC_BASE + 111] = (byte) 0xFE;
        return bank;
    }

    private static void le(byte[] bytes, int address, int value) {
        bytes[address] = (byte) value; bytes[address + 1] = (byte) (value >>> 8);
    }

    @Test void actualUsaOwnerBindsTheCompleteUncheckedWindowAfterProgramsClose() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        org.junit.jupiter.api.Assumptions.assumeTrue(root != null && !root.isBlank(), "Actual supplied USA FM source opt-in");
        CdFmSoundAssets assets;
        try (var files = java.nio.file.Files.list(java.nio.file.Path.of(root).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size());
            try (var programs = com.openggf.game.soniccd.media.CdProgramAssets.open(cues.getFirst(),
                    java.util.Optional.of(com.openggf.game.soniccd.CdReleaseVariant.USA), java.util.Optional.empty())) {
                assets = CdFmSoundAssets.fromPrograms(programs);
            }
        }
        var view = CdFmSoundArbitration.fromAssets(assets);
        assertEquals(0x78B, view.baseAddress(), "derive the complete nonzero-high-byte LE pointer from the actual bank");
        assertEquals(112, view.indexedBytes().length());
        assertArrayEquals(assets.readZ80(0x78B, 112).copy(), view.indexedBytes().copy());
        for (int id = 0x90; id <= 0xFF; id++)
            assertEquals(assets.readZ80(0x78B + id - 0x90, 1).unsignedByte(0), view.rawPriorityByte(id));
        assertEquals(4, view.rawPriorityByte(0xDF), "the neighboring first header byte is not an 80th declaration");
        assertEquals(0x6D, view.rawPriorityByte(0xA4));
        assertEquals(79, assets.declaredPriorities().length());
    }
}

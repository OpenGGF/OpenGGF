package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static com.openggf.game.soniccd.objects.CdCheckpointLiveAux.*;
import static org.junit.jupiter.api.Assertions.*;

/** Source-only contract vectors. Authored and frozen before production existed; all UNRUN. */
class TestCdCheckpointLiveAux {
    private static final int[] ADDRESSES = {
            0xFF1514, 0xFF1515, 0xFF1516, 0xFF1517,
            0xFFF648, 0xFFF649, 0xFFF64D, 0xFFF64E
    };
    private static final int[] RAW = {0xDE, 0xAD, 0xBE, 0xEF, 0x12, 0x34, 0x56, 0x9A};

    @Test
    void fieldAddressesAndWidthsMatchNativeDeclarations() {
        assertEquals(4, Field.values().length);
        assertEquals(0xFF1514, Field.TIME_LONG.nativeAddress());
        assertEquals(4, Field.TIME_LONG.width());
        assertEquals(0xFFF648, Field.WATER_HEIGHT2.nativeAddress());
        assertEquals(2, Field.WATER_HEIGHT2.width());
        assertEquals(0xFFF64D, Field.WATER_ROUTINE.nativeAddress());
        assertEquals(1, Field.WATER_ROUTINE.width());
        assertEquals(0xFFF64E, Field.WATER_FULLSCREEN.nativeAddress());
        assertEquals(1, Field.WATER_FULLSCREEN.width());
    }

    @Test
    void timeByteSelectorsExcludeTheLeadingByte() {
        assertEquals(3, TimeByte.values().length);
        assertEquals(0xFF1515, TimeByte.MINUTES.nativeAddress());
        assertEquals(0xFF1516, TimeByte.SECONDS.nativeAddress());
        assertEquals(0xFF1517, TimeByte.FRAMES.nativeAddress());
    }

    @Test
    void unknownDoesNotSupplyReadableZeros() {
        State state = unknown();
        assertState(state, 0, RAW);
        assertEquals(ingress(0, RAW), state);
        for (Field field : Field.values()) {
            UnknownLiveByte failure = assertThrows(UnknownLiveByte.class, () -> read(state, field));
            assertEquals(field.nativeAddress(), failure.nativeAddress());
        }
    }

    @Test
    void all256MasksExposeOnlyTheirDeclaredBytes() {
        for (int mask = 0; mask < 256; mask++) {
            assertState(ingress(mask, RAW), mask, RAW);
        }
    }

    @Test
    void all256MasksReadEachFieldOrReportItsFirstMissingByte() {
        Field[] fields = {Field.TIME_LONG, Field.WATER_HEIGHT2,
                Field.WATER_ROUTINE, Field.WATER_FULLSCREEN};
        int[] offsets = {0, 4, 6, 7};
        int[] widths = {4, 2, 1, 1};
        int[] expected = {0xDEADBEEF, 0x1234, 0x56, 0x9A};
        for (int mask = 0; mask < 256; mask++) {
            State state = ingress(mask, RAW);
            for (int f = 0; f < fields.length; f++) {
                Field field = fields[f];
                int missing = -1;
                for (int i = offsets[f]; i < offsets[f] + widths[f]; i++) {
                    if ((mask & (1 << i)) == 0) {
                        missing = i;
                        break;
                    }
                }
                if (missing < 0) {
                    assertEquals(expected[f], read(state, field));
                } else {
                    int address = ADDRESSES[missing];
                    UnknownLiveByte failure = assertThrows(UnknownLiveByte.class, () -> read(state, field));
                    assertEquals(address, failure.nativeAddress());
                }
                assertState(state, mask, RAW);
            }
        }
    }

    @Test
    void missingBytesAreReportedInAscendingNativeOrder() {
        for (int i = 0; i < 4; i++) {
            State state = ingress(0xFF & ~((1 << (4 - i)) - 1 << i), RAW);
            UnknownLiveByte failure = assertThrows(UnknownLiveByte.class, () -> read(state, Field.TIME_LONG));
            assertEquals(ADDRESSES[i], failure.nativeAddress());
        }
        State firstHeightMissing = ingress(0xCF, RAW);
        State secondHeightMissing = ingress(0xDF, RAW);
        assertEquals(0xFFF648, assertThrows(UnknownLiveByte.class,
                () -> read(firstHeightMissing, Field.WATER_HEIGHT2)).nativeAddress());
        assertEquals(0xFFF649, assertThrows(UnknownLiveByte.class,
                () -> read(secondHeightMissing, Field.WATER_HEIGHT2)).nativeAddress());
    }

    @Test
    void byteReadsRejectNonCanonicalAndUnownedAddresses() {
        int[] invalid = {Integer.MIN_VALUE, -1, 0, 0x1514, 0xF648, 0xFF1513, 0xFF1518,
                0xFFF647, 0xFFF64A, 0xFFF64B, 0xFFF64C, 0xFFF64F,
                0xFFFF1514, 0xFFFFF648, 0x1FF1514, Integer.MAX_VALUE};
        for (int address : invalid) {
            assertThrows(IllegalArgumentException.class, () -> unknown().knownByte(address));
            assertThrows(IllegalArgumentException.class, () -> ingress(0xFF, RAW).knownByte(address));
        }
    }

    @Test
    void ingressRequiresExactEightBytesAndAnUnsignedKnowledgeByte() {
        assertThrows(NullPointerException.class, () -> declaredDevIngress(null, 0));
        for (int length : new int[]{0, 1, 7, 9, 16}) {
            CdAssetBytes bytes = CdAssetBytes.copyOf(new byte[length]);
            assertThrows(IllegalArgumentException.class, () -> declaredDevIngress(bytes, 0));
        }
        CdAssetBytes bytes = CdAssetBytes.copyOf(new byte[8]);
        for (int mask : new int[]{-1, 256, 0x10000, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> declaredDevIngress(bytes, mask));
        }
        assertEquals(0, declaredDevIngress(bytes, 0).knownMask());
        assertEquals(255, declaredDevIngress(bytes, 255).knownMask());
    }

    @Test
    void unknownIngressBackingIsCanonicalForAll256Masks() {
        for (int mask = 0; mask < 256; mask++) {
            int[] changed = RAW.clone();
            for (int i = 0; i < 8; i++) {
                if ((mask & (1 << i)) == 0) { changed[i] ^= 0xFF; }
            }
            State first = ingress(mask, RAW);
            State second = ingress(mask, changed);
            assertEquals(first, second);
            assertEquals(second, first);
            assertEquals(first.hashCode(), second.hashCode());
            assertState(second, mask, RAW);
        }
    }

    @Test
    void knownZeroAndKnowledgeParticipateInValueIdentity() {
        State none = unknown();
        State oneZero = ingress(1, new int[8]);
        State allZero = ingress(255, new int[8]);
        assertNotEquals(none, oneZero);
        assertNotEquals(oneZero, allZero);
        assertNotEquals(none, allZero);
        assertEquals(OptionalInt.empty(), none.knownByte(0xFF1514));
        assertEquals(OptionalInt.of(0), oneZero.knownByte(0xFF1514));
        assertEquals(allZero, clearTimeAtSpawnZero(clearMiscAtLevelStart(none)));
        assertEquals(allZero.hashCode(), clearMiscAtLevelStart(clearTimeAtSpawnZero(none)).hashCode());
        assertEquals(allZero, allZero);
        assertFalse(allZero.equals(null));
        assertFalse(allZero.equals("zero"));
        assertNotEquals(ingress(255, RAW), ingress(255, 0xDE, 0xAD, 0xBE, 0xEE, 0x12, 0x34, 0x56, 0x9A));
    }

    @Test
    void miscClearPreservesRawTimeAndItsKnowledgeForAll256Masks() {
        for (int mask = 0; mask < 256; mask++) {
            State before = ingress(mask, RAW);
            State after = clearMiscAtLevelStart(before);
            assertState(after, mask | 0xF0, 0xDE, 0xAD, 0xBE, 0xEF, 0, 0, 0, 0);
            assertState(before, mask, RAW);
            assertEquals(after, clearMiscAtLevelStart(after));
        }
    }

    @Test
    void spawnZeroClearPreservesWaterAndItsKnowledgeForAll256Masks() {
        for (int mask = 0; mask < 256; mask++) {
            State before = ingress(mask, RAW);
            State after = clearTimeAtSpawnZero(before);
            assertState(after, mask | 0x0F, 0, 0, 0, 0, 0x12, 0x34, 0x56, 0x9A);
            assertState(before, mask, RAW);
            assertEquals(after, clearTimeAtSpawnZero(after));
        }
    }

    @Test
    void clearOperationsEstablishOnlyTheirRepresentedNativeSpans() {
        State misc = clearMiscAtLevelStart(unknown());
        State time = clearTimeAtSpawnZero(unknown());
        assertState(misc, 0xF0, new int[8]);
        assertState(time, 0x0F, new int[8]);
        assertEquals(0, read(misc, Field.WATER_HEIGHT2));
        assertEquals(0, read(misc, Field.WATER_ROUTINE));
        assertEquals(0, read(misc, Field.WATER_FULLSCREEN));
        assertThrows(UnknownLiveByte.class, () -> read(misc, Field.TIME_LONG));
        assertEquals(0, read(time, Field.TIME_LONG));
        assertThrows(UnknownLiveByte.class, () -> read(time, Field.WATER_HEIGHT2));
        assertEquals(clearMiscAtLevelStart(time), clearTimeAtSpawnZero(misc));
        assertState(unknown(), 0, RAW);
    }

    @Test
    void longStoresAcceptAllIntBitPatternsWithoutDecimalNormalization() {
        int[] values = {0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE,
                0xAABBCCFF, 0xFFFEFDFC, 0x0005FF3B, 0x000A0000, 0x00093B3B};
        for (int value : values) {
            State before = ingress(0xA0, RAW);
            State after = writeTimeLong(before, value);
            assertEquals(value, read(after, Field.TIME_LONG));
            assertState(after, 0xAF, value >>> 24, value >>> 16 & 255,
                    value >>> 8 & 255, value & 255, 0x12, 0x34, 0x56, 0x9A);
            assertState(before, 0xA0, RAW);
        }
    }

    @Test
    void timeByteStoresPreserveEveryOtherByteForAll256Masks() {
        TimeByte[] fields = {TimeByte.MINUTES, TimeByte.SECONDS, TimeByte.FRAMES};
        for (int mask = 0; mask < 256; mask++) {
            for (int t = 0; t < fields.length; t++) {
                for (int value : new int[]{0, 255}) {
                    State before = ingress(mask, RAW);
                    int[] expected = RAW.clone();
                    expected[t + 1] = value;
                    State after = writeTimeByte(before, fields[t], value);
                    assertState(after, mask | (1 << (t + 1)), expected);
                    assertState(before, mask, RAW);
                }
            }
        }
    }

    @Test
    void rawByteResultsPreserveAnArbitraryLeadingTimeByte() {
        State state = writeTimeLong(unknown(), 0xAABBCCFF);
        State frames = writeTimeByte(state, TimeByte.FRAMES, 0);
        State seconds = writeTimeByte(frames, TimeByte.SECONDS, 0xFF);
        State minutes = writeTimeByte(seconds, TimeByte.MINUTES, 0xFE);
        assertEquals(0xAABBCC00, read(frames, Field.TIME_LONG));
        assertEquals(0xAABBFF00, read(seconds, Field.TIME_LONG));
        assertEquals(0xAAFEFF00, read(minutes, Field.TIME_LONG));
        assertEquals(0xAABBCCFF, read(state, Field.TIME_LONG));
    }

    @Test
    void waterStoresPreserveEveryOtherByteForAll256Masks() {
        for (int mask = 0; mask < 256; mask++) {
            State before = ingress(mask, RAW);
            assertState(writeWaterRoutine(before, 0xE1), mask | 0x40,
                    0xDE, 0xAD, 0xBE, 0xEF, 0x12, 0x34, 0xE1, 0x9A);
            assertState(writeWaterHeight2(before, 0xFEDC), mask | 0x30,
                    0xDE, 0xAD, 0xBE, 0xEF, 0xFE, 0xDC, 0x56, 0x9A);
            assertState(writeWaterFullscreen(before, 0x80), mask | 0x80,
                    0xDE, 0xAD, 0xBE, 0xEF, 0x12, 0x34, 0x56, 0x80);
            assertState(before, mask, RAW);
        }
    }

    @Test
    void allUnsignedByteValuesAndWordEdgesRemainRaw() {
        for (int value = 0; value < 256; value++) {
            assertState(writeTimeByte(unknown(), TimeByte.MINUTES, value), 2,
                    0, value, 0, 0, 0, 0, 0, 0);
            assertState(writeTimeByte(unknown(), TimeByte.SECONDS, value), 4,
                    0, 0, value, 0, 0, 0, 0, 0);
            assertState(writeTimeByte(unknown(), TimeByte.FRAMES, value), 8,
                    0, 0, 0, value, 0, 0, 0, 0);
            assertEquals(value, read(writeWaterRoutine(unknown(), value), Field.WATER_ROUTINE));
            assertEquals(value, read(writeWaterFullscreen(unknown(), value), Field.WATER_FULLSCREEN));
        }
        for (int value : new int[]{0, 1, 0xFF, 0x100, 0x7FFF, 0x8000, 0xFF00, 0xFFFF}) {
            State state = writeWaterHeight2(unknown(), value);
            assertEquals(value, read(state, Field.WATER_HEIGHT2));
            assertState(state, 0x30, 0, 0, 0, 0, value >>> 8, value & 255, 0, 0);
        }
    }

    @Test
    void partialFieldReadsDoNotDemandOtherFieldsOrChangeState() {
        State state = ingress(0xC4, RAW);
        assertEquals(0x56, read(state, Field.WATER_ROUTINE));
        assertEquals(0x9A, read(state, Field.WATER_FULLSCREEN));
        assertEquals(OptionalInt.of(0xBE), state.knownByte(0xFF1516));
        assertEquals(0xFF1514, assertThrows(UnknownLiveByte.class,
                () -> read(state, Field.TIME_LONG)).nativeAddress());
        assertEquals(0xFFF648, assertThrows(UnknownLiveByte.class,
                () -> read(state, Field.WATER_HEIGHT2)).nativeAddress());
        assertState(state, 0xC4, RAW);
    }

    @Test
    void invalidStoreWidthsCannotChangeTheInput() {
        State state = ingress(0x55, RAW);
        State captured = ingress(0x55, RAW);
        for (int invalid : new int[]{-1, 256, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            for (TimeByte field : TimeByte.values()) {
                assertThrows(IllegalArgumentException.class, () -> writeTimeByte(state, field, invalid));
            }
            assertThrows(IllegalArgumentException.class, () -> writeWaterRoutine(state, invalid));
            assertThrows(IllegalArgumentException.class, () -> writeWaterFullscreen(state, invalid));
        }
        for (int invalid : new int[]{-1, 65536, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> writeWaterHeight2(state, invalid));
        }
        assertEquals(captured, state);
        assertEquals(captured.hashCode(), state.hashCode());
        assertState(state, 0x55, RAW);
    }

    @Test
    void nullOperandsAreRejectedWithoutAReplacementValue() {
        State state = ingress(0xFF, RAW);
        assertThrows(NullPointerException.class, () -> read(null, Field.TIME_LONG));
        assertThrows(NullPointerException.class, () -> read(state, null));
        assertThrows(NullPointerException.class, () -> clearMiscAtLevelStart(null));
        assertThrows(NullPointerException.class, () -> clearTimeAtSpawnZero(null));
        assertThrows(NullPointerException.class, () -> writeTimeLong(null, 0xDEADBEEF));
        assertThrows(NullPointerException.class, () -> writeTimeByte(null, TimeByte.FRAMES, 0));
        assertThrows(NullPointerException.class, () -> writeTimeByte(state, null, 0));
        assertThrows(NullPointerException.class, () -> writeWaterRoutine(null, 0));
        assertThrows(NullPointerException.class, () -> writeWaterHeight2(null, 0));
        assertThrows(NullPointerException.class, () -> writeWaterFullscreen(null, 0));
        assertState(state, 0xFF, RAW);
    }

    @Test
    void ingressAndCapturedValuesDoNotAliasCallerArrays() {
        byte[] caller = {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF,
                0x12, 0x34, 0x56, (byte) 0x9A};
        CdAssetBytes bytes = CdAssetBytes.copyOf(caller);
        State captured = declaredDevIngress(bytes, 0xFF);
        caller[0] = 0;
        caller[6] = 0;
        byte[] copy = bytes.copy();
        copy[1] = 0;
        copy[7] = 0;
        State later = writeWaterRoutine(writeTimeLong(captured, 0), 0);
        assertState(captured, 0xFF, RAW);
        assertState(later, 0xFF, 0, 0, 0, 0, 0x12, 0x34, 0, 0x9A);
        assertEquals(ingress(0xFF, RAW), captured);
    }

    @Test
    void immutableCaptureReplaysTheSameFiniteWriters() {
        State captured = ingress(0xA5, RAW);
        State first = replayStores(captured);
        State unrelatedLater = clearTimeAtSpawnZero(clearMiscAtLevelStart(first));
        State replayed = replayStores(captured);
        assertEquals(first, replayed);
        assertEquals(first.hashCode(), replayed.hashCode());
        assertState(first, 0xFF, 0xAA, 0x05, 0xFF, 0x3B, 0xFE, 0xDC, 0x80, 0xFF);
        assertState(captured, 0xA5, RAW);
        assertState(unrelatedLater, 0xFF, new int[8]);
    }

    @Test
    void suppliedCheckpointUnderflowBytesAreNeverNormalized() {
        State restored = writeTimeLong(unknown(), 0x00050000);
        State framesStored = writeTimeByte(restored, TimeByte.FRAMES, 59);
        State secondsStored = writeTimeByte(framesStored, TimeByte.SECONDS, 255);
        assertEquals(0x00050000, read(restored, Field.TIME_LONG));
        assertEquals(0x0005003B, read(framesStored, Field.TIME_LONG));
        assertEquals(0x0005FF3B, read(secondsStored, Field.TIME_LONG));
        assertEquals(0x0005FF3B, read(clearMiscAtLevelStart(secondsStored), Field.TIME_LONG));
        assertState(secondsStored, 0x0F, 0, 5, 255, 59, 0, 0, 0, 0);
    }

    private static State replayStores(State captured) {
        State state = writeTimeLong(captured, 0xAA050000);
        state = writeTimeByte(state, TimeByte.FRAMES, 59);
        state = writeTimeByte(state, TimeByte.SECONDS, 255);
        state = writeWaterRoutine(state, 0x80);
        state = writeWaterHeight2(state, 0xFEDC);
        return writeWaterFullscreen(state, 255);
    }

    private static State ingress(int mask, int... values) {
        assertEquals(8, values.length);
        byte[] bytes = new byte[8];
        for (int i = 0; i < 8; i++) { bytes[i] = (byte) values[i]; }
        return declaredDevIngress(CdAssetBytes.copyOf(bytes), mask);
    }

    private static void assertState(State state, int mask, int... values) {
        assertEquals(8, values.length);
        assertEquals(mask, state.knownMask());
        for (int i = 0; i < 8; i++) {
            OptionalInt expected = (mask & (1 << i)) == 0
                    ? OptionalInt.empty() : OptionalInt.of(values[i]);
            assertEquals(expected, state.knownByte(ADDRESSES[i]), "canonical byte " + i + ", mask " + mask);
        }
    }
}

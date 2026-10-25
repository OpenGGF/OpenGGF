package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.OptionalInt;

import static com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Boundary.OUTSIDE_OWNED_WINDOW;
import static com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN;
import static com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.AT_7400;
import static com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.AT_8120;
import static com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.AT_8480;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ROM-free byte-ownership specifications; ALL JAVA UNRUN in the source-only packet.
 * Expectations derive from scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * PLC_Cam1_Full (src/Level/Palmtree Panic/Data (Act 1 Present).asm:85-100),
 * PLC_Cam2_Incr (:183-190), and the six corresponding pinned Nemesis headers.
 * Headers yield Mosqui70/Pata50/Anton22/Plant28/Ramp63/Tamabboh59 tiles.
 * NemPCD_WriteRowToVDP (src/Level/Functions (General).asm:495-510) writes
 * four bytes per row; DecompPLC_Main (:752-758) completes eight rows per tile.
 * No decoder, media, object ID, queue, service clock or renderer is substituted.
 */
class TestCdR11aCameraArtResidency {
    // Deliberately independent literals: $7400+$8C0, $8120+$2C0, $8480+$B80.
    private static final byte[] MOSQUI = distinctBytes(2240, 0x11);
    private static final byte[] PLANT = distinctBytes(896, 0x43);
    private static final byte[] RAMP = distinctBytes(2016, 0x75);
    private static final byte[] PATA = distinctBytes(1600, 0xA7);
    private static final byte[] ANTON = distinctBytes(704, 0xD9);
    private static final byte[] TAMABBOH = distinctBytes(1888, 0x2B);

    @Test
    void devPreloadRetainsExactlyTheThreeSuppliedSourcesAndTheirKnownMasks() {
        // Catches padded/shifted sources or treating DEV as a complete cold clear.
        var state = seed();
        assertRegion(state, AT_7400, MOSQUI, allKnown(2240));
        assertRegion(state, AT_8120, new byte[704], new byte[704]);
        byte[] expected = new byte[2944];
        System.arraycopy(PLANT, 0, expected, 0, 896);
        System.arraycopy(RAMP, 0, expected, 928, 2016);
        byte[] known = allKnown(2944);
        Arrays.fill(known, 896, 928, (byte) 0);
        assertRegion(state, AT_8480, expected, known);
        assertEquals(0x7400, AT_7400.destinationWord());
        assertEquals(0x8C0, AT_7400.byteLength());
        assertEquals(70, AT_7400.tileCount());
        assertEquals(0x8120, AT_8120.destinationWord());
        assertEquals(0x2C0, AT_8120.byteLength());
        assertEquals(22, AT_8120.tileCount());
        assertEquals(0x8480, AT_8480.destinationWord());
        assertEquals(0xB80, AT_8480.byteLength());
        assertEquals(92, AT_8480.tileCount());
    }

    @Test
    void explicitlySuppliedFullIngressCanContainKnownZeroBytes() {
        // Catches a factory that conflates zero with unknown or invents seed provenance.
        byte[] first = new byte[2240];
        byte[] second = new byte[704];
        byte[] third = new byte[2944];
        var state = CdR11aCameraArtResidency.knownSeed(bytes(first), bytes(second), bytes(third));
        for (int id : new int[]{0x3A0, 0x3E5, 0x409, 0x41E, 0x424, 0x440, 0x47F}) {
            assertArrayEquals(new byte[32], CdR11aCameraArtResidency.readTile(state, id).copy());
        }
        assertRegion(state, AT_8120, second, allKnown(704));
        assertRegion(state, AT_8480, third, allKnown(2944));
    }

    @Test
    void seedsRejectTruncatedOrExtendedSourcesInsteadOfPaddingThem() {
        for (int bad : new int[]{2239, 2241}) {
            assertThrows(IllegalArgumentException.class,
                    () -> CdR11aCameraArtResidency.devSeed(bytes(new byte[bad]), bytes(PLANT), bytes(RAMP)));
            assertThrows(IllegalArgumentException.class,
                    () -> CdR11aCameraArtResidency.knownSeed(bytes(new byte[bad]), bytes(ANTON), bytes(new byte[2944])));
        }
        for (int bad : new int[]{895, 897}) {
            assertThrows(IllegalArgumentException.class,
                    () -> CdR11aCameraArtResidency.devSeed(bytes(MOSQUI), bytes(new byte[bad]), bytes(RAMP)));
        }
        for (int bad : new int[]{2015, 2017}) {
            assertThrows(IllegalArgumentException.class,
                    () -> CdR11aCameraArtResidency.devSeed(bytes(MOSQUI), bytes(PLANT), bytes(new byte[bad])));
        }
        for (int bad : new int[]{703, 705}) {
            assertThrows(IllegalArgumentException.class,
                    () -> CdR11aCameraArtResidency.knownSeed(bytes(MOSQUI), bytes(new byte[bad]), bytes(new byte[2944])));
        }
        for (int bad : new int[]{2943, 2945}) {
            assertThrows(IllegalArgumentException.class,
                    () -> CdR11aCameraArtResidency.knownSeed(bytes(MOSQUI), bytes(ANTON), bytes(new byte[bad])));
        }
    }

    @Test
    void gapCannotBeReadAsAnInventedZeroTileBeforeAnActualWrite() {
        var state = seed();
        assertBoundary(state, 0x440, UNKNOWN_PATTERN, 0x8800);
        var snapshot = state.region(AT_8480);
        for (int offset = 896; offset < 928; offset++) {
            assertTrue(snapshot.knownByte(offset).isEmpty());
            assertEquals(0, snapshot.knownMask().unsignedByte(offset));
        }
        assertArrayEquals(Arrays.copyOfRange(PLANT, 864, 896),
                CdR11aCameraArtResidency.readTile(state, 0x43F).copy());
        assertArrayEquals(Arrays.copyOfRange(RAMP, 0, 32),
                CdR11aCameraArtResidency.readTile(state, 0x441).copy());
    }

    @Test
    void antonBecomesKnownOnlyTileByTileAndCanBeCompletelyWritten() {
        var initial = seed();
        assertBoundary(initial, 0x409, UNKNOWN_PATTERN, 0x8120);
        assertBoundary(initial, 0x41E, UNKNOWN_PATTERN, 0x83C0);
        var partial = commit(initial, 0x8120, ANTON, 3);
        byte[] expected = new byte[704];
        System.arraycopy(ANTON, 0, expected, 0, 96);
        byte[] mask = new byte[704];
        Arrays.fill(mask, 0, 96, (byte) 1);
        assertRegion(partial, AT_8120, expected, mask);
        assertArrayEquals(Arrays.copyOfRange(ANTON, 64, 96),
                CdR11aCameraArtResidency.readTile(partial, 0x40B).copy());
        assertBoundary(partial, 0x40C, UNKNOWN_PATTERN, 0x8180);
        var complete = commit(initial, 0x8120, ANTON, 22);
        assertRegion(complete, AT_8120, ANTON, allKnown(704));
        assertEquals(initial.region(AT_7400), complete.region(AT_7400));
        assertEquals(initial.region(AT_8480), complete.region(AT_8480));
        assertBoundary(initial, 0x409, UNKNOWN_PATTERN, 0x8120);
    }

    @Test
    void firstThreePataTilesChangeOnlyNinetySixCurrentBytes() {
        var initial = seed();
        var partial = commit(initial, 0x7600, PATA, 3);
        byte[] expected = MOSQUI.clone();
        System.arraycopy(PATA, 0, expected, 0x200, 96);
        assertRegion(partial, AT_7400, expected, allKnown(2240));
        assertEquals(initial.region(AT_8120), partial.region(AT_8120));
        assertEquals(initial.region(AT_8480), partial.region(AT_8480));
        assertArrayEquals(Arrays.copyOfRange(MOSQUI, 0x260, 0x280),
                CdR11aCameraArtResidency.readTile(partial, 0x3B3).copy());
    }

    @Test
    void fiftyPataTilesReplaceTheMiddleLeavingSixteenPrefixAndFourSuffixTiles() {
        var initial = seed();
        var complete = commit(initial, 0x7600, PATA, 50);
        byte[] expected = MOSQUI.clone();
        System.arraycopy(PATA, 0, expected, 0x200, 1600);
        assertRegion(complete, AT_7400, expected, allKnown(2240));
        assertArrayEquals(Arrays.copyOfRange(MOSQUI, 0x1E0, 0x200),
                CdR11aCameraArtResidency.readTile(complete, 0x3AF).copy());
        assertArrayEquals(Arrays.copyOfRange(PATA, 1568, 1600),
                CdR11aCameraArtResidency.readTile(complete, 0x3E1).copy());
        assertArrayEquals(Arrays.copyOfRange(MOSQUI, 0x840, 0x860),
                CdR11aCameraArtResidency.readTile(complete, 0x3E2).copy());
        assertEquals(initial.region(AT_8120), complete.region(AT_8120));
        assertEquals(initial.region(AT_8480), complete.region(AT_8480));
    }

    @Test
    void mosquiReloadRestoresOnlyEachActuallyCommittedTileThenAllSeventy() {
        var initial = seed();
        var state = commit(initial, 0x7600, PATA, 50);
        byte[] expected = MOSQUI.clone();
        System.arraycopy(PATA, 0, expected, 0x200, 1600);
        for (int tile = 0; tile < 70; tile++) {
            state = CdR11aCameraArtResidency.writeTile(state, 0x7400 + tile * 32,
                    bytes(MOSQUI).slice(tile * 32, 32));
            System.arraycopy(MOSQUI, tile * 32, expected, tile * 32, 32);
            assertRegion(state, AT_7400, expected, allKnown(2240));
        }
        assertEquals(initial, state);
        assertEquals(initial.hashCode(), state.hashCode());
    }

    @Test
    void tamabbohOverwritesPlantGapAndRampButRetainsBothOutsideTails() {
        var initial = seed();
        var result = commit(initial, 0x8500, TAMABBOH, 59);
        byte[] expected = new byte[2944];
        System.arraycopy(PLANT, 0, expected, 0, 128);
        System.arraycopy(TAMABBOH, 0, expected, 128, 1888);
        System.arraycopy(RAMP, 1088, expected, 2016, 928);
        assertRegion(result, AT_8480, expected, allKnown(2944));
        assertArrayEquals(Arrays.copyOfRange(TAMABBOH, 768, 800),
                CdR11aCameraArtResidency.readTile(result, 0x440).copy());
        assertArrayEquals(Arrays.copyOfRange(TAMABBOH, 1856, 1888),
                CdR11aCameraArtResidency.readTile(result, 0x462).copy());
        assertArrayEquals(Arrays.copyOfRange(RAMP, 1088, 1120),
                CdR11aCameraArtResidency.readTile(result, 0x463).copy());
        assertEquals(initial.region(AT_7400), result.region(AT_7400));
        assertEquals(initial.region(AT_8120), result.region(AT_8120));
    }

    @Test
    void twentyFourTamabbohTilesLeaveGapUnknownAndTheTwentyFifthMakesItKnown() {
        var beforeGap = commit(seed(), 0x8500, TAMABBOH, 24);
        assertBoundary(beforeGap, 0x440, UNKNOWN_PATTERN, 0x8800);
        var afterGap = CdR11aCameraArtResidency.writeTile(beforeGap, 0x8800,
                bytes(TAMABBOH).slice(768, 32));
        assertArrayEquals(Arrays.copyOfRange(TAMABBOH, 768, 800),
                CdR11aCameraArtResidency.readTile(afterGap, 0x440).copy());
        assertArrayEquals(Arrays.copyOfRange(RAMP, 0, 32),
                CdR11aCameraArtResidency.readTile(afterGap, 0x441).copy());
        assertBoundary(beforeGap, 0x440, UNKNOWN_PATTERN, 0x8800);
    }

    @Test
    void allThreeFirstBatchProducersShareTheSamePhysicalAuthority() {
        var initial = seed();
        var pata = commit(initial, 0x7600, PATA, 50);
        var anton = commit(pata, 0x8120, ANTON, 22);
        var tamabboh = commit(anton, 0x8500, TAMABBOH, 59);
        assertEquals(pata.region(AT_7400), tamabboh.region(AT_7400));
        assertRegion(tamabboh, AT_8120, ANTON, allKnown(704));
        assertArrayEquals(Arrays.copyOfRange(PATA, 0, 32),
                CdR11aCameraArtResidency.readTile(tamabboh, 0x3B0).copy());
        assertArrayEquals(Arrays.copyOfRange(TAMABBOH, 0, 32),
                CdR11aCameraArtResidency.readTile(tamabboh, 0x428).copy());
        assertBoundary(initial, 0x409, UNKNOWN_PATTERN, 0x8120);
        assertBoundary(initial, 0x440, UNKNOWN_PATTERN, 0x8800);
    }

    @Test
    void finalTileInEachWindowIsWritableWithoutExtendingOwnership() {
        var initial = seed();
        for (int address : new int[]{0x7CA0, 0x83C0, 0x8FE0}) {
            byte[] tile = distinctBytes(32, address >>> 5);
            var result = CdR11aCameraArtResidency.writeTile(initial, address, bytes(tile));
            assertArrayEquals(tile, CdR11aCameraArtResidency.readTile(result, address >>> 5).copy());
        }
        for (int address : new int[]{0x7CC0, 0x83E0, 0x9000}) {
            var fault = assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,
                    () -> CdR11aCameraArtResidency.writeTile(initial, address, bytes(new byte[32])));
            assertEquals(OUTSIDE_OWNED_WINDOW, fault.boundary());
            assertEquals(address, fault.destinationWord());
            assertEquals(address >>> 5, fault.nativePatternId());
        }
    }

    @Test
    void outsideTilesIncludingSeparatelyOwnedPlayerAndPowerupAlwaysReachTypedBoundary() {
        var state = seed();
        // Includes both inter-window gaps and separately owned $A880/$F000 windows.
        for (int id : new int[]{0, 0x39F, 0x3E6, 0x408, 0x41F, 0x423, 0x480, 0x544, 0x780, 0x7FF}) {
            assertBoundary(state, id, OUTSIDE_OWNED_WINDOW, id * 32);
            var fault = assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,
                    () -> CdR11aCameraArtResidency.writeTile(state, id * 32, bytes(new byte[32])));
            assertEquals(OUTSIDE_OWNED_WINDOW, fault.boundary());
            assertEquals(id, fault.nativePatternId());
            assertEquals(id * 32, fault.destinationWord());
        }
    }

    @Test
    void destinationRequiresAnUnsignedNativeWordAndExactTileAlignmentWithoutWrapping() {
        var state = seed();
        for (int address : new int[]{-1, -32, 0x10000, 0x17400, Integer.MIN_VALUE,
                Integer.MAX_VALUE, 0x7401, 0x7410, 0x7FFF, 0xFFFF}) {
            assertThrows(IllegalArgumentException.class,
                    () -> CdR11aCameraArtResidency.writeTile(state, address, bytes(new byte[32])));
        }
        assertRegion(state, AT_7400, MOSQUI, allKnown(2240));
    }

    @Test
    void readerRequiresFinalElevenBitIdRatherThanMaskingSatFlagsOrAcceptingAnObjectId() {
        var state = seed();
        for (int id : new int[]{-1, 0x800, 0x13A0, 0x23A0, 0x83A0, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> CdR11aCameraArtResidency.readTile(state, id));
        }
        // An object ID is just an unowned native address here; there is no actor-selected bank.
        assertBoundary(state, 0x3E, OUTSIDE_OWNED_WINDOW, 0x7C0);
        var changed = CdR11aCameraArtResidency.writeTile(state, 0x7600, bytes(PATA).slice(0, 32));
        assertArrayEquals(Arrays.copyOfRange(PATA, 0, 32), CdR11aCameraArtResidency.readTile(changed, 0x3B0).copy());
        assertArrayEquals(Arrays.copyOfRange(MOSQUI, 512, 544), CdR11aCameraArtResidency.readTile(state, 0x3B0).copy());
    }

    @Test
    void commitsRequireExactlyThirtyTwoBytesEvenWhenTheNextTileIsOwned() {
        var state = seed();
        for (int length : new int[]{0, 1, 31, 33, 64, 1600}) {
            assertThrows(IllegalArgumentException.class,
                    () -> CdR11aCameraArtResidency.writeTile(state, 0x7600, bytes(new byte[length])));
        }
        assertEquals(seed(), state);
    }

    @Test
    void returnedStatesTilesAndMasksStayImmutableAcrossNewWritesAndArrayMutations() {
        byte[] supplied = distinctBytes(32, 0xC1);
        byte[] expectedTile = supplied.clone();
        var initial = seed();
        var oldRegion = initial.region(AT_7400);
        var oldTile = CdR11aCameraArtResidency.readTile(initial, 0x3B0);
        int oldHash = initial.hashCode();
        var tileInput = bytes(supplied);
        var changed = CdR11aCameraArtResidency.writeTile(initial, 0x7600, tileInput);
        Arrays.fill(supplied, (byte) 0);
        byte[] escapedTileCopy = CdR11aCameraArtResidency.readTile(changed, 0x3B0).copy();
        byte[] escapedMaskCopy = changed.region(AT_7400).knownMask().copy();
        Arrays.fill(escapedTileCopy, (byte) 0);
        Arrays.fill(escapedMaskCopy, (byte) 0);
        assertArrayEquals(expectedTile, CdR11aCameraArtResidency.readTile(changed, 0x3B0).copy());
        assertArrayEquals(Arrays.copyOfRange(MOSQUI, 512, 544), oldTile.copy());
        assertEquals(oldRegion, initial.region(AT_7400));
        assertEquals(oldHash, initial.hashCode());
        assertArrayEquals(allKnown(2240), changed.region(AT_7400).knownMask().copy());
    }

    @Test
    void stateAndRegionEqualityUseContentAcrossDifferentByteWrappersAndReplays() {
        var one = seed();
        var another = CdR11aCameraArtResidency.devSeed(bytes(MOSQUI.clone()), bytes(PLANT.clone()), bytes(RAMP.clone()));
        assertEquals(one, another);
        assertEquals(one.hashCode(), another.hashCode());
        assertEquals(one.region(AT_8480), another.region(AT_8480));
        assertEquals(one.region(AT_8480).hashCode(), another.region(AT_8480).hashCode());
        var left = commit(one, 0x7600, PATA, 3);
        var right = commit(another, 0x7600, PATA.clone(), 3);
        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertNotEquals(one, left);
        assertNotEquals(one, null);
        assertNotEquals(one, "camera");
    }

    @Test
    void knownZeroAndUnknownHaveDifferentValueIdentityAndCollectionBehavior() {
        var unknown = CdR11aCameraArtResidency.devSeed(bytes(new byte[2240]), bytes(new byte[896]), bytes(new byte[2016]));
        var known = CdR11aCameraArtResidency.knownSeed(bytes(new byte[2240]), bytes(new byte[704]), bytes(new byte[2944]));
        var oneTileKnown = CdR11aCameraArtResidency.writeTile(unknown, 0x8800, bytes(new byte[32]));
        assertNotEquals(unknown, known);
        assertNotEquals(unknown, oneTileKnown);
        assertNotEquals(unknown.region(AT_8480), oneTileKnown.region(AT_8480));
        assertTrue(unknown.region(AT_8480).knownByte(896).isEmpty());
        assertEquals(OptionalInt.of(0), oneTileKnown.region(AT_8480).knownByte(896));
        var values = new HashSet<CdR11aCameraArtResidency.State>();
        values.add(unknown);
        values.add(known);
        values.add(oneTileKnown);
        values.add(CdR11aCameraArtResidency.devSeed(bytes(new byte[2240]), bytes(new byte[896]), bytes(new byte[2016])));
        assertEquals(3, values.size());
        assertTrue(values.contains(CdR11aCameraArtResidency.writeTile(unknown, 0x8800, bytes(new byte[32]))));
    }

    @Test
    void regionDiagnosticsNeverExposeUnknownBackingAndValidateOffsets() {
        var state = seed();
        var anton = state.region(AT_8120);
        assertEquals(AT_8120, anton.region());
        assertArrayEquals(new byte[704], anton.knownMask().copy());
        assertTrue(anton.knownByte(0).isEmpty());
        assertTrue(anton.knownByte(703).isEmpty());
        assertThrows(IndexOutOfBoundsException.class, () -> anton.knownByte(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> anton.knownByte(704));
        assertNotEquals(anton, state.region(AT_7400));
        assertNotEquals(anton, null);
        assertNotEquals(anton, "region");
    }

    @Test
    void capturedImmutableStateCanReplayTheSameTileSequenceWithoutRestoreSideEffects() {
        var initial = seed();
        var captured = commit(initial, 0x7600, PATA, 3);
        captured = commit(captured, 0x8120, ANTON, 2);
        captured = commit(captured, 0x8500, TAMABBOH, 25);
        int capturedHash = captured.hashCode();
        var firstReplay = commit(commit(commit(captured, 0x7600, PATA, 50), 0x8120, ANTON, 22), 0x8500, TAMABBOH, 59);
        var secondReplay = commit(commit(commit(captured, 0x7600, PATA, 50), 0x8120, ANTON, 22), 0x8500, TAMABBOH, 59);
        assertEquals(firstReplay, secondReplay);
        assertEquals(firstReplay.hashCode(), secondReplay.hashCode());
        assertEquals(capturedHash, captured.hashCode());
        assertBoundary(captured, 0x40B, UNKNOWN_PATTERN, 0x8160);
        assertArrayEquals(Arrays.copyOfRange(PATA, 0, 32), CdR11aCameraArtResidency.readTile(captured, 0x3B0).copy());
        assertBoundary(initial, 0x440, UNKNOWN_PATTERN, 0x8800);
    }

    @Test
    void aLaterOwnershipFaultDoesNotEraseAPreviouslyReturnedCommittedPrefix() {
        var initial = seed();
        var retained = CdR11aCameraArtResidency.writeTile(initial, 0x7CA0, bytes(PATA).slice(0, 32));
        int retainedHash = retained.hashCode();
        var fault = assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,
                () -> CdR11aCameraArtResidency.writeTile(retained, 0x7CC0, bytes(PATA).slice(32, 32)));
        assertEquals(OUTSIDE_OWNED_WINDOW, fault.boundary());
        assertEquals(0x7CC0, fault.destinationWord());
        assertArrayEquals(Arrays.copyOfRange(PATA, 0, 32), CdR11aCameraArtResidency.readTile(retained, 0x3E5).copy());
        assertArrayEquals(Arrays.copyOfRange(MOSQUI, 2208, 2240), CdR11aCameraArtResidency.readTile(initial, 0x3E5).copy());
        assertEquals(retainedHash, retained.hashCode());
    }

    @Test
    void nullInputsDoNotCreateAStateOrReturnInventedBytes() {
        var state = seed();
        assertThrows(NullPointerException.class, () -> CdR11aCameraArtResidency.devSeed(null, bytes(PLANT), bytes(RAMP)));
        assertThrows(NullPointerException.class, () -> CdR11aCameraArtResidency.devSeed(bytes(MOSQUI), null, bytes(RAMP)));
        assertThrows(NullPointerException.class, () -> CdR11aCameraArtResidency.devSeed(bytes(MOSQUI), bytes(PLANT), null));
        assertThrows(NullPointerException.class, () -> CdR11aCameraArtResidency.knownSeed(null, bytes(ANTON), bytes(new byte[2944])));
        assertThrows(NullPointerException.class, () -> CdR11aCameraArtResidency.knownSeed(bytes(MOSQUI), null, bytes(new byte[2944])));
        assertThrows(NullPointerException.class, () -> CdR11aCameraArtResidency.knownSeed(bytes(MOSQUI), bytes(ANTON), null));
        assertThrows(NullPointerException.class, () -> CdR11aCameraArtResidency.writeTile(null, 0x7400, bytes(new byte[32])));
        assertThrows(NullPointerException.class, () -> CdR11aCameraArtResidency.writeTile(state, 0x7400, null));
        assertThrows(NullPointerException.class, () -> CdR11aCameraArtResidency.readTile(null, 0x3A0));
        assertThrows(NullPointerException.class, () -> state.region(null));
    }

    private static CdR11aCameraArtResidency.State seed() {
        return CdR11aCameraArtResidency.devSeed(bytes(MOSQUI), bytes(PLANT), bytes(RAMP));
    }

    /** Test driver only: commits one tile at a time; no production batch shortcut. */
    private static CdR11aCameraArtResidency.State commit(CdR11aCameraArtResidency.State state,
                                                       int destination, byte[] source, int tiles) {
        for (int tile = 0; tile < tiles; tile++) {
            state = CdR11aCameraArtResidency.writeTile(state, destination + tile * 32,
                    bytes(Arrays.copyOfRange(source, tile * 32, tile * 32 + 32)));
        }
        return state;
    }

    private static void assertRegion(CdR11aCameraArtResidency.State state,
                                     CdR11aCameraArtResidency.Region region, byte[] expected, byte[] known) {
        var snapshot = state.region(region);
        assertEquals(region, snapshot.region());
        assertArrayEquals(known, snapshot.knownMask().copy());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(known[i] == 0 ? OptionalInt.empty() : OptionalInt.of(Byte.toUnsignedInt(expected[i])),
                    snapshot.knownByte(i), "region " + region + " byte " + i);
        }
    }

    private static void assertBoundary(CdR11aCameraArtResidency.State state, int id,
                                       CdR11aCameraArtResidency.Boundary boundary, int address) {
        var fault = assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,
                () -> CdR11aCameraArtResidency.readTile(state, id));
        assertEquals(boundary, fault.boundary());
        assertEquals(id, fault.nativePatternId());
        assertEquals(address, fault.destinationWord());
    }

    private static CdAssetBytes bytes(byte[] value) { return CdAssetBytes.copyOf(value); }

    private static byte[] allKnown(int length) {
        byte[] mask = new byte[length];
        Arrays.fill(mask, (byte) 1);
        return mask;
    }

    private static byte[] distinctBytes(int length, int salt) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) bytes[i] = (byte) (salt + i * 29 + (i >>> 5) * 17);
        return bytes;
    }
}

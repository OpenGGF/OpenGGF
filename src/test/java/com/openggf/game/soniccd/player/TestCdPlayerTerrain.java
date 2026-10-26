package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.level.CdR11aTerrain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Source-derived FindLevelFloor[/2] and FindLevelWall[/2] public behavior. */
class TestCdPlayerTerrain {
    @Test void blankFirstAndExtensionKeepTheirDifferentDefaultDistances() {
        var probes = new Fixture().probes();
        assertEquals(28, probes.floor(35, 35, 13, 0, 16, 0).distance());
        assertEquals(12, probes.floorExtension(35, 35, 13, 0, 16, 0).distance());
        assertEquals(28, probes.wall(35, 35, 14, 0, 16, 0).distance());
        assertEquals(12, probes.wallExtension(35, 35, 14, 0, 16, 0).distance());
        assertTrue(probes.floor(35, 35, 13, 0, 16, 0).angleWrite().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"1,0,14", "8,3,4", "15,15,-15", "127,15,-127"})
    void partialPositiveHeightsKeepWordDistances(int height, int offset, int expected) {
        Fixture f = new Fixture();
        f.block(2, 2, 0x2001);
        f.shape(1, 7, 0x23, height, 0);
        var probes = f.probes();
        var result = probes.floor(35, 32 + offset, 13, 0, 16, 0);
        assertEquals(expected, result.distance());
        assertEquals(0x23, result.angleWrite().orElseThrow());
        assertEquals(expected, probes.floorExtension(35, 32 + offset, 13, 0, 16, 0).distance());
    }

    @Test void fullFirstHeightChecksBehindButFullExtensionReturnsDirectly() {
        Fixture f = new Fixture();
        f.block(2, 2, 0x2001);
        f.shape(1, 7, 0x20, 16, 0);
        var probes = f.probes();
        assertEquals(-4, probes.floor(35, 35, 13, 0, 16, 0).distance());
        assertEquals(-4, probes.floorExtension(35, 35, 13, 0, 16, 0).distance());
        f.block(2, 1, 0x2002);
        f.shape(2, 8, 0x31, 8, 0);
        probes = f.probes();
        assertEquals(-12, probes.floor(35, 35, 13, 0, 16, 0).distance());
        assertEquals(0x31, probes.floor(35, 35, 13, 0, 16, 0).angleWrite().orElseThrow());
        assertEquals(-4, probes.floorExtension(35, 35, 13, 0, 16, 0).distance());
    }

    @Test void downwardNegativeHeightForcesFullAndWritesZeroAngle() {
        Fixture f = new Fixture();
        f.block(2, 2, 0x2001);
        f.shape(1, 7, 0x39, -8, 0);
        var probes = f.probes();
        for (var result : new CdPlayerTerrain.Result[]{
                probes.floor(35, 35, 13, 0, 16, 0),
                probes.floorExtension(35, 35, 13, 0, 16, 0)}) {
            assertEquals(-4, result.distance());
            assertEquals(0, result.angleWrite().orElseThrow());
        }
    }

    @Test void negativeExtensionUsesComplementedCoordinateRatherThanPositiveHeightFormula() {
        Fixture f = new Fixture();
        f.block(2, 2, 0x2001);
        f.shape(1, 7, 0x39, -8, 0);
        var probes = f.probes();
        assertEquals(-4, probes.floorExtension(35, 35, 13, 0, -16, 0).distance());
        // At offset 8, -8+8 is nonnegative: the blank default retains the angle write.
        var outside = probes.floorExtension(35, 40, 13, 0, -16, 0);
        assertEquals(7, outside.distance());
        assertEquals(0x39, outside.angleWrite().orElseThrow());
        // The first probe instead checks behind and subtracts 16.
        assertEquals(-4, probes.floor(35, 35, 13, 0, -16, 0).distance());
    }

    @Test void extensionAdjustmentDoesNotNarrowDistanceToByte() {
        Fixture f = new Fixture();
        f.block(2, 2, 0x2001);
        f.shape(1, 7, 0x20, 16, 0);
        f.block(2, 1, 0x2002);
        f.shape(2, 8, 0x31, 127, 0);
        assertEquals(-143, f.probes().floor(35, 47, 13, 0, 16, 0).distance());
    }

    @Test void fullHeightIsNegatedBeforeTheNegativeFloorSpecialCase() {
        Fixture f = new Fixture();
        f.block(2, 2, 0x3001);
        f.shape(1, 7, 0x23, 16, 0);
        var result = f.probes().floor(35, 35, 13, 0, 16, 0);
        assertEquals(-4, result.distance());
        assertEquals(0, result.angleWrite().orElseThrow());
    }

    @Test void zeroMetricRetainsItsAngleUnlessTheExtensionWritesAnother() {
        Fixture f = new Fixture();
        f.block(2, 2, 0x2001);
        f.shape(1, 7, 0x27, 0, 0);
        var probes = f.probes();
        var emptyExtension = probes.floor(35, 35, 13, 0, 16, 0);
        assertEquals(28, emptyExtension.distance());
        assertEquals(0x27, emptyExtension.angleWrite().orElseThrow());
        f.block(2, 3, 0x2002);
        f.shape(2, 8, 0x32, 8, 0);
        var solidExtension = f.probes().floor(35, 35, 13, 0, 16, 0);
        assertEquals(20, solidExtension.distance());
        assertEquals(0x32, solidExtension.angleWrite().orElseThrow());
    }

    @Test void missingSolidityAndShapeZeroDoNotWriteAngle() {
        Fixture f = new Fixture();
        f.block(2, 2, 0x2001);
        f.shape(1, 7, 0x23, 8, 8);
        var probes = f.probes();
        assertTrue(probes.floor(35, 35, 14, 0, 16, 0).angleWrite().isEmpty());
        f.ids[1] = 0;
        assertTrue(f.probes().floor(35, 35, 13, 0, 16, 0).angleWrite().isEmpty());
    }

    @Test void nativeBlockFlipsChangeSampleSelectionAndAngleWithByteArithmetic() {
        Fixture f = new Fixture();
        f.block(2, 2, 0x2801); // X flipped, native solidity 13.
        f.shape(1, 7, 0x23, 0, 0);
        f.heights[7 * 16 + 12] = 8; // ~3 & 15.
        var x = f.probes().floor(35, 35, 13, 0, 16, 0);
        assertEquals(4, x.distance());
        assertEquals(0xDD, x.angleWrite().orElseThrow());

        f.block(2, 2, 0x3001); // Y flipped.
        f.heights[7 * 16 + 3] = -8;
        var y = f.probes().floor(35, 35, 13, 0, 16, 0);
        assertEquals(4, y.distance());
        assertEquals(0x5D, y.angleWrite().orElseThrow());

        // Caller flip mask changes metric sign, not the original block's angle transform.
        var mirrored = f.probes().floor(35, 35, 13, 0x1000, 16, 0);
        assertEquals(-4, mirrored.distance());
        assertEquals(0, mirrored.angleWrite().orElseThrow());
    }

    @Test void wallUsesWidthAndYColumnAndHasNoNegativeFloorForcedFullCase() {
        Fixture f = new Fixture();
        f.block(2, 2, 0x4001);
        f.shape(1, 7, 0x29, 1, -8);
        var probes = f.probes();
        var first = probes.wall(35, 35, 14, 0, 16, 0);
        assertEquals(-4, first.distance());
        assertEquals(0x29, first.angleWrite().orElseThrow());
        var ext = probes.wallExtension(35, 35, 14, 0, 16, 0);
        assertEquals(-4, ext.distance());
        assertEquals(0x29, ext.angleWrite().orElseThrow());
        assertEquals(23, probes.wall(40, 35, 14, 0, 16, 0).distance());
    }

    @Test void wallFlipsUseNativeAngleAndMetricAxes() {
        Fixture f = new Fixture();
        f.block(2, 2, 0x5801); // both flips and side solidity.
        f.shape(1, 7, 0x23, 0, 0);
        f.widths[7 * 16 + 12] = -8;
        var result = f.probes().wall(35, 35, 14, 0, 16, 0);
        assertEquals(4, result.distance());
        assertEquals(0xA3, result.angleWrite().orElseThrow());
        // Cancelling only X metric inversion follows the negative-width branch;
        // its original transformed angle is preserved.
        var mirrored = f.probes().wallExtension(35, 35, 14, 0x800, 16, 0);
        assertEquals(-4, mirrored.distance());
        assertEquals(0xA3, mirrored.angleWrite().orElseThrow());
    }

    @Test void fullFirstWidthChecksBehindWhileExtensionDoesNot() {
        Fixture f = new Fixture();
        f.block(2, 2, 0x4001);
        f.shape(1, 7, 0x20, 0, 16);
        var probes = f.probes();
        assertEquals(-4, probes.wall(35, 35, 14, 0, 16, 0).distance());
        assertEquals(0x20, probes.wall(35, 35, 14, 0, 16, 0).angleWrite().orElseThrow());
        f.block(1, 2, 0x4002);
        f.shape(2, 8, 0x31, 0, 8);
        probes = f.probes();
        assertEquals(-12, probes.wall(35, 35, 14, 0, 16, 0).distance());
        assertEquals(0x31, probes.wall(35, 35, 14, 0, 16, 0).angleWrite().orElseThrow());
        assertEquals(-4, probes.wallExtension(35, 35, 14, 0, 16, 0).distance());
    }

    @Test void coordinatesAndContinuationStepsAreNativeWords() {
        Fixture f = new Fixture();
        f.block(2, 2, 0x2001);
        f.shape(1, 7, 0x23, 8, 8);
        var probes = f.probes();
        assertEquals(probes.floor(35, 35, 13, 0, 16, 0),
                probes.floor(0x10023, 0x10023, 13, 0x10000, 0x10010, 0));
        assertEquals(probes.floor(-1, -1, 13, 0, 16, 0),
                probes.floor(0xFFFF, 0xFFFF, 13, 0, 16, 0));
        assertThrows(IllegalArgumentException.class, () -> probes.floor(35, 35, -1, 0, 16, 0));
        assertThrows(IllegalArgumentException.class, () -> probes.wall(35, 35, 16, 0, 16, 0));
    }

    @Test void unknownResidentCollisionBytesFailOnlyAfterNativeSoliditySelection() {
        Fixture f = new Fixture();
        f.fg[2] = (byte) 0xA8; // PP: flagged 40, actor bit6 remaps to 81.
        int at = 80 * 512 + (2 * 16 + 2) * 2;
        f.chunks[at] = 0x24; // Solid bit13 plus collision material1024, drawing0.
        var probes = f.probes();
        var failure = assertThrows(IllegalStateException.class,
                () -> probes.floor(35, 35, 13, 0, 16, 0x40));
        assertTrue(failure.getMessage().contains("unavailable resident bytes: 1024"));
        // Native tests solidity before reading the unknown collision-ID entry.
        var notSolid = probes.floor(35, 35, 14, 0, 16, 0x40);
        assertEquals(28, notSolid.distance());
        assertTrue(notSolid.angleWrite().isEmpty());
    }

    @Test void unavailableOrOddChunkBackingNeverBecomesBlankTerrain() {
        Fixture f = new Fixture();
        f.fg[2] = (byte) 0xF9; // Actor flag selects unavailable chunk122.
        var outside = f.probes();
        assertTrue(assertThrows(IllegalStateException.class,
                () -> outside.floor(35, 35, 13, 0, 16, 0x40))
                .getMessage().contains("OUTSIDE_SUPPLIED_CHUNKS"));
        f.fg[2] = (byte) 0x80;
        var odd = f.probes();
        assertTrue(assertThrows(IllegalStateException.class,
                () -> odd.wall(35, 35, 14, 0, 16, 0))
                .getMessage().contains("ODD_NATIVE_WORD_ADDRESS"));
    }

    @Test void zeroNativeMaterialSkipsEvenANonzeroCollisionMapEntryAtZero() {
        Fixture f = new Fixture();
        f.block(2, 2, 0x2000);
        f.shape(0, 7, 0x39, 8, 8);
        var result = f.probes().floor(35, 35, 13, 0, 16, 0);
        assertEquals(28, result.distance());
        assertTrue(result.angleWrite().isEmpty());
    }

    @Test void observerSeesNoFabricatedAngleOnBlankFloor() {
        var probes=new Fixture().probes();var writes=new java.util.ArrayList<Integer>();
        var result=probes.floor(35,35,13,0,16,0,writes::add);
        assertEquals(28,result.distance());assertTrue(result.angleWrite().isEmpty());assertTrue(writes.isEmpty());
    }

    @Test void observerPinsEveryNativeFlipStoreInOrder() {
        int[] words={0x2001,0x2801,0x3001,0x3801};int[] mirror={0,0,0x1000,0x1000};
        var expected=java.util.List.of(java.util.List.of(0x22),java.util.List.of(0x22,0xDE),
                java.util.List.of(0x22,0x62,0x9E,0x5E),java.util.List.of(0x22,0xDE,0x1E,0xE2,0xA2));
        int[] finalAngles={0x22,0xDE,0x5E,0xA2};
        for(int i=0;i<4;i++) {
            var f=new Fixture();f.block(2,2,words[i]);f.shape(1,7,0x22,8,0);var writes=new java.util.ArrayList<Integer>();
            var result=f.probes().floor(35,35,13,mirror[i],16,0,writes::add);
            assertEquals(4,result.distance());assertEquals(finalAngles[i],result.angleWrite().orElseThrow());assertEquals(expected.get(i),writes);
        }
    }

    @Test void observerIncludesForcedZeroInBothFloorEntries() {
        for(boolean adjacent:java.util.List.of(false,true)) {
            var f=new Fixture();f.block(2,adjacent?3:2,0x3001);f.shape(1,7,0x22,8,0);
            var writes=new java.util.ArrayList<Integer>();var result=f.probes().floor(35,35,13,0,16,0,writes::add);
            assertEquals(adjacent?12:-4,result.distance());assertEquals(0,result.angleWrite().orElseThrow());
            assertEquals(java.util.List.of(0x22,0x62,0x9E,0x5E,0),writes);
        }
    }

    @Test void observerPublishesEachAdjacentShapeAngleBeforeItsReturn() {
        for(boolean above:java.util.List.of(false,true)) {
            var f=new Fixture();f.block(2,2,0x2001);f.shape(1,7,0x22,above?16:0,0);
            f.block(2,above?1:3,0x2002);f.shape(2,8,0x44,8,0);var writes=new java.util.ArrayList<Integer>();
            var result=f.probes().floor(35,35,13,0,16,0,writes::add);
            assertEquals(above?-12:20,result.distance());assertEquals(0x44,result.angleWrite().orElseThrow());
            assertEquals(java.util.List.of(0x22,0x44),writes);
        }
    }

    @Test void observerKeepsFirstAngleWhenAdjacentMaterialIsUnavailable() {
        var f=new Fixture();f.fg[2]=(byte)0xA8;f.shape(1,7,0x22,0,0);
        // Declared foreground references blank40; the real object path bit6 reaches resident81.
        int first=80*512+(2*16+2)*2;f.chunks[first]=0x20;f.chunks[first+1]=1;
        int next=80*512+(3*16+2)*2;f.chunks[next]=0x24;f.chunks[next+1]=0;
        var writes=new java.util.ArrayList<Integer>();var probes=f.probes();
        var failure=assertThrows(IllegalStateException.class,()->probes.floor(35,35,13,0,16,0x40,writes::add));
        assertTrue(failure.getMessage().contains("unavailable resident bytes: 1024"));assertEquals(java.util.List.of(0x22),writes);
    }

    @Test void observerKeepsFirstAngleWhenAdjacentChunkIsUnavailable() {
        var f=new Fixture();f.block(2,15,0x2001);f.shape(1,7,0x22,0,0);f.fg[2+42]=(byte)0xF9;
        var writes=new java.util.ArrayList<Integer>();var probes=f.probes();
        var failure=assertThrows(IllegalStateException.class,()->probes.floor(35,255,13,0,16,0x40,writes::add));
        assertTrue(failure.getMessage().contains("OUTSIDE_SUPPLIED_CHUNKS"));assertEquals(java.util.List.of(0x22),writes);
    }

    @Test void observerExceptionStopsAtTheReachedRmwStoreAndPropagatesUnchanged() {
        var f=new Fixture();f.block(2,2,0x3801);f.shape(1,7,0x22,8,0);var probes=f.probes();
        var writes=new java.util.ArrayList<Integer>();var sentinel=new IllegalStateException("reached Y-flip ADD40 publication");
        var failure=assertThrows(IllegalStateException.class,()->probes.floor(35,35,13,0x1000,16,0,value->{
            writes.add(value);if(writes.size()==3)throw sentinel;
        }));
        assertSame(sentinel,failure);assertEquals(java.util.List.of(0x22,0xDE,0x1E),writes);
        var pure=probes.floor(35,35,13,0x1000,16,0);assertEquals(4,pure.distance());assertEquals(0xA2,pure.angleWrite().orElseThrow());
    }

    @Test void observerIsNotRetainedAndThePureEntryRemainsCompatible() {
        var f=new Fixture();f.block(2,2,0x2801);f.shape(1,7,0,8,0);var probes=f.probes();
        var first=new java.util.ArrayList<Integer>();var observed=probes.floor(35,35,13,0,16,0,first::add);
        assertEquals(java.util.List.of(0,0),first);assertEquals(4,observed.distance());
        assertEquals(observed,probes.floor(35,35,13,0,16,0));assertEquals(java.util.List.of(0,0),first);
        var second=new java.util.ArrayList<Integer>();assertEquals(observed,probes.floor(35,35,13,0,16,0,second::add));
        assertEquals(java.util.List.of(0,0),first);assertEquals(java.util.List.of(0,0),second);
    }

    @Test void observerNullAndInvalidBitRejectBeforeAnyWriter() {
        var probes=new Fixture().probes();var writes=new java.util.ArrayList<Integer>();
        assertThrows(NullPointerException.class,()->probes.floor(35,35,13,0,16,0,null));
        assertThrows(IllegalArgumentException.class,()->probes.floor(35,35,-1,0,16,0,writes::add));
        assertThrows(IllegalArgumentException.class,()->probes.floor(35,35,16,0,16,0,writes::add));assertTrue(writes.isEmpty());
    }

    private static final class Fixture {
        final byte[] chunks = new byte[61952];
        final byte[] fg = new byte[338];
        final byte[] bg = new byte[50];
        final byte[] ids = new byte[900];
        final byte[] angles = new byte[256];
        final byte[] heights = new byte[4096];
        final byte[] widths = new byte[4096];

        Fixture() {
            fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7;
            fg[2] = 2; // chunk 2 avoids the computed LevelChunks base-pointer sentinel.
        }
        void block(int x, int y, int word) {
            int at = 512 + (y * 16 + x) * 2;
            chunks[at] = (byte) (word >>> 8);
            chunks[at + 1] = (byte) word;
        }
        void shape(int block, int id, int angle, int height, int width) {
            ids[block] = (byte) id; angles[id] = (byte) angle;
            Arrays.fill(heights, id * 16, id * 16 + 16, (byte) height);
            Arrays.fill(widths, id * 16, id * 16 + 16, (byte) width);
        }
        CdPlayerTerrain probes() {
            var terrain = CdR11aTerrain.decode(new byte[6432], new byte[25600], chunks, fg, bg,
                    ids, angles, heights, widths);
            return new CdPlayerTerrain(terrain);
        }
    }

    // Collapse probe prefix contract. CONSTRUCTED public-format inputs; ALL JAVA UNRUN.
    @Test void prefixFloorCompleteResultAndEveryRawStoreMatchTheActualLegacyFloorSeven() {
        int[] words = {0x2001, 0x2801, 0x3001, 0x3801};
        int[] mirrors = {0, 0, 0x1000, 0x1000};
        var expected = java.util.List.of(java.util.List.of(0x22), java.util.List.of(0x22, 0xDE),
                java.util.List.of(0x22, 0x62, 0x9E, 0x5E), java.util.List.of(0x22, 0xDE, 0x1E, 0xE2, 0xA2));
        for (int i = 0; i < words.length; i++) {
            var f = new Fixture(); f.block(2, 2, words[i]); f.shape(1, 7, 0x22, 8, 0);
            var before = prefixImage(f); var owner = f.probes();
            var legacyWrites = new java.util.ArrayList<Integer>(); var raw = new java.util.ArrayList<Integer>();
            var legacy = owner.floor(35, 35, 13, mirrors[i], 16, 0, legacyWrites::add);
            assertEquals(legacy, owner.floorWithPrefix(35, 35, 13, mirrors[i], 16, 0, raw::add));
            assertEquals(owner.floor(35, 35, 13, mirrors[i], 16, 0), legacy);
            assertEquals(expected.get(i), raw); assertEquals(legacyWrites, raw); assertEquals(4, legacy.distance());
            assertPrefixImage(before, f);
        }
    }

    @Test void prefixWallCompleteResultAndFlipStoresRetainTheirNativeAxisOrder() {
        var f = new Fixture(); f.block(2, 2, 0x5801); f.shape(1, 7, 0x23, 0, 0);
        f.widths[7 * 16 + 12] = -8; var owner = f.probes(); var raw = new java.util.ArrayList<Integer>();
        var result = owner.wallWithPrefix(35, 35, 14, 0, 16, 0, raw::add);
        assertEquals(owner.wall(35, 35, 14, 0, 16, 0), result); assertEquals(4, result.distance());
        assertEquals(java.util.List.of(0x23, 0x63, 0x9D, 0x5D, 0xA3), raw);
        assertEquals(java.util.OptionalInt.of(0xA3), result.angleWrite());
    }

    @Test void prefixForcedZeroAndSameValueStoresAreNotCoalesced() {
        var f = new Fixture(); f.block(2, 2, 0x3001); f.shape(1, 7, 0x22, 8, 0);
        var raw = new java.util.ArrayList<Integer>(); var owner = f.probes();
        assertEquals(owner.floor(35, 35, 13, 0, 16, 0),
                owner.floorWithPrefix(35, 35, 13, 0, 16, 0, raw::add));
        assertEquals(java.util.List.of(0x22, 0x62, 0x9E, 0x5E, 0), raw);
        f.block(2, 2, 0x2801); f.shape(1, 7, 0, 8, 0); raw.clear();
        assertEquals(java.util.OptionalInt.of(0), f.probes().floorWithPrefix(35, 35, 13, 0, 16, 0, raw::add).angleWrite());
        assertEquals(java.util.List.of(0, 0), raw);
    }

    @Test void prefixBlankAndNonSolidReadsNeverInventAnAngleStoreOrRetainAWriter() {
        var f = new Fixture(); var owner = f.probes(); var raw = new java.util.ArrayList<Integer>();
        assertEquals(owner.floor(35, 35, 13, 0, 16, 0), owner.floorWithPrefix(35, 35, 13, 0, 16, 0, raw::add));
        assertEquals(owner.wall(35, 35, 14, 0, 16, 0), owner.wallWithPrefix(35, 35, 14, 0, 16, 0, raw::add));
        assertTrue(raw.isEmpty());
        f.block(2, 2, 0x2001); f.shape(1, 7, 3, 8, 0); owner = f.probes();
        var second = new java.util.ArrayList<Integer>();
        assertEquals(owner.floor(35, 35, 13, 0, 16, 0), owner.floorWithPrefix(35, 35, 13, 0, 16, 0, second::add));
        assertEquals(java.util.List.of(3), second); assertTrue(raw.isEmpty());
        assertTrue(owner.wallWithPrefix(35, 35, 14, 0, 16, 0, raw::add).angleWrite().isEmpty());
        assertTrue(raw.isEmpty());
    }

    @Test void prefixFirstUnavailableWordHasTypedNativeAddressAndTheUntouchedOriginalException() {
        var f = new Fixture(); f.fg[2] = (byte) 0xF9; var before = prefixImage(f); var owner = f.probes();
        var raw = new java.util.ArrayList<Integer>();
        var failure = assertThrows(CdPlayerTerrain.UnownedTerrainRead.class,
                () -> owner.floorWithPrefix(35, 35, 13, 0, 16, 0x40, raw::add));
        assertEquals(new CdPlayerTerrain.TerrainWord(
                new com.openggf.game.soniccd.level.CdTerrainCollision.Address(0, 0xF9, 122, 0xF244),
                com.openggf.game.soniccd.level.CdTerrainCollision.UnavailableReason.OUTSIDE_SUPPLIED_CHUNKS), failure.detail());
        assertPrefixOriginal(failure, "Unavailable native collision word at $21f244: OUTSIDE_SUPPLIED_CHUNKS");
        assertTrue(raw.isEmpty()); assertPrefixImage(before, f);
    }

    @Test void prefixOddWordRemainsAnUnavailableNativeReadRatherThanAHostArrayFailure() {
        var f = new Fixture(); f.fg[2] = (byte) 0x80; var owner = f.probes(); var raw = new java.util.ArrayList<Integer>();
        var failure = assertThrows(CdPlayerTerrain.UnownedTerrainRead.class,
                () -> owner.wallWithPrefix(35, 35, 14, 0, 16, 0, raw::add));
        assertEquals(new CdPlayerTerrain.TerrainWord(
                new com.openggf.game.soniccd.level.CdTerrainCollision.Address(0, 0x80, 0, 0xFE45),
                com.openggf.game.soniccd.level.CdTerrainCollision.UnavailableReason.ODD_NATIVE_WORD_ADDRESS), failure.detail());
        assertPrefixOriginal(failure, "Unavailable native collision word at $21fe45: ODD_NATIVE_WORD_ADDRESS");
        assertTrue(raw.isEmpty());
    }

    @Test void prefixFloorRawOddByteSurvivesItsOwnAdjacentWordFailure() {
        var f = new Fixture(); f.block(2, 15, 0x2001); f.shape(1, 7, 3, 0, 0); f.fg[2 + 42] = (byte) 0xF9;
        var owner = f.probes(); var raw = new java.util.ArrayList<Integer>(); var before = prefixImage(f);
        var failure = assertThrows(CdPlayerTerrain.UnownedTerrainRead.class,
                () -> owner.floorWithPrefix(35, 255, 13, 0, 16, 0x40, raw::add));
        assertEquals(java.util.List.of(3), raw);
        assertEquals(new CdPlayerTerrain.TerrainWord(
                new com.openggf.game.soniccd.level.CdTerrainCollision.Address(0x80, 0xF9, 122, 0xF204),
                com.openggf.game.soniccd.level.CdTerrainCollision.UnavailableReason.OUTSIDE_SUPPLIED_CHUNKS), failure.detail());
        assertPrefixOriginal(failure, "Unavailable native collision word at $21f204: OUTSIDE_SUPPLIED_CHUNKS");
        assertPrefixImage(before, f);
    }

    @Test void prefixWallRawOddByteSurvivesItsOwnAdjacentWordFailure() {
        var f = new Fixture(); f.block(15, 6, 0x4001); f.shape(1, 7, 3, 0, 0); f.fg[3] = (byte) 0xF9;
        var owner = f.probes(); var raw = new java.util.ArrayList<Integer>();
        var failure = assertThrows(CdPlayerTerrain.UnownedTerrainRead.class,
                () -> owner.wallWithPrefix(255, 105, 14, 0, 16, 0x40, raw::add));
        assertEquals(java.util.List.of(3), raw);
        assertEquals(new CdPlayerTerrain.TerrainWord(
                new com.openggf.game.soniccd.level.CdTerrainCollision.Address(1, 0xF9, 122, 0xF2C0),
                com.openggf.game.soniccd.level.CdTerrainCollision.UnavailableReason.OUTSIDE_SUPPLIED_CHUNKS), failure.detail());
        assertPrefixOriginal(failure, "Unavailable native collision word at $21f2c0: OUTSIDE_SUPPLIED_CHUNKS");
    }

    @Test void prefixCollisionIdFailureUsesOnlyThePubliclyAdmittedAlternateChunk() {
        var f = new Fixture(); f.fg[2] = (byte) 0x82;
        int at = 1024 + (2 * 16 + 2) * 2; f.chunks[at] = 0x63; f.chunks[at + 1] = (byte) 0x84;
        var before = prefixImage(f); var owner = f.probes(); var raw = new java.util.ArrayList<Integer>();
        for (boolean floor : java.util.List.of(true, false)) {
            var failure = assertThrows(CdPlayerTerrain.UnownedTerrainRead.class, () -> {
                if (floor) owner.floorWithPrefix(35, 35, 13, 0, 16, 0x40, raw::add);
                else owner.wallWithPrefix(35, 35, 14, 0, 16, 0x40, raw::add);
            });
            assertEquals(new CdPlayerTerrain.CollisionId(900), failure.detail());
            assertPrefixOriginal(failure, "Collision ID requires unavailable resident bytes: 900");
        }
        assertTrue(raw.isEmpty()); assertPrefixImage(before, f);
    }

    @Test void everyLegacyTerrainEntryStillThrowsThePlainOriginalClassAndExactMessage() {
        var f = new Fixture(); f.fg[2] = (byte) 0xF9; var owner = f.probes();
        var entries = java.util.List.<org.junit.jupiter.api.function.Executable>of(
                () -> owner.floor(35, 35, 13, 0, 16, 0x40),
                () -> owner.floor(35, 35, 13, 0, 16, 0x40, value -> fail("no reached angle")),
                () -> owner.floorExtension(35, 35, 13, 0, 16, 0x40),
                () -> owner.wall(35, 35, 14, 0, 16, 0x40),
                () -> owner.wallExtension(35, 35, 14, 0, 16, 0x40));
        for (var entry : entries) {
            var failure = assertThrows(IllegalStateException.class, entry);
            assertEquals(IllegalStateException.class, failure.getClass());
            assertEquals("Unavailable native collision word at $21f244: OUTSIDE_SUPPLIED_CHUNKS", failure.getMessage());
            assertNull(failure.getCause());
        }
        f.fg[2] = (byte) 0x82; int at = 1024 + (2 * 16 + 2) * 2;
        f.chunks[at] = 0x63; f.chunks[at + 1] = (byte) 0x84; var alternate = f.probes();
        assertEquals("Collision ID requires unavailable resident bytes: 900", assertThrows(IllegalStateException.class,
                () -> alternate.floor(35, 35, 13, 0, 16, 0x40)).getMessage());
        assertEquals(IllegalStateException.class, assertThrows(IllegalStateException.class,
                () -> alternate.wall(35, 35, 14, 0, 16, 0x40)).getClass());
    }

    @Test void prefixCallbacksRemainArbitraryHostExceptionsWithTheirExactIdentity() {
        var f = new Fixture(); f.block(2, 2, 0x3801); f.shape(1, 7, 0x22, 8, 8); var owner = f.probes();
        for (var sentinel : new RuntimeException[]{new RuntimeException("host"), new IllegalStateException("host state"),
                new IllegalArgumentException("host argument")}) {
            var raw = new java.util.ArrayList<Integer>();
            assertSame(sentinel, assertThrows(RuntimeException.class,
                    () -> owner.floorWithPrefix(35, 35, 13, 0x1000, 16, 0, value -> {
                        raw.add(value); if (raw.size() == 3) throw sentinel;
                    })));
            assertEquals(java.util.List.of(0x22, 0xDE, 0x1E), raw);
        }
        f.block(2, 2, 0x5801); var wall = f.probes(); var sentinel = new IllegalStateException("wall callback");
        assertSame(sentinel, assertThrows(IllegalStateException.class,
                () -> wall.wallWithPrefix(35, 35, 14, 0, 16, 0, value -> { throw sentinel; })));
    }

    @Test void prefixValidationAndResultInvariantsNeverBecomeNativeOwnershipMarkers() {
        var owner = new Fixture().probes(); var raw = new java.util.ArrayList<Integer>();
        assertEquals(NullPointerException.class, assertThrows(NullPointerException.class,
                () -> owner.floorWithPrefix(35, 35, 13, 0, 16, 0, null)).getClass());
        assertEquals(NullPointerException.class, assertThrows(NullPointerException.class,
                () -> owner.wallWithPrefix(35, 35, 14, 0, 16, 0, null)).getClass());
        for (int bit : new int[]{-1, 16}) {
            assertEquals("Solidity bit must select the original native word", assertThrows(IllegalArgumentException.class,
                    () -> owner.floorWithPrefix(35, 35, bit, 0, 16, 0, raw::add)).getMessage());
            assertEquals(IllegalArgumentException.class, assertThrows(IllegalArgumentException.class,
                    () -> owner.wallWithPrefix(35, 35, bit, 0, 16, 0, raw::add)).getClass());
        }
        assertTrue(raw.isEmpty());
        assertEquals(IllegalArgumentException.class, assertThrows(IllegalArgumentException.class,
                () -> new CdPlayerTerrain.Result(32768, java.util.OptionalInt.empty())).getClass());
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerTerrain.Result(0, java.util.OptionalInt.of(256)));
        assertThrows(NullPointerException.class, () -> new CdPlayerTerrain.Result(0, null));
        assertTrue(Arrays.stream(CdPlayerTerrain.UnownedTerrainRead.class.getDeclaredConstructors())
                .allMatch(c -> java.lang.reflect.Modifier.isPrivate(c.getModifiers())));
    }

    @Test void prefixCoordinatesMirrorsFlagsAndStepsKeepExactlyTheirLegacyNativeWidths() {
        var f = new Fixture(); f.block(2, 2, 0x2801); f.shape(1, 7, 0x23, 8, 8); var owner = f.probes();
        var raw = new java.util.ArrayList<Integer>();
        assertEquals(owner.floor(35, 35, 13, 0, 16, 0),
                owner.floorWithPrefix(0x10023, 0x10023, 13, 0x10000, 0x10010, 0x100, raw::add));
        assertEquals(java.util.List.of(0x23, 0xDD), raw); raw.clear();
        assertEquals(owner.wall(35, 35, 14, 0x800, -16, 0),
                owner.wallWithPrefix(0x10023, 0x10023, 14, 0x10800, 0xFFF0, 0x100, raw::add));
        assertTrue(raw.isEmpty());
    }

    private static void assertPrefixOriginal(CdPlayerTerrain.UnownedTerrainRead failure, String message) {
        var original = failure.originalException();
        assertEquals(IllegalStateException.class, original.getClass()); assertEquals(message, original.getMessage());
        assertNull(original.getCause()); assertSame(original, failure.originalException());
        assertSame(original, failure.getCause()); assertEquals(message, failure.getMessage());
        assertSame(original, assertThrows(IllegalStateException.class, () -> { throw failure.originalException(); }));
    }
    private static java.util.List<byte[]> prefixImage(Fixture f) {
        return java.util.List.of(f.chunks.clone(), f.fg.clone(), f.bg.clone(), f.ids.clone(), f.angles.clone(),
                f.heights.clone(), f.widths.clone());
    }
    private static void assertPrefixImage(java.util.List<byte[]> before, Fixture f) {
        var after = java.util.List.of(f.chunks, f.fg, f.bg, f.ids, f.angles, f.heights, f.widths);
        for (int i = 0; i < before.size(); i++) assertArrayEquals(before.get(i), after.get(i), "terrain array " + i);
    }
}

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
}

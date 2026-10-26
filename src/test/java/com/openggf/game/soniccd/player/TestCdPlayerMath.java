package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.level.CdR11aPlayerAssets;
import com.openggf.game.soniccd.level.CdSonicVisualAssets;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Literal scalar cases for CalcSine/CalcAngle with constructed, admitted asset bytes.
 * Authored before CdPlayerMath during the parent-owned validation freeze.
 * These Jupiter tests have NOT been compiled or executed; no media/native parity is asserted.
 */
class TestCdPlayerMath {
    @Test
    void sineAndCosineReadSignedWordsAtEveryCardinalAndDiagonal() {
        CdPlayerMath math = new Fixture().owner();
        // Native landmarks, not expectations computed by a trig implementation.
        int[][] cases = {
                {0x00, 0, 256}, {0x20, 181, 181},
                {0x40, 256, 0}, {0x60, 181, -181},
                {0x80, 0, -256}, {0xA0, -181, -181},
                {0xC0, -256, 0}, {0xE0, -181, 181}
        };
        for (int[] c : cases) {
            assertEquals(new CdPlayerMath.SineCosine(c[1], c[2]), math.calcSine(c[0]),
                    "angle " + c[0]);
        }
    }

    @Test
    void sineMasksToTheLowByteBeforeBothReadsIncludingTheCosineExtensionEnd() {
        CdPlayerMath math = new Fixture().owner();
        int[][] cases = {
                {-1, -6, 255}, {-255, 6, 255}, {-256, 0, 256},
                {0x100, 0, 256}, {0x1FF, -6, 255},
                {0x12345620, 181, 181}, {0x123456C0, -256, 0},
                {Integer.MIN_VALUE, 0, 256}, {Integer.MAX_VALUE, -6, 255}
        };
        for (int[] c : cases) {
            assertEquals(new CdPlayerMath.SineCosine(c[1], c[2]), math.calcSine(c[0]),
                    "register contents " + c[0]);
        }
    }

    @Test
    void negativeSineAsymmetriesAreReadRatherThanRebuiltFromPositiveQuadrants() {
        CdPlayerMath math = new Fixture().owner();
        // SineTable's $FF8B is -117, whereas the opposite positive word is 115.
        assertEquals(115, math.calcSine(19).sine());
        assertEquals(-117, math.calcSine(147).sine());
        assertEquals(115, math.calcSine(109).sine());
        assertEquals(-117, math.calcSine(237).sine());
        assertEquals(new CdPlayerMath.SineCosine(228, -117), math.calcSine(83));
        assertEquals(new CdPlayerMath.SineCosine(-228, -117), math.calcSine(173));
    }

    @Test
    void sineAndCosineDependOnTheSuppliedNoncanonicalWordsWithoutSharedFallbacks() {
        CdPlayerMath original = new Fixture().owner();
        Fixture changed = new Fixture();
        changed.sineWord(1, 37);
        changed.sineWord(65, -123);
        changed.sineWord(63, -73); // Also changes admitted extension word 319.
        changed.sineWord(255, 201);
        CdPlayerMath altered = changed.owner();

        CdPlayerMath.SineCosine captured = original.calcSine(1);
        assertEquals(new CdPlayerMath.SineCosine(6, 255), captured);
        assertEquals(new CdPlayerMath.SineCosine(37, -123), altered.calcSine(1));
        assertEquals(new CdPlayerMath.SineCosine(201, -73), altered.calcSine(255));
        assertEquals(new CdPlayerMath.SineCosine(6, 255), original.calcSine(1));
        assertEquals(new CdPlayerMath.SineCosine(6, 255), captured);
        // Admitted zero words remain zero; they are not holes for libm/shared data to fill.
        assertEquals(new CdPlayerMath.SineCosine(0, 0), altered.calcSine(2));
    }

    @Test
    void zeroVectorAndBothDirectionsOfEachAxisRetainNativeAngles() {
        CdPlayerMath math = new Fixture().owner();
        int[][] cases = {
                {0, 0, 0x40}, {1, 0, 0}, {32767, 0, 0},
                {-1, 0, 0x80}, {-32768, 0, 0x80},
                {0, 1, 0x40}, {0, 32767, 0x40},
                {0, -1, 0xC0}, {0, -32768, 0xC0}
        };
        for (int[] c : cases) { assertAngle(math, c); }
    }

    @Test
    void everyOctantUsesItsOwnUnsignedRatioBranchAndSignedQuadrant() {
        CdPlayerMath math = new Fixture().owner();
        // The constructed table stores $10 at quotient 128, deliberately not stock $12.
        int[][] cases = {
                {2, 1, 0x10}, {1, 2, 0x30}, {-1, 2, 0x50}, {-2, 1, 0x70},
                {-2, -1, 0x90}, {-1, -2, 0xB0}, {1, -2, 0xD0}, {2, -1, 0xF0}
        };
        for (int[] c : cases) { assertAngle(math, c); }
    }

    @Test
    void equalityReadsTheActual257thByteRatherThanClampingOrMaskingTheQuotient() {
        CdPlayerMath math = new Fixture().owner();
        // Fixture bytes 255=$1F and 256=$20 make clamping observable.
        int[][] cases = {
                {1, 1, 0x20}, {-1, 1, 0x60}, {-1, -1, 0xA0}, {1, -1, 0xE0},
                {32767, 32767, 0x20}, {-32768, -32768, 0xA0},
                {256, 255, 0x1F}, {255, 256, 0x21},
                {-256, 255, 0x61}, {-255, 256, 0x5F},
                {-256, -255, 0x9F}, {-255, -256, 0xA1},
                {256, -255, 0xE1}, {255, -256, 0xDF}
        };
        for (int[] c : cases) { assertAngle(math, c); }
    }

    @Test
    void divisionTruncatesBeforeTableThresholdsOnBothSidesOfTheDiagonal() {
        CdPlayerMath math = new Fixture().owner();
        // In this table quotient 7 -> 0, 8 -> 1, 15 -> 1, 16 -> 2.
        // 63*256/2048 is 7.875: rounding it would cross the first threshold.
        int[][] cases = {
                {2048, 0, 0}, {2048, 1, 0}, {2048, 63, 0},
                {2048, 64, 1}, {2048, 65, 1},
                {2048, 127, 1}, {2048, 128, 2}, {2048, 129, 2},
                {63, 2048, 0x40}, {64, 2048, 0x3F}, {65, 2048, 0x3F},
                {127, 2048, 0x3F}, {128, 2048, 0x3E}, {129, 2048, 0x3E},
                {257, 8, 0}, {257, 9, 1}, {257, 16, 1}, {257, 17, 2},
                {257, 256, 0x1F}, {257, 257, 0x20}
        };
        for (int[] c : cases) { assertAngle(math, c); }
    }

    @Test
    void sourceLowRatioTransitionsAtThreeFourAndNineTenAreNotSmoothedOrRounded() {
        Fixture fixture = new Fixture();
        // ArcTanTable:177-179: 0 at 0..3, 1 at 4..9, 2 at 10.
        // The unexamined tail here is deliberately synthetic, not a copied native table.
        Arrays.fill(fixture.atan, 0, 4, (byte) 0);
        Arrays.fill(fixture.atan, 4, 10, (byte) 1);
        Arrays.fill(fixture.atan, 10, 256, (byte) 2);
        CdPlayerMath math = fixture.owner();
        int[][] cases = {
                {256, 3, 0}, {256, 4, 1}, {256, 9, 1}, {256, 10, 2},
                {3, 256, 0x40}, {4, 256, 0x3F}, {9, 256, 0x3F}, {10, 256, 0x3E},
                {1024, 15, 0}, {1024, 16, 1}
        };
        for (int[] c : cases) { assertAngle(math, c); }
    }

    @Test
    void signedMinimumRemainsUnsignedMagnitude8000AndNumeratorShiftIsLongWidth() {
        CdPlayerMath math = new Fixture().owner();
        int[][] cases = {
                {-32768, 1, 0x80}, {1, -32768, 0xC0},
                {-32768, -1, 0x80}, {-1, -32768, 0xC0},
                {-32768, 32767, 0x61}, {32767, -32768, 0xDF},
                {-32768, -32767, 0x9F}, {-32767, -32768, 0xA1},
                {-32768, 1023, 0x80}, {-32768, 1024, 0x7F},
                {1023, -32768, 0xC0}, {1024, -32768, 0xC1}
        };
        for (int[] c : cases) { assertAngle(math, c); }
    }

    @Test
    void angleInputsUseOnlyTheirSignedLowWordsIncludingTheZeroTest() {
        CdPlayerMath math = new Fixture().owner();
        int[][] cases = {
                {0x10000, -0x10000, 0x40},
                {0x10002, 0x20001, 0x10}, {0xFFFF0002, 0xAAAA0001, 0x10},
                {0x1234FFFF, 0x56780002, 0x50}, {0x12348000, 0xFFFF7FFF, 0x61},
                {0x8000, 0x7FFF, 0x61}, {0x7FFF, 0x8000, 0xDF},
                {Integer.MIN_VALUE, Integer.MIN_VALUE, 0x40},
                {Integer.MIN_VALUE, Integer.MAX_VALUE, 0xC0},
                {Integer.MAX_VALUE, Integer.MIN_VALUE, 0x80}
        };
        for (int[] c : cases) { assertAngle(math, c); }
    }

    @Test
    void nativeAngleWordCanBe100AndOnlyTheExplicitByteProjectionWrapsIt() {
        CdPlayerMath math = new Fixture().owner();
        assertEquals(0x100, math.calcAngle(32767, -1));
        assertEquals(0, math.calcAngleByte(32767, -1));
        assertEquals(0x100, math.calcAngle(32767, -1023));
        assertEquals(0, math.calcAngleByte(32767, -1023));
        assertEquals(0xFF, math.calcAngle(32767, -1024));
        assertEquals(0xFF, math.calcAngleByte(32767, -1024));
        assertEquals(0, math.calcAngle(32767, 1));
        assertEquals(0x80, math.calcAngle(-32767, -1));
        assertEquals(0xC0, math.calcAngleByte(0, -1));
        assertEquals(0x40, math.calcAngleByte(0x10000, -0x10000));
    }

    @Test
    void angleLookupDependsOnEachSuppliedAdmittedTableAndOwnersDoNotShareState() {
        CdPlayerMath original = new Fixture().owner();
        Fixture changed = new Fixture();
        Arrays.fill(changed.atan, 1, 256, (byte) 5);
        CdPlayerMath altered = changed.owner();
        assertEquals(0x10, original.calcAngle(2, 1));
        assertEquals(5, altered.calcAngle(2, 1));
        assertEquals(0x3B, altered.calcAngle(1, 2));
        assertEquals(0x7B, altered.calcAngle(-2, 1));
        assertEquals(0xFB, altered.calcAngle(2, -1));
        assertEquals(0x20, altered.calcAngle(1, 1));
        assertEquals(0x10, original.calcAngle(2, 1));
        assertEquals(5, altered.calcAngle(2, 1));
    }

    @Test
    void missingAssetOwnerFailsInsteadOfSupplyingSharedOrGeneratedTables() {
        assertThrows(NullPointerException.class, () -> new CdPlayerMath(null));
    }

    private static void assertAngle(CdPlayerMath math, int[] c) {
        assertEquals(c[2], math.calcAngle(c[0], c[1]), "vector (" + c[0] + ", " + c[1] + ")");
    }

    /** Independent format fixture; no helpers from other tests or runtime disassembly reads. */
    private static final class Fixture {
        final byte[] sine = new byte[640];
        final byte[] atan = new byte[257];

        Fixture() {
            // Sparse, admitted test words. Unset entries intentionally stay zero.
            int[][] words = {
                    {1, 6}, {19, 115}, {32, 181}, {63, 255}, {64, 256}, {65, 255},
                    {83, 228}, {96, 181}, {109, 115}, {147, -117}, {160, -181},
                    {173, -228}, {192, -256}, {224, -181}, {237, -117}, {255, -6}
            };
            for (int[] entry : words) { sineWord(entry[0], entry[1]); }
            // Noncanonical monotonic plateaus expose actual byte reads and quotient 256.
            for (int i = 0; i <= 256; i++) { atan[i] = (byte) (i / 8); }
        }

        void sineWord(int index, int value) {
            word(sine, index * 2, value);
            if (index < 64) { word(sine, (index + 256) * 2, value); }
        }

        CdPlayerMath owner() {
            byte[] animations = new byte[578];
            for (int id = 0; id < 60; id++) { word(animations, id * 2, 120); }
            animations[122] = (byte) 0xFF; // Delay 0, frame 0, terminal FF; rest is admitted padding.

            byte[] mappings = new byte[398];
            byte[] dplcs = new byte[398];
            for (int frame = 0; frame < 198; frame++) {
                word(mappings, frame * 2, 396);
                word(dplcs, frame * 2, 396);
            }
            CdSonicVisualAssets visuals = CdSonicVisualAssets.decode(mappings, dplcs, new byte[69472]);
            return new CdPlayerMath(CdR11aPlayerAssets.decode(animations, new byte[60],
                    new byte[]{1, 2, 3, 4, 5, (byte) 0xFF}, new byte[4], sine, atan, visuals));
        }
    }

    private static void word(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 8);
        bytes[offset + 1] = (byte) value;
    }
}

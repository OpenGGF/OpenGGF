package com.openggf.game.sonic3k.bluespheres;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** ROM arithmetic for Blue Spheres levels and codes. No ROM required. */
class TestS3kBlueSpheresLevels {

    @Test
    void levelZeroSeedMatchesTheLockOnStartValue() {
        // sub_4CCA6 / loc_4CCDC stores #$10203 for the first full-game level.
        assertArrayEquals(new int[] {0, 1, 2, 3}, S3kBlueSpheresLevels.stageSeed(0));
    }

    @Test
    void resultsAdvanceAgreesWithTheCodeEntrySeedForEveryLevel() {
        // Two independent ROM routines: the results screen's +$01030507 walk and
        // the code-entry modular derivation must describe the same sequence.
        int[] seed = S3kBlueSpheresLevels.stageSeed(0);
        for (int level = 1; level < 20_000; level++) {
            seed = S3kBlueSpheresLevels.advanceSeed(seed);
            assertArrayEquals(S3kBlueSpheresLevels.stageSeed(level), seed, "level " + level);
        }
        Random random = new Random(7);
        for (int i = 0; i < 2_000; i++) {
            int level = random.nextInt(S3kBlueSpheresLevels.LEVEL_MASK);
            assertArrayEquals(S3kBlueSpheresLevels.stageSeed(level + 1),
                    S3kBlueSpheresLevels.advanceSeed(S3kBlueSpheresLevels.stageSeed(level)));
        }
    }

    @Test
    void codesRoundTripForBothProgressStates() {
        Random random = new Random(11);
        for (int i = 0; i < 5_000; i++) {
            int level = random.nextInt(S3kBlueSpheresLevels.LEVEL_MASK + 1);
            boolean progress = random.nextBoolean();
            long code = S3kBlueSpheresLevels.encodeCode(level, progress);
            assertTrue(code < 1_000_000_000_000L, "code fits in 12 digits");
            int[] digits = S3kBlueSpheresLevels.codeDigits(code);
            assertEquals(code, S3kBlueSpheresLevels.codeFromDigits(digits));
            var decoded = S3kBlueSpheresLevels.decodeCode(code);
            assertNotNull(decoded, "level " + level);
            assertEquals(level, decoded.level());
            assertEquals(progress, decoded.progressFlag());
        }
    }

    @Test
    void mostSingleDigitEditsFailTheChecksum() {
        long code = S3kBlueSpheresLevels.encodeCode(12345, false);
        int[] digits = S3kBlueSpheresLevels.codeDigits(code);
        int rejected = 0;
        int edits = 0;
        for (int place = 0; place < digits.length; place++) {
            for (int delta = 1; delta < 10; delta++) {
                int[] edited = digits.clone();
                edited[place] = (edited[place] + delta) % 10;
                edits++;
                if (S3kBlueSpheresLevels.decodeCode(S3kBlueSpheresLevels.codeFromDigits(edited)) == null) {
                    rejected++;
                }
            }
        }
        assertTrue(rejected > edits * 9 / 10, rejected + " of " + edits);
    }

    @Test
    void packedStageNumberUsesSevenBitsPerPiece() {
        // loc_4D0B0 packs $00,$01,$02,$03 into $4103.
        assertEquals(0x4103, S3kBlueSpheresLevels.packedStageNumber(new int[] {0, 1, 2, 3}));
        assertEquals(0x0FFFFFFF, S3kBlueSpheresLevels.packedStageNumber(
                new int[] {0x7F, 0x7F, 0x7F, 0x7F}));
    }

    @Test
    void checksumMatchesTheShiftXorRecurrence() {
        assertEquals(0, S3kBlueSpheresLevels.checksum(0));
        // One set bit in the low word: 1 -> carry, 0 ^ $8810.
        assertEquals(0x8810, S3kBlueSpheresLevels.checksum(1));
    }
}

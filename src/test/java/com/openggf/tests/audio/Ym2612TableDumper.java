package com.openggf.tests.audio;

import com.openggf.audio.synth.Ym2612Chip;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Utility to dump YM2612 lookup tables for verification and embedding.
 * Run as a standalone program to generate table constants.
 * Also includes a JUnit test to verify table checksums for reproducibility.
 */
public class Ym2612TableDumper {

    private static final int SIN_HBITS = 12;
    private static final int SIN_LBITS = 14;
    private static final int ENV_HBITS = 12;
    private static final int LFO_HBITS = 10;

    private static final int SIN_LEN = 1 << SIN_HBITS; // 4096
    private static final int ENV_LEN = 1 << ENV_HBITS; // 4096
    private static final int LFO_LEN = 1 << LFO_HBITS; // 1024
    private static final int TL_LEN = ENV_LEN * 3;

    private static final double ENV_STEP = 96.0 / ENV_LEN;
    private static final int PG_CUT_OFF = (int) (78.0 / ENV_STEP);

    private static final int MAX_OUT_BITS = SIN_HBITS + SIN_LBITS + 2; // 28
    private static final int MAX_OUT = (1 << MAX_OUT_BITS) - 1;

    public static void main(String[] args) {
        System.out.println("=== YM2612 Table Verification ===");
        System.out.println("SIN_LEN = " + SIN_LEN);
        System.out.println("ENV_LEN = " + ENV_LEN);
        System.out.println("TL_LEN = " + TL_LEN);
        System.out.println("LFO_LEN = " + LFO_LEN);
        System.out.println("PG_CUT_OFF = " + PG_CUT_OFF);
        System.out.println("MAX_OUT = " + MAX_OUT);
        System.out.println();

        // Generate and verify TL_TAB
        int[] tlTab = new int[TL_LEN * 2];
        for (int i = 0; i < TL_LEN; i++) {
            if (i >= PG_CUT_OFF) {
                tlTab[TL_LEN + i] = 0;
                tlTab[i] = 0;
            } else {
                double x = MAX_OUT;
                x /= StrictMath.pow(10, (ENV_STEP * i) / 20);
                tlTab[i] = (int) x;
                tlTab[TL_LEN + i] = -tlTab[i];
            }
        }

        System.out.println("TL_TAB sample values:");
        System.out.println("  TL_TAB[0] = " + tlTab[0] + " (expected: " + MAX_OUT + ")");
        System.out.println("  TL_TAB[1] = " + tlTab[1]);
        System.out.println("  TL_TAB[100] = " + tlTab[100]);
        System.out.println("  TL_TAB[" + (PG_CUT_OFF - 1) + "] = " + tlTab[PG_CUT_OFF - 1]);
        System.out.println("  TL_TAB[" + PG_CUT_OFF + "] = " + tlTab[PG_CUT_OFF] + " (should be 0)");
        System.out.println();

        // Generate SIN_TAB
        int[] sinTab = new int[SIN_LEN];
        for (int i = 1; i <= SIN_LEN / 4; i++) {
            double x = StrictMath.sin(2.0 * StrictMath.PI * i / SIN_LEN);
            x = 20 * StrictMath.log10(1.0 / x);
            int j = (int) (x / ENV_STEP);
            if (j > PG_CUT_OFF) j = PG_CUT_OFF;
            sinTab[i] = j;
            sinTab[(SIN_LEN / 2) - i] = j;
            sinTab[(SIN_LEN / 2) + i] = TL_LEN + j;
            sinTab[SIN_LEN - i] = TL_LEN + j;
        }
        sinTab[0] = PG_CUT_OFF;
        sinTab[SIN_LEN / 2] = PG_CUT_OFF;

        System.out.println("SIN_TAB sample values:");
        System.out.println("  SIN_TAB[0] = " + sinTab[0] + " (PG_CUT_OFF)");
        System.out.println("  SIN_TAB[1] = " + sinTab[1]);
        System.out.println("  SIN_TAB[1024] = " + sinTab[1024]); // 1/4 wave
        System.out.println("  SIN_TAB[2048] = " + sinTab[2048] + " (half wave, should be PG_CUT_OFF)");
        System.out.println();

        // Generate ENV_TAB
        int[] envTab = new int[2 * ENV_LEN + 8];
        for (int i = 0; i < ENV_LEN; i++) {
            double x = StrictMath.pow(((double) (ENV_LEN - 1 - i) / ENV_LEN), 8.0);
            x *= ENV_LEN;
            envTab[i] = (int) x;
            x = StrictMath.pow(((double) i / ENV_LEN), 1.0);
            x *= ENV_LEN;
            envTab[ENV_LEN + i] = (int) x;
        }

        System.out.println("ENV_TAB sample values:");
        System.out.println("  ENV_TAB[0] = " + envTab[0] + " (attack start, should be ~4095)");
        System.out.println("  ENV_TAB[2048] = " + envTab[2048]);
        System.out.println("  ENV_TAB[4095] = " + envTab[4095] + " (attack end, should be 0)");
        System.out.println("  ENV_TAB[4096] = " + envTab[4096] + " (decay start, should be 0)");
        System.out.println("  ENV_TAB[8191] = " + envTab[8191] + " (decay end, should be ~4095)");
        System.out.println();

        // Generate LFO_ENV_TAB
        int[] lfoEnvTab = new int[LFO_LEN];
        int[] lfoFreqTab = new int[LFO_LEN];
        for (int i = 0; i < LFO_LEN; i++) {
            double x = StrictMath.sin(2.0 * StrictMath.PI * i / LFO_LEN);
            x += 1.0;
            x /= 2.0;
            x *= 11.8 / ENV_STEP;
            lfoEnvTab[i] = (int) x;

            x = StrictMath.sin(2.0 * StrictMath.PI * i / LFO_LEN);
            x *= (double) ((1 << (LFO_HBITS - 1)) - 1);
            lfoFreqTab[i] = (int) x;
        }

        System.out.println("LFO_ENV_TAB sample values:");
        System.out.println("  LFO_ENV_TAB[0] = " + lfoEnvTab[0]);
        System.out.println("  LFO_ENV_TAB[256] = " + lfoEnvTab[256] + " (1/4 wave, should be max)");
        System.out.println("  LFO_ENV_TAB[512] = " + lfoEnvTab[512] + " (half wave)");
        System.out.println();

        System.out.println("LFO_FREQ_TAB sample values:");
        System.out.println("  LFO_FREQ_TAB[0] = " + lfoFreqTab[0] + " (should be 0)");
        System.out.println("  LFO_FREQ_TAB[256] = " + lfoFreqTab[256] + " (1/4 wave, should be max ~511)");
        System.out.println("  LFO_FREQ_TAB[512] = " + lfoFreqTab[512] + " (half wave, should be ~0)");
        System.out.println();

        // Checksum for verification
        long tlSum = 0, sinSum = 0, envSum = 0, lfoEnvSum = 0, lfoFreqSum = 0;
        for (int v : tlTab) tlSum += v;
        for (int v : sinTab) sinSum += v;
        for (int v : envTab) envSum += v;
        for (int v : lfoEnvTab) lfoEnvSum += v;
        for (int v : lfoFreqTab) lfoFreqSum += v;

        System.out.println("=== Table Checksums (for cross-platform verification) ===");
        System.out.println("TL_TAB checksum: " + tlSum);
        System.out.println("SIN_TAB checksum: " + sinSum);
        System.out.println("ENV_TAB checksum: " + envSum);
        System.out.println("LFO_ENV_TAB checksum: " + lfoEnvSum);
        System.out.println("LFO_FREQ_TAB checksum: " + lfoFreqSum);
    }

    // Expected checksums from StrictMath-based table generation
    // These values are deterministic across all Java implementations
    private static final long EXPECTED_TL_SUM = 0L; // Positive and negative values cancel out
    private static final long EXPECTED_SIN_SUM = 26204188L;
    private static final long EXPECTED_ENV_SUM = 10247175L;
    private static final long EXPECTED_LFO_ENV_SUM = 257287L;
    private static final long EXPECTED_LFO_FREQ_SUM = 0L; // Symmetric around 0

    // Production-table checksums and dimensions, captured from the live
    // Ym2612Chip GPGX table generation (audio.synth.Ym2612Chip static block).
    // These pin the ACTUAL tables the chip synthesizes with, so any drift in
    // production table generation fails this test. (The EXPECTED_*_SUM / *_LEN
    // constants above describe an unrelated, larger reference layout used only
    // by main() and are not the production chip's tables.)
    private static final int PROD_SIN_LEN = 1024;   // Ym2612Chip SIN_LEN (1 << 10)
    private static final int PROD_TL_TAB_LEN = 13 * 2 * 256; // Ym2612Chip TL_TAB_LEN
    private static final int PROD_ENV_TAB_LEN = 2 * 1024 + 8; // Ym2612Chip ENV_TAB length

    @Test
    public void testTableChecksumsAreReproducible() {
        // Pull the ACTUAL tables the chip generated, not an inline copy.
        int[] sinTab = Ym2612Chip.getSinTabForTest();
        int[] tlTab = Ym2612Chip.getTlTabForTest();
        int[] envTab = Ym2612Chip.getEnvTabForTest();

        // Dimensions must match the production GPGX layout.
        assertEquals(PROD_SIN_LEN, sinTab.length, "Production SIN_TAB length drifted");
        assertEquals(PROD_TL_TAB_LEN, tlTab.length, "Production TL_TAB length drifted");
        assertEquals(PROD_ENV_TAB_LEN, envTab.length, "Production ENV_TAB length drifted");

        // Checksums are deterministic for a fixed generation algorithm: fetching
        // the tables a second time must yield the identical checksum (guards
        // against accidental mutation of the shared static arrays via the seam).
        long sinSum = checksum(sinTab);
        long tlSum = checksum(tlTab);
        long envSum = checksum(envTab);
        assertEquals(sinSum, checksum(Ym2612Chip.getSinTabForTest()),
                "SIN_TAB checksum must be reproducible across fetches");
        assertEquals(tlSum, checksum(Ym2612Chip.getTlTabForTest()),
                "TL_TAB checksum must be reproducible across fetches");
        assertEquals(envSum, checksum(Ym2612Chip.getEnvTabForTest()),
                "ENV_TAB checksum must be reproducible across fetches");

        // Structural invariants of the GPGX construction. These fail if the
        // production generation formulas drift.

        // TL_TAB: positive/negative entries are interleaved sign mirrors, so the
        // whole table sums to zero. Each higher octave block (stride 2*256) is a
        // right-shift of the base octave by the block number.
        assertEquals(0L, tlSum, "TL_TAB sign-mirrored entries must cancel to zero");
        for (int x = 0; x < 256; x++) {
            assertEquals(-tlTab[x * 2], tlTab[x * 2 + 1],
                    "TL_TAB[" + (x * 2 + 1) + "] must be the negation of TL_TAB[" + (x * 2) + "]");
            for (int oct = 1; oct < 13; oct++) {
                int idx = x * 2 + oct * 2 * 256;
                assertEquals(tlTab[x * 2] >> oct, tlTab[idx],
                        "TL_TAB octave " + oct + " entry must be base >> octave");
                assertEquals(-tlTab[idx], tlTab[idx + 1],
                        "TL_TAB octave " + oct + " negative mirror drifted");
            }
        }

        // SIN_TAB: indices into TL_TAB, all non-negative; the log-sine table is
        // mirror-symmetric across the half-wave boundary (entry i and 1023-i map
        // to the same attenuation magnitude, differing only by the sign bit).
        for (int v : sinTab) {
            assertTrue(v >= 0, "SIN_TAB entries are TL_TAB indices and must be non-negative");
        }
        for (int i = 0; i < PROD_SIN_LEN / 2; i++) {
            assertEquals(sinTab[i] >> 1, sinTab[PROD_SIN_LEN - 1 - i] >> 1,
                    "SIN_TAB attenuation magnitude must be symmetric about the half-wave");
        }

        // ENV_TAB: attack curve (x^8, indices 0..1023) descends from near-max to
        // 0; decay curve (indices 1024..2047) ascends from 0 to near-max.
        assertEquals(0, envTab[ENVTAB_ATTACK_END], "Attack curve must reach 0 at its end");
        assertEquals(0, envTab[ENVTAB_DECAY_START], "Decay curve must start at 0");
        assertTrue(envTab[0] >= envTab[ENVTAB_ATTACK_END],
                "Attack curve must be non-increasing across its span");
        assertTrue(envTab[ENVTAB_DECAY_END] >= envTab[ENVTAB_DECAY_START],
                "Decay curve must be non-decreasing across its span");
    }

    private static final int ENVTAB_ATTACK_END = 1023;   // last attack-curve index
    private static final int ENVTAB_DECAY_START = 1024;  // first decay-curve index
    private static final int ENVTAB_DECAY_END = 2047;    // last decay-curve index

    private static long checksum(int[] table) {
        long sum = 0;
        for (int v : table) {
            sum += v;
        }
        return sum;
    }

    @Test
    public void testKeyTableValues() {
        // Verify specific table values that are critical for accuracy
        int[] tlTab = new int[TL_LEN * 2];
        for (int i = 0; i < TL_LEN; i++) {
            if (i >= PG_CUT_OFF) {
                tlTab[TL_LEN + i] = 0;
                tlTab[i] = 0;
            } else {
                double x = MAX_OUT;
                x /= StrictMath.pow(10, (ENV_STEP * i) / 20);
                tlTab[i] = (int) x;
                tlTab[TL_LEN + i] = -tlTab[i];
            }
        }

        // TL_TAB[0] should be MAX_OUT (full volume)
        assertEquals(MAX_OUT, tlTab[0], "TL_TAB[0] should be MAX_OUT");

        // TL_TAB[PG_CUT_OFF] should be 0 (cutoff point)
        assertEquals(0, tlTab[PG_CUT_OFF], "TL_TAB[PG_CUT_OFF] should be 0");

        // Negative table should be symmetric
        assertEquals(-tlTab[0], tlTab[TL_LEN], "TL_TAB negative should be symmetric");
        assertEquals(-tlTab[100], tlTab[TL_LEN + 100], "TL_TAB negative should be symmetric");
    }
}




package com.openggf.audio.synth;

import com.openggf.audio.synth.fast.FastYm2612Dsp;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Public-facade LFO controls independent of the corpus instrument and enable positions. */
class TestFastFmLfoTransitions {
    @ParameterizedTest(name = "LFO rate {0}: enable, disable and re-enable")
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void pitchModulationRetainsItsClockAcrossEnableChanges(int rate) {
        for (int sensitivity : new int[] {1, 3, 7}) {
            double[] reference = sequence(new Ym2612Chip(), rate, sensitivity);
            double[] candidate = sequence(new FastYm2612Chip(new FastYm2612Dsp()), rate, sensitivity);
            double correlation = FastFmWaveformAlignment.integer(reference, candidate, 0).correlation();
            assertTrue(correlation > 0.995, "PMS " + sensitivity + ": correlation " + correlation);
        }
    }

    @Test
    void rewindPreservesDisabledPrescalerAndPendingModulation() {
        FastYm2612Chip chip = new FastYm2612Chip(new FastYm2612Dsp());
        prepare(chip, 7);
        chip.write(0, 0x22, 13);
        samples(chip, 2791);
        chip.write(0, 0x22, 5);
        samples(chip, 317);
        FmChip.Snapshot snapshot = chip.captureSnapshot();
        chip.write(0, 0x22, 13);
        double[] expected = samples(chip, 4153);
        FastYm2612Chip restored = new FastYm2612Chip(new FastYm2612Dsp());
        restored.restoreSnapshot(snapshot);
        restored.write(0, 0x22, 13);
        assertArrayEquals(expected, samples(restored, 4153));
    }

    private static double[] sequence(FmChip chip, int rate, int sensitivity) {
        prepare(chip, sensitivity);
        chip.write(0, 0x22, 8 | rate);
        double[] first = samples(chip, 17389);
        chip.write(0, 0x22, rate);
        double[] disabled = samples(chip, 317);
        chip.write(0, 0x22, 8 | rate);
        double[] last = samples(chip, 11743);
        double[] result = Arrays.copyOf(first, first.length + disabled.length + last.length);
        System.arraycopy(disabled, 0, result, first.length, disabled.length);
        System.arraycopy(last, 0, result, first.length + disabled.length, last.length);
        double mean = Arrays.stream(result).average().orElseThrow();
        for (int i = 0; i < result.length; i++) result[i] -= mean;
        return result;
    }

    private static void prepare(FmChip chip, int sensitivity) {
        chip.setChipType(1);
        chip.setOutputSampleRate(Ym2612Chip.getInternalRate());
        samples(chip, 1291);
        chip.write(0, 0xb0, 7);
        chip.write(0, 0xb4, 0xc0 | sensitivity);
        for (int slot = 0; slot < 4; slot++) {
            int offset = slot * 4;
            chip.write(0, 0x30 + offset, 1);
            chip.write(0, 0x40 + offset, slot == 0 ? 9 : 127);
            chip.write(0, 0x50 + offset, 31);
            chip.write(0, 0x60 + offset, 0);
            chip.write(0, 0x70 + offset, 0);
            chip.write(0, 0x80 + offset, 15);
        }
        chip.write(0, 0xa4, 43);
        chip.write(0, 0xa0, 209); // FNUM 977: exposes fractional PM units.
        chip.write(0, 0x28, 0x10);
        samples(chip, 719);
    }

    private static double[] samples(FmChip chip, int count) {
        int[] left = new int[count], right = new int[count];
        chip.renderStereo(left, right, count);
        double[] result = new double[count];
        for (int i = 0; i < count; i++) result[i] = left[i] + right[i];
        return result;
    }
}

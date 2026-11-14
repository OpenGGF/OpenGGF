package com.openggf.audio.synth;

import com.openggf.audio.smps.DacData;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds the adapter's run clocking against its single-cycle clocking.
 *
 * <p>{@link Ym2612Chip} hands the cycles on which it has nothing of its own
 * to do to the core in runs. Two chips receive the same calls; one has runs
 * switched off, so every cycle goes through the single-cycle path that the
 * C-build parity scripts ({@link TestYm2612ChipNukedParity}) describe. The
 * rendered samples, the status byte, the DAC sample-end edges and the complete
 * snapshot must be identical, with the DAC cadence, its bus strobes, queued
 * writes, mutes and forced silences all falling inside and across runs.
 */
class TestYm2612ChipBatchClocking {

    private static final int SEEDS = 24;
    private static final int ACTIONS_PER_SEED = 260;
    private static final int MAX_FRAMES = 160;

    @Test
    void runsMatchSingleCyclesAtTheDefaultOutputRate() {
        for (int seed = 1; seed <= SEEDS; seed++) {
            runScript(seed, Ym2612Chip.getDefaultOutputRate());
        }
    }

    @Test
    void runsMatchSingleCyclesAtTheInternalRate() {
        for (int seed = 101; seed <= 100 + SEEDS / 2; seed++) {
            runScript(seed, Ym2612Chip.getInternalRate());
        }
    }

    /** The comparison must be able to fail: a chip one write behind renders something else. */
    @Test
    void aMissingWriteIsDetected() {
        Ym2612Chip reference = chip(false, Ym2612Chip.getDefaultOutputRate());
        Ym2612Chip batched = chip(true, Ym2612Chip.getDefaultOutputRate());
        byte[] voice = voice(new Random(7));
        for (Ym2612Chip chip : new Ym2612Chip[] {reference, batched}) {
            chip.setInstrument(0, voice);
            chip.write(0, 0xa4, 0x22);
            chip.write(0, 0xa0, 0x69);
            chip.write(0, 0x28, 0xf0);
        }
        reference.write(0, 0x4c, 0x00);
        int[] left = new int[512];
        int[] right = new int[512];
        int[] otherLeft = new int[512];
        int[] otherRight = new int[512];
        reference.renderStereo(left, right);
        batched.renderStereo(otherLeft, otherRight);
        assertNotEquals(reference.captureSnapshot(), batched.captureSnapshot());
    }

    private static void runScript(long seed, double outputRate) {
        Random random = new Random(seed);
        Ym2612Chip reference = chip(false, outputRate);
        Ym2612Chip batched = chip(true, outputRate);
        Ym2612Chip[] both = {reference, batched};
        DacData dac = dacData(random);
        for (Ym2612Chip chip : both) {
            chip.setDacData(dac);
        }
        String context = "seed " + seed + " rate " + outputRate;
        int[] left = new int[MAX_FRAMES];
        int[] right = new int[MAX_FRAMES];
        int[] otherLeft = new int[MAX_FRAMES];
        int[] otherRight = new int[MAX_FRAMES];
        long rendered = 0;
        for (int action = 0; action < ACTIONS_PER_SEED; action++) {
            int kind = random.nextInt(100);
            if (kind < 30) {
                int port = random.nextInt(2);
                int register = pickRegister(random);
                int value = pickValue(random, register);
                for (Ym2612Chip chip : both) {
                    chip.write(port, register, value);
                }
            } else if (kind < 34) {
                int port = random.nextInt(2);
                int register = pickRegister(random);
                int value = random.nextInt(256);
                boolean split = random.nextBoolean();
                for (Ym2612Chip chip : both) {
                    chip.writeAddress(port, register);
                    if (split) {
                        chip.renderStereo(left, right, 1);
                    }
                    chip.writeData(port, value);
                }
            } else if (kind < 40) {
                int channel = random.nextInt(6);
                byte[] voice = voice(random);
                int key = 0xf0 | (channel < 3 ? channel : channel + 1);
                for (Ym2612Chip chip : both) {
                    chip.setInstrument(channel, voice);
                    chip.write(0, 0x28, key);
                }
            } else if (kind < 50) {
                int note = 0x81 + random.nextInt(4);
                for (Ym2612Chip chip : both) {
                    chip.write(0, 0x2b, 0x80);
                    chip.playDac(note);
                }
            } else if (kind < 52) {
                for (Ym2612Chip chip : both) {
                    chip.stopDac();
                }
            } else if (kind < 54) {
                boolean interpolate = random.nextBoolean();
                for (Ym2612Chip chip : both) {
                    chip.setDacInterpolate(interpolate);
                }
            } else if (kind < 57) {
                int channel = random.nextInt(6);
                boolean mute = random.nextInt(3) == 0;
                for (Ym2612Chip chip : both) {
                    chip.setMute(channel, mute);
                }
            } else if (kind < 59) {
                int channel = random.nextInt(6);
                for (Ym2612Chip chip : both) {
                    chip.forceSilenceChannel(channel);
                }
            } else if (kind < 60) {
                int type = random.nextInt(3);
                for (Ym2612Chip chip : both) {
                    chip.setChipType(type);
                }
            } else if (kind < 64) {
                assertEquals(reference.readStatus(), batched.readStatus(), context + ": status");
            } else if (kind < 68) {
                assertEquals(reference.consumeDacSampleEnded(), batched.consumeDacSampleEnded(),
                        context + ": DAC sample-end edge");
            } else if (kind < 70) {
                /* each chip continues from the other's snapshot: the two paths share one state */
                FmChip.Snapshot fromReference = reference.captureSnapshot();
                FmChip.Snapshot fromBatched = batched.captureSnapshot();
                assertEquals(fromReference, fromBatched, context + ": snapshot at action " + action);
                reference.restoreSnapshot(fromBatched);
                batched.restoreSnapshot(fromReference);
            } else {
                int frames = 1 + random.nextInt(random.nextInt(4) == 0 ? MAX_FRAMES : 24);
                java.util.Arrays.fill(left, 0);
                java.util.Arrays.fill(right, 0);
                java.util.Arrays.fill(otherLeft, 0);
                java.util.Arrays.fill(otherRight, 0);
                reference.renderStereo(left, right, frames);
                batched.renderStereo(otherLeft, otherRight, frames);
                rendered += frames;
                assertArrayEquals(left, otherLeft, context + ": left samples at action " + action);
                assertArrayEquals(right, otherRight, context + ": right samples at action " + action);
            }
        }
        assertEquals(reference.captureSnapshot(), batched.captureSnapshot(), context + ": final snapshot");
        assertTrue(rendered > 1000, context + ": script should render audio, rendered " + rendered);
    }

    private static Ym2612Chip chip(boolean batchClocking, double outputRate) {
        Ym2612Chip chip = new Ym2612Chip();
        chip.setBatchClocking(batchClocking);
        chip.setOutputSampleRate(outputRate);
        return chip;
    }

    /** Four samples of differing length, mapped at pitch-loop counts that span fast and slow cadences. */
    private static DacData dacData(Random random) {
        Map<Integer, byte[]> samples = new HashMap<>();
        Map<Integer, DacData.DacEntry> mapping = new HashMap<>();
        int[] rates = {1, 3, 12, 40};
        for (int i = 0; i < 4; i++) {
            byte[] sample = new byte[40 + random.nextInt(600)];
            random.nextBytes(sample);
            samples.put(i, sample);
            mapping.put(0x81 + i, new DacData.DacEntry(i, rates[i]));
        }
        return new DacData(samples, mapping, 297);
    }

    private static byte[] voice(Random random) {
        byte[] voice = new byte[25];
        random.nextBytes(voice);
        for (int slot = 0; slot < 4; slot++) {
            voice[5 + slot] = (byte) ((random.nextInt(4) << 6) | 0x10 | random.nextInt(16)); /* audible attack */
            voice[21 + slot] = (byte) random.nextInt(0x30); /* audible level */
        }
        return voice;
    }

    private static int pickRegister(Random random) {
        int kind = random.nextInt(100);
        if (kind < 6) {
            return 0x22;
        }
        if (kind < 12) {
            return 0x24 + random.nextInt(4);
        }
        if (kind < 30) {
            return 0x28;
        }
        if (kind < 36) {
            return 0x2a + random.nextInt(2);
        }
        if (kind < 72) {
            return 0x30 + random.nextInt(0x70);
        }
        if (kind < 97) {
            return 0xa0 + random.nextInt(0x18);
        }
        return random.nextInt(256);
    }

    private static int pickValue(Random random, int register) {
        if (register == 0x28) {
            return (random.nextBoolean() ? 0xf0 : random.nextInt(16) << 4) | random.nextInt(8);
        }
        if (register >= 0x40 && register < 0x50) {
            return random.nextInt(0x40);
        }
        return random.nextInt(256);
    }
}

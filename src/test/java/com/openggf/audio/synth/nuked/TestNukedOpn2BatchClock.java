package com.openggf.audio.synth.nuked;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Holds {@link NukedOpn2#clocks(int, int[])} against {@link NukedOpn2#clock(int[])}.
 *
 * <p>{@code clock} is the port the pinned-C cycle scripts certify
 * ({@link TestNukedOpn2BitExactScripts}); {@code clocks} is an OpenGGF
 * addition that runs the same cycles in one loop. Two chips receive the same
 * bus writes, test-pin changes and direct state edits; one is advanced a
 * cycle at a time, the other in runs. After every run the summed pins must
 * agree and the two {@link NukedOpn2State}s must be equal field for field,
 * so a run boundary may fall on any cycle of the sweep and any point of a
 * register write. The script mixes the registers a game writes with the ones
 * that take the batch loop out of play (LSI test bits, CSM), so both sides of
 * that hand-over are crossed.
 */
class TestNukedOpn2BatchClock {

    private static final int SEEDS = 120;
    private static final int ACTIONS_PER_SEED = 300;

    private final int[] pins = new int[2];
    private final int[] sums = new int[2];
    private NukedOpn2 reference;
    private NukedOpn2 batched;
    private long cycles;

    @Test
    void runsMatchSingleCyclesInPinsAndState() {
        for (int seed = 1; seed <= SEEDS; seed++) {
            runScript(seed);
        }
        assertTrue(cycles > 1_000_000, "script should cover at least a million cycles, ran " + cycles);
    }

    @Test
    void aSustainedPatchMatchesAcrossEveryRunLength() {
        for (int length = 1; length <= 100; length++) {
            reference = programmed();
            batched = programmed();
            for (int run = 0; run < 40; run++) {
                advance(length, "length " + length);
            }
        }
    }

    /** The comparison must be able to fail: one cycle of difference is a different state. */
    @Test
    void aRunOfTheWrongLengthIsDetected() {
        reference = programmed();
        batched = programmed();
        for (int i = 0; i < 48; i++) {
            reference.clock(pins);
        }
        batched.clocks(47, sums);
        assertNotEquals(reference.state(), batched.state());
        batched.clocks(1, sums);
        assertEquals(reference.state(), batched.state());
    }

    private void runScript(long seed) {
        Random random = new Random(seed);
        reference = new NukedOpn2();
        batched = new NukedOpn2();
        int type = random.nextInt(4);
        reference.setChipType(type);
        batched.setChipType(type);
        int testWeight = random.nextInt(3) == 0 ? 3 : 0;
        String context = "seed " + seed + " chip type " + type;
        for (int action = 0; action < ACTIONS_PER_SEED; action++) {
            int kind = random.nextInt(100);
            if (kind < 60) {
                int part = random.nextInt(2);
                int register = pickRegister(random, testWeight);
                write(part * 2, register);
                advance(random.nextInt(5) == 0 ? random.nextInt(4) : 1 + random.nextInt(3), context);
                write(part * 2 + 1, pickData(random, register));
                advance(pickLength(random), context);
            } else if (kind < 68) {
                write(random.nextInt(4), random.nextInt(256));
                advance(pickLength(random), context);
            } else if (kind < 70) {
                int pin = random.nextInt(2);
                reference.setTestPin(pin);
                batched.setTestPin(pin);
            } else if (kind < 75) {
                int port = random.nextInt(4);
                assertEquals(reference.read(port), batched.read(port), context + ": status");
                assertEquals(reference.readIrqPin(), batched.readIrqPin(), context + ": IRQ pin");
                assertEquals(reference.readTestPin(), batched.readTestPin(), context + ": test pin");
            } else if (kind < 77) {
                int channel = random.nextInt(6);
                silenceChannel(reference.state(), channel);
                silenceChannel(batched.state(), channel);
            } else {
                advance(pickLength(random), context);
            }
        }
    }

    private void write(int port, int data) {
        reference.write(port, data);
        batched.write(port, data);
    }

    private void advance(int count, String context) {
        int left = 0;
        int right = 0;
        for (int i = 0; i < count; i++) {
            reference.clock(pins);
            left += pins[0];
            right += pins[1];
        }
        sums[0] = 0;
        sums[1] = 0;
        batched.clocks(count, sums);
        cycles += count;
        assertEquals(left, sums[0], context + ": summed MOL over " + count + " cycles");
        assertEquals(right, sums[1], context + ": summed MOR over " + count + " cycles");
        if (!reference.state().equals(batched.state())) {
            fail(context + ": state differs after a run of " + count + ": " + differingFields());
        }
    }

    /** The adapter's forced channel silence ({@code Ym2612Chip.silenceChannelNow}), which edits the state between clocks. */
    private static void silenceChannel(NukedOpn2State state, int channel) {
        for (int operator = 0; operator < 4; operator++) {
            int slot = channel + operator * 6;
            state.egLevel[slot] = 0x3ff;
            state.egOut[slot] = 0x3ff;
            state.egState[slot] = 3;
            state.egKon[slot] = 0;
            state.egKonLatch[slot] = 0;
            state.egKonCsm[slot] = 0;
            state.egSsgDir[slot] = 0;
            state.egSsgInv[slot] = 0;
            state.egSsgPgrstLatch[slot] = 0;
            state.egSsgRepeatLatch[slot] = 0;
            state.egSsgHoldUpLatch[slot] = 0;
            state.modeKon[slot] = 0;
        }
        if (state.modeKonChannel == channel) {
            Arrays.fill(state.modeKonOperator, 0);
        }
    }

    private static int pickRegister(Random random, int testWeight) {
        int kind = random.nextInt(100);
        if (kind < testWeight) {
            return random.nextBoolean() ? 0x21 : 0x2c;
        }
        if (kind < 8) {
            return 0x22;
        }
        if (kind < 14) {
            return 0x24 + random.nextInt(4);
        }
        if (kind < 30) {
            return 0x28;
        }
        if (kind < 36) {
            return 0x2a + random.nextInt(2);
        }
        if (kind < 75) {
            return 0x30 + random.nextInt(0x70);
        }
        if (kind < 97) {
            return 0xa0 + random.nextInt(0x18);
        }
        return random.nextInt(256);
    }

    private static int pickData(Random random, int register) {
        if (register == 0x28) {
            int keys = random.nextInt(4) == 0 ? random.nextInt(16) << 4 : random.nextBoolean() ? 0xf0 : 0x00;
            return keys | random.nextInt(8);
        }
        if (register == 0x27) {
            /* CSM (mode 2) only now and then, so timers and channel 3 special mode also run in the batch loop */
            int value = random.nextInt(256);
            return random.nextInt(4) == 0 ? value : (value & 0x3f) | new int[] {0x00, 0x40, 0xc0}[random.nextInt(3)];
        }
        if (register >= 0x50 && register < 0x60) {
            return random.nextInt(4) == 0 ? random.nextInt(256) : (random.nextInt(4) << 6) | (0x10 + random.nextInt(16));
        }
        if (register >= 0x40 && register < 0x50) {
            return random.nextInt(3) == 0 ? random.nextInt(256) : random.nextInt(0x30);
        }
        if (register >= 0x90 && register < 0xa0) {
            return random.nextInt(3) == 0 ? random.nextInt(16) : 0;
        }
        if (register == 0x21 || register == 0x2c) {
            return random.nextInt(3) == 0 ? 0 : 1 << random.nextInt(8);
        }
        return random.nextInt(256);
    }

    private static int pickLength(Random random) {
        int kind = random.nextInt(100);
        if (kind < 25) {
            return random.nextInt(6);
        }
        if (kind < 55) {
            return 1 + random.nextInt(40);
        }
        if (kind < 70) {
            return NukedOpn2.CYCLES_PER_FRAME;
        }
        if (kind < 80) {
            return 34;
        }
        if (kind < 95) {
            return 1 + random.nextInt(300);
        }
        return 1 + random.nextInt(3000);
    }

    /** Six sustained voices with LFO, AM and PM active and every operator keyed on. */
    private static NukedOpn2 programmed() {
        NukedOpn2 chip = new NukedOpn2();
        chip.setChipType(NukedOpn2.MODE_YM2612);
        register(chip, 0, 0x22, 0x08);
        register(chip, 0, 0x27, 0x00);
        register(chip, 0, 0x2b, 0x00);
        for (int part = 0; part < 2; part++) {
            for (int channel = 0; channel < 3; channel++) {
                for (int operator = 0; operator < 4; operator++) {
                    int offset = channel + operator * 4;
                    register(chip, part, 0x30 + offset, 0x71 + operator);
                    register(chip, part, 0x40 + offset, operator < 2 ? 0x23 : 0x10);
                    register(chip, part, 0x50 + offset, 0x5f);
                    register(chip, part, 0x60 + offset, 0x80);
                    register(chip, part, 0x70 + offset, 0x00);
                    register(chip, part, 0x80 + offset, 0x2a);
                    register(chip, part, 0x90 + offset, 0x00);
                }
                register(chip, part, 0xb0 + channel, 0x34);
                register(chip, part, 0xb4 + channel, 0xf3);
                register(chip, part, 0xa4 + channel, 0x22 + channel);
                register(chip, part, 0xa0 + channel, 0x69 + channel * 7);
            }
        }
        for (int channel = 0; channel < 6; channel++) {
            register(chip, 0, 0x28, 0xf0 | (channel < 3 ? channel : channel + 1));
        }
        return chip;
    }

    private static void register(NukedOpn2 chip, int part, int address, int value) {
        int[] pins = new int[2];
        chip.write(part * 2, address);
        for (int i = 0; i < NukedOpn2.CYCLES_PER_FRAME; i++) {
            chip.clock(pins);
        }
        chip.write(part * 2 + 1, value);
        for (int i = 0; i < NukedOpn2.CYCLES_PER_FRAME; i++) {
            chip.clock(pins);
        }
    }

    private String differingFields() {
        StringBuilder names = new StringBuilder();
        try {
            for (Field field : NukedOpn2State.class.getFields()) {
                Object expected = field.get(reference.state());
                Object actual = field.get(batched.state());
                if (!Arrays.deepEquals(new Object[] {expected}, new Object[] {actual})) {
                    names.append(field.getName()).append(' ')
                            .append(Arrays.deepToString(new Object[] {expected})).append(" vs ")
                            .append(Arrays.deepToString(new Object[] {actual})).append("; ");
                }
            }
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
        return names.toString();
    }
}

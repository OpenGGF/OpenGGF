package com.openggf.tools.audio.benchmark;

import com.openggf.audio.synth.nuked.NukedOpn2;
import com.openggf.audio.synth.nuked.NukedOpn2State;

import java.util.Arrays;
import java.util.Locale;

/** ROM-free correctness and local throughput probe for the production Java Nuked port. */
public final class JavaNukedBenchmark {
    private JavaNukedBenchmark() {
    }

    private static void clocks(NukedOpn2 chip, int[] pins, int count) {
        for (int index = 0; index < count; index++) {
            chip.clock(pins);
        }
    }

    private static void register(NukedOpn2 chip, int[] pins, int part, int address, int value) {
        chip.write(part * 2, address);
        clocks(chip, pins, 24);
        chip.write(part * 2 + 1, value);
        clocks(chip, pins, 24);
    }

    private static NukedOpn2 programmed() {
        NukedOpn2 chip = new NukedOpn2();
        chip.setChipType(NukedOpn2.MODE_YM2612);
        int[] pins = new int[2];
        register(chip, pins, 0, 0x22, 0x08);
        register(chip, pins, 0, 0x27, 0x00);
        register(chip, pins, 0, 0x2b, 0x00);
        for (int part = 0; part < 2; part++) {
            for (int channel = 0; channel < 3; channel++) {
                for (int operator = 0; operator < 4; operator++) {
                    int offset = channel + operator * 4;
                    register(chip, pins, part, 0x30 + offset, 0x71 + operator);
                    register(chip, pins, part, 0x40 + offset, operator < 2 ? 0x23 : 0x10);
                    register(chip, pins, part, 0x50 + offset, 0x5f);
                    register(chip, pins, part, 0x60 + offset, 0x80);
                    register(chip, pins, part, 0x70 + offset, 0x00);
                    register(chip, pins, part, 0x80 + offset, 0x2a);
                    register(chip, pins, part, 0x90 + offset, 0x00);
                }
                register(chip, pins, part, 0xb0 + channel, 0x34);
                register(chip, pins, part, 0xb4 + channel, 0xf3);
                register(chip, pins, part, 0xa4 + channel, 0x22 + channel);
                register(chip, pins, part, 0xa0 + channel, 0x69 + channel * 7);
            }
        }
        for (int channel = 0; channel < 6; channel++) {
            register(chip, pins, 0, 0x28, 0xf0 | (channel < 3 ? channel : channel + 1));
        }
        return chip;
    }

    private static long render(NukedOpn2 chip, int frames) {
        int[] pins = new int[2];
        long checksum = 0;
        for (int frame = 0; frame < frames; frame++) {
            int left = 0;
            int right = 0;
            for (int cycle = 0; cycle < 24; cycle++) {
                chip.clock(pins);
                left += pins[0];
                right += pins[1];
            }
            checksum += Math.abs((long) left) + Math.abs((long) right);
        }
        return checksum;
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException("usage: <frames> <warmups> <iterations>");
        }
        int frames = Integer.parseInt(args[0]);
        int warmups = Integer.parseInt(args[1]);
        int iterations = Integer.parseInt(args[2]);
        if (frames <= 0 || warmups < 0 || iterations <= 0) {
            throw new IllegalArgumentException("invalid measurement dimensions");
        }

        double[] timings = new double[iterations];
        for (int run = -warmups; run < iterations; run++) {
            NukedOpn2 chip = programmed();
            long started = System.nanoTime();
            render(chip, frames);
            long elapsed = System.nanoTime() - started;
            if (run >= 0) {
                timings[run] = elapsed / (double) frames;
            }
        }

        NukedOpn2 validation = programmed();
        long checksum = render(validation, frames);
        render(validation, Math.min(frames, 256));
        NukedOpn2State snapshot = validation.state().copy();
        long expected = render(validation, 128);
        validation.state().copyFrom(snapshot);
        long actual = render(validation, 128);
        int snapshotErrors = expected == actual ? 0 : 1;
        validation.state().copyFrom(snapshot);
        int[] pins = new int[2];
        register(validation, pins, 0, 0x28, 0xf0);
        long control = render(validation, 128);
        validation.state().copyFrom(snapshot);
        register(validation, pins, 0, 0x28, 0x00);
        long changed = render(validation, 128);
        int negativeChanges = control == changed ? 0 : 1;

        System.out.printf(Locale.ROOT,
                "{\"implementation\":\"java-nuked\",\"checksum\":%d," +
                        "\"snapshot_errors\":%d,\"negative_control_changes\":%d," +
                        "\"nanoseconds_per_frame\":%s}%n",
                checksum, snapshotErrors, negativeChanges, Arrays.toString(timings));
    }
}

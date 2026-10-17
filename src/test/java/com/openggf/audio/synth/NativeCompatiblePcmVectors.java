package com.openggf.audio.synth;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared input of the native-compatible PCM component vectors
 * ({@code src/test/resources/audio/native-compatible-pcm/v1/README.md}): one
 * fixed synthetic FM + DAC register stream driven through the production
 * {@link Ym2612Chip} with its own write queue and bus pacing. The raw bus
 * strobes it produces are the input the native-source harness replays into
 * Genesis Plus GX's own {@code ym3438.c} / {@code blip_buf.c} / {@code eq.c}.
 */
final class NativeCompatiblePcmVectors {

    static final String RESOURCE_ROOT = "/audio/native-compatible-pcm/v1/";
    /** NTSC frames every vector covers. */
    static final int NTSC_FRAMES = 8;
    /** 24-cycle chip frames rendered: enough clocks to end {@link #NTSC_FRAMES} frames plus one sweep. */
    static final int CHIP_FRAMES =
            (int) (((long) NTSC_FRAMES * NativeCompatiblePcmOutput.FRAME_MCYCLES
                    / NativeCompatiblePcmOutput.YM_CLOCK_RATIO + 48) / 24);

    /** One voice: algorithm 7, four carriers, fast attack, no decay; port-0 {@code {register, value}} pairs for FM1 (FM2 uses register + 1). */
    private static final int[][] VOICE = {
            {0x30, 0x01}, {0x34, 0x01}, {0x38, 0x02}, {0x3C, 0x01},
            {0x40, 0x20}, {0x44, 0x28}, {0x48, 0x20}, {0x4C, 0x30},
            {0x50, 0x1F}, {0x54, 0x1F}, {0x58, 0x1F}, {0x5C, 0x1F},
            {0x60, 0x00}, {0x64, 0x00}, {0x68, 0x00}, {0x6C, 0x00},
            {0x70, 0x00}, {0x74, 0x00}, {0x78, 0x00}, {0x7C, 0x00},
            {0x80, 0x0F}, {0x84, 0x0F}, {0x88, 0x0F}, {0x8C, 0x0F},
            {0x90, 0x00}, {0x94, 0x00}, {0x98, 0x00}, {0x9C, 0x00},
            {0xB0, 0x07}};

    private NativeCompatiblePcmVectors() {
    }

    /** A PSG data-bus byte at absolute master clock {@code masterClock} of the stage's timeline. */
    record PsgWrite(long masterClock, int value) {
    }

    /** When non-null, receives every PSG write made on a chip with a stage, in order (vector input). */
    static List<PsgWrite> psgRecorder;

    /** A raw YM bus strobe presented before clock {@code cycle}; {@code busPort} is the Nuked port 0..3. */
    record Strobe(long cycle, int busPort, int value) {
    }

    /** Records every physical strobe the chip presents to the core. */
    static final class StrobeRecorder implements ChipWriteObserver {
        final List<Strobe> strobes = new ArrayList<>();

        @Override
        public void onYm2612Write(int port, int register, int value) {
        }

        @Override
        public void onPsgWrite(int value) {
        }

        @Override
        public boolean observesPhysicalWrites() {
            return true;
        }

        @Override
        public void onYm2612BusWrite(long cycle, int busPort, int value, PhysicalWriteOrigin origin) {
            strobes.add(new Strobe(cycle, busPort, value));
        }
    }

    /** A production chip at direct (internal) rate with the stage enabled from power-on. */
    static Ym2612Chip newChip(NativeCompatiblePcmOutput.Config config, ChipWriteObserver observer) {
        Ym2612Chip chip = new Ym2612Chip();
        chip.setOutputSampleRate(Ym2612Chip.getInternalRate());
        if (observer != null) {
            chip.setWriteObserver(observer);
        }
        if (config != null) {
            chip.enableNativeCompatibleOutput(config);
        }
        return chip;
    }

    /**
     * Drives the fixed stream through {@code chip} up to chip frame {@code toFrame}
     * (exclusive), starting at {@code fromFrame}. Event frames are chip-frame
     * indices; writes queue and are applied with the chip's own bus pacing at
     * the next render. Returns the chip's direct-output frames (left, right).
     */
    static void drive(Ym2612Chip chip, int fromFrame, int toFrame, int[] left, int[] right) {
        int frame = fromFrame;
        for (int event : EVENT_FRAMES) {
            if (event < fromFrame || event >= toFrame) {
                continue;
            }
            render(chip, frame, event, left, right);
            frame = event;
            applyEvent(chip, event);
        }
        render(chip, frame, toFrame, left, right);
    }

    private static final int[] EVENT_FRAMES = buildEventFrames();

    private static int[] buildEventFrames() {
        List<Integer> frames = new ArrayList<>(List.of(0, 10, 100, 300, 800, 1500, 1600, 2400, 3000, 3200,
                3500, 4200, 4800, 5600));
        for (int i = 0; i < 20; i++) {
            frames.add(3520 + i * 50);
        }
        frames.addAll(List.of(5000, 6000));
        return frames.stream().mapToInt(Integer::intValue).sorted().distinct().toArray();
    }

    private static void applyEvent(Ym2612Chip chip, int event) {
        switch (event) {
            case 0 -> {
                psg(chip, 0x9F, 0xBF, 0xDF, 0xFF);
                chip.write(0, 0x22, 0x00);
                chip.write(0, 0x27, 0x00);
                chip.write(0, 0x28, 0x00);
                chip.write(0, 0x28, 0x01);
                for (int[] reg : VOICE) {
                    chip.write(0, reg[0], reg[1]);
                    chip.write(0, reg[0] + 1, reg[1]);
                }
                chip.write(0, 0xB4, 0xC0);
                chip.write(0, 0xB5, 0x80);
                chip.write(0, 0xA4, 0x22);
                chip.write(0, 0xA0, 0x69);
                chip.write(0, 0xA5, 0x1A);
                chip.write(0, 0xA1, 0x0F);
            }
            case 10 -> chip.write(0, 0x28, 0xF0);
            case 100 -> psg(chip, 0x8E, 0x0F, 0x92);
            case 800 -> psg(chip, 0xA5, 0x0A, 0xB4);
            case 1600 -> psg(chip, 0xE4, 0xF3);
            case 2400 -> psg(chip, 0xC9, 0x05, 0xE3);
            case 3200 -> psg(chip, 0x98, 0xF6);
            case 4200 -> psg(chip, 0x9F, 0xC2, 0x3F);
            case 4800 -> psg(chip, 0xFF, 0xE6, 0xF1);
            case 5600 -> psg(chip, 0xBF, 0xFF);
            case 300 -> chip.write(0, 0x28, 0xF1);
            case 1500 -> {
                chip.write(0, 0xA4, 0x2A);
                chip.write(0, 0xA0, 0x9B);
            }
            case 3000 -> chip.write(0, 0x28, 0x00);
            case 3500 -> {
                chip.write(1, 0xB6, 0xC0);
                chip.write(0, 0x2B, 0x80);
            }
            case 5000 -> chip.write(0, 0x2B, 0x00);
            case 6000 -> chip.write(0, 0x28, 0x01);
            default -> {
                int step = (event - 3520) / 50;
                chip.write(0, 0x2A, (0x80 + (step % 2 == 0 ? 1 : -1) * (step * 6 + 9)) & 0xFF);
            }
        }
    }

    /** PSG bytes through the production chip's native-compatible tap, recorded with their master clock. */
    private static void psg(Ym2612Chip chip, int... values) {
        for (int value : values) {
            NativeCompatiblePcmOutput stage = chip.nativeCompatibleOutput();
            if (stage != null && psgRecorder != null) {
                psgRecorder.add(new PsgWrite(stage.masterClock(), value));
            }
            chip.nativeCompatiblePsgWrite(value);
        }
    }

    static String psgWritesCsv(List<PsgWrite> writes) {
        StringBuilder csv = new StringBuilder("master_clock,value\n");
        for (PsgWrite write : writes) {
            csv.append(write.masterClock()).append(',').append(write.value()).append('\n');
        }
        return csv.toString();
    }

    private static void render(Ym2612Chip chip, int from, int to, int[] left, int[] right) {
        if (to <= from) {
            return;
        }
        int[] l = new int[to - from];
        int[] r = new int[to - from];
        chip.renderStereo(l, r, to - from);
        if (left != null) {
            System.arraycopy(l, 0, left, from, to - from);
            System.arraycopy(r, 0, right, from, to - from);
        }
    }

    static String strobesCsv(List<Strobe> strobes) {
        StringBuilder csv = new StringBuilder("cycle,bus_port,value\n");
        for (Strobe strobe : strobes) {
            csv.append(strobe.cycle()).append(',').append(strobe.busPort()).append(',')
                    .append(strobe.value()).append('\n');
        }
        return csv.toString();
    }

    static byte[] resource(String name) {
        try (InputStream in = NativeCompatiblePcmVectors.class.getResourceAsStream(RESOURCE_ROOT + name)) {
            if (in == null) {
                throw new IllegalStateException("missing vector resource " + RESOURCE_ROOT + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String resourceText(String name) {
        return new String(resource(name), StandardCharsets.US_ASCII);
    }

    static short[] s16(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        short[] samples = new short[buffer.remaining() / 2];
        buffer.asShortBuffer().get(samples);
        return samples;
    }

    static byte[] s16Bytes(short[] samples, int count) {
        ByteBuffer buffer = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count; i++) {
            buffer.putShort(samples[i]);
        }
        return buffer.array();
    }

    /** Renders the stream with {@code config} and drains every completed native-compatible sample. */
    static short[] renderStage(NativeCompatiblePcmOutput.Config config) {
        Ym2612Chip chip = newChip(config, null);
        drive(chip, 0, CHIP_FRAMES, null, null);
        NativeCompatiblePcmOutput stage = chip.nativeCompatibleOutput();
        short[] out = new short[stage.pendingStereoFrames() * 2];
        stage.drain(out, 0, out.length / 2);
        return out;
    }
}

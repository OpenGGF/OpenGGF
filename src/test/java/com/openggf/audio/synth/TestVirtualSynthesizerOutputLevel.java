package com.openggf.audio.synth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class TestVirtualSynthesizerOutputLevel {
    @Test
    void isolatedYmOutputReachesTheMixerWithoutSyntheticHeadroomAttenuation() {
        double sampleRate = 44_100.0;
        VirtualSynthesizer synth = new VirtualSynthesizer(sampleRate);
        Ym2612Chip reference = new Ym2612Chip();
        reference.setOutputSampleRate(sampleRate);

        byte[] voice = {
                0x32,
                0x71, 0x0D, 0x33, 0x01,
                0x5F, 0x5F, 0x5F, 0x5F,
                0x14, 0x0E, 0x0E, 0x0E,
                0x08, 0x08, 0x08, 0x08,
                0x0F, 0x0F, 0x0F, 0x0F,
                0x1B, 0x16, 0x1F, 0x00
        };
        synth.setInstrument(this, 0, voice);
        reference.setInstrument(0, voice);
        writeNote(synth);
        writeNote(reference);

        int frames = 128;
        int[] expectedLeft = new int[frames];
        int[] expectedRight = new int[frames];
        reference.renderStereo(expectedLeft, expectedRight, frames);
        short[] actual = new short[frames * 2];
        synth.render(actual);

        short[] expected = new short[actual.length];
        for (int frame = 0; frame < frames; frame++) {
            expected[frame * 2] = saturate(expectedLeft[frame]);
            expected[frame * 2 + 1] = saturate(expectedRight[frame]);
        }
        assertArrayEquals(expected, actual,
                "the shared S1/S2/S3K mixer must preserve GPGX YM2612 output level");
    }

    private void writeNote(VirtualSynthesizer synth) {
        synth.writeFm(this, 0, 0xA4, 0x22);
        synth.writeFm(this, 0, 0xA0, 0x69);
        synth.writeFm(this, 0, 0xB4, 0xC0);
        synth.writeFm(this, 0, 0x28, 0xF0);
    }

    private static void writeNote(Ym2612Chip chip) {
        chip.write(0, 0xA4, 0x22);
        chip.write(0, 0xA0, 0x69);
        chip.write(0, 0xB4, 0xC0);
        chip.write(0, 0x28, 0xF0);
    }

    private static short saturate(int sample) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample));
    }
}

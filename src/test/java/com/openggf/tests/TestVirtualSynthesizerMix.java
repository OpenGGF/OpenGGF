package com.openggf.tests;

import com.openggf.audio.synth.VirtualSynthesizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class TestVirtualSynthesizerMix {

    @Test
    public void mixedFmAndPsgOutputRemainsBitExact() {
        VirtualSynthesizer synth = new VirtualSynthesizer();
        configurePsg(synth);
        configureFm(synth);

        short[] buffer = new short[32];
        synth.render(buffer);

        // FM-only control (all four PSG channels muted) is unchanged by the
        // clean-room PSG rewrite: 378, 908, 1373, 1822, 2346, 2829, 3287,
        // 3744, 4236, 4478, 4477, 4480, 4478, 4479, 4479, 4479 per side.
        assertArrayEquals(new short[] {
                378, 378, 914, 914, 1363, 1363, 1854, 1854,
                2325, 2325, 2924, 2924, 3315, 3315, 4282, 4282,
                7374, 7374, 8585, 8585, 8425, 8425, 8580, 8580,
                8507, 8507, 8543, 8543, 8523, 8523, 8519, 8519
        }, buffer);
    }

    private static void configurePsg(VirtualSynthesizer synth) {
        synth.writePsg(TestVirtualSynthesizerMix.class, 0x80);
        synth.writePsg(TestVirtualSynthesizerMix.class, 0x20);
        synth.writePsg(TestVirtualSynthesizerMix.class, 0x90);
    }

    private static void configureFm(VirtualSynthesizer synth) {
        synth.writeFm(TestVirtualSynthesizerMix.class, 0, 0xB0, 0x07);
        synth.writeFm(TestVirtualSynthesizerMix.class, 0, 0xB4, 0xC0);
        synth.writeFm(TestVirtualSynthesizerMix.class, 0, 0xA0, 0x00);
        synth.writeFm(TestVirtualSynthesizerMix.class, 0, 0xA4, 0x22);

        int[] slots = {0x00, 0x04, 0x08, 0x0C};
        for (int slot : slots) {
            synth.writeFm(TestVirtualSynthesizerMix.class, 0, 0x30 + slot, 0x01);
            synth.writeFm(TestVirtualSynthesizerMix.class, 0, 0x40 + slot, 0x00);
            synth.writeFm(TestVirtualSynthesizerMix.class, 0, 0x50 + slot, 0x1F);
            synth.writeFm(TestVirtualSynthesizerMix.class, 0, 0x60 + slot, 0x10);
            synth.writeFm(TestVirtualSynthesizerMix.class, 0, 0x70 + slot, 0x08);
            synth.writeFm(TestVirtualSynthesizerMix.class, 0, 0x80 + slot, 0x05);
        }

        synth.writeFm(TestVirtualSynthesizerMix.class, 0, 0x28, 0xF0);
    }
}

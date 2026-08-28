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

        assertArrayEquals(new short[] {
                757, 757, 1817, 1817, 2746, 2746, 3644, 3644,
                4693, 4693, 5658, 5658, 6574, 6574, 7489, 7489,
                12671, 12671, 13147, 13147, 13138, 13138, 13136, 13136,
                13124, 13124, 13118, 13118, 13109, 13109, 8959, 8959
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

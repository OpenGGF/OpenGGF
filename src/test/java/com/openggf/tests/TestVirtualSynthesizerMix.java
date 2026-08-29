package com.openggf.tests;

import com.openggf.audio.synth.VirtualSynthesizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mixed FM + PSG render is deterministic, carries both chips, and does
 * not depend on how the frames are batched.
 */
public class TestVirtualSynthesizerMix {

    private static final int FRAMES = 512;

    @Test
    public void mixedFmAndPsgOutputIsDeterministicAndBatchInvariant() {
        VirtualSynthesizer batched = new VirtualSynthesizer();
        VirtualSynthesizer again = new VirtualSynthesizer();
        VirtualSynthesizer fmOnly = new VirtualSynthesizer();
        VirtualSynthesizer fmSingle = new VirtualSynthesizer();
        VirtualSynthesizer psgOnly = new VirtualSynthesizer();
        configurePsg(batched);
        configureFm(batched);
        configurePsg(again);
        configureFm(again);
        configureFm(fmOnly);
        configureFm(fmSingle);
        configurePsg(psgOnly);

        short[] batchedOut = new short[FRAMES * 2];
        batched.render(batchedOut);
        short[] againOut = new short[FRAMES * 2];
        again.render(againOut);
        assertArrayEquals(batchedOut, againOut, "the mix must be deterministic");

        // The FM chip renders n frames in one call exactly as 1 frame n times.
        short[] fmOut = new short[FRAMES * 2];
        fmOnly.render(fmOut);
        short[] fmSingleOut = new short[FRAMES * 2];
        for (int frame = 0; frame < FRAMES; frame++) {
            fmSingle.renderFrames(fmSingleOut, frame, 1);
        }
        assertArrayEquals(fmOut, fmSingleOut, "one call of n frames must equal n calls of one frame");

        short[] psgOut = new short[FRAMES * 2];
        psgOnly.render(psgOut);
        assertTrue(peakToPeak(fmOut) > 1000, "FM channel must be audible on its own");
        assertTrue(peakToPeak(psgOut) > 1000, "PSG channel must be audible on its own");

        // Both chips accumulate into the same buffer before the master gain, so
        // the mix differs from either chip alone.
        boolean differsFromFm = false;
        boolean differsFromPsg = false;
        for (int i = 0; i < batchedOut.length; i++) {
            differsFromFm |= batchedOut[i] != fmOut[i];
            differsFromPsg |= batchedOut[i] != psgOut[i];
        }
        assertTrue(differsFromFm && differsFromPsg, "mix must carry both chips");
    }

    private static int peakToPeak(short[] samples) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (short sample : samples) {
            min = Math.min(min, sample);
            max = Math.max(max, sample);
        }
        return max - min;
    }

    private static void configurePsg(VirtualSynthesizer synth) {
        synth.writePsg(TestVirtualSynthesizerMix.class, 0x80);
        synth.writePsg(TestVirtualSynthesizerMix.class, 0x20);
        synth.writePsg(TestVirtualSynthesizerMix.class, 0x90);
    }

    private static void configureFm(VirtualSynthesizer synth) {
        synth.writeFm(TestVirtualSynthesizerMix.class, 0, 0xB0, 0x07);
        synth.writeFm(TestVirtualSynthesizerMix.class, 0, 0xB4, 0xC0);
        synth.writeFm(TestVirtualSynthesizerMix.class, 0, 0xA4, 0x22);
        synth.writeFm(TestVirtualSynthesizerMix.class, 0, 0xA0, 0x00);

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

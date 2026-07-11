package com.openggf.mods;

import com.openggf.io.ModInputLimits;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestPcmResampler {
    @Test
    void linearResamplingIsDeterministicForMonoStereoUpDownAndEqualRates() {
        PcmData mono = PcmData.takeOwnership(8_000, 1, new short[] {0, 8_000, 16_000, 24_000});
        PcmResampler resampler = new PcmResampler();
        PcmData up = resampler.resample(mono, 16_000, ModInputLimits.production());
        assertArrayEquals(new short[] {0, 4_000, 8_000, 12_000, 16_000, 20_000, 24_000, 24_000}, up.copySamples());
        assertArrayEquals(up.copySamples(), resampler.resample(mono, 16_000, ModInputLimits.production()).copySamples());
        PcmData fastMono = PcmData.takeOwnership(16_000, 1, new short[] {0, 8_000, 16_000, 24_000});
        assertArrayEquals(new short[] {0, 16_000},
                resampler.resample(fastMono, 8_000, ModInputLimits.production()).copySamples());
        assertSame(mono, resampler.resample(mono, 8_000, ModInputLimits.production()));
        assertThrows(IllegalArgumentException.class,
                () -> resampler.resample(mono, 7_999, ModInputLimits.production()));
        assertThrows(IllegalArgumentException.class,
                () -> resampler.resample(mono, 192_001, ModInputLimits.production()));

        PcmData stereo = PcmData.takeOwnership(8_000, 2, new short[] {0, 100, 8_000, 8_100});
        assertArrayEquals(new short[] {0, 100, 4_000, 4_100, 8_000, 8_100, 8_000, 8_100},
                resampler.resample(stereo, 16_000, ModInputLimits.production()).copySamples());
    }

    @Test
    void frameAndLoopConversionUseHalfUpRationalRoundingAndValidateBounds() {
        PcmResampler resampler = new PcmResampler();
        assertEquals(3, resampler.convertFrame(1, 8_000, 22_050));
        assertEquals(6, resampler.convertFrame(2, 8_000, 22_050));
        PcmData source = PcmData.takeOwnership(8_000, 1, new short[] {1, 2, 3, 4});
        PcmResampler.LoopBounds bounds = resampler.convertLoop(1, 3, source, 16_000);
        assertEquals(new PcmResampler.LoopBounds(2, 6), bounds);
        assertEquals(new PcmResampler.LoopBounds(2, 8),
                resampler.convertLoop(1, 4, source, 16_000));
        assertThrows(IllegalArgumentException.class, () -> resampler.convertLoop(1, 5, source, 16_000));
        PcmData highRate = PcmData.takeOwnership(192_000, 1, new short[] {1, 2, 3});
        assertThrows(IllegalArgumentException.class,
                () -> resampler.convertLoop(1, 2, highRate, 8_000));
        assertThrows(ArithmeticException.class,
                () -> resampler.convertFrame(Long.MAX_VALUE, 8_000, 192_000));
    }

    @Test
    void ceilOutputFramesPreserveOneFrameAtExtremeDownsampleAndExactEofLoop() {
        PcmResampler resampler = new PcmResampler();
        PcmData mono = PcmData.takeOwnership(192_000, 1, new short[] {123});
        PcmData stereo = PcmData.takeOwnership(192_000, 2, new short[] {123, -456});

        assertArrayEquals(new short[] {123},
                resampler.resample(mono, 8_000, ModInputLimits.production()).copySamples());
        assertArrayEquals(new short[] {123, -456},
                resampler.resample(stereo, 8_000, ModInputLimits.production()).copySamples());
        assertEquals(new PcmResampler.LoopBounds(0, 1),
                resampler.convertLoop(0, 1, mono, 8_000));
    }
}

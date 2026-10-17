package com.openggf.audio.session;

import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.audio.synth.FmCoreSelection;
import com.openggf.audio.synth.NativeCompatiblePcmOutput;
import com.openggf.audio.synth.VirtualSynthesizer;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One presentation of {@code sampleRate / frameRate} samples spans one NTSC hardware frame (896,040 master
 * clocks) of sound-chip time when the device synthesizes at {@link SmpsHardwareFrameBudget#synthesisRate}.
 * Measured with the native-compatible stage's master clock, so the measure is the chip's own time.
 */
class TestSmpsHardwareFrameBudget {

    private static final int SAMPLE_RATE = 48_000;
    private static final int FRAME_RATE = 60;
    private static final int SAMPLES = SAMPLE_RATE / FRAME_RATE;
    private static final long FRAME = SmpsHardwareFrameBudget.NTSC_MASTER_CLOCKS_PER_FRAME;
    /** Upper bound on the chip's resampler look-ahead plus pending bus writes, in master clocks. */
    private static final long LOOKAHEAD_BOUND = 42L * 24 * 16;

    @Test
    void synthesisRateIsDerivedFromTheNtscFrameAndLeavesPalUnchanged() {
        assertEquals(SAMPLE_RATE * 53_693_175.0 / (FRAME_RATE * 896_040.0),
                SmpsHardwareFrameBudget.synthesisRate(SAMPLE_RATE, FRAME_RATE, false));
        assertEquals(44_100.0, SmpsHardwareFrameBudget.synthesisRate(44_100, 50, true), "PAL keeps the sample rate");
    }

    @Test
    void presentationsAdvanceExactlyOneHardwareFrameAcrossARestore() {
        try (OwnedSmpsAudioStream stream = stream(SmpsHardwareFrameBudget.synthesisRate(SAMPLE_RATE, FRAME_RATE,
                false))) {
            short[] pcm = new short[SAMPLES * 2];
            for (int frame = 1; frame <= 300; frame++) {
                stream.serviceAndRenderFrame(pcm, SAMPLES);
                long clock = masterClock(stream);
                assertTrue(clock >= frame * FRAME && clock < frame * FRAME + LOOKAHEAD_BOUND,
                        "presentation " + frame + " left the chip at " + clock + ", not one hardware frame each");
            }
            VirtualSynthesizer.Snapshot snapshot = stream.captureSynthSnapshotForTesting();
            long[] uninterrupted = run(stream, 120);
            short[] uninterruptedPcm = drain(stream);
            stream.restoreSynthSnapshotForTesting(snapshot, null); // no DAC bank was selected
            long[] restored = run(stream, 120);
            assertArrayEquals(uninterrupted, restored, "the budget and its phase carry across a restore");
            assertArrayEquals(uninterruptedPcm, drain(stream));
        }
    }

    @Test
    void wallClockRateFallsShortOfOneHardwareFrameAsMeasured() {
        // Control: the previous configuration (device at the 48 kHz presentation rate) loses a frame in 600.
        try (OwnedSmpsAudioStream stream = stream(SAMPLE_RATE)) {
            short[] pcm = new short[SAMPLES * 2];
            for (int frame = 0; frame < 600; frame++) {
                stream.serviceAndRenderFrame(pcm, SAMPLES);
            }
            assertTrue(masterClock(stream) < 600 * FRAME, "1/60 s per presentation is 894,886 master clocks");
        }
    }

    private static OwnedSmpsAudioStream stream(double synthesisRate) {
        return new OwnedSmpsAudioStream("budget-test", 0,
                new SmpsPhysicalDevice.Settings(synthesisRate, false, FmCoreSelection.ACCURATE,
                        NativeCompatiblePcmOutput.Config.withFilter(NativeCompatiblePcmOutput.Filter.NONE)),
                LegacyCompatibilitySmpsPhysicalPolicy.INSTANCE, ChipWriteObserver.NONE);
    }

    private static long[] run(OwnedSmpsAudioStream stream, int frames) {
        short[] pcm = new short[SAMPLES * 2];
        long[] clocks = new long[frames];
        for (int frame = 0; frame < frames; frame++) {
            stream.serviceAndRenderFrame(pcm, SAMPLES);
            clocks[frame] = masterClock(stream);
        }
        return clocks;
    }

    private static long masterClock(OwnedSmpsAudioStream stream) {
        return stream.nativeCompatibleMasterClockForTesting();
    }

    private static short[] drain(OwnedSmpsAudioStream stream) {
        short[] out = new short[2 * 4 * 8192];
        int frames = stream.drainNativeCompatiblePcm(out, 0, out.length / 2);
        return Arrays.copyOf(out, frames * 2);
    }
}

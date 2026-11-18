package com.openggf.audio.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestAudioFrameClock {
    @Test
    void divisibleSampleRateProducesSameCountEveryFrame() {
        AudioFrameClock clock = new AudioFrameClock(48_000, 60);

        assertEquals(800, clock.samplesForNextFrame());
        assertEquals(800, clock.samplesForNextFrame());
        assertEquals(1_600, clock.totalSamplesProduced());
    }

    @Test
    void fractionalFramePeekIsRepeatableAndProductionCarriesTheRemainder() {
        AudioFrameClock clock = new AudioFrameClock(32_000, 60);

        // At 32 kHz and 60 Hz the three-frame cadence is 533, 533, 534.
        int[] cadence = {533, 533, 534};
        for (int cycle = 0; cycle < 20; cycle++) {
            for (int samples : cadence) {
                long before = clock.totalSamplesProduced();
                assertEquals(samples, clock.peekSamplesForNextFrame());
                assertEquals(samples, clock.peekSamplesForNextFrame());
                assertEquals(before, clock.totalSamplesProduced());
                assertEquals(samples, clock.samplesForNextFrame());
                assertEquals(before + samples, clock.totalSamplesProduced());
            }
        }

        assertEquals(32_000, clock.totalSamplesProduced());
    }

    @Test
    void restoreReinstatesTotalAndRemainder() {
        AudioFrameClock clock = new AudioFrameClock(44_100, 64);
        clock.samplesForNextFrame();
        AudioFrameClock.Snapshot snapshot = clock.captureSnapshot();
        clock.samplesForNextFrame();

        clock.restoreSnapshot(snapshot);

        assertEquals(snapshot.totalSamplesProduced(), clock.totalSamplesProduced());
        assertEquals(snapshot.remainder(), clock.remainder());
        assertEquals(689, clock.samplesForNextFrame());
    }
}

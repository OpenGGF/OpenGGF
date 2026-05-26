package com.openggf.audio.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestAudioFrameClockSnapshotRoundTrip {

    @Test
    void captureRestoreReproducesSamplesForNextFrameSequence() {
        AudioFrameClock clock = new AudioFrameClock(48000, 60);
        // Advance to a non-trivial state with a non-zero remainder.
        for (int i = 0; i < 17; i++) {
            clock.samplesForNextFrame();
        }
        AudioFrameClock.Snapshot snapshot = clock.captureSnapshot();
        long totalAtSnapshot = clock.totalSamplesProduced();
        int remainderAtSnapshot = clock.remainder();

        // Walk forward and capture the sequence.
        int[] forwardSequence = new int[10];
        for (int i = 0; i < 10; i++) {
            forwardSequence[i] = clock.samplesForNextFrame();
        }

        // Restore back to the snapshot and re-run.
        clock.restoreSnapshot(snapshot);
        assertEquals(totalAtSnapshot, clock.totalSamplesProduced());
        assertEquals(remainderAtSnapshot, clock.remainder());

        for (int i = 0; i < 10; i++) {
            assertEquals(forwardSequence[i], clock.samplesForNextFrame(),
                    "sample count for frame " + i + " must match across restore");
        }
    }

    @Test
    void restoreRejectsMismatchedRate() {
        AudioFrameClock clock = new AudioFrameClock(48000, 60);
        AudioFrameClock.Snapshot mismatched =
                new AudioFrameClock.Snapshot(44100, 60, 0, 0);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> clock.restoreSnapshot(mismatched));
    }
}

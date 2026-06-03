package com.openggf.audio.runtime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StreamBackedRuntimeProducedCountTest {

    private static StreamBackedDeterministicAudioRuntime newRuntime() {
        return new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(48000, 60),
                new AudioOutputFifo(48000 * 2),
                new PcmHistoryRing(48000),
                1);
    }

    @Test
    void recordsProducedFrameCountForNormalAdvance() {
        StreamBackedDeterministicAudioRuntime runtime = newRuntime();
        runtime.advanceFrame(1, FrameAudioMode.NORMAL);
        assertEquals(800, runtime.lastProducedFrames());

        short[] target = new short[800 * 2];
        int drained = runtime.drainPcm(target, runtime.lastProducedFrames());
        assertEquals(800, drained, "drain returns exactly the produced frame count");
    }
}

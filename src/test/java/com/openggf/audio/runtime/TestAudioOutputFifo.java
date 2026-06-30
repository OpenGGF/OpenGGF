package com.openggf.audio.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestAudioOutputFifo {
    @Test
    void drainsFramesInWriteOrderAndReportsAvailableFrames() {
        AudioOutputFifo fifo = new AudioOutputFifo(4);

        fifo.write(new short[] {1, 2, 3, 4}, 2);
        short[] target = new short[4];

        int drained = fifo.drain(target, 2);

        assertEquals(2, drained);
        assertArrayEquals(new short[] {1, 2, 3, 4}, target);
        assertEquals(0, fifo.availableFrames());
    }

    @Test
    void underrunZeroFillsMissingFramesAndIncrementsCounter() {
        AudioOutputFifo fifo = new AudioOutputFifo(4);
        fifo.write(new short[] {7, 8}, 1);
        short[] target = new short[] {-1, -1, -1, -1, -1, -1};

        int drained = fifo.drain(target, 3);

        assertEquals(1, drained);
        assertArrayEquals(new short[] {7, 8, 0, 0, 0, 0}, target);
        assertEquals(1, fifo.underruns());
    }

    @Test
    void boundedCapacityDropsOverflowTailAndIncrementsCounter() {
        AudioOutputFifo fifo = new AudioOutputFifo(2);

        int written = fifo.write(new short[] {1, 2, 3, 4, 5, 6}, 3);
        short[] target = new short[6];
        int drained = fifo.drain(target, 3);

        assertEquals(2, written);
        assertEquals(2, drained);
        assertArrayEquals(new short[] {1, 2, 3, 4, 0, 0}, target);
        assertEquals(1, fifo.overruns());
    }

    @Test
    void flushClearsQueuedFramesWithoutResettingCounters() {
        AudioOutputFifo fifo = new AudioOutputFifo(1);
        fifo.write(new short[] {1, 2, 3, 4}, 2);

        fifo.flush();

        assertEquals(0, fifo.availableFrames());
        assertEquals(1, fifo.overruns());
    }
}

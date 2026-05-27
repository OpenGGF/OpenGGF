package com.openggf.audio.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestPcmHistoryRing {
    @Test
    void reverseCursorReadsNewestFramesFirst() {
        PcmHistoryRing history = new PcmHistoryRing(4);
        history.write(new short[] {1, 10, 2, 20, 3, 30}, 3);

        short[] target = new short[4];
        int read = history.createReverseCursor().readPrevious(target, 2);

        assertEquals(2, read);
        assertArrayEquals(new short[] {3, 30, 2, 20}, target);
    }

    @Test
    void boundedHistoryDropsOldestFrames() {
        PcmHistoryRing history = new PcmHistoryRing(2);
        history.write(new short[] {1, 10, 2, 20, 3, 30}, 3);

        short[] target = new short[6];
        int read = history.createReverseCursor().readPrevious(target, 3);

        assertEquals(2, read);
        assertArrayEquals(new short[] {3, 30, 2, 20, 0, 0}, target);
    }

    @Test
    void cursorContinuesFromPreviousReverseRead() {
        PcmHistoryRing history = new PcmHistoryRing(4);
        history.write(new short[] {1, 10, 2, 20, 3, 30}, 3);
        PcmHistoryRing.ReverseCursor cursor = history.createReverseCursor();

        short[] first = new short[2];
        short[] second = new short[2];

        assertEquals(1, cursor.readPrevious(first, 1));
        assertEquals(1, cursor.readPrevious(second, 1));
        assertArrayEquals(new short[] {3, 30}, first);
        assertArrayEquals(new short[] {2, 20}, second);
    }

    @Test
    void committingReverseCursorMakesNextReverseSessionResumeFromConsumedPosition() {
        PcmHistoryRing history = new PcmHistoryRing(4);
        history.write(new short[] {1, 10, 2, 20, 3, 30, 4, 40}, 4);
        PcmHistoryRing.ReverseCursor firstCursor = history.createReverseCursor();
        short[] first = new short[4];

        assertEquals(2, firstCursor.readPrevious(first, 2));
        history.commitReverseCursor(firstCursor);

        short[] second = new short[2];
        assertEquals(1, history.createReverseCursor().readPrevious(second, 1));
        assertArrayEquals(new short[] {2, 20}, second);
    }

    @Test
    void clearInvalidatesHistoryForNewCursors() {
        PcmHistoryRing history = new PcmHistoryRing(4);
        history.write(new short[] {1, 10, 2, 20}, 2);

        history.clear();

        short[] target = new short[2];
        assertEquals(0, history.createReverseCursor().readPrevious(target, 1));
        assertArrayEquals(new short[] {0, 0}, target);
    }

    @Test
    void capacityFramesFor_timeMode_multipliesSampleRateBySeconds() {
        assertEquals(44100 * 10, PcmHistoryRing.capacityFramesFor(44100, "time", 10, 2));
        assertEquals(48000 * 5, PcmHistoryRing.capacityFramesFor(48000, "TIME", 5, 99));
    }

    @Test
    void capacityFramesFor_defaultLimitTypeFallsBackToTime() {
        assertEquals(44100 * 10, PcmHistoryRing.capacityFramesFor(44100, null, 10, 2));
        assertEquals(44100 * 10, PcmHistoryRing.capacityFramesFor(44100, "", 10, 2));
        assertEquals(44100 * 10, PcmHistoryRing.capacityFramesFor(44100, "unknown", 10, 2));
    }

    @Test
    void capacityFramesFor_sizeMode_divides16BitStereoBytesByFrame() {
        // 1 MB / (2 channels * 2 bytes per short) = 262144 frames per MB
        assertEquals(262144, PcmHistoryRing.capacityFramesFor(44100, "size", 999, 1));
        assertEquals(524288, PcmHistoryRing.capacityFramesFor(44100, "size", 999, 2));
        assertEquals(262144, PcmHistoryRing.capacityFramesFor(44100, "SIZE", 999, 1));
    }

    @Test
    void capacityFramesFor_clampsNonPositiveInputs() {
        // seconds and sizeMB both clamp to at least 1 before computing capacity
        assertEquals(44100, PcmHistoryRing.capacityFramesFor(44100, "time", 0, 2));
        assertEquals(44100, PcmHistoryRing.capacityFramesFor(44100, "time", -5, 2));
        assertEquals(262144, PcmHistoryRing.capacityFramesFor(44100, "size", 10, 0));
        assertEquals(262144, PcmHistoryRing.capacityFramesFor(44100, "size", 10, -1));
    }
}

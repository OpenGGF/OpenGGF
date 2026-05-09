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
    void expandableHistoryRetainsFramesBeyondInitialCapacity() {
        PcmHistoryRing history = PcmHistoryRing.expandable(2, 4);
        history.write(new short[] {1, 10, 2, 20, 3, 30, 4, 40}, 4);

        short[] target = new short[8];
        int read = history.createReverseCursor().readPrevious(target, 4);

        assertEquals(4, read);
        assertArrayEquals(new short[] {4, 40, 3, 30, 2, 20, 1, 10}, target);
    }

    @Test
    void expandableHistoryDropsOldestFramesAfterMaxCapacity() {
        PcmHistoryRing history = PcmHistoryRing.expandable(2, 4);
        history.write(new short[] {
                1, 10,
                2, 20,
                3, 30,
                4, 40,
                5, 50
        }, 5);

        short[] target = new short[10];
        int read = history.createReverseCursor().readPrevious(target, 5);

        assertEquals(4, read);
        assertArrayEquals(new short[] {5, 50, 4, 40, 3, 30, 2, 20, 0, 0}, target);
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
}

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
    void prependBackwardExtendsReadableWindow() {
        PcmHistoryRing ring = new PcmHistoryRing(16);
        // Write 4 forward frames: samples 0..7.
        short[] forward = {0, 1, 2, 3, 4, 5, 6, 7};
        ring.write(forward, 4);

        PcmHistoryRing.ReverseCursor cursor = ring.createReverseCursor();
        // Read 2 frames to advance the cursor past those slots.
        short[] readBack = new short[4];
        cursor.readPrevious(readBack, 2);
        // Now cursor.nextReadableFrame=1, oldestReadableFrame=0. Two slots consumed.

        // Prepend 4 older frames at logical indices [-4, -3, -2, -1] (adjacency
        // requirement: startAudioFrame + frames == cursor.oldestReadableFrame).
        short[] older = {-1, -10, -2, -20, -3, -30, -4, -40};
        ring.prependBackward(-4L, cursor, older, 4);

        // Now reverse-read the remaining 6 frames: [1 right after the read],
        // [0], then [-1], [-2], [-3], [-4].
        short[] tail = new short[12];
        int read = cursor.readPrevious(tail, 6);
        assertEquals(6, read);
        assertArrayEquals(new short[] {
                2, 3,        // forward frame 1
                0, 1,        // forward frame 0
                -1, -10,     // prepended frame -1
                -2, -20,     // prepended frame -2
                -3, -30,     // prepended frame -3
                -4, -40      // prepended frame -4
        }, tail);
    }

    @Test
    void prependBackwardRejectsNonAdjacentStart() {
        PcmHistoryRing ring = new PcmHistoryRing(16);
        ring.write(new short[]{0, 0, 0, 0}, 2);
        PcmHistoryRing.ReverseCursor cursor = ring.createReverseCursor();
        short[] payload = new short[4];
        // oldestReadableFrame is 0. Adjacency requires startAudioFrame + frames == 0.
        // Passing startAudioFrame=-3, frames=2 -> -3+2 = -1 != 0.
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> ring.prependBackward(-3L, cursor, payload, 2));
    }

    @Test
    void prependBackwardRejectsSpanExceedingCapacity() {
        PcmHistoryRing ring = new PcmHistoryRing(8);
        ring.write(new short[]{0, 0, 0, 0, 0, 0, 0, 0}, 4);
        PcmHistoryRing.ReverseCursor cursor = ring.createReverseCursor();
        // unread = nextReadableFrame - oldestReadableFrame + 1 = 4. Capacity = 8.
        // Adding 5 more frames at the front makes total span 4 + 5 = 9 > 8.
        short[] payload = new short[10];
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> ring.prependBackward(cursor.oldestReadableFrame() - 5L, cursor, payload, 5));
    }
}

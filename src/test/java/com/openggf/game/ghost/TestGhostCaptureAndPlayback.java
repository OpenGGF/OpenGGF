package com.openggf.game.ghost;

import com.openggf.ghost.GhostFrame;
import com.openggf.ghost.GhostFrameCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestGhostCaptureAndPlayback {
    @Test
    void captureProducesDecodableFrameStream() {
        GhostCaptureBuffer buf = new GhostCaptureBuffer();
        buf.capture(100, 200, 5, true, false, 2, false, false);
        buf.capture(104, 199, 6, true, false, 2, false, true);
        assertEquals(2, buf.frameCount());
        byte[] data = buf.toFrameData();
        assertEquals(2 * GhostFrameCodec.BYTES, data.length);
        GhostFrame second = GhostFrameCodec.decode(data, GhostFrameCodec.BYTES);
        assertEquals(new GhostFrame(104, 199, 6, true, false, true, 2, false), second);
    }

    @Test
    void resetClearsBuffer() {
        GhostCaptureBuffer buf = new GhostCaptureBuffer();
        buf.capture(1, 1, 1, false, false, 0, false, false);
        buf.reset();
        assertEquals(0, buf.frameCount());
    }

    @Test
    void cursorIsSpawnAnchoredAndHoldsFinalPose() {
        GhostCaptureBuffer buf = new GhostCaptureBuffer();
        buf.capture(10, 0, 0, false, false, 0, false, false);
        buf.capture(20, 0, 0, false, false, 0, false, false);
        buf.capture(30, 0, 0, false, false, 0, false, true);
        GhostRecording rec = new GhostRecording(
                new GhostHeader(1, "s3k", 0, 0, "sonic", "x", 0, 2, new int[0], new byte[32]),
                buf.toFrameData());
        GhostPlaybackCursor cursor = new GhostPlaybackCursor(rec);
        assertEquals(10, cursor.frameFor(0).x());
        assertEquals(30, cursor.frameFor(2).x());
        assertEquals(30, cursor.frameFor(500).x()); // hold last pose
        assertFalse(cursor.isFinishedAt(1));
        assertTrue(cursor.isFinishedAt(2));
    }
}

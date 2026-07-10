package com.openggf.net.client;

import com.openggf.ghost.GhostFrame;
import com.openggf.ghost.GhostFrameCodec;
import com.openggf.net.protocol.GhostPackets;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestGhostStreamPublisher {
    private final List<byte[]> sent = new ArrayList<>();
    private final GhostStreamPublisher publisher = new GhostStreamPublisher(sent::add);

    private static GhostFrame frame(int x) {
        return new GhostFrame(x, 100, 1, false, false, false, 2, false);
    }

    @Test
    void batchesThreeFramesPerPacketWithRunningIndices() {
        publisher.beginAttempt(1);
        for (int i = 0; i < 7; i++) {
            publisher.onFrame(frame(100 + i));
        }
        assertEquals(2, sent.size());
        GhostPackets.FramesBatch first = GhostPackets.decodeFrames(sent.get(0));
        GhostPackets.FramesBatch second = GhostPackets.decodeFrames(sent.get(1));
        assertEquals(0, first.startFrameIndex());
        assertEquals(3, second.startFrameIndex());
        assertEquals(1, second.attemptId());
        publisher.finishAttempt();
        assertEquals(3, sent.size());
        assertEquals(1, GhostPackets.decodeFrames(sent.get(2)).frameCount());
        assertEquals(7, publisher.framesPublished());
    }

    @Test
    void streamHashCoversExactFrameBytes() throws Exception {
        publisher.beginAttempt(3);
        publisher.onFrame(frame(1));
        publisher.onFrame(frame(2));
        publisher.finishAttempt();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] expectedBytes = new byte[2 * GhostFrameCodec.BYTES];
        GhostFrameCodec.encode(frame(1), expectedBytes, 0);
        GhostFrameCodec.encode(frame(2), expectedBytes, GhostFrameCodec.BYTES);
        assertArrayEquals(digest.digest(expectedBytes), publisher.streamHashSha256());
    }

    @Test
    void abandonDropsPartialBatchAndNewAttemptRestartsIndices() {
        publisher.beginAttempt(1);
        publisher.onFrame(frame(1));
        publisher.abandonAttempt();
        assertTrue(sent.isEmpty());
        publisher.beginAttempt(2);
        publisher.onFrame(frame(1));
        publisher.onFrame(frame(2));
        publisher.onFrame(frame(3));
        GhostPackets.FramesBatch batch = GhostPackets.decodeFrames(sent.get(0));
        assertEquals(2, batch.attemptId());
        assertEquals(0, batch.startFrameIndex());
        assertEquals(2, publisher.currentAttemptId());
    }

    @Test
    void enforcesIncreasingAttemptAndActiveLifecycle() {
        assertThrows(IllegalStateException.class, () -> publisher.onFrame(frame(1)));
        publisher.beginAttempt(2);
        assertThrows(IllegalStateException.class, () -> publisher.beginAttempt(2));
        publisher.abandonAttempt();
        assertThrows(IllegalArgumentException.class, () -> publisher.beginAttempt(2));
        assertThrows(IllegalStateException.class, publisher::finishAttempt);
        publisher.beginAttempt(3);
        publisher.onFrame(frame(1));
        publisher.finishAttempt();
        assertThrows(IllegalStateException.class, publisher::finishAttempt);
    }
}

package com.openggf.net.client;

import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.protocol.GhostPackets;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Upstream lobby presence: three-frame batches per stream, and a new stream at a teleport. */
class TestLobbyPosePublisher {
    private static GhostFrame at(int x) {
        return new GhostFrame(x, 0x200, 1, false, false, false, 2, false);
    }

    @Test
    void batchesThreeFramesAndStartsANewStreamAfterATeleport() {
        List<byte[]> sent = new ArrayList<>();
        LobbyPosePublisher publisher = new LobbyPosePublisher(sent::add);
        publisher.onFrame(at(0x100));
        assertTrue(sent.isEmpty(), "nothing streams before a lobby level is ready");
        publisher.beginStream();
        for (int i = 0; i < 4; i++) publisher.onFrame(at(0x100 + i));
        assertEquals(1, sent.size());
        GhostPackets.FramesBatch first = GhostPackets.decodeLobbyFrames(sent.getFirst());
        assertEquals(0, first.attemptId());
        assertEquals(0, first.startFrameIndex());
        assertEquals(3, first.frameCount());

        publisher.onFrame(at(0x900)); // a respawn: the host would refuse this jump in one stream
        assertEquals(2, sent.size(), "the partial batch is flushed first");
        GhostPackets.FramesBatch tail = GhostPackets.decodeLobbyFrames(sent.get(1));
        assertEquals(0, tail.attemptId());
        assertEquals(3, tail.startFrameIndex());
        assertEquals(1, tail.frameCount());
        assertEquals(1, publisher.streamId());
        publisher.end();
        GhostPackets.FramesBatch fresh = GhostPackets.decodeLobbyFrames(sent.getLast());
        assertEquals(1, fresh.attemptId());
        assertEquals(0, fresh.startFrameIndex(), "the new stream starts at frame 0");
        assertFalse(publisher.isActive());
        publisher.onFrame(at(0x900));
        assertEquals(3, sent.size(), "nothing streams once ended");
    }
}

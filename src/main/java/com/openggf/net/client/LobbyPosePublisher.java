package com.openggf.net.client;

import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.ghost.GhostFrameCodec;
import com.openggf.net.hub.TrackValidationProfile;
import com.openggf.net.protocol.GhostPackets;

/**
 * Upstream lobby presence between rounds: batches the local player's lobby-level poses three
 * frames to a packet ({@link GhostPackets#TYPE_LOBBY_FRAMES}). Each lobby-level visit is one
 * stream; a jump the host would refuse (a respawn or other teleport) starts a new stream, so
 * receivers restart that player's playback instead of the host dropping it. Nothing is hashed or
 * scored: presence is cosmetic.
 */
@com.openggf.game.ModApi
public final class LobbyPosePublisher {
    private final GhostStreamPublisher.BinarySender sender;
    private final byte[] batch = new byte[GhostPackets.MAX_UPSTREAM_FRAMES_PER_PACKET * GhostFrameCodec.BYTES];
    private int batchCount;
    private int streamId = -1;
    private int nextFrameIndex;
    private GhostFrame previous;
    private boolean active;

    public LobbyPosePublisher(GhostStreamPublisher.BinarySender sender) {
        this.sender = java.util.Objects.requireNonNull(sender, "sender");
    }

    /** Starts a new stream (a lobby level was loaded); the next frame is its frame 0. */
    public void beginStream() {
        streamId++;
        nextFrameIndex = 0;
        batchCount = 0;
        previous = null;
        active = true;
    }

    /** Adds one pose; ignored while no stream is active. */
    public void onFrame(GhostFrame frame) {
        if (!active) {
            return;
        }
        if (previous != null && (Math.abs(frame.x() - previous.x())
                > TrackValidationProfile.GLOBAL_SPEED_CEILING_PX_PER_FRAME
                || Math.abs(frame.y() - previous.y())
                > TrackValidationProfile.GLOBAL_SPEED_CEILING_PX_PER_FRAME)) {
            flush();
            beginStream();
        }
        GhostFrameCodec.encode(frame, batch, batchCount * GhostFrameCodec.BYTES);
        batchCount++;
        previous = frame;
        if (batchCount == GhostPackets.MAX_UPSTREAM_FRAMES_PER_PACKET) {
            flush();
        }
    }

    /** Sends any partial batch and stops streaming until the next {@link #beginStream()}. */
    public void end() {
        if (active) {
            flush();
        }
        active = false;
    }

    public boolean isActive() {
        return active;
    }

    /** The current stream's id (-1 before the first). */
    public int streamId() {
        return streamId;
    }

    private void flush() {
        if (batchCount == 0) {
            return;
        }
        byte[] frameData = new byte[batchCount * GhostFrameCodec.BYTES];
        System.arraycopy(batch, 0, frameData, 0, frameData.length);
        sender.sendBinary(GhostPackets.encodeLobbyFrames(streamId, nextFrameIndex, frameData));
        nextFrameIndex += batchCount;
        batchCount = 0;
    }
}

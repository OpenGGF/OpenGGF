package com.openggf.net.client;

import com.openggf.ghost.GhostFrame;
import com.openggf.ghost.GhostFrameCodec;
import com.openggf.net.protocol.GhostPackets;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Three-frame upstream batching plus a SHA-256 digest of the exact frame stream. */
public final class GhostStreamPublisher {
    @FunctionalInterface
    public interface BinarySender {
        void sendBinary(byte[] data);
    }

    private final BinarySender sender;
    private final byte[] batch = new byte[
            GhostPackets.MAX_UPSTREAM_FRAMES_PER_PACKET * GhostFrameCodec.BYTES];
    private int batchCount;
    private int attemptId;
    private int lastAttemptId;
    private int nextFrameIndex;
    private MessageDigest digest = newDigest();
    private byte[] sealedHash;
    private boolean active;

    public GhostStreamPublisher(BinarySender sender) {
        this.sender = sender;
    }

    public void beginAttempt(int newAttemptId) {
        if (active) {
            throw new IllegalStateException("attempt already active");
        }
        if (newAttemptId <= lastAttemptId) {
            throw new IllegalArgumentException("attempt ids must strictly increase");
        }
        attemptId = newAttemptId;
        lastAttemptId = newAttemptId;
        nextFrameIndex = 0;
        batchCount = 0;
        digest = newDigest();
        sealedHash = null;
        active = true;
    }

    public void onFrame(GhostFrame frame) {
        requireActive();
        int offset = batchCount * GhostFrameCodec.BYTES;
        GhostFrameCodec.encode(frame, batch, offset);
        digest.update(batch, offset, GhostFrameCodec.BYTES);
        batchCount++;
        if (batchCount == GhostPackets.MAX_UPSTREAM_FRAMES_PER_PACKET) {
            flush();
        }
    }

    public void finishAttempt() {
        requireActive();
        flush();
        sealedHash = digest.digest();
        active = false;
    }

    public void abandonAttempt() {
        requireActive();
        batchCount = 0;
        active = false;
    }

    public byte[] streamHashSha256() {
        if (sealedHash == null) {
            throw new IllegalStateException("attempt not finished");
        }
        return sealedHash.clone();
    }

    public int framesPublished() {
        return nextFrameIndex;
    }

    public int currentAttemptId() {
        return attemptId;
    }

    private void flush() {
        if (batchCount == 0) {
            return;
        }
        byte[] frameData = new byte[batchCount * GhostFrameCodec.BYTES];
        System.arraycopy(batch, 0, frameData, 0, frameData.length);
        sender.sendBinary(GhostPackets.encodeFrames(attemptId, nextFrameIndex, frameData));
        nextFrameIndex += batchCount;
        batchCount = 0;
    }

    private void requireActive() {
        if (!active) {
            throw new IllegalStateException("no active attempt");
        }
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

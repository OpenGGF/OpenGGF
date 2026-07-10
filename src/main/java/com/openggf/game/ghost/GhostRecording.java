package com.openggf.game.ghost;

import com.openggf.ghost.GhostFrame;
import com.openggf.ghost.GhostFrameCodec;

public final class GhostRecording {
    private final GhostHeader header;
    private final byte[] frameData;

    public GhostRecording(GhostHeader header, byte[] frameData) {
        if (frameData.length < GhostFrameCodec.BYTES || frameData.length % GhostFrameCodec.BYTES != 0) {
            throw new IllegalArgumentException(
                    "frameData must be a non-empty multiple of " + GhostFrameCodec.BYTES + " bytes");
        }
        this.header = header;
        this.frameData = frameData.clone();   // defensive: recording is immutable
    }

    public GhostHeader header() { return header; }
    public byte[] frameData() { return frameData.clone(); }
    public int frameCount() { return frameData.length / GhostFrameCodec.BYTES; }

    /** Clamps past-the-end reads to the final frame so playback holds the finish pose. */
    public GhostFrame frameAt(int index) {
        int clamped = Math.min(Math.max(index, 0), frameCount() - 1);
        return GhostFrameCodec.decode(frameData, clamped * GhostFrameCodec.BYTES);
    }
}

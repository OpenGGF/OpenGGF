package com.openggf.game.ghost;

import java.io.ByteArrayOutputStream;

/** Per-frame render-state sampler for the local player's run (main spec §3/§7). */
public final class GhostCaptureBuffer {
    private final ByteArrayOutputStream frames = new ByteArrayOutputStream();
    private final byte[] scratch = new byte[GhostFrameCodec.BYTES];
    private int frameCount;

    public void capture(int centreX, int centreY, int mappingFrame, boolean hFlip, boolean vFlip,
                        int priorityBucket, boolean highPriority, boolean finished) {
        GhostFrameCodec.encode(new GhostFrame(centreX & 0xFFFF, centreY & 0xFFFF, mappingFrame,
                hFlip, vFlip, finished, priorityBucket, highPriority), scratch, 0);
        frames.write(scratch, 0, scratch.length);
        frameCount++;
    }

    public int frameCount() { return frameCount; }
    public byte[] toFrameData() { return frames.toByteArray(); }

    public void reset() {
        frames.reset();
        frameCount = 0;
    }
}

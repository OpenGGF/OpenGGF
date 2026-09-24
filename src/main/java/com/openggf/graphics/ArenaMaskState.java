package com.openggf.graphics;

import java.nio.ByteBuffer;

/** Explicit level-owned presentation state. No camera, collision, or gameplay RNG writes.
 * Call advance once per executed gameplay pass, never from rendering. The owning
 * level includes these bytes in its rewind state; fresh level instances start clear.
 * One event coordinator owns activation/release for a level, including boss callers.
 */
public final class ArenaMaskState {
    public static final int SNAPSHOT_BYTES = 4 * Integer.BYTES;
    public static final int FADE_FRAMES = 45;
    private boolean requested;
    private int activeWidth = 320;
    private int fade;
    private int noiseFrame;

    public void activate(int width) {
        if (width <= 0) throw new IllegalArgumentException("Active arena width must be positive");
        activeWidth = width;
        requested = true;
    }
    public void release() { requested = false; }
    public void advance() {
        fade = Math.max(0, Math.min(FADE_FRAMES, fade + (requested ? 1 : -1)));
        noiseFrame = (noiseFrame + 1) & 0xFFFF;
    }
    public int activeWidth() { return activeWidth; }
    public int noiseFrame() { return noiseFrame; }
    public boolean requested() { return requested; }
    public float intensity() {
        float t = fade / (float) FADE_FRAMES;
        return t * t * (3 - 2 * t);
    }
    public void writeTo(ByteBuffer buffer) {
        buffer.putInt(requested ? 1 : 0).putInt(activeWidth).putInt(fade).putInt(noiseFrame);
    }
    public void readFrom(ByteBuffer buffer) {
        requested = buffer.getInt() != 0;
        activeWidth = buffer.getInt(); fade = buffer.getInt(); noiseFrame = buffer.getInt();
    }
}

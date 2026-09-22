package com.openggf.game.sonic3k.runtime;

import java.nio.ByteBuffer;

/** LRZ3 screen and special-event words, captured by the owning zone runtime. */
public final class LrzBossActState {
    static final int CAPTURE_BYTES = 9 * Integer.BYTES;
    private boolean initialized;
    private int foregroundRoutine;
    private int foregroundRequest;
    private int autoscrollRoutine = -1;
    private int autoscrollDelay;
    private int cameraFractionX;
    private int cameraFractionY;
    private int chunkEditX;
    private int chunkEditY;

    public boolean initialized() { return initialized; }
    public void markInitialized() { initialized = true; }
    public int foregroundRoutine() { return foregroundRoutine; }
    public void setForegroundRoutine(int value) { foregroundRoutine = value; }
    public boolean consumeForegroundRequest() {
        boolean result = foregroundRequest != 0;
        foregroundRequest = 0;
        return result;
    }
    public void requestForegroundAdvance() { foregroundRequest = -1; }
    public int autoscrollRoutine() { return autoscrollRoutine; }
    public void setAutoscrollRoutine(int value) { autoscrollRoutine = value; }
    public int autoscrollDelay() { return autoscrollDelay; }
    public void setAutoscrollDelay(int value) { autoscrollDelay = value; }
    public int cameraFractionX() { return cameraFractionX; }
    public int cameraFractionY() { return cameraFractionY; }
    public void setCameraFractions(int x, int y) {
        cameraFractionX = x & 0xFFFF;
        cameraFractionY = y & 0xFFFF;
    }
    public int chunkEditX() { return chunkEditX; }
    public int chunkEditY() { return chunkEditY; }
    public void requestChunkEdit(int x, int y) { chunkEditX = x & 0xFFFF; chunkEditY = y & 0xFFFF; }
    public void clearChunkEdit() { chunkEditX = 0; }

    void captureTo(ByteBuffer buffer) {
        buffer.putInt(initialized ? 1 : 0).putInt(foregroundRoutine).putInt(foregroundRequest)
                .putInt(autoscrollRoutine).putInt(autoscrollDelay).putInt(cameraFractionX)
                .putInt(cameraFractionY).putInt(chunkEditX).putInt(chunkEditY);
    }
    void restoreFrom(ByteBuffer buffer) {
        initialized = buffer.getInt() != 0;
        foregroundRoutine = buffer.getInt(); foregroundRequest = buffer.getInt();
        autoscrollRoutine = buffer.getInt(); autoscrollDelay = buffer.getInt();
        cameraFractionX = buffer.getInt(); cameraFractionY = buffer.getInt();
        chunkEditX = buffer.getInt(); chunkEditY = buffer.getInt();
    }
}

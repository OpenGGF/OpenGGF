package com.openggf.game.sonic3k.runtime;

import java.nio.ByteBuffer;

/** LRZ3 screen and special-event words, captured by the owning zone runtime. */
public final class LrzBossActState {
    static final int CAPTURE_BYTES = 14 * Integer.BYTES + 192;
    private boolean initialized;
    private int foregroundRoutine;
    private int foregroundRequest;
    private int autoscrollRoutine = -1;
    private int autoscrollDelay;
    private int cameraFractionX;
    private int cameraFractionY;
    private int chunkEditX;
    private int chunkEditY;
    private int lavaDirection;
    private int lavaAmplitude;
    private int lavaFlow;
    public int lavaDirection() { return lavaDirection; }
    public void setLavaDirection(int value) { lavaDirection = value & 0xFFFF; }
    public int lavaAmplitude() { return lavaAmplitude; }
    public int lavaFlow() { return lavaFlow; }
    /** Obj_59FC4 owns these writes; a later boss dispatch may change direction only. */
    public void advanceLava(boolean tilted) {
        if (tilted) {
            if ((lavaDirection & 255) != 0) lavaAmplitude = Math.min(0x80, lavaAmplitude + 1);
            else lavaAmplitude = Math.max(0, lavaAmplitude - 1);
            rebuildLavaHeights(lavaAmplitude, lavaDirection);
        }
        lavaFlow = lavaAmplitude * 768;
        if ((lavaDirection & 0xFF00) == 0) lavaFlow = -lavaFlow;
    }
    private boolean capsuleOpened;
    private final byte[] lavaHeights = new byte[192];
    public LrzBossActState() { java.util.Arrays.fill(lavaHeights, (byte) 0x30); }
    public boolean capsuleOpened() { return capsuleOpened; }
    public void setCapsuleOpened(boolean value) { capsuleOpened = value; }

    /** Obj_59FC4 writes HScroll_table+$100..$1BF, before background events sample it. */
    public void rebuildLavaHeights(int amplitude, int directionWord) {
        boolean reverse = (directionWord & 0xFF00) != 0;
        for (int i = 0; i < lavaHeights.length; i++) {
            int distance = reverse ? 175 - i : i - 16;
            lavaHeights[i] = (byte) (0x30 + Math.floorDiv(distance * amplitude, 256));
        }
    }
    public int lavaHeight(int index) {
        // Widescreen extends beyond the native table: hold its outermost sample.
        return lavaHeights[Math.clamp(index, 0, lavaHeights.length - 1)] & 255;
    }
    private int paletteMode; // Palette_cycle_counters+$00: 0 normal, $80 frozen, 1 fire.
    public int paletteMode() { return paletteMode; }
    public void setPaletteMode(int value) { paletteMode = value & 255; }

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
                .putInt(cameraFractionY).putInt(chunkEditX).putInt(chunkEditY).putInt(paletteMode).putInt(capsuleOpened ? 1 : 0).putInt(lavaDirection).putInt(lavaAmplitude).putInt(lavaFlow).put(lavaHeights);
    }
    void restoreFrom(ByteBuffer buffer) {
        initialized = buffer.getInt() != 0;
        foregroundRoutine = buffer.getInt(); foregroundRequest = buffer.getInt();
        autoscrollRoutine = buffer.getInt(); autoscrollDelay = buffer.getInt();
        cameraFractionX = buffer.getInt(); cameraFractionY = buffer.getInt();
        chunkEditX = buffer.getInt(); chunkEditY = buffer.getInt(); paletteMode = buffer.getInt();
        capsuleOpened = buffer.getInt() != 0; lavaDirection = buffer.getInt(); lavaAmplitude = buffer.getInt();
        lavaFlow = buffer.getInt(); buffer.get(lavaHeights);
    }
}

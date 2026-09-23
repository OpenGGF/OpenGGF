package com.openggf.game.sonic3k.runtime;

import java.nio.ByteBuffer;
import java.util.function.IntBinaryOperator;

/** Native DEZ3 Plane A storage; layout reads occur only when the ROM redraws cells. */
public final class DezFinalPlaneState {
    static final int SNAPSHOT_BYTES = 64 * 32 * Short.BYTES + 3 * Integer.BYTES;
    private final short[] descriptors = new short[64 * 32];
    private int revision;
    private int delayedPosition;
    private int delayedRowCount = -1;

    public int revision() { return revision; }
    public int delayedPosition() { return delayedPosition; }
    public int delayedRowCount() { return delayedRowCount; }
    public boolean redrawing() { return delayedRowCount >= 0; }

    public int descriptor(int worldX, int worldY) {
        return descriptors[index(worldX, worldY)] & 0xFFFF;
    }

    private static int index(int x, int y) {
        return ((y >>> 3) & 31) * 64 + ((x >>> 3) & 63);
    }

    /** Setup_TileRowDraw: align to 16px, write two cell rows, wrap destination only. */
    public void writeRow(int sourceX, int sourceY, int blocks, IntBinaryOperator source) {
        int x = (short) sourceX & ~15;
        int y = (short) sourceY & ~15;
        for (int dy = 0; dy < 16; dy += 8) {
            for (int dx = 0; dx < blocks * 16; dx += 8) {
                descriptors[index(x + dx, y + dy)] = (short) source.applyAsInt(x + dx, y + dy);
            }
        }
        revision++;
    }

    /** Refresh_PlaneFull: sixteen 16px rows beginning at the supplied camera window. */
    public void refresh(int x, int y, IntBinaryOperator source) {
        for (int row = 0; row < 16; row++) writeRow(x, y + row * 16, 32, source);
    }

    /** loc_5A5E0/5A61A/5A6FE, unlike DEZ2's initial $E0 delayed position. */
    public void beginRedraw() {
        delayedPosition = 0xF0;
        delayedRowCount = 0xF;
    }

    /** Draw_PlaneVertBottomUp with d1=d2=0: two rows per call, eight calls total. */
    public void advanceRedraw(IntBinaryOperator source) {
        if (!redrawing()) return;
        for (int i = 0; i < 2 && redrawing(); i++) {
            writeRow(0, delayedPosition, 32, source);
            delayedPosition = (short) (delayedPosition - 0x10);
            delayedRowCount--;
        }
    }

    void capture(ByteBuffer buffer) {
        buffer.putInt(revision).putInt(delayedPosition).putInt(delayedRowCount);
        for (short descriptor : descriptors) buffer.putShort(descriptor);
    }

    void restore(ByteBuffer buffer) {
        revision = buffer.getInt(); delayedPosition = buffer.getInt(); delayedRowCount = buffer.getInt();
        for (int i = 0; i < descriptors.length; i++) descriptors[i] = buffer.getShort();
    }
}

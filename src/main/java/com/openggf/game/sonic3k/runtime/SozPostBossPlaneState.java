package com.openggf.game.sonic3k.runtime;

import java.nio.ByteBuffer;
import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;

/** Plane B descriptors retained by loc_5661A/56676 while the postboss art is admitted. */
public final class SozPostBossPlaneState {
    static final int SNAPSHOT_BYTES = 64 * 32 * Short.BYTES + 4 * Integer.BYTES;
    private final short[] descriptors = new short[64 * 32];
    private int revision;
    private int delayedY;
    private int roundedY;
    private int remaining;

    public int revision() { return revision; }
    public int descriptor(int x, int y) { return descriptors[((y >>> 3) & 31) * 64 + ((x >>> 3) & 63)] & 0xFFFF; }

    public void begin(int oldX, int oldY, int newY, IntUnaryOperator bandOffset, IntBinaryOperator source) {
        // Draw_BG leaves the preceding row in the 16th physical slot; the visible
        // 15-row range starts at the current rounded vertical position.
        for (int y = (oldY & ~15) - 16; y < (oldY & ~15) + 240; y += 16) {
            int band = y < 0x440 ? 0 : Math.min(12, 1 + ((y - 0x440) >> 4));
            writeRow(oldX + bandOffset.applyAsInt(band), y, source);
        }
        roundedY = newY & 0x7F0;
        delayedY = (roundedY + 0xE0) & 0x7F0;
        remaining = 15;
        revision = 1;
    }

    /** Draw_PlaneVertBottomUp, then loc_566A8's ordinary entering-row maintenance. */
    public void advance(int newY, IntBinaryOperator source) {
        if (revision == 0) return;
        int current = newY & 0x7F0;
        for (int i = 0; i < 2 && remaining >= 0; i++) {
            if (delayedY >= current && delayedY <= ((current + 0xF0) & 0x7F0)) {
                writeRow(0x200, delayedY, source);
            }
            delayedY = (short) (delayedY - 16);
            remaining--;
        }
        int difference = (short) (roundedY - current);
        if (difference != 0) {
            int y = current;
            if ((byte) difference < 0) {
                difference = -difference;
                y = (roundedY + 0xF0) & 0x7F0;
            }
            writeRow(0x200, y, source);
            if ((difference & 0x30) != 0x10) writeRow(0x200, (y + 16) & 0x7F0, source);
        }
        roundedY = current;
        revision++;
    }

    public void clear() { revision = 0; java.util.Arrays.fill(descriptors, (short) 0); }

    private void writeRow(int x, int y, IntBinaryOperator source) {
        x &= ~15;
        for (int dy = 0; dy < 16; dy += 8) for (int dx = 0; dx < 512; dx += 8) {
            int sx = x + dx, sy = y + dy;
            descriptors[((sy >>> 3) & 31) * 64 + ((sx >>> 3) & 63)] = (short) source.applyAsInt(sx, sy & 0x7FF);
        }
    }

    void capture(ByteBuffer buffer) {
        buffer.putInt(revision).putInt(delayedY).putInt(roundedY).putInt(remaining);
        for (short descriptor : descriptors) buffer.putShort(descriptor);
    }
    void restore(ByteBuffer buffer) {
        revision = buffer.getInt(); delayedY = buffer.getInt(); roundedY = buffer.getInt(); remaining = buffer.getInt();
        for (int i = 0; i < descriptors.length; i++) descriptors[i] = buffer.getShort();
    }
}

package com.openggf.game.sonic3k.runtime;

import java.nio.ByteBuffer;
import java.util.function.IntBinaryOperator;

/** DEZ2 loc_59532/59556: the old Plane B survives Load_Level and is replaced bottom-up. */
public final class DezTransitionPlaneState {
    static final int SNAPSHOT_BYTES = 64 * 32 * Short.BYTES + 2 * Integer.BYTES;
    private final short[] descriptors = new short[64 * 32];
    private int revision;
    private int remaining;

    public int revision() { return revision; }
    public int remaining() { return remaining; }
    public int descriptor(int x, int y) {
        return descriptors[((y >>> 3) & 31) * 64 + ((x >>> 3) & 63)] & 0xFFFF;
    }
    public void retain(IntBinaryOperator source) {
        for (int y = 0; y < 256; y += 8)
            for (int x = 0; x < 512; x += 8)
                descriptors[(y / 8) * 64 + x / 8] = (short) source.applyAsInt(x, y);
        revision = 1;
        remaining = 15;
    }
    public void copyFrom(DezTransitionPlaneState source) {
        System.arraycopy(source.descriptors, 0, descriptors, 0, descriptors.length);
        revision = source.revision;
        remaining = source.remaining;
    }
    /** Draw_delayed_position starts at $E0; two 16px rows, skipping the final negative source row. */
    public void advance(IntBinaryOperator source) {
        if (revision == 0 || remaining < 0) return;
        for (int i = 0; i < 2; i++) {
            int row = (remaining - 1) * 16;
            if (row < 0) { remaining--; continue; }
            for (int dy = 0; dy < 16; dy += 8)
                for (int x = 0; x < 512; x += 8)
                    descriptors[((row + dy) / 8) * 64 + x / 8] = (short) source.applyAsInt(x, row + dy);
            remaining--;
        }
        revision++;
    }
    void capture(ByteBuffer buffer) {
        buffer.putInt(revision).putInt(remaining);
        for (short descriptor : descriptors) buffer.putShort(descriptor);
    }
    void restore(ByteBuffer buffer) {
        revision = buffer.getInt(); remaining = buffer.getInt();
        for (int i = 0; i < descriptors.length; i++) descriptors[i] = buffer.getShort();
    }
}

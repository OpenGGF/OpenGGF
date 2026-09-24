package com.openggf.game.sonic3k.runtime;

import java.nio.ByteBuffer;
import java.util.function.IntBinaryOperator;

/** SSZ2 stage$C's retained 64x32-cell Plane A and Draw_delayed_* words. */
public final class SszEndingPlaneState {
    static final int CAPTURE_BYTES = 64 * 32 * Short.BYTES + 6 * Integer.BYTES;
    private final short[] cells = new short[64 * 32];
    private int revision;
    private int position;
    private int remaining = -1;
    private int paletteTimer;
    private int paletteCursor;
    private int cameraYRounded;
    public int revision() { return revision; }
    public int remaining() { return remaining; }
    public int descriptor(int x, int y) { return cells[index(x, y)] & 0xFFFF; }
    private static int index(int x, int y) { return ((y >>> 3) & 31) * 64 + ((x >>> 3) & 63); }

    /** sub_5928C; stage8 clears the gate, but preserves the shared timer/cursor. */
    public int advanceWaterPalette() {
        return advancePalette(6, 0x30, 7);
    }

    /** sub_592EE shares both words with sub_5928C; switching cycles does not reset either. */
    public int advanceEmeraldPalette() {
        return advancePalette(4, 0x38, 3);
    }

    private int advancePalette(int stride, int limit, int delay) {
        if (revision == 0) return -1;
        paletteTimer = (short) (paletteTimer - 1);
        if (paletteTimer >= 0) return -1;
        paletteTimer = delay;
        paletteCursor = (paletteCursor + stride) & 0xFFFF;
        if (paletteCursor >= limit) paletteCursor = 0;
        return paletteCursor;
    }

    public void begin(int cameraY, IntBinaryOperator source) {
        // Before the delayed overwrite, preserve the currently populated full plane.
        int y = cameraY & ~15;
        for (int row = 0; row < 16; row++) writeRow(0, y + row * 16, source);
        position = 0x1F0;
        remaining = 0xF;
    }
    public boolean advance(IntBinaryOperator source) {
        // Draw_PlaneVertBottomUp: two rows, d1=$200, accepted source Y=$100..$1F0.
        return advanceRows(source, 0x200, 0x100, 0x1F0);
    }
    /** loc_58C1A retains the first island plane while starting the second bottom-up redraw. */
    public void beginSecondRedraw() {
        position = 0x7F0;
        remaining = 0xF;
    }
    public boolean advanceSecondRedraw(IntBinaryOperator source) {
        return advanceRows(source, 0, 0x700, 0x7F0);
    }
    /** loc_58C42 resets the native drawing cursor alongside Camera_Y_pos. */
    public void finishSecondRedraw() { cameraYRounded = 0x720; }

    /** loc_58C68 / Draw_TileRow: retain the ring and refresh only incoming rows. */
    public void streamRows(int cameraY, IntBinaryOperator source) {
        int next = cameraY & 0xFF0;
        int previous = cameraYRounded;
        cameraYRounded = next;
        int difference = (short) (previous - next);
        if (difference == 0) return;
        int sourceY = next;
        // The shipped routine tests the low byte, including vertical wrap.
        if ((byte) difference < 0) {
            difference = -difference;
            sourceY = (previous + 0xF0) & 0xFF0;
        }
        writeRow(0, sourceY, source);
        if ((difference & 0x30) != 0x10) writeRow(0, (sourceY + 0x10) & 0xFF0, source);
    }

    private boolean advanceRows(IntBinaryOperator source, int x, int minY, int maxY) {
        for (int row = 0; row < 2 && remaining >= 0; row++) {
            if (position >= minY && position <= maxY) writeRow(x, position, source);
            position -= 16;
            remaining--;
        }
        return remaining < 0;
    }
    private void writeRow(int x, int y, IntBinaryOperator source) {
        for (int dy = 0; dy < 16; dy += 8)
            for (int dx = 0; dx < 512; dx += 8)
                cells[index(x + dx, y + dy)] = (short) source.applyAsInt(x + dx, y + dy);
        revision++;
    }
    void capture(ByteBuffer out) {
        out.putInt(revision).putInt(position).putInt(remaining).putInt(paletteTimer).putInt(paletteCursor).putInt(cameraYRounded);
        for (short cell : cells) out.putShort(cell);
    }
    void restore(ByteBuffer in) {
        revision = in.getInt(); position = in.getInt(); remaining = in.getInt();
        paletteTimer = in.getInt(); paletteCursor = in.getInt(); cameraYRounded = in.getInt();
        for (int i = 0; i < cells.length; i++) cells[i] = in.getShort();
    }
}

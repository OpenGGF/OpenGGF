package com.openggf.game.sonic3k.runtime;

import java.nio.ByteBuffer;

/**
 * SSZ2 background routine 4's retained Plane B: sixteen 16-pixel row slots of the 256-line
 * VDP ring, each recording the layout row {@code Draw_TileRow} last wrote into it.
 *
 * <p>{@code loc_58BBC} (sonic3k.asm:117846-117875) lifts {@code Camera_Y_pos_BG_copy} by
 * {@code $100} and re-rounds {@code Camera_Y_pos_BG_rounded} with the lifted value, so no row is
 * redrawn and the ring keeps showing the island it already holds. Routine 4
 * (sonic3k.asm:117996) then loads {@code a3 = Level_layout_main}, the foreground row pointers,
 * over the {@code Level_layout_main+2} that {@code ScreenEvents} passes background events
 * (sonic3k.asm:102239-102246). As {@code loc_59208} lowers the copy, {@code loc_58D1E}'s
 * {@code Draw_TileRow} (d1=0, d6=$20: layout X 0-$1FF) replaces each entering row with a
 * foreground row. Cells are read from the layout at render time; neither layout region changes
 * after {@code loc_58AE0}'s rows 9-10 write.
 */
public final class SszEndingBackgroundPlaneState {
    static final int CAPTURE_BYTES = Integer.BYTES + 2 * Short.BYTES + 16 * Short.BYTES;
    private static final int ROW_SLOTS = 16;
    /** {@code Camera_Y_pos_mask}, set to {@code $FF0} by {@code LevelSetup} (sonic3k.asm:102206). */
    private static final int Y_MASK = 0xFF0;

    private int revision;
    private short rounded;
    private short foregroundSlots;
    private final short[] sourceY = new short[ROW_SLOTS];

    /** Zero until {@code loc_58BBC} hands Plane B to routine 4. */
    public int revision() { return revision; }

    /**
     * {@code loc_58BBC}: the slots keep the rows drawn at the old copy; only the copy and the
     * rounded word move. The fifteen rows from the old rounded value cover the visible window;
     * the sixteenth slot is overwritten by the first upward row before it can be seen.
     */
    public void lift(int previousCopyY, int liftedCopyY) {
        int first = previousCopyY & Y_MASK;
        for (int row = 0; row < ROW_SLOTS; row++) {
            int y = (first + row * 16) & 0xFFFF;
            sourceY[(y >> 4) & 15] = (short) y;
        }
        foregroundSlots = 0;
        rounded = (short) (liftedCopyY & Y_MASK);
        revision++;
    }

    /** {@code Draw_TileRow} with {@code a6/a5 = Camera_Y_pos_BG_copy/_rounded}. */
    public void drawTileRow(int copyY) {
        if (revision == 0) return;
        int next = copyY & Y_MASK;
        int previous = rounded & 0xFFFF;
        rounded = (short) next;
        int difference = (short) (previous - next);
        if (difference == 0) return;
        int y = next;
        // tst.b d2: the shipped routine tests the low byte for the direction.
        if ((byte) difference < 0) {
            difference = -difference;
            y = (previous + 0xF0) & Y_MASK;
        }
        writeRow(y);
        // Plane_double_update_flag: anything other than a single 16-pixel step draws a second row.
        if ((difference & 0x30) != 0x10) writeRow((y + 0x10) & Y_MASK);
    }

    /** Whether the slot holding plane line {@code y} was last written from {@code Level_layout_main}. */
    public boolean foregroundRow(int y) { return (foregroundSlots & (1 << ((y >> 4) & 15))) != 0; }

    /** The layout Y that plane line {@code y} currently shows. */
    public int sourceY(int y) { return (sourceY[(y >> 4) & 15] & 0xFFFF) + (y & 15); }

    private void writeRow(int y) {
        int slot = (y >> 4) & 15;
        sourceY[slot] = (short) y;
        foregroundSlots |= (short) (1 << slot);
        revision++;
    }

    void capture(ByteBuffer out) {
        out.putInt(revision).putShort(rounded).putShort(foregroundSlots);
        for (short y : sourceY) out.putShort(y);
    }

    void restore(ByteBuffer in) {
        revision = in.getInt(); rounded = in.getShort(); foregroundSlots = in.getShort();
        for (int i = 0; i < sourceY.length; i++) sourceY[i] = in.getShort();
    }
}

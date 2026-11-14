package com.openggf.game.internal;

/**
 * Engine-only: a level whose collision lookup indexes its layout RAM row by the X position's
 * high byte, masked to the row's width, rather than wrapping X across the loaded layout. Each
 * row of that RAM holds the foreground in its first half and the background in its second,
 * and the cells past a layout's declared width are the zeroes the loader cleared, so a probe
 * past the foreground's last column reads a blank cell (or, in the second half, the
 * background) instead of the foreground's first column. The level manager's collision
 * lookups apply it to every foreground probe, the player's and the objects'.
 */
public interface CollisionLayoutColumnWindow {
    /** Marks a column that {@link #collisionLayoutColumn} resolved into the row's background half. */
    int BACKGROUND_HALF = 0x1000;

    /**
     * The layout column a collision probe at {@code x} reads: a foreground map column, a
     * background map column with {@link #BACKGROUND_HALF} set, or {@code -1} for a cleared cell.
     */
    int collisionLayoutColumn(int x);
}

package com.openggf.game.internal;

import com.openggf.util.ShortIndexedView;

/** Internal, render-only pixel-column sampling of an already rendered background plane. */
public interface BackgroundColumnRemap {
    /** Null retains ordinary scroll composition. Views remain immutable for the frame. */
    Columns backgroundColumns();

    /**
     * Source X is in background world pixels; Y offsets are subtracted from the ordinary sample Y.
     * FBO rows at/after sourceYEndExclusive retain ordinary scrolling. The cutoff is
     * measured before remapping, so a moving lower band is not bent with the sky.
     */
    record Columns(ShortIndexedView sourceX, ShortIndexedView yOffsets, int sourceYEndExclusive) {
        public Columns(ShortIndexedView sourceX, ShortIndexedView yOffsets) {
            this(sourceX, yOffsets, Integer.MAX_VALUE);
        }
        public Columns {
            if (sourceYEndExclusive <= 0) throw new IllegalArgumentException("Column remap band must be nonempty");
            if (sourceX.size() != yOffsets.size() || sourceX.size() == 0) {
                throw new IllegalArgumentException("Background column views must have equal nonzero length");
            }
        }
    }
}

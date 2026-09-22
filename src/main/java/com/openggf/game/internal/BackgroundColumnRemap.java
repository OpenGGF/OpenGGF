package com.openggf.game.internal;

import com.openggf.util.ShortIndexedView;

/** Internal, render-only pixel-column sampling of an already rendered background plane. */
public interface BackgroundColumnRemap {
    /** Null retains ordinary scroll composition. Views remain immutable for the frame. */
    Columns backgroundColumns();

    /** Source X is in FBO pixels; Y offsets are subtracted from the ordinary sample Y. */
    record Columns(ShortIndexedView sourceX, ShortIndexedView yOffsets) {
        public Columns {
            if (sourceX.size() != yOffsets.size() || sourceX.size() == 0) {
                throw new IllegalArgumentException("Background column views must have equal nonzero length");
            }
        }
    }
}

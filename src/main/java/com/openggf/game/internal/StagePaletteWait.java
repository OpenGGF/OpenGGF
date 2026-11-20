package com.openggf.game.internal;

import com.openggf.level.LevelManager;

/** A stage palette program that holds the main loop while it waits for VSync. */
public interface StagePaletteWait {
    /** Publishes and advances the palette after one serviced VSync. */
    void advancePaletteWait();

    interface Source {
        /** The current wait, or null when the stage may run its main loop. */
        StagePaletteWait pendingPaletteWait(LevelManager level);
    }

    static StagePaletteWait pending(LevelManager level) {
        return level != null && level.getAnimatedPaletteManager() instanceof Source source
                ? source.pendingPaletteWait(level) : null;
    }
}

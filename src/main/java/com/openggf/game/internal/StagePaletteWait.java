package com.openggf.game.internal;

import com.openggf.level.LevelManager;

import java.lang.reflect.Proxy;

/** A stage palette program that holds the main loop while it waits for VSync. */
public interface StagePaletteWait {
    /** Publishes and advances the palette after one serviced VSync. */
    void advancePaletteWait();

    @FunctionalInterface
    interface Source {
        /** The current wait, or null when the stage may run its main loop. */
        StagePaletteWait pendingPaletteWait(LevelManager level);
    }

    static StagePaletteWait pending(LevelManager level) {
        var source = source(level);
        return source == null ? null : source.pendingPaletteWait(level);
    }

    static boolean supported(LevelManager level) {
        return source(level) != null;
    }

    private static Source source(LevelManager level) {
        var world = level == null ? null : level.getWorldSession();
        var source = world == null ? null : world.getGameModule().getGameService(Source.class);
        return source != null && !Proxy.isProxyClass(source.getClass())
                && source.getClass().getClassLoader() == StagePaletteWait.class.getClassLoader()
                ? source : null;
    }
}

package com.openggf.game;

import com.openggf.game.internal.CheckpointBackgroundScroll;
import com.openggf.util.EngineCallerAccess;

/** Internal checkpoint transport; deliberately outside the creator Mod API. */
public final class CheckpointBackgroundScrollAccess {
    private CheckpointBackgroundScrollAccess() { }

    public static CheckpointBackgroundScroll saved(CheckpointState state) {
        requireEngineCaller();
        return state.savedBackgroundScroll;
    }

    public static void save(CheckpointState state, CheckpointBackgroundScroll background) {
        requireEngineCaller();
        state.savedBackgroundScroll = background;
    }

    public static CheckpointBackgroundScroll saved(LevelLoadContext context) {
        requireEngineCaller();
        return context.checkpointBackgroundScroll;
    }

    public static void restore(LevelLoadContext context, CheckpointState state) {
        requireEngineCaller();
        state.savedBackgroundScroll = context.checkpointBackgroundScroll;
    }

    private static void requireEngineCaller() {
        if (EngineCallerAccess.callerOutside(CheckpointBackgroundScrollAccess.class).getClassLoader()
                != CheckpointBackgroundScrollAccess.class.getClassLoader()) {
            throw new SecurityException("Engine checkpoint transport only");
        }
    }
}

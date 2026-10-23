package com.openggf.game;

import com.openggf.game.session.WorldSession;
import java.util.Objects;

/** Engine-only world binding for one actual load attempt; absent from the Mod API surface. */
public final class LevelLoadRuntimeAccess {
    private LevelLoadRuntimeAccess() { }

    public static void bindWorld(LevelLoadContext context, WorldSession world) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(world, "world");
        if (context.runtimeWorld != null && context.runtimeWorld != world)
            throw new IllegalArgumentException("Load context belongs to another world");
        context.runtimeWorld = world;
    }

    public static WorldSession requireWorld(LevelLoadContext context) {
        return Objects.requireNonNull(context.runtimeWorld, "Load attempt requires an explicit world");
    }
}

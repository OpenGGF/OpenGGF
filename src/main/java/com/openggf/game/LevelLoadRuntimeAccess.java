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

    /** Actual attempt participants are optional for decode/preview, required by assembled player init. */
    public static void bindParticipants(LevelLoadContext context, WorldSession world,
                                        com.openggf.sprites.managers.SpriteManager sprites) {
        bindWorld(context, world);
        if (context.runtimeSprites != null && context.runtimeSprites != sprites)
            throw new IllegalArgumentException("Load context belongs to another sprite manager");
        context.runtimeSprites = sprites;
    }

    public static com.openggf.sprites.managers.SpriteManager requireSprites(LevelLoadContext context) {
        return Objects.requireNonNull(context.runtimeSprites, "Participant assembly requires an explicit sprite manager");
    }

    public static WorldSession requireWorld(LevelLoadContext context) {
        return Objects.requireNonNull(context.runtimeWorld, "Load attempt requires an explicit world");
    }
}

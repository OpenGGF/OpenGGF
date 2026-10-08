package com.openggf.game.session;

import com.openggf.game.LevelLoadCause;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.sprites.managers.SpriteManager;

/** Internal access to the policy owner pinned by WorldSession, without exposing provider state on its API. */
public final class WorldSessionPolicyAccess {
    private WorldSessionPolicyAccess() { }

    public static boolean hasPolicies(WorldSession world) {
        return world != null && world.policies != null;
    }

    public static <T> T getService(WorldSession world, Class<T> type) {
        return hasPolicies(world) ? world.policies.getService(type) : null;
    }

    public static void registerRewind(WorldSession world, RewindRegistry registry) {
        if (hasPolicies(world)) registry.register(world.policies.rewindAdapter());
    }

    public static void bindRoster(WorldSession world, SpriteManager sprites) {
        if (hasPolicies(world)) world.policies.bindRoster(sprites);
    }

    public static void beforeAssembly(WorldSession world, LevelLoadCause cause) {
        if (hasPolicies(world)) world.policies.beforeAssembly(cause);
    }

    public static void failedAssembly(WorldSession world, LevelLoadCause cause) {
        if (hasPolicies(world)) world.policies.failedAssembly(cause);
    }

    public static void closeScreens(WorldSession world) {
        if (hasPolicies(world)) world.policies.closeScreens();
    }

    static void retire(WorldSession world) {
        if (hasPolicies(world)) world.policies.retire();
    }
}

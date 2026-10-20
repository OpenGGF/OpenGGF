package com.openggf.game.session;

import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshottable;

import java.util.Objects;

/** Explicit engine-only access to one world's PLC owner, never the current ambient session. */
public final class WorldPlcLifecycleAccess {
    private WorldPlcLifecycleAccess() { }

    public static PlcLifecycleService owned(WorldSession world) {
        return Objects.requireNonNull(world, "world").worldPlcLifecycle;
    }

    /** Existing module-scoped games retain their original lookup lifecycle. */
    public static PlcLifecycleService service(WorldSession world) {
        var owned = owned(world);
        return owned != null ? owned : world.getGameModule().getGameService(PlcLifecycleService.class);
    }

    public static <T> T ownedService(WorldSession world, Class<T> type) {
        var owned = owned(world);
        return owned != null && PlcLifecycleService.class.isAssignableFrom(type) && type.isInstance(owned)
                ? type.cast(owned) : null;
    }

    public static <T> T require(WorldSession world, Class<T> type) {
        return Objects.requireNonNull(ownedService(world, type), "explicit world PLC owner for " + type.getName());
    }

    public static void register(WorldSession world, RewindRegistry registry) {
        var owned = owned(world);
        if (owned instanceof RewindSnapshottable<?> adapter)
            com.openggf.game.rewind.NativeRewindAdapterPublication.registerWorldPlc(registry, world, adapter);
    }
}

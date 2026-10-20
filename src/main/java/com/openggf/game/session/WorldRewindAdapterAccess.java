package com.openggf.game.session;

import com.openggf.game.internal.NativePhysicalLevelLoad;
import com.openggf.game.rewind.NativeRewindAdapterPublication;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.util.EngineCallerAccess;
import java.lang.invoke.MethodHandles;
import java.util.Objects;

/** Explicit, engine-only access to the final world slot; never allocates an owner. */
public final class WorldRewindAdapterAccess {
    private WorldRewindAdapterAccess() { }

    public static RewindSnapshottable<?> owned(MethodHandles.Lookup lookup, WorldSession world) {
        Class<?> caller = EngineCallerAccess.callerOutside(WorldRewindAdapterAccess.class);
        if (lookup == null || !lookup.hasFullPrivilegeAccess() || lookup.lookupClass() != caller
                || (caller != GameplayModeContext.class && caller != NativeRewindAdapterPublication.class
                    && caller.getNestHost() != NativePhysicalLevelLoad.class))
            throw new SecurityException("World DATA access belongs to its actual engine composition");
        return Objects.requireNonNull(world, "world").worldRewindAdapter;
    }

    public static RewindSnapshottable<?> register(MethodHandles.Lookup lookup,
            WorldSession world, RewindRegistry registry) {
        Class<?> caller = EngineCallerAccess.callerOutside(WorldRewindAdapterAccess.class);
        if (lookup == null || !lookup.hasFullPrivilegeAccess() || lookup.lookupClass() != caller
                || caller != GameplayModeContext.class)
            throw new SecurityException("World DATA publication belongs to gameplay composition");
        var adapter = Objects.requireNonNull(world, "world").worldRewindAdapter;
        if (adapter != null) NativeRewindAdapterPublication.registerWorldData(registry, world, adapter);
        return adapter;
    }
}

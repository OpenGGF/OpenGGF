package com.openggf.game.session;

import com.openggf.game.internal.NativePhysicalLevelLoad;
import com.openggf.game.internal.NativePhysicalWorldData;
import com.openggf.game.internal.WorldDataRewindPublication;
import com.openggf.game.rewind.NativeRewindAdapterPublication;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.util.EngineCallerAccess;
import java.lang.invoke.MethodHandles;
import java.util.Objects;

/** Explicit engine composition of a durable DATA service and its reserved runtime projection. */
public final class WorldRewindAdapterAccess {
    private WorldRewindAdapterAccess() { }

    public static RewindSnapshottable<?> owned(MethodHandles.Lookup lookup, WorldSession world) {
        requireOwnerAccess(lookup);
        return adapter(Objects.requireNonNull(world, "world"));
    }

    public static NativePhysicalWorldData data(MethodHandles.Lookup lookup, WorldSession world) {
        requireOwnerAccess(lookup);
        return Objects.requireNonNull(world, "world").worldPhysicalData;
    }

    private static void requireOwnerAccess(MethodHandles.Lookup lookup) {
        Class<?> caller = EngineCallerAccess.callerOutside(WorldRewindAdapterAccess.class);
        if (lookup == null || !lookup.hasFullPrivilegeAccess() || lookup.lookupClass() != caller
                || (caller != GameplayModeContext.class && caller != NativeRewindAdapterPublication.class
                    && caller.getNestHost() != NativePhysicalLevelLoad.class))
            throw new SecurityException("World DATA access belongs to its actual engine composition");
    }

    private static RewindSnapshottable<?> adapter(WorldSession world) {
        if (world.worldPhysicalData == null) return null;
        if (!(world.worldPhysicalData instanceof WorldDataRewindPublication publication))
            throw new IllegalStateException("World DATA has no native rewind projection");
        return Objects.requireNonNull(publication.dataRewindState(), "world DATA rewind projection");
    }

    public static RewindSnapshottable<?> register(MethodHandles.Lookup lookup,
            WorldSession world, RewindRegistry registry) {
        Class<?> caller = EngineCallerAccess.callerOutside(WorldRewindAdapterAccess.class);
        if (lookup == null || !lookup.hasFullPrivilegeAccess() || lookup.lookupClass() != caller
                || caller != GameplayModeContext.class)
            throw new SecurityException("World DATA publication belongs to gameplay composition");
        var adapter = adapter(Objects.requireNonNull(world, "world"));
        if (adapter != null) NativeRewindAdapterPublication.registerWorldData(registry, world, adapter);
        return adapter;
    }
}

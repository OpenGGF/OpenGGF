package com.openggf.mods.code;

import java.util.function.Supplier;

/**
 * Engine-only bridge that runs engine work with no creator callback owner in scope. A course
 * restore requested from inside a frame controller's callback rebuilds engine objects; without
 * this, objects the restore re-creates would inherit the calling mod as their owner. Not part of
 * the Mod API: creator callers are refused, so a mod cannot use it to escape owner attribution.
 */
public final class EngineOwnerScope {
    private EngineOwnerScope() { }

    public static <T> T withoutCreatorOwner(Supplier<T> work) {
        Class<?> caller = com.openggf.util.EngineCallerAccess.callerOutside(EngineOwnerScope.class);
        if (caller.getClassLoader() != EngineOwnerScope.class.getClassLoader()) {
            throw new SecurityException("Creator code cannot suspend its own callback owner");
        }
        return OwnerCallbackScope.withoutOwner(work);
    }
}

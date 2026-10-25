package com.openggf.level.objects;

/** Engine-only notification that a native object left its level comparison loop. */
public final class NativeLevelExitInternalAccess {
    private NativeLevelExitInternalAccess() { }

    /**
     * Notifies only the world captured by these injected services. Standalone
     * object hosts without an owned comparison context have nothing to close.
     * This changes diagnostic publication ownership, never physical art work.
     *
     * @return whether the current owning context received the notification
     */
    public static boolean endSourceComparisonAtRomModeChange(ObjectServices services) {
        Class<?> caller = com.openggf.util.EngineCallerAccess.callerOutside(NativeLevelExitInternalAccess.class);
        if (caller.getClassLoader() != NativeLevelExitInternalAccess.class.getClassLoader()) {
            throw new SecurityException("Native level exits belong to the engine object owner");
        }
        return services instanceof DefaultObjectServices owned
                && owned.endSourceComparisonAtRomModeChange();
    }
}

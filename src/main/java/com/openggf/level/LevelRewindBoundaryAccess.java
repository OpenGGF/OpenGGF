package com.openggf.level;

import com.openggf.game.rewind.RewindBoundaryReporter;

/** Host-only wiring of level boundaries to the attached gameplay context. */
public final class LevelRewindBoundaryAccess {
    private LevelRewindBoundaryAccess() {
    }

    public static com.openggf.level.objects.ObjectManager attachedObjectManager(LevelManager manager) {
        return manager.attachedObjectManager();
    }

    public static void bind(LevelManager manager, RewindBoundaryReporter reporter) {
        java.util.Objects.requireNonNull(manager, "manager").setRewindBoundaryReporter(reporter);
    }

    /** The title/load owner has finished deferred player, object and provider assembly. */
    public static void initialAssemblyCompleted(LevelManager manager) {
        java.util.Objects.requireNonNull(manager, "manager").markRewindLevelLoadBoundary();
    }
}

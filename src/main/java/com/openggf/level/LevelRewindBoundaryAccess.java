package com.openggf.level;

import com.openggf.game.rewind.RewindBoundaryReporter;

/** Host-only wiring of level boundaries to the attached gameplay context. */
public final class LevelRewindBoundaryAccess {
    private LevelRewindBoundaryAccess() {
    }

    public static void bind(LevelManager manager, RewindBoundaryReporter reporter) {
        java.util.Objects.requireNonNull(manager, "manager").setRewindBoundaryReporter(reporter);
    }
}

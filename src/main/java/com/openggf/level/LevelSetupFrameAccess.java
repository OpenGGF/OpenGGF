package com.openggf.level;

/**
 * Engine-internal bridge for the fresh-load setup frame.
 *
 * <p>A production driver brackets each represented iteration. A fresh level
 * load inside an open iteration makes that iteration S3K Level's setup frame
 * (sonic3k.asm:7536-7775): no title-loop object dispatch or Nemesis arm
 * belongs to it. A load performed between iterations has no setup frame.
 */
public final class LevelSetupFrameAccess {
    private LevelSetupFrameAccess() { }

    /** Opens one driver iteration and ends any previous setup frame. */
    public static void beginIteration(LevelManager level) {
        if (level != null) level.beginDriverIteration();
    }

    /** Closes the iteration; its setup-frame result stays observable. */
    public static void endIteration(LevelManager level) {
        if (level != null) level.endDriverIteration();
    }

    /** Whether the latest iteration performed a fresh load and no later one has opened. */
    public static boolean inFreshLevelSetupFrame(LevelManager level) {
        return level != null && level.isInFreshLevelSetupFrame();
    }
}

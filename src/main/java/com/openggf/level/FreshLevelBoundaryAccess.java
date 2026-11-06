package com.openggf.level;

/**
 * Engine-internal bridge to the fresh-level player/camera boundary for loads
 * whose caller places the destination players after the load itself.
 */
public final class FreshLevelBoundaryAccess {
    private FreshLevelBoundaryAccess() { }

    /**
     * S3K Level: clears object RAM in loc_60DE (sonic3k.asm:7649-7651), sets
     * the start position at loc_6310's Get_LevelSizeStart once the title loop
     * has exited (7789-7792), and creates the players in loc_6468
     * (7880-7915). Holds the already-placed destination players and the
     * source camera behind the title loop until those publication points.
     */
    public static void beginAfterSetup(LevelManager level, short previousCameraX,
                                       short previousCameraY, int previousRings) {
        level.beginFreshLevelTransitionBoundaryAfterSetup(
                previousCameraX, previousCameraY, previousRings);
    }

    /**
     * Completes a held boundary at a title release that enters a mode with no
     * LevelLoop fresh-boundary owner of its own (a bonus stage): loc_6468
     * creates the players and runs its initial Process_Sprites before the
     * first loop iteration. Returns whether that initial pass ran.
     */
    public static boolean completeAtRelease(LevelManager level) {
        return level.completeFreshLevelTransitionBoundaryReportingInitialPass();
    }
}

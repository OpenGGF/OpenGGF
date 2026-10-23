package com.openggf.level;

import com.openggf.level.objects.HudProfileAccess;

/**
 * Engine-only: a hosted run's scene covering the level with its menu hides the stock HUD.
 * Presentation only, unlike HUD suppression, which also stops the act timer. A level load
 * builds a fresh HUD manager, so the run host re-applies the cover every frame.
 */
public final class LevelHudCoverAccess {
    private LevelHudCoverAccess() { }

    public static void setCovered(LevelManager level, boolean covered) {
        HudProfileAccess.setCoveredByHost(level.hudRenderManager, covered);
    }

    public static boolean covered(LevelManager level) {
        return HudProfileAccess.coveredByHost(level.hudRenderManager);
    }
}

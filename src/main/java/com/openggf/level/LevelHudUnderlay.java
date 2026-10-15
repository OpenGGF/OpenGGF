package com.openggf.level;

/**
 * Host-owned screen-space work painted after the level's sprites and before
 * its HUD pass, for overlays a game's sprite renderer orders beneath the HUD.
 */
public final class LevelHudUnderlay {
    private LevelHudUnderlay() { }

    /**
     * Runs {@code levelDraw} with {@code underlay} painted at the start of the
     * level's HUD pass.
     *
     * @return whether the draw reached its HUD pass and painted the underlay;
     *         when it did not, the caller still owes the overlay
     */
    public static boolean draw(LevelManager level, Runnable levelDraw, Runnable underlay) {
        LevelRenderer renderer = level.spritePresentationRenderer();
        renderer.hudUnderlay = underlay;
        try {
            levelDraw.run();
            return renderer.hudUnderlay == null;
        } finally {
            renderer.hudUnderlay = null;
        }
    }
}

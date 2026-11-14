package com.openggf.level;

/**
 * Internal view (engine only; not part of the compiled-mod API) of whether building
 * whole-level tilemaps ahead of a terrain change still pays off.
 */
public final class LevelTilemapPrebuildAccess {
    private LevelTilemapPrebuildAccess() { }

    /**
     * False when the foreground is streamed (a GPU renderer is present): a later
     * invalidation then redraws only changed cells or draws the camera's columns first
     * and the rest over following frames, so building both whole images in advance only
     * moves that work onto an earlier frame. True for headless runs, which keep the
     * whole-image path and its prebuilt hand-off.
     */
    public static boolean prebuildsTransitionTilemaps(LevelManager level) {
        LevelTilemapManager tilemaps = level == null ? null : level.getTilemapManager();
        return tilemaps != null && !tilemaps.streamsForegroundBuild();
    }
}

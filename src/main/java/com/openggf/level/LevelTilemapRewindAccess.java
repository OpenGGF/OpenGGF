package com.openggf.level;

/** Engine-only geometry-restore bridge; not part of the compiled-mod API. */
public final class LevelTilemapRewindAccess {
    private LevelTilemapRewindAccess() { }

    public static void rebindCurrentLevelGeometryForRestore(LevelManager manager) {
        manager.rebindCurrentLevelGeometryForRewindRestore();
    }

    public static void invalidateGeometryCachesForRestore(LevelManager manager) {
        LevelTilemapManager tilemaps = manager.getTilemapManager();
        if (tilemaps != null) {
            tilemaps.invalidateGeometryCachesForRewindRestore();
        } else {
            // Preserve the existing early/no-tilemap invalidation seam.
            manager.invalidateAllTilemaps();
        }
    }
}

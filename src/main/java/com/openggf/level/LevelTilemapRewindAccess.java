package com.openggf.level;

/** Engine-only geometry-restore bridge; not part of the compiled-mod API. */
public final class LevelTilemapRewindAccess {
    private LevelTilemapRewindAccess() { }

    public static void rebindCurrentLevelGeometryForRestore(LevelManager manager) {
        if (manager.level == null) throw new IllegalStateException("Geometry restore requires the current level");
        manager.blockPixelSize = manager.level.getBlockPixelSize();
        manager.chunksPerBlockSide = manager.level.getChunksPerBlockSide();
        manager.cacheLevelDimensions();
        if (manager.tilemapManager != null) {
            manager.tilemapManager.updateGeometry(manager.buildGeometry());
            manager.tilemapManager.invalidateGeometryCachesForRewindRestore();
        } else {
            manager.invalidateForegroundTilemap();
        }
        // Level identity and zone have not changed. Their animation owners and
        // captured logical state remain bound to this level, including its new map.
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

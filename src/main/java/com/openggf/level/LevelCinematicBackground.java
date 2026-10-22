package com.openggf.level;

/** Draw-only background window selection over privately owned native descriptor state. */
final class LevelCinematicBackground {
    private LevelCinematicBackground() { }

    static boolean prepare(LevelManager level, LevelTilemapManager tiles, LevelScrollPresentation scroll) {
        var parameters = java.util.Objects.requireNonNull(scroll.backgroundBuildParameters(),
                "Native scroll has no captured background build policy");
        tiles.setCinematicBackgroundParameters(parameters);
        int x = scroll.registers().backgroundX();
        var mode = scroll.tilemapMode();
        boolean fullWidth = LevelManager.applyBackgroundTilemapWindowSelection(tiles, x, mode,
                parameters.fullWidthWindow(), parameters.persistentPlane(), parameters.wraps(),
                scroll.registers().backgroundPeriod(), level.cachedBgContiguousWidthPx, parameters.loopBandY());
        // All policy and descriptor callbacks are frozen; the ordinary layout reader only
        // reads the already-loaded ROM world and fills this private descriptor storage.
        tiles.ensureBackgroundTilemapData(level::getBlockAtPosition, null, level.currentZone,
                null, mode, x, scroll.registers().backgroundY(), level.verticalWrapEnabled);
        return fullWidth;
    }
}

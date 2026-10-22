package com.openggf.level;

import com.openggf.level.scroll.BgTilemapUpdateMode;

/** Draw-only background window selection over privately owned native descriptor state. */
final class LevelCinematicBackground {
    private LevelCinematicBackground() { }

    static boolean prepare(LevelManager level, LevelTilemapManager tiles, LevelScrollPresentation scroll) {
        var parameters = java.util.Objects.requireNonNull(scroll.backgroundBuildParameters(),
                "Native scroll has no captured background build policy");
        tiles.setCinematicBackgroundParameters(parameters);
        int x = scroll.registers().backgroundX();
        var mode = scroll.tilemapMode();
        boolean fullWidth = parameters.fullWidthWindow() && x != Integer.MIN_VALUE;
        int period = fullWidth ? level.cachedBgContiguousWidthPx : scroll.registers().backgroundPeriod();
        if (fullWidth) {
            if (tiles.getBgTilemapBaseX() != 0) {
                tiles.setBgTilemapBaseX(0);
                tiles.setBackgroundTilemapDirty(true);
            }
        } else if (mode == BgTilemapUpdateMode.STATIC_WINDOW && !parameters.persistentPlane()
                && x != Integer.MIN_VALUE && parameters.wraps()) {
            int base = Math.floorDiv(x, LevelConstants.CHUNK_WIDTH) * LevelConstants.CHUNK_WIDTH;
            if (base != tiles.getBgTilemapBaseX()) tiles.requestBgWindowBaseX(base);
        } else if (mode == BgTilemapUpdateMode.STATIC_WINDOW && tiles.getBgTilemapBaseX() != 0) {
            tiles.setBgTilemapBaseX(0);
            tiles.setBackgroundTilemapDirty(true);
        }
        if (period != tiles.getCurrentBgPeriodWidth()) {
            tiles.setCurrentBgPeriodWidth(period);
            tiles.setBackgroundTilemapDirty(true);
        }
        if (parameters.loopBandY() != tiles.getBgLoopBandBaseY()) {
            tiles.setBgLoopBandBaseY(parameters.loopBandY());
            tiles.setBackgroundTilemapDirty(true);
        }
        // All policy and descriptor callbacks are frozen; the ordinary layout reader only
        // reads the already-loaded ROM world and fills this private descriptor storage.
        tiles.ensureBackgroundTilemapData(level::getBlockAtPosition, null, level.currentZone,
                null, mode, x, scroll.registers().backgroundY(), level.verticalWrapEnabled);
        return fullWidth;
    }
}

package com.openggf.level;

/** Test-only observations of the existing retained cache; never mutates or creates an owner. */
public final class NativeBackgroundTestAccess {
    private NativeBackgroundTestAccess() { }

    public static byte[] descriptors(LevelManager level) {
        var bytes = level.getTilemapManager().getBackgroundTilemapData();
        return bytes == null ? null : bytes.clone();
    }

    /** Width/height, effective source origin, period, wrap height, cache base and loop-band base. */
    public static int[] geometry(LevelManager level) {
        var cache = level.getTilemapManager();
        return new int[] {cache.getBackgroundTilemapWidthTiles(), cache.getBackgroundTilemapHeightTiles(),
                cache.getBackgroundTilemapSourceX(), cache.getBackgroundTilemapSourceY(),
                cache.getCurrentBgPeriodWidth(), cache.getBackgroundVdpWrapHeightTiles(),
                cache.getBgTilemapBaseX(), cache.getBgLoopBandBaseY()};
    }

    public static boolean[] flags(LevelManager level) {
        var cache = level.getTilemapManager();
        return new boolean[] {cache.isBackgroundTilemapDirty(), cache.isRetainedBackgroundPlaneAuthoritative(),
                cache.isPersistentBgBaselineValid()};
    }
}

package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.game.internal.BackgroundBuildProjection;
import com.openggf.game.internal.BackgroundBuildProjectionSource;
import com.openggf.game.internal.BackgroundDescriptorOverride;

/** Native-published background build policy; draw-only sampling reads no live event owners. */
record BackgroundBuildParameters(boolean wraps, boolean foregroundWraps, boolean linearOverflow,
                                 boolean fullWidthRequired, boolean persistentPlane, boolean fullWidthWindow,
                                 int loopBandY, boolean introOcean, BackgroundBuildProjection projection,
                                 long foregroundRevision, int foregroundRingRows,
                                 java.util.function.IntBinaryOperator foregroundDescriptors,
                                 com.openggf.graphics.ForegroundWindow foregroundWindow,
                                 com.openggf.graphics.ForegroundScrollSplit foregroundSplit) {
    BackgroundBuildParameters(boolean wraps, boolean foregroundWraps, boolean linearOverflow,
                              boolean fullWidthRequired, boolean persistentPlane, boolean fullWidthWindow,
                              int loopBandY, boolean introOcean, BackgroundBuildProjection projection) {
        this(wraps, foregroundWraps, linearOverflow, fullWidthRequired, persistentPlane, fullWidthWindow,
                loopBandY, introOcean, projection, 0, 0, null, null, null);
    }
    static BackgroundBuildParameters capture(LevelManager level) {
        if (level == null) return null;
        var provider = level.zoneFeatureProvider;
        var registry = GameServices.zoneRuntimeRegistryOrNull();
        var runtime = registry == null ? null : registry.current();
        var scroll = level.parallaxManager;
        int x = scroll == null ? Integer.MIN_VALUE : scroll.getBgCameraX();
        var projection = provider instanceof BackgroundBuildProjectionSource source
                ? source.captureBackgroundBuild() : null;
        if (projection == null) {
            long unsupportedRevision = provider instanceof BackgroundDescriptorOverride owner ? owner.backgroundDescriptorRevision() : 0;
            double base = provider == null ? 0 : provider.getVdpNametableBase(level.currentZone,
                    level.currentAct, level.camera.getX(), Math.max(1, level.cachedBgWidthPx / 8));
            projection = new BackgroundBuildProjection(unsupportedRevision, unsupportedRevision == 0 ? null : (sx, sy) -> {
                throw new IllegalStateException("Directed background descriptor owner must supply an immutable background build projection");
            }, ignored -> base);
        }
        long foregroundRevision = provider instanceof com.openggf.game.internal.ForegroundDescriptorOverride owner
                ? owner.foregroundDescriptorRevision() : 0;
        int foregroundRows = 0;
        java.util.function.IntBinaryOperator foregroundDescriptors = null;
        if (foregroundRevision != 0 && provider instanceof com.openggf.game.internal.ForegroundDescriptorOverride owner) {
            foregroundRows = owner.foregroundDescriptorRingRows();
            int width = Math.max(1, level.cachedFgWidthPx / 8);
            int height = foregroundRows > 0 ? foregroundRows : Math.max(1, level.cachedFgHeightPx / 8);
            // This opt-in presentation image shares the background snapshot's 8 MiB bound.
            // Ordinary modules do not capture directed presentation descriptors.
            long count = (long) width * height;
            if (count > 2L * 1024 * 1024)
                throw new IllegalStateException("Directed foreground descriptor image exceeds bounded snapshot: "
                        + width + "x" + height + " cells; maximum 2097152 cells (8 MiB)");
            // Freeze only an active event-owned plane. Ordinary layout tiles remain ROM-backed.
            int[] cells = new int[(int) count];
            for (int y = 0; y < height; y++) for (int sx = 0; sx < width; sx++)
                cells[y * width + sx] = owner.foregroundDescriptorAt(sx * 8, y * 8);
            foregroundDescriptors = (sx, sy) -> cells[Math.floorMod(Math.floorDiv(sy, 8), height) * width
                    + Math.floorMod(Math.floorDiv(sx, 8), width)];
        }
        var window = provider == null ? null : provider.foregroundWindow();
        if (window != null) window = new com.openggf.graphics.ForegroundWindow(window.widthTiles(),
                window.heightTiles(), window.top(), window.descriptors().clone());
        var split = provider instanceof com.openggf.game.internal.ForegroundVerticalScrollSplit owner
                ? owner.foregroundVerticalScrollSplit() : null;
        return new BackgroundBuildParameters(provider != null && provider.bgWrapsHorizontally(),
                provider != null && provider.foregroundWrapsHorizontally(),
                provider != null && provider.useLinearBackgroundLayoutOverflow(level.currentZone),
                runtime != null && runtime.requiresFullWidthBgTilemap(),
                runtime != null && runtime.usesPersistentBackgroundVdpPlane(),
                provider != null && provider.useFullWidthBackgroundTilemapWindow(level.currentZone, level.currentAct,
                        x, level.cachedBgContiguousWidthPx),
                provider == null ? -1 : provider.backgroundLoopBandBaseY(level.currentZone, level.currentAct),
                provider != null && provider.isIntroOceanPhaseActive(level.currentZone, level.currentAct), projection, foregroundRevision, foregroundRows, foregroundDescriptors, window, split);
    }
}

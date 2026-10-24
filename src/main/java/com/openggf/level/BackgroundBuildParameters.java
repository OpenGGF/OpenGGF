package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.game.internal.BackgroundBuildProjection;
import com.openggf.game.internal.BackgroundBuildProjectionSource;
import com.openggf.game.internal.BackgroundDescriptorOverride;
import com.openggf.game.internal.RetainedPlaneDescriptorSource;

/** Native-published background build policy; draw-only sampling reads no live event owners. */
record BackgroundBuildParameters(boolean wraps, boolean foregroundWraps, boolean linearOverflow,
                                 boolean fullWidthRequired, boolean persistentPlane, boolean fullWidthWindow,
                                 int loopBandY, boolean introOcean, BackgroundBuildProjection projection,
                                 long foregroundRevision, int foregroundRingRows,
                                 java.util.function.IntBinaryOperator foregroundDescriptors,
                                 com.openggf.graphics.ForegroundWindow foregroundWindow,
                                 com.openggf.graphics.ForegroundScrollSplit foregroundSplit,
                                 RetainedImage retainedForeground, RetainedImage retainedBackground,
                                 ArenaBounds arenaBounds, boolean foregroundVerticalWrap) {
    BackgroundBuildParameters {
        java.util.Objects.requireNonNull(retainedForeground, "captured foreground authority");
        java.util.Objects.requireNonNull(retainedBackground, "captured background authority");
        foregroundWindow = copyWindow(foregroundWindow);
    }

    @Override public com.openggf.graphics.ForegroundWindow foregroundWindow() {
        return copyWindow(foregroundWindow);
    }

    private static com.openggf.graphics.ForegroundWindow copyWindow(com.openggf.graphics.ForegroundWindow window) {
        return window == null ? null : new com.openggf.graphics.ForegroundWindow(window.widthTiles(),
                window.heightTiles(), window.top(), window.descriptors().clone());
    }

    /** Raw native arena domain and clock, captured once with the background build policy. */
    record ArenaBounds(int minX, int maxX, int noiseFrame, boolean wraps) { }

    /** Complete immutable native descriptor words. Object identity is the capture generation.
     * An inactive image is an explicit frozen decision, never permission to query a live owner.
     */
    static final class RetainedImage implements RetainedPlaneDescriptorSource.Plane {
        private static final RetainedImage INACTIVE = new RetainedImage();
        private final int width, height;
        private final long revision;
        private final int[] words;

        private RetainedImage() {
            width = height = 0;
            revision = 0;
            words = new int[0];
        }

        RetainedImage(int width, int height, long revision, int[] descriptors) {
            validateDimensions(width, height);
            if (revision == 0) throw new IllegalArgumentException("Active retained image requires a nonzero revision");
            if (descriptors == null || descriptors.length != width * height)
                throw new IllegalArgumentException("Retained descriptor image must contain every plane cell");
            this.width = width;
            this.height = height;
            this.revision = revision;
            words = descriptors.clone();
            for (int i = 0; i < words.length; i++) words[i] &= 0xffff;
        }

        boolean active() { return revision != 0; }
        @Override public int widthTiles() { return width; }
        @Override public int heightTiles() { return height; }
        @Override public long revision() { return revision; }
        int[] descriptors() { return words.clone(); }
        @Override public int descriptorAt(int pixelX, int pixelY) {
            if (!active()) throw new IllegalStateException("Inactive captured plane has no descriptors");
            return words[Math.floorMod(Math.floorDiv(pixelY, 8), height) * width
                    + Math.floorMod(Math.floorDiv(pixelX, 8), width)];
        }

        private static void validateDimensions(int width, int height) {
            if (!validDimension(width) || !validDimension(height))
                throw new IllegalArgumentException("Retained plane dimensions must be positive powers of two <=128: "
                        + width + "x" + height);
        }
        private static boolean validDimension(int value) {
            return value > 0 && value <= 128 && (value & (value - 1)) == 0;
        }

        private static RetainedImage capture(RetainedPlaneDescriptorSource.Plane plane,
                                             long revision, int width, int height) {
            if (revision == 0) return INACTIVE;
            int[] words = new int[width * height];
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
                words[y * width + x] = plane.descriptorAt(x * 8, y * 8);
            return new RetainedImage(width, height, revision, words);
        }
    }
    /** Tile-build-only compatibility shape: no arena metadata is available.
     * Cinematic arena sampling rejects this construction instead of querying live owners.
     */
    BackgroundBuildParameters(boolean wraps, boolean foregroundWraps, boolean linearOverflow,
                              boolean fullWidthRequired, boolean persistentPlane, boolean fullWidthWindow,
                              int loopBandY, boolean introOcean, BackgroundBuildProjection projection) {
        this(wraps, foregroundWraps, linearOverflow, fullWidthRequired, persistentPlane, fullWidthWindow,
                loopBandY, introOcean, projection, 0, 0, null, null, null,
                RetainedImage.INACTIVE, RetainedImage.INACTIVE, null, false);
    }
    static BackgroundBuildParameters capture(LevelManager level) {
        if (level == null) return null;
        var provider = level.zoneFeatureProvider;
        var retainedSource = provider instanceof RetainedPlaneDescriptorSource source ? source : null;
        var foregroundPlane = retainedSource == null ? null : retainedSource.foregroundPlane();
        var backgroundPlane = retainedSource == null ? null : retainedSource.backgroundPlane();
        long fgRevision = foregroundPlane == null ? 0 : foregroundPlane.revision();
        long bgRevision = backgroundPlane == null ? 0 : backgroundPlane.revision();
        int fgWidth = fgRevision == 0 ? 0 : foregroundPlane.widthTiles();
        int fgHeight = fgRevision == 0 ? 0 : foregroundPlane.heightTiles();
        int bgWidth = bgRevision == 0 ? 0 : backgroundPlane.widthTiles();
        int bgHeight = bgRevision == 0 ? 0 : backgroundPlane.heightTiles();
        // Validate both complete images before reading any descriptor from either owner.
        if (fgRevision != 0) RetainedImage.validateDimensions(fgWidth, fgHeight);
        if (bgRevision != 0) RetainedImage.validateDimensions(bgWidth, bgHeight);
        var retainedForeground = RetainedImage.capture(foregroundPlane, fgRevision, fgWidth, fgHeight);
        var retainedBackground = RetainedImage.capture(backgroundPlane, bgRevision, bgWidth, bgHeight);
        var registry = GameServices.zoneRuntimeRegistryOrNull();
        var runtime = registry == null ? null : registry.current();
        var scroll = level.parallaxManager;
        int x = scroll == null ? Integer.MIN_VALUE : scroll.getBgCameraX();
        var projection = retainedBackground.active() ? new BackgroundBuildProjection(0, null, ignored -> 0)
                : provider instanceof BackgroundBuildProjectionSource source ? source.captureBackgroundBuild() : null;
        if (projection == null) {
            long unsupportedRevision = provider instanceof BackgroundDescriptorOverride owner ? owner.backgroundDescriptorRevision() : 0;
            double base = provider == null ? 0 : provider.getVdpNametableBase(level.currentZone,
                    level.currentAct, level.camera.getX(), Math.max(1, level.cachedBgWidthPx / 8));
            projection = new BackgroundBuildProjection(unsupportedRevision, unsupportedRevision == 0 ? null : (sx, sy) -> {
                throw new IllegalStateException("Directed background descriptor owner must supply an immutable background build projection");
            }, ignored -> base);
        }
        long foregroundRevision = !retainedForeground.active()
                && provider instanceof com.openggf.game.internal.ForegroundDescriptorOverride owner
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
        // Keep the existing policy getter order, and carry the resolved foreground wrap
        // into arena metadata rather than invoking its owner a second time.
        boolean wraps = provider != null && provider.bgWrapsHorizontally();
        boolean foregroundWraps = provider != null && provider.foregroundWrapsHorizontally();
        return new BackgroundBuildParameters(wraps, foregroundWraps,
                provider != null && provider.useLinearBackgroundLayoutOverflow(level.currentZone),
                runtime != null && runtime.requiresFullWidthBgTilemap(),
                runtime != null && runtime.usesPersistentBackgroundVdpPlane(),
                provider != null && provider.useFullWidthBackgroundTilemapWindow(level.currentZone, level.currentAct,
                        x, level.cachedBgContiguousWidthPx),
                provider == null ? -1 : provider.backgroundLoopBandBaseY(level.currentZone, level.currentAct),
                provider != null && provider.isIntroOceanPhaseActive(level.currentZone, level.currentAct), projection, foregroundRevision, foregroundRows, foregroundDescriptors, window, split,
                retainedForeground, retainedBackground,
                LevelScrollPresentation.captureArenaBounds(level, foregroundWraps), level.verticalWrapEnabled);
    }
}

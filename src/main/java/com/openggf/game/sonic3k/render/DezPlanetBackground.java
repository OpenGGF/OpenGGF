package com.openggf.game.sonic3k.render;

import com.openggf.game.internal.BackgroundColumnRemap.Columns;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.util.ShortIndexedView;

/**
 * Widescreen presentation extension of DEZ2's cropped, fixed ROM planet. The native 320px image is
 * unchanged. Outside it, reflected 64px surface strips follow a circle inferred from the ROM
 * silhouette, without new art. This is an engine presentation choice, not a cartridge deformation
 * routine.
 */
public final class DezPlanetBackground {
    private Level sourceLevel;
    private int sourceWidth;
    private int sourceNativeLeft;
    private int sourceEndY;
    private Columns columns;
    private Level projectionLevel;
    private int projectionEndY;
    private Horizon horizon;
    private Horizon projectionHorizon;
    private com.openggf.game.internal.BackgroundColumnProjection projection;

    public Columns columns(LevelManager manager, int width) {
        return columns(manager, width, Integer.MAX_VALUE);
    }

    public Columns columns(LevelManager manager, int width, int endY) {
        return columns(manager, width, (width - 320) / 2, endY);
    }

    /**
     * {@code nativeLeft} is where the native 320px window starts in the raster: centred in
     * a wide gameplay camera, but 0 when a native camera is drawn into a wider raster.
     */
    public Columns columns(LevelManager manager, int width, int nativeLeft, int endY) {
        Level level = manager.getCurrentLevel();
        if (level == null || width <= 320) return null;
        if (sourceLevel == level && sourceWidth == width && sourceNativeLeft == nativeLeft
                && sourceEndY == endY) return columns;
        var captured = captureHorizon(manager, endY);
        columns = captured == null ? null : captured.columns(width, nativeLeft);
        sourceLevel = level;
        sourceWidth = width;
        sourceNativeLeft = nativeLeft;
        sourceEndY = endY;
        return columns;
    }

    /** Freeze the indexed ROM silhouette once; new director widths never query the live level. */
    public com.openggf.game.internal.BackgroundColumnProjection captureProjection(
            LevelManager manager, int endY) {
        var captured = captureHorizon(manager, endY);
        if (captured == null) return null;
        if (projection == null || projectionHorizon != captured)
            projection = view -> captured.columns(view.width(), (view.width() - 320) / 2);
        projectionHorizon = captured;
        return projection;
    }

    private record Horizon(int[] heights, int minimum, int first, int last, int endY) {
        Columns columns(int width, int nativeLeft) {
            return projectedColumns(heights, minimum, first, last, width, nativeLeft, endY);
        }
    }

    private Horizon captureHorizon(LevelManager manager, int endY) {
        Level level = manager.getCurrentLevel();
        if (level == null) return null;
        if (projectionLevel == level && projectionEndY == endY && horizon != null)
            return horizon;
        int[] heights = new int[320];
        int minimum = 224, first = 0, last = 0;
        for (int x = 0; x < 320; x++) {
            // Read the indexed art, not live RGB: a palette fade must not
            // change the inferred silhouette or get cached as another shape.
            int space = paletteIndex(manager, level, x, 0);
            while (heights[x] < 224 && paletteIndex(manager, level, x, heights[x]) == space)
                heights[x]++;
            if (heights[x] < minimum) {
                minimum = heights[x];
                first = x;
            }
            if (heights[x] == minimum) last = x;
        }

        horizon = new Horizon(heights, minimum, first, last, endY);
        projectionLevel = level;
        projectionEndY = endY;
        return horizon;
    }

    private static Columns projectedColumns(
            int[] horizon, int minimum, int first, int last, int width, int nativeLeft, int endY) {
        if (width <= 320) return null;
        // Three authored silhouette points determine the circle. The centre of
        // the flat, pixel-rounded apex avoids selecting its first lit column.
        double apexX = (first + last) / 2.0;
        double leftY = horizon[0], rightY = horizon[319];
        double a = 2 * apexX, b = 2 * (minimum - leftY);
        double c = apexX * apexX + minimum * minimum - leftY * leftY;
        double d = 638, e = 2 * (rightY - leftY);
        double f = 319 * 319 + rightY * rightY - leftY * leftY;
        double determinant = a * e - d * b;
        if (minimum == 224 || determinant == 0) return null;
        double centreX = (c * e - f * b) / determinant;
        double centreY = (a * f - d * c) / determinant;
        double radius = Math.hypot(centreX, centreY - leftY);
        short[] xs = new short[width], ys = new short[width];
        for (int x = 0; x < width; x++) {
            int local = x - nativeLeft;
            int sourceX = local;
            if (local < 0 || local >= 320) {
                boolean left = local < 0;
                int reflected = Math.floorMod(left ? -local - 1 : local - 320, 128);
                sourceX = reflected < 64 ? reflected : 127 - reflected;
                if (!left) sourceX = 319 - sourceX;
                int edge = left ? 0 : 319;
                double curve = height(local, centreX, centreY, radius);
                double boundary = height(edge, centreX, centreY, radius);
                int target = horizon[edge] + (int) Math.round(curve - boundary);
                ys[x] = (short) (target - horizon[sourceX]);
            }
            xs[x] = (short) sourceX;
        }
        return new Columns(view(xs), view(ys), endY);
    }

    private static double height(int x, double cx, double cy, double radius) {
        return cy - Math.sqrt(Math.max(0, radius * radius - (x - cx) * (x - cx)));
    }

    private static int paletteIndex(LevelManager manager, Level level, int x, int y) {
        int descriptor = manager.getBackgroundTileDescriptorAtWorld(x, y);
        int index =
                level.getPattern(descriptor & 0x7FF)
                        .getPixel(
                                (descriptor & 0x800) != 0 ? 7 - (x & 7) : x & 7,
                                (descriptor & 0x1000) != 0 ? 7 - (y & 7) : y & 7);
        return ((descriptor >>> 13) & 3) * 16 + index;
    }

    private static ShortIndexedView view(short[] values) {
        return new ShortIndexedView() {
            public int size() {
                return values.length;
            }

            public short get(int index) {
                return values[index];
            }
        };
    }
}

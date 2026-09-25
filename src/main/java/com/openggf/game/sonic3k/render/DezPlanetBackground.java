package com.openggf.game.sonic3k.render;

import com.openggf.game.internal.BackgroundColumnRemap.Columns;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.util.ShortIndexedView;

/**
 * Widescreen presentation extension of DEZ2's cropped, fixed ROM planet.
 * The native 320px image is unchanged. Outside it, reflected 64px surface
 * strips follow a circle inferred from the ROM silhouette, without new art.
 * This is an engine presentation choice, not a cartridge deformation routine.
 */
public final class DezPlanetBackground {
    private Level sourceLevel;
    private int sourceWidth;
    private int sourceEndY;
    private Columns columns;

    public Columns columns(LevelManager manager, int width) {
        return columns(manager, width, Integer.MAX_VALUE);
    }

    public Columns columns(LevelManager manager, int width, int endY) {
        Level level = manager.getCurrentLevel();
        if (level == null || width <= 320) return null;
        if (sourceLevel == level && sourceWidth == width && sourceEndY == endY) return columns;
        int[] horizon = new int[320];
        int minimum = 224, first = 0, last = 0;
        for (int x = 0; x < 320; x++) {
            // Read the indexed art, not live RGB: a palette fade must not
            // change the inferred silhouette or get cached as another shape.
            int space = paletteIndex(manager, level, x, 0);
            while (horizon[x] < 224 && paletteIndex(manager, level, x, horizon[x]) == space) horizon[x]++;
            if (horizon[x] < minimum) { minimum = horizon[x]; first = x; }
            if (horizon[x] == minimum) last = x;
        }
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
            int local = x - (width - 320) / 2;
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
        columns = new Columns(view(xs), view(ys), endY);
        sourceLevel = level;
        sourceWidth = width;
        sourceEndY = endY;
        return columns;
    }

    private static double height(int x, double cx, double cy, double radius) {
        return cy - Math.sqrt(Math.max(0, radius * radius - (x - cx) * (x - cx)));
    }

    private static int paletteIndex(LevelManager manager, Level level, int x, int y) {
        int descriptor = manager.getBackgroundTileDescriptorAtWorld(x, y);
        int index = level.getPattern(descriptor & 0x7FF).getPixel(
                (descriptor & 0x800) != 0 ? 7 - (x & 7) : x & 7,
                (descriptor & 0x1000) != 0 ? 7 - (y & 7) : y & 7);
        return ((descriptor >>> 13) & 3) * 16 + index;
    }

    private static ShortIndexedView view(short[] values) {
        return new ShortIndexedView() {
            public int size() { return values.length; }
            public short get(int index) { return values[index]; }
        };
    }
}

package com.openggf.mods.ui;

import java.util.Objects;

/** Small drawing operations shared by scene and level-overlay canvases. */
@com.openggf.game.ModApi
public final class UiPrimitives {
    private UiPrimitives() { }
    public static void frame(PixelCanvas c, int x, int y, int w, int h, int argb) {
        Objects.requireNonNull(c, "canvas");
        if (w <= 0 || h <= 0) return;
        c.fill(x, y, w, 1, argb); c.fill(x, y + h - 1, w, 1, argb);
        c.fill(x, y, 1, h, argb); c.fill(x + w - 1, y, 1, h, argb);
    }
    public static void gradient(PixelCanvas c, int x, int y, int w, int h, int top, int bottom) {
        Objects.requireNonNull(c, "canvas");
        if (w <= 0 || h <= 0) return;
        if (h > 4096) throw new IllegalArgumentException("Gradient height exceeds 4096");
        for (int row = 0; row < h; row++) {
            double amount = h == 1 ? 0 : (double) row / (h - 1);
            int color = 0;
            for (int shift = 0; shift <= 24; shift += 8) {
                int a = (top >>> shift) & 255, b = (bottom >>> shift) & 255;
                color |= (int) Math.round(a + (b - a) * amount) << shift;
            }
            c.fill(x, y + row, w, 1, color);
        }
    }
    public static void meter(PixelCanvas c, int x, int y, int w, int h, int value, int maximum,
                             int background, int foreground) {
        Objects.requireNonNull(c, "canvas");
        if (maximum < 1) throw new IllegalArgumentException("Positive meter maximum required");
        if (w <= 0 || h <= 0) return;
        c.fill(x, y, w, h, background);
        int filled = (int) ((long) w * Math.clamp(value, 0, maximum) / maximum);
        if (filled > 0) c.fill(x, y, filled, h, foreground);
    }
}

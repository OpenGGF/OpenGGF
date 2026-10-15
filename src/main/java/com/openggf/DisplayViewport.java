package com.openggf;

/** Framebuffer-pixel viewport; both axes use the same scale so game pixels stay square. */
final class DisplayViewport {
    record Bounds(int x, int y, int width, int height) { }

    static Bounds fit(int width, int height, int nativeWidth, int nativeHeight, boolean integer) {
        if (width <= 0 || height <= 0) return new Bounds(0, 0, 0, 0);
        double scale = Math.min((double) width / nativeWidth, (double) height / nativeHeight);
        // Small windows must downscale instead of clipping a minimum 1x viewport.
        if (integer && scale >= 1) scale = Math.floor(scale);
        int w = Math.max(1, (int) Math.floor(nativeWidth * scale));
        int h = Math.max(1, (int) Math.floor(nativeHeight * scale));
        return new Bounds((width - w) / 2, (height - h) / 2, w, h);
    }
}

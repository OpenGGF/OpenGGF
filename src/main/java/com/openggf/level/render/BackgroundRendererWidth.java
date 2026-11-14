package com.openggf.level.render;

/**
 * Engine-side entry for the picture width of the shared {@link BackgroundRenderer}: its per-column
 * VScroll buffer and the width one pass composes for, kept out of its {@code @ModApi} surface.
 */
public final class BackgroundRendererWidth {
    private BackgroundRendererWidth() { }

    public static void apply(BackgroundRenderer renderer, int screenWidth) {
        renderer.applyResolvedDisplayWidth(screenWidth);
    }

    /**
     * Composes for a fixed-width target (a native Competition view is 320 columns at every display
     * width) until called again with zero.
     */
    public static void pin(BackgroundRenderer renderer, int width) {
        renderer.pinDisplayWidth(width);
    }
}

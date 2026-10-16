package com.openggf.level.render;

/**
 * Engine-side entry for resizing the shared {@link BackgroundRenderer}'s per-column VScroll buffer,
 * kept out of its {@code @ModApi} surface.
 */
public final class BackgroundRendererWidth {
    private BackgroundRendererWidth() { }

    public static void apply(BackgroundRenderer renderer, int screenWidth) {
        renderer.applyResolvedDisplayWidth(screenWidth);
    }
}

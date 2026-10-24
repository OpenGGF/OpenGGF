package com.openggf.graphics;

/** Internal offscreen host bridge; presentation never changes gameplay configuration. */
public final class GraphicsPresentationViewport {
    private GraphicsPresentationViewport() { }

    public static void withViewport(GraphicsManager graphics, int width, int height, Runnable draw) {
        graphics.withPresentationViewport(width, height, draw);
    }
}

package com.openggf.testmode;

import com.openggf.graphics.PixelFontTextRenderer;

/** Render contract shared by level and special-stage visual trace sessions. */
public interface TraceSessionOverlay {
    void render(PixelFontTextRenderer textRenderer);

    default void handleInput(com.openggf.control.InputHandler input) { }

    default boolean detailsVisible() { return false; }
}

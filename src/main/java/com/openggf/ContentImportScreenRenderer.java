package com.openggf;

import com.openggf.graphics.MenuPixelFont;
import com.openggf.graphics.TexturedQuadRenderer;

import java.io.IOException;

/** Render-thread-owned menu resources shared by title and editor import entry points. */
public final class ContentImportScreenRenderer implements AutoCloseable {
    private MenuPixelFont font;
    private TexturedQuadRenderer renderer;

    public void render(ContentImportScreen screen, float[] projection, int width) {
        if (!screen.isOpen()) return;
        if (renderer == null) {
            var created = new TexturedQuadRenderer();
            var createdFont = new MenuPixelFont();
            try {
                created.init(); createdFont.init("pixel-font.png", created);
                renderer = created; font = createdFont;
            } catch (IOException | RuntimeException failure) {
                createdFont.cleanup(); created.cleanup();
                throw new IllegalStateException("Unable to render content import screen", failure);
            }
        }
        renderer.setProjectionMatrix(projection);
        font.beginMegaBatch();
        screen.render(font, width);
        font.endMegaBatch();
    }

    @Override public void close() {
        if (font != null) { font.cleanup(); font = null; }
        if (renderer != null) { renderer.cleanup(); renderer = null; }
    }
}

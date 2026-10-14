package com.openggf.editor.project;

import com.openggf.graphics.MenuPixelFont;
import com.openggf.graphics.TexturedQuadRenderer;

import java.io.IOException;

/** Render-thread-owned resources for the project modal on both title and editor screens. */
public final class EditorProjectMenuRenderer implements AutoCloseable {
    private MenuPixelFont font;
    private TexturedQuadRenderer renderer;

    public void render(EditorProjectMenu menu, float[] projection, int width) {
        if (!menu.isOpen()) return;
        if (renderer == null) {
            var created = new TexturedQuadRenderer();
            var createdFont = new MenuPixelFont();
            try {
                created.init(); createdFont.init("pixel-font.png", created);
                renderer = created; font = createdFont;
            } catch (IOException | RuntimeException failure) {
                createdFont.cleanup(); created.cleanup();
                throw new IllegalStateException("Unable to render editor project menu", failure);
            }
        }
        renderer.setProjectionMatrix(projection);
        font.beginMegaBatch();
        menu.render(font, width);
        font.endMegaBatch();
    }

    @Override public void close() {
        if (font != null) { font.cleanup(); font = null; }
        if (renderer != null) { renderer.cleanup(); renderer = null; }
    }
}

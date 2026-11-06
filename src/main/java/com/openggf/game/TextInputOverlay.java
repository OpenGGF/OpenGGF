package com.openggf.game;

import com.openggf.control.InputHandler;
import com.openggf.control.InputHandlerInternalAccess;
import com.openggf.graphics.MenuPixelFont;
import com.openggf.graphics.TexturedQuadRenderer;
import java.io.IOException;

/** Host preedit for creator scenes and courses that use the existing text-capture API. */
public final class TextInputOverlay implements AutoCloseable {
    private MenuPixelFont font;
    private TexturedQuadRenderer renderer;
    public void render(InputHandler input, float[] projection, int width, int height) {
        if (input == null) return;
        var session = InputHandlerInternalAccess.textInput(input);
        var composition = session.legacyComposition();
        if (composition.isEmpty()) return;
        if (font == null) {
            renderer = new TexturedQuadRenderer();
            font = new MenuPixelFont();
            try { renderer.init(); font.init(null, renderer); }
            catch (IOException | RuntimeException failure) { close(); throw new IllegalStateException("Unable to render text input", failure); }
        }
        renderer.setProjectionMatrix(projection);
        font.setLanguage(com.openggf.graphics.text.HostTextContext.current());
        var line = font.shape(composition.text(), 1);
        int room = Math.max(1, width - 30), position = line.caretX(composition.caret());
        int offset = Math.max(0, position - room + 1);
        int top = height - line.metrics().cellHeight() - line.below() - 8;
        font.beginMegaBatch();
        font.fillRect(8, top - line.above() - 3, width - 16, line.metrics().lineHeight() + 8, .012f, .025f, .09f, 1);
        font.drawTextWindow(line, 15, top, room, offset, 1, 1, 1, 1, 1);
        CompositionPresentation.underline(font, line, composition, 0, 15, top, room, offset);
        font.fillRect(15 + position - offset, top - line.above(), 1, line.metrics().lineHeight(), .3f, .9f, 1, 1);
        font.endMegaBatch();
        session.legacyCaretRectangle(15 + position - offset, top - line.above(), 1, line.metrics().lineHeight());
    }
    @Override public void close() {
        if (font != null) { font.cleanup(); font = null; }
        if (renderer != null) { renderer.cleanup(); renderer = null; }
    }
}

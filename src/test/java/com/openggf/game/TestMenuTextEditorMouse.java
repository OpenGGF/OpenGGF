package com.openggf.game;

import com.openggf.control.InputHandler;
import com.openggf.graphics.PixelFont;
import com.openggf.i18n.UiMessages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** Mouse targets come from the same native/tall key rectangles that the editor draws. */
@Isolated
class TestMenuTextEditorMouse {
    private String previousLanguage;
    private com.openggf.graphics.text.TextLanguage previousTextLanguage;

    @org.junit.jupiter.api.BeforeEach
    void englishGeometry() {
        previousLanguage = UiMessages.locale().toLanguageTag();
        previousTextLanguage = com.openggf.graphics.text.HostTextContext.current();
        UiMessages.selectLanguage("en");
    }

    @org.junit.jupiter.api.AfterEach
    void restoreLanguage() {
        UiMessages.selectLanguage(previousLanguage);
        com.openggf.graphics.text.HostTextContext.activate(previousTextLanguage);
    }

    private record Draw(String text, int x, int y) { }

    private static final class Font extends PixelFont {
        final List<Draw> draws = new ArrayList<>();
        @Override public void drawText(String text, int x, int y, float scale, float r, float g, float b, float a) {
            draws.add(new Draw(text, x, y));
        }
    }

    @Test
    void mouseChoosesTheRenderedNumericKeysInNativeAndTallLayoutsAtBothWidths() {
        String previous = UiMessages.locale().toLanguageTag();
        try {
            for (String language : List.of("en", "ar")) {
                UiMessages.selectLanguage(language);
                for (int width : new int[] {320, 400}) {
                    var editor = new MenuTextEditor("Frame", "", 9, null, MenuTextEditor.Mode.INTEGER);
                    var font = new Font();
                    var input = new InputHandler();
                    clickKey(editor, font, input, width, "4");
                    clickKey(editor, font, input, width, "2");
                    assertEquals("42", editor.value(), language + " at " + width);
                    assertEquals(MenuTextEditor.Result.NONE, editor.consumeResult(), "keys do not close the field");
                    clickKey(editor, font, input, width, UiMessages.text("menu.text.set"));
                    assertEquals(MenuTextEditor.Result.ACCEPTED, editor.consumeResult(), language + " Set at " + width);
                    assertEquals("42", editor.value());
                }
            }
        } finally {
            UiMessages.selectLanguage(previous);
        }
    }

    @Test
    void keyGapsAreNotKeysAndMouseBackCancelsWithoutAccepting() {
        var editor = new MenuTextEditor("Frame", "", 9, null, MenuTextEditor.Mode.INTEGER);
        var font = new Font();
        editor.render(font, 320);
        var input = new InputHandler();
        click(editor, input, 69, 90); // Three-pixel gap between the drawn 1 and 2 cells.
        assertEquals("", editor.value());
        input.handleMouseButton(GLFW_MOUSE_BUTTON_RIGHT, GLFW_PRESS);
        editor.update(input, new MasterTitlePointer(100, 100, true, false,
                input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_RIGHT), 0));
        assertEquals(MenuTextEditor.Result.CANCELLED, editor.consumeResult());
    }

    private static void clickKey(MenuTextEditor editor, Font font, InputHandler input, int width, String key) {
        font.draws.clear();
        editor.render(font, width);
        Draw drawn = font.draws.stream().filter(d -> d.y() > 80 && d.y() < 192 && d.text().equalsIgnoreCase(key))
                .findFirst().orElseThrow(() -> new AssertionError("No rendered key " + key + ": " + font.draws));
        click(editor, input, drawn.x() + 1, drawn.y() + 1);
    }

    private static void click(MenuTextEditor editor, InputHandler input, int x, int y) {
        input.handleMouseMove(x, y);
        input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
        editor.update(input, new MasterTitlePointer(x, y, true,
                input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_LEFT), false, 0));
        input.update();
        input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE);
        editor.update(input, new MasterTitlePointer(x, y, false, false, false, 0));
        input.update();
    }
}

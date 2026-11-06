package com.openggf.editor.hud;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.control.MouseWheel;
import com.openggf.graphics.text.HostText;
import com.openggf.graphics.text.HostTextContext;
import com.openggf.graphics.text.TextFace;
import com.openggf.i18n.UiMessages;

import java.util.List;

import static com.openggf.editor.hud.EditorHudCanvas.Size.SMALL;
import static com.openggf.editor.hud.EditorHudPad.Button.*;
import static com.openggf.editor.hud.EditorHudTheme.*;
import static org.lwjgl.glfw.GLFW.*;

/** Read-only HUD text. Input stays here until Back, so reading never invokes the selected action. */
final class EditorHudReader {
    private String text;
    private int offset;
    private int capacity = 1;
    private int maximum;
    private int ticks;
    private String backLabel = "Esc";

    boolean isOpen() { return text != null; }
    void open(String value) { text = value; offset = 0; }

    void update(InputHandler input, EditorHudPad pad) {
        ticks++;
        backLabel = MenuInput.backLabel(input);
        if (MenuInput.back(input) || pad.pressed(EAST)) {
            text = null;
            pad.consumeHeld();
            return;
        }
        int dy = pad.navY() + (MenuInput.down(input) ? 1 : 0) - (MenuInput.up(input) ? 1 : 0)
                - MouseWheel.of(input).notches();
        int page = pad.navX() + (MenuInput.right(input) ? 1 : 0) - (MenuInput.left(input) ? 1 : 0);
        if (pad.repeated(LB) || input.isKeyPressed(GLFW_KEY_PAGE_UP)) page--;
        if (pad.repeated(RB) || input.isKeyPressed(GLFW_KEY_PAGE_DOWN)) page++;
        offset = Math.clamp(offset + dy + page * capacity, 0, maximum);
        if (input.isKeyPressed(GLFW_KEY_HOME)) offset = 0;
        if (input.isKeyPressed(GLFW_KEY_END)) offset = maximum;
    }

    List<String> visibleLines(int width, int height) {
        List<String> lines = HostText.shared().wrap(com.openggf.game.MenuDetailsScreen.readable(text == null ? "" : text), Math.max(1, width - 48),
                TextFace.COMPACT, HostTextContext.current());
        capacity = Math.max(1, (height - 104) / (EditorHudCanvas.lineHeight(SMALL) + 3));
        maximum = Math.max(0, lines.size() - capacity);
        offset = Math.clamp(offset, 0, maximum);
        return lines.subList(offset, Math.min(lines.size(), offset + capacity));
    }

    static String hint(String key) {
        return key + " / R3 " + UiMessages.text("menu.path.details_caps");
    }

    void draw(EditorHudCanvas canvas, EditorHudLayout layout) {
        int width = layout.width(), height = layout.height();
        canvas.rect(0, 0, width, height, SHADOW, .8f);
        canvas.panel(12, 12, width - 24, height - 24, PANEL, 1);
        canvas.text(UiMessages.text("menu.path.details_caps"), 24, 22, SMALL, GOLD, 1);
        List<String> lines = visibleLines(width, height);
        int y = 44;
        for (String line : lines) {
            canvas.text(line, 24, y, SMALL, TEXT, 1);
            y += EditorHudCanvas.lineHeight(SMALL) + 3;
        }
        String controls = UiMessages.text("menu.mods.ui.left_right_page_up_down_line")
                + "    " + backLabel + " " + UiMessages.text("menu.details.back");
        canvas.text(com.openggf.game.MenuStyle.pageText(controls, width - 48, SMALL.scale, ticks),
                24, height - 36, SMALL, TEXT_DIM, 1);
    }
}

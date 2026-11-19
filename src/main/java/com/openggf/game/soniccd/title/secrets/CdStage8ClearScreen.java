package com.openggf.game.soniccd.title.secrets;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.game.GameServices;
import com.openggf.game.MenuChrome;
import com.openggf.game.MenuFeedback;
import com.openggf.game.MenuStyle;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.soniccd.CdCommand;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.i18n.UiMessages;

import java.util.List;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glIsEnabled;

/** OpenGGF's acknowledgement for clearing the Sound Test special stage. */
final class CdStage8ClearScreen implements NativeMenuScreen {
    private static final int CARD_W = 272;
    private static final int BUTTON_W = CARD_W - 24;
    private static final int BUTTON_Y = 131;

    private final PixelFontTextRenderer text = PixelFontTextRenderer.menu();
    private InputHandler input;
    private int frame;
    private Exit exit = Exit.NONE;

    @Override public void initialize() {
        // SPCmd_LoadSS8Credits starts the disc's ending cue (SPX.asm:1144-1151).
        GameServices.audio().playMusic(CdCommand.ENDINGMUS.id());
    }

    @Override public void update(InputHandler input) {
        this.input = input;
        frame++;
        int width = GameServices.graphics().getProjectionWidth();
        var pointer = MenuInput.pointer(input, width, presentationHeight());
        if (MenuInput.accept(input) || MenuInput.back(input) || pointer.leftPressed()
                && pointer.over((width - BUTTON_W) / 2, BUTTON_Y, BUTTON_W, buttonHeight())) {
            MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
            exit = new Exit(Destination.TITLE_SCREEN, null, false);
        }
    }

    @Override public void draw() {
        var graphics = GameServices.graphics();
        if (graphics.isHeadlessMode()) return;
        int width = graphics.getProjectionWidth();
        text.setProjectionMatrix(graphics.getProjectionMatrixBuffer());
        var font = text.font();
        boolean blending = glIsEnabled(GL_BLEND);
        glEnable(GL_BLEND);
        try {
            text.beginBatch();
            MenuChrome.background(font, width);
            String title = UiMessages.text("menu.soniccd.stage8.cleared");
            var lines = MenuStyle.wrap(title, CARD_W - 24, 1f);
            int pitch = MenuChrome.labelLineHeight();
            int cardY = BUTTON_Y - 39 - lines.size() * pitch;
            int buttonH = buttonHeight();
            MenuChrome.panel(font, (width - CARD_W) / 2, cardY, CARD_W,
                    BUTTON_Y + buttonH + 12 - cardY);
            MenuChrome.icon(font, MenuChrome.Icon.CHECK, (width - MenuChrome.ICON_SIZE) / 2,
                    cardY + 13, MenuChrome.CYAN);
            int y = BUTTON_Y - 12 - lines.size() * pitch;
            for (String line : lines) {
                MenuChrome.label(font, line, (width - MenuChrome.labelWidth(line)) / 2, y, MenuChrome.GOLD);
                y += pitch;
            }
            MenuChrome.button(font, (width - BUTTON_W) / 2, BUTTON_Y, BUTTON_W, buttonH,
                    UiMessages.text("menu.soniccd.stage8.back_to_title"), true, true, frame);
            MenuChrome.footer(font, width, null, List.of(
                    MenuChrome.hint(MenuChrome.confirmKey(input), UiMessages.text("hub.ui.select_caps"))),
                    MenuChrome.controller(input), frame);
        } finally {
            try {
                text.endBatch();
            } finally {
                if (!blending) glDisable(GL_BLEND);
            }
        }
    }

    private static int buttonHeight() {
        return Math.max(20, MenuChrome.compactLineHeight() + 6);
    }

    @Override public void setClearColor() {
        if (!GameServices.graphics().isHeadlessMode()) glClearColor(0, 0, 0, 1);
    }

    @Override public void reset() {
        text.cleanup();
        input = null;
        frame = 0;
        exit = Exit.NONE;
    }

    @Override public Exit consumeExit() {
        Exit next = exit;
        exit = Exit.NONE;
        return next;
    }
}

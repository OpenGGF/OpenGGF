package com.openggf.game.soniccd.backupram;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.game.GameServices;
import com.openggf.game.MenuChrome;
import com.openggf.game.MenuFeedback;
import com.openggf.game.MenuStyle;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.soniccd.program.CdSceneCoordinator;
import com.openggf.graphics.PixelFont;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.i18n.UiMessages;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.lwjgl.opengl.GL11.glClearColor;

/** OpenGGF's single-save reset prompt, reached from the Sonic CD title. */
final class CdSaveManagerScreen implements NativeMenuScreen {
    private static final Logger LOGGER = Logger.getLogger(CdSaveManagerScreen.class.getName());
    private static final int WIDTH = 320, LEFT = 12, ROW_WIDTH = WIDTH - 2 * LEFT;
    private static final int FIRST_ROW = 112, ROW_HEIGHT = 24, ROW_GAP = 6;
    private static final String KEY = "menu.soniccd.save_manager.";

    private final CdSceneCoordinator coordinator;
    private final PixelFontTextRenderer text = PixelFontTextRenderer.menu();
    private InputHandler input;
    private boolean resetSelected;
    private boolean exiting;
    private boolean writeFailed;
    private int frame;

    CdSaveManagerScreen(CdSceneCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override public void initialize() {
        resetSelected = false;
        exiting = false;
        writeFailed = false;
        frame = 0;
    }

    @Override public void update(InputHandler input) {
        if (exiting || input == null) return;
        this.input = input;
        frame++;
        var pointer = MenuInput.pointer(input, WIDTH, presentationHeight());
        if (MenuInput.back(input) || pointer.inside() && pointer.rightPressed()) {
            finish(MenuFeedback.Cue.CANCEL);
            return;
        }

        boolean clicked = false;
        if (pointer.leftPressed()) {
            if (pointer.over(LEFT, rowY(false), ROW_WIDTH, ROW_HEIGHT)) {
                resetSelected = false;
                clicked = true;
            } else if (pointer.over(LEFT, rowY(true), ROW_WIDTH, ROW_HEIGHT)) {
                resetSelected = true;
                clicked = true;
            }
        }
        boolean up = MenuInput.up(input);
        boolean down = MenuInput.down(input);
        if (!clicked && up != down && resetSelected == up) {
            resetSelected = down;
            MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE);
            return;
        }
        if (!clicked && !MenuInput.accept(input)) return;
        if (resetSelected) {
            try {
                coordinator.flow().backupRam().resetGameSave();
            } catch (UncheckedIOException failure) {
                LOGGER.log(Level.WARNING, "Could not reset the Sonic CD save", failure);
                writeFailed = true;
                resetSelected = false;
                return;
            }
        }
        finish(resetSelected ? MenuFeedback.Cue.CONFIRM : MenuFeedback.Cue.CANCEL);
    }

    private void finish(MenuFeedback.Cue cue) {
        coordinator.backupRamManagerReturned();
        MenuFeedback.emit(cue);
        exiting = true;
    }

    @Override public void draw() {
        text.setProjectionMatrix(GameServices.graphics().getProjectionMatrixBuffer());
        PixelFont font = text.font();
        text.beginBatch();
        try {
            MenuChrome.page(font, WIDTH, UiMessages.text(KEY + "title"), null, null);
            String body = UiMessages.text(KEY + (writeFailed ? "error" : "description"));
            int y = 48 + MenuChrome.compactMetrics().above();
            for (String line : MenuStyle.wrap(body, ROW_WIDTH - 12, MenuStyle.COMPACT)) {
                MenuChrome.compact(font, line, LEFT + 6, y, writeFailed ? MenuChrome.AMBER : MenuChrome.TEXT);
                y += Math.max(MenuChrome.DESCRIPTION_PITCH, MenuChrome.compactLineHeight());
            }
            drawOption(font, false, MenuChrome.Icon.CHECK, "keep");
            drawOption(font, true, MenuChrome.Icon.WARNING, "reset");
            if (input != null) {
                MenuChrome.footer(font, WIDTH, null, List.of(
                        MenuChrome.upDown(input, UiMessages.text(KEY + "move")),
                        MenuChrome.hint(MenuInput.confirmLabel(input), UiMessages.text(KEY + "choose")),
                        MenuChrome.hint(MenuInput.backLabel(input), UiMessages.text("menu.details.back"))),
                        MenuInput.controller(input), frame);
            }
        } finally {
            text.endBatch();
        }
    }

    private void drawOption(PixelFont font, boolean reset, MenuChrome.Icon icon, String label) {
        int y = rowY(reset);
        boolean selected = reset == resetSelected;
        if (selected) MenuChrome.focus(font, LEFT, y, ROW_WIDTH, ROW_HEIGHT, frame);
        else MenuChrome.panel(font, LEFT, y, ROW_WIDTH, ROW_HEIGHT);
        MenuChrome.icon(font, icon, LEFT + 8, y + 8, reset ? MenuChrome.AMBER : MenuChrome.CYAN);
        MenuChrome.compact(font, UiMessages.text(KEY + label), LEFT + 24,
                y + (ROW_HEIGHT - MenuChrome.compactLineHeight()) / 2 + MenuChrome.compactMetrics().above(),
                selected ? MenuChrome.WHITE : MenuChrome.TEXT);
    }

    private static int rowY(boolean reset) {
        return reset ? FIRST_ROW + ROW_HEIGHT + ROW_GAP : FIRST_ROW;
    }

    @Override public void setClearColor() { glClearColor(0, 0, 0, 1); }

    @Override public void reset() {
        text.cleanup();
        input = null;
        initialize();
    }

    @Override public Exit consumeExit() {
        if (!exiting) return Exit.NONE;
        exiting = false;
        return new Exit(Destination.TITLE_SCREEN, null, false);
    }
}

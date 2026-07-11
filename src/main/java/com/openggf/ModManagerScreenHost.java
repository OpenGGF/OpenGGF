package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.graphics.PixelFont;
import com.openggf.game.MasterTitleScreen;
import com.openggf.mods.ui.ModManagerScreen;

import java.util.Objects;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

/** Root composition adapter between engine input/rendering and the neutral mod UI. */
public final class ModManagerScreenHost implements MasterTitleScreen.ModManagerView {
    private final ModManagerScreen screen;

    public ModManagerScreenHost(ModManagerScreen screen) {
        this.screen = Objects.requireNonNull(screen, "screen");
    }

    @Override
    public void update(InputHandler input) {
        screen.update(menuInput(Objects.requireNonNull(input, "input")));
    }

    @Override public void render() { screen.render(); }
    @Override public boolean consumeCloseRequested() { return screen.consumeCloseRequested(); }
    @Override public void suppressInputUntilNeutral() { screen.suppressInputUntilNeutral(); }

    static ModManagerScreen.MenuInput menuInput(InputHandler input) {
        var logical = input.logical();
        boolean escape = input.isKeyPressed(GLFW_KEY_ESCAPE);
        return new ModManagerScreen.MenuInput() {
            @Override public boolean menuUp() { return logical.menuUp(); }
            @Override public boolean menuDown() { return logical.menuDown(); }
            @Override public boolean menuLeft() { return logical.menuLeft(); }
            @Override public boolean menuRight() { return logical.menuRight(); }
            @Override public boolean menuAccept() { return logical.menuAccept(); }
            @Override public boolean menuBack() { return logical.menuBack(); }
            @Override public boolean startHeld() { return logical.player1().startHeld(); }
            @Override public boolean escape() { return escape; }
        };
    }

    public static ModManagerScreen.TextSink textSink(PixelFont font) {
        Objects.requireNonNull(font, "font");
        return new ModManagerScreen.TextSink() {
            @Override public void begin() { font.beginMegaBatch(); }
            @Override public void draw(String text, int x, int y, float scale,
                                       float r, float g, float b, float a) {
                font.drawText(text, x, y, scale, r, g, b, a);
            }
            @Override public void end() { font.endMegaBatch(); }
        };
    }
}

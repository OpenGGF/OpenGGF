package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.editor.EditorCommandPalette;
import com.openggf.editor.LevelEditorController;
import com.openggf.game.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.glfw.GLFW.*;

/** The editor menu's Playtest caps are the keys the game loop really binds to the same host actions. */
class TestGameLoopEditorPlaytestShortcuts {
    @Test
    void playtestCapsAreTheKeysTheGameLoopBindsToTheSameActions() {
        var fromCursor = new AtomicInteger();
        var fromStart = new AtomicInteger();
        var palette = EditorCommandPalette.forController(new LevelEditorController());
        palette.setHostActions(fromCursor::incrementAndGet, fromStart::incrementAndGet);
        var playtests = palette.extraCommands(EditorCommandPalette.Group.PLAYTEST);
        assertEquals(List.of("Shift+Tab", "F5"), playtests.stream().map(EditorCommandPalette.HostCommand::shortcut).toList());

        var loop = new GameLoopEditorController();
        loop.setPlaytestToggleHandler(fromCursor::incrementAndGet);
        loop.setFreshStartHandler(fromStart::incrementAndGet);
        InputHandler input = new InputHandler();
        input.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        press(loop, input, GLFW_KEY_TAB);
        input.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_RELEASE);
        assertEquals(1, fromCursor.get(), "Shift+Tab runs the action the menu labels Playtest from cursor");
        press(loop, input, GLFW_KEY_F5);
        assertEquals(1, fromStart.get(), "F5 runs the action the menu labels Playtest from level start");
        playtests.forEach(command -> command.action().run());
        assertEquals(2, fromCursor.get());
        assertEquals(2, fromStart.get());
    }

    private static void press(GameLoopEditorController loop, InputHandler input, int key) {
        input.handleKeyEvent(key, GLFW_PRESS);
        loop.consumeEditorFrame(GameMode.EDITOR, input, () -> false, () -> true, () -> true);
        input.handleKeyEvent(key, GLFW_RELEASE);
        input.update();
    }
}

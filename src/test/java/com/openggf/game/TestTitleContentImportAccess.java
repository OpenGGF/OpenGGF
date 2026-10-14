package com.openggf.game;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.launch.LaunchProfileStore;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class TestTitleContentImportAccess {
    @Test
    void toolsExposeBothEditorAndImportWithHeldConfirmationOpeningOnlyOnce() {
        var screen = activeScreen();
        var imports = new AtomicInteger();
        var editors = new AtomicInteger();
        screen.setEditorProjectsOpenHandler(editors::incrementAndGet);
        TitleContentImportAccess.bind(screen, imports::incrementAndGet);
        var input = new InputHandler();
        for (int i = 0; i < 6; i++) press(screen, input, GLFW_KEY_DOWN);
        assertTrue(TitleContentImportAccess.acceptsDrop(screen), "The ordinary action list can receive a drop");
        press(screen, input, GLFW_KEY_ENTER);
        for (int i = 0; i < 3; i++) press(screen, input, GLFW_KEY_DOWN);
        input.handleKeyEvent(GLFW_KEY_ENTER, GLFW_PRESS);
        screen.update(input); screen.update(input);
        assertEquals(1, imports.get());
        assertEquals(0, editors.get());
    }

    @Test
    void dropCannotInterruptSettingsOrTitleTransitions() {
        var screen = activeScreen();
        var input = new InputHandler();
        assertTrue(TitleContentImportAccess.acceptsDrop(screen));
        for (int i = 0; i < 5; i++) press(screen, input, GLFW_KEY_DOWN);
        press(screen, input, GLFW_KEY_ENTER);
        assertFalse(TitleContentImportAccess.acceptsDrop(screen));
        press(screen, input, GLFW_KEY_ESCAPE);
        assertTrue(TitleContentImportAccess.acceptsDrop(screen));
        screen.setStateForTest(MasterTitleScreen.State.CONFIRMING);
        assertFalse(TitleContentImportAccess.acceptsDrop(screen));
    }

    @Test
    void successfulImportUsesTheExistingModManagerAndNeutralInputBoundary() {
        var screen = activeScreen();
        var opened = new AtomicInteger();
        var neutral = new AtomicInteger();
        screen.setModManagerScreenFactory(font -> new MasterTitleScreen.ModManagerView() {
            public void update(InputHandler input) { }
            public void render() { }
            public boolean consumeCloseRequested() { return false; }
            public void suppressInputUntilNeutral() { neutral.incrementAndGet(); }
        });
        screen.setModManagerOpenHandler(opened::incrementAndGet);
        TitleContentImportAccess.openMods(screen);
        assertEquals(1, neutral.get());
        assertEquals(0, opened.get(), "The real manager factory retains its normal activation boundary");
        assertFalse(TitleContentImportAccess.acceptsDrop(screen), "Drops cannot replace pending manager choices");
    }

    private static MasterTitleScreen activeScreen() {
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        var screen = new MasterTitleScreen(config, new LaunchProfileStore(config), ignored -> { });
        screen.setStateForTest(MasterTitleScreen.State.ACTIVE);
        return screen;
    }

    private static void press(MasterTitleScreen screen, InputHandler input, int key) {
        input.handleKeyEvent(key, GLFW_PRESS); screen.update(input);
        input.handleKeyEvent(key, GLFW_RELEASE); input.update(); screen.update(input);
    }
}

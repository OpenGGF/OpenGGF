package com.openggf.game;

import com.openggf.configuration.*;
import com.openggf.control.InputHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static com.openggf.configuration.SonicConfiguration.*;

class TestEngineSettingsDisplayConfirmation {
    @TempDir Path directory;

    @Test void savePreviewsAndOnlyKeepPersistsAndCloses() {
        var config = SonicConfigurationService.createStandalone(directory);
        var screen = preview(config);
        assertTrue(com.openggf.configuration.DisplaySettingsTrial.forConfig(config).active());
        assertFalse(screen.consumeCloseRequested());
        assertFalse(screen.consumeApplied());
        assertEquals("WINDOWED", SonicConfigurationService.createStandalone(directory).getString(DISPLAY_WINDOW_MODE));
        String candidate = com.openggf.configuration.DisplaySettingsTrial.forConfig(config).windowMode();
        press(screen, GLFW_KEY_UP); // Keep; safe default is Revert.
        press(screen, GLFW_KEY_ENTER);
        assertFalse(com.openggf.configuration.DisplaySettingsTrial.forConfig(config).active());
        assertTrue(screen.consumeCloseRequested());
        assertTrue(screen.consumeApplied());
        assertEquals(candidate, SonicConfigurationService.createStandalone(directory).getString(DISPLAY_WINDOW_MODE));
    }

    @Test void backAndDefaultConfirmationRestoreWithoutSaving() {
        for (int key : new int[]{GLFW_KEY_ESCAPE, GLFW_KEY_ENTER}) {
            var config = SonicConfigurationService.createStandalone(directory);
            var screen = preview(config);
            press(screen, key);
            assertFalse(com.openggf.configuration.DisplaySettingsTrial.forConfig(config).active());
            assertEquals("WINDOWED", com.openggf.configuration.DisplaySettingsTrial.forConfig(config).windowMode());
            assertFalse(screen.consumeCloseRequested());
            assertFalse(screen.consumeApplied());
        }
    }

    @Test void expiredOrCancelledTrialCannotBeConfirmedByLateInput() {
        var config = SonicConfigurationService.createStandalone(directory);
        var screen = preview(config);
        press(screen, GLFW_KEY_UP);
        com.openggf.configuration.DisplaySettingsTrial.forConfig(config).cancel(); // Engine has observed timeout while menu updates were suspended.
        press(screen, GLFW_KEY_ENTER);
        assertFalse(screen.consumeApplied());
        assertFalse(screen.consumeCloseRequested());
        assertEquals("WINDOWED", config.getString(DISPLAY_WINDOW_MODE));
    }

    @Test void saveFailureRestoresModeAndKeepsDraftForRetry() throws Exception {
        var config = SonicConfigurationService.createStandalone(directory);
        var screen = preview(config);
        java.nio.file.Files.deleteIfExists(directory.resolve("config.yaml"));
        java.nio.file.Files.createDirectory(directory.resolve("config.yaml"));
        press(screen, GLFW_KEY_UP);
        press(screen, GLFW_KEY_ENTER);
        assertFalse(com.openggf.configuration.DisplaySettingsTrial.forConfig(config).active());
        assertEquals("WINDOWED", config.getString(DISPLAY_WINDOW_MODE));
        assertFalse(screen.consumeCloseRequested());
        java.nio.file.Files.delete(directory.resolve("config.yaml"));
        press(screen, GLFW_KEY_ESCAPE);
        press(screen, GLFW_KEY_ENTER);
        assertTrue(com.openggf.configuration.DisplaySettingsTrial.forConfig(config).active(), "draft retained for another preview/save attempt");
    }

    private EngineSettingsScreen preview(SonicConfigurationService config) {
        var screen = new EngineSettingsScreen(config, null);
        int index = new EngineSettingsDraft(config).keys(EngineSettingsDraft.Category.DISPLAY).indexOf(DISPLAY_WINDOW_MODE);
        assertTrue(index >= 0);
        for (int i = 0; i < index; i++) press(screen, GLFW_KEY_DOWN);
        press(screen, GLFW_KEY_RIGHT);
        press(screen, GLFW_KEY_ESCAPE);
        press(screen, GLFW_KEY_ENTER);
        return screen;
    }

    private void press(EngineSettingsScreen screen, int key) {
        var input = new InputHandler();
        input.handleKeyEvent(key, GLFW_PRESS);
        screen.update(input);
        input.update();
        input.handleKeyEvent(key, GLFW_RELEASE);
        screen.update(input);
    }
}

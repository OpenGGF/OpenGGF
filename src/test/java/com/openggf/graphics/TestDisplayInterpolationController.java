package com.openggf.graphics;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.TitleInputOwnership;
import com.openggf.graphics.color.DisplayColorProfileController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static com.openggf.configuration.SonicConfiguration.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class TestDisplayInterpolationController {
    @TempDir Path directory;

    @Test
    void pressTogglesSavedPreferenceAndShowsTransientFeedback() {
        var config = SonicConfigurationService.createStandalone(directory);
        var controller = new DisplayInterpolationController(config);
        var input = new InputHandler();
        input.handleKeyEvent(GLFW_KEY_N, GLFW_PRESS);
        controller.update(input);
        assertTrue(config.getBoolean(DISPLAY_INTERPOLATION));
        assertTrue(SonicConfigurationService.createStandalone(directory).getBoolean(DISPLAY_INTERPOLATION));
        assertEquals("Interpolation: On", controller.notificationText());
        input.update();
        controller.update(input);
        assertTrue(config.getBoolean(DISPLAY_INTERPOLATION), "held keys must not retrigger");
        input.handleKeyEvent(GLFW_KEY_N, GLFW_RELEASE);
        input.update();
        input.handleKeyEvent(GLFW_KEY_N, GLFW_PRESS);
        controller.update(input);
        assertFalse(config.getBoolean(DISPLAY_INTERPOLATION));
        assertFalse(SonicConfigurationService.createStandalone(directory).getBoolean(DISPLAY_INTERPOLATION));
        assertEquals("Interpolation: Off", controller.notificationText());
        input.update();
        for (int i = 0; i < DisplayColorProfileController.NOTIFICATION_FRAMES; i++) controller.update(input);
        assertNull(controller.notificationText());
    }

    @Test
    void usesReboundKeyAndReadsMenuChangesBeforeToggling() {
        var config = SonicConfigurationService.createStandalone(directory);
        config.setConfigValue(DISPLAY_INTERPOLATION_TOGGLE_KEY, GLFW_KEY_F8);
        var controller = new DisplayInterpolationController(config);
        var input = new InputHandler();
        input.handleKeyEvent(GLFW_KEY_N, GLFW_PRESS);
        controller.update(input);
        assertFalse(config.getBoolean(DISPLAY_INTERPOLATION));
        config.setConfigValue(DISPLAY_INTERPOLATION, true);
        input.handleKeyEvent(GLFW_KEY_F8, GLFW_PRESS);
        controller.update(input);
        assertFalse(config.getBoolean(DISPLAY_INTERPOLATION));
        assertEquals("Interpolation: Off", controller.notificationText());
    }

    @Test
    void unboundShortcutDoesNothing() {
        var config = SonicConfigurationService.createStandalone(directory);
        config.setConfigValue(DISPLAY_INTERPOLATION_TOGGLE_KEY, -1);
        var controller = new DisplayInterpolationController(config);
        var input = new InputHandler();
        input.handleKeyEvent(GLFW_KEY_N, GLFW_PRESS);
        controller.update(input);
        controller.update(null);
        assertFalse(config.getBoolean(DISPLAY_INTERPOLATION));
        assertNull(controller.notificationText());
    }

    @Test
    void sharedDisplayRouteSuppressesShortcutForTextModalsTitleMenusAndPicker() {
        var config = SonicConfigurationService.createStandalone(directory);
        var controller = new DisplayInterpolationController(config);
        var input = new InputHandler();
        input.handleKeyEvent(GLFW_KEY_N, GLFW_PRESS);
        Runnable displayKeys = () -> controller.update(input);
        TitleInputOwnership.routeDisplay(GameMode.LEVEL, null, true, false, () -> false, displayKeys, () -> { });
        TitleInputOwnership.routeDisplay(GameMode.MASTER_TITLE_SCREEN, null, false, false, () -> false, displayKeys, () -> { });
        TitleInputOwnership.routeDisplay(GameMode.LEVEL, null, false, true, () -> true, displayKeys, () -> { });
        TitleInputOwnership.routeDisplay(GameMode.LEVEL, null, false, false, () -> true, displayKeys, () -> { });
        assertFalse(config.getBoolean(DISPLAY_INTERPOLATION));
        assertNull(controller.notificationText());
        TitleInputOwnership.routeDisplay(GameMode.LEVEL, null, false, false, () -> false, displayKeys, () -> { });
        assertTrue(config.getBoolean(DISPLAY_INTERPOLATION));
    }
}

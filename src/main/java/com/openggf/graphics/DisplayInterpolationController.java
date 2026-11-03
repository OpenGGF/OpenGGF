package com.openggf.graphics;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.graphics.color.DisplayColorProfileController;

/** Runtime shortcut; the live frame owner reads the saved preference on its next iteration. */
public final class DisplayInterpolationController {
    private final SonicConfigurationService config;
    private final int toggleKey;
    private String notificationText;
    private int notificationFramesRemaining;

    public DisplayInterpolationController(SonicConfigurationService config) {
        this.config = config;
        toggleKey = config.getInt(SonicConfiguration.DISPLAY_INTERPOLATION_TOGGLE_KEY);
    }

    public void update(InputHandler input) {
        if (input != null && toggleKey >= 0 && input.isKeyPressed(toggleKey)) {
            boolean enabled = !config.getBoolean(SonicConfiguration.DISPLAY_INTERPOLATION);
            config.setConfigValue(SonicConfiguration.DISPLAY_INTERPOLATION, enabled);
            config.saveConfig();
            notificationText = "Interpolation: " + (enabled ? "On" : "Off");
            notificationFramesRemaining = DisplayColorProfileController.NOTIFICATION_FRAMES;
            return;
        }
        if (notificationFramesRemaining > 0 && --notificationFramesRemaining == 0) {
            notificationText = null;
        }
    }

    public String notificationText() {
        return notificationText;
    }
}

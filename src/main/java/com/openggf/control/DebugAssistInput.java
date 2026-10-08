package com.openggf.control;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.debug.DebugOverlayToggle;

/** Detects debug shortcuts that assist the player; a hosted run reports them, never blocks them. */
public final class DebugAssistInput {
    private DebugAssistInput() {
    }

    public static boolean pressed(InputHandler input,
                                       SonicConfigurationService configuration) {
        for (DebugOverlayToggle toggle : DebugOverlayToggle.values()) {
            if (input.isKeyPressed(toggle.keyCode())) {
                return true;
            }
        }
        return input.isKeyPressed(configuration.getInt(SonicConfiguration.DEBUG_MODE_KEY))
                || input.isKeyPressedWithoutModifiers(configuration.getInt(
                SonicConfiguration.DEBUG_LAST_CHECKPOINT_KEY))
                || input.isKeyPressed(configuration.getInt(
                SonicConfiguration.SUPER_SONIC_DEBUG_KEY))
                || input.isKeyPressed(configuration.getInt(
                SonicConfiguration.GIVE_EMERALDS_KEY))
                || input.isKeyPressed(configuration.getInt(
                SonicConfiguration.HYPER_FORM_DEBUG_KEY))
                || input.isKeyPressed(configuration.getInt(
                SonicConfiguration.GIVE_SUPER_EMERALDS_KEY));
    }
}

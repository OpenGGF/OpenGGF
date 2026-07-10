package com.openggf.game.timeattack;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.debug.DebugOverlayToggle;

/** Detects debug inputs that taint, but never block, a timed attempt. */
public final class TimeAttackDebugInput {
    private TimeAttackDebugInput() {
    }

    public static boolean taintPressed(InputHandler input,
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
                SonicConfiguration.GIVE_EMERALDS_KEY));
    }
}

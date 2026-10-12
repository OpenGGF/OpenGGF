package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.rewind.LiveRewindManager;

/** Rewind owns restored world state; the host may still discard fresh retained input. */
final class GameLoopPacingInterruption {
    private GameLoopPacingInterruption() { }

    static boolean allows(LiveRewindManager rewind, SonicConfigurationService config, InputHandler input) {
        return !rewind.isRewindingOrReleasing()
                && !(config.getBoolean(SonicConfiguration.LIVE_REWIND_ENABLED) && input != null
                    && input.isKeyDown(config.getInt(SonicConfiguration.LIVE_REWIND_KEY)));
    }
}

package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.control.InputHandler;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameMode;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.LevelInputOverlay;
import com.openggf.game.session.SessionManager;

import java.util.function.BooleanSupplier;

/** Modal input ownership and the host's visible pause, separate from ROM pause state. */
final class GameLoopPauseInput {
    private GameLoopPauseInput() { }

    static boolean handleOverlay(GameMode mode, InputHandler input) {
        if (ExternalFrameOrInputOwnership.active(com.openggf.game.session.EngineServices.current())) return false;
        var overlay = overlay(mode);
        return overlay != null && overlay.handleInput(input);
    }

    static LevelInputOverlay overlay(GameMode mode) {
        var world = SessionManager.getCurrentWorldSession();
        if (mode != GameMode.LEVEL || world == null || world.getGameModule() == null) {
            return null;
        }
        return world.getGameModule().getGameService(LevelInputOverlay.class);
    }

    static boolean configurationPaused(GameMode mode) {
        var overlay = overlay(mode);
        return overlay != null && overlay.pausesGameplay();
    }

    static boolean userPauseAllowed(GameMode mode) {
        return switch (mode) {
            case LEVEL, TITLE_CARD, SPECIAL_STAGE, SPECIAL_STAGE_RESULTS, BONUS_STAGE -> true;
            default -> false;
        };
    }

    static boolean nextUserPaused(GameMode mode, InputHandler input, SonicConfigurationService config,
                                  boolean paused, boolean overlayOwnsPause, boolean takeoverConsumed,
                                  PlaybackDebugManager playback,
                                  BooleanSupplier requestTakeover) {
        if (!userPauseAllowed(mode)) {
            return false;
        }
        // Gamepad Start owns visible host pause and audio, not silent ROM Game_paused.
        if (!overlayOwnsPause && !takeoverConsumed && (input.isKeyPressed(config.getInt(SonicConfiguration.PAUSE_KEY))
                || (!TraceSessionLauncher.isRunFrameDriverActive()
                && !playback.isDriving(mode) && input.logical().player1().startPressed()))) {
            if (paused && requestTakeover.getAsBoolean()) {
                return false;
            }
            return !paused;
        }
        return paused;
    }

    static boolean frameStep(InputHandler input, SonicConfigurationService config, boolean paused) {
        return paused && input.isKeyPressed(config.getInt(SonicConfiguration.FRAME_STEP_KEY));
    }

    static boolean requestTakeover(GameMode mode, InputHandler input,
                                   SonicConfigurationService config, boolean paused,
                                   BooleanSupplier requestTakeover) {
        return input != null && paused && userPauseAllowed(mode)
                && input.isKeyPressed(config.getInt(SonicConfiguration.PAUSE_KEY))
                && requestTakeover.getAsBoolean();
    }

    static void updateAudio(AudioManager audio, boolean paused) {
        if (paused) {
            audio.pause();
        } else {
            audio.resume();
        }
    }
}

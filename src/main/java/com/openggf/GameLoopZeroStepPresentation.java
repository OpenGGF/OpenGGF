package com.openggf;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.GameMode;
import com.openggf.game.LevelInputOverlay;
import com.openggf.game.mutators.GameplayMutatorPacing;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Responsive host controls on slow-motion presentations with no ROM iteration or clock service. */
final class GameLoopZeroStepPresentation {
    private GameLoopZeroStepPresentation() { }

    static void run(GameMode mode, InputHandler input, SonicConfigurationService config,
                    PlaybackDebugManager playback, boolean paused, EscapeToMasterTitleController escape,
                    Predicate<LevelInputOverlay> configurationCommand, BooleanSupplier takeover,
                    Consumer<Boolean> publishPause, Runnable recordingInput,
                    BooleanSupplier eligible, GameplayMutatorPacing pacing) {
        Objects.requireNonNull(input, "InputHandler must be set before presentation").refreshLogicalSnapshot();
        try {
            boolean overlayOwnsPause = GameLoopPauseInput.handleOverlay(mode, input);
            var overlay = GameLoopPauseInput.overlay(mode);
            if (overlayOwnsPause) escape.reset();
            if (overlay != null && configurationCommand.test(overlay)) {
                pacing.clearPendingInput();
                return;
            }
            if (!overlayOwnsPause) {
                escape.update(mode, input);
                recordingInput.run();
            }
            boolean nextPaused = GameLoopPauseInput.nextUserPaused(mode, input, config, paused,
                    overlayOwnsPause, false, playback, takeover);
            if (nextPaused != paused) publishPause.accept(nextPaused);
            if (!overlayOwnsPause && eligible.getAsBoolean()) pacing.retain(input.logical());
            else pacing.clearPendingInput();
        } finally {
            input.update();
        }
    }
}

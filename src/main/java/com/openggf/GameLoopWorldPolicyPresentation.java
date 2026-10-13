package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameplayObservation.Phase;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSessionPolicyAccess;

import java.util.function.BooleanSupplier;

/** Samples policy lifetime around the ordinary pacing owner; it never schedules a native tick. */
final class GameLoopWorldPolicyPresentation {
    record Before(boolean setup, boolean paused, boolean held) { }

    private GameLoopWorldPolicyPresentation() { }

    static Before capture(GameLoop loop, TitleCardProvider titleCard) {
        boolean setup = loop.getCurrentGameMode() != GameMode.LEVEL || loop.levelManager == null
                || loop.levelManager.hasPendingInitialProcessSpritesPass()
                || titleCard != null && titleCard.isOverlayActive();
        boolean paused = loop.isPaused() || loop.gameState != null && loop.gameState.isGamePaused();
        boolean held = loop.resolveGameplayModeContext() != null
                && WorldSessionPolicyAccess.holdsGameplay(loop.resolveGameplayModeContext().getWorldSession());
        return new Before(setup, paused, held);
    }

    static void publish(GameLoop loop, Before before, TitleCardProvider titleCard, InputHandler input,
                        EngineContext services, BooleanSupplier rewinding,
                        BooleanSupplier pacingEligible, BooleanSupplier fading) {
        var context = loop.resolveGameplayModeContext();
        GameMode mode = loop.getCurrentGameMode();
        if (context != null) {
            Phase phase = rewinding.getAsBoolean() ? Phase.REWIND
                    : loop.isPaused() || loop.gameState != null && loop.gameState.isGamePaused() ? Phase.PAUSED
                    : mode == GameMode.SPECIAL_STAGE || mode == GameMode.BONUS_STAGE ? Phase.STAGE
                    : before.paused() ? Phase.PAUSED
                    : before.setup() || mode != GameMode.LEVEL ? Phase.SETUP
                    : loop.levelManager == null || loop.levelManager.hasPendingInitialProcessSpritesPass()
                        || titleCard != null && titleCard.isOverlayActive() ? Phase.SETUP
                    : !pacingEligible.getAsBoolean() || fading.getAsBoolean() ? Phase.TRANSITION
                    : before.held() ? Phase.HELD : Phase.ACTIVE;
            WorldSessionPolicyAccess.onPresentation(context, input.logical(), phase);
        } else if (mode == GameMode.TITLE_SCREEN || mode == GameMode.DATA_SELECT) {
            WorldSessionPolicyAccess.onMenuPresentation(SessionManager.getCurrentWorldSession(), input.logical(),
                    services.graphics().getProjectionWidth(), Math.max(1, (int) services.graphics().getScreenHeight()),
                    services.audio()::playSfx);
        }
    }
}

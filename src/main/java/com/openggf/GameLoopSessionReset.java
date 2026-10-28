package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2SystemAction;
import com.openggf.debug.playback.Bk2SystemActions;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.debug.playback.PlaybackInputBridge;
import com.openggf.game.GameMode;
import com.openggf.game.nativemenu.NativeMenuController;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.NativeSessionResetPolicy;
import com.openggf.game.session.SessionResetLifecycle;

/** Owns explicit movie reset boundaries and the subsequent native menu input rows. */
final class GameLoopSessionReset {
    private final GameLoop loop;
    private final EngineContext services;
    private final PlaybackDebugManager playback;
    private final PlaybackInputBridge menuInput = new PlaybackInputBridge();
    private Bk2Movie resetMovie;
    private int preparedMenuCursor = -1;
    private boolean menuInputPublished;

    GameLoopSessionReset(GameLoop loop, EngineContext services) {
        this.loop = loop;
        this.services = services;
        playback = services.playbackDebug();
    }

    /** Runs before any outgoing-world PLC token is opened. */
    boolean prepareOrConsume(InputHandler input) {
        preparedMenuCursor = -1;
        if (!Bk2SystemActions.playing(playback)) {
            releaseMenuInput(input);
            resetMovie = null;
            return false;
        }
        Bk2SystemAction action = Bk2SystemActions.current(playback);
        if (action != Bk2SystemAction.NONE) {
            var replacement = SessionResetLifecycle.restart(services,
                    action == Bk2SystemAction.POWER ? NativeSessionResetPolicy.Kind.COLD_POWER
                            : NativeSessionResetPolicy.Kind.WARM_RESET,
                    loop::retireRuntimeForSessionReset);
            loop.setGameplayMode(replacement);
            loop.initializeTitleScreenMode();
            resetMovie = Bk2SystemActions.movie(playback);
            Bk2SystemActions.completeReset(playback);
            input.update();
            return true;
        }
        if (resetMovie == null || resetMovie != Bk2SystemActions.movie(playback)) {
            releaseMenuInput(input);
            resetMovie = null;
            return false;
        }
        GameMode mode = loop.getCurrentGameMode();
        if (mode == GameMode.TITLE_SCREEN || mode == GameMode.DATA_SELECT || mode == GameMode.LEVEL_SELECT
                || NativeMenuController.handles(mode)) {
            preparedMenuCursor = playback.getCursorFrame();
            menuInput.publishImmediately(playback, input, null);
            menuInputPublished = true;
        } else {
            releaseMenuInput(input);
        }
        return false;
    }

    /** Called after a successful menu iteration, including its launch completion. */
    void completeFrame(InputHandler input) {
        int cursor = preparedMenuCursor;
        preparedMenuCursor = -1;
        if (cursor >= 0 && Bk2SystemActions.movie(playback) == resetMovie && playback.getCursorFrame() == cursor) {
            // This native menu consumed the controller sample, even though no
            // ordinary level gameplay body ran. Held A/B/C must not repeat a press.
            playback.onCurrentGameplayTickExecuted();
            playback.onLevelFrameAdvanced();
        }
        if (cursor >= 0) releaseMenuInput(input);
    }

    private void releaseMenuInput(InputHandler input) {
        if (menuInputPublished) {
            menuInput.releaseOwnedInput();
            menuInputPublished = false;
        }
    }
}

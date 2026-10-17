package com.openggf;

import com.openggf.game.GameMode;
import com.openggf.game.GameOverExit;
import com.openggf.game.GameServices;
import com.openggf.game.TitleEntryFadeSource;

/**
 * Title mode entered by a level runtime that keeps its last picture for the title's entry fade
 * ({@link TitleEntryFadeSource}). The level's own game-mode write is consumed after the level pass
 * that made it, so the mode changes in that pass, as {@code Sega_Screen} writes {@code Game_mode} 4
 * there. Each following TITLE_SCREEN frame runs one fade pass and reads no input for the title;
 * the last pass hands over to the unchanged title return. Every other exit keeps its request for
 * the existing game-over path. State lives in the source and follows rewind.
 */
final class GameLoopTitleEntryFade {
    private static TitleEntryFadeSource pendingSource() {
        var registry = GameServices.zoneRuntimeRegistryOrNull();
        return registry != null && registry.current() instanceof TitleEntryFadeSource source
                && source.titleEntryFadePending() ? source : null;
    }

    /** True while TITLE_SCREEN still shows the retained level picture. */
    static boolean showsLevel(GameMode mode) {
        return mode == GameMode.TITLE_SCREEN && pendingSource() != null;
    }

    /** After a completed level pass: take a title exit whose source owns an entry fade. */
    static boolean beginAfterLevelPass(GameLoop loop, Runnable stopMusic) {
        if (loop.levelManager.getGameOverExitRequested() != GameOverExit.TITLE_SCREEN || pendingSource() == null) {
            return false;
        }
        loop.levelManager.consumeGameOverExitRequest();
        loop.levelManager.setLevelInactiveForTransition(true);
        stopMusic.run(); // the existing title exit's audio call, unchanged
        loop.setGameMode(GameMode.TITLE_SCREEN);
        return true;
    }

    /** One TITLE_SCREEN frame of the entry fade; false when no entry fade is pending. */
    static boolean step(GameLoop loop, Runnable returnToTitle) {
        TitleEntryFadeSource source = pendingSource();
        if (source == null) {
            return false;
        }
        if (source.stepTitleEntryFade(loop.levelManager.getObjectManager().getObjectServices())) {
            returnToTitle.run();
        }
        loop.getInputHandler().update(); // the pads are read every V-int; the title does not act on them
        return true;
    }
}

package com.openggf;

import com.openggf.game.GameMode;
import com.openggf.game.LevelInputOverlay;
import com.openggf.graphics.FadeManager;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Retains a modal command until the native fade owner releases it. */
final class GameLoopConfigurationCommands {
    private LevelInputOverlay owner;
    private LevelInputOverlay.Command pending = LevelInputOverlay.Command.NONE;
    private boolean transitionHeld;

    void clear() {
        owner = null;
        pending = LevelInputOverlay.Command.NONE;
        transitionHeld = false;
    }

    /**
     * An admitted restart or hub return keeps native play and its input frozen behind
     * the fade it started, so the player cannot move, take damage or reopen the menu
     * while the screen goes black. The hold ends with that fade or its load owner.
     */
    boolean holdsTransition(GameMode mode, BooleanSupplier fadeActive) {
        if (transitionHeld && (mode != GameMode.LEVEL || !fadeActive.getAsBoolean())) transitionHeld = false;
        return transitionHeld;
    }

    void releaseTransition() {
        transitionHeld = false;
    }

    boolean handle(LevelInputOverlay overlay, Supplier<FadeManager> fade,
                   Runnable admitted, Runnable returnToHub, Runnable restart) {
        if (owner != null && owner != overlay) clear();
        var requested = overlay.consumeCommand();
        if (pending == LevelInputOverlay.Command.NONE && requested != null
                && requested != LevelInputOverlay.Command.NONE) {
            pending = requested;
            owner = overlay;
        }
        if (pending == LevelInputOverlay.Command.NONE) return false;
        if (fade.get().isActive()) {
            overlay.commandQueued(true);
            return false;
        }
        var command = pending;
        clear();
        overlay.commandQueued(false);
        admitted.run();
        transitionHeld = command == LevelInputOverlay.Command.RETURN_TO_HUB
                || command == LevelInputOverlay.Command.FULL_RESTART;
        switch (command) {
            case RESUME -> { }
            case RETURN_TO_HUB -> returnToHub.run();
            case FULL_RESTART -> restart.run();
            case NONE -> throw new IllegalStateException("Empty configuration command was admitted");
        }
        return true;
    }
}

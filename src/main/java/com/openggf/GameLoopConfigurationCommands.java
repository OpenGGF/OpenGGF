package com.openggf;

import com.openggf.game.LevelInputOverlay;
import com.openggf.graphics.FadeManager;
import java.util.function.Supplier;

/** Retains a modal command until the native fade owner releases it. */
final class GameLoopConfigurationCommands {
    private LevelInputOverlay owner;
    private LevelInputOverlay.Command pending = LevelInputOverlay.Command.NONE;

    void clear() {
        owner = null;
        pending = LevelInputOverlay.Command.NONE;
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
        switch (command) {
            case RESUME -> { }
            case RETURN_TO_HUB -> returnToHub.run();
            case FULL_RESTART -> restart.run();
            case NONE -> throw new IllegalStateException("Empty configuration command was admitted");
        }
        return true;
    }
}

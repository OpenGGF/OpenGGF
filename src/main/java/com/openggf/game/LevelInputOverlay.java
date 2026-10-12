package com.openggf.game;

import com.openggf.control.InputHandler;

/** Optional module service for an in-level modal UI, dispatched before host pause input. */
@ModApi
@FunctionalInterface
public interface LevelInputOverlay {
    @ModApi
    enum Command { NONE, RESUME, FULL_RESTART, RETURN_TO_HUB }
    /**
     * Receives live input even when host-paused. Return true while a modal UI owns Start/Enter;
     * the host then suppresses its pause toggle for this input frame. Gameplay freezing remains
     * the overlay owner's responsibility. Return false to retain ordinary host pause controls.
     */
    boolean handleInput(InputHandler input);

    /** A configuration hold freezes native play; focus regain and frame-step do not release it. */
    default boolean pausesGameplay() { return false; }

    /** View-only menu, drawn after the native scene and HUD. */
    default void drawOverlay() { }

    /** One-shot request serviced by the host through its ordinary fade/load owners. Null means NONE. */
    default Command consumeCommand() { return Command.NONE; }

    /** Host acknowledgment: a fade retains the command and the menu displays its waiting state. */
    default void commandQueued(boolean waitingForFade) { }

    /** Releases presentation resources when the disposable gameplay context closes. */
    default void close() { }
}

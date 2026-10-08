package com.openggf.game;

import com.openggf.control.InputHandler;

/** Optional module service for an in-level modal UI, dispatched before host pause input. */
@ModApi
@FunctionalInterface
public interface LevelInputOverlay {
    /**
     * Receives live input even when host-paused. Return true while a modal UI owns Start/Enter;
     * the host then suppresses its pause toggle for this input frame. Gameplay freezing remains
     * the overlay owner's responsibility. Return false to retain ordinary host pause controls.
     */
    boolean handleInput(InputHandler input);
}

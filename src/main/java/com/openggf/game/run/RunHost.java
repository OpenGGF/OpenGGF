package com.openggf.game.run;

import com.openggf.game.ModApi;
import com.openggf.mods.ui.LevelOverlayCanvas;

import java.util.List;

/**
 * The owner of a launched run: observes it, may hold it, draws over it and is told when it ends.
 * Callbacks run on the engine thread at fixed points of the frame and must not block. They
 * cannot change gameplay state; a host influences the run only through its
 * {@link com.openggf.game.session.GameplayRunPolicy}, by holding steps, and through
 * {@link RunHandle} commands applied at the next safe boundary.
 */
@ModApi
public interface RunHost {
    /** The run's level is loaded and its first step is next (on launch and after every retry). */
    default void onLevelReady(RunLevelStart start) {
    }

    /**
     * Called before each level step. Returning false holds the run this frame: no gameplay
     * step executes and no {@link RunStep} is delivered (a countdown, a lobby wait).
     */
    default boolean admitStep(RunInput input) {
        return true;
    }

    /** The step just executed. */
    default void afterStep(RunStep step) {
    }

    /** Ghosts to draw this frame; at most eight are drawn. */
    default List<GhostPose> ghosts() {
        return List.of();
    }

    /** Draws screen-space presentation above the level and stock HUD. */
    default void drawOverlay(LevelOverlayCanvas canvas) {
    }

    /** The run has ended; control is about to return to the host's scene. */
    default void onRunEnded(RunEndReason reason) {
    }
}

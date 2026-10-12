package com.openggf.game.mode;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.ModApi;

/** Session-owned alternative level rules. One call is one presentation/input row. */
@ModApi
public interface GameplayFrameController {
    /** Runs before any course clock, fade, PLC, physics or object service. */
    boolean beforeTick(CourseControl course, LogicalInputSnapshot input);
    /** Observes the completed native step, or a held row, at a stable boundary. */
    void afterTick(CourseControl course, boolean advanced);
    /**
     * Passes the supplied logical player snapshot to native movement on advanced rows.
     * The default retains neutral player input for turn-based modes. This never admits
     * gameplay on a held row or changes movie-owned sampling and button edges.
     */
    default boolean nativePlayerInput() { return false; }
    /** Retains a rolling ball through native landing/zero-speed/object transitions. */
    default boolean retainRolling() { return false; }
    /** Optional view-only scene, e.g. a read-only remote projection. */
    default boolean drawScene() { return false; }
    /** View-only rewind effect, including creator-owned reverse playback. The host honors its effect settings.
     * Returning null is equivalent to no effect; throwing remains an owner-attributed callback failure. */
    default com.openggf.game.presentation.RewindPresentation rewindPresentation() {
        return com.openggf.game.presentation.RewindPresentation.NONE;
    }
    /** Draws a view-only overlay after the level scene. */
    default void drawOverlay() { }
    /** Freezes audio presentation while a mode menu or room hold is open. */
    default boolean presentationPaused() { return false; }
    /** Competitive network sessions may disable local debug seeking. */
    default boolean allowsDebugRewind() { return true; }
    /** Consumes a request to return to the module title at a stable boundary. */
    default boolean consumeTitleRequest() { return false; }
    /** Releases owned room resources when the gameplay session closes. */
    default void close() { }
}

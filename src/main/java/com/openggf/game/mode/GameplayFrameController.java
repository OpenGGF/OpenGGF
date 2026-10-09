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
     * Pad input the native step consumes on an advanced row, asked once per advanced row after
     * {@link #beforeTick}. The default is neutral, so live input never reaches a controlled
     * course. Derive the snapshot from controller state only (for example a recorded program
     * row, with press edges from the previous row) so the row stays reproducible.
     */
    default LogicalInputSnapshot nativeInput(CourseControl course) { return LogicalInputSnapshot.neutral(); }
    /**
     * Claims the keyboard's text and editing keys while true, renewed every row; see
     * {@link com.openggf.control.InputHandler#captureKeyboardText()}. Read the row's typed
     * characters and claimed keys through {@link CourseControl}.
     */
    default boolean capturesKeyboardText() { return false; }
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

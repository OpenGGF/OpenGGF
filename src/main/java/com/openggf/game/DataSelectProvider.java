package com.openggf.game;

import com.openggf.control.InputHandler;

/**
 * Provider interface for the Data Select screen (S3K save file selection).
 * Games that support save slots implement this to manage the data select UI.
 */
public interface DataSelectProvider {

    /** Lifecycle state of the data select screen. */
    enum State { INACTIVE, FADE_IN, ACTIVE, EXITING }

    /** Initialize the data select screen (load art, palettes, layout). */
    void initialize();

    /** Update logic each frame (cursor movement, selection, input). */
    void update(InputHandler input);

    /** Draw the data select screen. */
    void draw();

    /**
     * Sets the projection-space viewport width for widescreen centering.
     *
     * <p>The data-select background tiles to fill the full viewport width; all
     * other content is centred by {@code (viewportWidth - 320) / 2}. At native
     * width 320 the offset is 0 — byte-identical. Default implementation is a
     * no-op (native behaviour).
     */
    default void setViewportWidth(int width) {
        // default: no-op (native 320)
    }

    /** Set the OpenGL clear color for the data select background. */
    void setClearColor();

    /** Reset all state (called when leaving the data select screen). */
    void reset();

    /** Returns the current lifecycle state. */
    State getState();

    /** Returns true if the screen is in the EXITING state. */
    boolean isExiting();

    /** Returns true if the screen is in the ACTIVE state. */
    boolean isActive();
}

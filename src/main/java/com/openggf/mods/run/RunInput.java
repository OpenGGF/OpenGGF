package com.openggf.mods.run;

import com.openggf.game.ModApi;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PhysicalInput;

/** Input visible to a run host while it decides whether to admit a step. */
@ModApi
public interface RunInput {
    /** Logical controller state for this frame, before the step runs. */
    LogicalInputSnapshot input();

    /**
     * This host frame's immutable physical keyboard/pad snapshot, independent of logical replay
     * input. Repeated calls return the same snapshot; disabled controllers contribute no pad
     * state or events. A scene overlay may already have drained transitions before the host is
     * called, so held state remains available but events can be empty. Use held-state edges for
     * controls shared with an overlay.
     */
    default PhysicalInput physicalInput() {
        return PhysicalInput.neutral();
    }

    /** First host frame after engine pause: rebaseline physical held-state edges before acting. */
    default boolean resumedAfterPause() { return false; }

    /** True while a physical keyboard key is held ({@code SceneKeys} code), without pad substitutions. */
    boolean keyDown(int key);

    /** True on the frame a physical keyboard key went down ({@code SceneKeys} code). */
    boolean keyPressed(int key);

    /**
     * True while the loaded level is still fading its own palette in from black over its first
     * gameplay steps (S3K's {@code Level} arms that fade; S1 and S2 levels never do). A host
     * that holds the run to show the level, such as a countdown, can run those steps with the
     * player's input held ({@link RunHost#holdsPlayerInput()}) and hold once this clears.
     */
    default boolean levelFadingIn() {
        return false;
    }
}

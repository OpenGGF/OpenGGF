package com.openggf.mods.run;

import com.openggf.game.ModApi;
import com.openggf.control.LogicalInputSnapshot;

/** Input visible to a run host while it decides whether to admit a step. */
@ModApi
public interface RunInput {
    /** Logical controller state for this frame, before the step runs. */
    LogicalInputSnapshot input();

    /** True while a key is held ({@code SceneKeys} code). */
    boolean keyDown(int key);

    /** True on the frame a key went down ({@code SceneKeys} code). */
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

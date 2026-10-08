package com.openggf.game.run;

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
}

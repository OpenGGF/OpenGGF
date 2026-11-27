package com.openggf.game.internal;

import com.openggf.control.InputHandler;

/** Game-owned native controller consumer, separate from live configuration UI. */
@FunctionalInterface
public interface NativeLevelInputOwner {
    /** Consumes the already published sample; true reserves Start from native pause. */
    boolean handleInput(InputHandler input);

    /** Handles a native exit during user pause. True releases the host pause and claims the input edge. */
    default boolean handleHostPausedInput(InputHandler input) {
        return false;
    }
}

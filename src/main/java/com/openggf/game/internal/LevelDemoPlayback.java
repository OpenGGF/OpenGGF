package com.openggf.game.internal;

import com.openggf.control.PlayerInputState;

/** Controller source for an active attract level, read at the ordinary level loop. */
public interface LevelDemoPlayback {
    PlayerInputState inputForNextIteration();

    boolean playerCanInterrupt();

    boolean hasFinished();

    /** Publishes ROM controls only for this admitted world body. */
    static <T> T withControllers(com.openggf.control.InputHandler input,
            com.openggf.control.LogicalInputSnapshot controllers, java.util.function.Supplier<T> body) {
        return com.openggf.control.InputHandlerInternalAccess.withNativeInput(input, controllers, body);
    }
}

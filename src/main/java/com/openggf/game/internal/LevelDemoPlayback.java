package com.openggf.game.internal;

import com.openggf.control.PlayerInputState;

/** Controller source for an active attract level, read at the ordinary level loop. */
public interface LevelDemoPlayback {
    PlayerInputState inputForNextIteration();

    boolean playerCanInterrupt();

    boolean hasFinished();
}

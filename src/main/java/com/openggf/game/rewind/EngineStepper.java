package com.openggf.game.rewind;

/**
 * Drives the engine forward one frame using the given inputs. Owned by
 * the visualiser / engine glue, passed into RewindController.
 */
@FunctionalInterface
@com.openggf.game.ModApi
public interface EngineStepper {
    void step(com.openggf.debug.playback.Bk2FrameInput inputs);
}

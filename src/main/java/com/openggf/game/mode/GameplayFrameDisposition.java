package com.openggf.game.mode;

import com.openggf.game.ModApi;

/** One controller decision for one input/presentation row, before any world clock runs. */
@ModApi
public enum GameplayFrameDisposition {
    /** Run the ordinary native world step with the supplied logical P1/P2 input and policy filters. */
    ADVANCE_NATIVE_INPUT,
    /** Run the native world step with neutral input; the controller owns impulses and movement. */
    ADVANCE_CONTROLLED,
    /** Hold world clocks, objects and physics. Entry presentation and audio policy remain independent. */
    HOLD
}

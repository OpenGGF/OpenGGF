package com.openggf.game.sonic1.scroll;

import com.openggf.game.internal.CheckpointBackgroundScroll;

/** Native background word bank; fractional longs and cloud RAM are not lamppost state. */
interface Sonic1CheckpointScrollHandler {
    CheckpointBackgroundScroll captureBackgroundScroll();
    void restoreBackgroundScroll(CheckpointBackgroundScroll state);
}

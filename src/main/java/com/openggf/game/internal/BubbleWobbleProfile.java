package com.openggf.game.internal;

/** Internal ROM bubble-table override; the native angle and movement owners are unchanged. */
@FunctionalInterface
public interface BubbleWobbleProfile {
    int offset(int index, int defaultOffset);
}

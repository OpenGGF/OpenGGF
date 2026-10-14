package com.openggf.game.internal;

import com.openggf.level.resources.KosinskiModuleQueue;

/** Session injection for a native module owner that publishes pattern DMA. */
public interface PatternModuleQueueOwner {
    void bindPatternModuleQueue(KosinskiModuleQueue queue);
}

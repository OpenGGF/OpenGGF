package com.openggf.level.scroll;

/**
 * Internal hook for handlers whose normal update advances logical scroll RAM.
 * Registry reconciliation rebuilds presentation from restored RAM, including
 * a snapshot taken before the handler's first ordinary update.
 */
public interface ScrollOutputRebuilder {
    void rebuildOutput(int[] horizontalScroll, int cameraX, int cameraY, int frameCounter, int actId);
}

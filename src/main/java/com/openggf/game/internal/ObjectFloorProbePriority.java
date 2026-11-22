package com.openggf.game.internal;

/** Optional sprite tile-priority write when an object floor probe reads the level layout. */
public interface ObjectFloorProbePriority {
    enum Write { KEEP, LOW, HIGH }

    Write objectFloorProbePriority(int x, int y);
}

package com.openggf.game.internal;

/** Native sprite-table producer order; independent of game names and presentation width. */
public interface SpriteTableBuildProfile {
    enum PlacedRings { OBJECT_SLOTS, BEFORE_OBJECT_BUCKETS }
    PlacedRings placedRingEmission();
}

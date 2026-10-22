package com.openggf.game.internal;

/** Native sprite-table producer order; independent of game names and presentation width. */
public interface SpriteTableBuildProfile {
    enum PlacedRings { OBJECT_SLOTS, BEFORE_OBJECT_BUCKETS }
    enum PlacedRingY { OBJECT_MAPPING, MASK_11_BEFORE_BIAS, BIAS_BEFORE_SCREEN_WRAP }
    PlacedRings placedRingEmission();
    default PlacedRingY placedRingY() { return PlacedRingY.OBJECT_MAPPING; }
    default boolean lostRingHighPriority() { return true; }
}

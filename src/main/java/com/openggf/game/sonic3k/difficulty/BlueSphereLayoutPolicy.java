package com.openggf.game.sonic3k.difficulty;

/** Optional entry-only transform of a ROM-backed native Blue Sphere layout. */
@com.openggf.game.ModApi
@FunctionalInterface
public interface BlueSphereLayoutPolicy {
    /**
     * Receives an isolated layout copy. The host validates the returned copy,
     * preserving the starting trailer, original blue/ring cells and safe start.
     */
    byte[] transformLayout(int stageIndex, byte[] romLayout);
}

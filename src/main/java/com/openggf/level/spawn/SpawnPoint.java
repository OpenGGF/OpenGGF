package com.openggf.level.spawn;

/**
 * Minimal spawn coordinate contract for windowed placement managers.
 */
@com.openggf.game.ModApi
public interface SpawnPoint {
    int x();
    int y();
}

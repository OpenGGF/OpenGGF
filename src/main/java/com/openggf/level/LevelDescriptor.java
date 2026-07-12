package com.openggf.level;

import com.openggf.game.ModApi;

/**
 * Stable metadata required to identify and enter a level.
 *
 * <p>Stock levels are represented by {@link LevelData}; mod-provided levels use
 * synthetic descriptors with level indices in the reserved {@code 0x400+} band.
 */
@ModApi
public interface LevelDescriptor {
    int levelIndex();

    int startX();

    int startY();
}

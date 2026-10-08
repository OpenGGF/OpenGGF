package com.openggf.mods.code;

import com.openggf.game.LevelEventProvider;
import com.openggf.game.ModApi;

/**
 * Constructs a fresh event provider for each load or respawn of one mod zone.
 * Stateful handlers should implement {@link RewindableZoneEvents}; stateless
 * {@link LevelEventProvider} implementations retain their ordinary lifecycle.
 */
@FunctionalInterface
@ModApi
public interface ZoneEventFactory {
    LevelEventProvider create();
}

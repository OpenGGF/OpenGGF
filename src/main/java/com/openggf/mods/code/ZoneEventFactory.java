package com.openggf.mods.code;

import com.openggf.game.LevelEventProvider;
import com.openggf.game.ModApi;

/** Creator callback that constructs the event provider for one mod zone. */
@FunctionalInterface
@ModApi
public interface ZoneEventFactory {
    LevelEventProvider create();
}

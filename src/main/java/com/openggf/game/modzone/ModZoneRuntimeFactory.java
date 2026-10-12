package com.openggf.game.modzone;

import com.openggf.game.ModApi;

/** Creates the typed runtime services for one hosted act load or respawn. */
@ModApi
@FunctionalInterface
public interface ModZoneRuntimeFactory {
    ModZoneRuntimeServices create(ModZoneRuntimeContext context);
}

package com.openggf.game.internal;

/**
 * Durable native physical DATA belonging to one world. The selected fresh-Level
 * load action clears it; the service owns no gameplay managers or registry.
 */
public interface NativePhysicalWorldData {
    void clearAtFreshLevel(NativePhysicalLevelLoad.Clear invocation);
}

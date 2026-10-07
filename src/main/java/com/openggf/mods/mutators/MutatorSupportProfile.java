package com.openggf.mods.mutators;

import java.util.Set;

/** Explicitly advertised gameplay cells. The host consumes capabilities rather than game names. */
@com.openggf.game.ModApi
public interface MutatorSupportProfile {
    Set<MutatorCapability> capabilities(int zone, int act);
    boolean supportsPlayer(String characterKey, boolean leader);
}

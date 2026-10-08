package com.openggf.mods.mutators;

import java.util.Set;

/** Explicitly advertised gameplay cells. The host consumes capabilities rather than game names. */
@com.openggf.game.ModApi
public interface MutatorSupportProfile {
    Set<MutatorCapability> capabilities(int zone, int act);
    boolean supportsPlayer(String characterKey, boolean leader);

    /** Narrow character/art support per effect; legacy providers retain their established scope. */
    default boolean supportsPlayer(MutatorCapability capability, String characterKey, boolean leader) {
        return supportsPlayer(characterKey, leader);
    }

    /** Empty when an option applies; otherwise the host displays why it is unavailable. */
    default String optionUnavailableReason(String mutatorLocalId, String optionId) { return ""; }

    /** Native location/presentation labels belong to the module, not the shared settings widget. */
    default String startLabel() { return "Start Emerald Hill"; }
    default String locationLabel(int zone, int act) { return "Sonic 2 / Emerald Hill 1 / solo Sonic"; }
}

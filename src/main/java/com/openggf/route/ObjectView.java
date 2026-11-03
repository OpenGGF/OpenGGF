package com.openggf.route;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A copied observation of one live object that reports a capability or carries a player. Its
 * type key is the owner's class name, for selection and display; shared code never switches on it.
 *
 * @param phase         the owner's current phase, empty when it reports none
 * @param hitsRemaining the owner's remaining hits, or -1 when it reports none
 */
public record ObjectView(InstanceKey instance, Optional<PlacementKey> placement, String typeKey, int centreX,
                         int centreY, Set<Capability> capabilities, List<String> phaseNames, String phase,
                         int hitsRemaining, boolean defeated) {
    public ObjectView {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(typeKey, "typeKey");
        capabilities = Set.copyOf(capabilities);
        phaseNames = List.copyOf(phaseNames);
        phase = phase == null ? "" : phase;
    }

    public boolean has(Capability capability) {
        return capabilities.contains(capability);
    }
}

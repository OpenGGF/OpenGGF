package com.openggf.mods;

import java.util.List;
import java.util.Objects;

/** Immutable process-lifetime view of descriptors selected by the Task 5 eligibility freeze. */
public final class EffectiveModCatalog {
    public static final EffectiveModCatalog EMPTY = new EffectiveModCatalog(List.of());

    private final List<ModDescriptor> orderedEnabled;

    public EffectiveModCatalog(List<ModDescriptor> orderedEnabled) {
        this.orderedEnabled = List.copyOf(Objects.requireNonNull(orderedEnabled, "orderedEnabled"));
    }

    public List<ModDescriptor> orderedEnabled() {
        return orderedEnabled;
    }
}

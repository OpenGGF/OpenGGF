package com.openggf.mods.mutators;

import com.openggf.mods.code.ModFaultBoundary;
import java.util.List;
import java.util.Objects;

/** Engine-only immutable contributions, aggregated before opening one world configuration owner. */
public record MutatorCatalog(List<OwnedMutator> definitions, ModFaultBoundary faults) {
    public MutatorCatalog {
        definitions = List.copyOf(definitions);
        Objects.requireNonNull(faults, "faults");
        var keys = new java.util.LinkedHashMap<String, OwnedMutator>();
        definitions.forEach(owned -> { if (keys.putIfAbsent(owned.key(), owned) != null)
            throw new IllegalArgumentException("Duplicate prepared mutator: " + owned.key()); });
        MutatorSessionState.validateCatalog(keys);
    }
    public MutatorCatalog append(List<OwnedMutator> added, ModFaultBoundary boundary) {
        if (faults != boundary) throw new IllegalArgumentException("Mutator catalog fault authority differs");
        var combined = new java.util.ArrayList<>(definitions); combined.addAll(added);
        return new MutatorCatalog(combined, faults);
    }
}

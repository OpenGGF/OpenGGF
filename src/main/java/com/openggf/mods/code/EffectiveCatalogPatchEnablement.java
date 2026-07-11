package com.openggf.mods.code;

import com.openggf.game.patch.PatchEnablement;
import com.openggf.game.patch.PatchOwner;
import com.openggf.mods.EffectiveModCatalog;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Boot-frozen explicit owner policy; patch ids are never used as provenance. */
public final class EffectiveCatalogPatchEnablement implements PatchEnablement {
    private final Map<String, Integer> orderByOwner;

    public EffectiveCatalogPatchEnablement(EffectiveModCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        LinkedHashMap<String, Integer> order = new LinkedHashMap<>();
        for (int index = 0; index < catalog.orderedEnabled().size(); index++) {
            String owner = catalog.orderedEnabled().get(index).manifest().id();
            if (order.putIfAbsent(owner, index) != null) {
                throw new IllegalArgumentException("Duplicate effective mod owner: " + owner);
            }
        }
        this.orderByOwner = Map.copyOf(order);
    }

    @Override public boolean isEnabled(PatchOwner owner) {
        Objects.requireNonNull(owner, "owner");
        if (owner instanceof PatchOwner.BuiltIn) return true;
        return orderByOwner.containsKey(requireKnown((PatchOwner.Mod) owner));
    }

    @Override public int orderOf(PatchOwner owner) {
        Objects.requireNonNull(owner, "owner");
        if (owner instanceof PatchOwner.BuiltIn) return BUILTIN_ORDER;
        return orderByOwner.get(requireKnown((PatchOwner.Mod) owner));
    }

    private String requireKnown(PatchOwner.Mod owner) {
        if (!orderByOwner.containsKey(owner.modId())) {
            throw new IllegalArgumentException("Unknown mod patch owner: " + owner.modId());
        }
        return owner.modId();
    }
}

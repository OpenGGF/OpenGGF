package com.openggf.mods.code;

import com.openggf.game.patch.GamePatch;
import com.openggf.level.objects.ObjectFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable output of one owner's successful private registration transaction. */
public record ModRegistrationPlan(String ownerModId, String baseGameId,
                                  Map<String, ObjectFactory> objectFactories,
                                  Map<String, BakedSheetRef> objectArt,
                                  List<GamePatch> explicitPatches) {
    public ModRegistrationPlan {
        Objects.requireNonNull(ownerModId, "ownerModId");
        Objects.requireNonNull(baseGameId, "baseGameId");
        objectFactories = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(
                Objects.requireNonNull(objectFactories, "objectFactories")));
        objectArt = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(
                Objects.requireNonNull(objectArt, "objectArt")));
        explicitPatches = List.copyOf(Objects.requireNonNull(explicitPatches, "explicitPatches"));
    }

    public boolean hasContent() {
        return !objectFactories.isEmpty() || !objectArt.isEmpty();
    }
}

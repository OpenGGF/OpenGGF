package com.openggf.mods.code;

import com.openggf.game.patch.GamePatch;
import com.openggf.io.ModAssetRoot;
import com.openggf.level.objects.BakedSheetReader;
import com.openggf.level.objects.ObjectFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable output of one owner's successful private registration transaction. */
public record ModRegistrationPlan(String ownerModId, String baseGameId,
                                  Map<String, ObjectFactory> objectFactories,
                                  Map<String, BakedSheetRef> objectArt,
                                  Map<String, BakedSheetReader.BakedSheet> preparedObjectArt,
                                  List<GamePatch> explicitPatches) {
    public ModRegistrationPlan {
        Objects.requireNonNull(ownerModId, "ownerModId");
        Objects.requireNonNull(baseGameId, "baseGameId");
        objectFactories = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(
                Objects.requireNonNull(objectFactories, "objectFactories")));
        objectArt = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(
                Objects.requireNonNull(objectArt, "objectArt")));
        preparedObjectArt = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(
                Objects.requireNonNull(preparedObjectArt, "preparedObjectArt")));
        if (!preparedObjectArt.isEmpty() && !preparedObjectArt.keySet().equals(objectArt.keySet())) {
            throw new IllegalArgumentException("Prepared object-art keys must match declared keys");
        }
        explicitPatches = List.copyOf(Objects.requireNonNull(explicitPatches, "explicitPatches"));
    }

    public ModRegistrationPlan(String ownerModId, String baseGameId,
                               Map<String, ObjectFactory> objectFactories,
                               Map<String, BakedSheetRef> objectArt,
                               List<GamePatch> explicitPatches) {
        this(ownerModId, baseGameId, objectFactories, objectArt, Map.of(), explicitPatches);
    }

    public boolean hasContent() {
        return !objectFactories.isEmpty() || !objectArt.isEmpty();
    }

    /** Resolves and validates all declared sheets before the contribution is published. */
    ModRegistrationPlan prepareObjectArt(ModAssetRoot assets) {
        Objects.requireNonNull(assets, "assets");
        if (objectArt.isEmpty()) return this;
        Map<String, BakedSheetReader.BakedSheet> prepared = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, BakedSheetRef> entry : objectArt.entrySet()) {
            String path = entry.getValue().entryPath();
            try {
                byte[] bytes = assets.readBounded(path, assets.limits().maxAssetBytes());
                prepared.put(entry.getKey(), BakedSheetReader.read(bytes, assets.limits()));
            } catch (java.io.IOException | SecurityException error) {
                throw new ModRegistrationException(ownerModId, "MOD_ART_ASSET_INVALID",
                        "Missing or invalid object-art asset: " + path, path, error);
            }
        }
        return new ModRegistrationPlan(ownerModId, baseGameId, objectFactories, objectArt,
                prepared, explicitPatches);
    }
}

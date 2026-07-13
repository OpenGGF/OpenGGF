package com.openggf.mods.code;

import com.openggf.game.patch.GamePatch;
import com.openggf.game.CharacterDefinition;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameModuleRouting;
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
                                  List<GamePatch> explicitPatches,
                                  List<ModZoneContribution> zones,
                                  List<PreparedModZone> preparedZones,
                                  Map<String, String> objectPreviewArtKeys,
                                  Map<CharacterKey, CharacterDefinition> characters,
                                  com.openggf.game.GameModule standaloneModule,
                                  Map<String, RomArtRequest> romObjectArt) {
    public ModRegistrationPlan {
        Objects.requireNonNull(ownerModId, "ownerModId");
        if ((standaloneModule == null) == (baseGameId == null)) {
            throw new IllegalArgumentException(
                    "Exactly one of baseGameId or standaloneModule must be present");
        }
        if (standaloneModule != null
                && (!GameModuleRouting.isStandalone(standaloneModule)
                || !ownerModId.equals(standaloneModule.getIdentifier())
                || !ownerModId.equals(standaloneModule.getGameCode()))) {
            throw new IllegalArgumentException("Standalone module owner/identity mismatch");
        }
        objectFactories = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(
                Objects.requireNonNull(objectFactories, "objectFactories")));
        objectArt = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(
                Objects.requireNonNull(objectArt, "objectArt")));
        preparedObjectArt = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(
                Objects.requireNonNull(preparedObjectArt, "preparedObjectArt")));
        objectPreviewArtKeys = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(
                Objects.requireNonNull(objectPreviewArtKeys, "objectPreviewArtKeys")));
        characters = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(
                Objects.requireNonNull(characters, "characters")));
        romObjectArt = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(
                Objects.requireNonNull(romObjectArt, "romObjectArt")));
        characters.forEach((key, definition) -> {
            if (!key.equals(definition.key()) || !ownerModId.equals(key.ownerModId().orElse(null))) {
                throw new IllegalArgumentException("Character contribution owner/key mismatch");
            }
        });
        if (!preparedObjectArt.isEmpty() && !preparedObjectArt.keySet().equals(objectArt.keySet())) {
            throw new IllegalArgumentException("Prepared object-art keys must match declared keys");
        }
        explicitPatches = List.copyOf(Objects.requireNonNull(explicitPatches, "explicitPatches"));
        zones = List.copyOf(Objects.requireNonNull(zones, "zones"));
        preparedZones = List.copyOf(Objects.requireNonNull(preparedZones, "preparedZones"));
        if (preparedZones.size() != zones.size()) {
            throw new IllegalArgumentException("Every declared zone must have one prepared payload");
        }
        for (int i = 0; i < zones.size(); i++) {
            ModZoneContribution declared = zones.get(i);
            PreparedModZone prepared = preparedZones.get(i);
            if (!ownerModId.equals(prepared.ownerModId())
                    || !declared.localKey().equals(prepared.localKey())
                    || !declared.insertAfter().equals(prepared.insertAfter())) {
                throw new IllegalArgumentException("Prepared zones must exactly match declarations");
            }
        }
    }

    /** Compatibility constructor for the pre-ROM-art-intake canonical shape. */
    public ModRegistrationPlan(String ownerModId, String baseGameId,
                               Map<String, ObjectFactory> objectFactories,
                               Map<String, BakedSheetRef> objectArt,
                               Map<String, BakedSheetReader.BakedSheet> preparedObjectArt,
                               List<GamePatch> explicitPatches, List<ModZoneContribution> zones,
                               List<PreparedModZone> preparedZones,
                               Map<String, String> objectPreviewArtKeys,
                               Map<CharacterKey, CharacterDefinition> characters,
                               com.openggf.game.GameModule standaloneModule) {
        this(ownerModId, baseGameId, objectFactories, objectArt, preparedObjectArt,
                explicitPatches, zones, preparedZones, objectPreviewArtKeys, characters,
                standaloneModule, Map.of());
    }

    /** Compatibility constructor for the pre-standalone canonical shape. */
    public ModRegistrationPlan(String ownerModId, String baseGameId,
                               Map<String, ObjectFactory> objectFactories,
                               Map<String, BakedSheetRef> objectArt,
                               Map<String, BakedSheetReader.BakedSheet> preparedObjectArt,
                               List<GamePatch> explicitPatches, List<ModZoneContribution> zones,
                               List<PreparedModZone> preparedZones,
                               Map<String, String> objectPreviewArtKeys,
                               Map<CharacterKey, CharacterDefinition> characters) {
        this(ownerModId, baseGameId, objectFactories, objectArt, preparedObjectArt,
                explicitPatches, zones, preparedZones, objectPreviewArtKeys, characters, null);
    }

    public ModRegistrationPlan(String ownerModId, String baseGameId,
                               Map<String,ObjectFactory> objectFactories, Map<String,BakedSheetRef> objectArt,
                               Map<String,BakedSheetReader.BakedSheet> preparedObjectArt,
                               List<GamePatch> explicitPatches, List<ModZoneContribution> zones,
                               List<PreparedModZone> preparedZones) {
        this(ownerModId,baseGameId,objectFactories,objectArt,preparedObjectArt,explicitPatches,zones,
                preparedZones,Map.of(),Map.of(),null);
    }

    public ModRegistrationPlan(String ownerModId, String baseGameId,
                               Map<String, ObjectFactory> objectFactories,
                               Map<String, BakedSheetRef> objectArt,
                               List<GamePatch> explicitPatches) {
        this(ownerModId, baseGameId, objectFactories, objectArt, Map.of(), explicitPatches,
                List.of(),List.of(),Map.of(),Map.of(),null);
    }

    public ModRegistrationPlan(String ownerModId, String baseGameId,
                               Map<String, ObjectFactory> objectFactories,
                               Map<String, BakedSheetRef> objectArt,
                               Map<String, BakedSheetReader.BakedSheet> preparedObjectArt,
                               List<GamePatch> explicitPatches) {
        this(ownerModId, baseGameId, objectFactories, objectArt, preparedObjectArt,
                explicitPatches, List.of(), List.of(),Map.of(),Map.of(),null);
    }

    /** Compatibility constructor for the Phase-2 canonical record shape. */
    public ModRegistrationPlan(String ownerModId, String baseGameId,
                               Map<String, ObjectFactory> objectFactories,
                               Map<String, BakedSheetRef> objectArt,
                               Map<String, BakedSheetReader.BakedSheet> preparedObjectArt,
                               List<GamePatch> explicitPatches, List<ModZoneContribution> zones,
                               List<PreparedModZone> preparedZones,
                               Map<String, String> objectPreviewArtKeys) {
        this(ownerModId, baseGameId, objectFactories, objectArt, preparedObjectArt,
                explicitPatches, zones, preparedZones, objectPreviewArtKeys, Map.of(),null);
    }

    public static ModRegistrationPlan characterOnly(String ownerModId, String baseGameId,
            Map<CharacterKey, CharacterDefinition> characters) {
        return new ModRegistrationPlan(ownerModId, baseGameId, Map.of(), Map.of(), Map.of(),
                List.of(), List.of(), List.of(), Map.of(), characters, null);
    }

    public boolean hasContent() {
        return !objectFactories.isEmpty() || !objectArt.isEmpty() || !zones.isEmpty()
                || !characters.isEmpty() || !romObjectArt.isEmpty();
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
                prepared, explicitPatches, zones, preparedZones,objectPreviewArtKeys,characters,
                standaloneModule, romObjectArt);
    }

    /** Resolves all level exports while the bounded creator view is still alive. */
    ModRegistrationPlan prepareZones(ModAssetRoot assets) {
        return this;
    }
}

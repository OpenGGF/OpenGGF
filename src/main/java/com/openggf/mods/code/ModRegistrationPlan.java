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
                                  Map<String, RomArtRequest> romObjectArt,
                                  Map<com.openggf.game.ZoneKey.Mod,
                                          com.openggf.game.GameplayLaunchTeam> launchTeams,
                                  Map<com.openggf.game.ZoneKey.Mod,
                                          com.openggf.game.GameplayInputFilter> inputFilters,
                                  Map<com.openggf.game.ZoneKey.Mod,
                                          com.openggf.level.objects.HudProfile> hudProfiles,
                                  com.openggf.mods.scene.ModSceneFactory startupScene,
                                  String requiredDisplayAspect,
                                  Map<String, java.util.function.Supplier<com.openggf.game.GameServiceBundle>> serviceBundles,
                                  Map<String, com.openggf.level.LevelPatch> decodedLevelPatches,
                                  int contributionLimit,
                                  Map<String, com.openggf.mods.mutators.OwnedMutator> mutators) {
    public ModRegistrationPlan {
        if (contributionLimit < 1 || contributionLimit > com.openggf.io.ModInputLimits.production().maxCollectionEntries())
            throw new IllegalArgumentException("Invalid contribution limit");
        serviceBundles = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(Objects.requireNonNull(serviceBundles)));
        decodedLevelPatches = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(Objects.requireNonNull(decodedLevelPatches)));
        if (serviceBundles.size() + decodedLevelPatches.size() > contributionLimit)
            throw new IllegalArgumentException("Owner contribution limit exceeded");
        serviceBundles.forEach((key, factory) -> { com.openggf.game.ModKeySyntax.requireLocalName(key); Objects.requireNonNull(factory); });
        decodedLevelPatches.forEach((key, patch) -> { com.openggf.game.ModKeySyntax.requireLocalName(key); Objects.requireNonNull(patch); });
        if (requiredDisplayAspect != null && baseGameId == null) {
            throw new IllegalArgumentException("A required display width is available to patch mods only");
        }
        if (startupScene != null && baseGameId == null) {
            throw new IllegalArgumentException("Startup scenes are available to patch mods only");
        }
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
        launchTeams = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(
                Objects.requireNonNull(launchTeams, "launchTeams")));
        inputFilters = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(
                Objects.requireNonNull(inputFilters, "inputFilters")));
        hudProfiles = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(
                Objects.requireNonNull(hudProfiles, "hudProfiles")));
        mutators = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(
                Objects.requireNonNull(mutators, "mutators")));
        if (!mutators.isEmpty() && baseGameId == null) {
            throw new IllegalArgumentException("Mutators require a stock-game patch owner");
        }
        if (mutators.size() > 32) throw new IllegalArgumentException("Too many owner mutators");
        mutators.forEach((key, owned) -> {
            if (!ownerModId.equals(owned.ownerModId()) || !key.equals(owned.key())) {
                throw new IllegalArgumentException("Mutator contribution owner/key mismatch");
            }
        });
        com.openggf.mods.mutators.MutatorSessionState.validateCatalog(mutators);
        validatePolicies(ownerModId, launchTeams, "launch team");
        validatePolicies(ownerModId, inputFilters, "input filter");
        validatePolicies(ownerModId, hudProfiles, "HUD profile");
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
        long expectedActs = zones.stream().mapToLong(zone -> 1L + zone.additionalActs().size()).sum();
        if (expectedActs > contributionLimit) throw new IllegalArgumentException("Owner authored-act limit exceeded");
        if (preparedZones.size() != expectedActs) {
            throw new IllegalArgumentException("Every declared act must have one prepared payload");
        }
        int preparedIndex = 0;
        for (ModZoneContribution declared : zones) {
            for (int act = 0; act < declared.acts().size(); act++) {
                PreparedModZone prepared = preparedZones.get(preparedIndex++);
                if (!ownerModId.equals(prepared.ownerModId())
                        || !declared.localKey().equals(prepared.localKey())
                        || !Objects.equals(declared.insertAfter(), prepared.insertAfter())
                        || (declared.gameStart() && act == 0) != prepared.gameStart()
                        || prepared.actIndex() != act
                        || declared.runtimeFactory() != prepared.runtimeFactory()) {
                    throw new IllegalArgumentException("Prepared acts must exactly match declarations");
                }
            }
        }
        if ("any".equals(baseGameId) && ((startupScene == null && mutators.isEmpty()) || !objectFactories.isEmpty()
                || !objectArt.isEmpty() || (!explicitPatches.isEmpty() && mutators.isEmpty()) || !zones.isEmpty()
                || !objectPreviewArtKeys.isEmpty() || !characters.isEmpty() || !romObjectArt.isEmpty()
                || !launchTeams.isEmpty() || !inputFilters.isEmpty() || !hudProfiles.isEmpty() || !serviceBundles.isEmpty() || !decodedLevelPatches.isEmpty())) {
            throw new IllegalArgumentException(
                    "baseGame any permits a shared startup scene or typed mutator catalogue with stock-game decorators; game-specific content is forbidden");
        }
        if ("any".equals(baseGameId) && explicitPatches.stream()
                .anyMatch(patch -> !List.of("s1", "s2", "s3k").contains(patch.baseGameId())))
            throw new IllegalArgumentException("Shared mutator decorators must target a concrete stock game");
    }

    /** Expands a validated shared scene/catalogue without leaking another game's decorators. */
    List<ModRegistrationPlan> stockScenePlans() {
        if (!"any".equals(baseGameId)) return List.of(this);
        return List.of("s1", "s2", "s3k").stream().map(game -> new ModRegistrationPlan(
                ownerModId, game, objectFactories, objectArt, preparedObjectArt,
                explicitPatches.stream().filter(patch -> game.equals(patch.baseGameId())).toList(),
                zones, preparedZones, objectPreviewArtKeys, characters, null, romObjectArt,
                launchTeams, inputFilters, hudProfiles, startupScene, requiredDisplayAspect,
                serviceBundles, decodedLevelPatches, contributionLimit, mutators)).toList();
    }

    private static void validatePolicies(String ownerModId,
            Map<com.openggf.game.ZoneKey.Mod, ?> policies, String policyName) {
        for (Map.Entry<com.openggf.game.ZoneKey.Mod, ?> entry : policies.entrySet()) {
            com.openggf.game.ZoneKey.Mod destination = entry.getKey();
            if (destination == null || !ownerModId.equals(destination.ownerModId())) {
                throw new IllegalArgumentException(
                        "Foreign or null " + policyName + " destination");
            }
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("Null " + policyName + " policy");
            }
        }
    }

    /** Compatibility constructor for the pre-mutator canonical shape. */
    public ModRegistrationPlan(String ownerModId, String baseGameId,
            Map<String, ObjectFactory> objectFactories, Map<String, BakedSheetRef> objectArt,
            Map<String, BakedSheetReader.BakedSheet> preparedObjectArt, List<GamePatch> explicitPatches,
            List<ModZoneContribution> zones, List<PreparedModZone> preparedZones,
            Map<String, String> objectPreviewArtKeys, Map<CharacterKey, CharacterDefinition> characters,
            com.openggf.game.GameModule standaloneModule, Map<String, RomArtRequest> romObjectArt,
            Map<com.openggf.game.ZoneKey.Mod, com.openggf.game.GameplayLaunchTeam> launchTeams,
            Map<com.openggf.game.ZoneKey.Mod, com.openggf.game.GameplayInputFilter> inputFilters,
            Map<com.openggf.game.ZoneKey.Mod, com.openggf.level.objects.HudProfile> hudProfiles,
            com.openggf.mods.scene.ModSceneFactory startupScene, String requiredDisplayAspect,
            Map<String, java.util.function.Supplier<com.openggf.game.GameServiceBundle>> serviceBundles,
            Map<String, com.openggf.level.LevelPatch> decodedLevelPatches, int contributionLimit) {
        this(ownerModId, baseGameId, objectFactories, objectArt, preparedObjectArt, explicitPatches,
                zones, preparedZones, objectPreviewArtKeys, characters, standaloneModule, romObjectArt,
                launchTeams, inputFilters, hudProfiles, startupScene, requiredDisplayAspect,
                serviceBundles, decodedLevelPatches, contributionLimit, Map.of());
    }

    /** Compatibility constructor for the mutator catalogue before shared service/patch helpers. */
    public ModRegistrationPlan(String ownerModId, String baseGameId,
            Map<String, ObjectFactory> objectFactories, Map<String, BakedSheetRef> objectArt,
            Map<String, BakedSheetReader.BakedSheet> preparedObjectArt, List<GamePatch> explicitPatches,
            List<ModZoneContribution> zones, List<PreparedModZone> preparedZones,
            Map<String, String> objectPreviewArtKeys, Map<CharacterKey, CharacterDefinition> characters,
            com.openggf.game.GameModule standaloneModule, Map<String, RomArtRequest> romObjectArt,
            Map<com.openggf.game.ZoneKey.Mod, com.openggf.game.GameplayLaunchTeam> launchTeams,
            Map<com.openggf.game.ZoneKey.Mod, com.openggf.game.GameplayInputFilter> inputFilters,
            Map<com.openggf.game.ZoneKey.Mod, com.openggf.level.objects.HudProfile> hudProfiles,
            com.openggf.mods.scene.ModSceneFactory startupScene, String requiredDisplayAspect,
            Map<String, com.openggf.mods.mutators.OwnedMutator> mutators) {
        this(ownerModId, baseGameId, objectFactories, objectArt, preparedObjectArt, explicitPatches,
                zones, preparedZones, objectPreviewArtKeys, characters, standaloneModule, romObjectArt,
                launchTeams, inputFilters, hudProfiles, startupScene, requiredDisplayAspect,
                Map.of(), Map.of(), com.openggf.io.ModInputLimits.production().maxCollectionEntries(), mutators);
    }

    /** Compatibility constructor for the pre-service canonical shape. */
    public ModRegistrationPlan(String ownerModId, String baseGameId,
            Map<String, ObjectFactory> objectFactories, Map<String, BakedSheetRef> objectArt,
            Map<String, BakedSheetReader.BakedSheet> preparedObjectArt, List<GamePatch> explicitPatches,
            List<ModZoneContribution> zones, List<PreparedModZone> preparedZones,
            Map<String, String> objectPreviewArtKeys, Map<CharacterKey, CharacterDefinition> characters,
            com.openggf.game.GameModule standaloneModule, Map<String, RomArtRequest> romObjectArt,
            Map<com.openggf.game.ZoneKey.Mod, com.openggf.game.GameplayLaunchTeam> launchTeams,
            Map<com.openggf.game.ZoneKey.Mod, com.openggf.game.GameplayInputFilter> inputFilters,
            Map<com.openggf.game.ZoneKey.Mod, com.openggf.level.objects.HudProfile> hudProfiles,
            com.openggf.mods.scene.ModSceneFactory startupScene, String requiredDisplayAspect) {
        this(ownerModId, baseGameId, objectFactories, objectArt, preparedObjectArt, explicitPatches,
                zones, preparedZones, objectPreviewArtKeys, characters, standaloneModule, romObjectArt,
                launchTeams, inputFilters, hudProfiles, startupScene, requiredDisplayAspect,
                Map.of(), Map.of(), com.openggf.io.ModInputLimits.production().maxCollectionEntries(), Map.of());
    }

    /** Compatibility constructor for the pre-display-width canonical shape. */
    public ModRegistrationPlan(String ownerModId, String baseGameId,
                               Map<String, ObjectFactory> objectFactories,
                               Map<String, BakedSheetRef> objectArt,
                               Map<String, BakedSheetReader.BakedSheet> preparedObjectArt,
                               List<GamePatch> explicitPatches,
                               List<ModZoneContribution> zones,
                               List<PreparedModZone> preparedZones,
                               Map<String, String> objectPreviewArtKeys,
                               Map<CharacterKey, CharacterDefinition> characters,
                               com.openggf.game.GameModule standaloneModule,
                               Map<String, RomArtRequest> romObjectArt,
                               Map<com.openggf.game.ZoneKey.Mod,
                                       com.openggf.game.GameplayLaunchTeam> launchTeams,
                               Map<com.openggf.game.ZoneKey.Mod,
                                       com.openggf.game.GameplayInputFilter> inputFilters,
                               Map<com.openggf.game.ZoneKey.Mod,
                                       com.openggf.level.objects.HudProfile> hudProfiles,
                               com.openggf.mods.scene.ModSceneFactory startupScene) {
        this(ownerModId, baseGameId, objectFactories, objectArt, preparedObjectArt, explicitPatches, zones,
                preparedZones, objectPreviewArtKeys, characters, standaloneModule, romObjectArt, launchTeams,
                inputFilters, hudProfiles, startupScene, null);
    }

    /** Compatibility constructor for the pre-startup-scene canonical shape. */
    public ModRegistrationPlan(String ownerModId, String baseGameId,
                               Map<String, ObjectFactory> objectFactories,
                               Map<String, BakedSheetRef> objectArt,
                               Map<String, BakedSheetReader.BakedSheet> preparedObjectArt,
                               List<GamePatch> explicitPatches,
                               List<ModZoneContribution> zones,
                               List<PreparedModZone> preparedZones,
                               Map<String, String> objectPreviewArtKeys,
                               Map<CharacterKey, CharacterDefinition> characters,
                               com.openggf.game.GameModule standaloneModule,
                               Map<String, RomArtRequest> romObjectArt,
                               Map<com.openggf.game.ZoneKey.Mod,
                                       com.openggf.game.GameplayLaunchTeam> launchTeams,
                               Map<com.openggf.game.ZoneKey.Mod,
                                       com.openggf.game.GameplayInputFilter> inputFilters,
                               Map<com.openggf.game.ZoneKey.Mod,
                                       com.openggf.level.objects.HudProfile> hudProfiles) {
        this(ownerModId, baseGameId, objectFactories, objectArt, preparedObjectArt, explicitPatches, zones,
                preparedZones, objectPreviewArtKeys, characters, standaloneModule, romObjectArt, launchTeams,
                inputFilters, hudProfiles, null);
    }

    /** Compatibility constructor for the pre-gameplay-policy canonical shape. */
    public ModRegistrationPlan(String ownerModId, String baseGameId,
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
        this(ownerModId, baseGameId, objectFactories, objectArt, preparedObjectArt,
                explicitPatches, zones, preparedZones, objectPreviewArtKeys, characters,
                standaloneModule, romObjectArt, Map.of(), Map.of(), Map.of());
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
                || !characters.isEmpty() || !romObjectArt.isEmpty()
                || !launchTeams.isEmpty() || !inputFilters.isEmpty() || !hudProfiles.isEmpty()
                || startupScene != null || requiredDisplayAspect != null || !mutators.isEmpty()
                || !serviceBundles.isEmpty() || !decodedLevelPatches.isEmpty();
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
                standaloneModule, romObjectArt, launchTeams, inputFilters, hudProfiles, startupScene,
                requiredDisplayAspect, serviceBundles, decodedLevelPatches, contributionLimit, mutators);
    }

    /** Resolves all level exports while the bounded creator view is still alive. */
    ModRegistrationPlan prepareZones(ModAssetRoot assets) {
        return this;
    }
}

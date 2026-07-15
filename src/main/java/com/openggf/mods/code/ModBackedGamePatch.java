package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.modzone.ModZoneAdapter;
import com.openggf.game.modzone.ModZoneDataSelectDecorator;
import com.openggf.game.modzone.ModZoneLevelData;
import com.openggf.game.modzone.ModZoneRuntimeProfile;
import com.openggf.io.ModInputLimits;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSpriteSheet;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Engine-owned backing decorator for one frozen content registration plan. */
public final class ModBackedGamePatch implements GamePatch {
    private final ModRegistrationPlan plan;
    private final ModFaultBoundary faultBoundary;
    private final java.util.function.BiConsumer<String,
            com.openggf.game.sonic2.dataselect.S2SaveFinding> saveFindingSink;
    private final RomArtSheetSource romArtSource;

    /**
     * Source of materialized ROM-art sheets; injectable for tests. Engine-internal — must never
     * enter the {@code @ModApi} surface.
     */
    interface RomArtSheetSource {
        Map<String, ObjectSpriteSheet> materialize(String owner, Map<String, RomArtRequest> requests);
    }

    static RomArtSheetSource productionRomArtSource() {
        return (owner, requests) -> {
            try {
                return RomArtMaterializer.materialize(owner, requests,
                        GameServices.rom().getRom(), ModInputLimits.production());
            } catch (IOException e) {
                throw new ModRegistrationException(owner, "MOD_ROM_ART_INVALID",
                        "ROM unavailable during art materialization", null, e);
            }
        };
    }

    public ModBackedGamePatch(ModRegistrationPlan plan) {
        this(plan, null, (owner, finding) -> {});
    }

    public ModBackedGamePatch(ModRegistrationPlan plan, ModFaultBoundary faultBoundary) {
        this(plan, faultBoundary, (owner, finding) -> {});
    }

    public ModBackedGamePatch(ModRegistrationPlan plan, ModFaultBoundary faultBoundary,
                              java.util.function.BiConsumer<String,
                                      com.openggf.game.sonic2.dataselect.S2SaveFinding> saveFindingSink) {
        this(plan, faultBoundary, saveFindingSink, productionRomArtSource());
    }

    ModBackedGamePatch(ModRegistrationPlan plan, ModFaultBoundary faultBoundary,
                       java.util.function.BiConsumer<String,
                               com.openggf.game.sonic2.dataselect.S2SaveFinding> saveFindingSink,
                       RomArtSheetSource romArtSource) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.faultBoundary = faultBoundary;
        this.saveFindingSink = Objects.requireNonNull(saveFindingSink, "saveFindingSink");
        this.romArtSource = Objects.requireNonNull(romArtSource, "romArtSource");
        if (!plan.hasContent()) throw new IllegalArgumentException("Backing patch requires content");
        if (!plan.objectArt().isEmpty()
                && !plan.preparedObjectArt().keySet().equals(plan.objectArt().keySet())) {
            throw new IllegalArgumentException("Backing patch requires validated object art");
        }
        if (!plan.preparedZones().isEmpty() && plan.preparedZones().size() != plan.zones().size()) {
            throw new IllegalArgumentException("Backing patch requires validated mod zones");
        }
        if (plan.preparedZones().stream().anyMatch(zone -> zone.eventFactory() != null)
                && faultBoundary == null) {
            throw new IllegalArgumentException("Mod zone events require an installed fault boundary");
        }
        if (!plan.characters().isEmpty() && faultBoundary == null) {
            throw new IllegalArgumentException("Mod characters require an installed fault boundary");
        }
        if ((!plan.launchTeams().isEmpty() || !plan.inputFilters().isEmpty()
                || !plan.hudProfiles().isEmpty()) && faultBoundary == null) {
            throw new IllegalArgumentException(
                    "Mod gameplay policies require an installed fault boundary");
        }
    }

    public ModRegistrationPlan plan() { return plan; }
    @Override public String id() { return plan.ownerModId() + ":content"; }
    @Override public String displayName() { return plan.ownerModId() + " content"; }
    @Override public String baseGameId() { return plan.baseGameId(); }
    @Override public boolean activatesFor(GameplayLaunchRequest request) {
        return plan.baseGameId().equals(request.gameId());
    }
    @Override public Set<LogicalRom> romPrerequisites() { return Set.of(); }
    @Override public List<String> providedMainCharacters() {
        return plan.characters().keySet().stream()
                .map(com.openggf.game.CharacterKey::persisted).toList();
    }
    @Override public java.util.Map<com.openggf.game.CharacterKey,
            com.openggf.game.CharacterDefinition> providedCharacterDefinitions() {
        return plan.characters();
    }
    @Override public GameModule apply(GameModule base, PatchContext context) {
        ModZoneAdapter zoneAdapter = plan.preparedZones().isEmpty()
                ? null : base.getModZoneAdapter();
        List<PreparedModZone> resolvedZones = plan.preparedZones();
        if (!resolvedZones.isEmpty()) {
            if (zoneAdapter == GameModule.EMPTY_MOD_ZONE_ADAPTER) {
                throw new ModRegistrationException(plan.ownerModId(),
                        "MOD_ZONE_HOST_UNSUPPORTED",
                        "Host game does not support additive mod zones", null, null);
            }
            resolvedZones = resolvedZones.stream().map(zone -> {
                ModZoneLevelData hostData = prepareHostData(zone);
                zoneAdapter.validate(zone.ownerModId(), hostData);
                ModZoneRuntimeProfile profile = zoneAdapter.runtimeProfile(
                        zone.ownerModId(), hostData);
                if (!ModZoneRuntimeProfile.flatEmpty().equals(profile)) {
                    throw new ModRegistrationException(zone.ownerModId(),
                            "MOD_ZONE_RUNTIME_PROFILE_UNSUPPORTED",
                            "Host adapter requested unsupported additive-zone runtime features",
                            null, null);
                }
                return zone.withRuntimeProfile(profile, hostData);
            }).toList();
        }
        List<PreparedModZone> publishedZones = resolvedZones;
        ModZoneDataSelectDecorator dataSelectDecorator =
                zoneAdapter instanceof ModZoneDataSelectDecorator decorator ? decorator : null;
        List<ModObjectKeyRegistry.Registration> registrations = plan.objectFactories().entrySet().stream()
                .map(entry -> new ModObjectKeyRegistry.Registration(
                        plan.ownerModId(), entry.getKey(), entry.getValue()))
                .toList();
        ModObjectKeyRegistry objectKeys = new ModObjectKeyRegistry(registrations);
        Map<String, ObjectSpriteSheet> romSheets = plan.romObjectArt().isEmpty()
                ? Map.of()
                : romArtSource.materialize(plan.ownerModId(), plan.romObjectArt());
        return new DelegatingGameModule(base, id()) {
            private com.openggf.game.PlayableCharacterRegistry playableCharacters;
            private com.openggf.game.ObjectArtProvider objectArtProvider;
            private com.openggf.game.ZoneRegistry zoneRegistry;
            private com.openggf.game.LevelEventProvider levelEvents;
            private com.openggf.game.dataselect.DataSelectHostProfile dataSelectHost;
            private com.openggf.game.dataselect.DataSelectPresentationProvider dataSelectPresentation;
            private com.openggf.game.GameplayPolicyProvider gameplayPolicies;

            @Override
            public synchronized com.openggf.game.PlayableCharacterRegistry getPlayableCharacterRegistry() {
                if (playableCharacters == null) {
                    com.openggf.game.PlayableCharacterRegistry registry =
                            super.getPlayableCharacterRegistry();
                    for (var entry : plan.characters().entrySet()) {
                        registry = registry.register(entry.getKey(),
                                OwnerAwareCharacterDefinition.wrap(
                                        entry.getKey(), entry.getValue(), faultBoundary));
                    }
                    playableCharacters = registry;
                }
                return playableCharacters;
            }

            @Override
            public synchronized com.openggf.game.GameplayPolicyProvider getGameplayPolicyProvider() {
                if (gameplayPolicies == null) {
                    com.openggf.game.GameplayPolicyProvider inherited =
                            super.getGameplayPolicyProvider();
                    gameplayPolicies = new com.openggf.game.GameplayPolicyProvider() {
                        @Override
                        public java.util.Optional<com.openggf.game.GameplayLaunchTeam> launchTeam(
                                com.openggf.game.ZoneKey destination) {
                            return callGameplayPolicy(destination, () -> {
                                if (destination instanceof com.openggf.game.ZoneKey.Mod mod) {
                                    com.openggf.game.GameplayLaunchTeam contributed =
                                            plan.launchTeams().get(mod);
                                    if (contributed != null) {
                                        return java.util.Optional.of(contributed);
                                    }
                                }
                                return inherited.launchTeam(destination);
                            });
                        }

                        @Override
                        public java.util.Optional<com.openggf.game.GameplayInputFilter> inputFilter(
                                com.openggf.game.ZoneKey destination) {
                            return callGameplayPolicy(destination, () -> {
                                java.util.Optional<com.openggf.game.GameplayInputFilter> resolved;
                                if (destination instanceof com.openggf.game.ZoneKey.Mod mod) {
                                    com.openggf.game.GameplayInputFilter contributed =
                                            plan.inputFilters().get(mod);
                                    if (contributed != null) {
                                        resolved = java.util.Optional.of(contributed);
                                    } else {
                                        resolved = inherited.inputFilter(destination);
                                    }
                                    return resolved.map(filter -> filter ==
                                                    com.openggf.game.GameplayInputFilter.IDENTITY
                                                    || filter instanceof OwnerAwareGameplayInputFilter
                                            ? filter
                                            : new OwnerAwareGameplayInputFilter(
                                                    mod.ownerModId(), filter, faultBoundary));
                                }
                                return inherited.inputFilter(destination);
                            });
                        }

                        @Override
                        public java.util.Optional<com.openggf.level.objects.HudProfile> hudProfile(
                                com.openggf.game.ZoneKey destination) {
                            return callGameplayPolicy(destination, () -> {
                                if (destination instanceof com.openggf.game.ZoneKey.Mod mod) {
                                    com.openggf.level.objects.HudProfile contributed =
                                            plan.hudProfiles().get(mod);
                                    if (contributed != null) {
                                        return java.util.Optional.of(contributed);
                                    }
                                }
                                return inherited.hudProfile(destination);
                            });
                        }

                        private <T> T callGameplayPolicy(com.openggf.game.ZoneKey destination,
                                                        java.util.function.Supplier<T> callback) {
                            if (!(destination instanceof com.openggf.game.ZoneKey.Mod mod)) {
                                return callback.get();
                            }
                            if (faultBoundary == null) {
                                throw new IllegalStateException(
                                        "Mod gameplay policies require an installed fault boundary");
                            }
                            return faultBoundary.call(mod.ownerModId(), callback);
                        }
                    };
                }
                return gameplayPolicies;
            }

            @Override
            public ObjectRegistry createObjectRegistry() {
                ObjectRegistry stockOrDecorated = super.createObjectRegistry();
                return registrations.isEmpty()
                        ? stockOrDecorated
                        : new ModDecoratedObjectRegistry(stockOrDecorated, objectKeys,
                                plan.objectPreviewArtKeys(), faultBoundary);
            }

            @Override
            public synchronized com.openggf.game.ObjectArtProvider getObjectArtProvider() {
                if (objectArtProvider == null) {
                    com.openggf.game.ObjectArtProvider inherited = super.getObjectArtProvider();
                    objectArtProvider = (plan.preparedObjectArt().isEmpty() && romSheets.isEmpty())
                            ? inherited
                            : ModArtOverlayProvider.decorate(inherited, plan.preparedObjectArt(), romSheets);
                }
                return objectArtProvider;
            }

            @Override
            public synchronized com.openggf.game.ZoneRegistry getZoneRegistry() {
                if (zoneRegistry == null) {
                    zoneRegistry = ModZoneRegistry.decorate(super.getZoneRegistry(), publishedZones);
                }
                return zoneRegistry;
            }

            @Override
            public com.openggf.game.MusicReference getLevelMusicReference(int zoneIndex, int actIndex) {
                return getZoneRegistry().getMusicReference(zoneIndex, actIndex);
            }

            @Override
            public com.openggf.level.Level loadLevelOverride(int levelIndex)
                    throws java.io.IOException {
                if (publishedZones.isEmpty()) return super.loadLevelOverride(levelIndex);
                com.openggf.game.ZoneRegistry registry = getZoneRegistry();
                if (registry instanceof ModZoneRegistry mods) {
                    PreparedModZone contribution = mods.levelContribution(levelIndex);
                    if (contribution != null) {
                        return zoneAdapter.load(contribution.ownerModId(), contribution.hostData());
                    }
                }
                return super.loadLevelOverride(levelIndex);
            }

            @Override
            public int[] getBackgroundScrollOverride(int levelIndex, int cameraX, int cameraY) {
                com.openggf.game.ZoneRegistry registry = getZoneRegistry();
                if (registry instanceof ModZoneRegistry mods) {
                    PreparedModZone contribution = mods.levelContribution(levelIndex);
                    if (contribution != null) {
                        return switch (contribution.runtimeProfile().scroll()) {
                            case FLAT -> new int[]{cameraX, cameraY};
                        };
                    }
                }
                return super.getBackgroundScrollOverride(levelIndex, cameraX, cameraY);
            }

            @Override
            public synchronized com.openggf.game.LevelEventProvider getLevelEventProvider() {
                if (levelEvents == null) {
                    if (publishedZones.isEmpty()) {
                        return super.getLevelEventProvider();
                    }
                    int inheritedZoneCount = super.getZoneRegistry().getZoneCount();
                    levelEvents = new ModZoneEventProvider(super.getLevelEventProvider(),
                            inheritedZoneCount, publishedZones, faultBoundary);
                }
                return levelEvents;
            }

            @Override
            public synchronized com.openggf.game.dataselect.DataSelectHostProfile getDataSelectHostProfile() {
                if (plan.preparedZones().isEmpty()) return super.getDataSelectHostProfile();
                if (dataSelectHost == null) {
                    com.openggf.game.dataselect.DataSelectHostProfile inherited =
                            super.getDataSelectHostProfile();
                    com.openggf.game.dataselect.DataSelectHostProfile decorated =
                            dataSelectDecorator == null ? inherited
                            : dataSelectDecorator.decorateHostProfile(
                                    inherited, this::getZoneRegistry,
                                    (owner, finding) -> saveFindingSink.accept(owner,
                                            new com.openggf.game.sonic2.dataselect.S2SaveFinding(
                                                    finding.ownerModId(), finding.code(), finding.detail())));
                    dataSelectHost = ModGameStartResolver.decorate(decorated, inherited,
                            (ModZoneRegistry) getZoneRegistry(),
                            (owner, finding) -> saveFindingSink.accept(owner,
                                    new com.openggf.game.sonic2.dataselect.S2SaveFinding(
                                            finding.ownerModId(), finding.code(), finding.detail())));
                }
                return dataSelectHost;
            }

            @Override
            public synchronized com.openggf.game.dataselect.DataSelectPresentationProvider
                    getDataSelectPresentationProvider() {
                if (plan.preparedZones().isEmpty()) return super.getDataSelectPresentationProvider();
                if (dataSelectPresentation == null) {
                    com.openggf.game.dataselect.DataSelectPresentationProvider inherited =
                            super.getDataSelectPresentationProvider();
                    dataSelectPresentation = dataSelectDecorator == null ? inherited
                            : dataSelectDecorator.decoratePresentationProvider(
                                    inherited, getDataSelectHostProfile());
                }
                return dataSelectPresentation;
            }

            @Override
            public com.openggf.game.DataSelectProvider getDataSelectProvider() {
                return getDataSelectPresentationProvider();
            }
        };
    }

    private static ModZoneLevelData prepareHostData(PreparedModZone zone) {
        try {
            return ModZoneLoader.prepareHostData(zone.definition());
        } catch (IOException e) {
            throw new ModRegistrationException(zone.ownerModId(), "MOD_ZONE_INVALID",
                    "Unable to prepare additive-zone data", null, e);
        }
    }
}

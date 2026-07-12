package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.level.objects.ObjectRegistry;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Engine-owned backing decorator for one frozen content registration plan. */
public final class ModBackedGamePatch implements GamePatch {
    private final ModRegistrationPlan plan;
    private final ModFaultBoundary faultBoundary;
    private final java.util.function.BiConsumer<String,
            com.openggf.game.sonic2.dataselect.S2SaveFinding> saveFindingSink;

    public ModBackedGamePatch(ModRegistrationPlan plan) {
        this(plan, null, (owner, finding) -> {});
    }

    public ModBackedGamePatch(ModRegistrationPlan plan, ModFaultBoundary faultBoundary) {
        this(plan, faultBoundary, (owner, finding) -> {});
    }

    public ModBackedGamePatch(ModRegistrationPlan plan, ModFaultBoundary faultBoundary,
                              java.util.function.BiConsumer<String,
                                      com.openggf.game.sonic2.dataselect.S2SaveFinding> saveFindingSink) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.faultBoundary = faultBoundary;
        this.saveFindingSink = Objects.requireNonNull(saveFindingSink, "saveFindingSink");
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
    }

    public ModRegistrationPlan plan() { return plan; }
    @Override public String id() { return plan.ownerModId() + ":content"; }
    @Override public String displayName() { return plan.ownerModId() + " content"; }
    @Override public String baseGameId() { return plan.baseGameId(); }
    @Override public boolean activatesFor(GameplayLaunchRequest request) {
        return plan.baseGameId().equals(request.gameId());
    }
    @Override public Set<LogicalRom> romPrerequisites() { return Set.of(); }
    @Override public List<String> providedMainCharacters() { return List.of(); }
    @Override public GameModule apply(GameModule base, PatchContext context) {
        List<ModObjectKeyRegistry.Registration> registrations = plan.objectFactories().entrySet().stream()
                .map(entry -> new ModObjectKeyRegistry.Registration(
                        plan.ownerModId(), entry.getKey(), entry.getValue()))
                .toList();
        ModObjectKeyRegistry objectKeys = new ModObjectKeyRegistry(registrations);
        return new DelegatingGameModule(base, id()) {
            private com.openggf.game.PlayableCharacterRegistry playableCharacters;
            private com.openggf.game.ObjectArtProvider objectArtProvider;
            private com.openggf.game.ZoneRegistry zoneRegistry;
            private com.openggf.game.LevelEventProvider levelEvents;
            private com.openggf.game.dataselect.DataSelectHostProfile dataSelectHost;
            private com.openggf.game.dataselect.DataSelectPresentationProvider dataSelectPresentation;

            @Override
            public synchronized com.openggf.game.PlayableCharacterRegistry getPlayableCharacterRegistry() {
                if (playableCharacters == null) {
                    com.openggf.game.PlayableCharacterRegistry registry =
                            super.getPlayableCharacterRegistry();
                    for (var entry : plan.characters().entrySet()) {
                        registry = registry.register(entry.getKey(), entry.getValue());
                    }
                    playableCharacters = registry;
                }
                return playableCharacters;
            }

            @Override
            public ObjectRegistry createObjectRegistry() {
                ObjectRegistry stockOrDecorated = super.createObjectRegistry();
                return registrations.isEmpty()
                        ? stockOrDecorated
                        : new ModDecoratedObjectRegistry(stockOrDecorated, objectKeys,
                                plan.objectPreviewArtKeys());
            }

            @Override
            public synchronized com.openggf.game.ObjectArtProvider getObjectArtProvider() {
                if (objectArtProvider == null) {
                    com.openggf.game.ObjectArtProvider inherited = super.getObjectArtProvider();
                    objectArtProvider = plan.preparedObjectArt().isEmpty()
                            ? inherited
                            : ModArtOverlayProvider.decorate(inherited, plan.preparedObjectArt());
                }
                return objectArtProvider;
            }

            @Override
            public synchronized com.openggf.game.ZoneRegistry getZoneRegistry() {
                if (zoneRegistry == null) {
                    zoneRegistry = ModZoneRegistry.decorate(super.getZoneRegistry(), plan.preparedZones());
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
                com.openggf.game.ZoneRegistry registry = getZoneRegistry();
                if (registry instanceof ModZoneRegistry mods) {
                    PreparedModZone contribution = mods.levelContribution(levelIndex);
                    if (contribution != null) return ModZoneLoader.load(
                            contribution, super.getAdditiveLevelRingSpriteSheet());
                }
                return super.loadLevelOverride(levelIndex);
            }

            @Override
            public int[] getBackgroundScrollOverride(int levelIndex, int cameraX, int cameraY) {
                com.openggf.game.ZoneRegistry registry = getZoneRegistry();
                if (registry instanceof ModZoneRegistry mods && mods.levelContribution(levelIndex) != null) {
                    return new int[]{cameraX, cameraY};
                }
                return super.getBackgroundScrollOverride(levelIndex, cameraX, cameraY);
            }

            @Override
            public synchronized com.openggf.game.LevelEventProvider getLevelEventProvider() {
                if (levelEvents == null) {
                    com.openggf.game.ZoneRegistry registry = getZoneRegistry();
                    if (!(registry instanceof ModZoneRegistry mods)
                            || mods.contributions().stream().noneMatch(zone -> zone.eventFactory() != null)) {
                        return super.getLevelEventProvider();
                    }
                    levelEvents = new ModZoneEventProvider(super.getLevelEventProvider(), mods, faultBoundary);
                }
                return levelEvents;
            }

            @Override
            public synchronized com.openggf.game.dataselect.DataSelectHostProfile getDataSelectHostProfile() {
                if (plan.preparedZones().isEmpty()) return super.getDataSelectHostProfile();
                if (dataSelectHost == null) {
                    dataSelectHost = new com.openggf.game.sonic2.dataselect.S2DataSelectProfile(
                            this::getZoneRegistry, finding -> saveFindingSink.accept(
                                    finding.ownerModId() == null ? "s2-save" : finding.ownerModId(), finding));
                }
                return dataSelectHost;
            }

            @Override
            public synchronized com.openggf.game.dataselect.DataSelectPresentationProvider
                    getDataSelectPresentationProvider() {
                if (plan.preparedZones().isEmpty()) return super.getDataSelectPresentationProvider();
                if (dataSelectPresentation == null) {
                    dataSelectPresentation = com.openggf.game.dataselect.CrossGameDataSelectPresentations
                            .donated(com.openggf.game.dataselect.CrossGameDataSelectPresentations.DONOR_S3K,
                                    getDataSelectHostProfile());
                }
                return dataSelectPresentation;
            }

            @Override
            public com.openggf.game.DataSelectProvider getDataSelectProvider() {
                return getDataSelectPresentationProvider();
            }
        };
    }
}

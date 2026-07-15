package com.openggf.mods.code;

import com.openggf.game.LevelEventProvider;
import com.openggf.game.AbstractLevelEventManager;
import com.openggf.game.LevelEventRewindResolver;

import java.util.List;
import java.util.Objects;

/** Selects stock or mod events at level init and routes creator callbacks through the fault boundary. */
final class ModZoneEventProvider implements LevelEventProvider, LevelEventRewindResolver {
    private final LevelEventProvider stock;
    private final int inheritedZoneCount;
    private final List<PreparedModZone> addedZones;
    private final ModFaultBoundary boundary;
    private LevelEventProvider active;
    private String activeOwner;

    ModZoneEventProvider(LevelEventProvider stock, int inheritedZoneCount,
                         List<PreparedModZone> addedZones, ModFaultBoundary boundary) {
        this.stock = stock;
        if (inheritedZoneCount < 0) {
            throw new IllegalArgumentException("inheritedZoneCount must be non-negative");
        }
        this.inheritedZoneCount = inheritedZoneCount;
        this.addedZones = List.copyOf(Objects.requireNonNull(addedZones, "addedZones"));
        this.boundary = this.addedZones.stream().anyMatch(zone -> zone.eventFactory() != null)
                ? Objects.requireNonNull(boundary, "boundary") : boundary;
    }

    @Override
    public void initLevel(int zone, int act) {
        if (zone < inheritedZoneCount) {
            active = stock;
            activeOwner = null;
            if (stock != null) stock.initLevel(zone, act);
            return;
        }
        int addedIndex = zone - inheritedZoneCount;
        if (addedIndex < 0 || addedIndex >= addedZones.size()) {
            throw new IllegalArgumentException("Zone is outside this mod event provider: " + zone);
        }
        PreparedModZone contribution = addedZones.get(addedIndex);
        activeOwner = contribution.ownerModId();
        active = contribution.optionalEventFactory()
                .map(factory -> boundary.call(activeOwner, factory::create))
                .orElse(null);
        if (active != null) boundary.run(activeOwner, () -> active.initLevel(zone, act));
    }

    @Override public void update() { invoke(LevelEventProvider::update); }
    @Override public void updatePrePhysics() { invoke(LevelEventProvider::updatePrePhysics); }
    @Override public void updateFixedInLevelObjectsBeforeDynamicObjects() {
        invoke(LevelEventProvider::updateFixedInLevelObjectsBeforeDynamicObjects);
    }
    @Override public void updateFixedInLevelObjects() { invoke(LevelEventProvider::updateFixedInLevelObjects); }

    @Override
    public AbstractLevelEventManager resolveLevelEventRewindManager(int zoneIndex) {
        if (zoneIndex < 0) {
            return null;
        }
        if (zoneIndex < inheritedZoneCount) {
            return LevelEventRewindResolver.resolve(stock, zoneIndex);
        }
        return null;
    }

    private void invoke(java.util.function.Consumer<LevelEventProvider> callback) {
        if (active == null) return;
        if (activeOwner == null) callback.accept(active);
        else boundary.run(activeOwner, () -> callback.accept(active));
    }

}

package com.openggf.mods.code;

import com.openggf.game.LevelEventProvider;

import java.util.Objects;

/** Selects stock or mod events at level init and routes creator callbacks through the fault boundary. */
final class ModZoneEventProvider implements LevelEventProvider {
    private final LevelEventProvider stock;
    private final ModZoneRegistry zones;
    private final ModFaultBoundary boundary;
    private LevelEventProvider active;
    private String activeOwner;

    ModZoneEventProvider(LevelEventProvider stock, ModZoneRegistry zones, ModFaultBoundary boundary) {
        this.stock = stock;
        this.zones = Objects.requireNonNull(zones, "zones");
        this.boundary = Objects.requireNonNull(boundary, "boundary");
    }

    @Override
    public void initLevel(int zone, int act) {
        if (zone < zones.getZoneCount() - zones.contributions().size()) {
            active = stock;
            activeOwner = null;
            if (stock != null) stock.initLevel(zone, act);
            return;
        }
        PreparedModZone contribution = zones.contributions().get(
                zone - (zones.getZoneCount() - zones.contributions().size()));
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

    private void invoke(java.util.function.Consumer<LevelEventProvider> callback) {
        if (active == null) return;
        if (activeOwner == null) callback.accept(active);
        else boundary.run(activeOwner, () -> callback.accept(active));
    }

}

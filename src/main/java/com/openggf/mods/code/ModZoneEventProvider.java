package com.openggf.mods.code;

import com.openggf.game.LevelEventProvider;
import com.openggf.game.AbstractLevelEventManager;
import com.openggf.game.LevelEventRewindResolver;

import com.openggf.game.rewind.RewindAdapterOwnership;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.level.objects.ObjectInstance;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Objects;

/** Selects stock or mod events at level init and routes creator callbacks through the fault boundary. */
final class ModZoneEventProvider implements LevelEventProvider, LevelEventRewindResolver {
    private final LevelEventProvider stock;
    private final int inheritedZoneCount;
    private final List<List<PreparedModZone>> addedZones;
    private final ModFaultBoundary boundary;
    private LevelEventProvider active;
    private String activeOwner;
    private int activeZone = -1;
    private int activeAct;
    private record ZoneAct(int zone, int act) { }
    private final Map<ZoneAct, LevelEventProvider> pending = new HashMap<>();
    private final List<RewindSnapshottable<?>> eventAdapters;

    ModZoneEventProvider(LevelEventProvider stock, int inheritedZoneCount,
                         List<PreparedModZone> addedZones, ModFaultBoundary boundary) {
        this.stock = stock;
        if (inheritedZoneCount < 0) {
            throw new IllegalArgumentException("inheritedZoneCount must be non-negative");
        }
        this.inheritedZoneCount = inheritedZoneCount;
        var grouped = new LinkedHashMap<com.openggf.game.ZoneKey.Mod, List<PreparedModZone>>();
        for (PreparedModZone contribution : Objects.requireNonNull(addedZones, "addedZones")) {
            var key = new com.openggf.game.ZoneKey.Mod(contribution.ownerModId(), contribution.localKey());
            var acts = grouped.computeIfAbsent(key, ignored -> new java.util.ArrayList<>());
            if (contribution.actIndex() != acts.size()) throw new IllegalArgumentException("Unordered event act " + key);
            acts.add(contribution);
        }
        this.addedZones = grouped.values().stream().map(List::copyOf).toList();
        this.boundary = addedZones.stream().anyMatch(zone -> zone.eventFactory() != null)
                ? Objects.requireNonNull(boundary, "boundary") : boundary;
        java.util.ArrayList<RewindSnapshottable<?>> adapters = new java.util.ArrayList<>();
        for (int i = 0; i < this.addedZones.size(); i++) adapters.add(new EventAdapter(i));
        this.eventAdapters = List.copyOf(adapters);
    }

    @Override
    public void initLevel(int zone, int act) {
        activeZone = zone;
        activeAct = act;
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
        PreparedModZone contribution = addedZones.get(addedIndex).get(act);
        activeOwner = contribution.ownerModId();
        ZoneAct destination = new ZoneAct(zone, act);
        active = pending.containsKey(destination) ? pending.remove(destination) : create(contribution);
        pending.clear();
        if (active != null) boundary.run(activeOwner, () -> active.initLevel(zone, act));
    }

    private LevelEventProvider create(PreparedModZone contribution) {
        return contribution.optionalEventFactory()
                .map(factory -> boundary.call(contribution.ownerModId(), factory::create)).orElse(null);
    }

    @Override public boolean defersInitialObjectPlacementUntilAfterLevelEvents(int zone, int act) {
        if (zone < inheritedZoneCount) return stock != null && stock.defersInitialObjectPlacementUntilAfterLevelEvents(zone, act);
        PreparedModZone contribution = addedZones.get(zone - inheritedZoneCount).get(act);
        ZoneAct destination = new ZoneAct(zone, act);
        if (!pending.containsKey(destination)) pending.put(destination, create(contribution));
        LevelEventProvider staged = pending.get(destination);
        return staged != null && boundary.call(contribution.ownerModId(),
                () -> staged.defersInitialObjectPlacementUntilAfterLevelEvents(zone, act));
    }
    @Override public void restoreEventOwnedObjectsAfterPlacementReset() { invoke(LevelEventProvider::restoreEventOwnedObjectsAfterPlacementReset); }
    @Override public void advanceVblankOnlyState() { invoke(LevelEventProvider::advanceVblankOnlyState); }
    @Override public void updateAfterObjectsBeforeCamera() { invoke(LevelEventProvider::updateAfterObjectsBeforeCamera); }
    @Override public void updateAfterCameraBoundaryEasing() { invoke(LevelEventProvider::updateAfterCameraBoundaryEasing); }
    @Override public void updateAtLevelLoopTail() { invoke(LevelEventProvider::updateAtLevelLoopTail); }
    @Override public void onPlayableLandingAnimationWrite(AbstractPlayableSprite player) {
        invoke(provider -> provider.onPlayableLandingAnimationWrite(player));
    }
    @Override public boolean ownsFixedDrowningBubbleCadence() { return query(LevelEventProvider::ownsFixedDrowningBubbleCadence); }
    @Override public boolean ownsFixedDrowningBubbleCadence(AbstractPlayableSprite player) {
        return query(provider -> provider.ownsFixedDrowningBubbleCadence(player));
    }
    @Override public boolean isSidekickObjectOrderFollowSteeringContext(AbstractPlayableSprite sidekick,
            AbstractPlayableSprite leader) { return query(provider -> provider.isSidekickObjectOrderFollowSteeringContext(sidekick, leader)); }
    @Override public boolean isSidekickObjectOrderFollowNudgeContext(AbstractPlayableSprite sidekick,
            AbstractPlayableSprite leader) { return query(provider -> provider.isSidekickObjectOrderFollowNudgeContext(sidekick, leader)); }
    @Override public boolean isSidekickDoorSupportGraceFollowSteeringContext(AbstractPlayableSprite sidekick,
            ObjectInstance object) { return query(provider -> provider.isSidekickDoorSupportGraceFollowSteeringContext(sidekick, object)); }
    @Override public boolean usesSidekickRomVisibleCatchUpMarkerFrameCounterBridge(AbstractPlayableSprite sidekick) {
        return query(provider -> provider.usesSidekickRomVisibleCatchUpMarkerFrameCounterBridge(sidekick));
    }
    @Override public boolean shouldEnterSidekickDormantMarker(AbstractPlayableSprite sidekick) {
        return query(provider -> provider.shouldEnterSidekickDormantMarker(sidekick));
    }
    @Override public boolean interceptPitDeath(AbstractPlayableSprite player) {
        return query(provider -> provider.interceptPitDeath(player));
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

    @Override public List<RewindSnapshottable<?>> resolveLevelEventRewindAdapters(int zoneIndex) {
        if (zoneIndex < 0) return List.of();
        if (zoneIndex < inheritedZoneCount) return LevelEventRewindResolver.adapters(stock, zoneIndex);
        int index = zoneIndex - inheritedZoneCount;
        return index >= addedZones.size() || addedZones.get(index).getFirst().eventFactory() == null
                ? List.of() : List.of(eventAdapters.get(index));
    }

    @Override public void reconcileLevelEventAfterRewindRestore(int zoneIndex) {
        if (zoneIndex < inheritedZoneCount) {
            LevelEventRewindResolver.reconcile(stock, zoneIndex);
        } else if (zoneIndex == activeZone && active != null) {
            boundary.run(activeOwner, () -> {
                if (active instanceof RewindableZoneEvents<?> events) events.reconcileAfterRewindRestore();
                else if (active instanceof AbstractLevelEventManager manager) manager.reconcileAfterRewindRestore();
            });
        }
    }

    private boolean query(java.util.function.Predicate<LevelEventProvider> callback) {
        return active != null && (activeOwner == null ? callback.test(active)
                : boundary.call(activeOwner, () -> callback.test(active)));
    }

    private void invoke(java.util.function.Consumer<LevelEventProvider> callback) {
        if (active == null) return;
        if (activeOwner == null) callback.accept(active);
        else boundary.run(activeOwner, () -> callback.accept(active));
    }

    private enum EmptyState { INSTANCE }
    private record EventState(int act, Object payload, Map<String, Object> extras) { }

    private final class EventAdapter implements RewindSnapshottable<EventState> {
        private final int zone;
        private final String owner;
        private final String key;
        EventAdapter(int index) {
            PreparedModZone contribution = addedZones.get(index).getFirst();
            zone = inheritedZoneCount + index;
            owner = contribution.ownerModId();
            key = RewindAdapterOwnership.bind(this, owner, "zone-events/" + contribution.localKey());
        }
        @Override public String key() { return key; }
        private void requireActive() {
            if (activeZone != zone) throw new IllegalStateException("Event adapter is outside the active zone");
        }
        @Override public EventState capture() {
            return boundary.call(owner, () -> {
                requireActive();
                Object payload = active instanceof RewindSnapshottable<?> state ? state.capture() : EmptyState.INSTANCE;
                var extras = new LinkedHashMap<String, Object>();
                if (active instanceof AbstractLevelEventManager manager) {
                    for (RewindSnapshottable<?> extra : manager.extraRewindAdapters()) {
                        if (extras.putIfAbsent(extra.key(), Objects.requireNonNull(extra.capture(), "Event extra snapshot")) != null)
                            throw new IllegalArgumentException("Duplicate event-local rewind key: " + extra.key());
                    }
                }
                return new EventState(activeAct, Objects.requireNonNull(payload, "Event snapshot"), Map.copyOf(extras));
            });
        }
        @Override public void restore(EventState snapshot) {
            boundary.run(owner, () -> {
                requireActive();
                if (snapshot.act() != activeAct) throw new IllegalStateException("Event snapshot belongs to another act");
                if (active instanceof RewindSnapshottable<?> state) restorePayload(state, snapshot.payload());
                if (active instanceof AbstractLevelEventManager manager) {
                    for (RewindSnapshottable<?> extra : manager.extraRewindAdapters()) {
                        Object payload = snapshot.extras().get(extra.key());
                        if (payload == null) extra.resetForMissingSnapshot(); else restorePayload(extra, payload);
                    }
                }
            });
        }
        @Override public void resetForMissingSnapshot() {
            boundary.run(owner, () -> {
                requireActive();
                if (active instanceof AbstractLevelEventManager manager) {
                    active.initLevel(activeZone, activeAct);
                    for (RewindSnapshottable<?> extra : manager.extraRewindAdapters()) extra.resetForMissingSnapshot();
                } else if (active instanceof RewindSnapshottable<?> state) state.resetForMissingSnapshot();
                else if (active != null) active.initLevel(activeZone, activeAct);
            });
        }
    }
    @SuppressWarnings("unchecked")
    private static void restorePayload(RewindSnapshottable<?> adapter, Object payload) {
        ((RewindSnapshottable<Object>) adapter).restore(payload);
    }
}

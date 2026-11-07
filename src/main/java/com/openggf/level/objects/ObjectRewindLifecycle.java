package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PowerUpObject;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PerSlotEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Owns actor rewind capture and reconstruction. Actors reconstructed in the
 * normal pass publish identities, restore fields/references, then settle.
 * Constructor children are adopted rather than spawned twice. Player-bound
 * effects deferred to the later refresh hook complete a separate scalar-only
 * handoff and do not join this pass's already-expired reference table.
 *
 * <p>The population owns storage and ordinary membership/identity operations. The manager owns
 * world clocks, placement and contacts; its named counter-publication phase runs
 * between resetting the population and reconstructing actors. The only manager
 * hooks used here are ordinary admission, service binding and cache invalidation,
 * preserving their creator fault and Stageplay boundaries.
 */
final class ObjectRewindLifecycle {
    private final ObjectManager manager;
    private final ObjectPopulation population;
    private final ObjectCallbackRouter callbacks;
    private final ObjectServices services;
    private final ObjectRegistry registry;
    private final ObjectRewindPlayerBindings playerBindings = new ObjectRewindPlayerBindings();
    private boolean inPlaceRestoreEnabled = true;
    private final Map<ObjectSpawn, ObjectInstance> reuseScratch = new IdentityHashMap<>();
    private final Map<Class<?>, Boolean> reuseConstructionSideEffects = new HashMap<>();
    private final List<AbstractObjectInstance> constructionChildren = new ArrayList<>();
    private boolean capturingChildren;
    private RewindClassResolver classResolver = RewindClassResolver.ENGINE_ONLY;

    ObjectRewindLifecycle(ObjectManager manager, ObjectPopulation population) {
        this.manager = manager;
        this.population = population;
        callbacks = manager.objectCallbacks;
        services = manager.objectServices;
        registry = manager.registry;
    }

    record CapturedPopulation(long[] usedSlotsBits, List<PerSlotEntry> slots,
                              List<ObjectManagerSnapshot.ChildSpawnEntry> childSpawns,
                              List<DynamicObjectEntry> dynamicObjects) { }

    /** Deferred field work is ordered placed-first, then dynamic reconstruction order. */
    private record RestoredObjectState(AbstractObjectInstance instance, PerObjectRewindSnapshot state) { }

    CapturedPopulation capture(RewindCaptureContext rewindContext) {
        List<PerSlotEntry> slots = capturePlacedActors(rewindContext);
        List<ObjectManagerSnapshot.ChildSpawnEntry> childSpawns = captureChildReservations();
        List<DynamicObjectEntry> dynamic = captureDynamicActors(rewindContext);
        return new CapturedPopulation(population.captureOwnedUsedSlotBits(), slots, childSpawns, dynamic);
    }

    private List<PerSlotEntry> capturePlacedActors(RewindCaptureContext rewindContext) {
        // Capture per-active-slot state
        List<PerSlotEntry> slots = new ArrayList<>();
        for (Map.Entry<ObjectSpawn, ObjectInstance> entry : population.placed.entrySet()) {
            ObjectSpawn spawn = entry.getKey();
            ObjectInstance inst = entry.getValue();
            if (inst instanceof AbstractObjectInstance aoi) {
                slots.add(new PerSlotEntry(
                        callbacks.call(inst, aoi::getSlotIndex),
                        spawn,
                        aoi.getClass().getName(),
                        callbacks.captureRewind(aoi, rewindContext),
                        population.identities.get(inst)
                ));
            }
        }
        // Memoized sort keys: a plain extractor would rebuild the
        // spawn-key string per comparison (O(n log n) string builds
        // per capture). Keys are computed once per entry; the sorted
        // order — part of snapshot determinism — is unchanged.
        Map<Object, String> slotSpawnKeys = new java.util.IdentityHashMap<>();
        slots.sort(Comparator
                .comparingInt(PerSlotEntry::slotIndex)
                .thenComparing(entry -> slotSpawnKeys.computeIfAbsent(
                        entry, e -> ObjectRewindOrdering.spawnKey(entry.spawn()))));

        return slots;
    }

    private List<ObjectManagerSnapshot.ChildSpawnEntry> captureChildReservations() {
        // Capture child-slot reservations
        List<ObjectManagerSnapshot.ChildSpawnEntry> childSpawns = new ArrayList<>();
        for (Map.Entry<ObjectSpawn, int[]> entry : population.reservedChildren.entrySet()) {
            int[] slotArray = entry.getValue();
            childSpawns.add(new ObjectManagerSnapshot.ChildSpawnEntry(
                    entry.getKey(),
                    Arrays.copyOf(slotArray, slotArray.length)
            ));
        }
        Map<Object, String> childParentKeys = new java.util.IdentityHashMap<>();
        Map<Object, String> childSlotKeys = new java.util.IdentityHashMap<>();
        childSpawns.sort(Comparator
                .comparing((ObjectManagerSnapshot.ChildSpawnEntry entry) ->
                        childParentKeys.computeIfAbsent(
                                entry, e -> ObjectRewindOrdering.spawnKey(entry.parentSpawn())))
                .thenComparing(entry -> childSlotKeys.computeIfAbsent(
                        entry, e -> Arrays.toString(entry.reservedSlots()))));

        return childSpawns;
    }

    private List<DynamicObjectEntry> captureDynamicActors(RewindCaptureContext rewindContext) {
        List<DynamicObjectEntry> dynamicEntries =
                new ArrayList<>();
        for (ObjectInstance inst : population.dynamic) {
            if (population.dynamicOwnership.excludesFromRewind(inst)) {
                continue;
            }
            if (inst instanceof AbstractObjectInstance aoi) {
                var playerOwner = playerBoundOwner(inst);
                var playerOwnerId = playerOwner == null ? null
                        : rewindContext.requireIdentityTable().encodePlayer(playerOwner);
                dynamicEntries.add(
                        new DynamicObjectEntry(
                                inst.getClass().getName(),
                                inst.getSpawn(),
                                aoi.getSlotIndex(),
                                callbacks.captureRewind(aoi, rewindContext),
                                playerOwnerId == null ? playerOwner : null,
                                population.identities.get(inst),
                                callbacks.owner(inst),
                                population.dynamicOwnership.isRewindableAuxiliary(inst),
                                playerOwnerId));
            }
        }

        return dynamicEntries;
    }

    RewindCaptureContext captureIdentityContext() {
        RewindIdentityTable table = new RewindIdentityTable();
        registerPlayersInto(table);
        population.registerIdentities(table);
        return RewindCaptureContext.withIdentityTable(table);
    }

    RewindCaptureContext restorePopulation(ObjectManagerSnapshot s, Runnable publishWorldCounters) {
        // Reconstructed actors form a two-phase graph: publish their identities,
        // restore fields/references, then settle. A generic recreation may instead
        // defer a player effect to the later refresh hook. Such an effect is not a
        // required graph target here: required missing refs fail; optional refs stay
        // absent. Late scalar completion does not automatically fix earlier refs.
        RewindIdentityTable restoreTable = new RewindIdentityTable();
        registerPlayersInto(restoreTable);
        // Resolve structural player bindings while this restore table is live. Keep one
        // prepared identity per original entry so parked probe retries cannot duplicate work.
        var preparedDynamicEntries = playerBindings.prepareEntries(s.dynamicObjects(), restoreTable);
        RewindCaptureContext rewindContext = RewindCaptureContext.withIdentityTable(restoreTable);
        List<RestoredObjectState> restoredObjects = new ArrayList<>();

        // Index live instances by spawn identity so matching snapshot entries
        // can reuse them in place instead of paying a full reconstruction.
        Map<ObjectSpawn, ObjectInstance> previousActive = reuseScratch;
        previousActive.clear();
        if (inPlaceRestoreEnabled && !population.placed.isEmpty()) {
            previousActive.putAll(population.placed);
        }
        // Clear current active objects (without mutating ObjectPlacementController state).
        // Extra live objects absent from the snapshot are dropped here exactly as the
        // pre-reuse path dropped them (reference drop, no onUnload), via not being
        // re-registered below.
        population.clearForReconstruction(s.dynamicObjects());
        // Capture construction-spawned children produced while active objects are
        // reconstructed below, so the dynamic reconciliation loop can adopt them in
        // place instead of recreating duplicates. (See registerRewindReconstructionChild.)
        constructionChildren.clear();
        capturingChildren = true;

        // Restore scalar counters and slot occupancy
        population.allocator.restoreFromLongArray(s.usedSlotsBits());
        publishWorldCounters.run();
        // Restore the object-id counter so any new objects spawned after restore
        // (in replay or fresh gameplay) do not alias the restored objects' ids.
        population.restoreIdentitySequence(s.dynamicObjectIdCounter());

        restoreActiveObjects(s.slots(), previousActive, restoreTable, restoredObjects);

        // Restore reserved child slots after placed-object reconstruction.
        population.restoreChildReservations(s.childSpawns());

        restoreDynamicObjects(s.dynamicObjects(), preparedDynamicEntries,
                restoreTable, restoredObjects);

        // Identities of actors reconstructed in this pass are now available.
        // Deferred player effects are excluded; settle only the restored graph.
        restoreObjectFieldsAndSettle(restoredObjects, rewindContext);

        return rewindContext;
    }

    /** Reuse or construct placed actors and publish identities; field restoration follows reconstruction. */
    private void restoreActiveObjects(List<PerSlotEntry> entries,
            Map<ObjectSpawn, ObjectInstance> previousActive, RewindIdentityTable restoreTable,
            List<RestoredObjectState> restoredObjects) {
        try {
            for (PerSlotEntry entry : entries) {
                ObjectSpawn spawn = entry.spawn();
                int targetSlot = entry.slotIndex();
                ObjectInstance previous = previousActive.get(spawn);
                if (previous != null) callbacks.register(previous, spawn);
                ObjectInstance inst;
                if (previous instanceof AbstractObjectInstance previousAoi
                        && canReuseForRewindRestore(previousAoi, entry)) {
                    // Reused instances keep their injected services and renderer wiring.
                    inst = previous;
                } else {
                    if (previous != null) callbacks.unregister(previous);
                    // Use PRE_ALLOCATED_SLOT so the constructor picks up the correct slot
                    int dynamicCountBefore = population.dynamic.size();
                    int reservedChildBefore = population.reservedChildren.size();
                    int reconstructionChildBefore = constructionChildren.size();
                    inst = ObjectConstructionContext.withRewindActiveRestore(
                            () -> ObjectConstructionContext.with(services, targetSlot,
                                    () -> registry != null ? callbacks.construct(
                                            spawn, () -> registry.create(spawn)) : null));
                    if (inst != null) {
                        if (inst instanceof AbstractObjectInstance constructed) {
                            callbacks.run(constructed, () -> manager.bindObjectServices(constructed));
                            callbacks.run(constructed, () -> ObjectConstructionContext.withRewindActiveRestore(() -> {
                                constructed.recreateConstructionChildrenForRewind();
                                return null;
                            }));
                        }
                        // Constructors that spawn children or reserve child slots have
                        // restore-relevant construction side effects that in-place reuse
                        // would skip; latch those classes onto the recreate path. Under
                        // rewind restore, child spawns are routed to
                        // constructionChildren (not population.dynamic), so count that
                        // growth too — otherwise a child-spawning boss would look
                        // side-effect-free and wrongly become reuse-eligible.
                        boolean constructionSideEffects =
                                population.dynamic.size() != dynamicCountBefore
                                || population.reservedChildren.size() != reservedChildBefore
                                || constructionChildren.size() != reconstructionChildBefore;
                        reuseConstructionSideEffects.merge(
                                inst.getClass(), constructionSideEffects, Boolean::logicalOr);
                    }
                }
                if (inst instanceof AbstractObjectInstance aoi) {
                    callbacks.run(aoi, () -> manager.bindObjectServices(aoi));
                    if (callbacks.call(aoi, aoi::getSlotIndex) < 0 && targetSlot >= 0) {
                        callbacks.run(aoi, () -> aoi.setSlotIndex(targetSlot));
                    }
                    population.admitRestoredPlaced(spawn, inst, entry.objectId(), restoreTable);
                    // Defer per-instance field-blob application to phase 2.
                    restoredObjects.add(new RestoredObjectState(aoi, entry.state()));
                } else if (inst != null) {
                    population.admitRestoredPlaced(spawn, inst, entry.objectId(), restoreTable);
                }
            }
        } finally {
            previousActive.clear();
        }
    }

    private void restoreDynamicObjects(List<DynamicObjectEntry> entries,
            Map<DynamicObjectEntry, DynamicObjectEntry> preparedDynamicEntries,
            RewindIdentityTable restoreTable, List<RestoredObjectState> restoredObjects) {
        List<DynamicObjectEntry> parkedDynamicEntries = new ArrayList<>();
        for (DynamicObjectEntry entry : entries) {
            var prepared = preparedDynamicEntries.get(entry);
            if (!restoreDynamicObject(prepared, restoreTable, restoredObjects)) {
                parkedDynamicEntries.add(prepared);
            }
        }
        // Snapshot order may put a child before the parent that constructs it.
        // Each successful pass can expose another generation of adoptable children;
        // one retry cannot resolve deeper chains. Stop when no entry makes progress.
        boolean parkedPassMadeProgress = true;
        while (parkedPassMadeProgress && !parkedDynamicEntries.isEmpty()) {
            parkedPassMadeProgress = false;
            List<DynamicObjectEntry> stillParked = new ArrayList<>();
            for (DynamicObjectEntry entry : parkedDynamicEntries) {
                if (restoreDynamicObject(entry, restoreTable, restoredObjects)) {
                    parkedPassMadeProgress = true;
                } else {
                    stillParked.add(entry);
                }
            }
            parkedDynamicEntries = stillParked;
        }
        // Stop routing further child spawns into the reconstruction-children scratch:
        // any spawns triggered from here on (Phase 2 state restore,
        // afterRewindRestoreSettled, or later gameplay) are normal wiring, not
        // reconstruction side effects to adopt.
        capturingChildren = false;
        // A constructor can create children absent from the captured population.
        // Notify their owners before dropping them so no parent retains a live orphan.
        for (AbstractObjectInstance unmatched : constructionChildren) {
            callbacks.run(unmatched, unmatched::onDroppedAsUnmatchedRewindReconstructionChild);
        }
        constructionChildren.clear();
    }

    /** Adopt or recreate one dynamic actor and publish its captured slot/identity before restoring fields. */
    private boolean restoreDynamicObject(DynamicObjectEntry entry,
            RewindIdentityTable restoreTable, List<RestoredObjectState> restoredObjects) {
        // Prefer adopting the construction-spawned child the reconstructed parent
        // already created and back-references. Adopting it in place gives exact
        // captured state AND keeps the parent's child reference valid, with no
        // double-spawn. Routine-spawned children (no construction counterpart) fall
        // back to the generic recreate path.
        AbstractObjectInstance adopted = adoptRewindReconstructionChild(entry);
        ObjectInstance inst = adopted != null
                ? adopted
                : ObjectConstructionContext.withRewindActiveRestore(
                        () -> callbacks.construct(entry.ownerModId(),
                                () -> recreateDynamicObject(entry)));
        if (!(inst instanceof AbstractObjectInstance aoi)) {
            return false;
        }
        callbacks.run(aoi, () -> manager.bindObjectServices(aoi));
        // Both adopted children and generic recreations must own the
        // captured slot before callbacks and population.executionOrder registration.
        // A spawn-only constructor leaves slotIndex at -1; waiting
        // for phase-2 field restore loses the slot lookup used to
        // rebind riding/standing contacts to moving dynamic objects.
        // The allocator already owns the snapshot reservation.
        int slot = entry.slotIndex();
        if (slot >= 0) {
            callbacks.run(aoi, () -> aoi.setSlotIndex(slot));
        }
        if (adopted == null) {
            callbacks.run(aoi, () -> ObjectConstructionContext.withRewindActiveRestore(() -> {
                aoi.recreateConstructionChildrenForRewind();
                return null;
            }));
            // Went through the generic recreateForRewind() path, not adoption --
            // give owners that keep their own back-reference list (e.g.
            // AbstractBossChild -> parent.childComponents) a chance to re-register
            // this instance. Adopted candidates were already registered by the
            // owning parent's own construction-time spawn, so this is skipped for
            // them (see onRecreatedForRewind()'s javadoc).
            callbacks.run(aoi, aoi::onRecreatedForRewind);
        }
        population.publishReconstructedDynamic(aoi, entry, restoreTable);
        // Defer per-instance field-blob application to phase 2.
        restoredObjects.add(new RestoredObjectState(aoi, entry.state()));
        return true;
    }

    private void restoreObjectFieldsAndSettle(List<RestoredObjectState> restoredObjects,
            RewindCaptureContext rewindContext) {
        for (RestoredObjectState work : restoredObjects) {
            callbacks.restoreRewind(work.instance(), work.state(), rewindContext);
        }
        for (RestoredObjectState work : restoredObjects) {
            callbacks.run(work.instance(), work.instance()::afterRewindRestoreSettled);
        }
    }

    private boolean canReuseForRewindRestore(AbstractObjectInstance previous,
            PerSlotEntry entry) {
        if (callbacks.call(previous, previous::getSlotIndex) != entry.slotIndex()) {
            return false;
        }
        Class<?> type = previous.getClass();
        if (entry.className() == null || !type.getName().equals(entry.className())) {
            return false;
        }
        // Require one observed in-restore reconstruction of this class without
        // construction side effects before reusing instances of it.
        Boolean observed = reuseConstructionSideEffects.get(type);
        if (observed == null || observed) { // null = never observed in-restore, true = has side effects
            return false;
        }
        return ObjectRewindTypeSafety.isSafe(type);
    }

    private PlayableEntity playerBoundOwner(ObjectInstance inst) {
        if (inst instanceof ShieldObjectInstance shield) {
            return callbacks.call(inst, shield::getPlayer);
        }
        if (inst instanceof PowerUpObject powerUp && powerUp.isInvincibilityStars()) {
            return powerUp.boundPlayer();
        }
        return null;
    }

    private ObjectInstance recreateDynamicObject(
            DynamicObjectEntry entry) {
        entry = NativeCompetitionPlayerBoundRestore.rebind(manager, services, entry);
        DynamicObjectRecreateContext context = new DynamicObjectRecreateContext(manager, classResolver);
        // Generic reconstruction: only classes that explicitly implement
        // RewindRecreatable opt into dynamic restore. Unsupported dynamic classes
        // still drop on restore and remain visible in coverage diagnostics.
        if (isRewindRecreatable(entry, context)) {
            return ObjectRewindDynamicCodecs.genericRecreate(entry, context);
        }
        return null;
    }

    private static boolean isRewindRecreatable(DynamicObjectEntry entry,
                                               DynamicObjectRecreateContext context) {
        if (entry.className() == null) return false;
        return context.classResolver().resolve(entry.ownerModId(), entry.className())
                .filter(RewindRecreatable.class::isAssignableFrom).isPresent();
    }

    private void registerPlayersInto(RewindIdentityTable table) {
        com.openggf.sprites.playable.AbstractPlayableSprite main = null;
        List<PlayableEntity> sidekicks = List.of();
        if (services != null) {
            var sprites = services.spriteManager();
            if (sprites != null) {
                main = sprites.getMainPlayable();
            } else {
                // Standalone services may supply a camera without a roster owner.
                var camera = services.camera();
                main = camera != null ? camera.getFocusedSprite() : null;
            }
            List<PlayableEntity> serviceSidekicks = services.sidekicks();
            if (serviceSidekicks != null) {
                sidekicks = serviceSidekicks;
            }
        }
        if (main != null) {
            table.registerPlayer(main, com.openggf.game.rewind.identity.PlayerRefId.mainPlayer());
        }
        for (int i = 0; i < sidekicks.size(); i++) {
            PlayableEntity sidekick = sidekicks.get(i);
            if (sidekick == null || sidekick == main) {
                continue;
            }
            table.registerPlayer(sidekick, com.openggf.game.rewind.identity.PlayerRefId.sidekick(i));
        }
    }

    /**
     * Match creator owner and runtime class, preferring the captured construction
     * subtype/ordinal. FIFO remains the fallback for children without that ordinal
     * convention; adopting the actual constructor child preserves parent references.
     */
    private AbstractObjectInstance adoptRewindReconstructionChild(
            DynamicObjectEntry entry) {
        String className = entry.className();
        if (className == null) {
            return null;
        }
        int capturedSubtype = entry.spawn() != null ? entry.spawn().subtype() : 0;
        int fifoFallbackIndex = -1;
        for (int i = 0; i < constructionChildren.size(); i++) {
            AbstractObjectInstance child = constructionChildren.get(i);
            if (!child.getClass().getName().equals(className) || !java.util.Objects.equals(
                    entry.ownerModId(), callbacks.owner(child))) {
                continue;
            }
            if (fifoFallbackIndex < 0) {
                fifoFallbackIndex = i;
            }
            ObjectSpawn childSpawn = callbacks.call(child, child::getSpawn);
            if (childSpawn != null && childSpawn.subtype() == capturedSubtype) {
                return constructionChildren.remove(i);
            }
        }
        return fifoFallbackIndex >= 0 ? constructionChildren.remove(fifoFallbackIndex) : null;
    }

    RewindClassResolver classResolver() { return classResolver; }

    void setClassResolver(RewindClassResolver resolver) {
        classResolver = java.util.Objects.requireNonNull(resolver, "resolver");
    }

    void clearPendingPlayerBindings() { playerBindings.clear(); }
    void setInPlaceRestoreEnabled(boolean enabled) { inPlaceRestoreEnabled = enabled; }
    boolean capturesConstructionChildren() { return capturingChildren; }

    void recordConstructionChild(AbstractObjectInstance child) {
        if (child != null && capturingChildren) {
            manager.registerInheritedCallbackOwner(child);
            constructionChildren.add(child);
        }
    }

    /**
     * Completes the captured effect for this freshly recreated player incarnation.
     * Selection and the late handoff live together: creator callback owner,
     * captured identity, native admission, captured order, collision binding,
     * then scalar fields.
     * An unmatched object follows ordinary power-up admission in the spawner.
     */
    boolean completePendingPlayerBoundObject(ObjectInstance object) {
        Class<?> baseType;
        Supplier<PlayableEntity> owner;
        if (object instanceof ShieldObjectInstance shield) {
            baseType = ShieldObjectInstance.class;
            owner = shield::getPlayer;
        } else if (object instanceof PowerUpObject powerUp && powerUp.isInvincibilityStars()) {
            baseType = InvincibilityStarsObjectInstance.class;
            owner = powerUp::boundPlayer;
        } else {
            return false;
        }
        DynamicObjectEntry captured = playerBindings.consumeForOwnerAndClass(
                baseType, object.getClass(), owner);
        if (captured == null) return false;

        callbacks.registerOwnerIfAbsent(object, captured.ownerModId());
        if (captured.objectId() != null) {
            population.restoreCapturedIdentity(object, captured.objectId());
            ObjectLifetimeOps.addDynamicAtReservedSlot(manager, object, captured.slotIndex());
            population.restoreCapturedOrder();
            manager.bindRestoredCollisionObject(captured.objectId(), object);
        } else {
            // Legacy entries have no stable captured identity; preserve native fixed-slot admission.
            ObjectLifetimeOps.addDynamicAtReservedSlot(manager, object, captured.slotIndex());
        }
        if (object instanceof AbstractObjectInstance actor) {
            // Keep the original late-spawner overload. Actor reconstruction uses
            // the context-aware field pass; this existing scalar handoff does not.
            actor.restoreRewindState(captured.state());
        }
        return true;
    }

    void enqueuePendingPlayerBoundEntry(Class<?> baseType, DynamicObjectEntry entry) {
        playerBindings.enqueue(baseType, entry);
    }

    ObjectRewindPlayerBindings.PendingReturn claimPendingPlayerBoundReturn(
            Class<?> baseType, DynamicObjectEntry entry, PlayableEntity owner) {
        return playerBindings.claimReturn(baseType, entry, owner);
    }

    DynamicObjectEntry consumePendingPlayerBoundEntry(
            Class<?> baseType, Predicate<DynamicObjectEntry> matcher) {
        return playerBindings.consume(baseType, matcher);
    }
}

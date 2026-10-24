package com.openggf.level.objects;

import com.openggf.camera.Camera;

import java.util.Optional;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Native placement admission and structural retirement for a directed world. */
final class ObjectStageplayController {
    private final ObjectManager objects;

    private final List<ObjectSpawn> sourceSpawns;
    private final String sourceGame;
    private final int sourceZone, sourceAct;

    ObjectStageplayController(ObjectManager objects, List<ObjectSpawn> spawns, ObjectServices services) {
        this.objects = objects;
        var world = services == null ? null : services.worldSession();
        var module = world == null ? null : world.getGameModule();
        var nativeSource = module == null ? null : module.getGameService(NativePlacementSource.class);
        sourceSpawns = nativeSource == null ? List.copyOf(spawns) : List.copyOf(nativeSource.placements());
        var game = module == null ? null : module.getGameId();
        sourceGame = game == null ? null : game.code();
        sourceZone = world == null ? 0 : world.getCurrentZone();
        sourceAct = world == null ? 0 : world.getCurrentAct();
    }

    List<ObjectSpawn> sourceSpawns() { return sourceSpawns; }

    boolean isRingPlacement(ObjectSpawn spawn) {
        // Auxiliary routines can share a numeric ring ID without being placements.
        return spawn != null && spawn.layoutIndex() >= 0
                && objects.registry instanceof MutatorPlacementClassifier classifier
                && classifier.isRingPlacement(spawn);
    }

    void noteFactorySuppression(ObjectSpawn spawn) {
        if (objects.stageplayActivation != null && objects.placement.isCounterBasedRespawn()
                && objects.placement.getCounterForSpawn(spawn) >= 0 && !objects.placement.isRemembered(spawn)
                && !objects.placement.isDestroyedInWindow(objects.placement.getSpawnIndex(spawn)))
            objects.stageplayActivation.skippedFactory(objects.placement.getSpawnIndex(spawn));
    }

    void activateWindows(Camera camera, BooleanSupplier freeSlot, Predicate<ObjectSpawn> admit) {
        if (objects.stageplayActivation == null) return;
        objects.placement.activateStageplayWindows(sourceSpawns, objects.activeObjects.keySet(), camera,
                objects::stageplayAllowsPlacement, freeSlot, admit);
    }

    StageplayObjectDirection.PlacementIdentity identity(ObjectSpawn spawn) {
        if (sourceGame == null)
            throw new IllegalStateException("Portable object placement requires a native game identity");
        return StageplayObjectDirection.PlacementIdentity.of(sourceGame, sourceZone, sourceAct, spawn);
    }

    Optional<ObjectInstance> find(StageplayObjectDirection.PlacementIdentity identity) {
        return objects.activeObjects.entrySet().stream()
                .filter(entry -> entry.getKey().layoutIndex() >= 0 && identity(entry.getKey()).equals(identity))
                .map(java.util.Map.Entry::getValue).findFirst();
    }

    private ObjectSpawn canonical(StageplayObjectDirection.PlacementIdentity identity) {
        return sourceSpawns.stream()
                .filter(spawn -> spawn.layoutIndex() >= 0 && identity(spawn).equals(identity))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "Portable object placement is absent from this set: " + identity));
    }

    void retire(ObjectInstance object) {
        objects.suspendStageplayContacts(object);
        ObjectSpawn placementSpawn = objects.instanceToSpawn.get(object);
        objects.releaseSlotIfManaged(object);
        if (object instanceof AbstractObjectInstance nativeObject)
            ObjectLifetimeOps.clearPreviousManagerSlot(nativeObject);
        if (placementSpawn != null) {
            objects.freeAllReservedChildSlots(placementSpawn);
            objects.placement.removeFromActive(placementSpawn);
            objects.removeActiveObject(placementSpawn);
        } else objects.removeDynamicObjectInstance(object);
        // A picture removal retires the graph without invoking native cleanup
        // routines that can publish boss/event state. Structural members are
        // retired explicitly by the controller, including constructor children.
        objects.deferredDynamicExecThisFrame.remove(object);
        objects.bucketsDirty = true;
        objects.markActiveObjectCachesDirty();
    }

    ObjectInstance materialize(
            StageplayObjectDirection.PlacementIdentity identity) {
        ObjectSpawn spawn = canonical(identity);
        if (!objects.stageplayAllowsPlacement(spawn)) {
            throw new IllegalArgumentException("Object placement was removed by the screenplay");
        }
        return materialize(spawn);
    }

    ObjectInstance materialize(ObjectSpawn requested) {
        ObjectSpawn spawn = canonical(identity(requested));
        ObjectInstance existing = find(identity(spawn)).orElse(null);
        if (existing != null) {
            if (objects.objectCallbacks.call(existing, existing::isDestroyed))
                throw new IllegalStateException("Placed object is retired by native state; "
                        + "cast a fresh unplaced object to start another native routine");
            return existing;
        }
        if (objects.placement.isCounterBasedRespawn()) {
            if (objects.stageplayActivation == null || objects.placement.getSpawnIndex(spawn) < 0)
                throw new IllegalStateException("Placed object has no canonical native respawn context; "
                        + "retain the level layout or cast a fresh unplaced object");
            if (!objects.placement.admitStageplayPlacement(spawn, true))
                throw new IllegalStateException("Placed object is retired or remembered by native state; "
                        + "cast a fresh unplaced object to start another native routine");
        }
        // Explicit actor readmission preserves the removal filter for native placement scans.
        int slot = objects.allocateSlot();
        if (slot < 0) throw new IllegalStateException("Native object activation window is full");
        ObjectInstance instance;
        try {
            instance =
                    ObjectDirectionAccess.forManager(objects)
                            .withAuthoredAdmission(
                                    "stageplay-placement",
                                    () ->
                                            ObjectConstructionContext.with(
                                                    objects.objectServices,
                                                    slot,
                                                    () ->
                                                            objects.registry == null
                                                                    ? null
                                                                    : objects.objectCallbacks.construct(
                                                                            spawn,
                                                                            () ->
                                                                                    objects.registry.create(
                                                                                            spawn))));
        } catch (RuntimeException | Error failure) {
            objects.releaseSlot(slot);
            throw failure;
        }
        if (instance == null) {
            objects.releaseSlot(slot);
            throw new IllegalArgumentException("Object placement has no native factory");
        }
        if (instance instanceof AbstractObjectInstance object) {
            objects.runObjectCallback(instance, () -> {
                objects.bindObjectServices(object);
                if (object.getSlotIndex() < 0) object.setSlotIndex(slot);
                if (objects.placement.isCounterBasedRespawn()) {
                    int counter = objects.placement.getCounterForSpawn(spawn);
                    if (counter >= 0) object.setRespawnStateIndex(counter);
                }
            });
        } else objects.releaseSlot(slot);
        // Explicit cast ownership must exist before the persistent clear admission gate.
        // Keep vanilla placement scans filtered while permitting this authored readmission.
        objects.stageplayDirection.claimAuthored(instance);
        objects.registerActiveObject(spawn, instance);
        objects.bucketsDirty = true;
        objects.markActiveObjectCachesDirty();
        return instance;
    }

}

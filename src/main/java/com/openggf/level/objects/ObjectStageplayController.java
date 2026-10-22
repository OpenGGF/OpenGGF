package com.openggf.level.objects;

import java.util.Optional;

/** Native placement admission and structural retirement for a directed world. */
final class ObjectStageplayController {
    private final ObjectManager objects;

    ObjectStageplayController(ObjectManager objects) { this.objects = objects; }

    StageplayObjectDirection.PlacementIdentity identity(ObjectSpawn spawn) {
        return StageplayObjectDirection.PlacementIdentity.of(objects.stageplaySourceGame,
                objects.stageplaySourceZone, objects.stageplaySourceAct, spawn);
    }

    Optional<ObjectInstance> find(StageplayObjectDirection.PlacementIdentity identity) {
        return objects.activeObjects.entrySet().stream()
                .filter(entry -> entry.getKey().layoutIndex() >= 0 && identity(entry.getKey()).equals(identity))
                .map(java.util.Map.Entry::getValue).findFirst();
    }

    private ObjectSpawn canonical(StageplayObjectDirection.PlacementIdentity identity) {
        return objects.stageplaySourceSpawns.stream()
                .filter(spawn -> spawn.layoutIndex() >= 0 && identity(spawn).equals(identity))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "Portable object placement is absent from this set: " + identity));
    }

    void retire(ObjectInstance object) {
        objects.suspendStageplayContacts(object);
        ObjectSpawn placementSpawn = objects.instanceToSpawn.get(object);
        objects.releaseSlotIfManaged(object);
        if (object instanceof AbstractObjectInstance nativeObject) nativeObject.setSlotIndex(-1);
        if (placementSpawn != null) {
            objects.freeAllReservedChildSlots(placementSpawn);
            objects.placement.removeFromActive(placementSpawn);
            objects.removeActiveObject(placementSpawn);
        } else objects.removeDynamicObjectInstance(object);
        // A picture removal retires the graph without invoking native cleanup
        // routines that can publish boss/event state. Structural members are
        // retired explicitly by the controller, including constructor children.
        objects.deferredDynamicExecThisFrame.remove(object);
        objects.bucketsDirty = objects.activeObjectsCacheDirty = true;
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
            objects.bindObjectServices(object);
            if (object.getSlotIndex() < 0) object.setSlotIndex(slot);
            if (objects.placement.isCounterBasedRespawn()) {
                int counter = objects.placement.getCounterForSpawn(spawn);
                if (counter >= 0) object.setRespawnStateIndex(counter);
            }
        } else objects.releaseSlot(slot);
        // Explicit cast ownership must exist before the persistent clear admission gate.
        // Keep vanilla placement scans filtered while permitting this authored readmission.
        objects.stageplayDirection.claimAuthored(instance);
        objects.registerActiveObject(spawn, instance);
        objects.bucketsDirty = objects.activeObjectsCacheDirty = true;
        return instance;
    }

}

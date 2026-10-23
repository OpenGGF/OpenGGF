package com.openggf.level.objects;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.competition.NativeCompetitionRoster;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/** Internal competition streaming seam; deliberately outside the creator API. */
public final class ObjectManagerInternalAccess {
    private ObjectManagerInternalAccess() { }

    /** Bind before reset(), whose initial placement pass seeds both native cursors. */
    public static void enableNativeCompetitionStreaming(ObjectManager manager,
                                                         NativeCompetitionObjectStreaming streaming) {
        manager.nativeCompetitionStreaming().enableNativeCompetitionStreaming(streaming);
    }

    /** Bind before reset. One source-order pass, independent of either camera. */
    public static void enableNativeCompetitionUpfrontPlacement(ObjectManager manager, int maxXExclusive) {
        manager.nativeCompetitionStreaming().enableNativeCompetitionUpfrontPlacement(maxXExclusive);
    }

    /** Register alongside the ordinary object snapshot, which owns actors and respawn bytes. */
    public static RewindSnapshottable<Object> nativeCompetitionUpfrontPlacementRewindAdapter(ObjectManager manager) {
        return new RewindSnapshottable<>() {
            @Override public String key() { return "native_competition_upfront_placement"; }
            @Override public Object capture() { return manager.nativeCompetitionStreaming().captureNativeCompetitionUpfrontPlacement(); }
            @Override public void restore(Object snapshot) { manager.nativeCompetitionStreaming().restoreNativeCompetitionUpfrontPlacement(snapshot); }
        };
    }

    /** -1 leaves ordinary allocation unchanged; the active policy supplies the exclusive bound. */
    public static int nativeCompetitionGeneralAllocationSlotLimitExclusive(ObjectManager manager) {
        return manager.nativeCompetitionStreaming().nativeCompetitionGeneralAllocationSlotLimitExclusive();
    }

    public static RewindSnapshottable<Object> nativeCompetitionStreamingRewindAdapter(ObjectManager manager) {
        return new RewindSnapshottable<>() {
            @Override public String key() { return "native_competition_object_streaming"; }
            @Override public Object capture() { return manager.nativeCompetitionStreaming().captureNativeCompetitionStreaming(); }
            @Override public void restore(Object snapshot) { manager.nativeCompetitionStreaming().restoreNativeCompetitionStreaming(snapshot); }
        };
    }

    public static void swapNativeCompetitionStreamingSlots(ObjectManager manager) {
        manager.nativeCompetitionStreaming().swapNativeCompetitionStreamingSlots();
    }

    /** Placement streaming shares the manager's live collections and private callbacks. */
    static final class Streaming implements NativeCompetitionObjectStreaming.Loader {
        private final ObjectPlacementController placement;
        private final ObjectPlacementAdmission placementAdmission;
        private final ObjectRegistry registry;
        private final SlotAllocator slotAllocator;
        private final ObjectSlotLayout slotLayout;
        private final ObjectServices objectServices;
        private final ObjectCallbackRouter objectCallbacks;
        private final Map<ObjectSpawn, ObjectInstance> activeObjects;
        private final Map<ObjectInstance, ObjectSpawn> instanceToSpawn;
        private final List<ObjectInstance> dynamicObjects;
        private final BiConsumer<ObjectSpawn, ObjectInstance> registerActiveObject;
        private final Consumer<ObjectSpawn> removeActiveObject;
        private final Predicate<ObjectInstance> removeDynamicObjectInstance;
        private final IntConsumer releaseSlot;
        private final Runnable invalidateCaches;

        Streaming(ObjectPlacementController placement, ObjectPlacementAdmission placementAdmission,
                ObjectRegistry registry, SlotAllocator slotAllocator, ObjectSlotLayout slotLayout,
                ObjectServices objectServices, ObjectCallbackRouter objectCallbacks,
                Map<ObjectSpawn, ObjectInstance> activeObjects, Map<ObjectInstance, ObjectSpawn> instanceToSpawn,
                List<ObjectInstance> dynamicObjects, BiConsumer<ObjectSpawn, ObjectInstance> registerActiveObject,
                Consumer<ObjectSpawn> removeActiveObject, Predicate<ObjectInstance> removeDynamicObjectInstance,
                IntConsumer releaseSlot, Runnable invalidateCaches) {
            this.placement = placement;
            this.placementAdmission = placementAdmission;
            this.registry = registry;
            this.slotAllocator = slotAllocator;
            this.slotLayout = slotLayout;
            this.objectServices = objectServices;
            this.objectCallbacks = objectCallbacks;
            this.activeObjects = activeObjects;
            this.instanceToSpawn = instanceToSpawn;
            this.dynamicObjects = dynamicObjects;
            this.registerActiveObject = registerActiveObject;
            this.removeActiveObject = removeActiveObject;
            this.removeDynamicObjectInstance = removeDynamicObjectInstance;
            this.releaseSlot = releaseSlot;
            this.invalidateCaches = invalidateCaches;
        }

        boolean hasFreeDynamicSlotForDeferredAllocation(int alreadyDeferred) {
            int limit = nativeCompetitionGeneralAllocationSlotLimitExclusive();
            if (limit < 0) return slotAllocator.freeSlotCount() > alreadyDeferred;
            int available = 0;
            for (int slot = slotLayout.firstDynamicSlot(); slot < limit; slot++) {
                if (slotAllocator.isEmpty(slot)) available++;
            }
            return available > alreadyDeferred;
        }

        int firstFreeDynamicSlot() {
            int slot = slotAllocator.firstFreeSlot();
            int limit = nativeCompetitionGeneralAllocationSlotLimitExclusive();
            return slot >= 0 && (limit < 0 || slot < limit) ? slot : -1;
        }

        private NativeCompetitionRoster nativeCompetitionRoster() {
            return NativeCompetitionRoster.from(objectServices.worldSession());
        }

        boolean active() {
            return placement.hasNativeCompetitionStreaming() && nativeCompetitionRoster() != null;
        }

        void enableNativeCompetitionStreaming(NativeCompetitionObjectStreaming streaming) {
            if (nativeCompetitionRoster() == null) {
                throw new IllegalStateException("Native object streaming requires an active explicit roster");
            }
            placement.configureNativeCompetitionStreaming(streaming);
        }

        boolean upfrontActive() {
            return placement.hasNativeCompetitionUpfrontPlacement() && nativeCompetitionRoster() != null;
        }

        void enableNativeCompetitionUpfrontPlacement(int maxXExclusive) {
            if (nativeCompetitionRoster() == null) {
                throw new IllegalStateException("Native upfront placement requires an active explicit roster");
            }
            placement.configureNativeCompetitionUpfrontPlacement(maxXExclusive);
        }

        Object captureNativeCompetitionUpfrontPlacement() {
            return placement.nativeCompetitionUpfrontPlacement().capture();
        }

        void restoreNativeCompetitionUpfrontPlacement(Object snapshot) {
            placement.nativeCompetitionUpfrontPlacement().restore(snapshot);
        }

        void releaseUpfrontPlacementSlots() {
            if (!upfrontActive()) return;
            // reset() clears the entire allocator separately. Editor resync instead
            // replaces only placement owners: retire their SST occupancy before
            // discarding their identities, preserving unrelated dynamic owners.
            for (ObjectInstance instance : activeObjects.values()) {
                if (instance instanceof AbstractObjectInstance aoi) {
                    releaseSlot.accept(objectCallbacks.call(aoi, aoi::getSlotIndex));
                }
            }
        }

        private int nextNativeCompetitionUpfrontSlot(int previousSlot) {
            // CreateNewSprite4 continues the original AllocateObject scan strictly
            // after the last actor; it never restarts at a newly freed earlier slot
            // (sonic3k.asm:37934-37944). No empty probe reserves SST memory.
            for (int slot = previousSlot + 1; slot < slotLayout.lastDynamicSlotExclusive(); slot++) {
                if (slotAllocator.isEmpty(slot)) return slot;
            }
            return -1;
        }

        private boolean loadNativeCompetitionUpfrontSpawn(ObjectSpawn spawn, int slot) {
            return placement.loadNativeCompetitionUpfrontSpawn(spawn,
                    () -> slotAllocator.reserve(slot)
                            && constructNativeCompetitionPlacement(spawn, placement.getCounterForSpawn(spawn), slot));
        }

        boolean initializeUpfrontPlacement() {
            if (!upfrontActive()) return false;
            placement.nativeCompetitionUpfrontPlacement().initialize(slotAllocator::firstFreeSlot,
                    this::loadNativeCompetitionUpfrontSpawn, this::nextNativeCompetitionUpfrontSlot);
            return true;
        }

        boolean isOutsideUpfrontUnloadWindow(int referenceX) {
            return placement.nativeCompetitionUpfrontPlacement().isOutsideUnloadWindow(referenceX);
        }

        int nativeCompetitionGeneralAllocationSlotLimitExclusive() {
            return active()
                    ? placement.nativeCompetitionGeneralAllocationSlotLimitExclusive() : -1;
        }

        Object captureNativeCompetitionStreaming() { return placement.captureNativeCompetitionStreaming(); }
        void restoreNativeCompetitionStreaming(Object snapshot) { placement.restoreNativeCompetitionStreaming(snapshot); }

        void swapNativeCompetitionStreamingSlots() {
            if (!active()) throw new IllegalStateException("Native object streaming is inactive");
            placement.swapNativeCompetitionStreamingSlots();
        }

        void update() {
            NativeCompetitionRoster roster = nativeCompetitionRoster();
            placement.updateNativeCompetitionStreaming(roster.camera(0).getX(), roster.camera(1).getX(),
                    this);
        }

        @Override public boolean load(ObjectSpawn spawn, int counter, int firstSlot, int count) {
            return placement.loadNativeCompetitionSpawn(spawn, counter,
                    () -> createNativeCompetitionPlacement(spawn, counter, firstSlot, count));
        }

        private boolean createNativeCompetitionPlacement(ObjectSpawn spawn, int counter, int firstSlot, int count) {
            if (!placementAdmission.allows(spawn)) return true;
            int selected = -1;
            // Placement-specific AllocateObject_2P scan. SlotAllocator remains the
            // single occupancy authority, including reservations made by other owners.
            for (int slot = firstSlot; slot < firstSlot + count; slot++) {
                if (slotAllocator.reserve(slot)) { selected = slot; break; }
            }
            if (selected < 0) return false;
            return constructNativeCompetitionPlacement(spawn, counter, selected);
        }

        private boolean constructNativeCompetitionPlacement(ObjectSpawn spawn, int counter, int preSlot) {
            ObjectInstance instance = ObjectConstructionContext.with(objectServices, preSlot,
                    () -> registry != null ? objectCallbacks.construct(spawn, () -> registry.create(spawn)) : null);
            if (instance == null) { releaseSlot.accept(preSlot); return false; }
            if (instance instanceof AbstractObjectInstance aoi) {
                objectCallbacks.run(aoi, () -> {
                    aoi.setServices(objectServices);
                    aoi.setSlotIndex(preSlot);
                    if (counter >= 0) aoi.setRespawnStateIndex(counter);
                });
            } else releaseSlot.accept(preSlot);
            // Native ChkLoadObj_2P consults only its respawn byte, never a Java
            // layout identity. An untracked general-pool owner can survive bank
            // eviction and be instantiated again on reentry. Keep both actual SST
            // owners; the older instance now has independent dynamic lifetime.
            ObjectInstance previous = activeObjects.remove(spawn);
            if (previous != null) {
                instanceToSpawn.remove(previous);
                dynamicObjects.add(previous);
            }
            registerActiveObject.accept(spawn, instance);
            invalidateCaches.run();
            return true;
        }

        @Override public void unload(int firstSlot, int count) {
            // ObjectsManager_2P_UnloadObjectBlock deletes actual RAM occupants,
            // even when they have moved away from their original placement column.
            List<ObjectInstance> occupants = new ArrayList<>(activeObjects.values());
            occupants.addAll(dynamicObjects);
            occupants.sort(Comparator.comparingInt(o -> o instanceof AbstractObjectInstance a ? a.getSlotIndex() : -1));
            for (ObjectInstance instance : occupants) {
                if (!(instance instanceof AbstractObjectInstance aoi)) continue;
                int slot = objectCallbacks.call(aoi, aoi::getSlotIndex);
                if (slot < firstSlot || slot >= firstSlot + count) continue;
                int respawn = objectCallbacks.call(aoi, aoi::getRespawnStateIndex);
                placement.clearNativeCompetitionRespawnLatch(respawn);
                ObjectSpawn spawn = instanceToSpawn.get(instance);
                objectCallbacks.runAndUnregister(instance, instance::onUnload);
                releaseSlot.accept(slot);
                if (spawn != null) {
                    placement.removeFromActiveForUnload(spawn);
                    removeActiveObject.accept(spawn);
                } else removeDynamicObjectInstance.test(instance);
                invalidateCaches.run();
            }
        }
    }
}

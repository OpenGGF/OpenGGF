package com.openggf.level.objects;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.competition.NativeCompetitionRoster;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Internal competition streaming seam; deliberately outside the creator API. */
public final class ObjectManagerInternalAccess {
    private ObjectManagerInternalAccess() { }

    public static InitialDispatch captureInitialDispatch(ObjectManager manager) {
        return new InitialDispatch(manager);
    }

    public static void retireInitialDispatch(ObjectManager manager) {
        if (manager != null) manager.initialDispatch().retireState();
    }

    public static final class InitialDispatch {
        private final ObjectManager manager;
        private final InitialObjectDispatchController.Reservation original;

        private InitialDispatch(ObjectManager manager) {
            this.manager = Objects.requireNonNull(manager);
            original = manager.initialDispatch().capture();
        }

        public void requireCurrent() { original.requireCurrent(); }

        public InitialObjectDispatchScope begin(int cameraX, com.openggf.game.PlayableEntity player,
                List<? extends com.openggf.game.PlayableEntity> sidekicks) {
            return manager.initialDispatch().begin(original, cameraX, player, sidekicks);
        }
    }

    private static final class SupersededInitialDispatch extends ObjectCallbackAbortException {
        private SupersededInitialDispatch() { super("Initial object dispatch was superseded", null); }
    }

    static ObjectCallbackAbortException supersededInitialDispatch() { return new SupersededInitialDispatch(); }

    public static boolean isInitialDispatchCancellation(Throwable failure) {
        return failure instanceof SupersededInitialDispatch;
    }

    /** S2's locked title leave runs RunObjects without another placement pass. */
    public static void runNativeCompetitionTitleObjectPass(ObjectManager manager) {
        manager.runNativeCompetitionTitleObjectPass();
    }

    /** Bind before reset(), whose initial placement pass seeds both native cursors. */
    public static void enableNativeCompetitionStreaming(ObjectManager manager,
                                                         NativeCompetitionObjectStreaming streaming) {
        manager.nativeCompetitionStreaming().enableNativeCompetitionStreaming(streaming);
    }

    /** Bind an actual native full-delete memory writer; the callback is structural, not rewind state. */
    public static void enableNativeCompetitionStreaming(ObjectManager manager,
            NativeCompetitionObjectStreaming streaming, IntConsumer occupiedFullDeleteWriter) {
        Objects.requireNonNull(occupiedFullDeleteWriter, "occupied full-delete writer");
        var collaborator = manager.nativeCompetitionStreaming();
        collaborator.enableNativeCompetitionStreaming(streaming);
        collaborator.occupiedFullDeleteWriter = occupiedFullDeleteWriter;
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

    /** Unconditional non-player prefix SST write in [2, firstDynamicSlot), without allocation or unloading. */
    public static <T extends AbstractObjectInstance> T replaceNativeCompetitionPrefixObject(
            ObjectManager manager, int absoluteSlot, Supplier<T> factory) {
        enableNativeCompetitionCollisionPointers(manager);
        // CheckGameOver writes Obj39 directly to shared SSTs 2/3, with no
        // allocation or occupancy branch (s2.asm:38274-38316,41157-41209).
        return ObjectNativeSstOverwrite.replacePrefix(manager.nativeSstSurface(),
                manager.initialCollisionResponseList(), absoluteSlot, factory);
    }

    /** Unconditional fixed-address write; does not delete, unload or allocate the previous owner. */
    public static <T extends AbstractObjectInstance> T replaceNativeCompetitionFixedObject(
            ObjectManager manager, int absoluteSlot, Supplier<T> factory) {
        enableNativeCompetitionCollisionPointers(manager);
        // Item 4 selects Breathing_bubbles directly, with no branch after its
        // occupancy test (loc_36612, sonic3k.asm:73550-73570). The factory owns
        // native field copying; replacement only changes the registered owner.
        return ObjectNativeSstOverwrite.replaceFixed(manager.nativeSstSurface(),
                manager.initialCollisionResponseList(), absoluteSlot, factory);
    }

    /**
     * Configures native pointer reads immediately. Register before initial dispatch
     * and restore AFTER the ordinary object adapter. Missing snapshots fail closed;
     * normal manager reset clears both pointer lists through its canonical owner.
     */
    public static RewindSnapshottable<Object> nativeCompetitionCollisionPointersRewindAdapter(ObjectManager manager) {
        enableNativeCompetitionCollisionPointers(manager);
        return new RewindSnapshottable<>() {
            @Override public String key() { return "native_competition_collision_pointers"; }
            @Override public Object capture() {
                enableNativeCompetitionCollisionPointers(manager);
                return manager.initialCollisionResponseList().captureNativePointers();
            }
            @Override public void restore(Object snapshot) {
                enableNativeCompetitionCollisionPointers(manager);
                manager.initialCollisionResponseList().restoreNativePointers(snapshot);
            }
        };
    }

    /** Immutable canonical previous-player list, resolving each captured physical SST to its live code owner. */
    public static List<ObjectInstance> previousCollisionResponseObjects(ObjectManager manager) {
        enableNativeCompetitionCollisionPointers(manager);
        return List.copyOf(manager.initialCollisionResponseList().playerReadView());
    }

    private static void enableNativeCompetitionCollisionPointers(ObjectManager manager) {
        if (NativeCompetitionRoster.from(manager.services().worldSession()) == null) {
            throw new IllegalStateException("Native collision pointers require an active explicit roster");
        }
        manager.initialCollisionResponseList().configureNativePointerProjection(slot ->
                slot >= 0 && slot < manager.getLastProcessSlotExclusive() ? manager.currentNativeSstOwner(slot) : null);
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
        private IntConsumer occupiedFullDeleteWriter;

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
            occupiedFullDeleteWriter = null;
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
            if (occupiedFullDeleteWriter != null) {
                unloadOccupiedNativeSlots(firstSlot, count);
                return;
            }
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

        private void unloadOccupiedNativeSlots(int firstSlot, int count) {
            int end = Math.addExact(firstSlot, count);
            if (firstSlot < 0 || count < 0 || end > slotLayout.lastProcessSlotExclusive())
                throw new IllegalArgumentException("Invalid native full-delete range");
            // ObjectsManager_2P_UnloadObjectBlock .deleteBlockLoop (s2.asm:33550-33577)
            // reads the current ID at each physical address, skips ID0 entirely,
            // then clears respawn before the full SST clear. Never preclear holes.
            for (int slot = firstSlot; slot < end; slot++) {
                AbstractObjectInstance owner = currentNativeOwner(slot);
                if (owner != null) fullDeleteNativeOwner(slot, owner);
            }
        }

        private AbstractObjectInstance currentNativeOwner(int slot) {
            return ObjectNativeSstOverwrite.currentOwner(activeObjects.values(), dynamicObjects, objectCallbacks, slot);
        }

        private void requireSelectedNativeOwner(int slot, AbstractObjectInstance owner) {
            if (currentNativeOwner(slot) != owner)
                throw new IllegalStateException("Native full-delete owner changed at slot " + slot);
        }

        private void fullDeleteNativeOwner(int slot, AbstractObjectInstance owner) {
            // Keep the selected receiver's router boundary active even when graph
            // retirement unregisters it before onRemoved/onUnload callbacks.
            objectCallbacks.run(owner, () -> {
                requireSelectedNativeOwner(slot, owner);
                int id = owner.getLiveObjectId();
                requireSelectedNativeOwner(slot, owner);
                if (id == 0) return;
                if (id < 0 || id > 0xFF)
                    throw new IllegalStateException("Unknown native full-delete ID at slot " + slot);
                int respawn = owner.getRespawnStateIndex();
                requireSelectedNativeOwner(slot, owner);
                ObjectSpawn spawn = instanceToSpawn.get(owner);
                if (spawn != null && activeObjects.get(spawn) != owner)
                    throw new IllegalStateException("Native full-delete placement owner changed at slot " + slot);
                placement.clearNativeCompetitionRespawnLatch(respawn);
                occupiedFullDeleteWriter.accept(slot);
                requireSelectedNativeOwner(slot, owner);
                if (spawn != null && activeObjects.get(spawn) != owner)
                    throw new IllegalStateException("Native full-delete placement owner changed at slot " + slot);
                boolean detached = false;
                try {
                    ObjectLifetimeOps.clearPreviousManagerSlot(owner);
                    detached = true;
                    releaseSlot.accept(slot);
                    if (spawn != null) {
                        placement.removeFromActiveForUnload(spawn);
                        removeActiveObject.accept(spawn);
                    } else if (!removeDynamicObjectInstance.test(owner)) {
                        throw new IllegalStateException("Native full-delete owner is not registered at slot " + slot);
                    }
                    invalidateCaches.run();
                    owner.onUnload();
                } finally {
                    if (detached) objectCallbacks.unregister(owner);
                }
                // No slot or spawn cleanup follows notification: a callback may
                // already have installed the next physical owner at this address.
            });
        }
    }
}

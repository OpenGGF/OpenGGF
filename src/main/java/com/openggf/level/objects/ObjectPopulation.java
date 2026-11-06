package com.openggf.level.objects;

import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.SpawnRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.level.TransitionSstOccupant;
import com.openggf.level.objects.boss.BossChildComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry;

/**
 * Owns the live object population: placement identity, dynamic membership, SST
 * occupancy/execution slots, child reservations and stable rewind identities.
 * Rendering and rewind operate on this one population; neither keeps a shadow
 * inventory. World clocks, placement cursors and contacts belong to their owners.
 */
final class ObjectPopulation {
    private final ObjectCallbackRouter callbacks;
    private final Runnable invalidateMembership;
    private final BiConsumer<ObjectInstance, Boolean> publishNativeIdentity;
    private final Supplier<StageplayObjectDirection> directedAdmission;
    // Ordinary membership/identity publication and retirement belong to the
    // semantic operations below. Trusted execution-slot writers still include
    // the manager's live cursor phase and native overwrite/adoption surfaces;
    // the child-reservation ledger owns reservation-array/allocator transitions,
    // and native occurrence detach may update the reverse placement association.
    // Those paths must preserve callback reads, cache boundaries and slot order;
    // actor rewind uses the named reconstruction/reset operations, not raw clears.
    final ObjectSlotLayout slots;
    final SlotAllocator allocator;
    final ObjectInstance[] executionOrder;
    final Map<ObjectSpawn, ObjectInstance> placed = new IdentityHashMap<>();
    final Map<ObjectInstance, ObjectSpawn> spawnByInstance = new IdentityHashMap<>();
    final List<ObjectInstance> dynamic = new ArrayList<>();
    final DynamicObjectOwnership dynamicOwnership = new DynamicObjectOwnership();
    final Map<ObjectSpawn, int[]> reservedChildren = new IdentityHashMap<>();
    final IdentityHashMap<ObjectInstance, ObjectRefId> identities =
            new IdentityHashMap<>();
    private int nextIdentityOrdinal;

    ObjectPopulation(ObjectCallbackRouter callbacks, ObjectSlotLayout slots,
                     Runnable invalidateMembership,
                     BiConsumer<ObjectInstance, Boolean> publishNativeIdentity,
                     Supplier<StageplayObjectDirection> directedAdmission) {
        this.callbacks = callbacks;
        this.invalidateMembership = invalidateMembership;
        this.publishNativeIdentity = publishNativeIdentity;
        this.directedAdmission = directedAdmission;
        this.slots = slots;
        executionOrder = new ObjectInstance[slots.processSlotCount()];
        allocator = new SlotAllocator(slots,
                slots.twoAxisCursorPlacement() ? SlotEmptyPredicate.ROUTINE_POINTER : SlotEmptyPredicate.ID_BYTE);
    }

    /** Returns whether this admission minted an identity (native publication needs that fact). */
    private boolean assignIdentity(ObjectInstance instance, ObjectSpawn spawn) {
        boolean firstIdentity = !identities.containsKey(instance);
        if (firstIdentity) {
            if (spawn != null) {
                var spawnRef = SpawnRefId.fromSpawn(spawn);
                identities.put(instance, ObjectRefId.forObject(
                        spawnRef, nextIdentityOrdinal++));
            } else {
                int slot = instance instanceof AbstractObjectInstance object ? object.getSlotIndex() : -1;
                identities.put(instance, ObjectRefId.dynamic(
                        slot, 0, nextIdentityOrdinal++));
            }
        }
        return firstIdentity;
    }

    /** Ordinary admission publishes the native word only after the stable identity exists. */
    void publishIdentity(ObjectInstance instance, ObjectSpawn spawn) {
        publishNativeIdentity.accept(instance, assignIdentity(instance, spawn));
    }

    /** Structural placement admission, including its existing creator/directed-world boundary. */
    void admitPlaced(ObjectSpawn spawn, ObjectInstance instance) {
        callbacks.register(instance, spawn);
        if (directedAdmission.get() != null
                && !callbacks.call(instance, () -> directedAdmission.get().allowsObject(instance))) {
            callbacks.run(instance, () -> directedAdmission.get().removeDynamic(instance));
            callbacks.unregister(instance);
            return;
        }
        if (directedAdmission.get() != null) {
            callbacks.run(instance, () -> directedAdmission.get().noteNativeAdmission(instance));
        }
        placed.put(spawn, instance);
        invalidateMembership.run();
        spawnByInstance.put(instance, spawn);
        publishIdentity(instance, spawn);
    }

    /** Captured identity precedes normal admission so restoring never burns a new ordinal. */
    void admitRestoredPlaced(ObjectSpawn spawn, ObjectInstance instance, ObjectRefId capturedId,
                             RewindIdentityTable restoreTable) {
        if (capturedId != null) identities.put(instance, capturedId);
        admitPlaced(spawn, instance);
        if (capturedId != null) restoreTable.registerObject(instance, capturedId);
        if (instance instanceof AbstractObjectInstance object) publishExecutionSlot(object);
    }

    /**
     * Retirement keeps occurrence identity available through object-side state expiry.
     * The world notification sees the reverse association removed but the old stable
     * identity still present; identity retirement follows that callback, as before.
     */
    void retirePlaced(ObjectSpawn spawn, ObjectCallbackRouter.RemovalCallback removalCallback,
                      Consumer<ObjectInstance> expireObjectState,
                      BiConsumer<ObjectInstance, ObjectCallbackRouter.RemovalCallback> notifyWorld) {
        ObjectInstance removed = placed.remove(spawn);
        if (removed == null) return;
        if (removalCallback == null || !removalCallback.isFor(removed)) {
            removalCallback = callbacks.captureRemovalCallback(removed);
        }
        invalidateMembership.run();
        callbacks.unregister(removed);
        expireObjectState.accept(removed);
        spawnByInstance.remove(removed);
        notifyWorld.accept(removed, removalCallback);
        identities.remove(removed);
    }

    /**
     * Dynamic retirement expires contact state before ownership and identity.
     * Unlike placed retirement, its world notification sees the identity retired.
     */
    boolean retireDynamic(ObjectInstance object, ObjectCallbackRouter.RemovalCallback removalCallback,
                          Consumer<ObjectInstance> expireObjectState,
                          BiConsumer<ObjectInstance, ObjectCallbackRouter.RemovalCallback> notifyWorld) {
        if (!dynamic.remove(object)) {
            return false;
        }
        if (removalCallback == null || !removalCallback.isFor(object)) {
            removalCallback = callbacks.captureRemovalCallback(object);
        }
        invalidateMembership.run();
        expireObjectState.accept(object);
        dynamicOwnership.remove(object);
        identities.remove(object);
        notifyWorld.accept(object, removalCallback);
        return true;
    }

    /** Restore/reset discards membership without performing ordinary unload callbacks. */
    void clearPlaced() {
        callbacks.unregisterAll(placed.values());
        placed.clear();
        invalidateMembership.run();
        spawnByInstance.clear();
    }

    /**
     * Publishes a reconstructed dynamic actor as one population operation. Its
     * captured slot was installed before constructor-child hooks ran. Identity
     * and auxiliary membership must exist before invalidation and exec publication.
     */
    void publishReconstructedDynamic(AbstractObjectInstance object, DynamicObjectEntry entry,
                                     RewindIdentityTable restoreTable) {
        if (entry.objectId() != null) {
            identities.put(object, entry.objectId());
            restoreTable.registerObject(object, entry.objectId());
        } else {
            publishIdentity(object, callbacks.call(object, object::getSpawn));
        }
        dynamic.add(object);
        if (entry.rewindableAuxiliary()) dynamicOwnership.markRewindable(object);
        invalidateMembership.run();
        publishExecutionSlot(object);
    }

    /**
     * Normal native admission has already selected/bound its slot. Publish stable
     * identity and membership before the execution owner sets same-frame flags;
     * invalidate after those flags, exactly at the previous publication boundary.
     */
    void publishDynamicAdmission(ObjectInstance object, ObjectSpawn spawn, Runnable publishFrameAdmission) {
        publishIdentity(object, spawn);
        dynamic.add(object);
        publishFrameAdmission.run();
        invalidateMembership.run();
    }

    boolean admitNonRewindableAuxiliary(ObjectInstance object, ObjectServices services) {
        if (!prepareAuxiliary(object, services)) return false;
        dynamic.add(object);
        dynamicOwnership.markNonRewindable(object);
        return true;
    }

    boolean admitRewindableAuxiliary(ObjectInstance object, ObjectServices services,
                                    Set<ObjectInstance> deferredThisFrame, boolean updating) {
        if (!prepareAuxiliary(object, services)) return false;
        publishIdentity(object, object.getSpawn());
        dynamic.add(object);
        dynamicOwnership.markRewindable(object);
        if (updating && object instanceof AbstractObjectInstance instance
                && instance.skipsSameFrameUpdateAfterSpawn()) {
            deferredThisFrame.add(object);
        }
        return true;
    }

    private boolean prepareAuxiliary(ObjectInstance object, ObjectServices services) {
        if (object == null) return false;
        if (object instanceof AbstractObjectInstance instance) {
            instance.setServices(services);
            ObjectLifetimeOps.clearPreviousManagerSlot(instance);
        }
        return true;
    }

    /** Reset keeps callback-owner retirement between membership and classification retirement. */
    void clearDynamicForReset(Runnable retireCallbackOwners) {
        dynamic.clear();
        retireCallbackOwners.run();
        dynamicOwnership.clear();
    }

    /** Reconstruction resets actor membership/order without ordinary unload callbacks. */
    void clearForReconstruction(List<DynamicObjectEntry> capturedDynamics) {
        clearPlaced();
        dynamic.clear();
        dynamicOwnership.clear();
        Arrays.fill(executionOrder, null);
        dynamicOwnership.rememberCapturedOrder(capturedDynamics);
    }

    void clearNativeReservationsAndExecution() {
        reservedChildren.clear();
        allocator.clear();
        Arrays.fill(executionOrder, null);
    }

    void restoreChildReservations(List<com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.ChildSpawnEntry> entries) {
        reservedChildren.clear();
        for (var entry : entries) {
            reservedChildren.put(entry.parentSpawn(),
                    Arrays.copyOf(entry.reservedSlots(), entry.reservedSlots().length));
        }
    }

    /** Late effects rejoin the captured dynamic order; the metadata owner never mutates this list. */
    void restoreCapturedOrder() {
        Comparator<ObjectInstance> order = dynamicOwnership.capturedOrder(identities::get);
        if (order != null) dynamic.sort(order);
    }

    /** Destroyed-object cleanup preserves unload/contact/notification order before iterator removal. */
    boolean retireDestroyedDynamics(IntConsumer releaseSlot, Consumer<ObjectInstance> expireObjectState,
                                   BiConsumer<ObjectInstance, ObjectCallbackRouter.RemovalCallback> notifyWorld) {
        boolean changed = false;
        Iterator<ObjectInstance> iterator = dynamic.iterator();
        while (iterator.hasNext()) {
            ObjectInstance object = iterator.next();
            if (callbacks.call(object, object::isDestroyed)) {
                if (object instanceof AbstractObjectInstance actor) {
                    int slot = callbacks.call(object, actor::getSlotIndex);
                    if (slot >= 0) releaseSlot.accept(slot);
                }
                var removalCallback = callbacks.captureRemovalCallback(object);
                callbacks.runAndUnregister(object, object::onUnload);
                expireObjectState.accept(object);
                dynamicOwnership.remove(object);
                identities.remove(object);
                notifyWorld.accept(object, removalCallback);
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    /** Install the captured identity before ordinary admission can mint a new ordinal. */
    void restoreCapturedIdentity(ObjectInstance object, ObjectRefId capturedId) {
        if (capturedId == null) {
            throw new IllegalArgumentException("restored dynamic object identity is required");
        }
        identities.put(object, capturedId);
    }

    private void publishExecutionSlot(AbstractObjectInstance object) {
        int slot = callbacks.call(object, object::getSlotIndex);
        if (slot >= 0 && slot < executionOrder.length) executionOrder[slot] = object;
    }

    int nextIdentityOrdinal() { return nextIdentityOrdinal; }

    /** Reset/restore retires the old ledger and ordinal together before reconstruction. */
    void restoreIdentitySequence(int capturedNextOrdinal) {
        nextIdentityOrdinal = capturedNextOrdinal;
        identities.clear();
    }

    void inheritIdentities(ObjectPopulation previous, List<ObjectInstance> carried) {
        nextIdentityOrdinal = Math.max(nextIdentityOrdinal, previous.nextIdentityOrdinal);
        for (ObjectInstance object : carried) {
            var id = previous.identities.get(object);
            if (id != null) identities.put(object, id);
        }
    }

    void registerIdentities(RewindIdentityTable table) {
        for (ObjectInstance instance : placed.values()) {
            var id = identities.get(instance);
            if (id != null) table.registerObject(instance, id);
        }
        for (ObjectInstance instance : dynamic) {
            var id = identities.get(instance);
            if (id != null) table.registerObject(instance, id);
        }
    }

    Map<Integer, Integer> occupiedDynamicSlotIds(Collection<ObjectInstance> active) {
        Map<Integer, Integer> occupancy = new HashMap<>();
        for (ObjectInstance instance : active) {
            if (!(instance instanceof AbstractObjectInstance object)
                    || callbacks.call(instance, instance::isDestroyed)) continue;
            ObjectSpawn spawn = callbacks.call(instance, instance::getSpawn);
            if (spawn == null) continue;
            int slot = callbacks.call(instance, object::getSlotIndex);
            if (slots.isDynamicSlot(slot)) {
                int liveId = callbacks.call(instance, object::getLiveObjectId);
                occupancy.put(slot, liveId >= 0 ? liveId : spawn.objectId() & 0xFF);
            }
        }
        return occupancy;
    }

    Map<Integer, Integer> occupiedDynamicSlotIdsWithReservations(Collection<ObjectInstance> active) {
        Map<Integer, Integer> occupancy = occupiedDynamicSlotIds(active);
        for (int[] childSlots : reservedChildren.values()) {
            if (childSlots == null) continue;
            for (int slot : childSlots) {
                if (slots.isDynamicSlot(slot)) {
                    occupancy.putIfAbsent(slot, ObjectManager.SLOT_STATE_RESERVED_CHILD);
                }
            }
        }
        int lastDynamic = slots.firstDynamicSlot() + slots.dynamicSlotCount();
        for (int slot = slots.firstDynamicSlot(); slot < lastDynamic; slot++) {
            if (slots.isDynamicSlot(slot) && !allocator.isEmpty(slot)) {
                occupancy.putIfAbsent(slot, ObjectManager.SLOT_STATE_UNATTRIBUTED);
            }
        }
        return occupancy;
    }

    List<TransitionSstOccupant> snapshotPersistent() {
        List<TransitionSstOccupant> snapshot = new ArrayList<>();
        for (ObjectInstance instance : dynamic) {
            if (instance == null || callbacks.call(instance, instance::isDestroyed)
                    || !callbacks.call(instance, instance::isPersistent)
                    || instance instanceof BossChildComponent) continue;
            snapshot.add(new TransitionSstOccupant(instance,
                    instance instanceof AbstractObjectInstance object
                            ? callbacks.call(instance, object::getSlotIndex) : -1));
        }
        return snapshot;
    }

    List<TransitionSstOccupant> snapshotAllLiveSstObjects() {
        List<TransitionSstOccupant> snapshot = new ArrayList<>();
        placed.values().forEach(instance -> addLiveSlotBacked(snapshot, instance));
        dynamic.forEach(instance -> addLiveSlotBacked(snapshot, instance));
        snapshot.sort(Comparator.comparingInt(TransitionSstOccupant::originalSlot));
        return snapshot;
    }

    private void addLiveSlotBacked(List<TransitionSstOccupant> snapshot, ObjectInstance instance) {
        if (instance == null || callbacks.call(instance, instance::isDestroyed)
                || snapshot.stream().anyMatch(entry -> entry.identity() == instance)) return;
        if (instance instanceof AbstractObjectInstance object) {
            int slot = callbacks.call(instance, object::getSlotIndex);
            if (slot >= 0) snapshot.add(new TransitionSstOccupant(instance, slot));
        }
    }

    long[] captureOwnedUsedSlotBits() {
        BitSet owned = new BitSet(slots.dynamicSlotCount());
        for (ObjectInstance instance : placed.values()) {
            markOwnedSlot(owned, instance);
        }
        for (ObjectInstance instance : dynamic) {
            markOwnedSlot(owned, instance);
        }
        for (int[] childSlots : reservedChildren.values()) {
            if (childSlots == null) {
                continue;
            }
            for (int slot : childSlots) {
                if (slots.isDynamicSlot(slot)) {
                    owned.set(slots.toExecIndex(slot));
                }
            }
        }
        return owned.toLongArray();
    }

    private void markOwnedSlot(BitSet owned, ObjectInstance instance) {
        if (instance instanceof AbstractObjectInstance object) {
            int slot = callbacks.call(instance, object::getSlotIndex);
            if (slots.isDynamicSlot(slot)) owned.set(slots.toExecIndex(slot));
        }
    }

    void applyLevelRepeatOffset(Collection<ObjectInstance> active, int offsetX, int offsetY) {
        for (ObjectInstance instance : new ArrayList<>(active)) {
            if (instance != null && !callbacks.call(instance, instance::isDestroyed)
                    && callbacks.call(instance, instance::participatesInLevelRepeatOffset)) {
                callbacks.run(instance, () -> instance.applyLevelRepeatOffset(offsetX, offsetY));
            }
        }
    }

    boolean rebuildCachesIfDirty(boolean dirty, List<ObjectInstance> activeCache,
                                 List<ObjectInstance> solidCache,
                                 List<ObjectInstance> touchCache) {
        if (!dirty) return false;
        activeCache.clear();
        activeCache.addAll(placed.values());
        activeCache.addAll(dynamic);
        activeCache.sort(Comparator.comparingInt(instance ->
                instance instanceof AbstractObjectInstance object
                        ? callbacks.call(instance, object::getSlotIndex) : Integer.MAX_VALUE));
        solidCache.clear();
        touchCache.clear();
        for (ObjectInstance instance : activeCache) {
            if (instance instanceof SolidObjectProvider) solidCache.add(instance);
            if (instance instanceof TouchResponseProvider) touchCache.add(instance);
        }
        return false;
    }

    boolean hasInlinePlaneSwitcher(ObjectSpawn spawn) {
        return placed.values().stream().anyMatch(instance ->
                instance instanceof InlinePlaneSwitcher
                        && java.util.Objects.equals(instance.getSpawn(), spawn));
    }
}

package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.level.TransitionSstOccupant;
import com.openggf.level.objects.boss.BossChildComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Read-only population diagnostics and transition snapshots for {@link ObjectManager}. */
final class ObjectManagerPopulationView {
    private final ObjectCallbackRouter callbacks;
    private final ObjectSlotLayout slots;
    private final SlotAllocator allocator;
    private final Map<ObjectSpawn, int[]> reservedChildren;
    private final Map<ObjectSpawn, ObjectInstance> placed;
    private final List<ObjectInstance> dynamic;
    private final List<ObjectInstance> postPlayerScratch;

    ObjectManagerPopulationView(ObjectCallbackRouter callbacks, ObjectSlotLayout slots,
                                SlotAllocator allocator, Map<ObjectSpawn, int[]> reservedChildren,
                                Map<ObjectSpawn, ObjectInstance> placed,
                                List<ObjectInstance> dynamic,
                                List<ObjectInstance> postPlayerScratch) {
        this.callbacks = callbacks;
        this.slots = slots;
        this.allocator = allocator;
        this.reservedChildren = reservedChildren;
        this.placed = placed;
        this.dynamic = dynamic;
        this.postPlayerScratch = postPlayerScratch;
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

    void runPostPlayerHooks(Collection<ObjectInstance> active, PlayableEntity player, int vIntRunCount) {
        if (player == null) return;
        postPlayerScratch.clear();
        postPlayerScratch.addAll(active);
        try {
            for (ObjectInstance instance : postPlayerScratch) {
                if (instance != null && !callbacks.call(instance, instance::isDestroyed)
                        && instance instanceof PostPlayerUpdateHook hook) {
                    callbacks.run(instance, () -> hook.updatePostPlayer(vIntRunCount, player));
                }
            }
        } finally {
            postPlayerScratch.clear();
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

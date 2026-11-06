package com.openggf.level.objects;

import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;

final class ObjectCollisionResponseList {
    private static final int S3K_MAX_ENTRIES = 0x7E / 2;

    private final List<ObjectInstance> previousObjects = new ArrayList<>();
    private final List<ObjectInstance> currentObjects = new ArrayList<>();
    // Capture RAM addresses at publication, before Java deletion can detach an
    // owner's slot. Empty addresses still occupy native list capacity.
    private final List<Integer> previousSlots = new ArrayList<>();
    private final List<Integer> currentSlots = new ArrayList<>();
    private IntFunction<ObjectInstance> nativePointerResolver;
    private boolean nativeSlotsAuthoritative;
    private final List<ObjectRefId> restoredPreviousOrder = new ArrayList<>();
    private final List<ObjectRefId> restoredCurrentOrder = new ArrayList<>();
    private final Map<ObjectRefId, ObjectInstance> restoredObjects = new HashMap<>();
    private boolean usePrevious;
    private int currentBuildCursor;
    private ObjectManagerSnapshot.CollisionBuildStage buildStage =
            ObjectManagerSnapshot.CollisionBuildStage.IDLE;

    void setUsePrevious(boolean usePrevious) {
        this.usePrevious = usePrevious;
    }

    boolean usesPrevious() {
        return usePrevious;
    }

    List<ObjectInstance> touchResponseObjects(List<ObjectInstance> currentObjects) {
        return usePrevious ? playerReadView() : currentObjects;
    }

    private ObjectCallbackRouter eligibilityCallbacks;

    void freezePreviousReadView() {
        usePrevious = true;
        buildStage = ObjectManagerSnapshot.CollisionBuildStage.PREVIOUS_READ_FROZEN;
    }

    void resetCurrentBuild() {
        currentObjects.clear();
        currentSlots.clear();
        restoredCurrentOrder.clear();
        currentBuildCursor = 0;
        buildStage = ObjectManagerSnapshot.CollisionBuildStage.CURRENT_BUILD_RESET;
    }

    void abortCurrentBuild() {
        // The dispatch scope is closed by the caller, but the partially built ROM
        // list remains represented runtime state. Rewind may capture this seam and
        // must retain both its ordered ids and its build cursor/stage.
    }

    void markDynamicBuildComplete() {
        buildStage = ObjectManagerSnapshot.CollisionBuildStage.DYNAMIC_BUILD_COMPLETE;
    }

    void addToCurrentBuild(ObjectInstance instance) {
        addToCurrentBuild(instance, eligibilityCallbacks);
    }

    void addToCurrentBuild(ObjectInstance instance, ObjectCallbackRouter callbacks) {
        if (isEligible(instance, callbacks) && currentSlots.size() < S3K_MAX_ENTRIES) {
            int slot = physicalSlot(instance, callbacks);
            currentObjects.add(instance);
            currentSlots.add(slot);
            currentBuildCursor++;
            buildStage = ObjectManagerSnapshot.CollisionBuildStage.CURRENT_BUILDING;
        }
    }

    void captureCompletedBuild() {
        refreshNativePointerOwners();
        previousSlots.clear();
        previousSlots.addAll(currentSlots);
        nativeSlotsAuthoritative = nativePointerResolver != null;
        previousObjects.clear();
        previousObjects.addAll(currentObjects);
        restoredPreviousOrder.clear();
        restoredCurrentOrder.clear();
        restoredObjects.clear();
        usePrevious = true;
        buildStage = ObjectManagerSnapshot.CollisionBuildStage.COMPLETED;
    }

    void captureCompletedBuild(List<ObjectInstance> objects) {
        resetCurrentBuild();
        for (ObjectInstance instance : objects) {
            addToCurrentBuild(instance);
        }
        captureCompletedBuild();
    }

    /** Explicit transient admission before the player slot, retaining native capacity and existing relative order. */
    boolean admitBeforePlayer(ObjectInstance instance) {
        return admitBeforePlayer(instance, eligibilityCallbacks);
    }

    boolean admitBeforePlayer(ObjectInstance instance, ObjectCallbackRouter callbacks) {
        if (!isEligible(instance, callbacks) || previousSlots.size() >= S3K_MAX_ENTRIES) return false;
        if (!playerReadView().contains(instance)) {
            int slot = physicalSlot(instance, callbacks);
            previousObjects.addFirst(instance);
            previousSlots.addFirst(slot);
        }
        return true;
    }

    List<ObjectInstance> playerReadView() {
        if (nativePointerResolver != null) project(previousSlots, previousObjects);
        return previousObjects;
    }

    List<ObjectInstance> currentBuildView() {
        if (nativePointerResolver != null) project(currentSlots, currentObjects);
        return currentObjects;
    }

    ObjectManagerSnapshot.CollisionResponseState captureRewindState(
            Function<ObjectInstance, ObjectRefId> encoder) {
        refreshNativePointerOwners();
        return new ObjectManagerSnapshot.CollisionResponseState(
                encode(previousObjects, encoder),
                encode(currentObjects, encoder),
                usePrevious,
                currentBuildCursor,
                buildStage);
    }

    void restoreRewindState(
            ObjectManagerSnapshot.CollisionResponseState state,
            Function<ObjectRefId, ObjectInstance> resolver) {
        previousObjects.clear();
        currentObjects.clear();
        previousSlots.clear();
        currentSlots.clear();
        nativeSlotsAuthoritative = false;
        restoredPreviousOrder.clear();
        restoredCurrentOrder.clear();
        restoredObjects.clear();
        if (state == null) {
            usePrevious = false;
            currentBuildCursor = 0;
            buildStage = ObjectManagerSnapshot.CollisionBuildStage.IDLE;
            return;
        }
        restoredPreviousOrder.addAll(state.previousCollisionObjectIds());
        restoredCurrentOrder.addAll(state.currentCollisionBuildObjectIds());
        resolveKnown(restoredPreviousOrder, resolver);
        resolveKnown(restoredCurrentOrder, resolver);
        rebuildRestoredLists();
        usePrevious = state.usePreviousCollisionList();
        currentBuildCursor = state.currentCollisionBuildCursor();
        buildStage = state.collisionBuildStage();
    }

    void bindRestoredObject(ObjectRefId id, ObjectInstance object) {
        if (id == null || object == null) {
            throw new IllegalArgumentException("restored collision responder identity is required");
        }
        if (nativeSlotsAuthoritative && nativePointerResolver != null) {
            refreshNativePointerOwners();
            return;
        }
        if (restoredPreviousOrder.contains(id) || restoredCurrentOrder.contains(id)) {
            restoredObjects.put(id, object);
            rebuildRestoredLists();
        }
    }

    void captureForNextFrame(List<ObjectInstance> currentObjects) {
        captureForNextFrame(currentObjects, null);
    }

    /**
     * Eligibility asks each object whether it publishes this frame, so creator
     * objects are consulted through the fault router when one is supplied.
     */
    void captureForNextFrame(List<ObjectInstance> currentObjects,
            ObjectCallbackRouter callbacks) {
        boolean selectedReadView = usePrevious;
        ObjectCallbackRouter previousRouter = eligibilityCallbacks;
        eligibilityCallbacks = callbacks;
        try {
            captureCompletedBuild(currentObjects);
        } finally {
            eligibilityCallbacks = previousRouter;
        }
        usePrevious = selectedReadView;
    }

    void configureNativePointerProjection(IntFunction<ObjectInstance> resolver) {
        nativePointerResolver = java.util.Objects.requireNonNull(resolver, "native pointer resolver");
    }

    void refreshNativePointerOwners() {
        if (nativePointerResolver != null) {
            project(previousSlots, previousObjects);
            project(currentSlots, currentObjects);
        }
    }

    private void project(List<Integer> slots, List<ObjectInstance> objects) {
        objects.clear();
        for (int slot : slots) {
            ObjectInstance owner = slot < 0 ? null : nativePointerResolver.apply(slot);
            if (owner != null) objects.add(owner);
        }
    }

    private int physicalSlot(ObjectInstance instance) {
        return physicalSlot(instance, eligibilityCallbacks);
    }

    private int physicalSlot(ObjectInstance instance, ObjectCallbackRouter callbacks) {
        if (!(instance instanceof AbstractObjectInstance object)) return -1;
        return callbacks != null ? callbacks.call(instance, object::getSlotIndex) : object.getSlotIndex();
    }

    Object captureNativePointers() {
        return new State(toArray(previousSlots), toArray(currentSlots));
    }

    void restoreNativePointers(Object snapshot) {
        if (!(snapshot instanceof State state)) {
            throw new IllegalArgumentException("Invalid native collision pointer snapshot");
        }
        if (nativePointerResolver == null) {
            throw new IllegalStateException("Native collision pointer projection is not configured");
        }
        previousSlots.clear();
        for (int slot : state.previousSlots) previousSlots.add(slot);
        currentSlots.clear();
        for (int slot : state.currentSlots) currentSlots.add(slot);
        // All addresses, including unresolved/empty SSTs, are now authoritative.
        // Later player-bound owner registration resolves by address, not old ids.
        nativeSlotsAuthoritative = true;
        restoredPreviousOrder.clear();
        restoredCurrentOrder.clear();
        restoredObjects.clear();
        refreshNativePointerOwners();
    }

    private static int[] toArray(List<Integer> slots) {
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }

    private record State(int[] previousSlots, int[] currentSlots) {
        private State {
            previousSlots = checkedCopy(previousSlots);
            currentSlots = checkedCopy(currentSlots);
        }
        private static int[] checkedCopy(int[] slots) {
            java.util.Objects.requireNonNull(slots, "native collision addresses");
            if (slots.length > S3K_MAX_ENTRIES) {
                throw new IllegalArgumentException("Native collision pointer list exceeds capacity");
            }
            for (int slot : slots) {
                if (slot < -1 || slot > 0xFFFF) {
                    throw new IllegalArgumentException("Invalid native collision SST address");
                }
            }
            return slots.clone();
        }
    }

    private boolean isEligible(ObjectInstance instance) {
        return isEligible(instance, eligibilityCallbacks);
    }

    private boolean isEligible(ObjectInstance instance, ObjectCallbackRouter callbacks) {
        // Add_SpriteToCollisionResponseList has no camera-range gate; the
        // object routine decides whether to publish, and this helper only
        // enforces the native $7E-byte list capacity
        // (docs/skdisasm/sonic3k.asm:21236-21246).
        if (!(instance instanceof TouchResponseProvider)) {
            return false;
        }
        if (callbacks == null) {
            return !instance.isDestroyed()
                    && instance.publishesTouchResponseListEntryThisFrame();
        }
        return !callbacks.call(instance, instance::isDestroyed)
                && callbacks.call(instance, instance::publishesTouchResponseListEntryThisFrame);
    }

    private static List<ObjectRefId> encode(
            List<ObjectInstance> objects,
            Function<ObjectInstance, ObjectRefId> encoder) {
        List<ObjectRefId> ids = new ArrayList<>(objects.size());
        for (ObjectInstance object : objects) {
            ObjectRefId id = encoder.apply(object);
            if (id == null) {
                throw new IllegalStateException(
                        "collision response publisher has no rewind identity: "
                                + object.getClass().getName());
            }
            ids.add(id);
        }
        return ids;
    }

    private void resolveKnown(
            List<ObjectRefId> ids,
            Function<ObjectRefId, ObjectInstance> resolver) {
        for (ObjectRefId id : ids) {
            ObjectInstance object = resolver.apply(id);
            if (object != null) {
                restoredObjects.put(id, object);
            }
        }
    }

    private void rebuildRestoredLists() {
        previousObjects.clear();
        currentObjects.clear();
        previousSlots.clear();
        currentSlots.clear();
        appendResolved(restoredPreviousOrder, previousObjects, previousSlots);
        appendResolved(restoredCurrentOrder, currentObjects, currentSlots);
    }

    private void appendResolved(List<ObjectRefId> order, List<ObjectInstance> target, List<Integer> slots) {
        for (ObjectRefId id : order) {
            ObjectInstance object = restoredObjects.get(id);
            if (object != null) {
                target.add(object);
                slots.add(physicalSlot(object));
            }
        }
    }
}

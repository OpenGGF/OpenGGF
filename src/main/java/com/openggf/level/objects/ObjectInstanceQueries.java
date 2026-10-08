package com.openggf.level.objects;

import com.openggf.game.rewind.identity.ObjectRefId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Live population queries; allocation, callback ownership and rewind IDs stay with the manager. */
final class ObjectInstanceQueries {
    private ObjectInstanceQueries() {
    }

    static ObjectQuery liveQuery(ObjectManager manager, Map<ObjectInstance, ObjectRefId> identities) {
        return new LiveQuery(manager, identities);
    }

    static int objectIdInSlot(Collection<ObjectInstance> objects, ObjectCallbackRouter callbacks, int slot) {
        for (ObjectInstance instance : objects) {
            if (instance instanceof AbstractObjectInstance object
                    && callbacks.call(instance, object::getSlotIndex) == slot
                    && !callbacks.call(instance, instance::isDestroyed)) {
                ObjectSpawn spawn = callbacks.call(instance, instance::getSpawn);
                if (spawn != null) return spawn.objectId() & 0xFF;
            }
        }
        return -1;
    }

    static boolean hasLiveLostRingAtSlot(Collection<ObjectInstance> dynamicObjects, int slotIndex) {
        if (slotIndex < 0) {
            return false;
        }
        for (ObjectInstance instance : dynamicObjects) {
            if (instance instanceof com.openggf.level.rings.LostRingObjectInstance ring
                    && !ring.isDestroyed()
                    && ring.getSlotIndex() == slotIndex) {
                return true;
            }
        }
        return false;
    }

    static <T extends ObjectInstance> List<T> activeObjectsOfType(
            Map<?, ObjectInstance> activeObjects,
            Collection<ObjectInstance> dynamicObjects,
            Class<T> type) {
        List<T> matches = new ArrayList<>();
        Set<ObjectInstance> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        collectMatches(activeObjects.values(), type, seen, matches);
        collectMatches(dynamicObjects, type, seen, matches);
        matches.sort((a, b) -> Integer.compare(slotOf(a), slotOf(b)));
        return matches;
    }

    private static <T extends ObjectInstance> void collectMatches(
            Collection<ObjectInstance> objects,
            Class<T> type,
            Set<ObjectInstance> seen,
            List<T> matches) {
        for (ObjectInstance instance : objects) {
            if (type.isInstance(instance) && seen.add(instance)) {
                matches.add(type.cast(instance));
            }
        }
    }

    private static int slotOf(ObjectInstance instance) {
        return instance instanceof AbstractObjectInstance object ? object.getSlotIndex() : Integer.MAX_VALUE;
    }

    private static final class LiveQuery implements ObjectQuery {
        private final ObjectManager manager;
        private final Map<ObjectInstance, ObjectRefId> identities;

        private LiveQuery(ObjectManager manager, Map<ObjectInstance, ObjectRefId> identities) {
            this.manager = manager;
            this.identities = identities;
        }

        @Override
        public <T extends ObjectInstance> List<T> activeObjectsOfType(Class<T> type) {
            List<T> objects = manager.activeObjectsOfType(Objects.requireNonNull(type));
            objects.sort((left, right) -> compareIdentities(identities.get(left), identities.get(right)));
            return List.copyOf(objects);
        }

        @Override
        public Optional<ObjectRefId> identityOf(ObjectInstance object) {
            for (ObjectInstance live : manager.getActiveObjects()) {
                if (live == object) return Optional.ofNullable(identities.get(object));
            }
            return Optional.empty();
        }

        @Override
        public Optional<ObjectInstance> resolve(ObjectRefId identity) {
            Objects.requireNonNull(identity);
            for (ObjectInstance live : manager.getActiveObjects()) {
                if (identity.equals(identities.get(live))) return Optional.of(live);
            }
            return Optional.empty();
        }

        private static int compareIdentities(ObjectRefId left, ObjectRefId right) {
            if (left == right) return 0;
            if (left == null) return 1;
            if (right == null) return -1;
            int order = Integer.compare(left.dynamicId(), right.dynamicId());
            if (order == 0) order = Integer.compare(left.spawnId(), right.spawnId());
            return order == 0 ? Integer.compare(left.generation(), right.generation()) : order;
        }
    }
}

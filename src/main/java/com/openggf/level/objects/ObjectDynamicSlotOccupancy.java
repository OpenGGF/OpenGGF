package com.openggf.level.objects;

import java.util.HashMap;
import java.util.Map;

/** Read-only dynamic SST occupancy view used by trace comparison. */
final class ObjectDynamicSlotOccupancy {
    private ObjectDynamicSlotOccupancy() {
    }

    static Map<Integer, Integer> snapshot(
            Iterable<ObjectInstance> activeObjects,
            ObjectSlotLayout slotLayout) {
        Map<Integer, Integer> occupancy = new HashMap<>();
        for (ObjectInstance instance : activeObjects) {
            if (instance instanceof AbstractObjectInstance object
                    && !instance.isDestroyed()
                    && instance.getSpawn() != null
                    && slotLayout.isDynamicSlot(object.getSlotIndex())) {
                occupancy.put(object.getSlotIndex(), instance.getSpawn().objectId() & 0xFF);
            }
        }
        return occupancy;
    }
}

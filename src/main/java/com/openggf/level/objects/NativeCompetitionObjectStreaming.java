package com.openggf.level.objects;

import java.util.List;

/** Internal native placement owner; installed explicitly by a competition world. */
public interface NativeCompetitionObjectStreaming {
    interface Loader {
        /** Success also includes a respawn-table skip; failure parks the native cursor. */
        boolean load(ObjectSpawn spawn, int respawnIndex, int firstSlot, int slotCount);
        void unload(int firstSlot, int slotCount);
    }

    /** Semantic bound for plain general allocation; after-current allocation has its own native contract. */
    int generalAllocationSlotLimitExclusive();
    void reset(List<ObjectSpawn> spawns, int initialCameraX);
    void update(int firstCameraX, int secondCameraX, Loader loader);
    Object capture();
    void restore(Object snapshot);
    void swapSlots();
}

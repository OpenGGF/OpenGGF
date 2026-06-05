package com.openggf.level.objects;

import java.util.List;

public interface ObjectRegistry {
    ObjectInstance create(ObjectSpawn spawn);

    void reportCoverage(List<ObjectSpawn> spawns);

    String getPrimaryName(int objectId);

    default ObjectSlotLayout objectSlotLayout() {
        return ObjectSlotLayout.SONIC_1;
    }

    /**
     * Per-game object load/unload windowing boundary consumed by the shared
     * {@link ObjectManager}. Defaults to {@link ObjectWindowingStrategy#LEGACY}
     * (no override; S1/S3K). The S2 registry returns the ROM-exact S2 strategy.
     */
    default ObjectWindowingStrategy objectWindowingStrategy() {
        return ObjectWindowingStrategy.LEGACY;
    }

    /**
     * Game-specific dynamic-object rewind codecs. Shared/core codecs remain in
     * {@link ObjectRewindDynamicCodecs}; concrete game object classes belong in
     * the owning registry implementation.
     */
    default List<DynamicObjectRewindCodec> dynamicRewindCodecs() {
        return List.of();
    }

    default List<String> getAliases(int objectId) {
        return List.of();
    }
}

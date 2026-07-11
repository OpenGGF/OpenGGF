package com.openggf.level.objects;

import java.util.Objects;

/**
 * Narrow restore-time context exposed to dynamic object restore and to
 * {@link ObjectRewindDynamicCodecs#genericRecreate}.
 */
public record DynamicObjectRecreateContext(ObjectManager objectManager,
                                           RewindClassResolver classResolver) {
    public DynamicObjectRecreateContext(ObjectManager objectManager) {
        this(objectManager, RewindClassResolver.ENGINE_ONLY);
    }

    public DynamicObjectRecreateContext {
        Objects.requireNonNull(objectManager, "objectManager");
        Objects.requireNonNull(classResolver, "classResolver");
    }

    public ObjectServices objectServices() {
        return objectManager.objectServicesForRewind();
    }

    /**
     * Returns the game-specific {@link ObjectRegistry} so that
     * {@link ObjectRewindDynamicCodecs#genericRecreate} can rebuild spawn-constructible
     * objects via {@code registry.create(spawn)} without referencing game-specific packages.
     */
    public ObjectRegistry objectRegistry() {
        return objectManager.rewindObjectRegistry();
    }
}

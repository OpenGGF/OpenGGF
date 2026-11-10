package com.openggf.level.objects;

import java.util.Objects;

/**
 * Narrow restore-time context exposed to dynamic object restore and to
 * {@link ObjectRewindDynamicCodecs#genericRecreate}.
 */
public record DynamicObjectRecreateContext(ObjectManager objectManager,
                                           RewindClassResolver classResolver,
                                           ObjectInstance resolvedParent,
                                           boolean parentResolutionProvided) {
    public DynamicObjectRecreateContext(ObjectManager objectManager) {
        this(objectManager, RewindClassResolver.ENGINE_ONLY);
    }

    /** Old engine-host callers retain lazy lookup in the eventual hook context. */
    public DynamicObjectRecreateContext(ObjectManager objectManager, RewindClassResolver classResolver) {
        this(objectManager, classResolver, null, false);
    }

    public DynamicObjectRecreateContext {
        Objects.requireNonNull(objectManager, "objectManager");
        Objects.requireNonNull(classResolver, "classResolver");
        if (!parentResolutionProvided && resolvedParent != null) {
            throw new IllegalArgumentException("A resolved parent requires an authoritative resolution");
        }
    }

    /** Carry the lifecycle's restore-table answer, including authoritative null. */
    public static DynamicObjectRecreateContext withResolvedParent(
            ObjectManager objectManager, RewindClassResolver classResolver, ObjectInstance parent) {
        return new DynamicObjectRecreateContext(objectManager, classResolver, parent, true);
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
        return objectManager.objectRegistry();
    }
}

package com.openggf.level.objects;

/**
 * Compatibility name for {@link SpawnRewindRecreatable}. All generic recreation
 * hooks now run with construction services installed by ObjectRewindDynamicCodecs.
 * New spawn-only implementations use SpawnRewindRecreatable. This adapter also
 * preserves construction services for historical callers invoking the hook directly.
 */
public interface SpawnConstructionContextRewindRecreatable extends SpawnRewindRecreatable {
    @Override
    default AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> SpawnRewindRecreatable.super.recreateForRewind(ctx));
    }
}

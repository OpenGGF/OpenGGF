package com.openggf.level;

import java.util.Objects;
import java.util.Set;

/** Engine admission bridge. This loader check binds provenance; it is not a Java sandbox. */
public final class LevelPatchOwnership {
    private LevelPatchOwnership() { }

    public static LevelPatch bind(String verifiedOwner,LevelPatch patch,Set<String> registeredOwnedKeys) {
        ClassLoader caller=com.openggf.util.EngineCallerAccess
                .callerOutside(LevelPatchOwnership.class).getClassLoader();
        if (caller!=LevelPatchOwnership.class.getClassLoader())
            throw new SecurityException("Only the engine may bind decoded level patch ownership");
        return Objects.requireNonNull(patch,"patch").bindOwnership(verifiedOwner,Set.copyOf(registeredOwnedKeys));
    }

    /** Engine-only final decode bridge; ordinary patches retain their existing snapshot behavior. */
    public static Level applyDecoded(LevelPatch patch,Level source) {
        ClassLoader caller=com.openggf.util.EngineCallerAccess
                .callerOutside(LevelPatchOwnership.class).getClassLoader();
        if (caller!=LevelPatchOwnership.class.getClassLoader())
            throw new SecurityException("Only the engine may apply admitted native placements");
        Objects.requireNonNull(patch,"patch");
        Objects.requireNonNull(source,"source");
        if (!patch.usesNativePlacements()) return patch.apply(source);
        if (!patch.matchesZone(source)) return source;
        if (!(source instanceof AbstractLevel nativeLevel) || source instanceof MutableLevel
                || source.getClass().getClassLoader()!=LevelPatchOwnership.class.getClassLoader())
            throw new IllegalArgumentException("Native placement admission requires an engine-defined native level");
        if (patch.retainsRings() && source instanceof com.openggf.level.objects.RingObjectPlacementMapping)
            throw new IllegalArgumentException("Native ring retention cannot replace object-backed ring placement ownership");
        try {
            if (source.getClass().getMethod("getObjects").getDeclaringClass()!=AbstractLevel.class
                    || source.getClass().getMethod("getRings").getDeclaringClass()!=AbstractLevel.class)
                throw new IllegalArgumentException("Native placement admission requires native placement accessors");
        } catch (NoSuchMethodException exception) {
            throw new IllegalArgumentException("Native placement accessors are unavailable",exception);
        }
        return patch.applyNativeDecoded(nativeLevel);
    }
}
